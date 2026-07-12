package rw.itunda.system.web

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import rw.itunda.core.incident.IncidentAlreadyResolvedException
import rw.itunda.core.incident.IncidentDetector
import rw.itunda.core.incident.IncidentNotFoundException
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError

// Mapped under api/v1/system/incidents specifically so it inherits SecurityConfig's existing
// hasRole("ADMIN") gate, same convention as ComplianceController/FraudController.
@RestController
@RequestMapping("/api/v1/system/incidents")
class IncidentController(private val incidentDetector: IncidentDetector) {

    @GetMapping
    fun getIncidents(): ResponseEntity<Map<String, Any>> =
        ResponseEntity.ok(mapOf("success" to true, "incidents" to incidentDetector.getIncidents()))

    @PostMapping("/{incidentId}/resolve")
    fun resolve(
        @PathVariable incidentId: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any>> {
        val incident = incidentDetector.resolve(incidentId, currentUser.userId)
        return ResponseEntity.ok(mapOf("success" to true, "incident" to incident))
    }

    @ExceptionHandler(IncidentNotFoundException::class)
    fun handleNotFound(ex: IncidentNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("INCIDENT_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(IncidentAlreadyResolvedException::class)
    fun handleAlreadyResolved(ex: IncidentAlreadyResolvedException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("INCIDENT_ALREADY_RESOLVED", ex.message ?: "Conflict"))
}
