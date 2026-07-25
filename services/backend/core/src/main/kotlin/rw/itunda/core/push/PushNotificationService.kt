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
 * of already-tested, unrelated call sites at once. Wired into the one real, named,
 * sourced gap this feature closes (`MerchantBookingService`'s "new booking request"
 * notify, per Naver Smart Place's own real "push notifications on new bookings"
 * feature) as a live proof, with the other 18 sites named as a real, valuable,
 * still-open follow-up rather than silently left unaddressed.
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
