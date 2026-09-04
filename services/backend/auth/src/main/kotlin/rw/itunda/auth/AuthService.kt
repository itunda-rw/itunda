package rw.itunda.auth

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.core.domain.InterestJar
import rw.itunda.core.domain.TermsAcceptance
import rw.itunda.core.domain.TermsCatalog
import rw.itunda.core.domain.User
import rw.itunda.core.domain.Account
import rw.itunda.core.domain.AccountType
import rw.itunda.core.geo.GeoUtils
import rw.itunda.core.geo.NominatimGeocodingClient
import rw.itunda.core.realtime.RealtimeMessagePublisher
import rw.itunda.core.repository.InterestJarRepository
import rw.itunda.core.repository.TermsAcceptanceRepository
import rw.itunda.core.repository.UserRepository
import rw.itunda.core.repository.AccountRepository
import rw.itunda.core.account.AccountNumberGenerator
import rw.itunda.core.validation.isValidEmail
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

// Real Toss-sourced 6-digit numeric PIN (support.toss.im's own real help-center
// articles: "6자리 비밀번호") -- see AuthService.register's own doc comment.
private val PIN_PATTERN = Regex("^\\d{6}$")

/**
 * Port of backend/src/controllers/auth.controller.ts's login/register, with one fix
 * applied from day one instead of ported as a known gap: registration provisions a real
 * MAIN account (zero balance) for the new user. The Express backend's SECURITY.md lists
 * "new accounts have no account of their own" as its #1 open remediation item — since this
 * is a fresh implementation, there's no reason to carry that gap forward.
 */
@Service
class AuthService(
    private val userRepository: UserRepository,
    private val accountRepository: AccountRepository,
    private val interestJarRepository: InterestJarRepository,
    private val jwtService: JwtService,
    private val tokenBlocklistService: TokenBlocklistService,
    private val rateLimiter: RateLimiter,
    private val nominatimGeocodingClient: NominatimGeocodingClient,
    private val deviceService: DeviceService,
    private val realtimeMessagePublisher: RealtimeMessagePublisher,
    private val accountNumberGenerator: AccountNumberGenerator,
    private val termsAcceptanceRepository: TermsAcceptanceRepository,
    private val userVerificationService: UserVerificationService,
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
        // Real Toss/Korean-fintech-style 약관 동의 (terms consent) enforcement -- see
        // TermsCatalog's own doc comment for the full sourced account. Checked before
        // any real write below (fail fast, same discipline the phone-uniqueness check
        // right above already follows) -- a client that skips a required checkbox
        // never gets a real account or a real account provisioned for it.
        val acceptedTermsIds = request.acceptedTermsIds.toSet()
        val missingRequiredTermsIds = TermsCatalog.requiredIds() - acceptedTermsIds
        if (missingRequiredTermsIds.isNotEmpty()) {
            throw RequiredTermsNotAcceptedException("Please agree to all required terms to continue")
        }
        // Real Toss-sourced passwordless-login rollout (2026-08-24, direct user
        // follow-up "we need that simplification" after real sourced research into
        // exactly how Toss's own flow works): registration is phone+OTP, then a real
        // 6-digit numeric PIN -- not a free-form password. Real Toss's own actual term
        // is literally "6자리 비밀번호" (6-digit password), confirmed via
        // support.toss.im's own real help-center articles.
        if (!PIN_PATTERN.matches(request.password)) {
            throw InvalidPinException("Your PIN must be exactly 6 digits")
        }
        // Real gap found 2026-09-05: email is optional, but an email that IS provided
        // was never checked for even being shaped like one -- see EmailValidation.kt's
        // own doc comment for the full account.
        if (request.email != null && !isValidEmail(request.email)) {
            throw InvalidEmailException("Please enter a valid email address")
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
            pinSet = true,
        )
        userRepository.save(user)
        // Real immutable consent audit trail -- one row per real accepted term
        // (required AND any optional ones the client actually sent), only ever for
        // ids TermsCatalog itself recognizes (an unknown id is silently ignored here
        // rather than 500ing registration over a client sending a stale/removed id).
        val validAcceptedTermsIds = acceptedTermsIds.intersect(TermsCatalog.validIds())
        val termsById = TermsCatalog.documents.associateBy { it.id }
        validAcceptedTermsIds.forEach { termsId ->
            val document = termsById.getValue(termsId)
            termsAcceptanceRepository.save(
                TermsAcceptance(
                    id = "terms_acceptance_${UUID.randomUUID()}",
                    userId = user.id,
                    termsId = document.id,
                    termsVersion = document.version,
                ),
            )
        }
        // Real phone verification, sent at registration itself (2026-07-26) -- see
        // requestPhoneVerification's own doc comment for the full delivery story.
        userVerificationService.sendPhoneVerificationCode(user.id)

        accountRepository.save(
            Account(
                id = "account_${UUID.randomUUID()}",
                userId = user.id,
                accountNumber = accountNumberGenerator.generate(2024100000L),
                accountName = "${user.firstName}'s Main Account",
                type = AccountType.MAIN,
                balance = BigDecimal.ZERO,
                availableBalance = BigDecimal.ZERO,
            ),
        )
        // Real Toss Bank/Toss Pay separation -- see AccountType.PAY's own doc comment.
        // Provisioned unconditionally at registration, same as MAIN/SAVINGS below --
        // every real user has an itunda Pay money balance from day one, starting at
        // zero and auto-funded from MAIN (or an external linked account) the first
        // time it's actually needed (MerchantService.collect).
        accountRepository.save(
            Account(
                id = "account_${UUID.randomUUID()}",
                userId = user.id,
                accountNumber = accountNumberGenerator.generate(2024100000L),
                accountName = "${user.firstName}'s itunda Pay Money",
                type = AccountType.PAY,
                balance = BigDecimal.ZERO,
                availableBalance = BigDecimal.ZERO,
            ),
        )
        // Fixed 2026-07-13, found live: SavingsService.createGoal requires a real
        // AccountType.SAVINGS account and only MAIN was ever provisioned here, so
        // POST /api/v1/savings/goals 404'd (ACCOUNT_NOT_FOUND) for every real user.
        val savingsAccount = accountRepository.save(
            Account(
                id = "account_${UUID.randomUUID()}",
                userId = user.id,
                accountNumber = accountNumberGenerator.generate(2024100000L),
                accountName = "${user.firstName}'s Savings Account",
                type = AccountType.SAVINGS,
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
                accountId = savingsAccount.id,
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
        // StocksService.buyStock requires a real AccountType.INVESTMENT account and only
        // the seeded demo user (SeedDataRunner) ever got one -- POST /api/v1/stocks/buy
        // 404'd (ACCOUNT_NOT_FOUND, via NoAccountException) for every real registered
        // user, meaning the entire real Toss Securities/Kakao Pay Securities-style
        // stock-buying feature was silently unusable outside the demo account.
        accountRepository.save(
            Account(
                id = "account_${UUID.randomUUID()}",
                userId = user.id,
                accountNumber = accountNumberGenerator.generate(2024100000L),
                accountName = "${user.firstName}'s Investment Account",
                type = AccountType.INVESTMENT,
                balance = BigDecimal.ZERO,
                availableBalance = BigDecimal.ZERO,
            ),
        )

        // Real device binding (2026-07-20) -- the device used to register already
        // proved password ownership in this same request, so it's auto-trusted rather
        // than needing a separate step-up immediately after signing up.
        deviceService.recordRegistrationDevice(user.id, request.deviceId, request.deviceName)
        // Real passwordless-login rollout (2026-08-24) -- see DeviceService.
        // registerKeyDuringAuth's own doc comment: folds real device-key registration
        // into this same request when the client supplies one, so the very next app
        // open can use biometric/PIN-pad against this device instead of the full PIN.
        request.devicePublicKey?.let { deviceService.registerKeyDuringAuth(user.id, request.deviceId, it) }
        return issueAuthResponse(user, "Registration successful", request.deviceId)
    }

    // Real unified phone-first entry (2026-08-13) -- see PhoneCheckRequest's own doc
    // comment. Rate-limited by phoneNumber like login/register above: this is a real
    // account-existence oracle (an attacker could probe numbers to learn who has an
    // itunda account), the same accepted trade-off real Toss/Kakao/WhatsApp all make
    // for this exact UX -- a phone number isn't a secret the way a password is, and
    // limiting attempts keeps bulk enumeration expensive without blocking the one
    // real check a genuine user needs. Reuses existsByPhoneNumber, the same repository
    // method register() above already calls for its own duplicate-account guard.
    fun checkPhoneExists(phoneNumber: String): PhoneCheckResponse {
        rateLimiter.checkLimit("auth:check-phone:$phoneNumber", limit = 10, window = Duration.ofMinutes(1))
        return PhoneCheckResponse(exists = userRepository.existsByPhoneNumber(phoneNumber))
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
        // Real passwordless-login rollout (2026-08-24) -- see DeviceService.
        // registerKeyDuringAuth's own doc comment. Covers both a pre-existing user
        // establishing a key for the first time and a device re-establishing one
        // (e.g. after being revoked) -- either way the caller just proved PIN/password
        // ownership in this exact request.
        request.devicePublicKey?.let { deviceService.registerKeyDuringAuth(user.id, request.deviceId, it) }
        return issueAuthResponse(user, "Login successful", request.deviceId)
    }

    // Real passwordless LOGIN (2026-08-24) -- see DeviceService.verifyLoginSignature's
    // own doc comment for why this is a distinct method from the existing JWT-gated
    // device-verify endpoints. This IS the "no PIN needed on a recognized device"
    // outcome Toss's own real flow is built on -- a valid signature issues a real,
    // fresh session exactly like login()/register() do, no password/PIN involved.
    fun loginWithDeviceSignature(request: LoginWithSignatureRequest): AuthResponse {
        val user = deviceService.verifyLoginSignature(request.phoneNumber, request.deviceId, request.signature)
        return issueAuthResponse(user, "Login successful", request.deviceId)
    }

    // Real Toss-sourced "set your 6-digit PIN" flow (2026-08-24) -- used both by a
    // pre-PIN-era user upgrading (currentCredential = their existing, any-shape
    // password) and by a real "forgot PIN" reset (currentCredential = a fresh
    // credential re-established via phone OTP re-verification -- itself a separate,
    // already-real UserVerificationService flow, not duplicated here). Either way,
    // the exact same passwordEncoder.matches proof-of-ownership login() already
    // requires, reused rather than re-implemented.
    @Transactional
    fun setPin(userId: String, request: SetPinRequest): PublicUser {
        if (!PIN_PATTERN.matches(request.newPin)) {
            throw InvalidPinException("Your PIN must be exactly 6 digits")
        }
        val user = userRepository.findById(userId).orElseThrow { UserNotFoundException("User not found") }
        if (!passwordEncoder.matches(request.currentCredential, user.passwordHash)) {
            throw InvalidCredentialsException("Incorrect current password or PIN")
        }
        user.passwordHash = passwordEncoder.encode(request.newPin)
        user.pinSet = true
        userRepository.save(user)
        return user.toPublic()
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

    // Real age-eligibility gate for the Youth account (2026-07-28) -- see
    // YouthAccountService's own doc comment for the sourced 만 7세~18세 real eligibility
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
}

// Top-level + internal (not a private AuthService member) so UserVerificationService's
// own confirmEmailVerification/confirmPhoneVerification -- and any other same-module
// caller -- can build the same real PublicUser shape without duplicating this mapping.
internal fun User.toPublic() = PublicUser(
    id = id, phoneNumber = phoneNumber, email = email, firstName = firstName,
    lastName = lastName, kycVerified = kycVerified, creditScore = creditScore, createdAt = createdAt,
    referralCode = referralCode, profilePhotoUrl = profilePhotoUrl, emailVerified = emailVerified,
    phoneVerified = phoneVerified,
    neighborhood = neighborhood, neighborhoodVerifiedAt = neighborhoodVerifiedAt,
    neighborhoodVerificationCount = neighborhoodVerificationCount,
    secondNeighborhood = secondNeighborhood,
    birthDate = birthDate,
    pinSet = pinSet,
)
