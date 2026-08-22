package rw.itunda.feature.shop.impl

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import rw.itunda.core.designsystem.components.pressScaleClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import retrofit2.HttpException
import rw.itunda.core.designsystem.components.BackTopBar
import rw.itunda.core.designsystem.components.IdsTextField
import rw.itunda.core.designsystem.components.QtyButton
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import rw.itunda.core.designsystem.itundaface.WishlistHeart
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.MerchantProductDto
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.OrderItemRequest
import rw.itunda.core.network.PlaceOrderRequest
import rw.itunda.core.network.ShoppingMerchantDto
import rw.itunda.core.network.isDeviceNotVerifiedError
import rw.itunda.core.network.superAppErrorMessage
import java.io.IOException
import java.util.UUID




@Composable
internal fun ProductDetailScreen(
    merchant: ShoppingMerchantDto,
    product: MerchantProductDto,
    cart: SnapshotStateMap<String, CommerceCartLine>,
    onBack: () -> Unit,
    onViewCart: () -> Unit,
    favorited: Boolean = false,
    favoriteBusy: Boolean = false,
    onToggleFavorite: () -> Unit = {},
) {
    BackHandler(onBack = onBack)
    val totalItems = cart.values.sumOf { it.quantity }
    val key = "${merchant.merchantId}:${product.id}"
    val qty = cart[key]?.quantity ?: 0
    fun setQty(newQty: Int) {
        val stock = product.stockQuantity
        if (newQty <= 0) cart.remove(key)
        else if (stock == null || newQty <= stock) {
            cart[key] = CommerceCartLine(merchant.merchantId, merchant.businessName, product, newQty)
        }
    }
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = Ids.layout.screenHorizontal, vertical = Ids.layout.screenVertical)) {
        BackTopBar(merchant.businessName, onBack)
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                ProductImageThumb(product.imageUrl, size = 220.dp, corner = 16.dp)
                // Real Shop product wishlist button on the detail page (2026-07-24) --
                // Chloe Youn's Coupang case study (docs/DESIGN_REFERENCES.md Section 5)
                // names wishlist as available directly alongside add-to-cart, not
                // buried behind a sub-menu.
                WishlistHeart(
                    favorited = favorited,
                    size = 26.dp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .semantics { contentDescription = if (favorited) "Remove from wishlist" else "Add to wishlist" }
                        .pressScaleClickable(enabled = !favoriteBusy, onClick = onToggleFavorite),
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(product.name, color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(modifier = Modifier.height(6.dp))
            ProductPriceRow(product)
            Spacer(modifier = Modifier.height(6.dp))
            ProductRatingBadge(product.id)
            Text(
                product.stockQuantity?.let { if (it == 0) "Out of stock" else "$it available" } ?: "Available",
                color = if (product.stockQuantity == 0) Ids.colors.danger else Ids.colors.textSecondary,
                fontSize = 12.sp,
            )
            val description = product.description
            if (!description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(description, color = Ids.colors.textSecondary, fontSize = 13.sp, lineHeight = 19.sp)
            }
            Spacer(modifier = Modifier.height(16.dp))
            ProductInquirySection(product.id)
            Spacer(modifier = Modifier.height(12.dp))
            SubscribeAndSaveButton(merchantId = merchant.merchantId, productId = product.id)
            Spacer(modifier = Modifier.height(20.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                QtyButton("-") { setQty(qty - 1) }
                Text(qty.toString(), modifier = Modifier.width(36.dp), textAlign = TextAlign.Center, color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                QtyButton("+") { val stock = product.stockQuantity; if (stock == null || qty < stock) setQty(qty + 1) }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Ids.colors.brand)
                    .pressScaleClickable(enabled = product.stockQuantity.let { it == null || it > 0 }) {
                        setQty(maxOf(1, qty))
                    }
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (qty > 0) "Update cart" else "Add to cart", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
        if (totalItems > 0) {
            CartFab(totalItems, onClick = onViewCart)
        }
    }
}

/**
 * Real per-seller order splitting -- the merchant-grouped cart's checkout screen.
 * Each merchant group becomes its own real, independent placeOrder() call (its own
 * Idempotency-Key, its own account-to-account ledger transaction) -- sequential, not
 * parallel: these are real money-moving calls against the same buyer account, and a
 * clear one-at-a-time result list is more honest than a swallowed batch result. A
 * failure on one merchant's order does not block or roll back any other, matching
 * how a real multi-seller checkout behaves (each seller is charged/fulfilled
 * independently in real life).
 */
@Composable
internal fun MultiCartView(
    cart: SnapshotStateMap<String, CommerceCartLine>,
    onBack: () -> Unit,
    onOrderPlaced: (List<CommerceCheckoutResult>) -> Unit,
    deviceStepUpHost: @Composable (visible: Boolean, onDismiss: () -> Unit, onVerified: suspend () -> Unit) -> Unit,
) {
    BackHandler(onBack = onBack)
    var address by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // Real device binding step-up (2026-07-21) -- every order in this batch shares
    // the same device/session, so hitting this once means every remaining order
    // would fail identically -- the loop below stops at the first one rather than
    // collecting N duplicate failures, same fix already applied to bank-mfe's
    // MultiCartView.
    var needsDeviceVerification by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val groups = cart.values.groupBy { it.merchantId }
    val groupList = remember(groups) { groups.entries.toList() }
    val grandTotal = cart.values.sumOf { it.product.price * it.quantity }
    // Real fix (2026-08-10): found live-testing bank-mfe's identical checkout screen
    // -- retrying after device verification here used to just clear the flag with no
    // retry at all, same original gap, but this loop also places one real order per
    // merchant sequentially and stops at the first DEVICE_NOT_VERIFIED. A naive retry
    // would have RE-PLACED every order that already succeeded before the failure -- a
    // real duplicate-order bug, not just friction. These two let a retry resume from
    // exactly the merchant that failed.
    val checkoutResults = remember { mutableListOf<CommerceCheckoutResult>() }
    var resumeIndex by remember { mutableStateOf(0) }

    suspend fun placeOrders() {
        submitting = true
        error = null
        needsDeviceVerification = false
        for (i in resumeIndex until groupList.size) {
            val (merchantId, lines) = groupList[i]
            try {
                val res = NetworkClient.apiService.placeOrder(
                    idempotencyKey = UUID.randomUUID().toString(),
                    request = PlaceOrderRequest(
                        merchantId = merchantId,
                        items = lines.map { OrderItemRequest(it.product.id, it.quantity) },
                        deliveryAddress = address.trim(),
                    ),
                )
                checkoutResults.add(CommerceCheckoutResult(merchantId, lines.first().businessName, res.order, null))
            } catch (e: HttpException) {
                if (isDeviceNotVerifiedError(e)) {
                    resumeIndex = i
                    needsDeviceVerification = true
                    submitting = false
                    return
                }
                checkoutResults.add(CommerceCheckoutResult(merchantId, lines.first().businessName, null, superAppErrorMessage(e)))
            } catch (e: IOException) {
                checkoutResults.add(CommerceCheckoutResult(merchantId, lines.first().businessName, null, "Couldn't reach itunda. Check your connection and try again."))
            }
        }
        submitting = false
        onOrderPlaced(checkoutResults)
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = Ids.layout.screenHorizontal, vertical = Ids.layout.screenVertical)) {
        BackTopBar("Your cart", onBack)
        if (groups.isEmpty()) {
            Text("Your cart is empty.", color = Ids.colors.textSecondary, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))
            return@Column
        }
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
            groups.forEach { (merchantId, lines) ->
                item(key = merchantId) {
                    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Ids.layout.cardCornerRadius)).background(Ids.colors.surface).padding(16.dp)) {
                        Text(lines.first().businessName, color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        lines.forEach { line ->
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${line.product.name} x${line.quantity}", color = Ids.colors.textPrimary, fontSize = 13.sp)
                                Text("%,.0f RWF".format(line.product.price * line.quantity), color = Ids.colors.textPrimary, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
            item {
                Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Ids.layout.cardCornerRadius)).background(Ids.colors.surface).padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total (${groups.size} order${if (groups.size == 1) "" else "s"})", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("%,.0f RWF".format(grandTotal), color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    IdsTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = "Delivery address",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    error?.let { Text(it, color = Ids.colors.danger, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) }
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(if (submitting || address.isBlank()) Ids.colors.textTertiary else Ids.colors.brand)
                .pressScaleClickable(enabled = !submitting && address.isNotBlank()) {
                    coroutineScope.launch { placeOrders() }
                }
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) { Text(if (submitting) "Placing orders…" else "Place ${groups.size} order${if (groups.size == 1) "" else "s"}", color = Color.White, fontWeight = FontWeight.Bold) }
        deviceStepUpHost(
            needsDeviceVerification,
            { needsDeviceVerification = false },
            { placeOrders() },
        )
    }
}

@Composable
internal fun MultiCartResultsView(results: List<CommerceCheckoutResult>, onDone: () -> Unit) {
    val successCount = results.count { it.order != null }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("$successCount of ${results.size} order${if (results.size == 1) "" else "s"} placed") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                results.forEach { r ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(r.businessName, color = Ids.colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        if (r.order != null) {
                            Text("%,.0f RWF — placed".format(r.order.totalAmount), color = Ids.colors.success, fontSize = 13.sp)
                        } else {
                            Text(r.error ?: "Failed", color = Ids.colors.danger, fontSize = 13.sp)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDone) { Text(if (results.any { it.order == null }) "Back to cart" else "Done") } },
    )
}

