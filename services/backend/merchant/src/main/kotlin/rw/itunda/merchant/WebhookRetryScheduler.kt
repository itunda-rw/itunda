package rw.itunda.merchant

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional
import rw.itunda.core.domain.WebhookDeliveryStatus
import rw.itunda.core.repository.WebhookDeliveryRepository
import java.time.Instant

/**
 * Polls the real, persisted webhook retry queue -- the second half of the pattern
 * [WebhookDeliveryService] writes the first half of, same durable-outbox shape as
 * [rw.itunda.core.events.OutboxRelay] and the same demo-speed-poll-of-a-real-business-
 * cadence convention as [rw.itunda.savings.AutoSaveScheduler]: the *retry intervals*
 * (1, 4, 16, ..., 4096 minutes) are Toss's real documented schedule, unchanged; only
 * the poll frequency below is sped up for a single-process demo environment -- checking
 * every 10 seconds for deliveries whose real next-attempt time has passed is cheap and
 * correct, it does not mean retries happen every 10 seconds.
 *
 * Real bug found live (2026-08-09), same class as [rw.itunda.core.events.OutboxRelay]'s
 * own fix on the same day: `findTop100ByStatusAndNextAttemptAtBeforeOrderByNextAttemptAtAsc`
 * is `PESSIMISTIC_WRITE` over an unbounded `status = PENDING` range, which takes real
 * InnoDB next-key (row + GAP) locks under MySQL's default REPEATABLE READ isolation --
 * if enough merchants' webhook endpoints are slow/unresponsive at once (this method's
 * own per-row HTTP timeout is 5s, and up to 100 rows can be due in one poll), that gap
 * lock can be held long enough to block an unrelated NEW webhook_deliveries insert from
 * a real event elsewhere in the backend. Same fix: READ_COMMITTED keeps the real
 * per-row lock this method needs (stopping two replicas double-retrying the same
 * delivery) without gap-locking rows that don't exist yet.
 */
@Component
class WebhookRetryScheduler(
    private val webhookDeliveryRepository: WebhookDeliveryRepository,
    private val webhookDeliveryService: WebhookDeliveryService,
) {
    private val log = LoggerFactory.getLogger(WebhookRetryScheduler::class.java)

    @Scheduled(fixedDelay = 10000)
    @Transactional(isolation = Isolation.READ_COMMITTED)
    fun run() {
        val due = webhookDeliveryRepository.findTop100ByStatusAndNextAttemptAtBeforeOrderByNextAttemptAtAsc(
            WebhookDeliveryStatus.PENDING,
            Instant.now(),
        )
        for (delivery in due) {
            log.info("Delivering queued webhook {} to {} (attempt {}/{})", delivery.id, WebhookUrlPolicy.displayTarget(delivery.webhookUrl), delivery.attemptCount + 1, WebhookDeliveryService.MAX_ATTEMPTS)
            webhookDeliveryService.retry(delivery)
        }
    }
}
