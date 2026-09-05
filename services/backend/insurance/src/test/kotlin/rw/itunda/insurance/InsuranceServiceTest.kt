package rw.itunda.insurance

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.InsuranceClaim
import rw.itunda.core.domain.InsuranceClaimStatus
import rw.itunda.core.domain.InsurancePolicy
import rw.itunda.core.domain.InsurancePremiumFund
import rw.itunda.core.domain.InsurancePremiumFundStatus
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.Notification
import rw.itunda.core.domain.Account
import rw.itunda.core.domain.AccountType
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerPostResult
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.InsuranceClaimRepository
import rw.itunda.core.repository.InsurancePolicyRepository
import rw.itunda.core.repository.InsurancePremiumFundRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.AccountRepository
import java.math.BigDecimal
import java.time.Duration
import java.time.LocalDate
import java.util.Optional

/**
 * First test coverage for insurance -- specifically a regression guard for a real bug
 * fixed earlier this same session: enrollInPlan used to call a *different*, mocked
 * LedgerService (getAccount() fabricated a random account, .save() calls were
 * commented out) so premium debits silently never happened despite the parity matrix
 * describing this flow as ledger-backed. It now posts through the real
 * rw.itunda.core.ledger.LedgerService -- this file asserts the actual ledger legs it
 * posts, not just that *a* ledger call happens.
 */
class InsuranceServiceTest : BehaviorSpec({

    fun account(id: String, userId: String, type: AccountType, balance: BigDecimal = BigDecimal("100000")) = Account(
        id = id, userId = userId, accountNumber = "ACC-$id", accountName = "Test account",
        type = type, balance = balance, availableBalance = balance,
    )

    fun premiumFund(id: String, userId: String, policyId: String, targetAmount: BigDecimal, currentAmount: BigDecimal, dailyContribution: BigDecimal = BigDecimal.ZERO) = InsurancePremiumFund(
        id = id, userId = userId, policyId = policyId, targetAmount = targetAmount, currentAmount = currentAmount, dailyContribution = dailyContribution,
    )

    Given("a user with a MAIN account enrolling in a real plan") {
        val insurancePolicyRepository = mockk<InsurancePolicyRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val insuranceClaimRepository = mockk<InsuranceClaimRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val insurancePremiumFundRepository = mockk<InsurancePremiumFundRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = InsuranceService(insurancePolicyRepository, accountRepository, ledgerService, insuranceClaimRepository, rateLimiter, insurancePremiumFundRepository, notificationRepository, pushNotificationService)

        every { accountRepository.findByUserId("user_1") } returns listOf(
            account("account_main", "user_1", AccountType.MAIN),
            account("account_savings", "user_1", AccountType.SAVINGS),
        )
        val legsSlot = slot<List<LedgerLeg>>()
        every { ledgerService.postLedgerTransaction("RWF", capture(legsSlot)) } returns LedgerPostResult("ledgertxn_1", emptyList())
        every { insurancePolicyRepository.save(any()) } answers { firstArg() }

        When("enrolling in Health Shield (15,000 RWF/month)") {
            val policy = service.enrollInPlan("user_1", "ins_1")

            Then("it debits the MAIN account specifically, not just any account the user has") {
                val accountLeg = legsSlot.captured.first { it.accountType == LedgerAccountType.WALLET }
                accountLeg.accountId shouldBe "account_main"
                accountLeg.direction shouldBe LedgerDirection.DEBIT
                accountLeg.amount shouldBe BigDecimal("15000")
            }
            Then("it credits insurance_premium_revenue for the exact same amount -- this is the real ledger, not the fake one this session found and deleted") {
                val revenueLeg = legsSlot.captured.first { it.accountType == LedgerAccountType.INSURANCE_PREMIUM_REVENUE }
                revenueLeg.accountId shouldBe "insurance_premium_revenue"
                revenueLeg.direction shouldBe LedgerDirection.CREDIT
                revenueLeg.amount shouldBe BigDecimal("15000")
                verify(exactly = 1) { ledgerService.postLedgerTransaction("RWF", any()) }
            }
            Then("it creates a real, saved policy tied to the plan") {
                policy.planName shouldBe "Health Shield"
                policy.category shouldBe "health"
                policy.status shouldBe "active"
                verify(exactly = 1) { insurancePolicyRepository.save(any()) }
            }
        }

        When("enrolling in a plan that doesn't exist") {
            Then("it throws PlanNotFoundException before touching the ledger") {
                try {
                    service.enrollInPlan("user_1", "ins_does_not_exist")
                    error("expected PlanNotFoundException")
                } catch (e: PlanNotFoundException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }
    }

    Given("a user with no accounts at all") {
        val insurancePolicyRepository = mockk<InsurancePolicyRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val insuranceClaimRepository = mockk<InsuranceClaimRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val insurancePremiumFundRepository = mockk<InsurancePremiumFundRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = InsuranceService(insurancePolicyRepository, accountRepository, ledgerService, insuranceClaimRepository, rateLimiter, insurancePremiumFundRepository, notificationRepository, pushNotificationService)

        every { accountRepository.findByUserId("user_2") } returns emptyList()

        When("enrolling in a plan") {
            Then("it throws NoAccountException before touching the ledger") {
                try {
                    service.enrollInPlan("user_2", "ins_1")
                    error("expected NoAccountException")
                } catch (e: NoAccountException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }
    }

    fun policy(id: String, userId: String, status: String = "active") = InsurancePolicy(
        id = id, userId = userId, planId = "ins_1", planName = "Health Shield", category = "health",
        status = status, startDate = LocalDate.now(), endDate = LocalDate.now().plusYears(1),
        monthlyPremium = BigDecimal("15000"), nextPaymentDate = LocalDate.now(), policyNumber = "POL-1",
    )

    Given("a user with a real active policy filing a real claim") {
        val insurancePolicyRepository = mockk<InsurancePolicyRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val insuranceClaimRepository = mockk<InsuranceClaimRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val insurancePremiumFundRepository = mockk<InsurancePremiumFundRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = InsuranceService(insurancePolicyRepository, accountRepository, ledgerService, insuranceClaimRepository, rateLimiter, insurancePremiumFundRepository, notificationRepository, pushNotificationService)

        every { insurancePolicyRepository.findById("pol_1") } returns Optional.of(policy("pol_1", "user_1"))
        every { insuranceClaimRepository.save(any()) } answers { firstArg() }

        When("filing a claim against their own active policy") {
            val claim = service.submitClaim("user_1", "pol_1", "Hospital stay", BigDecimal("50000"))

            Then("it creates a real SUBMITTED claim, not auto-approved") {
                claim.status shouldBe InsuranceClaimStatus.SUBMITTED
                claim.amount shouldBe BigDecimal("50000")
                verify(exactly = 1) { insuranceClaimRepository.save(any()) }
            }
        }
    }

    Given("a user trying to claim against someone else's policy") {
        val insurancePolicyRepository = mockk<InsurancePolicyRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val insuranceClaimRepository = mockk<InsuranceClaimRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val insurancePremiumFundRepository = mockk<InsurancePremiumFundRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = InsuranceService(insurancePolicyRepository, accountRepository, ledgerService, insuranceClaimRepository, rateLimiter, insurancePremiumFundRepository, notificationRepository, pushNotificationService)

        every { insurancePolicyRepository.findById("pol_2") } returns Optional.of(policy("pol_2", "owner_1"))

        When("attacker files a claim against it") {
            Then("it throws PolicyNotFoundException -- same 404-not-403 pattern used everywhere else") {
                try {
                    service.submitClaim("attacker", "pol_2", "fake claim", BigDecimal("1000"))
                    error("expected PolicyNotFoundException")
                } catch (e: PolicyNotFoundException) {
                    verify(exactly = 0) { insuranceClaimRepository.save(any()) }
                }
            }
        }
    }

    Given("a user trying to claim against a completed (non-active) policy") {
        val insurancePolicyRepository = mockk<InsurancePolicyRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val insuranceClaimRepository = mockk<InsuranceClaimRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val insurancePremiumFundRepository = mockk<InsurancePremiumFundRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = InsuranceService(insurancePolicyRepository, accountRepository, ledgerService, insuranceClaimRepository, rateLimiter, insurancePremiumFundRepository, notificationRepository, pushNotificationService)

        every { insurancePolicyRepository.findById("pol_3") } returns Optional.of(policy("pol_3", "user_1", status = "lapsed"))

        When("filing a claim") {
            Then("it throws PolicyNotActiveException") {
                try {
                    service.submitClaim("user_1", "pol_3", "test", BigDecimal("1000"))
                    error("expected PolicyNotActiveException")
                } catch (e: PolicyNotActiveException) {
                    verify(exactly = 0) { insuranceClaimRepository.save(any()) }
                }
            }
        }
    }

    Given("an ADMIN approving a real pending claim") {
        val insurancePolicyRepository = mockk<InsurancePolicyRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val insuranceClaimRepository = mockk<InsuranceClaimRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val insurancePremiumFundRepository = mockk<InsurancePremiumFundRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = InsuranceService(insurancePolicyRepository, accountRepository, ledgerService, insuranceClaimRepository, rateLimiter, insurancePremiumFundRepository, notificationRepository, pushNotificationService)
        every { notificationRepository.save(any()) } answers { firstArg() }

        val claim = InsuranceClaim(id = "claim_1", policyId = "pol_1", userId = "user_1", description = "Hospital stay", amount = BigDecimal("50000"))
        every { insuranceClaimRepository.findById("claim_1") } returns Optional.of(claim)
        every { insuranceClaimRepository.save(any()) } answers { firstArg() }
        every { accountRepository.findByUserIdAndType("user_1", AccountType.MAIN) } returns account("account_1", "user_1", AccountType.MAIN)
        val legsSlot = slot<List<LedgerLeg>>()
        every { ledgerService.postLedgerTransaction("RWF", capture(legsSlot)) } returns LedgerPostResult("ledgertxn_claim", emptyList())

        When("approving it") {
            val decided = service.decideClaim("claim_1", "admin_1", approve = true, reason = null)

            Then("it pays out from insurance_claims_expense straight into the claimant's real account") {
                val expenseLeg = legsSlot.captured.first { it.accountType == LedgerAccountType.INSURANCE_CLAIMS_EXPENSE }
                expenseLeg.direction shouldBe LedgerDirection.DEBIT
                expenseLeg.amount shouldBe BigDecimal("50000")
                val accountLeg = legsSlot.captured.first { it.accountType == LedgerAccountType.WALLET }
                accountLeg.accountId shouldBe "account_1"
                accountLeg.direction shouldBe LedgerDirection.CREDIT
                accountLeg.amount shouldBe BigDecimal("50000")
            }
            Then("the claim is marked APPROVED with a real reviewer") {
                decided.status shouldBe InsuranceClaimStatus.APPROVED
                decided.reviewedBy shouldBe "admin_1"
            }
            Then("it real-notifies the claimant that their claim was approved and paid") {
                verify(exactly = 1) {
                    notificationRepository.save(
                        match { it.userId == "user_1" && it.type == "INSURANCE_CLAIM_DECIDED" && it.body.contains("50000") },
                    )
                }
            }
            Then("the claimant also gets a real mobile push notification, not just the in-app one") {
                verify(exactly = 1) { pushNotificationService.sendToUser("user_1", "Claim approved", any(), mapOf("claimId" to "claim_1")) }
            }
        }
    }

    Given("an ADMIN rejecting a real pending claim") {
        val insurancePolicyRepository = mockk<InsurancePolicyRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val insuranceClaimRepository = mockk<InsuranceClaimRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val insurancePremiumFundRepository = mockk<InsurancePremiumFundRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = InsuranceService(insurancePolicyRepository, accountRepository, ledgerService, insuranceClaimRepository, rateLimiter, insurancePremiumFundRepository, notificationRepository, pushNotificationService)
        every { notificationRepository.save(any()) } answers { firstArg() }

        val claim = InsuranceClaim(id = "claim_2", policyId = "pol_1", userId = "user_1", description = "test", amount = BigDecimal("10000"))
        every { insuranceClaimRepository.findById("claim_2") } returns Optional.of(claim)
        every { insuranceClaimRepository.save(any()) } answers { firstArg() }

        When("rejecting it") {
            val decided = service.decideClaim("claim_2", "admin_1", approve = false, reason = "Not covered by policy")

            Then("it never touches the ledger or the claimant's account") {
                decided.status shouldBe InsuranceClaimStatus.REJECTED
                decided.decisionReason shouldBe "Not covered by policy"
                verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                verify(exactly = 0) { accountRepository.findByUserIdAndType(any(), any()) }
            }
            Then("it still real-notifies the claimant, with the real rejection reason included") {
                verify(exactly = 1) {
                    notificationRepository.save(
                        match { it.userId == "user_1" && it.type == "INSURANCE_CLAIM_DECIDED" && it.body.contains("Not covered by policy") },
                    )
                }
                verify(exactly = 1) { pushNotificationService.sendToUser("user_1", "Claim rejected", any(), mapOf("claimId" to "claim_2")) }
            }
        }

        When("rejecting with a reason over 255 characters") {
            Then("it throws InvalidClaimDecisionReasonException before ever saving or touching the ledger") {
                try {
                    service.decideClaim("claim_2", "admin_1", approve = false, reason = "x".repeat(256))
                    error("expected InvalidClaimDecisionReasonException")
                } catch (e: InvalidClaimDecisionReasonException) {
                    verify(exactly = 0) { insuranceClaimRepository.save(any()) }
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }
    }

    Given("a claim decision made while still inside its own real transaction") {
        val insurancePolicyRepository = mockk<InsurancePolicyRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val insuranceClaimRepository = mockk<InsuranceClaimRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val insurancePremiumFundRepository = mockk<InsurancePremiumFundRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = InsuranceService(insurancePolicyRepository, accountRepository, ledgerService, insuranceClaimRepository, rateLimiter, insurancePremiumFundRepository, notificationRepository, pushNotificationService)
        // relaxed=true mishandles JpaRepository's generic `<S extends T> S save(S)` and
        // returns a raw Object, ClassCastException-ing at the call site -- same fix as
        // RewardsServiceTest's rewardClaimRepository.save stub.
        every { notificationRepository.save(any()) } answers { firstArg() }

        val claim = InsuranceClaim(id = "claim_3", policyId = "pol_1", userId = "user_1", description = "Car repair", amount = BigDecimal("20000"))
        every { insuranceClaimRepository.findById("claim_3") } returns Optional.of(claim)
        every { insuranceClaimRepository.save(any()) } answers { firstArg() }
        every { accountRepository.findByUserIdAndType("user_1", AccountType.MAIN) } returns account("account_1", "user_1", AccountType.MAIN)
        every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_claim3", emptyList())

        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization()
        try {
            service.decideClaim("claim_3", "admin_1", approve = true, reason = null)

            Then("the real decision and durable in-app notification are recorded, but the mobile push is withheld") {
                verify(exactly = 1) { insuranceClaimRepository.save(any()) }
                verify(exactly = 1) { notificationRepository.save(match { it.userId == "user_1" && it.type == "INSURANCE_CLAIM_DECIDED" }) }
                verify(exactly = 0) { pushNotificationService.sendToUser(any(), any(), any(), any()) }
            }

            Then("the claimant receives the push only after the real transaction commits") {
                org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations().single().afterCommit()
                verify(exactly = 1) { pushNotificationService.sendToUser("user_1", "Claim approved", any(), mapOf("claimId" to "claim_3")) }
            }
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization()
        }
    }

    Given("a real user exceeds the real claim-filing rate limit") {
        val insurancePolicyRepository = mockk<InsurancePolicyRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val insuranceClaimRepository = mockk<InsuranceClaimRepository>()
        val rateLimiter = mockk<RateLimiter>()
        val insurancePremiumFundRepository = mockk<InsurancePremiumFundRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = InsuranceService(insurancePolicyRepository, accountRepository, ledgerService, insuranceClaimRepository, rateLimiter, insurancePremiumFundRepository, notificationRepository, pushNotificationService)
        every { rateLimiter.checkLimit("insurance:claim:user_9", limit = 10, window = Duration.ofHours(1)) } throws RateLimitExceededException("Too many requests")

        When("they try to file another real claim") {
            Then("it real-propagates RateLimitExceededException, found missing in a 2026-07-19 security sweep") {
                try {
                    service.submitClaim("user_9", "pol_1", "test", BigDecimal("1000"))
                    error("expected RateLimitExceededException")
                } catch (e: RateLimitExceededException) {
                    verify(exactly = 0) { insuranceClaimRepository.save(any()) }
                }
            }
        }
    }
    // Real recurring-premium-collection bug fix regression coverage (2026-08-02) -- see
    // InsuranceService.collectPremium's own doc comment. Each scenario gets its own Given
    // block, not a sibling When, per this session's own hard-won Kotest lesson: sibling
    // When blocks under the same Given share one mutable entity, and a mutation (e.g.
    // policy.status = "lapsed") in one would otherwise leak into its siblings.
    Given("a policy due for premium collection with enough MAIN account balance") {
        val insurancePolicyRepository = mockk<InsurancePolicyRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val insuranceClaimRepository = mockk<InsuranceClaimRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val insurancePremiumFundRepository = mockk<InsurancePremiumFundRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = InsuranceService(insurancePolicyRepository, accountRepository, ledgerService, insuranceClaimRepository, rateLimiter, insurancePremiumFundRepository, notificationRepository, pushNotificationService)

        val duePolicy = policy("pol_due_1", "user_1")
        every { accountRepository.findByUserIdAndType("user_1", AccountType.MAIN) } returns account("account_main", "user_1", AccountType.MAIN)
        val legsSlot = slot<List<LedgerLeg>>()
        every { ledgerService.postLedgerTransaction("RWF", capture(legsSlot)) } returns LedgerPostResult("ledgertxn_premium", emptyList())
        every { insurancePolicyRepository.save(any()) } answers { firstArg() }

        When("collecting the premium") {
            val result = service.collectPremium(duePolicy)

            Then("it succeeds straight from the account, never touching the premium fund repository") {
                result shouldBe true
                val accountLeg = legsSlot.captured.first { it.accountType == LedgerAccountType.WALLET }
                accountLeg.accountId shouldBe "account_main"
                accountLeg.direction shouldBe LedgerDirection.DEBIT
                accountLeg.amount shouldBe BigDecimal("15000")
                val revenueLeg = legsSlot.captured.first { it.accountType == LedgerAccountType.INSURANCE_PREMIUM_REVENUE }
                revenueLeg.direction shouldBe LedgerDirection.CREDIT
                revenueLeg.amount shouldBe BigDecimal("15000")
                verify(exactly = 0) { insurancePremiumFundRepository.findByPolicyIdAndStatus(any(), any()) }
            }
            Then("it advances nextPaymentDate by exactly 30 real days") {
                duePolicy.nextPaymentDate shouldBe LocalDate.now().plusDays(30)
                duePolicy.status shouldBe "active"
            }
        }
    }

    Given("a policy due for premium collection whose MAIN account is short, but with a well-funded premium fund") {
        val insurancePolicyRepository = mockk<InsurancePolicyRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val insuranceClaimRepository = mockk<InsuranceClaimRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val insurancePremiumFundRepository = mockk<InsurancePremiumFundRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = InsuranceService(insurancePolicyRepository, accountRepository, ledgerService, insuranceClaimRepository, rateLimiter, insurancePremiumFundRepository, notificationRepository, pushNotificationService)

        val duePolicy = policy("pol_due_2", "user_1")
        val fund = premiumFund("ipf_1", "user_1", "pol_due_2", targetAmount = BigDecimal("15000"), currentAmount = BigDecimal("15000"))
        every { accountRepository.findByUserIdAndType("user_1", AccountType.MAIN) } returns account("account_main", "user_1", AccountType.MAIN, balance = BigDecimal("1000"))
        every { insurancePremiumFundRepository.findByPolicyIdAndStatus("pol_due_2", InsurancePremiumFundStatus.active) } returns fund
        val legsSlot = slot<List<LedgerLeg>>()
        every { ledgerService.postLedgerTransaction("RWF", capture(legsSlot)) } returns LedgerPostResult("ledgertxn_premium_fund", emptyList())
        every { insurancePremiumFundRepository.save(any()) } answers { firstArg() }
        every { insurancePolicyRepository.save(any()) } answers { firstArg() }

        When("collecting the premium") {
            val result = service.collectPremium(duePolicy)

            Then("it falls back to draining the premium fund, not the short account") {
                result shouldBe true
                val fundLeg = legsSlot.captured.first { it.accountType == LedgerAccountType.INSURANCE_PREMIUM_FUND_PAYABLE }
                fundLeg.accountId shouldBe "insurance_premium_fund_payable"
                fundLeg.direction shouldBe LedgerDirection.DEBIT
                fundLeg.amount shouldBe BigDecimal("15000")
                val revenueLeg = legsSlot.captured.first { it.accountType == LedgerAccountType.INSURANCE_PREMIUM_REVENUE }
                revenueLeg.direction shouldBe LedgerDirection.CREDIT
                revenueLeg.amount shouldBe BigDecimal("15000")
                fund.currentAmount shouldBe BigDecimal.ZERO
                duePolicy.nextPaymentDate shouldBe LocalDate.now().plusDays(30)
                duePolicy.status shouldBe "active"
            }
        }
    }

    Given("a policy due for premium collection where both the account and the fund are short") {
        val insurancePolicyRepository = mockk<InsurancePolicyRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val insuranceClaimRepository = mockk<InsuranceClaimRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val insurancePremiumFundRepository = mockk<InsurancePremiumFundRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = InsuranceService(insurancePolicyRepository, accountRepository, ledgerService, insuranceClaimRepository, rateLimiter, insurancePremiumFundRepository, notificationRepository, pushNotificationService)

        val duePolicy = policy("pol_due_3", "user_1")
        every { accountRepository.findByUserIdAndType("user_1", AccountType.MAIN) } returns account("account_main", "user_1", AccountType.MAIN, balance = BigDecimal("1000"))
        every { insurancePremiumFundRepository.findByPolicyIdAndStatus("pol_due_3", InsurancePremiumFundStatus.active) } returns null
        every { insurancePolicyRepository.save(any()) } answers { firstArg() }

        When("collecting the premium") {
            val result = service.collectPremium(duePolicy)

            Then("it never touches the ledger and real-lapses the policy instead of failing loudly") {
                result shouldBe false
                duePolicy.status shouldBe "lapsed"
                verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                verify(exactly = 1) { insurancePolicyRepository.save(duePolicy) }
            }
        }
    }

    Given("a user contributing to their own active premium fund that's almost at target") {
        val insurancePolicyRepository = mockk<InsurancePolicyRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val insuranceClaimRepository = mockk<InsuranceClaimRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val insurancePremiumFundRepository = mockk<InsurancePremiumFundRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = InsuranceService(insurancePolicyRepository, accountRepository, ledgerService, insuranceClaimRepository, rateLimiter, insurancePremiumFundRepository, notificationRepository, pushNotificationService)

        val fund = premiumFund("ipf_2", "user_1", "pol_1", targetAmount = BigDecimal("15000"), currentAmount = BigDecimal("14000"))
        every { insurancePremiumFundRepository.findById("ipf_2") } returns Optional.of(fund)
        every { accountRepository.findByUserIdAndType("user_1", AccountType.MAIN) } returns account("account_main", "user_1", AccountType.MAIN)
        every { ledgerService.postLedgerTransaction("RWF", any()) } returns LedgerPostResult("ledgertxn_contrib", emptyList())
        every { insurancePremiumFundRepository.save(any()) } answers { firstArg() }

        When("contributing 5,000 RWF, more than what's left to reach the target") {
            val result = service.contributeToFund("user_1", "ipf_2", BigDecimal("5000"))

            Then("it caps at targetAmount instead of overshooting to 19,000, same .min() convention as SavingsGoal") {
                result.currentAmount shouldBe BigDecimal("15000")
            }

            Then("only the real 1,000 RWF gap is ever moved through the ledger, not the full 5,000 requested") {
                verify(exactly = 1) {
                    ledgerService.postLedgerTransaction(
                        "RWF",
                        match { legs -> legs.all { it.amount.compareTo(BigDecimal("1000")) == 0 } },
                    )
                }
            }
        }
    }

    Given("a user cancelling their own active, funded premium fund") {
        val insurancePolicyRepository = mockk<InsurancePolicyRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val insuranceClaimRepository = mockk<InsuranceClaimRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val insurancePremiumFundRepository = mockk<InsurancePremiumFundRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = InsuranceService(insurancePolicyRepository, accountRepository, ledgerService, insuranceClaimRepository, rateLimiter, insurancePremiumFundRepository, notificationRepository, pushNotificationService)

        val fund = premiumFund("ipf_3", "user_1", "pol_1", targetAmount = BigDecimal("15000"), currentAmount = BigDecimal("10000"))
        every { insurancePremiumFundRepository.findById("ipf_3") } returns Optional.of(fund)
        every { accountRepository.findByUserIdAndType("user_1", AccountType.MAIN) } returns account("account_main", "user_1", AccountType.MAIN)
        val legsSlot = slot<List<LedgerLeg>>()
        every { ledgerService.postLedgerTransaction("RWF", capture(legsSlot)) } returns LedgerPostResult("ledgertxn_cancel", emptyList())
        every { insurancePremiumFundRepository.save(any()) } answers { firstArg() }

        When("cancelling it") {
            val result = service.cancelFund("user_1", "ipf_3")

            Then("it refunds the real 10,000 RWF back to the MAIN account and zeroes the fund") {
                result.currentAmount shouldBe BigDecimal.ZERO
                result.status shouldBe InsurancePremiumFundStatus.cancelled
                val fundLeg = legsSlot.captured.first { it.accountType == LedgerAccountType.INSURANCE_PREMIUM_FUND_PAYABLE }
                fundLeg.direction shouldBe LedgerDirection.DEBIT
                fundLeg.amount shouldBe BigDecimal("10000")
                val accountLeg = legsSlot.captured.first { it.accountType == LedgerAccountType.WALLET }
                accountLeg.accountId shouldBe "account_main"
                accountLeg.direction shouldBe LedgerDirection.CREDIT
                accountLeg.amount shouldBe BigDecimal("10000")
            }
        }
    }

    Given("an attacker trying to create a premium fund against someone else's policy") {
        val insurancePolicyRepository = mockk<InsurancePolicyRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val insuranceClaimRepository = mockk<InsuranceClaimRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val insurancePremiumFundRepository = mockk<InsurancePremiumFundRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = InsuranceService(insurancePolicyRepository, accountRepository, ledgerService, insuranceClaimRepository, rateLimiter, insurancePremiumFundRepository, notificationRepository, pushNotificationService)

        every { insurancePolicyRepository.findById("pol_owned") } returns Optional.of(policy("pol_owned", "owner_1"))

        When("the attacker calls createPremiumFund") {
            Then("it real-404s -- same 404-not-403 IDOR pattern used everywhere else") {
                try {
                    service.createPremiumFund("attacker", "pol_owned", BigDecimal("500"))
                    error("expected PolicyNotFoundException")
                } catch (e: PolicyNotFoundException) {
                    verify(exactly = 0) { insurancePremiumFundRepository.save(any()) }
                }
            }
        }
    }

    Given("an attacker trying to contribute to someone else's premium fund") {
        val insurancePolicyRepository = mockk<InsurancePolicyRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val insuranceClaimRepository = mockk<InsuranceClaimRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val insurancePremiumFundRepository = mockk<InsurancePremiumFundRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = InsuranceService(insurancePolicyRepository, accountRepository, ledgerService, insuranceClaimRepository, rateLimiter, insurancePremiumFundRepository, notificationRepository, pushNotificationService)

        every { insurancePremiumFundRepository.findById("ipf_owned") } returns Optional.of(
            premiumFund("ipf_owned", "owner_1", "pol_1", targetAmount = BigDecimal("15000"), currentAmount = BigDecimal("1000")),
        )

        When("the attacker calls contributeToFund") {
            Then("it real-404s instead of leaking whether the fund exists") {
                try {
                    service.contributeToFund("attacker", "ipf_owned", BigDecimal("500"))
                    error("expected PremiumFundNotFoundException")
                } catch (e: PremiumFundNotFoundException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }
    }

    fun policyWithEndDate(id: String, userId: String, endDate: LocalDate, status: String = "active", renewalReminderSentAt: java.time.Instant? = null) = InsurancePolicy(
        id = id, userId = userId, planId = "ins_1", planName = "Health Shield", category = "health",
        status = status, startDate = LocalDate.now().minusMonths(11), endDate = endDate,
        monthlyPremium = BigDecimal("15000"), nextPaymentDate = LocalDate.now(), policyNumber = "POL-$id",
        renewalReminderSentAt = renewalReminderSentAt,
    )

    Given("real active insurance policies at various points in their real renewal window") {
        val insurancePolicyRepository = mockk<InsurancePolicyRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val insuranceClaimRepository = mockk<InsuranceClaimRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val insurancePremiumFundRepository = mockk<InsurancePremiumFundRepository>()
        val notificationRepository = mockk<NotificationRepository>()
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        every { notificationRepository.save(any()) } answers { firstArg() }
        val service = InsuranceService(insurancePolicyRepository, accountRepository, ledgerService, insuranceClaimRepository, rateLimiter, insurancePremiumFundRepository, notificationRepository, pushNotificationService)

        When("a real active policy's real endDate is already inside the 30-day renewal window") {
            val soon = policyWithEndDate("pol_soon", "user_a", LocalDate.now().plusDays(10))
            every { insurancePolicyRepository.findByStatusAndRenewalReminderSentAtIsNull("active") } returns listOf(soon)

            Then("it is a real due candidate") {
                service.getPoliciesDueForRenewalReminder().map { it.id } shouldBe listOf("pol_soon")
            }
        }

        When("a real active policy's real endDate is genuinely still outside the renewal window") {
            val far = policyWithEndDate("pol_far", "user_b", LocalDate.now().plusDays(60))
            every { insurancePolicyRepository.findByStatusAndRenewalReminderSentAtIsNull("active") } returns listOf(far)

            Then("it is real-excluded -- not due yet") {
                service.getPoliciesDueForRenewalReminder() shouldBe emptyList()
            }
        }

        When("sending a real reminder for a genuinely due policy") {
            val policy = policyWithEndDate("pol_due", "user_c", LocalDate.now().plusDays(5))
            every { insurancePolicyRepository.findById("pol_due") } returns Optional.of(policy)
            every { insurancePolicyRepository.save(any()) } answers { firstArg() }

            service.sendRenewalReminder("pol_due")

            Then("it real-notifies once and real-marks renewalReminderSentAt") {
                verify(exactly = 1) { notificationRepository.save(match { it.type == "INSURANCE_POLICY_RENEWAL_DUE" && it.userId == "user_c" }) }
                policy.renewalReminderSentAt shouldNotBe null
            }
        }

        When("sending a reminder for a policy that already has one") {
            val policy = policyWithEndDate("pol_already", "user_d", LocalDate.now().plusDays(5), renewalReminderSentAt = java.time.Instant.now())
            every { insurancePolicyRepository.findById("pol_already") } returns Optional.of(policy)

            service.sendRenewalReminder("pol_already")

            Then("it real-skips -- no double notification for the same real renewal") {
                verify(exactly = 0) { notificationRepository.save(any()) }
            }
        }

        When("sending a reminder for a policy that has genuinely lapsed") {
            val policy = policyWithEndDate("pol_lapsed", "user_e", LocalDate.now().plusDays(5), status = "lapsed")
            every { insurancePolicyRepository.findById("pol_lapsed") } returns Optional.of(policy)

            service.sendRenewalReminder("pol_lapsed")

            Then("it real-skips -- a lapsed policy is not genuinely renewing") {
                verify(exactly = 0) { notificationRepository.save(any()) }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
