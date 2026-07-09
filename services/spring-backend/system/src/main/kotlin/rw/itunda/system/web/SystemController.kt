package rw.itunda.system.web

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/system")
class SystemController {

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

    @GetMapping("/rails")
    fun getPaymentRails(): ResponseEntity<Map<String, Any>> {
        return ResponseEntity.ok(mapOf("success" to true, "rails" to emptyList<Any>()))
    }
}
