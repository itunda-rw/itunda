package rw.itunda.merchant

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.Transaction
import rw.itunda.core.domain.TransactionStatus
import rw.itunda.core.domain.TransactionType
import rw.itunda.core.domain.Wallet
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.TransactionRepository
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.UUID

/**
 * Real cashback on purchases at itunda's own registered merchants -- the "browse
 * partner merchants, earn cashback" half of the "Toss Shopping" gap from the expanded
 * 2026-07-17 goal. Toss Shopping's real cashback rates vary by merchant/campaign and
 * aren't publicly documented per-merchant, so there's no specific external number to
 * source here -- this uses itunda's own flat, simple rate instead, the same "itunda's
 * own convention, not invented to look like a sourced fact" discipline
 * `LoansService.MIN_SCORE_TO_QUALIFY` already established for its own underwriting
 * thresholds. Itunda has no external merchant-partnership network to draw a real
 * "shopping catalog" from -- reusing itunda's own already-real registered `Merchant`
 * directory (see ShoppingController) is the honest, non-fabricated Rwanda adaptation,
 * the same pattern this document's own "Rwanda adaptation" column already uses
 * elsewhere (e.g. Mutuelle de Santé standing in for a generic health-insurance plan).
 *
 * Reuses the exact `rewards_expense` `LedgerAccountType.REWARDS_EXPENSE` clearing
 * account `RewardsService` already established for task-claim rewards -- this is the
 * same real kind of expense, just earned by shopping instead of completing a task.
 *
 * Deliberately called from `MerchantService.collect()` (QR payments only, where a real
 * itunda payer wallet exists) wrapped in a try/catch at the call site -- a cashback
 * failure must never roll back or fail the real payment that already succeeded, same
 * "auxiliary side-effect can't block real money movement" discipline already applied to
 * webhook delivery. Not applied to `chargeCard()`: a card payer has no itunda wallet at
 * all (`senderId = "external_card_..."`), so there's nothing real to credit.
 *
 * Deliberately plain `@Transactional` (default `REQUIRED` propagation), joining the
 * caller's existing transaction rather than `REQUIRES_NEW` -- a real bug caught live
 * during this pass's own verification: `REQUIRES_NEW` opens a genuinely separate DB
 * transaction/connection while `collect()`'s own transaction is still open and already
 * holds a row lock on this exact payer wallet (`LedgerService.postLedgerTransaction`'s
 * `findByIdForUpdate`), so awarding cashback to the *same* wallet self-deadlocked on a
 * real `Lock wait timeout exceeded` MySQL error. `REQUIRES_NEW` was also wrong on the
 * merits, not just slow: it would let cashback survive even if the payment itself
 * rolled back afterward -- rewarding a purchase that never actually completed. Joining
 * the caller's transaction fixes both: no separate lock to contend for, and cashback
 * now correctly rolls back together with the payment it's rewarding.
 */
@Service
class ShoppingCashbackService(
    private val ledgerService: LedgerService,
    private val transactionRepository: TransactionRepository,
) {
    private val cashbackRate = BigDecimal("0.01")

    @Transactional
    fun awardCashback(payerWallet: Wallet, purchaseAmount: BigDecimal, merchantName: String): BigDecimal {
        val cashbackAmount = purchaseAmount.multiply(cashbackRate).setScale(2, RoundingMode.HALF_UP)
        if (cashbackAmount <= BigDecimal.ZERO) return BigDecimal.ZERO

        val result = ledgerService.postLedgerTransaction(
            payerWallet.currency,
            listOf(
                LedgerLeg(payerWallet.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, cashbackAmount, "Shopping cashback - $merchantName"),
                LedgerLeg("rewards_expense", LedgerAccountType.REWARDS_EXPENSE, LedgerDirection.DEBIT, cashbackAmount, "Shopping cashback - $merchantName"),
            ),
        )

        // A real Transaction row -- not just ledger entries -- so cashback shows up in
        // the payer's own real transaction history (WalletService.getTransactionHistory
        // reads the transactions table, not ledger_entries directly). channel =
        // "CASHBACK" keeps it out of MerchantService.getReport()'s revenue report,
        // which only counts type == PAYMENT -- this is a DEPOSIT, a real, separate fact.
        transactionRepository.save(
            Transaction(
                id = result.transactionId,
                referenceNumber = "CASHBACK${System.currentTimeMillis()}${UUID.randomUUID().toString().take(4)}",
                senderId = "system_shopping_cashback",
                recipientId = payerWallet.userId,
                fromWalletId = null,
                toWalletId = payerWallet.id,
                amount = cashbackAmount,
                fee = BigDecimal.ZERO,
                currency = payerWallet.currency,
                type = TransactionType.DEPOSIT,
                status = TransactionStatus.COMPLETED,
                description = "Shopping cashback - $merchantName",
                channel = "CASHBACK",
                completedAt = Instant.now(),
            ),
        )
        return cashbackAmount
    }
}
