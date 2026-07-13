package rw.itunda.core.health

import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

data class RailHealth(
    val railId: String,
    val displayName: String,
    val totalAttempts: Long,
    val successCount: Long,
    val failureCount: Long,
    val successRate: Double,
    val avgLatencyMs: Long,
)

private class RailCounters(@Volatile var displayName: String) {
    val totalAttempts = AtomicLong()
    val successCount = AtomicLong()
    val totalLatencyMs = AtomicLong()

    fun record(success: Boolean, latencyMs: Long) {
        totalAttempts.incrementAndGet()
        if (success) successCount.incrementAndGet()
        totalLatencyMs.addAndGet(latencyMs)
    }

    fun toHealth(railId: String): RailHealth {
        val total = totalAttempts.get()
        val success = successCount.get()
        return RailHealth(
            railId = railId,
            displayName = displayName,
            totalAttempts = total,
            successCount = success,
            failureCount = total - success,
            successRate = if (total == 0L) 1.0 else success.toDouble() / total,
            avgLatencyMs = if (total == 0L) 0L else totalLatencyMs.get() / total,
        )
    }
}

/**
 * Real per-rail health, aggregated from real attempts as they happen -- see
 * docs/TOSS_PARITY_MATRIX.md's Operations/Provider health row. Hooked into
 * SimulatedProviderConnector's every real network-call simulation (each
 * attemptOnce roll, plus the offline fast-fail path), the same choke point
 * IncidentDetector uses, so this reflects actual observed success rate and
 * latency rather than replaying the static per-rail config
 * (RailProfile.successRate/avgLatencyMs are the simulated *inputs*; this is
 * the *measured output*, and the two will diverge over a small sample --
 * that's expected, not a bug).
 *
 * In-memory (ConcurrentHashMap), same reasoning as IncidentDetector's
 * rolling failure tracking and WalletService.QuoteStore: ephemeral
 * operational state, not something that needs to survive a restart.
 */
@Service
class ProviderHealthTracker {
    private val counters = ConcurrentHashMap<String, RailCounters>()

    fun recordAttempt(railId: String, displayName: String, success: Boolean, latencyMs: Long) {
        counters.computeIfAbsent(railId) { RailCounters(displayName) }.record(success, latencyMs)
    }

    fun snapshot(): List<RailHealth> =
        counters.entries
            .map { (railId, c) -> c.toHealth(railId) }
            .sortedBy { it.railId }
}
