package rw.itunda.bills

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import rw.itunda.core.domain.BillAutoPaySetting
import rw.itunda.core.domain.Notification
import rw.itunda.core.domain.Wallet
import rw.itunda.core.domain.WalletType
import rw.itunda.core.events.EventPublisher
import rw.itunda.core.ledger.LedgerPostResult
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.provider.ProviderConnector
import rw.itunda.core.provider.ProviderDeclinedException
import rw.itunda.core.provider.RailProfile
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.BillAutoPaySettingRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.TransactionRepository
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
        val eventPublisher = mockk<EventPublisher>(relaxed = true)
        val transactionRepository = mockk<TransactionRepository>()
        val billAutoPaySettingRepository = mockk<BillAutoPaySettingRepository>()
        val service = BillsService(walletRepository, ledgerService, providerConnector, eventPublisher, transactionRepository, billAutoPaySettingRepository)

        every { walletRepository.findByUserIdAndType("user_1", WalletType.MAIN) } returns wallet()
        // relaxed=true mishandles JpaRepository's generic `<S extends T> S save(S)` and
        // returns a raw Object, ClassCastException-ing at the call site -- same fix as
        // RewardsServiceTest's rewardClaimRepository.save stub.
        every { transactionRepository.save(any()) } answers { firstArg() }

        When("the provider accepts the payment") {
            every { providerConnector.attempt(any(), any()) } returns Unit
            every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_1", emptyList())

            val result = service.payBill("user_1", "bill_1", BigDecimal("35000"), "REG-12345", "REG")

            Then("it calls the provider connector before posting to the ledger, saves a real Transaction row, and completes") {
                verify(exactly = 1) { providerConnector.attempt(any(), any()) }
                verify(exactly = 1) { ledgerService.postLedgerTransaction(any(), any()) }
                verify(exactly = 1) {
                    transactionRepository.save(match {
                        it.id == "ledgertxn_1" && it.senderId == "user_1" &&
                            it.type == rw.itunda.core.domain.TransactionType.BILL &&
                            it.status == rw.itunda.core.domain.TransactionStatus.COMPLETED
                    })
                }
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

    Given("a user with an active auto-pay setting for REG - Electricity (bill_1, 35000)") {
        val walletRepository = mockk<WalletRepository>()
        val ledgerService = mockk<LedgerService>()
        val providerConnector = mockk<ProviderConnector>()
        val eventPublisher = mockk<EventPublisher>(relaxed = true)
        val transactionRepository = mockk<TransactionRepository>()
        val billAutoPaySettingRepository = mockk<BillAutoPaySettingRepository>()
        val notificationRepository = mockk<NotificationRepository>()
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = BillsService(walletRepository, ledgerService, providerConnector, eventPublisher, transactionRepository, billAutoPaySettingRepository)
        val processor = BillAutoPayProcessor(billAutoPaySettingRepository, service, notificationRepository, pushNotificationService)

        every { walletRepository.findByUserIdAndType("user_1", WalletType.MAIN) } returns wallet()
        every { transactionRepository.save(any()) } answers { firstArg() }
        every { providerConnector.attempt(any(), any()) } returns Unit
        every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_1", emptyList())
        every { billAutoPaySettingRepository.save(any()) } answers { firstArg() }
        // relaxed=true mishandles JpaRepository's generic `<S extends T> S save(S)` and
        // returns a raw Object, ClassCastException-ing at the call site -- same fix as
        // RewardsServiceTest's rewardClaimRepository.save stub.
        every { notificationRepository.save(any()) } answers { firstArg() }

        fun setting(maxAmount: BigDecimal, lastPaidBillId: String? = null) = BillAutoPaySetting(
            id = "setting_1", userId = "user_1", providerId = "b1",
            accountNumber = "REG-12345", maxAmount = maxAmount, active = true, lastPaidBillId = lastPaidBillId,
        )

        When("setAutoPay is called for an unknown provider") {
            Then("it throws BillProviderNotFoundException") {
                try {
                    service.setAutoPay("user_1", "does_not_exist", "REG-12345", BigDecimal("50000"))
                    error("expected BillProviderNotFoundException")
                } catch (e: BillProviderNotFoundException) {
                    // expected
                }
            }
        }

        When("the due bill is within the cap and not yet paid") {
            every { billAutoPaySettingRepository.findByActiveTrue() } returns listOf(setting(BigDecimal("50000")))

            val results = processor.process()

            Then("it pays the bill via the real payBill path and records lastPaidBillId") {
                results.size shouldBe 1
                results[0]["billId"] shouldBe "bill_1"
                verify(exactly = 1) { ledgerService.postLedgerTransaction(any(), any()) }
                val saved = slot<BillAutoPaySetting>()
                verify(exactly = 1) { billAutoPaySettingRepository.save(capture(saved)) }
                saved.captured.lastPaidBillId shouldBe "bill_1"
            }
        }

        When("the due bill exceeds the user's maxAmount cap") {
            every { billAutoPaySettingRepository.findByActiveTrue() } returns listOf(setting(BigDecimal("10000")))

            val results = processor.process()

            Then("it skips the bill and never touches the ledger") {
                results.size shouldBe 0
                verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
            }
        }

        When("the bill was already paid in a prior poll") {
            every { billAutoPaySettingRepository.findByActiveTrue() } returns listOf(setting(BigDecimal("50000"), lastPaidBillId = "bill_1"))

            val results = processor.process()

            Then("it skips the bill and never re-pays it") {
                results.size shouldBe 0
                verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
            }
        }

        When("clearAutoPay is called") {
            every { billAutoPaySettingRepository.findByUserIdAndProviderId("user_1", "b1") } returns setting(BigDecimal("50000"))

            service.clearAutoPay("user_1", "b1")

            Then("it deactivates the setting rather than deleting it") {
                val saved = slot<BillAutoPaySetting>()
                verify(exactly = 1) { billAutoPaySettingRepository.save(capture(saved)) }
                saved.captured.active shouldBe false
            }
        }

        When("one of two active settings fails against the ledger") {
            val wallet2 = Wallet(
                id = "wallet_2", userId = "user_2", accountNumber = "ACC-2", accountName = "Test wallet 2",
                type = WalletType.MAIN, balance = BigDecimal("50000"), availableBalance = BigDecimal("50000"),
            )
            every { walletRepository.findByUserIdAndType("user_2", WalletType.MAIN) } returns wallet2

            val failingSetting = setting(BigDecimal("50000"))
            val succeedingSetting = BillAutoPaySetting(
                id = "setting_2", userId = "user_2", providerId = "b2",
                accountNumber = "WASAC-67890", maxAmount = BigDecimal("50000"), active = true, lastPaidBillId = null,
            )
            every { billAutoPaySettingRepository.findByActiveTrue() } returns listOf(failingSetting, succeedingSetting)

            // failingSetting (user_1/bill_1) is processed first and throws; this proves the
            // self-invocation @Transactional pitfall fix -- without the per-row try/catch this
            // whole sweep is really one DB transaction, so an uncaught exception here would
            // silently roll back succeedingSetting's already-committed payment too.
            every {
                ledgerService.postLedgerTransaction(any(), any())
            } throws IllegalStateException("insufficient funds") andThen LedgerPostResult("ledgertxn_2", emptyList())

            val results = processor.process()

            Then("the failing setting is skipped but the other user's payment still succeeds") {
                results.size shouldBe 1
                results[0]["billId"] shouldBe "bill_2"
                verify(exactly = 2) { ledgerService.postLedgerTransaction(any(), any()) }
                verify(exactly = 1) {
                    billAutoPaySettingRepository.save(match { it.id == "setting_2" && it.lastPaidBillId == "bill_2" })
                }
                verify(exactly = 0) {
                    billAutoPaySettingRepository.save(match { it.id == "setting_1" })
                }
            }

            Then("the failing user receives a real BILL_AUTOPAY_FAILED notification and push") {
                val notif = slot<Notification>()
                verify(exactly = 1) { notificationRepository.save(capture(notif)) }
                notif.captured.userId shouldBe "user_1"
                notif.captured.type shouldBe "BILL_AUTOPAY_FAILED"
                verify(exactly = 1) { pushNotificationService.sendToUser("user_1", any(), any(), any()) }
            }
        }
    }
})  {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
