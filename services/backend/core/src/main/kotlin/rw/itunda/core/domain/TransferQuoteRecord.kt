package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

/** Durable transfer quote shared by all backend instances. Status transitions are guarded by SQL. */
@Entity
@Table(name = "transfer_quotes")
class TransferQuoteRecord(
    @Id
    @Column(length = 80, nullable = false)
    val id: String,

    @Column(name = "user_id", length = 64, nullable = false)
    val userId: String,

    @Column(name = "from_account_id", length = 64, nullable = false)
    val fromAccountId: String,

    @Column(length = 255, nullable = false)
    val recipient: String,

    @Column(precision = 18, scale = 2, nullable = false)
    val amount: BigDecimal,

    @Column(precision = 18, scale = 2, nullable = false)
    val fee: BigDecimal,

    @Column(name = "total_debit", precision = 18, scale = 2, nullable = false)
    val totalDebit: BigDecimal,

    @Column(length = 8, nullable = false)
    val currency: String,

    @Enumerated(EnumType.STRING)
    @Column(length = 16, nullable = false)
    var status: TransferQuoteRecordStatus,

    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,

    @Column(name = "claimed_at")
    var claimedAt: Instant? = null,

    @Column(name = "confirmed_at")
    var confirmedAt: Instant? = null,
)

enum class TransferQuoteRecordStatus { PENDING, CLAIMED, CONFIRMED, EXPIRED, CANCELLED }
