package rw.itunda.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.app.R
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.designsystem.theme.IdsIcons
import rw.itunda.core.network.NetworkClient

// Real Toss Bank 관리 (Manage) screen (2026-09-01, direct user-supplied Toss
// screenshots of that exact screen) -- ports web's own already-built
// AccountManageScreen.tsx (services/micro-frontends/bank-mfe/src/AccountManageScreen.tsx)
// so Android's gear icon opens an account-scoped hub instead of jumping straight
// to the generic app-wide Settings screen (see ItundaAppScreen.kt's own
// onOpenManage wiring for the previous behavior). Every row below routes to an
// already-built, real itunda screen or feature -- never a fabricated destination.
// Android is actually ahead of web/iOS here for two rows web/iOS can't reach
// directly: "Manage devices" deep-links straight to DeviceListScreen (now
// `internal`, ItundaAppScreen.kt's own showDeviceList) instead of web's
// redirect-through-Settings, and "Scheduled transfers" (Toss's real 예약송금) has
// its own top-level screen already (ScheduledTransferListScreen) that this can
// jump straight to.
//
// Real, named, deliberately NOT built here (matching AccountManageScreen.tsx's own
// disclosure) -- these are genuinely Korea-specific banking infrastructure/
// regulation (Open Banking/firm banking, tax-free limits, telecom fraud-sharing,
// ATM limits, Credit Information Usage Policy) or a real itunda gap not rushed
// into a UI pass (changing your account password, an account nickname, closing
// your account) -- not silently dropped, see the disclosure text at the bottom.
@Composable
fun AccountManageScreen(
    accountNumber: String,
    onBack: () -> Unit,
    onOpenCard: () -> Unit,
    onOpenDevices: () -> Unit,
    onOpenInterestJar: () -> Unit,
    onOpenAutoTransfer: () -> Unit,
    onOpenScheduledTransfers: () -> Unit,
    onOpenForeignCurrency: () -> Unit,
    onOpenBills: () -> Unit,
    onOpenSupport: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val formattedAccountNumber = accountNumber.chunked(4).joinToString("-")
    // Real Toss "Verification method"/"Transfer limit" sub-screens (2026-09-01) --
    // both are pure informational reads of data the backend already exposes (see
    // web's own AccountManageScreen.tsx VerificationMethodScreen/TransferLimitScreen),
    // shown inline here rather than plumbed as new top-level ItundaAppScreen.kt
    // navigation state, since neither one deep-links anywhere further.
    var showVerification by remember { mutableStateOf(false) }
    var showTransferLimit by remember { mutableStateOf(false) }

    if (showVerification) {
        VerificationMethodScreen(onBack = { showVerification = false })
        return
    }
    if (showTransferLimit) {
        TransferLimitScreen(onBack = { showTransferLimit = false })
        return
    }

    Column(modifier = Modifier.fillMaxSize().background(Ids.colors.background).verticalScroll(rememberScrollState())) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = Ids.layout.screenHorizontal, vertical = 12.dp)) {
            Box(
                modifier = Modifier.size(Ids.layout.minTouchTarget).clip(CircleShape).pressScaleClickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(IdsIcons.Back, contentDescription = stringResource(R.string.back), modifier = Modifier.size(18.dp), tint = Ids.colors.textPrimary)
            }
        }
        Column(modifier = Modifier.padding(horizontal = Ids.layout.screenHorizontal)) {
            Text(
                stringResource(R.string.account_manage_caption, formattedAccountNumber),
                color = Ids.colors.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(bottom = 24.dp),
            )

            FlatSection(title = stringResource(R.string.account_manage_section_account), rows = listOf(
                FlatRow(title = stringResource(R.string.account_manage_debit_card), showChevron = true, onClick = onOpenCard),
                FlatRow(title = stringResource(R.string.account_manage_interest_earned), showChevron = true, onClick = onOpenInterestJar),
            ))
            SectionSpacer()
            FlatSection(title = stringResource(R.string.account_manage_section_security), rows = listOf(
                FlatRow(title = stringResource(R.string.account_manage_devices), showChevron = true, onClick = onOpenDevices),
                FlatRow(title = stringResource(R.string.account_manage_verification_method), showChevron = true, onClick = { showVerification = true }),
            ))
            SectionSpacer()
            FlatSection(title = stringResource(R.string.account_manage_section_transfer), rows = listOf(
                FlatRow(title = stringResource(R.string.account_manage_auto_transfer), showChevron = true, onClick = onOpenAutoTransfer),
                FlatRow(title = stringResource(R.string.account_manage_scheduled_transfers), showChevron = true, onClick = onOpenScheduledTransfers),
                FlatRow(title = stringResource(R.string.account_manage_transfer_limit), showChevron = true, onClick = { showTransferLimit = true }),
            ))
            SectionSpacer()
            FlatSection(title = stringResource(R.string.account_manage_section_foreign_currency), rows = listOf(
                FlatRow(title = stringResource(R.string.account_manage_exchange_rates), showChevron = true, onClick = onOpenForeignCurrency),
            ))
            SectionSpacer()
            FlatSection(title = stringResource(R.string.account_manage_section_taxes_bills), rows = listOf(
                FlatRow(title = stringResource(R.string.account_manage_pay_taxes_bills), showChevron = true, onClick = onOpenBills),
            ))
            SectionSpacer()
            FlatSection(title = stringResource(R.string.account_manage_section_support), rows = listOf(
                FlatRow(title = stringResource(R.string.account_manage_get_help), showChevron = true, onClick = onOpenSupport),
            ))

            Text(
                stringResource(R.string.account_manage_disclosure),
                color = Ids.colors.textTertiary, fontSize = 11.sp,
                modifier = Modifier.padding(top = 28.dp, bottom = 24.dp),
            )
        }
    }
}

@Composable
private fun SectionSpacer() {
    Box(modifier = Modifier.padding(top = 20.dp))
}

// Shared header for both sub-screens below -- mirrors web's ManageSubScreen.
@Composable
private fun ManageSubScreen(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    BackHandler(onBack = onBack)
    Column(modifier = Modifier.fillMaxSize().background(Ids.colors.background).verticalScroll(rememberScrollState())) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = Ids.layout.screenHorizontal, vertical = 12.dp)) {
            Box(
                modifier = Modifier.size(Ids.layout.minTouchTarget).clip(CircleShape).pressScaleClickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(IdsIcons.Back, contentDescription = stringResource(R.string.back), modifier = Modifier.size(18.dp), tint = Ids.colors.textPrimary)
            }
        }
        Column(modifier = Modifier.padding(horizontal = Ids.layout.screenHorizontal)) {
            Text(title, color = Ids.colors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 20.dp))
            content()
        }
    }
}

@Composable
private fun ManageDetailRow(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color = Ids.colors.textPrimary) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
    ) {
        Text(label, color = Ids.colors.textPrimary, fontSize = 14.sp)
        Text(value, color = valueColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

// Real "Verification method" screen (2026-09-01, direct user-supplied Toss Bank
// Manage-screen screenshot) -- shows exactly two real, already-tracked facts rather
// than a fabricated 2FA-method list: whether the phone on this account is verified
// (PublicUser.phoneVerified) and whether THIS device has completed real biometric/
// passwordless device-key registration (TrustedDeviceDto.publicKey != null for the
// entry matching this device's own real DeviceStore.getOrCreateDeviceId()) -- mirrors
// web's AccountManageScreen.tsx VerificationMethodScreen exactly.
@Composable
private fun VerificationMethodScreen(onBack: () -> Unit) {
    var phoneVerified by remember { mutableStateOf<Boolean?>(null) }
    var deviceVerified by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(Unit) {
        phoneVerified = try { NetworkClient.authApi.getProfile().user.phoneVerified } catch (e: Exception) { null }
        val deviceId = NetworkClient.currentDeviceStore().getOrCreateDeviceId()
        deviceVerified = try {
            NetworkClient.authApi.getMyDevices().devices.any { it.deviceId == deviceId && it.publicKey != null }
        } catch (e: Exception) { null }
    }

    ManageSubScreen(title = stringResource(R.string.account_manage_verification_method), onBack = onBack) {
        val verifiedLabel = stringResource(R.string.verification_method_verified)
        val notVerifiedLabel = stringResource(R.string.verification_method_not_verified)
        val loadingLabel = stringResource(R.string.verification_method_loading)
        val verifiedColor = Ids.colors.textBrand
        val notVerifiedColor = Ids.colors.textSecondary
        fun label(v: Boolean?) = if (v == null) loadingLabel else if (v) verifiedLabel else notVerifiedLabel
        fun color(v: Boolean?) = if (v == true) verifiedColor else notVerifiedColor
        ManageDetailRow(stringResource(R.string.verification_method_phone), label(phoneVerified), color(phoneVerified))
        ManageDetailRow(stringResource(R.string.verification_method_device), label(deviceVerified), color(deviceVerified))
        Text(
            stringResource(R.string.verification_method_footnote),
            color = Ids.colors.textTertiary, fontSize = 11.sp,
            modifier = Modifier.padding(top = 16.dp, bottom = 24.dp),
        )
    }
}

// Real "Transfer limit" screen (2026-09-01) -- the real, enforced
// P2pTransferLimitService caps, previously surfaced only reactively as a decline
// error on an oversized transfer. Mirrors web's TransferLimitScreen exactly.
@Composable
private fun TransferLimitScreen(onBack: () -> Unit) {
    var limit by remember { mutableStateOf<rw.itunda.core.network.TransferLimitResponse?>(null) }
    var error by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try { limit = NetworkClient.apiService.getTransferLimit() } catch (e: Exception) { error = true }
    }

    ManageSubScreen(title = stringResource(R.string.account_manage_transfer_limit), onBack = onBack) {
        val current = limit
        when {
            error -> Text(stringResource(R.string.transfer_limit_error), color = Ids.colors.danger, fontSize = 13.sp, modifier = Modifier.padding(vertical = 12.dp))
            current == null -> Text(stringResource(R.string.transfer_limit_loading), color = Ids.colors.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(vertical = 12.dp))
            else -> {
                ManageDetailRow(stringResource(R.string.transfer_limit_per_transfer), "%,.0f RWF".format(current.perTransferLimit))
                ManageDetailRow(stringResource(R.string.transfer_limit_daily), "%,.0f RWF".format(current.dailyLimit))
                ManageDetailRow(stringResource(R.string.transfer_limit_remaining_today), "%,.0f RWF".format(current.remainingToday), Ids.colors.textBrand)
            }
        }
    }
}
