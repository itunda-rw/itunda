package rw.itunda.account

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import rw.itunda.core.domain.ExchangeRateAlert
import rw.itunda.core.domain.Account
import rw.itunda.core.domain.AccountType
import rw.itunda.core.fx.ForeignCurrencyRateClient
import rw.itunda.core.ledger.LedgerPostResult
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.CurrencyConversionRepository
import rw.itunda.core.repository.ExchangeRateAlertRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.AccountRepository
import rw.itunda.core.account.AccountNumberGenerator
import java.math.BigDecimal
import java.util.Optional

/**
 * First test coverage for the real 토스뱅크 외화통장 (foreign-currency account)
 * equivalent -- see ForeignCurrencyAccountService's own doc comment.
 */
class ForeignCurrencyAccountServiceTest : BehaviorSpec({

    fun account(id: String, userId: String, type: AccountType, currency: String = "RWF") = Account(
        id = id, userId = userId, accountNumber = "ACC-$id", accountName = "Test account",
        type = type, balance = BigDecimal("100000"), availableBalance = BigDecimal("100000"), currency = currency,
    )

    Given("a real user opening a foreign-currency account for the first time") {
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val rateClient = mockk<ForeignCurrencyRateClient>()
        val currencyConversionRepository = mockk<CurrencyConversionRepository>()
        val accountNumberGenerator = mockk<AccountNumberGenerator>(relaxed = true)
        val exchangeRateAlertRepository = mockk<ExchangeRateAlertRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = ForeignCurrencyAccountService(
            accountRepository, ledgerService, rateClient, currencyConversionRepository, accountNumberGenerator,
            exchangeRateAlertRepository, notificationRepository, pushNotificationService,
        )

        val mainAccount = account("account_main", "user_1", AccountType.MAIN)
        every { accountRepository.findByUserIdAndType("user_1", AccountType.MAIN) } returns mainAccount
        every { accountRepository.findByIdForUpdate("account_main") } returns Optional.of(mainAccount)
        every { accountRepository.findByUserIdAndTypeAndCurrency("user_1", AccountType.FOREIGN_CURRENCY, "USD") } returns null
        every { accountRepository.save(any()) } answers { firstArg() }

        When("opening a real supported USD account") {
            val result = service.openAccount("user_1", "usd")

            Then("it real-creates a new zero-balance USD account") {
                result.type shouldBe AccountType.FOREIGN_CURRENCY
                result.currency shouldBe "USD"
                result.balance shouldBe BigDecimal.ZERO
            }
            // Real bug found live (2026-08-02) -- see openAccount's own doc comment:
            // this asserts the actual fix mechanism, the same "lock a different
            // already-existing row" precedent YouthAccountService.openYouthAccount's own
            // identical-shaped fix establishes for a reject-if-already-exists
            // check-then-CREATE race.
            Then("it real-locks the user's own MAIN account row before creating the new account") {
                verify(exactly = 1) { accountRepository.findByIdForUpdate("account_main") }
            }
        }

        When("opening an account for an unsupported currency") {
            Then("it throws UnsupportedCurrencyException before ever touching the account repository") {
                try {
                    service.openAccount("user_1", "JPY")
                    error("expected UnsupportedCurrencyException")
                } catch (e: UnsupportedCurrencyException) {
                    verify(exactly = 0) { accountRepository.findByIdForUpdate(any()) }
                }
            }
        }
    }

    Given("a real user who already has a USD account") {
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val rateClient = mockk<ForeignCurrencyRateClient>()
        val currencyConversionRepository = mockk<CurrencyConversionRepository>()
        val accountNumberGenerator = mockk<AccountNumberGenerator>(relaxed = true)
        val exchangeRateAlertRepository = mockk<ExchangeRateAlertRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = ForeignCurrencyAccountService(
            accountRepository, ledgerService, rateClient, currencyConversionRepository, accountNumberGenerator,
            exchangeRateAlertRepository, notificationRepository, pushNotificationService,
        )

        val mainAccount = account("account_main", "user_1", AccountType.MAIN)
        val existingUsd = account("account_usd", "user_1", AccountType.FOREIGN_CURRENCY, "USD")
        every { accountRepository.findByUserIdAndType("user_1", AccountType.MAIN) } returns mainAccount
        every { accountRepository.findByIdForUpdate("account_main") } returns Optional.of(mainAccount)
        every { accountRepository.findByUserIdAndTypeAndCurrency("user_1", AccountType.FOREIGN_CURRENCY, "USD") } returns existingUsd

        When("trying to open a second USD account") {
            Then("it throws ForeignCurrencyAccountAlreadyExistsException and never creates a real duplicate") {
                try {
                    service.openAccount("user_1", "USD")
                    error("expected ForeignCurrencyAccountAlreadyExistsException")
                } catch (e: ForeignCurrencyAccountAlreadyExistsException) {
                    verify(exactly = 0) { accountRepository.save(any()) }
                }
            }
        }
    }

    Given("a real user with no MAIN account at all trying to open a foreign-currency account") {
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val rateClient = mockk<ForeignCurrencyRateClient>()
        val currencyConversionRepository = mockk<CurrencyConversionRepository>()
        val accountNumberGenerator = mockk<AccountNumberGenerator>(relaxed = true)
        val exchangeRateAlertRepository = mockk<ExchangeRateAlertRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = ForeignCurrencyAccountService(
            accountRepository, ledgerService, rateClient, currencyConversionRepository, accountNumberGenerator,
            exchangeRateAlertRepository, notificationRepository, pushNotificationService,
        )

        every { accountRepository.findByUserIdAndType("user_2", AccountType.MAIN) } returns null

        When("opening a real supported currency") {
            Then("it throws AccountNotFoundException before ever attempting to lock or create anything") {
                try {
                    service.openAccount("user_2", "USD")
                    error("expected AccountNotFoundException")
                } catch (e: AccountNotFoundException) {
                    verify(exactly = 0) { accountRepository.findByIdForUpdate(any()) }
                    verify(exactly = 0) { accountRepository.save(any()) }
                }
            }
        }
    }

    Given("a real user converting RWF into a USD account they already hold") {
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val rateClient = mockk<ForeignCurrencyRateClient>()
        val currencyConversionRepository = mockk<CurrencyConversionRepository>()
        val accountNumberGenerator = mockk<AccountNumberGenerator>(relaxed = true)
        val exchangeRateAlertRepository = mockk<ExchangeRateAlertRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = ForeignCurrencyAccountService(
            accountRepository, ledgerService, rateClient, currencyConversionRepository, accountNumberGenerator,
            exchangeRateAlertRepository, notificationRepository, pushNotificationService,
        )

        val mainAccount = account("account_main", "user_1", AccountType.MAIN)
        val usdAccount = account("account_usd", "user_1", AccountType.FOREIGN_CURRENCY, "USD")
        every { accountRepository.findByUserIdAndType("user_1", AccountType.MAIN) } returns mainAccount
        every { accountRepository.findByUserIdAndTypeAndCurrency("user_1", AccountType.FOREIGN_CURRENCY, "USD") } returns usdAccount
        every { rateClient.getRate("RWF", "USD") } returns 0.00069
        every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_fx_1", emptyList())
        every { currencyConversionRepository.save(any()) } answers { firstArg() }

        When("converting a real 100,000 RWF amount") {
            val conversion = service.convert("user_1", "RWF", "USD", BigDecimal("100000"))

            Then("it real-applies itunda's own margin below the real live mid-market rate") {
                val gross = BigDecimal("100000").multiply(BigDecimal(0.00069)).setScale(2, java.math.RoundingMode.HALF_UP)
                val margin = gross.multiply(BigDecimal("0.015")).setScale(2, java.math.RoundingMode.HALF_UP)
                conversion.toAmount shouldBe gross.subtract(margin)
                conversion.marginAmount shouldBe margin
            }
        }

        When("converting to an unopened foreign currency") {
            every { accountRepository.findByUserIdAndTypeAndCurrency("user_1", AccountType.FOREIGN_CURRENCY, "EUR") } returns null
            every { rateClient.getRate("RWF", "EUR") } returns 0.00064

            Then("it throws ForeignCurrencyAccountNotFoundException before touching the ledger") {
                try {
                    service.convert("user_1", "RWF", "EUR", BigDecimal("10000"))
                    error("expected ForeignCurrencyAccountNotFoundException")
                } catch (e: ForeignCurrencyAccountNotFoundException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }
    }

    Given("a real user setting a real 환율 알림 (exchange rate alert) on RWF/USD") {
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val rateClient = mockk<ForeignCurrencyRateClient>()
        val currencyConversionRepository = mockk<CurrencyConversionRepository>()
        val accountNumberGenerator = mockk<AccountNumberGenerator>(relaxed = true)
        val exchangeRateAlertRepository = mockk<ExchangeRateAlertRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = ForeignCurrencyAccountService(
            accountRepository, ledgerService, rateClient, currencyConversionRepository, accountNumberGenerator,
            exchangeRateAlertRepository, notificationRepository, pushNotificationService,
        )

        every { exchangeRateAlertRepository.save(any()) } answers { firstArg() }

        When("setting a new alert with an invalid direction") {
            Then("it throws InvalidRateAlertException before ever touching the repository") {
                try {
                    service.setRateAlert("user_1", "RWF", "USD", 1500.0, "SIDEWAYS")
                    error("expected InvalidRateAlertException")
                } catch (e: InvalidRateAlertException) {
                    verify(exactly = 0) { exchangeRateAlertRepository.save(any()) }
                }
            }
        }

        When("setting a new alert with a negative target rate") {
            Then("it throws InvalidRateAlertException") {
                try {
                    service.setRateAlert("user_1", "RWF", "USD", -5.0, "ABOVE")
                    error("expected InvalidRateAlertException")
                } catch (e: InvalidRateAlertException) {
                    verify(exactly = 0) { exchangeRateAlertRepository.save(any()) }
                }
            }
        }

        When("setting a valid new alert") {
            every { exchangeRateAlertRepository.findByUserIdAndFromCurrencyAndToCurrency("user_1", "RWF", "USD") } returns null
            val saved = slot<ExchangeRateAlert>()

            val alert = service.setRateAlert("user_1", "rwf", "usd", 1500.0, "ABOVE")

            Then("it real-creates a new alert row with the given target and direction") {
                verify(exactly = 1) { exchangeRateAlertRepository.save(capture(saved)) }
                alert.fromCurrency shouldBe "RWF"
                alert.toCurrency shouldBe "USD"
                alert.targetRate shouldBe 1500.0
                alert.direction shouldBe "ABOVE"
            }
        }

        When("setting a new target on an already-triggered alert") {
            val existing = ExchangeRateAlert(id = "fx_alert_1", userId = "user_1", fromCurrency = "RWF", toCurrency = "USD", targetRate = 1400.0, direction = "ABOVE", alertTriggeredAt = java.time.Instant.now())
            every { exchangeRateAlertRepository.findByUserIdAndFromCurrencyAndToCurrency("user_1", "RWF", "USD") } returns existing

            val alert = service.setRateAlert("user_1", "RWF", "USD", 1600.0, "ABOVE")

            Then("it real-re-arms the alert, clearing the old triggered timestamp") {
                alert.alertTriggeredAt shouldBe null
                alert.targetRate shouldBe 1600.0
            }
        }
    }

    Given("a real due exchange rate alert whose real live rate has genuinely crossed the target") {
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val rateClient = mockk<ForeignCurrencyRateClient>()
        val currencyConversionRepository = mockk<CurrencyConversionRepository>()
        val accountNumberGenerator = mockk<AccountNumberGenerator>(relaxed = true)
        val exchangeRateAlertRepository = mockk<ExchangeRateAlertRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = ForeignCurrencyAccountService(
            accountRepository, ledgerService, rateClient, currencyConversionRepository, accountNumberGenerator,
            exchangeRateAlertRepository, notificationRepository, pushNotificationService,
        )

        val alert = ExchangeRateAlert(id = "fx_alert_1", userId = "user_1", fromCurrency = "RWF", toCurrency = "USD", targetRate = 0.0007, direction = "ABOVE")
        every { exchangeRateAlertRepository.findById("fx_alert_1") } returns java.util.Optional.of(alert)
        every { exchangeRateAlertRepository.save(any()) } answers { firstArg() }
        // relaxed=true mishandles JpaRepository's generic `<S extends T> S save(S)` and
        // returns a raw Object, ClassCastException-ing at the call site -- same fix as
        // RewardsServiceTest's rewardClaimRepository.save stub.
        every { notificationRepository.save(any()) } answers { firstArg() }

        When("the real rate has crossed the ABOVE target") {
            every { rateClient.getRate("RWF", "USD") } returns 0.00075

            service.triggerRateAlert("fx_alert_1")

            Then("it real-notifies the user and marks the alert triggered") {
                verify(exactly = 1) { notificationRepository.save(match { it.type == "EXCHANGE_RATE_ALERT" && it.userId == "user_1" }) }
                verify(exactly = 1) { pushNotificationService.sendToUser("user_1", any(), any(), any()) }
                alert.alertTriggeredAt shouldNotBe null
            }
        }

        When("the real rate has NOT crossed the target") {
            alert.alertTriggeredAt = null
            every { rateClient.getRate("RWF", "USD") } returns 0.0005

            service.triggerRateAlert("fx_alert_1")

            Then("it never notifies and never marks the alert triggered") {
                verify(exactly = 0) { notificationRepository.save(any()) }
                alert.alertTriggeredAt shouldBe null
            }
        }

        When("the real rate provider is currently unreachable") {
            alert.alertTriggeredAt = null
            every { rateClient.getRate("RWF", "USD") } returns null

            service.triggerRateAlert("fx_alert_1")

            Then("it honestly skips rather than fabricating a rate") {
                verify(exactly = 0) { notificationRepository.save(any()) }
                alert.alertTriggeredAt shouldBe null
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
