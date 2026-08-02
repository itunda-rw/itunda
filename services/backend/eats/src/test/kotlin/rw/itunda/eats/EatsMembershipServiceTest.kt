package rw.itunda.eats

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import rw.itunda.core.domain.EatsMembership
import rw.itunda.core.domain.WalletType
import rw.itunda.core.domain.Wallet
import rw.itunda.core.ledger.LedgerPostResult
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.EatsMembershipRepository
import rw.itunda.core.repository.WalletRepository
import java.math.BigDecimal
import java.time.Instant

/**
 * Real optimistic-lock regression test (found live in a 2026-08-02 audit pass):
 * `subscribe` is a real check-then-act create-or-extend shape once a membership row
 * already exists (read the current `activeUntil`, extend it, save) -- the real DB
 * unique constraint on `userId` only protects the very first subscribe's INSERT race,
 * not two concurrent EXTENSIONS of an already-existing membership, which would both
 * charge the real wallet but only actually extend `activeUntil` once. This proves the
 * fix's real mechanism: extending saves the SAME pre-existing `EatsMembership` row,
 * which is what makes its own @Version field actually guard a concurrent second
 * extend on that exact row.
 */
class EatsMembershipServiceTest : BehaviorSpec({

    Given("a real user with an already-active Eats Club membership") {
        val eatsMembershipRepository = mockk<EatsMembershipRepository>()
        val walletRepository = mockk<WalletRepository>()
        val ledgerService = mockk<LedgerService>()
        val service = EatsMembershipService(eatsMembershipRepository, walletRepository, ledgerService)

        val wallet = Wallet(
            id = "wallet_1", userId = "user_1", accountNumber = "ACC-1", accountName = "Test wallet",
            type = WalletType.MAIN, balance = BigDecimal("10000"), availableBalance = BigDecimal("10000"),
        )
        val existing = EatsMembership(id = "eats_membership_1", userId = "user_1", activeUntil = Instant.parse("2026-08-10T00:00:00Z"))
        every { walletRepository.findByUserIdAndType("user_1", WalletType.MAIN) } returns wallet
        every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_1", emptyList())
        every { eatsMembershipRepository.findByUserId("user_1") } returns existing
        val savedSlot = mutableListOf<EatsMembership>()
        every { eatsMembershipRepository.save(capture(savedSlot)) } answers { firstArg() }

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
