package rw.itunda.agents

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** Customer-facing cash-point discovery backed by Itunda's own location data. */
@RestController
@RequestMapping("/api/v1/agents")
class AgentDiscoveryController(private val agentService: AgentService) {
    @GetMapping("/nearby")
    fun nearby(
        @RequestParam latitude: Double,
        @RequestParam longitude: Double,
        @RequestParam(defaultValue = "5") radiusKm: Double,
    ): ResponseEntity<Map<String, Any>> = ResponseEntity.ok(
        mapOf("success" to true, "agents" to agentService.nearby(latitude, longitude, radiusKm)),
    )
}
