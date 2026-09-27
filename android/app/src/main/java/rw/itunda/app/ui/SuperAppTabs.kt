package rw.itunda.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import rw.itunda.core.designsystem.components.pressScaleClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
// Real 2-column image-led product grid (2026-07-21) -- closes
// docs/DESIGN_REFERENCES.md Section 5 recommendation #5.
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import coil.compose.AsyncImage
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.AddReaction
import androidx.compose.material.icons.automirrored.outlined.Comment
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.RateReview
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.WebSocket
import retrofit2.HttpException
import rw.itunda.core.network.isDeviceNotVerifiedError
import rw.itunda.core.network.AddGroupMemberRequest
import rw.itunda.core.network.CreateSplitBillRequest
import rw.itunda.core.network.SplitBillWithParticipants
import rw.itunda.core.network.AddCommunityCommentRequest
import rw.itunda.core.network.CommunityCategoryDto
import rw.itunda.core.network.CommunityCommentWithAuthorDto
import rw.itunda.core.network.CommunityPostDto
import rw.itunda.core.network.ConversationSummaryDto
import rw.itunda.core.network.CreateCommunityPostRequest
import rw.itunda.core.network.CreateChatReportRequest
import rw.itunda.core.network.CreateJobPostRequest
import rw.itunda.core.network.CreateListingRequest
import rw.itunda.core.network.CreatePropertyListingRequest
import rw.itunda.core.network.JobCategoryDto
import rw.itunda.core.network.JobPostDto
import rw.itunda.core.network.PropertyListingDto
import rw.itunda.core.network.PropertyTypeDto
import rw.itunda.core.network.EatsOrderDto
import rw.itunda.core.network.FavoriteListingDto
import rw.itunda.core.network.FavoriteJobPostDto
import rw.itunda.core.network.FavoritePropertyListingDto
import rw.itunda.core.network.FavoriteRestaurantDto
import rw.itunda.core.network.AddressSuggestionDto
import rw.itunda.core.network.CreateGroupRequest
import rw.itunda.core.network.CreateHoodReportRequest
import rw.itunda.core.network.EatsRatingResponse
import rw.itunda.core.network.GroupMemberDto
import rw.itunda.core.network.GroupMessageDto
import rw.itunda.core.network.GroupSummaryDto
import rw.itunda.core.network.MessagingSocketPush
import rw.itunda.core.network.SendGroupMessageRequest
import rw.itunda.core.network.SubmitEatsReviewRequest
import rw.itunda.core.network.EatsOrderItemRequest
import rw.itunda.core.network.GiftDto
import rw.itunda.core.network.SendGiftInConversationRequest
import rw.itunda.core.network.ListingDto
import rw.itunda.core.network.MakeOfferRequest
import rw.itunda.core.network.SetNeighborhoodRequest
import rw.itunda.core.network.SetConversationQuietRequest
import rw.itunda.core.network.MerchantProductDto
import rw.itunda.core.network.MessageDto
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.OrderDto
import rw.itunda.core.network.OrderItemDto
import rw.itunda.core.network.OrderItemRequest
import rw.itunda.core.network.PlaceEatsOrderRequest
import rw.itunda.core.network.PlaceOrderRequest
import rw.itunda.core.network.ProductRatingResponse
import rw.itunda.core.network.ProductReviewDto
import rw.itunda.core.network.SubmitProductReviewRequest
import rw.itunda.core.network.MakePropertyOfferRequest
import rw.itunda.core.network.PriceOfferDto
import rw.itunda.core.network.PropertyPriceOfferDto
import rw.itunda.core.network.RespondToPropertyOfferRequest
import rw.itunda.core.network.ReactionGroupDto
import rw.itunda.core.network.RespondToOfferRequest
import rw.itunda.core.network.RiderDto
import rw.itunda.core.network.SendMessageRequest
import rw.itunda.core.network.SetRiderAvailabilityRequest
import rw.itunda.core.network.ShoppingMerchantDto
import rw.itunda.core.network.StartConversationRequest
import rw.itunda.core.network.TokenStore
import rw.itunda.core.network.ToggleReactionRequest
import rw.itunda.core.network.TalkContactDto
import rw.itunda.core.network.UpdateEatsOrderStatusRequest
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.designsystem.theme.IdsIcons
import rw.itunda.core.designsystem.components.BackTopBar
import rw.itunda.core.designsystem.components.EmptyState
import rw.itunda.core.designsystem.components.ErrorCard
import rw.itunda.core.designsystem.components.HoodReportAction
import rw.itunda.core.designsystem.components.ListingActionButton
import rw.itunda.core.designsystem.components.NeighborhoodSetupPrompt
import rw.itunda.core.designsystem.components.QtyButton
import rw.itunda.core.designsystem.components.SearchAndCategoryChips
import rw.itunda.core.designsystem.components.SkeletonBlock
import rw.itunda.core.designsystem.components.StarGold
import rw.itunda.core.designsystem.components.StarRatingRow
import rw.itunda.core.designsystem.components.TabHeader
import rw.itunda.core.designsystem.components.chatMessageTime
import rw.itunda.core.designsystem.components.relativeTimeAgo
import rw.itunda.core.designsystem.components.rememberRealLocationRequester
import rw.itunda.core.network.superAppErrorMessage
import rw.itunda.feature.marketplace.impl.MarketplaceContent
import rw.itunda.feature.jobs.impl.JobsContent
import rw.itunda.feature.property.impl.PropertyContent
import rw.itunda.feature.community.impl.CommunityContent
import java.io.IOException
import java.time.Instant
import java.util.UUID

/**
 * Real Talk (Kakao-style 1:1 messaging), Hood (당근마켓-style neighborhood
 * marketplace), and Shop (Coupang-style multi-item checkout) tabs -- the mobile
 * clients for the three new "super app" phase backends (rw.itunda.messaging,
 * rw.itunda.marketplace, rw.itunda.commerce) built earlier this session, replacing
 * the old Home/Benefits/Shop/Pay/All bottom nav's Benefits/Shop/Pay tabs with
 * Shop/Hood/Talk (2026-07-18). Kept in their own file, not ItundaAppScreen.kt, since
 * that file was already large before these three real, full features were added.
 *
 * Deliberately local Composable state (`remember`, refetched on every tab entry via
 * `LaunchedEffect(Unit)`), not new MainViewModel StateFlows -- matches the existing
 * on-demand-fetch precedent SettingsScreen/loadSettingsData() already established,
 * rather than bloating MainViewModel's single shared fetchData() with three new
 * unrelated feature areas. Refetching on every tab entry is also the *correct* UX for
 * a chat/marketplace/shop surface (freshness matters more here than for Home's mostly-
 * static balance), not merely a shortcut.
 */

// SkeletonBlock/EmptyState/ErrorCard/TabHeader relocated 2026-07-22 to
// core/designsystem/components/HoodShared.kt while extracting Marketplace into
// :features:marketplace:impl -- these are true cross-feature UI atoms (Community/Jobs/
// Property below all use them too), so they now live where any Feature module can
// reach them without depending on :app. See that file's own header comment.

// SearchAndCategoryChips relocated 2026-07-23 to core/designsystem/components/
// HoodShared.kt while extracting Shop/Commerce into :features:shop:impl -- Eats
// (below) now imports the shared copy instead of a second local one.

// Talk (TalkView/TalkTab/DirectMessagesList/GroupsList/GroupRow/GroupThreadView/
// GroupSplitBillsView/GroupManageMembersView/GroupMessageBubble/ConversationRow/
// ChatThreadView/MessageReactionsRow/OfferBubble/OfferActionButton/GiftBubble/
// MessageBubble) moved 2026-07-23 to :features:talk:impl -- the seventh and final
// Feature extraction, confirming the same module-boundary pattern holds even for a
// real-time WebSocket-based section, not just REST-CRUD ones. See TalkScreen.kt's own
// header comment for the full account. Called from ItundaAppScreen.kt now, not here.

// ============================== HOOD (Marketplace) ==============================

internal enum class HoodMode { MARKETPLACE, COMMUNITY, JOBS, PROPERTY }

// Real 당근-style neighborhood-services hub (2026-07-19), given a real 당근 top bar
// and pill-chip category row 2026-08-03 (user-provided real 당근마켓 screenshots,
// light + dark) -- previously a generic Toss-style underline-tab strip, which was
// itunda's own house style bleeding into a section explicitly meant to feel like a
// different real product. Marketplace/Community (동네생활)/Jobs (당근알바)/Property
// (당근부동산) still fold into this one tab via the same chip row (no free bottom-nav
// slot for each), now styled the way Karrot's own 전체/부동산/중고거래/... row is.
// HoodTab's own real chip row (Market/Life/Jobs/Home) was retired 2026-08-10: real
// user correction, same fix applied to Shop/Eats above -- nesting all four behind one
// Explore row with an internal switcher is noise a flat catalog shouldn't have.
// Unlike Shop/Eats, though, the four sections share a genuinely rich, deliberately
// Karrot-sourced shell (neighborhood-name top bar, per-mode personal-view menu sheet,
// Marketplace's own "Write" FAB, neighborhood switcher/second-neighborhood dialogs) --
// not a simple toggle -- so that shell is kept, just parameterized by a fixed `mode`
// instead of internal switchable state, and mounted once per flat Explore
// destination (see ItundaAppScreen.kt's showMarketplace/showCommunity/showJobs/
// showProperty) instead of once behind a shared chip row.
@Composable
internal fun HoodSectionScreen(
    mode: HoodMode,
    onMessageSeller: (String) -> Unit,
    onOpenSettings: () -> Unit = {},
) {
    var neighborhoodName by remember { mutableStateOf<String?>(null) }
    var neighborhoodVerificationCount by remember { mutableStateOf(0) }
    // Real dual-neighborhood support (2026-08-04) -- see User.secondNeighborhood's own
    // doc comment on the backend.
    var secondNeighborhoodName by remember { mutableStateOf<String?>(null) }
    var showSecondNeighborhoodPrompt by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    // Bumped by the FAB below; MarketplaceContent reacts to any change by forcing its
    // own new-listing form open -- see that Composable's own doc comment on this param.
    var requestNewListingSignal by remember { mutableStateOf(0) }
    // Real fix, 2026-08-03: user correction, verified against the real daangn.com --
    // itunda had TWO stacked chip rows where real Karrot has one. My-listings/
    // Purchases/Wishlist/Alerts are personal-management views with no real chip-row
    // equivalent in any real app's main browse screen -- moved behind the hamburger
    // menu instead (the profile-icon consolidation the user chose), matching where
    // every real app puts this class of view. Signal pattern mirrors
    // requestNewListingSignal above -- MarketplaceContent reacts to the counter
    // changing, keyed with which view was requested.
    var showMenuSheet by remember { mutableStateOf(false) }
    var requestedMarketplaceView by remember { mutableStateOf(0 to "") }
    // Real fix, 2026-08-03: same "two stacked chip rows" fix as Marketplace's own,
    // generalized to Community/Jobs/Property once Community/Jobs/Property.kt were
    // found to share the exact same Browse/Near-me/Neighborhood(+personal-views) chip
    // row pattern -- see each Content composable's own doc comment. "My posts" is
    // Community's only personal-management view (no Purchases/Wishlist/Alerts
    // equivalent there).
    var requestedCommunityView by remember { mutableStateOf(0 to "") }
    var requestedJobsView by remember { mutableStateOf(0 to "") }
    var requestedPropertyView by remember { mutableStateOf(0 to "") }
    // Real fix, 2026-08-03, third correction same day: verified via search (a real
    // 당근 FAQ result) that real Karrot has no "Browse/Near me/Neighborhood" chip
    // trio at all -- "홈 화면 왼쪽 상단 동네 이름을 클릭해주세요" (tap the neighborhood
    // name at the top-left of the home screen) is the real way to change it, and the
    // default feed is already neighborhood-scoped, extending outward automatically
    // ("설정한 동네와 가까운 근처까지 게시글을 추천해줍니다"). Bumped after a real
    // neighborhood save so MarketplaceContent's own auto-detect re-runs and the feed
    // reflects the change immediately.
    var showNeighborhoodPrompt by remember { mutableStateOf(false) }
    var neighborhoodRefreshSignal by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        try {
            val user = NetworkClient.authApi.getProfile().user
            neighborhoodName = user.neighborhood
            neighborhoodVerificationCount = user.neighborhoodVerificationCount
            secondNeighborhoodName = user.secondNeighborhood
        } catch (_: Exception) {
            // The individual Hood services retain their own usable neighborhood setup
            // prompt; this shared context label is deliberately best-effort.
        }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Real 당근마켓 top bar (2026-08-03): a location pin + the user's real
            // neighborhood name in bold (their actual, most-specific 동네, matching
            // Karrot's own primary/secondary neighborhood pair when a second one is
            // set), then search/notifications/menu icons on the right. Neither
            // itunda's other tabs nor Karrot itself repeat a section title here (the
            // bottom nav already says "Hood") -- this bar's whole job is "where am I,
            // geographically," which no other itunda tab needs.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Ids.layout.screenHorizontal, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Real fix, 2026-08-03 -- see showNeighborhoodPrompt's own doc comment:
                // this is now the real, verified way to open the neighborhood
                // switcher, not a chip buried in a filter row.
                // Real WCAG AA fix, item 242 (docs/ACCESSIBILITY.md's original §2 icon-
                // only-interactive-element finding, re-found here in newer code): the
                // icon and the text below used to carry two separate, independent
                // .clickable modifiers, making this icon its own distinct, unlabeled
                // (contentDescription = null) clickable accessibility node -- TalkBack
                // would announce it as a bare, unnamed "Button" -- and left a real tap
                // dead-zone between icon and text where neither clickable region
                // covered. Wrapped icon+text in their own Row with one shared
                // .clickable (scoped narrowly to just these two -- the outer Row also
                // holds the unrelated Search/Notifications/Menu icons as later
                // siblings, so the single clickable couldn't just move onto it) so the
                // icon merges into one accessible node named by the visible text, and
                // the gap between them is now part of the same continuous tap target.
                Row(
                    modifier = Modifier.weight(1f).pressScaleClickable { showNeighborhoodPrompt = true },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.LocationOn, contentDescription = null,
                        modifier = Modifier.size(20.dp), tint = Ids.colors.textPrimary,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        // Real dual-neighborhood display (2026-08-04) -- Karrot's own real
                        // primary/secondary neighborhood pair, shown together once both are set.
                        listOfNotNull(neighborhoodName, secondNeighborhoodName).joinToString(" · ").ifBlank { "Set your neighborhood" },
                        color = Ids.colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                if (neighborhoodVerificationCount > 0) {
                    Text(
                        "confirmed ${neighborhoodVerificationCount}×",
                        color = Ids.colors.textTertiary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
                // Real fix, 2026-08-03: all 3 of these were purely decorative, no
                // onClick at all -- the same "dead tap" class of bug found and fixed
                // repeatedly elsewhere this session (Home's old "See all", the All
                // screen's duplicate rows). Search stays undocumented/unwired
                // honestly: there's no real keyword-search endpoint for listings on
                // the backend yet (grepped services/backend/marketplace -- confirmed
                // absent), and this app doesn't fake search results. Bell routes to
                // the real, already-built SettingsScreen (itunda has one consolidated
                // notifications+settings screen). Menu opens a real dropdown --
                // consolidated Settings + (in Market mode) My listings/Purchases/
                // Wishlist/Alerts, the personal-management views this same-day fix
                // moved off the second chip row.
                Icon(IdsIcons.Search, contentDescription = "Search", modifier = Modifier.size(24.dp), tint = Ids.colors.textPrimary)
                Spacer(modifier = Modifier.width(16.dp))
                Icon(
                    IdsIcons.Bell, contentDescription = "Notifications",
                    modifier = Modifier.size(24.dp).pressScaleClickable(onClick = onOpenSettings), tint = Ids.colors.textPrimary,
                )
                Spacer(modifier = Modifier.width(16.dp))
                Icon(
                    Icons.Outlined.Menu, contentDescription = "Menu",
                    modifier = Modifier.size(24.dp).pressScaleClickable { showMenuSheet = true }, tint = Ids.colors.textPrimary,
                )
            }
            when (mode) {
                // Real proof-of-slice Feature extraction (2026-07-22/23) -- Marketplace now
                // lives in :features:marketplace:impl, the first Hood-mode section pulled out
                // of this file to match Toss's real Microfeatures architecture.
                HoodMode.MARKETPLACE -> MarketplaceContent(
                    onMessageSeller = onMessageSeller,
                    requestNewListingSignal = requestNewListingSignal,
                    requestedView = requestedMarketplaceView,
                    neighborhoodRefreshSignal = neighborhoodRefreshSignal,
                )
                // Fourth Feature extraction (2026-07-23), same pattern. onOpenGroupChat
                // reuses the same onMessageSeller callback (2026-07-24) -- see
                // TalkScreen.kt's own doc comment on why a real GroupConversation id works
                // through the exact same hand-off Marketplace/Jobs/Property already share.
                HoodMode.COMMUNITY -> CommunityContent(
                    onOpenGroupChat = onMessageSeller,
                    requestedView = requestedCommunityView,
                    neighborhoodRefreshSignal = neighborhoodRefreshSignal,
                )
                // Second Feature extraction (2026-07-23), same pattern as Marketplace above.
                HoodMode.JOBS -> JobsContent(
                    onMessagePoster = onMessageSeller,
                    requestedView = requestedJobsView,
                    neighborhoodRefreshSignal = neighborhoodRefreshSignal,
                )
                // Third Feature extraction (2026-07-23), same pattern as Marketplace/Jobs above.
                HoodMode.PROPERTY -> PropertyContent(
                    onMessageLister = onMessageSeller,
                    requestedView = requestedPropertyView,
                    neighborhoodRefreshSignal = neighborhoodRefreshSignal,
                )
            }
        }
        // Real 당근 글쓰기 FAB layout (2026-08-03): a floating pill, always present
        // regardless of which chip is selected, matching the real reference
        // screenshots' position/shape exactly (bottom-end, "+" + label). Color is
        // itunda's own Ids.colors.brand, not Karrot's real orange -- user correction,
        // same day: "themes/interactions... we use what itunda already [has]," i.e.
        // adopt the UI/UX (layout, shape) from a reference, not its color identity.
        // Currently wired to Marketplace's own new-listing form (the mode these
        // reference screenshots are literally of); Life/Jobs/Home's own "new post"
        // entry points stay as their existing in-content buttons rather than this FAB
        // silently doing nothing when tapped from a mode it can't act on yet.
        if (mode == HoodMode.MARKETPLACE) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp)
                    .clip(RoundedCornerShape(Ids.layout.chipCornerRadius))
                    .background(Ids.colors.brand)
                    .pressScaleClickable { requestNewListingSignal++ }
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(IdsIcons.Add, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Write", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
        // Real hamburger dropdown (2026-08-03) -- see this Composable's own
        // showMenuSheet doc comment. A plain dropdown anchored under the Menu icon,
        // not a full bottom sheet -- this is a short, non-draggable action list
        // (matching Android's own standard overflow-menu convention), not the
        // Maps-style peek/half/full sheet IdsBottomSheetOverlay is built for.
        if (showMenuSheet) {
            BackHandler { showMenuSheet = false }
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.32f)).clickable { showMenuSheet = false },
            ) {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 64.dp, end = Ids.layout.screenHorizontal)
                        .width(220.dp)
                        .clip(RoundedCornerShape(Ids.layout.sectionCornerRadius))
                        .background(Ids.colors.surface)
                        .padding(vertical = 8.dp),
                ) {
                    if (mode == HoodMode.MARKETPLACE) {
                        listOf("My listings" to "MINE", "Purchases" to "PURCHASES", "Wishlist" to "WISHLIST", "Alerts" to "ALERTS", "Hidden listings" to "HIDDEN").forEach { (label, key) ->
                            Text(
                                label,
                                color = Ids.colors.textPrimary,
                                fontSize = 15.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .pressScaleClickable {
                                        requestedMarketplaceView = (requestedMarketplaceView.first + 1) to key
                                        showMenuSheet = false
                                    }
                                    .padding(horizontal = 18.dp, vertical = 12.dp),
                            )
                        }
                        Divider(color = Ids.colors.divider, modifier = Modifier.padding(vertical = 4.dp))
                    }
                    if (mode == HoodMode.COMMUNITY) {
                        Text(
                            "My posts",
                            color = Ids.colors.textPrimary,
                            fontSize = 15.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .pressScaleClickable {
                                    requestedCommunityView = (requestedCommunityView.first + 1) to "MINE"
                                    showMenuSheet = false
                                }
                                .padding(horizontal = 18.dp, vertical = 12.dp),
                        )
                        Divider(color = Ids.colors.divider, modifier = Modifier.padding(vertical = 4.dp))
                    }
                    if (mode == HoodMode.JOBS) {
                        listOf("My posts" to "MINE", "Jobs I did" to "WORKED", "My applications" to "APPLICATIONS", "Saved" to "SAVED", "My résumé" to "RESUME").forEach { (label, key) ->
                            Text(
                                label,
                                color = Ids.colors.textPrimary,
                                fontSize = 15.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .pressScaleClickable {
                                        requestedJobsView = (requestedJobsView.first + 1) to key
                                        showMenuSheet = false
                                    }
                                    .padding(horizontal = 18.dp, vertical = 12.dp),
                            )
                        }
                        Divider(color = Ids.colors.divider, modifier = Modifier.padding(vertical = 4.dp))
                    }
                    if (mode == HoodMode.PROPERTY) {
                        listOf("My listings" to "MINE", "Places I got" to "ACQUIRED", "Saved" to "SAVED", "시세 Value" to "VALUATION").forEach { (label, key) ->
                            Text(
                                label,
                                color = Ids.colors.textPrimary,
                                fontSize = 15.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .pressScaleClickable {
                                        requestedPropertyView = (requestedPropertyView.first + 1) to key
                                        showMenuSheet = false
                                    }
                                    .padding(horizontal = 18.dp, vertical = 12.dp),
                            )
                        }
                        Divider(color = Ids.colors.divider, modifier = Modifier.padding(vertical = 4.dp))
                    }
                    Text(
                        "Settings",
                        color = Ids.colors.textPrimary,
                        fontSize = 15.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .pressScaleClickable { showMenuSheet = false; onOpenSettings() }
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                    )
                }
            }
        }
        // Real neighborhood switcher (2026-08-03) -- see showNeighborhoodPrompt's
        // own doc comment. Reuses the real, already-built NeighborhoodSetupPrompt
        // (live GPS + reverse-geocode) rather than inventing a new flow.
        if (showNeighborhoodPrompt) {
            BackHandler { showNeighborhoodPrompt = false }
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.32f)).clickable { showNeighborhoodPrompt = false },
                contentAlignment = Alignment.Center,
            ) {
                Box(modifier = Modifier.padding(horizontal = Ids.layout.screenHorizontal).clickable(enabled = false) {}) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        NeighborhoodSetupPrompt(onDone = { name ->
                            neighborhoodName = name
                            showNeighborhoodPrompt = false
                            neighborhoodRefreshSignal++
                        })
                        // Real dual-neighborhood support (2026-08-04) -- Karrot's own real
                        // second-neighborhood mechanic (e.g. home + workplace), surfaced
                        // right alongside the primary setup rather than buried elsewhere.
                        Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = Ids.colors.surface), modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    secondNeighborhoodName?.let { "Second: $it" } ?: "Add a second neighborhood",
                                    color = Ids.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(
                                        if (secondNeighborhoodName != null) "Change" else "Add",
                                        color = Ids.colors.brand, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                                        modifier = Modifier.pressScaleClickable { showSecondNeighborhoodPrompt = true },
                                    )
                                    if (secondNeighborhoodName != null) {
                                        Text(
                                            "Remove",
                                            color = Ids.colors.danger, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                                            modifier = Modifier.pressScaleClickable {
                                                coroutineScope.launch {
                                                    try {
                                                        val res = NetworkClient.authApi.clearSecondNeighborhood()
                                                        secondNeighborhoodName = res.user.secondNeighborhood
                                                        neighborhoodRefreshSignal++
                                                    } catch (_: Exception) {
                                                        // Best-effort -- the switcher stays open so the user can retry.
                                                    }
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (showSecondNeighborhoodPrompt) {
            BackHandler { showSecondNeighborhoodPrompt = false }
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.32f)).clickable { showSecondNeighborhoodPrompt = false },
                contentAlignment = Alignment.Center,
            ) {
                Box(modifier = Modifier.padding(horizontal = Ids.layout.screenHorizontal).clickable(enabled = false) {}) {
                    NeighborhoodSetupPrompt(isSecond = true, onDone = { name ->
                        secondNeighborhoodName = name
                        showSecondNeighborhoodPrompt = false
                        neighborhoodRefreshSignal++
                    })
                }
            }
        }
    }
}

// Marketplace (HoodView/NeighborhoodSetupPrompt/MarketplaceContent/ListingWishlistView/
// NewListingForm/ListingCard/ListingActionButton/HoodReportAction/relativeTimeAgo) moved
// 2026-07-22/23 to :features:marketplace:impl and core/designsystem/components/
// HoodShared.kt -- see MarketplaceScreen.kt's own header comment.
// Jobs (JobsView/JobsContent/JobPostWishlistView/NewJobPostForm/JobPostCard) moved
// 2026-07-23 to :features:jobs:impl, same pattern as Marketplace above.
// Property (PropertyView/PropertyContent/PropertyWishlistView/NewPropertyListingForm/
// PropertyListingCard) moved 2026-07-23 to :features:property:impl, same pattern.
// Community (CommunityView/CommunityContent/NewCommunityPostForm/CommunityPostCard/
// CommunityPostDetailScreen) moved 2026-07-23 to :features:community:impl, same pattern
// -- all four Hood-mode sections are now Feature modules.

// ShopTab (the Shop/Eats segmented-toggle wrapper around CommerceShopContent/
// EatsContent) was retired 2026-08-10: real user correction -- nesting Shop/Eats
// behind one Explore row with its own internal toggle is a tab bar inside a tab,
// noise a flat catalog shouldn't have. ItundaAppScreen.kt now calls
// CommerceShopContent/EatsContent directly as two separate flat Explore rows.

// Commerce (CommerceView/CommerceShopContent/CartFab/ProductImageThumb/ProductPriceRow/
// MerchantDetailView/QtyButton/MultiCartView/MultiCartResultsView/CommerceOrderRow/
// MyCommerceOrdersView/ProductRatingBadge/ProductReviewRow/OrderItemReviews) moved
// 2026-07-23 to :features:shop:impl, same pattern as the Hood-mode extractions above.
// Eats + Deliver (EatsMode/OrderFoodContent/FavoriteRestaurantsView/RestaurantRatingBadge/
// ReviewOrderCard/RestaurantMenuView/AddressAutocompleteField/EatsCheckoutView/
// EatsOrderConfirmationView/EatsOrderRow/MyEatsOrdersView/ReorderButton/DeliverContent)
// moved 2026-07-23 to :features:eats:impl -- both in one module since Deliver was
// already tightly coupled to Eats' own types, see EatsScreen.kt's own header comment.

