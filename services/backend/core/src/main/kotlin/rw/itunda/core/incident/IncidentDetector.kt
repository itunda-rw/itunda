package rw.itunda.core.incident

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import rw.itunda.core.domain.Incident
import rw.itunda.core.domain.IncidentStatus
import rw.itunda.core.provider.RailProfile
import rw.itunda.core.repository.IncidentRepository
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class IncidentNotFoundException(message: String) : RuntimeException(message)
class IncidentAlreadyResolvedException(message: String) : RuntimeException(message)

private const val FAILURE_WINDOW_MINUTES = 5L
private const val FAILURE_THRESHOLD = 2

/**
 * Real incident auto-detection -- see docs/TOSS_PARITY_MATRIX.md's Operations/Incidents row.
 * Hooked directly into SimulatedProviderConnector.attempt() (the single choke point every
 * money-moving flow that calls a rail goes through -- WalletService.confirmTransfer,
 * BillsService.payBill/buyAirtime), so this reacts to real decline events, not a human typing
 * "rail X is down" after the fact.
 *
 * Rolling failure counts are in-memory (ConcurrentHashMap), same reasoning
 * WalletService.QuoteStore's quotes are: ephemeral operational state, not something that
 * needs to survive a restart -- a real incident detector restarting mid-outage will just
 * re-detect the outage from the next few failures, which is an acceptable tradeoff for a
 * single-process demo system.
 */
@Service
class IncidentDetector(private val incidentRepository: IncidentRepository) {
    private val recentFailures = ConcurrentHashMap<String, MutableList<Instant>>()

    // Real bug found live: callers of this (BillsService.buyAirtime/payBill,
    // WalletService.confirmTransfer) are @Transactional, and a provider decline throws a
    // RuntimeException that propagates out of them -- Spring's default rollback-on-
    // RuntimeException behavior was rolling back this method's incidentRepository.save()
    // along with the caller's own (correctly-rolled-back) ledger work, since without an
    // explicit propagation this just joined the caller's ambient transaction. Confirmed live:
    // real declines were happening (verified via direct logging of the actual random rolls)
    // but no Incident row ever persisted. REQUIRES_NEW gives this its own transaction that
    // commits independently of whatever the caller ultimately does -- same reasoning
    // EventPublisher.publishImmediately (vs publishAfterCommit) already documents for the
    // identical class of problem.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun recordFailure(rail: RailProfile) {
        val now = Instant.now()
        val windowStart = now.minus(FAILURE_WINDOW_MINUTES, ChronoUnit.MINUTES)
        val timestamps = recentFailures.computeIfAbsent(rail.id) { mutableListOf() }

        synchronized(timestamps) {
            timestamps.add(now)
            timestamps.removeAll { it.isBefore(windowStart) }

            if (timestamps.size >= FAILURE_THRESHOLD && incidentRepository.findByRailIdAndStatus(rail.id, IncidentStatus.OPEN) == null) {
                incidentRepository.save(
                    Incident(
                        id = "incident_${UUID.randomUUID()}",
                        railId = rail.id,
                        railDisplayName = rail.displayName,
                        description = "${timestamps.size} declines from ${rail.displayName} in the last $FAILURE_WINDOW_MINUTES minutes",
                        failureCount = timestamps.size,
                    ),
                )
            }
        }
    }

    fun getIncidents(): List<Incident> = incidentRepository.findAllByOrderByOpenedAtDesc()

    @Transactional
    fun resolve(incidentId: String, resolvedBy: String): Incident {
        val incident = incidentRepository.findById(incidentId).orElseThrow { IncidentNotFoundException("Incident not found") }
        if (incident.status == IncidentStatus.RESOLVED) {
            throw IncidentAlreadyResolvedException("Incident is already resolved")
        }
        incident.status = IncidentStatus.RESOLVED
        incident.resolvedAt = Instant.now()
        incident.resolvedBy = resolvedBy
        recentFailures.remove(incident.railId)
        return incidentRepository.save(incident)
    }
}
