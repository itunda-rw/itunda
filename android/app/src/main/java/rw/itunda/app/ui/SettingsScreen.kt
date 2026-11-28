package rw.itunda.app.ui

import androidx.compose.foundation.background
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.components.LocalRealActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.app.R
import rw.itunda.core.network.AppLocalePreference
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.SessionManager
import rw.itunda.core.network.NotificationDto
import kotlinx.coroutines.launch
import android.widget.Toast
import rw.itunda.core.network.TextScaleOption
import rw.itunda.core.network.TextScalePreference
import rw.itunda.core.network.ThemeMode
import rw.itunda.core.network.ThemePreference
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.designsystem.theme.IdsIcons
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
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onOpenSend: () -> Unit = {},
    onOpenPay: () -> Unit = {},
) {
    val profile by viewModel.profile.collectAsState()
    val devices by viewModel.devices.collectAsState()
    val deviceError by viewModel.deviceError.collectAsState()
    val baseContext = LocalContext.current
    val locale by AppLocalePreference.locale.collectAsState()
    val rootTextScale by TextScalePreference.option.collectAsState()
    val scope = rememberCoroutineScope()

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
    var showSecurityScreen by remember { mutableStateOf(false) }
    var pinCheckIndex by remember { mutableStateOf(0) }
    var quickSignInEnabled by remember { mutableStateOf(true) }
    var showNotificationSettings by remember { mutableStateOf(false) }
    // Real PIN app-unlock setup (2026-08-13) -- see TokenStore.setPin's own doc
    // comment and PinScreen.kt. hasPin is local @Composable state (not re-read from
    // TokenStore on every recomposition) so the "Change PIN" label updates the
    // instant a PIN is set, without needing this whole screen to reload.
    var showPinSetup by remember { mutableStateOf(false) }
    val pinTokenStore = remember { NetworkClient.currentTokenStore() }
    var hasPin by remember { mutableStateOf(pinTokenStore.hasPin()) }
    // Real itunda-branded legal document bodies (2026-08-30) -- see
    // LegalDocumentScreen's own doc comment for the full sourced account of why
    // these 3 rows were inert until now.
    var openLegalDocument by remember { mutableStateOf<Pair<String, String>?>(null) }
    openLegalDocument?.let { (id, label) ->
        LegalDocumentScreen(documentId = id, fallbackTitle = label, onBack = { openLegalDocument = null })
        return
    }
    if (showDeviceList) {
        DeviceListScreen(
            devices = devices,
            onRevoke = { deviceId -> viewModel.revokeDeviceFromSettings(deviceId) },
            onBack = { showDeviceList = false },
            error = deviceError,
        )
        return
    }
    if (showNotificationSettings) {
        NotificationSettingsScreen(onBack = { showNotificationSettings = false })
        return
    }
    if (showPinSetup) {
        PinSetupScreen(
            onBack = { showPinSetup = false },
            onPinSet = { pin ->
                pinTokenStore.setPin(pin)
                pinTokenStore.setAppLockEnabled(true)
                hasPin = true
                showPinSetup = false
            },
        )
        return
    }

    if (showSecurityScreen) {
        val activity = LocalRealActivity.current
        val biometricAvailable = remember(activity) { NIDABiometricAuth(activity).isAvailable() }
        val tokenStore = remember { NetworkClient.currentTokenStore() }
        var appLockEnabled by remember { mutableStateOf(tokenStore.isAppLockEnabled()) }
        val deviceKeyManager = remember { DeviceKeyManager() }
        var hasDeviceKey by remember { mutableStateOf(deviceKeyManager.hasKey()) }
        rw.itunda.feature.my.impl.SettingsSecurityScreen(
            language = locale,
            pinCheckIndex = pinCheckIndex,
            onPinCheckIndexChanged = { pinCheckIndex = (pinCheckIndex + 1) % 3 },
            quickSignInEnabled = quickSignInEnabled,
            onQuickSignInChanged = { quickSignInEnabled = it },
            biometricAvailable = biometricAvailable,
            appLockEnabled = appLockEnabled,
            onAppLockChanged = { checked ->
                if (checked && !hasPin) {
                    showPinSetup = true
                } else {
                    appLockEnabled = checked
                    tokenStore.setAppLockEnabled(checked)
                }
            },
            hasDeviceKey = hasDeviceKey,
            onRemoveDeviceKey = {
                deviceKeyManager.removeKey()
                hasDeviceKey = false
            },
            onRegisterDeviceKey = { password, onResult ->
                try {
                    val publicKey = deviceKeyManager.generateKeyPair()
                    viewModel.registerDeviceKey(publicKey, password) { success, error ->
                        if (success) {
                            hasDeviceKey = true
                        } else {
                            deviceKeyManager.removeKey()
                            hasDeviceKey = false
                        }
                        onResult(success, error)
                    }
                } catch (error: Exception) {
                    deviceKeyManager.removeKey()
                    onResult(false, error.message)
                }
            },
            onChangePin = { showPinSetup = true },
            onOpenDevices = { showDeviceList = true },
            onBack = { showSecurityScreen = false },
        )
        return
    }

    // Real Toss/Kakao pull-to-refresh (2026-08-12 research pass) -- same real
    // mechanics as ItundaAppScreen.kt's own Home-tab implementation, applied here
    // since Settings' own notifications list is exactly the kind of live, changing
    // data itunda's pull-to-refresh research already named as the reason Home got
    // this gesture in the first place.
    // Theme and text size open as full-screen pickers (prototype), applying on tap.
    var showThemeDialog by remember { mutableStateOf(false) }
    if (showThemeDialog) {
        val themeMode by ThemePreference.mode.collectAsState()
        SettingsPickerScreen(
            title = stringResource(R.string.settings_theme_vibration),
            options = listOf(
                ThemeMode.SYSTEM to stringResource(R.string.settings_theme_system),
                ThemeMode.LIGHT to stringResource(R.string.settings_theme_light),
                ThemeMode.DARK to stringResource(R.string.settings_theme_dark),
            ).map { (mode, label) -> PickerOption(label, themeMode == mode) { ThemePreference.set(mode) } },
            onBack = { showThemeDialog = false },
        )
        return
    }
    var showLanguagePicker by remember { mutableStateOf(false) }
    if (showLanguagePicker) {
        rw.itunda.feature.my.impl.SettingsLanguageScreen(
            selectedLanguage = locale,
            onLanguageSelected = { AppLocalePreference.set(baseContext, it) },
            onBack = { showLanguagePicker = false },
        )
        return
    }
    var showTextScaleDialog by remember { mutableStateOf(false) }
    if (showTextScaleDialog) {
        SettingsPickerScreen(
            title = stringResource(R.string.settings_text_size),
            options = TextScaleOption.values().map { o -> PickerOption(o.label, rootTextScale == o, 17f * o.multiplier) { TextScalePreference.set(o) } },
            onBack = { showTextScaleDialog = false },
        )
        return
    }

    rw.itunda.feature.my.impl.SettingsHomeScreen(
        profile = profile,
        language = locale,
        textSizeLabel = rootTextScale.label,
        deviceModel = android.os.Build.MODEL,
        deviceCount = devices.size,
        sendLimitLabel = "500,000 RWF",
        payLabel = "Pay balance",
        moneyNotificationsEnabled = isNotificationChannelEnabled(baseContext, rw.itunda.app.push.NotificationChannels.CHANNEL_MONEY),
        generalNotificationsEnabled = isNotificationChannelEnabled(baseContext, rw.itunda.app.push.NotificationChannels.CHANNEL_GENERAL),
        onMoneyNotificationsClick = { showNotificationSettings = true },
        onGeneralNotificationsClick = { showNotificationSettings = true },
        onBack = onBack,
        onOpenDisplay = { showThemeDialog = true },
        onOpenTextSize = { showTextScaleDialog = true },
        onOpenLanguage = { showLanguagePicker = true },
        onOpenManageContacts = {
            Toast.makeText(
                baseContext,
                when (locale) {
                    "rw" -> "Gucunga abo uziranye ntibiraboneka."
                    "fr" -> "La gestion des contacts n'est pas encore disponible."
                    else -> "Contact management isn't available in this build yet."
                },
                Toast.LENGTH_SHORT,
            ).show()
        },
        onOpenNotifications = { showNotificationSettings = true },
        onOpenSecurity = { showSecurityScreen = true },
        onOpenDevices = { showDeviceList = true },
        onOpenSendLimits = onOpenSend,
        onOpenPay = onOpenPay,
        onMarkAllRead = { viewModel.markAllNotificationsRead() },
        onOpenLegal = { documentId ->
            val title = when (documentId) {
                "credit_data_policy" -> baseContext.getString(R.string.settings_credit_data_policy)
                "privacy_policy" -> baseContext.getString(R.string.settings_privacy_policy)
                else -> baseContext.getString(R.string.settings_terms_consent)
            }
            openLegalDocument = documentId to title
        },
        onSignOut = {
            scope.launch {
                SessionManager.logout()
                onBack()
            }
        },
    )
}

private fun isNotificationChannelEnabled(
    context: android.content.Context,
    channelId: String,
): Boolean {
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
        val manager = context.getSystemService(android.app.NotificationManager::class.java) ?: return false
        return manager.getNotificationChannel(channelId)?.importance != android.app.NotificationManager.IMPORTANCE_NONE
    }
    return androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
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
                    modifier = Modifier.pressScaleClickable(onClick = onMarkAllRead),
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
internal fun SettingsSubScreenHeader(title: String, onBack: () -> Unit, trailing: @Composable () -> Unit = {}) {
    // Prototype `.bar`: 70dp, 48dp back at x=6, 20sp title inline at x=58 (re-measured 2026-11-23; the old
    // "back arrow on its own row + 28sp title" layout did not match any prototype sub-screen).
    Row(Modifier.fillMaxWidth().height(70.dp).padding(start = 6.dp, end = 12.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).pressScaleClickable(onClick = onBack), contentAlignment = Alignment.Center) {
            rw.itunda.core.designsystem.components.ItundaArt("back", rw.itunda.core.designsystem.components.ItundaArtSet.Line, 24.dp, tint = Ids.colors.textPrimary)
        }
        Text(title, fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp, color = Ids.colors.textPrimary, modifier = Modifier.padding(start = 4.dp).weight(1f))
        trailing()
    }
}

// Real Toss card container (2026-08-12) -- see this screen's own doc comment for the
// full account of why: a real, rounded, surface-colored group per section, matching
// the actual Toss Settings screenshot's own card-list layout instead of one
// continuous scrolling list with inline dividers between sections.
@Composable
internal fun DeviceListScreen(
    devices: List<rw.itunda.core.network.TrustedDeviceDto>,
    onRevoke: (String) -> Unit,
    onBack: () -> Unit,
    error: String? = null,
) {
    Column(modifier = Modifier.fillMaxSize().background(Ids.colors.background)) {
        SettingsSubScreenHeader(stringResource(R.string.settings_devices), onBack)
        error?.let { Text(it, color = Ids.colors.danger, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)) }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            items(devices, key = { it.deviceId }) { device ->
                DeviceRow(device, onRevoke = { onRevoke(device.deviceId) })
            }
            item {
                // Real bulk action: revokes every other device one by one through the same endpoint.
                val thisId = remember { NetworkClient.currentDeviceStore().getOrCreateDeviceId() }
                val others = devices.filter { it.deviceId != thisId }
                if (others.isNotEmpty()) {
                    Box(
                        Modifier.padding(top = 16.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Ids.colors.brand.copy(alpha = 0.12f))
                            .pressScaleClickable { others.forEach { onRevoke(it.deviceId) } }.padding(vertical = 18.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(stringResource(R.string.settings_sign_out_others), color = Ids.colors.textBrand, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                }
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
        Text("Choose what itunda can notify you about.", color = Ids.colors.textTertiary, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp))
        listOf(
            Triple(stringResource(R.string.settings_channel_money), "Money in, money out, bills", rw.itunda.app.push.NotificationChannels.CHANNEL_MONEY),
            Triple(stringResource(R.string.settings_channel_general), "Offers, community and map updates", rw.itunda.app.push.NotificationChannels.CHANNEL_GENERAL),
        ).forEach { (title, sub, channel) ->
            Row(Modifier.fillMaxWidth().pressScaleClickable { context.startActivity(channelSettingsIntent(context, channel)) }.padding(horizontal = 24.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, color = Ids.colors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text(sub, color = Ids.colors.textTertiary, fontSize = 14.sp)
                }
                Text("Manage", color = Ids.colors.textBrand, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// Real Toss card container (2026-08-12) -- see this screen's own doc comment for the
// full account of why: a real, rounded, surface-colored group per section, matching
// the actual Toss Settings screenshot's own card-list layout instead of one
// continuous scrolling list with inline dividers between sections.

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth(), content = content)
}

// Plain chevron row (2026-08-12) -- the real Toss Settings screenshot's second row
// pattern: no icon box, just a label and a trailing chevron, used for rows that
// simply navigate elsewhere (Send/Pay) rather than toggle something in place.
@Composable
private fun SettingsChevronRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().pressScaleClickable(onClick = onClick).padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Ids.colors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Icon(IdsIcons.ChevronRight, contentDescription = null, tint = Ids.colors.textTertiary)
    }
}

// Real device management row (2026-07-21 port) -- mirrors bank-mfe's Devices tab row
// exactly: name/label, trusted-vs-untrusted status, and a real "Remove" action.
@Composable
private fun DeviceRow(device: rw.itunda.core.network.TrustedDeviceDto, onRevoke: () -> Unit) {
    val isThisDevice = remember { device.deviceId == NetworkClient.currentDeviceStore().getOrCreateDeviceId() }
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                (device.deviceName ?: stringResource(R.string.settings_unknown_device)) + if (isThisDevice) stringResource(R.string.settings_this_device_suffix) else "",
                color = Ids.colors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
            )
            Text(
                if (device.trusted) stringResource(R.string.settings_trusted) else stringResource(R.string.settings_not_verified),
                color = if (device.trusted) Ids.colors.textTertiary else Ids.colors.danger, fontSize = 13.sp,
            )
        }
        // The device you are using is never removable from here (prototype shows no button on it).
        if (!isThisDevice) {
            Box(
                Modifier.clip(RoundedCornerShape(16.dp)).background(Ids.colors.surfaceSoft).pressScaleClickable(onClick = onRevoke).padding(horizontal = 18.dp, vertical = 12.dp),
            ) { Text(stringResource(R.string.settings_remove), color = Ids.colors.danger, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun NotificationRow(notification: NotificationDto, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressScaleClickable(enabled = !notification.isRead, onClick = onClick)
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
            Icon(IdsIcons.Bell, contentDescription = null, modifier = Modifier.size(16.dp), tint = Ids.colors.textPrimary)
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
