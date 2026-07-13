package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalDate

/**
 * A real, persisted record of every attempt SimulatedProviderConnector makes -- the durable
 * counterpart to ProviderHealthTracker's in-memory live-health view (which resets on restart
 * and can't be grouped by day). Exists specifically to back real reconciliation -- see
 * docs/TOSS_PARITY_MATRIX.md's Operations/Reconciliation row for why this needed to exist
 * (a prior version of that row falsely claimed this kind of log already existed).
 */
@Entity
@Table(name = "provider_attempt_log")
class ProviderAttemptLog(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "rail_id", nullable = false, length = 64)
    val railId: String,

    @Column(name = "rail_display_name", nullable = false)
    val railDisplayName: String,

    @Column(nullable = false)
    val success: Boolean,

    @Column(name = "latency_ms", nullable = false)
    val latencyMs: Long,

    @Column(name = "occurred_at", nullable = false)
    val occurredAt: Instant = Instant.now(),

    @Column(name = "occurred_date", nullable = false)
    val occurredDate: LocalDate = LocalDate.now(),
) {
    protected constructor() : this(id = "", railId = "", railDisplayName = "", success = false, latencyMs = 0)
}
