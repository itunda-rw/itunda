package rw.itunda.wallet

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import rw.itunda.core.domain.LinkedAccount
import rw.itunda.core.domain.LinkedAccountStatus
import rw.itunda.core.domain.Wallet
import rw.itunda.core.domain.WalletAutoTopUpSetting
import rw.itunda.core.domain.WalletType
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerPostResult
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.provider.ProviderConnector
import rw.itunda.core.provider.ProviderDeclinedException
import rw.itunda.core.repository.LinkedAccountRepository
import rw.itunda.core.repository.TransactionRepository
import rw.itunda.core.repository.WalletAutoTopUpSettingRepository
import rw.itunda.core.repository.WalletRepository
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.Optional

/** First test coverage for the real Naver Pay Money 자동충전 (auto-charge) equivalent. */
class AutoTopUpServiceTest : BehaviorSpec({

    fun wallet(id: String, userId: String, balance: String) = Wallet(
        id = id, userId = userId, accountNumber = "ACC-$id", accountName = "Test wallet",
        type = WalletType.MAIN, balance = BigDecimal(balance), availableBalance = BigDecimal(balance),
    )

    fun linkedAccount(id: String, userId: String, status: LinkedAccountStatus = LinkedAccountStatus.LINKED) = LinkedAccount(
        id = id, userId = userId, provider = "MTN", externalAccountNumberMasked = "•••• 1234", status = status,
    )

    Given("configuring a real auto top-up rule") {
        val walletAutoTopUpSettingRepository = mockk<WalletAutoTopUpSettingRepository>()
        val walletRepository = mockk<WalletRepository>()
        val linkedAccountRepository = mockk<LinkedAccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val transactionRepository = mockk<TransactionRepository>(relaxed = true)
        every { transactionRepository.save(any()) } answers { firstArg() }
        val providerConnector = mockk<ProviderConnector>()
        val service = AutoTopUpService(walletAutoTopUpSettingRepository, walletRepository, linkedAccountRepository, ledgerService, transactionRepository, providerConnector)

        When("configuring for the first time with a real LINKED account") {
            every { walletRepository.findById("wallet_1") } returns Optional.of(wallet("wallet_1", "user_1", "5000"))
            every { linkedAccountRepository.findById("linked_1") } returns Optional.of(linkedAccount("linked_1", "user_1"))
            every { walletAutoTopUpSettingRepository.findByWalletId("wallet_1") } returns null
            every { walletAutoTopUpSettingRepository.save(any()) } answers { firstArg() }

            val result = service.configure("user_1", "wallet_1", "linked_1", BigDecimal("2000"), BigDecimal("10000"), 3, true)

            Then("it real-creates a new setting with the exact real threshold/top-up amounts") {
                result.walletId shouldBe "wallet_1"
                result.thresholdAmount shouldBe BigDecimal("2000")
                result.topUpAmount shouldBe BigDecimal("10000")
                result.enabled shouldBe true
            }
        }

        When("configuring against a wallet that isn't the caller's own") {
            every { walletRepository.findById("wallet_2") } returns Optional.of(wallet("wallet_2", "owner_1", "5000"))

            Then("it throws WalletNotOwnedException before touching the linked account") {
                try {
                    service.configure("attacker", "wallet_2", "linked_1", BigDecimal("2000"), BigDecimal("10000"), 3, true)
                    error("expected WalletNotOwnedException")
                } catch (e: WalletNotOwnedException) {
                    verify(exactly = 0) { linkedAccountRepository.findById(any()) }
                }
            }
        }

        When("configuring against a linked account that's no longer LINKED") {
            every { walletRepository.findById("wallet_1") } returns Optional.of(wallet("wallet_1", "user_1", "5000"))
            every { linkedAccountRepository.findById("linked_unlinked") } returns Optional.of(linkedAccount("linked_unlinked", "user_1", LinkedAccountStatus.UNLINKED))

            Then("it throws AutoTopUpLinkedAccountNotLinkedException") {
                try {
                    service.configure("user_1", "wallet_1", "linked_unlinked", BigDecimal("2000"), BigDecimal("10000"), 3, true)
                    error("expected AutoTopUpLinkedAccountNotLinkedException")
                } catch (e: AutoTopUpLinkedAccountNotLinkedException) {
                    // expected
                }
            }
        }

        When("configuring with a zero top-up amount") {
            every { walletRepository.findById("wallet_1") } returns Optional.of(wallet("wallet_1", "user_1", "5000"))

            Then("it throws AutoTopUpInvalidAmountException before touching the linked account") {
                try {
                    service.configure("user_1", "wallet_1", "linked_1", BigDecimal("2000"), BigDecimal.ZERO, 3, true)
                    error("expected AutoTopUpInvalidAmountException")
                } catch (e: AutoTopUpInvalidAmountException) {
                    verify(exactly = 0) { linkedAccountRepository.findById(any()) }
                }
            }
        }
    }

    Given("evaluating a real wallet whose balance has dropped below its real threshold") {
        val walletAutoTopUpSettingRepository = mockk<WalletAutoTopUpSettingRepository>()
        val walletRepository = mockk<WalletRepository>()
        val linkedAccountRepository = mockk<LinkedAccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val transactionRepository = mockk<TransactionRepository>(relaxed = true)
        every { transactionRepository.save(any()) } answers { firstArg() }
        val providerConnector = mockk<ProviderConnector>()
        val service = AutoTopUpService(walletAutoTopUpSettingRepository, walletRepository, linkedAccountRepository, ledgerService, transactionRepository, providerConnector)

        val lowWallet = wallet("wallet_1", "user_1", "1000")
        val setting = WalletAutoTopUpSetting(
            id = "auto_topup_1", userId = "user_1", walletId = "wallet_1", linkedAccountId = "linked_1",
            thresholdAmount = BigDecimal("2000"), topUpAmount = BigDecimal("10000"), dailyTriggerCap = 3,
        )

        When("the real provider pull succeeds") {
            every { walletRepository.findById("wallet_1") } returns Optional.of(lowWallet)
            every { walletAutoTopUpSettingRepository.findByWalletId("wallet_1") } returns setting
            every { linkedAccountRepository.findById("linked_1") } returns Optional.of(linkedAccount("linked_1", "user_1"))
            every { providerConnector.attempt(any(), any()) } returns Unit
            val legsSlot = slot<List<LedgerLeg>>()
            every { ledgerService.postLedgerTransaction("RWF", capture(legsSlot)) } returns LedgerPostResult("ledgertxn_1", emptyList())
            every { walletAutoTopUpSettingRepository.save(any()) } answers { firstArg() }

            val result = service.evaluateAndTopUp("user_1", "wallet_1")

            Then("it real-triggers, crediting the wallet and debiting rail_suspense for the exact real top-up amount") {
                result.triggered shouldBe true
                val legs = legsSlot.captured
                legs.first { it.accountId == "wallet_1" }.direction shouldBe rw.itunda.core.domain.LedgerDirection.CREDIT
                legs.first { it.accountId == "wallet_1" }.amount shouldBe BigDecimal("10000")
                legs.first { it.accountId == "rail_suspense" }.direction shouldBe rw.itunda.core.domain.LedgerDirection.DEBIT
            }
            Then("it real-records a Transaction of type DEPOSIT via the auto_topup channel") {
                verify(exactly = 1) { transactionRepository.save(match { it.type == rw.itunda.core.domain.TransactionType.DEPOSIT && it.channel == "auto_topup" && it.amount == BigDecimal("10000") }) }
            }
            Then("it real-increments triggersToday") {
                setting.triggersToday shouldBe 1
            }
        }

        When("the real provider declines the pull") {
            every { walletRepository.findById("wallet_1") } returns Optional.of(lowWallet)
            val freshSetting = WalletAutoTopUpSetting(
                id = "auto_topup_2", userId = "user_1", walletId = "wallet_1", linkedAccountId = "linked_1",
                thresholdAmount = BigDecimal("2000"), topUpAmount = BigDecimal("10000"), dailyTriggerCap = 3,
            )
            every { walletAutoTopUpSettingRepository.findByWalletId("wallet_1") } returns freshSetting
            every { linkedAccountRepository.findById("linked_1") } returns Optional.of(linkedAccount("linked_1", "user_1"))
            every { providerConnector.attempt(any(), any()) } throws ProviderDeclinedException("MTN Mobile Money declined")

            val result = service.evaluateAndTopUp("user_1", "wallet_1")

            Then("it real-reports not triggered and never touches the ledger, leaving the wallet balance untouched") {
                result.triggered shouldBe false
                verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
            }
        }

        When("the wallet balance is already at or above the real threshold") {
            val healthyWallet = wallet("wallet_1", "user_1", "5000")
            every { walletRepository.findById("wallet_1") } returns Optional.of(healthyWallet)
            val freshSetting = WalletAutoTopUpSetting(
                id = "auto_topup_3", userId = "user_1", walletId = "wallet_1", linkedAccountId = "linked_1",
                thresholdAmount = BigDecimal("2000"), topUpAmount = BigDecimal("10000"), dailyTriggerCap = 3,
            )
            every { walletAutoTopUpSettingRepository.findByWalletId("wallet_1") } returns freshSetting

            val result = service.evaluateAndTopUp("user_1", "wallet_1")

            Then("it real-reports not triggered without ever calling the real provider") {
                result.triggered shouldBe false
                verify(exactly = 0) { providerConnector.attempt(any(), any()) }
            }
        }

        When("the real daily trigger cap has already been reached today") {
            every { walletRepository.findById("wallet_1") } returns Optional.of(lowWallet)
            val cappedSetting = WalletAutoTopUpSetting(
                id = "auto_topup_4", userId = "user_1", walletId = "wallet_1", linkedAccountId = "linked_1",
                thresholdAmount = BigDecimal("2000"), topUpAmount = BigDecimal("10000"), dailyTriggerCap = 2,
                triggersToday = 2, lastTriggerDate = LocalDate.now(),
            )
            every { walletAutoTopUpSettingRepository.findByWalletId("wallet_1") } returns cappedSetting

            val result = service.evaluateAndTopUp("user_1", "wallet_1")

            Then("it real-reports not triggered without ever calling the real provider") {
                result.triggered shouldBe false
                verify(exactly = 0) { providerConnector.attempt(any(), any()) }
            }
        }

        When("the real daily trigger cap was reached YESTERDAY, not today") {
            every { walletRepository.findById("wallet_1") } returns Optional.of(lowWallet)
            val staleSetting = WalletAutoTopUpSetting(
                id = "auto_topup_5", userId = "user_1", walletId = "wallet_1", linkedAccountId = "linked_1",
                thresholdAmount = BigDecimal("2000"), topUpAmount = BigDecimal("10000"), dailyTriggerCap = 2,
                triggersToday = 2, lastTriggerDate = LocalDate.now().minusDays(1),
            )
            every { walletAutoTopUpSettingRepository.findByWalletId("wallet_1") } returns staleSetting
            every { linkedAccountRepository.findById("linked_1") } returns Optional.of(linkedAccount("linked_1", "user_1"))
            every { providerConnector.attempt(any(), any()) } returns Unit
            every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_2", emptyList())
            every { walletAutoTopUpSettingRepository.save(any()) } answers { firstArg() }

            val result = service.evaluateAndTopUp("user_1", "wallet_1")

            Then("it real-resets the daily counter for the new real day and triggers") {
                result.triggered shouldBe true
                staleSetting.triggersToday shouldBe 1
            }
        }

        When("auto top-up is disabled for this wallet") {
            every { walletRepository.findById("wallet_1") } returns Optional.of(lowWallet)
            val disabledSetting = WalletAutoTopUpSetting(
                id = "auto_topup_6", userId = "user_1", walletId = "wallet_1", linkedAccountId = "linked_1",
                thresholdAmount = BigDecimal("2000"), topUpAmount = BigDecimal("10000"), enabled = false,
            )
            every { walletAutoTopUpSettingRepository.findByWalletId("wallet_1") } returns disabledSetting

            val result = service.evaluateAndTopUp("user_1", "wallet_1")

            Then("it real-reports not triggered without ever calling the real provider") {
                result.triggered shouldBe false
                verify(exactly = 0) { providerConnector.attempt(any(), any()) }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
