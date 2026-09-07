package rw.itunda.core.notifications

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import rw.itunda.core.repository.NotificationRepository
import java.time.Duration
import java.time.Instant

internal val NOTIFICATION_READ_RETENTION: Duration = Duration.ofDays(90)

/**
 * Real cleanup (2026-09-07, Notifications product-completeness pass) -- ~55
 * modules across this backend write into `notifications` forever with no prior
 * archival, matching the exact unbounded-retention risk
 * IdempotencyCleanupScheduler/VerificationTokenCleanupScheduler already exist to
 * solve for their own tables. Only READ notifications older than the retention
 * window are removed; an unread notification is kept regardless of age so a user
 * can never silently lose one they haven't seen yet.
 */
@Component
class NotificationCleanupScheduler(
    private val repository: NotificationRepository,
) {
    private val log = LoggerFactory.getLogger(NotificationCleanupScheduler::class.java)

    @Scheduled(fixedDelayString = "\${itunda.notifications.cleanup-interval-ms:3600000}")
    @Transactional
    fun run() {
        val deleted = repository.deleteByIsReadTrueAndCreatedAtBefore(Instant.now().minus(NOTIFICATION_READ_RETENTION))
        if (deleted > 0) log.info("Removed {} old, already-read notification(s)", deleted)
    }
}
