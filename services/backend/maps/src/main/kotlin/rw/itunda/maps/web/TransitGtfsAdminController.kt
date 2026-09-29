package rw.itunda.maps.web

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import rw.itunda.core.transit.TransitGtfsFetchFailedException
import rw.itunda.core.transit.TransitGtfsImportService
import rw.itunda.core.web.ApiError

// Mapped under api/v1/system/transit so it inherits SecurityConfig's existing
// hasRole("ADMIN") rule on the system path prefix -- same real RBAC gate
// MerchantModerationAdminController already uses. See TransitGtfsImportService's own
// doc comment: this triggers a real, one-time (re-runnable) fetch-and-replace import
// of the real Kigali GTFS feed, not a live per-request call.
@RestController
@RequestMapping("/api/v1/system/transit")
class TransitGtfsAdminController(
    private val transitGtfsImportService: TransitGtfsImportService,
) {
    @PostMapping("/import-gtfs")
    fun importGtfs(): ResponseEntity<Map<String, Any?>> {
        val result = transitGtfsImportService.importFromUrl()
        return ResponseEntity.ok(mapOf("success" to true, "imported" to result))
    }

    @ExceptionHandler(TransitGtfsFetchFailedException::class)
    fun handleFetchFailed(ex: TransitGtfsFetchFailedException) =
        ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ApiError("TRANSIT_GTFS_FETCH_FAILED", ex.message ?: "GTFS fetch failed"))
}
