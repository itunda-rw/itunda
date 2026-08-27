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
import androidx.compose.foundation.layout.padding
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
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.components.rememberCountUp
import rw.itunda.core.designsystem.components.rememberSpringOverscrollModifier
import rw.itunda.core.designsystem.components.trackScrollPressedKey
import rw.itunda.feature.talk.impl.TalkTab
import rw.itunda.feature.maps.impl.MapScreen
import rw.itunda.feature.shop.impl.CommerceShopContent
import rw.itunda.feature.eats.impl.EatsContent
import rw.itunda.core.designsystem.components.IdsButtonSize
import rw.itunda.core.designsystem.components.IdsButtonVariant
import rw.itunda.core.designsystem.components.IdsIconButton
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
import rw.itunda.feature.talk.impl.ObjectKey
import rw.itunda.feature.maps.impl.PlaceRestaurant
import rw.itunda.feature.maps.impl.PlaceMarket
import rw.itunda.feature.maps.impl.PlaceBank
import rw.itunda.feature.maps.impl.PlaceBusStop
import rw.itunda.feature.maps.impl.PlaceSchool
import rw.itunda.feature.maps.impl.PlaceItundaAgent
import rw.itunda.feature.talk.impl.ObjectCreditCard
import rw.itunda.feature.talk.impl.ObjectMobilePhone
import rw.itunda.feature.talk.impl.ObjectLightBulb
import rw.itunda.feature.talk.impl.TravelHouse
import rw.itunda.feature.talk.impl.TravelCar
import rw.itunda.feature.talk.impl.NatureStar
import rw.itunda.feature.talk.impl.NatureGlowingStar
import rw.itunda.feature.talk.impl.ObjectPen

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
internal val AccentIndigo = Color(0xFF7472F4)
internal val AccentTeal = Color(0xFF14AE85)
internal val AccentPurple = Color(0xFF7C5CFC)
internal val AccentOrange = Color(0xFFF2A93B)
internal val AccentRed = Color(0xFFFF5B5B)
private val AccentPink = Color(0xFFEC5F8C)
internal val AccentGray = Color(0xFF6B7684)

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
    data class Success(val message: String, val amountRwf: Long, val recipientLabel: String) : TransferStep()
}

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
private fun TransferSuccessScreen(amountRwf: Long, recipientLabel: String, onDone: () -> Unit) {
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
    )
}

// Promoted 2026-08-12 into core:designsystem's IdsCelebrationScreen (see its own doc
// comment) -- the exact same "MoneySuccessScreen"/"ConfettiBurst"/"ConfettiParticle"
// this comment used to describe, moved so Grow31SavingsScreen.kt/WeeklySavingsScreen.kt
// (a second and third real call site, a genuine completed-challenge milestone that had
// zero success acknowledgment at all) can reuse it without :app-to-:app duplication.

/** Real savings deposit/claim flow (2026-07-12) -- see SavingsAmountScreen.kt. */
private sealed class SavingsFlowStep : java.io.Serializable {
    data class Deposit(val goalId: String, val goalName: String) : SavingsFlowStep()
    data object ClaimInterest : SavingsFlowStep()
    // Real acknowledgment moment (2026-08-11) -- see IdsCelebrationScreen's own doc
    // comment: both deposit and claim previously just set savingsFlowStep = null on
    // success, same silent-close gap TransferStep.Success closes for transfers.
    // celebratory = true only for claimed interest -- real earned money, matches
    // Toss's own confetti-for-positive-moments example; a routine deposit into a goal
    // you set up yourself isn't that same kind of surprise-and-delight moment.
    data class Success(val headline: String, val message: String, val celebratory: Boolean) : SavingsFlowStep()
}

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
        var savingsFlowStep by rememberSaveable { mutableStateOf<SavingsFlowStep?>(null) }
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
                    onBack = { transferStep = null },
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
                        availableBalance = primaryAccountForTransfer?.availableBalance ?: 0.0,
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
                            biometricError = null
                            biometricAuth.authenticateForTransaction(
                                reason = if (isGift) "Confirm sending a $amountRwf RWF gift" else "Confirm sending $amountRwf RWF"
                            ) { success, error ->
                                if (success) {
                                    isSendingTransfer = true
                                    coroutineScope.launch {
                                        suspend fun doSend() = if (isGift) {
                                            viewModel.sendGift(step.accountNumber, amountRwf, giftNote, giftTheme)
                                        } else {
                                            viewModel.sendTransfer(step.accountNumber, amountRwf, memo = giftNote ?: "")
                                        }
                                        when (val result = doSend()) {
                                            is rw.itunda.app.ui.MoneyActionResult.Success -> {
                                                isSendingTransfer = false
                                                transferStep = TransferStep.Success(result.message, amountRwf, recipientDisplayName ?: step.accountNumber)
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
                                                    val retryResult = doSend()
                                                    isSendingTransfer = false
                                                    if (retryResult is rw.itunda.app.ui.MoneyActionResult.Success) transferStep = TransferStep.Success(retryResult.message, amountRwf, recipientDisplayName ?: step.accountNumber)
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
                is TransferStep.Success -> TransferSuccessScreen(
                    amountRwf = step.amountRwf,
                    recipientLabel = step.recipientLabel,
                    onDone = { transferStep = null },
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
                                is rw.itunda.app.ui.MoneyActionResult.Success -> {
                                    isSavingsSubmitting = false
                                    savingsFlowStep = SavingsFlowStep.Success("%,d RWF saved".format(amountRwf), result.message, celebratory = false)
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
                                    rw.itunda.core.designsystem.components.IdsToast.show(coroutineScope, result.message)
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
                                        when (retryResult) {
                                            is rw.itunda.app.ui.MoneyActionResult.Success -> savingsFlowStep = SavingsFlowStep.Success("%,d RWF saved".format(amountRwf), retryResult.message, celebratory = false)
                                            is rw.itunda.app.ui.MoneyActionResult.Queued -> {
                                                savingsFlowStep = null
                                                rw.itunda.core.designsystem.components.IdsToast.show(coroutineScope, retryResult.message)
                                            }
                                            is rw.itunda.app.ui.MoneyActionResult.Failure -> savingsError = retryResult.message
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
                                is rw.itunda.app.ui.MoneyActionResult.Success -> {
                                    isSavingsSubmitting = false
                                    // celebratory = true -- real earned money, matches
                                    // Toss's own confetti-for-positive-moments example
                                    // (see IdsCelebrationScreen's own doc comment).
                                    savingsFlowStep = SavingsFlowStep.Success("Interest claimed", result.message, celebratory = true)
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
                                        if (retryResult is rw.itunda.app.ui.MoneyActionResult.Success) savingsFlowStep = SavingsFlowStep.Success("Interest claimed", retryResult.message, celebratory = true)
                                        else if (retryResult is rw.itunda.app.ui.MoneyActionResult.Failure) savingsError = retryResult.message
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
                onLogout = { coroutineScope.launch { rw.itunda.core.network.SessionManager.logout() } },
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
            BankHubScreen(
                viewModel = viewModel,
                onBack = { showBank = false },
                onDepositToGoal = { goalId, goalName -> showBank = false; savingsFlowStep = SavingsFlowStep.Deposit(goalId, goalName) },
                onClaimInterest = { showBank = false; savingsFlowStep = SavingsFlowStep.ClaimInterest },
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
                onOpenManage = { showAccountDetail = false; showSettings = true },
                onTopUp = { showAccountDetail = false; showAgentCash = true },
                onSend = { showAccountDetail = false; transferStep = TransferStep.Recipient },
                onClaimInterest = { showAccountDetail = false; savingsFlowStep = SavingsFlowStep.ClaimInterest },
            )
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
                    onSendMoney = { showTransferHub = false; transferStep = TransferStep.Recipient },
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
                    ItundaTab.Home -> HomeTab(
                        viewModel,
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
                    // Real, dedicated primary tab (2026-08-10, see ItundaTab's own doc
                    // comment) -- previously PayTab was only reachable via a showPay
                    // overlay from Home's QR icon or a Menu row. No BackHandler here,
                    // same as Explore/You below: a persistent bottom-nav destination,
                    // not a screen pushed on top of one.
                    ItundaTab.Pay -> PayTab(
                        viewModel,
                        onSend = { transferStep = TransferStep.Recipient },
                        onCashOutAtAgent = { showAgentCash = true },
                        onSwitchTab = { selectedTab = it },
                        onOpenSupport = { showSupport = true },
                    )
                    // Seventh and final Feature extraction (2026-07-23) -- see
                    // TalkScreen.kt's own header comment for why deviceStepUpHost is
                    // injected (DeviceStepUpHost.kt wraps :features:payments:impl's
                    // dialog, so it can't become a direct Feature-to-Feature dependency).
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
                // Real fix (2026-08-24): was Ids.colors.surface, which diverges from
                // Ids.colors.background in dark mode (0x202027 vs 0x17171C), making
                // the bottom bar visibly stand out from the page -- real Toss/Coupang
                // keep every chrome bar the same color as the content underneath it.
                .background(Ids.colors.background),
                // Real fix (2026-08-25, direct user comparison against the live real
                // Toss app on the same physical device -- uiautomator-measured):
                // this Row's own top/bottom padding used to stack with each tab
                // Column's own padding below, a real "padding on padding" bug that
                // alone added ~10dp of dead space no design called for. Toss's real
                // bar (measured the same way, same device) has exactly one padding
                // layer -- so does this one now.
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ItundaTab.entries.forEach { tab ->
                val selected = tab == selectedTab
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .pressScaleClickable { onSelect(tab) }
                        // Real fix (2026-08-25) -- matches Toss's own real measured
                        // ~8dp top / ~8dp bottom inset exactly (this Column is now the
                        // only padding layer, see the Row's own comment above).
                        .padding(top = 8.dp, bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label,
                        modifier = Modifier.size(24.dp),
                        tint = if (selected) Ids.colors.brand else Ids.colors.textTertiary
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = tab.label,
                        fontSize = 11.sp,
                        // Real fix (2026-08-25, same live-device comparison) -- Compose's
                        // Text defaults to the font's full built-in line-height metrics
                        // (~24dp measured for this 11sp label), well beyond the glyphs'
                        // own ink; Toss's real native-Android label renders the same text
                        // at ~14dp. lineHeight + includeFontPadding=false + a trimmed
                        // LineHeightStyle is Compose's own documented fix for exactly
                        // this gap, not a made-up workaround.
                        lineHeight = 13.sp,
                        style = LocalTextStyle.current.copy(
                            platformStyle = PlatformTextStyle(includeFontPadding = false),
                            lineHeightStyle = LineHeightStyle(
                                alignment = LineHeightStyle.Alignment.Center,
                                trim = LineHeightStyle.Trim.Both,
                            ),
                        ),
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (selected) Ids.colors.brand else Ids.colors.textTertiary
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTab(
    viewModel: MainViewModel,
    onOpenPay: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    onOpenOverview: () -> Unit = {},
    onOpenBank: () -> Unit = {},
    onOpenIdentity: () -> Unit = {},
    onOpenLoans: () -> Unit = {},
    onOpenAccountDetail: () -> Unit = {},
    onOpenMarketplace: () -> Unit = {},
    onOpenCommunity: () -> Unit = {},
    onOpenJobs: () -> Unit = {},
    onOpenProperty: () -> Unit = {},
    onOpenInvest: () -> Unit = {},
    onOpenShop: () -> Unit = {},
) {
    val discoverItems by viewModel.discoverItems.collectAsState()
    val unreadNotificationCount by viewModel.unreadNotificationCount.collectAsState()
    val isOffline by viewModel.isOffline.collectAsState()
    val primaryAccount by viewModel.primaryAccount.collectAsState()
    // Real, minimal usage signal (2026-08-10) -- see the "itunda: the wedge, not the
    // mirror" strategy memo, recommendation (ii), and rw.itunda.core.network.
    // recordAnalyticsEvent's own doc comment. Fired once per real composition of
    // Home, the baseline every retention question is measured against -- same event
    // name/shape bank-mfe's identical HomeView effect already fires.
    LaunchedEffect(Unit) { rw.itunda.core.network.recordAnalyticsEvent("home_view") }

    // Real Naver-style Home redesign (2026-08-14, direct user reference: 5 real
    // Naver Home screenshots -- search bar, weather/stock widgets, a Clip video
    // grid, an infinite content feed). itunda has no weather/entertainment content
    // to show honestly, but it IS a real super app (direct user correction: "itunda
    // is super app more than just fintech") with real cross-vertical content -- the
    // same Marketplace/Community/Jobs/Property "my neighborhood" endpoints Explore's
    // own screens already call, just never merged into one feed before. Each
    // screen fetches its own minimal real data (same convention BankHubScreen's own
    // depositProtection/creditScore fetches already establish), not pushed into
    // MainViewModel as a global concern.
    var stocks by remember { mutableStateOf<List<rw.itunda.core.network.StockDto>>(emptyList()) }
    var trendingListings by remember { mutableStateOf<List<rw.itunda.core.network.ListingDto>>(emptyList()) }
    var feedEntries by remember { mutableStateOf<List<HomeFeedEntry>>(emptyList()) }
    LaunchedEffect(Unit) {
        try { stocks = rw.itunda.core.network.NetworkClient.apiService.getStocks().stocks.take(2) } catch (_: Exception) {}
    }
    // Real fix (2026-08-26, live-caught: uiautomator logs showed 4 guaranteed
    // 400s -- NeighborhoodNotSetException -- firing on every single Home load
    // for any user who hasn't set a neighborhood yet). All 4 of these are
    // real "my neighborhood" endpoints that unconditionally throw when
    // MarketplaceService.myNeighborhood's own real caller.neighborhood check
    // fails server-side; the try/catch below already degraded gracefully
    // (empty feed, no crash) but still wasted 4 real round-trips + 4 noisy
    // error logs every load. viewModel.profile is already fetched at app
    // launch for other reasons (see MainViewModel's own init), so this is a
    // free, already-cached check, not a 5th network call to save 4.
    val profile by viewModel.profile.collectAsState()
    LaunchedEffect(profile?.neighborhood) {
        if (profile?.neighborhood == null) return@LaunchedEffect
        val listings = try { rw.itunda.core.network.NetworkClient.apiService.getListingsMyNeighborhood().listings } catch (_: Exception) { emptyList() }
        val posts = try { rw.itunda.core.network.NetworkClient.apiService.getCommunityPostsMyNeighborhood().posts } catch (_: Exception) { emptyList() }
        val jobs = try { rw.itunda.core.network.NetworkClient.apiService.getJobPostsMyNeighborhood().posts } catch (_: Exception) { emptyList() }
        val properties = try { rw.itunda.core.network.NetworkClient.apiService.getPropertyListingsMyNeighborhood().listings } catch (_: Exception) { emptyList() }
        // Listings with a real photo lead the Trending grid (Naver's own Clip section
        // is image-first); the rest -- including photo-less listings -- flow into the
        // merged feed below like every other vertical.
        val (withPhoto, withoutPhoto) = listings.partition { !it.photoUrl.isNullOrBlank() }
        trendingListings = withPhoto.take(4)
        val trendingIds = trendingListings.map { it.id }.toSet()
        feedEntries = (
            (listings.filter { it.id !in trendingIds }).map {
                HomeFeedEntry(it.id, "marketplace", it.title, "%,.0f RWF".format(it.price), it.createdAt, it.photoUrl)
            } +
            posts.map { HomeFeedEntry(it.id, "community", it.title, it.body.take(80), it.createdAt) } +
            jobs.map { HomeFeedEntry(it.id, "jobs", it.title, "${it.payType} · %,.0f RWF".format(it.payAmount), it.createdAt) } +
            properties.map { HomeFeedEntry(it.id, "property", it.title, "%,.0f RWF".format(it.price), it.createdAt) }
        ).sortedByDescending { it.createdAt }.take(30)
    }
    // Real blended "universal search" (2026-08-14, direct user reference: real Naver
    // search results blend multiple content types on one page -- products, places,
    // posts -- each with its own card style, not a single flat list). itunda's own
    // equivalents are its 5 real content verticals; all fired in parallel (matching
    // this file's own "each screen fetches its own minimal real data" convention),
    // each independently null (loading) / empty (no matches) / populated, rendered as
    // labeled sections rather than one merged, type-blind list.
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<rw.itunda.core.network.ProductSearchResultDto>?>(null) }
    var marketplaceResults by remember { mutableStateOf<List<HomeFeedEntry>?>(null) }
    var communityResults by remember { mutableStateOf<List<HomeFeedEntry>?>(null) }
    var jobResults by remember { mutableStateOf<List<HomeFeedEntry>?>(null) }
    var propertyResults by remember { mutableStateOf<List<HomeFeedEntry>?>(null) }
    val searchScope = rememberCoroutineScope()
    fun runSearch(query: String) {
        searchResults = null; marketplaceResults = null; communityResults = null; jobResults = null; propertyResults = null
        searchScope.launch {
            searchResults = try { rw.itunda.core.network.NetworkClient.apiService.searchProducts(query).products } catch (_: Exception) { emptyList() }
        }
        searchScope.launch {
            marketplaceResults = try {
                rw.itunda.core.network.NetworkClient.apiService.searchListings(query).listings.map {
                    HomeFeedEntry(it.id, "marketplace", it.title, "%,.0f RWF".format(it.price), it.createdAt, it.photoUrl)
                }
            } catch (_: Exception) { emptyList() }
        }
        searchScope.launch {
            communityResults = try {
                rw.itunda.core.network.NetworkClient.apiService.searchCommunityPosts(query).posts.map {
                    HomeFeedEntry(it.id, "community", it.title, it.body.take(80), it.createdAt)
                }
            } catch (_: Exception) { emptyList() }
        }
        searchScope.launch {
            jobResults = try {
                rw.itunda.core.network.NetworkClient.apiService.searchJobPosts(query).posts.map {
                    HomeFeedEntry(it.id, "jobs", it.title, "${it.payType} · %,.0f RWF".format(it.payAmount), it.createdAt)
                }
            } catch (_: Exception) { emptyList() }
        }
        searchScope.launch {
            propertyResults = try {
                rw.itunda.core.network.NetworkClient.apiService.searchPropertyListings(query).listings.map {
                    HomeFeedEntry(it.id, "property", it.title, "%,.0f RWF".format(it.price), it.createdAt)
                }
            } catch (_: Exception) { emptyList() }
        }
    }

    // Real Toss/Kakao pull-to-refresh (2026-08-11 research pass) -- Home had no way
    // to manually refresh at all beyond leaving and re-entering the tab, despite
    // being the one screen with the most live, changing data (balance, transactions,
    // Discover). MainViewModel.isRefreshing already spans the exact duration of the
    // real fetch (see its own doc comment), so the indicator only hides once fresh
    // data has actually landed rather than on a fixed timer.
    val pullToRefreshState = rememberPullToRefreshState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    LaunchedEffect(pullToRefreshState.isRefreshing) {
        if (pullToRefreshState.isRefreshing) viewModel.retry()
    }
    LaunchedEffect(isRefreshing) {
        if (!isRefreshing) pullToRefreshState.endRefresh()
    }

    Box(Modifier.fillMaxSize().nestedScroll(pullToRefreshState.nestedScrollConnection)) {
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
            item { HomeTopBar(onOpenPay = onOpenPay, onOpenNotifications = onOpenNotifications, onOpenOverview = onOpenOverview, onOpenAccountDetail = onOpenAccountDetail, unreadCount = unreadNotificationCount) }
        item {
            HomeSearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it; if (it.isBlank()) searchResults = null else runSearch(it) },
                onClear = { searchQuery = ""; searchResults = null },
            )
        }
        // Real blended "universal search" results (2026-08-14) -- see this Column's own
        // state declarations above for the full "why" (Naver blends multiple content
        // types on one results page). Each of the 5 sources is independently
        // null/empty/populated; a section only renders once its own fetch actually
        // resolves, and only if it found something -- no empty section headers, no
        // fabricated "0 results" padding.
        if (searchQuery.isNotBlank()) {
            val stillLoading = searchResults == null || marketplaceResults == null ||
                communityResults == null || jobResults == null || propertyResults == null
            val totalResults = (searchResults?.size ?: 0) + (marketplaceResults?.size ?: 0) +
                (communityResults?.size ?: 0) + (jobResults?.size ?: 0) + (propertyResults?.size ?: 0)
            if (stillLoading && totalResults == 0) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                        androidx.compose.material3.CircularProgressIndicator(color = Ids.colors.brand)
                    }
                }
            } else if (!stillLoading && totalResults == 0) {
                item { EmptyState(stringResource(R.string.home_search_empty)) }
            } else {
                searchResults?.takeIf { it.isNotEmpty() }?.let { results ->
                    item { HomeSearchSectionHeader(stringResource(R.string.home_search_section_products)) }
                    items(results, key = { "product_${it.id}" }) { result -> HomeSearchResultRow(result, onClick = onOpenShop) }
                }
                marketplaceResults?.takeIf { it.isNotEmpty() }?.let { results ->
                    item { HomeSearchSectionHeader(stringResource(R.string.home_search_section_marketplace)) }
                    items(results, key = { "marketplace_${it.id}" }) { entry -> HomeFeedRow(entry, onClick = onOpenMarketplace) }
                }
                communityResults?.takeIf { it.isNotEmpty() }?.let { results ->
                    item { HomeSearchSectionHeader(stringResource(R.string.home_search_section_community)) }
                    items(results, key = { "community_${it.id}" }) { entry -> HomeFeedRow(entry, onClick = onOpenCommunity) }
                }
                jobResults?.takeIf { it.isNotEmpty() }?.let { results ->
                    item { HomeSearchSectionHeader(stringResource(R.string.home_search_section_jobs)) }
                    items(results, key = { "jobs_${it.id}" }) { entry -> HomeFeedRow(entry, onClick = onOpenJobs) }
                }
                propertyResults?.takeIf { it.isNotEmpty() }?.let { results ->
                    item { HomeSearchSectionHeader(stringResource(R.string.home_search_section_property)) }
                    items(results, key = { "property_${it.id}" }) { entry -> HomeFeedRow(entry, onClick = onOpenProperty) }
                }
            }
        } else {
        // Real fix (2026-08-13, direct live-device catch): MainViewModel.isOffline
        // was already real and correctly toggled (showOfflinePlaceholder's own doc
        // comment even claims this screen is "labeled via isOffline rather than
        // presented as real") -- but nothing anywhere in this file actually read
        // it. A genuinely offline user saw normal-looking placeholder data (a
        // "RWF 0" account, a fixed 3-item Discover feed) with zero indication any of
        // it wasn't real.
        if (isOffline) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Ids.colors.dangerTint)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.CloudOff, contentDescription = null, modifier = Modifier.size(18.dp), tint = Ids.colors.danger)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(stringResource(R.string.home_offline_banner), color = Ids.colors.danger, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        // Real personalized recommendation card (2026-08-11) -- direct comparison
        // against real Toss Bank reference screenshots (user-provided): Toss leads
        // Home with a large, illustrated, name-addressed card ("TUYIZERE ERIC님 복권
        // 열어보기"), not a small generic list entry. discoverItems was already real,
        // already fetched (GET /api/v1/discover) -- DiscoverSection further down just
        // rendered every item at the same small, unpersonalized weight regardless of
        // real fields like isNew/badge that exist specifically to signal priority.
        // Promotes the single highest-priority real item to hero treatment instead of
        // inventing new fabricated content; DiscoverSection below excludes it from its
        // own list so nothing renders twice.
        // Sorted by `priority`, not `isNew` (2026-08-11) -- see backend
        // DiscoverService's own doc comment (Toss Intelligence-banner research): the
        // backend now does real per-user eligibility + ranking (KYC prompts outrank a
        // static "Yego Vouchers" promo, etc.), the same "server-side decision, not
        // exposed to the client" principle that article's own architecture is built
        // around. Client-side `isNew` sorting was itunda's own invented substitute
        // before the backend had any real ranking signal to sort by.
        val heroDiscoverItem = discoverItems.sortedByDescending { it.priority }.firstOrNull()
        if (heroDiscoverItem != null) {
            item {
                // Real per-category destination (2026-08-13, direct user comparison:
                // "itunda intelligence banner should [look] like this") -- the real
                // Toss lottery banner's own CTA button is genuinely tappable, unlike
                // this card's prior deliberately-inert CTA (see PersonalRecommendationCard's
                // own doc comment on why it used to be plain Text, not a button: no
                // destination existed on DiscoverItem at the time). DiscoverService's
                // real backend only ever emits 3 real category values
                // (account/savings/credit, confirmed by grep) -- each maps to a real,
                // already-built itunda screen, not a guess.
                val onOpenAction: () -> Unit = when (heroDiscoverItem.category) {
                    "account" -> onOpenIdentity
                    "savings" -> onOpenBank
                    "credit" -> onOpenLoans
                    else -> {
                        {}
                    }
                }
                PersonalRecommendationCard(heroDiscoverItem, onOpenAction = onOpenAction)
            }
        }
        // Real architectural fix (2026-08-13, direct user directive): "all itunda
        // product features are independent and isolated -- itunda bank is a complete
        // product... tabs are not products, are just access points." The itunda Bank
        // summary card (total saved), spending insight, and credit score all used to
        // render directly here on Home -- real Bank-product content duplicated onto
        // a tab that's meant to be a generic access point, not itself a product. All
        // three moved into BankHubScreen (see its own doc comment), which is now the
        // one complete, self-contained place for everything Bank. The itunda Pay
        // account card (balance, Cash out/Send, recent transactions) had the exact
        // same problem -- direct user follow-up after the Bank fix, pointing at a
        // screenshot still showing this card on Home: "this is bank features
        // remained in home tab move them keep each feature independent and isolated."
        // Moved into PayTab (see its own doc comment) for the same reason. Home no
        // longer carries any Bank- or Pay-specific data or destinations at all,
        // keeping Home free to change shape later (the user's own stated example: a
        // future Naver-style search surface) without that change touching either
        // product.
        // Real Discover feed (found 2026-07-22) -- MainViewModel already fetched this
        // from GET /api/v1/discover on every launch, but it was never rendered
        // anywhere in the app: a real, live data flow with no UI consumer. See
        // DiscoverController.kt's own promotional-item catalog on the backend.
        // Excludes heroDiscoverItem (2026-08-11) -- promoted above into
        // PersonalRecommendationCard, shouldn't also render here at the smaller weight.
        val remainingDiscoverItems = discoverItems.filter { it.id != heroDiscoverItem?.id }
        if (remainingDiscoverItems.isNotEmpty()) {
            item { DiscoverSection(remainingDiscoverItems) }
        }
        // Real market widget row (2026-08-14) -- account balance (real, already
        // fetched) plus real RSE stock ticker chips (getStocks, InvestScreen's own
        // real data source), matching Naver's own weather/stock-index widget row
        // structurally without inventing weather data itunda has no source for.
        if (primaryAccount != null || stocks.isNotEmpty()) {
            item { HomeMarketWidgetRow(primaryAccount, stocks, onOpenBank = onOpenBank, onOpenInvest = onOpenInvest) }
        }
        if (trendingListings.isNotEmpty()) {
            item { HomeTrendingGrid(trendingListings, onOpenMarketplace = onOpenMarketplace) }
        }
        if (feedEntries.isNotEmpty()) {
            item {
                Text(
                    stringResource(R.string.home_feed_title),
                    color = Ids.colors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            items(feedEntries, key = { it.id }) { entry ->
                HomeFeedRow(
                    entry,
                    onClick = when (entry.kind) {
                        "marketplace" -> onOpenMarketplace
                        "community" -> onOpenCommunity
                        "jobs" -> onOpenJobs
                        "property" -> onOpenProperty
                        else -> ({})
                    },
                )
            }
        }
        }
        }
        PullToRefreshContainer(state = pullToRefreshState, modifier = Modifier.align(Alignment.TopCenter))
    }
}

// Real cross-vertical feed entry (2026-08-14) -- see HomeTab's own Naver-redesign
// doc comment. A plain discriminated shape, not a sealed hierarchy: the 4 real
// source DTOs (ListingDto/CommunityPostDto/JobPostDto/PropertyListingDto) share no
// common interface, and this is only ever used to sort+render, not to dispatch
// type-specific business logic.
internal data class HomeFeedEntry(
    val id: String,
    val kind: String,
    val title: String,
    val subtitle: String,
    val createdAt: String,
    val photoUrl: String? = null,
)

// Real, display-only mirrors of each product's own real backend rate constant --
// same convention Grow31SavingsScreen's own grow31BonusRateForStreak() and
// UpfrontDepositScreen's own ANNUAL_RATE already establish, just file-scoped here
// since BankHubScreen's hub-level teaser rows (below) need them before any specific
// plan exists to read a real per-plan rate off of. Keep in sync with the real
// source: WeeklySavingsService.BASE_RATE/BONUS_RATE, Grow31SavingsService's real
// streak-bonus tier table (10.0 at the max 31-day streak), UpfrontDepositScreen's
// own ANNUAL_RATE.
private const val BANK_HUB_WEEKLY_SAVINGS_BASE_RATE = 5.0
private const val BANK_HUB_GROW31_MAX_BONUS_RATE = 10.0
private const val BANK_HUB_UPFRONT_DEPOSIT_ANNUAL_RATE = 2.80

// Real itunda Bank product hub (2026-08-11) -- see the doc comment on the showBank
// state var in ItundaAppScreen for the full "itunda Bank vs itunda Pay/account"
// naming research this came out of. Every row here is a real, already-built screen
// (see LoansScreen.kt/InvestScreen.kt/SaccoScreen.kt/IkiminaScreen.kt/etc.'s own doc
// comments) -- this just gives them a shared front door with real aggregate data,
// instead of each living as an unconnected flat row. Sections mirror MenuScreen's
// own already-researched "Save & grow"/"Borrow" split (see its own "Real Toss Bank
// reference mapping" comment) rather than inventing a new taxonomy, and Moto-Taxi
// Ownership/Harvest advance sit under Borrow there (not Save & grow, despite the old
// Home coop rail grouping them with SACCO/Ikimina) because both convert to a loan --
// kept consistent with that existing, already-vetted categorization rather than the
// coop rail's simpler "Rwanda savings products" framing this replaces.
//
// Real Toss Bank reference (20 screenshots, 2026-08-21, direct user follow-up:
// "bank home screen should look 100% like toss bank screen, icons, size, layout,
// functionality, everything"): the real screen has no card-holding 카드/관리-style
// header tab at all -- every section (Save & grow, Borrow) renders flat and
// continuous, always visible, not behind a toggle. The BankHubTab split this
// replaces was itself an itunda invention reasoning from a DIFFERENT real
// screenshot set (Section 65's Card/Manage header comparison) -- removed now that
// a more direct, more recent real reference shows the actual real structure.

@Composable
private fun BankHubScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onDepositToGoal: (goalId: String, goalName: String) -> Unit,
    onClaimInterest: () -> Unit,
    onOpenSacco: () -> Unit,
    onOpenIkimina: () -> Unit,
    onOpenMotoOwnership: () -> Unit,
    onOpenHarvestAdvance: () -> Unit,
    onOpenLoans: () -> Unit,
    onOpenInvest: () -> Unit,
    onOpenWeeklySavings: () -> Unit,
    onOpenGrow31Savings: () -> Unit,
    onOpenUpfrontDeposit: () -> Unit,
    onOpenVupLoan: () -> Unit,
    onOpenStudentLoan: () -> Unit,
    // Real Toss Bank reference (12-image direct comparison, 2026-08-13, user caught a
    // real mistake: "you mixed itunda bank with home screen") -- the real "Auto
    // Transfer / N Items" row belongs on the real Toss BANK account-detail screen,
    // not the super-app Home tab. Was briefly (and wrongly) added to HomeTab's
    // AccountHeroCard instead -- moved here, its real home, using the same real
    // autoTransferCount data (AutoTransferListScreen) already fetched at the top
    // level for exactly this purpose.
    autoTransferCount: Int = 0,
    onOpenAutoTransfers: () -> Unit = {},
    onOpenCreditScore: () -> Unit = {},
    onOpenSpendingInsight: () -> Unit = {},
    // Real fix (2026-08-14) -- see HomeTab's own onOpenAccountDetail comment for the
    // full story: this is Bank's real home for the account-account ledger view, not
    // Home's account switcher.
    onOpenAccountDetail: () -> Unit = {},
) {
    BackHandler(onBack = onBack)
    val primaryAccount by viewModel.primaryAccount.collectAsState()
    val savingsGoals by viewModel.savingsGoals.collectAsState()
    val interestJar by viewModel.interestJar.collectAsState()
    val roundUpSettings by viewModel.roundUpSettings.collectAsState()
    var showRoundUpDialog by remember { mutableStateOf(false) }
    var showNewGoalDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    // Real Deposit Protection Fund status (2026-08-11) -- own-screen fetch, same
    // "each screen fetches its own minimal real data" precedent OverviewScreen's own
    // getProfile() call already establishes, rather than growing MainViewModel's
    // Home-load path with a fetch only this screen needs.
    var depositProtection by remember { mutableStateOf<rw.itunda.core.network.DepositProtectionStatus?>(null) }
    LaunchedEffect(Unit) {
        try {
            depositProtection = rw.itunda.core.network.NetworkClient.apiService.getDepositProtectionStatus().status
        } catch (_: Exception) {
            // Non-critical -- the disclosure copy below still renders without it.
        }
    }
    // Real architectural fix (2026-08-13, direct user directive): credit score and
    // spending insight used to be fetched by HomeTab -- moved here, same "each
    // screen fetches its own minimal real data" precedent as depositProtection
    // above, now that Bank (not Home) is their real home.
    val spendingInsight by viewModel.spendingInsight.collectAsState()
    val spendingTopCategory = spendingInsight?.categories?.maxByOrNull { it.amount.toDouble() }
    var creditScore by remember { mutableStateOf<rw.itunda.core.network.CreditScoreResponse?>(null) }
    LaunchedEffect(Unit) {
        try {
            creditScore = rw.itunda.core.network.NetworkClient.apiService.getCreditScore()
        } catch (_: Exception) {
            // Non-critical -- the row just won't render if this fails.
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            BackTopBar(title = stringResource(R.string.bank_title), onBack = onBack)
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = Ids.layout.screenHorizontal),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(Ids.layout.cardGap),
        ) {
            // Real Toss Bank reference (20 screenshots, 2026-08-21, direct user
            // follow-up: "bank home screen should look 100% like toss bank screen,
            // icons, size, layout, functionality, everything"): the real screen has
            // no card anywhere here -- the account balance sits flat on the page
            // background, "Total saved" (an itunda-only aggregate with no real Toss
            // equivalent) is gone, and Auto-transfer is a plain flat row, not a card.
            // Tapping the balance still opens the real ledger (AccountDetailScreen),
            // same real destination as before.
            if (primaryAccount != null) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().pressScaleClickable(onClick = onOpenAccountDetail).padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(stringResource(R.string.bank_account_account), fontSize = 13.sp, color = Ids.colors.textSecondary)
                            Spacer(modifier = Modifier.height(4.dp))
                            val animatedBalance = rememberCountUp(primaryAccount!!.balance)
                            Text(
                                "%,.0f ${primaryAccount!!.currency}".format(animatedBalance),
                                fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Ids.colors.textPrimary,
                            )
                        }
                        Icon(IdsIcons.ChevronRight, contentDescription = null, tint = Ids.colors.textTertiary)
                    }
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().pressScaleClickable(onClick = onOpenAutoTransfers).padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(32.dp).clip(CircleShape).background(Ids.colors.chip),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Outlined.Autorenew, contentDescription = null, tint = Ids.colors.textPrimary, modifier = Modifier.size(16.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(stringResource(R.string.home_auto_transfer_title), color = Ids.colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (autoTransferCount > 0) stringResource(R.string.home_auto_transfer_active, autoTransferCount) else stringResource(R.string.home_auto_transfer_setup),
                            color = Ids.colors.textSecondary,
                            fontSize = 13.sp,
                        )
                        Icon(IdsIcons.ChevronRight, contentDescription = null, tint = Ids.colors.textTertiary, modifier = Modifier.size(18.dp))
                    }
                }
                androidx.compose.material3.HorizontalDivider(color = Ids.colors.divider, thickness = 0.5.dp)
            }
            item {
                val roundUpOff = stringResource(R.string.home_round_up_off)
                val roundUpOn = stringResource(R.string.home_round_up_on)
                val roundUpSetUp = stringResource(R.string.home_round_up_set_up)
                val interestJarLabel = stringResource(R.string.home_interest_jar)
                val roundUpTitle = stringResource(R.string.home_round_up_title)
                val roundUpRoundingText = roundUpSettings?.roundToNearest?.let { stringResource(R.string.home_round_up_rounding, "%,.0f".format(it)) }
                val savingsProgressPattern = stringResource(R.string.home_savings_progress)
                val savingsProgressByDatePattern = stringResource(R.string.home_savings_progress_by_date)
                ShellSection(
                    title = stringResource(R.string.bank_save_grow),
                    rows = buildList {
                        interestJar?.let { jar ->
                            // Real interest-methodology transparency (2026-08-11) --
                            // this row only ever showed the opaque earned-this-month
                            // figure, never the rate or accrual frequency backing it
                            // (SavingsService.accrueInterest() divides jar.rate, the
                            // real annual rate, by 365 for a real daily accrual --
                            // that math was never shown to the user on this platform).
                            // Matches the same fix applied to bank-mfe's mislabeled
                            // "daily interest" copy the same day.
                            add(
                                ShellRow(
                                    interestJarLabel,
                                    // earnedTotal (real running all-time accrual, kept by
                                    // SavingsService.accrueInterest) had no client anywhere
                                    // -- only the this-month figure was ever shown, so a
                                    // saver could never see what the jar had earned overall.
                                    if (jar.earnedTotal > 0.0) {
                                        stringResource(
                                            R.string.home_interest_jar_rate_subtitle_total,
                                            "%.1f".format(jar.rate),
                                            "%,.0f".format(jar.earnedTotal),
                                        )
                                    } else {
                                        stringResource(R.string.home_interest_jar_rate_subtitle, "%.1f".format(jar.rate))
                                    },
                                    "%,.0f RWF".format(jar.earnedThisMonth),
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
                            // Real fix (2026-08-23, same session as the backend guard
                            // against depositing into an already-completed goal): this row
                            // had no visual distinction for a completed goal at all --
                            // tapping opened the same deposit flow as any active goal,
                            // matching what used to be a real silent-money-loss bug on the
                            // backend. Web's own equivalent card already appends this same
                            // "· Completed 🎉" marker (BankDashboard.tsx).
                            val completed = goal.status == "completed"
                            add(
                                ShellRow(
                                    goal.name,
                                    (
                                        // The backend has carried a real targetDate on every
                                        // savings goal all along, but this row only ever showed
                                        // progress -- a goal without its deadline is just a
                                        // balance. Falls back to the plain pattern when the
                                        // goal genuinely has no date set.
                                        goal.targetDate?.takeIf { it.isNotBlank() }?.let { date ->
                                            savingsProgressByDatePattern.format(
                                                "%,.0f".format(goal.currentAmount),
                                                "%,.0f".format(goal.targetAmount),
                                                date.take(10),
                                            )
                                        } ?: savingsProgressPattern.format("%,.0f".format(goal.currentAmount), "%,.0f".format(goal.targetAmount))
                                    ) + if (completed) " · Completed 🎉" else "",
                                    "$progressPercent%",
                                    Icons.Outlined.Savings,
                                    AccentIndigo,
                                    onClick = { onDepositToGoal(goal.id, goal.name) },
                                )
                            )
                        }
                        // Always offered, including (especially) when the list is empty:
                        // Android had no create path at all until 2026-08-14, so this
                        // section could only ever be empty on this platform.
                        add(
                            ShellRow(
                                stringResource(R.string.savings_new_goal_title),
                                stringResource(R.string.savings_new_goal_subtitle),
                                "+",
                                Icons.Outlined.Savings,
                                AccentTeal,
                                onClick = { showNewGoalDialog = true },
                            )
                        )
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
                        // Real inline rates (2026-08-18, direct 12-image Toss Bank
                        // comparison follow-up, Section 65's own named-not-yet-fixed
                        // gap: "real inline interest-rate display on every row").
                        // These three used to show zero real number at all -- a bare
                        // descriptive string plus a generic ">" chevron -- unlike the
                        // interest jar row above, which already surfaces its real rate.
                        // Rates mirrored from each product's own real backend constant
                        // (display-only copies, same convention grow31BonusRateForStreak
                        // and UpfrontDepositScreen's own ANNUAL_RATE already establish):
                        // WeeklySavingsService.BASE_RATE/BONUS_RATE (5.0/3.0),
                        // Grow31SavingsService's real streak-bonus tier table (max 10.0
                        // at a 31-day streak, mirrored locally as
                        // grow31BonusRateForStreak(31) above), UpfrontDepositScreen's own
                        // ANNUAL_RATE (2.80). Distinct icons too, closing this same
                        // section's other named gap ("richer/varied per-product
                        // iconography") -- all three used to share the identical Savings
                        // icon as the interest jar and every savings goal row above.
                        add(ShellRow("26-week savings", "$BANK_HUB_WEEKLY_SAVINGS_BASE_RATE% base rate, escalates weekly", ">", Icons.Outlined.CalendarMonth, AccentIndigo, onClick = onOpenWeeklySavings))
                        add(ShellRow("31-day savings", "Daily streak, up to $BANK_HUB_GROW31_MAX_BONUS_RATE% bonus rate", ">", Icons.Outlined.Bolt, AccentOrange, onClick = onOpenGrow31Savings))
                        add(ShellRow("12-month deposit", "$BANK_HUB_UPFRONT_DEPOSIT_ANNUAL_RATE%/yr interest paid upfront, principal locked", ">", Icons.Outlined.Lock, AccentPurple, onClick = onOpenUpfrontDeposit))
                        add(ShellRow(stringResource(R.string.home_coop_rail_ikimina_title), stringResource(R.string.home_coop_rail_ikimina_subtitle), ">", Icons.Outlined.Groups, AccentTeal, onClick = onOpenIkimina))
                        add(ShellRow(stringResource(R.string.home_coop_rail_sacco_title), stringResource(R.string.home_coop_rail_sacco_subtitle), ">", Icons.Outlined.AccountBalance, AccentPurple, onClick = onOpenSacco))
                        add(ShellRow("Investments", "RSE stocks, bonds & fixed income, IPOs", ">", Icons.Outlined.TrendingUp, AccentTeal, onClick = onOpenInvest))
                    }
                )
            }
            item {
                ShellSection(
                    title = stringResource(R.string.bank_borrow),
                    rows = listOf(
                        // Real icon differentiation (2026-08-18, same Section 65
                        // follow-up as above): all three of these used to share the
                        // identical AccountBalanceWallet icon -- Harvest advance and
                        // VUP now get their own distinct, semantically-apt icon from
                        // the same already-depended-upon material-icons-extended
                        // library ("Get a loan" keeps AccountBalanceWallet as the
                        // genuinely generic personal/SME loan product).
                        ShellRow("Get a loan", "Personal, salary-backed, SME working capital", ">", Icons.Outlined.AccountBalanceWallet, AccentIndigo, onClick = onOpenLoans),
                        ShellRow(stringResource(R.string.home_coop_rail_harvest_title), stringResource(R.string.home_coop_rail_harvest_subtitle), ">", Icons.Outlined.Agriculture, AccentTeal, onClick = onOpenHarvestAdvance),
                        ShellRow("VUP Financial Services", "Means-tested government microloan for farming, livestock, business", ">", IdsIcons.ShieldCheck, AccentIndigo, onClick = onOpenVupLoan),
                        ShellRow("Student loan", "BRD higher-education loan -- 11% undergraduate, 12% postgraduate", ">", Icons.Outlined.School, AccentPurple, onClick = onOpenStudentLoan),
                        ShellRow(stringResource(R.string.home_coop_rail_moto_title), "Save a 30% down payment, then convert to a loan for your own bike", ">", Icons.Outlined.DirectionsBike, AccentTeal, onClick = onOpenMotoOwnership),
                    )
                )
            }
            // Real architectural fix (2026-08-13, direct user directive): "itunda bank
            // is a complete product... with all features" -- credit score and
            // spending insight used to render on Home instead, real Bank-product
            // content stranded on a tab that's meant to be a generic access point.
            // Moved here (state fetched near the top of this composable, alongside
            // depositProtection's own identical pattern -- see above).
            item {
                ShellSection(
                    title = stringResource(R.string.bank_insights),
                    rows = buildList {
                        if ((spendingInsight?.totalSpent?.toDouble() ?: 0.0) > 0.0) {
                            add(
                                ShellRow(
                                    "%,.0f RWF".format(spendingInsight?.totalSpent?.toDouble() ?: 0.0),
                                    if (spendingTopCategory != null) stringResource(R.string.home_spent_period_category, spendingTopCategory.name) else stringResource(R.string.home_spent_period),
                                    ">",
                                    Icons.Outlined.PieChart,
                                    AccentPurple,
                                    onClick = onOpenSpendingInsight,
                                ),
                            )
                        }
                        add(
                            ShellRow(
                                stringResource(R.string.home_credit_score_title),
                                creditScore?.let { stringResource(R.string.home_credit_score_value, it.score) } ?: stringResource(R.string.home_credit_score_subtitle),
                                stringResource(R.string.home_credit_score_action),
                                Icons.Outlined.TrendingUp,
                                AccentPurple,
                                onClick = onOpenCreditScore,
                            ),
                        )
                    },
                )
            }
            // Real licensed-bank/deposit-insurance disclosure (2026-08-11) -- see
            // docs/TOSS_PARITY_MATRIX.md's own confirmation of "zero real banking-
            // license implementation anywhere" and TOSS_FEATURE_SPECIFICATION.md's
            // Pillar 3 listing "itunda Bank... RBDB licensed in Rwanda" as roadmap-
            // only, never built. This exact screen has carried the "itunda Bank" name
            // on every platform since the same day AccountSwitcherSheet's own doc
            // comment (above) reasoned that label risks a real regulatory overclaim
            // for the account row -- with no disclosure anywhere clarifying itunda's
            // actual (unlicensed) status.
            //
            // Real Deposit Protection Fund card (2026-08-11) -- see
            // DepositProtectionFund.kt's own doc comment: rather than just disclosing
            // an absence, this shows the real, working, ledger-backed reserve itunda
            // maintains as its own internal simulation of what real deposit protection
            // could look like -- same "real mechanics, honestly labeled as itunda's
            // own scheme" discipline this codebase already applies to VUP/RSE/SACCO.
            depositProtection?.let { dp ->
                item {
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                        Text(stringResource(R.string.bank_deposit_protection_title), color = Ids.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.bank_deposit_protection_covered), color = Ids.colors.textSecondary, fontSize = 13.sp)
                            Text("%,.0f RWF".format(dp.yourCoveredBalance), color = Ids.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(stringResource(R.string.bank_deposit_protection_cap, "%,.0f".format(dp.coverageCapPerUser)), color = Ids.colors.textTertiary, fontSize = 11.sp)
                        Text(stringResource(R.string.bank_deposit_protection_reserve, "%,.0f".format(dp.fundReserveBalance)), color = Ids.colors.textTertiary, fontSize = 11.sp)
                    }
                }
            }
            item {
                Text(
                    stringResource(R.string.bank_status_disclosure),
                    color = Ids.colors.textTertiary,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                )
            }
        }
        if (showRoundUpDialog) {
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
        if (showNewGoalDialog) {
            NewSavingsGoalDialog(
                onDismiss = { showNewGoalDialog = false },
                onCreate = { name, target, monthly, date ->
                    viewModel.createSavingsGoal(name, target, monthly, date)
                },
                onCreated = { showNewGoalDialog = false },
            )
        }
    }
}

// Real savings-goal creation dialog (2026-08-14). targetDate is a plain YYYY-MM-DD
// text field rather than a picker: the backend takes it as a nullable String and
// bank-mfe passes it the same way, so a picker would be inventing a stricter contract
// than the real one -- and the field is genuinely optional.
@Composable
private fun NewSavingsGoalDialog(
    onDismiss: () -> Unit,
    onCreate: suspend (name: String, targetAmountRwf: Long, monthlyRwf: Long?, targetDate: String?) -> MoneyActionResult,
    onCreated: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var targetAmount by remember { mutableStateOf("") }
    var monthly by remember { mutableStateOf("") }
    var targetDate by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val parsedTarget = targetAmount.filter { it.isDigit() }.toLongOrNull()

    androidx.compose.material3.AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = Ids.colors.surface,
        title = { Text(stringResource(R.string.savings_new_goal_title), color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                IdsTextField(value = name, onValueChange = { name = it }, label = stringResource(R.string.savings_new_goal_name_label), modifier = Modifier.fillMaxWidth())
                IdsTextField(
                    value = targetAmount,
                    onValueChange = { targetAmount = it.filter { c -> c.isDigit() } },
                    label = stringResource(R.string.savings_new_goal_target_label),
                    modifier = Modifier.fillMaxWidth(),
                )
                IdsTextField(
                    value = monthly,
                    onValueChange = { monthly = it.filter { c -> c.isDigit() } },
                    label = stringResource(R.string.savings_new_goal_monthly_label),
                    modifier = Modifier.fillMaxWidth(),
                )
                IdsTextField(value = targetDate, onValueChange = { targetDate = it }, label = stringResource(R.string.savings_new_goal_date_label), modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, color = Ids.colors.danger, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                enabled = !busy && name.isNotBlank() && parsedTarget != null && parsedTarget > 0,
                onClick = {
                    busy = true
                    error = null
                    coroutineScope.launch {
                        when (
                            val result = onCreate(
                                name.trim(),
                                parsedTarget ?: 0L,
                                monthly.toLongOrNull(),
                                targetDate.trim().ifBlank { null },
                            )
                        ) {
                            is MoneyActionResult.Success -> {
                                busy = false
                                onCreated()
                                rw.itunda.core.designsystem.components.IdsToast.show(coroutineScope, "Savings goal created.")
                            }
                            is MoneyActionResult.Failure -> { busy = false; error = result.message }
                            else -> busy = false
                        }
                    }
                },
            ) { Text(if (busy) "…" else stringResource(R.string.savings_new_goal_create), color = Ids.colors.brand, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss, enabled = !busy) {
                Text(stringResource(R.string.scam_report_cancel), color = Ids.colors.textSecondary)
            }
        },
    )
}

// Real personalized recommendation card -- see HomeTab's own doc comment on
// heroDiscoverItem for the full account of what real Toss reference screenshot this
// was compared against and what it promotes. Real fetch is scoped locally, not
// through MainViewModel.profile (only ever loaded today by SettingsScreen's own
// LaunchedEffect), same "each screen fetches its own minimal real data" precedent
// HoodTab's neighborhoodName fetch already established -- avoids Home also firing
// loadSettingsData()'s heavier notifications/devices calls just for a first name.
// Real restyle (2026-08-13, direct user comparison against the real Toss lottery
// banner, "itunda intelligence banner should [look] like this and if nothing new to
// recommend or suggestion it should disappear"): the "disappear when empty" behavior
// already existed (this whole composable is only ever called from inside HomeTab's own
// `if (heroDiscoverItem != null)` gate) -- what didn't match was the visual weight and
// the CTA's real interactivity. Now a real full-bleed accentColor-tinted card (using
// the item's own real per-item color more prominently, not inventing a new one),
// a real session-local dismiss control (matching the reference's own "X" -- generic
// and safe, not a fabricated destination), and a real full-width CTA button now that
// onOpenAction resolves to a real per-category destination (see the call site's own
// doc comment) instead of the dead-tap risk the prior "deliberately Text, not
// IdsButton" comment was written to avoid.
@Composable
private fun PersonalRecommendationCard(item: rw.itunda.core.network.DiscoverItem, onOpenAction: () -> Unit) {
    var firstName by remember { mutableStateOf<String?>(null) }
    var dismissed by remember(item.id) { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        try {
            val profile = rw.itunda.core.network.NetworkClient.authApi.getProfile()
            if (profile.success) firstName = profile.user.firstName
        } catch (_: Exception) {
            // Best-effort -- the card still works with a generic CTA if this fails.
        }
    }
    val accentColor = try {
        Color(android.graphics.Color.parseColor(item.color))
    } catch (_: IllegalArgumentException) {
        AccentIndigo
    }
    if (dismissed) return
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Ids.layout.cardCornerRadius))
            .background(accentColor.copy(alpha = 0.10f)),
    ) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(accentColor.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.CardGiftcard, contentDescription = null, modifier = Modifier.size(24.dp), tint = accentColor)
                }
                if (item.isNew) {
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        stringResource(R.string.home_new_badge), color = accentColor, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(accentColor.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
            Text(item.title, color = Ids.colors.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 28.sp)
            Text(item.subtitle, color = Ids.colors.textSecondary, fontSize = 14.sp)
            IdsButton(
                firstName?.let { stringResource(R.string.home_recommendation_cta_named, it, item.description) } ?: item.description,
                onClick = onOpenAction,
                variant = IdsButtonVariant.Filled,
                size = IdsButtonSize.Large,
            )
        }
        IdsIconButton(
            IdsIcons.Close,
            contentDescription = stringResource(R.string.home_dismiss_recommendation),
            onClick = { dismissed = true },
            modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).size(32.dp),
        )
    }
}

@Composable
private fun DiscoverSection(items: List<rw.itunda.core.network.DiscoverItem>) {
    Column {
        Text(stringResource(R.string.home_discover), color = Ids.colors.textPrimary, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
        items.forEach { discoverItem ->
            val accentColor = try {
                Color(android.graphics.Color.parseColor(discoverItem.color))
            } catch (_: IllegalArgumentException) {
                AccentIndigo
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
private fun HomeTopBar(
    onOpenPay: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    onOpenOverview: () -> Unit = {},
    onOpenAccountDetail: () -> Unit = {},
    // Real Toss Bank reference (2 images, 2026-08-13, direct user comparison, "app bar
    // should be 100% same as this"): the real top bar has no search field at all --
    // just the account switcher on the left and a real "Pay" shortcut + a
    // notification bell with a real unread-count dot on the right. itunda's own
    // search Box here had never actually been wired to anything (no onClick, no
    // destination -- a real dead UI element, not just a style mismatch), so removing
    // it fixes two real problems at once. unreadCount is the same real
    // MainViewModel.unreadNotificationCount already powering NotificationListScreen's
    // own badge, just never surfaced here.
    unreadCount: Int = 0,
) {
    // Real account switcher (2026-08-11) -- direct comparison against real Toss Bank
    // reference screenshots (user-provided): Toss's own top bar leads with "토스뱅크 >",
    // a tappable account-identity element, not a bare wordmark -- this bar had nothing
    // in that position at all. Real backend already exists for this
    // (rw.itunda.overview.LinkedAccountController, GET/POST /api/v1/accounts/linked,
    // consumed today only inside AutoTopUpScreen/OverviewScreen, never surfaced as a
    // top-level switcher). Deliberately does NOT let a linked account's demoBalance
    // replace Home's own real account balance -- LinkedAccount.kt's own doc comment
    // draws a hard, deliberate line ("Never counted in real netWorth") against
    // blending real and demo money, so this is a real browse/manage sheet, not a
    // literal "switch which balance Home shows" control.
    var showAccountSwitcher by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.pressScaleClickable { showAccountSwitcher = true },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("itunda", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Icon(
                Icons.Outlined.ExpandMore, contentDescription = stringResource(R.string.home_account_switcher),
                modifier = Modifier.size(20.dp), tint = Ids.colors.textPrimary,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        // Real "Pay" shortcut, matching the reference's own bracket-accented pill --
        // opens the same real "Pay" screen (scan-or-pay-by-code) the My tab's Pay row
        // already reaches (found real, previously reachable only via QR icon here,
        // 2026-07-22 audit).
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .border(1.dp, Ids.colors.divider, RoundedCornerShape(999.dp))
                .pressScaleClickable(onClick = onOpenPay)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.home_pay_shortcut), color = Ids.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(modifier = Modifier.width(4.dp))
        // Both icons were real no-op taps (found 2026-07-22 audit) despite their own
        // real destinations already existing elsewhere in this file. Notifications
        // opened the whole Settings screen until 2026-08-12 -- corrected after a
        // direct user clarification against real Toss screenshots: the bell opens
        // the real notifications FEED (NotificationListScreen, showNotificationsFeed
        // above), a different real screen from Settings' own "Notifications" row
        // (notification SEND preferences, see SettingsScreen.kt's
        // NotificationSettingsScreen). The red dot is real (unreadCount > 0), not
        // decorative -- matches the reference's own real unread indicator.
        Box {
            IdsIconButton(IdsIcons.Bell, contentDescription = stringResource(R.string.home_notifications), onClick = onOpenNotifications)
            if (unreadCount > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 6.dp, end = 6.dp)
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(Ids.colors.danger),
                )
            }
        }
    }
    if (showAccountSwitcher) {
        AccountSwitcherSheet(
            onDismiss = { showAccountSwitcher = false },
            onOpenOverview = { showAccountSwitcher = false; onOpenOverview() },
            onOpenAccount = { showAccountSwitcher = false; onOpenAccountDetail() },
        )
    }
}

@Composable
private fun AccountSwitcherSheet(onDismiss: () -> Unit, onOpenOverview: () -> Unit, onOpenAccount: () -> Unit = {}) {
    var linkedAccounts by remember { mutableStateOf<List<rw.itunda.core.network.LinkedAccountEntityDto>?>(null) }
    LaunchedEffect(Unit) {
        try {
            linkedAccounts = rw.itunda.core.network.NetworkClient.apiService.getLinkedAccounts().linkedAccounts
                .filter { it.status == "LINKED" }
        } catch (_: Exception) {
            linkedAccounts = emptyList()
        }
    }
    BackHandler(onBack = onDismiss)
    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.32f)).clickable(onClick = onDismiss)) {
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 64.dp, start = Ids.layout.screenHorizontal, end = Ids.layout.screenHorizontal)
                .fillMaxWidth()
                .clip(RoundedCornerShape(Ids.layout.cardCornerRadius))
                .background(Ids.colors.surface)
                .clickable(enabled = false) {}
                .padding(vertical = 8.dp),
        ) {
            Text(
                stringResource(R.string.home_your_accounts),
                color = Ids.colors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
            )
            // Real destination (2026-08-13, direct user screenshots of tapping their
            // own real Toss Bank account row): this row had never been clickable --
            // now opens AccountDetailScreen, the real balance+interest+ledger view.
            Row(
                modifier = Modifier.fillMaxWidth().pressScaleClickable(onClick = onOpenAccount).padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(22.dp), tint = Ids.colors.brand)
                Spacer(modifier = Modifier.width(12.dp))
                // Renamed from bare "itunda account" (2026-08-23, direct user
                // correction, see docs/UI_UX_GUIDELINES.md §12): collided with the
                // real, universal "[Brand] Account = identity layer" convention
                // (Google/Samsung/Apple/Kakao Account) -- the exact same shape of
                // confusion as "Kakao Account" being ambiguous between identity,
                // KakaoTalk, and KakaoBank. An earlier pass (2026-08-11) had
                // deliberately avoided "Itunda Bank" here specifically to not
                // overclaim a real banking license this project doesn't have --
                // direct user follow-up superseded that concern for this project's
                // real context (a research/demo super-app modeling real fintech UX
                // patterns, not a product seeking actual regulatory approval), and
                // "itunda Bank" is already the established name for this exact
                // product everywhere else in the app (BankHubScreen, the Bank tab,
                // etc.) -- this row was the one remaining holdout still saying bare
                // "itunda account."
                Text("itunda Bank account", color = Ids.colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Icon(Icons.Outlined.CheckCircle, contentDescription = stringResource(R.string.home_current_account), modifier = Modifier.size(18.dp), tint = Ids.colors.brand)
            }
            when {
                linkedAccounts == null -> {}
                linkedAccounts!!.isEmpty() -> {}
                else -> linkedAccounts!!.forEach { account ->
                    Divider(color = Ids.colors.divider, modifier = Modifier.padding(horizontal = 18.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.AccountBalance, contentDescription = null, modifier = Modifier.size(22.dp), tint = Ids.colors.textSecondary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${account.provider} · ${account.externalAccountNumberMasked}", color = Ids.colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            // Real, explicit "Demo" label -- see LinkedAccount.kt's own doc
                            // comment: no live Open Banking access exists, so this balance is
                            // honestly labeled rather than presented as if it were real.
                            Text(
                                account.demoBalance?.let { "${account.demoBalanceCurrency ?: "RWF"} %,.0f (Demo)".format(it) } ?: stringResource(R.string.home_demo_balance_unavailable),
                                color = Ids.colors.textSecondary, fontSize = 12.sp,
                            )
                        }
                    }
                }
            }
            Divider(color = Ids.colors.divider, modifier = Modifier.padding(horizontal = 18.dp))
            Text(
                stringResource(R.string.home_link_another_account),
                color = Ids.colors.brand, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .fillMaxWidth()
                    .pressScaleClickable(onClick = onOpenOverview)
                    .padding(horizontal = 18.dp, vertical = 12.dp),
            )
        }
    }
}

// Real Toss Bank account-detail screen (2026-08-13, 3 direct user screenshots of
// their own real Toss Bank account: "when you click on bank accounts that what
// you should see"). Reached by tapping the "itunda account" row in
// AccountSwitcherSheet above, which had never actually been clickable before this.
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
                        Text(
                            "itunda ${primaryAccount!!.accountNumber.chunked(4).joinToString("-")}",
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

@Composable
private fun TransactionDetailRow(label: String, value: String) {
    // Real fix, applied proactively (this exact "two unweighted Texts in a Row can
    // overflow into each other" bug was just caught live in AccountLedgerRow above
    // this same session -- see that fix's own doc comment): the value gets
    // weight(1f) so a long description wraps within its own bounded space instead
    // of running past the label.
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = Ids.colors.textSecondary, fontSize = 14.sp)
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            value, color = Ids.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

private data class ShellRow(
    val title: String,
    val subtitle: String,
    val action: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val iconColor: Color = AccentIndigo,
    // Added 2026-07-12 for the real Savings section's rows (deposit/claim) --
    // default null preserves every existing purely-promotional ShellRow call site
    // unchanged.
    val onClick: (() -> Unit)? = null,
)

// Flattened 2026-08-22 (direct user directive: "out bank home screen should
// look 100% like toss bank screen") -- real Toss Bank's own product catalog
// (Save & Grow / Borrow / Insights) renders as one continuous flat list, not a
// grid of separate rounded cards; matches iOS's AccountLedgerDetailRow and
// AccountDetailScreen.tsx's flat row pattern built the same session. 38dp
// circular icon badge (was 42dp square-ish RoundedCornerShape(16.dp)) to match
// those exactly.
// Real fix (2026-08-24, direct user directive, real Toss product-list
// screenshots): dropped the Divider() this used to render between rows -- real
// Toss lists separate rows with whitespace alone, not a hairline rule per row.
// FlatSection below already got this right (see its own doc comment); this was
// the one real remaining list style still adding one.
@Composable
private fun ShellSection(title: String, rows: List<ShellRow>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (title.isNotEmpty()) {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ids.colors.textPrimary)
            Spacer(modifier = Modifier.height(4.dp))
        }
        rows.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (row.onClick != null) Modifier.pressScaleClickable(onClick = row.onClick) else Modifier)
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
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
                    Icon(row.icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(row.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Ids.colors.textPrimary)
                    if (row.subtitle.isNotEmpty()) {
                        Text(row.subtitle, fontSize = 14.sp, color = Ids.colors.textSecondary)
                    }
                }
                if (row.action == ">") {
                    Icon(IdsIcons.ChevronRight, contentDescription = null, tint = Ids.colors.textTertiary)
                } else if (row.action.isNotBlank()) {
                    // Real fix (2026-08-13, direct user report: "entire app is
                    // still messy"): this button's onClick was hardcoded to a
                    // no-op regardless of row.onClick -- the surrounding Row above
                    // is already clickable via row.onClick when set, but a nested
                    // clickable element (this button) intercepts the tap before it
                    // reaches the parent, so tapping directly on the visually
                    // obvious CTA (e.g. credit score's "View") silently did
                    // nothing while tapping elsewhere in the same row worked.
                    IdsButton(row.action, onClick = row.onClick ?: {}, variant = IdsButtonVariant.Tinted, size = IdsButtonSize.Small)
                }
            }
        }
    }
}

// Real fix (2026-08-11, itunda Pay research pass -- user-provided KakaoPay/Toss
// Pay screenshots): this whole tab was a decorative mockup -- MapPlaceholder's
// hardcoded "5 nearby stores" text, PayFeatureCard's hardcoded "30% rewards" with
// onClick = {} no-op buttons, and a ShellSection row with a hardcoded "RWF 31,031"
// that had no onClick at all. Zero calls to any real Pay backend endpoint. The
// REAL payment-collection UI (pay-by-code, pay-by-static-QR, Face Pay -- all
// genuinely wired to rw.itunda.merchant.MerchantService.collect) already existed,
// just misfiled inside the unrelated Shop feature where the Pay tab/Home QR icon
// could never reach it. This is that real UI, moved to where "Pay" actually means
// pay -- see PayAMerchantSection's own doc comment in ShopScreen.kt for why it's
// exposed from :features:shop:impl rather than duplicated.
private enum class PayTabMode { MY_CODE, PAY_MERCHANT }

@Composable
private fun PayTab(
    viewModel: MainViewModel,
    onSend: () -> Unit,
    onCashOutAtAgent: () -> Unit,
    onSwitchTab: (ItundaTab) -> Unit,
    onOpenSupport: () -> Unit,
) {
    // Real Toss Pay home reference (4 screenshots, 2026-08-22, direct user follow-up:
    // "our pay home screen should also look 100% like toss pay home screen") -- real
    // FacePay enrollment (getFacePayStatus) and real RewardsService task list
    // (getRewardTasks/claimRewardTask, ApiService.kt), never surfaced on this tab
    // before. See PayHomeExtras.kt's own doc comment for the honest-scoping detail
    // (coupons/"how to pay online" omitted, no real backend for either).
    var facePayEnrolled by remember { mutableStateOf(false) }
    var facePayBusy by remember { mutableStateOf(false) }
    var rewardTasks by remember { mutableStateOf<List<rw.itunda.core.network.RewardTaskDto>>(emptyList()) }
    var rewardsTotal by remember { mutableStateOf(0.0) }
    var claimingRewardId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        try {
            facePayEnrolled = rw.itunda.core.network.NetworkClient.apiService.getFacePayStatus().enrolled
        } catch (e: Exception) {
            // Non-critical -- the row just keeps showing the last-known state.
        }
        try {
            val result = rw.itunda.core.network.NetworkClient.apiService.getRewardTasks()
            rewardTasks = result.tasks
            rewardsTotal = result.rewardsTotal
        } catch (e: Exception) {
            // Non-critical -- the preview section just stays hidden.
        }
    }
    // Real "345 stores nearby" banner -- see PayHomeExtras.kt's rememberNearbyMerchants.
    val nearbyMerchants = rememberNearbyMerchants()
    val payTabTransactions by viewModel.transactions.collectAsState()
    // Real fix (2026-08-11, same session -- direct user pushback: "why is itunda pay
    // have no simplicity at all pay by code?"): PAY_MERCHANT (manual merchant-ID/
    // amount entry) was the ONLY way to pay -- real friction Toss's own "Postel's
    // Law -- minimize input requests" research argues against. Real KakaoPay/Toss
    // Pay's actual primary in-store flow has the CUSTOMER's own scannable code
    // already on screen the moment Pay opens, no typing at all -- MY_CODE is that,
    // now the default. PAY_MERCHANT stays for a merchant with a printed/posted
    // static QR (a market stall), the one real case where typing a merchant ID is
    // still the honest baseline until real camera scanning exists on that side too.
    var mode by remember { mutableStateOf(PayTabMode.MY_CODE) }
    // Real swipeable funding-source cards (2026-08-11) -- the user's own KakaoPay
    // reference screenshot's bottom card carousel. The real, buildable slice of that:
    // itunda's own real accounts (PAY + any opened foreign-currency ones,
    // ForeignCurrencyAccountService) as distinct swipeable cards, where the settled
    // card is the one CustomerPaymentCode.accountId actually funds the QR from -- see
    // MerchantService.generateCustomerPaymentCode's own doc comment. No fabricated
    // membership/deal cards: itunda has no real backend for those as payment sources.
    // Defaults to PAY (2026-08-21 fix), not MAIN -- matches MerchantService.collect/
    // chargeByCustomerCode's own real default funding source post-separation.
    var accounts by remember { mutableStateOf<List<rw.itunda.core.network.Account>>(emptyList()) }
    var selectedAccountId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        try {
            val pay = rw.itunda.core.network.NetworkClient.apiService.getAccounts().accounts.filter { it.type == "PAY" }
            val foreign = rw.itunda.core.network.NetworkClient.apiService.getForeignAccounts().accounts
            accounts = pay + foreign
        } catch (e: Exception) {
            // Real, non-critical -- MyPaymentCodeCard falls back to the backend's own
            // PAY default when accounts never load.
        }
    }
    LaunchedEffect(accounts) {
        if (selectedAccountId == null) selectedAccountId = accounts.firstOrNull { it.type == "PAY" }?.id
    }
    // Real "Toss Pay Money" detail/statement screen (user screenshots, 2026-08-21) --
    // see PayMoneyDetailScreen's own doc comment.
    var openAccountDetail by remember { mutableStateOf<rw.itunda.core.network.Account?>(null) }
    if (openAccountDetail != null) {
        PayMoneyDetailScreen(
            account = openAccountDetail!!,
            onBack = { openAccountDetail = null },
            onSend = { openAccountDetail = null; onSend() },
            // Real gap, honestly scoped out for now (same as bank-mfe's identical
            // choice): itunda has no self-service "pull an amount from my linked
            // account right now" flow, only AutoTopUpCard's threshold-based auto
            // top-up. Closing back to Pay rather than routing somewhere unrelated.
            onAddMoney = { openAccountDetail = null },
        )
        return
    }
    val coroutineScope = rememberCoroutineScope()
    val handleFacePayToggle: () -> Unit = {
        coroutineScope.launch {
            facePayBusy = true
            try {
                if (facePayEnrolled) rw.itunda.core.network.NetworkClient.apiService.revokeFacePay() else rw.itunda.core.network.NetworkClient.apiService.enrollFacePay()
                facePayEnrolled = !facePayEnrolled
            } catch (e: Exception) {
                // Non-critical -- the row just keeps showing the last-known state.
            } finally {
                facePayBusy = false
            }
        }
    }
    val handleClaimReward: (String) -> Unit = { taskId ->
        coroutineScope.launch {
            claimingRewardId = taskId
            try {
                rw.itunda.core.network.NetworkClient.apiService.claimRewardTask(
                    java.util.UUID.randomUUID().toString(),
                    rw.itunda.core.network.ClaimRewardTaskRequest(taskId),
                )
                val result = rw.itunda.core.network.NetworkClient.apiService.getRewardTasks()
                rewardTasks = result.tasks
                rewardsTotal = result.rewardsTotal
            } catch (e: Exception) {
                // Non-critical -- the row just stays claimable, retryable on next tap.
            } finally {
                claimingRewardId = null
            }
        }
    }
    // Real fix (2026-08-25, direct user report + live-measured, same session as
    // ItundaBottomBar's own identical padding-stacking fix): this LazyColumn's own
    // `vertical = screenVertical` bottom padding was stacking with the Scaffold's
    // already-correct bottomBar inset (paddingValues, applied once by the shared Box
    // in ItundaAppScreen's own Scaffold) -- a real, measured 16dp of dead space
    // between the last row and the nav bar that nothing needed. Top kept (real
    // breathing room below the status bar); bottom now comes from Scaffold alone.
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(start = Ids.layout.screenHorizontal, end = Ids.layout.screenHorizontal, top = Ids.layout.screenVertical),
        verticalArrangement = Arrangement.spacedBy(Ids.layout.cardGap)
    ) {
        // Real Toss Pay home reference (4 screenshots, 2026-08-22): a bold "Pay"
        // wordmark plus a settings icon, not PlainTopBar's generic decorative "..."
        // menu -- itunda has no dedicated Pay-settings screen yet, so this honestly
        // routes to the You tab (the closest real settings destination) rather than
        // fabricating one.
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Pay", color = Ids.colors.textPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                IdsIconButton(icon = Icons.Outlined.Settings, contentDescription = "Pay settings", onClick = { onSwitchTab(ItundaTab.You) })
            }
        }
        item { NearbyMerchantsBanner(merchants = nearbyMerchants) }
        item {
            FacePayStatusRow(
                enrolled = facePayEnrolled,
                busy = facePayBusy,
                cashbackRatePercent = nearbyMerchants.takeIf { it.isNotEmpty() }?.let { list -> list.sumOf { it.cashbackRate } / list.size * 100.0 },
                onToggle = handleFacePayToggle,
            )
        }
        // Real Toss Pay reference (user-provided screenshots, 2026-08-21): the real Pay
        // home screen has no headline balance card at all -- it's a nearby-merchant-
        // rewards surface leading straight into the payment-method picker (Facepay/QR
        // sheet: linked bank account or card, no separate "Pay Money" balance shown
        // anywhere). AccountHeroCard (real balance, account number, "See all"
        // transactions) was itunda's own invented pattern, not a real Toss Pay one --
        // removed. MyPaymentCodeCard below already carries both the real funding-
        // source picker and its own real balance/nearby-merchant-benefits row, so it's
        // the real equivalent of Toss Pay's own map+rewards home surface. Send/Cash
        // out kept as a compact quick-action row (no capability lost) since Pay is
        // still itunda's real complete send/receive product; the real interest-claim
        // prompt and recent-transactions preview stay owned by BankHubScreen, which
        // was always their true home per that screen's own doc comment.
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                IdsButton(stringResource(R.string.home_cash_out), onClick = onCashOutAtAgent, modifier = Modifier.weight(1f), variant = IdsButtonVariant.Tinted, size = IdsButtonSize.Medium, icon = IdsIcons.Add)
                IdsButton(stringResource(R.string.home_send), onClick = onSend, modifier = Modifier.weight(1f), variant = IdsButtonVariant.Filled, size = IdsButtonSize.Medium, icon = IdsIcons.Send)
            }
        }
        item {
            // Real KakaoPay reference (user's own screenshot): the segmented control is
            // a dark pill with a lighter-grey highlight behind the active label, not a
            // bright brand-color fill -- text stays white either way. Uses
            // Ids.colors.surface (not .chip) for the active highlight: .chip and
            // .surfaceSoft resolve to the literal same hex in both themes
            // (IdsSemanticColors.kt), so the highlight was rendering invisibly against
            // its own container until this fix -- .surface is the one token
            // guaranteed distinct from .surfaceSoft in both light and dark.
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(999.dp)).background(Ids.colors.surfaceSoft).padding(4.dp),
            ) {
                listOf(PayTabMode.MY_CODE to "My code", PayTabMode.PAY_MERCHANT to "Pay a merchant").forEach { (m, label) ->
                    val active = mode == m
                    Text(
                        label, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        color = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (active) Ids.colors.surface else androidx.compose.ui.graphics.Color.Transparent)
                            .pressScaleClickable { mode = m }
                            .padding(vertical = 10.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
        when (mode) {
            PayTabMode.MY_CODE -> {
                item {
                    MyPaymentCodeCard(
                        selectedAccount = accounts.find { it.id == selectedAccountId },
                        onOpenAccountDetail = { openAccountDetail = it },
                    )
                }
                if (accounts.size > 1) {
                    item {
                        AccountCardCarousel(
                            accounts = accounts,
                            selectedAccountId = selectedAccountId,
                            onSelect = { selectedAccountId = it },
                        )
                    }
                }
            }
            PayTabMode.PAY_MERCHANT -> item {
                rw.itunda.feature.shop.impl.PayAMerchantSection(
                    deviceStepUpHost = { visible, onDismiss, onVerified ->
                        DeviceStepUpHost(visible = visible, onDismiss = onDismiss, onVerified = onVerified)
                    },
                )
            }
        }
        item { RewardsSummaryRow(rewardsTotal = rewardsTotal, payBalance = accounts.find { it.type == "PAY" }?.balance) }
        item { RewardsPreviewSection(tasks = rewardTasks, claimingId = claimingRewardId, onClaim = handleClaimReward) }
        item { PaymentHistorySection(transactions = payTabTransactions) }
        item { GetHelpLinks(onOpenSupport = onOpenSupport) }
    }
}

// Real customer-presented payment code (2026-08-11) -- see backend's
// MerchantService.generateCustomerPaymentCode doc comment. Auto-refreshes shortly
// before its own real 2-minute expiry so a customer standing at a register never
// has it silently go stale mid-checkout.
//
// Rebuilt 2026-08-11 to closely match the user's own real KakaoPay screenshot
// (not an invented layout -- see feedback_dont_imagine_use_real_reference memory):
// one white card holding the masked pay button, the account balance, the funding
// account, and nearby benefits, in that order. The card is pinned to raw IdsColors
// light values (White/Grey100/Gray900 etc.) rather than the theme-adaptive
// Ids.colors -- the reference screenshot itself is shown against a dark system
// background yet the payment card stays white, the same real "barcode/QR needs to
// read against white under a POS scanner regardless of phone theme" reasoning
// IdsSemanticColors.kt's own light-palette comment already documents.
@Composable
private fun MyPaymentCodeCard(selectedAccount: rw.itunda.core.network.Account?, onOpenAccountDetail: (rw.itunda.core.network.Account) -> Unit) {
    var revealed by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf<String?>(null) }
    var expiresAtMillis by remember { mutableStateOf(0L) }
    var error by remember { mutableStateOf<String?>(null) }
    var secondsLeft by remember { mutableStateOf(0) }
    var linkedAccount by remember { mutableStateOf<rw.itunda.core.network.LinkedAccountEntityDto?>(null) }
    var nearbyAds by remember { mutableStateOf<List<rw.itunda.core.network.NearbyMerchantAdDto>>(emptyList()) }
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

    // Re-keyed on selectedAccount.id (not just `revealed`): swiping AccountCardCarousel
    // to a different real account while the code is already showing must regenerate it
    // against the newly-selected account, not silently keep charging the old one.
    LaunchedEffect(revealed, selectedAccount?.id) {
        if (!revealed) {
            // Real NFC bridge (2026-08-27) -- see TransitPresentmentStore.kt's own doc
            // comment. A hidden code must never still be broadcastable over NFC.
            rw.itunda.app.nfc.TransitPresentmentStore.clear()
            return@LaunchedEffect
        }
        while (true) {
            try {
                val res = rw.itunda.core.network.NetworkClient.apiService.generateCustomerPaymentCode(
                    rw.itunda.core.network.GenerateCustomerPaymentCodeRequest(accountId = selectedAccount?.id),
                )
                code = res.code
                expiresAtMillis = java.time.Instant.parse(res.expiresAt).toEpochMilli()
                error = null
                rw.itunda.app.nfc.TransitPresentmentStore.set(res.code, expiresAtMillis)
            } catch (e: Exception) {
                error = "Could not load your payment code."
            }
            val waitMs = (expiresAtMillis - System.currentTimeMillis() - 10_000L).coerceAtLeast(5_000L)
            kotlinx.coroutines.delay(waitMs)
        }
    }
    // A rider who navigates away from this screen entirely (composable disposed, not
    // just `revealed` toggled off) must also stop broadcasting -- same real intent as
    // the `!revealed` branch above.
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { rw.itunda.app.nfc.TransitPresentmentStore.clear() }
    }
    LaunchedEffect(revealed) {
        if (!revealed) return@LaunchedEffect
        while (true) {
            secondsLeft = ((expiresAtMillis - System.currentTimeMillis()) / 1000L).toInt().coerceAtLeast(0)
            kotlinx.coroutines.delay(1000)
        }
    }
    // Real linked funding account (rw.itunda.overview.LinkedAccountService) -- same
    // data AutoTopUpScreen/OverviewScreen already fetch, read-only display here.
    LaunchedEffect(Unit) {
        try {
            linkedAccount = rw.itunda.core.network.NetworkClient.apiService.getLinkedAccounts().linkedAccounts.firstOrNull { it.status == "LINKED" }
        } catch (e: Exception) {
            // Real, non-critical.
        }
    }
    // Real 당근(Karrot)-style radius-targeted nearby merchant ads (MerchantAdService.nearby)
    // -- same rememberRealLocationRequester + endpoint ShopScreen.kt's own nearby-ads
    // rail already established. Silent when location is denied or nothing is nearby.
    val requestLocation = rw.itunda.core.designsystem.components.rememberRealLocationRequester(
        onLocating = {},
        onSuccess = { lat, lng ->
            coroutineScope.launch {
                try {
                    nearbyAds = rw.itunda.core.network.NetworkClient.apiService.getNearbyMerchantAds(lat, lng).ads
                } catch (e: Exception) {
                    // Real, non-critical -- the row just won't render if this fails.
                }
            }
        },
        onError = {},
    )
    LaunchedEffect(Unit) { requestLocation() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(IdsColors.White)
            .padding(20.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(IdsColors.Grey100).padding(vertical = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (!revealed) {
                // Real fix (2026-08-13, direct user report: "this pay UI/UX it's so
                // bad" -- researched real KakaoPay's own reveal-gate: the code screen
                // requires security auth before showing the real barcode/QR, not an
                // unprotected tap). This pill used to be a small, ambiguous "Pay"
                // label floating alone in an empty gray box -- nothing communicated
                // that tapping it does anything, let alone that it's a real security
                // gate protecting a real payment code. A lock icon + explicit "Tap to
                // show your code" copy makes the gate and the action both legible.
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(
                        modifier = Modifier.size(56.dp).clip(CircleShape).background(IdsColors.White),
                        contentAlignment = Alignment.Center,
                    ) {
                        // Real fix (2026-08-24): was a raw Material Lock icon, itunda
                        // already has its own real LockGlyph (itundaface) for this
                        // exact security concept, matching web/iOS's identical fix.
                        rw.itunda.core.designsystem.itundaface.LockGlyph(size = 24.dp)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Your payment code is hidden", color = IdsColors.Gray900, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Protects you if someone else has your phone", color = IdsColors.Gray600, fontSize = 12.sp)
                    }
                    Text(
                        "Tap to show", color = IdsColors.White, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(IdsColors.Blue500)
                            .pressScaleClickable { revealed = true }
                            .padding(horizontal = 32.dp, vertical = 12.dp),
                    )
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val currentCode = code
                    when {
                        currentCode != null -> {
                            // Real correction (product-feel audit, item 242): this
                            // was QR-only -- KakaoPay's own real screenshots (App
                            // Store listing + 3 real screenshots of the user's own
                            // live app, fetched/sent this session) show the primary
                            // code is a real linear BARCODE (Code128) with a small
                            // QR secondary, not QR alone. Same real correction just
                            // made on bank-mfe/iOS -- see PayQrCodeUtil.kt's own
                            // generatePayBarcodeBitmap doc comment.
                            val barcode = remember(currentCode) { generatePayBarcodeBitmap(currentCode) }
                            val qr = remember(currentCode) { generatePayQrBitmap(currentCode) }
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                androidx.compose.foundation.Image(
                                    bitmap = barcode,
                                    contentDescription = "Your payment barcode",
                                    // Real fix: FillBounds (not the default Fit) so the
                                    // barcode always exactly fills whatever weighted
                                    // width this card actually has, no letterboxing --
                                    // safe specifically for a 1D barcode since a real
                                    // scanner only reads the bar-WIDTH sequence along
                                    // one scan line, not the image's aspect ratio, so a
                                    // horizontal-only stretch never breaks decodability.
                                    contentScale = androidx.compose.ui.layout.ContentScale.FillBounds,
                                    modifier = Modifier.weight(1f).height(60.dp).clip(RoundedCornerShape(6.dp)).background(IdsColors.White),
                                )
                                androidx.compose.foundation.Image(
                                    bitmap = qr,
                                    contentDescription = "Your payment QR code",
                                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(6.dp)).background(IdsColors.White),
                                )
                            }
                            Text(
                                if (secondsLeft > 0) "Refreshes in ${secondsLeft}s" else "Refreshing…",
                                color = IdsColors.Gray600, fontSize = 12.sp,
                            )
                        }
                        error != null -> Text(error!!, color = IdsColors.Red500, fontSize = 13.sp)
                        else -> androidx.compose.material3.CircularProgressIndicator(color = IdsColors.Blue500)
                    }
                }
            }
        }

        if (selectedAccount != null) {
            Spacer(Modifier.height(20.dp))
            // Real drill-in to the "Toss Pay Money" detail/statement screen (user
            // screenshots, 2026-08-21) -- see PayMoneyDetailScreen's own doc comment.
            Row(
                Modifier.fillMaxWidth().pressScaleClickable { onOpenAccountDetail(selectedAccount) },
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (selectedAccount.type == "PAY") "itunda Pay" else "itunda Pay (${selectedAccount.currency})",
                    color = IdsColors.Gray900, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${selectedAccount.currency} ${
                            if (selectedAccount.currency == "RWF") "%,.0f".format(selectedAccount.availableBalance)
                            else "%,.2f".format(selectedAccount.availableBalance)
                        }",
                        color = IdsColors.Gray900, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                    )
                    Icon(IdsIcons.ChevronRight, contentDescription = null, modifier = Modifier.size(16.dp), tint = IdsColors.Gray500)
                }
            }
            val account = linkedAccount
            if (account != null) {
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Funding account", color = IdsColors.Gray600, fontSize = 13.sp)
                    Text("${account.provider} ${account.externalAccountNumberMasked}", color = IdsColors.Gray700, fontSize = 13.sp)
                }
            }
        }

        if (nearbyAds.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            androidx.compose.material3.HorizontalDivider(color = IdsColors.Grey200, thickness = 1.dp)
            Spacer(Modifier.height(16.dp))
            Text("Nearby benefits", color = IdsColors.Gray900, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(12.dp))
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                items(nearbyAds, key = { it.ad.id }) { nearbyAd ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(64.dp),
                    ) {
                        Box(
                            modifier = Modifier.size(40.dp).clip(CircleShape).background(IdsColors.Blue100),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(nearbyAd.businessName.take(1).uppercase(), color = IdsColors.Blue600, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(nearbyAd.businessName, color = IdsColors.Gray800, fontSize = 11.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        Text("${(nearbyAd.distanceKm * 1000).toInt()}m", color = IdsColors.Gray600, fontSize = 10.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        // Real fix (2026-08-13, direct user report: "this pay UI/UX it's so bad"):
        // the real disclosure this line makes (itunda pays from its own ledger, not
        // a card network) is genuinely important and stays -- only the wording
        // changes, from an internal-doc-comment-style "--" aside to plain,
        // user-facing copy a real product would actually ship.
        Text(
            "Pays instantly from your real itunda balance.",
            color = IdsColors.Gray600, fontSize = 11.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// accountCardColor + AccountCardCarousel now live in AccountCardCarousel.kt
// (extracted 2026-08-26 to stay under this file's own file-size-lint baseline).

// Real Explore primary bottom tab (renamed 2026-08-10 from All -- see ItundaTab's own
// doc comment for the full history: separated from My at the user's own direct
// request in an earlier pass ("My and All screen should be separated like KakaoPay"),
// then brought back to the bottom nav directly once mini-apps and (planned) games
// meant this exhaustive service catalog needed to be one tap away, not nested two
// taps under My; My is now its own primary tab (ItundaTab.You) instead of a
// profile-icon-reachable screen from here. Content that doesn't
// belong in an exhaustive product catalog.
@Composable
private fun MenuScreen(
    onOpenShop: () -> Unit = {},
    onOpenEats: () -> Unit = {},
    onOpenMarketplace: () -> Unit = {},
    onOpenCommunity: () -> Unit = {},
    onOpenJobs: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenInvest: () -> Unit = {},
    onOpenBank: () -> Unit = {},
    onOpenMap: () -> Unit = {},
    onOpenOverview: () -> Unit = {},
    onOpenLoans: () -> Unit = {},
    onOpenSupport: () -> Unit = {},
    onOpenCreditScore: () -> Unit = {},
    onOpenCertificate: () -> Unit = {},
    onOpenIdentity: () -> Unit = {},
    onOpenWeeklySavings: () -> Unit = {},
    onOpenGrow31Savings: () -> Unit = {},
    onOpenUpfrontDeposit: () -> Unit = {},
    onOpenYouthAccount: () -> Unit = {},
    onOpenCard: () -> Unit = {},
    onOpenTransit: () -> Unit = {},
    onOpenGroupAccounts: () -> Unit = {},
    onOpenIkimina: () -> Unit = {},
    onOpenSacco: () -> Unit = {},
    onOpenHarvestAdvance: () -> Unit = {},
    onOpenSpending: () -> Unit = {},
    onOpenRides: () -> Unit = {},
    onOpenDesignatedDriver: () -> Unit = {},
    onOpenBikeRental: () -> Unit = {},
    onOpenParking: () -> Unit = {},
    onOpenMotoFareCollect: () -> Unit = {},
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
    // Real fix (2026-08-10): direct response to repeated, specific product feedback
    // ("itunda still feels like code not product... not organized... hard to
    // navigate") -- traced to a concrete cause: this screen rendered ~17 categories
    // and 60+ rows fully expanded, always, in one long scroll, below a "Search" box
    // that was pure decoration (no TextField, nothing typed into it ever did
    // anything). That's the exact anti-pattern Hick's Law names -- decision time
    // rises with visible choice count -- and it's a data dump (whatever got built,
    // in build order) rather than an information architecture. The row content and
    // every real onClick below is completely unchanged; only how it's found and
    // shown changed: a real, working search over all of it.
    var menuSearchQuery by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    // CORRECTED 2026-08-12: the 16 heavier categories below used to collapse behind a
    // tap-to-expand accordion (added 2026-08-10 on a Hick's Law/decision-overload
    // theory). A real, direct user-provided screenshot batch of the actual Toss app's
    // own All tab shows every category's items always fully visible, no
    // collapse/expand mechanic anywhere -- Toss's real answer to a long list is the
    // real, working search box above (kept, unchanged), not hiding content behind a
    // tap. Reverted to always-expanded FlatSection for all 16, matching the real
    // screenshots exactly; expandedMenuSection state removed as dead code.

    // Shop/Eats/Marketplace/Community/Jobs added here as separate flat rows
    // (2026-08-10, real user correction) -- all lost their own primary tab (see
    // ItundaTab's own doc comment), and nesting them behind one "Shop"/"Hood" row
    // with an internal toggle would be a tab bar inside a tab: real noise a flat
    // catalog shouldn't have. Each opens its real feature/impl content directly.
    // Marketplace/Community/Jobs/Property's real, deliberately Karrot-sourced
    // top-bar/menu-sheet/FAB shell (previously shared via HoodTab's chip row) now
    // lives in HoodSectionScreen, parameterized per section instead of switchable --
    // see that composable's own doc comment. Pay's own row removed since Pay is now
    // a primary tab itself, not something Explore needs to surface.
    val quickLinksRows = listOf(
        FlatRow("Shop", subtitle = "Coupang-style commerce", glyph = { ShoppingBagGlyph(size = 28.dp) }, onClick = onOpenShop),
        FlatRow("Eats", subtitle = "Food delivery, order or deliver", glyph = { PlaceRestaurant(size = 28.dp) }, onClick = onOpenEats),
        FlatRow("Marketplace", subtitle = "당근마켓-style neighborhood buy/sell", glyph = { PlaceMarket(size = 28.dp) }, onClick = onOpenMarketplace),
        FlatRow("Community", subtitle = "Neighborhood life, local questions and posts", glyph = { SpeechBubbleGlyph(size = 28.dp) }, onClick = onOpenCommunity),
        FlatRow("Jobs", subtitle = "Neighborhood gigs and part-time work", glyph = { BriefcaseGlyph(size = 28.dp) }, onClick = onOpenJobs),
        FlatRow("Property", subtitle = "Neighborhood rentals and sales", glyph = { TravelHouse(size = 28.dp) }, onClick = onOpenProperty),
        // Real fix (2026-08-13, direct user report: "entire app is still messy...
        // give me something real"): used to open BenefitsTab, a full screen of
        // entirely fabricated content -- a fake "P 137" points pill, a fake "🎁
        // Limited gift for Rwanda / 25,000" banner, 4 dead "Visit" buttons (Happy
        // lottery/Push the button/Try on/Bring friends, zero real backend), and a
        // fake "3 chances to get money back / RWF 5,000 / BK account -> TUYIZERE
        // Eric" card. This row's own subtitle ("Points, coupons, rewards") already
        // describes exactly what the real "Reward tasks" mini-app below in this same
        // section does (rw.itunda.rewards, real tasks/steps/referral, real RWF
        // payouts) -- points there instead of a second, fake destination.
        FlatRow(
            "Benefits", subtitle = "Points, coupons, rewards", glyph = { GiftBox(size = 28.dp) },
            onClick = { context.startActivity(android.content.Intent(context, rw.itunda.app.miniapps.RewardTasksMiniAppActivity::class.java)) },
        ),
        FlatRow("Invest", subtitle = "RSE stocks, real portfolio", glyph = { ChartIncreasingGlyph(size = 28.dp) }, onClick = onOpenInvest),
        FlatRow("26-Week Savings", subtitle = "Escalating auto-save, streak bonus", glyph = { MoneyBagGlyph(size = 28.dp) }, onClick = onOpenWeeklySavings),
        FlatRow("31-Day Savings", subtitle = "Daily streak, tiered bonus rate", glyph = { MoneyBagGlyph(size = 28.dp) }, onClick = onOpenGrow31Savings),
        FlatRow("Map", subtitle = "Real Rwanda map, self-hosted", glyph = { PinGlyph(size = 28.dp) }, onClick = onOpenMap),
    )
    val accountsRows = listOf(
        FlatRow("Open account", subtitle = "Itunda Account, other banks, RSE brokerage", glyph = { PlaceBank(size = 28.dp) }, onClick = onOpenOverview),
        FlatRow("My assets", subtitle = "Accounts, loans, RSE holdings, cards, points", glyph = { MoneyBagGlyph(size = 28.dp) }, onClick = onOpenOverview),
        FlatRow("Card", subtitle = "App-controlled spend limits, one-tap freeze", glyph = { ObjectCreditCard(size = 28.dp) }, onClick = onOpenCard),
        // Real Kigali public-transit stored-value balance (2026-08-27) -- see
        // TransitScreen.kt's own doc comment for the full sourced account.
        FlatRow("Transit", subtitle = "Top up and tap to pay your real Kigali bus fare", glyph = { PlaceBusStop(size = 28.dp) }, onClick = onOpenTransit),
        FlatRow("Spending", subtitle = "Real, ledger-based category breakdown", glyph = { BarChartGlyph(size = 28.dp) }, onClick = onOpenSpending),
        FlatRow("Group account", subtitle = "Shared account with dues and split expenses", icon = Icons.Outlined.Group, iconColor = AccentPurple, onClick = onOpenGroupAccounts),
        FlatRow("Family", subtitle = "Link a guardian or child, view read-only spending", glyph = { FamilyGlyph(size = 28.dp) }, onClick = onOpenFamilyLink),
        FlatRow("Foreign currency", subtitle = "Hold and convert USD, EUR, GBP", glyph = { GlobeGlyph(size = 28.dp) }, onClick = onOpenForeignCurrency),
        FlatRow("Subscriptions", subtitle = "Detected recurring payments + merchant billing plans", glyph = { CalendarGlyph(size = 28.dp) }, onClick = onOpenSubscriptions),
        FlatRow("Digital certificate", subtitle = "Sign agreements in Itunda", glyph = { ObjectPen(size = 28.dp) }, onClick = onOpenCertificate),
    )
    val sendPayRows = listOf(
        FlatRow("Transfer", subtitle = "Auto-transfer, split a bill", icon = IdsIcons.Send, iconColor = AccentIndigo, onClick = onOpenTransferHub),
        FlatRow("Request money", subtitle = "Generate a real payment request code", glyph = { ReceiptGlyph(size = 28.dp) }, onClick = onOpenRequestMoney),
        FlatRow("Auto top-up", subtitle = "Refill your account automatically from a linked account", glyph = { RefreshCardGlyph(size = 28.dp) }, onClick = onOpenAutoTopUp),
        FlatRow("Mobile plan", subtitle = "MTN, Airtel, broadband", glyph = { ObjectMobilePhone(size = 28.dp) }, onClick = {
            context.startActivity(android.content.Intent(context, rw.itunda.app.miniapps.PayBillsMiniAppActivity::class.java))
        }),
    )
    // Real icon differentiation (2026-08-24) -- these 6 rows all shared the identical
    // Savings icon, the exact "parallel icon-clash instance" DESIGN_REFERENCES.md
    // Section 65 named as a ready-made next pass but never fixed. Reuses
    // BankHubScreen's own already-shipped, real per-product mapping (Section 193)
    // byte-for-byte where the same product appears there, rather than inventing a
    // second, divergent icon choice for the identical concept on a different screen.
    // Youth account has no BankHubScreen counterpart to mirror -- ChildCare is new,
    // picked for the real "ages 7-18 starter account" concept, not already used
    // elsewhere in this row set.
    val saveGrowRows = listOf(
        FlatRow("26-week savings", subtitle = "Escalating weekly deposit plan", glyph = { MoneyBagGlyph(size = 28.dp) }, onClick = onOpenWeeklySavings),
        FlatRow("31-day savings", subtitle = "Daily streak, tiered bonus rate", glyph = { MoneyBagGlyph(size = 28.dp) }, onClick = onOpenGrow31Savings),
        FlatRow("12-month deposit", subtitle = "Interest paid upfront, principal locked", glyph = { LockGlyph(size = 28.dp) }, onClick = onOpenUpfrontDeposit),
        FlatRow("Youth account", subtitle = "Capped starter account, ages 7-18", glyph = { ChildGlyph(size = 28.dp) }, onClick = onOpenYouthAccount),
        FlatRow("Ikimina", subtitle = "Rotating savings group -- everyone takes a turn", icon = Icons.Outlined.Groups, iconColor = AccentTeal, onClick = onOpenIkimina),
        FlatRow("SACCO shares", subtitle = "Buy cooperative shares, earn a real dividend", glyph = { PlaceBank(size = 28.dp) }, onClick = onOpenSacco),
    )
    // Same real gap, same fix shape, same BankHubScreen (Section 193) mapping reused
    // for Harvest advance/VUP/Student loan/Moto-Taxi -- 3 of these 6 rows shared the
    // identical AccountBalanceWallet icon before this. "Get a loan" and "Credit score"
    // already had their own distinct icons and are unchanged.
    val borrowRows = listOf(
        FlatRow("Get a loan", subtitle = "Personal, salary-backed, SME working capital", glyph = { MoneyBagGlyph(size = 28.dp) }, onClick = onOpenLoans),
        FlatRow("Credit score", subtitle = "Free check, alternative data", glyph = { NatureGlowingStar(size = 28.dp) }, onClick = onOpenCreditScore),
        FlatRow("Harvest advance", subtitle = "Coffee cooperative input financing", glyph = { SeedlingGlyph(size = 28.dp) }, onClick = onOpenHarvestAdvance),
        FlatRow("VUP Financial Services", subtitle = "Means-tested government microloan for farming, livestock, business", glyph = { ShieldEmojiGlyph(size = 28.dp) }, onClick = onOpenVupLoan),
        FlatRow("Student loan", subtitle = "BRD higher-education loan -- 11% undergraduate, 12% postgraduate", glyph = { PlaceSchool(size = 28.dp) }, onClick = onOpenStudentLoan),
        FlatRow("Moto-Taxi Ownership", subtitle = "Save a 30% down payment, then convert to a loan for your own bike", icon = Icons.Outlined.DirectionsBike, iconColor = AccentTeal, onClick = onOpenMotoOwnership),
    )
    val transportRows = listOf(
        FlatRow("Rides", subtitle = "Request a ride or drive for real fares", glyph = { TravelCar(size = 28.dp) }, onClick = onOpenRides),
        FlatRow("Designated driver", subtitle = "A driver takes you and your own car home", glyph = { ObjectKey(size = 28.dp) }, onClick = onOpenDesignatedDriver),
        FlatRow("Bike rental", subtitle = "Rent a nearby bike or scooter, billed by the minute", glyph = { BikeGlyph(size = 28.dp) }, onClick = onOpenBikeRental),
        FlatRow("Parking", subtitle = "Rent a nearby parking spot, billed by the hour", glyph = { ParkingGlyph(size = 28.dp) }, onClick = onOpenParking),
        FlatRow("Bus", subtitle = "Book intercity bus seats or post your own route", glyph = { PlaceBusStop(size = 28.dp) }, onClick = onOpenBus),
        // Real "tap to pay your moto-taxi fare" (2026-08-27, direct user follow-up:
        // "now we can make pay for tax and moto as well") -- see
        // MotoFareCollectScreen.kt's own doc comment for the full sourced account.
        FlatRow("Collect a moto fare", subtitle = "Drivers: tap or scan a rider's code to collect a real fare", glyph = { BikeGlyph(size = 28.dp) }, onClick = onOpenMotoFareCollect),
        FlatRow("Vehicle inspection", subtitle = "Pay a mechanic to inspect a used car before you buy", glyph = { WrenchGlyph(size = 28.dp) }, onClick = onOpenVehicleInspection),
        FlatRow("My vehicles", subtitle = "Track your car's estimated resale value", glyph = { TravelCar(size = 28.dp) }, onClick = onOpenVehicleValuation),
    )
    val communityTrustRows = listOf(
        FlatRow("Trust score", subtitle = "How your neighbors see you on Marketplace, Jobs, and Property", glyph = { NatureStar(size = 28.dp) }, onClick = onOpenTrustScore),
        FlatRow("Q&A", subtitle = "Ask a question, answer one, get adopted", glyph = { SpeechBubbleGlyph(size = 28.dp) }, onClick = onOpenKnowledge),
    )
    val cashAgentRows = listOf(
        FlatRow("Agent till", subtitle = "For assigned cash-agent operators: cash-in, cash-out, till count", glyph = { PlaceItundaAgent(size = 28.dp) }, onClick = onOpenAgentOperator),
        FlatRow("Float marketplace", subtitle = "For assigned cash-agents: offer or request float from nearby agents", glyph = { PlaceMarket(size = 28.dp) }, onClick = onOpenFloatMarketplace),
    )
    val switchSaveRows = listOf(
        FlatRow("Switch your personal loan", trailing = "12% ~ 24%", trailingIsLink = true, glyph = { MoneyBagGlyph(size = 28.dp) }, onClick = onOpenLoans),
        FlatRow("Switch your rent deposit loan", trailing = "9% ~ 15%", trailingIsLink = true, glyph = { TravelHouse(size = 28.dp) }, onClick = onOpenLoans),
        FlatRow("Switch your SME loan", trailing = "11% ~ 22%", trailingIsLink = true, glyph = { PlaceMarket(size = 28.dp) }, onClick = onOpenLoans)
    )
    val cardsRows = listOf(
        FlatRow("Itunda Card", trailing = "5% back on bills", trailingIsLink = true, glyph = { ObjectCreditCard(size = 28.dp) }, onClick = onOpenCard),
        FlatRow("Virtual card", trailing = "Instant issue", glyph = { ObjectCreditCard(size = 28.dp) }, onClick = onOpenCard)
    )
    val servicesRows = listOf(
        FlatRow("Rent deposit protection", glyph = { TravelHouse(size = 28.dp) }),
        FlatRow("Recurring payments", glyph = { CalendarGlyph(size = 28.dp) }, onClick = onOpenSubscriptions),
        FlatRow("Import recurring payments", icon = Icons.Outlined.LocalShipping, iconColor = AccentGray),
        FlatRow("REG & WASAC bills", glyph = { ObjectLightBulb(size = 28.dp) }, onClick = {
            context.startActivity(android.content.Intent(context, rw.itunda.app.miniapps.PayBillsMiniAppActivity::class.java))
        }),
        // Real fix (2026-08-11): interest now auto-credits to the account the instant
        // it accrues (see backend SavingsService.accrueInterest's own doc comment) --
        // "Claim interest now" overclaimed a pending action that no longer exists.
        FlatRow("Interest earned this month", glyph = { MoneyBagGlyph(size = 28.dp) }, onClick = onClaimInterest),
        FlatRow("SME income tax estimate", glyph = { ReceiptGlyph(size = 28.dp) }),
        FlatRow("Split a bill with friends", glyph = { SplitBillDice(size = 28.dp) }, onClick = onSwitchToTalk),
        FlatRow("Shared calendar", glyph = { CalendarGlyph(size = 28.dp) }),
        FlatRow("Kids' allowance tasks", glyph = { ChildGlyph(size = 28.dp) })
    )
    val foreignCurrencyRows = listOf(
        FlatRow("Foreign currency account", trailing = "100% rate preference", trailingIsLink = true, glyph = { GlobeGlyph(size = 28.dp) }, onClick = onOpenForeignCurrency),
        FlatRow("International transfer", glyph = { GlobeGlyph(size = 28.dp) }, onClick = onOpenForeignCurrency)
    )
    val growMoneyRows = listOf(
        FlatRow("RSE stocks", subtitle = "BOK, MTNR, BLR, IMR, CMR, EQTY", glyph = { ChartIncreasingGlyph(size = 28.dp) }, onClick = onOpenInvest),
        FlatRow("Bonds & fixed income", trailing = "7.5% ~ 12%", trailingIsLink = true, glyph = { PlaceBank(size = 28.dp) }, onClick = onOpenInvest),
        FlatRow("IPO schedule", glyph = { ChartIncreasingGlyph(size = 28.dp) }, onClick = onOpenInvest),
        FlatRow("Brokerage account", trailing = "Up to 30,000 RWF", trailingIsLink = true, glyph = { PlaceBank(size = 28.dp) }, onClick = onOpenInvest)
    )
    val pensionRows = listOf(
        FlatRow("Check my RSSB pension", glyph = { PlaceBank(size = 28.dp) }),
        FlatRow("Pension products", glyph = { MoneyBagGlyph(size = 28.dp) })
    )
    val loansRows = listOf(
        FlatRow("Check my max limit", glyph = { ChartIncreasingGlyph(size = 28.dp) }, onClick = onOpenLoans),
        FlatRow("Personal loan", trailing = "11% ~ 24%", trailingIsLink = true, glyph = { MoneyBagGlyph(size = 28.dp) }, onClick = onOpenLoans)
    )
    // Real Toss arrangement (2026-08-12, direct user screenshot comparison) -- the
    // real All-tab reference screenshots have NO "Notifications & consent"-style
    // section at all; Notifications and legal-document links live exclusively in
    // Settings on the real app (Section 51/52, docs/DESIGN_REFERENCES.md), not
    // duplicated into the app-launcher-style All tab. This section used to render
    // here; its 3 legal rows (never had a real destination -- itunda has no
    // Credit-data-usage/Privacy-policy/Terms document screens yet, same honest
    // inert-row state as before, not fabricated now either) moved to a real
    // "Legal Documents" card in SettingsScreen.kt instead of staying duplicated in
    // two places. The "Notifications" row is dropped outright, not moved -- Settings'
    // own "Notifications" row already covers the same real destination.
    // Real Toss icon convention (2026-08-12, direct user screenshot comparison) --
    // the real Help section shows a distinct colored icon on every single row; these
    // 6 rows previously had none at all (plain text), a real visible "still not the
    // same" gap the user flagged directly against the reference screenshot.
    val supportRows = listOf(
        FlatRow("FAQ", glyph = { QuestionGlyph(size = 28.dp) }),
        FlatRow("Live chat", glyph = { SpeechBubbleGlyph(size = 28.dp) }),
        FlatRow("Call support", glyph = { ObjectMobilePhone(size = 28.dp) }),
        FlatRow("Report an issue with a transaction", glyph = { WarningGlyph(size = 28.dp) }, showChevron = true, onClick = onOpenSupport),
        FlatRow("My support tickets", glyph = { VoucherTicket(size = 28.dp) }, showChevron = true, onClick = onOpenSupport),
        FlatRow("Announcements", glyph = { BellGlyph(size = 28.dp) })
    )
    // Real Toss Bank reference mapping (see the doc comment further down, kept in
    // place, for the full account of Switch & save/Cards/Services/Foreign
    // currency/Grow your money/Pension/Loans/Notifications & consent/Support).
    val allMenuSectionsForSearch = listOf(
        "Quick links" to quickLinksRows,
        "Accounts & cards" to accountsRows,
        "Send & pay" to sendPayRows,
        "Save & grow" to saveGrowRows,
        "Borrow" to borrowRows,
        "Transport" to transportRows,
        "Community & trust" to communityTrustRows,
        "Cash agent tools" to cashAgentRows,
        "Switch & save" to switchSaveRows,
        "Cards" to cardsRows,
        "Services" to servicesRows,
        "Foreign currency" to foreignCurrencyRows,
        "Grow your money" to growMoneyRows,
        "Pension" to pensionRows,
        "Loans" to loansRows,
        "Support" to supportRows,
    )

    // Real fix (2026-08-25) -- same redundant-bottom-padding-vs-Scaffold-inset bug as
    // PayTab's own identical LazyColumn, see that one's doc comment for the full
    // account.
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(start = Ids.layout.screenHorizontal, end = Ids.layout.screenHorizontal, top = Ids.layout.screenVertical),
        verticalArrangement = Arrangement.spacedBy(Ids.layout.cardGap)
    ) {
        item {
            AllTopBar(
                onOpenAuthentication = onOpenIdentity,
                onOpenHelp = onOpenSupport,
                onOpenSettings = onOpenSettings,
            )
        }
        item {
            SearchBar(
                query = menuSearchQuery,
                onQueryChange = { menuSearchQuery = it },
                placeholder = "Search everything else",
            )
        }
        if (menuSearchQuery.isBlank()) {
            // Real Toss layout (2026-08-12), matching the real screenshot's own
            // opening two sections exactly: "Open" (a real app-launcher shortcut grid,
            // itunda's Mini/Games/Bank/Pick already matched this concept 1:1, just
            // renamed to the real label) then a real quick-launch grid, in that order
            // -- itunda previously had FIVE separate "quick access"-flavored sections
            // stacked before reaching any of the real categorized content (Quick
            // links, Quick access, Mini apps, Partner mini-apps, Shortcuts), which is
            // real, visible clutter the actual Toss app doesn't have. Consolidated to
            // three: Open, Shortcuts, then Quick links (with Mini apps' 4 rows folded
            // in rather than kept as their own separate header) -- "Shortcuts" stays
            // that name, not renamed to "Recent", since it's still a static curated
            // list, not real recently-used tracking (the exact honesty bug this same
            // screen's own 2026-08-10 fix already corrected once).
            item {
                // Real fix (2026-08-13, direct user report: "entire app is still
                // messy... keep fixing"): this whole grid had no onItemClick at all --
                // IconGridSection's default is a silent no-op, so all 4 tiles here were
                // dead taps. "Youth" (real YouthAccountScreen, ages 7-18 capped
                // account -- renamed from the bare "Mini" label 2026-08-23, see
                // docs/UI_UX_GUIDELINES.md §12: that label used an Apps icon that
                // visually suggested Saronite's own real "mini-app" framework, not the
                // banking product it actually opened) and "Bank" (real itunda Bank
                // hub) both already have working destinations elsewhere in this file,
                // just never wired here. "Games" is honestly still unbuilt -- this
                // same file's own MenuScreen doc comment above already calls it
                // "(planned)" -- and "Pick" has no real backing feature anywhere in
                // this codebase (grepped). Left both unwired rather than fabricating a
                // destination neither one has.
                IconGridSection(
                    "Open",
                    listOf(
                        "Youth" to Icons.Outlined.Savings,
                        "Games" to Icons.Outlined.SportsEsports,
                        "Bank" to Icons.Outlined.AccountBalance,
                        "Pick" to IdsIcons.Star,
                    ),
                    onItemClick = { label ->
                        when (label) {
                            "Youth" -> onOpenYouthAccount()
                            "Bank" -> onOpenBank()
                        }
                    },
                )
            }
            item {
                IconGridSection(
                    "Shortcuts",
                    listOf(
                        "Open account" to Icons.Outlined.AddCircleOutline,
                        "Verify" to Icons.Outlined.VerifiedUser,
                        "Send" to IdsIcons.Send,
                        "Group" to Icons.Outlined.Group,
                        "Property" to Icons.Outlined.HomeWork,
                        "Insurance" to IdsIcons.ShieldCheck,
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
            // Benefits/Pay folded in here (2026-07-18) -- both lost their own top-level
            // tab when the bottom nav became Home/Shop/Hood/Talk/My, but stay just as
            // reachable as a real row instead of being dropped. Mini apps' own 4 rows
            // (Account balance/Pay bills/Reward tasks/Insurance) merged in here too
            // (2026-08-12), not kept as their own separate "Mini apps" header.
            item {
                FlatSection(
                    "Quick links",
                    quickLinksRows + listOf(
                        FlatRow("Account balance", onClick = {
                            context.startActivity(android.content.Intent(context, rw.itunda.app.miniapps.AccountBalanceMiniAppActivity::class.java))
                        }),
                        FlatRow("Pay bills", onClick = {
                            context.startActivity(android.content.Intent(context, rw.itunda.app.miniapps.PayBillsMiniAppActivity::class.java))
                        }),
                        FlatRow("Reward tasks", onClick = {
                            context.startActivity(android.content.Intent(context, rw.itunda.app.miniapps.RewardTasksMiniAppActivity::class.java))
                        }),
                        FlatRow("Insurance", onClick = {
                            context.startActivity(android.content.Intent(context, rw.itunda.app.miniapps.InsuranceMiniAppActivity::class.java))
                        }),
                    ),
                )
            }
            // Real Partner SDK section (2026-07-17) -- lists REAL approved third-party
            // mini-apps from GET /api/v1/mini-apps/catalog (services/backend/partners),
            // closing the mobile half of docs/TOSS_PARITY_MATRIX.md's Partner SDK row.
            // Empty when the catalog has no approved entries yet (a real, honest empty
            // state, not hidden entirely, so this section's existence is itself visible
            // proof the mechanism is wired up end to end).
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
            item { FlatSection("Accounts & cards", accountsRows) }
            item { FlatSection("Send & pay", sendPayRows) }
            item { FlatSection("Save & grow", saveGrowRows) }
            item { FlatSection("Borrow", borrowRows) }
            item { FlatSection("Transport", transportRows) }
            item { FlatSection("Community & trust", communityTrustRows) }
            // Kept last and separately labeled, not blended into the rows above: these two
            // are role-gated (only assigned cash-agent operators can use them), so grouping
            // them with everyday-user rows would itself be the same "wrong category" problem
            // this whole section just got fixed for.
            item { FlatSection("Cash agent tools", cashAgentRows) }
            // Everything below is modeled directly on the real Toss Bank
            // 갈아타기/신용카드/체크카드/서비스/외화/목돈굴리기/연금/대출/알림 및 동의/고객센터
            // reference screens (user-provided, 2026-07-10), adapted to Rwanda
            // rails per docs/FACT_CHECKED_TOSS_RWANDA_MAP.md's established
            // mapping (REG/WASAC/Irembo/RRA, MTN MoMo/Airtel Money, RSE tickers,
            // RSSB pension) rather than left as Korean-market content.
            item { FlatSection("Switch & save", switchSaveRows) }
            item { FlatSection("Cards", cardsRows) }
            item { FlatSection("Services", servicesRows) }
            item { FlatSection("Foreign currency", foreignCurrencyRows) }
            item { FlatSection("Grow your money", growMoneyRows) }
            item { FlatSection("Pension", pensionRows) }
            item { FlatSection("Loans", loansRows) }
            item { FlatSection("Support", supportRows) }
        } else {
            val query = menuSearchQuery.trim()
            val matchingSections = allMenuSectionsForSearch.map { (title, rows) ->
                title to rows.filter { it.title.contains(query, ignoreCase = true) }
            }.filter { it.second.isNotEmpty() }
            if (matchingSections.isEmpty()) {
                item {
                    Text(
                        "No match for \"$query\".",
                        color = Ids.colors.textTertiary,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            } else {
                matchingSections.forEach { (title, rows) ->
                    item { FlatSection(title, rows) }
                }
            }
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
    onSwitchToEats: () -> Unit = {},
    onSwitchToMarketplace: () -> Unit = {},
    onSwitchToJobs: () -> Unit = {},
    onSwitchToProperty: () -> Unit = {},
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
    // Both of these are real, working, previously-uncalled read-backs (found by a
    // 2026-08-14 sweep of every ApiService method with zero call sites). Each closes a
    // real write-with-no-read asymmetry: you could report a scam account or review a
    // booking, and then never see it again anywhere in any client.
    var myScamReports by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<List<rw.itunda.core.network.ScamReportDto>>(emptyList()) }
    var myBookingReviews by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<List<rw.itunda.core.network.MerchantBookingReviewDto>>(emptyList()) }

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
        try { myScamReports = rw.itunda.core.network.NetworkClient.apiService.getMyScamReports().reports } catch (_: Exception) { }
        try { myBookingReviews = rw.itunda.core.network.NetworkClient.apiService.getMyBookingReviews().reviews } catch (_: Exception) { }
    }

    // Real fix (2026-08-25) -- same redundant-bottom-padding-vs-Scaffold-inset bug as
    // PayTab's own identical LazyColumn, see that one's doc comment for the full
    // account.
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(start = Ids.layout.screenHorizontal, end = Ids.layout.screenHorizontal, top = Ids.layout.screenVertical),
        verticalArrangement = Arrangement.spacedBy(Ids.layout.cardGap),
    ) {
        item { BackTopBar("My", onBack) }
        item { ProfilePhotoCard() }
        // Real Toss-sourced passwordless-login rollout (2026-08-23) -- see
        // PinUpgradeCard.kt's own doc comment. Own file, not inline here, matching
        // this session's own file-size-lint discipline for this already-oversized
        // file.
        item { PinUpgradeCard() }
        item { VerificationCard() }
        if (affiliateLinks.isNotEmpty()) {
            item {
                val totalClicks = affiliateLinks.sumOf { it.clickCount }
                val totalEarned = affiliateCommissions.sumOf { it.commissionAmount }
                // Real fix (2026-08-24, flat-design sweep): dropped the IdsCard wrapper,
                // matching "My orders" below (already flat) (docs/UI_UX_GUIDELINES.md §10).
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
        // Real order tracking -- Naver Pay/Shopping's own "My" tab leads with recent
        // orders across every product, not a settings list. Shows the real 3 most
        // recent orders per product; tapping switches to that product's own tab where
        // the full MyCommerceOrdersView/MyEatsOrdersView already lives.
        if (shopOrders.isNotEmpty() || eatsOrders.isNotEmpty()) {
            item { Text("My orders", color = Ids.colors.textPrimary, fontSize = 19.sp, fontWeight = FontWeight.Bold) }
            items(shopOrders.take(3), key = { it.id }) { order ->
                Row(
                    modifier = Modifier.fillMaxWidth().pressScaleClickable(onClick = onSwitchToShop).padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text("Shop order", color = Ids.colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(order.status, color = Ids.colors.textSecondary, fontSize = 13.sp)
                    }
                    Text("%,.0f RWF".format(order.totalAmount), color = Ids.colors.textPrimary, fontSize = 15.sp)
                }
            }
            items(eatsOrders.take(3), key = { it.id }) { order ->
                Row(
                    modifier = Modifier.fillMaxWidth().pressScaleClickable(onClick = onSwitchToEats).padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text("Eats order", color = Ids.colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(order.status, color = Ids.colors.textSecondary, fontSize = 13.sp)
                    }
                    Text("%,.0f RWF".format(order.totalAmount), color = Ids.colors.textPrimary, fontSize = 15.sp)
                }
            }
        }
        // Real favorites/wishlist tracking across every product with one -- counts are
        // real (GET .../favorites on each module), tapping switches directly to that
        // real destination's own dedicated WISHLIST view. Each of Marketplace/Jobs/
        // Property is its own flat Explore destination now (2026-08-10, Hood's chip
        // row retired), so this is a real, direct deep link, not "one more tap" into
        // a shared sub-view the way it was when Hood was one nested screen.
        item { Text("My favorites", color = Ids.colors.textPrimary, fontSize = 19.sp, fontWeight = FontWeight.Bold) }
        item {
            FlatSection(
                "",
                listOf(
                    FlatRow("Marketplace wishlist", trailing = "$favoriteListingsCount", glyph = { WishlistHeart(favorited = true, size = 28.dp) }, onClick = onSwitchToMarketplace),
                    FlatRow("Jobs wishlist", trailing = "$favoriteJobPostsCount", glyph = { WishlistHeart(favorited = true, size = 28.dp) }, onClick = onSwitchToJobs),
                    FlatRow("Property wishlist", trailing = "$favoritePropertyListingsCount", glyph = { WishlistHeart(favorited = true, size = 28.dp) }, onClick = onSwitchToProperty),
                    FlatRow("Restaurant favorites", trailing = "$favoriteRestaurantsCount", glyph = { WishlistHeart(favorited = true, size = 28.dp) }, onClick = onSwitchToEats),
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
                    FlatRow("Marketplace", trailing = "$myListingsCount", glyph = { PlaceMarket(size = 28.dp) }, onClick = onSwitchToMarketplace),
                    FlatRow("Jobs posted", trailing = "$myJobPostsCount", glyph = { BriefcaseGlyph(size = 28.dp) }, onClick = onSwitchToJobs),
                    FlatRow("Property listed", trailing = "$myPropertyListingsCount", glyph = { TravelHouse(size = 28.dp) }, onClick = onSwitchToProperty),
                ),
            )
        }
        // Real read-back of reviews this user has written. ownerReply is the reason
        // this matters most: a merchant can already reply to a review, and until
        // 2026-08-14 there was nowhere in any client the author could ever see that
        // reply -- the endpoint existed and worked, it just had no caller.
        if (myBookingReviews.isNotEmpty()) {
            item { Text("My reviews", color = Ids.colors.textPrimary, fontSize = 19.sp, fontWeight = FontWeight.Bold) }
            // Real fix (2026-08-24, flat-design sweep): dropped the per-row IdsCard --
            // this is a ledger-style history list (a log of past reviews), matching the
            // deliberate per-row-divider convention already established for statement/
            // ledger lists (docs/DESIGN_REFERENCES.md §274), not a catalog list.
            items(myBookingReviews, key = { it.id }) { review ->
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(review.serviceName, color = Ids.colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Text("★".repeat(review.rating.coerceIn(0, 5)), color = StarGold, fontSize = 13.sp)
                    }
                    review.comment?.takeIf { it.isNotBlank() }?.let {
                        Text(it, color = Ids.colors.textSecondary, fontSize = 13.sp)
                    }
                    review.ownerReply?.takeIf { it.isNotBlank() }?.let { reply ->
                        Column(
                            modifier = Modifier.fillMaxWidth()
                                .background(Ids.colors.surfaceSoft, androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                                .padding(10.dp),
                        ) {
                            Text("Owner replied", color = Ids.colors.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text(reply, color = Ids.colors.textPrimary, fontSize = 13.sp)
                        }
                    }
                    Text(relativeTimeAgo(review.createdAt), color = Ids.colors.textTertiary, fontSize = 11.sp)
                }
                Divider(color = Ids.colors.divider, thickness = 0.5.dp)
            }
        }
        // Real read-back of scam reports this user filed from the Transfer flow's own
        // "report this account" dialog. Reporting worked; seeing what you reported
        // never did, on any client.
        if (myScamReports.isNotEmpty()) {
            item { Text("My scam reports", color = Ids.colors.textPrimary, fontSize = 19.sp, fontWeight = FontWeight.Bold) }
            // Real fix (2026-08-24, flat-design sweep): see "My reviews" above -- same
            // ledger-style history list, same per-row-divider convention.
            items(myScamReports, key = { it.id }) { report ->
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(report.reportedIdentifier, color = Ids.colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text(report.reason, color = Ids.colors.textSecondary, fontSize = 13.sp)
                    Text(relativeTimeAgo(report.createdAt), color = Ids.colors.textTertiary, fontSize = 11.sp)
                }
                Divider(color = Ids.colors.divider, thickness = 0.5.dp)
            }
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
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Real fix (2026-08-13, direct user report against a live screenshot: a
            // bare "Profile photo URL" text box + "Save photo" button read as an
            // unpolished, engineer-facing debug control). itunda genuinely has no
            // image-upload/hosting pipeline to build a real device photo picker on top
            // of -- see this composable's own doc comment and Merchant.photoUrl's
            // identical "real URL, not a fabricated upload" discipline -- so the honest
            // fix is explaining what this real feature actually does and giving live
            // visual feedback, not pretending to be a picker it isn't. Preview shows
            // urlInput itself (before saving) rather than only the already-saved
            // profilePhotoUrl, so pasting a link gives an immediate result.
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                rw.itunda.core.designsystem.components.IdsAvatar(
                    name = displayName.ifBlank { "?" },
                    photoUrl = urlInput.trim().ifBlank { profilePhotoUrl },
                    size = 56.dp,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text("Profile photo", color = Ids.colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Paste a link to a photo hosted elsewhere -- itunda doesn't host photo uploads yet.",
                        color = Ids.colors.textSecondary, fontSize = 12.sp,
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                error?.let { Text(it, color = Ids.colors.danger, fontSize = 12.sp) }
                IdsTextField(
                    value = urlInput, onValueChange = { urlInput = it },
                    label = "Photo link",
                    placeholder = "https://example.com/my-photo.jpg",
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

    // Real fix (2026-08-24, flat-design sweep): dropped the IdsCard wrapper -- a
    // lone conditional section in the "My" LazyColumn, no sibling to separate it
    // from (docs/UI_UX_GUIDELINES.md §10).
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Verify your account", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        if (!phoneVerified) VerificationRow(kind = "phone", hasEmail = true, onVerified = ::load)
        if (!emailVerified) VerificationRow(kind = "email", hasEmail = email != null, onVerified = ::load)
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
                        .pressScaleClickable(enabled = !busy) {
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
                // Real CTA-label-clarity fix (2026-08-24, docs/DESIGN_REFERENCES.md §11 --
                // same web fix as BankDashboard.tsx's VerificationRow, commit 58d58259): a
                // bare "Confirm" doesn't state the outcome, per Toss's own dark-pattern-
                // prevention CTA rule.
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (busy || code.isBlank()) Ids.colors.textTertiary else Ids.colors.brand)
                        .pressScaleClickable(enabled = !busy && code.isNotBlank()) { confirm() }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) { Text(if (busy) "…" else if (kind == "email") "Verify email" else "Verify phone number", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            }
        }
        error?.let { Text(it, color = Ids.colors.danger, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)) }
    }
}

internal data class FlatRow(
    val title: String,
    val subtitle: String? = null,
    val trailing: String? = null,
    val trailingIsLink: Boolean = false,
    val icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    val iconColor: Color = AccentIndigo,
    // Real Toss icon-language fix (2026-08-26, direct user screenshot comparison
    // against Toss's own All-tab): Toss never wraps its category icons in a
    // colored background square -- every glyph in TossFace is itself a distinct,
    // full-color illustration, so no chip is needed to differentiate rows. Takes
    // priority over icon/iconColor below when set; itundaface only covers a real
    // subset of this screen's ~90 rows today (see itundaface's own file doc
    // comments for sourcing), so rows without a real glyph yet keep the
    // Material-icon-in-a-square fallback rather than getting a fabricated one.
    val glyph: (@Composable () -> Unit)? = null,
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
        rows.forEach { row -> FlatSectionRow(row) }
    }
}

// Extracted from FlatSection's own forEach body (2026-08-11) so the press-scale
// state below gets its own composable slot per row instead of sharing one across a
// loop. Real Toss micro-interaction reference -- see IdsButton.kt's own doc comment;
// this is the single most-tapped row shape in the app (every Home/Bank hub/Menu
// list uses it) and had zero press feedback before this.
@Composable
private fun FlatSectionRow(row: FlatRow) {
    val onClick = row.onClick
    val interactionSource = remember { MutableInteractionSource() }
    val pressScale = if (onClick != null) rememberPressScale(interactionSource) else 1f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(pressScale)
            .then(if (onClick != null) Modifier.clickable(interactionSource = interactionSource, indication = LocalIndication.current, onClick = onClick) else Modifier)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            if (row.glyph != null) {
                Box(modifier = Modifier.size(34.dp), contentAlignment = Alignment.Center) { row.glyph.invoke() }
                Spacer(modifier = Modifier.width(14.dp))
            } else if (row.icon != null) {
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
            Icon(IdsIcons.ChevronRight, contentDescription = null, tint = Ids.colors.textTertiary)
        }
    }
}


// Real fix (2026-08-10): this was pure decoration -- a Box with static text, no
// TextField, nothing typed into it ever did anything. Worse than no search bar at
// all: it promised a feature that wasn't there. Now backed by real state (see
// MenuScreen's own menuSearchQuery) that filters every FlatSection row by title --
// the ~17-category, 60+-row menu below this bar is a "find X" problem as much as a
// "browse by category" one.
@Composable
private fun SearchBar(query: String, onQueryChange: (String) -> Unit, placeholder: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Ids.colors.surfaceSoft)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Icon(IdsIcons.Search, contentDescription = null, tint = Ids.colors.textTertiary, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(placeholder, color = Ids.colors.textSecondary, fontSize = 16.sp)
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(color = Ids.colors.textPrimary, fontSize = 16.sp),
                cursorBrush = SolidColor(Ids.colors.brand),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(22.dp)) {
                Icon(IdsIcons.Close, contentDescription = "Clear search", tint = Ids.colors.textTertiary, modifier = Modifier.size(16.dp))
            }
        }
    }
}

// BackTopBar relocated 2026-07-23 to core/designsystem/components/HoodShared.kt while
// extracting Community into :features:community:impl -- every call site across :app
// now imports it from there instead.

// CORRECTED 2026-08-12: an earlier pass here claimed "a text navbar ('ID | Support |
// Settings') is a website convention with no equivalent anywhere in real Toss" and
// replaced it with a bold username + single gear icon -- that claim was wrong,
// contradicted directly by a real user-provided screenshot of the actual Toss app's
// own All-tab header, which is exactly a 3-link text row: "Authentication | Help |
// Settings" (not "ID | Support | Settings" -- close but not the real labels either).
// No bold username shown on this specific screen in the real screenshot (that
// personalization lives on Home's own switcher header instead, a different real
// screenshot from the same batch, not duplicated here).
@Composable
private fun AllTopBar(onOpenAuthentication: () -> Unit = {}, onOpenHelp: () -> Unit = {}, onOpenSettings: () -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Authentication",
            color = Ids.colors.textSecondary,
            fontSize = 15.sp,
            modifier = Modifier.pressScaleClickable(onClick = onOpenAuthentication),
        )
        androidx.compose.material3.VerticalDivider(
            modifier = Modifier.padding(horizontal = 10.dp).height(14.dp),
            color = Ids.colors.textTertiary,
        )
        Text(
            "Help",
            color = Ids.colors.textSecondary,
            fontSize = 15.sp,
            modifier = Modifier.pressScaleClickable(onClick = onOpenHelp),
        )
        androidx.compose.material3.VerticalDivider(
            modifier = Modifier.padding(horizontal = 10.dp).height(14.dp),
            color = Ids.colors.textTertiary,
        )
        Text(
            "Settings",
            color = Ids.colors.textSecondary,
            fontSize = 15.sp,
            modifier = Modifier.pressScaleClickable(onClick = onOpenSettings),
        )
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
                        modifier = Modifier.weight(1f).pressScaleClickable { onItemClick(label) },
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
