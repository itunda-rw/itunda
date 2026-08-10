package rw.itunda.discover.web

import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import rw.itunda.core.security.CurrentUser
import rw.itunda.discover.DiscoverService

// Real personalized Discover (2026-08-11) -- see DiscoverService's own doc comment for
// the full Toss Intelligence-banner research this came out of. This controller used to
// return the exact same 8 hardcoded items to every caller regardless of who they were,
// despite `@AuthenticationPrincipal CurrentUser` already being resolvable here (every
// call is already JWT-authenticated -- SecurityConfig has no permitAll() entry for this
// path) -- the identity was simply never read. All real personalization/ranking/
// frequency-capping logic lives in DiscoverService; this stays a thin adapter.
@RestController
@RequestMapping("/api/v1/discover")
class DiscoverController(private val discoverService: DiscoverService) {

    @GetMapping
    fun getDiscoverItems(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any>> {
        val result = discoverService.getDiscoverFor(currentUser.userId)
        return ResponseEntity.ok(mapOf(
            "success" to true,
            "items" to result.items,
            "banners" to result.banners
        ))
    }

    @GetMapping("/{category}")
    fun getDiscoverByCategory(@AuthenticationPrincipal currentUser: CurrentUser, @PathVariable category: String): ResponseEntity<Map<String, Any>> {
        val filtered = discoverService.getDiscoverFor(currentUser.userId).items.filter { it.category == category }
        return ResponseEntity.ok(mapOf(
            "success" to true,
            "items" to filtered
        ))
    }
}
