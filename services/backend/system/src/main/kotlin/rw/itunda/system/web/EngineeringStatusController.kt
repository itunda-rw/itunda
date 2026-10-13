package rw.itunda.system.web

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import rw.itunda.system.engineering.EngineeringStatusProvider

/**
 * Read-only Engineering Status endpoint. SecurityConfig restricts /api/v1/system/**
 * to ADMIN-role JWTs; provider credentials are server-side only.
 */
@RestController
@RequestMapping("/api/v1/system/engineering")
class EngineeringStatusController(
    private val engineeringStatusProvider: EngineeringStatusProvider,
) {
    @GetMapping("/status")
    fun status(): ResponseEntity<Map<String, Any>> =
        try {
            ResponseEntity.ok(engineeringStatusProvider.readStatus())
        } catch (ex: Exception) {
            // Log only the exception type: provider messages may contain operational details.
            logger.warn("Engineering status unavailable ({})", ex.javaClass.simpleName)
            // Never return a healthy-looking empty payload when GitHub/configuration fails.
            ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(
                mapOf(
                    "code" to "ENGINEERING_STATUS_UNAVAILABLE",
                    "message" to "Engineering status is unavailable or not configured.",
                ),
            )
        }
    
    private companion object {
        val logger = LoggerFactory.getLogger(EngineeringStatusController::class.java)
    }
}
