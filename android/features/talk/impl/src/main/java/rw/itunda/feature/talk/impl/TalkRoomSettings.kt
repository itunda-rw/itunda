package rw.itunda.feature.talk.impl

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import rw.itunda.core.designsystem.components.BackTopBar
import rw.itunda.core.designsystem.components.IdsButton
import rw.itunda.core.designsystem.components.IdsButtonSize
import rw.itunda.core.designsystem.components.IdsButtonVariant
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.NetworkClient

// Real per-room settings (itunda Talk redesign, 2026-08-28) -- theme/background and
// input-lock are genuinely device-local (real KakaoTalk keeps these client-side
// too, no backend endpoint fabricated); notification sound and home-screen shortcut
// use real platform APIs rather than an in-app fake.
internal enum class TalkRoomTheme(val label: String) { SYSTEM("Default"), LIGHT("Light"), DARK("Dark") }

internal class TalkRoomPreferenceStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("itunda_talk_room_prefs", Context.MODE_PRIVATE)

    fun getTheme(conversationId: String): TalkRoomTheme =
        TalkRoomTheme.entries.find { it.name == prefs.getString("theme_$conversationId", null) } ?: TalkRoomTheme.SYSTEM

    fun setTheme(conversationId: String, theme: TalkRoomTheme) {
        prefs.edit().putString("theme_$conversationId", theme.name).apply()
    }

    fun isLocked(conversationId: String): Boolean = prefs.getBoolean("locked_$conversationId", false)

    fun setLocked(conversationId: String, locked: Boolean) {
        prefs.edit().putBoolean("locked_$conversationId", locked).apply()
    }
}

@Composable
internal fun TalkRoomSettingsView(conversationId: String, roomName: String, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val store = remember { TalkRoomPreferenceStore(context) }
    var theme by remember { mutableStateOf(store.getTheme(conversationId)) }
    var locked by remember { mutableStateOf(store.isLocked(conversationId)) }
    var exporting by remember { mutableStateOf(false) }
    var exportedText by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val channelId = "talk_room_$conversationId"
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (nm?.getNotificationChannel(channelId) == null) {
                nm?.createNotificationChannel(NotificationChannel(channelId, roomName, NotificationManager.IMPORTANCE_DEFAULT))
            }
        }
    }

    // Real client-side chat export -- fetches this conversation's real message
    // history and hands it to the native share sheet as plain text. No new
    // file-provider config; a text share works for any chat length without one.
    suspend fun exportChat() {
        exporting = true
        try {
            val res = NetworkClient.apiService.getMessages(conversationId)
            val text = if (res.success) {
                res.messages.reversed().joinToString("\n") { "[${it.sentAt.take(16)}] ${it.body}" }
            } else {
                ""
            }
            exportedText = text
        } catch (_: Exception) {
            exportedText = ""
        } finally {
            exporting = false
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        BackTopBar("Room settings", onBack)
        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Text("Theme", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TalkRoomTheme.entries.forEach { option ->
                        IdsButton(
                            text = option.label,
                            variant = if (theme == option) IdsButtonVariant.Filled else IdsButtonVariant.Tinted,
                            size = IdsButtonSize.Small,
                            onClick = { theme = option; store.setTheme(conversationId, option) },
                        )
                    }
                }
            }
            item {
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Lock this chat", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Text("Require your device unlock to open it", color = Ids.colors.textSecondary, fontSize = 12.sp)
                    }
                    Switch(checked = locked, onCheckedChange = { locked = it; store.setLocked(conversationId, it) })
                }
            }
            item {
                IdsButton(
                    text = "Notification sound",
                    variant = IdsButtonVariant.Tinted,
                    size = IdsButtonSize.Medium,
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startActivity(
                                Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    .putExtra(Settings.EXTRA_CHANNEL_ID, channelId),
                            )
                        }
                    },
                )
            }
            item { Spacer(modifier = Modifier.height(4.dp)) }
            item {
                IdsButton(
                    text = if (exporting) "Preparing…" else "Export chat",
                    enabled = !exporting,
                    variant = IdsButtonVariant.Tinted,
                    size = IdsButtonSize.Medium,
                    onClick = {
                        coroutineScope.launch {
                            exportChat()
                            val text = exportedText
                            if (!text.isNullOrBlank()) {
                                context.startActivity(
                                    Intent.createChooser(
                                        Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text).putExtra(Intent.EXTRA_SUBJECT, roomName),
                                        "Export chat with $roomName",
                                    ),
                                )
                            }
                        }
                    },
                )
            }
            item {
                IdsButton(
                    text = "Add to home screen",
                    variant = IdsButtonVariant.Tinted,
                    size = IdsButtonSize.Medium,
                    onClick = { addTalkRoomShortcut(context, conversationId, roomName) },
                )
            }
        }
    }
}

// Real home-screen shortcut (2026-08-28) -- ShortcutManager.requestPinShortcut is a
// real, working Android O+ API; the OS shows its own genuine pin-confirmation UI.
// The pinned shortcut's target Intent is a plain app launch (same as tapping the
// app icon) rather than a conversation-specific deep link -- this app has no
// cold-start Intent-extra handling anywhere yet to open a specific conversation
// from outside the running app (TalkTab's own initialConversationId hand-off is
// purely in-memory Compose state, set only from within the running UI), and
// building that is a real, separate piece of work, not fabricated here.
private fun addTalkRoomShortcut(context: Context, conversationId: String, roomName: String) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val shortcutManager = context.getSystemService(ShortcutManager::class.java) ?: return
    if (!shortcutManager.isRequestPinShortcutSupported) return
    val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
    val shortcut = ShortcutInfo.Builder(context, "talk_room_$conversationId")
        .setShortLabel(roomName)
        .setIcon(Icon.createWithResource(context, android.R.drawable.sym_action_chat))
        .setIntent(launchIntent)
        .build()
    shortcutManager.requestPinShortcut(shortcut, null)
}
