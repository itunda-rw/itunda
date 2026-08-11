package rw.itunda.app.push

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import rw.itunda.app.MainActivity
import rw.itunda.app.R
import rw.itunda.core.network.SessionManager
import rw.itunda.core.network.SessionState
import java.util.concurrent.atomic.AtomicInteger

/**
 * The real other half of `PushNotificationService`/`RealFcmPushSender` (backend): the
 * client side that actually receives and shows what they send. FCM is the transport
 * (see :app's build.gradle.kts doc comment for why -- Android has no OS-sanctioned
 * alternative for waking a backgrounded app; this is the same real mechanism Toss,
 * Kakao, and every other Android app sit on top of), but everything about WHAT gets
 * shown and HOW is itunda's own: two real notification channels
 * (`NotificationChannels`, matching backend's `RealFcmPushSender.CHANNEL_MONEY`/
 * `CHANNEL_GENERAL` by id exactly), Toss's own big-text/single-line-glanceable
 * notification shape, and itunda's own brand color/icon instead of the generic system
 * default.
 *
 * Registered in AndroidManifest.xml with the standard
 * `com.google.firebase.MESSAGING_EVENT` intent-filter. Never constructed directly --
 * the OS/Play Services instantiates this.
 */
class ItundaMessagingService : FirebaseMessagingService() {

    // A fresh FCM token can arrive at any time (reinstall-adjacent FCM behavior, not
    // just a first-ever install) -- SessionManager.registerDeviceToken() already runs
    // once right after every login/register with whatever token is current *then*;
    // this covers the token *changing* underneath an already-logged-in session, which
    // that call alone would miss.
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        if (SessionManager.sessionState.value !is SessionState.LoggedIn) return
        CoroutineScope(Dispatchers.IO).launch {
            SessionManager.registerDeviceToken()
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val title = message.notification?.title ?: message.data["title"] ?: return
        val body = message.notification?.body ?: message.data["body"] ?: ""
        showNotification(title, body, message.data["type"], message.data)
    }

    private fun showNotification(title: String, body: String, type: String?, data: Map<String, String>) {
        // Real permission gate (2026-08-12): posting without it throws a
        // SecurityException on API 33+ once a user has actually revoked it in system
        // Settings after previously granting it -- NotificationPermissionPrompt only
        // ever asks once, it can't stop that later revocation, so this check (not just
        // the manifest declaration) is what keeps a revoke-after-grant from crashing
        // this service.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val isHighPriority = type in HIGH_PRIORITY_TYPES
        val channelId = if (isHighPriority) NotificationChannels.CHANNEL_MONEY else NotificationChannels.CHANNEL_GENERAL
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data.forEach { (key, value) -> putExtra(key, value) }
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            idCounter.incrementAndGet(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_stat_itunda)
            .setColor(ContextCompat.getColor(this, R.color.itunda_notification_brand))
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(if (isHighPriority) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        // A unique id per notification, not one reused id per type -- two real
        // MONEY_RECEIVED pushes arriving close together must both stay visible in the
        // tray; reusing an id would silently replace the first before the user ever saw
        // it, which is never acceptable for a money-moving alert.
        NotificationManagerCompat.from(this).notify(idCounter.incrementAndGet(), notification)
    }

    private companion object {
        val HIGH_PRIORITY_TYPES = setOf("MONEY_RECEIVED", "FRAUD_ALERT")
        val idCounter = AtomicInteger(0)
    }
}
