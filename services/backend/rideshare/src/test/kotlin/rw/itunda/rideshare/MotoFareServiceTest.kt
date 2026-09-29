package rw.itunda.rideshare

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.account.AutoTopUpService
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.Account
import rw.itunda.core.domain.AccountType
import rw.itunda.core.domain.CustomerPaymentCode
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.fraud.FraudRuleEngine
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerPostResult
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.AccountRepository
import rw.itunda.core.repository.CustomerPaymentCodeRepository
import rw.itunda.core.repository.MotoFareTripRepository
import java.math.BigDecimal
import java.time.Instant

/** First test coverage for the real "tap to pay your moto-taxi fare" flow -- see
 * MotoFareTrip.kt's own doc comment for the full sourced account. */
class MotoFareServiceTest : BehaviorSpec({

    fun account(id: String, userId: String, type: AccountType = AccountType.PAY, balance: String = "1000000") = Account(
        id = id, userId = userId, accountNumber = "ACC-$id", accountName = "Test account",
        type = type, balance = BigDecimal(balance), availableBalance = BigDecimal(balance),
    )

    fun paymentCode(userId: String, code: String = "abc123", usedAt: Instant? = null, expiresAt: Instant = Instant.now().plusSeconds(60)) =
        CustomerPaymentCode(id = "cpc_1", userId = userId, code = code, expiresAt = expiresAt, usedAt = usedAt)

    fun newService(
        motoFareTripRepository: MotoFareTripRepository = mockk(relaxed = true),
        accountRepository: AccountRepository = mockk(),
        customerPaymentCodeRepository: CustomerPaymentCodeRepository = mockk(),
        autoTopUpService: AutoTopUpService = mockk<AutoTopUpService>(relaxed = true).also {
            every { it.ensureSufficientPayBalance(any(), any(), any()) } answers { secondArg() }
        },
        ledgerService: LedgerService = mockk(),
        rateLimiter: RateLimiter = mockk(relaxed = true),
        fraudRuleEngine: FraudRuleEngine = mockk(relaxed = true),
    ) = MotoFareService(motoFareTripRepository, accountRepository, customerPaymentCodeRepository, autoTopUpService, ledgerService, rateLimiter, fraudRuleEngine)

    Given("a real driver tapping/scanning a rider's still-valid, unused payment code") {
        val motoFareTripRepository = mockk<MotoFareTripRepository>()
        val accountRepository = mockk<AccountRepository>()
        val customerPaymentCodeRepository = mockk<CustomerPaymentCodeRepository>()
        val ledgerService = mockk<LedgerService>()
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val riderAccount = account("rider_pay_1", "rider_1")
        val driverAccount = account("driver_pay_1", "driver_1")
        every { motoFareTripRepository.save(any()) } answers { firstArg() }
        every { customerPaymentCodeRepository.findByCode("abc123") } returns paymentCode("rider_1")
        every { customerPaymentCodeRepository.save(any()) } answers { firstArg() }
        every { accountRepository.findByUserIdAndType("rider_1", AccountType.PAY) } returns riderAccount
        every { accountRepository.findByUserIdAndType("driver_1", AccountType.PAY) } returns driverAccount
        val legsSlot = mutableListOf<List<LedgerLeg>>()
        every { ledgerService.postLedgerTransaction("RWF", capture(legsSlot)) } returns LedgerPostResult("ledgertxn_1", emptyList())
        val service = newService(motoFareTripRepository = motoFareTripRepository, accountRepository = accountRepository, customerPaymentCodeRepository = customerPaymentCodeRepository, ledgerService = ledgerService, fraudRuleEngine = fraudRuleEngine)

        When("the driver collects a real, in-range fare") {
            val result = service.collectFare("driver_1", "abc123", BigDecimal("1500"))

            Then("it real-posts a direct WALLET-to-WALLET payment (rider debited, driver credited) and real-consumes the code") {
                result.fare shouldBe BigDecimal("1500")
                val legs = legsSlot.first()
                legs.size shouldBe 2
                legs.first { it.accountId == "rider_pay_1" }.direction shouldBe LedgerDirection.DEBIT
                legs.first { it.accountId == "driver_pay_1" }.direction shouldBe LedgerDirection.CREDIT
                legs.all { it.accountType == LedgerAccountType.WALLET } shouldBe true
                verify(exactly = 1) { customerPaymentCodeRepository.save(match { it.usedAt != null }) }
                verify(exactly = 1) { fraudRuleEngine.evaluate("rider_1", "driver_1", BigDecimal("1500"), "ledgertxn_1") }
            }
        }
    }

    Given("a real fare outside Kigali's real sourced 400-6000 RWF range") {
        val service = newService()

        When("a driver tries to collect it") {
            Then("it real-400s before ever touching a code or the ledger") {
                try {
                    service.collectFare("driver_1", "abc123", BigDecimal("50"))
                    error("expected MotoFareInvalidFareException")
                } catch (_: MotoFareInvalidFareException) {
                    // expected
                }
            }
        }
    }

    Given("a real payment code that doesn't exist") {
        val customerPaymentCodeRepository = mockk<CustomerPaymentCodeRepository>()
        every { customerPaymentCodeRepository.findByCode("ghost") } returns null
        val service = newService(customerPaymentCodeRepository = customerPaymentCodeRepository)

        When("a driver tries to collect it") {
            Then("it real-404s rather than silently no-oping") {
                try {
                    service.collectFare("driver_1", "ghost", BigDecimal("1000"))
                    error("expected MotoFareCodeNotFoundException")
                } catch (_: MotoFareCodeNotFoundException) {
                    // expected
                }
            }
        }
    }

    Given("a real payment code already used by a previous driver") {
        val customerPaymentCodeRepository = mockk<CustomerPaymentCodeRepository>()
        every { customerPaymentCodeRepository.findByCode("abc123") } returns paymentCode("rider_1", usedAt = Instant.now())
        val service = newService(customerPaymentCodeRepository = customerPaymentCodeRepository)

        When("a second driver tries to collect the same code") {
            Then("it real-blocks the replay") {
                try {
                    service.collectFare("driver_1", "abc123", BigDecimal("1000"))
                    error("expected MotoFareCodeNotPayableException")
                } catch (_: MotoFareCodeNotPayableException) {
                    // expected
                }
            }
        }
    }

    Given("a real driver's own payment code") {
        val customerPaymentCodeRepository = mockk<CustomerPaymentCodeRepository>()
        every { customerPaymentCodeRepository.findByCode("abc123") } returns paymentCode("driver_1")
        val service = newService(customerPaymentCodeRepository = customerPaymentCodeRepository)

        When("that same driver tries to collect their own code") {
            Then("it real-blocks self-collection") {
                try {
                    service.collectFare("driver_1", "abc123", BigDecimal("1000"))
                    error("expected MotoFareSelfCollectionException")
                } catch (_: MotoFareSelfCollectionException) {
                    // expected
                }
            }
        }
    }

    Given("a real driver with no itunda account to receive the fare") {
        val accountRepository = mockk<AccountRepository>()
        val customerPaymentCodeRepository = mockk<CustomerPaymentCodeRepository>()
        every { customerPaymentCodeRepository.findByCode("abc123") } returns paymentCode("rider_1")
        every { accountRepository.findByUserIdAndType("rider_1", AccountType.PAY) } returns account("rider_pay_1", "rider_1")
        every { accountRepository.findByUserIdAndType("driver_1", AccountType.PAY) } returns null
        every { accountRepository.findByUserIdAndType("driver_1", AccountType.MAIN) } returns null
        val service = newService(accountRepository = accountRepository, customerPaymentCodeRepository = customerPaymentCodeRepository)

        When("the driver tries to collect a fare anyway") {
            Then("it real-404s rather than silently dropping the money") {
                try {
                    service.collectFare("driver_1", "abc123", BigDecimal("1000"))
                    error("expected MotoFareNoAccountException")
                } catch (_: MotoFareNoAccountException) {
                    // expected
                }
            }
        }
    }
})
