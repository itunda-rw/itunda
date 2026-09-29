package rw.itunda.marketplace

import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal
import java.time.Instant

/**
 * First test coverage for MarketplaceEscrowAutoReleaseScheduler -- real escrowed-money
 * release (당근마켓/Naver Cafe 안심결제-style auto-release after a buyer never explicitly
 * confirmed). Same real "one bad row (e.g. a since-deleted listing) can't poison the
 * sweep" resilience contract this class's own doc comment names explicitly.
 */
class MarketplaceEscrowAutoReleaseSchedulerTest : BehaviorSpec({

    fun escrow(id: String) = rw.itunda.core.domain.MarketplaceEscrow(
        id = id, listingId = "listing_$id", buyerId = "buyer_$id", sellerId = "seller_$id",
        amount = BigDecimal("10000"), fee = BigDecimal("150"), holdTransactionId = "ledgertxn_$id",
        createdAt = Instant.now(),
    )

    Given("3 escrows due for auto-release, where the middle one's listing was since deleted") {
        val marketplaceService = mockk<MarketplaceService>()
        every { marketplaceService.getEscrowsDueForAutoRelease() } returns listOf(escrow("e1"), escrow("e2"), escrow("e3"))
        every { marketplaceService.autoReleaseEscrow("e1") } returns Unit
        every { marketplaceService.autoReleaseEscrow("e2") } throws RuntimeException("listing no longer exists")
        every { marketplaceService.autoReleaseEscrow("e3") } returns Unit
        val scheduler = MarketplaceEscrowAutoReleaseScheduler(marketplaceService)

        When("the sweep runs") {
            scheduler.run()

            Then("all 3 are still released -- the middle failure doesn't poison the sweep for the others") {
                verify(exactly = 1) { marketplaceService.autoReleaseEscrow("e1") }
                verify(exactly = 1) { marketplaceService.autoReleaseEscrow("e2") }
                verify(exactly = 1) { marketplaceService.autoReleaseEscrow("e3") }
            }
        }
    }

    Given("no escrows due for auto-release") {
        val marketplaceService = mockk<MarketplaceService>()
        every { marketplaceService.getEscrowsDueForAutoRelease() } returns emptyList()
        val scheduler = MarketplaceEscrowAutoReleaseScheduler(marketplaceService)

        When("the sweep runs") {
            scheduler.run()

            Then("nothing is released, no exception is thrown") {
                verify(exactly = 0) { marketplaceService.autoReleaseEscrow(any()) }
            }
        }
    }
})
