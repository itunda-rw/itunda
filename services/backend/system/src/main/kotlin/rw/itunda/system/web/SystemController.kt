package rw.itunda.system.web

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import rw.itunda.core.domain.IncidentStatus
import rw.itunda.core.health.ProviderHealthTracker
import rw.itunda.core.incident.IncidentDetector
import rw.itunda.core.provider.MtnMomoNotConfiguredException
import rw.itunda.core.provider.MtnMomoRequestFailedException
import rw.itunda.core.provider.MtnMomoSandboxClient
import rw.itunda.core.reconciliation.ReconciliationService
import rw.itunda.core.web.ApiError
import java.time.LocalDate
import java.time.format.DateTimeParseException

@RestController
@RequestMapping("/api/v1/system")
class SystemController(
    private val providerHealthTracker: ProviderHealthTracker,
    private val incidentDetector: IncidentDetector,
    private val reconciliationService: ReconciliationService,
    private val mtnMomoSandboxClient: MtnMomoSandboxClient,
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

    // Real reconciliation, aggregated by rail/day from a real persisted attempt log --
    // built and live-verified 2026-07-13, replacing a previously false claim in
    // docs/TOSS_PARITY_MATRIX.md's Operations/Reconciliation row (corrected same day, see
    // that row for the full account). One-sided: reconciles itunda's own attempt log
    // against itself -- there is no real external settlement file to diff it against.
    @GetMapping("/reconciliation")
    fun getReconciliation(@RequestParam(required = false) date: String?): ResponseEntity<Map<String, Any>> {
        val reportDate = date?.let { LocalDate.parse(it) } ?: LocalDate.now()
        val report = reconciliationService.report(reportDate).map { rail ->
            mapOf(
                "railId" to rail.railId,
                "displayName" to rail.railDisplayName,
                "totalAttempts" to rail.totalAttempts,
                "successCount" to rail.successCount,
                "failureCount" to rail.failureCount,
                "successRate" to rail.successRate,
                "avgLatencyMs" to rail.avgLatencyMs,
            )
        }
        return ResponseEntity.ok(mapOf("success" to true, "date" to reportDate.toString(), "rails" to report))
    }

    // Real, administratively-triggered connectivity proof against MTN's real MoMo
    // Collection sandbox (2026-07-17) -- see MtnMomoSandboxClient's own doc comment for
    // why this is deliberately NOT wired into any real user-facing transfer/bill flow.
    // ADMIN-gated the same way every other /api/v1/system/** route already is (see
    // SecurityConfig) -- this makes a real, live external call, not something a normal
    // user action should ever trigger.
    @PostMapping("/mtn-momo/connectivity-test")
    fun testMtnMomoConnectivity(): ResponseEntity<Map<String, Any?>> {
        val result = mtnMomoSandboxClient.testConnectivity()
        return ResponseEntity.ok(
            mapOf(
                "success" to true,
                "referenceId" to result.referenceId,
                "status" to result.status,
                "financialTransactionId" to result.financialTransactionId,
                "latencyMs" to result.latencyMs,
            ),
        )
    }

    @ExceptionHandler(MtnMomoNotConfiguredException::class)
    fun handleMtnMomoNotConfigured(ex: MtnMomoNotConfiguredException) =
        ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(ApiError("MTN_MOMO_NOT_CONFIGURED", ex.message ?: "Not configured"))

    @ExceptionHandler(MtnMomoRequestFailedException::class)
    fun handleMtnMomoRequestFailed(ex: MtnMomoRequestFailedException) =
        ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ApiError("MTN_MOMO_REQUEST_FAILED", ex.message ?: "Request failed"))

    @ExceptionHandler(DateTimeParseException::class)
    fun handleBadDate(ex: DateTimeParseException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_DATE_FORMAT", "date must be in YYYY-MM-DD format"))
}
