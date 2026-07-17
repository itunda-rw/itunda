package rw.itunda.partners.web

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import rw.itunda.partners.PartnerService

// The real "app store" surface a mobile Saronite host client would fetch to know which
// third-party mini-apps are approved and available -- requires a normal itunda-user
// JWT (default SecurityConfig .anyRequest().authenticated(), no ADMIN role needed),
// unlike /api/v1/partners/** (partner-authenticated) or /api/v1/system/partners/**
// (ADMIN-only review). See PartnerService's own doc comment for why the mobile side
// doesn't actually consume/render this into a running mini-app yet.
@RestController
@RequestMapping("/api/v1/mini-apps")
class MiniAppCatalogController(private val partnerService: PartnerService) {

    @GetMapping("/catalog")
    fun catalog(): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "miniApps" to partnerService.getCatalog()))
}
