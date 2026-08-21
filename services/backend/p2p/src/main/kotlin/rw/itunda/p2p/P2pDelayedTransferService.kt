package rw.itunda.p2p

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.Notification
import rw.itunda.core.domain.P2pDelayedTransfer
import rw.itunda.core.domain.P2pDelayedTransferStatus
import rw.itunda.core.domain.Transaction
import rw.itunda.core.domain.TransactionStatus
import rw.itunda.core.domain.TransactionType
import rw.itunda.core.domain.AccountType
import rw.itunda.core.ledger.InsufficientFundsException
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.P2pDelayedTransferRepository
import rw.itunda.core.repository.TransactionRepository
import rw.itunda.core.repository.UserRepository
import rw.itunda.core.repository.AccountRepository
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.util.UUID

class P2pDelayedTransferNotFoundException(message: String) : RuntimeException(message)
class P2pDelayedTransferNotCancellableException(message: String) : RuntimeException(message)

/**
 * Real Korean 지연이체서비스 (Delayed Transfer Service) -- see `P2pDelayedTransfer.kt`'s
 * own doc comment for the full sourced account. Kept as its own service, not folded
 * into `P2pService` (already this module's largest, most heavily-tuned class -- three
 * separate documented transaction-composition bugs were found and fixed live in
 * `sendDirect`'s own round-up integration alone): this is a genuinely separate,
 * self-contained money-movement path with its own hold/release/cancel lifecycle, the
 * same "coherent, separable real feature gets its own service" discipline
 * `RideTrustedContactService`'s own doc comment already establishes for the identical
 * reason.
 *
 * Deliberately does NOT reuse `P2pService.sendDirect`'s fraud-engine/round-up/auto-
 * top-up/family-spend-limit machinery -- each of those was tuned specifically for an
 * INSTANT transfer's own transaction boundary (see `sendDirect`'s own three-attempt
 * round-up bug-fix history), and this is a genuinely different shape: money moves
 * into a REAL clearing account now, and to the recipient later, on the scheduler's own
 * transaction. Grafting that machinery on here without the same live-verification this
 * codebase's own standing discipline requires would risk silently reintroducing the
 * exact class of bug `sendDirect` already paid to fix. A real, named, deliberately
 * scoped-down follow-up if this v1 proves out.
 *
 * The one deliberate exception (Section 186): [P2pTransferLimitService]'s real
 * per-transfer/daily-cumulative "이체한도" cap IS shared with `sendDirect`, on purpose
 * -- see that class's own doc comment for why a real safety CAP (unlike the auxiliary
 * conveniences above) must be enforced against the identical real daily total
 * regardless of which push-transfer path the sender used, or the delayed path would
 * be a trivial way around it.
 */
@Service
class P2pDelayedTransferService(
    private val p2pDelayedTransferRepository: P2pDelayedTransferRepository,
    private val accountRepository: AccountRepository,
    private val userRepository: UserRepository,
    private val transactionRepository: TransactionRepository,
    private val ledgerService: LedgerService,
    private val rateLimiter: RateLimiter,
    private val notificationRepository: NotificationRepository,
    private val pushNotificationService: PushNotificationService,
    private val p2pTransferLimitService: P2pTransferLimitService,
) {
    private val log = LoggerFactory.getLogger(P2pDelayedTransferService::class.java)

    /**
     * Real "Send safely" -- the explicit, opt-in delayed alternative to
     * [P2pService.sendDirect]. Resolves the recipient the exact same way
     * [P2pService.resolveRecipientAccount] does (a phone number, falling back to an
     * account number), but instead of crediting them immediately, the sender's real
     * money is held in `p2p_delay_holding` until [P2pDelayedTransferReleaseScheduler]
     * releases it after [P2pDelayedTransfer.DELAY_WINDOW], or the sender cancels first.
     */
    @Transactional
    fun sendDelayed(senderUserId: String, recipientIdentifier: String, amount: BigDecimal, description: String): P2pDelayedTransfer {
        if (amount <= BigDecimal.ZERO) throw P2pInvalidAmountException("Amount must be greater than zero")
        val trimmedIdentifier = recipientIdentifier.trim()
        if (trimmedIdentifier.isEmpty()) throw P2pRecipientNotFoundException("Recipient is required")

        // Real anti-spam limit, same 30/hour convention P2pService.sendDirect already
        // established for a real mutating money-movement endpoint.
        rateLimiter.checkLimit("p2p:send-delayed:$senderUserId", limit = 30, window = Duration.ofHours(1))

        val senderAccount = accountRepository.findByUserIdAndType(senderUserId, AccountType.MAIN)
            ?: throw P2pNoAccountException("No account found for this account")

        val recipientAccount = (
            userRepository.findByPhoneNumber(trimmedIdentifier)?.let { accountRepository.findByUserIdAndType(it.id, AccountType.MAIN) }
                ?: accountRepository.findByAccountNumber(trimmedIdentifier)
            ) ?: throw P2pRecipientNotFoundException("No itunda account found for this phone number or account number")

        if (recipientAccount.userId == senderUserId) {
            throw P2pSelfPaymentException("You can't send money to yourself -- check the recipient and try again")
        }
        if (senderAccount.availableBalance < amount) {
            throw InsufficientFundsException("Insufficient available balance for this transfer")
        }
        // Real Korean "이체한도" (transfer limit) enforcement (Section 186) -- see
        // P2pTransferLimitService's own doc comment. Shared, real cap against the same
        // real daily total sendDirect enforces -- this delayed path removes real money
        // from the sender's control immediately too, so it must count against the
        // identical real number.
        p2pTransferLimitService.enforce(senderUserId, senderAccount.id, amount)

        val trimmedDescription = description.trim().ifEmpty { "Transfer" }
        val result = ledgerService.postLedgerTransaction(
            senderAccount.currency,
            listOf(
                LedgerLeg(senderAccount.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, amount, "Delayed transfer - $trimmedDescription"),
                LedgerLeg("p2p_delay_holding", LedgerAccountType.P2P_DELAY_HOLDING, LedgerDirection.CREDIT, amount, "Delayed transfer held - $trimmedDescription"),
            ),
        )

        transactionRepository.save(
            Transaction(
                id = result.transactionId,
                referenceNumber = "P2PDLY${System.currentTimeMillis()}${UUID.randomUUID().toString().take(4)}",
                senderId = senderUserId,
                recipientId = recipientAccount.userId,
                fromAccountId = senderAccount.id,
                toAccountId = recipientAccount.id,
                amount = amount,
                fee = BigDecimal.ZERO,
                currency = senderAccount.currency,
                type = TransactionType.TRANSFER,
                status = TransactionStatus.COMPLETED,
                description = "Delayed transfer - $trimmedDescription",
                completedAt = Instant.now(),
            ),
        )

        val transfer = p2pDelayedTransferRepository.save(
            P2pDelayedTransfer(
                id = "p2p_delayed_${UUID.randomUUID()}",
                senderUserId = senderUserId,
                senderAccountId = senderAccount.id,
                recipientUserId = recipientAccount.userId,
                recipientAccountId = recipientAccount.id,
                amount = amount,
                description = trimmedDescription,
                holdTransactionId = result.transactionId,
                releaseAt = Instant.now().plus(P2pDelayedTransfer.DELAY_WINDOW),
            ),
        )
        log.info("Delayed transfer {} held {} RWF from {} to {}, releasing at {}", transfer.id, amount, senderUserId, recipientAccount.userId, transfer.releaseAt)
        return transfer
    }

    fun getMyDelayedTransfers(senderUserId: String): List<P2pDelayedTransfer> =
        p2pDelayedTransferRepository.findBySenderUserIdOrderByCreatedAtDesc(senderUserId)

    /**
     * Real sender-initiated cancel -- refunds the real held amount back to the sender's
     * own account. IDOR-safe: [P2pDelayedTransferRepository.findByIdAndSenderUserId]
     * compares the resource's real owning field against the caller, never just
     * existence, same discipline this codebase's own IDOR-audit precedent establishes.
     */
    @Transactional
    fun cancel(senderUserId: String, transferId: String): P2pDelayedTransfer {
        val transfer = p2pDelayedTransferRepository.findByIdAndSenderUserId(transferId, senderUserId)
            ?: throw P2pDelayedTransferNotFoundException("Delayed transfer not found")
        if (transfer.status != P2pDelayedTransferStatus.PENDING) {
            throw P2pDelayedTransferNotCancellableException("This transfer has already been ${transfer.status.name.lowercase()} -- it can no longer be cancelled")
        }
        val senderAccount = accountRepository.findByUserIdAndType(senderUserId, AccountType.MAIN)
            ?: throw P2pNoAccountException("No account found for this account")

        val result = ledgerService.postLedgerTransaction(
            senderAccount.currency,
            listOf(
                LedgerLeg("p2p_delay_holding", LedgerAccountType.P2P_DELAY_HOLDING, LedgerDirection.DEBIT, transfer.amount, "Delayed transfer cancelled"),
                LedgerLeg(senderAccount.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, transfer.amount, "Delayed transfer refund"),
            ),
        )
        transfer.status = P2pDelayedTransferStatus.CANCELLED
        transfer.resolutionTransactionId = result.transactionId
        transfer.updatedAt = Instant.now()
        val saved = p2pDelayedTransferRepository.save(transfer)
        log.info("Delayed transfer {} cancelled by sender, {} RWF refunded", transfer.id, transfer.amount)
        return saved
    }

    // Real read-only scheduler feed -- see P2pDelayedTransferReleaseScheduler's own doc
    // comment. Coarse repo filter plus the exact real due-window check, same shape
    // MarketplaceService.getEscrowsDueForAutoRelease already establishes.
    fun getDueForRelease(): List<P2pDelayedTransfer> =
        p2pDelayedTransferRepository.findByStatus(P2pDelayedTransferStatus.PENDING)
            .filter { !it.releaseAt.isAfter(Instant.now()) }

    /**
     * Real per-item release, called only from [P2pDelayedTransferReleaseScheduler]'s own
     * try/catch-per-row loop -- never a batch-transactional loop over every due row
     * (the exact, previously-recurring "scheduler transaction-poisoning" bug class this
     * codebase's own Sections 115-181 already found and fixed nine times). Re-checks
     * `status == PENDING` on a fresh read before acting -- the same re-check-before-act
     * guard `MarketplaceService.autoReleaseEscrow` already establishes -- so a transfer
     * the sender cancelled milliseconds earlier is silently skipped, not double-processed.
     */
    @Transactional
    fun release(transferId: String) {
        val transfer = p2pDelayedTransferRepository.findById(transferId).orElse(null) ?: return
        if (transfer.status != P2pDelayedTransferStatus.PENDING) return
        val recipientAccount = accountRepository.findById(transfer.recipientAccountId).orElse(null) ?: return

        val result = ledgerService.postLedgerTransaction(
            recipientAccount.currency,
            listOf(
                LedgerLeg("p2p_delay_holding", LedgerAccountType.P2P_DELAY_HOLDING, LedgerDirection.DEBIT, transfer.amount, "Delayed transfer released"),
                LedgerLeg(recipientAccount.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, transfer.amount, "Delayed transfer received"),
            ),
        )
        transfer.status = P2pDelayedTransferStatus.COMPLETED
        transfer.resolutionTransactionId = result.transactionId
        transfer.updatedAt = Instant.now()
        p2pDelayedTransferRepository.save(transfer)
        notifyMoneyReceived(transfer)
    }

    // Same real-time "money received" notification shape P2pService.notifyMoneyReceived
    // already establishes -- best-effort, never rolls back real money that already moved.
    private fun notifyMoneyReceived(transfer: P2pDelayedTransfer) {
        try {
            val sender = userRepository.findById(transfer.senderUserId).orElse(null)
            val senderName = sender?.let { "${it.firstName} ${it.lastName}" } ?: "Someone"
            val title = "Money received"
            val body = "$senderName sent you ${transfer.amount} RWF."
            notificationRepository.save(
                Notification(
                    id = "notif_${UUID.randomUUID()}",
                    userId = transfer.recipientUserId,
                    type = "MONEY_RECEIVED",
                    title = title,
                    body = body,
                    isRead = false,
                    createdAt = Instant.now(),
                    dataJson = null,
                ),
            )
            pushNotificationService.sendToUser(transfer.recipientUserId, title, body, type = "MONEY_RECEIVED")
        } catch (e: Exception) {
            log.warn("Notification skipped for released delayed transfer {}: {}", transfer.id, e.message)
        }
    }
}
