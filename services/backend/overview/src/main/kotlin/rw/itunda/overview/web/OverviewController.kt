package rw.itunda.overview.web

import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import rw.itunda.core.security.CurrentUser
import rw.itunda.overview.OverviewService

@RestController
@RequestMapping("/api/v1/overview")
class OverviewController(private val overviewService: OverviewService) {

    @GetMapping
    fun getOverview(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any>> {
        val result = overviewService.getOverview(currentUser.userId)
        return ResponseEntity.ok(
            mapOf(
                "success" to true,
                "netWorth" to result.netWorth,
                "accounts" to result.accounts,
                "savings" to result.savings,
                "loans" to result.loans,
                "investments" to result.investments,
                "insurance" to result.insurance,
                "linkedAccounts" to result.linkedAccounts,
            ),
        )
    }
}
