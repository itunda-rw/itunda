package rw.itunda.core.support

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.SupportTicket
import rw.itunda.core.domain.SupportTicketCategory
import rw.itunda.core.domain.SupportTicketResolution
import rw.itunda.core.domain.SupportTicketStatus
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.LedgerEntryRepository
import rw.itunda.core.repository.SupportTicketRepository
import rw.itunda.core.repository.TransactionRepository
import rw.itunda.core.repository.WalletRepository
import java.time.Instant
import java.util.UUID

class SupportTransactionNotFoundException(message: String) : RuntimeException(message)
class SupportTransactionNotOwnedException(message: String) : RuntimeException(message)
class SupportTicketNotFoundException(message: String) : RuntimeException(message)
class SupportTicketAlreadyResolvedException(message: String) : RuntimeException(message)

/**
 * Real customer support ticket workflow -- see [rw.itunda.core.domain.SupportTicket]'s
 * own doc comment for why this exists (closing a false "real" claim in
 * docs/TOSS_PARITY_MATRIX.md's Non-Negotiable Gates section). Lives in :core, not the
 * new :support module, so both the user-facing ticket-creation controller and the
 * ADMIN-gated review queue in :system can share one implementation -- same split as
 * [rw.itunda.core.fraud.FraudRuleEngine] (:core) + FraudController (:system).
 */
@Service
class SupportService(
    private val supportTicketRepository: SupportTicketRepository,
    private val transactionRepository: TransactionRepository,
    private val walletRepository: WalletRepository,
    private val ledgerEntryRepository: LedgerEntryRepository,
    private val ledgerService: LedgerService,
) {
    companion object {
        // Real itunda-defined SLA (not a sourced Toss number -- see SupportTicket's own
        // doc comment). ACCOUNT_TAKEOVER is tightest given a wallet is frozen and the
        // user is locked out of moving money on it until reviewed.
        val SLA_HOURS = mapOf(
            SupportTicketCategory.ACCOUNT_TAKEOVER to 4L,
            SupportTicketCategory.PAYMENT_DISPUTE to 48L,
            SupportTicketCategory.GENERAL to 72L,
        )
    }

    @Transactional
    fun createTicket(userId: String, transactionId: String, category: SupportTicketCategory, description: String): SupportTicket {
        val transaction = transactionRepository.findById(transactionId)
            .orElseThrow { SupportTransactionNotFoundException("Transaction not found") }
        if (transaction.senderId != userId && transaction.recipientId != userId) {
            throw SupportTransactionNotOwnedException("That transaction does not belong to you")
        }

        var frozeWalletId: String? = null
        if (category == SupportTicketCategory.ACCOUNT_TAKEOVER) {
            // Real account-takeover-specific flow: freeze whichever side of this
            // transaction's real wallets belongs to the reporting user, so a
            // suspected-compromised account can't move any more money out while
            // under review. Enforced for real in LedgerService.postLedgerTransaction,
            // not just a cosmetic flag -- see that class's own comment.
            val candidateWalletId = when (userId) {
                transaction.senderId -> transaction.fromWalletId
                else -> transaction.toWalletId
            }
            val wallet = candidateWalletId?.let { walletRepository.findById(it).orElse(null) }
            if (wallet != null && wallet.userId == userId && wallet.isActive) {
                wallet.isActive = false
                walletRepository.save(wallet)
                frozeWalletId = wallet.id
            }
        }

        val ticket = SupportTicket(
            id = "ticket_${UUID.randomUUID()}",
            userId = userId,
            transactionId = transactionId,
            category = category,
            description = description,
            frozeWalletId = frozeWalletId,
            dueBy = Instant.now().plusSeconds(SLA_HOURS.getValue(category) * 3600),
        )
        return supportTicketRepository.save(ticket)
    }

    fun getMyTickets(userId: String): List<SupportTicket> = supportTicketRepository.findByUserIdOrderByCreatedAtDesc(userId)

    // Overdue-first: an open ticket past its own dueBy is the one that needs a
    // reviewer's attention right now, ahead of a ticket that still has time left --
    // the real "escalation" half of the SLA/escalation policy this closes.
    fun getQueue(): List<SupportTicket> {
        val now = Instant.now()
        return supportTicketRepository.findByStatusOrderByDueByAsc(SupportTicketStatus.OPEN)
            .sortedWith(compareByDescending<SupportTicket> { it.dueBy.isBefore(now) }.thenBy { it.dueBy })
    }

    @Transactional
    fun resolve(ticketId: String, reviewerId: String, resolution: SupportTicketResolution, notes: String?): SupportTicket {
        val ticket = supportTicketRepository.findById(ticketId)
            .orElseThrow { SupportTicketNotFoundException("Support ticket not found") }
        if (ticket.status == SupportTicketStatus.RESOLVED) {
            throw SupportTicketAlreadyResolvedException("This ticket has already been resolved")
        }

        if (resolution == SupportTicketResolution.REFUNDED) {
            ticket.refundTransactionId = reverseTransaction(ticket.transactionId)
        }

        // Unfreeze regardless of decision -- a ticket reaching a decision is always the
        // end of the review, whether it confirmed the takeover (REFUNDED) or found the
        // activity legitimate (REJECTED); either way the account shouldn't stay frozen
        // once a human has looked at it.
        ticket.frozeWalletId?.let { walletId ->
            walletRepository.findById(walletId).orElse(null)?.let { wallet ->
                wallet.isActive = true
                walletRepository.save(wallet)
            }
        }

        ticket.status = SupportTicketStatus.RESOLVED
        ticket.resolution = resolution
        ticket.resolutionNotes = notes
        ticket.reviewedBy = reviewerId
        ticket.resolvedAt = Instant.now()
        return supportTicketRepository.save(ticket)
    }

    /**
     * Real double-entry reversal: reads the original transaction's own ledger legs and
     * posts a new transaction with every leg's direction flipped (same accounts, same
     * amounts, including the fee) -- a reversing entry, never mutating or deleting the
     * original, matching this backend's append-only ledger discipline everywhere else.
     */
    private fun reverseTransaction(transactionId: String): String {
        val originalEntries = ledgerEntryRepository.findByTransactionId(transactionId)
        if (originalEntries.isEmpty()) {
            throw SupportTransactionNotFoundException("No ledger entries found for transaction $transactionId")
        }
        val currency = originalEntries.first().currency
        val reversedLegs = originalEntries.map { entry ->
            val flipped = if (entry.direction == LedgerDirection.DEBIT) LedgerDirection.CREDIT else LedgerDirection.DEBIT
            LedgerLeg(entry.accountId, entry.accountType, flipped, entry.amount, "Refund for ${entry.memo}")
        }
        return ledgerService.postLedgerTransaction(currency, reversedLegs).transactionId
    }
}
