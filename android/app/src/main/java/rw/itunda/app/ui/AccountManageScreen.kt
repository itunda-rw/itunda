package rw.itunda.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlinx.coroutines.launch
import rw.itunda.app.R
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.components.FlatRow
import rw.itunda.core.designsystem.components.FlatSection
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
// Real cross-platform drift found and fixed live (2026-09-12, re-audit against
// a fuller Toss Manage-screen screenshot batch): this file had "Scheduled
// transfers" but was missing "Delayed transfers" (Toss's real 지연이체
// anti-phishing hold) entirely, even though DelayedTransferListScreen already
// existed and was already wired elsewhere (TransferHubScreen). Both rows are
// now present -- they're genuinely distinct real itunda features, not a
// rename.
//
// Real new features added in the same re-audit (2026-09-12): "Account nickname"
// (a real, user-editable label, see AccountService.setNickname's own doc comment
// on the backend -- distinct from the fixed, system-assigned accountName) and
// "Change password" (PUT /api/v1/auth/pin already existed and already covered
// exactly this case -- see AuthService.setPin's own doc comment -- but had no
// Manage-screen entry point on any platform until now). Mirrors web's
// AccountManageScreen.tsx AccountNicknameScreen/ChangePasswordScreen exactly.
//
// Real, named, deliberately NOT built here (matching AccountManageScreen.tsx's own
// disclosure) -- these are genuinely Korea-specific banking infrastructure/
// regulation (Open Banking/firm banking, tax-free limits, telecom fraud-sharing,
// ATM limits, Credit Information Usage Policy) or a real itunda gap sized like its
// own feature (primary-account designation, self-service account closing) -- not
// silently dropped, see the disclosure text at the bottom.
@Composable
fun AccountManageScreen(
    accountId: String,
    accountNumber: String,
    nickname: String?,
    onNicknameChanged: (String?) -> Unit,
    onBack: () -> Unit,
    onOpenCard: () -> Unit,
    onOpenDevices: () -> Unit,
    onOpenInterestJar: () -> Unit,
    onOpenAutoTransfer: () -> Unit,
    onOpenScheduledTransfers: () -> Unit,
    onOpenDelayedTransfers: () -> Unit,
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
    var showNickname by remember { mutableStateOf(false) }
    var showChangePassword by remember { mutableStateOf(false) }

    if (showVerification) {
        VerificationMethodScreen(onBack = { showVerification = false })
        return
    }
    if (showTransferLimit) {
        TransferLimitScreen(onBack = { showTransferLimit = false })
        return
    }
    if (showNickname) {
        AccountNicknameScreen(
            accountId = accountId,
            currentNickname = nickname,
            onBack = { showNickname = false },
            onSaved = { updated -> onNicknameChanged(updated); showNickname = false },
        )
        return
    }
    if (showChangePassword) {
        ChangePasswordScreen(onBack = { showChangePassword = false })
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
                FlatRow(title = stringResource(R.string.account_manage_nickname), showChevron = true, onClick = { showNickname = true }),
            ))
            SectionSpacer()
            FlatSection(title = stringResource(R.string.account_manage_section_security), rows = listOf(
                FlatRow(title = stringResource(R.string.account_manage_devices), showChevron = true, onClick = onOpenDevices),
                FlatRow(title = stringResource(R.string.account_manage_verification_method), showChevron = true, onClick = { showVerification = true }),
                FlatRow(title = stringResource(R.string.account_manage_change_password), showChevron = true, onClick = { showChangePassword = true }),
            ))
            SectionSpacer()
            FlatSection(title = stringResource(R.string.account_manage_section_transfer), rows = listOf(
                FlatRow(title = stringResource(R.string.account_manage_auto_transfer), showChevron = true, onClick = onOpenAutoTransfer),
                FlatRow(title = stringResource(R.string.account_manage_scheduled_transfers), showChevron = true, onClick = onOpenScheduledTransfers),
                FlatRow(title = stringResource(R.string.account_manage_delayed_transfers), showChevron = true, onClick = onOpenDelayedTransfers),
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
                ManageDetailRow(stringResource(R.string.transfer_limit_per_transfer), String.format(Locale.US, "%,.0f RWF", current.perTransferLimit))
                ManageDetailRow(stringResource(R.string.transfer_limit_daily), String.format(Locale.US, "%,.0f RWF", current.dailyLimit))
                ManageDetailRow(stringResource(R.string.transfer_limit_remaining_today), String.format(Locale.US, "%,.0f RWF", current.remainingToday), Ids.colors.textBrand)
            }
        }
    }
}

// Real "Account nickname" screen (2026-09-12, "계좌 별명" -- direct user-supplied
// Toss Bank Manage-screen screenshots) -- see AccountService.setNickname's own
// doc comment on the backend. A blank submission clears it back to unset,
// matching the backend's own convention. Mirrors web's AccountNicknameScreen.
@Composable
private fun AccountNicknameScreen(accountId: String, currentNickname: String?, onBack: () -> Unit, onSaved: (String?) -> Unit) {
    var input by remember { mutableStateOf(currentNickname ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val genericError = stringResource(R.string.account_nickname_error)

    ManageSubScreen(title = stringResource(R.string.account_manage_nickname), onBack = onBack) {
        rw.itunda.core.designsystem.components.IdsTextField(
            value = input,
            onValueChange = { if (it.length <= 50) input = it },
            label = stringResource(R.string.account_manage_nickname),
            placeholder = stringResource(R.string.account_nickname_placeholder),
            isError = error != null,
            errorText = error,
            modifier = Modifier.fillMaxWidth(),
        )
        Box(modifier = Modifier.padding(top = 12.dp)) {
            rw.itunda.core.designsystem.components.IdsButton(
                text = stringResource(if (busy) R.string.account_nickname_saving else R.string.account_nickname_save),
                enabled = !busy,
                onClick = {
                    busy = true
                    error = null
                    scope.launch {
                        try {
                            val response = NetworkClient.apiService.setAccountNickname(
                                accountId,
                                rw.itunda.core.network.SetAccountNicknameRequest(nickname = input.trim()),
                            )
                            onSaved(response.account.nickname)
                        } catch (e: Exception) {
                            error = e.message ?: genericError
                        } finally {
                            busy = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Text(
            stringResource(R.string.account_nickname_footnote),
            color = Ids.colors.textTertiary, fontSize = 11.sp,
            modifier = Modifier.padding(top = 16.dp, bottom = 24.dp),
        )
    }
}

private enum class ChangePasswordStep { CREDENTIAL, PIN, CONFIRM, SUCCESS }

// Real "Change password" screen (2026-09-12, direct user-supplied Toss Bank
// Manage-screen screenshots) -- PUT /api/v1/auth/pin (ApiService.setAccountPin)
// already exists and already covers exactly this case (re-proving the current
// credential to set a new one); reuses PinScreen.kt's exact PinDots/PinKeypad
// components, the same shape as first-time PIN setup, minus its one-time-only
// upgrade gate -- this is a general "change it again" flow reachable any time
// from here. Mirrors web's ChangePasswordScreen exactly.
@Composable
private fun ChangePasswordScreen(onBack: () -> Unit) {
    var step by remember { mutableStateOf(ChangePasswordStep.CREDENTIAL) }
    var currentCredential by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var pinInput by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val shakeOffset = remember { Animatable(0f) }
    val mismatchError = stringResource(R.string.change_password_mismatch)
    val genericError = stringResource(R.string.change_password_error)

    fun onDigit(digit: String) {
        if (pinInput.length >= 6) return
        error = null
        pinInput += digit
        if (pinInput.length == 6) {
            when (step) {
                ChangePasswordStep.PIN -> {
                    newPin = pinInput
                    pinInput = ""
                    step = ChangePasswordStep.CONFIRM
                }
                ChangePasswordStep.CONFIRM -> {
                    if (pinInput != newPin) {
                        error = mismatchError
                        scope.launch {
                            shakeOffset.animateTo(16f, animationSpec = androidx.compose.animation.core.tween(60))
                            shakeOffset.animateTo(-16f, animationSpec = androidx.compose.animation.core.tween(60))
                            shakeOffset.animateTo(0f, animationSpec = androidx.compose.animation.core.tween(60))
                        }
                        pinInput = ""
                        step = ChangePasswordStep.PIN
                    } else {
                        val confirmed = pinInput
                        pinInput = ""
                        busy = true
                        scope.launch {
                            try {
                                NetworkClient.authApi.setAccountPin(
                                    rw.itunda.core.network.SetAccountPinRequest(currentCredential = currentCredential, newPin = confirmed),
                                )
                                step = ChangePasswordStep.SUCCESS
                            } catch (e: Exception) {
                                error = e.message ?: genericError
                                step = ChangePasswordStep.CREDENTIAL
                            } finally {
                                busy = false
                            }
                        }
                    }
                }
                else -> Unit
            }
        }
    }

    ManageSubScreen(title = stringResource(R.string.account_manage_change_password), onBack = onBack) {
        when (step) {
            ChangePasswordStep.SUCCESS -> {
                Text(stringResource(R.string.change_password_success), color = Ids.colors.textBrand, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
                Box(modifier = Modifier.padding(top = 12.dp)) {
                    rw.itunda.core.designsystem.components.IdsButton(
                        text = stringResource(R.string.change_password_done),
                        variant = rw.itunda.core.designsystem.components.IdsButtonVariant.Tinted,
                        onClick = onBack,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            ChangePasswordStep.CREDENTIAL -> {
                rw.itunda.core.designsystem.components.IdsTextField(
                    value = currentCredential,
                    onValueChange = { currentCredential = it; error = null },
                    label = stringResource(R.string.change_password_current_placeholder),
                    isPassword = true,
                    isError = error != null,
                    errorText = error,
                    modifier = Modifier.fillMaxWidth(),
                )
                Box(modifier = Modifier.padding(top = 12.dp)) {
                    rw.itunda.core.designsystem.components.IdsButton(
                        text = stringResource(R.string.change_password_continue),
                        enabled = currentCredential.isNotBlank(),
                        onClick = { step = ChangePasswordStep.PIN },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            ChangePasswordStep.PIN, ChangePasswordStep.CONFIRM -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)) {
                    Text(
                        stringResource(if (step == ChangePasswordStep.PIN) R.string.change_password_new_label else R.string.change_password_confirm_label),
                        color = Ids.colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    )
                    if (error != null) {
                        Text(error!!, color = Ids.colors.danger, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                    Box(modifier = Modifier.padding(top = 20.dp)) {
                        PinDots(filledCount = pinInput.length, offsetX = shakeOffset.value)
                    }
                }
                if (!busy) {
                    PinKeypad(onDigit = ::onDigit, onBackspace = { if (pinInput.isNotEmpty()) { pinInput = pinInput.dropLast(1); error = null } })
                }
            }
        }
    }
}
