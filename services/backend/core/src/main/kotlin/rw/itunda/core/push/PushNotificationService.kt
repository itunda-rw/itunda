package rw.itunda.core.push

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import rw.itunda.core.repository.DeviceTokenRepository

/**
 * Real push fan-out to every device a user has registered -- see `PushSender`'s own doc
 * comment for the simulated-boundary account. Deliberately best-effort per token (one
 * bad/simulated-failing token must never block delivery to the user's other devices),
 * same "auxiliary side-effect can't block the real action that triggered it" discipline
 * `RoundUpService.processRoundUp`/`ShoppingCashbackService`'s own callers already
 * establish -- a push failure must never surface as if the underlying booking/payment/
 * whatever triggered it had failed.
 *
 * **Deliberately not wired into every existing `Notification` call site in this pass**
 * (a repo-wide grep found 19 of them at the time) -- rewiring all of them in one pass
 * would touch a lot of already-tested, unrelated call sites at once. Wired into three
 * real, named, sourced gaps so far: `MerchantBookingService`'s "new booking request"
 * notify (2026-07-26, per Naver Smart Place's own real "push notifications on new
 * bookings" feature), `MerchantBookingReviewService`'s owner-reply notify (2026-07-27,
 * the sibling gap that same research line named), and `FraudReviewService.decide`'s
 * confirmed-fraud security alert (2026-07-28, per Toss's own real customer-facing FDS
 * flow -- a security alert is exactly the kind of thing that shouldn't wait for the
 * next in-app poll), and `P2pService.notifyMoneyReceived`'s real-time "money received"
 * push (2026-07-28) -- of every notification type in this backend, this is the one
 * closest to real Toss's own signature, most-relied-on notification, picked as the
 * highest-priority remaining site at the time. Doc-comment currency check (2026-09-07,
 * Notifications product-completeness pass): the real count has grown to 97 call sites
 * across 54 files as the backend has grown -- the dominant pattern at nearly all of
 * them is already a direct `notificationRepository.save(...)` +
 * `pushNotificationService.sendToUser(...)` pair in the same method (confirmed by
 * spot-checking Eats/Messaging/Merchant/Fraud/P2P), so most of that growth is already
 * correctly wired through this service by each call site itself, not a still-open
 * backlog of exactly "~15 remaining" sites frozen from the original 2026-07-28 count.
 */
@Service
class PushNotificationService(
    private val deviceTokenRepository: DeviceTokenRepository,
    private val pushSender: PushSender,
) {
    private val log = LoggerFactory.getLogger(PushNotificationService::class.java)

    // `type` (e.g. "MONEY_RECEIVED") rides in the data payload's "type" key -- both
    // RealFcmPushSender (Android notification channel + FCM priority) and the Android
    // client's own ItundaMessagingService (which channel to post under, where a tap
    // deep-links to) read it. Optional and additive: every pre-existing call site that
    // doesn't pass one keeps working exactly as before, defaulting to the "general"
    // channel on both ends.
    fun sendToUser(userId: String, title: String, body: String, data: Map<String, String> = emptyMap(), type: String? = null) {
        val tokens = try {
            deviceTokenRepository.findByUserId(userId)
        } catch (e: Exception) {
            // Push is auxiliary. A transient token-store failure must not fail the
            // payment, fraud-review, or other business action that triggered it.
            log.warn("Could not load push tokens for user {}: {}", userId, e.message)
            return
        }
        if (tokens.isEmpty()) return
        val fullData = if (type != null) data + ("type" to type) else data
        tokens.forEach { token ->
            try {
                pushSender.send(token, title, body, fullData)
            } catch (e: Exception) {
                log.warn("Push delivery failed for user {} token {}: {}", userId, token.id, e.message)
            }
        }
    }
}
