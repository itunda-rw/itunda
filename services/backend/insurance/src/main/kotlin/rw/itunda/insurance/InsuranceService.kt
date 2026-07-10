package rw.itunda.insurance

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.core.domain.InsurancePolicy
import rw.itunda.core.domain.WalletType
import rw.itunda.core.ledger.service.LedgerService
import rw.itunda.core.ledger.service.PostingRequest
import rw.itunda.core.ledger.domain.Direction
import rw.itunda.core.repository.InsurancePolicyRepository
import rw.itunda.core.repository.WalletRepository
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

class PlanNotFoundException(message: String) : RuntimeException(message)
class NoWalletException(message: String) : RuntimeException(message)

@Service
class InsuranceService(
    private val insurancePolicyRepository: InsurancePolicyRepository,
    private val walletRepository: WalletRepository,
    private val ledgerService: LedgerService
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

        ledgerService.postTransaction(
            transactionReference = "ins_enroll_${UUID.randomUUID()}",
            description = "First premium - $planName",
            entries = listOf(
                PostingRequest(accountId = premiumWallet.id, amount = monthlyPremium, direction = Direction.DEBIT),
                PostingRequest(accountId = "insurance_premium_revenue", amount = monthlyPremium, direction = Direction.CREDIT)
            )
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
}
