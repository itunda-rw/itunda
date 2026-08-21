package rw.itunda.auth

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jakarta.persistence.LockModeType
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.data.jpa.repository.Lock
import rw.itunda.core.domain.InterestJar
import rw.itunda.core.domain.Notification
import rw.itunda.core.domain.PhoneVerificationToken
import rw.itunda.core.domain.TermsAcceptance
import rw.itunda.core.domain.TermsCatalog
import rw.itunda.core.domain.User
import rw.itunda.core.domain.Account
import rw.itunda.core.domain.AccountType
import rw.itunda.core.geo.NominatimGeocodingClient
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.realtime.RealtimeMessagePublisher
import rw.itunda.core.repository.EmailVerificationTokenRepository
import rw.itunda.core.repository.InterestJarRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.PhoneVerificationTokenRepository
import rw.itunda.core.repository.TermsAcceptanceRepository
import rw.itunda.core.repository.UserRepository
import rw.itunda.core.repository.AccountRepository
import rw.itunda.core.account.AccountNumberGenerator
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Optional

/**
 * First test coverage for auth -- the module gating every other endpoint, and the one
 * with real, documented vulnerabilities fixed here before (SECURITY.md: login used to
 * accept any password for any phone number and issue a static, non-expiring mock
 * token). These tests exist specifically to catch a regression of those exact fixes,
 * not just generic coverage.
 *
 * JwtService is used for real (it's a small, pure, self-contained class -- no external
 * dependencies), same reasoning as AccountServiceTest's real QuoteStore: real signed/
 * verified tokens are more meaningful coverage than a mocked stand-in. Same for
 * BCryptPasswordEncoder, which AuthService instantiates internally and can't be mocked
 * anyway. UserRepository/AccountRepository (real DB) and TokenBlocklistService/RateLimiter
 * (real Redis) are mocked.
 */
class AuthServiceTest : BehaviorSpec({

    val testSecret = "test-secret-at-least-32-bytes-long-for-hs256!!"
    val passwordEncoder = BCryptPasswordEncoder()

    Given("a fresh AuthService") {
        val userRepository = mockk<UserRepository>()
        val accountRepository = mockk<AccountRepository>()
        val interestJarRepository = mockk<InterestJarRepository>()
        val jwtService = JwtService(testSecret)
        val tokenBlocklistService = mockk<TokenBlocklistService>()
        val rateLimiter = mockk<RateLimiter>()
        every { rateLimiter.checkLimit(any(), any(), any()) } returns Unit
        val emailVerificationTokenRepository = mockk<EmailVerificationTokenRepository>()
        every { emailVerificationTokenRepository.invalidateUnusedByUserId(any()) } returns 0
        val phoneVerificationTokenRepository = mockk<PhoneVerificationTokenRepository>()
        every { phoneVerificationTokenRepository.invalidateUnusedByUserId(any()) } returns 0
        val notificationRepository = mockk<NotificationRepository>()
        val nominatimGeocodingClient = mockk<NominatimGeocodingClient>()
        // Real device binding (2026-07-20) -- relaxed since these tests aren't about
        // device binding itself, just registration/login/profile behavior; DeviceServiceTest
        // covers the real device-recording/verification logic directly.
        val deviceService = mockk<DeviceService>(relaxed = true)
        // Real push (item 122) -- relaxed since these tests aren't about the push
        // pipeline itself, just registration/login/profile behavior.
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val realtimeMessagePublisher = mockk<RealtimeMessagePublisher>(relaxed = true)
        val accountNumberGenerator = mockk<AccountNumberGenerator>(relaxed = true)
        // Real Toss/Korean-fintech-style 약관 동의 (terms consent) -- relaxed since most
        // of these pre-existing tests aren't about terms consent itself, just
        // registration/login/profile behavior; the dedicated "terms consent" Given
        // block below covers the real enforcement logic directly. A default `save`
        // stub is still needed even though this mock is relaxed: `JpaRepository.save`
        // is a self-bounded generic (`fun <S : T> save(entity: S): S`), and mockk's
        // relaxed auto-answer can't safely synthesize a same-shape return value for
        // that signature -- a real `ClassCastException` during spec construction,
        // confirmed live catching this exact issue, not a guess.
        val termsAcceptanceRepository = mockk<TermsAcceptanceRepository>(relaxed = true)
        every { termsAcceptanceRepository.save(any()) } answers { firstArg() }
        // Split out of AuthService (2026-08-21) -- see UserVerificationService.kt's own
        // doc comment. Built from the same shared mocks this Given block already
        // declares, not a duplicated fixture -- register() below still exercises the
        // real cross-class call into this instance for its own phone-OTP delivery.
        val userVerificationService = UserVerificationService(
            userRepository, emailVerificationTokenRepository, phoneVerificationTokenRepository,
            notificationRepository, pushNotificationService, rateLimiter,
        )
        val service = AuthService(
            userRepository, accountRepository, interestJarRepository, jwtService, tokenBlocklistService, rateLimiter,
            nominatimGeocodingClient, deviceService, realtimeMessagePublisher, accountNumberGenerator,
            termsAcceptanceRepository, userVerificationService,
        )
        val requiredTermsIds = TermsCatalog.requiredIds().toList()

        When("registering a brand-new phone number") {
            every { rateLimiter.checkLimit(any(), any(), any()) } returns Unit
            every { userRepository.existsByPhoneNumber("+250788000001") } returns false
            every { userRepository.save(any()) } answers { firstArg() }
            every { accountRepository.save(any()) } answers { firstArg() }
            val jarSlot = mutableListOf<InterestJar>()
            every { interestJarRepository.save(capture(jarSlot)) } answers { firstArg() }
            val phoneTokenSlot = mutableListOf<PhoneVerificationToken>()
            every { phoneVerificationTokenRepository.save(capture(phoneTokenSlot)) } answers { firstArg() }
            val notificationSlot = mutableListOf<Notification>()
            every { notificationRepository.save(capture(notificationSlot)) } answers { firstArg() }
            val termsAcceptanceSlot = mutableListOf<TermsAcceptance>()
            every { termsAcceptanceRepository.save(capture(termsAcceptanceSlot)) } answers { firstArg() }

            val response = service.register(RegisterRequest("+250788000001", "a@b.rw", "Jean", "B", "password123", acceptedTermsIds = requiredTermsIds))

            Then("it rate-limits, creates a user, provisions real zero-balance MAIN, PAY, SAVINGS, and INVESTMENT accounts, and issues real tokens") {
                verify(exactly = 1) { rateLimiter.checkLimit("auth:register:+250788000001", 3, any()) }
                val accountSlot = mutableListOf<Account>()
                // Fixed 2026-07-13: registration used to only provision MAIN, so
                // POST /api/v1/savings/goals 404'd (ACCOUNT_NOT_FOUND) for every real user --
                // this asserts all real accounts exist, not just that *a* account got saved.
                // INVESTMENT added 2026-07-27: the same real gap, one layer deeper --
                // StocksService.buyStock required a real INVESTMENT account only the seeded
                // demo user ever had, so POST /api/v1/stocks/buy 404'd for every real user.
                // PAY added 2026-08-21: real Toss Bank/Toss Pay separation -- see
                // AccountType.PAY's own doc comment.
                verify(exactly = 4) { accountRepository.save(capture(accountSlot)) }
                accountSlot.map { it.type }.toSet() shouldBe setOf(AccountType.MAIN, AccountType.PAY, AccountType.SAVINGS, AccountType.INVESTMENT)
                accountSlot.all { it.balance.signum() == 0 } shouldBe true
                jwtService.verify(response.accessToken) shouldNotBe null
                jwtService.verify(response.refreshToken)!!.isRefresh shouldBe true

                // Fixed 2026-07-20: SeedDataRunner was the only place an InterestJar was ever
                // created, so GET /api/v1/savings/interest-jar 404'd for every real user.
                jarSlot.single().accountId shouldBe accountSlot.single { it.type == AccountType.SAVINGS }.id
                jarSlot.single().earnedThisMonth.signum() shouldBe 0
            }
            Then("it real-sends a 6-digit phone verification code via a real in-app notification, at registration itself") {
                phoneTokenSlot.single().token.startsWith("\$2") shouldBe true
                val notification = notificationSlot.single { it.type == "PHONE_VERIFICATION" }
                val deliveredCode = Regex("\\d{6}").find(notification.body)!!.value
                passwordEncoder.matches(deliveredCode, phoneTokenSlot.single().token) shouldBe true
            }
            Then("it real-records one immutable TermsAcceptance per real accepted required term") {
                termsAcceptanceSlot.map { it.termsId }.toSet() shouldBe requiredTermsIds.toSet()
                termsAcceptanceSlot.all { it.userId.isNotBlank() } shouldBe true
            }
        }

        When("registering with a valid referral code") {
            val referrer = User(
                id = "user_referrer", phoneNumber = "+250788000010", firstName = "Ref", lastName = "R",
                passwordHash = "unused", referralCode = "ITDREF01", createdAt = Instant.now(),
            )
            every { rateLimiter.checkLimit(any(), any(), any()) } returns Unit
            every { userRepository.existsByPhoneNumber("+250788000011") } returns false
            every { userRepository.findByReferralCode("ITDREF01") } returns referrer
            val userSlot = mutableListOf<User>()
            every { userRepository.save(capture(userSlot)) } answers { firstArg() }
            every { accountRepository.save(any()) } answers { firstArg() }
            every { interestJarRepository.save(any()) } answers { firstArg() }
            every { phoneVerificationTokenRepository.save(any()) } answers { firstArg() }
            every { notificationRepository.save(any()) } answers { firstArg() }

            service.register(RegisterRequest("+250788000011", null, "New", "User", "password123", "ITDREF01", acceptedTermsIds = requiredTermsIds))

            Then("the new user is saved with a real referredByUserId pointing at the referrer") {
                userSlot.single().referredByUserId shouldBe "user_referrer"
            }
        }

        When("registering with a referral code that doesn't belong to anyone") {
            every { rateLimiter.checkLimit(any(), any(), any()) } returns Unit
            every { userRepository.existsByPhoneNumber("+250788000012") } returns false
            every { userRepository.findByReferralCode("BOGUSCODE") } returns null

            Then("it throws ReferralCodeNotFoundException before ever saving a user -- an invalid code fails loudly, not silently") {
                try {
                    service.register(RegisterRequest("+250788000012", null, "New", "User", "password123", "BOGUSCODE", acceptedTermsIds = requiredTermsIds))
                    error("expected ReferralCodeNotFoundException")
                } catch (e: ReferralCodeNotFoundException) {
                    verify(exactly = 0) { userRepository.save(any()) }
                }
            }
        }

        When("registering a phone number that's already taken") {
            every { rateLimiter.checkLimit(any(), any(), any()) } returns Unit
            every { userRepository.existsByPhoneNumber("+250788000002") } returns true

            Then("it throws PhoneAlreadyRegisteredException without touching the account repository") {
                try {
                    service.register(RegisterRequest("+250788000002", null, "Jean", "B", "password123"))
                    error("expected PhoneAlreadyRegisteredException")
                } catch (e: PhoneAlreadyRegisteredException) {
                    verify(exactly = 0) { accountRepository.save(any()) }
                }
            }
        }

        // Real Toss/Korean-fintech-style 약관 동의 (terms consent) enforcement -- see
        // TermsCatalog's own doc comment for the full sourced account (Korea's real
        // 2025-02-14 dark-pattern regulation and 2026-09-11 penalty increase). itunda
        // had zero terms-consent tracking anywhere before this.
        When("registering without accepting every required term") {
            every { rateLimiter.checkLimit(any(), any(), any()) } returns Unit
            every { userRepository.existsByPhoneNumber("+250788000020") } returns false

            Then("it throws RequiredTermsNotAcceptedException before ever saving a user") {
                // Only one of the two real required terms accepted -- a genuine
                // partial-consent case, not just "accepted nothing at all".
                val partialTermsIds = listOf(requiredTermsIds.first())
                try {
                    service.register(RegisterRequest("+250788000020", null, "Jean", "B", "password123", acceptedTermsIds = partialTermsIds))
                    error("expected RequiredTermsNotAcceptedException")
                } catch (e: RequiredTermsNotAcceptedException) {
                    verify(exactly = 0) { userRepository.save(any()) }
                    verify(exactly = 0) { accountRepository.save(any()) }
                }
            }
        }

        When("registering with every required term accepted but the real optional marketing term skipped") {
            every { rateLimiter.checkLimit(any(), any(), any()) } returns Unit
            every { userRepository.existsByPhoneNumber("+250788000021") } returns false
            every { userRepository.save(any()) } answers { firstArg() }
            every { accountRepository.save(any()) } answers { firstArg() }
            every { interestJarRepository.save(any()) } answers { firstArg() }
            every { phoneVerificationTokenRepository.save(any()) } answers { firstArg() }
            every { notificationRepository.save(any()) } answers { firstArg() }
            val termsAcceptanceSlot = mutableListOf<TermsAcceptance>()
            every { termsAcceptanceRepository.save(capture(termsAcceptanceSlot)) } answers { firstArg() }

            Then("registration succeeds -- an optional term is honestly optional, never a blocker") {
                service.register(RegisterRequest("+250788000021", null, "Jean", "B", "password123", acceptedTermsIds = requiredTermsIds))
                termsAcceptanceSlot.map { it.termsId }.toSet() shouldBe requiredTermsIds.toSet()
                (TermsCatalog.requiredIds() - termsAcceptanceSlot.map { it.termsId }.toSet()).isEmpty() shouldBe true
            }
        }

        When("registering with every required term plus the real optional marketing term also accepted") {
            every { rateLimiter.checkLimit(any(), any(), any()) } returns Unit
            every { userRepository.existsByPhoneNumber("+250788000022") } returns false
            every { userRepository.save(any()) } answers { firstArg() }
            every { accountRepository.save(any()) } answers { firstArg() }
            every { interestJarRepository.save(any()) } answers { firstArg() }
            every { phoneVerificationTokenRepository.save(any()) } answers { firstArg() }
            every { notificationRepository.save(any()) } answers { firstArg() }
            val termsAcceptanceSlot = mutableListOf<TermsAcceptance>()
            every { termsAcceptanceRepository.save(capture(termsAcceptanceSlot)) } answers { firstArg() }

            Then("it records a real TermsAcceptance for the optional term too, not just the required ones") {
                val allTermsIds = TermsCatalog.documents.map { it.id }
                service.register(RegisterRequest("+250788000022", null, "Jean", "B", "password123", acceptedTermsIds = allTermsIds))
                termsAcceptanceSlot.map { it.termsId }.toSet() shouldBe allTermsIds.toSet()
            }
        }

        When("registering with an unknown/stale terms id mixed in with the real required ones") {
            every { rateLimiter.checkLimit(any(), any(), any()) } returns Unit
            every { userRepository.existsByPhoneNumber("+250788000023") } returns false
            every { userRepository.save(any()) } answers { firstArg() }
            every { accountRepository.save(any()) } answers { firstArg() }
            every { interestJarRepository.save(any()) } answers { firstArg() }
            every { phoneVerificationTokenRepository.save(any()) } answers { firstArg() }
            every { notificationRepository.save(any()) } answers { firstArg() }
            val termsAcceptanceSlot = mutableListOf<TermsAcceptance>()
            every { termsAcceptanceRepository.save(capture(termsAcceptanceSlot)) } answers { firstArg() }

            Then("it silently ignores the unknown id rather than 500ing registration over it") {
                service.register(RegisterRequest("+250788000023", null, "Jean", "B", "password123", acceptedTermsIds = requiredTermsIds + "some_removed_terms_id"))
                termsAcceptanceSlot.map { it.termsId }.toSet() shouldBe requiredTermsIds.toSet()
            }
        }

        When("registering while rate-limited") {
            every { rateLimiter.checkLimit("auth:register:+250788000003", 3, any()) } throws
                RateLimitExceededException("Too many attempts")

            Then("it throws before ever checking whether the phone is taken") {
                try {
                    service.register(RegisterRequest("+250788000003", null, "Jean", "B", "password123"))
                    error("expected RateLimitExceededException")
                } catch (e: RateLimitExceededException) {
                    verify(exactly = 0) { userRepository.existsByPhoneNumber(any()) }
                }
            }
        }

        When("logging in with the correct password") {
            val user = User(
                id = "user_1", phoneNumber = "+250788000004", firstName = "Jean", lastName = "B",
                passwordHash = passwordEncoder.encode("correct-password"), createdAt = Instant.now(),
            )
            every { rateLimiter.checkLimit(any(), any(), any()) } returns Unit
            every { userRepository.findByPhoneNumber("+250788000004") } returns user

            val response = service.login(LoginRequest("+250788000004", "correct-password"))

            Then("it succeeds and issues a real token for that user") {
                jwtService.verify(response.accessToken)!!.userId shouldBe "user_1"
            }
        }

        When("logging in with the wrong password") {
            val user = User(
                id = "user_2", phoneNumber = "+250788000005", firstName = "Jean", lastName = "B",
                passwordHash = passwordEncoder.encode("correct-password"), createdAt = Instant.now(),
            )
            every { rateLimiter.checkLimit(any(), any(), any()) } returns Unit
            every { userRepository.findByPhoneNumber("+250788000005") } returns user

            Then("it throws InvalidCredentialsException -- this is the exact bug SECURITY.md documented fixing (login used to accept any password)") {
                try {
                    service.login(LoginRequest("+250788000005", "totally-wrong-password"))
                    error("expected InvalidCredentialsException")
                } catch (e: InvalidCredentialsException) {
                    // expected
                }
            }
        }

        When("logging in with a phone number that has no account") {
            every { rateLimiter.checkLimit(any(), any(), any()) } returns Unit
            every { userRepository.findByPhoneNumber("+250788999999") } returns null

            Then("it throws InvalidCredentialsException, not a null-pointer or a different error that would leak which phones are registered") {
                try {
                    service.login(LoginRequest("+250788999999", "anything"))
                    error("expected InvalidCredentialsException")
                } catch (e: InvalidCredentialsException) {
                    // expected
                }
            }
        }

        When("refreshing with a token that was already used once (blacklisted)") {
            val refreshToken = jwtService.issueRefreshToken("user_3")
            val decoded = jwtService.verify(refreshToken)!!
            every { tokenBlocklistService.isBlacklisted(decoded.jti) } returns true

            Then("it throws InvalidRefreshTokenException rather than silently minting a new token pair") {
                try {
                    service.refresh(refreshToken)
                    error("expected InvalidRefreshTokenException")
                } catch (e: InvalidRefreshTokenException) {
                    // expected
                }
            }
        }

        When("refreshing with a real access token instead of a refresh token") {
            val accessToken = jwtService.issueAccessToken("user_4", "+250788000006", "USER")

            Then("it throws InvalidRefreshTokenException -- an access token must not double as a refresh token") {
                try {
                    service.refresh(accessToken)
                    error("expected InvalidRefreshTokenException")
                } catch (e: InvalidRefreshTokenException) {
                    // expected
                }
            }
        }

        When("updating a real profile photo URL") {
            val user = User(
                id = "user_6", phoneNumber = "+250788000008", firstName = "Jean", lastName = "B",
                passwordHash = "unused", createdAt = Instant.now(),
            )
            every { userRepository.findById("user_6") } returns Optional.of(user)
            every { userRepository.save(any()) } answers { firstArg() }

            val result = service.updateProfilePhoto("user_6", "https://cdn.itunda.rw/avatars/user_6.jpg")

            Then("it saves the real URL and returns it on the public profile") {
                result.profilePhotoUrl shouldBe "https://cdn.itunda.rw/avatars/user_6.jpg"
            }
        }

        When("updating a profile photo URL longer than the real 512-char DB column bound") {
            val user = User(
                id = "user_6b", phoneNumber = "+250788000018", firstName = "Jean", lastName = "B",
                passwordHash = "unused", createdAt = Instant.now(),
            )
            every { userRepository.findById("user_6b") } returns Optional.of(user)

            Then("it throws InvalidProfilePhotoUrlException rather than risking a raw DB insert failure") {
                try {
                    service.updateProfilePhoto("user_6b", "https://cdn.itunda.rw/" + "x".repeat(500))
                    error("expected InvalidProfilePhotoUrlException")
                } catch (e: InvalidProfilePhotoUrlException) {
                    // expected
                }
            }
        }

        // Email/phone OTP verification coverage (request/confirm, both flows) now lives
        // in UserVerificationServiceTest.kt, following UserVerificationService.kt's own
        // extraction out of AuthService.kt.

        When("refreshing with a valid, not-yet-used refresh token") {
            val user = User(
                id = "user_5", phoneNumber = "+250788000007", firstName = "Jean", lastName = "B",
                passwordHash = "unused", createdAt = Instant.now(),
            )
            val refreshToken = jwtService.issueRefreshToken("user_5")
            val decoded = jwtService.verify(refreshToken)!!
            every { tokenBlocklistService.isBlacklisted(decoded.jti) } returns false
            every { userRepository.findById("user_5") } returns Optional.of(user)
            every { tokenBlocklistService.blacklist(any(), any()) } returns Unit

            val response = service.refresh(refreshToken)

            Then("it rotates: the old token gets blacklisted and a brand-new pair is issued") {
                verify(exactly = 1) { tokenBlocklistService.blacklist(decoded.jti, decoded.expiresAt) }
                jwtService.verify(response.accessToken)!!.userId shouldBe "user_5"
                response.refreshToken shouldNotBe refreshToken
            }
        }

        When("logging out with a valid access token") {
            val accessToken = jwtService.issueAccessToken("user_77", "+250788000077", "USER")
            val refreshToken = jwtService.issueRefreshToken("user_77")
            val decodedAccess = jwtService.verify(accessToken)!!
            val decodedRefresh = jwtService.verify(refreshToken)!!
            every { tokenBlocklistService.blacklist(any(), any()) } returns Unit

            service.logout(accessToken, refreshToken)

            Then("it revokes the credentials and closes only this access token's live sockets") {
                verify { tokenBlocklistService.blacklist(decodedAccess.jti, decodedAccess.expiresAt) }
                verify { tokenBlocklistService.blacklist(decodedRefresh.jti, decodedRefresh.expiresAt) }
                verify(exactly = 1) { realtimeMessagePublisher.closeSessionsForToken("user_77", decodedAccess.jti) }
            }
        }

        When("setting a real neighborhood from a real coordinate that reverse-geocodes successfully") {
            val user = User(id = "user_6", phoneNumber = "+250788000008", firstName = "A", lastName = "B", passwordHash = "x")
            every { rateLimiter.checkLimit("auth:neighborhood:user_6", limit = 10, window = Duration.ofHours(1)) } returns Unit
            every { userRepository.findById("user_6") } returns Optional.of(user)
            every { nominatimGeocodingClient.reverseGeocode(-1.9536, 30.0605) } returns "Nyarugenge"
            every { userRepository.save(any()) } answers { firstArg() }

            val result = service.setNeighborhood("user_6", -1.9536, 30.0605)

            Then("the real neighborhood is persisted and returned") {
                result.neighborhood shouldBe "Nyarugenge"
            }
        }

        When("setting a neighborhood but reverse geocoding can't resolve one") {
            every { rateLimiter.checkLimit("auth:neighborhood:user_6", limit = 10, window = Duration.ofHours(1)) } returns Unit
            every { userRepository.findById("user_6") } returns Optional.of(
                User(id = "user_6", phoneNumber = "+250788000008", firstName = "A", lastName = "B", passwordHash = "x"),
            )
            every { nominatimGeocodingClient.reverseGeocode(0.0, 0.0) } returns null

            Then("it throws NeighborhoodNotResolvedException -- an honest failure, not a silent no-op") {
                try {
                    service.setNeighborhood("user_6", 0.0, 0.0)
                    error("expected NeighborhoodNotResolvedException")
                } catch (e: NeighborhoodNotResolvedException) {
                    // expected
                }
            }
        }

        When("setting a neighborhood with an invalid coordinate") {
            Then("it throws InvalidCoordinatesException before ever touching the real rate limiter or geocoder") {
                try {
                    service.setNeighborhood("user_6", 200.0, 30.0)
                    error("expected InvalidCoordinatesException")
                } catch (e: InvalidCoordinatesException) {
                    verify(exactly = 0) { rateLimiter.checkLimit(any(), any(), any()) }
                }
            }
        }

        When("a real user exceeds the real neighborhood-setting rate limit") {
            every {
                rateLimiter.checkLimit("auth:neighborhood:user_6", limit = 10, window = Duration.ofHours(1))
            } throws RateLimitExceededException("Too many requests")

            Then("it real-propagates RateLimitExceededException, found missing in a 2026-07-20 security sweep") {
                try {
                    service.setNeighborhood("user_6", -1.9536, 30.0605)
                    error("expected RateLimitExceededException")
                } catch (e: RateLimitExceededException) {
                    verify(exactly = 0) { nominatimGeocodingClient.reverseGeocode(any(), any()) }
                }
            }
        }

        When("setting a real, plausible past birth date") {
            val user = User(id = "user_7", phoneNumber = "+250788000009", firstName = "A", lastName = "B", passwordHash = "x")
            every { userRepository.findById("user_7") } returns Optional.of(user)
            every { userRepository.save(any()) } answers { firstArg() }

            val result = service.setBirthDate("user_7", LocalDate.of(2015, 6, 1))

            Then("the real birth date is persisted and returned") {
                result.birthDate shouldBe LocalDate.of(2015, 6, 1)
            }
        }

        When("setting a birth date that's today or in the future") {
            Then("it throws InvalidBirthDateException before ever touching the repository") {
                try {
                    service.setBirthDate("user_7", LocalDate.now(ZoneOffset.UTC).plusDays(1))
                    error("expected InvalidBirthDateException")
                } catch (e: InvalidBirthDateException) {
                    verify(exactly = 0) { userRepository.save(any()) }
                }
            }
        }

        When("setting an implausibly old birth date") {
            Then("it throws InvalidBirthDateException") {
                try {
                    service.setBirthDate("user_7", LocalDate.now(ZoneOffset.UTC).minusYears(121))
                    error("expected InvalidBirthDateException")
                } catch (e: InvalidBirthDateException) {
                    verify(exactly = 0) { userRepository.save(any()) }
                }
            }
        }
    }

    Given("the verification-token repositories") {
        Then("both active-token lookups are pessimistically locked for one-time consumption") {
            EmailVerificationTokenRepository::class.java
                .getMethod("findFirstByUserIdAndUsedAtIsNullOrderByCreatedAtDesc", String::class.java)
                .getAnnotation(Lock::class.java).value shouldBe LockModeType.PESSIMISTIC_WRITE
            PhoneVerificationTokenRepository::class.java
                .getMethod("findFirstByUserIdAndUsedAtIsNullOrderByCreatedAtDesc", String::class.java)
                .getAnnotation(Lock::class.java).value shouldBe LockModeType.PESSIMISTIC_WRITE
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
