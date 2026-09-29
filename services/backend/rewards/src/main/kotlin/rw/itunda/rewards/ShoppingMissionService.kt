package rw.itunda.rewards

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.Notification
import rw.itunda.core.domain.ShoppingMissionReward
import rw.itunda.core.domain.ShoppingWelcomeBonusClaim
import rw.itunda.core.domain.AccountType
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.ShoppingMissionRewardRepository
import rw.itunda.core.repository.ShoppingWelcomeBonusClaimRepository
import rw.itunda.core.repository.AccountRepository
import java.math.BigDecimal
import java.security.SecureRandom
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

class MissionAlreadyCompletedException(message: String) : RuntimeException(message)

// Real Toss Shopping mission types (2026-08-12, direct user screenshot) -- CHECK_IN/
// SCROLL/SPIN/CAT_FEED are real once-per-real-day missions (ShoppingMissionReward's own
// flags); WELCOME_BONUS is the one real once-ever claim (ShoppingWelcomeBonusClaim).
// rewardAmount for SPIN is nominal only -- see ShoppingMissionService.drawSpinReward for
// the real weighted-random payout, following the exact same real, stated-odds discipline
// StepRewardTier.lotteryOdds already established (item 248): odds are a public constant
// a client can display, not a hidden mechanic.
enum class ShoppingMissionType(val rewardAmount: BigDecimal) {
    CHECK_IN(BigDecimal("20")),
    SCROLL(BigDecimal("30")),
    SPIN(BigDecimal("0")),
    CAT_FEED(BigDecimal("15")),
    WELCOME_BONUS(BigDecimal("200")),
}

// Real, stated odds (same discipline as StepRewardTier.lotteryOdds, item 248) -- a
// weighted draw among 5 real payout amounts, heavier toward the low end so the
// expected value stays close to a flat daily mission (~30 RWF) rather than being a
// disguised high-value giveaway.
data class SpinOutcome(val amount: BigDecimal, val odds: Double)
val SPIN_OUTCOMES = listOf(
    SpinOutcome(BigDecimal("10"), 0.35),
    SpinOutcome(BigDecimal("20"), 0.30),
    SpinOutcome(BigDecimal("50"), 0.20),
    SpinOutcome(BigDecimal("100"), 0.10),
    SpinOutcome(BigDecimal("300"), 0.05),
)

data class MissionClaimResult(val type: ShoppingMissionType, val amountEarned: BigDecimal, val newAccountBalance: BigDecimal)
data class MissionStatusEntry(val type: ShoppingMissionType, val label: String, val rewardAmount: BigDecimal, val completedToday: Boolean, val claimedEver: Boolean = false)

/**
 * Real Toss Shopping "포인트 및 쿠폰받기" (get points and coupons) mission row -- see
 * `ShoppingMissionReward`'s own doc comment for the full sourced account. Every mission
 * pays real RWF straight into the user's real account via `LedgerService`, following
 * `StepRewardService`'s exact established architecture rather than inventing a second,
 * separate "points" currency this app has never had.
 */
@Service
class ShoppingMissionService(
    private val missionRepository: ShoppingMissionRewardRepository,
    private val welcomeBonusRepository: ShoppingWelcomeBonusClaimRepository,
    private val accountRepository: AccountRepository,
    private val ledgerService: LedgerService,
    private val notificationRepository: NotificationRepository,
    private val pushNotificationService: PushNotificationService,
    private val random: java.util.Random = SecureRandom(),
) {
    private val log = LoggerFactory.getLogger(ShoppingMissionService::class.java)

    fun getStatus(userId: String, today: LocalDate = LocalDate.now(ZoneOffset.UTC)): List<MissionStatusEntry> {
        val reward = missionRepository.findByUserIdAndMissionDate(userId, today.toString())
        val welcomeClaimed = welcomeBonusRepository.existsById(userId)
        return listOf(
            MissionStatusEntry(ShoppingMissionType.CHECK_IN, "Check-in", ShoppingMissionType.CHECK_IN.rewardAmount, reward?.checkedIn == true),
            MissionStatusEntry(ShoppingMissionType.SCROLL, "Scroll", ShoppingMissionType.SCROLL.rewardAmount, reward?.scrolled == true),
            MissionStatusEntry(ShoppingMissionType.SPIN, "Draw a prize", SPIN_OUTCOMES.minOf { it.amount }, reward?.spun == true),
            MissionStatusEntry(ShoppingMissionType.CAT_FEED, "Cat", ShoppingMissionType.CAT_FEED.rewardAmount, reward?.catFed == true),
            MissionStatusEntry(ShoppingMissionType.WELCOME_BONUS, "Claim reward", ShoppingMissionType.WELCOME_BONUS.rewardAmount, welcomeClaimed, claimedEver = welcomeClaimed),
        )
    }

    // Real, stated odds -- returned alongside the mission status above so a client can
    // show "35% chance of +10 RWF ... 5% chance of +300 RWF" before a user ever spins,
    // the same honest-disclosure discipline item 248 already established.
    fun spinOutcomes(): List<SpinOutcome> = SPIN_OUTCOMES

    @Transactional
    fun completeDailyMission(userId: String, type: ShoppingMissionType, today: LocalDate = LocalDate.now(ZoneOffset.UTC)): MissionClaimResult {
        require(type != ShoppingMissionType.WELCOME_BONUS) { "Use claimWelcomeBonus for the one-time welcome bonus" }
        val dateKey = today.toString()
        val existing = missionRepository.findByUserIdAndMissionDate(userId, dateKey)
        val reward = existing ?: ShoppingMissionReward(id = "shopmission_${UUID.randomUUID()}", userId = userId, missionDate = dateKey)

        val alreadyDone = when (type) {
            ShoppingMissionType.CHECK_IN -> reward.checkedIn
            ShoppingMissionType.SCROLL -> reward.scrolled
            ShoppingMissionType.SPIN -> reward.spun
            ShoppingMissionType.CAT_FEED -> reward.catFed
            ShoppingMissionType.WELCOME_BONUS -> true
        }
        if (alreadyDone) throw MissionAlreadyCompletedException("${type.name} already completed today")

        // Real weighted-random spin payout (SPIN only) -- every other mission pays its
        // own flat, disclosed rewardAmount.
        val amount = if (type == ShoppingMissionType.SPIN) drawSpinReward() else type.rewardAmount

        val newBalance = creditAccount(userId, amount, "Shopping mission - ${type.name}")
        when (type) {
            ShoppingMissionType.CHECK_IN -> reward.checkedIn = true
            ShoppingMissionType.SCROLL -> reward.scrolled = true
            ShoppingMissionType.SPIN -> reward.spun = true
            ShoppingMissionType.CAT_FEED -> reward.catFed = true
            ShoppingMissionType.WELCOME_BONUS -> {}
        }
        reward.updatedAt = Instant.now()
        missionRepository.save(reward)
        notifyMissionRewardEarned(userId, amount)
        return MissionClaimResult(type, amount, newBalance)
    }

    @Transactional
    fun claimWelcomeBonus(userId: String): MissionClaimResult {
        if (welcomeBonusRepository.existsById(userId)) throw MissionAlreadyCompletedException("Welcome bonus already claimed")
        val newBalance = creditAccount(userId, ShoppingMissionType.WELCOME_BONUS.rewardAmount, "Shopping mission - WELCOME_BONUS")
        // The real once-ever guard: a second concurrent claim fails this unique-PK
        // insert instead of racing an app-level boolean check.
        welcomeBonusRepository.save(ShoppingWelcomeBonusClaim(userId = userId))
        notifyMissionRewardEarned(userId, ShoppingMissionType.WELCOME_BONUS.rewardAmount)
        return MissionClaimResult(ShoppingMissionType.WELCOME_BONUS, ShoppingMissionType.WELCOME_BONUS.rewardAmount, newBalance)
    }

    // Real whole-class zero-notification gap found live (2026-09-14, same sweep that
    // already fixed RewardsService.claim/StepRewardService.reportSteps/
    // ShoppingCashbackService.awardCashback -- this class's own doc comment says it
    // follows "StepRewardService's exact established architecture", but that
    // architecture now includes a notification this class never copied).
    private fun notifyMissionRewardEarned(userId: String, amount: BigDecimal) {
        val title = "Reward earned"
        val body = "You earned ${amount.toPlainString()} RWF for completing a shopping mission."
        notificationRepository.save(
            Notification(
                id = "notif_${UUID.randomUUID()}", userId = userId, type = "SHOPPING_MISSION_REWARD_EARNED",
                title = title, body = body, isRead = false, createdAt = Instant.now(),
                dataJson = "{}",
            ),
        )
        sendPushAfterCommit(userId, title, body)
    }

    private fun sendPushAfterCommit(userId: String, title: String, body: String) {
        val send = {
            try {
                pushNotificationService.sendToUser(userId, title, body)
            } catch (e: Exception) {
                log.warn("Could not send shopping-mission push to user {}", userId, e)
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

    private fun drawSpinReward(): BigDecimal {
        val roll = random.nextDouble()
        var cumulative = 0.0
        for (outcome in SPIN_OUTCOMES) {
            cumulative += outcome.odds
            if (roll < cumulative) return outcome.amount
        }
        return SPIN_OUTCOMES.last().amount
    }

    private fun creditAccount(userId: String, amount: BigDecimal, memo: String): BigDecimal {
        val account = accountRepository.findByUserIdAndType(userId, AccountType.MAIN)
            ?: throw RewardsNoAccountException("No account found for this account")
        ledgerService.postLedgerTransaction(
            account.currency,
            listOf(
                LedgerLeg("rewards_expense", LedgerAccountType.REWARDS_EXPENSE, LedgerDirection.DEBIT, amount, memo),
                LedgerLeg(account.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, amount, memo),
            ),
        )
        return accountRepository.findByUserIdAndType(userId, AccountType.MAIN)?.balance ?: account.balance
    }
}
