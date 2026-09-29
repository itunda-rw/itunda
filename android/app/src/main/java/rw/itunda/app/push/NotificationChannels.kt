package rw.itunda.app.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Real Toss-style multi-channel notification setup (2026-08-12) -- Toss's own real
 * Android notification settings screen splits push into separate categories a user
 * can individually mute/re-tone (money movement vs. everything else), not one
 * undifferentiated stream. Two channels for this pass, matching
 * `RealFcmPushSender.CHANNEL_MONEY`/`CHANNEL_GENERAL` (backend) and the
 * `com.google.firebase.messaging.default_notification_channel_id` manifest metadata
 * exactly by id string -- these three places must never drift apart, or a push either
 * silently lands on the wrong channel or (worse, pre-O devices aside) fails to post at
 * all on API 26+ if the channel id it names was never created.
 *
 * Called once from `ItundaApplication.onCreate()`, before any push can possibly
 * arrive. `createNotificationChannels` is itself idempotent (recreating an existing
 * channel with the same id is a safe no-op that preserves the user's own
 * mute/importance overrides), so this needs no "already created" guard of its own.
 */
object NotificationChannels {
    const val CHANNEL_MONEY = "money"
    const val CHANNEL_GENERAL = "general"

    fun createAll(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = ContextCompat.getSystemService(context, NotificationManager::class.java) ?: return
        val money = NotificationChannel(
            CHANNEL_MONEY,
            "Money",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Money received, sent, and security alerts"
            enableVibration(true)
        }
        val general = NotificationChannel(
            CHANNEL_GENERAL,
            "General",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Bookings, orders, messages, and everything else"
        }
        manager.createNotificationChannels(listOf(money, general))
    }
}
