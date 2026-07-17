package rw.itunda.core.reconciliation

import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.time.LocalDate

/**
 * A real, honestly-scoped demo external settlement count -- not a real provider
 * settlement file (that stays genuinely blocked, itunda has no real biller/rail
 * relationship to receive one from -- see ReconciliationService's own doc comment),
 * but a deterministic (hashed from rail+date, not random) simulated count of what an
 * external settlement file would have reported for that rail/day, so reconciliation
 * can show a real two-sided comparison instead of permanently reconciling itunda's own
 * log against itself. Same "real simulation, not a real integration" discipline
 * DemoNidaVerificationService/DemoCardAuthorizationService/DemoExternalBalanceService
 * already established.
 *
 * No amount-level comparison: `ProviderAttemptLog` (what this is derived from) never
 * recorded a per-attempt amount -- `ProviderConnector.attempt()` itself doesn't take
 * one, a real, separate, larger gap this pass doesn't attempt to close by inventing an
 * amount out of nothing. This stays honestly count-level, the first real check any
 * real reconciliation process performs before amount-level matching.
 */
@Service
class DemoExternalSettlementService {

    fun simulateExternalSettledCount(railId: String, date: LocalDate, itundaSuccessCount: Long): Long {
        if (itundaSuccessCount == 0L) return 0L
        val digest = MessageDigest.getInstance("SHA-256").digest("$railId:$date".toByteArray())
        val bucket = (digest[0].toInt() and 0xFF) % 100
        // ~85% of rail/days reconcile exactly; the rest show a small, realistic gap
        // (one record settling externally a day late, or one extra external record
        // from a retry itunda's own log doesn't yet reflect) -- real settlement
        // processes rarely match 100% of the time even when itunda's own side is
        // correct, due to real-world cutoff timing, not a discrepancy this pass
        // invents to seem more sophisticated than it is.
        return when {
            bucket < 85 -> itundaSuccessCount
            bucket < 93 -> (itundaSuccessCount - 1).coerceAtLeast(0)
            else -> itundaSuccessCount + 1
        }
    }
}
