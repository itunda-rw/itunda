package rw.itunda.notifications

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import rw.itunda.core.domain.Notification
import rw.itunda.core.repository.NotificationRepository

data class ServiceChannelBubble(
    val id: String,
    val title: String,
    val body: String,
    val type: String,
    val createdAt: String,
    val isRead: Boolean,
    val ctaRoute: String?,
)

/**
 * Real itunda service channel (itunda Talk redesign, 2026-08-28) -- a real
 * 카카오페이/토스-style alimtalk channel thread, but scoped to itunda's own already-real
 * automated `Notification` rows only (no third-party businesses exist on this
 * platform). Deliberately a **fully virtual, read-only thread, no persisted
 * conversation row at all**: this is a projection of `Notification` rows the caller
 * already owns, with no other participant, no send capability, and no message-history
 * state that would justify a real synthetic `GroupConversation` row the way
 * `isDirect`'s 1:1 split-bill group does. `NotificationRepository` stays the single
 * source of truth -- no new write path, no duplicated storage, matching this feature's
 * own explicit constraint (the existing Home-bell feed at `GET /api/v1/notifications`
 * must keep reading the exact same real data, not a fork of it).
 */
@Service
class ServiceChannelService(private val notificationRepository: NotificationRepository) {

    fun getServiceChannelThread(userId: String, pageable: Pageable): Page<ServiceChannelBubble> =
        notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable).map { it.toBubble() }

    private fun Notification.toBubble() = ServiceChannelBubble(
        id = id, title = title, body = body, type = type,
        createdAt = createdAt.toString(), isRead = isRead, ctaRoute = ctaRouteFor(type),
    )

    // Small, honest, non-exhaustive lookup -- an unmapped type simply gets no CTA
    // (the bubble is still real and readable, just not tappable-to-a-screen), never a
    // guessed/wrong route. Covers the highest-value real types from the ~90 already
    // live in production; extend as real product need surfaces, same incremental
    // rollout discipline PushNotificationService's own doc comment already applies.
    private fun ctaRouteFor(type: String): String? = when (type) {
        "MONEY_RECEIVED", "MONEY_SENT", "CARD_CHARGE" -> "/bank/transactions"
        "NEW_MESSAGE" -> "/talk"
        "FRAUD_ALERT", "FRAUD_CONFIRMED", "NEW_DEVICE_LOGIN" -> "/settings/security"
        "BUDGET_OVER", "BUDGET_NEAR" -> "/bank/budget"
        "BILL_AUTOPAY_LOW_BALANCE" -> "/bills"
        "VUP_LOAN_DUE_SOON", "STUDENT_LOAN_GRACE_PERIOD_ENDING_SOON", "POSTPAID_CREDIT_PAYMENT_DUE_SOON" -> "/loans"
        "SAVINGS_GOAL_MATURED", "SAVINGS_GOAL_COMPLETED" -> "/savings"
        "COMMUNITY_COMMENT" -> "/hood/community"
        else -> null
    }
}
