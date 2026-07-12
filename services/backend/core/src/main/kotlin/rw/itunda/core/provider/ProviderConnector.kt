package rw.itunda.core.provider

import org.springframework.stereotype.Component
import rw.itunda.core.incident.IncidentDetector
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
}

interface ProviderConnector {
    /** Simulates a network call to an external rail. Throws [ProviderDeclinedException]
     * on failure; callers must call this *before* posting to the ledger, so a decline
     * never touches a wallet balance. */
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
 */
@Component
class SimulatedProviderConnector(private val incidentDetector: IncidentDetector) : ProviderConnector {
    override fun attempt(rail: RailProfile, description: String) {
        if (rail.offline) {
            incidentDetector.recordFailure(rail)
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
        Thread.sleep(rail.avgLatencyMs)
        return Random.nextDouble() < rail.successRate
    }
}
