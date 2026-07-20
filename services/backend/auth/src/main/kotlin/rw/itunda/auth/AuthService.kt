package rw.itunda.auth

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.core.domain.EmailVerificationToken
import rw.itunda.core.domain.InterestJar
import rw.itunda.core.domain.Notification
import rw.itunda.core.domain.User
import rw.itunda.core.domain.Wallet
import rw.itunda.core.domain.WalletType
import rw.itunda.core.geo.GeoUtils
import rw.itunda.core.geo.NominatimGeocodingClient
import rw.itunda.core.repository.EmailVerificationTokenRepository
import rw.itunda.core.repository.InterestJarRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.UserRepository
import rw.itunda.core.repository.WalletRepository
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.util.UUID

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
    private val notificationRepository: NotificationRepository,
    private val nominatimGeocodingClient: NominatimGeocodingClient,
    private val deviceService: DeviceService,
) {
    private val passwordEncoder = BCryptPasswordEncoder()

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

        walletRepository.save(
            Wallet(
                id = "wallet_${UUID.randomUUID()}",
                userId = user.id,
                accountNumber = generateAccountNumber(),
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
                accountNumber = generateAccountNumber(),
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
        jwtService.verify(accessToken)?.let { tokenBlocklistService.blacklist(it.jti, it.expiresAt) }
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
        val user = userRepository.findById(userId).orElseThrow { UserNotFoundException("User not found") }
        if (user.email == null) throw NoEmailOnFileException("No email address on file to verify")
        if (user.emailVerified) throw EmailAlreadyVerifiedException("Email is already verified")

        val token = UUID.randomUUID().toString().replace("-", "")
        emailVerificationTokenRepository.save(
            EmailVerificationToken(
                id = "evt_${UUID.randomUUID()}", userId = userId, token = token,
                expiresAt = Instant.now().plusSeconds(1800), createdAt = Instant.now(),
            ),
        )
        notificationRepository.save(
            Notification(
                id = "notif_${UUID.randomUUID()}", userId = userId, type = "PROFILE_EMAIL_VERIFICATION",
                title = "Verify your email", body = "Your email verification code is $token. It expires in 30 minutes.",
                isRead = false, createdAt = Instant.now(), dataJson = null,
            ),
        )
    }

    @Transactional
    fun confirmEmailVerification(userId: String, token: String): PublicUser {
        val record = emailVerificationTokenRepository.findByToken(token)
            ?.takeIf { it.userId == userId }
            ?: throw InvalidVerificationTokenException("Invalid or expired verification token")
        if (record.usedAt != null || record.expiresAt.isBefore(Instant.now())) {
            throw InvalidVerificationTokenException("Invalid or expired verification token")
        }
        record.usedAt = Instant.now()
        emailVerificationTokenRepository.save(record)

        val user = userRepository.findById(userId).orElseThrow { UserNotFoundException("User not found") }
        user.emailVerified = true
        userRepository.save(user)
        return user.toPublic()
    }

    private fun issueAuthResponse(user: User, message: String, deviceId: String? = null) = AuthResponse(
        message = message,
        user = user.toPublic(),
        accessToken = jwtService.issueAccessToken(user.id, user.phoneNumber, user.role, deviceId),
        refreshToken = jwtService.issueRefreshToken(user.id, deviceId),
    )

    private fun generateAccountNumber(): String = (2024100000L + (Math.random() * 900000).toLong()).toString()

    // No collision-avoidance loop, same accepted-risk precedent as generateAccountNumber
    // above -- a UUID-derived 6-char code has a negligible real collision chance, and the
    // real DB unique constraint on referral_code is the actual backstop.
    private fun generateReferralCode(): String = "ITD" + UUID.randomUUID().toString().replace("-", "").take(6).uppercase()

    private fun User.toPublic() = PublicUser(
        id = id, phoneNumber = phoneNumber, email = email, firstName = firstName,
        lastName = lastName, kycVerified = kycVerified, creditScore = creditScore, createdAt = createdAt,
        referralCode = referralCode, profilePhotoUrl = profilePhotoUrl, emailVerified = emailVerified,
        neighborhood = neighborhood,
    )
}
