package rw.itunda.account

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.LedgerEntry
import rw.itunda.core.domain.Notification
import rw.itunda.core.domain.SpendingBudget
import rw.itunda.core.domain.AccountType
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.LedgerEntryRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.SpendingBudgetRepository
import rw.itunda.core.repository.AccountRepository
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.YearMonth
import java.util.UUID

/**
 * Split out of AccountService.kt (2026-08-21) -- see
 * docs/ARCHITECTURE_GUIDELINES.md §2 ("code that changes together lives together") and
 * scripts/file-size-lint.py's own real 500-line guideline, which AccountService.kt had
 * just crossed for the first time. Spending categorization and budgets are a real,
 * cohesive concern distinct from AccountService's own core account/transfer
 * responsibility -- no other module ever called these methods except through
 * AccountController, so nothing outside this pair (controller + service) needed to move.
 */
@Service
class SpendingInsightService(
    private val accountRepository: AccountRepository,
    private val ledgerEntryRepository: LedgerEntryRepository,
    private val spendingBudgetRepository: SpendingBudgetRepository,
    private val notificationRepository: NotificationRepository,
    private val pushNotificationService: PushNotificationService,
) {
    // Real spending categorization (2026-07-13) -- deliberately built over the ledger, not
    // the `transactions` table. Every module (bills, loans, stocks, insurance, savings,
    // rewards, merchant) posts through LedgerService directly; only AccountService.
    // confirmTransfer ever writes a Transaction row, so categorizing by TransactionType
    // would show ~100% "Transfer" regardless of what a user actually did. Every ACCOUNT-
    // account DEBIT is real money leaving the account; its sibling ledger legs (same
    // transactionId) reveal what it actually paid for.
    fun getSpendingInsight(userId: String): SpendingInsightResult {
        val accountIds = accountRepository.findByUserId(userId).map { it.id }.toSet()
        // Real N+1 fix (2026-09-13) -- see LedgerEntryRepository.findByAccountIdInOrderByCreatedAtDesc's
        // own doc comment: a real user can hold several accounts (GROUP per Ikimina joined,
        // FOREIGN_CURRENCY per currency held), so this was one query per account, not 1-3.
        val debits = (if (accountIds.isEmpty()) emptyList() else ledgerEntryRepository.findByAccountIdInOrderByCreatedAtDesc(accountIds))
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
        // Real N+1 fix (2026-09-13) -- same fix shape as getSpendingInsight above.
        val debitsSinceLastMonth = (if (accountIds.isEmpty()) emptyList() else ledgerEntryRepository.findByAccountIdInAndCreatedAtAfter(accountIds, startOfLastMonth))
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
                // Same reasoning as CARD_SPEND_EXPENSE/POSTPAID_CREDIT_PAYABLE above -- a
                // real transit top-up (TransitService.topUp) debits the account and
                // credits this holding account in the very same transaction, so it
                // real-appears here and deserves its own category.
                LedgerAccountType.TRANSIT_BALANCE_PAYABLE -> "Transit"
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
                // TRANSIT_FARE_EXPENSE (2026-08-27) -- same shape as this comment's own
                // reasoning above: TransitService.tapFare posts between itunda's own two
                // internal accounts (TRANSIT_BALANCE_PAYABLE debit / TRANSIT_FARE_EXPENSE
                // credit), never touching a user's ACCOUNT debit directly (that already
                // happened, into TRANSIT_BALANCE_PAYABLE, at top-up time) -- unreachable
                // in practice but still required for exhaustiveness.
                // BAD_DEBT_EXPENSE (Bank product-completeness pass, cycle 2, 2026-09-08) --
                // same shape: VupLoanService.decide/CooperativeService.decide's write-off
                // posting is entirely between itunda's own two internal accounts
                // (bad_debt_expense debit / loan_payable credit), never touching the
                // borrower's own ACCOUNT at all -- unreachable in practice but still
                // required for exhaustiveness.
                LedgerAccountType.REWARDS_EXPENSE, LedgerAccountType.INTEREST_EXPENSE, LedgerAccountType.INSURANCE_CLAIMS_EXPENSE, LedgerAccountType.INTEREST_INCOME, LedgerAccountType.AGENT_COMMISSION_EXPENSE, LedgerAccountType.DEPOSIT_PROTECTION_RESERVE, LedgerAccountType.DEPOSIT_PROTECTION_EXPENSE, LedgerAccountType.TRANSIT_FARE_EXPENSE, LedgerAccountType.BAD_DEBT_EXPENSE, null -> "Other"
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
            spendingBudgetRepository.save(budget)
            // Real fix (2026-09-13, push-before-commit ordering sweep): the push used to
            // fire BEFORE notifiedOver was saved -- a rollback after the push would leave
            // the flag unset and the very next GET /budgets poll would resend it. Same
            // discipline SavingsService.sendMaturityReminder's own fix already establishes.
            sendBudgetPushAfterCommit(budget.userId, title, body)
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
            spendingBudgetRepository.save(budget)
            // Real fix (2026-09-13, push-before-commit ordering sweep) -- same as the
            // OVER branch above.
            sendBudgetPushAfterCommit(budget.userId, title, body)
        }
    }

    private fun sendBudgetPushAfterCommit(userId: String, title: String, body: String) {
        val send = { pushNotificationService.sendToUser(userId, title, body) }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            send()
            return
        }
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() = send()
        })
    }
}
