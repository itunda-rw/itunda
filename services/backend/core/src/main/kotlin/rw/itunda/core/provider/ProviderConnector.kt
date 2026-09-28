package rw.itunda.core.provider

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import rw.itunda.core.health.ProviderHealthTracker
import rw.itunda.core.incident.IncidentDetector
import rw.itunda.core.reconciliation.ReconciliationService
import kotlin.random.Random

class ProviderDeclinedException(message: String) : RuntimeException(message)

/**
 * A per-rail simulated profile. Values here are designed for demo realism, not
 * measured from any real provider -- itunda has no live MTN MoMo/Airtel Money/REG/
 * WASAC/Irembo integration to draw a real SLA from (see RnpPaymentGateway.kt's own
 * doc comment for the same honesty about mobile-money specifically). This rebuilds,
 * fresh, the shape TOSS_PARITY_MATRIX.md describes for the now-removed Express
 * backend's providerConnectors.ts (real per-rail avgLatencyMs/successRate, one retry
 * for degraded rails, always-fail for offline rails) -- that file no longer exists in
 * this repo, so this isn't a port, it's a rebuild against the same design.
 */
data class RailProfile(
    val id: String,
    val displayName: String,
    val avgLatencyMs: Long,
    val successRate: Double,
    val degraded: Boolean = false,
    val offline: Boolean = false,
)

object RailCatalog {
    val reg = RailProfile("reg", "REG - Electricity", avgLatencyMs = 600, successRate = 0.96)
    val wasac = RailProfile("wasac", "WASAC - Water", avgLatencyMs = 700, successRate = 0.92, degraded = true)
    val mtnMomo = RailProfile("mtn_momo", "MTN Mobile Money", avgLatencyMs = 400, successRate = 0.98)
    val airtelMoney = RailProfile("airtel_money", "Airtel Money", avgLatencyMs = 450, successRate = 0.97)
    val irembo = RailProfile("irembo", "Irembo Services", avgLatencyMs = 500, successRate = 0.96)
    val entertainment = RailProfile("entertainment", "DSTV/Startimes", avgLatencyMs = 350, successRate = 0.99)
    val generic = RailProfile("generic", "Generic rail", avgLatencyMs = 500, successRate = 0.95)

    /** Best-effort match against a free-text provider name (bill/airtime request
     * fields are loosely-typed strings, not a foreign key into a provider table --
     * see BillsService's callers). Falls back to [generic] rather than guessing. */
    fun resolve(providerName: String?): RailProfile {
        val name = providerName?.uppercase() ?: return generic
        return when {
            "REG" in name -> reg
            "WASAC" in name -> wasac
            "MTN" in name -> mtnMomo
            "AIRTEL" in name -> airtelMoney
            "IREMBO" in name -> irembo
            "DSTV" in name || "STARTIMES" in name -> entertainment
            else -> generic
        }
    }

    /**
     * Real per-rail routing for P2P account transfers (2026-07-13) -- closes the gap
     * docs/TOSS_PARITY_MATRIX.md's Transfer row named: AccountService.confirmTransfer
     * called [resolve] against `quote.recipient`, a phone number, not a provider name,
     * so it always fell through to [generic] regardless of which real rail the
     * recipient's number actually belongs to. A phone number's own prefix is a real,
     * publicly documented signal of carrier assignment in Rwanda's national numbering
     * plan (RURA, Rwanda's telecom regulator): 078 is allocated to MTN Rwanda, 072/073
     * to Airtel Rwanda (confirmed via Wikipedia's "Telephone numbers in Rwanda" article,
     * itself sourced from RURA's own numbering plan). Deliberately conservative: only
     * routes the two prefix ranges confirmed from that source; anything else (a
     * landline, an unrecognized prefix, a malformed number) falls back to [generic]
     * rather than guessing, same discipline [resolve] above already follows.
     */
    fun resolveByPhoneNumber(phoneNumber: String): RailProfile {
        val digits = phoneNumber.filter { it.isDigit() }
        val national = when {
            digits.startsWith("250") && digits.length >= 12 -> digits.substring(3)
            digits.startsWith("0") && digits.length == 10 -> digits.substring(1)
            else -> digits
        }
        return when (national.take(2)) {
            "78" -> mtnMomo
            "72", "73" -> airtelMoney
            else -> generic
        }
    }
}

interface ProviderConnector {
    /** Simulates a network call to an external rail. Throws [ProviderDeclinedException]
     * on failure; callers must call this *before* posting to the ledger, so a decline
     * never touches a account balance. */
    fun attempt(rail: RailProfile, description: String)
}

/**
 * Real (simulated) network call: sleeps ~[RailProfile.avgLatencyMs], then rolls
 * failure against [RailProfile.successRate]. Offline rails always fail without
 * sleeping (matching a real fast-fail on a known-down rail); degraded rails get one
 * retry before declining, mirroring the design TOSS_PARITY_MATRIX.md describes.
 *
 * Every real decline is reported to IncidentDetector (2026-07-13) -- the single choke
 * point every rail-calling flow passes through, so real incident auto-detection reacts to
 * actual failures here, not a separate/duplicated failure-counting mechanism per caller.
 * Every real attempt (success or failure) is also reported to ProviderHealthTracker
 * (2026-07-13) with its real measured latency, for the same reason, and persisted via
 * ReconciliationService (2026-07-13) so it can be aggregated by rail/day after the fact --
 * ProviderHealthTracker is in-memory and resets on restart, which is fine for a live-health
 * view but not for a reconciliation report.
 */
@Component
class SimulatedProviderConnector(
    private val incidentDetector: IncidentDetector,
    private val providerHealthTracker: ProviderHealthTracker,
    private val reconciliationService: ReconciliationService,
    @Value("\${itunda.providers.simulation-enabled:true}") private val simulationEnabled: Boolean,
) : ProviderConnector {
    override fun attempt(rail: RailProfile, description: String) {
        if (!simulationEnabled) {
            throw ProviderDeclinedException(
                rail.displayName + " is unavailable: no real production provider is configured",
            )
        }

        if (rail.offline) {
            incidentDetector.recordFailure(rail)
            providerHealthTracker.recordAttempt(rail.id, rail.displayName, success = false, latencyMs = 0)
            reconciliationService.logAttempt(rail, success = false, latencyMs = 0)
            throw ProviderDeclinedException("${rail.displayName} is currently offline")
        }

        if (attemptOnce(rail)) return

        if (rail.degraded) {
            if (attemptOnce(rail)) return
        }

        incidentDetector.recordFailure(rail)
        throw ProviderDeclinedException("${rail.displayName} declined: $description")
    }

    private fun attemptOnce(rail: RailProfile): Boolean {
        val start = System.currentTimeMillis()
        Thread.sleep(rail.avgLatencyMs)
        val ok = Random.nextDouble() < rail.successRate
        val latencyMs = System.currentTimeMillis() - start
        providerHealthTracker.recordAttempt(rail.id, rail.displayName, ok, latencyMs)
        reconciliationService.logAttempt(rail, ok, latencyMs)
        return ok
    }
}
