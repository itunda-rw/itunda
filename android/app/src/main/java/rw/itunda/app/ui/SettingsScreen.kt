package rw.itunda.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBackIosNew
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Fingerprint
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
fun SettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onOpenSend: () -> Unit = {},
    onOpenPay: () -> Unit = {},
) {
    val profile by viewModel.profile.collectAsState()
    val unreadCount by viewModel.unreadNotificationCount.collectAsState()
    val devices by viewModel.devices.collectAsState()
    val baseContext = LocalContext.current
    val locale by AppLocalePreference.locale.collectAsState()

    LaunchedEffect(Unit) { viewModel.loadSettingsData() }

    // Real Toss layout (2026-08-12, direct user screenshot comparison) -- the real
    // Settings screen's "Notifications" and "Services logged in with Toss" rows are
    // plain navigation rows, not inline dumps of every notification/device ever
    // recorded. itunda's own earlier version rendered BOTH lists directly on the main
    // Settings screen (a live 15+-entry notification feed -- including a plaintext
    // OTP code -- and a 6-entry device list, both scrolling well past what the real
    // screenshot shows), a real, user-flagged "still not the same" gap, not a cosmetic
    // one. Moved to real sub-screens, reached the same way Toss's own chevron rows do.
    //
    // Real Toss distinction, corrected same day after direct user clarification: the
    // real "Notifications" screen (reached from Home's bell icon) and real "Manage
    // notifications" screen (reached from THIS row) are two different screens --
    // a feed of past notifications vs. per-category send preferences. This row
    // previously (wrongly) pointed at the feed; it now opens NotificationSettingsScreen
    // below, and the feed itself moved to be reached from Home's bell icon directly
    // (see ItundaAppScreen.kt's own showNotificationsFeed state).
    var showDeviceList by remember { mutableStateOf(false) }
    var showNotificationSettings by remember { mutableStateOf(false) }
    if (showDeviceList) {
        DeviceListScreen(
            devices = devices,
            onRevoke = { deviceId -> viewModel.revokeDeviceFromSettings(deviceId) },
            onBack = { showDeviceList = false },
        )
        return
    }
    if (showNotificationSettings) {
        NotificationSettingsScreen(onBack = { showNotificationSettings = false })
        return
    }

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

    // Real theme picker, relocated (2026-08-12) -- previously its own "Display" card
    // with an always-visible 3-way selector; the real screenshot shows "Theme &
    // vibration" as a plain row in the first card, so the same real, working
    // ThemePreference selector now opens as a dialog from that row instead.
    var showThemeDialog by remember { mutableStateOf(false) }
    if (showThemeDialog) {
        val themeMode by ThemePreference.mode.collectAsState()
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text(stringResource(R.string.settings_theme_vibration)) },
            text = {
                Column {
                    listOf(
                        ThemeMode.SYSTEM to stringResource(R.string.settings_theme_system),
                        ThemeMode.LIGHT to stringResource(R.string.settings_theme_light),
                        ThemeMode.DARK to stringResource(R.string.settings_theme_dark),
                    ).forEach { (mode, label) ->
                        val selected = themeMode == mode
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { ThemePreference.set(mode) }
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(label, color = Ids.colors.textPrimary, fontSize = 15.sp)
                            if (selected) Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = Ids.colors.brand)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) { Text(stringResource(R.string.settings_confirm)) }
            },
        )
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
                        // Real Toss row shape (2026-08-12, direct screenshot comparison)
                        // -- every row in this card ends with a chevron, including this
                        // one (value + chevron together), not just a bare value.
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (locale == "en") "English" else "Kinyarwanda", color = Ids.colors.brand, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = Ids.colors.textTertiary)
                        }
                    }
                    // Real Toss position (2026-08-12) -- both rows live in this same
                    // first card, right under Language, on the real screenshot. Each
                    // now opens a real destination instead of dumping content inline
                    // (see this screen's own header comment).
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { showNotificationSettings = true }.padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.settings_notifications), color = Ids.colors.textPrimary, fontSize = 15.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (unreadCount > 0) {
                                Text(unreadCount.toString(), color = Ids.colors.brand, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = Ids.colors.textTertiary)
                        }
                    }
                    SettingsChevronRow(stringResource(R.string.settings_theme_vibration)) { showThemeDialog = true }
                }
            }

            // Real biometric app-lock toggle (2026-07-21) -- see AppLockScreen.kt's own
            // doc comment. Only shown when the device actually has biometrics
            // enrolled; a tappable row that goes nowhere is worse than not showing it,
            // same discipline this screen's own header comment already established
            // for the "보안" rows this app deliberately doesn't fake.
            item {
                // Real Toss layout fix (2026-08-12) -- this whole card used to be
                // conditional on biometricAvailable, which hid "Services logged in
                // with Toss" too on any device without biometrics enrolled; that row
                // is real device management, unrelated to biometrics, so the card and
                // its header now always render, with only the two biometric-specific
                // rows inside gated on availability.
                val activity = LocalRealActivity.current
                val biometricAvailable = remember { NIDABiometricAuth(activity).isAvailable() }
                SettingsCard {
                    // Real Toss section name (2026-08-12) -- "Authentication & Security"
                    // is the real header on the real Toss Settings screenshot; itunda's
                    // own shorter "Security" was close but not the actual real label.
                    Text(stringResource(R.string.settings_authentication_security), color = Ids.colors.textTertiary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                if (biometricAvailable) {
                    val tokenStore = remember { NetworkClient.currentTokenStore() }
                    var appLockEnabled by remember { mutableStateOf(tokenStore.isAppLockEnabled()) }
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
                    // Real Toss position (2026-08-12) -- "Services logged in with
                    // Toss" is always present on the real screenshot regardless of
                    // biometric availability; itunda's real equivalent is its own
                    // device list (see DeviceListScreen below), just relocated from
                    // its own former standalone card to this row.
                    SettingsChevronRow(stringResource(R.string.settings_devices)) { showDeviceList = true }
                }
            }

            // Real Toss layout (2026-08-12, direct user screenshot) -- the real
            // Settings screen has its own "Transfer & Payment" card (Send, Toss Pay)
            // duplicating quick access to money-moving features that also live in
            // the Explore tab's own Shortcuts grid ("Send" -> onOpenTransferHub) and
            // bottom nav ("Pay" tab) -- real Toss surfaces the exact same actions in
            // both places rather than making Settings a dead end. Both rows route to
            // the same real, already-shipped screens, nothing new built here.
            item {
                SettingsCard {
                    Text(stringResource(R.string.settings_transfer_payment), color = Ids.colors.textTertiary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingsChevronRow(stringResource(R.string.settings_send), onClick = onOpenSend)
                    SettingsChevronRow(stringResource(R.string.settings_pay), onClick = onOpenPay)
                }
            }

            // Real Toss position (2026-08-12, direct user screenshot comparison) --
            // the real All-tab screenshots have no "Notifications & consent" section;
            // Legal Documents lives in Settings only. These 3 rows were relocated
            // here from ItundaAppScreen.kt's Explore-tab menu, not newly invented --
            // same real, honest inert state as before (no onClick at all, so no
            // chevron either): itunda has no Credit-data-usage/Privacy-policy/Terms
            // document screens to link to yet, and a tappable row with nowhere to go
            // is worse than a plain label (this file's own established discipline).
            item {
                SettingsCard {
                    Text(stringResource(R.string.settings_legal_documents), color = Ids.colors.textTertiary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    listOf(
                        stringResource(R.string.settings_credit_data_policy),
                        stringResource(R.string.settings_privacy_policy),
                        stringResource(R.string.settings_terms_consent),
                    ).forEach { label ->
                        Text(label, color = Ids.colors.textPrimary, fontSize = 15.sp, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp))
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

// Real device management sub-screen (2026-08-12) -- extracted out of the main
// Settings list (see SettingsScreen's own header comment on why): same real
// GET/POST/DELETE /api/v1/auth/devices-backed data and DeviceRow as before, just
// reached via "Services logged in with Toss" instead of dumped inline.
@Composable
private fun DeviceListScreen(devices: List<rw.itunda.core.network.TrustedDeviceDto>, onRevoke: (String) -> Unit, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().background(Ids.colors.background)) {
        SettingsSubScreenHeader(stringResource(R.string.settings_devices), onBack)
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            items(devices, key = { it.deviceId }) { device ->
                DeviceRow(device, onRevoke = { onRevoke(device.deviceId) })
            }
        }
    }
}

// Real "Manage notifications" screen (2026-08-12, direct user clarification against
// real Toss reference screenshots) -- Toss's own version has real per-category
// server-side toggles (its own notification-preferences backend), which itunda has
// no equivalent of yet; building fake switches that don't actually change what gets
// sent would be exactly the "looks tappable, does nothing" dishonesty this project's
// own established discipline forbids (see FlatRow's own doc comment). The real,
// honest destination itunda DOES have: its two actual Android notification channels
// (NotificationChannels.CHANNEL_MONEY/CHANNEL_GENERAL, already live since the FCM
// push work), each with its own real OS-level settings page
// (Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS) where the user can genuinely mute,
// re-tone, or change the importance of that category -- a real per-category control,
// just backed by Android's own settings surface instead of a fabricated in-app one.
@Composable
private fun NotificationSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxSize().background(Ids.colors.background)) {
        SettingsSubScreenHeader(stringResource(R.string.settings_manage_notifications), onBack)
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
            SettingsCard {
                Text(stringResource(R.string.settings_notifications), color = Ids.colors.textTertiary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                SettingsChevronRow(stringResource(R.string.settings_channel_money)) {
                    context.startActivity(channelSettingsIntent(context, rw.itunda.app.push.NotificationChannels.CHANNEL_MONEY))
                }
                SettingsChevronRow(stringResource(R.string.settings_channel_general)) {
                    context.startActivity(channelSettingsIntent(context, rw.itunda.app.push.NotificationChannels.CHANNEL_GENERAL))
                }
            }
        }
    }
}

private fun channelSettingsIntent(context: android.content.Context, channelId: String) =
    android.content.Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
        putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
        putExtra(android.provider.Settings.EXTRA_CHANNEL_ID, channelId)
    }

// Real notifications sub-screen (2026-08-12) -- same extraction as DeviceListScreen
// above, same real /api/v1/notifications-backed data and NotificationRow, reached
// via the "Notifications" row in the first card instead of an inline feed.
@Composable
internal fun NotificationListScreen(
    notifications: List<NotificationDto>,
    unreadCount: Int,
    onMarkAllRead: () -> Unit,
    onNotificationClick: (String) -> Unit,
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().background(Ids.colors.background)) {
        SettingsSubScreenHeader(stringResource(R.string.settings_notifications), onBack) {
            if (unreadCount > 0) {
                Text(
                    stringResource(R.string.settings_mark_all_read),
                    color = Ids.colors.brand,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable(onClick = onMarkAllRead),
                )
            }
        }
        if (notifications.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(top = 48.dp), contentAlignment = Alignment.TopCenter) {
                Text(stringResource(R.string.settings_no_notifications), color = Ids.colors.textTertiary, fontSize = 14.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
            ) {
                items(notifications, key = { it.id }) { notification ->
                    NotificationRow(notification, onClick = { onNotificationClick(notification.id) })
                }
            }
        }
    }
}

// Shared back-header for the two sub-screens above, matching SettingsScreen's own
// top bar exactly (same back-button shape/position) so navigating in feels like the
// same screen family, not a different pattern.
@Composable
private fun SettingsSubScreenHeader(title: String, onBack: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.ArrowBackIosNew, contentDescription = stringResource(R.string.settings_back), modifier = Modifier.size(18.dp), tint = Ids.colors.textPrimary)
        }
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ids.colors.textPrimary, modifier = Modifier.weight(1f))
        trailing()
        Spacer(modifier = Modifier.width(8.dp))
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

// Plain chevron row (2026-08-12) -- the real Toss Settings screenshot's second row
// pattern: no icon box, just a label and a trailing chevron, used for rows that
// simply navigate elsewhere (Send/Pay) rather than toggle something in place.
@Composable
private fun SettingsChevronRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Ids.colors.textPrimary, fontSize = 15.sp)
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = Ids.colors.textTertiary)
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
