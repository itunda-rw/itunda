package rw.itunda.wallet

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
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
import rw.itunda.core.ledger.InsufficientFundsException
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.provider.ProviderConnector
import rw.itunda.core.provider.ProviderDeclinedException
import rw.itunda.core.provider.RailCatalog
import rw.itunda.core.repository.TransactionRepository
import rw.itunda.core.repository.WalletRepository
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
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
    private val ledgerService: LedgerService,
    private val eventPublisher: EventPublisher,
    private val providerConnector: ProviderConnector,
) {
    private val quoteStore = QuoteStore()

    fun getWallets(userId: String): List<Wallet> = walletRepository.findByUserId(userId)

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

        val rail = RailCatalog.resolve(quote.recipient)
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
