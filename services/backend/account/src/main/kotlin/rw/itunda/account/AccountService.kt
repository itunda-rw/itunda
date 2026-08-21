package rw.itunda.account

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.LedgerEntry
import rw.itunda.core.domain.Notification
import rw.itunda.core.domain.SpendingBudget
import rw.itunda.core.domain.Transaction
import rw.itunda.core.domain.TransactionStatus
import rw.itunda.core.domain.TransactionType
import rw.itunda.core.domain.Account
import rw.itunda.core.domain.AccountType
import rw.itunda.core.events.EventPublisher
import rw.itunda.core.events.PaymentProviderFailedEvent
import rw.itunda.core.events.TOPIC_PAYMENT_PROVIDER_FAILED
import rw.itunda.core.events.TOPIC_TRANSFER_CONFIRMED
import rw.itunda.core.events.TransferConfirmedEvent
import rw.itunda.core.fraud.FraudRuleEngine
import rw.itunda.core.ledger.InsufficientFundsException
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.provider.ProviderConnector
import rw.itunda.core.provider.ProviderDeclinedException
import rw.itunda.core.provider.RailCatalog
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.LedgerEntryRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.SpendingBudgetRepository
import rw.itunda.core.repository.TransactionRepository
import rw.itunda.core.repository.AccountRepository
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.YearMonth
import java.util.UUID

/**
 * Port of backend/src/services/transfers.ts + account.controller.ts's quote/confirm split.
 * Ownership enforcement mirrors the fix applied to the Express backend (see SECURITY.md
 * "Broken authorization despite real authentication"): a account id supplied by the client
 * is only ever usable if it actually belongs to the authenticated caller.
 *
 * Not yet ported from the Express version: per-rail fee inference (rails.ts) -- this uses
 * a single flat 1% fee for every transfer, real per-rail fee tiers are follow-on work, not
 * silently dropped scope.
 *
 * Provider connector wired in (2026-07-11): previously every transfer always succeeded
 * with no rail simulation at all, unlike bills/airtime (see BillsService) -- explicitly
 * flagged open in docs/TOSS_RWANDA_ALIGNMENT.md's gap list ("Not yet extended to
 * transfers"). `recipient` is a free-text string (a phone number in practice, per
 * scripts/demo-e2e.sh), not a structured rail selection, so `RailCatalog.resolve` almost
 * always falls through to `generic` here rather than matching a named rail -- an honest
 * reflection of not having a real per-rail routing concept for P2P transfers yet, not a
 * bug.
 */
@Service
class AccountService(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val ledgerEntryRepository: LedgerEntryRepository,
    private val ledgerService: LedgerService,
    private val eventPublisher: EventPublisher,
    private val providerConnector: ProviderConnector,
    private val fraudRuleEngine: FraudRuleEngine,
    private val spendingBudgetRepository: SpendingBudgetRepository,
    private val notificationRepository: NotificationRepository,
    private val pushNotificationService: PushNotificationService,
) {
    private val quoteStore = QuoteStore()

    // Real Toss Timeline-style unusual-spend heuristic constants -- see
    // getTransactionTimeline's own doc comment for the full account.
    private val UNUSUAL_SPEND_MULTIPLIER = BigDecimal("2.5")
    private val MIN_PRIOR_DEBITS_FOR_BASELINE = 3

    fun getAccounts(userId: String): List<Account> = accountRepository.findByUserId(userId)

    // Real spending categorization (2026-07-13) -- deliberately built over the ledger, not
    // the `transactions` table. Every module (bills, loans, stocks, insurance, savings,
    // rewards, merchant) posts through LedgerService directly; only AccountService.
    // confirmTransfer ever writes a Transaction row, so categorizing by TransactionType
    // would show ~100% "Transfer" regardless of what a user actually did. Every ACCOUNT-
    // account DEBIT is real money leaving the account; its sibling ledger legs (same
    // transactionId) reveal what it actually paid for.
    fun getSpendingInsight(userId: String): SpendingInsightResult {
        val accountIds = accountRepository.findByUserId(userId).map { it.id }.toSet()
        val debits = accountIds
            .flatMap { ledgerEntryRepository.findByAccountIdOrderByCreatedAtDesc(it) }
            .filter { it.direction == LedgerDirection.DEBIT }
        return categorizeDebits(debits)
    }

    /**
     * Real Kakao Pay 페이아이 소비 리포트 (AI spending report, sourced 2026-08) -- a
     * period-over-period comparison [getSpendingInsight] never had (it's a real but
     * unbounded, all-time total). This month vs. last calendar month, reusing the exact
     * same [categorizeDebits] categorization so the numbers here can never drift from
     * what a plain spending-insight view would show for the same transactions. Real
     * month boundaries via [YearMonth], same convention [setBudget]/[getBudgets] already
     * use in this file -- not a fabricated "AI" model, an honest rules-based comparison
     * against the user's own real ledger history.
     */
    fun getMonthlySpendingReport(userId: String): MonthlySpendingReport {
        val accountIds = accountRepository.findByUserId(userId).map { it.id }.toSet()
        val startOfLastMonth = YearMonth.now().minusMonths(1).atDay(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()
        val startOfThisMonth = YearMonth.now().atDay(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()
        val debitsSinceLastMonth = accountIds
            .flatMap { ledgerEntryRepository.findByAccountIdAndCreatedAtAfter(it, startOfLastMonth) }
            .filter { it.direction == LedgerDirection.DEBIT }
        val (currentMonthDebits, lastMonthDebits) = debitsSinceLastMonth.partition { !it.createdAt.isBefore(startOfThisMonth) }

        val current = categorizeDebits(currentMonthDebits)
        val previous = categorizeDebits(lastMonthDebits)
        val previousByCategory = previous.categories.associate { it.name to it.amount }

        val categories = current.categories.map { c ->
            val previousAmount = previousByCategory[c.name] ?: BigDecimal.ZERO
            SpendingComparisonCategory(c.name, c.amount, previousAmount, percentChange(c.amount, previousAmount))
        }
        return MonthlySpendingReport(current.totalSpent, previous.totalSpent, percentChange(current.totalSpent, previous.totalSpent), categories)
    }

    // Null (not 0%) when there's genuinely nothing to compare against -- a real "new
    // this month" signal, same "never fabricate a derived number" discipline
    // MerchantProduct.discountPercent's own doc comment already established elsewhere.
    private fun percentChange(current: BigDecimal, previous: BigDecimal): Int? {
        if (previous.compareTo(BigDecimal.ZERO) == 0) return null
        return current.subtract(previous).divide(previous, 4, RoundingMode.HALF_UP).multiply(BigDecimal(100)).toInt()
    }

    // Real business expense summary (2026-08-11) -- see LoanOffer.requiresBusinessAccount's
    // own doc comment for the broader real business-banking gap this closes alongside.
    // Toss Bank's real "세금 신고용 이용내역 자동발송" (auto-send tax-filing usage summary,
    // tossbank.com/articles/selfemployed) periodically compiles a business account's own
    // categorized spend so a sole proprietor doesn't have to collect receipts by hand.
    // itunda has no real Rwanda Revenue Authority integration to file INTO (same
    // genuinely-blocked-external-access category as NIDA/PSP elsewhere in this codebase)
    // -- this is the real, honest slice: a categorized, period-scoped summary of the
    // BUSINESS account's own real ledger history, reusing getSpendingInsight's exact
    // categorization (`categorizeDebits` below) so the numbers here always match what the
    // same transactions would show on a personal spending insight. `sinceMonthsAgo`
    // defaults to 3 -- itunda's own honest choice; no real Rwandan tax-filing calendar
    // was sourced to anchor a specific period to (DESIGN_REFERENCES.md's own "Unresolved"
    // note for this section already flags Rwanda-specific filing dates as unsourced).
    fun getBusinessExpenseSummary(userId: String, sinceMonthsAgo: Long = 3): SpendingInsightResult {
        val account = accountRepository.findByUserIdAndType(userId, AccountType.BUSINESS)
            ?: throw AccountNotFoundException("Open an itunda Business account first")
        val since = Instant.now().minus(java.time.Duration.ofDays(sinceMonthsAgo * 30))
        val debits = ledgerEntryRepository.findByAccountIdAndCreatedAtAfter(account.id, since)
            .filter { it.direction == LedgerDirection.DEBIT }
        return categorizeDebits(debits)
    }

    private fun categorizeDebits(debits: List<LedgerEntry>): SpendingInsightResult {
        // Real N+1 fix (2026-07-19 sweep): one batch findByTransactionIdIn instead of one
        // findByTransactionId call per debit -- a real user's spending insight otherwise
        // cost one query per real debit ever made, growing unboundedly with usage.
        val siblingsByTransactionId = ledgerEntryRepository
            .findByTransactionIdIn(debits.map { it.transactionId }.distinct())
            .groupBy { it.transactionId }

        val totals = linkedMapOf<String, BigDecimal>()
        for (debit in debits) {
            val siblings = siblingsByTransactionId[debit.transactionId] ?: emptyList()
            // Real bug found live during this pass's own verification, unrelated to the
            // N+1 fix above but surfaced by it: a fee-charging transfer posts THREE legs
            // (ACCOUNT debit, RAIL_SUSPENSE credit, FEE_REVENUE credit), and neither
            // findByTransactionId nor findByTransactionIdIn has an ORDER BY, so plain
            // firstOrNull{} non-deterministically picked FEE_REVENUE over RAIL_SUSPENSE
            // depending on row order -- a real transfer could show up as "Fees" instead
            // of "Transfers". Prefer any non-account, non-fee sibling first; only fall
            // back to FEE_REVENUE if that's genuinely the sole counterpart.
            val counterpart = siblings.firstOrNull { it.accountType != LedgerAccountType.WALLET && it.accountType != LedgerAccountType.FEE_REVENUE }
                ?: siblings.firstOrNull { it.accountType != LedgerAccountType.WALLET }
            val category = when (counterpart?.accountType) {
                // RAIL_SUSPENSE is shared by three real modules (AccountService.confirmTransfer,
                // BillsService.payBill, BillsService.buyAirtime -- confirmed live, all three post
                // to the same "rail_suspense" clearing account), so accountType alone can't tell
                // them apart. Each debit's own memo can, since every module writes a distinct
                // prefix ("Transfer to X", "Bill payment X", "Airtime X") -- more precise than a
                // shared clearing-account label, still grounded in real written data, not guessed.
                LedgerAccountType.RAIL_SUSPENSE -> when {
                    debit.memo.startsWith("Bill payment", ignoreCase = true) -> "Bills"
                    debit.memo.startsWith("Airtime", ignoreCase = true) -> "Airtime"
                    else -> "Transfers"
                }
                LedgerAccountType.LOAN_PAYABLE -> "Loans"
                LedgerAccountType.SECURITIES_SUSPENSE -> "Investing"
                LedgerAccountType.SAVINGS_GOAL_PAYABLE -> "Savings"
                LedgerAccountType.INSURANCE_PREMIUM_REVENUE -> "Insurance"
                // Real Ejo Heza ya Moto-style premium savings fund (2026-08-02) -- a
                // manual/auto contribution debits the account and credits this account in
                // the very same transaction (InsuranceService.contributeToFund/
                // autoContributeToFund), the same shape SAVINGS_GOAL_PAYABLE already
                // establishes for savings deposits. Grouped under "Insurance" rather than
                // its own category since it's still real premium money, just paid ahead
                // of time instead of at collection time.
                LedgerAccountType.INSURANCE_PREMIUM_FUND_PAYABLE -> "Insurance"
                LedgerAccountType.FEE_REVENUE -> "Fees"
                LedgerAccountType.EATS_DELIVERY_HOLDING -> "Food delivery"
                // Real Baemin-style tiered order-amount promotion (2026-08-16) -- only
                // ever posted alongside an Eats order (see EatsOrderService.placeOrder),
                // same "Food delivery" category as EATS_DELIVERY_HOLDING above. A PICKUP
                // order (zero delivery fee, that leg filtered out entirely) with a real
                // promotion discount applied would otherwise have PROMOTION_EXPENSE as
                // its only non-account-non-fee counterpart -- still real food spending,
                // not a separate category.
                LedgerAccountType.PROMOTION_EXPENSE -> "Food delivery"
                LedgerAccountType.GIFT_HOLDING -> "Gifts"
                LedgerAccountType.AGENT_CASH -> "Cash-in"
                LedgerAccountType.CASH_VAULT -> "Other"
                LedgerAccountType.FX_CLEARING -> "Currency conversion"
                LedgerAccountType.MARKETPLACE_ESCROW_HOLDING -> "Marketplace"
                LedgerAccountType.BOOKING_DEPOSIT_HOLDING -> "Bookings"
                // Real 당근마켓 중고차 정비소 동행 (used-car mechanic-inspection
                // accompaniment) -- see VehicleInspectionBooking.kt's own doc comment.
                LedgerAccountType.VEHICLE_INSPECTION_HOLDING -> "Vehicle inspection"
                LedgerAccountType.RIDE_HOLDING -> "Rides"
                // Real Kakao T 대리운전 (designated driver) -- see
                // DesignatedDriverTrip.kt's own doc comment.
                LedgerAccountType.DESIGNATED_DRIVER_HOLDING -> "Designated driver"
                LedgerAccountType.EMOTICON_REVENUE -> "Emoticons"
                LedgerAccountType.GIFT_VOUCHER_HOLDING -> "Gift vouchers"
                // Unlike the credit-side expense accounts grouped into "Other" below,
                // CARD_SPEND_EXPENSE is genuinely credited in the very same transaction
                // a debit card purchase debits the account (CardService.chargeWithCard),
                // so it real-appears here and deserves its own category, not "Other".
                LedgerAccountType.CARD_SPEND_EXPENSE -> "Card purchases"
                // Same reasoning as CARD_SPEND_EXPENSE directly above -- a real postpaid
                // credit spend (PostpaidCreditService.spend) credits the account in the
                // very same transaction it debits POSTPAID_CREDIT_PAYABLE, so it
                // real-appears here and deserves its own category, not "Other".
                LedgerAccountType.POSTPAID_CREDIT_PAYABLE -> "Postpaid credit"
                // Real Korean 지연이체서비스 (Delayed Transfer Service, 2026-08-18) -- see
                // P2pDelayedTransfer.kt's own doc comment. Same reachable-in-practice
                // shape CARD_SPEND_EXPENSE/POSTPAID_CREDIT_PAYABLE establish just above:
                // P2pDelayedTransferService.sendDelayed debits the sender's ACCOUNT and
                // credits this holding account in the very same transaction, so it
                // real-appears here and deserves its own category, same as an instant
                // P2P transfer would (grouped under RAIL_SUSPENSE's "Transfers" case
                // above, but a delayed transfer never touches rail_suspense).
                LedgerAccountType.P2P_DELAY_HOLDING -> "Transfers"
                // REWARDS_EXPENSE/INTEREST_EXPENSE/INSURANCE_CLAIMS_EXPENSE are all credit-side
                // accounts (they pay money *into* a account) -- they'd never realistically be the
                // counterpart to a ACCOUNT debit here, but the compiler correctly demands every
                // LedgerAccountType be handled since this is an exhaustive `when`. INTEREST_INCOME
                // (2026-07-27) is the same shape -- OverdraftService.accrueInterest's own real
                // counterpart leg is LOAN_PAYABLE, never a direct ACCOUNT debit.
                // DEPOSIT_PROTECTION_RESERVE/DEPOSIT_PROTECTION_EXPENSE (2026-08-11) -- same
                // shape as this comment's own reasoning above: DepositProtectionService.
                // accrueContribution posts between itunda's own two internal accounts, never
                // touching a user's ACCOUNT debit, so this branch is unreachable in practice
                // but still required for exhaustiveness.
                LedgerAccountType.REWARDS_EXPENSE, LedgerAccountType.INTEREST_EXPENSE, LedgerAccountType.INSURANCE_CLAIMS_EXPENSE, LedgerAccountType.INTEREST_INCOME, LedgerAccountType.AGENT_COMMISSION_EXPENSE, LedgerAccountType.DEPOSIT_PROTECTION_RESERVE, LedgerAccountType.DEPOSIT_PROTECTION_EXPENSE, null -> "Other"
                LedgerAccountType.WALLET -> "Other"
            }
            totals[category] = (totals[category] ?: BigDecimal.ZERO) + debit.amount
        }

        val categories = totals.map { (name, amount) -> SpendingCategory(name, amount) }
            .sortedByDescending { it.amount }
        val total = totals.values.fold(BigDecimal.ZERO) { acc, v -> acc + v }
        return SpendingInsightResult(categories, total)
    }

    // Real budgeting/limits (2026-07-13) -- closes docs/TOSS_PARITY_MATRIX.md's Spending
    // row's own named gap. category == null means an overall (all-spending) budget;
    // otherwise it must match one of getSpendingInsight's own real category names, so a
    // budget's "spent" figure is grounded in the exact same categorization, not a
    // separate parallel one.
    @Transactional
    fun setBudget(userId: String, category: String?, monthlyLimit: BigDecimal): SpendingBudget {
        require(monthlyLimit > BigDecimal.ZERO) { "Monthly limit must be greater than zero" }
        val month = YearMonth.now().toString()
        val existing = spendingBudgetRepository.findByUserIdAndMonth(userId, month).find { it.category == category }
        if (existing != null) {
            existing.monthlyLimit = monthlyLimit
            existing.updatedAt = Instant.now()
            return spendingBudgetRepository.save(existing)
        }
        return spendingBudgetRepository.save(
            SpendingBudget(id = "budget_${UUID.randomUUID()}", userId = userId, category = category, monthlyLimit = monthlyLimit, month = month),
        )
    }

    @Transactional
    fun getBudgets(userId: String): List<BudgetView> {
        val month = YearMonth.now().toString()
        val budgets = spendingBudgetRepository.findByUserIdAndMonth(userId, month)
        if (budgets.isEmpty()) return emptyList()

        val insight = getSpendingInsight(userId)
        val spentByCategory = insight.categories.associate { it.name to it.amount }

        return budgets.map { budget ->
            val spent = if (budget.category == null) insight.totalSpent else (spentByCategory[budget.category] ?: BigDecimal.ZERO)
            val percentUsed = if (budget.monthlyLimit > BigDecimal.ZERO) {
                spent.divide(budget.monthlyLimit, 4, RoundingMode.HALF_UP).multiply(BigDecimal(100)).toInt()
            } else 0
            val status = when {
                percentUsed >= 100 -> BudgetStatus.OVER
                percentUsed >= 80 -> BudgetStatus.NEAR
                else -> BudgetStatus.UNDER
            }
            maybeNotifyBudgetThreshold(budget, status)
            BudgetView(
                category = budget.category,
                monthlyLimit = budget.monthlyLimit,
                spent = spent,
                remaining = (budget.monthlyLimit - spent).max(BigDecimal.ZERO),
                percentUsed = percentUsed,
                status = status,
            )
        }
    }

    // Real, once-per-threshold-per-month alert (2026-07-13) -- writes an actual
    // Notification row (rw.itunda.notifications' own GET /api/v1/notifications
    // already reads this table; nothing in this backend had ever written to it
    // outside of demo seed data before this). notifiedNear/notifiedOver guard against
    // re-notifying on every single GET /budgets poll.
    //
    // Real push wired in (2026-07-28) -- a budget alert triggered by a GET /budgets poll
    // is exactly the kind of thing a user would otherwise never see until they happen to
    // check their budgets screen; the whole point of a spending alert is catching it
    // before the NEXT purchase, not after.
    private fun maybeNotifyBudgetThreshold(budget: SpendingBudget, status: BudgetStatus) {
        val label = budget.category ?: "overall spending"
        if (status == BudgetStatus.OVER && !budget.notifiedOver) {
            budget.notifiedOver = true
            val title = "Budget exceeded"
            val body = "You've gone over your $label budget for this month."
            notificationRepository.save(
                Notification(
                    id = "notif_${UUID.randomUUID()}", userId = budget.userId, type = "BUDGET_OVER",
                    title = title, body = body,
                    isRead = false, createdAt = Instant.now(), dataJson = null,
                ),
            )
            pushNotificationService.sendToUser(budget.userId, title, body)
            spendingBudgetRepository.save(budget)
        } else if (status == BudgetStatus.NEAR && !budget.notifiedNear) {
            budget.notifiedNear = true
            val title = "Approaching budget limit"
            val body = "You've used 80% or more of your $label budget for this month."
            notificationRepository.save(
                Notification(
                    id = "notif_${UUID.randomUUID()}", userId = budget.userId, type = "BUDGET_NEAR",
                    title = title, body = body,
                    isRead = false, createdAt = Instant.now(), dataJson = null,
                ),
            )
            pushNotificationService.sendToUser(budget.userId, title, body)
            spendingBudgetRepository.save(budget)
        }
    }

    // Real transaction history (2026-07-12) -- TransactionRepository's
    // findBySenderIdOrRecipientIdOrderByCreatedAtDesc already existed with no
    // controller endpoint ever calling it; this is what backs the new card/
    // transaction-history screen on both platforms.
    fun getTransactionHistory(userId: String): List<Transaction> =
        transactionRepository.findBySenderIdOrRecipientIdOrderByCreatedAtDesc(userId, userId)

    /**
     * Real Toss Timeline (타임라인)-style unusual-spend flag (2026-07-26) -- see
     * blog.toss.im/2020/01/09/toss/experience/toss-user-interview-timeline (Toss's own
     * user-interview writeup of the real feature: "평소보다 큰 지출은 빨간색으로 표시되어
     * 어디에 과도하게 돈을 썼는지 쉽게 볼 수 있습니다" -- an unusually large expense is
     * shown in red right in the transaction list, distinct from the monthly-aggregate
     * [getBudgets] limit this backend already had). Toss's own exact comparison formula
     * isn't published, so this is itunda's own honest heuristic, not a claimed reproduction:
     * a COMPLETED debit (this user's own money leaving) is flagged when it exceeds
     * [UNUSUAL_SPEND_MULTIPLIER] times the average of this user's own PRIOR completed
     * debits, and only once at least [MIN_PRIOR_DEBITS_FOR_BASELINE] prior debits exist --
     * a brand-new account's very first purchase has no real baseline to be "unusual"
     * against, so it's never flagged. Deliberately computed chronologically forward (a
     * transaction is only compared against debits that happened BEFORE it), never using
     * a later transaction to judge an earlier one -- the same "no lookahead" discipline
     * SubscriptionDetectionService's own price-change comparison already established.
     * Purely additive: [getTransactionHistory] above is completely unchanged, this is a
     * new, separate read path over the exact same real Transaction rows.
     */
    fun getTransactionTimeline(userId: String): List<TransactionTimelineEntry> {
        val transactions = transactionRepository.findBySenderIdOrRecipientIdOrderByCreatedAtDesc(userId, userId)
        val ascending = transactions.sortedBy { it.createdAt }
        val priorDebitAmounts = mutableListOf<BigDecimal>()
        val unusuallyLargeById = mutableMapOf<String, Boolean>()
        for (t in ascending) {
            if (t.senderId != userId || t.status != TransactionStatus.COMPLETED) continue
            val unusuallyLarge = if (priorDebitAmounts.size >= MIN_PRIOR_DEBITS_FOR_BASELINE) {
                val average = priorDebitAmounts.fold(BigDecimal.ZERO) { acc, a -> acc + a }
                    .divide(BigDecimal(priorDebitAmounts.size), 4, RoundingMode.HALF_UP)
                t.amount > average.multiply(UNUSUAL_SPEND_MULTIPLIER)
            } else {
                false
            }
            unusuallyLargeById[t.id] = unusuallyLarge
            priorDebitAmounts.add(t.amount)
        }
        return transactions.map { TransactionTimelineEntry(it, unusuallyLargeById[it.id] ?: false) }
    }

    fun getAccountById(accountId: String, userId: String): Account {
        val account = accountRepository.findById(accountId).orElse(null)
        // 404 (not 403) on someone else's account id, so this endpoint can't be used to
        // probe which account ids exist — same choice made in the Express fix.
        if (account == null || account.userId != userId) throw AccountNotFoundException("Account not found")
        return account
    }

    fun quoteTransfer(userId: String, fromAccountId: String?, recipient: String, amount: BigDecimal): TransferQuote {
        require(amount > BigDecimal.ZERO) { "Amount must be greater than zero" }
        require(recipient.isNotBlank()) { "Recipient is required" }

        val accountId = fromAccountId ?: accountRepository.findByUserIdAndType(userId, AccountType.MAIN)?.id
            ?: throw AccountNotFoundException("No account found for this account")
        val account = accountRepository.findById(accountId).orElseThrow { AccountNotFoundException("Account not found") }
        // Real IDOR fix (2026-08-02): a caller-supplied fromAccountId that belongs to a
        // DIFFERENT user used to 403 ("that account does not belong to you") rather than
        // 404 -- confirming the id is real to anyone who guesses or enumerates it, the
        // same probe [getAccountById]'s own doc comment already documents fixing for the
        // direct account-lookup endpoint. Same fix, same reasoning, applied here too.
        if (account.userId != userId) throw AccountNotFoundException("Account not found")

        val fee = amount.multiply(BigDecimal("0.01")).setScale(0, RoundingMode.HALF_UP)
        if (account.availableBalance < amount.add(fee)) {
            throw InsufficientFundsException("Insufficient available balance for this transfer")
        }

        return quoteStore.create(userId, account.id, recipient, amount, fee, account.currency)
    }

    @Transactional
    fun confirmTransfer(quoteId: String, userId: String): Pair<Transaction, BigDecimal> {
        val quote = quoteStore.get(quoteId) ?: throw QuoteNotFoundException("Transfer quote not found")
        // Real IDOR fix (2026-08-02): a quoteId belonging to a DIFFERENT user used to
        // 403 ("that quote does not belong to you") rather than 404, the same
        // real-existence-confirming probe [getAccountById]'s own doc comment already
        // documents fixing for the direct account-lookup endpoint -- same fix here.
        if (quote.userId != userId) throw QuoteNotFoundException("Transfer quote not found")

        // Real double-spend fix (2026-08-02): claim() atomically transitions PENDING ->
        // CLAIMED (see QuoteStore.claim's own doc comment) so at most one concurrent
        // confirmTransfer call for this exact quoteId can ever pass this point -- the
        // previous shape (a plain `quote.status == PENDING` read here, followed by a
        // plain `quote.status = CONFIRMED` write only after the provider call and
        // ledger post below) had no such atomicity: two concurrent calls could both
        // observe PENDING before either wrote CONFIRMED, and both go on to post the
        // real ledger legs below, a genuine double-spend of one quote.
        val claimed = quoteStore.claim(quoteId, Instant.now()) ?: when (quote.status) {
            QuoteStatus.CONFIRMED -> throw QuoteAlreadyUsedException("Transfer quote was already confirmed")
            QuoteStatus.CLAIMED -> throw QuoteAlreadyUsedException("Transfer quote is already being confirmed")
            else -> throw QuoteExpiredException("Transfer quote is ${quote.status.name.lowercase()}, request a new quote")
        }

        // Real per-rail routing (2026-07-13) -- resolve() (used by bills/airtime,
        // which get a real provider name) always fell through to generic here since
        // quote.recipient is a phone number, not a provider name. See
        // RailCatalog.resolveByPhoneNumber's own doc comment for the real, sourced
        // (RURA numbering plan) prefix routing this now does instead.
        val rail = RailCatalog.resolveByPhoneNumber(claimed.recipient)
        try {
            providerConnector.attempt(rail, "Transfer to ${claimed.recipient}")
        } catch (e: ProviderDeclinedException) {
            // Releases the claim back to PENDING so the same quote can still be
            // retried, matching the pre-existing behavior (a decline never used to
            // touch quote.status at all, leaving it PENDING for a retry).
            quoteStore.releaseClaim(quoteId)
            // Published via publishImmediately, not publishAfterCommit -- this
            // @Transactional method is about to roll back once the exception below
            // propagates (nothing has been written yet), so an afterCommit hook would
            // never fire for it. Same reasoning as BillsService.attemptOrPublishFailure.
            eventPublisher.publishImmediately(
                TOPIC_PAYMENT_PROVIDER_FAILED,
                rail.id,
                PaymentProviderFailedEvent(
                    railId = rail.id,
                    railDisplayName = rail.displayName,
                    description = "Transfer to ${claimed.recipient}",
                    amount = claimed.amount,
                    currency = claimed.currency,
                    reason = e.message ?: "declined",
                    failedAt = Instant.now(),
                ),
            )
            throw e
        }

        val result = ledgerService.postLedgerTransaction(
            claimed.currency,
            listOf(
                LedgerLeg(claimed.fromAccountId, LedgerAccountType.WALLET, LedgerDirection.DEBIT, claimed.totalDebit, "Transfer to ${claimed.recipient}"),
                LedgerLeg("rail_suspense", LedgerAccountType.RAIL_SUSPENSE, LedgerDirection.CREDIT, claimed.amount, "Rail settlement for ${claimed.recipient}"),
                LedgerLeg("fee_revenue", LedgerAccountType.FEE_REVENUE, LedgerDirection.CREDIT, claimed.fee, "Transfer fee"),
            ),
        )
        claimed.status = QuoteStatus.CONFIRMED

        val account = accountRepository.findById(claimed.fromAccountId).orElseThrow { AccountNotFoundException("Account not found") }
        val transaction = Transaction(
            id = result.transactionId,
            referenceNumber = "TXN${System.currentTimeMillis()}${UUID.randomUUID().toString().take(4)}",
            senderId = account.userId,
            recipientId = "external",
            fromAccountId = quote.fromAccountId,
            amount = quote.amount,
            fee = quote.fee,
            currency = quote.currency,
            type = TransactionType.TRANSFER,
            status = TransactionStatus.COMPLETED,
            description = "Transfer to ${quote.recipient}",
            completedAt = Instant.now(),
            createdAt = quote.createdAt,
        )
        // Fraud review wired in (2026-07-13) -- same real FraudRuleEngine already wired
        // into P2pService.payRequest, extended to itunda's other real money-moving path.
        // recipientUserId is null, not "external": this flow has no real itunda-user
        // recipient concept (confirmed in P2pService's own doc comment -- transfer always
        // routes through the simulated external rail), so NEW_RECIPIENT simply never
        // fires here, which is correct rather than a gap. Called before the save, same
        // ordering reasoning as P2pService.payRequest's own inline comment: evaluating
        // after would let this transaction match itself as prior history.
        fraudRuleEngine.evaluate(account.userId, null, quote.amount, transaction.id)
        transactionRepository.save(transaction)

        eventPublisher.publishAfterCommit(
            TOPIC_TRANSFER_CONFIRMED,
            transaction.id,
            TransferConfirmedEvent(
                transactionId = transaction.id,
                fromAccountId = quote.fromAccountId,
                recipient = quote.recipient,
                amount = quote.amount,
                fee = quote.fee,
                currency = quote.currency,
                confirmedAt = Instant.now(),
            ),
        )

        return transaction to account.balance
    }
}
