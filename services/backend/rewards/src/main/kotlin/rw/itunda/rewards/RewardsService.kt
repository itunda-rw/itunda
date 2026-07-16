package rw.itunda.rewards

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.RewardClaim
import rw.itunda.core.domain.TransactionStatus
import rw.itunda.core.domain.TransactionType
import rw.itunda.core.domain.WalletType
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.RewardClaimRepository
import rw.itunda.core.repository.SavingsGoalRepository
import rw.itunda.core.repository.TransactionRepository
import rw.itunda.core.repository.WalletRepository
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class RewardTaskNotFoundException(message: String) : RuntimeException(message)
class RewardTaskAlreadyClaimedException(message: String) : RuntimeException(message)
class RewardTaskNotEligibleException(message: String) : RuntimeException(message)
class RewardsNoWalletException(message: String) : RuntimeException(message)

data class RewardTaskDef(val id: String, val title: String, val subtitle: String, val rewardAmount: BigDecimal)
data class RewardTaskView(val id: String, val title: String, val subtitle: String, val rewardAmount: BigDecimal, val claimed: Boolean, val claimedAt: Instant?, val eligible: Boolean)
data class RewardTasksResult(val tasks: List<RewardTaskView>, val rewardsTotal: BigDecimal)
data class ClaimRewardResult(val message: String, val rewardAmount: BigDecimal, val newBalance: BigDecimal)

@Service
class RewardsService(
    private val rewardClaimRepository: RewardClaimRepository,
    private val walletRepository: WalletRepository,
    private val ledgerService: LedgerService,
    private val transactionRepository: TransactionRepository,
    private val savingsGoalRepository: SavingsGoalRepository,
) {

    // Static catalog, same convention as InsuranceService's insurancePlans / LoansService's
    // offers -- "Refer a friend" deliberately matches the 5,000 RWF figure already shown as
    // static copy in DiscoverController's "Referral Bonus" card, so the two don't disagree.
    val taskCatalog = listOf(
        RewardTaskDef("task_profile", "Complete your profile", "Add a profile photo and verify your email", BigDecimal("500")),
        RewardTaskDef("task_first_transfer", "Make your first transfer", "Send money to a friend or family member", BigDecimal("1000")),
        RewardTaskDef("task_first_bill", "Pay your first bill", "Use itunda to pay any utility bill", BigDecimal("500")),
        RewardTaskDef("task_savings_goal", "Set a savings goal", "Start building your savings with itunda", BigDecimal("300")),
        RewardTaskDef("task_referral", "Refer a friend", "Invite a friend who completes their first transaction", BigDecimal("5000")),
    )

    // Real-activity verification (2026-07-16). Closes the parity matrix's "claiming is
    // honor-system today, not verified against real user activity" gap for the three
    // tasks where verification data already exists in the system (transfer, bill,
    // savings goal both use existing repository queries; task_first_bill only became
    // checkable once BillsService started writing a real Transaction row -- see that
    // file's own doc comment). `task_profile` (no profile-photo/email-verification
    // schema or flow exists anywhere in this backend) and `task_referral` (no referral
    // subsystem -- codes, attribution, completion detection -- exists at all) stay
    // honor-system on purpose: building either prerequisite is a genuinely separate,
    // larger feature, not a query away like the other three. Always eligible rather
    // than silently rejecting real users' honest claims on those two until that work
    // is done -- see docs/TOSS_PARITY_MATRIX.md's Rewards row for the tracked gap.
    private fun isEligible(userId: String, taskId: String): Boolean = when (taskId) {
        "task_first_transfer" -> transactionRepository.existsBySenderIdAndTypeAndStatus(userId, TransactionType.TRANSFER, TransactionStatus.COMPLETED)
        "task_first_bill" -> transactionRepository.existsBySenderIdAndTypeAndStatus(userId, TransactionType.BILL, TransactionStatus.COMPLETED)
        "task_savings_goal" -> savingsGoalRepository.existsByUserId(userId)
        else -> true // task_profile, task_referral: still honor-system, see comment above
    }

    fun getTasks(userId: String): RewardTasksResult {
        val claims = rewardClaimRepository.findByUserId(userId).associateBy { it.taskId }
        val views = taskCatalog.map { def ->
            val claim = claims[def.id]
            RewardTaskView(
                def.id, def.title, def.subtitle, def.rewardAmount,
                claimed = claim != null, claimedAt = claim?.claimedAt,
                eligible = claim != null || isEligible(userId, def.id),
            )
        }
        val total = claims.values.fold(BigDecimal.ZERO) { acc, c -> acc + c.amount }
        return RewardTasksResult(views, total)
    }

    @Transactional
    fun claim(userId: String, taskId: String): ClaimRewardResult {
        val task = taskCatalog.find { it.id == taskId } ?: throw RewardTaskNotFoundException("Reward task not found")
        if (rewardClaimRepository.existsByUserIdAndTaskId(userId, taskId)) {
            throw RewardTaskAlreadyClaimedException("This reward has already been claimed")
        }
        if (!isEligible(userId, taskId)) {
            throw RewardTaskNotEligibleException("This task hasn't been completed yet")
        }
        val wallet = walletRepository.findByUserIdAndType(userId, WalletType.MAIN)
            ?: throw RewardsNoWalletException("No wallet found for this account")

        ledgerService.postLedgerTransaction(
            wallet.currency,
            listOf(
                LedgerLeg("rewards_expense", LedgerAccountType.REWARDS_EXPENSE, LedgerDirection.DEBIT, task.rewardAmount, "Reward claimed - ${task.title}"),
                LedgerLeg(wallet.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, task.rewardAmount, "Reward claimed - ${task.title}"),
            ),
        )

        rewardClaimRepository.save(
            RewardClaim(id = "rwc_${UUID.randomUUID()}", userId = userId, taskId = taskId, amount = task.rewardAmount, claimedAt = Instant.now()),
        )

        val newTotal = rewardClaimRepository.findByUserId(userId).fold(BigDecimal.ZERO) { acc, c -> acc + c.amount }
        return ClaimRewardResult("Reward claimed", task.rewardAmount, newTotal)
    }
}
