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

enum class AutoTransferFrequency { WEEKLY, MONTHLY }
enum class AutoTransferStatus { ACTIVE, PAUSED, CANCELLED }

/**
 * Real Toss Bank 자동이체 (auto-transfer) equivalent -- see the real 송금 page's own
 * "토스뱅크 자동이체 2건" row this models. Mirrors `WeeklySavingsPlan`'s real
 * schedule-locking shape (a day locked once at creation, `nextExecutionAt` only ever
 * advanced forward by the scheduler, never recomputed from "today"), not a from-scratch
 * scheduling convention. `recipientIdentifier` is resolved fresh against
 * `P2pService.sendDirect`'s exact real phone-number-then-account-number lookup on every
 * execution (not cached as a accountId) so a recipient who closes and reopens an account
 * with a new account, or changes their phone number, is still resolved correctly --
 * `recipientName` is a cached display label only, never re-derived money-movement
 * identity.
 */
@Entity
@Table(name = "auto_transfers")
class AutoTransfer(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "user_id", nullable = false, length = 64)
    val userId: String,

    @Column(name = "account_id", nullable = false, length = 64)
    val accountId: String,

    @Column(name = "recipient_identifier", nullable = false, length = 64)
    var recipientIdentifier: String,

    @Column(name = "recipient_name", nullable = false)
    var recipientName: String,

    @Column(nullable = false, precision = 18, scale = 2)
    var amount: BigDecimal,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var frequency: AutoTransferFrequency,

    // ISO day-of-week (1=Monday..7=Sunday) for WEEKLY, locked at creation -- same
    // "never re-read as today's weekday" discipline as WeeklySavingsPlan.openingWeekday.
    @Column(name = "day_of_week")
    var dayOfWeek: Int? = null,

    // 1-28 for MONTHLY (deliberately capped below 29 so every real month has that day --
    // same real-world constraint Toss's own auto-transfer date picker enforces).
    @Column(name = "day_of_month")
    var dayOfMonth: Int? = null,

    @Column(length = 255)
    var description: String = "Auto-transfer",

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: AutoTransferStatus = AutoTransferStatus.ACTIVE,

    @Column(name = "next_execution_at", nullable = false)
    var nextExecutionAt: Instant = Instant.now(),

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "last_executed_at")
    var lastExecutedAt: Instant? = null,

    @Column(name = "execution_count", nullable = false)
    var executionCount: Int = 0,

    // Real, honest "skipped, will retry next cycle" signal -- e.g. insufficient funds --
    // never a fabricated success. Cleared on the next successful execution.
    @Column(name = "last_failure_reason", length = 255)
    var lastFailureReason: String? = null,

    @Column(name = "cancelled_at")
    var cancelledAt: Instant? = null,

    // User updates/cancellation and scheduled execution can race; only one state
    // transition may advance or stop a transfer in a given cycle.
    @Version
    @Column(nullable = false)
    var version: Long = 0,
) {
    protected constructor() : this(
        id = "", userId = "", accountId = "", recipientIdentifier = "", recipientName = "",
        amount = BigDecimal.ZERO, frequency = AutoTransferFrequency.MONTHLY,
    )
}
