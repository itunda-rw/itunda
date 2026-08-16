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
import rw.itunda.core.domain.Transaction
import rw.itunda.core.domain.TransactionStatus
import rw.itunda.core.domain.TransactionType
import rw.itunda.core.domain.WalletType
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.InterestJarRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.SavingsGoalRepository
import rw.itunda.core.repository.TransactionRepository
import rw.itunda.core.repository.WalletRepository
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import java.util.UUID

private const val AUTO_CONTRIBUTION_INTERVAL_DAYS = 30L
private const val INTEREST_ACCRUAL_INTERVAL_DAYS = 1L

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
    private val transactionRepository: TransactionRepository,
    private val notificationRepository: NotificationRepository,
    private val pushNotificationService: PushNotificationService,
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

        // Real fix (2026-08-11): interest now auto-credits the real wallet balance
        // the instant it accrues (see accrueInterest's own doc comment, matching real
        // Toss Bank passbook interest -- "통장 이자" posts directly, no manual claim
        // step exists in a real bank). Posting a SECOND ledger credit here for the
        // same already-arrived money would be a real double-credit bug -- this now
        // just clears the running "earned this month" display counter, the same
        // "mark as seen" shape a notification-read flag has, not a real second
        // transfer. The wallet balance genuinely doesn't change here anymore.
        val claimed = jar.earnedThisMonth
        val wallet = walletRepository.findById(jar.walletId).orElseThrow { NoWalletException("Wallet not found") }
        jar.earnedThisMonth = BigDecimal.ZERO
        jar.lastPaidAt = Instant.now()
        interestJarRepository.save(jar)

        return mapOf("claimed" to claimed, "newBalance" to wallet.balance)
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

    // Real Toss Bank passbook interest semantics (user-provided screenshots,
    // 2026-08-11 -- "통장 이자" +36원/+19원 posting directly into the real transaction
    // history the moment it accrues): interest now credits the real wallet balance
    // and creates a real Transaction row on EVERY accrual, not just a display-only
    // `earnedThisMonth` counter requiring a separate manual claim. This replaces the
    // previous "accrue into a jar, then claim into the wallet" two-step flow --
    // real bank passbook interest has no manual claim step at all, it just appears.
    // `earnedThisMonth`/`earnedTotal` are kept as running display totals of interest
    // ALREADY credited (not pending), still useful for the Interest jar summary card.
    // jar.balance stays a synced display cache of the real wallet balance, never the
    // source of truth. nextPayoutAt advances by exactly one real day (not "now + 1
    // day") so a scheduler catch-up after downtime doesn't silently shrink the
    // accrual window.
    @Transactional
    fun accrueInterest(jar: InterestJar) {
        val wallet = walletRepository.findById(jar.walletId).orElse(null) ?: return
        val dailyRate = BigDecimal.valueOf(jar.rate).divide(BigDecimal(100), 10, RoundingMode.HALF_UP).divide(BigDecimal(365), 10, RoundingMode.HALF_UP)
        val accrued = wallet.balance.multiply(dailyRate).setScale(2, RoundingMode.HALF_UP)
        if (accrued > BigDecimal.ZERO) {
            val ledger = ledgerService.postLedgerTransaction(
                "RWF",
                listOf(
                    LedgerLeg("interest_expense", LedgerAccountType.INTEREST_EXPENSE, LedgerDirection.DEBIT, accrued, "Savings interest"),
                    LedgerLeg(wallet.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, accrued, "Savings interest"),
                ),
            )
            transactionRepository.save(
                Transaction(
                    id = ledger.transactionId,
                    referenceNumber = "INTEREST${System.currentTimeMillis()}${UUID.randomUUID().toString().take(4)}",
                    senderId = "system_interest",
                    recipientId = jar.userId,
                    toWalletId = wallet.id,
                    amount = accrued,
                    fee = BigDecimal.ZERO,
                    currency = "RWF",
                    type = TransactionType.INTEREST,
                    status = TransactionStatus.COMPLETED,
                    description = "Savings interest",
                    completedAt = Instant.now(),
                ),
            )
            jar.earnedThisMonth = jar.earnedThisMonth.add(accrued)
            jar.earnedTotal = jar.earnedTotal.add(accrued)
        }
        val updatedWallet = walletRepository.findById(jar.walletId).orElse(wallet)
        jar.balance = updatedWallet.balance
        jar.nextPayoutAt = jar.nextPayoutAt.plus(INTEREST_ACCRUAL_INTERVAL_DAYS, ChronoUnit.DAYS)
        interestJarRepository.save(jar)
    }

    // Real KB국민은행-style 상품만기알림서비스 (product maturity alert service,
    // obank.kbstar.com's own real named product) candidate query -- an active goal
    // whose real targetDate has genuinely arrived (today or already past) and hasn't
    // been notified yet. targetDate is real free-text set at goal creation with no
    // format enforcement (see SavingsService.createGoal), so a value that doesn't
    // parse as a real ISO LocalDate is honestly skipped here rather than crashing the
    // whole sweep over one bad row -- the same per-row resilience
    // StockPriceAlertScheduler's own doc comment already establishes for an unrelated
    // reminder feature.
    fun getGoalsDueForMaturityReminder(): List<SavingsGoal> {
        val today = LocalDate.now()
        return savingsGoalRepository.findByStatusAndTargetDateIsNotNullAndMaturityNotifiedAtIsNull(SavingsGoalStatus.active)
            .filter { goal ->
                val raw = goal.targetDate ?: return@filter false
                val parsed = try {
                    LocalDate.parse(raw)
                } catch (e: DateTimeParseException) {
                    null
                }
                parsed != null && !parsed.isAfter(today)
            }
    }

    /** One real maturity-reminder notification, called per-goal by the scheduler --
     * re-checks `maturityNotifiedAt` right before sending so a genuine race can't
     * double-fire, same resilience discipline [[StocksService.triggerPriceAlert]]'s own
     * doc comment already establishes. */
    @Transactional
    fun sendMaturityReminder(goalId: String) {
        val goal = savingsGoalRepository.findById(goalId).orElse(null) ?: return
        if (goal.status != SavingsGoalStatus.active || goal.maturityNotifiedAt != null) return

        val title = "${goal.name} has matured"
        val body = "Your savings goal \"${goal.name}\" reached its target date. Current balance: ${goal.currentAmount} RWF."
        notificationRepository.save(
            Notification(
                id = "notif_${UUID.randomUUID()}", userId = goal.userId, type = "SAVINGS_GOAL_MATURED",
                title = title, body = body, isRead = false, createdAt = Instant.now(), dataJson = "{\"goalId\":\"${goal.id}\"}",
            ),
        )
        pushNotificationService.sendToUser(goal.userId, title, body, mapOf("goalId" to goal.id))
        goal.maturityNotifiedAt = Instant.now()
        savingsGoalRepository.save(goal)
    }
}
