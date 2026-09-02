package rw.itunda.savings

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.InterestJar
import rw.itunda.core.domain.LedgerAccount
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.Notification
import rw.itunda.core.domain.SavingsGoal
import rw.itunda.core.domain.SavingsGoalStatus
import rw.itunda.core.domain.Transaction
import rw.itunda.core.domain.TransactionStatus
import rw.itunda.core.domain.TransactionType
import rw.itunda.core.domain.AccountType
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.InterestJarRepository
import rw.itunda.core.repository.LedgerAccountRepository
import rw.itunda.core.repository.LedgerEntryRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.SavingsGoalRepository
import rw.itunda.core.repository.TransactionRepository
import rw.itunda.core.repository.AccountRepository
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
class AccountNotOwnedException(message: String) : RuntimeException(message)
class NoAccountException(message: String) : RuntimeException(message)
class NoInterestJarException(message: String) : RuntimeException(message)
class NoInterestAvailableException(message: String) : RuntimeException(message)
class GoalAlreadyCompletedException(message: String) : RuntimeException(message)
class InsufficientGoalBalanceException(message: String) : RuntimeException(message)

/**
 * Port of backend/src/controllers/savings.controller.ts, with the same ownership check
 * added to the Express fix: a caller can only deposit from a account they actually own.
 * Unlike the Express version's single hardcoded interestJar object (one user's data,
 * gated to that owner after the fix), InterestJar here is a real per-user table from the
 * start — a new user simply doesn't have a row yet (404) rather than being blocked from
 * a shared singleton that was never theirs.
 */
@Service
class SavingsService(
    private val accountRepository: AccountRepository,
    private val savingsGoalRepository: SavingsGoalRepository,
    private val interestJarRepository: InterestJarRepository,
    private val ledgerService: LedgerService,
    private val rateLimiter: RateLimiter,
    private val transactionRepository: TransactionRepository,
    private val notificationRepository: NotificationRepository,
    private val pushNotificationService: PushNotificationService,
    private val ledgerAccountRepository: LedgerAccountRepository,
    private val ledgerEntryRepository: LedgerEntryRepository,
) {
    private val log = LoggerFactory.getLogger(SavingsService::class.java)

    fun getGoals(userId: String) = savingsGoalRepository.findByUserId(userId)

    private fun goalLedgerAccountId(goal: SavingsGoal) = "sg_ledger_${goal.id}"

    // Real per-bucket ledger isolation (2026-08-31) -- see BucketTransactionDto's own
    // doc comment for the full "why": every goal used to share ONE pool account
    // ("savings_goal_payable", also reused by the unrelated MotoOwnershipService), so
    // no goal's own deposit/withdrawal history could ever be isolated. Get-or-create,
    // same proven pattern as AgentService's per-agent cash account -- no LedgerService/
    // schema change needed, just a new row in the existing ledger_accounts table.
    private fun ensureGoalLedgerAccount(goal: SavingsGoal): String {
        val id = goalLedgerAccountId(goal)
        if (!ledgerAccountRepository.existsById(id)) {
            ledgerAccountRepository.save(LedgerAccount(id = id, name = "Savings Goal: ${goal.name}"))
            // Real one-time reconciliation for goals that already held money BEFORE
            // this per-goal isolation shipped: that money is real and currently sits
            // in the shared pool, not this brand-new account. Without this, the very
            // next withdrawal would debit a account that has never actually held any
            // of this goal's money, driving it negative while the old pool stays
            // overstated by the same amount -- a real ledger-integrity bug, not just a
            // cosmetic history gap. This is a real, auditable balancing entry that
            // correctly re-attributes money that already, truly belongs to this goal
            // -- not fake data. Itemized history from before this point isn't
            // recoverable (the shared pool never distinguished goals), so this goal's
            // own ledger honestly starts here, at its true current balance.
            if (goal.currentAmount > BigDecimal.ZERO) {
                ledgerService.postLedgerTransaction(
                    "RWF",
                    listOf(
                        LedgerLeg("savings_goal_payable", LedgerAccountType.SAVINGS_GOAL_PAYABLE, LedgerDirection.DEBIT, goal.currentAmount, "Opening balance for ${goal.name}"),
                        LedgerLeg(id, LedgerAccountType.SAVINGS_GOAL_PAYABLE, LedgerDirection.CREDIT, goal.currentAmount, "Opening balance for ${goal.name}"),
                    ),
                )
            }
        }
        return id
    }

    fun getGoalTransactions(userId: String, goalId: String): List<BucketTransactionDto> {
        val goal = savingsGoalRepository.findById(goalId).filter { it.userId == userId }.orElseThrow { GoalNotFoundException("Goal not found") }
        return ledgerEntryRepository.findByAccountIdOrderByCreatedAtDesc(goalLedgerAccountId(goal)).map { it.toBucketTransactionDto() }
    }

    fun getInterestJarTransactions(userId: String): List<BucketTransactionDto> {
        val jar = interestJarRepository.findById(userId).orElseThrow { NoInterestJarException("No interest jar found for this account") }
        return ledgerEntryRepository.findByAccountIdOrderByCreatedAtDesc(jar.accountId).map { it.toBucketTransactionDto() }
    }

    @Transactional
    fun createGoal(userId: String, name: String, targetAmount: BigDecimal, monthlyContribution: BigDecimal?, targetDate: String?, category: String?): SavingsGoal {
        // Real anti-spam limit -- found missing in a 2026-07-19 security sweep. Unlike
        // deposit/claim (both money-moving, both already Idempotency-Key protected),
        // goal creation is free row creation with zero protection of any kind.
        rateLimiter.checkLimit("savings:goal:$userId", limit = 10, window = Duration.ofHours(1))
        val savingsAccount = accountRepository.findByUserIdAndType(userId, AccountType.SAVINGS) ?: throw NoAccountException("No savings account found for this account")
        return savingsGoalRepository.save(
            SavingsGoal(
                id = "sg_${UUID.randomUUID()}", userId = userId, accountId = savingsAccount.id, name = name,
                targetAmount = targetAmount, currentAmount = BigDecimal.ZERO,
                monthlyContribution = monthlyContribution ?: BigDecimal.ZERO, interestRate = 7.5,
                targetDate = targetDate, category = category ?: "general",
            ),
        )
    }

    @Transactional
    fun depositToGoal(userId: String, goalId: String, amount: BigDecimal, fromAccountId: String?): SavingsGoal {
        // Real hardening (concurrency-audit thread) -- locks the goal row before the
        // currentAmount read/mutate + ledger post below, avoiding wasted ledger-posting
        // work + a raw optimistic-lock exception if this races withdrawFromGoal/
        // autoContribute on the same goal. SavingsGoal's own @Version already made this
        // provably NOT a fund-leak either way -- see this thread's own standing rule.
        val goal = savingsGoalRepository.findByIdForUpdate(goalId).filter { it.userId == userId }.orElseThrow { GoalNotFoundException("Goal not found") }
        // Real bug found+fixed (2026-08-23): nothing previously stopped a deposit into an
        // already-completed goal -- the real money debit/ledger-credit below ran
        // unconditionally, but `currentAmount` is capped at `targetAmount`, so the money
        // would leave the user's account and land in the savings_goal_payable ledger
        // account with no corresponding increase anywhere the user can see -- functionally
        // vanishing. **Closed 2026-08-31** -- see withdrawFromGoal below, a real
        // withdraw/close-goal path now exists to reclaim it.
        if (goal.status == SavingsGoalStatus.completed) throw GoalAlreadyCompletedException("This goal has already reached its target")

        val sourceAccount = if (fromAccountId != null) {
            val account = accountRepository.findById(fromAccountId).orElseThrow { NoAccountException("Account not found") }
            if (account.userId != userId) throw AccountNotOwnedException("That account does not belong to you")
            account
        } else {
            accountRepository.findByUserIdAndType(userId, AccountType.MAIN) ?: throw NoAccountException("No account found for this account")
        }

        // Real bug found live (2026-08-31, via InsuranceService.contributeToFund's own
        // build-time review comment naming this exact bug class here first): this used
        // to post the FULL requested `amount` to the ledger and only cap the *field*
        // (currentAmount) at targetAmount afterwards -- an overshooting deposit moved
        // real money into savings_goal_payable that the capped field then never
        // accounted for, and withdrawFromGoal can only ever reclaim up to
        // goal.currentAmount, permanently stranding the excess with no path back to the
        // user. Clamping the amount actually moved to the real remaining gap BEFORE
        // touching the ledger keeps every RWF that leaves the account accounted for and
        // withdrawable.
        val actualAmount = amount.min(goal.targetAmount.subtract(goal.currentAmount))
        ledgerService.postLedgerTransaction(
            sourceAccount.currency,
            listOf(
                LedgerLeg(sourceAccount.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, actualAmount, "Deposit to ${goal.name}"),
                LedgerLeg(ensureGoalLedgerAccount(goal), LedgerAccountType.SAVINGS_GOAL_PAYABLE, LedgerDirection.CREDIT, actualAmount, "Deposit to ${goal.name}"),
            ),
        )

        goal.currentAmount = goal.currentAmount.add(actualAmount).min(goal.targetAmount)
        val justCompleted = goal.status != SavingsGoalStatus.completed && goal.currentAmount >= goal.targetAmount
        if (justCompleted) goal.status = SavingsGoalStatus.completed
        val saved = savingsGoalRepository.save(goal)
        if (justCompleted) notifyGoalCompleted(saved)
        return saved
    }

    // Real gap found live (2026-08-31, direct user reference against Toss's own real
    // 보관하기/나눠모으기 pockets -- every one supports both 채우기 (fill) and 꺼내기
    // (withdraw), never a one-way deposit): depositToGoal's own doc comment already
    // named this exact gap in 2026-08-23 ("functionally vanishing... no withdraw/
    // close-goal endpoint to reclaim it") but it was never actually built until now.
    // Real double-entry reversal of the exact deposit legs (flipped direction), same
    // reasoning OrderService.cancelOrder/EatsOrderService.refundAndCancel already
    // establish elsewhere in this codebase -- never mutates the goal's own deposit
    // history, just posts a new, offsetting transaction. A partial withdraw simply
    // reduces currentAmount; a full withdraw (amount == currentAmount) leaves the goal
    // at zero, still active, not deleted -- same "closing a pocket doesn't delete its
    // history" real Toss behavior.
    @Transactional
    fun withdrawFromGoal(userId: String, goalId: String, amount: BigDecimal, toAccountId: String?): SavingsGoal {
        // Real hardening (concurrency-audit thread) -- same reasoning as
        // depositToGoal's own identical lock above.
        val goal = savingsGoalRepository.findByIdForUpdate(goalId).filter { it.userId == userId }.orElseThrow { GoalNotFoundException("Goal not found") }
        if (amount <= BigDecimal.ZERO) throw InsufficientGoalBalanceException("Amount must be positive")
        if (amount > goal.currentAmount) {
            throw InsufficientGoalBalanceException("Cannot withdraw more than this goal's current balance (${goal.currentAmount})")
        }

        val destinationAccount = if (toAccountId != null) {
            val account = accountRepository.findById(toAccountId).orElseThrow { NoAccountException("Account not found") }
            if (account.userId != userId) throw AccountNotOwnedException("That account does not belong to you")
            account
        } else {
            accountRepository.findByUserIdAndType(userId, AccountType.MAIN) ?: throw NoAccountException("No account found for this account")
        }

        ledgerService.postLedgerTransaction(
            destinationAccount.currency,
            listOf(
                LedgerLeg(ensureGoalLedgerAccount(goal), LedgerAccountType.SAVINGS_GOAL_PAYABLE, LedgerDirection.DEBIT, amount, "Withdraw from ${goal.name}"),
                LedgerLeg(destinationAccount.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, amount, "Withdraw from ${goal.name}"),
            ),
        )

        goal.currentAmount = goal.currentAmount.subtract(amount)
        // A completed goal that's had money withdrawn back out is honestly no longer
        // complete -- matches how a real bank account reopens for contributions the
        // moment its balance drops below a "goal reached" milestone.
        if (goal.status == SavingsGoalStatus.completed && goal.currentAmount < goal.targetAmount) {
            goal.status = SavingsGoalStatus.active
        }
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
    fun autoContribute(goalArg: SavingsGoal): Boolean {
        // Real hardening (concurrency-audit thread) -- goalArg was loaded by
        // AutoSaveScheduler's OWN transaction (a batch query, `getGoalsDueForAutoContribution`),
        // already committed and detached by the time this method's own @Transactional
        // starts. Re-fetching locked here (rather than mutating goalArg directly) closes
        // the same race depositToGoal/withdrawFromGoal's own locks close -- a manual
        // deposit/withdraw racing this scheduled contribution on the same goal.
        val goal = savingsGoalRepository.findByIdForUpdate(goalArg.id).orElseThrow { GoalNotFoundException("Goal not found") }
        val sourceAccount = accountRepository.findByUserIdAndType(goal.userId, AccountType.MAIN)
        if (sourceAccount == null || sourceAccount.availableBalance < goal.monthlyContribution) {
            log.info("Skipping auto-contribution for goal {} -- insufficient funds or no MAIN account", goal.id)
            return false
        }

        // Same real overshoot fix as depositToGoal above (2026-08-31) -- a recurring
        // auto-contribution that would overshoot the goal's target previously still
        // moved the FULL monthlyContribution into the ledger, stranding the excess.
        val actualContribution = goal.monthlyContribution.min(goal.targetAmount.subtract(goal.currentAmount))
        ledgerService.postLedgerTransaction(
            sourceAccount.currency,
            listOf(
                LedgerLeg(sourceAccount.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, actualContribution, "Auto-save to ${goal.name}"),
                LedgerLeg(ensureGoalLedgerAccount(goal), LedgerAccountType.SAVINGS_GOAL_PAYABLE, LedgerDirection.CREDIT, actualContribution, "Auto-save to ${goal.name}"),
            ),
        )

        goal.currentAmount = goal.currentAmount.add(actualContribution).min(goal.targetAmount)
        // getGoalsDueForAutoContribution already filters to status == active, so this is
        // always a genuine active->completed transition, unlike depositToGoal's own guard.
        val justCompleted = goal.currentAmount >= goal.targetAmount
        if (justCompleted) goal.status = SavingsGoalStatus.completed
        goal.lastAutoContributionAt = Instant.now()
        val saved = savingsGoalRepository.save(goal)
        if (justCompleted) notifyGoalCompleted(saved)
        return true
    }

    fun getInterestJar(userId: String) = interestJarRepository.findById(userId).orElseThrow { NoInterestJarException("No interest jar found for this account") }

    @Transactional
    fun claimInterest(userId: String): Map<String, Any?> {
        val jar = interestJarRepository.findById(userId).orElseThrow { NoInterestJarException("No interest jar found for this account") }
        if (jar.earnedThisMonth <= BigDecimal.ZERO) throw NoInterestAvailableException("No interest available to claim")

        // Real fix (2026-08-11): interest now auto-credits the real account balance
        // the instant it accrues (see accrueInterest's own doc comment, matching real
        // Toss Bank passbook interest -- "통장 이자" posts directly, no manual claim
        // step exists in a real bank). Posting a SECOND ledger credit here for the
        // same already-arrived money would be a real double-credit bug -- this now
        // just clears the running "earned this month" display counter, the same
        // "mark as seen" shape a notification-read flag has, not a real second
        // transfer. The account balance genuinely doesn't change here anymore.
        val claimed = jar.earnedThisMonth
        val account = accountRepository.findById(jar.accountId).orElseThrow { NoAccountException("Account not found") }
        jar.earnedThisMonth = BigDecimal.ZERO
        jar.lastPaidAt = Instant.now()
        interestJarRepository.save(jar)

        return mapOf("claimed" to claimed, "newBalance" to account.balance)
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
    // history the moment it accrues): interest now credits the real account balance
    // and creates a real Transaction row on EVERY accrual, not just a display-only
    // `earnedThisMonth` counter requiring a separate manual claim. This replaces the
    // previous "accrue into a jar, then claim into the account" two-step flow --
    // real bank passbook interest has no manual claim step at all, it just appears.
    // `earnedThisMonth`/`earnedTotal` are kept as running display totals of interest
    // ALREADY credited (not pending), still useful for the Interest jar summary card.
    // jar.balance stays a synced display cache of the real account balance, never the
    // source of truth. nextPayoutAt advances by exactly one real day (not "now + 1
    // day") so a scheduler catch-up after downtime doesn't silently shrink the
    // accrual window.
    @Transactional
    fun accrueInterest(jar: InterestJar) {
        val account = accountRepository.findById(jar.accountId).orElse(null) ?: return
        val dailyRate = BigDecimal.valueOf(jar.rate).divide(BigDecimal(100), 10, RoundingMode.HALF_UP).divide(BigDecimal(365), 10, RoundingMode.HALF_UP)
        val accrued = account.balance.multiply(dailyRate).setScale(2, RoundingMode.HALF_UP)
        // Real celebratory moment (2026-08-23) -- see notifyGoalCompleted/
        // notifyLoanPaidOff's own doc comments for the sourced Toss rationale this
        // continues. Deliberately a ONE-TIME signal, not fired on every accrual --
        // this scheduler runs daily per jar, so a push on every single accrual would be
        // real notification fatigue, exactly what the interaction-philosophy research
        // this whole initiative is grounded in (toss.tech/article/interaction) names as
        // a reason a design gets discarded, not shipped. earnedTotal is a lifetime
        // running total that's never reset (unlike earnedThisMonth), so "was zero,
        // about to become positive" is a safe, genuinely once-ever signal per jar.
        val isFirstAccrualEver = jar.earnedTotal == BigDecimal.ZERO && accrued > BigDecimal.ZERO
        if (accrued > BigDecimal.ZERO) {
            val ledger = ledgerService.postLedgerTransaction(
                "RWF",
                listOf(
                    LedgerLeg("interest_expense", LedgerAccountType.INTEREST_EXPENSE, LedgerDirection.DEBIT, accrued, "Savings interest"),
                    LedgerLeg(account.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, accrued, "Savings interest"),
                ),
            )
            transactionRepository.save(
                Transaction(
                    id = ledger.transactionId,
                    referenceNumber = "INTEREST${System.currentTimeMillis()}${UUID.randomUUID().toString().take(4)}",
                    senderId = "system_interest",
                    recipientId = jar.userId,
                    toAccountId = account.id,
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
        val updatedAccount = accountRepository.findById(jar.accountId).orElse(account)
        jar.balance = updatedAccount.balance
        jar.nextPayoutAt = jar.nextPayoutAt.plus(INTEREST_ACCRUAL_INTERVAL_DAYS, ChronoUnit.DAYS)
        val saved = interestJarRepository.save(jar)
        if (isFirstAccrualEver) notifyFirstInterestAccrual(saved, accrued)
    }

    private fun notifyFirstInterestAccrual(jar: InterestJar, accrued: BigDecimal) {
        try {
            val title = "Your money started earning 🎉"
            val body = "You just earned your first $accrued RWF in savings interest -- it'll keep adding up automatically."
            notificationRepository.save(
                Notification(
                    id = "notif_${UUID.randomUUID()}", userId = jar.userId, type = "FIRST_INTEREST_ACCRUAL",
                    title = title, body = body, isRead = false, createdAt = Instant.now(), dataJson = "{\"accountId\":\"${jar.accountId}\"}",
                ),
            )
            sendFirstInterestAccrualPushAfterCommit(jar.userId, title, body, jar.accountId)
        } catch (e: Exception) {
            // Non-critical -- the real interest accrual already succeeded.
        }
    }

    private fun sendFirstInterestAccrualPushAfterCommit(userId: String, title: String, body: String, accountId: String) {
        val send = { pushNotificationService.sendToUser(userId, title, body, mapOf("accountId" to accountId), type = "FIRST_INTEREST_ACCRUAL") }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            send()
            return
        }
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() = send()
        })
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

    // Real celebratory moment (2026-08-23, itunda's own product-feel initiative) --
    // Toss's own real interaction-design writing (toss.tech/article/1st_interaction_designer)
    // cites a congratulatory message on a fully-paid-off loan as a real example of "finding
    // the hidden emotion" behind a transaction, not just confirming it happened. A completed
    // savings goal is the direct savings-side analogue: real user effort (recurring
    // deposits/auto-contributions over time) reaching a real target, deserving the same
    // acknowledgement -- and itunda already had the exact backend event
    // (SavingsGoalStatus.completed) with zero notification wired to it before this.
    // Deferred to after-commit -- see PushNotificationService's own doc comment and
    // P2pService.sendMoneyReceivedPushAfterCommit's identical pattern: this fires from
    // inside an already-open @Transactional method, so the push must wait for that
    // transaction to actually commit rather than firing (and potentially misleading the
    // user) ahead of a rollback.
    private fun notifyGoalCompleted(goal: SavingsGoal) {
        try {
            val title = "Goal reached! 🎉"
            val body = "You've saved ${goal.currentAmount} RWF for \"${goal.name}\" -- goal complete."
            notificationRepository.save(
                Notification(
                    id = "notif_${UUID.randomUUID()}", userId = goal.userId, type = "SAVINGS_GOAL_COMPLETED",
                    title = title, body = body, isRead = false, createdAt = Instant.now(), dataJson = "{\"goalId\":\"${goal.id}\"}",
                ),
            )
            sendGoalCompletedPushAfterCommit(goal.userId, title, body, goal.id)
        } catch (e: Exception) {
            // Non-critical -- the real deposit/auto-contribution already succeeded.
        }
    }

    private fun sendGoalCompletedPushAfterCommit(userId: String, title: String, body: String, goalId: String) {
        val send = { pushNotificationService.sendToUser(userId, title, body, mapOf("goalId" to goalId), type = "SAVINGS_GOAL_COMPLETED") }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            send()
            return
        }
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() = send()
        })
    }
}
