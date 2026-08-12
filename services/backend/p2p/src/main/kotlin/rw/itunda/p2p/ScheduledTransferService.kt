package rw.itunda.p2p

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.ScheduledTransfer
import rw.itunda.core.domain.ScheduledTransferStatus
import rw.itunda.core.domain.WalletType
import rw.itunda.core.ledger.InsufficientFundsException
import rw.itunda.core.repository.ScheduledTransferRepository
import rw.itunda.core.repository.UserRepository
import rw.itunda.core.repository.WalletRepository
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

class ScheduledTransferNotFoundException(message: String) : RuntimeException(message)
class ScheduledTransferInvalidDateException(message: String) : RuntimeException(message)
class ScheduledTransferNotPendingException(message: String) : RuntimeException(message)

/**
 * Real Toss 예약송금 (scheduled/reserved one-time transfer) equivalent -- see
 * ScheduledTransfer.kt's own doc comment. Execution reuses `P2pService.sendDirect`
 * unmodified, same real-money-movement-reuse discipline `AutoTransferService` already
 * established for a different real recurring flow.
 */
@Service
class ScheduledTransferService(
    private val scheduledTransferRepository: ScheduledTransferRepository,
    private val walletRepository: WalletRepository,
    private val userRepository: UserRepository,
    private val p2pService: P2pService,
    private val rateLimiter: RateLimiter,
) {
    private val log = LoggerFactory.getLogger(ScheduledTransferService::class.java)

    fun create(userId: String, recipientIdentifier: String, amount: BigDecimal, scheduledDate: LocalDate, description: String): ScheduledTransfer {
        if (amount <= BigDecimal.ZERO) throw P2pInvalidAmountException("Amount must be greater than zero")
        val trimmedIdentifier = recipientIdentifier.trim()
        if (trimmedIdentifier.isEmpty()) throw P2pRecipientNotFoundException("Recipient is required")

        // Real "1회" (one-time) future-date-only constraint -- Toss's own scheduled
        // transfer always runs starting the next real 9am on the scheduled date, so
        // today's date (already past that cutoff for a same-day schedule) isn't a real
        // choice; only a genuine future date is.
        val today = LocalDate.now(ZoneOffset.UTC)
        if (!scheduledDate.isAfter(today)) throw ScheduledTransferInvalidDateException("Scheduled date must be a future date")

        rateLimiter.checkLimit("scheduledtransfer:create:$userId", limit = 20, window = Duration.ofHours(1))

        val senderWallet = walletRepository.findByUserIdAndType(userId, WalletType.MAIN)
            ?: throw P2pNoWalletException("No wallet found for this account")

        // Same real phone-number-then-account-number resolution as P2pService.sendDirect
        // -- only used here to validate the recipient exists and cache a real display
        // name, never a walletId, same reasoning AutoTransfer.kt's own doc comment gives.
        val recipientUser = userRepository.findByPhoneNumber(trimmedIdentifier)
        val recipientWallet = (
            if (recipientUser != null) walletRepository.findByUserIdAndType(recipientUser.id, WalletType.MAIN) else null
            ) ?: walletRepository.findByAccountNumber(trimmedIdentifier)
            ?: throw P2pRecipientNotFoundException("No itunda account found for this phone number or account number")
        if (recipientWallet.userId == userId) {
            throw P2pSelfPaymentException("Scheduled transfers need a different recipient -- you can't send to yourself")
        }
        val recipientDisplayName = userRepository.findById(recipientWallet.userId).map { "${it.firstName} ${it.lastName}" }.orElse(trimmedIdentifier)

        val scheduledTransfer = ScheduledTransfer(
            id = "scheduledtransfer_${UUID.randomUUID()}",
            userId = userId,
            walletId = senderWallet.id,
            recipientIdentifier = trimmedIdentifier,
            recipientName = recipientDisplayName,
            amount = amount,
            description = description.trim().ifEmpty { "Scheduled transfer" },
            scheduledDate = scheduledDate,
        )
        return scheduledTransferRepository.save(scheduledTransfer)
    }

    fun getMine(userId: String): List<ScheduledTransfer> = scheduledTransferRepository.findByUserIdOrderByCreatedAtDesc(userId)

    fun cancel(userId: String, id: String): ScheduledTransfer {
        val scheduledTransfer = scheduledTransferRepository.findByIdAndUserId(id, userId)
            ?: throw ScheduledTransferNotFoundException("Scheduled transfer not found")
        if (scheduledTransfer.status != ScheduledTransferStatus.PENDING) {
            throw ScheduledTransferNotPendingException("Only a pending scheduled transfer can be cancelled")
        }
        scheduledTransfer.status = ScheduledTransferStatus.CANCELLED
        scheduledTransfer.cancelledAt = Instant.now()
        return scheduledTransferRepository.save(scheduledTransfer)
    }

    fun getDueForExecution(): List<ScheduledTransfer> =
        scheduledTransferRepository.findByStatusAndScheduledDateLessThanEqual(ScheduledTransferStatus.PENDING, LocalDate.now(ZoneOffset.UTC))

    // Real one-time execution, reusing P2pService.sendDirect's exact real ledger
    // movement -- returns false (never throws) on a genuine, honest failure so the
    // scheduler's per-item loop is never blocked by one bad scheduled transfer, same
    // discipline AutoTransferService.executeOne already established. Unlike a
    // recurring auto-transfer, a failed one-time transfer has no "next cycle" to retry
    // on -- it's marked FAILED once and stays there, an honest terminal state rather
    // than silently vanishing or looping forever.
    @Transactional
    fun executeOne(scheduledTransfer: ScheduledTransfer): Boolean {
        val succeeded = try {
            val (transaction, _) = p2pService.sendDirect(scheduledTransfer.userId, scheduledTransfer.recipientIdentifier, scheduledTransfer.amount, scheduledTransfer.description)
            scheduledTransfer.status = ScheduledTransferStatus.EXECUTED
            scheduledTransfer.executedAt = Instant.now()
            scheduledTransfer.transactionId = transaction.id
            true
        } catch (e: InsufficientFundsException) {
            scheduledTransfer.status = ScheduledTransferStatus.FAILED
            scheduledTransfer.failureReason = "Insufficient balance"
            false
        } catch (e: P2pRecipientNotFoundException) {
            scheduledTransfer.status = ScheduledTransferStatus.FAILED
            scheduledTransfer.failureReason = "Recipient account no longer available"
            false
        } catch (e: Exception) {
            log.error("Scheduled transfer {} failed with an unexpected error", scheduledTransfer.id, e)
            scheduledTransfer.status = ScheduledTransferStatus.FAILED
            scheduledTransfer.failureReason = "Couldn't complete this transfer"
            false
        }
        scheduledTransferRepository.save(scheduledTransfer)
        return succeeded
    }
}
