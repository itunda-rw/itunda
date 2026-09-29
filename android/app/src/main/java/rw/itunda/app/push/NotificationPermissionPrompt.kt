package rw.itunda.app.push

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Real Android 13+ (API 33+) `POST_NOTIFICATIONS` runtime permission request --
 * without it, every real push this app can now send (see `ItundaMessagingService`'s
 * own doc comment) simply never shows, with no crash and no error the user would ever
 * see, which is exactly why this exists as its own explicit flow rather than assuming
 * the manifest `<uses-permission>` declaration alone is enough.
 *
 * Matches Toss's own real onboarding pattern -- a plain OS permission dialog with no
 * context, cold-launched, has a real, well-documented lower opt-in rate than one shown
 * after a short first-party explanation of *why* -- so this shows a real rationale
 * dialog first, and only launches the actual system prompt if the user taps through it.
 * Asked at most once ever (persisted locally): Toss doesn't re-nag every app launch
 * after a user has already made a choice, and neither should this -- a user who denies
 * can still re-enable it later from system Settings, same as any other Android app.
 *
 * Called once from `ItundaAppScreen`, which only renders for an already-logged-in
 * session -- the real contextual moment (right after reaching Home, not before the
 * user has any reason to trust this app yet) rather than at cold app launch.
 */
@Composable
fun NotificationPermissionPrompt() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    val context = LocalContext.current
    val prefs = remember { context.applicationContext.getSharedPreferences("itunda_notification_prompt", Context.MODE_PRIVATE) }
    var showRationale by remember {
        mutableStateOf(
            !prefs.getBoolean(KEY_ASKED, false) &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED,
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        // Outcome (granted or denied) doesn't change what's persisted -- either way,
        // the user made a real choice and this must not ask again.
    }

    LaunchedEffect(Unit) {
        if (!showRationale) prefs.edit().putBoolean(KEY_ASKED, true).apply()
    }

    if (showRationale) {
        AlertDialog(
            onDismissRequest = {
                showRationale = false
                prefs.edit().putBoolean(KEY_ASKED, true).apply()
            },
            title = { Text("Get notified in real time") },
            text = { Text("Itunda can tell you the moment money arrives, a booking updates, or your account needs attention -- turn on notifications to stay in the loop.") },
            confirmButton = {
                TextButton(onClick = {
                    showRationale = false
                    prefs.edit().putBoolean(KEY_ASKED, true).apply()
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }) { Text("Turn on notifications") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showRationale = false
                    prefs.edit().putBoolean(KEY_ASKED, true).apply()
                }) { Text("Not now") }
            },
        )
    }
}

private const val KEY_ASKED = "asked_post_notifications"
