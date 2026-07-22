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
import androidx.compose.material.icons.outlined.ArrowBackIosNew
import androidx.compose.material.icons.automirrored.outlined.Comment
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Inbox
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
import rw.itunda.feature.shop.impl.CommerceShopContent
import rw.itunda.feature.eats.impl.EatsContent
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

private enum class HoodMode { MARKETPLACE, COMMUNITY, JOBS, PROPERTY }

// Real 당근-style neighborhood-services hub (2026-07-19) -- Marketplace, Community
// (동네생활), Jobs (당근알바), and Property (당근부동산) all fold into this one tab
// via a segmented toggle, matching the exact "no free bottom-nav slot, fold into an
// existing tab" pattern ShopTab's own Shop/Eats toggle already established.
@Composable
internal fun HoodTab(onMessageSeller: (String) -> Unit) {
    var mode by remember { mutableStateOf(HoodMode.MARKETPLACE) }
    var neighborhoodName by remember { mutableStateOf<String?>(null) }
    var neighborhoodVerificationCount by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        try {
            val user = NetworkClient.authApi.getProfile().user
            neighborhoodName = user.neighborhood
            neighborhoodVerificationCount = user.neighborhoodVerificationCount
        } catch (_: Exception) {
            // The individual Hood services retain their own usable neighborhood setup
            // prompt; this shared context label is deliberately best-effort.
        }
    }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Ids.layout.screenHorizontal, vertical = 8.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(TossCardSoft)
                .padding(4.dp),
        ) {
            listOf(
                HoodMode.MARKETPLACE to "Market", HoodMode.COMMUNITY to "Life",
                HoodMode.JOBS to "Jobs", HoodMode.PROPERTY to "Home",
            ).forEach { (m, label) ->
                val selected = m == mode
                Text(
                    label,
                    color = if (selected) Color.White else TossSecondary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) TossBlue else Color.Transparent)
                        .clickable { mode = m }
                        .padding(vertical = 8.dp),
                )
            }
        }
        neighborhoodName?.let { neighborhood ->
            Text(
                "📍 Near $neighborhood" + if (neighborhoodVerificationCount > 0) " · confirmed ${neighborhoodVerificationCount}×" else "",
                color = TossSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = Ids.layout.screenHorizontal, vertical = 2.dp),
            )
        }
        when (mode) {
            // Real proof-of-slice Feature extraction (2026-07-22/23) -- Marketplace now
            // lives in :features:marketplace:impl, the first Hood-mode section pulled out
            // of this file to match Toss's real Microfeatures architecture. routeMiniMap
            // is passed in because a real drawn road route needs MapLibre + :app's own
            // BuildConfig.TILES_BASE_URL (RouteMiniMap.kt below), out of scope for this
            // slice -- see MarketplaceScreen.kt's own header comment for the full account.
            HoodMode.MARKETPLACE -> MarketplaceContent(
                onMessageSeller = onMessageSeller,
                routeMiniMap = { fromLat, fromLng, toLat, toLng, fromLabel, toLabel ->
                    RouteMiniMap(fromLat, fromLng, toLat, toLng, fromLabel, toLabel)
                },
            )
            // Fourth Feature extraction (2026-07-23), same pattern -- no routeMiniMap
            // needed since Community has no lat/lng "directions" feature.
            HoodMode.COMMUNITY -> CommunityContent()
            // Second Feature extraction (2026-07-23), same pattern as Marketplace above.
            HoodMode.JOBS -> JobsContent(
                onMessagePoster = onMessageSeller,
                routeMiniMap = { fromLat, fromLng, toLat, toLng, fromLabel, toLabel ->
                    RouteMiniMap(fromLat, fromLng, toLat, toLng, fromLabel, toLabel)
                },
            )
            // Third Feature extraction (2026-07-23), same pattern as Marketplace/Jobs above.
            HoodMode.PROPERTY -> PropertyContent(
                onMessageLister = onMessageSeller,
                routeMiniMap = { fromLat, fromLng, toLat, toLng, fromLabel, toLabel ->
                    RouteMiniMap(fromLat, fromLng, toLat, toLng, fromLabel, toLabel)
                },
            )
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

// ============================== SHOP (Commerce + Eats) ==============================

private enum class ShopMode { SHOP, EATS }

@Composable
internal fun ShopTab() {
    var mode by remember { mutableStateOf(ShopMode.SHOP) }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Ids.layout.screenHorizontal, vertical = 8.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(TossCardSoft)
                .padding(4.dp),
        ) {
            listOf(ShopMode.SHOP to "Shop", ShopMode.EATS to "Eats").forEach { (m, label) ->
                val selected = m == mode
                Text(
                    label,
                    color = if (selected) Color.White else TossSecondary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) TossBlue else Color.Transparent)
                        .clickable { mode = m }
                        .padding(vertical = 8.dp),
                )
            }
        }
        when (mode) {
            // Fifth Feature extraction (2026-07-23) -- see ShopScreen.kt's own header
            // comment for why deviceStepUpHost is injected rather than owned directly
            // (DeviceStepUpHost.kt wraps :features:payments:impl's dialog, so it can't
            // become a dependency of :features:shop:impl without a forbidden sideways
            // Feature-to-Feature dependency).
            ShopMode.SHOP -> CommerceShopContent(
                deviceStepUpHost = { visible, onDismiss, onVerified ->
                    DeviceStepUpHost(visible = visible, onDismiss = onDismiss, onVerified = onVerified)
                },
            )
            // Sixth Feature extraction (2026-07-23) -- folds Deliver (rider role) in too;
            // see EatsScreen.kt's own header comment for why they share one module and
            // for the routeMiniMap/deviceStepUpHost injection reasoning.
            ShopMode.EATS -> EatsContent(
                routeMiniMap = { fromLat, fromLng, toLat, toLng, fromLabel, toLabel ->
                    RouteMiniMap(fromLat, fromLng, toLat, toLng, fromLabel, toLabel)
                },
                deviceStepUpHost = { visible, onDismiss, onVerified ->
                    DeviceStepUpHost(visible = visible, onDismiss = onDismiss, onVerified = onVerified)
                },
            )
        }
    }
}

// Commerce (CommerceView/CommerceShopContent/CartFab/ProductImageThumb/ProductPriceRow/
// MerchantDetailView/QtyButton/MultiCartView/MultiCartResultsView/CommerceOrderRow/
// MyCommerceOrdersView/ProductRatingBadge/ProductReviewRow/OrderItemReviews) moved
// 2026-07-23 to :features:shop:impl, same pattern as the Hood-mode extractions above.
// Eats + Deliver (EatsMode/OrderFoodContent/FavoriteRestaurantsView/RestaurantRatingBadge/
// ReviewOrderCard/RestaurantMenuView/AddressAutocompleteField/EatsCheckoutView/
// EatsOrderConfirmationView/EatsOrderRow/MyEatsOrdersView/ReorderButton/DeliverContent)
// moved 2026-07-23 to :features:eats:impl -- both in one module since Deliver was
// already tightly coupled to Eats' own types, see EatsScreen.kt's own header comment.

