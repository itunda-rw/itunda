package rw.itunda.wallet

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.Notification
import rw.itunda.core.domain.SpendingBudget
import rw.itunda.core.domain.Transaction
import rw.itunda.core.domain.TransactionStatus
import rw.itunda.core.domain.TransactionType
import rw.itunda.core.domain.Wallet
import rw.itunda.core.domain.WalletType
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
import rw.itunda.core.repository.LedgerEntryRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.SpendingBudgetRepository
import rw.itunda.core.repository.TransactionRepository
import rw.itunda.core.repository.WalletRepository
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.YearMonth
import java.util.UUID

/**
 * Port of backend/src/services/transfers.ts + wallet.controller.ts's quote/confirm split.
 * Ownership enforcement mirrors the fix applied to the Express backend (see SECURITY.md
 * "Broken authorization despite real authentication"): a wallet id supplied by the client
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
class WalletService(
    private val walletRepository: WalletRepository,
    private val transactionRepository: TransactionRepository,
    private val ledgerEntryRepository: LedgerEntryRepository,
    private val ledgerService: LedgerService,
    private val eventPublisher: EventPublisher,
    private val providerConnector: ProviderConnector,
    private val fraudRuleEngine: FraudRuleEngine,
    private val spendingBudgetRepository: SpendingBudgetRepository,
    private val notificationRepository: NotificationRepository,
) {
    private val quoteStore = QuoteStore()

    fun getWallets(userId: String): List<Wallet> = walletRepository.findByUserId(userId)

    // Real spending categorization (2026-07-13) -- deliberately built over the ledger, not
    // the `transactions` table. Every module (bills, loans, stocks, insurance, savings,
    // rewards, merchant) posts through LedgerService directly; only WalletService.
    // confirmTransfer ever writes a Transaction row, so categorizing by TransactionType
    // would show ~100% "Transfer" regardless of what a user actually did. Every WALLET-
    // account DEBIT is real money leaving the wallet; its sibling ledger legs (same
    // transactionId) reveal what it actually paid for.
    fun getSpendingInsight(userId: String): SpendingInsightResult {
        val walletIds = walletRepository.findByUserId(userId).map { it.id }.toSet()
        val debits = walletIds
            .flatMap { ledgerEntryRepository.findByAccountIdOrderByCreatedAtDesc(it) }
            .filter { it.direction == LedgerDirection.DEBIT }

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
            // (WALLET debit, RAIL_SUSPENSE credit, FEE_REVENUE credit), and neither
            // findByTransactionId nor findByTransactionIdIn has an ORDER BY, so plain
            // firstOrNull{} non-deterministically picked FEE_REVENUE over RAIL_SUSPENSE
            // depending on row order -- a real transfer could show up as "Fees" instead
            // of "Transfers". Prefer any non-wallet, non-fee sibling first; only fall
            // back to FEE_REVENUE if that's genuinely the sole counterpart.
            val counterpart = siblings.firstOrNull { it.accountType != LedgerAccountType.WALLET && it.accountType != LedgerAccountType.FEE_REVENUE }
                ?: siblings.firstOrNull { it.accountType != LedgerAccountType.WALLET }
            val category = when (counterpart?.accountType) {
                // RAIL_SUSPENSE is shared by three real modules (WalletService.confirmTransfer,
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
                LedgerAccountType.FEE_REVENUE -> "Fees"
                LedgerAccountType.EATS_DELIVERY_HOLDING -> "Food delivery"
                LedgerAccountType.GIFT_HOLDING -> "Gifts"
                LedgerAccountType.AGENT_CASH -> "Cash-in"
                LedgerAccountType.CASH_VAULT -> "Other"
                LedgerAccountType.FX_CLEARING -> "Currency conversion"
                LedgerAccountType.MARKETPLACE_ESCROW_HOLDING -> "Marketplace"
                LedgerAccountType.BOOKING_DEPOSIT_HOLDING -> "Bookings"
                LedgerAccountType.RIDE_HOLDING -> "Rides"
                LedgerAccountType.EMOTICON_REVENUE -> "Emoticons"
                // REWARDS_EXPENSE/INTEREST_EXPENSE/INSURANCE_CLAIMS_EXPENSE are all credit-side
                // accounts (they pay money *into* a wallet) -- they'd never realistically be the
                // counterpart to a WALLET debit here, but the compiler correctly demands every
                // LedgerAccountType be handled since this is an exhaustive `when`.
                LedgerAccountType.REWARDS_EXPENSE, LedgerAccountType.INTEREST_EXPENSE, LedgerAccountType.INSURANCE_CLAIMS_EXPENSE, null -> "Other"
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
    private fun maybeNotifyBudgetThreshold(budget: SpendingBudget, status: BudgetStatus) {
        val label = budget.category ?: "overall spending"
        if (status == BudgetStatus.OVER && !budget.notifiedOver) {
            budget.notifiedOver = true
            notificationRepository.save(
                Notification(
                    id = "notif_${UUID.randomUUID()}", userId = budget.userId, type = "BUDGET_OVER",
                    title = "Budget exceeded", body = "You've gone over your $label budget for this month.",
                    isRead = false, createdAt = Instant.now(), dataJson = null,
                ),
            )
            spendingBudgetRepository.save(budget)
        } else if (status == BudgetStatus.NEAR && !budget.notifiedNear) {
            budget.notifiedNear = true
            notificationRepository.save(
                Notification(
                    id = "notif_${UUID.randomUUID()}", userId = budget.userId, type = "BUDGET_NEAR",
                    title = "Approaching budget limit", body = "You've used 80% or more of your $label budget for this month.",
                    isRead = false, createdAt = Instant.now(), dataJson = null,
                ),
            )
            spendingBudgetRepository.save(budget)
        }
    }

    // Real transaction history (2026-07-12) -- TransactionRepository's
    // findBySenderIdOrRecipientIdOrderByCreatedAtDesc already existed with no
    // controller endpoint ever calling it; this is what backs the new card/
    // transaction-history screen on both platforms.
    fun getTransactionHistory(userId: String): List<Transaction> =
        transactionRepository.findBySenderIdOrRecipientIdOrderByCreatedAtDesc(userId, userId)

    fun getWalletById(walletId: String, userId: String): Wallet {
        val wallet = walletRepository.findById(walletId).orElse(null)
        // 404 (not 403) on someone else's wallet id, so this endpoint can't be used to
        // probe which wallet ids exist — same choice made in the Express fix.
        if (wallet == null || wallet.userId != userId) throw WalletNotFoundException("Wallet not found")
        return wallet
    }

    fun quoteTransfer(userId: String, fromWalletId: String?, recipient: String, amount: BigDecimal): TransferQuote {
        require(amount > BigDecimal.ZERO) { "Amount must be greater than zero" }
        require(recipient.isNotBlank()) { "Recipient is required" }

        val walletId = fromWalletId ?: walletRepository.findByUserIdAndType(userId, WalletType.MAIN)?.id
            ?: throw WalletNotFoundException("No wallet found for this account")
        val wallet = walletRepository.findById(walletId).orElseThrow { WalletNotFoundException("Wallet not found") }
        if (wallet.userId != userId) throw WalletNotOwnedException("That wallet does not belong to you")

        val fee = amount.multiply(BigDecimal("0.01")).setScale(0, RoundingMode.HALF_UP)
        if (wallet.availableBalance < amount.add(fee)) {
            throw InsufficientFundsException("Insufficient available balance for this transfer")
        }

        return quoteStore.create(userId, wallet.id, recipient, amount, fee, wallet.currency)
    }

    @Transactional
    fun confirmTransfer(quoteId: String, userId: String): Pair<Transaction, BigDecimal> {
        val quote = quoteStore.get(quoteId) ?: throw QuoteNotFoundException("Transfer quote not found")
        if (quote.userId != userId) throw WalletNotOwnedException("That quote does not belong to you")
        if (quote.status == QuoteStatus.CONFIRMED) throw QuoteAlreadyUsedException("Transfer quote was already confirmed")
        if (quote.status != QuoteStatus.PENDING) throw QuoteExpiredException("Transfer quote is ${quote.status.name.lowercase()}")
        if (Instant.now().isAfter(quote.expiresAt)) {
            quote.status = QuoteStatus.EXPIRED
            throw QuoteExpiredException("Transfer quote has expired, request a new quote")
        }

        // Real per-rail routing (2026-07-13) -- resolve() (used by bills/airtime,
        // which get a real provider name) always fell through to generic here since
        // quote.recipient is a phone number, not a provider name. See
        // RailCatalog.resolveByPhoneNumber's own doc comment for the real, sourced
        // (RURA numbering plan) prefix routing this now does instead.
        val rail = RailCatalog.resolveByPhoneNumber(quote.recipient)
        try {
            providerConnector.attempt(rail, "Transfer to ${quote.recipient}")
        } catch (e: ProviderDeclinedException) {
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
                    description = "Transfer to ${quote.recipient}",
                    amount = quote.amount,
                    currency = quote.currency,
                    reason = e.message ?: "declined",
                    failedAt = Instant.now(),
                ),
            )
            throw e
        }

        val result = ledgerService.postLedgerTransaction(
            quote.currency,
            listOf(
                LedgerLeg(quote.fromWalletId, LedgerAccountType.WALLET, LedgerDirection.DEBIT, quote.totalDebit, "Transfer to ${quote.recipient}"),
                LedgerLeg("rail_suspense", LedgerAccountType.RAIL_SUSPENSE, LedgerDirection.CREDIT, quote.amount, "Rail settlement for ${quote.recipient}"),
                LedgerLeg("fee_revenue", LedgerAccountType.FEE_REVENUE, LedgerDirection.CREDIT, quote.fee, "Transfer fee"),
            ),
        )
        quote.status = QuoteStatus.CONFIRMED

        val wallet = walletRepository.findById(quote.fromWalletId).orElseThrow { WalletNotFoundException("Wallet not found") }
        val transaction = Transaction(
            id = result.transactionId,
            referenceNumber = "TXN${System.currentTimeMillis()}${UUID.randomUUID().toString().take(4)}",
            senderId = wallet.userId,
            recipientId = "external",
            fromWalletId = quote.fromWalletId,
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
        fraudRuleEngine.evaluate(wallet.userId, null, quote.amount, transaction.id)
        transactionRepository.save(transaction)

        eventPublisher.publishAfterCommit(
            TOPIC_TRANSFER_CONFIRMED,
            transaction.id,
            TransferConfirmedEvent(
                transactionId = transaction.id,
                fromWalletId = quote.fromWalletId,
                recipient = quote.recipient,
                amount = quote.amount,
                fee = quote.fee,
                currency = quote.currency,
                confirmedAt = Instant.now(),
            ),
        )

        return transaction to wallet.balance
    }
}
