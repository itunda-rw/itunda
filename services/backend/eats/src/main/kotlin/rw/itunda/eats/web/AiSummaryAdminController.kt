package rw.itunda.eats.web

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import rw.itunda.eats.AiSummaryService

// Mapped under api/v1/system so it inherits SecurityConfig's existing hasRole("ADMIN")
// rule on the system path prefix -- same real RBAC gate
// MerchantModerationAdminController/TransitGtfsAdminController already use. A manual,
// controlled trigger for AiSummaryService's own real batch job, alongside the
// automatic daily AiSummaryScheduler -- useful for a first, deliberately-supervised
// run right after the real llama-server instance is deployed.
@RestController
@RequestMapping("/api/v1/system/ai-summaries")
class AiSummaryAdminController(
    private val aiSummaryService: AiSummaryService,
) {
    @PostMapping("/generate")
    fun generate(@RequestParam(required = false, defaultValue = "20") limit: Int): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "generated" to aiSummaryService.generateMissing(limit)))
}
