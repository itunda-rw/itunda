package rw.itunda.system.web

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import rw.itunda.core.domain.IncidentStatus
import rw.itunda.core.health.ProviderHealthTracker
import rw.itunda.core.incident.IncidentDetector

@RestController
@RequestMapping("/api/v1/system")
class SystemController(
    private val providerHealthTracker: ProviderHealthTracker,
    private val incidentDetector: IncidentDetector,
) {

    // Simple mock endpoints for the system dashboard to complete the migration
    @GetMapping("/dashboard")
    fun getSystemDashboard(): ResponseEntity<Map<String, Any>> {
        return ResponseEntity.ok(mapOf(
            "success" to true,
            "dashboard" to mapOf(
                "generatedAt" to java.time.Instant.now().toString(),
                "country" to "Rwanda",
                "currency" to "RWF",
                "operations" to mapOf("todayVolume" to 0),
                "operatingLayer" to mapOf("activeConsents" to 0)
            )
        ))
    }
    
    @GetMapping("/capabilities")
    fun getProductCapabilities(): ResponseEntity<Map<String, Any>> {
        return ResponseEntity.ok(mapOf("success" to true, "capabilities" to emptyList<Any>(), "summary" to emptyMap<String, Any>()))
    }
    
    @GetMapping("/parity")
    fun getParityMatrix(): ResponseEntity<Map<String, Any>> {
        return ResponseEntity.ok(mapOf("success" to true, "parity" to emptyList<Any>(), "gates" to emptyList<Any>()))
    }

    // Real per-rail health, built and live-verified 2026-07-13 -- see
    // docs/TOSS_PARITY_MATRIX.md's Operations/Provider health row. Unlike the stubs
    // above, this is measured from real attempts (ProviderHealthTracker), not a mock.
    @GetMapping("/rails")
    fun getPaymentRails(): ResponseEntity<Map<String, Any>> {
        val openIncidentRailIds = incidentDetector.getIncidents()
            .filter { it.status == IncidentStatus.OPEN }
            .map { it.railId }
            .toSet()
        val rails = providerHealthTracker.snapshot().map { health ->
            mapOf(
                "railId" to health.railId,
                "displayName" to health.displayName,
                "totalAttempts" to health.totalAttempts,
                "successCount" to health.successCount,
                "failureCount" to health.failureCount,
                "successRate" to health.successRate,
                "avgLatencyMs" to health.avgLatencyMs,
                "status" to if (health.railId in openIncidentRailIds) "INCIDENT" else "HEALTHY",
            )
        }
        return ResponseEntity.ok(mapOf("success" to true, "rails" to rails))
    }
}
