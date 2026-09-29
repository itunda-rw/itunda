package rw.itunda.eats

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import io.mockk.verify
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.Rider
import rw.itunda.core.domain.Account
import rw.itunda.core.domain.AccountType
import rw.itunda.core.repository.RiderRepository
import rw.itunda.core.repository.AccountRepository
import java.math.BigDecimal
import java.time.Duration

class RiderServiceTest : BehaviorSpec({

    fun account(id: String, userId: String) = Account(
        id = id, userId = userId, accountNumber = "ACC-$id", accountName = "Test account",
        type = AccountType.MAIN, balance = BigDecimal("10000"), availableBalance = BigDecimal("10000"),
    )

    Given("a real itunda user with an existing account") {
        val riderRepository = mockk<RiderRepository>()
        val accountRepository = mockk<AccountRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val service = RiderService(riderRepository, accountRepository, rateLimiter)
        val account = account("account_1", "user_1")

        When("they register as a rider for the first time") {
            every { riderRepository.findByUserId("user_1") } returns null
            every { accountRepository.findByUserIdAndType("user_1", AccountType.MAIN) } returns account
            every { riderRepository.save(any()) } answers { firstArg() }

            val rider = service.register("user_1")

            Then("it creates a real rider record pointing at their own real account") {
                rider.userId shouldBe "user_1"
                rider.accountId shouldBe "account_1"
                rider.available shouldBe false
            }
        }

        When("they try to register a second time") {
            every { riderRepository.findByUserId("user_1") } returns Rider(id = "rider_1", userId = "user_1", accountId = "account_1")

            Then("it throws RiderAlreadyRegisteredException") {
                try {
                    service.register("user_1")
                    error("expected RiderAlreadyRegisteredException")
                } catch (e: RiderAlreadyRegisteredException) {
                    // expected
                }
            }
        }

        When("a real registered rider goes online") {
            val rider = Rider(id = "rider_1", userId = "user_1", accountId = "account_1", available = false)
            every { riderRepository.findByUserId("user_1") } returns rider
            every { riderRepository.save(any()) } answers { firstArg() }

            val result = service.setAvailability("user_1", true)

            Then("their real availability flag flips") {
                result.available shouldBe true
            }
        }

        When("someone who never registered tries to toggle availability") {
            every { riderRepository.findByUserId("stranger") } returns null

            Then("it throws RiderNotRegisteredException") {
                try {
                    service.setAvailability("stranger", true)
                    error("expected RiderNotRegisteredException")
                } catch (e: RiderNotRegisteredException) {
                    // expected
                }
            }
        }

        When("a real registered rider shares their real current location") {
            val rider = Rider(id = "rider_1", userId = "user_1", accountId = "account_1")
            every { riderRepository.findByUserId("user_1") } returns rider
            every { riderRepository.save(any()) } answers { firstArg() }

            val result = service.updateLocation("user_1", -1.9536, 30.0605)

            Then("it persists the real coordinates and stamps a real locationUpdatedAt") {
                result.currentLatitude shouldBe -1.9536
                result.currentLongitude shouldBe 30.0605
                (result.locationUpdatedAt != null) shouldBe true
            }
        }

        When("a real rider submits an out-of-range coordinate") {
            Then("it throws InvalidRiderLocationException before touching the repository") {
                try {
                    service.updateLocation("user_1", 200.0, 30.0605)
                    error("expected InvalidRiderLocationException")
                } catch (e: InvalidRiderLocationException) {
                    // expected
                }
            }
        }

        When("someone who never registered tries to share a location") {
            every { riderRepository.findByUserId("stranger") } returns null

            Then("it throws RiderNotRegisteredException") {
                try {
                    service.updateLocation("stranger", -1.9536, 30.0605)
                    error("expected RiderNotRegisteredException")
                } catch (e: RiderNotRegisteredException) {
                    // expected
                }
            }
        }

        // Real rate limit added 2026-07-20 -- see updateLocation's own doc comment for
        // why this endpoint only just got its first real client callers.
        When("a real rider's location update is checked against the rate limiter first") {
            val rider = Rider(id = "rider_1", userId = "user_1", accountId = "account_1")
            every { riderRepository.findByUserId("user_1") } returns rider
            every { riderRepository.save(any()) } answers { firstArg() }
            every { rateLimiter.checkLimit("eats:rider-location:user_1", limit = 20, window = Duration.ofMinutes(1)) } just Runs

            service.updateLocation("user_1", -1.9536, 30.0605)

            Then("the real rate limiter was consulted with the correct key/limit before the repository was touched") {
                verify(exactly = 1) { rateLimiter.checkLimit("eats:rider-location:user_1", limit = 20, window = Duration.ofMinutes(1)) }
            }
        }

        When("a real rider has exceeded the location-update rate limit") {
            every { rateLimiter.checkLimit("eats:rider-location:user_1", limit = 20, window = Duration.ofMinutes(1)) } throws
                RateLimitExceededException("Too many attempts, please try again later")

            Then("it throws RateLimitExceededException before ever looking up the rider") {
                try {
                    service.updateLocation("user_1", -1.9536, 30.0605)
                    error("expected RateLimitExceededException")
                } catch (e: RateLimitExceededException) {
                    // expected
                }
                verify(exactly = 0) { riderRepository.findByUserId("user_1") }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
