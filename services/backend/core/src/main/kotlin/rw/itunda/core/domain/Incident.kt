package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

enum class IncidentStatus { OPEN, RESOLVED }

/**
 * A real, automatically-opened operational incident -- see docs/TOSS_PARITY_MATRIX.md's
 * Operations/Incidents row ("No incident-response tooling in-product"). SECURITY.md's
 * runbooks are a process document for humans; this is real product surface: an ADMIN-visible
 * record that gets created by the system itself when a rail is actually failing, not typed in
 * by a human after the fact. See rw.itunda.core.incident.IncidentDetector for how it opens.
 */
@Entity
@Table(name = "incidents")
class Incident(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "rail_id", nullable = false, length = 64)
    val railId: String,

    @Column(name = "rail_display_name", nullable = false)
    val railDisplayName: String,

    @Column(nullable = false)
    val description: String,

    @Column(name = "failure_count", nullable = false)
    val failureCount: Int,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: IncidentStatus = IncidentStatus.OPEN,

    @Column(name = "opened_at", nullable = false)
    val openedAt: Instant = Instant.now(),

    @Column(name = "resolved_at")
    var resolvedAt: Instant? = null,

    @Column(name = "resolved_by", length = 64)
    var resolvedBy: String? = null,
) {
    protected constructor() : this(id = "", railId = "", railDisplayName = "", description = "", failureCount = 0)
}
