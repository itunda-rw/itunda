package rw.itunda.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBackIosNew
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.NotificationDto
import rw.itunda.core.network.ThemeMode
import rw.itunda.core.network.ThemePreference
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.identity.DeviceKeyManager
import rw.itunda.core.identity.NIDABiometricAuth

/**
 * Real account settings screen, matching the real Toss reference screenshots
 * (user-provided, 2026-07-12): "내 정보" (my info -- real name/phone from
 * /api/v1/auth/profile), a real notifications list (/api/v1/notifications, with
 * mark-as-read already real on the backend, just never surfaced anywhere), and
 * logout.
 *
 * Real device management added 2026-07-21 (see the Devices section below) --
 * closes the "not rendered as fake interactive rows" gap this comment used to name:
 * there IS now a real device-binding backend (DeviceService.kt, modeled on Toss's own
 * published Gateway/Passport architecture), already shipped on web (bank-mfe's
 * Devices tab) since 2026-07-20. This is the Android port, same real
 * GET/POST/DELETE /api/v1/auth/devices endpoints, same real list/revoke actions.
 */
@Composable
fun SettingsScreen(viewModel: MainViewModel, onBack: () -> Unit, onLogout: () -> Unit) {
    val profile by viewModel.profile.collectAsState()
    val notifications by viewModel.notifications.collectAsState()
    val unreadCount by viewModel.unreadNotificationCount.collectAsState()
    val devices by viewModel.devices.collectAsState()

    LaunchedEffect(Unit) { viewModel.loadSettingsData() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ids.colors.background)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.ArrowBackIosNew, contentDescription = "Back", modifier = Modifier.size(18.dp), tint = Ids.colors.textPrimary)
            }
            Text("Settings", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ids.colors.textPrimary)
        }

        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
            item {
                Text("My info", color = Ids.colors.textTertiary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(44.dp).clip(CircleShape).background(Ids.colors.chip),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.Person, contentDescription = null, tint = Ids.colors.textPrimary)
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            profile?.let { "${it.firstName} ${it.lastName}" } ?: "—",
                            color = Ids.colors.textPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(profile?.phoneNumber ?: "", color = Ids.colors.textTertiary, fontSize = 14.sp)
                    }
                }
                androidx.compose.material3.Divider(color = Ids.colors.divider)
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Real biometric app-lock toggle (2026-07-21) -- see AppLockScreen.kt's own
            // doc comment. Only shown when the device actually has biometrics
            // enrolled; a tappable row that goes nowhere is worse than not showing it,
            // same discipline this screen's own header comment already established
            // for the "보안" rows this app deliberately doesn't fake.
            item {
                val activity = LocalContext.current as androidx.fragment.app.FragmentActivity
                val biometricAvailable = remember { NIDABiometricAuth(activity).isAvailable() }
                if (biometricAvailable) {
                    val tokenStore = remember { NetworkClient.currentTokenStore() }
                    var appLockEnabled by remember { mutableStateOf(tokenStore.isAppLockEnabled()) }
                    Text("Security", color = Ids.colors.textTertiary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier.size(44.dp).clip(CircleShape).background(Ids.colors.chip),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Outlined.Fingerprint, contentDescription = null, tint = Ids.colors.textPrimary)
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Unlock with biometrics", color = Ids.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Text("Require Face/Fingerprint to open Itunda", color = Ids.colors.textTertiary, fontSize = 13.sp)
                        }
                        Switch(
                            checked = appLockEnabled,
                            onCheckedChange = {
                                appLockEnabled = it
                                tokenStore.setAppLockEnabled(it)
                            },
                            colors = SwitchDefaults.colors(checkedTrackColor = Ids.colors.brand),
                        )
                    }

                    // Real Keystore-signed-challenge device verification (item 246) --
                    // see DeviceKeyManager's own doc comment. A hardware-backed key,
                    // gated behind this same password re-entry cost (registerDeviceKey
                    // enforces it server-side too, never trusting a client-only check),
                    // that turns every FUTURE device step-up (TransferFlow.kt's
                    // DeviceStepUpDialog) into a fingerprint/face prompt instead.
                    val deviceKeyManager = remember { DeviceKeyManager() }
                    var hasDeviceKey by remember { mutableStateOf(deviceKeyManager.hasKey()) }
                    var showKeyPasswordPrompt by remember { mutableStateOf(false) }
                    var keyRegisterError by remember { mutableStateOf<String?>(null) }
                    var keyRegistering by remember { mutableStateOf(false) }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier.size(44.dp).clip(CircleShape).background(Ids.colors.chip),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Outlined.Fingerprint, contentDescription = null, tint = Ids.colors.textPrimary)
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Verify this device with biometrics", color = Ids.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Text("Skip retyping your password for step-up verification", color = Ids.colors.textTertiary, fontSize = 13.sp)
                        }
                        Switch(
                            checked = hasDeviceKey,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    keyRegisterError = null
                                    showKeyPasswordPrompt = true
                                } else {
                                    deviceKeyManager.removeKey()
                                    hasDeviceKey = false
                                }
                            },
                            colors = SwitchDefaults.colors(checkedTrackColor = Ids.colors.brand),
                        )
                    }

                    if (showKeyPasswordPrompt) {
                        var password by remember { mutableStateOf("") }
                        AlertDialog(
                            onDismissRequest = { showKeyPasswordPrompt = false; keyRegisterError = null },
                            title = { Text("Confirm your password") },
                            text = {
                                Column {
                                    Text(
                                        "Enter your password once to enable biometric device verification.",
                                        color = Ids.colors.textTertiary,
                                        fontSize = 13.sp,
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = password,
                                        onValueChange = { password = it },
                                        visualTransformation = PasswordVisualTransformation(),
                                        singleLine = true,
                                        label = { Text("Password") },
                                    )
                                    keyRegisterError?.let {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(it, color = Ids.colors.danger, fontSize = 12.sp)
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(
                                    enabled = password.isNotBlank() && !keyRegistering,
                                    onClick = {
                                        keyRegistering = true
                                        val publicKey = deviceKeyManager.generateKeyPair()
                                        viewModel.registerDeviceKey(publicKey, password) { success, error ->
                                            keyRegistering = false
                                            if (success) {
                                                hasDeviceKey = true
                                                showKeyPasswordPrompt = false
                                            } else {
                                                // Don't leave an unregistered key sitting in the Keystore --
                                                // hasDeviceKey must keep meaning "the server also has this key".
                                                deviceKeyManager.removeKey()
                                                keyRegisterError = error
                                            }
                                        }
                                    },
                                ) { Text(if (keyRegistering) "Verifying…" else "Confirm") }
                            },
                            dismissButton = {
                                TextButton(onClick = { showKeyPasswordPrompt = false; keyRegisterError = null }) { Text("Cancel") }
                            },
                        )
                    }

                    androidx.compose.material3.Divider(color = Ids.colors.divider)
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // Real in-app theme override (2026-08-03) -- see ThemePreference.kt's own
            // doc comment. Fixes a real, reproduced complaint: a phone left in system
            // dark mode makes the whole app render with the dark palette, which reads
            // as "nothing like Toss" against the light reference screenshots this app
            // is built from -- there was no way back to the light look short of
            // changing the phone's own OS-wide setting. Default SYSTEM.
            item {
                val themeMode by ThemePreference.mode.collectAsState()
                Text("Display", color = Ids.colors.textTertiary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier.size(44.dp).clip(CircleShape).background(Ids.colors.chip),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.DarkMode, contentDescription = null, tint = Ids.colors.textPrimary)
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Theme", color = Ids.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text("Match device, or force light/dark", color = Ids.colors.textTertiary, fontSize = 13.sp)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(ThemeMode.SYSTEM to "System", ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark").forEach { (mode, label) ->
                        val selected = themeMode == mode
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) Ids.colors.brand else Ids.colors.chip)
                                .clickable { ThemePreference.set(mode) }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                label,
                                color = if (selected) rw.itunda.core.designsystem.theme.IdsColors.White else Ids.colors.textSecondary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
                androidx.compose.material3.Divider(color = Ids.colors.divider, modifier = Modifier.padding(top = 12.dp))
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Real device management (2026-07-21 port) -- see this screen's own header
            // comment. Mirrors bank-mfe's Devices tab: every device this account has
            // ever signed in from, whether it's trusted (can move money) or merely
            // seen, and a real "Remove" action.
            item {
                Text("Devices", color = Ids.colors.textTertiary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
            }
            items(devices, key = { it.id }) { device ->
                DeviceRow(device, onRevoke = { viewModel.revokeDeviceFromSettings(device.deviceId) })
            }
            item {
                androidx.compose.material3.Divider(color = Ids.colors.divider)
                Spacer(modifier = Modifier.height(16.dp))
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Notifications", color = Ids.colors.textTertiary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    if (unreadCount > 0) {
                        Text(
                            "Mark all read",
                            color = Ids.colors.brand,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable { viewModel.markAllNotificationsRead() }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (notifications.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                        Text("No notifications", color = Ids.colors.textTertiary, fontSize = 14.sp)
                    }
                }
            } else {
                items(notifications, key = { it.id }) { notification ->
                    NotificationRow(notification, onClick = { viewModel.markNotificationRead(notification.id) })
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onLogout)
                        .padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Logout, contentDescription = null, tint = Ids.colors.danger)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Log out", color = Ids.colors.danger, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}

// Real device management row (2026-07-21 port) -- mirrors bank-mfe's Devices tab row
// exactly: name/label, trusted-vs-untrusted status, and a real "Remove" action.
@Composable
private fun DeviceRow(device: rw.itunda.core.network.TrustedDeviceDto, onRevoke: () -> Unit) {
    val isThisDevice = remember { device.deviceId == NetworkClient.currentDeviceStore().getOrCreateDeviceId() }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(44.dp).clip(CircleShape).background(Ids.colors.chip),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.PhoneAndroid, contentDescription = null, tint = Ids.colors.textPrimary)
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                (device.deviceName ?: "Unknown device") + if (isThisDevice) " (this device)" else "",
                color = Ids.colors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                if (device.trusted) "Trusted -- can send money" else "Not verified -- can't send money yet",
                color = if (device.trusted) Ids.colors.textTertiary else Ids.colors.danger,
                fontSize = 12.sp,
            )
        }
        Text(
            "Remove",
            color = Ids.colors.danger,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable(onClick = onRevoke),
        )
    }
}

@Composable
private fun NotificationRow(notification: NotificationDto, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !notification.isRead, onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(if (notification.isRead) Ids.colors.chip else Ids.colors.pressed),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.Notifications, contentDescription = null, modifier = Modifier.size(16.dp), tint = Ids.colors.textPrimary)
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text(
                notification.title,
                color = Ids.colors.textPrimary,
                fontSize = 15.sp,
                fontWeight = if (notification.isRead) FontWeight.Normal else FontWeight.Bold
            )
            Text(notification.body, color = Ids.colors.textTertiary, fontSize = 13.sp)
        }
    }
}
