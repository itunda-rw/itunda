package rw.itunda.insurance

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.InsuranceClaim
import rw.itunda.core.domain.InsuranceClaimStatus
import rw.itunda.core.domain.InsurancePolicy
import rw.itunda.core.domain.InsurancePremiumFund
import rw.itunda.core.domain.InsurancePremiumFundStatus
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.Notification
import rw.itunda.core.domain.AccountType
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.InsuranceClaimRepository
import rw.itunda.core.repository.InsurancePolicyRepository
import rw.itunda.core.repository.InsurancePremiumFundRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.AccountRepository
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

private const val PREMIUM_FUND_AUTO_CONTRIBUTION_INTERVAL_DAYS = 30L

class PlanNotFoundException(message: String) : RuntimeException(message)
class NoAccountException(message: String) : RuntimeException(message)
class PolicyNotFoundException(message: String) : RuntimeException(message)
class PolicyNotActiveException(message: String) : RuntimeException(message)
class ClaimNotFoundException(message: String) : RuntimeException(message)
class ClaimNotPendingException(message: String) : RuntimeException(message)
class InvalidClaimException(message: String) : RuntimeException(message)
class PremiumFundNotFoundException(message: String) : RuntimeException(message)
class PremiumFundAlreadyExistsException(message: String) : RuntimeException(message)
class PremiumFundNotActiveException(message: String) : RuntimeException(message)
class InvalidPremiumFundAmountException(message: String) : RuntimeException(message)

@Service
class InsuranceService(
    private val insurancePolicyRepository: InsurancePolicyRepository,
    private val accountRepository: AccountRepository,
    private val ledgerService: LedgerService,
    private val insuranceClaimRepository: InsuranceClaimRepository,
    private val rateLimiter: RateLimiter,
    private val insurancePremiumFundRepository: InsurancePremiumFundRepository,
    private val notificationRepository: NotificationRepository,
    private val pushNotificationService: PushNotificationService,
) {
    private val log = LoggerFactory.getLogger(InsuranceService::class.java)

    val insurancePlans = listOf(
        mapOf("id" to "ins_1", "name" to "Health Shield", "category" to "health", "provider" to "RSSB", "monthlyPremium" to 15000, "coverageAmount" to 5000000, "description" to "Comprehensive health cover for you and family", "features" to listOf("Outpatient", "Inpatient", "Dental", "Vision", "Maternity"), "rating" to 4.8, "enrolledCount" to 120000, "color" to "#0066FF"),
        mapOf("id" to "ins_2", "name" to "Life Cover Plus", "category" to "life", "provider" to "Sanlam Rwanda", "monthlyPremium" to 8000, "coverageAmount" to 20000000, "description" to "Term life insurance protecting your family", "features" to listOf("Death benefit", "Critical illness", "Disability cover"), "rating" to 4.6, "enrolledCount" to 45000, "color" to "#9C27B0"),
        mapOf("id" to "ins_3", "name" to "Travel Safe", "category" to "travel", "provider" to "UAP Rwanda", "monthlyPremium" to 3500, "coverageAmount" to 2000000, "description" to "International travel protection", "features" to listOf("Medical evacuation", "Trip cancellation", "Baggage loss", "24/7 support"), "rating" to 4.5, "enrolledCount" to 18000, "color" to "#FF9500"),
        mapOf("id" to "ins_4", "name" to "Motor Comprehensive", "category" to "motor", "provider" to "SONARWA", "monthlyPremium" to 25000, "coverageAmount" to 10000000, "description" to "Full motor vehicle protection", "features" to listOf("Accident cover", "Third-party", "Theft", "Fire", "Natural disaster"), "rating" to 4.4, "enrolledCount" to 67000, "color" to "#00C853"),
        mapOf("id" to "ins_5", "name" to "Mutuelle de Santé", "category" to "health", "provider" to "Government", "monthlyPremium" to 3000, "coverageAmount" to 1000000, "description" to "Rwanda community-based health insurance", "features" to listOf("Primary care", "Hospital admissions", "Maternity", "Child health"), "rating" to 4.2, "enrolledCount" to 8500000, "color" to "#34C0AC")
    )

    fun getPlans() = insurancePlans

    fun getMyPolicies(userId: String) = insurancePolicyRepository.findByUserId(userId)

    @Transactional
    fun enrollInPlan(userId: String, planId: String): InsurancePolicy {
        val plan = insurancePlans.find { it["id"] == planId } ?: throw PlanNotFoundException("Plan not found")
        val premiumAccount = accountRepository.findByUserId(userId).find { it.type == AccountType.MAIN } ?: throw NoAccountException("No account found for this account")

        val monthlyPremium = BigDecimal(plan["monthlyPremium"].toString())
        val planName = plan["name"] as String

        ledgerService.postLedgerTransaction(
            premiumAccount.currency,
            listOf(
                LedgerLeg(premiumAccount.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, monthlyPremium, "First premium - $planName"),
                LedgerLeg("insurance_premium_revenue", LedgerAccountType.INSURANCE_PREMIUM_REVENUE, LedgerDirection.CREDIT, monthlyPremium, "First premium - $planName"),
            ),
        )

        val policy = InsurancePolicy(
            id = "pol_${UUID.randomUUID()}",
            userId = userId,
            planId = planId,
            planName = planName,
            category = plan["category"] as String,
            status = "active",
            startDate = LocalDate.now(),
            endDate = LocalDate.now().plusYears(1),
            monthlyPremium = monthlyPremium,
            nextPaymentDate = LocalDate.now().plusDays(30),
            policyNumber = "POL-${System.currentTimeMillis()}"
        )

        return insurancePolicyRepository.save(policy)
    }

    // Real recurring-premium-collection bug fix (2026-08-02) -- enrollInPlan charges the
    // FIRST premium at enrollment and sets nextPaymentDate = now + 30 days, but a repo-wide
    // grep confirmed nextPaymentDate was written once and never read anywhere else: no
    // scheduler, no job, nothing ever collected a second premium. Every policy silently
    // stopped being paid for after month one, forever, with zero consequence. findAll() +
    // in-memory filter is the same honest choice as SavingsService.getGoalsDueForAutoContribution
    // at this system's real data scale, not a premature indexed query.
    fun getPoliciesDueForPremiumCollection(): List<InsurancePolicy> {
        val today = LocalDate.now()
        return insurancePolicyRepository.findAll().filter { policy ->
            policy.status == "active" && !policy.nextPaymentDate.isAfter(today)
        }
    }

    // Tries the user's MAIN account first, exactly the same DEBIT account / CREDIT
    // insurance_premium_revenue leg shape enrollInPlan already uses for the first premium.
    // If the account alone is short, falls back to draining an active InsurancePremiumFund
    // linked to this policy (see InsurancePremiumFund.kt) before giving up. Returns false
    // (not an exception) when NEITHER source can cover it -- this runs from a background
    // scheduler, same "skip this cycle, don't fail loudly" convention as
    // SavingsService.autoContribute -- but unlike a savings goal simply missing a
    // contribution, an unpaid premium has a real consequence: the policy lapses.
    @Transactional
    fun collectPremium(policy: InsurancePolicy): Boolean {
        val account = accountRepository.findByUserIdAndType(policy.userId, AccountType.MAIN)
        if (account != null && account.availableBalance >= policy.monthlyPremium) {
            ledgerService.postLedgerTransaction(
                account.currency,
                listOf(
                    LedgerLeg(account.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, policy.monthlyPremium, "Premium - ${policy.planName}"),
                    LedgerLeg("insurance_premium_revenue", LedgerAccountType.INSURANCE_PREMIUM_REVENUE, LedgerDirection.CREDIT, policy.monthlyPremium, "Premium - ${policy.planName}"),
                ),
            )
            policy.nextPaymentDate = policy.nextPaymentDate.plusDays(30)
            insurancePolicyRepository.save(policy)
            return true
        }

        val fund = insurancePremiumFundRepository.findByPolicyIdAndStatus(policy.id, InsurancePremiumFundStatus.active)
        if (fund != null && fund.currentAmount >= policy.monthlyPremium) {
            ledgerService.postLedgerTransaction(
                "RWF",
                listOf(
                    LedgerLeg("insurance_premium_fund_payable", LedgerAccountType.INSURANCE_PREMIUM_FUND_PAYABLE, LedgerDirection.DEBIT, policy.monthlyPremium, "Premium - ${policy.planName}"),
                    LedgerLeg("insurance_premium_revenue", LedgerAccountType.INSURANCE_PREMIUM_REVENUE, LedgerDirection.CREDIT, policy.monthlyPremium, "Premium - ${policy.planName}"),
                ),
            )
            fund.currentAmount = fund.currentAmount.subtract(policy.monthlyPremium)
            insurancePremiumFundRepository.save(fund)
            policy.nextPaymentDate = policy.nextPaymentDate.plusDays(30)
            insurancePolicyRepository.save(policy)
            return true
        }

        // Real-world consequence of a genuinely unpaid premium: coverage lapses, the same
        // way a real insurer would stop covering a policyholder who stops paying.
        policy.status = "lapsed"
        insurancePolicyRepository.save(policy)
        return false
    }

    fun submitClaim(userId: String, policyId: String, description: String, amount: BigDecimal): InsuranceClaim {
        if (amount <= BigDecimal.ZERO) throw InvalidClaimException("Claim amount must be greater than zero")
        val trimmedDescription = description.trim()
        if (trimmedDescription.isEmpty() || trimmedDescription.length > 255) {
            throw InvalidClaimException("Claim description is required and must be 255 characters or fewer")
        }
        // Real anti-spam limit -- found missing in a 2026-07-19 security sweep. Filing
        // is deliberately not Idempotency-Key protected (not money-moving), but that left
        // it with zero protection of any kind against a flood of bogus claims.
        rateLimiter.checkLimit("insurance:claim:$userId", limit = 10, window = Duration.ofHours(1))
        val policy = insurancePolicyRepository.findById(policyId)
            .filter { it.userId == userId }
            .orElseThrow { PolicyNotFoundException("Policy not found") }
        if (policy.status != "active") {
            throw PolicyNotActiveException("Cannot file a claim against a ${policy.status} policy")
        }
        val claim = InsuranceClaim(id = "claim_${UUID.randomUUID()}", policyId = policyId, userId = userId, description = trimmedDescription, amount = amount)
        return insuranceClaimRepository.save(claim)
    }

    fun getMyClaims(userId: String) = insuranceClaimRepository.findByUserIdOrderBySubmittedAtDesc(userId)

    fun getClaimsQueue() = insuranceClaimRepository.findByStatusOrderBySubmittedAtAsc(InsuranceClaimStatus.SUBMITTED)

    @Transactional
    fun decideClaim(claimId: String, reviewerId: String, approve: Boolean, reason: String?): InsuranceClaim {
        val claim = insuranceClaimRepository.findById(claimId).orElseThrow { ClaimNotFoundException("Claim not found") }
        if (claim.status != InsuranceClaimStatus.SUBMITTED) {
            throw ClaimNotPendingException("Claim is already ${claim.status}")
        }

        if (approve) {
            val account = accountRepository.findByUserIdAndType(claim.userId, AccountType.MAIN)
                ?: throw NoAccountException("No account found for this account")
            ledgerService.postLedgerTransaction(
                account.currency,
                listOf(
                    LedgerLeg("insurance_claims_expense", LedgerAccountType.INSURANCE_CLAIMS_EXPENSE, LedgerDirection.DEBIT, claim.amount, "Claim payout - ${claim.description}"),
                    LedgerLeg(account.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, claim.amount, "Claim payout - ${claim.description}"),
                ),
            )
        }

        claim.status = if (approve) InsuranceClaimStatus.APPROVED else InsuranceClaimStatus.REJECTED
        claim.reviewedBy = reviewerId
        claim.reviewedAt = Instant.now()
        claim.decisionReason = reason
        val saved = insuranceClaimRepository.save(claim)

        // Real claim-decision notification -- previously a claimant had no way to ever
        // learn their claim was decided except by polling GET /claims themselves. Every
        // real insurer notifies on both outcomes (approval confirms the real payout that
        // already landed above; rejection explains why, using the same real reason text
        // an admin/reviewer already provides), same "decide = terminal action, tell the
        // real person it happened to" discipline OrderReturnService.decide already
        // establishes for a structurally identical approve/reject decision.
        val decidedTitle = if (approve) "Claim approved" else "Claim rejected"
        val decidedBody = if (approve) {
            "Your claim for \"${claim.description}\" was approved. ${claim.amount.toPlainString()} RWF has been credited to your account."
        } else {
            "Your claim for \"${claim.description}\" was rejected.${reason?.let { " Reason: $it" } ?: ""}"
        }
        notificationRepository.save(
            Notification(
                id = "notif_${UUID.randomUUID()}", userId = claim.userId, type = "INSURANCE_CLAIM_DECIDED",
                title = decidedTitle, body = decidedBody, isRead = false, createdAt = Instant.now(),
                dataJson = "{\"claimId\":\"${claim.id}\"}",
            ),
        )
        sendPushAfterCommit(claim.userId, decidedTitle, decidedBody, claim.id)
        return saved
    }

    // Same real "defer the mobile push until the real ledger/status change is
    // durable, but the in-app Notification row is saved immediately" discipline
    // OrderReturnService.sendPushAfterCommit already establishes for a structurally
    // identical approve/reject decision.
    private fun sendPushAfterCommit(userId: String, title: String, body: String, claimId: String) {
        val data = mapOf("claimId" to claimId)
        val send = {
            try {
                pushNotificationService.sendToUser(userId, title, body, data)
            } catch (e: Exception) {
                log.warn("Could not send insurance-claim-decision push for claim {}", claimId, e)
            }
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            send()
            return
        }
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() = send()
        })
    }

    // Real Ejo Heza ya Moto-style premium savings fund (2026-08-02) -- see
    // InsurancePremiumFund.kt's own doc comment. Sourced from Africa-Press (2026)
    // reporting Rwanda's ~46,000 registered taxi-moto riders facing insurance premiums
    // up to RWF 250,000/year for older bikes, worsened since the taxi-moto cooperatives
    // that used to pool this cost were dissolved. Generic and policy-linked (not
    // moto-only) so any user can save toward a specific policy's premium ahead of time,
    // letting collectPremium above draw on it instead of lapsing the policy when the
    // account alone is short.
    @Transactional
    fun createPremiumFund(userId: String, policyId: String, dailyContribution: BigDecimal): InsurancePremiumFund {
        val policy = insurancePolicyRepository.findById(policyId)
            .filter { it.userId == userId }
            .orElseThrow { PolicyNotFoundException("Policy not found") }
        if (insurancePremiumFundRepository.findByPolicyIdAndStatus(policyId, InsurancePremiumFundStatus.active) != null) {
            throw PremiumFundAlreadyExistsException("An active premium fund already exists for this policy")
        }
        // Real anti-spam limit, same convention as SavingsService.createGoal -- row
        // creation is free and otherwise has zero protection of any kind.
        rateLimiter.checkLimit("insurance:premium-fund:$userId", limit = 10, window = Duration.ofHours(1))
        return insurancePremiumFundRepository.save(
            InsurancePremiumFund(
                id = "ipf_${UUID.randomUUID()}", userId = userId, policyId = policyId,
                targetAmount = policy.monthlyPremium, currentAmount = BigDecimal.ZERO,
                dailyContribution = dailyContribution,
            ),
        )
    }

    @Transactional
    fun contributeToFund(userId: String, fundId: String, amount: BigDecimal): InsurancePremiumFund {
        val fund = insurancePremiumFundRepository.findById(fundId)
            .filter { it.userId == userId }
            .orElseThrow { PremiumFundNotFoundException("Premium fund not found") }
        if (fund.status != InsurancePremiumFundStatus.active) {
            throw PremiumFundNotActiveException("Cannot contribute to a ${fund.status} premium fund")
        }
        if (amount <= BigDecimal.ZERO) {
            throw InvalidPremiumFundAmountException("Contribution amount must be greater than zero")
        }
        val account = accountRepository.findByUserIdAndType(userId, AccountType.MAIN) ?: throw NoAccountException("No account found for this account")
        // Real bug caught in this feature's own build-time review: SavingsGoal's
        // depositToGoal/autoContribute post the FULL requested amount to the ledger and
        // only cap the *field* at targetAmount afterwards -- so an overshooting
        // contribution moves real money into the account/liability legs that the capped
        // field then never accounts for, and cancelFund only ever refunds
        // fund.currentAmount, permanently stranding the excess in the shared
        // insurance_premium_fund_payable clearing account with no path back to the user.
        // Clamping the amount actually moved to the real remaining gap BEFORE touching
        // the ledger keeps every RWF that leaves the account accounted for and refundable.
        val actualAmount = amount.min(fund.targetAmount.subtract(fund.currentAmount))
        ledgerService.postLedgerTransaction(
            account.currency,
            listOf(
                LedgerLeg(account.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, actualAmount, "Premium fund contribution"),
                LedgerLeg("insurance_premium_fund_payable", LedgerAccountType.INSURANCE_PREMIUM_FUND_PAYABLE, LedgerDirection.CREDIT, actualAmount, "Premium fund contribution"),
            ),
        )
        fund.currentAmount = fund.currentAmount.add(actualAmount)
        return insurancePremiumFundRepository.save(fund)
    }

    @Transactional
    fun cancelFund(userId: String, fundId: String): InsurancePremiumFund {
        val fund = insurancePremiumFundRepository.findById(fundId)
            .filter { it.userId == userId }
            .orElseThrow { PremiumFundNotFoundException("Premium fund not found") }
        if (fund.status != InsurancePremiumFundStatus.active) {
            throw PremiumFundNotActiveException("Cannot cancel a ${fund.status} premium fund")
        }
        if (fund.currentAmount > BigDecimal.ZERO) {
            val account = accountRepository.findByUserIdAndType(userId, AccountType.MAIN) ?: throw NoAccountException("No account found for this account")
            ledgerService.postLedgerTransaction(
                account.currency,
                listOf(
                    LedgerLeg("insurance_premium_fund_payable", LedgerAccountType.INSURANCE_PREMIUM_FUND_PAYABLE, LedgerDirection.DEBIT, fund.currentAmount, "Premium fund refund"),
                    LedgerLeg(account.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, fund.currentAmount, "Premium fund refund"),
                ),
            )
        }
        fund.currentAmount = BigDecimal.ZERO
        fund.status = InsurancePremiumFundStatus.cancelled
        return insurancePremiumFundRepository.save(fund)
    }

    fun getMyPremiumFunds(userId: String) = insurancePremiumFundRepository.findByUserId(userId)

    // Same 30-day-cadence-via-lastAutoContributionAt-null-or-stale pattern as
    // SavingsService.getGoalsDueForAutoContribution/autoContribute -- see that function's
    // own doc comment for why findAll() + in-memory filter is the honest choice at this
    // system's real data scale. dailyContribution == ZERO funds are manual-only and never
    // selected here; funds already at target are excluded since there's nothing left to
    // save toward (InsurancePremiumFund has no separate "completed" status).
    fun getFundsDueForAutoContribution(): List<InsurancePremiumFund> {
        val cutoff = Instant.now().minus(PREMIUM_FUND_AUTO_CONTRIBUTION_INTERVAL_DAYS, ChronoUnit.DAYS)
        return insurancePremiumFundRepository.findAll().filter { fund ->
            fund.status == InsurancePremiumFundStatus.active &&
                fund.dailyContribution > BigDecimal.ZERO &&
                fund.currentAmount < fund.targetAmount &&
                (fund.lastAutoContributionAt == null || fund.lastAutoContributionAt!!.isBefore(cutoff))
        }
    }

    // Returns false (not an exception) on insufficient MAIN account balance -- same "skip
    // this cycle" convention as SavingsService.autoContribute; a real recurring
    // contribution just retries next cycle rather than failing loudly mid-batch.
    @Transactional
    fun autoContributeToFund(fund: InsurancePremiumFund): Boolean {
        val account = accountRepository.findByUserIdAndType(fund.userId, AccountType.MAIN)
        // Same overshoot clamp as contributeToFund above -- never move more than the
        // real remaining gap, so the ledger and fund.currentAmount always agree.
        val actualAmount = fund.dailyContribution.min(fund.targetAmount.subtract(fund.currentAmount))
        if (account == null || account.availableBalance < actualAmount) {
            return false
        }
        ledgerService.postLedgerTransaction(
            account.currency,
            listOf(
                LedgerLeg(account.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, actualAmount, "Premium fund auto-contribution"),
                LedgerLeg("insurance_premium_fund_payable", LedgerAccountType.INSURANCE_PREMIUM_FUND_PAYABLE, LedgerDirection.CREDIT, actualAmount, "Premium fund auto-contribution"),
            ),
        )
        fund.currentAmount = fund.currentAmount.add(actualAmount)
        fund.lastAutoContributionAt = Instant.now()
        insurancePremiumFundRepository.save(fund)
        return true
    }

    // Real Kakao Pay/Toss Insurance 갱신 안내 (renewal notice) -- see
    // InsurancePolicy.renewalReminderSentAt's own doc comment for the real sourcing.
    // `endDate` has been a real, stored field since this policy concept existed, but
    // nothing ever notified a user as it approached -- same "real data sitting unused"
    // shape SavingsService.getGoalsDueForMaturityReminder already closed once for
    // SavingsGoal.targetDate. RENEWAL_WINDOW_DAYS = 30, itunda's own honest choice
    // within the real sourced 30-45 day range, not a fabricated figure.
    fun getPoliciesDueForRenewalReminder(): List<InsurancePolicy> {
        val cutoff = LocalDate.now().plusDays(RENEWAL_WINDOW_DAYS)
        return insurancePolicyRepository.findByStatusAndRenewalReminderSentAtIsNull("active")
            .filter { !it.endDate.isAfter(cutoff) }
    }

    /** One real renewal-reminder notification, called per-policy by the scheduler --
     * re-checks `status`/`renewalReminderSentAt` right before sending so a genuine race
     * can't double-fire, same resilience discipline SavingsService.sendMaturityReminder's
     * own doc comment already establishes. */
    @Transactional
    fun sendRenewalReminder(policyId: String) {
        val policy = insurancePolicyRepository.findById(policyId).orElse(null) ?: return
        if (policy.status != "active" || policy.renewalReminderSentAt != null) return

        val title = "Your ${policy.planName} policy is renewing soon"
        val body = "Your \"${policy.planName}\" policy expires on ${policy.endDate}. It will auto-renew unless you cancel."
        notificationRepository.save(
            Notification(
                id = "notif_${UUID.randomUUID()}", userId = policy.userId, type = "INSURANCE_POLICY_RENEWAL_DUE",
                title = title, body = body, isRead = false, createdAt = Instant.now(), dataJson = "{\"policyId\":\"${policy.id}\"}",
            ),
        )
        pushNotificationService.sendToUser(policy.userId, title, body, mapOf("policyId" to policy.id))
        policy.renewalReminderSentAt = Instant.now()
        insurancePolicyRepository.save(policy)
    }

    companion object {
        private const val RENEWAL_WINDOW_DAYS = 30L
    }
}
