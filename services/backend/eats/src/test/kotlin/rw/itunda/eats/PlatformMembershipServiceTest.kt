package rw.itunda.eats

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import rw.itunda.core.domain.PlatformMembership
import rw.itunda.core.domain.WalletType
import rw.itunda.core.domain.Wallet
import rw.itunda.core.ledger.LedgerPostResult
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.PlatformMembershipRepository
import rw.itunda.core.repository.WalletRepository
import java.math.BigDecimal
import java.time.Instant

/**
 * Real optimistic-lock regression test (found live in a 2026-08-02 audit pass) -- same
 * real check-then-act create-or-extend race EatsMembershipServiceTest's own doc comment
 * names, here for the platform-wide Wow-style membership.
 */
class PlatformMembershipServiceTest : BehaviorSpec({

    Given("a real user with an already-active platform membership") {
        val platformMembershipRepository = mockk<PlatformMembershipRepository>()
        val walletRepository = mockk<WalletRepository>()
        val ledgerService = mockk<LedgerService>()
        val service = PlatformMembershipService(platformMembershipRepository, walletRepository, ledgerService)

        val wallet = Wallet(
            id = "wallet_1", userId = "user_1", accountNumber = "ACC-1", accountName = "Test wallet",
            type = WalletType.MAIN, balance = BigDecimal("10000"), availableBalance = BigDecimal("10000"),
        )
        val existing = PlatformMembership(id = "platform_membership_1", userId = "user_1", activeUntil = Instant.parse("2026-08-10T00:00:00Z"))
        every { walletRepository.findByUserIdAndType("user_1", WalletType.MAIN) } returns wallet
        every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_1", emptyList())
        every { platformMembershipRepository.findByUserId("user_1") } returns existing
        val savedSlot = mutableListOf<PlatformMembership>()
        every { platformMembershipRepository.save(capture(savedSlot)) } answers { firstArg() }

        When("subscribing again to extend it") {
            service.subscribe("user_1", 30)

            Then("the exact same row object -- the one carrying the real @Version -- is what gets saved") {
                (savedSlot.first() === existing) shouldBe true
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
