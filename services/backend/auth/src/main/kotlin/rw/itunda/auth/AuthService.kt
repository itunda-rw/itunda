package rw.itunda.auth

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import rw.itunda.core.domain.EmailVerificationToken
import rw.itunda.core.domain.InterestJar
import rw.itunda.core.domain.Notification
import rw.itunda.core.domain.PhoneVerificationToken
import rw.itunda.core.domain.User
import rw.itunda.core.domain.Wallet
import rw.itunda.core.domain.WalletType
import rw.itunda.core.geo.GeoUtils
import rw.itunda.core.geo.NominatimGeocodingClient
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.realtime.RealtimeMessagePublisher
import rw.itunda.core.repository.EmailVerificationTokenRepository
import rw.itunda.core.repository.InterestJarRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.PhoneVerificationTokenRepository
import rw.itunda.core.repository.UserRepository
import rw.itunda.core.repository.WalletRepository
import rw.itunda.core.wallet.AccountNumberGenerator
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import java.security.SecureRandom

/**
 * Port of backend/src/controllers/auth.controller.ts's login/register, with one fix
 * applied from day one instead of ported as a known gap: registration provisions a real
 * MAIN wallet (zero balance) for the new user. The Express backend's SECURITY.md lists
 * "new accounts have no wallet of their own" as its #1 open remediation item — since this
 * is a fresh implementation, there's no reason to carry that gap forward.
 */
@Service
class AuthService(
    private val userRepository: UserRepository,
    private val walletRepository: WalletRepository,
    private val interestJarRepository: InterestJarRepository,
    private val jwtService: JwtService,
    private val tokenBlocklistService: TokenBlocklistService,
    private val rateLimiter: RateLimiter,
    private val emailVerificationTokenRepository: EmailVerificationTokenRepository,
    private val phoneVerificationTokenRepository: PhoneVerificationTokenRepository,
    private val notificationRepository: NotificationRepository,
    private val nominatimGeocodingClient: NominatimGeocodingClient,
    private val deviceService: DeviceService,
    private val pushNotificationService: PushNotificationService,
    private val realtimeMessagePublisher: RealtimeMessagePublisher,
    private val accountNumberGenerator: AccountNumberGenerator,
) {
    private val passwordEncoder = BCryptPasswordEncoder()
    private val logger = LoggerFactory.getLogger(AuthService::class.java)
    private val secureRandom = SecureRandom()

    @Transactional
    fun register(request: RegisterRequest): AuthResponse {
        // Stricter and longer-windowed than login: creating an account is a rarer,
        // more sensitive action than a login retry, so fewer attempts should be
        // tolerated before this starts looking like account-creation spam.
        rateLimiter.checkLimit("auth:register:${request.phoneNumber}", limit = 3, window = Duration.ofMinutes(10))
        if (userRepository.existsByPhoneNumber(request.phoneNumber)) {
            throw PhoneAlreadyRegisteredException("An account with this phone number already exists")
        }
        // Real, not honor-system: an invalid/typo'd code fails registration loudly
        // rather than silently registering with no attribution, matching this repo's
        // general "don't swallow the error" convention.
        val referredByUserId = request.referralCode?.let { code ->
            userRepository.findByReferralCode(code)?.id
                ?: throw ReferralCodeNotFoundException("Referral code not found")
        }
        val user = User(
            id = "user_${UUID.randomUUID()}",
            phoneNumber = request.phoneNumber,
            email = request.email,
            firstName = request.firstName,
            lastName = request.lastName,
            passwordHash = passwordEncoder.encode(request.password),
            kycVerified = false,
            creditScore = 0,
            createdAt = Instant.now(),
            referralCode = generateReferralCode(),
            referredByUserId = referredByUserId,
        )
        userRepository.save(user)
        // Real phone verification, sent at registration itself (2026-07-26) -- see
        // requestPhoneVerification's own doc comment for the full delivery story.
        sendPhoneVerificationCode(user.id)

        walletRepository.save(
            Wallet(
                id = "wallet_${UUID.randomUUID()}",
                userId = user.id,
                accountNumber = accountNumberGenerator.generate(2024100000L),
                accountName = "${user.firstName}'s Main Account",
                type = WalletType.MAIN,
                balance = BigDecimal.ZERO,
                availableBalance = BigDecimal.ZERO,
            ),
        )
        // Fixed 2026-07-13, found live: SavingsService.createGoal requires a real
        // WalletType.SAVINGS wallet and only MAIN was ever provisioned here, so
        // POST /api/v1/savings/goals 404'd (WALLET_NOT_FOUND) for every real user.
        val savingsWallet = walletRepository.save(
            Wallet(
                id = "wallet_${UUID.randomUUID()}",
                userId = user.id,
                accountNumber = accountNumberGenerator.generate(2024100000L),
                accountName = "${user.firstName}'s Savings Account",
                type = WalletType.SAVINGS,
                balance = BigDecimal.ZERO,
                availableBalance = BigDecimal.ZERO,
            ),
        )
        // Fixed 2026-07-20, found live: the same "only ever seeded, never provisioned"
        // gap as above, one layer deeper -- SeedDataRunner was the ONLY place an
        // InterestJar was ever created (one hardcoded row for the demo user), so
        // GET /api/v1/savings/interest-jar 404'd (INTEREST_JAR_NOT_FOUND) for every real
        // registered user. Real Kakao Bank SafeBox (세이프박스) provisions interest
        // accrual the moment the sub-account exists -- InterestAccrualScheduler grows
        // this for real from a zero balance, same as a fresh SafeBox earning nothing
        // until money lands in it.
        interestJarRepository.save(
            InterestJar(
                userId = user.id,
                walletId = savingsWallet.id,
                balance = BigDecimal.ZERO,
                rate = 7.5,
                earnedThisMonth = BigDecimal.ZERO,
                earnedTotal = BigDecimal.ZERO,
                lastPaidAt = Instant.now(),
                nextPayoutAt = Instant.now().plusSeconds(86400),
            ),
        )

        // Real bug found and fixed 2026-07-27, same "only ever seeded, never
        // provisioned" gap as SAVINGS/InterestJar above, one more layer over:
        // StocksService.buyStock requires a real WalletType.INVESTMENT wallet and only
        // the seeded demo user (SeedDataRunner) ever got one -- POST /api/v1/stocks/buy
        // 404'd (WALLET_NOT_FOUND, via NoWalletException) for every real registered
        // user, meaning the entire real Toss Securities/Kakao Pay Securities-style
        // stock-buying feature was silently unusable outside the demo account.
        walletRepository.save(
            Wallet(
                id = "wallet_${UUID.randomUUID()}",
                userId = user.id,
                accountNumber = accountNumberGenerator.generate(2024100000L),
                accountName = "${user.firstName}'s Investment Account",
                type = WalletType.INVESTMENT,
                balance = BigDecimal.ZERO,
                availableBalance = BigDecimal.ZERO,
            ),
        )

        // Real device binding (2026-07-20) -- the device used to register already
        // proved password ownership in this same request, so it's auto-trusted rather
        // than needing a separate step-up immediately after signing up.
        deviceService.recordRegistrationDevice(user.id, request.deviceId, request.deviceName)
        return issueAuthResponse(user, "Registration successful", request.deviceId)
    }

    fun login(request: LoginRequest): AuthResponse {
        // Keyed by phoneNumber, not IP: the actual asset being brute-forced is one
        // account's password, and a real attacker rotates source IPs long before they
        // rotate target phone numbers. Checked before the password comparison so a
        // locked-out attacker can't keep spending CPU on bcrypt.
        rateLimiter.checkLimit("auth:login:${request.phoneNumber}", limit = 5, window = Duration.ofMinutes(1))
        val user = userRepository.findByPhoneNumber(request.phoneNumber)
            ?: throw InvalidCredentialsException("Invalid phone number or password")
        if (!passwordEncoder.matches(request.password, user.passwordHash)) {
            throw InvalidCredentialsException("Invalid phone number or password")
        }
        // Real device binding (2026-07-20) -- login still succeeds from an unrecognized
        // device (matches real bank UX: you can sign in and look around), it's just
        // recorded as untrusted until a real step-up re-verification -- see
        // DeviceVerificationFilter for where that's actually enforced.
        deviceService.recordLoginDevice(user.id, request.deviceId, request.deviceName)
        return issueAuthResponse(user, "Login successful", request.deviceId)
    }

    fun getProfile(userId: String): PublicUser {
        val user = userRepository.findById(userId).orElseThrow { UserNotFoundException("User not found") }
        return user.toPublic()
    }

    /**
     * Revokes the current access token and, if a refresh token is supplied, revokes
     * that too — otherwise a client could "log out" the access token while still
     * holding a live refresh token capable of silently minting a fresh one, which
     * isn't a real logout at all.
     */
    fun logout(accessToken: String, refreshToken: String?) {
        jwtService.verify(accessToken)?.let { decoded ->
            tokenBlocklistService.blacklist(decoded.jti, decoded.expiresAt)
            realtimeMessagePublisher.closeSessionsForToken(decoded.userId, decoded.jti)
        }
        refreshToken?.let { jwtService.verify(it)?.let { decoded -> tokenBlocklistService.blacklist(decoded.jti, decoded.expiresAt) } }
    }

    /**
     * Refresh token rotation: the presented refresh token is immediately revoked and a
     * brand-new access+refresh pair is issued. A stolen-but-unused refresh token that
     * gets replayed after the legitimate client already rotated it is now a detectable,
     * blocked replay instead of a silently reusable long-lived credential.
     */
    fun refresh(refreshToken: String): AuthResponse {
        val decoded = jwtService.verify(refreshToken)
            ?: throw InvalidRefreshTokenException("Refresh token is invalid or expired")
        if (!decoded.isRefresh) throw InvalidRefreshTokenException("Not a refresh token")
        if (tokenBlocklistService.isBlacklisted(decoded.jti)) {
            throw InvalidRefreshTokenException("Refresh token has already been used or revoked")
        }
        val user = userRepository.findById(decoded.userId).orElseThrow { InvalidRefreshTokenException("User not found") }

        tokenBlocklistService.blacklist(decoded.jti, decoded.expiresAt)
        // Real device binding (2026-07-20): the real deviceId claim from the presented
        // refresh token carries forward onto the freshly-minted pair, so a client never
        // needs to resend it on every refresh cycle.
        return issueAuthResponse(user, "Token refreshed", decoded.deviceId)
    }

    // Real, buildable half of task_profile (2026-07-17): a URL, not a binary upload --
    // see UpdateProfilePhotoRequest's doc comment for why.
    @Transactional
    fun updateProfilePhoto(userId: String, profilePhotoUrl: String): PublicUser {
        val user = userRepository.findById(userId).orElseThrow { UserNotFoundException("User not found") }
        val trimmed = profilePhotoUrl.trim()
        if (trimmed.isEmpty()) {
            throw InvalidProfilePhotoUrlException("A profile photo URL is required")
        }
        // Real bound, found via the same systematic sweep that fixed the identical gap
        // across Commerce/Eats/Marketplace/Jobs/RealEstate/Community/Messaging/Maps/
        // Merchant/Partners the same day -- profile_photo_url is VARCHAR(512), and this
        // DB's real STRICT_TRANS_TABLES mode throws a raw, unhandled 500 on an
        // over-length insert.
        if (trimmed.length > 512) {
            throw InvalidProfilePhotoUrlException("Profile photo URL must be 512 characters or fewer")
        }
        user.profilePhotoUrl = trimmed
        userRepository.save(user)
        return user.toPublic()
    }

    // Real hyperlocal neighborhood (2026-07-20) -- closes the "User has no address/
    // district field" gap Marketplace/Community/Jobs/RealEstate's own doc comments all
    // name. A real coordinate in (the same opt-in-share-my-location convention those
    // modules already use for a single post), reverse-geocoded through itunda's own
    // self-hosted Nominatim into a real neighborhood/sector-level name -- never a
    // self-declared free-text field, so it can't drift from where the user actually is.
    // Throws rather than silently storing null when geocoding can't resolve a real
    // neighborhood (unconfigured/unreachable/no match) -- an honest failure a client can
    // show, not a silent no-op that looks like it worked.
    @Transactional
    fun setNeighborhood(userId: String, latitude: Double, longitude: Double): PublicUser {
        if (!GeoUtils.isValidCoordinate(latitude, longitude)) {
            throw InvalidCoordinatesException("Latitude must be between -90 and 90, longitude between -180 and 180")
        }
        // Real anti-spam limit (found missing in a 2026-07-20 security sweep) -- every
        // other real endpoint in this codebase that burns a real external-network call
        // per request already has one (e.g. AccountAggregationService's "accounts:link",
        // 10/hour, whose own comment says exactly why: unlimited calls would just burn
        // another real simulated connector/provider call every single time). This one
        // calls itunda's own real self-hosted Nominatim on every invocation and had
        // shipped with zero protection.
        rateLimiter.checkLimit("auth:neighborhood:$userId", limit = 10, window = Duration.ofHours(1))
        val user = userRepository.findById(userId).orElseThrow { UserNotFoundException("User not found") }
        val neighborhood = nominatimGeocodingClient.reverseGeocode(latitude, longitude)
            ?: throw NeighborhoodNotResolvedException("Couldn't determine a neighborhood for this location")
        user.neighborhood = neighborhood
        user.neighborhoodVerifiedAt = Instant.now()
        user.neighborhoodVerificationCount += 1
        userRepository.save(user)
        return user.toPublic()
    }

    // Real dual-neighborhood support (2026-08-04) -- see User.kt's own doc comment.
    // Same real reverse-geocode-only provenance and anti-spam limit as setNeighborhood
    // above (shares the same rate-limit key/budget -- a second Nominatim call is exactly
    // as expensive as the first, no reason for a separate allowance). A caller with no
    // primary neighborhood yet can still call this -- it doesn't require setNeighborhood
    // to have run first, since "second" here just means "an additional real place", not
    // literally the second one ever set.
    @Transactional
    fun setSecondNeighborhood(userId: String, latitude: Double, longitude: Double): PublicUser {
        if (!GeoUtils.isValidCoordinate(latitude, longitude)) {
            throw InvalidCoordinatesException("Latitude must be between -90 and 90, longitude between -180 and 180")
        }
        rateLimiter.checkLimit("auth:neighborhood:$userId", limit = 10, window = Duration.ofHours(1))
        val user = userRepository.findById(userId).orElseThrow { UserNotFoundException("User not found") }
        val neighborhood = nominatimGeocodingClient.reverseGeocode(latitude, longitude)
            ?: throw NeighborhoodNotResolvedException("Couldn't determine a neighborhood for this location")
        user.secondNeighborhood = neighborhood
        userRepository.save(user)
        return user.toPublic()
    }

    @Transactional
    fun clearSecondNeighborhood(userId: String): PublicUser {
        val user = userRepository.findById(userId).orElseThrow { UserNotFoundException("User not found") }
        user.secondNeighborhood = null
        userRepository.save(user)
        return user.toPublic()
    }

    // Real age-eligibility gate for the Mini wallet (2026-07-28) -- see
    // MiniWalletService's own doc comment for the sourced 만 7세~18세 real eligibility
    // window this backs. Set once; a real, plausible past date only -- neither a future
    // date (obviously wrong input) nor implausibly far in the past (a fat-fingered year).
    @Transactional
    fun setBirthDate(userId: String, birthDate: LocalDate): PublicUser {
        val today = LocalDate.now(ZoneOffset.UTC)
        if (!birthDate.isBefore(today)) {
            throw InvalidBirthDateException("Birth date must be in the past")
        }
        if (birthDate.isBefore(today.minusYears(120))) {
            throw InvalidBirthDateException("Birth date is not plausible")
        }
        val user = userRepository.findById(userId).orElseThrow { UserNotFoundException("User not found") }
        user.birthDate = birthDate
        userRepository.save(user)
        return user.toPublic()
    }

    // Real, single-use, 30-minute token -- see EmailVerificationToken's doc comment.
    // Delivered via a real Notification (this backend's own existing in-app delivery
    // mechanism, already used for budget alerts) rather than a real email, since there
    // is no SMTP relay anywhere in this backend -- the token itself is real and never
    // echoed back in this endpoint's own response, so a client can't self-verify without
    // actually receiving it through that real channel.
    @Transactional
    fun requestEmailVerification(userId: String) {
        rateLimiter.checkLimit("auth:email-verify-request:$userId", limit = 3, window = Duration.ofMinutes(15))
        val user = userRepository.findById(userId).orElseThrow { UserNotFoundException("User not found") }
        if (user.email == null) throw NoEmailOnFileException("No email address on file to verify")
        if (user.emailVerified) throw EmailAlreadyVerifiedException("Email is already verified")

        val token = UUID.randomUUID().toString().replace("-", "")
        emailVerificationTokenRepository.invalidateUnusedByUserId(userId)
        emailVerificationTokenRepository.save(
            EmailVerificationToken(
                id = "evt_${UUID.randomUUID()}", userId = userId, token = passwordEncoder.encode(token),
                expiresAt = Instant.now().plusSeconds(1800), createdAt = Instant.now(),
            ),
        )
        val title = "Verify your email"
        val body = "Your email verification code is $token. It expires in 30 minutes."
        notificationRepository.save(
            Notification(
                id = "notif_${UUID.randomUUID()}", userId = userId, type = "PROFILE_EMAIL_VERIFICATION",
                title = title, body = body,
                isRead = false, createdAt = Instant.now(), dataJson = null,
            ),
        )
        // Real push (item 122, 2026-07-29) -- of every real Notification call site in
        // this backend, an OTP code delivery is the single most time-sensitive: a user
        // is actively waiting on this screen for it, unlike most other notification
        // types that are fine to pick up on the next in-app poll. See
        // PushNotificationService's own doc comment for the fuller "why these sites"
        // account.
        sendVerificationPushAfterCommit(userId, title, body)
    }

    @Transactional
    fun confirmEmailVerification(userId: String, token: String): PublicUser {
        val record = emailVerificationTokenRepository.findFirstByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(userId)
            ?.takeIf { it.userId == userId }
            ?: throw InvalidVerificationTokenException("Invalid or expired verification token")
        if (record.usedAt != null || !passwordEncoder.matches(token, record.token) || record.expiresAt.isBefore(Instant.now())) {
            throw InvalidVerificationTokenException("Invalid or expired verification token")
        }
        record.usedAt = Instant.now()
        emailVerificationTokenRepository.save(record)

        val user = userRepository.findById(userId).orElseThrow { UserNotFoundException("User not found") }
        user.emailVerified = true
        userRepository.save(user)
        return user.toPublic()
    }

    /**
     * Real phone verification -- closes docs/DESIGN_REFERENCES.md's own recommendation
     * ("the single most Toss-aligned, currently-real gap: Toss's whole real-name-
     * verification model is built on proven phone possession; itunda currently accepts
     * any unverified number"). Mirrors `requestEmailVerification`/
     * `confirmEmailVerification` field-for-field, per that same recommendation's own
     * instruction, with two honest differences: the code is a real 6-digit OTP (matching
     * real phone-verification UX, not a hex token) and it's sent automatically at
     * registration itself (`register` calls this directly), not only opt-in from a
     * profile screen -- "at registration" is the whole point of this gap. Delivered via
     * itunda's own real in-app Notification, same "no SMTP/SMS relay in this backend, so
     * reuse the real mechanism that does exist rather than fabricate one" convention
     * email verification already established -- honestly, this does NOT prove real SMS
     * possession the way a genuine carrier-delivered OTP would, since the user is already
     * authenticated when they see the in-app notification.
     */
    private fun sendPhoneVerificationCode(userId: String) {
        // Authentication codes are secrets: SecureRandom avoids the predictability of
        // Math.random while preserving the existing six-digit UX and 30-minute expiry.
        val code = (100000 + secureRandom.nextInt(900000)).toString()
        // A resend replaces, rather than adds to, the active challenge set.
        phoneVerificationTokenRepository.invalidateUnusedByUserId(userId)
        phoneVerificationTokenRepository.save(
            PhoneVerificationToken(
                id = "pvt_${UUID.randomUUID()}", userId = userId, token = passwordEncoder.encode(code),
                expiresAt = Instant.now().plusSeconds(1800), createdAt = Instant.now(),
            ),
        )
        val title = "Verify your phone number"
        val body = "Your phone verification code is $code. It expires in 30 minutes."
        notificationRepository.save(
            Notification(
                id = "notif_${UUID.randomUUID()}", userId = userId, type = "PHONE_VERIFICATION",
                title = title, body = body,
                isRead = false, createdAt = Instant.now(), dataJson = null,
            ),
        )
        // Real push (item 122) -- see requestEmailVerification's own doc comment above.
        sendVerificationPushAfterCommit(userId, title, body)
    }

    /** OTP pushes are external effects; only expose a code once its token row has committed. */
    private fun sendVerificationPushAfterCommit(userId: String, title: String, body: String) {
        val send = {
            try {
                pushNotificationService.sendToUser(userId, title, body)
            } catch (e: Exception) {
                logger.warn("Could not send verification push for user {}", userId, e)
            }
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            send()
            return
        }
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() = send()
        })
    }

    /** Real resend, for the code sent automatically at registration expiring or getting lost. */
    @Transactional
    fun requestPhoneVerification(userId: String) {
        rateLimiter.checkLimit("auth:phone-verify-request:$userId", limit = 3, window = Duration.ofMinutes(15))
        val user = userRepository.findById(userId).orElseThrow { UserNotFoundException("User not found") }
        if (user.phoneVerified) throw PhoneAlreadyVerifiedException("Phone number is already verified")
        sendPhoneVerificationCode(userId)
    }

    @Transactional
    fun confirmPhoneVerification(userId: String, code: String): PublicUser {
        // Six-digit OTPs have a deliberately small keyspace for usability, so limit
        // guesses independently of resend limits before looking up the token.
        rateLimiter.checkLimit("auth:phone-verify:$userId", limit = 5, window = Duration.ofMinutes(15))
        val record = phoneVerificationTokenRepository.findFirstByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(userId)
            ?.takeIf { it.userId == userId }
            ?: throw InvalidVerificationTokenException("Invalid or expired verification code")
        if (!passwordEncoder.matches(code, record.token) || record.expiresAt.isBefore(Instant.now())) {
            throw InvalidVerificationTokenException("Invalid or expired verification code")
        }
        record.usedAt = Instant.now()
        phoneVerificationTokenRepository.save(record)

        val user = userRepository.findById(userId).orElseThrow { UserNotFoundException("User not found") }
        user.phoneVerified = true
        userRepository.save(user)
        return user.toPublic()
    }

    private fun issueAuthResponse(user: User, message: String, deviceId: String? = null) = AuthResponse(
        message = message,
        user = user.toPublic(),
        accessToken = jwtService.issueAccessToken(user.id, user.phoneNumber, user.role, deviceId),
        refreshToken = jwtService.issueRefreshToken(user.id, deviceId),
    )

    // No collision-avoidance loop, same accepted-risk precedent as generateAccountNumber
    // above -- a UUID-derived 6-char code has a negligible real collision chance, and the
    // real DB unique constraint on referral_code is the actual backstop.
    private fun generateReferralCode(): String = "ITD" + UUID.randomUUID().toString().replace("-", "").take(6).uppercase()

    private fun User.toPublic() = PublicUser(
        id = id, phoneNumber = phoneNumber, email = email, firstName = firstName,
        lastName = lastName, kycVerified = kycVerified, creditScore = creditScore, createdAt = createdAt,
        referralCode = referralCode, profilePhotoUrl = profilePhotoUrl, emailVerified = emailVerified,
        phoneVerified = phoneVerified,
        neighborhood = neighborhood, neighborhoodVerifiedAt = neighborhoodVerifiedAt,
        neighborhoodVerificationCount = neighborhoodVerificationCount,
        secondNeighborhood = secondNeighborhood,
        birthDate = birthDate,
    )
}
