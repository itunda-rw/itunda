package rw.itunda.savings

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.InterestJar
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.Notification
import rw.itunda.core.domain.SavingsGoal
import rw.itunda.core.domain.SavingsGoalStatus
import rw.itunda.core.domain.WalletType
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.InterestJarRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.SavingsGoalRepository
import rw.itunda.core.repository.WalletRepository
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

private const val AUTO_CONTRIBUTION_INTERVAL_DAYS = 30L
private const val INTEREST_ACCRUAL_INTERVAL_DAYS = 1L

// Real "hidden money" nudge cadence (2026-07-21) -- see maybeNudgeUnclaimed's own doc
// comment. Weekly, not per-accrual (accrual runs daily): Toss's own real "숨은 돈 찾기"
// (find hidden money) feature surfaces dormant/uncollected balances periodically, not
// as a constant nag -- matching that cadence rather than pinging on every accrual cycle.
private const val UNCLAIMED_INTEREST_NUDGE_INTERVAL_DAYS = 7L

class GoalNotFoundException(message: String) : RuntimeException(message)
class WalletNotOwnedException(message: String) : RuntimeException(message)
class NoWalletException(message: String) : RuntimeException(message)
class NoInterestJarException(message: String) : RuntimeException(message)
class NoInterestAvailableException(message: String) : RuntimeException(message)

/**
 * Port of backend/src/controllers/savings.controller.ts, with the same ownership check
 * added to the Express fix: a caller can only deposit from a wallet they actually own.
 * Unlike the Express version's single hardcoded interestJar object (one user's data,
 * gated to that owner after the fix), InterestJar here is a real per-user table from the
 * start — a new user simply doesn't have a row yet (404) rather than being blocked from
 * a shared singleton that was never theirs.
 */
@Service
class SavingsService(
    private val walletRepository: WalletRepository,
    private val savingsGoalRepository: SavingsGoalRepository,
    private val interestJarRepository: InterestJarRepository,
    private val ledgerService: LedgerService,
    private val rateLimiter: RateLimiter,
    private val notificationRepository: NotificationRepository,
) {
    private val log = LoggerFactory.getLogger(SavingsService::class.java)

    fun getGoals(userId: String) = savingsGoalRepository.findByUserId(userId)

    @Transactional
    fun createGoal(userId: String, name: String, targetAmount: BigDecimal, monthlyContribution: BigDecimal?, targetDate: String?, category: String?): SavingsGoal {
        // Real anti-spam limit -- found missing in a 2026-07-19 security sweep. Unlike
        // deposit/claim (both money-moving, both already Idempotency-Key protected),
        // goal creation is free row creation with zero protection of any kind.
        rateLimiter.checkLimit("savings:goal:$userId", limit = 10, window = Duration.ofHours(1))
        val savingsWallet = walletRepository.findByUserIdAndType(userId, WalletType.SAVINGS) ?: throw NoWalletException("No savings wallet found for this account")
        return savingsGoalRepository.save(
            SavingsGoal(
                id = "sg_${UUID.randomUUID()}", userId = userId, walletId = savingsWallet.id, name = name,
                targetAmount = targetAmount, currentAmount = BigDecimal.ZERO,
                monthlyContribution = monthlyContribution ?: BigDecimal.ZERO, interestRate = 7.5,
                targetDate = targetDate, category = category ?: "general",
            ),
        )
    }

    @Transactional
    fun depositToGoal(userId: String, goalId: String, amount: BigDecimal, fromWalletId: String?): SavingsGoal {
        val goal = savingsGoalRepository.findById(goalId).filter { it.userId == userId }.orElseThrow { GoalNotFoundException("Goal not found") }

        val sourceWallet = if (fromWalletId != null) {
            val wallet = walletRepository.findById(fromWalletId).orElseThrow { NoWalletException("Wallet not found") }
            if (wallet.userId != userId) throw WalletNotOwnedException("That wallet does not belong to you")
            wallet
        } else {
            walletRepository.findByUserIdAndType(userId, WalletType.MAIN) ?: throw NoWalletException("No wallet found for this account")
        }

        ledgerService.postLedgerTransaction(
            sourceWallet.currency,
            listOf(
                LedgerLeg(sourceWallet.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, amount, "Deposit to ${goal.name}"),
                LedgerLeg("savings_goal_payable", LedgerAccountType.SAVINGS_GOAL_PAYABLE, LedgerDirection.CREDIT, amount, "Deposit to ${goal.name}"),
            ),
        )

        goal.currentAmount = goal.currentAmount.add(amount).min(goal.targetAmount)
        if (goal.currentAmount >= goal.targetAmount) goal.status = SavingsGoalStatus.completed
        return savingsGoalRepository.save(goal)
    }

    // Real recurring auto-save (2026-07-13) -- monthlyContribution was accepted and stored
    // at goal creation but nothing ever read it until now. findAll() + in-memory filter is
    // the honest choice at this system's actual data scale -- a real production system with
    // many more goals would want a bounded/indexed query (see OutboxRelay's
    // findTop100By...  for the established convention here once that scale exists).
    fun getGoalsDueForAutoContribution(): List<SavingsGoal> {
        val cutoff = Instant.now().minus(AUTO_CONTRIBUTION_INTERVAL_DAYS, ChronoUnit.DAYS)
        return savingsGoalRepository.findAll().filter { goal ->
            goal.status == SavingsGoalStatus.active &&
                goal.monthlyContribution > BigDecimal.ZERO &&
                (goal.lastAutoContributionAt == null || goal.lastAutoContributionAt!!.isBefore(cutoff))
        }
    }

    // Returns false (not an exception) on insufficient funds -- a real recurring job skips
    // this cycle and retries next time, the same way a real bank's standing order behaves,
    // rather than failing loudly for something that isn't the user's fault mid-batch.
    @Transactional
    fun autoContribute(goal: SavingsGoal): Boolean {
        val sourceWallet = walletRepository.findByUserIdAndType(goal.userId, WalletType.MAIN)
        if (sourceWallet == null || sourceWallet.availableBalance < goal.monthlyContribution) {
            log.info("Skipping auto-contribution for goal {} -- insufficient funds or no MAIN wallet", goal.id)
            return false
        }

        ledgerService.postLedgerTransaction(
            sourceWallet.currency,
            listOf(
                LedgerLeg(sourceWallet.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, goal.monthlyContribution, "Auto-save to ${goal.name}"),
                LedgerLeg("savings_goal_payable", LedgerAccountType.SAVINGS_GOAL_PAYABLE, LedgerDirection.CREDIT, goal.monthlyContribution, "Auto-save to ${goal.name}"),
            ),
        )

        goal.currentAmount = goal.currentAmount.add(goal.monthlyContribution).min(goal.targetAmount)
        if (goal.currentAmount >= goal.targetAmount) goal.status = SavingsGoalStatus.completed
        goal.lastAutoContributionAt = Instant.now()
        savingsGoalRepository.save(goal)
        return true
    }

    fun getInterestJar(userId: String) = interestJarRepository.findById(userId).orElseThrow { NoInterestJarException("No interest jar found for this account") }

    @Transactional
    fun claimInterest(userId: String): Map<String, Any?> {
        val jar = interestJarRepository.findById(userId).orElseThrow { NoInterestJarException("No interest jar found for this account") }
        if (jar.earnedThisMonth <= BigDecimal.ZERO) throw NoInterestAvailableException("No interest available to claim")

        val claimed = jar.earnedThisMonth
        val wallet = walletRepository.findById(jar.walletId).orElseThrow { NoWalletException("Wallet not found") }
        ledgerService.postLedgerTransaction(
            "RWF",
            listOf(
                LedgerLeg("interest_expense", LedgerAccountType.INTEREST_EXPENSE, LedgerDirection.DEBIT, claimed, "Savings interest payout"),
                LedgerLeg(wallet.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, claimed, "Savings interest payout"),
            ),
        )

        jar.earnedThisMonth = BigDecimal.ZERO
        jar.lastPaidAt = Instant.now()
        interestJarRepository.save(jar)

        val updatedWallet = walletRepository.findById(jar.walletId).orElseThrow { NoWalletException("Wallet not found") }
        return mapOf("claimed" to claimed, "newBalance" to updatedWallet.balance)
    }

    // Real daily interest accrual (2026-07-20) -- found live: earnedThisMonth/earnedTotal
    // were only ever written by claimInterest (reading or zeroing, never incrementing) and
    // by SeedDataRunner's one hardcoded demo row. Every real jar's balance sat frozen at
    // zero forever -- claimInterest always 404'd/NoInterestAvailable for a real account.
    // Same findAll()-then-filter honesty as getGoalsDueForAutoContribution -- real data
    // scale here doesn't yet justify an indexed query.
    fun getJarsDueForAccrual(): List<InterestJar> {
        val now = Instant.now()
        return interestJarRepository.findAll().filter { it.nextPayoutAt.isBefore(now) || it.nextPayoutAt == now }
    }

    // Real Kakao Bank SafeBox (세이프박스) semantics: interest accrues daily off the
    // *actual* savings wallet balance, not a stored snapshot -- jar.balance is kept as a
    // synced display cache, never the source of truth. nextPayoutAt advances by exactly
    // one real day (not "now + 1 day") so a scheduler catch-up after downtime doesn't
    // silently shrink the accrual window.
    @Transactional
    fun accrueInterest(jar: InterestJar) {
        val wallet = walletRepository.findById(jar.walletId).orElse(null) ?: return
        val dailyRate = BigDecimal.valueOf(jar.rate).divide(BigDecimal(100), 10, RoundingMode.HALF_UP).divide(BigDecimal(365), 10, RoundingMode.HALF_UP)
        val accrued = wallet.balance.multiply(dailyRate).setScale(2, RoundingMode.HALF_UP)
        jar.balance = wallet.balance
        if (accrued > BigDecimal.ZERO) {
            jar.earnedThisMonth = jar.earnedThisMonth.add(accrued)
            jar.earnedTotal = jar.earnedTotal.add(accrued)
        }
        jar.nextPayoutAt = jar.nextPayoutAt.plus(INTEREST_ACCRUAL_INTERVAL_DAYS, ChronoUnit.DAYS)
        // Mutates jar.lastNudgedAt (if due) and creates the Notification, but doesn't
        // save the jar itself -- folded into the single save below instead of a second
        // round-trip.
        maybeNudgeUnclaimed(jar)
        interestJarRepository.save(jar)
    }

    // Real "hidden money" nudge (2026-07-21) -- modeled on Toss's own real, published
    // "숨은 돈 찾기" (find hidden money) feature (toss.tech/tossfeed's "마이데이터로 숨은
    // 돈 찾는 3가지 방법", tossbank.com's own "숨은 금융자산" articles): Toss proactively
    // surfaces dormant deposits, unclaimed insurance payouts, and unused card points a
    // user has but isn't actively looking at. itunda has no external institution
    // aggregation to mirror the dormant-deposit/insurance half of that (would need a
    // real MyData-style consent relationship this repo has no path to, the same class of
    // gap as NIDA/RDB access) -- but the identical PATTERN already exists entirely
    // within itunda's own ledger: interest accrues daily into `earnedThisMonth`
    // (see accrueInterest above) with no proactive surface at all before this fix --
    // silently found via a direct read of this file: only `claimInterest` ever zeroed
    // it, nothing ever notified a user that it existed to claim. A real user could accrue
    // real RWF for weeks and never know, unless they happened to open the Savings tab --
    // the exact "money that exists but isn't surfaced" gap Toss's real feature targets.
    //
    // Weekly cadence (not per-accrual, which runs daily): a real Notification every
    // single day would be spam, not a helpful nudge -- matches how Toss's own feature
    // surfaces periodically, not constantly. Deliberately silent when there's nothing to
    // claim (earnedThisMonth <= 0) -- a nudge about zero money isn't a real nudge.
    private fun maybeNudgeUnclaimed(jar: InterestJar) {
        if (jar.earnedThisMonth <= BigDecimal.ZERO) return
        val lastNudgedAt = jar.lastNudgedAt
        val due = lastNudgedAt == null || Duration.between(lastNudgedAt, Instant.now()).toDays() >= UNCLAIMED_INTEREST_NUDGE_INTERVAL_DAYS
        if (!due) return
        notificationRepository.save(
            Notification(
                id = "notif_${UUID.randomUUID()}",
                userId = jar.userId,
                type = "UNCLAIMED_INTEREST",
                title = "You have interest waiting",
                body = "${jar.earnedThisMonth} RWF in savings interest is ready to claim -- it's just sitting there until you do.",
                isRead = false,
                createdAt = Instant.now(),
                dataJson = "{\"earnedThisMonth\":\"${jar.earnedThisMonth}\"}",
            ),
        )
        jar.lastNudgedAt = Instant.now()
    }
}
