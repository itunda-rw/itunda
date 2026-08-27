package rw.itunda.transit

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.Account
import rw.itunda.core.domain.AccountType
import rw.itunda.core.domain.CustomerPaymentCode
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.TransitBalance
import rw.itunda.core.domain.TransitOperator
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerPostResult
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.AccountRepository
import rw.itunda.core.repository.CustomerPaymentCodeRepository
import rw.itunda.core.repository.TransitBalanceRepository
import rw.itunda.core.repository.TransitTripRepository
import java.math.BigDecimal
import java.time.Instant
import java.util.Optional

/** First test coverage for the real Kigali transit stored-value balance -- see
 * TransitBalance.kt's own doc comment for the full sourced account. */
class TransitServiceTest : BehaviorSpec({

    fun account(id: String, userId: String) = Account(
        id = id, userId = userId, accountNumber = "ACC-$id", accountName = "Test account",
        type = AccountType.MAIN, balance = BigDecimal("1000000"), availableBalance = BigDecimal("1000000"),
    )

    fun freshBalance(userId: String, amount: BigDecimal = BigDecimal.ZERO) = TransitBalance(
        id = "transit_1", userId = userId, balance = amount,
    )

    fun newService(
        transitBalanceRepository: TransitBalanceRepository = mockk(),
        transitTripRepository: TransitTripRepository = mockk(),
        accountRepository: AccountRepository = mockk(),
        customerPaymentCodeRepository: CustomerPaymentCodeRepository = mockk(),
        ledgerService: LedgerService = mockk(),
        rateLimiter: RateLimiter = mockk(relaxed = true),
    ) = TransitService(transitBalanceRepository, transitTripRepository, accountRepository, customerPaymentCodeRepository, ledgerService, rateLimiter)

    Given("a real user topping up their transit balance for the first time") {
        val transitBalanceRepository = mockk<TransitBalanceRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        every { transitBalanceRepository.findByUserId("user_1") } returns null
        val savedSlot = mutableListOf<TransitBalance>()
        every { transitBalanceRepository.save(capture(savedSlot)) } answers { firstArg() }
        every { transitBalanceRepository.findByIdForUpdate(any()) } answers { Optional.of(savedSlot.first()) }
        every { accountRepository.findByUserIdAndType("user_1", AccountType.MAIN) } returns account("account_1", "user_1")
        val legsSlot = mutableListOf<List<LedgerLeg>>()
        every { ledgerService.postLedgerTransaction("RWF", capture(legsSlot)) } returns LedgerPostResult("ledgertxn_1", emptyList())
        val service = newService(transitBalanceRepository = transitBalanceRepository, accountRepository = accountRepository, ledgerService = ledgerService)

        When("topping up 2000 RWF") {
            val result = service.topUp("user_1", BigDecimal("2000"))

            Then("it real-creates the balance and posts a balanced WALLET debit / TRANSIT_BALANCE_PAYABLE credit ledger transaction") {
                val legs = legsSlot.first()
                legs.size shouldBe 2
                legs.first { it.accountType == LedgerAccountType.WALLET }.direction shouldBe LedgerDirection.DEBIT
                legs.first { it.accountType == LedgerAccountType.TRANSIT_BALANCE_PAYABLE }.direction shouldBe LedgerDirection.CREDIT
                result.balance shouldBe BigDecimal("2000")
            }
        }
    }

    Given("a real user topping up a zero or negative amount") {
        val service = newService()

        When("topping up zero") {
            Then("it real-400s rather than posting an empty ledger transaction") {
                try {
                    service.topUp("user_1", BigDecimal.ZERO)
                    error("expected TransitInvalidAmountException")
                } catch (_: TransitInvalidAmountException) {
                    // expected
                }
            }
        }
    }

    Given("a real transit balance with 1000 RWF") {
        val transitBalanceRepository = mockk<TransitBalanceRepository>()
        val transitTripRepository = mockk<TransitTripRepository>()
        val ledgerService = mockk<LedgerService>()
        val balance = freshBalance("user_1", BigDecimal("1000"))
        every { transitBalanceRepository.findByUserId("user_1") } returns balance
        every { transitBalanceRepository.findByIdForUpdate("transit_1") } returns Optional.of(balance)
        every { transitBalanceRepository.save(any()) } answers { firstArg() }
        every { transitTripRepository.save(any()) } answers { firstArg() }
        val legsSlot = mutableListOf<List<LedgerLeg>>()
        every { ledgerService.postLedgerTransaction("RWF", capture(legsSlot)) } returns LedgerPostResult("ledgertxn_1", emptyList())
        val service = newService(
            transitBalanceRepository = transitBalanceRepository, transitTripRepository = transitTripRepository, ledgerService = ledgerService,
        )

        When("tapping a real, in-range fare with a real operator") {
            val result = service.tapFare("user_1", TransitOperator.KIGALI_BUS_SERVICES, BigDecimal("300"))

            Then("it real-deducts the fare and posts a balanced TRANSIT_BALANCE_PAYABLE debit / TRANSIT_FARE_EXPENSE credit ledger transaction") {
                val legs = legsSlot.first()
                legs.size shouldBe 2
                legs.first { it.accountType == LedgerAccountType.TRANSIT_BALANCE_PAYABLE }.direction shouldBe LedgerDirection.DEBIT
                legs.first { it.accountType == LedgerAccountType.TRANSIT_FARE_EXPENSE }.direction shouldBe LedgerDirection.CREDIT
                result.trip.fare shouldBe BigDecimal("300")
                result.trip.operator shouldBe TransitOperator.KIGALI_BUS_SERVICES
                result.balance.balance shouldBe BigDecimal("700")
            }
        }

        When("tapping with an operator itunda doesn't real-support") {
            Then("it real-400s rather than silently accepting a fabricated operator") {
                try {
                    service.tapFare("user_1", "Some Random Bus Co", BigDecimal("300"))
                    error("expected TransitInvalidOperatorException")
                } catch (_: TransitInvalidOperatorException) {
                    // expected
                }
            }
        }

        When("tapping with a fare outside Kigali's real sourced 200-500 RWF range") {
            Then("it real-400s rather than accepting a fabricated fare") {
                try {
                    service.tapFare("user_1", TransitOperator.ROYAL_EXPRESS, BigDecimal("50000"))
                    error("expected TransitInvalidFareException")
                } catch (_: TransitInvalidFareException) {
                    // expected
                }
            }
        }
    }

    Given("a real transit balance too low for the fare") {
        val transitBalanceRepository = mockk<TransitBalanceRepository>()
        val balance = freshBalance("user_1", BigDecimal("100"))
        every { transitBalanceRepository.findByUserId("user_1") } returns balance
        every { transitBalanceRepository.findByIdForUpdate("transit_1") } returns Optional.of(balance)
        val service = newService(transitBalanceRepository = transitBalanceRepository)

        When("tapping a fare it can't cover") {
            Then("it real-blocks before ever touching the ledger") {
                try {
                    service.tapFare("user_1", TransitOperator.KIGALI_BUS_SERVICES, BigDecimal("200"))
                    error("expected TransitInsufficientBalanceException")
                } catch (_: TransitInsufficientBalanceException) {
                    // expected
                }
            }
        }
    }

    Given("a real user with no transit balance yet") {
        val transitBalanceRepository = mockk<TransitBalanceRepository>()
        every { transitBalanceRepository.findByUserId("user_1") } returns null
        val service = newService(transitBalanceRepository = transitBalanceRepository)

        When("tapping a fare") {
            Then("it real-404s rather than silently creating one mid-tap") {
                try {
                    service.tapFare("user_1", TransitOperator.KIGALI_BUS_SERVICES, BigDecimal("200"))
                    error("expected TransitNoAccountException")
                } catch (_: TransitNoAccountException) {
                    // expected
                }
            }
        }
    }

    fun paymentCode(userId: String, code: String = "abc123", usedAt: Instant? = null, expiresAt: Instant = Instant.now().plusSeconds(60)) =
        CustomerPaymentCode(id = "cpc_1", userId = userId, code = code, expiresAt = expiresAt, usedAt = usedAt)

    Given("a real collector reading a rider's still-valid, unused payment code via NFC or a QR scan") {
        val transitBalanceRepository = mockk<TransitBalanceRepository>()
        val transitTripRepository = mockk<TransitTripRepository>()
        val customerPaymentCodeRepository = mockk<CustomerPaymentCodeRepository>()
        val ledgerService = mockk<LedgerService>()
        val riderBalance = freshBalance("rider_1", BigDecimal("1000"))
        every { customerPaymentCodeRepository.findByCode("abc123") } returns paymentCode("rider_1")
        every { customerPaymentCodeRepository.save(any()) } answers { firstArg() }
        every { transitBalanceRepository.findByUserId("rider_1") } returns riderBalance
        every { transitBalanceRepository.findByIdForUpdate("transit_1") } returns Optional.of(riderBalance)
        every { transitBalanceRepository.save(any()) } answers { firstArg() }
        every { transitTripRepository.save(any()) } answers { firstArg() }
        every { ledgerService.postLedgerTransaction("RWF", any()) } returns LedgerPostResult("ledgertxn_1", emptyList())
        val service = newService(
            transitBalanceRepository = transitBalanceRepository, transitTripRepository = transitTripRepository,
            customerPaymentCodeRepository = customerPaymentCodeRepository, ledgerService = ledgerService,
        )

        When("the collector taps to collect a real, in-range fare") {
            val result = service.tapFareByCode("collector_1", "abc123", TransitOperator.ROYAL_EXPRESS, BigDecimal("300"))

            Then("it real-charges the RIDER's balance (not the collector's) and real-consumes the code") {
                result.fare shouldBe BigDecimal("300")
                result.operator shouldBe TransitOperator.ROYAL_EXPRESS
                riderBalance.balance shouldBe BigDecimal("700")
                verify(exactly = 1) { customerPaymentCodeRepository.save(match { it.usedAt != null }) }
            }
        }
    }

    Given("a real payment code that doesn't exist") {
        val customerPaymentCodeRepository = mockk<CustomerPaymentCodeRepository>()
        every { customerPaymentCodeRepository.findByCode("ghost") } returns null
        val service = newService(customerPaymentCodeRepository = customerPaymentCodeRepository)

        When("a collector tries to tap it") {
            Then("it real-404s rather than silently no-oping") {
                try {
                    service.tapFareByCode("collector_1", "ghost", TransitOperator.KIGALI_BUS_SERVICES, BigDecimal("200"))
                    error("expected TransitCodeNotFoundException")
                } catch (_: TransitCodeNotFoundException) {
                    // expected
                }
            }
        }
    }

    Given("a real payment code already used by a previous collector") {
        val customerPaymentCodeRepository = mockk<CustomerPaymentCodeRepository>()
        every { customerPaymentCodeRepository.findByCode("abc123") } returns paymentCode("rider_1", usedAt = Instant.now())
        val service = newService(customerPaymentCodeRepository = customerPaymentCodeRepository)

        When("a second collector tries to tap the same code") {
            Then("it real-blocks the replay") {
                try {
                    service.tapFareByCode("collector_1", "abc123", TransitOperator.KIGALI_BUS_SERVICES, BigDecimal("200"))
                    error("expected TransitCodeNotPayableException")
                } catch (_: TransitCodeNotPayableException) {
                    // expected
                }
            }
        }
    }

    Given("a real payment code that has expired") {
        val customerPaymentCodeRepository = mockk<CustomerPaymentCodeRepository>()
        every { customerPaymentCodeRepository.findByCode("abc123") } returns paymentCode("rider_1", expiresAt = Instant.now().minusSeconds(1))
        val service = newService(customerPaymentCodeRepository = customerPaymentCodeRepository)

        When("a collector tries to tap it") {
            Then("it real-blocks rather than accepting a stale code") {
                try {
                    service.tapFareByCode("collector_1", "abc123", TransitOperator.KIGALI_BUS_SERVICES, BigDecimal("200"))
                    error("expected TransitCodeNotPayableException")
                } catch (_: TransitCodeNotPayableException) {
                    // expected
                }
            }
        }
    }

    Given("a real user's own payment code") {
        val customerPaymentCodeRepository = mockk<CustomerPaymentCodeRepository>()
        every { customerPaymentCodeRepository.findByCode("abc123") } returns paymentCode("user_1")
        val service = newService(customerPaymentCodeRepository = customerPaymentCodeRepository)

        When("that same user tries to collect their own code") {
            Then("it real-blocks self-collection") {
                try {
                    service.tapFareByCode("user_1", "abc123", TransitOperator.KIGALI_BUS_SERVICES, BigDecimal("200"))
                    error("expected TransitSelfCollectionException")
                } catch (_: TransitSelfCollectionException) {
                    // expected
                }
            }
        }
    }
})
