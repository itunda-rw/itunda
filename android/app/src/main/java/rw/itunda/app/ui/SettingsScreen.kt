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
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.app.R
import rw.itunda.core.network.AppLocalePreference
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
 *
 * Localized 2026-08-08 (docs/DESIGN_REFERENCES.md Section 19) -- the 4th screen in
 * the Kinyarwanda thread, and the only place a logged-in user can reach a language
 * switcher at all, since LoginScreen.kt's own switcher is unreachable once signed
 * in. Same shared rw.itunda.core.network.AppLocalePreference LoginScreen.kt now
 * uses, found and fixed the same day: it used to hold only screen-local state that
 * never propagated past LoginScreen's own subtree.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: MainViewModel, onBack: () -> Unit, onLogout: () -> Unit) {
    val profile by viewModel.profile.collectAsState()
    val notifications by viewModel.notifications.collectAsState()
    val unreadCount by viewModel.unreadNotificationCount.collectAsState()
    val devices by viewModel.devices.collectAsState()
    val baseContext = LocalContext.current
    val locale by AppLocalePreference.locale.collectAsState()

    LaunchedEffect(Unit) { viewModel.loadSettingsData() }

    // Real Toss/Kakao pull-to-refresh (2026-08-12 research pass) -- same real
    // mechanics as ItundaAppScreen.kt's own Home-tab implementation, applied here
    // since Settings' own notifications list is exactly the kind of live, changing
    // data itunda's pull-to-refresh research already named as the reason Home got
    // this gesture in the first place.
    val pullToRefreshState = rememberPullToRefreshState()
    val isLoadingSettings by viewModel.isLoadingSettings.collectAsState()
    LaunchedEffect(pullToRefreshState.isRefreshing) {
        if (pullToRefreshState.isRefreshing) viewModel.loadSettingsData()
    }
    LaunchedEffect(isLoadingSettings) {
        if (!isLoadingSettings) pullToRefreshState.endRefresh()
    }

    Box(Modifier.fillMaxSize().nestedScroll(pullToRefreshState.nestedScrollConnection)) {
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
                Icon(Icons.Outlined.ArrowBackIosNew, contentDescription = stringResource(R.string.settings_back), modifier = Modifier.size(18.dp), tint = Ids.colors.textPrimary)
            }
            Text(
                stringResource(R.string.settings_title),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Ids.colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Real Toss layout (2026-08-12), matching a direct user-provided screenshot
            // of the real Toss app's own Settings screen: separate rounded cards per
            // section (My info / Authentication & Security / Display / Devices /
            // Notifications), not one continuous flat list with inline dividers -- the
            // divider-per-section pattern this screen used before was itunda's own
            // invented layout, not sourced from a real screenshot. "Language" moved
            // here from the top bar's own EN/RW toggle to match the real screenshot's
            // placement (a row inside the first card, right under My info) -- same
              // real, already-working AppLocalePreference, just relocated.
            item {
                SettingsCard {
                    Text(stringResource(R.string.settings_my_info), color = Ids.colors.textTertiary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { AppLocalePreference.set(baseContext, if (locale == "en") "rw" else "en") }
                            .padding(vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.settings_language), color = Ids.colors.textPrimary, fontSize = 15.sp)
                        Text(if (locale == "en") "English" else "Kinyarwanda", color = Ids.colors.brand, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            // Real biometric app-lock toggle (2026-07-21) -- see AppLockScreen.kt's own
            // doc comment. Only shown when the device actually has biometrics
            // enrolled; a tappable row that goes nowhere is worse than not showing it,
            // same discipline this screen's own header comment already established
            // for the "보안" rows this app deliberately doesn't fake.
            item {
                val activity = LocalRealActivity.current
                val biometricAvailable = remember { NIDABiometricAuth(activity).isAvailable() }
                if (biometricAvailable) {
                    val tokenStore = remember { NetworkClient.currentTokenStore() }
                    var appLockEnabled by remember { mutableStateOf(tokenStore.isAppLockEnabled()) }
                    SettingsCard {
                    // Real Toss section name (2026-08-12) -- "Authentication & Security"
                    // is the real header on the real Toss Settings screenshot; itunda's
                    // own shorter "Security" was close but not the actual real label.
                    Text(stringResource(R.string.settings_authentication_security), color = Ids.colors.textTertiary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
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
                            Text(stringResource(R.string.settings_unlock_biometrics), color = Ids.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.settings_unlock_biometrics_body), color = Ids.colors.textTertiary, fontSize = 13.sp)
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
                            Text(stringResource(R.string.settings_verify_biometrics), color = Ids.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.settings_verify_biometrics_body), color = Ids.colors.textTertiary, fontSize = 13.sp)
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
                            title = { Text(stringResource(R.string.settings_confirm_password)) },
                            text = {
                                Column {
                                    Text(
                                        stringResource(R.string.settings_confirm_password_body),
                                        color = Ids.colors.textTertiary,
                                        fontSize = 13.sp,
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = password,
                                        onValueChange = { password = it },
                                        visualTransformation = PasswordVisualTransformation(),
                                        singleLine = true,
                                        label = { Text(stringResource(R.string.settings_password)) },
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
                                ) { Text(if (keyRegistering) stringResource(R.string.settings_verifying) else stringResource(R.string.settings_confirm)) }
                            },
                            dismissButton = {
                                TextButton(onClick = { showKeyPasswordPrompt = false; keyRegisterError = null }) { Text(stringResource(R.string.settings_cancel)) }
                            },
                        )
                    }
                    }
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
                SettingsCard {
                Text(stringResource(R.string.settings_display), color = Ids.colors.textTertiary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
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
                        Text(stringResource(R.string.settings_theme), color = Ids.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.settings_theme_body), color = Ids.colors.textTertiary, fontSize = 13.sp)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(
                        ThemeMode.SYSTEM to stringResource(R.string.settings_theme_system),
                        ThemeMode.LIGHT to stringResource(R.string.settings_theme_light),
                        ThemeMode.DARK to stringResource(R.string.settings_theme_dark),
                    ).forEach { (mode, label) ->
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
                }
            }

            // Real device management (2026-07-21 port) -- see this screen's own header
            // comment. Mirrors bank-mfe's Devices tab: every device this account has
            // ever signed in from, whether it's trusted (can move money) or merely
            // seen, and a real "Remove" action.
            item {
                SettingsCard {
                Text(stringResource(R.string.settings_devices), color = Ids.colors.textTertiary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                devices.forEach { device ->
                    DeviceRow(device, onRevoke = { viewModel.revokeDeviceFromSettings(device.deviceId) })
                }
                }
            }

            item {
                SettingsCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.settings_notifications), color = Ids.colors.textTertiary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    if (unreadCount > 0) {
                        Text(
                            stringResource(R.string.settings_mark_all_read),
                            color = Ids.colors.brand,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable { viewModel.markAllNotificationsRead() }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (notifications.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.settings_no_notifications), color = Ids.colors.textTertiary, fontSize = 14.sp)
                    }
                } else {
                    notifications.forEach { notification ->
                        NotificationRow(notification, onClick = { viewModel.markNotificationRead(notification.id) })
                    }
                }
                }
            }

            // Real Toss layout (2026-08-12) -- "Close Toss account" is its own
            // standalone card at the bottom of the real screenshot, separate from
            // everything above it; itunda's real equivalent account-boundary action is
            // Log out (there's no real account-closure flow to fabricate a destination
            // for), given the same standalone-card treatment.
            item {
                SettingsCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onLogout),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Logout, contentDescription = null, tint = Ids.colors.danger)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(stringResource(R.string.settings_log_out), color = Ids.colors.danger, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
                }
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
    PullToRefreshContainer(state = pullToRefreshState, modifier = Modifier.align(Alignment.TopCenter))
    }
}

// Real Toss card container (2026-08-12) -- see this screen's own doc comment for the
// full account of why: a real, rounded, surface-colored group per section, matching
// the actual Toss Settings screenshot's own card-list layout instead of one
// continuous scrolling list with inline dividers between sections.
@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    androidx.compose.material3.Card(
        shape = RoundedCornerShape(Ids.layout.cardCornerRadius),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = Ids.colors.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp), content = content)
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
                (device.deviceName ?: stringResource(R.string.settings_unknown_device)) + if (isThisDevice) stringResource(R.string.settings_this_device_suffix) else "",
                color = Ids.colors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                if (device.trusted) stringResource(R.string.settings_trusted) else stringResource(R.string.settings_not_verified),
                color = if (device.trusted) Ids.colors.textTertiary else Ids.colors.danger,
                fontSize = 12.sp,
            )
        }
        Text(
            stringResource(R.string.settings_remove),
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
