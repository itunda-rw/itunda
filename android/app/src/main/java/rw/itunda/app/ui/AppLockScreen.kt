package rw.itunda.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.identity.NIDABiometricAuth

/**
 * Real app-launch biometric unlock gate (2026-07-21) -- closes the gap named in
 * docs/DESIGN_REFERENCES.md Section 8: Toss's real login credential is a 6-digit PIN
 * or Face ID, never a conventional password, but itunda's own [NIDABiometricAuth] was
 * only ever wired to a transaction step-up gate (see ItundaAppScreen.kt's transfer
 * confirm), never to opening the app itself. This is pure reuse of that already-real
 * Keystore/BiometricPrompt plumbing -- no new crypto, no schema change, just a new
 * call site gating entry into [ItundaAppScreen] once per process launch, shown only
 * when the device actually has biometrics enrolled and the user hasn't disabled it in
 * Settings (see TokenStore.isAppLockEnabled).
 *
 * Deliberately a single auto-triggered prompt, not a typed PIN field -- itunda has no
 * real PIN storage anywhere, and building a second, weaker credential store solely for
 * this screen would be a fake-security shortcut this project's own SECURITY.md
 * standard doesn't allow. A device with no biometrics enrolled skips this gate
 * entirely (checked by the caller) rather than blocking a real user out of the app.
 */
@Composable
fun AppLockScreen(activity: FragmentActivity, onUnlocked: () -> Unit) {
    val biometricAuth = remember(activity) { NIDABiometricAuth(activity) }
    var error by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    // Real Android platform haptics guidance (developer.android.com/develop/ui/views/
    // haptics/haptics-principles), not just Toss-sourced this time -- "fingerprint
    // acceptance or rejection" is named as one of the canonical moments haptic
    // feedback belongs. This is the single highest-frequency real interaction in the
    // whole app (runs on every cold launch when app-lock is enabled) and had zero
    // haptic feedback of any kind before this. HapticFeedbackType.Confirm/Reject
    // aren't available at this project's pinned Compose UI version (same constraint
    // IdsCelebrationScreen's own doc comment already established) -- LongPress reads
    // as a single confident buzz for success, same as it does there.
    val haptics = LocalHapticFeedback.current

    fun attemptUnlock() {
        error = null
        checking = true
        biometricAuth.authenticateForTransaction(reason = "Unlock Itunda") { success, message ->
            checking = false
            if (success) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onUnlocked()
            } else {
                error = message
            }
        }
    }

    // Auto-trigger once on first composition -- matching Face ID's own real-world
    // convention (prompt appears immediately, not behind an extra tap) rather than
    // making a real user tap an extra "Unlock" button just to see the prompt.
    LaunchedEffect(Unit) { attemptUnlock() }

    Box(
        modifier = Modifier.fillMaxSize().background(Ids.colors.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(
                modifier = Modifier
                    .background(Ids.colors.chip, shape = RoundedCornerShape(28.dp))
                    .padding(20.dp),
            ) {
                Icon(Icons.Outlined.Fingerprint, contentDescription = null, tint = Ids.colors.brand, modifier = Modifier.padding(4.dp))
            }
            Text("Itunda is locked", color = Ids.colors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            if (error != null) {
                Text(error ?: "", color = Ids.colors.danger, fontSize = 14.sp)
            } else {
                Text("Verify your identity to continue", color = Ids.colors.textTertiary, fontSize = 14.sp)
            }
            Button(
                onClick = ::attemptUnlock,
                enabled = !checking,
                colors = ButtonDefaults.buttonColors(containerColor = Ids.colors.brand),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp),
            ) {
                Text(if (checking) "Checking…" else "Unlock")
            }
        }
    }
}
