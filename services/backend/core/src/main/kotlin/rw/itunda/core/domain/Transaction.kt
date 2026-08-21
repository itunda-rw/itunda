package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

// INTEREST added 2026-08-11 -- see SavingsService.accrueInterest's own doc comment:
// real Toss Bank passbook interest (user-provided screenshots) posts as its own real
// transaction-history line item ("통장 이자 +36원") the moment it accrues, not as an
// invisible ledger-only balance change requiring a separate manual claim.
enum class TransactionType { TRANSFER, PAYMENT, DEPOSIT, WITHDRAWAL, BILL, AIRTIME, LOAN, INTEREST }
enum class TransactionStatus { PENDING, COMPLETED, FAILED, CANCELLED }

/** Mirrors backend/src/types/index.ts Transaction. */
@Entity
@Table(name = "transactions")
class Transaction(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "reference_number", nullable = false, unique = true, length = 64)
    val referenceNumber: String,

    @Column(name = "sender_id", nullable = false, length = 64)
    val senderId: String,

    @Column(name = "recipient_id", nullable = false, length = 64)
    val recipientId: String,

    @Column(name = "from_account_id", length = 64)
    val fromAccountId: String? = null,

    @Column(name = "to_account_id", length = 64)
    val toAccountId: String? = null,

    @Column(nullable = false, precision = 18, scale = 2)
    val amount: BigDecimal,

    @Column(nullable = false, precision = 18, scale = 2)
    val fee: BigDecimal,

    @Column(nullable = false, length = 8)
    val currency: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    val type: TransactionType,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: TransactionStatus,

    @Column(nullable = false, length = 255)
    val description: String,

    @Column(length = 64)
    val channel: String? = null,

    @Column(name = "provider_reference", length = 80)
    val providerReference: String? = null,

    @Column(name = "completed_at")
    var completedAt: Instant? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
) {
    protected constructor() : this(
        id = "", referenceNumber = "", senderId = "", recipientId = "",
        amount = BigDecimal.ZERO, fee = BigDecimal.ZERO, currency = "RWF",
        type = TransactionType.TRANSFER, status = TransactionStatus.PENDING, description = "",
    )
}
