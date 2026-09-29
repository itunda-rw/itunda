package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalTime

/**
 * Real 당근마켓 방해금지 시간 (do-not-disturb window) for keyword alerts -- Karrot
 * Market's own official FAQ (cs.kr.karrotmarket.com/wv/faqs/19): a user sets a real
 * start and end time; during that window, no notifications ring. Scoped here to
 * keyword-alert pushes specifically (`KeywordAlertService.notifyMatchingAlerts`),
 * matching the narrower, honest follow-up this class's own doc comment named -- a real
 * app-wide mute-everything setting would touch every notification call site in this
 * codebase, a much larger, separately-scoped feature this pass doesn't attempt.
 *
 * Real local clock time, not UTC: `startTime`/`endTime` are compared against
 * `Africa/Kigali` (Rwanda's own real, single, DST-free timezone), since a "quiet
 * hours" feature is honestly meaningless if it's silently offset from a real user's
 * actual local sleeping hours.
 */
@Entity
@Table(name = "keyword_alert_quiet_hours")
class KeywordAlertQuietHours(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "user_id", nullable = false, unique = true, length = 64)
    val userId: String,

    @Column(name = "start_time", nullable = false)
    var startTime: LocalTime,

    @Column(name = "end_time", nullable = false)
    var endTime: LocalTime,

    @Column(nullable = false)
    var enabled: Boolean = true,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
) {
    protected constructor() : this(id = "", userId = "", startTime = LocalTime.MIDNIGHT, endTime = LocalTime.MIDNIGHT)
}
