package rw.itunda.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.ArrowBackIosNew
import androidx.compose.material.icons.outlined.AttachMoney
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.CurrencyExchange
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material.icons.outlined.DynamicFeed
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.HomeWork
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Percent
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PersonAddAlt
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Redeem
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.RequestQuote
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.Swipe
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import rw.itunda.core.designsystem.components.BackTopBar
import rw.itunda.core.designsystem.components.IdsButton
import rw.itunda.feature.talk.impl.TalkTab
import rw.itunda.feature.maps.impl.MapScreen
import rw.itunda.core.designsystem.components.IdsButtonSize
import rw.itunda.core.designsystem.components.IdsButtonVariant
import rw.itunda.core.designsystem.components.IdsIconButton
import rw.itunda.core.designsystem.theme.IdsTheme
import rw.itunda.core.designsystem.theme.Ids

// Aliased to the real theme-reactive design-system tokens (see
// core/designsystem/theme/IdsSemanticColors.kt) rather than the ad-hoc,
// half dark-mode-aware set this file used to hand-roll -- kept as thin
// aliases (not a full rename) since this file's Composables all reference
// these names throughout; the fix was making the values real, not renaming
// every call site.
// internal (not private): SuperAppTabs.kt (Talk/Hood/Shop -- the new
// Messaging/Marketplace/Commerce tabs, 2026-07-18) shares this exact same visual
// language rather than hand-rolling a second palette.
internal val TossBlue: Color
    @Composable get() = Ids.colors.brand
private val TossBackground: Color
    @Composable get() = Ids.colors.background
internal val TossCard: Color
    @Composable get() = Ids.colors.surface
internal val TossCardSoft: Color
    @Composable get() = Ids.colors.surfaceSoft
internal val TossText: Color
    @Composable get() = Ids.colors.textPrimary
internal val TossSecondary: Color
    @Composable get() = Ids.colors.textSecondary
internal val TossTertiary: Color
    @Composable get() = Ids.colors.textTertiary
internal val TossLine: Color
    @Composable get() = Ids.colors.divider
private val TossChip: Color
    @Composable get() = Ids.colors.chip

// Fixed vivid accent colors for the small product-icon badges in
// FlatSection rows (갈아타기/서비스/외화/목돈굴리기/연금/대출 등) -- these are
// brand/product colors in real Toss, not semantic theme colors, so unlike
// TossBlue etc. above they intentionally stay constant across light/dark.
internal val AccentBlue = Color(0xFF3182F6)
internal val AccentTeal = Color(0xFF14AE85)
internal val AccentPurple = Color(0xFF7C5CFC)
internal val AccentOrange = Color(0xFFF2A93B)
internal val AccentRed = Color(0xFFFF5B5B)
private val AccentPink = Color(0xFFEC5F8C)
internal val AccentGray = Color(0xFF6B7684)

// Real super-app bottom nav (2026-07-18): Home/Shop/Hood/Talk/My, replacing the
// previous Home/Benefits/Shop/Pay/All layout now that itunda has real Coupang-style
// commerce (Shop), 당근마켓-style marketplace (Hood), and Kakao-style messaging (Talk)
// backends to put behind top-level tabs. Benefits and Pay lose their own tabs -- both
// were already either fully static/promotional (Benefits) or backed by a dead,
// never-wired QR button (Pay -- HomeTopBar's own scan icon has no onClick either) --
// and are now reachable as real rows inside My instead of losing their reachability
// outright.
internal enum class TossTab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Home("Home", Icons.Outlined.Home),
    Shop("Shop", Icons.Outlined.ShoppingBag),
    Hood("Hood", Icons.Outlined.LocationOn),
    Talk("Talk", Icons.Outlined.Chat),
    // Real 전체 (All services) bottom tab (2026-07-24) -- previously nested two taps
    // deep (Home -> My -> Menu icon), at the user's own direct request to bring it
    // back to a first-class bottom-nav slot now that mini-apps/games need to be one
    // tap away, matching real Toss's own bottom nav (홈/혜택/쇼핑/페이/전체 -- no
    // separate "My" tab at all; personal info lives at the top of 전체 instead). The
    // previous My tab's own real content (orders/favorites/listings tracking, a
    // genuine itunda addition beyond Toss parity) isn't dropped -- it's reachable one
    // tap in via the profile icon at the top of this screen, the exact same nesting
    // this tab used to have with Menu, just inverted.
    All("All", Icons.Outlined.Apps)
}

/**
 * Real top-level navigation for the transfer flow -- a full-screen takeover
 * over the tab scaffold, matching how the reference screenshots show it
 * (no bottom nav visible during recipient/amount entry). Genuinely wired
 * to a visible entry point (WalletHeroCard's "Send" button), not built and
 * left unreachable like the screens it replaces.
 */
// Serializable so rememberSaveable can survive process death mid-flow (2026-07-12)
// -- BiometricPrompt in particular backgrounds the host Activity behind a system
// overlay, which is exactly the condition Android is most likely to reclaim a
// low-priority process under memory pressure. Without this, that reclaim silently
// resets the whole flow to Home with no error shown, even though the confirm call
// (idempotency-key protected) may have already gone through.
private sealed class TransferStep : java.io.Serializable {
    data object Recipient : TransferStep()
    data class Amount(val accountNumber: String) : TransferStep()
}

/** Real savings deposit/claim flow (2026-07-12) -- see SavingsAmountScreen.kt. */
private sealed class SavingsFlowStep : java.io.Serializable {
    data class Deposit(val goalId: String, val goalName: String) : SavingsFlowStep()
    data object ClaimInterest : SavingsFlowStep()
}

@Composable
fun ItundaAppScreen(
    viewModel: MainViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    openMapFromDeepLink: Boolean = false,
    initialMapSearchQuery: String? = null,
    onMapDeepLinkConsumed: () -> Unit = {},
) {
    IdsTheme {
        var selectedTab by rememberSaveable { mutableStateOf(TossTab.Home) }
        var transferStep by rememberSaveable { mutableStateOf<TransferStep?>(null) }
        var savingsFlowStep by rememberSaveable { mutableStateOf<SavingsFlowStep?>(null) }
        var showTransactionHistory by rememberSaveable { mutableStateOf(false) }
        var showSettings by rememberSaveable { mutableStateOf(false) }
        var showBenefits by rememberSaveable { mutableStateOf(false) }
        var showPay by rememberSaveable { mutableStateOf(false) }
        var showMap by rememberSaveable { mutableStateOf(false) }
        var mapSearchQueryForScreen by rememberSaveable { mutableStateOf<String?>(null) }
        var showAgentCash by rememberSaveable { mutableStateOf(false) }
        var showInvest by rememberSaveable { mutableStateOf(false) }
        // Real Overview/Loans/Support screens (2026-07-22) -- these three backend
        // modules (rw.itunda.overview, rw.itunda.loans, rw.itunda.support) were fully
        // built with zero client UI anywhere until now; see OverviewScreen.kt/
        // LoansScreen.kt/SupportScreen.kt's own doc comments for the full account.
        var showOverview by rememberSaveable { mutableStateOf(false) }
        var showLoans by rememberSaveable { mutableStateOf(false) }
        var showSupport by rememberSaveable { mutableStateOf(false) }
        var showCreditScore by rememberSaveable { mutableStateOf(false) }
        var showCertificate by rememberSaveable { mutableStateOf(false) }
        var showIdentity by rememberSaveable { mutableStateOf(false) }
        // Real 26-week savings plan screen (2026-07-21) -- this feature's ledger-backed
        // backend (WeeklySavingsController/WeeklySavingsService) never had ANY mobile UI
        // before now.
        var showWeeklySavings by rememberSaveable { mutableStateOf(false) }
        // Real Toss Bank 먼저 이자받는 정기예금 (interest-paid-upfront term deposit)
        // screen (2026-07-25) -- same "backend existed with zero mobile UI" gap
        // WeeklySavingsScreen closed above.
        var showUpfrontDeposit by rememberSaveable { mutableStateOf(false) }
        var showMiniWallet by rememberSaveable { mutableStateOf(false) }
        var showGroupAccounts by rememberSaveable { mutableStateOf(false) }
        var showSpending by rememberSaveable { mutableStateOf(false) }
        var showRides by rememberSaveable { mutableStateOf(false) }
        // Real 토스뱅크 외화통장 (foreign-currency account) screen (2026-07-25) -- same
        // "backend existed with zero mobile UI" gap-close pattern as the two above.
        var showForeignCurrency by rememberSaveable { mutableStateOf(false) }
        // Real person-to-person payment request (item 170) -- same "backend real,
        // live-verified, zero mobile UI" gap-close pattern as the two above.
        var showRequestMoney by rememberSaveable { mutableStateOf(false) }
        // Real Naver Pay Money 자동충전 auto top-up screen (item 176) -- same pattern.
        var showAutoTopUp by rememberSaveable { mutableStateOf(false) }
        // Real 전체 (All services) menu (2026-07-22) -- separated from My per the
        // user's direct request; see MenuScreen's own doc comment.
        var showMyTab by rememberSaveable { mutableStateOf(false) }
        // Real Toss Bank 송금 (Transfer) full page (2026-07-24) -- reachable from the
        // 전체/Menu screen's own "Financial services" section, matching real Toss where
        // Home's own Send button stays a quick recipient-picker (unchanged, confirmed
        // against a real Toss screenshot of that exact screen) while the full grouped
        // page (Send money/Auto-transfer/history) lives one level into the menu.
        var showTransferHub by rememberSaveable { mutableStateOf(false) }
        var showAutoTransfers by rememberSaveable { mutableStateOf(false) }
        var autoTransferCount by remember { mutableStateOf(0) }
        LaunchedEffect(showTransferHub) {
            if (showTransferHub) {
                try { autoTransferCount = rw.itunda.core.network.NetworkClient.apiService.getMyAutoTransfers().autoTransfers.count { it.status == "ACTIVE" } } catch (_: Exception) { }
            }
        }
        // Mirrors NAVER Maps' app-to-map handoff, but remains inside Itunda's own
        // authenticated map stack. Consume once so recomposition cannot reopen the map.
        LaunchedEffect(openMapFromDeepLink) {
            if (openMapFromDeepLink) {
                showMap = true
                mapSearchQueryForScreen = initialMapSearchQuery
                onMapDeepLinkConsumed()
            }
        }
        // Real "message seller" hand-off from Hood to Talk (2026-07-18) -- mirrors
        // bank-mfe's BankDashboard.tsx pendingConversationId/onConsumedInitial pattern
        // exactly: HoodTab's contactSeller() switches the selected tab AND stashes the
        // real returned conversation id here, so TalkTab opens straight into that real
        // chat thread instead of dropping the buyer on a conversation list.
        var pendingConversationId by rememberSaveable { mutableStateOf<String?>(null) }
        var biometricError by remember { mutableStateOf<String?>(null) }
        val activity = androidx.compose.ui.platform.LocalContext.current as androidx.fragment.app.FragmentActivity
        val biometricAuth = remember(activity) { rw.itunda.core.identity.NIDABiometricAuth(activity) }

        // Real device binding step-up (2026-07-21 port) -- shared across every
        // money-moving flow below (Transfer, Savings deposit, Interest claim) so a
        // real 403 DEVICE_NOT_VERIFIED from any of them shows the same real dialog
        // rather than three separate copies. See MainViewModel.verifyDevice /
        // DeviceStepUpDialog's own doc comments for the full account.
        var showDeviceStepUp by remember { mutableStateOf(false) }
        var deviceStepUpBusy by remember { mutableStateOf(false) }
        var deviceStepUpError by remember { mutableStateOf<String?>(null) }
        var pendingDeviceRetry by remember { mutableStateOf<(suspend () -> Unit)?>(null) }

        val step = transferStep
        var isSendingTransfer by remember { mutableStateOf(false) }
        val coroutineScope = rememberCoroutineScope()
        val primaryWalletForTransfer by viewModel.primaryWallet.collectAsState()
        // Real saved-contacts list (found 2026-07-22 fully built on the backend with
        // zero client UI anywhere) -- fetched when the recipient screen opens rather
        // than eagerly on app launch, since it's only ever needed here.
        var contacts by remember { mutableStateOf<List<rw.itunda.core.network.ContactDto>>(emptyList()) }
        suspend fun loadContacts() {
            try { contacts = rw.itunda.core.network.NetworkClient.apiService.getContacts().contacts } catch (_: Exception) { }
        }
        LaunchedEffect(step is TransferStep.Recipient) {
            if (step is TransferStep.Recipient) loadContacts()
        }
        if (step != null) {
            // Without this, system/gesture back during a transfer falls through to
            // the Activity's default back behavior (there's no NavHost here) and
            // exits the app mid-transfer instead of stepping back a screen -- the
            // same history-backed back-navigation gap toss/use-funnel's real design
            // (confirmed via toss.tech/GitHub research, 2026-07-12) is built to close.
            BackHandler {
                transferStep = when (step) {
                    is TransferStep.Recipient -> null
                    is TransferStep.Amount -> TransferStep.Recipient
                }
            }
            when (step) {
                is TransferStep.Recipient -> rw.itunda.feature.payments.impl.RecipientEntryScreen(
                    onBack = { transferStep = null },
                    onNext = { accountNumber -> transferStep = TransferStep.Amount(accountNumber) },
                    contacts = contacts.map { rw.itunda.feature.payments.impl.ContactUi(it.name, it.phoneNumber, it.bank) },
                    onAddContact = { name, phoneNumber ->
                        coroutineScope.launch {
                            try {
                                rw.itunda.core.network.NetworkClient.apiService.addContact(
                                    rw.itunda.core.network.AddContactRequest(name, phoneNumber = phoneNumber),
                                )
                                loadContacts()
                            } catch (_: Exception) {
                                // Best-effort -- a failed save just leaves the form's
                                // input in place for the user to retry.
                            }
                        }
                    },
                )
                is TransferStep.Amount -> {
                    rw.itunda.feature.payments.impl.TransferAmountScreen(
                        recipientAccountNumber = step.accountNumber,
                        availableBalance = primaryWalletForTransfer?.availableBalance ?: 0.0,
                        isSubmitting = isSendingTransfer,
                        onBack = { transferStep = TransferStep.Recipient },
                        onConfirm = { amountRwf ->
                            // Toss-style biometric confirmation gate before a transfer
                            // completes -- see docs/ARCHITECTURE.md's NIDABiometricAuth
                            // note. Real quote+confirm call now follows a successful
                            // check (2026-07-12, see MainViewModel.sendTransfer) --
                            // previously "success" here just closed the sheet without
                            // moving any real money (see TransferFlow.kt's old header).
                            biometricError = null
                            biometricAuth.authenticateForTransaction(
                                reason = "Confirm sending $amountRwf RWF"
                            ) { success, error ->
                                if (success) {
                                    isSendingTransfer = true
                                    coroutineScope.launch {
                                        when (val result = viewModel.sendTransfer(step.accountNumber, amountRwf)) {
                                            is rw.itunda.app.ui.MoneyActionResult.Success -> {
                                                isSendingTransfer = false
                                                transferStep = null
                                            }
                                            // sendTransfer never actually returns Queued -- a
                                            // transfer confirm is deliberately never queued
                                            // offline (see MainViewModel.depositToSavingsGoal's
                                            // doc comment for why) -- handled only because
                                            // MoneyActionResult is a shared sealed interface.
                                            is rw.itunda.app.ui.MoneyActionResult.Queued -> {
                                                isSendingTransfer = false
                                                transferStep = null
                                            }
                                            is rw.itunda.app.ui.MoneyActionResult.Failure -> {
                                                isSendingTransfer = false
                                                biometricError = result.message
                                            }
                                            is rw.itunda.app.ui.MoneyActionResult.DeviceNotVerified -> {
                                                isSendingTransfer = false
                                                deviceStepUpError = null
                                                pendingDeviceRetry = {
                                                    isSendingTransfer = true
                                                    val retryResult = viewModel.sendTransfer(step.accountNumber, amountRwf)
                                                    isSendingTransfer = false
                                                    if (retryResult is rw.itunda.app.ui.MoneyActionResult.Success) transferStep = null
                                                    else if (retryResult is rw.itunda.app.ui.MoneyActionResult.Failure) biometricError = retryResult.message
                                                }
                                                showDeviceStepUp = true
                                            }
                                        }
                                    }
                                } else {
                                    biometricError = error ?: "Couldn't verify. Try again."
                                }
                            }
                        }
                    )
                    biometricError?.let { message ->
                        androidx.compose.material3.Text(
                            text = message,
                            color = Ids.colors.danger,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                        )
                    }
                }
            }
            if (showDeviceStepUp) {
                rw.itunda.feature.payments.impl.DeviceStepUpDialog(
                    busy = deviceStepUpBusy,
                    error = deviceStepUpError,
                    onCancel = { showDeviceStepUp = false; deviceStepUpError = null; pendingDeviceRetry = null },
                    onVerify = { password ->
                        deviceStepUpError = null
                        deviceStepUpBusy = true
                        coroutineScope.launch {
                            when (val result = viewModel.verifyDevice(password)) {
                                is rw.itunda.app.ui.MoneyActionResult.Success -> {
                                    deviceStepUpBusy = false
                                    showDeviceStepUp = false
                                    val retry = pendingDeviceRetry
                                    pendingDeviceRetry = null
                                    retry?.invoke()
                                }
                                is rw.itunda.app.ui.MoneyActionResult.Failure -> {
                                    deviceStepUpBusy = false
                                    deviceStepUpError = result.message
                                }
                                else -> { deviceStepUpBusy = false }
                            }
                        }
                    }
                )
            }
            return@IdsTheme
        }

        val savingsStep = savingsFlowStep
        var isSavingsSubmitting by remember { mutableStateOf(false) }
        var savingsError by remember { mutableStateOf<String?>(null) }
        val availableBalanceForSavings by viewModel.primaryWallet.collectAsState()
        val savingsContext = androidx.compose.ui.platform.LocalContext.current
        if (savingsStep != null) {
            BackHandler { savingsFlowStep = null }
            when (savingsStep) {
                is SavingsFlowStep.Deposit -> rw.itunda.feature.payments.impl.SavingsAmountScreen(
                    goalName = savingsStep.goalName,
                    mode = rw.itunda.feature.payments.impl.SavingsAmountMode.deposit,
                    availableBalance = availableBalanceForSavings?.availableBalance ?: 0.0,
                    isSubmitting = isSavingsSubmitting,
                    onBack = { savingsFlowStep = null },
                    onConfirm = { amountRwf ->
                        isSavingsSubmitting = true
                        coroutineScope.launch {
                            when (val result = viewModel.depositToSavingsGoal(savingsStep.goalId, amountRwf)) {
                                is rw.itunda.app.ui.MoneyActionResult.Success -> {
                                    isSavingsSubmitting = false
                                    savingsFlowStep = null
                                }
                                // Real offline queueing (2026-07-13, see
                                // MainViewModel.depositToSavingsGoal): the deposit was
                                // saved locally, not executed yet -- close the sheet
                                // like a success (the user's intent was captured) but
                                // surface the distinction via a real Toast rather than
                                // silently treating it as identical to a completed
                                // deposit.
                                is rw.itunda.app.ui.MoneyActionResult.Queued -> {
                                    isSavingsSubmitting = false
                                    savingsFlowStep = null
                                    android.widget.Toast.makeText(savingsContext, result.message, android.widget.Toast.LENGTH_LONG).show()
                                }
                                is rw.itunda.app.ui.MoneyActionResult.Failure -> {
                                    isSavingsSubmitting = false
                                    savingsError = result.message
                                }
                                is rw.itunda.app.ui.MoneyActionResult.DeviceNotVerified -> {
                                    isSavingsSubmitting = false
                                    deviceStepUpError = null
                                    pendingDeviceRetry = {
                                        isSavingsSubmitting = true
                                        val retryResult = viewModel.depositToSavingsGoal(savingsStep.goalId, amountRwf)
                                        isSavingsSubmitting = false
                                        if (retryResult is rw.itunda.app.ui.MoneyActionResult.Success || retryResult is rw.itunda.app.ui.MoneyActionResult.Queued) savingsFlowStep = null
                                        else if (retryResult is rw.itunda.app.ui.MoneyActionResult.Failure) savingsError = retryResult.message
                                    }
                                    showDeviceStepUp = true
                                }
                            }
                        }
                    }
                )
                is SavingsFlowStep.ClaimInterest -> rw.itunda.feature.payments.impl.SavingsAmountScreen(
                    goalName = "Interest jar",
                    mode = rw.itunda.feature.payments.impl.SavingsAmountMode.claimInterest,
                    availableBalance = availableBalanceForSavings?.availableBalance ?: 0.0,
                    isSubmitting = isSavingsSubmitting,
                    onBack = { savingsFlowStep = null },
                    onConfirm = {
                        isSavingsSubmitting = true
                        coroutineScope.launch {
                            when (val result = viewModel.claimInterest()) {
                                is rw.itunda.app.ui.MoneyActionResult.Success -> {
                                    isSavingsSubmitting = false
                                    savingsFlowStep = null
                                }
                                // claimInterest never actually returns Queued (only
                                // SAVINGS_DEPOSIT is queued) -- handled only because
                                // MoneyActionResult is a shared sealed interface.
                                is rw.itunda.app.ui.MoneyActionResult.Queued -> {
                                    isSavingsSubmitting = false
                                    savingsFlowStep = null
                                }
                                is rw.itunda.app.ui.MoneyActionResult.Failure -> {
                                    isSavingsSubmitting = false
                                    savingsError = result.message
                                }
                                is rw.itunda.app.ui.MoneyActionResult.DeviceNotVerified -> {
                                    isSavingsSubmitting = false
                                    deviceStepUpError = null
                                    pendingDeviceRetry = {
                                        isSavingsSubmitting = true
                                        val retryResult = viewModel.claimInterest()
                                        isSavingsSubmitting = false
                                        if (retryResult is rw.itunda.app.ui.MoneyActionResult.Success) savingsFlowStep = null
                                        else if (retryResult is rw.itunda.app.ui.MoneyActionResult.Failure) savingsError = retryResult.message
                                    }
                                    showDeviceStepUp = true
                                }
                            }
                        }
                    }
                )
            }
            savingsError?.let { message ->
                androidx.compose.material3.Text(
                    text = message,
                    color = Ids.colors.danger,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
            }
            if (showDeviceStepUp) {
                rw.itunda.feature.payments.impl.DeviceStepUpDialog(
                    busy = deviceStepUpBusy,
                    error = deviceStepUpError,
                    onCancel = { showDeviceStepUp = false; deviceStepUpError = null; pendingDeviceRetry = null },
                    onVerify = { password ->
                        deviceStepUpError = null
                        deviceStepUpBusy = true
                        coroutineScope.launch {
                            when (val result = viewModel.verifyDevice(password)) {
                                is rw.itunda.app.ui.MoneyActionResult.Success -> {
                                    deviceStepUpBusy = false
                                    showDeviceStepUp = false
                                    val retry = pendingDeviceRetry
                                    pendingDeviceRetry = null
                                    retry?.invoke()
                                }
                                is rw.itunda.app.ui.MoneyActionResult.Failure -> {
                                    deviceStepUpBusy = false
                                    deviceStepUpError = result.message
                                }
                                else -> { deviceStepUpBusy = false }
                            }
                        }
                    }
                )
            }
            return@IdsTheme
        }

        if (showTransactionHistory) {
            BackHandler { showTransactionHistory = false }
            val transactionsForHistory by viewModel.transactions.collectAsState()
            val currentUserIdForHistory by viewModel.primaryWallet.collectAsState()
            rw.itunda.feature.payments.impl.TransactionHistoryScreen(
                transactions = transactionsForHistory.map { tx ->
                    rw.itunda.feature.payments.impl.TransactionDisplayItem(
                        id = tx.id,
                        description = tx.description,
                        amount = tx.amount,
                        currency = tx.currency,
                        status = tx.status,
                        isOutgoing = tx.senderId == currentUserIdForHistory?.userId,
                    )
                },
                onBack = { showTransactionHistory = false },
            )
            return@IdsTheme
        }

        if (showSettings) {
            BackHandler { showSettings = false }
            SettingsScreen(
                viewModel = viewModel,
                onBack = { showSettings = false },
                onLogout = { coroutineScope.launch { rw.itunda.core.network.SessionManager.logout() } },
            )
            return@IdsTheme
        }

        // Benefits/Pay folded into My as real full-screen entry points (2026-07-18)
        // rather than dropped outright -- same reachability, one fewer top-level tab.
        if (showBenefits) {
            BackHandler { showBenefits = false }
            Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
                Box(modifier = Modifier.fillMaxSize().padding(padding)) { BenefitsTab(onBack = { showBenefits = false }) }
            }
            return@IdsTheme
        }
        if (showPay) {
            BackHandler { showPay = false }
            Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
                Box(modifier = Modifier.fillMaxSize().padding(padding)) { PayTab(onBack = { showPay = false }) }
            }
            return@IdsTheme
        }
        // Real self-hosted Rwanda map (2026-07-19) -- same Quick-links full-screen
        // pattern as Pay/Benefits above, since the 5-tab bottom nav has no free slot.
        if (showMap) {
            BackHandler { showMap = false }
            MapScreen(
                onBack = { showMap = false },
                initialCategory = if (showAgentCash) "ITUNDA_AGENT" else null,
                initialSearchQuery = mapSearchQueryForScreen,
            )
            return@IdsTheme
        }
        if (showAgentCash) {
            BackHandler { showAgentCash = false }
            AgentCashScreen(
                onBack = { showAgentCash = false },
                // Keep the cash-out screen in the back stack: map Back returns the
                // customer to their code flow rather than silently dropping it.
                onFindNearbyAgent = { showMap = true },
            )
            return@IdsTheme
        }
        // Real Invest/Stocks screen (2026-07-20) -- same Quick-links full-screen
        // pattern as Pay/Benefits/Map above; this feature never had ANY mobile UI
        // before now, not even the original buy/sell/portfolio.
        if (showInvest) {
            BackHandler { showInvest = false }
            InvestScreen(onBack = { showInvest = false })
            return@IdsTheme
        }
        if (showOverview) {
            BackHandler { showOverview = false }
            OverviewScreen(onBack = { showOverview = false })
            return@IdsTheme
        }
        if (showLoans) {
            BackHandler { showLoans = false }
            LoansScreen(onBack = { showLoans = false })
            return@IdsTheme
        }
        if (showSupport) {
            BackHandler { showSupport = false }
            SupportScreen(onBack = { showSupport = false })
            return@IdsTheme
        }
        if (showCreditScore) {
            BackHandler { showCreditScore = false }
            CreditScoreScreen(onBack = { showCreditScore = false })
            return@IdsTheme
        }
        if (showCertificate) {
            BackHandler { showCertificate = false }
            CertificateScreen(onBack = { showCertificate = false })
            return@IdsTheme
        }
        if (showIdentity) {
            BackHandler { showIdentity = false }
            IdentityScreen(onBack = { showIdentity = false })
            return@IdsTheme
        }
        // Real 26-week savings plan screen (2026-07-21) -- same Quick-links full-screen
        // pattern as Invest/Map/Pay/Benefits above; this feature's ledger-backed backend
        // (WeeklySavingsController/WeeklySavingsService) never had ANY mobile UI before now.
        if (showWeeklySavings) {
            BackHandler { showWeeklySavings = false }
            WeeklySavingsScreen(onBack = { showWeeklySavings = false })
            return@IdsTheme
        }
        // Real Toss Bank 먼저 이자받는 정기예금 screen (2026-07-25) -- same pattern.
        if (showUpfrontDeposit) {
            BackHandler { showUpfrontDeposit = false }
            UpfrontDepositScreen(onBack = { showUpfrontDeposit = false })
            return@IdsTheme
        }
        // Real KakaoBank mini-style capped starter wallet screen (2026-07-28, item 100)
        // -- first mobile client for this feature. Same pattern.
        if (showMiniWallet) {
            BackHandler { showMiniWallet = false }
            MiniWalletScreen(onBack = { showMiniWallet = false })
            return@IdsTheme
        }
        // Real Kakao Bank 모임통장 (group/shared account) screen (2026-07-28, item 104)
        // -- first Android client for this feature. Same pattern.
        if (showGroupAccounts) {
            BackHandler { showGroupAccounts = false }
            GroupAccountScreen(onBack = { showGroupAccounts = false })
            return@IdsTheme
        }
        // Real Kakao Pay spending categorization screen (2026-07-28, item 107) -- first
        // Android client for this feature. Same pattern.
        if (showSpending) {
            BackHandler { showSpending = false }
            SpendingScreen(onBack = { showSpending = false })
            return@IdsTheme
        }
        // Real Kakao T-style ride-hailing screen (2026-07-28, item 109) -- first
        // Android client for this feature (bank-mfe has had it since 2026-07-26).
        if (showRides) {
            BackHandler { showRides = false }
            RideScreen(onBack = { showRides = false })
            return@IdsTheme
        }
        // Real 토스뱅크 외화통장 screen (2026-07-25) -- same pattern.
        if (showForeignCurrency) {
            BackHandler { showForeignCurrency = false }
            ForeignCurrencyScreen(onBack = { showForeignCurrency = false })
            return@IdsTheme
        }
        // Real person-to-person payment request screen (item 170) -- same pattern.
        if (showRequestMoney) {
            BackHandler { showRequestMoney = false }
            RequestMoneyScreen(onBack = { showRequestMoney = false })
            return@IdsTheme
        }
        // Real Naver Pay Money 자동충전 auto top-up screen (item 176) -- same pattern.
        if (showAutoTopUp) {
            BackHandler { showAutoTopUp = false }
            AutoTopUpScreen(onBack = { showAutoTopUp = false })
            return@IdsTheme
        }
        // Real My-activity overlay (2026-07-24) -- the exact inverse of the old
        // showMenu overlay: My's own real content (orders/favorites/listings) is now
        // reached one tap in from the All tab's profile icon, instead of All being
        // nested under My.
        if (showMyTab) {
            MyTab(
                onBack = { showMyTab = false },
                onSwitchToShop = { showMyTab = false; selectedTab = TossTab.Shop },
                onSwitchToHood = { showMyTab = false; selectedTab = TossTab.Hood },
            )
            return@IdsTheme
        }
        if (showTransferHub) {
            if (showAutoTransfers) {
                AutoTransferListScreen(
                    onBack = { showAutoTransfers = false },
                    onChanged = {
                        coroutineScope.launch {
                            try { autoTransferCount = rw.itunda.core.network.NetworkClient.apiService.getMyAutoTransfers().autoTransfers.count { it.status == "ACTIVE" } } catch (_: Exception) { }
                        }
                    },
                )
            } else {
                TransferHubScreen(
                    autoTransferCount = autoTransferCount,
                    onBack = { showTransferHub = false },
                    onSendMoney = { showTransferHub = false; transferStep = TransferStep.Recipient },
                    onOpenAutoTransfers = { showAutoTransfers = true },
                    onSplitBill = { showTransferHub = false; selectedTab = TossTab.Talk },
                    onOpenHistory = { showTransferHub = false; showTransactionHistory = true },
                )
            }
            return@IdsTheme
        }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                TossBottomBar(selectedTab = selectedTab, onSelect = { selectedTab = it })
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(paddingValues)
            ) {
                when (selectedTab) {
                    TossTab.Home -> HomeTab(
                        viewModel,
                        onSend = { transferStep = TransferStep.Recipient },
                        onDepositToGoal = { goalId, goalName -> savingsFlowStep = SavingsFlowStep.Deposit(goalId, goalName) },
                        onClaimInterest = { savingsFlowStep = SavingsFlowStep.ClaimInterest },
                        onOpenTransactionHistory = { showTransactionHistory = true },
                        onCashOutAtAgent = { showAgentCash = true },
                        onOpenPay = { showPay = true },
                        onOpenNotifications = { showSettings = true },
                    )
                    TossTab.Shop -> ShopTab()
                    TossTab.Hood -> HoodTab(
                        onMessageSeller = { conversationId ->
                            pendingConversationId = conversationId
                            selectedTab = TossTab.Talk
                        },
                    )
                    // Seventh and final Feature extraction (2026-07-23) -- see
                    // TalkScreen.kt's own header comment for why deviceStepUpHost is
                    // injected (DeviceStepUpHost.kt wraps :features:payments:impl's
                    // dialog, so it can't become a direct Feature-to-Feature dependency).
                    TossTab.Talk -> TalkTab(
                        initialConversationId = pendingConversationId,
                        onConsumedInitial = { pendingConversationId = null },
                        deviceStepUpHost = { visible, onDismiss, onVerified ->
                            DeviceStepUpHost(visible = visible, onDismiss = onDismiss, onVerified = onVerified)
                        },
                    )
                    // Real 전체 (All services) primary bottom tab (2026-07-24) -- see
                    // TossTab.All's own doc comment for why this replaced My here.
                    TossTab.All -> {
                        val partnerMiniApps by viewModel.partnerMiniApps.collectAsState()
                        MenuScreen(
                            onOpenMyTab = { showMyTab = true },
                            onOpenSettings = { showSettings = true },
                            onOpenPay = { showPay = true },
                            onOpenBenefits = { showBenefits = true },
                            onOpenInvest = { showInvest = true },
                            onOpenMap = { showMap = true },
                            onOpenOverview = { showOverview = true },
                            onOpenLoans = { showLoans = true },
                            onOpenSupport = { showSupport = true },
                            onOpenCreditScore = { showCreditScore = true },
                            onOpenCertificate = { showCertificate = true },
                            onOpenIdentity = { showIdentity = true },
                            onOpenWeeklySavings = { showWeeklySavings = true },
                            onOpenUpfrontDeposit = { showUpfrontDeposit = true },
                            onOpenMiniWallet = { showMiniWallet = true },
                            onOpenGroupAccounts = { showGroupAccounts = true },
                            onOpenSpending = { showSpending = true },
                            onOpenRides = { showRides = true },
                            onOpenForeignCurrency = { showForeignCurrency = true },
                            onOpenRequestMoney = { showRequestMoney = true },
                            onOpenAutoTopUp = { showAutoTopUp = true },
                            onOpenTransferHub = { showTransferHub = true },
                            onClaimInterest = { savingsFlowStep = SavingsFlowStep.ClaimInterest },
                            onSwitchToTalk = { selectedTab = TossTab.Talk },
                            partnerMiniApps = partnerMiniApps,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Toss's real bottom nav is a flat, edge-to-edge bar with a hairline top
 * divider and real icons -- not a floating rounded pill with letter-glyph
 * placeholders, which is what this was before and read as an unfinished
 * wireframe rather than an actual app (feedback from comparing directly
 * against real Toss screenshots, 2026-07-10).
 */
@Composable
private fun TossBottomBar(selectedTab: TossTab, onSelect: (TossTab) -> Unit) {
    Column {
        Divider(color = TossLine, thickness = 0.5.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(TossCard)
                .padding(top = 8.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TossTab.entries.forEach { tab ->
                val selected = tab == selectedTab
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelect(tab) }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label,
                        modifier = Modifier.size(24.dp),
                        tint = if (selected) TossBlue else TossTertiary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = tab.label,
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (selected) TossBlue else TossTertiary
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeTab(
    viewModel: MainViewModel,
    onSend: () -> Unit,
    onDepositToGoal: (goalId: String, goalName: String) -> Unit,
    onClaimInterest: () -> Unit,
    onOpenTransactionHistory: () -> Unit,
    onCashOutAtAgent: () -> Unit,
    onOpenPay: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
) {
    val primaryWallet by viewModel.primaryWallet.collectAsState()
    val balanceText = primaryWallet?.let { "${it.currency} %,.0f".format(it.balance) } ?: "RWF 0"
    val savingsGoals by viewModel.savingsGoals.collectAsState()
    val interestJar by viewModel.interestJar.collectAsState()
    val discoverItems by viewModel.discoverItems.collectAsState()
    val roundUpSettings by viewModel.roundUpSettings.collectAsState()
    var showRoundUpDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        // top/bottom kept as their own literal values, not forced into
        // screenVertical/sectionGap -- they're genuinely different from the other
        // 4 tabs' uniform vertical padding, and IdsLayout.kt's own header explains
        // why this pass doesn't force every value into a token that doesn't
        // actually fit.
        contentPadding = PaddingValues(start = Ids.layout.screenHorizontal, top = 14.dp, end = Ids.layout.screenHorizontal, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(Ids.layout.cardGap)
    ) {
        item { HomeTopBar(onOpenPay = onOpenPay, onOpenNotifications = onOpenNotifications) }
        item { WalletHeroCard(balanceText, onSend, onCashOutAtAgent) }
        item {
            ShellSection(
                title = "",
                rows = listOf(
                    // Wired to real transaction history (2026-07-12) -- the headline
                    // figure/label ("RWF 463,022" / "Spent in July") stay illustrative
                    // (no real spend-by-month aggregation endpoint exists yet), but
                    // tapping through now opens the real list rather than nothing.
                    ShellRow("RWF 463,022", "Spent in July", "3 new", Icons.Outlined.PieChart, AccentPurple, onClick = onOpenTransactionHistory),
                    ShellRow("Transfer cashback", "BK account -> TUYIZERE Eric", "Claim", Icons.Outlined.Payments, AccentBlue),
                    ShellRow("Sprinkle money to friends", "19:03:55 left", "Send", Icons.Outlined.Redeem, AccentOrange)
                )
            )
        }
        item {
            ShellSection(
                title = "",
                rows = listOf(
                    ShellRow("Get cashback every time you pay", "", ">", Icons.Outlined.Payments, AccentBlue),
                    ShellRow("Pay with face ID", "", ">", Icons.Outlined.Face, AccentPurple),
                    ShellRow("Receive government alerts", "", ">", Icons.Outlined.Campaign, AccentRed)
                )
            )
        }
        // Real savings goals + interest jar (2026-07-11) -- the first Home tab
        // content backed by services/backend's savings module rather than static
        // promotional copy. Rendered only once real data has arrived, so an empty
        // list before the first fetch resolves doesn't flash a title with nothing
        // under it.
        if (savingsGoals.isNotEmpty() || interestJar != null) {
            item {
                ShellSection(
                    title = "Savings",
                    rows = buildList {
                        interestJar?.let { jar ->
                            add(
                                ShellRow(
                                    "Interest jar",
                                    "Earned this month",
                                    "RWF %,.0f".format(jar.earnedThisMonth),
                                    Icons.Outlined.Savings,
                                    AccentOrange,
                                    onClick = onClaimInterest,
                                )
                            )
                        }
                        savingsGoals.forEach { goal ->
                            val progressPercent = if (goal.targetAmount > 0) {
                                (goal.currentAmount / goal.targetAmount * 100).toInt()
                            } else 0
                            add(
                                ShellRow(
                                    goal.name,
                                    "RWF %,.0f of %,.0f".format(goal.currentAmount, goal.targetAmount),
                                    "$progressPercent%",
                                    Icons.Outlined.Savings,
                                    AccentBlue,
                                    onClick = { onDepositToGoal(goal.id, goal.name) },
                                )
                            )
                        }
                        // Real Kakao Pay 머니굴리기 round-up equivalent (2026-07-25) --
                        // only offered once a goal exists to round into. See
                        // RoundUpSettings.kt's own doc comment.
                        if (savingsGoals.isNotEmpty()) {
                            add(
                                ShellRow(
                                    "Round-up saving",
                                    if (roundUpSettings?.enabled == true) {
                                        "Rounding up to RWF %,.0f".format(roundUpSettings?.roundToNearest)
                                    } else "Off",
                                    if (roundUpSettings?.enabled == true) "On" else "Set up",
                                    Icons.Outlined.CurrencyExchange,
                                    AccentPurple,
                                    onClick = { showRoundUpDialog = true },
                                )
                            )
                        }
                    }
                )
            }
        }
        if (showRoundUpDialog) {
            item {
                RoundUpSettingsDialog(
                    settings = roundUpSettings,
                    goals = savingsGoals,
                    onDismiss = { showRoundUpDialog = false },
                    onSave = { enabled, increment, goalId ->
                        coroutineScope.launch {
                            viewModel.setRoundUpSettings(enabled, increment, goalId)
                            showRoundUpDialog = false
                        }
                    },
                )
            }
        }
        // Real Discover feed (found 2026-07-22) -- MainViewModel already fetched this
        // from GET /api/v1/discover on every launch, but it was never rendered
        // anywhere in the app: a real, live data flow with no UI consumer. See
        // DiscoverController.kt's own promotional-item catalog on the backend.
        if (discoverItems.isNotEmpty()) {
            item { DiscoverSection(discoverItems) }
        }
    }
}

// Real Kakao Pay 머니굴리기 round-up settings sheet (2026-07-25) -- see
// RoundUpSettings.kt's own doc comment for the increment/goal invariants this
// mirrors (SUPPORTED_INCREMENTS, a goal is required to enable).
@Composable
private fun RoundUpSettingsDialog(
    settings: rw.itunda.core.network.RoundUpSettingsDto?,
    goals: List<rw.itunda.core.network.SavingsGoal>,
    onDismiss: () -> Unit,
    onSave: (enabled: Boolean, roundToNearest: Long, goalId: String?) -> Unit,
) {
    var enabled by remember { mutableStateOf(settings?.enabled ?: false) }
    var increment by remember { mutableStateOf(settings?.roundToNearest?.toLong() ?: 100L) }
    var selectedGoalId by remember { mutableStateOf(settings?.targetGoalId ?: goals.firstOrNull()?.id) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Round-up saving") },
        text = {
            Column {
                Text("Every time you send money, round the payment up and save the difference.", color = TossSecondary, fontSize = 13.sp)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Enable round-up", modifier = Modifier.weight(1f))
                    androidx.compose.material3.Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
                if (enabled) {
                    Spacer(Modifier.height(12.dp))
                    Text("Round up to nearest", color = TossSecondary, fontSize = 13.sp)
                    Row(modifier = Modifier.fillMaxWidth()) {
                        listOf(100L, 500L, 1000L).forEach { option ->
                            Row(
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                modifier = Modifier.clickable { increment = option }.padding(end = 8.dp)
                            ) {
                                androidx.compose.material3.RadioButton(selected = increment == option, onClick = { increment = option })
                                Text("RWF $option")
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Save into", color = TossSecondary, fontSize = 13.sp)
                    goals.forEach { goal ->
                        Row(
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { selectedGoalId = goal.id }
                        ) {
                            androidx.compose.material3.RadioButton(selected = selectedGoalId == goal.id, onClick = { selectedGoalId = goal.id })
                            Text(goal.name)
                        }
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = { onSave(enabled, increment, if (enabled) selectedGoalId else null) }) { Text("Save") }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun DiscoverSection(items: List<rw.itunda.core.network.DiscoverItem>) {
    Column {
        Text("Discover", color = TossText, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
        items.forEach { discoverItem ->
            val accentColor = try {
                Color(android.graphics.Color.parseColor(discoverItem.color))
            } catch (_: IllegalArgumentException) {
                AccentBlue
            }
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = TossCard),
                elevation = CardDefaults.cardElevation(defaultElevation = Ids.layout.cardElevation),
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(accentColor))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(discoverItem.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TossText)
                            if (discoverItem.isNew) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("NEW", color = accentColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Text(discoverItem.subtitle, fontSize = 14.sp, color = TossSecondary)
                    }
                    discoverItem.badge?.let { badge ->
                        Text(badge, color = accentColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeTopBar(onOpenPay: () -> Unit = {}, onOpenNotifications: () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Real search bar, not an empty placeholder box -- the previous
        // version here was a Box() with a background color and no children
        // at all, a genuine leftover bug (found comparing directly against
        // real Toss screenshots, 2026-07-10).
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(TossCardSoft)
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Text("Search", color = TossSecondary, fontSize = 15.sp)
        }
        // Both icons were real no-op taps (found 2026-07-22 audit) despite their own
        // real destinations already existing elsewhere in this file: QR scan opens
        // the same real "Pay" screen (scan-or-pay-by-code) the My tab's Pay row
        // already reaches; Notifications opens Settings, which already renders a
        // real notifications list against GET /api/v1/notifications.
        IdsIconButton(Icons.Outlined.QrCodeScanner, contentDescription = "Scan QR code", onClick = onOpenPay)
        IdsIconButton(Icons.Outlined.Notifications, contentDescription = "Notifications", onClick = onOpenNotifications)
    }
}

@Composable
private fun WalletHeroCard(balanceText: String, onSend: () -> Unit, onCashOutAtAgent: () -> Unit) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = TossCard),
        elevation = CardDefaults.cardElevation(defaultElevation = Ids.layout.cardElevation),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Wallet", fontSize = 14.sp, color = TossSecondary)
            Text(balanceText, fontSize = 34.sp, color = TossText, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IdsButton("Cash out", onClick = onCashOutAtAgent, modifier = Modifier.weight(1f), variant = IdsButtonVariant.Tinted, size = IdsButtonSize.Medium)
                IdsButton("Send", onClick = onSend, modifier = Modifier.weight(1f), variant = IdsButtonVariant.Filled, size = IdsButtonSize.Medium)
            }
            Divider(color = TossLine)
            WalletMiniRow("RWF 613", "Bravo Korea parking", "Send")
            WalletMiniRow("RWF 7,489", "Savings deposit", "Send")
            Text(
                "See all",
                modifier = Modifier.fillMaxWidth(),
                color = TossSecondary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun WalletMiniRow(amount: String, subtitle: String, action: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(TossChip),
            contentAlignment = Alignment.Center
        ) {
            // Was amount.take(1) -- literally the first character of the RWF
            // string as an "icon" (e.g. "R"), a real leftover bug, not a
            // deliberate placeholder. Real icon now.
            Icon(Icons.Outlined.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp), tint = TossText)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(amount, color = TossText, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(subtitle, color = TossSecondary, fontSize = 14.sp)
        }
        IdsButton(action, onClick = {}, variant = IdsButtonVariant.Tinted, size = IdsButtonSize.Small)
    }
}

private data class ShellRow(
    val title: String,
    val subtitle: String,
    val action: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val iconColor: Color = AccentBlue,
    // Added 2026-07-12 for the real Savings section's rows (deposit/claim) --
    // default null preserves every existing purely-promotional ShellRow call site
    // unchanged.
    val onClick: (() -> Unit)? = null,
)

@Composable
private fun ShellSection(title: String, rows: List<ShellRow>) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = TossCard),
        elevation = CardDefaults.cardElevation(defaultElevation = Ids.layout.cardElevation),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            if (title.isNotEmpty()) {
                Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TossText)
                Spacer(modifier = Modifier.height(8.dp))
            }
            rows.forEachIndexed { index, row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (row.onClick != null) Modifier.clickable(onClick = row.onClick) else Modifier)
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(row.iconColor),
                        contentAlignment = Alignment.Center
                    ) {
                        // Was showing row.third (the action label, e.g. "3 new"
                        // or "Claim") crammed into a 42dp icon box -- a real bug,
                        // not a placeholder; it also rendered a second time below
                        // via the row's own action button whenever longer than one character.
                        // Then briefly row.first's initial as a stopgap, then a
                        // real icon but on a flat muted TossChip background --
                        // real Toss's card-list icon badges (송금/자산 reference
                        // screenshots) are vivid per-item brand colors, not one
                        // neutral gray tone reused everywhere.
                        Icon(row.icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color.White)
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(row.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TossText)
                        if (row.subtitle.isNotEmpty()) {
                            Text(row.subtitle, fontSize = 14.sp, color = TossSecondary)
                        }
                    }
                    if (row.action == ">") {
                        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = TossTertiary)
                    } else if (row.action.isNotBlank()) {
                        IdsButton(row.action, onClick = {}, variant = IdsButtonVariant.Tinted, size = IdsButtonSize.Small)
                    }
                }
                if (index != rows.lastIndex) {
                    Divider(color = TossLine)
                }
            }
        }
    }
}

@Composable
private fun BenefitsTab(onBack: () -> Unit = {}) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Ids.layout.screenHorizontal, vertical = Ids.layout.screenVertical),
        verticalArrangement = Arrangement.spacedBy(Ids.layout.cardGap)
    ) {
        item { BackTopBar("Benefits", onBack) }
        item { PromoBannerCard() }
        item { PointPill("P 137") }
        item { BenefitsVisitCard() }
        item { CashbackChanceCard() }
    }
}

@Composable
private fun PayTab(onBack: () -> Unit = {}) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = Ids.layout.screenHorizontal, vertical = Ids.layout.screenVertical),
        verticalArrangement = Arrangement.spacedBy(Ids.layout.cardGap)
    ) {
        item { BackTopBar("Pay", onBack) }
        item { MapPlaceholder() }
        item { PayFeatureCard() }
        item { ShellSection("", listOf(
            ShellRow("Points and pay money", "Total RWF 31,031", " ", Icons.Outlined.Payments, AccentBlue),
            ShellRow("Received coupons", "", " ", Icons.Outlined.LocalOffer, AccentOrange)
        )) }
    }
}

// Real 전체 (All services) primary bottom tab (2026-07-24, promoted from a My-tab-nested
// overlay) -- see TossTab.All's own doc comment for the full history: separated from My
// at the user's own direct request in an earlier pass ("My and All screen should be
// separated like KakaoPay"), then brought back to the bottom nav directly once mini-apps
// and (planned) games meant this exhaustive service catalog needed to be one tap away,
// not nested two taps under My. MyTab is now the secondary, profile-icon-reachable
// screen for the personal-activity content (orders/favorites/listings) that doesn't
// belong in an exhaustive product catalog.
@Composable
private fun MenuScreen(
    onOpenMyTab: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenPay: () -> Unit = {},
    onOpenBenefits: () -> Unit = {},
    onOpenInvest: () -> Unit = {},
    onOpenMap: () -> Unit = {},
    onOpenOverview: () -> Unit = {},
    onOpenLoans: () -> Unit = {},
    onOpenSupport: () -> Unit = {},
    onOpenCreditScore: () -> Unit = {},
    onOpenCertificate: () -> Unit = {},
    onOpenIdentity: () -> Unit = {},
    onOpenWeeklySavings: () -> Unit = {},
    onOpenUpfrontDeposit: () -> Unit = {},
    onOpenMiniWallet: () -> Unit = {},
    onOpenGroupAccounts: () -> Unit = {},
    onOpenSpending: () -> Unit = {},
    onOpenRides: () -> Unit = {},
    onOpenForeignCurrency: () -> Unit = {},
    onOpenRequestMoney: () -> Unit = {},
    onOpenAutoTopUp: () -> Unit = {},
    onOpenTransferHub: () -> Unit = {},
    onClaimInterest: () -> Unit = {},
    onSwitchToTalk: () -> Unit = {},
    partnerMiniApps: List<rw.itunda.core.network.PartnerMiniAppDto>,
) {
    // No BackHandler here (2026-07-24): this is now a persistent bottom-nav
    // destination, not a screen pushed on top of one -- there's nothing to back out
    // to. My's own real content is one tap in via the profile icon below instead.
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
    var partnerLoadError by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    var loadingPartnerAppId by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = Ids.layout.screenHorizontal, vertical = Ids.layout.screenVertical),
        verticalArrangement = Arrangement.spacedBy(Ids.layout.cardGap)
    ) {
        item { AllTopBar(onOpenSettings = onOpenSettings, onOpenMyTab = onOpenMyTab) }
        item { SearchBar("Search") }
        // Benefits/Pay folded in here (2026-07-18) -- both lost their own top-level
        // tab when the bottom nav became Home/Shop/Hood/Talk/My, but stay just as
        // reachable as a real row instead of being dropped.
        item {
            FlatSection(
                "Quick links",
                listOf(
                    FlatRow("Pay", subtitle = "Scan or pay by code", icon = Icons.Outlined.QrCodeScanner, iconColor = AccentBlue, onClick = onOpenPay),
                    FlatRow("Benefits", subtitle = "Points, coupons, rewards", icon = Icons.Outlined.CardGiftcard, iconColor = AccentOrange, onClick = onOpenBenefits),
                    FlatRow("Invest", subtitle = "RSE stocks, real portfolio", icon = Icons.Outlined.TrendingUp, iconColor = AccentPurple, onClick = onOpenInvest),
                    FlatRow("26-Week Savings", subtitle = "Escalating auto-save, streak bonus", icon = Icons.Outlined.Savings, iconColor = AccentBlue, onClick = onOpenWeeklySavings),
                    FlatRow("Map", subtitle = "Real Rwanda map, self-hosted", icon = Icons.Outlined.Map, iconColor = AccentTeal, onClick = onOpenMap),
                ),
            )
        }
        item {
            IconGridSection("Quick access", listOf(
                "Mini" to Icons.Outlined.Apps,
                "Games" to Icons.Outlined.SportsEsports,
                "Bank" to Icons.Outlined.AccountBalance,
                "Pick" to Icons.Outlined.Star
            ))
        }
        item {
            MiniAppsSection(
                onWalletBalance = {
                    context.startActivity(android.content.Intent(context, rw.itunda.app.miniapps.WalletBalanceMiniAppActivity::class.java))
                },
                onPayBills = {
                    context.startActivity(android.content.Intent(context, rw.itunda.app.miniapps.PayBillsMiniAppActivity::class.java))
                },
                onRewardTasks = {
                    context.startActivity(android.content.Intent(context, rw.itunda.app.miniapps.RewardTasksMiniAppActivity::class.java))
                },
                onInsurance = {
                    context.startActivity(android.content.Intent(context, rw.itunda.app.miniapps.InsuranceMiniAppActivity::class.java))
                }
            )
        }
        // Real Partner SDK section (2026-07-17) -- lists REAL approved third-party
        // mini-apps from GET /api/v1/mini-apps/catalog (services/backend/partners),
        // closing the mobile half of docs/TOSS_PARITY_MATRIX.md's Partner SDK row.
        // Empty when the catalog has no approved entries yet (a real, honest empty
        // state, not hidden entirely, so this section's existence is itself visible
        // proof the mechanism is wired up end to end). Follows the exact same
        // FlatSection/tap-to-launch pattern as MiniAppsSection above, on purpose --
        // this is meant to read as a natural extension of first-party mini-apps, not a
        // separately-styled bolt-on.
        if (partnerMiniApps.isNotEmpty()) {
            item {
                FlatSection(
                    title = "Partner mini-apps",
                    rows = partnerMiniApps.map { app ->
                        FlatRow(
                            title = app.name,
                            subtitle = if (loadingPartnerAppId == app.id) "Loading..." else app.description,
                            onClick = {
                                if (loadingPartnerAppId == null) {
                                    loadingPartnerAppId = app.id
                                    coroutineScope.launch {
                                        rw.itunda.app.miniapps.PartnerMiniAppLoader.launch(
                                            activity = context as android.app.Activity,
                                            app = app,
                                            onError = { message -> partnerLoadError = message },
                                        )
                                        loadingPartnerAppId = null
                                    }
                                }
                            }
                        )
                    }
                )
            }
        }
        item {
            IconGridSection(
                "Recent services",
                listOf(
                    "Open acct" to Icons.Outlined.AddCircleOutline,
                    "Photo transfer" to Icons.Outlined.CameraAlt,
                    "Verify" to Icons.Outlined.VerifiedUser,
                    "Send" to Icons.Outlined.Send,
                    "Group" to Icons.Outlined.Group,
                    "Property" to Icons.Outlined.HomeWork,
                    "Insurance" to Icons.Outlined.Shield,
                    "More" to Icons.Outlined.MoreHoriz
                ),
                // Real KYC submission screen (found 2026-07-22 fully built on the
                // backend with zero UI anywhere) -- "Verify" was previously a
                // decorative icon with no click behavior at all, same as every other
                // item in this grid; only this one now has a real destination.
                onItemClick = { label -> if (label == "Verify") onOpenIdentity() },
            )
        }
        item {
            FlatSection("Financial services", listOf(
                FlatRow("Open account", subtitle = "Itunda Wallet, other banks, RSE brokerage", icon = Icons.Outlined.AddCircleOutline, iconColor = AccentBlue),
                FlatRow("My assets", subtitle = "Accounts, loans, RSE holdings, cards, points", icon = Icons.Outlined.PieChart, iconColor = AccentPurple, onClick = onOpenOverview),
                // Real Transfer full page (2026-07-24), matching real Toss's own 송금
                // row here ("자동이체 · 더치페이" subtitle) -- groups Send money/
                // Auto-transfer/history in one place instead of Home's Send button
                // (which stays a quick recipient-picker, unchanged) being the only
                // entry point.
                FlatRow("Transfer", subtitle = "Auto-transfer, split a bill", icon = Icons.AutoMirrored.Outlined.Send, iconColor = AccentBlue, onClick = onOpenTransferHub),
                FlatRow("Request money", subtitle = "Generate a real payment request code", icon = Icons.Outlined.RequestQuote, iconColor = AccentBlue, onClick = onOpenRequestMoney),
                FlatRow("Auto top-up", subtitle = "Refill your wallet automatically from a linked account", icon = Icons.Outlined.Autorenew, iconColor = AccentBlue, onClick = onOpenAutoTopUp),
                FlatRow("Get a loan", subtitle = "Personal, salary-backed, SME working capital", icon = Icons.Outlined.AccountBalanceWallet, iconColor = AccentBlue, onClick = onOpenLoans),
                FlatRow("Credit score", subtitle = "Free check, alternative data", icon = Icons.Outlined.TrendingUp, iconColor = AccentPurple, onClick = onOpenCreditScore),
                FlatRow("Spending", subtitle = "Real, ledger-based category breakdown", icon = Icons.Outlined.PieChart, iconColor = AccentBlue, onClick = onOpenSpending),
                FlatRow("Digital certificate", subtitle = "Sign agreements in Itunda", icon = Icons.Outlined.VerifiedUser, iconColor = AccentTeal, onClick = onOpenCertificate),
                FlatRow("26-week savings", subtitle = "Escalating weekly deposit plan", icon = Icons.Outlined.Savings, iconColor = AccentBlue, onClick = onOpenWeeklySavings),
                FlatRow("12-month deposit", subtitle = "Interest paid upfront, principal locked", icon = Icons.Outlined.Savings, iconColor = AccentPurple, onClick = onOpenUpfrontDeposit),
                FlatRow("Mini account", subtitle = "Capped starter wallet, ages 7-18", icon = Icons.Outlined.Savings, iconColor = AccentTeal, onClick = onOpenMiniWallet),
                FlatRow("Group account", subtitle = "Shared account with dues and split expenses", icon = Icons.Outlined.Group, iconColor = AccentPurple, onClick = onOpenGroupAccounts),
                FlatRow("Rides", subtitle = "Request a ride or drive for real fares", icon = Icons.Outlined.DirectionsCar, iconColor = AccentBlue, onClick = onOpenRides),
                FlatRow("Foreign currency", subtitle = "Hold and convert USD, EUR, GBP", icon = Icons.Outlined.CurrencyExchange, iconColor = AccentBlue, onClick = onOpenForeignCurrency),
                FlatRow("Mobile plan", subtitle = "MTN, Airtel, broadband", icon = Icons.Outlined.Public, iconColor = AccentTeal)
            ))
        }

        // Everything below is modeled directly on the real Toss Bank
        // 갈아타기/신용카드/체크카드/서비스/외화/목돈굴리기/연금/대출/알림 및 동의/고객센터
        // reference screens (user-provided, 2026-07-10), adapted to Rwanda
        // rails per docs/FACT_CHECKED_TOSS_RWANDA_MAP.md's established
        // mapping (REG/WASAC/Irembo/RRA, MTN MoMo/Airtel Money, RSE tickers,
        // RSSB pension) rather than left as Korean-market content.
        item {
            FlatSection("Switch & save", listOf(
                FlatRow("Switch your personal loan", trailing = "12% ~ 24%", trailingIsLink = true, icon = Icons.Outlined.AccountBalanceWallet, iconColor = AccentBlue),
                FlatRow("Switch your rent deposit loan", trailing = "9% ~ 15%", trailingIsLink = true, icon = Icons.Outlined.HomeWork, iconColor = AccentTeal),
                FlatRow("Switch your SME loan", trailing = "11% ~ 22%", trailingIsLink = true, icon = Icons.Outlined.Storefront, iconColor = AccentTeal)
            ))
        }
        item {
            FlatSection("Cards", listOf(
                FlatRow("Itunda Card", trailing = "5% back on bills", trailingIsLink = true, icon = Icons.Outlined.CreditCard, iconColor = AccentRed),
                FlatRow("Virtual card", trailing = "Instant issue", icon = Icons.Outlined.CreditCard, iconColor = AccentGray)
            ))
        }
        item {
            FlatSection("Services", listOf(
                FlatRow("Rent deposit protection", icon = Icons.Outlined.HomeWork, iconColor = AccentBlue),
                FlatRow("Recurring payments", icon = Icons.Outlined.Description, iconColor = AccentBlue),
                FlatRow("Import recurring payments", icon = Icons.Outlined.LocalShipping, iconColor = AccentGray),
                FlatRow("REG & WASAC bills", icon = Icons.Outlined.Bolt, iconColor = AccentBlue, onClick = {
                    context.startActivity(android.content.Intent(context, rw.itunda.app.miniapps.PayBillsMiniAppActivity::class.java))
                }),
                FlatRow("Claim interest now", icon = Icons.Outlined.Bolt, iconColor = AccentPurple, onClick = onClaimInterest),
                FlatRow("SME income tax estimate", icon = Icons.Outlined.Savings, iconColor = AccentOrange),
                // Real split-bill (found 2026-07-22 fully built with zero UI anywhere)
                // lives inside a specific group's own thread (Talk tab), not a
                // standalone flow -- this row hands off there rather than duplicating
                // a group picker.
                FlatRow("Split a bill with friends", icon = Icons.Outlined.Groups, iconColor = AccentBlue, onClick = onSwitchToTalk),
                FlatRow("Shared calendar", icon = Icons.Outlined.CalendarMonth, iconColor = AccentBlue),
                FlatRow("Kids' allowance tasks", icon = Icons.Outlined.CheckCircle, iconColor = AccentOrange)
            ))
        }
        item {
            FlatSection("Foreign currency", listOf(
                FlatRow("Foreign currency wallet", trailing = "100% rate preference", trailingIsLink = true, icon = Icons.Outlined.AccountBalanceWallet, iconColor = AccentPurple),
                FlatRow("International transfer", icon = Icons.Outlined.AttachMoney, iconColor = AccentBlue)
            ))
        }
        item {
            FlatSection("Grow your money", listOf(
                FlatRow("RSE stocks", subtitle = "BOK, MTNR, BLR, IMR, CMR, EQTY", icon = Icons.Outlined.ShowChart, iconColor = AccentTeal),
                FlatRow("Bonds & fixed income", trailing = "7.5% ~ 12%", trailingIsLink = true, icon = Icons.Outlined.AccountBalance, iconColor = AccentBlue),
                FlatRow("IPO schedule", icon = Icons.Outlined.TrendingUp, iconColor = AccentRed),
                FlatRow("Brokerage account", trailing = "Up to 30,000 RWF", trailingIsLink = true, icon = Icons.Outlined.AccountBalance, iconColor = AccentTeal)
            ))
        }
        item {
            FlatSection("Pension", listOf(
                FlatRow("Check my RSSB pension", icon = Icons.Outlined.AccountBalance, iconColor = AccentBlue),
                FlatRow("Pension products", icon = Icons.Outlined.Percent, iconColor = AccentBlue)
            ))
        }
        item {
            FlatSection("Loans", listOf(
                FlatRow("Check my max limit", icon = Icons.Outlined.TrendingUp, iconColor = AccentPurple, onClick = onOpenLoans),
                FlatRow("Personal loan", trailing = "11% ~ 24%", trailingIsLink = true, icon = Icons.Outlined.AccountBalanceWallet, iconColor = AccentBlue, onClick = onOpenLoans)
            ))
        }
        item {
            FlatSection("Notifications & consent", listOf(
                FlatRow("Notifications", showChevron = true),
                FlatRow("Credit data usage policy", showChevron = true),
                FlatRow("Privacy policy", showChevron = true),
                FlatRow("Terms & consent", showChevron = true)
            ))
        }
        item {
            FlatSection("Support", listOf(
                FlatRow("FAQ", showChevron = true),
                FlatRow("Live chat", showChevron = true),
                FlatRow("Call support", showChevron = true),
                FlatRow("Report an issue with a transaction", showChevron = true, onClick = onOpenSupport),
                FlatRow("My support tickets", showChevron = true, onClick = onOpenSupport),
                FlatRow("Announcements", showChevron = true)
            ))
        }
    }

    // Real, honest failure surface for the partner mini-app download/reload flow
    // (2026-07-17) -- a partner's bundle is arbitrary remote content fetched at tap
    // time, so a real network/HTTP/reload failure must be shown, not silently dropped.
    val currentPartnerLoadError = partnerLoadError
    if (currentPartnerLoadError != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { partnerLoadError = null },
            title = { Text("Couldn't load mini-app") },
            text = { Text(currentPartnerLoadError) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { partnerLoadError = null }) { Text("OK") }
            }
        )
    }
}

// Real Naver-style "My" personal hub (2026-07-22), replacing what used to be this
// bottom tab's entire content (the exhaustive service catalog, now MenuScreen above)
// -- at the user's direct request: "My should be like Naver style My since we have
// shopping and eats and other products where users need to easily get track of their
// orders, reservation, favorites." Every number/row here is a real fetched count or
// preview, not decoration -- the same "no fabricated numbers" discipline this whole
// app already follows elsewhere.
// Real My-activity screen (2026-07-24: trimmed to just this) -- "Quick links" and
// "My account" used to duplicate rows this screen's own content, back when it was the
// only way to reach them; now that All/MenuScreen is the primary bottom tab and already
// carries both of those sections itself, keeping a second copy here would just be
// stale duplication, not a real second path to anything. What's left is genuinely
// unique to this screen: real per-product order/favorite/listing tracking, the
// Naver-Pay-style addition this screen was built for in the first place.
@Composable
private fun MyTab(
    onBack: () -> Unit,
    onSwitchToShop: () -> Unit = {},
    onSwitchToHood: () -> Unit = {},
) {
    var shopOrders by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<List<rw.itunda.core.network.OrderDto>>(emptyList()) }
    var eatsOrders by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<List<rw.itunda.core.network.EatsOrderDto>>(emptyList()) }
    var favoriteListingsCount by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0) }
    var favoriteJobPostsCount by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0) }
    var favoritePropertyListingsCount by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0) }
    var favoriteRestaurantsCount by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0) }
    var myListingsCount by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0) }
    var myJobPostsCount by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0) }
    var myPropertyListingsCount by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0) }

    LaunchedEffect(Unit) {
        // Each fetch independent and best-effort -- one product's API hiccup must
        // never blank the rest of this real personal-activity summary.
        try { shopOrders = rw.itunda.core.network.NetworkClient.apiService.getMyOrders().orders } catch (_: Exception) { }
        try { eatsOrders = rw.itunda.core.network.NetworkClient.apiService.getMyEatsOrders().orders } catch (_: Exception) { }
        try { favoriteListingsCount = rw.itunda.core.network.NetworkClient.apiService.getMyFavoriteListings().favorites.size } catch (_: Exception) { }
        try { favoriteJobPostsCount = rw.itunda.core.network.NetworkClient.apiService.getMyFavoriteJobPosts().favorites.size } catch (_: Exception) { }
        try { favoritePropertyListingsCount = rw.itunda.core.network.NetworkClient.apiService.getMyFavoritePropertyListings().favorites.size } catch (_: Exception) { }
        try { favoriteRestaurantsCount = rw.itunda.core.network.NetworkClient.apiService.getMyFavoriteRestaurants().favorites.size } catch (_: Exception) { }
        try { myListingsCount = rw.itunda.core.network.NetworkClient.apiService.getMyListings().listings.size } catch (_: Exception) { }
        try { myJobPostsCount = rw.itunda.core.network.NetworkClient.apiService.getMyJobPosts().posts.size } catch (_: Exception) { }
        try { myPropertyListingsCount = rw.itunda.core.network.NetworkClient.apiService.getMyPropertyListings().listings.size } catch (_: Exception) { }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = Ids.layout.screenHorizontal, vertical = Ids.layout.screenVertical),
        verticalArrangement = Arrangement.spacedBy(Ids.layout.cardGap),
    ) {
        item { BackTopBar("My", onBack) }
        // Real order tracking -- Naver Pay/Shopping's own "My" tab leads with recent
        // orders across every product, not a settings list. Shows the real 3 most
        // recent orders per product; tapping switches to that product's own tab where
        // the full MyCommerceOrdersView/MyEatsOrdersView already lives.
        if (shopOrders.isNotEmpty() || eatsOrders.isNotEmpty()) {
            item { Text("My orders", color = TossText, fontSize = 19.sp, fontWeight = FontWeight.Bold) }
            items(shopOrders.take(3)) { order ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onSwitchToShop).padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text("Shop order", color = TossText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(order.status, color = TossSecondary, fontSize = 13.sp)
                    }
                    Text("RWF %,.0f".format(order.totalAmount), color = TossText, fontSize = 15.sp)
                }
            }
            items(eatsOrders.take(3)) { order ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onSwitchToShop).padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text("Eats order", color = TossText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(order.status, color = TossSecondary, fontSize = 13.sp)
                    }
                    Text("RWF %,.0f".format(order.totalAmount), color = TossText, fontSize = 15.sp)
                }
            }
        }
        // Real favorites/wishlist tracking across every product with one -- counts are
        // real (GET .../favorites on each module), tapping switches to the product's
        // own tab where its dedicated WISHLIST view already lives (Marketplace/Jobs/
        // Property under Hood, restaurants under Shop's Eats toggle) -- an honest,
        // one-more-tap scope, not a full deep link into the nested sub-view.
        item { Text("My favorites", color = TossText, fontSize = 19.sp, fontWeight = FontWeight.Bold) }
        item {
            FlatSection(
                "",
                listOf(
                    FlatRow("Marketplace wishlist", trailing = "$favoriteListingsCount", icon = Icons.Outlined.FavoriteBorder, iconColor = AccentRed, onClick = onSwitchToHood),
                    FlatRow("Jobs wishlist", trailing = "$favoriteJobPostsCount", icon = Icons.Outlined.FavoriteBorder, iconColor = AccentRed, onClick = onSwitchToHood),
                    FlatRow("Property wishlist", trailing = "$favoritePropertyListingsCount", icon = Icons.Outlined.FavoriteBorder, iconColor = AccentRed, onClick = onSwitchToHood),
                    FlatRow("Restaurant favorites", trailing = "$favoriteRestaurantsCount", icon = Icons.Outlined.FavoriteBorder, iconColor = AccentRed, onClick = onSwitchToShop),
                ),
            )
        }
        // Real "my own posts" tracking (Marketplace/Jobs/Property listings I created)
        // -- the same Naver-style "track your own activity" pattern as orders/favorites
        // above, not just a settings list.
        item {
            FlatSection(
                "My listings",
                listOf(
                    FlatRow("Marketplace", trailing = "$myListingsCount", icon = Icons.Outlined.Storefront, iconColor = AccentBlue, onClick = onSwitchToHood),
                    FlatRow("Jobs posted", trailing = "$myJobPostsCount", icon = Icons.Outlined.Work, iconColor = AccentBlue, onClick = onSwitchToHood),
                    FlatRow("Property listed", trailing = "$myPropertyListingsCount", icon = Icons.Outlined.HomeWork, iconColor = AccentTeal, onClick = onSwitchToHood),
                ),
            )
        }
        // "My account" (My assets/Get a loan/Credit score/etc) deliberately dropped
        // here (2026-07-24) -- every one of those rows already lives in the All tab's
        // own "Financial services" section now that All is the primary bottom tab;
        // keeping a second copy here would just be stale duplication.
    }
}

/**
 * Real entry point for Apps-in-Itunda mini-apps -- launches the genuine
 * ReactActivity subclasses in rw.itunda.app.miniapps, each loading a real RN
 * bundle from packages/saronite/mini-apps, not a placeholder screen.
 *
 * Flat, no card wrapper -- matches the real Toss settings/service screens
 * (송금, 전체 서비스, 고객센터 reference screenshots, 2026-07-10): rows sit
 * directly on the screen background, grouped by a small label, separated by
 * hairline dividers, not floated in an isolated white/gray card island. The
 * earlier card-per-section treatment read as generic fintech-app UI, not
 * Toss's actual, much flatter composition.
 */
@Composable
private fun MiniAppsSection(
    onWalletBalance: () -> Unit,
    onPayBills: () -> Unit,
    onRewardTasks: () -> Unit,
    onInsurance: () -> Unit
) {
    FlatSection(
        title = "Mini apps",
        rows = listOf(
            FlatRow("Wallet balance", onClick = onWalletBalance),
            FlatRow("Pay bills", onClick = onPayBills),
            FlatRow("Reward tasks", onClick = onRewardTasks),
            FlatRow("Insurance", onClick = onInsurance)
        )
    )
}

internal data class FlatRow(
    val title: String,
    val subtitle: String? = null,
    val trailing: String? = null,
    val trailingIsLink: Boolean = false,
    val icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    val iconColor: Color = AccentBlue,
    val showChevron: Boolean = false,
    val onClick: () -> Unit = {}
)

/**
 * The real Toss list pattern, matched directly against the reference
 * screenshots (user-provided, 2026-07-10) of 갈아타기/신용카드/체크카드/서비스/
 * 외화/목돈굴리기/연금/대출: a bold white section header (not a small gray
 * label), then plain rows with NO divider between them and NO card
 * background -- only a gap between different sections. Every row in that
 * product-list pattern carries a small colorful square icon (never a
 * chevron); a right-aligned value in brand blue appears only when there's
 * a real number/status to show (interest rate, discount). A second,
 * separate pattern exists for legal/settings lists (알림 및 동의, 고객센터):
 * no icon at all, plain chevron on the right -- selected per-row via
 * showChevron since both patterns can appear in the same screen.
 */
@Composable
internal fun FlatSection(title: String, rows: List<FlatRow>) {
    Column {
        Text(
            title,
            color = TossText,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        rows.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = row.onClick)
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    if (row.icon != null) {
                        Box(
                            modifier = Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(row.iconColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(row.icon, contentDescription = null, modifier = Modifier.size(19.dp), tint = Color.White)
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                    }
                    Column {
                        Text(row.title, color = TossText, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                        if (row.subtitle != null) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(row.subtitle, color = TossTertiary, fontSize = 13.sp)
                        }
                    }
                }
                if (row.trailing != null) {
                    Text(
                        row.trailing,
                        color = if (row.trailingIsLink) TossBlue else TossSecondary,
                        fontSize = 15.sp,
                        fontWeight = if (row.trailingIsLink) FontWeight.SemiBold else FontWeight.Normal
                    )
                } else if (row.showChevron) {
                    Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = TossTertiary)
                }
            }
        }
    }
}

@Composable
internal fun PlainTopBar(title: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = TossText, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("...", color = TossText, fontSize = 24.sp)
    }
}

@Composable
private fun PromoBannerCard() {
    Card(
        shape = RoundedCornerShape(Ids.layout.cardCornerRadius),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF5D2FE6)),
        elevation = CardDefaults.cardElevation(defaultElevation = Ids.layout.cardElevation),
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(220.dp).padding(20.dp)) {
            Column {
                Text(
                    // Toss's real open-source emoji font (github.com/toss/tossface),
                    // bundled from the actual release asset -- not a generic system
                    // emoji glyph.
                    "🎁 Limited gift for Rwanda",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = rw.itunda.core.designsystem.theme.TossFaceFontFamily
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("25,000", color = Color.White, fontSize = 54.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(modifier = Modifier.height(10.dp))
                Box(modifier = Modifier.clip(RoundedCornerShape(18.dp)).background(Color(0xFFEF56FF)).padding(horizontal = 26.dp, vertical = 12.dp)) {
                    Text("Redeem for free", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun PointPill(label: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(TossChip)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(label, color = TossText, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BenefitsVisitCard() {
    Card(
        shape = RoundedCornerShape(Ids.layout.cardCornerRadius),
        colors = CardDefaults.cardColors(containerColor = TossCard),
        elevation = CardDefaults.cardElevation(defaultElevation = Ids.layout.cardElevation),
    ) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("Visit 3 of 4 services and earn points", color = TossText, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            listOf(
                "Happy lottery" to Icons.Outlined.Casino,
                "Push the button" to Icons.Outlined.TouchApp,
                "Try on" to Icons.Outlined.Checkroom,
                "Bring friends" to Icons.Outlined.PersonAddAlt
            ).forEach { (title, icon) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(TossChip), contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = TossText)
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(title, modifier = Modifier.weight(1f), color = TossText, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    IdsButton("Visit", onClick = {}, variant = IdsButtonVariant.Tinted, size = IdsButtonSize.Small)
                }
            }
        }
    }
}

@Composable
private fun CashbackChanceCard() {
    Card(
        shape = RoundedCornerShape(Ids.layout.cardCornerRadius),
        colors = CardDefaults.cardColors(containerColor = TossCard),
        elevation = CardDefaults.cardElevation(defaultElevation = Ids.layout.cardElevation),
    ) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                "🍀 3 chances to get money back",
                color = TossText,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = rw.itunda.core.designsystem.theme.TossFaceFontFamily
            )
            Text("We will notify you when new chances are available", color = TossSecondary, fontSize = 15.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF246BFF)), contentAlignment = Alignment.Center) {
                    // Was the Korean Won symbol ("₩") -- wrong currency
                    // entirely for a Rwanda app; real icon now.
                    Icon(Icons.Outlined.CurrencyExchange, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color.White)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("RWF 5,000", color = TossText, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("BK account -> TUYIZERE Eric", color = TossSecondary)
                }
                IdsButton("Get back", onClick = {}, variant = IdsButtonVariant.Tinted, size = IdsButtonSize.Small)
            }
        }
    }
}

@Composable
private fun SearchBar(placeholder: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(TossCardSoft)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(placeholder, color = TossSecondary, fontSize = 16.sp)
    }
}

// BackTopBar relocated 2026-07-23 to core/designsystem/components/HoodShared.kt while
// extracting Community into :features:community:impl -- every call site across :app
// now imports it from there instead.

@Composable
private fun MapPlaceholder() {
    Card(
        shape = RoundedCornerShape(Ids.layout.cardCornerRadius),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFEFE4D7)),
        elevation = CardDefaults.cardElevation(defaultElevation = Ids.layout.cardElevation),
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.BottomCenter) {
            Box(modifier = Modifier.padding(bottom = 18.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xFF202228)).padding(horizontal = 20.dp, vertical = 10.dp)) {
                Text("5 nearby stores", color = TossText, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun PayFeatureCard() {
    Card(
        shape = RoundedCornerShape(Ids.layout.cardCornerRadius),
        colors = CardDefaults.cardColors(containerColor = TossCard),
        elevation = CardDefaults.cardElevation(defaultElevation = Ids.layout.cardElevation),
    ) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(TossChip), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Storefront, contentDescription = null, modifier = Modifier.size(18.dp), tint = TossBlue)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("itunda pay", color = TossSecondary)
                    Text("30% rewards at partner stores", color = TossBlue, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                }
                IdsButton("Find store", onClick = {}, variant = IdsButtonVariant.Tinted, size = IdsButtonSize.Small)
            }
            Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(TossCardSoft).padding(18.dp)) {
                Text("Apply pay money and points automatically", color = TossSecondary, fontSize = 16.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(TossChip), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Language, contentDescription = null, modifier = Modifier.size(18.dp), tint = TossText)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("How to pay online", color = TossText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("Use Itunda Pay on e-commerce and partner stores", color = TossSecondary)
                }
                IdsButton("See", onClick = {}, variant = IdsButtonVariant.Tinted, size = IdsButtonSize.Small)
            }
        }
    }
}

// Was a text navbar -- "ID | Support | Settings" with pipe separators --
// a website convention with no equivalent anywhere in real Toss. The
// 전체 (All) tab top bar is just the user's name plus a single settings
// icon button; support/ID live as rows further down the list, not up here.
@Composable
private fun AllTopBar(onOpenSettings: () -> Unit = {}, onOpenMyTab: (() -> Unit)? = null) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("TUYIZERE ERIC", color = TossText, fontWeight = FontWeight.Bold, fontSize = 26.sp)
        Row {
            // Real My-activity screen (2026-07-24, inverted from Menu) -- see
            // TossTab.All's own doc comment: the profile icon now leads to the
            // personal-activity screen (orders/favorites/listings), the exact reverse
            // of this bar's old Menu icon. Optional/nil so HomeTopBar's own reuse of a
            // similar bar isn't affected.
            if (onOpenMyTab != null) {
                IdsIconButton(Icons.Outlined.Person, contentDescription = "My activity", onClick = onOpenMyTab)
            }
            // Real Settings screen (2026-07-12, see SettingsScreen.kt) -- previously
            // wired directly to logout with no screen behind it at all.
            IdsIconButton(Icons.Outlined.Settings, contentDescription = "Settings", onClick = onOpenSettings)
        }
    }
}

// Was rendering item.take(1) -- the first letter of the label -- as the
// "icon" in every grid tile across the app (Mini/Games/Bank/Pick all
// showed as plain letters M/G/B/P). Real icons per item now; this is the
// single biggest reason the app read as a wireframe rather than Toss.
@Composable
private fun IconGridSection(
    title: String,
    items: List<Pair<String, androidx.compose.ui.graphics.vector.ImageVector>>,
    onItemClick: (String) -> Unit = {},
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(title, color = TossSecondary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        val chunked = items.chunked(4)
        chunked.forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                rowItems.forEach { (label, icon) ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f).clickable { onItemClick(label) },
                    ) {
                        Box(modifier = Modifier.size(54.dp).clip(RoundedCornerShape(18.dp)).background(TossCardSoft), contentAlignment = Alignment.Center) {
                            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = TossText)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(label, color = TossSecondary, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}


@Preview(showBackground = true)
@Composable
fun PreviewItundaAppScreen() {
    ItundaAppScreen()
}
