package rw.itunda.community.web

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import rw.itunda.community.HoodAiSummaryService

// Mapped under api/v1/system so it inherits SecurityConfig's existing hasRole("ADMIN")
// rule -- see eats.web.AiSummaryAdminController's own doc comment, same real RBAC
// gate and manual-trigger-alongside-the-scheduler shape.
@RestController
@RequestMapping("/api/v1/system/hood-ai-summaries")
class HoodAiSummaryAdminController(
    private val hoodAiSummaryService: HoodAiSummaryService,
) {
    @PostMapping("/generate")
    fun generate(@RequestParam(required = false, defaultValue = "20") limit: Int): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "generated" to hoodAiSummaryService.generateMissing(limit)))
}
