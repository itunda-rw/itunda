package rw.itunda.rewards

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.RewardClaim
import rw.itunda.core.domain.TransactionStatus
import rw.itunda.core.domain.TransactionType
import rw.itunda.core.domain.AccountType
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.DailyStepRewardRepository
import rw.itunda.core.repository.EatsReviewRepository
import rw.itunda.core.repository.KnowledgeAnswerRepository
import rw.itunda.core.repository.RewardClaimRepository
import rw.itunda.core.repository.SavingsGoalRepository
import rw.itunda.core.repository.TransactionRepository
import rw.itunda.core.repository.UserRepository
import rw.itunda.core.repository.AccountRepository
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class RewardTaskNotFoundException(message: String) : RuntimeException(message)
class RewardTaskAlreadyClaimedException(message: String) : RuntimeException(message)
class RewardTaskNotEligibleException(message: String) : RuntimeException(message)
class RewardsNoAccountException(message: String) : RuntimeException(message)
class RewardsUserNotFoundException(message: String) : RuntimeException(message)

data class RewardTaskDef(val id: String, val title: String, val subtitle: String, val rewardAmount: BigDecimal)
data class RewardTaskView(val id: String, val title: String, val subtitle: String, val rewardAmount: BigDecimal, val claimed: Boolean, val claimedAt: Instant?, val eligible: Boolean)
data class RewardTasksResult(val tasks: List<RewardTaskView>, val rewardsTotal: BigDecimal)
data class ClaimRewardResult(val message: String, val rewardAmount: BigDecimal, val newBalance: BigDecimal)
data class ReferralInfo(val referralCode: String?, val referredCount: Int, val completedReferralCount: Int)

// Real Naver Pay 페이펫-inspired collectible companion (2026-08-16, sourced from
// Naver Pay's real 2026 페이펫 upgrade) -- a purely cosmetic layer over real, already-
// tracked engagement, not a new points currency or fabricated AI. `level` grows from
// two real, already-stored signals: one-time task claims (RewardClaimRepository, max
// 5 today) and distinct real days the user has engaged with the step-reward system
// (DailyStepRewardRepository) -- deliberately NOT shopping-mission days for this v1,
// to keep the level computation to two clean COUNT queries rather than parsing
// ShoppingMissionReward's per-flag row shape; a real, honest, smaller v1 slice, not
// the full 7-category/4-minigame Naver version.
data class PetView(val level: Int, val stageName: String, val emoji: String, val claimedTaskCount: Int, val activeRewardDays: Long)

@Service
class RewardsService(
    private val rewardClaimRepository: RewardClaimRepository,
    private val accountRepository: AccountRepository,
    private val ledgerService: LedgerService,
    private val transactionRepository: TransactionRepository,
    private val savingsGoalRepository: SavingsGoalRepository,
    private val userRepository: UserRepository,
    private val dailyStepRewardRepository: DailyStepRewardRepository,
    private val knowledgeAnswerRepository: KnowledgeAnswerRepository,
    private val eatsReviewRepository: EatsReviewRepository,
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
        // Real Naver Pay non-transactional engagement reward (sourced: Naver Pay's real
        // points system pays out for Knowledge iN Q&A participation, not just spending)
        // -- itunda's own KnowledgeService is a real, already-shipped Naver 지식iN-style
        // Q&A feature whose own doc comment already named this exact gap as a deferred
        // follow-up. Every other catalog task rewards a transaction or a one-time setup
        // step; this is the first that rewards genuine community help.
        RewardTaskDef("task_knowledge_answer_adopted", "Get an answer adopted", "Help someone in Community Q&A and have your answer marked best", BigDecimal("500")),
        // Real Coupang/Baemin/Naver-sourced 포토리뷰 incentive -- every major Korean
        // delivery/e-commerce platform pays a small one-time reward for a review that
        // includes a real photo of the food/product, since photo-bearing reviews are
        // disproportionately trusted by other buyers (the same real motivation
        // EatsReview.photoUrl's own doc comment already cites for ranking photo
        // reviews first). itunda's EatsReview.photoUrl has existed since migration
        // V224 with zero incentive attached to actually using it -- this closes that.
        RewardTaskDef("task_first_photo_review", "Write a photo review", "Add a real photo to any Eats order review", BigDecimal("300")),
    )

    // Real-activity verification (2026-07-16/17). Closes the parity matrix's "claiming
    // is honor-system today, not verified against real user activity" gap for all five
    // catalog tasks. transfer/bill/savings goal use existing repository queries
    // (task_first_bill only became checkable once BillsService started writing a real
    // Transaction row -- see that file's own doc comment). task_referral matches the
    // task's own copy, "Invite a friend who completes their first transaction" --
    // eligible once any user this one referred (User.referredByUserId, set at
    // registration via AuthService) has a real COMPLETED TRANSFER-type Transaction of
    // their own. task_profile matches its own copy, "Add a profile photo and verify
    // your email" -- eligible once both User.profilePhotoUrl is set and
    // User.emailVerified is true (see AuthService's profile endpoints). `else -> false`
    // fails closed, not open: every real catalog task is covered above, so this branch
    // should be unreachable in practice, but a future never-explicitly-handled task
    // should not silently become claimable by default.
    private fun isEligible(userId: String, taskId: String): Boolean = when (taskId) {
        "task_first_transfer" -> transactionRepository.existsBySenderIdAndTypeAndStatus(userId, TransactionType.TRANSFER, TransactionStatus.COMPLETED)
        "task_first_bill" -> transactionRepository.existsBySenderIdAndTypeAndStatus(userId, TransactionType.BILL, TransactionStatus.COMPLETED)
        "task_savings_goal" -> savingsGoalRepository.existsByUserId(userId)
        // Real N+1 fix (2026-09-13): one batched DISTINCT-senderId query for every
        // referred friend instead of one existsBy call per friend -- same fix shape as
        // getReferralInfo below, which has the identical per-referred-friend check.
        "task_referral" -> userRepository.findAllByReferredByUserId(userId).map { it.id }.let { referredIds ->
            referredIds.isNotEmpty() &&
                transactionRepository.findDistinctSenderIdsBySenderIdInAndTypeAndStatus(referredIds, TransactionType.TRANSFER, TransactionStatus.COMPLETED).isNotEmpty()
        }
        "task_profile" -> userRepository.findById(userId)
            .map { it.profilePhotoUrl != null && it.emailVerified }
            .orElse(false)
        "task_knowledge_answer_adopted" -> knowledgeAnswerRepository.countByAnswererIdAndIsAdoptedTrue(userId) > 0
        "task_first_photo_review" -> eatsReviewRepository.existsByBuyerIdAndPhotoUrlIsNotNull(userId)
        else -> false
    }

    // Backs a real "share your code" UI: current referral code plus real progress
    // toward task_referral, not just the eligible boolean claim() itself checks.
    fun getReferralInfo(userId: String): ReferralInfo {
        val user = userRepository.findById(userId).orElseThrow { RewardsUserNotFoundException("User not found") }
        val referred = userRepository.findAllByReferredByUserId(userId)
        // Real N+1 fix (2026-09-13) -- see isEligible("task_referral")'s identical fix
        // above for the full rationale.
        val referredIds = referred.map { it.id }
        val completedSenderIds = if (referredIds.isEmpty()) {
            emptySet()
        } else {
            transactionRepository.findDistinctSenderIdsBySenderIdInAndTypeAndStatus(referredIds, TransactionType.TRANSFER, TransactionStatus.COMPLETED).toSet()
        }
        val completed = referred.count { it.id in completedSenderIds }
        return ReferralInfo(user.referralCode, referred.size, completed)
    }

    // Named stages (not just a bare number) mirror the real Naver Pay 페이펫 growth
    // framing (an egg that hatches and grows) -- deliberately plain emoji, not custom
    // art, since itunda has no image-asset pipeline to invent one (same honest bar
    // photoUrl-style fields already establish elsewhere in this codebase).
    private data class PetStage(val minLevel: Int, val name: String, val emoji: String)
    private val petStages = listOf(
        PetStage(1, "Egg", "🥚"),
        PetStage(2, "Hatchling", "🐣"),
        PetStage(4, "Chick", "🐤"),
        PetStage(7, "Fledgling", "🕊️"),
        PetStage(11, "Soaring", "🦅"),
    )

    fun getPet(userId: String): PetView {
        val claimedTaskCount = rewardClaimRepository.findByUserId(userId).size
        val activeRewardDays = dailyStepRewardRepository.countByUserId(userId)
        val level = 1 + claimedTaskCount + activeRewardDays.toInt()
        val stage = petStages.last { level >= it.minLevel }
        return PetView(level, stage.name, stage.emoji, claimedTaskCount, activeRewardDays)
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
        val account = accountRepository.findByUserIdAndType(userId, AccountType.MAIN)
            ?: throw RewardsNoAccountException("No account found for this account")

        ledgerService.postLedgerTransaction(
            account.currency,
            listOf(
                LedgerLeg("rewards_expense", LedgerAccountType.REWARDS_EXPENSE, LedgerDirection.DEBIT, task.rewardAmount, "Reward claimed - ${task.title}"),
                LedgerLeg(account.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, task.rewardAmount, "Reward claimed - ${task.title}"),
            ),
        )

        rewardClaimRepository.save(
            RewardClaim(id = "rwc_${UUID.randomUUID()}", userId = userId, taskId = taskId, amount = task.rewardAmount, claimedAt = Instant.now()),
        )

        val newTotal = rewardClaimRepository.findByUserId(userId).fold(BigDecimal.ZERO) { acc, c -> acc + c.amount }
        return ClaimRewardResult("Reward claimed", task.rewardAmount, newTotal)
    }
}
