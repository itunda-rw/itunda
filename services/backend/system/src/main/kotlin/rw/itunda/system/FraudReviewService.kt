package rw.itunda.system

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.core.domain.FraudFlag
import rw.itunda.core.domain.FraudFlagDecision
import rw.itunda.core.domain.Notification
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.FraudFlagRepository
import rw.itunda.core.repository.NotificationRepository
import java.time.Instant
import java.util.UUID

class FraudFlagNotFoundException(message: String) : RuntimeException(message)
class FraudFlagAlreadyReviewedException(message: String) : RuntimeException(message)

/**
 * Real, review-only fraud flag decisions -- see [rw.itunda.core.domain.FraudFlag]'s own
 * doc comment for why this never blocks a transaction outright.
 *
 * **Real gap found and fixed 2026-07-26**: before this, `decide()` only ever updated the
 * flag row itself -- the account owner whose own transaction was flagged never learned
 * anything, whether an ops reviewer confirmed real fraud or cleared it as a false
 * positive. Sourced from Toss's own real published customer-facing fraud-detection
 * flow (tosspayments.com/blog/articles/fds's own "위험거래 감지로 결제가 멈췄다면?" --
 * a real, published article specifically about what a Toss customer sees and does when
 * their own payment gets flagged): a confirmed fraud decision now sends the account
 * owner a real security notification, following `DeviceService`'s own established
 * `NEW_DEVICE_LOGIN` alert convention. A `CLEARED` decision (a false positive) sends
 * nothing -- the same way Toss's own real flow never bothers a customer whose flagged
 * transaction turned out to be legitimate.
 *
 * **Real push added 2026-07-28** -- reuses the same `PushNotificationService` path
 * `MerchantBookingService`/`MerchantBookingReviewService` already proved live. A
 * confirmed-fraud alert is exactly the kind of thing an account owner needs to see
 * immediately, not next time they happen to open the app and poll `/notifications` --
 * unlike those two auxiliary cases, best-effort here still applies (a push failure must
 * never make the reviewer's own decide() action look like it failed), but the real
 * urgency argument is stronger for security alerts than for a booking reply.
 */
@Service
class FraudReviewService(
    private val fraudFlagRepository: FraudFlagRepository,
    private val notificationRepository: NotificationRepository,
    private val pushNotificationService: PushNotificationService,
) {

    fun getQueue(pageable: Pageable): Page<FraudFlag> = fraudFlagRepository.findByReviewedFalseOrderByCreatedAtAsc(pageable)

    @Transactional
    fun decide(flagId: String, reviewerId: String, decision: FraudFlagDecision): FraudFlag {
        val flag = fraudFlagRepository.findById(flagId).orElseThrow { FraudFlagNotFoundException("Fraud flag not found") }
        if (flag.reviewed) {
            throw FraudFlagAlreadyReviewedException("This flag has already been reviewed")
        }
        flag.reviewed = true
        flag.decision = decision
        flag.reviewedBy = reviewerId
        flag.reviewedAt = Instant.now()
        val saved = fraudFlagRepository.save(flag)

        if (decision == FraudFlagDecision.CONFIRMED) {
            val title = "Suspicious activity confirmed on your account"
            val body = "We reviewed a flagged transaction on your account and confirmed it as suspicious. Contact support if you don't recognize this activity."
            notificationRepository.save(
                Notification(
                    id = "notif_${UUID.randomUUID()}", userId = flag.userId, type = "FRAUD_CONFIRMED",
                    title = title, body = body,
                    isRead = false, createdAt = Instant.now(), dataJson = "{\"flagId\":\"${saved.id}\",\"transactionId\":\"${saved.transactionId}\"}",
                ),
            )
            pushNotificationService.sendToUser(flag.userId, title, body, mapOf("flagId" to saved.id))
        }

        return saved
    }
}
