package rw.itunda.partners.web

import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import rw.itunda.core.web.pageMeta
import rw.itunda.partners.PartnerService

// The real "app store" surface every itunda client fetches to know which third-party
// mini-apps are approved and available -- requires a normal itunda-user JWT (default
// SecurityConfig .anyRequest().authenticated(), no ADMIN role needed), unlike
// /api/v1/partners/** (partner-authenticated) or /api/v1/system/partners/** (ADMIN-only
// review). Android's own host really downloads and runs an approved bundle from this
// same catalog (see PartnerMiniAppLoader.kt); bank-mfe/iOS only browse it so far.
@RestController
@RequestMapping("/api/v1/mini-apps")
class MiniAppCatalogController(private val partnerService: PartnerService) {

    @GetMapping("/catalog")
    fun catalog(@PageableDefault(size = 20) pageable: Pageable): ResponseEntity<Map<String, Any?>> {
        val page = partnerService.getCatalog(pageable)
        return ResponseEntity.ok(mapOf("success" to true, "miniApps" to page.content) + pageMeta(page))
    }
}
