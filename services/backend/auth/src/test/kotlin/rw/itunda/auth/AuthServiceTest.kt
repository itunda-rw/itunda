package rw.itunda.auth

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import rw.itunda.core.domain.EmailVerificationToken
import rw.itunda.core.domain.Notification
import rw.itunda.core.domain.User
import rw.itunda.core.domain.Wallet
import rw.itunda.core.domain.WalletType
import rw.itunda.core.geo.NominatimGeocodingClient
import rw.itunda.core.repository.EmailVerificationTokenRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.UserRepository
import rw.itunda.core.repository.WalletRepository
import java.time.Duration
import java.time.Instant
import java.util.Optional

/**
 * First test coverage for auth -- the module gating every other endpoint, and the one
 * with real, documented vulnerabilities fixed here before (SECURITY.md: login used to
 * accept any password for any phone number and issue a static, non-expiring mock
 * token). These tests exist specifically to catch a regression of those exact fixes,
 * not just generic coverage.
 *
 * JwtService is used for real (it's a small, pure, self-contained class -- no external
 * dependencies), same reasoning as WalletServiceTest's real QuoteStore: real signed/
 * verified tokens are more meaningful coverage than a mocked stand-in. Same for
 * BCryptPasswordEncoder, which AuthService instantiates internally and can't be mocked
 * anyway. UserRepository/WalletRepository (real DB) and TokenBlocklistService/RateLimiter
 * (real Redis) are mocked.
 */
class AuthServiceTest : BehaviorSpec({

    val testSecret = "test-secret-at-least-32-bytes-long-for-hs256!!"
    val passwordEncoder = BCryptPasswordEncoder()

    Given("a fresh AuthService") {
        val userRepository = mockk<UserRepository>()
        val walletRepository = mockk<WalletRepository>()
        val jwtService = JwtService(testSecret)
        val tokenBlocklistService = mockk<TokenBlocklistService>()
        val rateLimiter = mockk<RateLimiter>()
        val emailVerificationTokenRepository = mockk<EmailVerificationTokenRepository>()
        val notificationRepository = mockk<NotificationRepository>()
        val nominatimGeocodingClient = mockk<NominatimGeocodingClient>()
        // Real device binding (2026-07-20) -- relaxed since these tests aren't about
        // device binding itself, just registration/login/profile behavior; DeviceServiceTest
        // covers the real device-recording/verification logic directly.
        val deviceService = mockk<DeviceService>(relaxed = true)
        val service = AuthService(
            userRepository, walletRepository, jwtService, tokenBlocklistService, rateLimiter,
            emailVerificationTokenRepository, notificationRepository, nominatimGeocodingClient, deviceService,
        )

        When("registering a brand-new phone number") {
            every { rateLimiter.checkLimit(any(), any(), any()) } returns Unit
            every { userRepository.existsByPhoneNumber("+250788000001") } returns false
            every { userRepository.save(any()) } answers { firstArg() }
            every { walletRepository.save(any()) } answers { firstArg() }

            val response = service.register(RegisterRequest("+250788000001", "a@b.rw", "Jean", "B", "password123"))

            Then("it rate-limits, creates a user, provisions real zero-balance MAIN and SAVINGS wallets, and issues real tokens") {
                verify(exactly = 1) { rateLimiter.checkLimit("auth:register:+250788000001", 3, any()) }
                val walletSlot = mutableListOf<Wallet>()
                // Fixed 2026-07-13: registration used to only provision MAIN, so
                // POST /api/v1/savings/goals 404'd (WALLET_NOT_FOUND) for every real user --
                // this asserts both wallets exist, not just that *a* wallet got saved.
                verify(exactly = 2) { walletRepository.save(capture(walletSlot)) }
                walletSlot.map { it.type }.toSet() shouldBe setOf(WalletType.MAIN, WalletType.SAVINGS)
                walletSlot.all { it.balance.signum() == 0 } shouldBe true
                jwtService.verify(response.accessToken) shouldNotBe null
                jwtService.verify(response.refreshToken)!!.isRefresh shouldBe true
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
            every { walletRepository.save(any()) } answers { firstArg() }

            service.register(RegisterRequest("+250788000011", null, "New", "User", "password123", "ITDREF01"))

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
                    service.register(RegisterRequest("+250788000012", null, "New", "User", "password123", "BOGUSCODE"))
                    error("expected ReferralCodeNotFoundException")
                } catch (e: ReferralCodeNotFoundException) {
                    verify(exactly = 0) { userRepository.save(any()) }
                }
            }
        }

        When("registering a phone number that's already taken") {
            every { rateLimiter.checkLimit(any(), any(), any()) } returns Unit
            every { userRepository.existsByPhoneNumber("+250788000002") } returns true

            Then("it throws PhoneAlreadyRegisteredException without touching the wallet repository") {
                try {
                    service.register(RegisterRequest("+250788000002", null, "Jean", "B", "password123"))
                    error("expected PhoneAlreadyRegisteredException")
                } catch (e: PhoneAlreadyRegisteredException) {
                    verify(exactly = 0) { walletRepository.save(any()) }
                }
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

        When("requesting email verification with no email on file") {
            val user = User(
                id = "user_7", phoneNumber = "+250788000009", firstName = "Jean", lastName = "B",
                passwordHash = "unused", email = null, createdAt = Instant.now(),
            )
            every { userRepository.findById("user_7") } returns Optional.of(user)

            Then("it throws NoEmailOnFileException before creating any token or notification") {
                try {
                    service.requestEmailVerification("user_7")
                    error("expected NoEmailOnFileException")
                } catch (e: NoEmailOnFileException) {
                    verify(exactly = 0) { emailVerificationTokenRepository.save(any()) }
                    verify(exactly = 0) { notificationRepository.save(any()) }
                }
            }
        }

        When("requesting email verification when it's already verified") {
            val user = User(
                id = "user_8", phoneNumber = "+250788000013", firstName = "Jean", lastName = "B",
                passwordHash = "unused", email = "jean@itunda.rw", emailVerified = true, createdAt = Instant.now(),
            )
            every { userRepository.findById("user_8") } returns Optional.of(user)

            Then("it throws EmailAlreadyVerifiedException") {
                try {
                    service.requestEmailVerification("user_8")
                    error("expected EmailAlreadyVerifiedException")
                } catch (e: EmailAlreadyVerifiedException) {
                    verify(exactly = 0) { emailVerificationTokenRepository.save(any()) }
                }
            }
        }

        When("requesting email verification with a real, unverified email on file") {
            val user = User(
                id = "user_9", phoneNumber = "+250788000014", firstName = "Jean", lastName = "B",
                passwordHash = "unused", email = "jean@itunda.rw", emailVerified = false, createdAt = Instant.now(),
            )
            every { userRepository.findById("user_9") } returns Optional.of(user)
            val tokenSlot = mutableListOf<EmailVerificationToken>()
            every { emailVerificationTokenRepository.save(capture(tokenSlot)) } answers { firstArg() }
            val notificationSlot = mutableListOf<Notification>()
            every { notificationRepository.save(capture(notificationSlot)) } answers { firstArg() }

            service.requestEmailVerification("user_9")

            Then("it saves a real single-use token and delivers it via a real in-app notification, never in this call's own return value") {
                tokenSlot.single().userId shouldBe "user_9"
                tokenSlot.single().usedAt shouldBe null
                val notification = notificationSlot.single()
                notification.userId shouldBe "user_9"
                notification.type shouldBe "PROFILE_EMAIL_VERIFICATION"
                notification.body.contains(tokenSlot.single().token) shouldBe true
            }
        }

        When("confirming email verification with the real token just issued") {
            val user = User(
                id = "user_12", phoneNumber = "+250788000015", firstName = "Jean", lastName = "B",
                passwordHash = "unused", email = "jean12@itunda.rw", emailVerified = false, createdAt = Instant.now(),
            )
            val tokenRecord = EmailVerificationToken(
                id = "evt_1", userId = "user_12", token = "realtoken123",
                expiresAt = Instant.now().plusSeconds(1800),
            )
            every { emailVerificationTokenRepository.findByToken("realtoken123") } returns tokenRecord
            every { emailVerificationTokenRepository.save(any()) } answers { firstArg() }
            every { userRepository.findById("user_12") } returns Optional.of(user)
            every { userRepository.save(any()) } answers { firstArg() }

            val result = service.confirmEmailVerification("user_12", "realtoken123")

            Then("it flips emailVerified to true and marks the token used, once") {
                result.emailVerified shouldBe true
                tokenRecord.usedAt shouldNotBe null
                verify(exactly = 1) { emailVerificationTokenRepository.save(any()) }
            }
        }

        When("confirming email verification with a token that belongs to a different user") {
            val tokenRecord = EmailVerificationToken(
                id = "evt_2", userId = "user_other", token = "stolentoken",
                expiresAt = Instant.now().plusSeconds(1800),
            )
            every { emailVerificationTokenRepository.findByToken("stolentoken") } returns tokenRecord

            Then("it throws InvalidVerificationTokenException -- ownership is checked, not just token validity") {
                try {
                    service.confirmEmailVerification("user_13", "stolentoken")
                    error("expected InvalidVerificationTokenException")
                } catch (e: InvalidVerificationTokenException) {
                    verify(exactly = 0) { userRepository.save(any()) }
                }
            }
        }

        When("confirming email verification with an expired token") {
            val tokenRecord = EmailVerificationToken(
                id = "evt_3", userId = "user_14", token = "expiredtoken",
                expiresAt = Instant.now().minusSeconds(60),
            )
            every { emailVerificationTokenRepository.findByToken("expiredtoken") } returns tokenRecord

            Then("it throws InvalidVerificationTokenException -- a stale token can't verify an email") {
                try {
                    service.confirmEmailVerification("user_14", "expiredtoken")
                    error("expected InvalidVerificationTokenException")
                } catch (e: InvalidVerificationTokenException) {
                    verify(exactly = 0) { userRepository.save(any()) }
                }
            }
        }

        When("confirming email verification with an already-used token") {
            val tokenRecord = EmailVerificationToken(
                id = "evt_4", userId = "user_15", token = "usedtoken",
                expiresAt = Instant.now().plusSeconds(1800), usedAt = Instant.now().minusSeconds(60),
            )
            every { emailVerificationTokenRepository.findByToken("usedtoken") } returns tokenRecord

            Then("it throws InvalidVerificationTokenException -- a token verifies an email exactly once") {
                try {
                    service.confirmEmailVerification("user_15", "usedtoken")
                    error("expected InvalidVerificationTokenException")
                } catch (e: InvalidVerificationTokenException) {
                    verify(exactly = 0) { userRepository.save(any()) }
                }
            }
        }

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
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
