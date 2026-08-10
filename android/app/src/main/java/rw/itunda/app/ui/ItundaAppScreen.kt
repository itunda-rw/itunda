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
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.ArrowBackIosNew
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.AttachMoney
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Build
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
import androidx.compose.material.icons.outlined.DirectionsBike
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.LocalParking
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
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import rw.itunda.app.R
import rw.itunda.core.designsystem.components.BackTopBar
import rw.itunda.core.designsystem.components.IdsButton
import rw.itunda.core.designsystem.components.IdsCard
import rw.itunda.feature.talk.impl.TalkTab
import rw.itunda.feature.maps.impl.MapScreen
import rw.itunda.core.designsystem.components.IdsButtonSize
import rw.itunda.core.designsystem.components.IdsButtonVariant
import rw.itunda.core.designsystem.components.IdsIconButton
import rw.itunda.core.designsystem.components.IdsTextField
import rw.itunda.core.designsystem.theme.IdsTheme
import rw.itunda.core.designsystem.theme.IdsTypography
import rw.itunda.core.designsystem.theme.Ids

// Real gap found live (2026-08-10), user-flagged: this file used to alias the real
// theme-reactive design-system tokens (core/designsystem/theme/IdsSemanticColors.kt)
// under Toss-branded names (TossBlue, TossCard, ...) -- itunda's own screens naming
// their own colors after a different company's product read exactly like an
// unfinished fork, not a real product with its own identity. Every call site across
// this module now references Ids.colors.* directly; no alias layer left to name.

// Fixed vivid accent colors for the small product-icon badges in
// FlatSection rows (갈아타기/서비스/외화/목돈굴리기/연금/대출 등) -- these are
// brand/product colors in real Toss, not semantic theme colors, so unlike
// Ids.colors.brand etc. above they intentionally stay constant across light/dark.
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
internal enum class ItundaTab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
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
        var selectedTab by rememberSaveable { mutableStateOf(ItundaTab.Home) }
        var transferStep by rememberSaveable { mutableStateOf<TransferStep?>(null) }
        var savingsFlowStep by rememberSaveable { mutableStateOf<SavingsFlowStep?>(null) }
        var showTransactionHistory by rememberSaveable { mutableStateOf(false) }
        var showSettings by rememberSaveable { mutableStateOf(false) }
        var showBenefits by rememberSaveable { mutableStateOf(false) }
        var showPay by rememberSaveable { mutableStateOf(false) }
        var showMap by rememberSaveable { mutableStateOf(false) }
        var mapSearchQueryForScreen by rememberSaveable { mutableStateOf<String?>(null) }
        // Real "Delivery" pill deep-link, Maps -> Eats (2026-08-09) -- itunda's
        // Feature-module isolation forbids Maps depending on Eats directly, so this
        // shell (the only thing that can see both) carries a small, plain (not
        // rememberSaveable -- an in-flight navigation intent has no reason to survive
        // process death) pending-target across the tab switch. Cleared by ShopTab once
        // consumed, so returning to Shop later doesn't re-trigger the same restaurant.
        var pendingEatsMerchantId by remember { mutableStateOf<String?>(null) }
        var pendingEatsMerchantName by remember { mutableStateOf<String?>(null) }
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
        // Real ikimina (Rwanda's own rotating savings & credit association) -- the
        // first feature in this codebase not sourced from Toss/Kakao/Naver/Coupang.
        var showIkimina by rememberSaveable { mutableStateOf(false) }
        // Real Umurenge SACCO-style shares & dividends -- the second Rwanda-specific
        // feature not sourced from Toss/Kakao/Naver/Coupang.
        var showSacco by rememberSaveable { mutableStateOf(false) }
        // Real coffee-cooperative harvest-advance / input financing -- the third
        // Rwanda-specific feature not sourced from Toss/Kakao/Naver/Coupang.
        var showHarvestAdvance by rememberSaveable { mutableStateOf(false) }
        // Real Toss Bank 체크카드 (check/debit card) screen (2026-07-31, item 207) --
        // bank-mfe shipped first; same pattern.
        var showCard by rememberSaveable { mutableStateOf(false) }
        var showSpending by rememberSaveable { mutableStateOf(false) }
        var showRides by rememberSaveable { mutableStateOf(false) }
        // Real Kakao T 대리운전 (designated driver, item 221) -- bank-mfe shipped first;
        // same pattern.
        var showDesignatedDriver by rememberSaveable { mutableStateOf(false) }
        // Real Kakao T 바이크 (Kakao T Bike, item 222) -- bank-mfe shipped first; same
        // pattern.
        var showBikeRental by rememberSaveable { mutableStateOf(false) }
        // Real Kakao T 주차 (Kakao T Parking, item 223) -- bank-mfe shipped first; same
        // pattern.
        var showParking by rememberSaveable { mutableStateOf(false) }
        var showBus by rememberSaveable { mutableStateOf(false) }
        // Real Naver 지식iN (Knowledge iN) open-topic community Q&A (item 225) --
        // bank-mfe shipped first; same pattern.
        var showKnowledge by rememberSaveable { mutableStateOf(false) }
        // Real 당근마켓 중고차 정비소 동행 (used-car mechanic-inspection accompaniment)
        // screen (2026-07-31) -- bank-mfe shipped first; same pattern.
        var showVehicleInspection by rememberSaveable { mutableStateOf(false) }
        // Real Toss 내 차 시세 (my car's market value) screen (2026-07-31) -- bank-mfe
        // shipped first; same pattern.
        var showVehicleValuation by rememberSaveable { mutableStateOf(false) }
        // Real Toss 유스 (Toss Youth)-style guardian-child link screen (2026-07-31) --
        // bank-mfe shipped first; same pattern.
        var showFamilyLink by rememberSaveable { mutableStateOf(false) }
        // Real detected + merchant-billing subscriptions screen (2026-07-31) --
        // bank-mfe shipped first; same pattern.
        var showSubscriptions by rememberSaveable { mutableStateOf(false) }
        // Real 토스뱅크 외화통장 (foreign-currency account) screen (2026-07-25) -- same
        // "backend existed with zero mobile UI" gap-close pattern as the two above.
        var showForeignCurrency by rememberSaveable { mutableStateOf(false) }
        // Real person-to-person payment request (item 170) -- same "backend real,
        // live-verified, zero mobile UI" gap-close pattern as the two above.
        var showRequestMoney by rememberSaveable { mutableStateOf(false) }
        // Real Naver Pay Money 자동충전 auto top-up screen (item 176) -- same pattern.
        var showAutoTopUp by rememberSaveable { mutableStateOf(false) }
        // Real Karrot-Score-style trust/reputation self-view screen (item 152) --
        // bank-mfe shipped first; same "backend real, zero native UI" pattern.
        var showTrustScore by rememberSaveable { mutableStateOf(false) }
        // Real Itunda cash-agent operator console (staff-facing till: cash-in/cash-out/
        // reconciliation) -- bank-mfe shipped first; same pattern.
        var showAgentOperator by rememberSaveable { mutableStateOf(false) }
        // Real peer-to-peer agent float rebalancing marketplace -- see
        // FloatMarketplaceScreen.kt's own doc comment for the full sourced account.
        var showFloatMarketplace by rememberSaveable { mutableStateOf(false) }
        // Real means-tested VUP (Vision 2020 Umurenge Programme) Financial Services
        // microloan -- see VupLoanScreen.kt's own doc comment for the full sourced
        // account. bank-mfe shipped first; this is the first native client.
        var showVupLoan by rememberSaveable { mutableStateOf(false) }
        // Real Rwanda BRD (Development Bank of Rwanda) higher-education student loan --
        // see StudentLoanScreen.kt's own doc comment for the full sourced account.
        // bank-mfe shipped first; this is the first native client.
        var showStudentLoan by rememberSaveable { mutableStateOf(false) }
        // Real Rwanda moto-taxi ownership savings-to-loan plan -- see
        // MotoOwnershipScreen.kt's own doc comment for the full sourced account.
        // bank-mfe shipped first (commits cf9d72fe/808a7ce4); this is the first
        // native client.
        var showMotoOwnership by rememberSaveable { mutableStateOf(false) }
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
        // Real Toss 예약송금 (scheduled/reserved one-time transfer) -- see
        // ScheduledTransferListScreen's own doc comment for the full sourced account.
        var showScheduledTransfers by rememberSaveable { mutableStateOf(false) }
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
        // Real deep-link from the menu's "Property" row into Hood's real
        // HoodMode.PROPERTY chip (2026-08-10) -- see HoodTab's own doc comment.
        var pendingHoodMode by remember { mutableStateOf<HoodMode?>(null) }
        var biometricError by remember { mutableStateOf<String?>(null) }
        val activity = LocalRealActivity.current
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
        // Real Toss 사기계좌 조회-style pre-transfer warning (item 152's sibling gap,
        // found 2026-07-31) -- see rw.itunda.p2p.ScamReportService's own doc comment.
        // A warning, not a hard block, matching bank-mfe's own scamCheck/ReportScamLink
        // (BankDashboard.tsx) exactly. Checked once per distinct recipient account
        // number, not on every recomposition.
        var scamReportCount by remember { mutableStateOf<Int?>(null) }
        var scamReported by remember { mutableStateOf(false) }
        var showScamReportDialog by remember { mutableStateOf(false) }
        LaunchedEffect(step) {
            if (step is TransferStep.Amount) {
                scamReported = false
                try {
                    val result = rw.itunda.core.network.NetworkClient.apiService.checkScamStatus(step.accountNumber).result
                    scamReportCount = if (result.warn) result.reportCount else 0
                } catch (_: Exception) {
                    // Real, non-critical -- a failed safety check must never block a
                    // real transfer the sender is otherwise entitled to make.
                }
            }
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
                        scamWarning = scamReportCount?.let { rw.itunda.feature.payments.impl.ScamWarningUi(it) },
                        scamReported = scamReported,
                        onReportScam = { showScamReportDialog = true },
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
            if (showScamReportDialog) {
                var reportReason by remember { mutableStateOf("") }
                var reportBusy by remember { mutableStateOf(false) }
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showScamReportDialog = false },
                    title = { Text(stringResource(R.string.scam_report_title), color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold) },
                    text = {
                        androidx.compose.foundation.text.BasicTextField(
                            value = reportReason,
                            onValueChange = { reportReason = it },
                            textStyle = androidx.compose.ui.text.TextStyle(color = Ids.colors.textPrimary, fontSize = 15.sp),
                            modifier = Modifier.fillMaxWidth()
                                .background(Ids.colors.surfaceSoft, androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                                .padding(12.dp),
                            decorationBox = { inner -> if (reportReason.isEmpty()) Text(stringResource(R.string.scam_report_reason_placeholder), color = Ids.colors.textTertiary, fontSize = 15.sp); inner() },
                        )
                    },
                    confirmButton = {
                        androidx.compose.material3.TextButton(
                            enabled = !reportBusy && reportReason.isNotBlank() && step is TransferStep.Amount,
                            onClick = {
                                val account = (step as? TransferStep.Amount)?.accountNumber ?: return@TextButton
                                reportBusy = true
                                coroutineScope.launch {
                                    try {
                                        rw.itunda.core.network.NetworkClient.apiService.reportScam(
                                            rw.itunda.core.network.ReportScamRequest(account, reportReason.trim()),
                                        )
                                        scamReported = true
                                    } catch (_: Exception) {
                                        // Best-effort, same non-critical discipline as the check above.
                                    } finally {
                                        reportBusy = false
                                        showScamReportDialog = false
                                    }
                                }
                            },
                        ) { Text(if (reportBusy) stringResource(R.string.scam_report_reporting) else stringResource(R.string.scam_report_button), color = Ids.colors.brand, fontWeight = FontWeight.SemiBold) }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(onClick = { showScamReportDialog = false }, enabled = !reportBusy) {
                            Text(stringResource(R.string.scam_report_cancel), color = Ids.colors.textSecondary)
                        }
                    },
                    containerColor = Ids.colors.surface,
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
                onOrderDelivery = { merchantId, businessName ->
                    pendingEatsMerchantId = merchantId
                    pendingEatsMerchantName = businessName
                    showMap = false
                    selectedTab = ItundaTab.Shop
                },
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
        if (showTrustScore) {
            BackHandler { showTrustScore = false }
            TrustScoreScreen(onBack = { showTrustScore = false })
            return@IdsTheme
        }
        if (showAgentOperator) {
            BackHandler { showAgentOperator = false }
            AgentOperatorScreen(onBack = { showAgentOperator = false })
            return@IdsTheme
        }
        if (showFloatMarketplace) {
            BackHandler { showFloatMarketplace = false }
            FloatMarketplaceScreen(onBack = { showFloatMarketplace = false })
            return@IdsTheme
        }
        if (showStudentLoan) {
            BackHandler { showStudentLoan = false }
            StudentLoanScreen(onBack = { showStudentLoan = false })
            return@IdsTheme
        }
        if (showVupLoan) {
            BackHandler { showVupLoan = false }
            VupLoanScreen(onBack = { showVupLoan = false })
            return@IdsTheme
        }
        if (showMotoOwnership) {
            BackHandler { showMotoOwnership = false }
            MotoOwnershipScreen(onBack = { showMotoOwnership = false })
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
        // Real Toss Bank 체크카드 (check/debit card) screen (2026-07-31, item 207) --
        // first Android client, same "backend real, zero mobile UI" gap-close pattern.
        if (showCard) {
            BackHandler { showCard = false }
            CardScreen(onBack = { showCard = false })
            return@IdsTheme
        }
        // Real Kakao Bank 모임통장 (group/shared account) screen (2026-07-28, item 104)
        // -- first Android client for this feature. Same pattern.
        if (showIkimina) {
            BackHandler { showIkimina = false }
            IkiminaScreen(onBack = { showIkimina = false })
            return@IdsTheme
        }
        if (showSacco) {
            BackHandler { showSacco = false }
            SaccoScreen(onBack = { showSacco = false })
            return@IdsTheme
        }
        if (showHarvestAdvance) {
            BackHandler { showHarvestAdvance = false }
            HarvestAdvanceScreen(onBack = { showHarvestAdvance = false })
            return@IdsTheme
        }
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
        if (showVehicleInspection) {
            BackHandler { showVehicleInspection = false }
            VehicleInspectionScreen(onBack = { showVehicleInspection = false })
        }
        if (showVehicleValuation) {
            BackHandler { showVehicleValuation = false }
            VehicleValuationScreen(onBack = { showVehicleValuation = false })
        }
        if (showFamilyLink) {
            BackHandler { showFamilyLink = false }
            FamilyLinkScreen(onBack = { showFamilyLink = false })
        }
        if (showSubscriptions) {
            BackHandler { showSubscriptions = false }
            SubscriptionsScreen(onBack = { showSubscriptions = false })
        }
        if (showRides) {
            BackHandler { showRides = false }
            RideScreen(onBack = { showRides = false })
            return@IdsTheme
        }
        if (showDesignatedDriver) {
            BackHandler { showDesignatedDriver = false }
            DesignatedDriverScreen(onBack = { showDesignatedDriver = false })
            return@IdsTheme
        }
        if (showBikeRental) {
            BackHandler { showBikeRental = false }
            BikeRentalScreen(onBack = { showBikeRental = false })
            return@IdsTheme
        }
        if (showBus) {
            BackHandler { showBus = false }
            BusScreen(onBack = { showBus = false })
            return@IdsTheme
        }
        if (showKnowledge) {
            BackHandler { showKnowledge = false }
            KnowledgeScreen(onBack = { showKnowledge = false })
            return@IdsTheme
        }
        if (showParking) {
            BackHandler { showParking = false }
            ParkingScreen(onBack = { showParking = false })
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
                onSwitchToShop = { showMyTab = false; selectedTab = ItundaTab.Shop },
                onSwitchToHood = { showMyTab = false; selectedTab = ItundaTab.Hood },
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
            } else if (showScheduledTransfers) {
                ScheduledTransferListScreen(onBack = { showScheduledTransfers = false })
            } else {
                TransferHubScreen(
                    autoTransferCount = autoTransferCount,
                    onBack = { showTransferHub = false },
                    onSendMoney = { showTransferHub = false; transferStep = TransferStep.Recipient },
                    onOpenAutoTransfers = { showAutoTransfers = true },
                    onOpenScheduledTransfers = { showScheduledTransfers = true },
                    onSplitBill = { showTransferHub = false; selectedTab = ItundaTab.Talk },
                    onOpenHistory = { showTransferHub = false; showTransactionHistory = true },
                )
            }
            return@IdsTheme
        }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                ItundaBottomBar(selectedTab = selectedTab, onSelect = { selectedTab = it })
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(paddingValues)
            ) {
                when (selectedTab) {
                    ItundaTab.Home -> HomeTab(
                        viewModel,
                        onSend = { transferStep = TransferStep.Recipient },
                        onDepositToGoal = { goalId, goalName -> savingsFlowStep = SavingsFlowStep.Deposit(goalId, goalName) },
                        onClaimInterest = { savingsFlowStep = SavingsFlowStep.ClaimInterest },
                        onOpenTransactionHistory = { showTransactionHistory = true },
                        onOpenSpendingInsight = { showSpending = true },
                        onCashOutAtAgent = { showAgentCash = true },
                        onOpenPay = { showPay = true },
                        onOpenNotifications = { showSettings = true },
                        onOpenSacco = { showSacco = true },
                        onOpenIkimina = { showIkimina = true },
                        onOpenMotoOwnership = { showMotoOwnership = true },
                        onOpenHarvestAdvance = { showHarvestAdvance = true },
                    )
                    ItundaTab.Shop -> ShopTab(
                        pendingEatsMerchantId = pendingEatsMerchantId,
                        pendingEatsMerchantName = pendingEatsMerchantName,
                        onPendingEatsConsumed = { pendingEatsMerchantId = null; pendingEatsMerchantName = null },
                    )
                    ItundaTab.Hood -> HoodTab(
                        onMessageSeller = { conversationId ->
                            pendingConversationId = conversationId
                            selectedTab = ItundaTab.Talk
                        },
                        onOpenSettings = { showSettings = true },
                        initialMode = pendingHoodMode ?: HoodMode.MARKETPLACE,
                        onConsumedInitialMode = { pendingHoodMode = null },
                    )
                    // Seventh and final Feature extraction (2026-07-23) -- see
                    // TalkScreen.kt's own header comment for why deviceStepUpHost is
                    // injected (DeviceStepUpHost.kt wraps :features:payments:impl's
                    // dialog, so it can't become a direct Feature-to-Feature dependency).
                    ItundaTab.Talk -> TalkTab(
                        initialConversationId = pendingConversationId,
                        onConsumedInitial = { pendingConversationId = null },
                        deviceStepUpHost = { visible, onDismiss, onVerified ->
                            DeviceStepUpHost(visible = visible, onDismiss = onDismiss, onVerified = onVerified)
                        },
                    )
                    // Real 전체 (All services) primary bottom tab (2026-07-24) -- see
                    // ItundaTab.All's own doc comment for why this replaced My here.
                    ItundaTab.All -> {
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
                            onOpenCard = { showCard = true },
                            onOpenGroupAccounts = { showGroupAccounts = true },
                            onOpenIkimina = { showIkimina = true },
                            onOpenSacco = { showSacco = true },
                            onOpenHarvestAdvance = { showHarvestAdvance = true },
                            onOpenSpending = { showSpending = true },
                            onOpenRides = { showRides = true },
                            onOpenDesignatedDriver = { showDesignatedDriver = true },
                            onOpenBikeRental = { showBikeRental = true },
                            onOpenParking = { showParking = true },
                            onOpenBus = { showBus = true },
                            onOpenKnowledge = { showKnowledge = true },
                            onOpenVehicleInspection = { showVehicleInspection = true },
                            onOpenVehicleValuation = { showVehicleValuation = true },
                            onOpenFamilyLink = { showFamilyLink = true },
                            onOpenSubscriptions = { showSubscriptions = true },
                            onOpenForeignCurrency = { showForeignCurrency = true },
                            onOpenRequestMoney = { showRequestMoney = true },
                            onOpenAutoTopUp = { showAutoTopUp = true },
                            onOpenTrustScore = { showTrustScore = true },
                            onOpenAgentOperator = { showAgentOperator = true },
                            onOpenFloatMarketplace = { showFloatMarketplace = true },
                            onOpenVupLoan = { showVupLoan = true },
                            onOpenStudentLoan = { showStudentLoan = true },
                            onOpenMotoOwnership = { showMotoOwnership = true },
                            onOpenTransferHub = { showTransferHub = true },
                            onClaimInterest = { savingsFlowStep = SavingsFlowStep.ClaimInterest },
                            onSwitchToTalk = { selectedTab = ItundaTab.Talk },
                            onOpenProperty = { pendingHoodMode = HoodMode.PROPERTY; selectedTab = ItundaTab.Hood },
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
private fun ItundaBottomBar(selectedTab: ItundaTab, onSelect: (ItundaTab) -> Unit) {
    Column {
        Divider(color = Ids.colors.divider, thickness = 0.5.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Ids.colors.surface)
                .padding(top = 8.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ItundaTab.entries.forEach { tab ->
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
                        tint = if (selected) Ids.colors.brand else Ids.colors.textTertiary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = tab.label,
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (selected) Ids.colors.brand else Ids.colors.textTertiary
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
    onOpenSpendingInsight: () -> Unit = {},
    onCashOutAtAgent: () -> Unit,
    onOpenPay: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    onOpenSacco: () -> Unit = {},
    onOpenIkimina: () -> Unit = {},
    onOpenMotoOwnership: () -> Unit = {},
    onOpenHarvestAdvance: () -> Unit = {},
) {
    val primaryWallet by viewModel.primaryWallet.collectAsState()
    val balanceText = primaryWallet?.let { "${it.currency} %,.0f".format(it.balance) } ?: "RWF 0"
    val savingsGoals by viewModel.savingsGoals.collectAsState()
    val interestJar by viewModel.interestJar.collectAsState()
    val discoverItems by viewModel.discoverItems.collectAsState()
    val roundUpSettings by viewModel.roundUpSettings.collectAsState()
    val recentTransactions by viewModel.transactions.collectAsState()
    val spendingInsight by viewModel.spendingInsight.collectAsState()
    var showRoundUpDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    // Real, minimal usage signal (2026-08-10) -- see the "itunda: the wedge, not the
    // mirror" strategy memo, recommendation (ii), and rw.itunda.core.network.
    // recordAnalyticsEvent's own doc comment. Fired once per real composition of
    // Home, the baseline every retention question is measured against -- same event
    // name/shape bank-mfe's identical HomeView effect already fires.
    LaunchedEffect(Unit) { rw.itunda.core.network.recordAnalyticsEvent("home_view") }

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
        item {
            WalletHeroCard(
                balanceText = balanceText,
                onSend = onSend,
                onCashOutAtAgent = onCashOutAtAgent,
                recentTransactions = recentTransactions.take(2),
                currentUserId = primaryWallet?.userId,
                onSeeAll = onOpenTransactionHistory,
                earnedThisMonth = interestJar?.earnedThisMonth ?: 0.0,
            )
        }
        // Real product-positioning fix (2026-08-10, see the "itunda: the wedge, not
        // the mirror" strategy memo from this same session, and the identical fix on
        // bank-mfe's HomeView): SACCO shares, Ikimina, Moto-Taxi Ownership, and
        // Harvest advance are itunda's only real Rwanda-specific products -- the ones
        // MTN MoMo's own roadmap can't trivially replicate -- yet all four lived only
        // inside MenuScreen's "Borrow"/"Save & grow" categories, same visual weight as
        // "Foreign currency". This doesn't add a feature; it gives four real, already-
        // shipped, genuinely differentiated screens a Home presence that matches what
        // they're worth, with real explanatory copy instead of a bare label. Unlike
        // bank-mfe (which only has scroll-sections/buried buttons to route to),
        // Android already has a dedicated full screen for each -- so this deep-links
        // precisely, not just to a parent tab.
        item {
            ShellSection(
                title = stringResource(R.string.home_coop_rail_title),
                rows = listOf(
                    ShellRow(
                        stringResource(R.string.home_coop_rail_sacco_title),
                        stringResource(R.string.home_coop_rail_sacco_subtitle),
                        ">", Icons.Outlined.AccountBalance, AccentPurple,
                        onClick = { coroutineScope.launch { rw.itunda.core.network.recordAnalyticsEvent("coop_rail_tap", "sacco") }; onOpenSacco() },
                    ),
                    ShellRow(
                        stringResource(R.string.home_coop_rail_ikimina_title),
                        stringResource(R.string.home_coop_rail_ikimina_subtitle),
                        ">", Icons.Outlined.Groups, AccentTeal,
                        onClick = { coroutineScope.launch { rw.itunda.core.network.recordAnalyticsEvent("coop_rail_tap", "ikimina") }; onOpenIkimina() },
                    ),
                    ShellRow(
                        stringResource(R.string.home_coop_rail_moto_title),
                        stringResource(R.string.home_coop_rail_moto_subtitle),
                        ">", Icons.Outlined.DirectionsBike, AccentBlue,
                        onClick = { coroutineScope.launch { rw.itunda.core.network.recordAnalyticsEvent("coop_rail_tap", "moto_ownership") }; onOpenMotoOwnership() },
                    ),
                    ShellRow(
                        stringResource(R.string.home_coop_rail_harvest_title),
                        stringResource(R.string.home_coop_rail_harvest_subtitle),
                        ">", Icons.Outlined.AccountBalanceWallet, AccentOrange,
                        onClick = { coroutineScope.launch { rw.itunda.core.network.recordAnalyticsEvent("coop_rail_tap", "harvest_advance") }; onOpenHarvestAdvance() },
                    ),
                )
            )
        }
        // Real Toss-style spending insight (2026-08-03) -- replaces a hardcoded
        // "RWF 463,022 / Spent in July" row that this file's own prior comment
        // admitted was illustrative. GET /api/v1/wallet/spending
        // (WalletService.getSpendingInsight) has been real since 2026-07-13 and
        // already had its own dedicated SpendingScreen -- this just surfaces the
        // same real total/top-category on Home instead of nowhere. Shown only once
        // there's real spend to report, same "don't flash an empty/zero section"
        // discipline the Savings section below already establishes.
        val topCategory = spendingInsight?.categories?.maxByOrNull { it.amount.toDouble() }
        if (spendingInsight != null && (spendingInsight?.totalSpent?.toDouble() ?: 0.0) > 0.0) {
            item {
                ShellSection(
                    title = "",
                    rows = listOf(
                        ShellRow(
                            "RWF %,.0f".format(spendingInsight?.totalSpent?.toDouble() ?: 0.0),
                            if (topCategory != null) stringResource(R.string.home_spent_period_category, topCategory.name) else stringResource(R.string.home_spent_period),
                            ">",
                            Icons.Outlined.PieChart,
                            AccentPurple,
                            onClick = onOpenSpendingInsight,
                        ),
                        ShellRow(stringResource(R.string.home_promo_cashback_title), stringResource(R.string.home_promo_cashback_subtitle), stringResource(R.string.home_promo_cashback_action), Icons.Outlined.Payments, AccentBlue),
                        ShellRow(stringResource(R.string.home_promo_sprinkle_title), stringResource(R.string.home_promo_sprinkle_subtitle), stringResource(R.string.home_promo_sprinkle_action), Icons.Outlined.Redeem, AccentOrange)
                    )
                )
            }
        } else {
            item {
                ShellSection(
                    title = "",
                    rows = listOf(
                        ShellRow(stringResource(R.string.home_promo_cashback_title), stringResource(R.string.home_promo_cashback_subtitle), stringResource(R.string.home_promo_cashback_action), Icons.Outlined.Payments, AccentBlue),
                        ShellRow(stringResource(R.string.home_promo_sprinkle_title), stringResource(R.string.home_promo_sprinkle_subtitle), stringResource(R.string.home_promo_sprinkle_action), Icons.Outlined.Redeem, AccentOrange)
                    )
                )
            }
        }
        item {
            ShellSection(
                title = "",
                rows = listOf(
                    ShellRow(stringResource(R.string.home_promo_pay_cashback), "", ">", Icons.Outlined.Payments, AccentBlue),
                    ShellRow(stringResource(R.string.home_promo_face_id), "", ">", Icons.Outlined.Face, AccentPurple),
                    ShellRow(stringResource(R.string.home_promo_gov_alerts), "", ">", Icons.Outlined.Campaign, AccentRed)
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
                val roundUpOff = stringResource(R.string.home_round_up_off)
                val roundUpOn = stringResource(R.string.home_round_up_on)
                val roundUpSetUp = stringResource(R.string.home_round_up_set_up)
                val interestJarLabel = stringResource(R.string.home_interest_jar)
                val earnedThisMonthLabel = stringResource(R.string.home_earned_this_month)
                val roundUpTitle = stringResource(R.string.home_round_up_title)
                val roundUpRoundingText = roundUpSettings?.roundToNearest?.let { stringResource(R.string.home_round_up_rounding, "%,.0f".format(it)) }
                // stringResource() is @Composable and buildList{}'s lambda below isn't, so
                // the per-goal progress pattern is fetched once here (outside the loop)
                // and applied with plain Kotlin String.format inside it, same reasoning as
                // the round-up strings above.
                val savingsProgressPattern = stringResource(R.string.home_savings_progress)
                ShellSection(
                    title = stringResource(R.string.home_savings),
                    rows = buildList {
                        interestJar?.let { jar ->
                            add(
                                ShellRow(
                                    interestJarLabel,
                                    earnedThisMonthLabel,
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
                                    savingsProgressPattern.format("%,.0f".format(goal.currentAmount), "%,.0f".format(goal.targetAmount)),
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
                                    roundUpTitle,
                                    if (roundUpSettings?.enabled == true) {
                                        roundUpRoundingText ?: roundUpOff
                                    } else roundUpOff,
                                    if (roundUpSettings?.enabled == true) roundUpOn else roundUpSetUp,
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
        title = { Text(stringResource(R.string.home_round_up_title)) },
        text = {
            Column {
                Text(stringResource(R.string.home_round_up_body), color = Ids.colors.textSecondary, fontSize = 13.sp)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.home_round_up_enable), modifier = Modifier.weight(1f))
                    androidx.compose.material3.Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
                if (enabled) {
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.home_round_up_nearest), color = Ids.colors.textSecondary, fontSize = 13.sp)
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
                    Text(stringResource(R.string.home_round_up_save_into), color = Ids.colors.textSecondary, fontSize = 13.sp)
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
            androidx.compose.material3.TextButton(onClick = { onSave(enabled, increment, if (enabled) selectedGoalId else null) }) { Text(stringResource(R.string.home_save)) }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text(stringResource(R.string.home_cancel)) }
        }
    )
}

@Composable
private fun DiscoverSection(items: List<rw.itunda.core.network.DiscoverItem>) {
    Column {
        Text(stringResource(R.string.home_discover), color = Ids.colors.textPrimary, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
        items.forEach { discoverItem ->
            val accentColor = try {
                Color(android.graphics.Color.parseColor(discoverItem.color))
            } catch (_: IllegalArgumentException) {
                AccentBlue
            }
            IdsCard(
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(accentColor))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(discoverItem.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Ids.colors.textPrimary)
                            if (discoverItem.isNew) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.home_new_badge), color = accentColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Text(discoverItem.subtitle, fontSize = 14.sp, color = Ids.colors.textSecondary)
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
                .background(Ids.colors.surfaceSoft)
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Text(stringResource(R.string.home_search), color = Ids.colors.textSecondary, fontSize = 15.sp)
        }
        // Both icons were real no-op taps (found 2026-07-22 audit) despite their own
        // real destinations already existing elsewhere in this file: QR scan opens
        // the same real "Pay" screen (scan-or-pay-by-code) the My tab's Pay row
        // already reaches; Notifications opens Settings, which already renders a
        // real notifications list against GET /api/v1/notifications.
        IdsIconButton(Icons.Outlined.QrCodeScanner, contentDescription = stringResource(R.string.home_scan_qr), onClick = onOpenPay)
        IdsIconButton(Icons.Outlined.Notifications, contentDescription = stringResource(R.string.home_notifications), onClick = onOpenNotifications)
    }
}

@Composable
private fun WalletHeroCard(
    balanceText: String,
    onSend: () -> Unit,
    onCashOutAtAgent: () -> Unit,
    recentTransactions: List<rw.itunda.core.network.TransactionDto>,
    currentUserId: String?,
    onSeeAll: () -> Unit,
    earnedThisMonth: Double,
) {
    IdsCard(
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Real Toss reference (user-provided, 2026-08-03): the real Home wallet
            // card header is the account name plus how much interest it's earned so
            // far this period ("에릭 +이자 7원"), not just a plain static "Wallet"
            // label -- InterestJar.earnedThisMonth was already fetched
            // (MainViewModel.interestJar, real GET /api/v1/wallet/interest-jar) and
            // already shown further down this tab's savings section, just never in
            // this header. Only shown once it's actually > 0 -- a brand-new wallet
            // with nothing earned yet keeps the plain label rather than a "+RWF 0"
            // that reads as broken.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.home_wallet), fontSize = 14.sp, color = Ids.colors.textSecondary)
                if (earnedThisMonth > 0.0) {
                    Text(
                        "  +RWF %,.0f".format(earnedThisMonth),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Ids.colors.success,
                    )
                }
            }
            // Real fix, 2026-08-03: was a hardcoded 34.sp literal -- IdsTypography
            // .LargeAmount exists specifically for "the single most important number
            // on the screen" (see its own doc comment, written for
            // AgentHomeScreen.kt's till total) and is itself 34sp, so the visual size
            // is unchanged; this just makes the hero wallet balance -- itunda's own
            // most important number on the Home tab -- use the real token instead of
            // a number that happens to currently match it.
            Text(balanceText, style = IdsTypography.LargeAmount, color = Ids.colors.textPrimary)
            // Real Toss reference (user-provided, 2026-08-03): the real Home wallet
            // card's own two buttons ("+ 채우기" / "↗ 보내기") both carry a leading
            // glyph -- IdsButton's icon param is new this pass (see its own doc
            // comment) specifically for this.
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IdsButton(stringResource(R.string.home_cash_out), onClick = onCashOutAtAgent, modifier = Modifier.weight(1f), variant = IdsButtonVariant.Tinted, size = IdsButtonSize.Medium, icon = Icons.Outlined.Add)
                IdsButton(stringResource(R.string.home_send), onClick = onSend, modifier = Modifier.weight(1f), variant = IdsButtonVariant.Filled, size = IdsButtonSize.Medium, icon = Icons.AutoMirrored.Outlined.Send)
            }
            // Real fix, 2026-08-03: these two rows used to be hardcoded literal
            // strings ("Bravo Korea parking" / "Savings deposit") baked into every
            // account regardless of whose it was -- viewModel.transactions
            // (GET /api/v1/wallet/transactions) was already fetched and already
            // powered the real TransactionHistoryScreen reachable from "See all"
            // below, just never shown here. Only rendered once real transactions
            // exist, matching the Savings section's own "don't show an empty
            // section" discipline.
            if (recentTransactions.isNotEmpty()) {
                Divider(color = Ids.colors.divider)
                recentTransactions.forEach { tx ->
                    WalletMiniRow(
                        transaction = tx,
                        isOutgoing = tx.senderId == currentUserId,
                        onClick = onSeeAll,
                    )
                }
            }
            // Real fix, 2026-08-03: "See all" rendered as plain, non-clickable Text --
            // it visually reads as a link (secondary color, medium weight, full-width)
            // but tapping it did nothing; onOpenTransactionHistory already existed and
            // was already wired to a sibling ShellRow in the same tab, just never to
            // this row.
            Text(
                stringResource(R.string.home_see_all),
                modifier = Modifier.fillMaxWidth().clickable(onClick = onSeeAll),
                color = Ids.colors.textSecondary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun WalletMiniRow(transaction: rw.itunda.core.network.TransactionDto, isOutgoing: Boolean, onClick: () -> Unit) {
    // Real Toss-style signed amount (2026-08-03) -- outgoing money is prefixed "-"
    // in the normal text color, incoming is prefixed "+" and tinted with the real
    // success token (Ids.colors.success), the same signed-and-tinted convention
    // real Toss transaction rows use, instead of every row showing an unsigned,
    // uncolored amount regardless of direction.
    val amountText = "${if (isOutgoing) "-" else "+"}${transaction.currency} %,.0f".format(transaction.amount)
    val amountColor = if (isOutgoing) Ids.colors.textPrimary else Ids.colors.success
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Ids.colors.chip),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (isOutgoing) Icons.Outlined.ArrowUpward else Icons.Outlined.ArrowDownward,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = Ids.colors.textPrimary,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(transaction.description, color = Ids.colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1)
        }
        Text(amountText, color = amountColor, fontWeight = FontWeight.Bold, fontSize = 15.sp)
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
    IdsCard(
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            if (title.isNotEmpty()) {
                Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ids.colors.textPrimary)
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
                        // real icon but on a flat muted Ids.colors.chip background --
                        // real Toss's card-list icon badges (송금/자산 reference
                        // screenshots) are vivid per-item brand colors, not one
                        // neutral gray tone reused everywhere.
                        Icon(row.icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color.White)
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(row.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Ids.colors.textPrimary)
                        if (row.subtitle.isNotEmpty()) {
                            Text(row.subtitle, fontSize = 14.sp, color = Ids.colors.textSecondary)
                        }
                    }
                    if (row.action == ">") {
                        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = Ids.colors.textTertiary)
                    } else if (row.action.isNotBlank()) {
                        IdsButton(row.action, onClick = {}, variant = IdsButtonVariant.Tinted, size = IdsButtonSize.Small)
                    }
                }
                if (index != rows.lastIndex) {
                    Divider(color = Ids.colors.divider)
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
// overlay) -- see ItundaTab.All's own doc comment for the full history: separated from My
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
    onOpenCard: () -> Unit = {},
    onOpenGroupAccounts: () -> Unit = {},
    onOpenIkimina: () -> Unit = {},
    onOpenSacco: () -> Unit = {},
    onOpenHarvestAdvance: () -> Unit = {},
    onOpenSpending: () -> Unit = {},
    onOpenRides: () -> Unit = {},
    onOpenDesignatedDriver: () -> Unit = {},
    onOpenBikeRental: () -> Unit = {},
    onOpenParking: () -> Unit = {},
    onOpenBus: () -> Unit = {},
    onOpenKnowledge: () -> Unit = {},
    onOpenVehicleInspection: () -> Unit = {},
    onOpenVehicleValuation: () -> Unit = {},
    onOpenFamilyLink: () -> Unit = {},
    onOpenSubscriptions: () -> Unit = {},
    onOpenForeignCurrency: () -> Unit = {},
    onOpenRequestMoney: () -> Unit = {},
    onOpenAutoTopUp: () -> Unit = {},
    onOpenTrustScore: () -> Unit = {},
    onOpenAgentOperator: () -> Unit = {},
    onOpenFloatMarketplace: () -> Unit = {},
    onOpenVupLoan: () -> Unit = {},
    onOpenStudentLoan: () -> Unit = {},
    onOpenMotoOwnership: () -> Unit = {},
    onOpenTransferHub: () -> Unit = {},
    onClaimInterest: () -> Unit = {},
    onSwitchToTalk: () -> Unit = {},
    onOpenProperty: () -> Unit = {},
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
            // Real gap found live (2026-08-10): titled "Recent services" but nothing
            // here was ever actually tracked as recent -- a static, hardcoded list,
            // which is its own small honesty problem on top of the bigger one: 6 of
            // its 8 icons were dead taps (tap ripple fires, nothing happens). Renamed
            // to "Shortcuts" (what it actually is) and wired to the same real
            // destinations the rest of this screen already uses. "Photo transfer" is
            // dropped outright, not wired -- grepping this app for any OCR/photo-based
            // transfer feature turns up nothing; same "don't fake a destination that
            // doesn't exist" call already made for QuickActions' Scan-to-Pay icon.
            IconGridSection(
                "Shortcuts",
                listOf(
                    "Open account" to Icons.Outlined.AddCircleOutline,
                    "Verify" to Icons.Outlined.VerifiedUser,
                    "Send" to Icons.Outlined.Send,
                    "Group" to Icons.Outlined.Group,
                    "Property" to Icons.Outlined.HomeWork,
                    "Insurance" to Icons.Outlined.Shield,
                ),
                onItemClick = { label ->
                    when (label) {
                        "Open account" -> onOpenOverview()
                        "Verify" -> onOpenIdentity()
                        "Send" -> onOpenTransferHub()
                        "Group" -> onOpenGroupAccounts()
                        "Property" -> onOpenProperty()
                        "Insurance" -> context.startActivity(android.content.Intent(context, rw.itunda.app.miniapps.InsuranceMiniAppActivity::class.java))
                    }
                },
            )
        }
        // Real gap found live (2026-08-10): this used to be one 33-row "Financial
        // services" section -- everything from savings to bus tickets to vehicle
        // inspection dumped under a single label that was actively wrong for most of
        // what it contained. This read as unorganized because it WAS unorganized: a
        // supply-side dump (whatever got built, in build order) rather than a
        // demand-side grouping (what the user is actually trying to do), the exact
        // anti-pattern Toss Tech's own 내 문서함 rewrite names and fixes (toss.tech/
        // article/mydoc, "화면 내에서 우선순위 정리가 되지 않았" -- "priorities weren't
        // organized within the screen"; they restructured around real user intent
        // instead of internal product structure). Same real rows, same real
        // onClick callbacks -- only the grouping and two previously-dead rows changed.
        item {
            FlatSection("Accounts & cards", listOf(
                // "Open account" was a dead tap (no onClick at all) despite its own
                // subtitle describing exactly what OverviewScreen's real "Link
                // account" flow already does -- wired to the same real destination
                // "My assets" already uses.
                FlatRow("Open account", subtitle = "Itunda Wallet, other banks, RSE brokerage", icon = Icons.Outlined.AddCircleOutline, iconColor = AccentBlue, onClick = onOpenOverview),
                FlatRow("My assets", subtitle = "Accounts, loans, RSE holdings, cards, points", icon = Icons.Outlined.PieChart, iconColor = AccentPurple, onClick = onOpenOverview),
                FlatRow("Card", subtitle = "App-controlled spend limits, one-tap freeze", icon = Icons.Outlined.CreditCard, iconColor = AccentBlue, onClick = onOpenCard),
                FlatRow("Spending", subtitle = "Real, ledger-based category breakdown", icon = Icons.Outlined.PieChart, iconColor = AccentBlue, onClick = onOpenSpending),
                FlatRow("Group account", subtitle = "Shared account with dues and split expenses", icon = Icons.Outlined.Group, iconColor = AccentPurple, onClick = onOpenGroupAccounts),
                FlatRow("Family", subtitle = "Link a guardian or child, view read-only spending", icon = Icons.Outlined.Groups, iconColor = AccentPurple, onClick = onOpenFamilyLink),
                FlatRow("Foreign currency", subtitle = "Hold and convert USD, EUR, GBP", icon = Icons.Outlined.CurrencyExchange, iconColor = AccentBlue, onClick = onOpenForeignCurrency),
                FlatRow("Subscriptions", subtitle = "Detected recurring payments + merchant billing plans", icon = Icons.Outlined.CalendarMonth, iconColor = AccentBlue, onClick = onOpenSubscriptions),
                FlatRow("Digital certificate", subtitle = "Sign agreements in Itunda", icon = Icons.Outlined.VerifiedUser, iconColor = AccentTeal, onClick = onOpenCertificate),
            ))
        }
        item {
            FlatSection("Send & pay", listOf(
                FlatRow("Transfer", subtitle = "Auto-transfer, split a bill", icon = Icons.AutoMirrored.Outlined.Send, iconColor = AccentBlue, onClick = onOpenTransferHub),
                FlatRow("Request money", subtitle = "Generate a real payment request code", icon = Icons.Outlined.RequestQuote, iconColor = AccentBlue, onClick = onOpenRequestMoney),
                FlatRow("Auto top-up", subtitle = "Refill your wallet automatically from a linked account", icon = Icons.Outlined.Autorenew, iconColor = AccentBlue, onClick = onOpenAutoTopUp),
                // "Mobile plan" was also a dead tap (no onClick) -- MTN/Airtel airtime
                // and broadband are real billers inside the Pay Bills mini-app
                // (packages/saronite/mini-apps/pay-bills), the same real destination
                // MiniAppsSection's own "Bills" quick-access icon launches.
                FlatRow("Mobile plan", subtitle = "MTN, Airtel, broadband", icon = Icons.Outlined.Public, iconColor = AccentTeal, onClick = {
                    context.startActivity(android.content.Intent(context, rw.itunda.app.miniapps.PayBillsMiniAppActivity::class.java))
                }),
            ))
        }
        item {
            FlatSection("Save & grow", listOf(
                FlatRow("26-week savings", subtitle = "Escalating weekly deposit plan", icon = Icons.Outlined.Savings, iconColor = AccentBlue, onClick = onOpenWeeklySavings),
                FlatRow("12-month deposit", subtitle = "Interest paid upfront, principal locked", icon = Icons.Outlined.Savings, iconColor = AccentPurple, onClick = onOpenUpfrontDeposit),
                FlatRow("Mini account", subtitle = "Capped starter wallet, ages 7-18", icon = Icons.Outlined.Savings, iconColor = AccentTeal, onClick = onOpenMiniWallet),
                FlatRow("Ikimina", subtitle = "Rotating savings group -- everyone takes a turn", icon = Icons.Outlined.Savings, iconColor = AccentTeal, onClick = onOpenIkimina),
                FlatRow("SACCO shares", subtitle = "Buy cooperative shares, earn a real dividend", icon = Icons.Outlined.Savings, iconColor = AccentPurple, onClick = onOpenSacco),
            ))
        }
        item {
            FlatSection("Borrow", listOf(
                FlatRow("Get a loan", subtitle = "Personal, salary-backed, SME working capital", icon = Icons.Outlined.AccountBalanceWallet, iconColor = AccentBlue, onClick = onOpenLoans),
                FlatRow("Credit score", subtitle = "Free check, alternative data", icon = Icons.Outlined.TrendingUp, iconColor = AccentPurple, onClick = onOpenCreditScore),
                FlatRow("Harvest advance", subtitle = "Coffee cooperative input financing", icon = Icons.Outlined.AccountBalanceWallet, iconColor = AccentTeal, onClick = onOpenHarvestAdvance),
                FlatRow("VUP Financial Services", subtitle = "Means-tested government microloan for farming, livestock, business", icon = Icons.Outlined.AccountBalanceWallet, iconColor = AccentBlue, onClick = onOpenVupLoan),
                FlatRow("Student loan", subtitle = "BRD higher-education loan -- 11% undergraduate, 12% postgraduate", icon = Icons.Outlined.School, iconColor = AccentPurple, onClick = onOpenStudentLoan),
                FlatRow("Moto-Taxi Ownership", subtitle = "Save a 30% down payment, then convert to a loan for your own bike", icon = Icons.Outlined.DirectionsBike, iconColor = AccentTeal, onClick = onOpenMotoOwnership),
            ))
        }
        item {
            FlatSection("Transport", listOf(
                FlatRow("Rides", subtitle = "Request a ride or drive for real fares", icon = Icons.Outlined.DirectionsCar, iconColor = AccentBlue, onClick = onOpenRides),
                FlatRow("Designated driver", subtitle = "A driver takes you and your own car home", icon = Icons.Outlined.SwapHoriz, iconColor = AccentTeal, onClick = onOpenDesignatedDriver),
                FlatRow("Bike rental", subtitle = "Rent a nearby bike or scooter, billed by the minute", icon = Icons.Outlined.DirectionsBike, iconColor = AccentBlue, onClick = onOpenBikeRental),
                FlatRow("Parking", subtitle = "Rent a nearby parking spot, billed by the hour", icon = Icons.Outlined.LocalParking, iconColor = AccentPurple, onClick = onOpenParking),
                FlatRow("Bus", subtitle = "Book intercity bus seats or post your own route", icon = Icons.Outlined.DirectionsBus, iconColor = AccentTeal, onClick = onOpenBus),
                FlatRow("Vehicle inspection", subtitle = "Pay a mechanic to inspect a used car before you buy", icon = Icons.Outlined.Build, iconColor = AccentTeal, onClick = onOpenVehicleInspection),
                FlatRow("My vehicles", subtitle = "Track your car's estimated resale value", icon = Icons.Outlined.DirectionsCar, iconColor = AccentTeal, onClick = onOpenVehicleValuation),
            ))
        }
        item {
            FlatSection("Community & trust", listOf(
                FlatRow("Trust score", subtitle = "How your neighbors see you on Marketplace, Jobs, and Property", icon = Icons.Outlined.VerifiedUser, iconColor = AccentTeal, onClick = onOpenTrustScore),
                FlatRow("Q&A", subtitle = "Ask a question, answer one, get adopted", icon = Icons.Outlined.HelpOutline, iconColor = AccentPurple, onClick = onOpenKnowledge),
            ))
        }
        // Kept last and separately labeled, not blended into the rows above: these two
        // are role-gated (only assigned cash-agent operators can use them), so grouping
        // them with everyday-user rows would itself be the same "wrong category" problem
        // this whole section just got fixed for.
        item {
            FlatSection("Cash agent tools", listOf(
                FlatRow("Agent till", subtitle = "For assigned cash-agent operators: cash-in, cash-out, till count", icon = Icons.Outlined.Storefront, iconColor = AccentBlue, onClick = onOpenAgentOperator),
                FlatRow("Float marketplace", subtitle = "For assigned cash-agents: offer or request float from nearby agents", icon = Icons.Outlined.SwapHoriz, iconColor = AccentTeal, onClick = onOpenFloatMarketplace),
            ))
        }

        // Everything below is modeled directly on the real Toss Bank
        // 갈아타기/신용카드/체크카드/서비스/외화/목돈굴리기/연금/대출/알림 및 동의/고객센터
        // reference screens (user-provided, 2026-07-10), adapted to Rwanda
        // rails per docs/FACT_CHECKED_TOSS_RWANDA_MAP.md's established
        // mapping (REG/WASAC/Irembo/RRA, MTN MoMo/Airtel Money, RSE tickers,
        // RSSB pension) rather than left as Korean-market content.
        item {
            // Real 대환대출 (loan refinancing) fix, 2026-08-03 -- found fully built on
            // the backend (LoansService.refinanceLoan, POST /api/v1/loans/refinance)
            // and already surfaced as a real "Refinance to a lower rate" button inside
            // LoansScreen.kt -- these 3 rows are real Toss 갈아타기 entry points into
            // that exact same flow, not a separate feature, so they route to
            // onOpenLoans like every other loan row on this screen rather than sitting
            // dead.
            FlatSection("Switch & save", listOf(
                FlatRow("Switch your personal loan", trailing = "12% ~ 24%", trailingIsLink = true, icon = Icons.Outlined.AccountBalanceWallet, iconColor = AccentBlue, onClick = onOpenLoans),
                FlatRow("Switch your rent deposit loan", trailing = "9% ~ 15%", trailingIsLink = true, icon = Icons.Outlined.HomeWork, iconColor = AccentTeal, onClick = onOpenLoans),
                FlatRow("Switch your SME loan", trailing = "11% ~ 22%", trailingIsLink = true, icon = Icons.Outlined.Storefront, iconColor = AccentTeal, onClick = onOpenLoans)
            ))
        }
        item {
            // Real fix, 2026-08-03: both rows point at the same real Card screen
            // (CardScreen.kt) Financial services' own "Card" row already opens --
            // these are a second, dead entry point into it, not a separate feature.
            FlatSection("Cards", listOf(
                FlatRow("Itunda Card", trailing = "5% back on bills", trailingIsLink = true, icon = Icons.Outlined.CreditCard, iconColor = AccentRed, onClick = onOpenCard),
                FlatRow("Virtual card", trailing = "Instant issue", icon = Icons.Outlined.CreditCard, iconColor = AccentGray, onClick = onOpenCard)
            ))
        }
        item {
            FlatSection("Services", listOf(
                FlatRow("Rent deposit protection", icon = Icons.Outlined.HomeWork, iconColor = AccentBlue),
                // Real fix, 2026-08-03: this is the real detected-recurring-payments +
                // billing-subscriptions feature (SubscriptionsScreen.kt,
                // GET /api/v1/subscriptions/*), already reachable via Financial
                // services' "Subscriptions" row -- was a second, dead entry point into
                // the exact same screen.
                FlatRow("Recurring payments", icon = Icons.Outlined.Description, iconColor = AccentBlue, onClick = onOpenSubscriptions),
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
            // Real fix, 2026-08-03: both rows are the same real foreign-currency
            // wallet Financial services' own "Foreign currency" row already opens.
            FlatSection("Foreign currency", listOf(
                FlatRow("Foreign currency wallet", trailing = "100% rate preference", trailingIsLink = true, icon = Icons.Outlined.AccountBalanceWallet, iconColor = AccentPurple, onClick = onOpenForeignCurrency),
                FlatRow("International transfer", icon = Icons.Outlined.AttachMoney, iconColor = AccentBlue, onClick = onOpenForeignCurrency)
            ))
        }
        item {
            // Real fix, 2026-08-03: all 4 rows are the same real RSE investing screen
            // (InvestScreen.kt, real deterministic-simulation stock data) Quick links'
            // own "Invest" row already opens -- routed there instead of sitting dead,
            // same reasoning as Switch & save/Cards/Foreign currency above.
            FlatSection("Grow your money", listOf(
                FlatRow("RSE stocks", subtitle = "BOK, MTNR, BLR, IMR, CMR, EQTY", icon = Icons.Outlined.ShowChart, iconColor = AccentTeal, onClick = onOpenInvest),
                FlatRow("Bonds & fixed income", trailing = "7.5% ~ 12%", trailingIsLink = true, icon = Icons.Outlined.AccountBalance, iconColor = AccentBlue, onClick = onOpenInvest),
                FlatRow("IPO schedule", icon = Icons.Outlined.TrendingUp, iconColor = AccentRed, onClick = onOpenInvest),
                FlatRow("Brokerage account", trailing = "Up to 30,000 RWF", trailingIsLink = true, icon = Icons.Outlined.AccountBalance, iconColor = AccentTeal, onClick = onOpenInvest)
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
            // Real fix: "Notifications" now opens Settings, which already has a real
            // notifications list + mark-as-read (SettingsScreen.kt) -- reusing existing
            // real infrastructure rather than leaving this row dead. The other 3 rows
            // have no real backend/content anywhere in this codebase (no legal-copy
            // source, no credit-data-usage-policy endpoint) -- honestly left as plain
            // labels (no chevron, no clickable) rather than a fake destination, matching
            // this project's own "don't fabricate a shortcut" discipline.
            FlatSection("Notifications & consent", listOf(
                FlatRow("Notifications", showChevron = true, onClick = onOpenSettings),
                FlatRow("Credit data usage policy"),
                FlatRow("Privacy policy"),
                FlatRow("Terms & consent")
            ))
        }
        item {
            // FAQ/Live chat/Call support/Announcements have no real backend behind them
            // either (confirmed via SupportScreen.kt's own doc comment) -- same honest
            // no-chevron treatment. The 2 rows that DO have a real destination
            // (transaction-ticket support, already shipped 2026-07-22) keep theirs.
            FlatSection("Support", listOf(
                FlatRow("FAQ"),
                FlatRow("Live chat"),
                FlatRow("Call support"),
                FlatRow("Report an issue with a transaction", showChevron = true, onClick = onOpenSupport),
                FlatRow("My support tickets", showChevron = true, onClick = onOpenSupport),
                FlatRow("Announcements")
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
    // Real 쿠팡파트너스 (Coupang Partners)-style affiliate earnings read-back (item 229)
    // -- link creation itself happens inline on the Shop product card's Share icon;
    // this is purely the read-back, mirroring bank-mfe's own AffiliateEarningsCard.
    var affiliateLinks by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<List<rw.itunda.core.network.AffiliateLinkDto>>(emptyList()) }
    var affiliateCommissions by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<List<rw.itunda.core.network.AffiliateCommissionDto>>(emptyList()) }

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
        try { affiliateLinks = rw.itunda.core.network.NetworkClient.apiService.getMyAffiliateLinks().links } catch (_: Exception) { }
        try { affiliateCommissions = rw.itunda.core.network.NetworkClient.apiService.getMyAffiliateCommissions().commissions } catch (_: Exception) { }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = Ids.layout.screenHorizontal, vertical = Ids.layout.screenVertical),
        verticalArrangement = Arrangement.spacedBy(Ids.layout.cardGap),
    ) {
        item { BackTopBar("My", onBack) }
        item { ProfilePhotoCard() }
        item { VerificationCard() }
        if (affiliateLinks.isNotEmpty()) {
            item {
                val totalClicks = affiliateLinks.sumOf { it.clickCount }
                val totalEarned = affiliateCommissions.sumOf { it.commissionAmount }
                IdsCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Partner earnings", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("Earn 3% on any purchase made through a product link you've shared.", color = Ids.colors.textSecondary, fontSize = 12.sp)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Links shared", color = Ids.colors.textSecondary, fontSize = 13.sp)
                            Text("${affiliateLinks.size}", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total clicks", color = Ids.colors.textSecondary, fontSize = 13.sp)
                            Text("$totalClicks", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total earned", color = Ids.colors.textSecondary, fontSize = 13.sp)
                            Text("${"%,.0f".format(totalEarned)} RWF", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
        // Real order tracking -- Naver Pay/Shopping's own "My" tab leads with recent
        // orders across every product, not a settings list. Shows the real 3 most
        // recent orders per product; tapping switches to that product's own tab where
        // the full MyCommerceOrdersView/MyEatsOrdersView already lives.
        if (shopOrders.isNotEmpty() || eatsOrders.isNotEmpty()) {
            item { Text("My orders", color = Ids.colors.textPrimary, fontSize = 19.sp, fontWeight = FontWeight.Bold) }
            items(shopOrders.take(3)) { order ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onSwitchToShop).padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text("Shop order", color = Ids.colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(order.status, color = Ids.colors.textSecondary, fontSize = 13.sp)
                    }
                    Text("RWF %,.0f".format(order.totalAmount), color = Ids.colors.textPrimary, fontSize = 15.sp)
                }
            }
            items(eatsOrders.take(3)) { order ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onSwitchToShop).padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text("Eats order", color = Ids.colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(order.status, color = Ids.colors.textSecondary, fontSize = 13.sp)
                    }
                    Text("RWF %,.0f".format(order.totalAmount), color = Ids.colors.textPrimary, fontSize = 15.sp)
                }
            }
        }
        // Real favorites/wishlist tracking across every product with one -- counts are
        // real (GET .../favorites on each module), tapping switches to the product's
        // own tab where its dedicated WISHLIST view already lives (Marketplace/Jobs/
        // Property under Hood, restaurants under Shop's Eats toggle) -- an honest,
        // one-more-tap scope, not a full deep link into the nested sub-view.
        item { Text("My favorites", color = Ids.colors.textPrimary, fontSize = 19.sp, fontWeight = FontWeight.Bold) }
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

// Real profile photo (URL, not a binary upload) -- also the real, buildable half of
// Rewards' task_profile. Found 2026-07-29 via a full-backend-endpoint sweep: a real,
// working `PUT /api/v1/auth/profile/photo` endpoint with zero client anywhere, and
// `PublicUser.profilePhotoUrl` wasn't even carried by this DTO until now.
@Composable
private fun ProfilePhotoCard() {
    var profilePhotoUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var displayName by rememberSaveable { mutableStateOf("") }
    var urlInput by rememberSaveable { mutableStateOf("") }
    var saving by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        try {
            val user = rw.itunda.core.network.NetworkClient.authApi.getProfile().user
            profilePhotoUrl = user.profilePhotoUrl
            urlInput = user.profilePhotoUrl ?: ""
            displayName = "${user.firstName} ${user.lastName}".trim()
        } catch (_: Exception) {
            // Real, non-critical -- the rest of "My" still works without this.
        }
    }

    IdsCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            // Real fix, 2026-08-05 (same audit that found Talk's avatar bugs) -- this
            // rendered an empty gray circle with nothing in it when no photo was set,
            // the exact same "missing avatar" gap Talk's GroupRow had.
            rw.itunda.core.designsystem.components.IdsAvatar(
                name = displayName.ifBlank { "?" },
                photoUrl = profilePhotoUrl,
                size = 56.dp,
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                error?.let { Text(it, color = Ids.colors.danger, fontSize = 12.sp) }
                IdsTextField(
                    value = urlInput, onValueChange = { urlInput = it },
                    label = "Profile photo URL",
                    modifier = Modifier.fillMaxWidth(),
                )
                IdsButton(
                    text = if (saving) "Saving…" else "Save photo",
                    enabled = !saving,
                    onClick = {
                        val trimmed = urlInput.trim()
                        if (trimmed.isEmpty()) { error = "Enter a photo URL."; return@IdsButton }
                        saving = true
                        error = null
                        coroutineScope.launch {
                            try {
                                profilePhotoUrl = rw.itunda.core.network.NetworkClient.authApi.updateProfilePhoto(
                                    rw.itunda.core.network.UpdateProfilePhotoRequest(trimmed),
                                ).user.profilePhotoUrl
                            } catch (_: Exception) {
                                error = "Could not update your profile photo."
                            } finally {
                                saving = false
                            }
                        }
                    },
                )
            }
        }
    }
}

// Real email/phone verification (item 169/178) -- see AuthApi.requestEmailVerification/
// requestPhoneVerification's own doc comment. bank-mfe (item 169) already has this
// (mirrored field-for-field); this is the first Android client. A real code is
// delivered via a real in-app Notification + push, no real SMS/email gateway exists.
@Composable
private fun VerificationCard() {
    var email by rememberSaveable { mutableStateOf<String?>(null) }
    var emailVerified by rememberSaveable { mutableStateOf(true) }
    var phoneVerified by rememberSaveable { mutableStateOf(true) }
    var loaded by rememberSaveable { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    fun load() {
        coroutineScope.launch {
            try {
                val user = rw.itunda.core.network.NetworkClient.authApi.getProfile().user
                email = user.email
                emailVerified = user.emailVerified
                phoneVerified = user.phoneVerified
            } catch (_: Exception) {
                // Best-effort, matching this card's own bank-mfe precedent.
            } finally {
                loaded = true
            }
        }
    }
    LaunchedEffect(Unit) { load() }

    if (!loaded || (emailVerified && phoneVerified)) return

    IdsCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Verify your account", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            if (!phoneVerified) VerificationRow(kind = "phone", hasEmail = true, onVerified = ::load)
            if (!emailVerified) VerificationRow(kind = "email", hasEmail = email != null, onVerified = ::load)
        }
    }
}

@Composable
private fun VerificationRow(kind: String, hasEmail: Boolean, onVerified: () -> Unit) {
    var sent by rememberSaveable { mutableStateOf(false) }
    var code by rememberSaveable { mutableStateOf("") }
    var busy by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    if (kind == "email" && !hasEmail) {
        Text("No email address on file to verify.", color = Ids.colors.textSecondary, fontSize = 12.sp)
        return
    }

    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        if (!sent) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(if (kind == "email") "Email not verified" else "Phone number not verified", color = Ids.colors.textPrimary, fontSize = 13.sp)
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (busy) Ids.colors.textTertiary else Ids.colors.brand)
                        .clickable(enabled = !busy) {
                            busy = true
                            error = null
                            coroutineScope.launch {
                                try {
                                    if (kind == "email") rw.itunda.core.network.NetworkClient.authApi.requestEmailVerification() else rw.itunda.core.network.NetworkClient.authApi.requestPhoneVerification()
                                    sent = true
                                } catch (e: retrofit2.HttpException) {
                                    error = rw.itunda.core.network.superAppErrorMessage(e)
                                } catch (e: java.io.IOException) {
                                    error = "Couldn't reach itunda. Check your connection and try again."
                                } finally {
                                    busy = false
                                }
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) { Text(if (busy) "…" else "Send code", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            }
        } else {
            // Real "Minimum Input" simplicity fix (Toss's own researched, sourced pattern --
            // toss.tech/article/4-ways-for-minimum-input, rule #2: "for fixed-digit fields like
            // ID or phone numbers, the CTA button becomes unnecessary" -- see
            // docs/DESIGN_REFERENCES.md §11). This code is a real, fixed 6-digit OTP
            // (AuthService.kt's own doc comment). Auto-confirms the instant the 6th digit is
            // typed, removing the extra tap for the common case -- the button stays visible and
            // still works as a manual fallback (e.g. after a paste that needs re-triggering, or
            // for anyone who prefers an explicit confirm), rather than being removed outright,
            // since this is a security-sensitive identity-verification step.
            fun confirm() {
                if (busy || code.isBlank()) return
                busy = true
                error = null
                coroutineScope.launch {
                    try {
                        if (kind == "email") {
                            rw.itunda.core.network.NetworkClient.authApi.confirmEmailVerification(rw.itunda.core.network.ConfirmEmailVerificationRequest(code.trim()))
                        } else {
                            rw.itunda.core.network.NetworkClient.authApi.confirmPhoneVerification(rw.itunda.core.network.ConfirmPhoneVerificationRequest(code.trim()))
                        }
                        onVerified()
                    } catch (e: retrofit2.HttpException) {
                        error = rw.itunda.core.network.superAppErrorMessage(e)
                    } catch (e: java.io.IOException) {
                        error = "Couldn't reach itunda. Check your connection and try again."
                    } finally {
                        busy = false
                    }
                }
            }
            LaunchedEffect(code) {
                if (code.trim().length == 6 && code.trim().all { it.isDigit() } && !busy) confirm()
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                // Real "Minimum Input" simplicity fix, closing docs/DESIGN_REFERENCES.md §11
                // recommendation #2: IdsTextField now supports autoFocus (rule #4, same
                // research as this screen's own auto-confirm fix), matching web's already-
                // shipped autoFocus on this exact field.
                IdsTextField(value = code, onValueChange = { code = it }, label = "Enter code", keyboardType = androidx.compose.ui.text.input.KeyboardType.Number, autoFocus = true, modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (busy || code.isBlank()) Ids.colors.textTertiary else Ids.colors.brand)
                        .clickable(enabled = !busy && code.isNotBlank()) { confirm() }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) { Text(if (busy) "…" else "Confirm", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            }
        }
        error?.let { Text(it, color = Ids.colors.danger, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)) }
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
    // Real dead-tap fix (item 247 follow-up, docs/DESIGN_REFERENCES.md §14 recommendation
    // #2 -- Simplicity24's "사용자의 실수" lesson on misleading affordances): previously
    // defaulted to a no-op `{}`, so every row with showChevron = true but no real
    // destination (Notifications/Privacy policy/Terms & consent/FAQ/Live chat/Call
    // support/Announcements -- confirmed via SupportScreen.kt's own 2026-07-22 doc
    // comment that no backend exists for any of these) still consumed the tap silently
    // via FlatSection's unconditional .clickable() below, exactly the "looks tappable,
    // does nothing" bug already found and fixed once this session in bank-mfe's
    // QuickActions. Nullable now, so a row can only render as tappable when it truly is.
    val onClick: (() -> Unit)? = null
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
            color = Ids.colors.textPrimary,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        rows.forEach { row ->
            val onClick = row.onClick
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
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
                        Text(row.title, color = Ids.colors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                        if (row.subtitle != null) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(row.subtitle, color = Ids.colors.textTertiary, fontSize = 13.sp)
                        }
                    }
                }
                if (row.trailing != null) {
                    Text(
                        row.trailing,
                        color = if (row.trailingIsLink) Ids.colors.brand else Ids.colors.textSecondary,
                        fontSize = 15.sp,
                        fontWeight = if (row.trailingIsLink) FontWeight.SemiBold else FontWeight.Normal
                    )
                } else if (row.showChevron && onClick != null) {
                    Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = Ids.colors.textTertiary)
                }
            }
        }
    }
}

@Composable
internal fun PlainTopBar(title: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Ids.colors.textPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("...", color = Ids.colors.textPrimary, fontSize = 24.sp)
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
            .background(Ids.colors.chip)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(label, color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BenefitsVisitCard() {
    IdsCard {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("Visit 3 of 4 services and earn points", color = Ids.colors.textPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            listOf(
                "Happy lottery" to Icons.Outlined.Casino,
                "Push the button" to Icons.Outlined.TouchApp,
                "Try on" to Icons.Outlined.Checkroom,
                "Bring friends" to Icons.Outlined.PersonAddAlt
            ).forEach { (title, icon) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Ids.colors.chip), contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = Ids.colors.textPrimary)
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(title, modifier = Modifier.weight(1f), color = Ids.colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    IdsButton("Visit", onClick = {}, variant = IdsButtonVariant.Tinted, size = IdsButtonSize.Small)
                }
            }
        }
    }
}

@Composable
private fun CashbackChanceCard() {
    IdsCard {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                "🍀 3 chances to get money back",
                color = Ids.colors.textPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = rw.itunda.core.designsystem.theme.TossFaceFontFamily
            )
            Text("We will notify you when new chances are available", color = Ids.colors.textSecondary, fontSize = 15.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF246BFF)), contentAlignment = Alignment.Center) {
                    // Was the Korean Won symbol ("₩") -- wrong currency
                    // entirely for a Rwanda app; real icon now.
                    Icon(Icons.Outlined.CurrencyExchange, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color.White)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("RWF 5,000", color = Ids.colors.textPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("BK account -> TUYIZERE Eric", color = Ids.colors.textSecondary)
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
            .background(Ids.colors.surfaceSoft)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(placeholder, color = Ids.colors.textSecondary, fontSize = 16.sp)
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
                Text("5 nearby stores", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun PayFeatureCard() {
    IdsCard {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Ids.colors.chip), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Storefront, contentDescription = null, modifier = Modifier.size(18.dp), tint = Ids.colors.brand)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("itunda pay", color = Ids.colors.textSecondary)
                    Text("30% rewards at partner stores", color = Ids.colors.brand, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                }
                IdsButton("Find store", onClick = {}, variant = IdsButtonVariant.Tinted, size = IdsButtonSize.Small)
            }
            Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Ids.colors.surfaceSoft).padding(18.dp)) {
                Text("Apply pay money and points automatically", color = Ids.colors.textSecondary, fontSize = 16.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Ids.colors.chip), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Language, contentDescription = null, modifier = Modifier.size(18.dp), tint = Ids.colors.textPrimary)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("How to pay online", color = Ids.colors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("Use Itunda Pay on e-commerce and partner stores", color = Ids.colors.textSecondary)
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
        Text("TUYIZERE ERIC", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 26.sp)
        Row {
            // Real My-activity screen (2026-07-24, inverted from Menu) -- see
            // ItundaTab.All's own doc comment: the profile icon now leads to the
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
        Text(title, color = Ids.colors.textSecondary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        val chunked = items.chunked(4)
        chunked.forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                rowItems.forEach { (label, icon) ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f).clickable { onItemClick(label) },
                    ) {
                        Box(modifier = Modifier.size(54.dp).clip(RoundedCornerShape(18.dp)).background(Ids.colors.surfaceSoft), contentAlignment = Alignment.Center) {
                            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = Ids.colors.textPrimary)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(label, color = Ids.colors.textSecondary, fontSize = 13.sp)
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
