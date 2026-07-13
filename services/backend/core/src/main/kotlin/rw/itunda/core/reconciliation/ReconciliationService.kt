package rw.itunda.core.reconciliation

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import rw.itunda.core.domain.ProviderAttemptLog
import rw.itunda.core.provider.RailProfile
import rw.itunda.core.repository.ProviderAttemptLogRepository
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class RailReconciliation(
    val railId: String,
    val railDisplayName: String,
    val totalAttempts: Long,
    val successCount: Long,
    val failureCount: Long,
    val successRate: Double,
    val avgLatencyMs: Long,
)

/**
 * Real reconciliation, aggregated by rail/day from a real persisted attempt log -- see
 * docs/TOSS_PARITY_MATRIX.md's Operations/Reconciliation row for the full account, including
 * why this needed to be built from scratch (a prior version of that row falsely claimed it
 * already existed). Explicitly one-sided: this reconciles itunda's own attempt log against
 * itself, aggregated by day, because there is no real external biller/rail settlement file to
 * diff it against -- that's an honest, named gap, not something this pretends to solve.
 *
 * REQUIRES_NEW on logAttempt for the same reason IncidentDetector.recordFailure needs it: this
 * is called from inside @Transactional caller methods (BillsService.payBill/buyAirtime,
 * WalletService.confirmTransfer) that roll back on a provider decline, and a decline is exactly
 * the event this log most needs to durably capture -- it must not roll back with the caller.
 */
@Service
class ReconciliationService(private val repository: ProviderAttemptLogRepository) {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun logAttempt(rail: RailProfile, success: Boolean, latencyMs: Long) {
        val now = Instant.now()
        repository.save(
            ProviderAttemptLog(
                id = "attemptlog_${UUID.randomUUID()}",
                railId = rail.id,
                railDisplayName = rail.displayName,
                success = success,
                latencyMs = latencyMs,
                occurredAt = now,
                occurredDate = LocalDate.now(),
            ),
        )
    }

    fun report(date: LocalDate): List<RailReconciliation> =
        repository.aggregateByDate(date)
            .map {
                RailReconciliation(
                    railId = it.getRailId(),
                    railDisplayName = it.getRailDisplayName(),
                    totalAttempts = it.getTotalAttempts(),
                    successCount = it.getSuccessCount(),
                    failureCount = it.getTotalAttempts() - it.getSuccessCount(),
                    successRate = if (it.getTotalAttempts() == 0L) 1.0 else it.getSuccessCount().toDouble() / it.getTotalAttempts(),
                    avgLatencyMs = it.getAvgLatencyMs().toLong(),
                )
            }
            .sortedBy { it.railId }
}
