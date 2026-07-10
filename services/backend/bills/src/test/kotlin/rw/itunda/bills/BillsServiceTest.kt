package rw.itunda.bills

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.core.domain.Wallet
import rw.itunda.core.domain.WalletType
import rw.itunda.core.ledger.LedgerPostResult
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.provider.ProviderConnector
import rw.itunda.core.provider.ProviderDeclinedException
import rw.itunda.core.provider.RailProfile
import rw.itunda.core.repository.WalletRepository
import java.math.BigDecimal

/**
 * First test coverage for bills/airtime, and specifically for the provider-connector
 * wiring added 2026-07-11 (previously every payment always succeeded against a flat
 * rail with no provider simulation at all). LedgerService and ProviderConnector are
 * both mocked -- this file is about one thing: that a declined provider attempt never
 * reaches the ledger, and a successful one does.
 */
class BillsServiceTest : BehaviorSpec({

    fun wallet() = Wallet(
        id = "wallet_1", userId = "user_1", accountNumber = "ACC-1", accountName = "Test wallet",
        type = WalletType.MAIN, balance = BigDecimal("50000"), availableBalance = BigDecimal("50000"),
    )

    Given("a user with a wallet") {
        val walletRepository = mockk<WalletRepository>()
        val ledgerService = mockk<LedgerService>()
        val providerConnector = mockk<ProviderConnector>()
        val service = BillsService(walletRepository, ledgerService, providerConnector)

        every { walletRepository.findByUserIdAndType("user_1", WalletType.MAIN) } returns wallet()

        When("the provider accepts the payment") {
            every { providerConnector.attempt(any(), any()) } returns Unit
            every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_1", emptyList())

            val result = service.payBill("user_1", "bill_1", BigDecimal("35000"), "REG-12345", "REG")

            Then("it calls the provider connector before posting to the ledger, and completes") {
                verify(exactly = 1) { providerConnector.attempt(any(), any()) }
                verify(exactly = 1) { ledgerService.postLedgerTransaction(any(), any()) }
                result["status"] shouldBe "COMPLETED"
                result["id"] shouldBe "ledgertxn_1"
            }
        }

        When("the provider declines the payment") {
            every { providerConnector.attempt(any(), any()) } throws ProviderDeclinedException("REG declined")

            Then("payBill throws ProviderDeclinedException and never touches the ledger") {
                try {
                    service.payBill("user_1", "bill_1", BigDecimal("35000"), "REG-12345", "REG")
                    error("expected ProviderDeclinedException")
                } catch (e: ProviderDeclinedException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }

            Then("buyAirtime also throws and never touches the ledger") {
                try {
                    service.buyAirtime("user_1", "+250788000000", BigDecimal("2000"), "MTN")
                    error("expected ProviderDeclinedException")
                } catch (e: ProviderDeclinedException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }

        When("no wallet exists for the user") {
            every { walletRepository.findByUserIdAndType("user_2", WalletType.MAIN) } returns null

            Then("it throws NoWalletException before ever calling the provider") {
                try {
                    service.payBill("user_2", "bill_1", BigDecimal("1000"), null, "REG")
                    error("expected NoWalletException")
                } catch (e: NoWalletException) {
                    verify(exactly = 0) { providerConnector.attempt(any(), any()) }
                }
            }
        }
    }
})  {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
