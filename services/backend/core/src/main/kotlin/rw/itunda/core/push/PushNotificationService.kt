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
 * (a repo-wide grep found 19 of them) -- rewiring all 19 in one pass would touch a lot
 * of already-tested, unrelated call sites at once. Wired into three real, named, sourced
 * gaps so far: `MerchantBookingService`'s "new booking request" notify (2026-07-26, per
 * Naver Smart Place's own real "push notifications on new bookings" feature),
 * `MerchantBookingReviewService`'s owner-reply notify (2026-07-27, the sibling gap that
 * same research line named), and `FraudReviewService.decide`'s confirmed-fraud security
 * alert (2026-07-28, per Toss's own real customer-facing FDS flow -- a security alert is
 * exactly the kind of thing that shouldn't wait for the next in-app poll), and
 * `P2pService.notifyMoneyReceived`'s real-time "money received" push (2026-07-28) -- of
 * every notification type in this backend, this is the one closest to real Toss's own
 * signature, most-relied-on notification, picked as the highest-priority remaining site
 * from the ~16 named above. The remaining ~15 sites stay a real, valuable, still-open
 * follow-up rather than silently left unaddressed.
 */
@Service
class PushNotificationService(
    private val deviceTokenRepository: DeviceTokenRepository,
    private val pushSender: PushSender,
) {
    private val log = LoggerFactory.getLogger(PushNotificationService::class.java)

    fun sendToUser(userId: String, title: String, body: String, data: Map<String, String> = emptyMap()) {
        val tokens = deviceTokenRepository.findByUserId(userId)
        if (tokens.isEmpty()) return
        tokens.forEach { token ->
            try {
                pushSender.send(token, title, body, data)
            } catch (e: Exception) {
                log.warn("Push delivery failed for user {} token {}: {}", userId, token.id, e.message)
            }
        }
    }
}
