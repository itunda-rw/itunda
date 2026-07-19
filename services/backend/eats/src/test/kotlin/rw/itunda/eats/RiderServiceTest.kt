package rw.itunda.eats

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import rw.itunda.core.domain.Rider
import rw.itunda.core.domain.Wallet
import rw.itunda.core.domain.WalletType
import rw.itunda.core.repository.RiderRepository
import rw.itunda.core.repository.WalletRepository
import java.math.BigDecimal

class RiderServiceTest : BehaviorSpec({

    fun wallet(id: String, userId: String) = Wallet(
        id = id, userId = userId, accountNumber = "ACC-$id", accountName = "Test wallet",
        type = WalletType.MAIN, balance = BigDecimal("10000"), availableBalance = BigDecimal("10000"),
    )

    Given("a real itunda user with an existing wallet") {
        val riderRepository = mockk<RiderRepository>()
        val walletRepository = mockk<WalletRepository>()
        val service = RiderService(riderRepository, walletRepository)
        val wallet = wallet("wallet_1", "user_1")

        When("they register as a rider for the first time") {
            every { riderRepository.findByUserId("user_1") } returns null
            every { walletRepository.findByUserIdAndType("user_1", WalletType.MAIN) } returns wallet
            every { riderRepository.save(any()) } answers { firstArg() }

            val rider = service.register("user_1")

            Then("it creates a real rider record pointing at their own real wallet") {
                rider.userId shouldBe "user_1"
                rider.walletId shouldBe "wallet_1"
                rider.available shouldBe false
            }
        }

        When("they try to register a second time") {
            every { riderRepository.findByUserId("user_1") } returns Rider(id = "rider_1", userId = "user_1", walletId = "wallet_1")

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
            val rider = Rider(id = "rider_1", userId = "user_1", walletId = "wallet_1", available = false)
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
            val rider = Rider(id = "rider_1", userId = "user_1", walletId = "wallet_1")
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
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
