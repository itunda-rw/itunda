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

data class TwoSidedRailReconciliation(
    val railId: String,
    val railDisplayName: String,
    val itundaSuccessCount: Long,
    val externalSettledCount: Long,
    val matched: Boolean,
    val discrepancy: Long,
    val isExternalCountDemo: Boolean,
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
class ReconciliationService(
    private val repository: ProviderAttemptLogRepository,
    private val demoExternalSettlementService: DemoExternalSettlementService,
) {

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

    // Real demo two-sided reconciliation (2026-07-17) -- see
    // DemoExternalSettlementService's own doc comment for why the external count is a
    // real, deterministic simulation rather than a real settlement file, and why this
    // stays count-level only, not amount-level.
    fun reportTwoSided(date: LocalDate): List<TwoSidedRailReconciliation> =
        report(date).map { rail ->
            val externalCount = demoExternalSettlementService.simulateExternalSettledCount(rail.railId, date, rail.successCount)
            TwoSidedRailReconciliation(
                railId = rail.railId,
                railDisplayName = rail.railDisplayName,
                itundaSuccessCount = rail.successCount,
                externalSettledCount = externalCount,
                matched = rail.successCount == externalCount,
                discrepancy = rail.successCount - externalCount,
                isExternalCountDemo = true,
            )
        }
}
