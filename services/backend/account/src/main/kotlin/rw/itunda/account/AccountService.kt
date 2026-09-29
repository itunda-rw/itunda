package rw.itunda.account

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
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
import rw.itunda.core.repository.TransactionRepository
import rw.itunda.core.repository.AccountRepository
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant
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
    private val ledgerService: LedgerService,
    private val eventPublisher: EventPublisher,
    private val providerConnector: ProviderConnector,
    private val fraudRuleEngine: FraudRuleEngine,
    private val rateLimiter: RateLimiter,
) {
    private val quoteStore = QuoteStore()

    // Real Toss Timeline-style unusual-spend heuristic constants -- see
    // getTransactionTimeline's own doc comment for the full account.
    private val UNUSUAL_SPEND_MULTIPLIER = BigDecimal("2.5")
    private val MIN_PRIOR_DEBITS_FOR_BASELINE = 3

    fun getAccounts(userId: String): List<Account> = accountRepository.findByUserId(userId)

    // Real transaction history (2026-07-12) -- TransactionRepository's
    // findBySenderIdOrRecipientIdOrderByCreatedAtDesc already existed with no
    // controller endpoint ever calling it; this is what backs the new card/
    // transaction-history screen on both platforms.
    fun getTransactionHistory(userId: String): List<Transaction> =
        transactionRepository.findBySenderIdOrRecipientIdOrderByCreatedAtDesc(userId, userId)

    // Real Toss Bank/Toss Pay separation follow-up (2026-08-21, user-provided real
    // screenshots of both the Toss Bank AND the real "Toss Pay Money" detail screens):
    // each shows its own separate transaction ledger, not the one shared user-wide
    // list getTransactionHistory above returns. getAccountById re-verifies real
    // ownership first (same IDOR-safety discipline this class already applies
    // everywhere else a client-supplied accountId is trusted), so an accountId
    // belonging to a different user real-404s rather than leaking their history.
    fun getAccountTransactionHistory(userId: String, accountId: String): List<Transaction> {
        val account = getAccountById(accountId, userId)
        return transactionRepository.findByFromAccountIdOrToAccountIdOrderByCreatedAtDesc(account.id, account.id)
    }

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

    // Real Toss Bank reference (2026-09-12, 12 real "관리"/Manage-screen
    // screenshots showing "계좌 별명" -- account nickname) -- a real, user-editable
    // personal label. Blank clears it back to unset (falls back to the account's
    // own real accountName display elsewhere), matching this codebase's own
    // "blank means no value" convention (see PartnerService.parsePartnerMiniAppCategory
    // for the identical shape). Same 404-not-403 ownership check as getAccountById
    // above, and the same VARCHAR-length-bound convention the 2026-09-05 sweep
    // applied everywhere else (nickname is VARCHAR(50)).
    fun setNickname(userId: String, accountId: String, nickname: String?): Account {
        val account = getAccountById(accountId, userId)
        val trimmed = nickname?.trim()?.ifBlank { null }
        if (trimmed != null && trimmed.length > 50) {
            throw IllegalArgumentException("Account nickname must be 50 characters or fewer")
        }
        account.nickname = trimmed
        return accountRepository.save(account)
    }

    // Real Toss "충전하기"/"옮기기" reference (2026-09-12, direct user-supplied Toss
    // Pay screenshots) -- a real, honest gap the earlier Pay Money detail screen
    // itself disclosed: "itunda has no self-service 'pull an amount from my linked
    // account right now' flow." Generalizes AutoTopUpService.topUpPayFromMain's own
    // real shape (senderId == recipientId == userId, both legs WALLET, zero fee,
    // TransactionType.TRANSFER) rather than reusing quoteTransfer/confirmTransfer
    // above -- those route through an EXTERNAL provider rail and charge a real 1%
    // fee, wrong for a purely internal move between two of the SAME user's own real
    // accounts. No fraud-rule evaluation, matching YouthAccountService.deposit's own
    // established precedent for MAIN -> Mini funding: fraud rules exist to catch
    // suspicious transfers TO OTHER PEOPLE, not moving your own money between your
    // own pockets.
    @Transactional
    fun transferBetweenOwnAccounts(userId: String, fromAccountId: String, toAccountId: String, amount: BigDecimal): Transaction {
        if (amount <= BigDecimal.ZERO) throw IllegalArgumentException("Amount must be greater than zero")
        if (fromAccountId == toAccountId) throw IllegalArgumentException("Choose two different accounts")
        rateLimiter.checkLimit("account:internal-transfer:$userId", limit = 30, window = Duration.ofHours(1))

        val fromAccount = getAccountById(fromAccountId, userId)
        val toAccount = getAccountById(toAccountId, userId)

        val result = ledgerService.postLedgerTransaction(
            fromAccount.currency,
            listOf(
                LedgerLeg(fromAccount.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, amount, "Transfer between your own accounts"),
                LedgerLeg(toAccount.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, amount, "Transfer between your own accounts"),
            ),
        )
        return transactionRepository.save(
            Transaction(
                id = result.transactionId,
                referenceNumber = "SELFXFER${System.currentTimeMillis()}${UUID.randomUUID().toString().take(4)}",
                senderId = userId, recipientId = userId, fromAccountId = fromAccount.id, toAccountId = toAccount.id,
                amount = amount, fee = BigDecimal.ZERO, currency = fromAccount.currency, type = TransactionType.TRANSFER,
                status = TransactionStatus.COMPLETED, description = "Transfer between your own accounts", channel = "INTERNAL_TRANSFER",
                completedAt = Instant.now(),
            ),
        )
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
        // Real gap found live (2026-09-04, same pass that found StocksService's
        // identical gap): this external-transfer confirm moves real money via
        // postLedgerTransaction below, same as P2pService.pay/send, but had no
        // rateLimiter.checkLimit at all. Same 30/hour baseline those already use.
        rateLimiter.checkLimit("account:confirm-transfer:$userId", limit = 30, window = Duration.ofHours(1))
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
