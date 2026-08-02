package rw.itunda.merchant

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import rw.itunda.core.domain.Merchant
import rw.itunda.core.domain.MerchantAd
import rw.itunda.core.domain.MerchantStatus
import rw.itunda.core.domain.Wallet
import rw.itunda.core.domain.WalletType
import rw.itunda.core.ledger.LedgerPostResult
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.MerchantAdRepository
import rw.itunda.core.repository.MerchantRepository
import rw.itunda.core.repository.WalletRepository
import java.math.BigDecimal
import java.time.Instant

/**
 * Real optimistic-lock regression test (found live in a 2026-08-02 audit pass) -- same
 * real check-then-act create-or-extend race EatsMembershipServiceTest's own doc comment
 * names, here for radius-targeted local ads: `createOrExtendAd`'s real DB unique
 * constraint on `merchantId` only protects the very first ad's INSERT, not two
 * concurrent EXTENSIONS of an already-existing ad.
 */
class MerchantAdServiceTest : BehaviorSpec({

    Given("a merchant with an already-active local ad") {
        val merchantRepository = mockk<MerchantRepository>()
        val merchantAdRepository = mockk<MerchantAdRepository>()
        val walletRepository = mockk<WalletRepository>()
        val ledgerService = mockk<LedgerService>()
        val service = MerchantAdService(merchantRepository, merchantAdRepository, walletRepository, ledgerService)

        val merchant = Merchant(
            id = "merchant_1", ownerUserId = "owner_1", walletId = "wallet_1", businessName = "Test Store",
            status = MerchantStatus.ACTIVE, latitude = -1.9, longitude = 30.0,
        )
        val wallet = Wallet(
            id = "wallet_1", userId = "owner_1", accountNumber = "ACC-1", accountName = "Test wallet",
            type = WalletType.MAIN, balance = BigDecimal("10000"), availableBalance = BigDecimal("10000"),
        )
        val existing = MerchantAd(
            id = "merchant_ad_1", merchantId = "merchant_1", title = "Old title", radiusMeters = 300,
            activeUntil = Instant.parse("2026-08-10T00:00:00Z"),
        )
        every { merchantRepository.findByOwnerUserId("owner_1") } returns merchant
        every { walletRepository.findByUserIdAndType("owner_1", WalletType.MAIN) } returns wallet
        every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_1", emptyList())
        every { merchantAdRepository.findByMerchantId("merchant_1") } returns existing
        val savedSlot = mutableListOf<MerchantAd>()
        every { merchantAdRepository.save(capture(savedSlot)) } answers { firstArg() }

        When("paying again to extend the existing ad") {
            service.createOrExtendAd("owner_1", "New title", null, 300, 7)

            Then("the exact same row object -- the one carrying the real @Version -- is what gets saved") {
                (savedSlot.first() === existing) shouldBe true
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
