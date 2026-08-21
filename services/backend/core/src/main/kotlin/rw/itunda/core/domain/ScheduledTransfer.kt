package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

enum class ScheduledTransferStatus { PENDING, EXECUTED, CANCELLED, FAILED }

/**
 * Real Toss 예약송금 (scheduled/reserved transfer) equivalent -- a genuinely distinct
 * feature from [AutoTransfer]'s own real 자동이체: this is explicitly ONE-TIME on a
 * single future date, not recurring (sourced: Toss's own scheduled-transfer flow lets
 * you pick "1회" and a specific future date; real transfers then run starting 9am on
 * that date, sequentially, with no specific time-of-day selectable, and are cancellable
 * any time before that cutoff). Reuses `P2pService.sendDirect`'s exact real
 * ACCOUNT-to-ACCOUNT ledger movement at execution time -- a scheduled transfer is not a
 * new kind of money movement, just a different trigger for the same one, the identical
 * reasoning `AutoTransfer.kt`'s own doc comment already gives.
 *
 * `scheduledDate` is a plain calendar date (no time-of-day), matching Toss's own real
 * "no specific time" constraint exactly rather than inventing more precision than the
 * real feature has. This backend's own honest simplification of the real "before 9am"
 * cancellation cutoff: cancellable any time while still `PENDING`, which is naturally
 * impossible once the scheduler executes it on the scheduled date -- a deliberately
 * coarser cutoff than Toss's exact time-of-day rule, not a claimed reproduction of it.
 */
@Entity
@Table(name = "scheduled_transfers")
class ScheduledTransfer(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "user_id", nullable = false, length = 64)
    val userId: String,

    @Column(name = "account_id", nullable = false, length = 64)
    val accountId: String,

    @Column(name = "recipient_identifier", nullable = false, length = 64)
    val recipientIdentifier: String,

    @Column(name = "recipient_name", nullable = false)
    val recipientName: String,

    @Column(nullable = false, precision = 18, scale = 2)
    val amount: BigDecimal,

    @Column(length = 255)
    var description: String = "Scheduled transfer",

    @Column(name = "scheduled_date", nullable = false)
    val scheduledDate: LocalDate,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: ScheduledTransferStatus = ScheduledTransferStatus.PENDING,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "executed_at")
    var executedAt: Instant? = null,

    @Column(name = "transaction_id", length = 64)
    var transactionId: String? = null,

    @Column(name = "failure_reason", length = 255)
    var failureReason: String? = null,

    @Column(name = "cancelled_at")
    var cancelledAt: Instant? = null,

    @Version
    @Column(nullable = false)
    var version: Long = 0,
) {
    protected constructor() : this(
        id = "", userId = "", accountId = "", recipientIdentifier = "", recipientName = "",
        amount = BigDecimal.ZERO, scheduledDate = LocalDate.now(),
    )
}
