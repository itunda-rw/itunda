package rw.itunda.feature.eats.impl

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import rw.itunda.core.designsystem.components.pressScaleClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Coffee
import androidx.compose.material.icons.outlined.Fastfood
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.RateReview
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import retrofit2.HttpException
import rw.itunda.core.designsystem.components.BackTopBar
import rw.itunda.core.designsystem.components.EmptyState
import rw.itunda.core.designsystem.components.ErrorCard
import rw.itunda.core.designsystem.components.StatusBadge
import rw.itunda.core.designsystem.components.IdsTextField
import rw.itunda.core.designsystem.components.ListingActionButton
import rw.itunda.core.designsystem.components.LiveRiderMiniMap
import rw.itunda.core.designsystem.components.QtyButton
import rw.itunda.core.designsystem.components.RouteMiniMap
import rw.itunda.core.designsystem.components.rememberRealLocationRequester
import rw.itunda.core.designsystem.components.SearchAndCategoryChips
import rw.itunda.core.designsystem.components.SkeletonBlock
import rw.itunda.core.designsystem.components.StarGold
import rw.itunda.core.designsystem.components.StarRatingRow
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.AddressSuggestionDto
import rw.itunda.core.network.DineInOrderDto
import rw.itunda.core.network.DineInOrderItemRequest
import rw.itunda.core.network.EATS_MEMBERSHIP_TIERS
import rw.itunda.core.network.PLATFORM_MEMBERSHIP_TIERS
import rw.itunda.core.network.PlatformMembershipDto
import rw.itunda.core.network.SubscribePlatformMembershipRequest
import rw.itunda.core.network.EatsDishDto
import rw.itunda.core.network.EatsMembershipDto
import rw.itunda.core.network.EatsOrderDto
import rw.itunda.core.network.EatsOrderItemRequest
import rw.itunda.core.network.EatsRatingResponse
import rw.itunda.core.network.EatsReviewDto
import rw.itunda.core.network.FavoriteRestaurantDto
import rw.itunda.core.network.SubscribeEatsMembershipRequest
import rw.itunda.core.network.MerchantProductDto
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.PlaceDineInOrderRequest
import rw.itunda.core.network.PlaceEatsOrderRequest
import rw.itunda.core.network.RiderDto
import rw.itunda.core.network.SetRiderAvailabilityRequest
import rw.itunda.core.network.ShoppingMerchantDto
import rw.itunda.core.network.SubmitEatsReviewRequest
import rw.itunda.core.network.UpdateEatsOrderStatusRequest
import rw.itunda.core.network.isDeviceNotVerifiedError
import rw.itunda.core.network.superAppErrorMessage
import java.io.IOException
import java.util.UUID

// Extracted from EatsScreen.kt (2026-08-19) -- continuing the same 'is this
// module boundary actually coherent' audit ShopScreen.kt/TalkScreen.kt already
// got (docs/ARCHITECTURE_GUIDELINES.md §2). DeliverContent is a whole separate
// "Deliver" tab (EatsMode.DELIVER), fully self-contained -- confirmed via grep
// it's called only from EatsContent (staying in EatsScreen.kt), which is the only
// reason it changed from private to internal. Kept the full, already-working
// import list rather than trim it -- see TalkSplitBills.kt's own doc comment
// (docs/DESIGN_REFERENCES.md Section 208) for why that's the safer default now.

@Composable
internal fun DeliverContent() {
    var rider by remember { mutableStateOf<RiderDto?>(null) }
    var loadedRider by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var registering by remember { mutableStateOf(false) }
    var available by remember { mutableStateOf<List<EatsOrderDto>?>(null) }
    var mine by remember { mutableStateOf<List<EatsOrderDto>?>(null) }
    var busyOrderId by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    fun loadRider() {
        coroutineScope.launch {
            try {
                val res = NetworkClient.apiService.getMyRiderProfile()
                if (res.success) rider = res.rider
                error = null
            } catch (e: HttpException) {
                if (e.code() == 404) {
                    rider = null
                } else {
                    error = superAppErrorMessage(e)
                }
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                loadedRider = true
            }
        }
    }
    LaunchedEffect(Unit) { loadRider() }

    suspend fun loadDeliveries() {
        try {
            val a = NetworkClient.apiService.getAvailableDeliveries()
            val m = NetworkClient.apiService.getRiderDeliveries()
            if (a.success) available = a.orders
            if (m.success) mine = m.orders
        } catch (_: Exception) {
            // Keep showing the last-known lists on a transient poll failure.
        }
    }
    LaunchedEffect(rider?.id) {
        if (rider == null) return@LaunchedEffect
        while (true) {
            loadDeliveries()
            delay(4000)
        }
    }

    if (!loadedRider) {
        SkeletonBlock()
        return
    }

    val currentRider = rider
    if (currentRider == null) {
        // Real fix (2026-08-24, flat-design sweep): dropped the Card wrapper --
        // this screen's own main content (docs/UI_UX_GUIDELINES.md §10).
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Deliver with Itunda", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Earn a real delivery fee for every order you deliver, paid straight to your account.",
                    color = Ids.colors.textSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(Ids.colors.brand)
                        .pressScaleClickable(enabled = !registering) {
                            registering = true
                            error = null
                            coroutineScope.launch {
                                try {
                                    val res = NetworkClient.apiService.registerRider()
                                    if (res.success) rider = res.rider
                                } catch (e: HttpException) {
                                    // Real Toss-style resolution (2026-08-10), matching
                                    // riderapp's own BecomeRiderScreen fix: registerRider's
                                    // only real 409 is RiderAlreadyRegisteredException --
                                    // the account genuinely IS already a rider, so load
                                    // their real profile and move forward instead of
                                    // showing an error for something that isn't actually
                                    // wrong.
                                    if (e.code() == 409) {
                                        loadRider()
                                    } else {
                                        error = superAppErrorMessage(e)
                                    }
                                } finally {
                                    registering = false
                                }
                            }
                        }
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                ) { Text(if (registering) "Registering…" else "Become a rider", color = Color.White, fontWeight = FontWeight.Bold) }
                error?.let { Text(it, color = Ids.colors.danger, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp)) }
        }
        return
    }

    val activeDeliveries = mine.orEmpty().filter { it.status != "DELIVERED" }
    val pastDeliveries = mine.orEmpty().filter { it.status == "DELIVERED" }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(Ids.layout.cardGap), contentPadding = PaddingValues(bottom = 20.dp)) {
        // Real fix (2026-08-24, flat-design sweep): dropped the Card wrapper -- a
        // lone status section (docs/UI_UX_GUIDELINES.md §10).
        item {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(if (currentRider.available) "You're online" else "You're offline", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(if (currentRider.available) "Visible for new deliveries" else "Go online to see deliveries", color = Ids.colors.textSecondary, fontSize = 12.sp)
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (currentRider.available) Ids.colors.danger else Ids.colors.brand)
                        .pressScaleClickable {
                            coroutineScope.launch {
                                try {
                                    val res = NetworkClient.apiService.setRiderAvailability(SetRiderAvailabilityRequest(!currentRider.available))
                                    if (res.success) rider = res.rider
                                } catch (e: HttpException) {
                                    error = superAppErrorMessage(e)
                                }
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) { Text(if (currentRider.available) "Go offline" else "Go online", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
            }
        }
        error?.let { item { Text(it, color = Ids.colors.danger, fontSize = 12.sp) } }
        if (activeDeliveries.isNotEmpty()) {
            item { Text("Your active deliveries", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
            items(activeDeliveries, key = { it.id }) { o ->
                val next = nextRiderStatus(o.status)
                EatsOrderRow(o) {
                    if (next != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Ids.colors.brand)
                                .pressScaleClickable(enabled = busyOrderId != o.id) {
                                    busyOrderId = o.id
                                    error = null
                                    coroutineScope.launch {
                                        try {
                                            NetworkClient.apiService.updateRiderOrderStatus(o.id, UpdateEatsOrderStatusRequest(next))
                                            loadDeliveries()
                                        } catch (e: HttpException) {
                                            error = superAppErrorMessage(e)
                                        } finally {
                                            busyOrderId = null
                                        }
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                        ) {
                            Text(
                                if (busyOrderId == o.id) "Updating…" else "Mark ${(EATS_STATUS_LABEL[next] ?: next).lowercase()}",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
            }
        }
        if (currentRider.available) {
            item { Text("Available deliveries", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
            if (available == null) {
                item { Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), modifier = Modifier.fillMaxWidth().height(100.dp)) {} }
            } else if (available!!.isEmpty()) {
                item { EmptyState("No deliveries waiting right now.", icon = Icons.AutoMirrored.Outlined.ReceiptLong) }
            } else {
                items(available!!, key = { it.id }) { o ->
                    EatsOrderRow(o) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Ids.colors.brand)
                                .pressScaleClickable(enabled = busyOrderId != o.id) {
                                    busyOrderId = o.id
                                    error = null
                                    coroutineScope.launch {
                                        try {
                                            NetworkClient.apiService.claimDelivery(o.id, java.util.UUID.randomUUID().toString())
                                            loadDeliveries()
                                        } catch (e: HttpException) {
                                            error = superAppErrorMessage(e)
                                        } finally {
                                            busyOrderId = null
                                        }
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                        ) { Text(if (busyOrderId == o.id) "Claiming…" else "Claim delivery", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                    }
                }
            }
        }
        if (pastDeliveries.isNotEmpty()) {
            item { Text("Completed", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
            items(pastDeliveries, key = { it.id }) { o -> EatsOrderRow(o) }
        }
    }
}
