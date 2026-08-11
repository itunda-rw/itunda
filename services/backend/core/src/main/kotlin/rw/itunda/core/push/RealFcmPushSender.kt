package rw.itunda.core.push

import com.google.firebase.messaging.AndroidConfig
import com.google.firebase.messaging.AndroidNotification
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingException
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.Notification
import org.slf4j.LoggerFactory
import rw.itunda.core.domain.DeviceToken

/**
 * The real other half of `PushSender`'s own doc comment: everything up to and including
 * "which real device tokens should receive this push, with what real title/body" was
 * already real (`PushNotificationService`) -- this is the real network hop to FCM's own
 * HTTP v1 API via Google's own Admin SDK, replacing `SimulatedPushSender`'s log-only
 * stand-in the moment a real Firebase project's service-account credentials are
 * configured (see `PushConfig`'s own doc comment for exactly how that switch happens).
 *
 * `channelId`/priority are picked from `data["type"]`, not hardcoded -- the same "money"
 * vs "general" Android notification channels `ItundaMessagingService` (Android client)
 * declares, so a money-moving push (`P2pService.notifyMoneyReceived`'s real
 * `MONEY_RECEIVED` type, Toss's own signature notification per `PushNotificationService`'s
 * doc comment) rings/vibrates immediately even in Doze, and everything else stays on the
 * quieter default channel. Every other existing call site that doesn't set `type` falls
 * back to "general" -- a real, honest, narrower rollout than rewriting all ~30 call sites
 * in one pass, matching this codebase's own "highest-priority real site first" precedent.
 */
class RealFcmPushSender(private val firebaseMessaging: FirebaseMessaging) : PushSender {
    private val log = LoggerFactory.getLogger(RealFcmPushSender::class.java)

    override fun send(deviceToken: DeviceToken, title: String, body: String, data: Map<String, String>): Boolean {
        val isHighPriority = data["type"] in HIGH_PRIORITY_TYPES
        val message = Message.builder()
            .setToken(deviceToken.token)
            .setNotification(Notification.builder().setTitle(title).setBody(body).build())
            .putAllData(data)
            .setAndroidConfig(
                AndroidConfig.builder()
                    .setPriority(if (isHighPriority) AndroidConfig.Priority.HIGH else AndroidConfig.Priority.NORMAL)
                    .setNotification(
                        AndroidNotification.builder()
                            .setChannelId(if (isHighPriority) CHANNEL_MONEY else CHANNEL_GENERAL)
                            .build(),
                    )
                    .build(),
            )
            .build()
        return try {
            val messageId = firebaseMessaging.send(message)
            log.debug("FCM push sent to {}: message id {}", deviceToken.token.take(12) + "...", messageId)
            true
        } catch (e: FirebaseMessagingException) {
            // An invalid/expired token is routine (uninstalled app, cleared data) --
            // matches PushNotificationService.sendToUser's own best-effort-per-token
            // discipline: this must never propagate and block the other devices' pushes.
            log.warn("FCM push failed for token {}: {}", deviceToken.token.take(12) + "...", e.message)
            false
        }
    }

    companion object {
        const val CHANNEL_MONEY = "money"
        const val CHANNEL_GENERAL = "general"
        private val HIGH_PRIORITY_TYPES = setOf("MONEY_RECEIVED", "FRAUD_ALERT")
    }
}
