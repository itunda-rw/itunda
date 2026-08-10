package rw.itunda.analytics.web

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import rw.itunda.core.domain.AnalyticsEvent
import rw.itunda.core.repository.AnalyticsEventRepository
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError

data class RecordEventRequest(val eventName: String, val platform: String, val metadata: String? = null)

class UnknownAnalyticsEventException(eventName: String) : RuntimeException("Unknown event name: $eventName")

/**
 * Real, minimal product-analytics endpoint -- see AnalyticsEvent.kt's own doc comment
 * for the full account and honest scope boundary.
 */
@RestController
@RequestMapping("/api/v1/analytics")
class AnalyticsController(private val repository: AnalyticsEventRepository) {

    companion object {
        // Deliberately small, closed vocabulary (see AnalyticsEvent.kt's own doc
        // comment) -- adding a new one is a one-line change here, not a migration, so
        // this isn't meant to gatekeep real product needs, just keep event names from
        // silently drifting into free-text noise no later query can rely on.
        val KNOWN_EVENTS = setOf(
            // Fired once per real Home tab load -- the baseline "did this user open
            // the app today" signal every retention question below is measured against.
            "home_view",
            // Fired when a user taps into any of the 4 cooperative-savings rail items
            // (SACCO shares/Ikimina/Moto-Taxi Ownership/Harvest advance) added to Home
            // this session -- see the "itunda: the wedge, not the mirror" memo,
            // recommendation (ii). `metadata` carries which of the 4 was tapped.
            "coop_rail_tap",
            // Real Toss Intelligence-banner-style frequency capping (2026-08-11) --
            // see DiscoverService's own doc comment. `metadata` carries the specific
            // banner id shown, not a free-text description -- DiscoverService's
            // AnalyticsEventRepository.countByUserIdAndEventNameAndMetadataJson query
            // depends on this being the exact same id string every time the same
            // banner is shown, so a banner can be capped independently of every other.
            "discover_banner_impression",
        )
    }

    @PostMapping("/events")
    fun recordEvent(@AuthenticationPrincipal currentUser: CurrentUser, @RequestBody request: RecordEventRequest): ResponseEntity<Map<String, Any>> {
        if (request.eventName !in KNOWN_EVENTS) throw UnknownAnalyticsEventException(request.eventName)
        repository.save(
            AnalyticsEvent(
                id = "analytics_event_${UUID.randomUUID()}",
                userId = currentUser.userId,
                eventName = request.eventName,
                platform = request.platform,
                metadataJson = request.metadata?.take(500),
            ),
        )
        return ResponseEntity.ok(mapOf("success" to true))
    }

    // ADMIN-only (see SecurityConfig's own /api/v1/analytics/summary matcher) -- this
    // exposes real aggregate usage data, same protection level as /api/v1/system/**.
    //
    // Real, honestly-scoped retention measure: of the distinct users who fired
    // `event`, what fraction had ANY event at least 24h after their own first
    // occurrence of it? This is a per-user existence check run in a loop, not one
    // hand-written SQL JOIN -- far easier to verify correct, and itunda's real event
    // volume today is nowhere near where that tradeoff would matter. Answers the exact
    // question the "itunda: the wedge, not the mirror" memo's recommendation (ii)
    // poses -- Grab's own real 12%→54%/47%→79% numbers (FoxData, 2020) are the same
    // shape of measurement, just at a scale itunda doesn't have yet.
    @GetMapping("/summary")
    fun getSummary(@RequestParam(defaultValue = "30") days: Long): ResponseEntity<Map<String, Any>> {
        val since = Instant.now().minus(days, ChronoUnit.DAYS)
        val eventCounts = KNOWN_EVENTS.associateWith { eventName ->
            mapOf(
                "totalEvents" to repository.countByEventNameSince(eventName, since),
                "distinctUsers" to repository.countDistinctUsersByEventNameSince(eventName, since),
            )
        }
        val totalActiveUsers = repository.countDistinctUsersSince(since)

        val coopRailUsers = repository.findDistinctUserIdsByEventNameSince("coop_rail_tap", since)
        val returnedAfterCoopTap = coopRailUsers.count { userId ->
            val firstTap = repository.findFirstByUserIdAndEventNameOrderByCreatedAtAsc(userId, "coop_rail_tap")
            firstTap != null && repository.existsByUserIdAndCreatedAtAfter(userId, firstTap.createdAt.plus(1, ChronoUnit.DAYS))
        }

        return ResponseEntity.ok(
            mapOf(
                "success" to true,
                "windowDays" to days,
                "totalActiveUsers" to totalActiveUsers,
                "events" to eventCounts,
                "coopRailReturnRate" to mapOf(
                    "usersWhoTapped" to coopRailUsers.size,
                    "returnedNextDayOrLater" to returnedAfterCoopTap,
                    "rate" to if (coopRailUsers.isNotEmpty()) returnedAfterCoopTap.toDouble() / coopRailUsers.size else null,
                ),
            ),
        )
    }

    @ExceptionHandler(UnknownAnalyticsEventException::class)
    fun handleUnknownEvent(ex: UnknownAnalyticsEventException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("UNKNOWN_EVENT", ex.message ?: "Bad request"))
}
