package rw.itunda.ledger.db.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "ledger_idempotency_records")
class LedgerIdempotencyEntity(
    @Id
    @Column(name = "record_key", length = 512, nullable = false)
    val recordKey: String,

    @Column(name = "request_json", columnDefinition = "TEXT", nullable = false)
    val requestJson: String,

    @Column(name = "status_code", nullable = false)
    var statusCode: Int,

    @Column(name = "transaction_id", length = 36)
    var transactionId: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now()
)
