package rw.itunda.auth

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import rw.itunda.core.domain.User
import rw.itunda.core.domain.Wallet
import rw.itunda.core.repository.UserRepository
import rw.itunda.core.repository.WalletRepository
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
        val service = AuthService(userRepository, walletRepository, jwtService, tokenBlocklistService, rateLimiter)

        When("registering a brand-new phone number") {
            every { rateLimiter.checkLimit(any(), any(), any()) } returns Unit
            every { userRepository.existsByPhoneNumber("+250788000001") } returns false
            every { userRepository.save(any()) } answers { firstArg() }
            every { walletRepository.save(any()) } answers { firstArg() }

            val response = service.register(RegisterRequest("+250788000001", "a@b.rw", "Jean", "B", "password123"))

            Then("it rate-limits, creates a user, provisions a real zero-balance wallet, and issues real tokens") {
                verify(exactly = 1) { rateLimiter.checkLimit("auth:register:+250788000001", 3, any()) }
                val walletSlot = mutableListOf<Wallet>()
                verify(exactly = 1) { walletRepository.save(capture(walletSlot)) }
                walletSlot.first().balance.signum() shouldBe 0
                jwtService.verify(response.accessToken) shouldNotBe null
                jwtService.verify(response.refreshToken)!!.isRefresh shouldBe true
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
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
