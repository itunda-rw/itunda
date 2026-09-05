package rw.itunda.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Agriculture
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.AttachMoney
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ChildCare
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.CurrencyExchange
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.Fastfood
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.ExpandMore
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
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.HomeWork
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Percent
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.RequestQuote
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.Swipe
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import coil.compose.AsyncImage
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
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
// Real Toss motion research (2026-08-11, toss.im/tossfeed/article/why-motion-in-finance):
// "움직임이 더해진 그래픽은 글을 읽지 않아도 직관적으로 이해할 수 있다" (movement added to
// graphics is understood intuitively without reading) and confetti/celebration for real
// positive moments ("행복한 순간") -- these two composable-scoped animation primitives back
// TransferSuccessScreen and IdsButton's new press feedback below.
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Spring
import androidx.compose.ui.unit.sp

import rw.itunda.app.R
import rw.itunda.core.designsystem.components.BackTopBar
import rw.itunda.core.designsystem.components.StarGold
import rw.itunda.core.designsystem.components.relativeTimeAgo
import rw.itunda.core.designsystem.components.IdsButton
import rw.itunda.core.designsystem.components.IdsCard
import rw.itunda.core.designsystem.components.rememberPressScale
import rw.itunda.core.designsystem.components.dashedBorder
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.components.rememberCountUp
import rw.itunda.core.designsystem.components.rememberSpringOverscrollModifier
import rw.itunda.core.designsystem.components.trackScrollPressedKey
import rw.itunda.feature.talk.impl.TalkTab
import rw.itunda.feature.maps.impl.MapScreen
import rw.itunda.feature.shop.impl.CommerceShopContent
import rw.itunda.feature.eats.impl.EatsContent
import rw.itunda.feature.credit.impl.LoansScreen
import rw.itunda.feature.credit.impl.CreditScoreScreen
import rw.itunda.feature.credit.impl.StudentLoanScreen
import rw.itunda.feature.credit.impl.VupLoanScreen
import rw.itunda.feature.banking.impl.BankHubScreen
import rw.itunda.feature.banking.impl.GroupAccountScreen
import rw.itunda.feature.banking.impl.Grow31SavingsScreen
import rw.itunda.feature.banking.impl.WeeklySavingsScreen
import rw.itunda.feature.home.impl.HomeTab
import rw.itunda.feature.pay.impl.AutoTransferListScreen
import rw.itunda.feature.pay.impl.PayTab
import rw.itunda.feature.pay.impl.ScheduledTransferListScreen
import rw.itunda.feature.pay.impl.TransferHubScreen
import rw.itunda.feature.menu.impl.MenuScreen
import rw.itunda.feature.my.impl.MyTab
import rw.itunda.feature.wealth.impl.InvestScreen
import rw.itunda.feature.ride.impl.RideScreen
import rw.itunda.feature.ride.impl.DesignatedDriverScreen
import rw.itunda.core.network.BucketDetailTarget
import rw.itunda.core.network.MoneyActionResult
import rw.itunda.core.designsystem.theme.AccentIndigo
import rw.itunda.core.designsystem.theme.AccentTeal
import rw.itunda.core.designsystem.theme.AccentPurple
import rw.itunda.core.designsystem.theme.AccentOrange
import rw.itunda.core.designsystem.components.IdsButtonSize
import rw.itunda.core.designsystem.components.IdsButtonVariant
import rw.itunda.core.designsystem.components.IdsIconButton
import rw.itunda.core.designsystem.components.LocalRealActivity
import rw.itunda.core.designsystem.components.DeviceStepUpHost
import rw.itunda.core.designsystem.components.AccountLedgerRow
import rw.itunda.core.designsystem.components.ledgerRowIcon
import rw.itunda.core.designsystem.components.ledgerRowTitle
import rw.itunda.core.designsystem.components.ledgerDateHeader
import rw.itunda.core.designsystem.components.ledgerFullDateTime
import rw.itunda.core.designsystem.components.transactionTypeLabel
import rw.itunda.core.designsystem.components.TransactionDetailRow
import rw.itunda.core.designsystem.components.FlatRow
import rw.itunda.core.designsystem.components.FlatSection
import rw.itunda.core.designsystem.components.IdsTextField
import rw.itunda.core.designsystem.components.EmptyState
import rw.itunda.core.designsystem.theme.IdsTheme
import rw.itunda.core.designsystem.theme.IdsTypography
import rw.itunda.core.designsystem.theme.IdsColors
import rw.itunda.core.designsystem.theme.IdsIcons
import rw.itunda.core.designsystem.theme.Ids
// itundaface glyphs for the pure-tossface Explore/Menu icon pass (2026-08-26) --
// see FlatRow's own doc comment. App already depends on both feature impl
// modules directly (MapScreen/TalkTab below), so importing their itundaface
// glyphs here isn't a new cross-feature boundary, unlike impl-to-impl imports.
import rw.itunda.core.designsystem.itundaface.ShoppingBagGlyph
import rw.itunda.core.designsystem.itundaface.MoneyBagGlyph
import rw.itunda.core.designsystem.itundaface.GlobeGlyph
import rw.itunda.core.designsystem.itundaface.LockGlyph
import rw.itunda.core.designsystem.itundaface.PinGlyph
import rw.itunda.core.designsystem.itundaface.BellGlyph
import rw.itunda.core.designsystem.itundaface.SpeechBubbleGlyph
import rw.itunda.core.designsystem.itundaface.SplitBillDice
import rw.itunda.core.designsystem.itundaface.WishlistHeart
import rw.itunda.core.designsystem.itundaface.BikeGlyph
import rw.itunda.core.designsystem.itundaface.GiftBox
import rw.itunda.core.designsystem.itundaface.VoucherTicket
import rw.itunda.core.designsystem.itundaface.BriefcaseGlyph
import rw.itunda.core.designsystem.itundaface.ChartIncreasingGlyph
import rw.itunda.core.designsystem.itundaface.QuestionGlyph
import rw.itunda.core.designsystem.itundaface.FamilyGlyph
import rw.itunda.core.designsystem.itundaface.WarningGlyph
import rw.itunda.core.designsystem.itundaface.ParkingGlyph
import rw.itunda.core.designsystem.itundaface.BarChartGlyph
import rw.itunda.core.designsystem.itundaface.CalendarGlyph
import rw.itunda.core.designsystem.itundaface.ChildGlyph
import rw.itunda.core.designsystem.itundaface.WrenchGlyph
import rw.itunda.core.designsystem.itundaface.SeedlingGlyph
import rw.itunda.core.designsystem.itundaface.RefreshCardGlyph
import rw.itunda.core.designsystem.itundaface.ReceiptGlyph
import rw.itunda.core.designsystem.itundaface.ShieldEmojiGlyph
import rw.itunda.core.designsystem.itundaface.PlaceMarket
import rw.itunda.core.designsystem.itundaface.TravelHouse

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
// AccentIndigo/Teal/Purple/Orange moved to core/designsystem/theme/AccentColors.kt
// (2026-09-02, Banking Feature-module decomposition slice 3) so BankHubScreen
// (moving to :features:banking:impl) and LedgerFormatting.kt/BucketDetailScreen.kt
// (staying in :app) share one real definition. AccentGray moved there too
// (2026-09-02, Menu Feature-module decomposition) -- MenuScreen was its only real
// caller.
internal val AccentRed = Color(0xFFFF5B5B)
private val AccentPink = Color(0xFFEC5F8C)

// Real super-app bottom nav: Home/Pay/Explore/Messages/You (2026-08-10), replacing
// the previous Home/Shop/Hood/Talk/All layout -- an explicit product decision after
// directly comparing both against each other (see docs/DESIGN_REFERENCES.md Section
// 41 in the web repo for the full comparison; bank-mfe's BankDashboard.tsx already
// shipped this same five). itunda is bank-first, so Pay and You (profile/account)
// get dedicated primary slots instead of being nested a tap into Explore/My the way
// the previous layout had them. Shop, Eats, Marketplace, Community, Jobs, and
// Property all lose their own tabs -- none demoted for being weak, all real,
// fully-built features -- and are each their own flat, individually reachable
// Explore entry point (`showShop`/`showEats`/`showMarketplace`/`showCommunity`/
// `showJobs`/`showProperty`, same established full-screen-entry-point pattern this
// file already used for Benefits/Pay/Map before those existed as tabs). Real
// same-day correction: an earlier pass nested Shop+Eats behind one row and
// Marketplace+Community+Jobs+Property behind another, each with its own internal
// mode toggle -- exactly mirroring web's now-retired ShopHub/HoodHub -- but a tab
// bar inside a tab is noise a flat catalog shouldn't have, so each is flat instead.
internal enum class ItundaTab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Home("Home", Icons.Outlined.Home),
    Pay("Pay", Icons.Outlined.Payments),
    Explore("Explore", Icons.Outlined.Apps),
    Messages("Messages", Icons.Outlined.Chat),
    // Profile/account as its own primary tab (2026-08-10) -- previously nested one
    // tap into All via a profile icon (see the old ItundaTab.All doc comment this
    // replaced). MyTab's own real content (orders/favorites/listings tracking) is
    // completely unchanged, just reached directly instead of via a nested icon.
    You("You", Icons.Outlined.Person)
}

/**
 * Real top-level navigation for the transfer flow -- a full-screen takeover
 * over the tab scaffold, matching how the reference screenshots show it
 * (no bottom nav visible during recipient/amount entry). Genuinely wired
 * to a visible entry point (AccountHeroCard's "Send" button), not built and
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
    // Real Toss reference (2026-08-11, toss.im/tossfeed/article/why-motion-in-finance):
    // Toss's own headline example of motion in a financial product is exactly this
    // moment -- "송금이 종료된 이후 나오는 체크 애니메이션으로 완료를 직관적으로 표현"
    // (the check animation shown after a transfer completes intuitively communicates
    // completion). Before this, a successful transfer here just set transferStep = null
    // directly -- the sheet silently closed with zero acknowledgment that real money had
    // actually moved, not even a Toast. See TransferSuccessScreen's own doc comment.
    data class Success(val message: String, val amountRwf: Long, val recipientLabel: String, val fraudWarnings: List<String> = emptyList()) : TransferStep()
}

// Real gap found live (2026-08-31, direct user reference of their own Toss app's
// "which account should the money come from" picker) -- see transferFromAccount's own
// call-site doc comment. A separate, sibling state to TransferStep rather than a new
// TransferStep.Amount field, since TransferStep.Recipient is a data object (no fields)
// and this needs to survive that step too.
private data class TransferFromAccount(val accountId: String, val accountName: String, val balance: Double) : java.io.Serializable

// Real Toss motion research (2026-08-11) -- see TransferStep.Success's own doc comment
// for the exact gap this closes: a real transfer's own success acknowledgment was
// previously nonexistent, not just under-designed. Deliberately pure Compose animation
// (spring-based scale-in, no Lottie/asset pipeline) rather than a full custom vector
// path-draw -- matches the article's own "resource efficiency" principle ("PNG 시퀀스
// 이미지를 Lottie로 처리... 성능 차이가 크게 느껴지지 않는 효율적인 결과", efficient
// results where the performance difference isn't perceptible) at itunda's actual scale,
// where a full 3D/Lottie asset pipeline for one screen would be disproportionate. Real
// haptic confirm fires once on entrance, synchronized with the visual pop -- the exact
// "co-design visual, audio, and haptic effects" principle this session's micro-
// interaction research (Toss/general UX sources) both independently named.
@Composable
private fun TransferSuccessScreen(amountRwf: Long, recipientLabel: String, fraudWarnings: List<String> = emptyList(), onDone: () -> Unit) {
    // Real Toss "Sent" success-screen reference (2026-08-23, user-supplied screenshot):
    // a real "To [name]" line (see recipientDisplayName's own doc comment above for
    // where this now-resolved name comes from) and a real Share action -- itunda's
    // established Intent.ACTION_SEND + createChooser pattern (ShopMerchantDetail.kt's
    // own affiliate-link share), not something invented for this screen.
    val shareContext = androidx.compose.ui.platform.LocalContext.current
    rw.itunda.core.designsystem.components.IdsCelebrationScreen(
        headline = "%,d RWF sent".format(amountRwf),
        message = "",
        recipientLabel = recipientLabel,
        onShare = {
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_TEXT, "Sent %,d RWF to %s via itunda".format(amountRwf, recipientLabel))
            }
            shareContext.startActivity(android.content.Intent.createChooser(intent, "Share"))
        },
        onDone = onDone,
        fraudWarnings = fraudWarnings,
    )
}

// Promoted 2026-08-12 into core:designsystem's IdsCelebrationScreen (see its own doc
// comment) -- the exact same "MoneySuccessScreen"/"ConfettiBurst"/"ConfettiParticle"
// this comment used to describe, moved so Grow31SavingsScreen.kt/WeeklySavingsScreen.kt
// (a second and third real call site, a genuine completed-challenge milestone that had
// zero success acknowledgment at all) can reuse it without :app-to-:app duplication.

/** Real savings deposit/claim flow (2026-07-12) -- see SavingsAmountScreen.kt. */
private sealed class SavingsFlowStep : java.io.Serializable {
    // wasAlreadyCompleted added 2026-09-05 (real Toss UX-writing "Find Hidden
    // Emotion" principle, toss.tech/article/8-writing-principles-of-toss -- see
    // MoneyActionResult.Success's own goalCompleted doc comment) -- the goal's
    // pre-deposit completion state, captured at the moment this step is created
    // (both call sites already have currentAmount/targetAmount in scope), so the
    // deposit-success handler can distinguish a genuine active->completed
    // transition from a redundant deposit into an already-completed goal.
    // Defaults to false so a caller with no goal data in scope is unaffected.
    data class Deposit(val goalId: String, val goalName: String, val wasAlreadyCompleted: Boolean = false) : SavingsFlowStep()
    // Real gap found live (2026-08-31, direct user re-reference of the real Toss
    // "얼마나 꺼낼까요?" (withdraw) screenshot) -- see backend SavingsService
    // .withdrawFromGoal's own doc comment for the full account.
    data class Withdraw(val goalId: String, val goalName: String, val currentAmount: Double) : SavingsFlowStep()
    data object ClaimInterest : SavingsFlowStep()
    // Real acknowledgment moment (2026-08-11) -- see IdsCelebrationScreen's own doc
    // comment: both deposit and claim previously just set savingsFlowStep = null on
    // success, same silent-close gap TransferStep.Success closes for transfers.
    // celebratory = true only for claimed interest -- real earned money, matches
    // Toss's own confetti-for-positive-moments example; a routine deposit into a goal
    // you set up yourself isn't that same kind of surprise-and-delight moment.
    // Extended 2026-09-05: a deposit that pushes the goal to completion IS that
    // same kind of moment (matches this app's own LoansView-equivalent loan-payoff
    // precedent) -- see the two deposit-success handlers below for the actual
    // celebratory = result.goalCompleted && !wasAlreadyCompleted check.
    data class Success(val headline: String, val message: String, val celebratory: Boolean) : SavingsFlowStep()
}

// BucketDetailTarget moved to :core:network/BucketDetailTarget.kt (2026-09-02,
// Banking Feature-module decomposition slice 4) so :features:banking:impl's
// BankHubScreen can share the same type.

@Composable
fun ItundaAppScreen(
    viewModel: MainViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    openMapFromDeepLink: Boolean = false,
    initialMapSearchQuery: String? = null,
    initialMapSharedFolder: Pair<String, String>? = null,
    identityVerifyRequestId: String? = null,
    onIdentityVerifyConsumed: () -> Unit = {},
    onMapDeepLinkConsumed: () -> Unit = {},
) {
    IdsTheme {
        // Real push notification permission request (2026-08-12) -- see
        // NotificationPermissionPrompt.kt's own doc comment for why here specifically:
        // this composable only ever renders for an already-logged-in session, the real
        // contextual moment Toss's own onboarding asks at, not cold app launch.
        rw.itunda.app.push.NotificationPermissionPrompt()
        var selectedTab by rememberSaveable { mutableStateOf(ItundaTab.Home) }
        var transferStep by rememberSaveable { mutableStateOf<TransferStep?>(null) }
        // Real gap found live (2026-08-31, direct user reference of their own Toss
        // app's "which account should the money come from" picker) -- previously
        // TransferAmountScreen always debited the sender's MAIN account
        // (primaryAccountForTransfer below) with no way to choose another one, even
        // though a real itunda user can hold more than one debit-capable Account row.
        // Null keeps every existing entry point (Home's "Send", the account-detail
        // screen's onSend, TransferHub) behaving exactly as before; only
        // OverviewScreen's new per-account Send button sets this.
        var transferFromAccount by rememberSaveable { mutableStateOf<TransferFromAccount?>(null) }
        var savingsFlowStep by rememberSaveable { mutableStateOf<SavingsFlowStep?>(null) }
        var bucketDetailTarget by rememberSaveable { mutableStateOf<BucketDetailTarget?>(null) }
        var showBankAssets by rememberSaveable { mutableStateOf(false) }
        // Real Toss Bank 관리 (Manage) account-settings hub (2026-09-01, direct
        // user-supplied Toss screenshots of that exact screen) -- ports web's own
        // already-built AccountManageScreen.tsx (see that file's own doc comment for
        // which real itunda features it surfaces and what's honestly scoped out) so
        // the gear icon opens an account-scoped hub instead of jumping straight to
        // the generic app-wide Settings screen. showDeviceList is a second, separate
        // top-level flag from SettingsScreen.kt's own local one -- lets this screen's
        // "Manage devices" row deep-link straight to DeviceListScreen (now `internal`)
        // without going through Settings at all.
        var showAccountManage by rememberSaveable { mutableStateOf(false) }
        var showDeviceList by rememberSaveable { mutableStateOf(false) }
        var showTransactionHistory by rememberSaveable { mutableStateOf(false) }
        var showSettings by rememberSaveable { mutableStateOf(false) }
        // Real Toss distinction (2026-08-12, direct user clarification against real
        // screenshots) -- the Home bell icon opens the real notifications FEED
        // directly; Settings' own "Notifications" row opens notification SETTINGS
        // (a different, real Toss screen -- "Manage notifications" in the reference
        // screenshots). This app previously conflated the two, routing the Home bell
        // into the whole Settings screen -- a real no-op-shaped bug (see this file's
        // own 2026-07-22 audit comment on HomeTopBar, now corrected).
        var showNotificationsFeed by rememberSaveable { mutableStateOf(false) }
        // Shop/Hood lost their own primary tabs (2026-08-10, see ItundaTab's own doc
        // comment) -- same real full-screen-entry-point pattern showMap already
        // established for non-primary destinations.
        // Shop and Eats are two flat, independent Explore rows (2026-08-10, real user
        // correction: nesting them behind one "Shop" entry with its own Shop/Eats
        // toggle -- ShopTab's real shape, correct when Shop was a primary tab -- is
        // noise once inside a catalog screen; each real feature/impl content
        // composable is called directly instead, no wrapper toggle).
        var showShop by rememberSaveable { mutableStateOf(false) }
        var showEats by rememberSaveable { mutableStateOf(false) }
        // Hood's own chip row (Market/Life/Jobs/Home) retired the same way (see
        // HoodSectionScreen's own doc comment): 4 flat destinations instead of one
        // Hood entry with an internal switcher.
        var showMarketplace by rememberSaveable { mutableStateOf(false) }
        var showCommunity by rememberSaveable { mutableStateOf(false) }
        var showJobs by rememberSaveable { mutableStateOf(false) }
        var showProperty by rememberSaveable { mutableStateOf(false) }
        var showMap by rememberSaveable { mutableStateOf(false) }
        var mapSearchQueryForScreen by rememberSaveable { mutableStateOf<String?>(null) }
        var mapSharedFolderForScreen by rememberSaveable { mutableStateOf<Pair<String, String>?>(null) }
        // Real "Delivery" pill deep-link, Maps -> Eats (2026-08-09) -- itunda's
        // Feature-module isolation forbids Maps depending on Eats directly, so this
        // shell (the only thing that can see both) carries a small, plain (not
        // rememberSaveable -- an in-flight navigation intent has no reason to survive
        // process death) pending-target across the tab switch. Cleared by EatsContent
        // once consumed, so returning to Eats later doesn't re-trigger the same restaurant.
        var pendingEatsMerchantId by remember { mutableStateOf<String?>(null) }
        var pendingEatsMerchantName by remember { mutableStateOf<String?>(null) }
        var showAgentCash by rememberSaveable { mutableStateOf(false) }
        var showInvest by rememberSaveable { mutableStateOf(false) }
        // Real itunda Bank product hub (2026-08-11) -- see the "itunda Bank vs itunda
        // account" naming correction earlier this session: KakaoPay/KakaoBank and Toss's
        // own Payments/Bank are genuinely distinct products, not just a generic/specific
        // naming pair. itunda's savings, SACCO, Ikimina, loans, and investment features
        // were real and already built, but scattered as flat rows with no product
        // identity of their own -- this gives them one, parallel to the itunda Pay tab
        // (ItundaTab.Pay), the same real split Toss and Kakao both make. Not a 6th
        // primary tab -- real Toss's own bottom nav doesn't put Toss Bank there either
        // despite it being a distinct product, it's a surface reached from Home.
        var showBank by rememberSaveable { mutableStateOf(false) }
        // Real Toss Bank account-detail screen (2026-08-13, 3 direct user
        // screenshots of their own real Toss Bank account: "when you click on bank
        // accounts that what you should see"). AccountSwitcherSheet's own "itunda
        // account" row (below) had never been clickable at all -- tapping it did
        // nothing. That real screen is a balance + unclaimed-interest + real
        // transaction ledger (with a running per-row balance and date-grouped
        // history), distinct from both HomeTab's compact account card and
        // BankHubScreen's product catalog -- see AccountDetailScreen's own doc
        // comment.
        var showAccountDetail by rememberSaveable { mutableStateOf(false) }
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
        // Real Toss Bank 키워봐요 31일적금 (Grow-it 31-day savings) screen (2026-08-12) --
        // same "backend existed with zero mobile UI" pattern as WeeklySavingsScreen above,
        // just shipped with a client from day one this time.
        var showGrow31Savings by rememberSaveable { mutableStateOf(false) }
        // Real Toss Bank 먼저 이자받는 정기예금 (interest-paid-upfront term deposit)
        // screen (2026-07-25) -- same "backend existed with zero mobile UI" gap
        // WeeklySavingsScreen closed above.
        var showUpfrontDeposit by rememberSaveable { mutableStateOf(false) }
        var showYouthAccount by rememberSaveable { mutableStateOf(false) }
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
        // Real Kigali public-transit stored-value balance (2026-08-27) -- bank-mfe
        // shipped first; same pattern.
        var showTransit by rememberSaveable { mutableStateOf(false) }
        // Real "agent collects a fare from a rider's presented code" flow (2026-08-27,
        // direct user follow-up: "for simplification we need nfc") -- reached from a
        // link inside TransitScreen, not its own top-level entry point.
        var showTransitCollect by rememberSaveable { mutableStateOf(false) }
        // Real "tap to pay your moto-taxi fare" (2026-08-27, direct user follow-up:
        // "now we can make pay for tax and moto as well").
        var showMotoFareCollect by rememberSaveable { mutableStateOf(false) }
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
                mapSharedFolderForScreen = initialMapSharedFolder
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
        val primaryAccountForTransfer by viewModel.primaryAccount.collectAsState()
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
        // Real Toss/Kakao Bank-style recipient-name confirmation ("받는분 성함 확인",
        // 2026-08-23) -- see ApiService.resolveRecipient's own doc comment: this
        // endpoint already had a real web client (bank-mfe) but Android's transfer flow
        // only ever showed the raw account number throughout, never a resolved name.
        // Best-effort like the scam check above it: a failed lookup falls back to
        // showing the account number on the Success screen, never blocks the transfer.
        var recipientDisplayName by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(step) {
            if (step is TransferStep.Amount) {
                scamReported = false
                recipientDisplayName = null
                try {
                    val result = rw.itunda.core.network.NetworkClient.apiService.checkScamStatus(step.accountNumber).result
                    scamReportCount = if (result.warn) result.reportCount else 0
                } catch (_: Exception) {
                    // Real, non-critical -- a failed safety check must never block a
                    // real transfer the sender is otherwise entitled to make.
                }
                try {
                    recipientDisplayName = rw.itunda.core.network.NetworkClient.apiService.resolveRecipient(step.accountNumber).recipient.displayName
                } catch (_: Exception) {
                    // Non-critical -- Success screen falls back to the account number.
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
                    // TransferSuccessScreen owns its own BackHandler(onBack = onDone) with
                    // identical behavior -- this branch just keeps this outer `when`
                    // exhaustive rather than relying on Compose's BackHandler stacking
                    // order to be the only thing preventing an unhandled state.
                    is TransferStep.Success -> null
                }
            }
            when (step) {
                is TransferStep.Recipient -> rw.itunda.feature.payments.impl.RecipientEntryScreen(
                    onBack = { transferStep = null; transferFromAccount = null },
                    onNext = { accountNumber -> transferStep = TransferStep.Amount(accountNumber) },
                    contacts = contacts.map { rw.itunda.feature.payments.impl.ContactUi(it.name, it.phoneNumber, it.bank, it.color, it.letter) },
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
                        availableBalance = transferFromAccount?.balance ?: primaryAccountForTransfer?.availableBalance ?: 0.0,
                        fromAccountName = transferFromAccount?.accountName,
                        isSubmitting = isSendingTransfer,
                        scamWarning = scamReportCount?.let { rw.itunda.feature.payments.impl.ScamWarningUi(it) },
                        scamReported = scamReported,
                        onReportScam = { showScamReportDialog = true },
                        onBack = { transferStep = TransferStep.Recipient },
                        onConfirm = { amountRwf, isGift, giftNote, giftTheme ->
                            // Toss-style biometric confirmation gate before a transfer
                            // completes -- see docs/ARCHITECTURE.md's NIDABiometricAuth
                            // note. Real quote+confirm call now follows a successful
                            // check (2026-07-12, see MainViewModel.sendTransfer) --
                            // previously "success" here just closed the sheet without
                            // moving any real money (see TransferFlow.kt's old header).
                            //
                            // Real defense-in-depth fix, 2026-09-05 (see iOS's own
                            // TransferFlowContainer.confirm fix, the same real bug there):
                            // BiometricPrompt always shows a real, blocking system dialog
                            // for every biometric type (unlike iOS's bare Touch ID, which
                            // shows no UI at all) -- so this Compose button was never
                            // practically double-tappable the way iOS's was. But
                            // isSendingTransfer only flipped true INSIDE the success
                            // callback, leaving a real (if narrow, dialog-launch-latency-
                            // sized) window where a sub-frame double-tap could still fire
                            // authenticateForTransaction twice before either dialog
                            // commits, each with its own fresh Idempotency-Key. Flipping
                            // this synchronously before the call closes that window too.
                            biometricError = null
                            isSendingTransfer = true
                            biometricAuth.authenticateForTransaction(
                                reason = if (isGift) "Confirm sending a $amountRwf RWF gift" else "Confirm sending $amountRwf RWF"
                            ) { success, error ->
                                if (success) {
                                    coroutineScope.launch {
                                        suspend fun doSend() = if (isGift) {
                                            viewModel.sendGift(step.accountNumber, amountRwf, giftNote, giftTheme)
                                        } else {
                                            viewModel.sendTransfer(step.accountNumber, amountRwf, memo = giftNote ?: "", fromAccountId = transferFromAccount?.accountId)
                                        }
                                        when (val result = doSend()) {
                                            is rw.itunda.core.network.MoneyActionResult.Success -> {
                                                isSendingTransfer = false
                                                transferStep = TransferStep.Success(result.message, amountRwf, recipientDisplayName ?: step.accountNumber, result.fraudWarnings)
                                            }
                                            // sendTransfer never actually returns Queued -- a
                                            // transfer confirm is deliberately never queued
                                            // offline (see MainViewModel.depositToSavingsGoal's
                                            // doc comment for why) -- handled only because
                                            // MoneyActionResult is a shared sealed interface.
                                            is rw.itunda.core.network.MoneyActionResult.Queued -> {
                                                isSendingTransfer = false
                                                transferStep = null
                                                transferFromAccount = null
                                            }
                                            is rw.itunda.core.network.MoneyActionResult.Failure -> {
                                                isSendingTransfer = false
                                                biometricError = result.message
                                            }
                                            is rw.itunda.core.network.MoneyActionResult.DeviceNotVerified -> {
                                                isSendingTransfer = false
                                                deviceStepUpError = null
                                                pendingDeviceRetry = {
                                                    isSendingTransfer = true
                                                    val retryResult = doSend()
                                                    isSendingTransfer = false
                                                    if (retryResult is rw.itunda.core.network.MoneyActionResult.Success) transferStep = TransferStep.Success(retryResult.message, amountRwf, recipientDisplayName ?: step.accountNumber, retryResult.fraudWarnings)
                                                    else if (retryResult is rw.itunda.core.network.MoneyActionResult.Failure) biometricError = retryResult.message
                                                }
                                                showDeviceStepUp = true
                                            }
                                        }
                                    }
                                } else {
                                    isSendingTransfer = false
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
                is TransferStep.Success -> TransferSuccessScreen(
                    amountRwf = step.amountRwf,
                    recipientLabel = step.recipientLabel,
                    fraudWarnings = step.fraudWarnings,
                    onDone = { transferStep = null; transferFromAccount = null },
                )
            }
            if (showDeviceStepUp) {
                rw.itunda.core.designsystem.components.DeviceStepUpDialog(
                    busy = deviceStepUpBusy,
                    error = deviceStepUpError,
                    onCancel = { showDeviceStepUp = false; deviceStepUpError = null; pendingDeviceRetry = null },
                    onVerify = { password ->
                        deviceStepUpError = null
                        deviceStepUpBusy = true
                        coroutineScope.launch {
                            when (val result = viewModel.verifyDevice(password)) {
                                is rw.itunda.core.network.MoneyActionResult.Success -> {
                                    deviceStepUpBusy = false
                                    showDeviceStepUp = false
                                    val retry = pendingDeviceRetry
                                    pendingDeviceRetry = null
                                    retry?.invoke()
                                }
                                is rw.itunda.core.network.MoneyActionResult.Failure -> {
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
        // stringResource(...) can't be called from inside coroutineScope.launch below
        // (not a @Composable call site) -- captured here and resolved via
        // Context.getString(...) instead, same established pattern OverviewScreen.kt's
        // own catch blocks already use.
        val savingsContext = androidx.compose.ui.platform.LocalContext.current
        var isSavingsSubmitting by remember { mutableStateOf(false) }
        var savingsError by remember { mutableStateOf<String?>(null) }
        val availableBalanceForSavings by viewModel.primaryAccount.collectAsState()
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
                                is rw.itunda.core.network.MoneyActionResult.Success -> {
                                    isSavingsSubmitting = false
                                    // Real Toss UX-writing "Find Hidden Emotion" principle
                                    // (toss.tech/article/8-writing-principles-of-toss) --
                                    // see MoneyActionResult.Success's own goalCompleted doc
                                    // comment. Only a genuine active->completed transition
                                    // is celebratory, not a redundant deposit into an
                                    // already-completed goal.
                                    val justCompleted = result.goalCompleted && !savingsStep.wasAlreadyCompleted
                                    savingsFlowStep = if (justCompleted) {
                                        SavingsFlowStep.Success(
                                            savingsContext.getString(R.string.savings_goal_completed_title),
                                            savingsContext.getString(R.string.savings_goal_completed_message, savingsStep.goalName),
                                            celebratory = true,
                                        )
                                    } else {
                                        SavingsFlowStep.Success(savingsContext.getString(R.string.savings_amount_saved, "%,d".format(amountRwf)), result.message, celebratory = false)
                                    }
                                }
                                // Real offline queueing (2026-07-13, see
                                // MainViewModel.depositToSavingsGoal): the deposit was
                                // saved locally, not executed yet -- close the sheet
                                // like a success (the user's intent was captured) but
                                // surface the distinction via a real Toast rather than
                                // silently treating it as identical to a completed
                                // deposit.
                                is rw.itunda.core.network.MoneyActionResult.Queued -> {
                                    isSavingsSubmitting = false
                                    savingsFlowStep = null
                                    rw.itunda.core.designsystem.components.IdsToast.show(coroutineScope, result.message)
                                }
                                is rw.itunda.core.network.MoneyActionResult.Failure -> {
                                    isSavingsSubmitting = false
                                    savingsError = result.message
                                }
                                is rw.itunda.core.network.MoneyActionResult.DeviceNotVerified -> {
                                    isSavingsSubmitting = false
                                    deviceStepUpError = null
                                    pendingDeviceRetry = {
                                        isSavingsSubmitting = true
                                        val retryResult = viewModel.depositToSavingsGoal(savingsStep.goalId, amountRwf)
                                        isSavingsSubmitting = false
                                        when (retryResult) {
                                            is rw.itunda.core.network.MoneyActionResult.Success -> {
                                                // Same "Find Hidden Emotion" logic as the
                                                // primary success path above.
                                                val justCompleted = retryResult.goalCompleted && !savingsStep.wasAlreadyCompleted
                                                savingsFlowStep = if (justCompleted) {
                                                    SavingsFlowStep.Success(
                                                        savingsContext.getString(R.string.savings_goal_completed_title),
                                                        savingsContext.getString(R.string.savings_goal_completed_message, savingsStep.goalName),
                                                        celebratory = true,
                                                    )
                                                } else {
                                                    SavingsFlowStep.Success(savingsContext.getString(R.string.savings_amount_saved, "%,d".format(amountRwf)), retryResult.message, celebratory = false)
                                                }
                                            }
                                            is rw.itunda.core.network.MoneyActionResult.Queued -> {
                                                savingsFlowStep = null
                                                rw.itunda.core.designsystem.components.IdsToast.show(coroutineScope, retryResult.message)
                                            }
                                            is rw.itunda.core.network.MoneyActionResult.Failure -> savingsError = retryResult.message
                                            else -> {}
                                        }
                                    }
                                    showDeviceStepUp = true
                                }
                            }
                        }
                    }
                )
                is SavingsFlowStep.Withdraw -> rw.itunda.feature.payments.impl.SavingsAmountScreen(
                    goalName = savingsStep.goalName,
                    mode = rw.itunda.feature.payments.impl.SavingsAmountMode.withdraw,
                    availableBalance = savingsStep.currentAmount,
                    isSubmitting = isSavingsSubmitting,
                    onBack = { savingsFlowStep = null },
                    onConfirm = { amountRwf ->
                        isSavingsSubmitting = true
                        coroutineScope.launch {
                            when (val result = viewModel.withdrawFromSavingsGoal(savingsStep.goalId, amountRwf)) {
                                is rw.itunda.core.network.MoneyActionResult.Success -> {
                                    isSavingsSubmitting = false
                                    savingsFlowStep = SavingsFlowStep.Success(savingsContext.getString(R.string.savings_amount_withdrawn, "%,d".format(amountRwf)), result.message, celebratory = false)
                                }
                                is rw.itunda.core.network.MoneyActionResult.Queued -> {
                                    // withdrawFromSavingsGoal never actually returns
                                    // Queued (unlike depositToSavingsGoal, no offline
                                    // queue support here) -- handled only because
                                    // MoneyActionResult is a shared sealed interface.
                                    isSavingsSubmitting = false
                                    savingsFlowStep = null
                                }
                                is rw.itunda.core.network.MoneyActionResult.Failure -> {
                                    isSavingsSubmitting = false
                                    savingsError = result.message
                                }
                                is rw.itunda.core.network.MoneyActionResult.DeviceNotVerified -> {
                                    isSavingsSubmitting = false
                                    deviceStepUpError = null
                                    pendingDeviceRetry = {
                                        isSavingsSubmitting = true
                                        val retryResult = viewModel.withdrawFromSavingsGoal(savingsStep.goalId, amountRwf)
                                        isSavingsSubmitting = false
                                        when (retryResult) {
                                            is rw.itunda.core.network.MoneyActionResult.Success -> savingsFlowStep = SavingsFlowStep.Success(savingsContext.getString(R.string.savings_amount_withdrawn, "%,d".format(amountRwf)), retryResult.message, celebratory = false)
                                            is rw.itunda.core.network.MoneyActionResult.Failure -> savingsError = retryResult.message
                                            else -> {}
                                        }
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
                                is rw.itunda.core.network.MoneyActionResult.Success -> {
                                    isSavingsSubmitting = false
                                    // celebratory = true -- real earned money, matches
                                    // Toss's own confetti-for-positive-moments example
                                    // (see IdsCelebrationScreen's own doc comment).
                                    savingsFlowStep = SavingsFlowStep.Success(savingsContext.getString(R.string.savings_interest_claimed), result.message, celebratory = true)
                                }
                                // claimInterest never actually returns Queued (only
                                // SAVINGS_DEPOSIT is queued) -- handled only because
                                // MoneyActionResult is a shared sealed interface.
                                is rw.itunda.core.network.MoneyActionResult.Queued -> {
                                    isSavingsSubmitting = false
                                    savingsFlowStep = null
                                }
                                is rw.itunda.core.network.MoneyActionResult.Failure -> {
                                    isSavingsSubmitting = false
                                    savingsError = result.message
                                }
                                is rw.itunda.core.network.MoneyActionResult.DeviceNotVerified -> {
                                    isSavingsSubmitting = false
                                    deviceStepUpError = null
                                    pendingDeviceRetry = {
                                        isSavingsSubmitting = true
                                        val retryResult = viewModel.claimInterest()
                                        isSavingsSubmitting = false
                                        if (retryResult is rw.itunda.core.network.MoneyActionResult.Success) savingsFlowStep = SavingsFlowStep.Success(savingsContext.getString(R.string.savings_interest_claimed), retryResult.message, celebratory = true)
                                        else if (retryResult is rw.itunda.core.network.MoneyActionResult.Failure) savingsError = retryResult.message
                                    }
                                    showDeviceStepUp = true
                                }
                            }
                        }
                    }
                )
                is SavingsFlowStep.Success -> rw.itunda.core.designsystem.components.IdsCelebrationScreen(
                    headline = savingsStep.headline,
                    message = savingsStep.message,
                    celebratory = savingsStep.celebratory,
                    onDone = { savingsFlowStep = null },
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
                rw.itunda.core.designsystem.components.DeviceStepUpDialog(
                    busy = deviceStepUpBusy,
                    error = deviceStepUpError,
                    onCancel = { showDeviceStepUp = false; deviceStepUpError = null; pendingDeviceRetry = null },
                    onVerify = { password ->
                        deviceStepUpError = null
                        deviceStepUpBusy = true
                        coroutineScope.launch {
                            when (val result = viewModel.verifyDevice(password)) {
                                is rw.itunda.core.network.MoneyActionResult.Success -> {
                                    deviceStepUpBusy = false
                                    showDeviceStepUp = false
                                    val retry = pendingDeviceRetry
                                    pendingDeviceRetry = null
                                    retry?.invoke()
                                }
                                is rw.itunda.core.network.MoneyActionResult.Failure -> {
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
            val currentUserIdForHistory by viewModel.primaryAccount.collectAsState()
            val transactionHistoryRefreshing by viewModel.isRefreshing.collectAsState()
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
                onRefresh = { viewModel.retry() },
                isRefreshing = transactionHistoryRefreshing,
            )
            return@IdsTheme
        }

        if (showSettings) {
            BackHandler { showSettings = false }
            SettingsScreen(
                viewModel = viewModel,
                onBack = { showSettings = false },
                onOpenSend = { showSettings = false; showTransferHub = true },
                onOpenPay = { showSettings = false; selectedTab = ItundaTab.Pay },
            )
            return@IdsTheme
        }

        if (showNotificationsFeed) {
            BackHandler { showNotificationsFeed = false }
            val feedNotifications by viewModel.notifications.collectAsState()
            val feedUnreadCount by viewModel.unreadNotificationCount.collectAsState()
            NotificationListScreen(
                notifications = feedNotifications,
                unreadCount = feedUnreadCount,
                onMarkAllRead = { viewModel.markAllNotificationsRead() },
                onNotificationClick = { id -> viewModel.markNotificationRead(id) },
                onBack = { showNotificationsFeed = false },
            )
            return@IdsTheme
        }

        if (showShop) {
            BackHandler { showShop = false }
            Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
                Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                    CommerceShopContent(
                        deviceStepUpHost = { visible, onDismiss, onVerified ->
                            DeviceStepUpHost(visible = visible, onDismiss = onDismiss, onVerified = onVerified)
                        },
                        onMessageSeller = { conversationId ->
                            pendingConversationId = conversationId
                            showShop = false
                            selectedTab = ItundaTab.Messages
                        },
                    )
                }
            }
            return@IdsTheme
        }
        if (showEats) {
            BackHandler { showEats = false }
            Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
                Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                    EatsContent(
                        deviceStepUpHost = { visible, onDismiss, onVerified ->
                            DeviceStepUpHost(visible = visible, onDismiss = onDismiss, onVerified = onVerified)
                        },
                        pendingMerchantId = pendingEatsMerchantId,
                        pendingMerchantName = pendingEatsMerchantName,
                        onPendingMerchantConsumed = { pendingEatsMerchantId = null; pendingEatsMerchantName = null },
                    )
                }
            }
            return@IdsTheme
        }
        if (showMarketplace) {
            BackHandler { showMarketplace = false }
            Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
                Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                    HoodSectionScreen(
                        mode = HoodMode.MARKETPLACE,
                        onMessageSeller = { conversationId ->
                            pendingConversationId = conversationId
                            showMarketplace = false
                            selectedTab = ItundaTab.Messages
                        },
                        onOpenSettings = { showSettings = true },
                    )
                }
            }
            return@IdsTheme
        }
        if (showCommunity) {
            BackHandler { showCommunity = false }
            Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
                Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                    HoodSectionScreen(
                        mode = HoodMode.COMMUNITY,
                        onMessageSeller = { conversationId ->
                            pendingConversationId = conversationId
                            showCommunity = false
                            selectedTab = ItundaTab.Messages
                        },
                        onOpenSettings = { showSettings = true },
                    )
                }
            }
            return@IdsTheme
        }
        if (showJobs) {
            BackHandler { showJobs = false }
            Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
                Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                    HoodSectionScreen(
                        mode = HoodMode.JOBS,
                        onMessageSeller = { conversationId ->
                            pendingConversationId = conversationId
                            showJobs = false
                            selectedTab = ItundaTab.Messages
                        },
                        onOpenSettings = { showSettings = true },
                    )
                }
            }
            return@IdsTheme
        }
        if (showProperty) {
            BackHandler { showProperty = false }
            Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
                Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                    HoodSectionScreen(
                        mode = HoodMode.PROPERTY,
                        onMessageSeller = { conversationId ->
                            pendingConversationId = conversationId
                            showProperty = false
                            selectedTab = ItundaTab.Messages
                        },
                        onOpenSettings = { showSettings = true },
                    )
                }
            }
            return@IdsTheme
        }
        // Real partner identity-disclosure consent. Deliberately checked before every
        // other destination below: arriving here means the user followed a partner's
        // link specifically to answer this, and the request itself expires in 5 minutes.
        if (identityVerifyRequestId != null) {
            BackHandler { onIdentityVerifyConsumed() }
            IdentityVerificationConsentScreen(
                requestId = identityVerifyRequestId,
                onDone = onIdentityVerifyConsumed,
            )
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
                initialSharedFolder = mapSharedFolderForScreen,
                onOrderDelivery = { merchantId, businessName ->
                    pendingEatsMerchantId = merchantId
                    pendingEatsMerchantName = businessName
                    showMap = false
                    showEats = true
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
            OverviewScreen(
                onBack = { showOverview = false },
                onOpenCard = { showOverview = false; showCard = true },
                onOpenLoans = { showOverview = false; showLoans = true },
                onOpenInvest = { showOverview = false; showInvest = true },
                onOpenProperty = { showOverview = false; showProperty = true },
                onOpenVehicleValuation = { showOverview = false; showVehicleValuation = true },
                onSend = { accountId, accountName, balance ->
                    showOverview = false
                    transferFromAccount = TransferFromAccount(accountId, accountName, balance)
                    transferStep = TransferStep.Recipient
                },
            )
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
        // Real Toss Bank 키워봐요 31일적금 screen (2026-08-12) -- same Quick-links
        // full-screen pattern as WeeklySavingsScreen directly above.
        if (showGrow31Savings) {
            BackHandler { showGrow31Savings = false }
            Grow31SavingsScreen(onBack = { showGrow31Savings = false })
            return@IdsTheme
        }
        // Real Toss Bank 먼저 이자받는 정기예금 screen (2026-07-25) -- same pattern.
        if (showUpfrontDeposit) {
            BackHandler { showUpfrontDeposit = false }
            UpfrontDepositScreen(onBack = { showUpfrontDeposit = false })
            return@IdsTheme
        }
        // Real KakaoBank mini-style capped starter account screen (2026-07-28, item 100)
        // -- first mobile client for this feature. Same pattern.
        if (showYouthAccount) {
            BackHandler { showYouthAccount = false }
            YouthAccountScreen(onBack = { showYouthAccount = false })
            return@IdsTheme
        }
        // Real Toss Bank 체크카드 (check/debit card) screen (2026-07-31, item 207) --
        // first Android client, same "backend real, zero mobile UI" gap-close pattern.
        if (showCard) {
            BackHandler { showCard = false }
            CardScreen(onBack = { showCard = false })
            return@IdsTheme
        }
        // Real Kigali public-transit stored-value balance (2026-08-27) -- see
        // TransitScreen.kt's own doc comment for the full sourced account.
        if (showTransit) {
            BackHandler { showTransit = false }
            TransitScreen(onBack = { showTransit = false }, onOpenCollect = { showTransitCollect = true })
            return@IdsTheme
        }
        // Real "agent collects a fare from a rider's presented code" flow (2026-08-27,
        // direct user follow-up: "for simplification we need nfc") -- see
        // TransitCollectScreen.kt's own doc comment for the full sourced account.
        if (showTransitCollect) {
            BackHandler { showTransitCollect = false }
            TransitCollectScreen(onBack = { showTransitCollect = false })
            return@IdsTheme
        }
        // Real "tap to pay your moto-taxi fare" (2026-08-27, direct user follow-up:
        // "now we can make pay for tax and moto as well") -- see
        // MotoFareCollectScreen.kt's own doc comment for the full sourced account.
        if (showMotoFareCollect) {
            BackHandler { showMotoFareCollect = false }
            MotoFareCollectScreen(onBack = { showMotoFareCollect = false })
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
        // Checked after every screen it deep-links into (Sacco/Ikimina/MotoOwnership/
        // HarvestAdvance/Loans/Invest/WeeklySavings/UpfrontDeposit/VupLoan/StudentLoan
        // above) -- this sequential if-chain's first match wins and return@IdsTheme's
        // out, so if showBank were checked first, tapping any row inside the hub would
        // just keep re-rendering the hub instead of opening what was tapped (both flags
        // stay true at once: this screen is still "open" underneath the one pushed on
        // top of it, same relationship Explore has with these same children).
        if (showBank) {
            BackHandler { showBank = false }
            val bankPrimaryAccount by viewModel.primaryAccount.collectAsState()
            val bankSavingsGoals by viewModel.savingsGoals.collectAsState()
            val bankInterestJar by viewModel.interestJar.collectAsState()
            val bankRoundUpSettings by viewModel.roundUpSettings.collectAsState()
            val bankSpendingInsight by viewModel.spendingInsight.collectAsState()
            BankHubScreen(
                primaryAccount = bankPrimaryAccount,
                savingsGoals = bankSavingsGoals,
                interestJar = bankInterestJar,
                roundUpSettings = bankRoundUpSettings,
                spendingInsight = bankSpendingInsight,
                onSetRoundUpSettings = viewModel::setRoundUpSettings,
                onCreateSavingsGoal = viewModel::createSavingsGoal,
                onBack = { showBank = false },
                onDepositToGoal = { goalId, goalName -> showBank = false; savingsFlowStep = SavingsFlowStep.Deposit(goalId, goalName) },
                onWithdrawFromGoal = { goalId, goalName, currentAmount -> showBank = false; savingsFlowStep = SavingsFlowStep.Withdraw(goalId, goalName, currentAmount) },
                onClaimInterest = { showBank = false; savingsFlowStep = SavingsFlowStep.ClaimInterest },
                onOpenBucketDetail = { target -> showBank = false; bucketDetailTarget = target },
                onOpenSacco = { showSacco = true },
                onOpenIkimina = { showIkimina = true },
                onOpenMotoOwnership = { showMotoOwnership = true },
                onOpenHarvestAdvance = { showHarvestAdvance = true },
                onOpenLoans = { showLoans = true },
                onOpenInvest = { showInvest = true },
                onOpenWeeklySavings = { showWeeklySavings = true },
                onOpenGrow31Savings = { showGrow31Savings = true },
                onOpenUpfrontDeposit = { showUpfrontDeposit = true },
                onOpenVupLoan = { showVupLoan = true },
                onOpenStudentLoan = { showStudentLoan = true },
                autoTransferCount = autoTransferCount,
                onOpenAutoTransfers = { showAutoTransfers = true },
                onOpenCreditScore = { showCreditScore = true },
                onOpenPay = { showBank = false; selectedTab = ItundaTab.Pay },
                // Real ordering fix: showSpending is checked AFTER showBank in this
                // same sequential if-chain below (unlike showCreditScore, which is
                // checked before it) -- without clearing showBank here first, tapping
                // this row would just keep re-rendering BankHubScreen forever, since
                // showBank's own `if` above it always wins and returns first. Same
                // real bug class this file's own showBank doc comment already warns
                // about for exactly this reason.
                onOpenSpendingInsight = { showBank = false; showSpending = true },
                // Real fix (2026-08-14) -- see HomeTab's own onOpenAccountDetail comment
                // above for the full "wrong home" story. showBank cleared first, same
                // reasoning as onOpenSpendingInsight just above.
                onOpenAccountDetail = { showBank = false; showAccountDetail = true },
            )
            return@IdsTheme
        }
        // Checked after showCard/showSettings/showAgentCash/transferStep/
        // savingsFlowStep (all set well above this point in the file) since this
        // screen's own Card/Manage/Top up/Send/Get interest actions deep-link into
        // each of them -- same ordering rule showBank's own comment above documents.
        if (showAccountDetail) {
            BackHandler { showAccountDetail = false }
            AccountDetailScreen(
                viewModel = viewModel,
                onBack = { showAccountDetail = false },
                onOpenCard = { showAccountDetail = false; showCard = true },
                onOpenManage = { showAccountDetail = false; showAccountManage = true },
                onTopUp = { showAccountDetail = false; showAgentCash = true },
                onSend = { showAccountDetail = false; transferStep = TransferStep.Recipient; transferFromAccount = null },
                onClaimInterest = { showAccountDetail = false; savingsFlowStep = SavingsFlowStep.ClaimInterest },
                onOpenBankAssets = { showAccountDetail = false; showBankAssets = true },
            )
            return@IdsTheme
        }
        if (showBankAssets) {
            BackHandler { showBankAssets = false }
            ItundaBankAssetsScreen(onBack = { showBankAssets = false })
            return@IdsTheme
        }
        // Checked after showCard/showDeviceList/showTransferHub/showForeignCurrency/
        // showSupport (all set well above/below this point) since this screen's own
        // rows deep-link into each of them -- same ordering rule showAccountDetail's
        // own comment above documents.
        if (showAccountManage) {
            BackHandler { showAccountManage = false }
            val manageContext = androidx.compose.ui.platform.LocalContext.current
            AccountManageScreen(
                accountNumber = viewModel.primaryAccount.value?.accountNumber ?: "",
                onBack = { showAccountManage = false },
                onOpenCard = { showAccountManage = false; showCard = true },
                onOpenDevices = { showAccountManage = false; showDeviceList = true },
                onOpenInterestJar = { showAccountManage = false; bucketDetailTarget = BucketDetailTarget.InterestJar },
                onOpenAutoTransfer = { showAccountManage = false; showTransferHub = true; showAutoTransfers = true },
                onOpenScheduledTransfers = { showAccountManage = false; showTransferHub = true; showScheduledTransfers = true },
                onOpenForeignCurrency = { showAccountManage = false; showForeignCurrency = true },
                onOpenBills = { manageContext.startActivity(android.content.Intent(manageContext, rw.itunda.app.miniapps.PayBillsMiniAppActivity::class.java)) },
                onOpenSupport = { showAccountManage = false; showSupport = true },
            )
            return@IdsTheme
        }
        if (showDeviceList) {
            BackHandler { showDeviceList = false }
            val devices by viewModel.devices.collectAsState()
            val deviceError by viewModel.deviceError.collectAsState()
            DeviceListScreen(
                devices = devices,
                onRevoke = { deviceId -> viewModel.revokeDeviceFromSettings(deviceId) },
                onBack = { showDeviceList = false },
                error = deviceError,
            )
            return@IdsTheme
        }
        bucketDetailTarget?.let { target ->
            BackHandler { bucketDetailTarget = null }
            when (target) {
                is BucketDetailTarget.InterestJar -> {
                    val jar by viewModel.interestJar.collectAsState()
                    BucketDetailScreen(
                        title = "Interest Jar",
                        subtitle = "Safe Box",
                        balanceText = "%,.0f RWF".format(jar?.balance ?: 0.0),
                        secondaryStatLabel = "Earned all-time",
                        secondaryStatValue = "%,.0f RWF".format(jar?.earnedTotal ?: 0.0),
                        fetchTransactions = { rw.itunda.core.network.NetworkClient.apiService.getInterestJarTransactions().transactions },
                        fillLabel = if ((jar?.earnedThisMonth ?: 0.0) > 0.0) "Get interest" else null,
                        onFill = if ((jar?.earnedThisMonth ?: 0.0) > 0.0) {
                            { bucketDetailTarget = null; savingsFlowStep = SavingsFlowStep.ClaimInterest }
                        } else null,
                        onBack = { bucketDetailTarget = null },
                    )
                }
                is BucketDetailTarget.Goal -> {
                    BucketDetailScreen(
                        title = target.name,
                        subtitle = "Savings Goal",
                        balanceText = "%,.0f RWF".format(target.currentAmount),
                        secondaryStatLabel = "Target",
                        secondaryStatValue = "%,.0f RWF".format(target.targetAmount),
                        fetchTransactions = { rw.itunda.core.network.NetworkClient.apiService.getSavingsGoalTransactions(target.id).transactions },
                        fillLabel = "Deposit",
                        onFill = {
                            bucketDetailTarget = null
                            savingsFlowStep = SavingsFlowStep.Deposit(target.id, target.name, wasAlreadyCompleted = target.currentAmount >= target.targetAmount)
                        },
                        withdrawLabel = "Withdraw",
                        onWithdraw = if (target.currentAmount > 0) {
                            { bucketDetailTarget = null; savingsFlowStep = SavingsFlowStep.Withdraw(target.id, target.name, target.currentAmount) }
                        } else null,
                        onBack = { bucketDetailTarget = null },
                    )
                }
            }
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
            return@IdsTheme
        }
        if (showVehicleValuation) {
            BackHandler { showVehicleValuation = false }
            VehicleValuationScreen(onBack = { showVehicleValuation = false })
            return@IdsTheme
        }
        if (showFamilyLink) {
            BackHandler { showFamilyLink = false }
            FamilyLinkScreen(onBack = { showFamilyLink = false })
            return@IdsTheme
        }
        if (showSubscriptions) {
            BackHandler { showSubscriptions = false }
            SubscriptionsScreen(onBack = { showSubscriptions = false })
            return@IdsTheme
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
                    onSendMoney = { showTransferHub = false; transferStep = TransferStep.Recipient; transferFromAccount = null },
                    onOpenAutoTransfers = { showAutoTransfers = true },
                    onOpenScheduledTransfers = { showScheduledTransfers = true },
                    onSplitBill = { showTransferHub = false; selectedTab = ItundaTab.Messages },
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
                    ItundaTab.Home -> {
                        val homeDiscoverItems by viewModel.discoverItems.collectAsState()
                        val homeUnreadNotificationCount by viewModel.unreadNotificationCount.collectAsState()
                        val homeIsOffline by viewModel.isOffline.collectAsState()
                        val homePrimaryAccount by viewModel.primaryAccount.collectAsState()
                        val homeProfile by viewModel.profile.collectAsState()
                        val homeIsRefreshing by viewModel.isRefreshing.collectAsState()
                        HomeTab(
                        discoverItems = homeDiscoverItems,
                        unreadNotificationCount = homeUnreadNotificationCount,
                        isOffline = homeIsOffline,
                        primaryAccount = homePrimaryAccount,
                        neighborhoodSet = homeProfile?.neighborhood != null,
                        isRefreshing = homeIsRefreshing,
                        onRetry = { viewModel.retry() },
                        onOpenPay = { selectedTab = ItundaTab.Pay },
                        onOpenNotifications = { showNotificationsFeed = true },
                        onOpenOverview = { showOverview = true },
                        onOpenBank = { showBank = true },
                        onOpenIdentity = { showIdentity = true },
                        onOpenLoans = { showLoans = true },
                        // Real fix (2026-08-14, direct user complaint: "when user click on
                        // that itunda account is when they see itunda bank details that's
                        // wrong bank details suppose to be accessed from bank not account
                        // right"). AccountDetailScreen is a real Toss BANK account-detail
                        // view (interest jar, Card/Manage, full ledger) -- same category of
                        // mistake this file's own onOpenCreditScore/onOpenSpendingInsight/
                        // autoTransferCount comments already document being caught and moved
                        // off Home once before. Tapping "itunda Bank account" (renamed
                        // 2026-08-23, see this row's own doc comment in
                        // AccountSwitcherSheet) in the account switcher now goes to Bank
                        // (its real home) instead of opening the ledger directly over Home;
                        // Bank's own new account-account card below is what actually opens
                        // AccountDetailScreen.
                        onOpenAccountDetail = { showBank = true },
                        // Real Naver-style Home redesign (2026-08-14, direct user
                        // reference: 5 real Naver Home screenshots -- search bar, weather/
                        // stock widgets, a Clip video grid, an infinite content feed).
                        // itunda has no weather/entertainment content to show honestly, but
                        // it IS a real super app (direct user correction: "itunda is super
                        // app more than just fintech") with real cross-vertical content --
                        // reuses the exact same show*=true flags Explore already wires to
                        // these same screens, just also reachable from Home's own feed now.
                        onOpenMarketplace = { showMarketplace = true },
                        onOpenCommunity = { showCommunity = true },
                        onOpenJobs = { showJobs = true },
                        onOpenProperty = { showProperty = true },
                        onOpenInvest = { showInvest = true },
                        onOpenShop = { showShop = true },
                        )
                    }
                    // Real, dedicated primary tab (2026-08-10, see ItundaTab's own doc
                    // comment) -- previously PayTab was only reachable via a showPay
                    // overlay from Home's QR icon or a Menu row. No BackHandler here,
                    // same as Explore/You below: a persistent bottom-nav destination,
                    // not a screen pushed on top of one.
                    ItundaTab.Pay -> {
                        val payTransactions by viewModel.transactions.collectAsState()
                        val payContext = androidx.compose.ui.platform.LocalContext.current
                        PayTab(
                        transactions = payTransactions,
                        onSend = { transferStep = TransferStep.Recipient; transferFromAccount = null },
                        onCashOutAtAgent = { showAgentCash = true },
                        onOpenSettings = { selectedTab = ItundaTab.You },
                        onBrowseMerchants = { selectedTab = ItundaTab.Explore },
                        onOpenSupport = { showSupport = true },
                        onOpenCard = { showCard = true },
                        payAMerchantSection = {
                            rw.itunda.feature.shop.impl.PayAMerchantSection(
                                deviceStepUpHost = { visible, onDismiss, onVerified ->
                                    DeviceStepUpHost(visible = visible, onDismiss = onDismiss, onVerified = onVerified)
                                },
                            )
                        },
                        onOpenRewardsMiniApp = {
                            payContext.startActivity(android.content.Intent(payContext, rw.itunda.app.miniapps.RewardTasksMiniAppActivity::class.java))
                        },
                        )
                    }
                    // Seventh and final Feature extraction (2026-07-23) -- see
                    // TalkScreen.kt's own header comment for why deviceStepUpHost is
                    // injected here rather than called directly inside :features:talk:impl.
                    // DeviceStepUpHost moved to :core:designsystem 2026-09-02 (no longer
                    // wraps a cross-Feature dialog), so a Feature module COULD call it
                    // directly now -- this injection wiring was left as-is since removing
                    // it is an unrelated cleanup, not required to unblock PayTab's own
                    // extraction (the reason this promotion happened).
                    ItundaTab.Messages -> TalkTab(
                        initialConversationId = pendingConversationId,
                        onConsumedInitial = { pendingConversationId = null },
                        deviceStepUpHost = { visible, onDismiss, onVerified ->
                            DeviceStepUpHost(visible = visible, onDismiss = onDismiss, onVerified = onVerified)
                        },
                    )
                    // Real Explore tab (2026-08-10, renamed from All) -- see
                    // ItundaTab's own doc comment. Same MenuScreen this file has used
                    // since 2026-07-24, just reached via a differently-named tab;
                    // Shop/Eats/Marketplace/Community/Jobs are new flat rows here since
                    // they lost their own primary tabs (Property already had its own
                    // row). Pay and My aren't rows here anymore -- both are their own
                    // primary tabs now.
                    ItundaTab.Explore -> {
                        val partnerMiniApps by viewModel.partnerMiniApps.collectAsState()
                        val menuContext = androidx.compose.ui.platform.LocalContext.current
                        MenuScreen(
                            onOpenShop = { showShop = true },
                            onOpenEats = { showEats = true },
                            onOpenMarketplace = { showMarketplace = true },
                            onOpenCommunity = { showCommunity = true },
                            onOpenJobs = { showJobs = true },
                            onOpenSettings = { showSettings = true },
                            onOpenInvest = { showInvest = true },
                            onOpenBank = { showBank = true },
                            onOpenMap = { showMap = true },
                            onOpenOverview = { showOverview = true },
                            onOpenLoans = { showLoans = true },
                            onOpenSupport = { showSupport = true },
                            onOpenCreditScore = { showCreditScore = true },
                            onOpenCertificate = { showCertificate = true },
                            onOpenIdentity = { showIdentity = true },
                            onOpenWeeklySavings = { showWeeklySavings = true },
                            onOpenGrow31Savings = { showGrow31Savings = true },
                            onOpenUpfrontDeposit = { showUpfrontDeposit = true },
                            onOpenYouthAccount = { showYouthAccount = true },
                            onOpenCard = { showCard = true },
                            onOpenTransit = { showTransit = true },
                            onOpenGroupAccounts = { showGroupAccounts = true },
                            onOpenIkimina = { showIkimina = true },
                            onOpenSacco = { showSacco = true },
                            onOpenHarvestAdvance = { showHarvestAdvance = true },
                            onOpenSpending = { showSpending = true },
                            onOpenRides = { showRides = true },
                            onOpenDesignatedDriver = { showDesignatedDriver = true },
                            onOpenBikeRental = { showBikeRental = true },
                            onOpenParking = { showParking = true },
                            onOpenMotoFareCollect = { showMotoFareCollect = true },
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
                            onSwitchToTalk = { selectedTab = ItundaTab.Messages },
                            onOpenProperty = { showProperty = true },
                            partnerMiniApps = partnerMiniApps,
                            onOpenRewardTasksMiniApp = {
                                menuContext.startActivity(android.content.Intent(menuContext, rw.itunda.app.miniapps.RewardTasksMiniAppActivity::class.java))
                            },
                            onOpenPayBillsMiniApp = {
                                menuContext.startActivity(android.content.Intent(menuContext, rw.itunda.app.miniapps.PayBillsMiniAppActivity::class.java))
                            },
                            onOpenInsuranceMiniApp = {
                                menuContext.startActivity(android.content.Intent(menuContext, rw.itunda.app.miniapps.InsuranceMiniAppActivity::class.java))
                            },
                            onOpenAccountBalanceMiniApp = {
                                menuContext.startActivity(android.content.Intent(menuContext, rw.itunda.app.miniapps.AccountBalanceMiniAppActivity::class.java))
                            },
                            onLaunchPartnerMiniApp = { activity, app, onError ->
                                rw.itunda.app.miniapps.PartnerMiniAppLoader.launch(activity = activity, app = app, onError = onError)
                            },
                        )
                    }
                    // Real, dedicated primary tab (2026-08-10, see ItundaTab's own doc
                    // comment) -- MyTab's own real content (orders/favorites/listings)
                    // is completely unchanged, just reached directly instead of via
                    // Explore's profile icon. No BackHandler, same persistent-tab
                    // reasoning as Pay/Explore above.
                    ItundaTab.You -> MyTab(
                        onBack = {},
                        onSwitchToShop = { showShop = true },
                        onSwitchToEats = { showEats = true },
                        onSwitchToMarketplace = { showMarketplace = true },
                        onSwitchToJobs = { showJobs = true },
                        onSwitchToProperty = { showProperty = true },
                    )
                }
            }
        }
    }
}

// ItundaBottomBar moved to ItundaAppSharedUi.kt (2026-09-02, Banking Feature-module
// decomposition slice 1) -- same package, zero import changes.

// HomeTab/HomeTopBar/AccountSwitcherSheet/PersonalRecommendationCard/DiscoverSection/
// HomeFeedEntry and the search+feed widgets from HomeTabWidgets.kt (now deleted) all
// moved to :features:home:impl (2026-09-02, Home Feature-module decomposition).

// Real Toss Bank account-detail screen (2026-08-13, 3 direct user screenshots of
// their own real Toss Bank account: "when you click on bank accounts that what
// you should see"). Reached by tapping the "itunda account" row in
// :features:home:impl's AccountSwitcherSheet, which had never actually been
// clickable before this.
// Distinct from both HomeTab's compact AccountHeroCard (a Home-tab summary, not a
// full ledger) and BankHubScreen's product catalog (savings goals/loans, not this
// account's own transaction history) -- this is the one real screen that shows
// balance + unclaimed interest + the actual transaction ledger for the account,
// matching the reference's header (back arrow, "Card"/"Manage"), balance block,
// interest row with its own "Get interest" CTA, Top up/Send buttons, and a
// date-grouped transaction list with a running balance on every row.
//
// Reuses only real, already-fetched data (MainViewModel.primaryAccount/.interestJar/
// .transactions, the same GET /api/v1/account/transactions this file's other
// screens already call) -- no new backend endpoint. TransactionDto carries no
// balance-snapshot-per-row field (confirmed by reading ApiService.kt), so each
// row's running balance is derived client-side by walking the real transaction
// list backward from the real current balance -- real arithmetic on real data,
// not an invented number.
@Composable
private fun AccountDetailScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onOpenCard: () -> Unit,
    onOpenManage: () -> Unit,
    onTopUp: () -> Unit,
    onSend: () -> Unit,
    onClaimInterest: () -> Unit,
    onOpenBankAssets: () -> Unit,
) {
    // Real Toss Bank reference (20 screenshots, 2026-08-21): this screen is
    // balance + interest + ledger only -- the product catalog (Save & grow/
    // Borrow) was briefly duplicated in here too (same 2026-08-21 session, direct
    // user instruction at the time), but a direct look at the live result showed
    // that was wrong: real Toss keeps that catalog on the bank HOME screen, not
    // the account-detail/ledger screen -- reverted per the user's own live
    // follow-up correction ("those below they are not supposed to be in itunda
    // account details screen ... they suppose to be in itunda bank home screen
    // like toss does"). BankHubScreen (below) is that real home screen and
    // already has this exact catalog -- untouched, was never the problem.
    BackHandler(onBack = onBack)
    val primaryAccount by viewModel.primaryAccount.collectAsState()
    val interestJar by viewModel.interestJar.collectAsState()
    val transactions by viewModel.transactions.collectAsState()
    val balance = primaryAccount?.balance ?: 0.0
    val currency = primaryAccount?.currency ?: "RWF"
    val currentUserId = primaryAccount?.userId

    val sorted = remember(transactions) { transactions.sortedByDescending { it.createdAt } }
    val withBalance = remember(sorted, balance, currentUserId) {
        var runningBalance = balance
        sorted.map { tx ->
            val afterBalance = runningBalance
            val delta = if (tx.senderId == currentUserId) -tx.amount else tx.amount
            runningBalance -= delta
            tx to afterBalance
        }
    }
    val grouped = remember(withBalance) {
        withBalance.groupBy { (tx, _) -> ledgerDateHeader(tx.createdAt) }
    }
    var selectedTransaction by remember { mutableStateOf<Pair<rw.itunda.core.network.TransactionDto, Double>?>(null) }
    val ledgerListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val touchedTransactionKey = remember { mutableStateOf<Any?>(null) }

    if (selectedTransaction != null) {
        val (tx, afterBalance) = selectedTransaction!!
        TransactionDetailScreen(
            transaction = tx,
            isOutgoing = tx.senderId == currentUserId,
            afterBalance = afterBalance,
            currency = currency,
            onBack = { selectedTransaction = null },
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize().background(Ids.colors.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Ids.layout.screenHorizontal, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(Ids.layout.minTouchTarget).clip(CircleShape).pressScaleClickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(IdsIcons.Back, contentDescription = stringResource(R.string.back), modifier = Modifier.size(18.dp), tint = Ids.colors.textPrimary)
            }
            Spacer(modifier = Modifier.weight(1f))
            // Real Toss reference (2026-08-13, direct user follow-up "still not the
            // same right?"): the real header's "card"/"Manage" both carry a leading
            // glyph, not bare text -- these two icons (CreditCard/Settings) are
            // already imported and used elsewhere in this file for the same real
            // destinations (see MenuScreen's own Card/Settings rows).
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.pressScaleClickable(onClick = onOpenCard).padding(8.dp)) {
                Icon(Icons.Outlined.CreditCard, contentDescription = null, modifier = Modifier.size(16.dp), tint = Ids.colors.textSecondary)
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.account_detail_card), color = Ids.colors.textSecondary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(modifier = Modifier.width(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.pressScaleClickable(onClick = onOpenManage).padding(8.dp)) {
                Icon(Icons.Outlined.Settings, contentDescription = null, modifier = Modifier.size(16.dp), tint = Ids.colors.textSecondary)
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.account_detail_manage), color = Ids.colors.textSecondary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        LazyColumn(
            // Real fix (2026-08-24, direct user follow-up: "toss uses spring effect
            // which users feel not only when they pressing a button but also when
            // they are scrolling through the lists like those transactions" --
            // watching this exact ledger scroll live on-device). See
            // SpringOverscroll.kt's own doc comment for the full sourced account.
            //
            // Follow-up fix (same day, "not as smooth as toss spring effect ...
            // user finger touch presable components while scroll user can feel
            // that spring effect"): trackScrollPressedKey adds the live
            // touch-follows-finger-during-scroll half of this -- see
            // ScrollPressTracker.kt's own doc comment.
            state = ledgerListState,
            modifier = Modifier.fillMaxSize().weight(1f)
                .then(rememberSpringOverscrollModifier())
                .trackScrollPressedKey(ledgerListState, touchedTransactionKey),
            contentPadding = PaddingValues(start = Ids.layout.screenHorizontal, end = Ids.layout.screenHorizontal, bottom = 16.dp),
        ) {
            item {
                Column(modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)) {
                    if (primaryAccount != null) {
                        // Real gap found live (2026-08-31, direct user correction: "it's
                        // not itunda account number it's itunda bank account number") --
                        // matches real Toss's own "토스뱅크 1000-XXXX-XXXX" pattern.
                        Text(
                            "itunda Bank ${primaryAccount!!.accountNumber.chunked(4).joinToString("-")}",
                            fontSize = 13.sp, color = Ids.colors.textSecondary,
                        )
                    }
                    val animatedBalance = rememberCountUp(balance)
                    Text("%,.0f $currency".format(animatedBalance), style = IdsTypography.LargeAmount, color = Ids.colors.textPrimary)
                }
            }
            val earnedThisMonth = interestJar?.earnedThisMonth ?: 0.0
            if (earnedThisMonth > 0.0) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Ids.colors.chip)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Ids.colors.brand))
                        Spacer(modifier = Modifier.width(10.dp))
                        // Real Toss reference: the row's own text is "이자 7원"
                        // ("Interest ₩7") -- a label plus the amount together, not
                        // the bare number this rendered as before.
                        Text(
                            "${stringResource(R.string.account_detail_interest_prefix)} %,.0f $currency".format(earnedThisMonth),
                            color = Ids.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        IdsButton(stringResource(R.string.account_detail_get_interest), onClick = onClaimInterest, variant = IdsButtonVariant.Filled, size = IdsButtonSize.Small)
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
            // Real "itunda Bank assets" summary row (2026-08-31, direct user-supplied
            // Toss Bank screenshot) -- see ItundaBankAssetsScreen.kt's own doc comment.
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().pressScaleClickable(onClick = onOpenBankAssets).padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.itunda_bank_assets_title), color = Ids.colors.textPrimary, fontSize = 15.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.itunda_bank_assets_view_all), color = Ids.colors.textSecondary, fontSize = 13.sp)
                        Icon(IdsIcons.ChevronRight, contentDescription = null, modifier = Modifier.size(16.dp), tint = Ids.colors.textTertiary)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            if (withBalance.isEmpty()) {
                item { EmptyState(stringResource(R.string.account_detail_empty)) }
            } else {
                grouped.forEach { (dateHeader, rows) ->
                    item {
                        Text(
                            dateHeader, color = Ids.colors.textTertiary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                        )
                    }
                    items(rows, key = { it.first.id }) { (tx, afterBalance) ->
                        AccountLedgerRow(
                            transaction = tx, isOutgoing = tx.senderId == currentUserId, afterBalance = afterBalance, currency = currency,
                            isScrollTouched = touchedTransactionKey.value == tx.id,
                            onClick = { selectedTransaction = tx to afterBalance },
                        )
                    }
                }
            }
        }
        // Real Toss Bank reference (20 screenshots, 2026-08-21, direct user
        // follow-up: "those buttons at bottom" -- the real Top up/Send row is
        // pinned to the bottom of the screen, not scrolling away with the ledger
        // content above it. Moved out of the LazyColumn into a fixed footer here,
        // matching FullScreenFlow's own bottomCTA convention on bank-mfe.
        //
        // Real fix (2026-08-24, direct user side-by-side against a real Toss Bank
        // screenshot: "there is no layer sitting btn button and those contents") --
        // dropped the .border() that used to draw a visible divider line above this
        // footer. The real reference has no seam at all between the scrolled ledger
        // and the fixed button row; same background color on both sides already
        // gives enough real separation on its own.
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .background(Ids.colors.background)
                .padding(horizontal = Ids.layout.screenHorizontal, vertical = 12.dp)
                .padding(bottom = 8.dp),
        ) {
            IdsButton(stringResource(R.string.account_detail_top_up), onClick = onTopUp, modifier = Modifier.weight(1f), variant = IdsButtonVariant.Tinted, size = IdsButtonSize.Medium)
            IdsButton(stringResource(R.string.home_send), onClick = onSend, modifier = Modifier.weight(1f), variant = IdsButtonVariant.Filled, size = IdsButtonSize.Medium)
        }
    }
}

// Real transaction-detail drill-in (2026-08-24, direct user follow-up: "no I mean
// presable effect" -- clarifying that the ledger row's missing press feedback was
// really pointing at a bigger gap, that tapping a row didn't go anywhere at all).
// Real Toss Bank reference (직접 3rd screenshot from this same thread's very first
// message: 상세내역 screen -- merchant name, amount with a copy action, 적요/거래
// 유형/일시/거래 후 잔액 as a clean label:value list). Honestly scoped to only the
// fields itunda's own TransactionDto (core/network/ApiService.kt) actually has --
// no invented "입금처/출금처" account-name row (senderId/recipientId are opaque
// user ids, not resolvable to a display name from this endpoint) and no
// "증명서 발급하기" certificate action (a real Korean bank-specific feature itunda
// has no backend for). The description shown here is the FULL, untouched original
// text (transaction.description, not ledgerRowTitle's stripped version) -- the list
// row strips the redundant category prefix precisely because this detail screen is
// where the complete text lives.
@Composable
private fun TransactionDetailScreen(
    transaction: rw.itunda.core.network.TransactionDto,
    isOutgoing: Boolean,
    afterBalance: Double,
    currency: String,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val amountText = "${if (isOutgoing) "-" else "+"}%,.0f $currency".format(transaction.amount)
    val amountColor = if (isOutgoing) Ids.colors.textPrimary else Ids.colors.brand
    val (rowIcon, rowIconColor) = ledgerRowIcon(transaction)

    Column(modifier = Modifier.fillMaxSize().background(Ids.colors.background)) {
        Box(
            modifier = Modifier.padding(8.dp).size(Ids.layout.minTouchTarget).clip(CircleShape).pressScaleClickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(IdsIcons.Back, contentDescription = stringResource(R.string.back), modifier = Modifier.size(18.dp), tint = Ids.colors.textPrimary)
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Ids.layout.screenHorizontal).padding(top = 12.dp),
        ) {
            Box(
                modifier = Modifier.size(56.dp).clip(CircleShape).background(rowIconColor),
                contentAlignment = Alignment.Center,
            ) {
                Icon(rowIcon, contentDescription = null, modifier = Modifier.size(26.dp), tint = Color.White)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(ledgerRowTitle(transaction), color = Ids.colors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            Text(amountText, color = amountColor, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(32.dp))
            TransactionDetailRow("Description", transaction.description)
            TransactionDetailRow("Type", transactionTypeLabel(transaction.type))
            TransactionDetailRow("Status", transaction.status.lowercase().replaceFirstChar { it.uppercase() })
            TransactionDetailRow("Date & time", ledgerFullDateTime(transaction.createdAt))
            TransactionDetailRow("Balance after", "%,.0f $currency".format(afterBalance))
            if (transaction.fee > 0.0) {
                TransactionDetailRow("Fee", "%,.0f $currency".format(transaction.fee))
            }
        }
    }
}

// TransactionDetailRow/ShellRow/ShellSection moved to ItundaAppFlatRows.kt
// (2026-09-02, Banking Feature-module decomposition slice 1) -- same package,
// zero import changes.

// PayTab/PayTabMode/MyPaymentCodeCard/AccountCardCarousel/PayHomeExtras/
// PayMoneyDetailScreen/CouponBoxScreen/MembershipScreen all moved to
// :features:pay:impl (2026-09-02, Pay Feature-module decomposition).

// MenuScreen/IconGridSection/AllTopBar/MenuSearchBar/menuSections all moved to
// :features:menu:impl (2026-09-02, Menu Feature-module decomposition).

// MyTab/ProfilePhotoCard/VerificationCard/VerificationRow/PinUpgradeCard all moved
// to :features:my:impl (2026-09-02, My Feature-module decomposition, completing
// the Home/Pay/Menu/My scope).

// FlatRow/FlatSection/FlatSectionRow moved to ItundaAppFlatRows.kt, SearchBar/
// AllTopBar/IconGridSection/MenuSearchBar moved to :features:menu:impl (2026-09-02,
// Menu Feature-module decomposition) -- were briefly in ItundaAppSharedUi.kt
// (Banking decomposition slice 1) before that.

// BackTopBar relocated 2026-07-23 to core/designsystem/components/HoodShared.kt while
// extracting Community into :features:community:impl -- every call site across :app
// now imports it from there instead.

@Preview(showBackground = true)
@Composable
fun PreviewItundaAppScreen() {
    ItundaAppScreen()
}

// Real "verify with itunda" consent screen. IdentityVerificationService's own doc
// comment describes exactly this screen -- "names the partner and exactly what will be
// shared ... no silent or default-approve path" -- but no client had ever implemented
// it, so a partner could create a request the user could never answer (found
// 2026-08-14). Nothing is disclosed until the user explicitly taps Approve: the GET
// below returns only the partner's name and the field labels, never the user's data.
@Composable
private fun IdentityVerificationConsentScreen(requestId: String, onDone: () -> Unit) {
    var request by remember { mutableStateOf<rw.itunda.core.network.IdentityVerificationRequestResponse?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var outcome by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(requestId) {
        loading = true
        error = null
        try {
            request = rw.itunda.core.network.NetworkClient.apiService.getIdentityVerificationRequest(requestId)
        } catch (e: retrofit2.HttpException) {
            error = rw.itunda.core.network.superAppErrorMessage(e)
        } catch (e: Exception) {
            error = "Couldn't reach itunda. Check your connection and try again."
        } finally {
            loading = false
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = Ids.layout.screenHorizontal, vertical = Ids.layout.screenVertical),
        verticalArrangement = Arrangement.spacedBy(Ids.layout.cardGap),
    ) {
        BackTopBar("Verify with itunda", onDone)
        when {
            loading -> Text("Loading request…", color = Ids.colors.textSecondary, fontSize = 14.sp)
            outcome != null -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(outcome!!, color = Ids.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("You can return to the app that sent you here.", color = Ids.colors.textSecondary, fontSize = 13.sp)
                IdsButton(text = "Done", onClick = onDone, modifier = Modifier.fillMaxWidth())
            }
            error != null -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(error!!, color = Ids.colors.danger, fontSize = 14.sp)
                IdsButton(text = "Close", onClick = onDone, modifier = Modifier.fillMaxWidth())
            }
            request != null && request!!.status != "PENDING" -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "This request has already been answered, or it expired. Requests are only valid for a few minutes.",
                    color = Ids.colors.textSecondary, fontSize = 14.sp,
                )
                IdsButton(text = "Close", onClick = onDone, modifier = Modifier.fillMaxWidth())
            }
            request != null -> {
                val req = request!!
                IdsCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("${req.partnerName} wants to verify your identity", color = Ids.colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("If you approve, itunda will share only this with them:", color = Ids.colors.textSecondary, fontSize = 13.sp)
                        req.requestedFields.forEach { field ->
                            Text("• $field", color = Ids.colors.textPrimary, fontSize = 14.sp)
                        }
                        Text(
                            "Nothing is shared unless you approve. itunda never shares your PIN, balance, or transaction history.",
                            color = Ids.colors.textSecondary, fontSize = 12.sp,
                        )
                    }
                }
                IdsButton(
                    text = if (busy) "…" else "Approve and share",
                    enabled = !busy,
                    onClick = {
                        busy = true
                        coroutineScope.launch {
                            try {
                                rw.itunda.core.network.NetworkClient.apiService.approveIdentityVerification(requestId)
                                outcome = "Shared with ${req.partnerName}."
                            } catch (e: retrofit2.HttpException) {
                                error = rw.itunda.core.network.superAppErrorMessage(e)
                            } catch (e: Exception) {
                                error = "Couldn't reach itunda. Check your connection and try again."
                            } finally {
                                busy = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                IdsButton(
                    text = "Decline",
                    variant = IdsButtonVariant.Tinted,
                    enabled = !busy,
                    onClick = {
                        busy = true
                        coroutineScope.launch {
                            try {
                                rw.itunda.core.network.NetworkClient.apiService.declineIdentityVerification(requestId)
                                outcome = "Declined. Nothing was shared."
                            } catch (e: retrofit2.HttpException) {
                                error = rw.itunda.core.network.superAppErrorMessage(e)
                            } catch (e: Exception) {
                                error = "Couldn't reach itunda. Check your connection and try again."
                            } finally {
                                busy = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
