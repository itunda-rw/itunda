package rw.itunda.p2p

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.AutoTransfer
import rw.itunda.core.domain.AutoTransferFrequency
import rw.itunda.core.domain.AutoTransferStatus
import rw.itunda.core.domain.Notification
import rw.itunda.core.domain.AccountType
import rw.itunda.core.ledger.InsufficientFundsException
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.AutoTransferRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.UserRepository
import rw.itunda.core.repository.AccountRepository
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.UUID

class AutoTransferNotFoundException(message: String) : RuntimeException(message)
class AutoTransferInvalidScheduleException(message: String) : RuntimeException(message)

/**
 * Real Toss Bank 자동이체 (auto-transfer) equivalent -- see AutoTransfer.kt's own doc
 * comment. Execution reuses `P2pService.sendDirect` unmodified: a scheduled auto-
 * transfer is not a different kind of money movement, just a different trigger for the
 * exact same real ACCOUNT-to-ACCOUNT ledger pair every direct transfer already uses.
 */
@Service
class AutoTransferService(
    private val autoTransferRepository: AutoTransferRepository,
    private val accountRepository: AccountRepository,
    private val userRepository: UserRepository,
    private val p2pService: P2pService,
    private val rateLimiter: RateLimiter,
    private val notificationRepository: NotificationRepository,
    private val pushNotificationService: PushNotificationService,
) {
    private val log = LoggerFactory.getLogger(AutoTransferService::class.java)

    fun create(
        userId: String,
        recipientIdentifier: String,
        amount: BigDecimal,
        frequency: AutoTransferFrequency,
        dayOfWeek: Int?,
        dayOfMonth: Int?,
        description: String,
    ): AutoTransfer {
        if (amount <= BigDecimal.ZERO) throw P2pInvalidAmountException("Amount must be greater than zero")
        val trimmedIdentifier = recipientIdentifier.trim()
        if (trimmedIdentifier.isEmpty()) throw P2pRecipientNotFoundException("Recipient is required")

        // Real anti-spam limit, same 20/hour convention generateRequest already
        // established for a real recurring-money-movement-creating endpoint.
        rateLimiter.checkLimit("autotransfer:create:$userId", limit = 20, window = Duration.ofHours(1))

        val senderAccount = accountRepository.findByUserIdAndType(userId, AccountType.MAIN)
            ?: throw P2pNoAccountException("No account found for this account")

        // Same real phone-number-then-account-number resolution as P2pService.sendDirect
        // -- only used here to validate the recipient exists and to cache a real display
        // name, never to cache a accountId (see AutoTransfer's own doc comment on why).
        val recipientUser = userRepository.findByPhoneNumber(trimmedIdentifier)
        val recipientAccount = (
            if (recipientUser != null) accountRepository.findByUserIdAndType(recipientUser.id, AccountType.MAIN) else null
            ) ?: accountRepository.findByAccountNumber(trimmedIdentifier)
            ?: throw P2pRecipientNotFoundException("No itunda account found for this phone number or account number")
        if (recipientAccount.userId == userId) {
            throw P2pSelfPaymentException("Auto-transfers need a different recipient -- you can't send to yourself")
        }
        val recipientDisplayName = userRepository.findById(recipientAccount.userId).map { "${it.firstName} ${it.lastName}" }.orElse(trimmedIdentifier)

        val nextExecutionAt = when (frequency) {
            AutoTransferFrequency.WEEKLY -> {
                if (dayOfWeek == null || dayOfWeek !in 1..7) throw AutoTransferInvalidScheduleException("dayOfWeek must be 1-7 (Monday-Sunday)")
                nextWeeklyOccurrence(dayOfWeek)
            }
            AutoTransferFrequency.MONTHLY -> {
                if (dayOfMonth == null || dayOfMonth !in 1..28) throw AutoTransferInvalidScheduleException("dayOfMonth must be 1-28")
                nextMonthlyOccurrence(dayOfMonth)
            }
        }

        val autoTransfer = AutoTransfer(
            id = "autotransfer_${UUID.randomUUID()}",
            userId = userId,
            accountId = senderAccount.id,
            recipientIdentifier = trimmedIdentifier,
            recipientName = recipientDisplayName,
            amount = amount,
            frequency = frequency,
            dayOfWeek = if (frequency == AutoTransferFrequency.WEEKLY) dayOfWeek else null,
            dayOfMonth = if (frequency == AutoTransferFrequency.MONTHLY) dayOfMonth else null,
            description = description.trim().ifEmpty { "Auto-transfer" },
            nextExecutionAt = nextExecutionAt,
        )
        return autoTransferRepository.save(autoTransfer)
    }

    fun getMine(userId: String): List<AutoTransfer> = autoTransferRepository.findByUserIdOrderByCreatedAtDesc(userId)

    fun pause(userId: String, id: String): AutoTransfer {
        val autoTransfer = autoTransferRepository.findByIdAndUserId(id, userId) ?: throw AutoTransferNotFoundException("Auto-transfer not found")
        autoTransfer.status = AutoTransferStatus.PAUSED
        return autoTransferRepository.save(autoTransfer)
    }

    fun resume(userId: String, id: String): AutoTransfer {
        val autoTransfer = autoTransferRepository.findByIdAndUserId(id, userId) ?: throw AutoTransferNotFoundException("Auto-transfer not found")
        autoTransfer.status = AutoTransferStatus.ACTIVE
        // Real re-arm, not an instant catch-up execution: resuming today re-anchors to
        // the next real occurrence of the plan's own locked day, same as the schedule
        // was computed at creation -- never fires immediately just because it was paused.
        autoTransfer.nextExecutionAt = when (autoTransfer.frequency) {
            AutoTransferFrequency.WEEKLY -> nextWeeklyOccurrence(autoTransfer.dayOfWeek!!)
            AutoTransferFrequency.MONTHLY -> nextMonthlyOccurrence(autoTransfer.dayOfMonth!!)
        }
        return autoTransferRepository.save(autoTransfer)
    }

    fun cancel(userId: String, id: String): AutoTransfer {
        val autoTransfer = autoTransferRepository.findByIdAndUserId(id, userId) ?: throw AutoTransferNotFoundException("Auto-transfer not found")
        autoTransfer.status = AutoTransferStatus.CANCELLED
        autoTransfer.cancelledAt = Instant.now()
        return autoTransferRepository.save(autoTransfer)
    }

    fun getDueForExecution(): List<AutoTransfer> =
        autoTransferRepository.findByStatusAndNextExecutionAtLessThanEqual(AutoTransferStatus.ACTIVE, Instant.now())

    // Real execution, reusing P2pService.sendDirect's exact real ledger movement --
    // returns false (never throws) on a genuine, honest failure (insufficient funds,
    // recipient account closed) so the scheduler's per-item loop is never blocked by
    // one bad auto-transfer, same discipline WeeklySavingsService.processDueInstallment
    // already established. A failed occurrence is skipped, not retried same-cycle: the
    // schedule still advances to the next real occurrence, same as a missed weekly
    // savings installment.
    //
    // Deliberately NOT @Transactional itself (2026-08-17 fix) -- p2pService.sendDirect
    // below is a separately-proxied bean, already fully @Transactional on its own. If
    // this method were also @Transactional, a real InsufficientFundsException/
    // P2pRecipientNotFoundException thrown from sendDirect would mark THIS method's own
    // ambient transaction rollback-only at the moment it throws -- catching it in the
    // try/catch below would not undo that mark, and the final autoTransferRepository
    // .save would fail with a real UnexpectedRollbackException even though the failure
    // was already handled gracefully. Identical root cause to
    // ProductSubscriptionService.executeOne's own 2026-08-17 fix -- see that method's
    // doc comment for the full account. sendDirect remains fully atomic on its own via
    // its own @Transactional annotation; autoTransferRepository.save below is
    // independently atomic via Spring Data's implicit per-call transaction.
    fun executeOne(autoTransfer: AutoTransfer): Boolean {
        val succeeded = try {
            p2pService.sendDirect(autoTransfer.userId, autoTransfer.recipientIdentifier, autoTransfer.amount, autoTransfer.description)
            autoTransfer.lastFailureReason = null
            autoTransfer.executionCount += 1
            autoTransfer.lastExecutedAt = Instant.now()
            true
        } catch (e: InsufficientFundsException) {
            autoTransfer.lastFailureReason = "Insufficient balance"
            false
        } catch (e: P2pRecipientNotFoundException) {
            autoTransfer.lastFailureReason = "Recipient account no longer available"
            false
        } catch (e: Exception) {
            log.error("Auto-transfer {} failed with an unexpected error", autoTransfer.id, e)
            autoTransfer.lastFailureReason = "Couldn't complete this transfer"
            false
        }
        if (!succeeded) {
            notifyTransferFailed(autoTransfer)
        }
        autoTransfer.nextExecutionAt = when (autoTransfer.frequency) {
            AutoTransferFrequency.WEEKLY -> autoTransfer.nextExecutionAt.plus(7, ChronoUnit.DAYS)
            AutoTransferFrequency.MONTHLY -> nextMonthlyOccurrence(autoTransfer.dayOfMonth!!, from = autoTransfer.nextExecutionAt)
        }
        autoTransferRepository.save(autoTransfer)
        return succeeded
    }

    // Real Toss Payments billing-failure alert -- same real, sourced convention
    // ProductSubscriptionService.notifyDeliveryFailed / BillAutoPayProcessor
    // .notifyAutoPayFailed already establish (see either's own doc comment): itunda's
    // recurring-transfer failure paths previously only ever recorded the failure
    // silently, never told the customer. Purely a best-effort side effect wrapped in
    // its own try/catch -- never allowed to affect the real schedule/save. Safe by
    // construction: executeOne is deliberately NOT @Transactional (its own 2026-08-17
    // fix), so a failing notification save can never poison the real transfer attempt.
    private fun notifyTransferFailed(autoTransfer: AutoTransfer) {
        try {
            val title = "Auto-transfer failed"
            val body = "We couldn't send your auto-transfer to ${autoTransfer.recipientName}: ${autoTransfer.lastFailureReason}. We'll try again next cycle."
            notificationRepository.save(
                Notification(
                    id = "notif_${UUID.randomUUID()}", userId = autoTransfer.userId, type = "AUTO_TRANSFER_FAILED",
                    title = title, body = body, isRead = false, createdAt = Instant.now(),
                    dataJson = "{\"autoTransferId\":\"${autoTransfer.id}\"}",
                ),
            )
            pushNotificationService.sendToUser(autoTransfer.userId, title, body, mapOf("autoTransferId" to autoTransfer.id))
        } catch (e: Exception) {
            log.warn("Could not send auto-transfer-failure notification for {}", autoTransfer.id, e)
        }
    }

    private fun nextWeeklyOccurrence(dayOfWeek: Int): Instant {
        val now = Instant.now().atZone(ZoneOffset.UTC)
        var candidate = now.with(java.time.DayOfWeek.of(dayOfWeek))
        if (!candidate.isAfter(now)) candidate = candidate.plusWeeks(1)
        return candidate.toInstant()
    }

    private fun nextMonthlyOccurrence(dayOfMonth: Int, from: Instant = Instant.now()): Instant {
        val base = from.atZone(ZoneOffset.UTC)
        var candidate = base.withDayOfMonth(dayOfMonth)
        if (!candidate.isAfter(base)) candidate = candidate.plusMonths(1).withDayOfMonth(dayOfMonth)
        return candidate.toInstant()
    }
}
