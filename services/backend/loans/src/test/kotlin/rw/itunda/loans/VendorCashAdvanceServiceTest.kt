package rw.itunda.loans

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.LedgerEntry
import rw.itunda.core.domain.Merchant
import rw.itunda.core.domain.VendorCashAdvance
import rw.itunda.core.domain.VendorCashAdvanceStatus
import rw.itunda.core.domain.Account
import rw.itunda.core.domain.AccountType
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerPostResult
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.LedgerEntryRepository
import rw.itunda.core.repository.MerchantRepository
import rw.itunda.core.repository.VendorCashAdvanceRepository
import rw.itunda.core.repository.AccountRepository
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.util.Optional

/**
 * First test coverage for the real Isoko Vendor Cash Advance -- see
 * VendorCashAdvanceService's own doc comment for the full sourced account. The
 * double-create-race test mirrors VupLoanServiceTest's/MotoOwnershipServiceTest's own
 * such test, asserting the caller's account lock happened before the active-advance
 * check. The repayEarly-clamp test guards against the same overshoot-clamp regression
 * class found in InsuranceService.contributeToFund/VupLoanService.repay/
 * MotoOwnershipService.repay: it asserts the actual ledger leg amount, not just the
 * resulting field. The disburse test verifies the multi-leg transaction that mirrors
 * MotoOwnershipService.convertToLoan's own doc comment's "verify the debit/credit
 * directions net to the right final owed amount" discipline, with an explicit
 * debits-equal-credits invariant assertion.
 *
 * Kotest lesson from this session: sibling `When` blocks under the same `Given` share
 * ONE mutable entity created in the `Given` block -- a mutation in one `When` leaks
 * into siblings. Any test needing a distinct starting state gets its own separate
 * `Given` block, not a sibling `When`.
 */
class VendorCashAdvanceServiceTest : BehaviorSpec({

    fun account(id: String, userId: String, availableBalance: BigDecimal = BigDecimal("1000000")) = Account(
        id = id, userId = userId, accountNumber = "ACC-$id", accountName = "Test account",
        type = AccountType.MAIN, balance = availableBalance, availableBalance = availableBalance,
    )

    fun merchant(id: String, ownerUserId: String, accountId: String) = Merchant(
        id = id, ownerUserId = ownerUserId, accountId = accountId, businessName = "Test Vendor Stall",
    )

    fun settlementEntry(accountId: String, amount: BigDecimal, createdAt: Instant, memo: String = "QR collection - Vegetables") = LedgerEntry(
        id = "entry_${java.util.UUID.randomUUID()}", transactionId = "ledgertxn_${java.util.UUID.randomUUID()}",
        accountId = accountId, accountType = LedgerAccountType.WALLET, direction = LedgerDirection.CREDIT,
        amount = amount, currency = "RWF", balanceAfter = amount, memo = memo, createdAt = createdAt,
    )

    fun newService(
        vendorCashAdvanceRepository: VendorCashAdvanceRepository = mockk(),
        merchantRepository: MerchantRepository = mockk(),
        accountRepository: AccountRepository = mockk(),
        ledgerService: LedgerService = mockk(),
        ledgerEntryRepository: LedgerEntryRepository = mockk(),
        rateLimiter: RateLimiter = mockk(relaxed = true),
    ) = VendorCashAdvanceService(vendorCashAdvanceRepository, merchantRepository, accountRepository, ledgerService, ledgerEntryRepository, rateLimiter)

    Given("a merchant with fewer than 14 real trading days of settlement history") {
        val merchantRepository = mockk<MerchantRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerEntryRepository = mockk<LedgerEntryRepository>()
        val service = newService(merchantRepository = merchantRepository, accountRepository = accountRepository, ledgerEntryRepository = ledgerEntryRepository)

        val m = merchant("merchant_1", "user_1", "account_1")
        every { merchantRepository.findById("merchant_1") } returns Optional.of(m)
        every { accountRepository.findById("account_1") } returns Optional.of(account("account_1", "user_1"))
        val entries = (1..5).map { settlementEntry("account_1", BigDecimal("3000"), Instant.now().minus(java.time.Duration.ofDays(it.toLong()))) }
        every { ledgerEntryRepository.findByAccountIdAndCreatedAtAfter("account_1", any()) } returns entries

        When("requesting an offer") {
            val offer = service.getOffer("user_1", "merchant_1")

            Then("it real-ineligibles with an honest reason, not a fabricated offer") {
                offer["eligible"] shouldBe false
                offer["reason"] shouldBe "At least 14 days of real itunda-collected settlement history is required"
            }
        }
    }

    Given("a merchant with exactly 20 real distinct trading days of clean 3,000 RWF/day settlement history") {
        val merchantRepository = mockk<MerchantRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerEntryRepository = mockk<LedgerEntryRepository>()
        val service = newService(merchantRepository = merchantRepository, accountRepository = accountRepository, ledgerEntryRepository = ledgerEntryRepository)

        val m = merchant("merchant_2", "user_1", "account_2")
        every { merchantRepository.findById("merchant_2") } returns Optional.of(m)
        every { accountRepository.findById("account_2") } returns Optional.of(account("account_2", "user_1"))
        // 20 distinct calendar days, one real 3,000 RWF settlement credit each -- total
        // inflow 60,000 over the trailing 30 days => averageDailySettlement = 2,000.00
        // exactly, a clean fixture to hand-verify the offer math against.
        val entries = (1..20).map { settlementEntry("account_2", BigDecimal("3000"), Instant.now().minus(java.time.Duration.ofDays(it.toLong()))) }
        every { ledgerEntryRepository.findByAccountIdAndCreatedAtAfter("account_2", any()) } returns entries

        When("requesting an offer") {
            val offer = service.getOffer("user_1", "merchant_2")

            Then("the offer is computed honestly from the real summed inflow: 2,000/day average x 90 = 180,000 principal, 8% flat fee = 14,400") {
                offer["eligible"] shouldBe true
                offer["averageDailySettlement"] shouldBe BigDecimal("2000.00")
                offer["offerAmount"] shouldBe BigDecimal("180000.00")
                offer["feeAmount"] shouldBe BigDecimal("14400.00")
                offer["collectionRatePercent"] shouldBe 15.0
            }
        }
    }

    // Real bug found in this feature's own build-time review (2026-08-02): getOffer
    // originally took only merchantId, with no ownership check at all -- GET /offer
    // requires a valid JWT (not permitAll in SecurityConfig), but any OTHER
    // authenticated itunda user could still probe another merchant's real average
    // daily settlement/trading-day count/advance eligibility just by knowing their
    // merchantId, a genuine business-financial-data leak between unrelated users.
    Given("someone who doesn't own a merchant trying to view its real offer") {
        val merchantRepository = mockk<MerchantRepository>()
        val service = newService(merchantRepository = merchantRepository)

        val m = merchant("merchant_3", "owner_1", "account_3")
        every { merchantRepository.findById("merchant_3") } returns Optional.of(m)

        When("the attacker requests an offer for a merchant they don't own") {
            Then("it real-404s, never revealing that merchant_3's real settlement data exists") {
                shouldThrow<VendorCashAdvanceNotFoundException> { service.getOffer("attacker", "merchant_3") }
            }
        }
    }

    Given("a merchant who already has an active vendor cash advance") {
        val vendorCashAdvanceRepository = mockk<VendorCashAdvanceRepository>()
        val merchantRepository = mockk<MerchantRepository>()
        val accountRepository = mockk<AccountRepository>()
        val service = newService(vendorCashAdvanceRepository = vendorCashAdvanceRepository, merchantRepository = merchantRepository, accountRepository = accountRepository)

        val m = merchant("merchant_3", "user_1", "account_3")
        every { merchantRepository.findById("merchant_3") } returns Optional.of(m)
        val w = account("account_3", "user_1")
        every { accountRepository.findById("account_3") } returns Optional.of(w)
        every { accountRepository.findByIdForUpdate("account_3") } returns Optional.of(w)
        val existing = VendorCashAdvance(
            id = "vendoradv_existing", merchantId = "merchant_3", principalAmount = BigDecimal("100000"),
            feeAmount = BigDecimal("8000"), totalOwed = BigDecimal("108000"), remainingOwed = BigDecimal("108000"),
            collectionRatePercent = 15.0, status = VendorCashAdvanceStatus.DISBURSED,
        )
        every { vendorCashAdvanceRepository.findByMerchantIdAndStatusIn("merchant_3", any()) } returns listOf(existing)

        When("applying for a second advance") {
            Then("the one-active-advance guard fires, after locking the merchant's own account row first (closing the double-create race)") {
                shouldThrow<VendorCashAdvanceAlreadyActiveException> {
                    service.applyForAdvance("user_1", "merchant_3")
                }
                verify(exactly = 1) { accountRepository.findByIdForUpdate("account_3") }
            }
        }

        When("someone who doesn't own this merchant tries to apply (IDOR)") {
            Then("it real-404s, not 403s") {
                shouldThrow<VendorCashAdvanceNotFoundException> { service.applyForAdvance("attacker", "merchant_3") }
            }
        }
    }

    Given("a real REQUESTED vendor cash advance being disbursed") {
        val vendorCashAdvanceRepository = mockk<VendorCashAdvanceRepository>()
        val merchantRepository = mockk<MerchantRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val service = newService(
            vendorCashAdvanceRepository = vendorCashAdvanceRepository, merchantRepository = merchantRepository,
            accountRepository = accountRepository, ledgerService = ledgerService,
        )

        val m = merchant("merchant_4", "user_1", "account_4")
        val advance = VendorCashAdvance(
            id = "vendoradv_1", merchantId = "merchant_4", principalAmount = BigDecimal("180000"),
            feeAmount = BigDecimal("14400"), totalOwed = BigDecimal("194400"), remainingOwed = BigDecimal("194400"),
            collectionRatePercent = 15.0, status = VendorCashAdvanceStatus.REQUESTED,
        )
        every { vendorCashAdvanceRepository.findById("vendoradv_1") } returns Optional.of(advance)
        every { merchantRepository.findById("merchant_4") } returns Optional.of(m)
        every { accountRepository.findById("account_4") } returns Optional.of(account("account_4", "user_1"))
        every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_disburse", emptyList())
        every { vendorCashAdvanceRepository.save(any()) } answers { firstArg() }

        When("disbursing it") {
            val result = service.disburse("user_1", "vendoradv_1")

            Then("the advance moves to DISBURSED with a real disbursedAt timestamp") {
                result.status shouldBe VendorCashAdvanceStatus.DISBURSED
                (result.disbursedAt != null) shouldBe true
            }

            Then("a single balanced 4-leg transaction CREDITs the account for principal only, DEBITs loan_payable for principal AND fee separately, and CREDITs fee_revenue for the fee -- net loan_payable position equals totalOwed exactly") {
                val legsSlot = slot<List<LedgerLeg>>()
                verify(exactly = 1) { ledgerService.postLedgerTransaction(any(), capture(legsSlot)) }
                val legs = legsSlot.captured
                legs.size shouldBe 4
                legs.any { it.accountId == "account_4" && it.direction == LedgerDirection.CREDIT && it.amount == BigDecimal("180000") } shouldBe true
                legs.count { it.accountId == "loan_payable" && it.accountType == LedgerAccountType.LOAN_PAYABLE && it.direction == LedgerDirection.DEBIT } shouldBe 2
                legs.any { it.accountId == "loan_payable" && it.direction == LedgerDirection.DEBIT && it.amount == BigDecimal("180000") } shouldBe true
                legs.any { it.accountId == "loan_payable" && it.direction == LedgerDirection.DEBIT && it.amount == BigDecimal("14400") } shouldBe true
                legs.any { it.accountId == "fee_revenue" && it.accountType == LedgerAccountType.FEE_REVENUE && it.direction == LedgerDirection.CREDIT && it.amount == BigDecimal("14400") } shouldBe true

                val netLoanPayableDebit = legs.filter { it.accountId == "loan_payable" && it.direction == LedgerDirection.DEBIT }.sumOf { it.amount }
                netLoanPayableDebit shouldBe BigDecimal("194400")
                netLoanPayableDebit shouldBe advance.totalOwed

                val debits = legs.filter { it.direction == LedgerDirection.DEBIT }.sumOf { it.amount }
                val credits = legs.filter { it.direction == LedgerDirection.CREDIT }.sumOf { it.amount }
                debits shouldBe credits
                debits shouldBe BigDecimal("194400")
            }
        }

        When("someone who doesn't own this merchant tries to disburse it (IDOR)") {
            Then("it real-404s, not 403s") {
                shouldThrow<VendorCashAdvanceNotFoundException> { service.disburse("attacker", "vendoradv_1") }
            }
        }
    }

    Given("a real DISBURSED vendor cash advance being repaid early with an overshooting amount") {
        val vendorCashAdvanceRepository = mockk<VendorCashAdvanceRepository>()
        val merchantRepository = mockk<MerchantRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val service = newService(
            vendorCashAdvanceRepository = vendorCashAdvanceRepository, merchantRepository = merchantRepository,
            accountRepository = accountRepository, ledgerService = ledgerService, rateLimiter = rateLimiter,
        )

        val m = merchant("merchant_5", "user_1", "account_5")
        val advance = VendorCashAdvance(
            id = "vendoradv_2", merchantId = "merchant_5", principalAmount = BigDecimal("180000"),
            feeAmount = BigDecimal("14400"), totalOwed = BigDecimal("194400"), remainingOwed = BigDecimal("30000"),
            collectionRatePercent = 15.0, status = VendorCashAdvanceStatus.DISBURSED,
        )
        every { vendorCashAdvanceRepository.findById("vendoradv_2") } returns Optional.of(advance)
        every { merchantRepository.findById("merchant_5") } returns Optional.of(m)
        every { accountRepository.findById("account_5") } returns Optional.of(account("account_5", "user_1"))
        every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_repay", emptyList())
        every { vendorCashAdvanceRepository.save(any()) } answers { firstArg() }

        When("repaying 100,000 against a real 30,000 remaining balance") {
            val result = service.repayEarly("user_1", "vendoradv_2", BigDecimal("100000"))

            Then("the ledger only ever sees the real clamped 30,000, not the raw overshooting amount, and status becomes REPAID") {
                result.status shouldBe VendorCashAdvanceStatus.REPAID
                result.remainingOwed shouldBe BigDecimal.ZERO
                verify {
                    ledgerService.postLedgerTransaction(any(), match { legs ->
                        legs.all { it.amount == BigDecimal("30000") } &&
                            legs.any { it.accountId == "account_5" && it.direction == LedgerDirection.DEBIT } &&
                            legs.any { it.accountId == "loan_payable" && it.accountType == LedgerAccountType.LOAN_PAYABLE && it.direction == LedgerDirection.CREDIT }
                    })
                }
            }

            // Real gap found live (sibling comparison against LoansService.repayLoan/
            // OverdraftService.repay, 2026-09-13): repayEarly had no rate limit at all.
            Then("the real rate limiter is actually consulted, not just mocked away") {
                verify(exactly = 1) { rateLimiter.checkLimit("vendor-advance:repay-early:user_1", limit = 30, window = Duration.ofHours(1)) }
            }
        }

        When("someone who doesn't own this merchant tries to repay it (IDOR)") {
            Then("it real-404s, not 403s") {
                shouldThrow<VendorCashAdvanceNotFoundException> { service.repayEarly("attacker", "vendoradv_2", BigDecimal("1000")) }
            }
        }
    }

    Given("a real DISBURSED advance whose daily collection is capped by the collection rate x real inflow (the smallest of the three caps)") {
        val vendorCashAdvanceRepository = mockk<VendorCashAdvanceRepository>()
        val merchantRepository = mockk<MerchantRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val ledgerEntryRepository = mockk<LedgerEntryRepository>()
        val service = newService(
            vendorCashAdvanceRepository = vendorCashAdvanceRepository, merchantRepository = merchantRepository,
            accountRepository = accountRepository, ledgerService = ledgerService, ledgerEntryRepository = ledgerEntryRepository,
        )

        val m = merchant("merchant_6", "user_1", "account_6")
        val w = account("account_6", "user_1", availableBalance = BigDecimal("500000"))
        val advance = VendorCashAdvance(
            id = "vendoradv_3", merchantId = "merchant_6", principalAmount = BigDecimal("180000"),
            feeAmount = BigDecimal("14400"), totalOwed = BigDecimal("194400"), remainingOwed = BigDecimal("100000"),
            collectionRatePercent = 15.0, status = VendorCashAdvanceStatus.DISBURSED, disbursedAt = Instant.now().minus(java.time.Duration.ofDays(2)),
        )
        every { vendorCashAdvanceRepository.findByIdForUpdate("vendoradv_3") } returns Optional.of(advance)
        every { merchantRepository.findById("merchant_6") } returns Optional.of(m)
        every { accountRepository.findById("account_6") } returns Optional.of(w)
        // Real inflow of 10,000 since last collection -- 15% of that is 1,500, far below
        // both remainingOwed (100,000) and accountBalance (500,000).
        every { ledgerEntryRepository.findByAccountIdAndCreatedAtAfter("account_6", any()) } returns
            listOf(settlementEntry("account_6", BigDecimal("10000"), Instant.now()))
        every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_collect1", emptyList())
        every { vendorCashAdvanceRepository.save(any()) } answers { firstArg() }

        When("running the daily collection") {
            val collected = service.runDailyCollection(advance)

            Then("only the real rate-share of inflow (1,500) is collected, not the full remainingOwed or accountBalance") {
                collected shouldBe true
                advance.remainingOwed shouldBe BigDecimal("98500.00")
                verify {
                    ledgerService.postLedgerTransaction(any(), match { legs -> legs.all { it.amount == BigDecimal("1500.00") } })
                }
            }
        }
    }

    Given("a real DISBURSED advance whose daily collection is capped by remainingOwed (real inflow would otherwise overpay it)") {
        val vendorCashAdvanceRepository = mockk<VendorCashAdvanceRepository>()
        val merchantRepository = mockk<MerchantRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val ledgerEntryRepository = mockk<LedgerEntryRepository>()
        val service = newService(
            vendorCashAdvanceRepository = vendorCashAdvanceRepository, merchantRepository = merchantRepository,
            accountRepository = accountRepository, ledgerService = ledgerService, ledgerEntryRepository = ledgerEntryRepository,
        )

        val m = merchant("merchant_7", "user_1", "account_7")
        val w = account("account_7", "user_1", availableBalance = BigDecimal("500000"))
        val advance = VendorCashAdvance(
            id = "vendoradv_4", merchantId = "merchant_7", principalAmount = BigDecimal("180000"),
            feeAmount = BigDecimal("14400"), totalOwed = BigDecimal("194400"), remainingOwed = BigDecimal("50000"),
            collectionRatePercent = 15.0, status = VendorCashAdvanceStatus.DISBURSED, disbursedAt = Instant.now().minus(java.time.Duration.ofDays(2)),
        )
        every { vendorCashAdvanceRepository.findByIdForUpdate("vendoradv_4") } returns Optional.of(advance)
        every { merchantRepository.findById("merchant_7") } returns Optional.of(m)
        every { accountRepository.findById("account_7") } returns Optional.of(w)
        // Real inflow of 1,000,000 -- 15% of that is 150,000, which would overpay the
        // real 50,000 remainingOwed if not capped.
        every { ledgerEntryRepository.findByAccountIdAndCreatedAtAfter("account_7", any()) } returns
            listOf(settlementEntry("account_7", BigDecimal("1000000"), Instant.now()))
        every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_collect2", emptyList())
        every { vendorCashAdvanceRepository.save(any()) } answers { firstArg() }

        When("running the daily collection") {
            val collected = service.runDailyCollection(advance)

            Then("collection is capped at exactly the real remainingOwed, and the advance moves DISBURSED -> REPAID at zero") {
                collected shouldBe true
                advance.remainingOwed shouldBe BigDecimal.ZERO
                advance.status shouldBe VendorCashAdvanceStatus.REPAID
                (advance.repaidAt != null) shouldBe true
                verify {
                    ledgerService.postLedgerTransaction(any(), match { legs -> legs.all { it.amount == BigDecimal("50000") } })
                }
            }
        }
    }

    Given("a real DISBURSED advance whose daily collection is capped by the merchant's own current account balance") {
        val vendorCashAdvanceRepository = mockk<VendorCashAdvanceRepository>()
        val merchantRepository = mockk<MerchantRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val ledgerEntryRepository = mockk<LedgerEntryRepository>()
        val service = newService(
            vendorCashAdvanceRepository = vendorCashAdvanceRepository, merchantRepository = merchantRepository,
            accountRepository = accountRepository, ledgerService = ledgerService, ledgerEntryRepository = ledgerEntryRepository,
        )

        val m = merchant("merchant_8", "user_1", "account_8")
        // A briefly thin account -- e.g. the merchant already moved most of it out --
        // far below what the rate-share of inflow or remainingOwed alone would allow.
        val w = account("account_8", "user_1", availableBalance = BigDecimal("2000"))
        val advance = VendorCashAdvance(
            id = "vendoradv_5", merchantId = "merchant_8", principalAmount = BigDecimal("180000"),
            feeAmount = BigDecimal("14400"), totalOwed = BigDecimal("194400"), remainingOwed = BigDecimal("100000"),
            collectionRatePercent = 15.0, status = VendorCashAdvanceStatus.DISBURSED, disbursedAt = Instant.now().minus(java.time.Duration.ofDays(2)),
        )
        every { vendorCashAdvanceRepository.findByIdForUpdate("vendoradv_5") } returns Optional.of(advance)
        every { merchantRepository.findById("merchant_8") } returns Optional.of(m)
        every { accountRepository.findById("account_8") } returns Optional.of(w)
        every { ledgerEntryRepository.findByAccountIdAndCreatedAtAfter("account_8", any()) } returns
            listOf(settlementEntry("account_8", BigDecimal("1000000"), Instant.now()))
        every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_collect3", emptyList())
        every { vendorCashAdvanceRepository.save(any()) } answers { firstArg() }

        When("running the daily collection") {
            val collected = service.runDailyCollection(advance)

            Then("collection never pushes the account negative -- it's capped at exactly the real current account balance (2,000)") {
                collected shouldBe true
                advance.remainingOwed shouldBe BigDecimal("98000")
                verify {
                    ledgerService.postLedgerTransaction(any(), match { legs -> legs.all { it.amount == BigDecimal("2000") } })
                }
            }
        }
    }

    Given("a real DISBURSED advance with zero real itunda-collected settlement inflow since the last collection") {
        val vendorCashAdvanceRepository = mockk<VendorCashAdvanceRepository>()
        val merchantRepository = mockk<MerchantRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerEntryRepository = mockk<LedgerEntryRepository>()
        val service = newService(
            vendorCashAdvanceRepository = vendorCashAdvanceRepository, merchantRepository = merchantRepository,
            accountRepository = accountRepository, ledgerEntryRepository = ledgerEntryRepository,
        )

        val m = merchant("merchant_9", "user_1", "account_9")
        val w = account("account_9", "user_1")
        val advance = VendorCashAdvance(
            id = "vendoradv_6", merchantId = "merchant_9", principalAmount = BigDecimal("180000"),
            feeAmount = BigDecimal("14400"), totalOwed = BigDecimal("194400"), remainingOwed = BigDecimal("100000"),
            collectionRatePercent = 15.0, status = VendorCashAdvanceStatus.DISBURSED, disbursedAt = Instant.now().minus(java.time.Duration.ofDays(2)),
        )
        every { vendorCashAdvanceRepository.findByIdForUpdate("vendoradv_6") } returns Optional.of(advance)
        every { merchantRepository.findById("merchant_9") } returns Optional.of(m)
        every { accountRepository.findById("account_9") } returns Optional.of(w)
        every { ledgerEntryRepository.findByAccountIdAndCreatedAtAfter("account_9", any()) } returns emptyList()

        When("running the daily collection") {
            val collected = service.runDailyCollection(advance)

            Then("it skips honestly rather than posting a zero/negative ledger leg") {
                collected shouldBe false
                advance.remainingOwed shouldBe BigDecimal("100000")
            }
        }
    }
})
