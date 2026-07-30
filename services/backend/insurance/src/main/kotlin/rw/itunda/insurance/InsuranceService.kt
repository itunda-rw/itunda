package rw.itunda.insurance

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.InsuranceClaim
import rw.itunda.core.domain.InsuranceClaimStatus
import rw.itunda.core.domain.InsurancePolicy
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.WalletType
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.InsuranceClaimRepository
import rw.itunda.core.repository.InsurancePolicyRepository
import rw.itunda.core.repository.WalletRepository
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class PlanNotFoundException(message: String) : RuntimeException(message)
class NoWalletException(message: String) : RuntimeException(message)
class PolicyNotFoundException(message: String) : RuntimeException(message)
class PolicyNotActiveException(message: String) : RuntimeException(message)
class ClaimNotFoundException(message: String) : RuntimeException(message)
class ClaimNotPendingException(message: String) : RuntimeException(message)
class InvalidClaimException(message: String) : RuntimeException(message)

@Service
class InsuranceService(
    private val insurancePolicyRepository: InsurancePolicyRepository,
    private val walletRepository: WalletRepository,
    private val ledgerService: LedgerService,
    private val insuranceClaimRepository: InsuranceClaimRepository,
    private val rateLimiter: RateLimiter,
) {

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
        val premiumWallet = walletRepository.findByUserId(userId).find { it.type == WalletType.MAIN } ?: throw NoWalletException("No wallet found for this account")

        val monthlyPremium = BigDecimal(plan["monthlyPremium"].toString())
        val planName = plan["name"] as String

        ledgerService.postLedgerTransaction(
            premiumWallet.currency,
            listOf(
                LedgerLeg(premiumWallet.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, monthlyPremium, "First premium - $planName"),
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
            val wallet = walletRepository.findByUserIdAndType(claim.userId, WalletType.MAIN)
                ?: throw NoWalletException("No wallet found for this account")
            ledgerService.postLedgerTransaction(
                wallet.currency,
                listOf(
                    LedgerLeg("insurance_claims_expense", LedgerAccountType.INSURANCE_CLAIMS_EXPENSE, LedgerDirection.DEBIT, claim.amount, "Claim payout - ${claim.description}"),
                    LedgerLeg(wallet.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, claim.amount, "Claim payout - ${claim.description}"),
                ),
            )
        }

        claim.status = if (approve) InsuranceClaimStatus.APPROVED else InsuranceClaimStatus.REJECTED
        claim.reviewedBy = reviewerId
        claim.reviewedAt = Instant.now()
        claim.decisionReason = reason
        return insuranceClaimRepository.save(claim)
    }
}
