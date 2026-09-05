package rw.itunda.feature.eats.impl

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlinx.coroutines.launch
import rw.itunda.core.designsystem.components.CameraQrScanner
import rw.itunda.core.designsystem.components.IdsButton
import rw.itunda.core.designsystem.components.IdsButtonSize
import rw.itunda.core.designsystem.components.IdsTextField
import rw.itunda.core.designsystem.components.generateQrBitmap
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.EatsOrderDto
import rw.itunda.core.network.CreateGroupEatsOrderRequest
import rw.itunda.core.network.GroupEatsOrderDetailResponse
import rw.itunda.core.network.GroupEatsOrderItemRequest
import rw.itunda.core.network.JoinGroupEatsOrderRequest
import rw.itunda.core.network.MerchantProductDto
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.SetGroupEatsOrderItemsRequest
import rw.itunda.core.network.ShoppingMerchantDto
import rw.itunda.core.network.superAppErrorMessage
import retrofit2.HttpException
import java.io.IOException
import rw.itunda.core.network.TokenStore
import java.util.UUID

private enum class GroupOrderMode { CLOSED, CREATE, JOIN_SCAN, JOIN_MANUAL, ACTIVE }

// Real 배달의민족 함께주문 (Baemin "Together Order") -- ported from bank-mfe
// (2026-09-03), see GroupEatsOrderDto's own doc comment on the network layer for the
// full sourced account. A join-code-shared cart in front of the same real
// checkout/payment path the normal single-buyer order flow uses. Reuses the same
// QR-generate/scan-first join convention OpenChatCard already established
// (docs/UI_UX_GUIDELINES.md's own "no manual-code UX" rule) rather than inventing a
// second one.
@Composable
internal fun GroupEatsOrderView(restaurants: List<ShoppingMerchantDto>?) {
    var mode by remember { mutableStateOf(GroupOrderMode.CLOSED) }
    var groupOrderId by remember { mutableStateOf<String?>(null) }
    var detail by remember { mutableStateOf<GroupEatsOrderDetailResponse?>(null) }
    var menu by remember { mutableStateOf<List<MerchantProductDto>?>(null) }
    var myItems by remember { mutableStateOf<List<GroupEatsOrderItemRequest>>(emptyList()) }
    var restaurantId by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var joinCode by remember { mutableStateOf("") }
    var qrBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var scanUnavailable by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var placedOrder by remember { mutableStateOf<EatsOrderDto?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val currentUserId = remember { NetworkClient.currentTokenStore().let(TokenStore::getUserId) }

    fun refresh(id: String) {
        coroutineScope.launch {
            try { detail = NetworkClient.apiService.getGroupEatsOrder(id) } catch (_: Exception) {}
        }
    }

    fun openOrder(order: rw.itunda.core.network.GroupEatsOrderDto) {
        groupOrderId = order.id
        qrBitmap = generateQrBitmap("itunda://join-eats?code=${order.joinCode}")
        mode = GroupOrderMode.ACTIVE
        refresh(order.id)
        coroutineScope.launch {
            try { menu = NetworkClient.apiService.getMerchantProducts(order.restaurantId).products } catch (_: Exception) {}
        }
    }

    fun submitJoinCode(raw: String) {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return
        busy = true
        error = null
        coroutineScope.launch {
            try {
                val res = NetworkClient.apiService.joinGroupEatsOrder(JoinGroupEatsOrderRequest(trimmed))
                if (res.success) openOrder(res.groupOrder)
            } catch (e: Exception) {
                error = "No together order found for this code."
            } finally {
                busy = false
            }
        }
    }

    when (mode) {
        GroupOrderMode.CLOSED -> {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                IdsButton(text = "Start together order", onClick = { mode = GroupOrderMode.CREATE }, size = IdsButtonSize.Medium, modifier = Modifier.weight(1f))
                IdsButton(text = "Join together order", onClick = { mode = GroupOrderMode.JOIN_SCAN }, size = IdsButtonSize.Medium, modifier = Modifier.weight(1f))
            }
        }

        GroupOrderMode.CREATE -> {
            Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                Text("Start a together order", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(
                    "Share one restaurant's cart with friends -- everyone adds their own items, you place one real order, and itunda asks each of them for their own share afterward.",
                    color = Ids.colors.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
                )
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (restaurants ?: emptyList()).forEach { r ->
                        val selected = r.merchantId == restaurantId
                        Text(
                            r.businessName,
                            color = if (selected) Ids.colors.surface else Ids.colors.textPrimary,
                            fontWeight = FontWeight.Bold, fontSize = 13.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(if (selected) Ids.colors.brand else Ids.colors.surfaceSoft)
                                .pressScaleClickable { restaurantId = r.merchantId }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                IdsTextField(value = address, onValueChange = { address = it }, label = "Delivery address", modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    IdsButton(text = "Cancel", onClick = { mode = GroupOrderMode.CLOSED }, size = IdsButtonSize.Medium, modifier = Modifier.weight(1f))
                    IdsButton(
                        text = if (busy) "Starting…" else "Start",
                        enabled = !busy && restaurantId.isNotBlank() && address.isNotBlank(),
                        onClick = {
                            busy = true
                            error = null
                            coroutineScope.launch {
                                try {
                                    val res = NetworkClient.apiService.createGroupEatsOrder(CreateGroupEatsOrderRequest(restaurantId, address.trim()))
                                    if (res.success) openOrder(res.groupOrder)
                                } catch (e: HttpException) {
                                    error = superAppErrorMessage(e)
                                } catch (e: IOException) {
                                    error = "Couldn't reach itunda. Check your connection and try again."
                                } finally {
                                    busy = false
                                }
                            }
                        },
                        size = IdsButtonSize.Medium,
                        modifier = Modifier.weight(1f),
                    )
                }
                error?.let { Text(it, color = Ids.colors.danger, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) }
            }
        }

        GroupOrderMode.JOIN_SCAN -> {
            Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                Text("Scan to join", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(10.dp))
                if (!scanUnavailable && !busy) {
                    CameraQrScanner(
                        onScanned = { raw ->
                            val match = Regex("[?&]code=([^&]+)").find(raw)
                            submitJoinCode(match?.groupValues?.get(1) ?: raw)
                        },
                        modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(12.dp)),
                    )
                }
                if (busy) Text("Joining…", color = Ids.colors.textSecondary, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    IdsButton(text = "Cancel", onClick = { mode = GroupOrderMode.CLOSED }, size = IdsButtonSize.Medium, modifier = Modifier.weight(1f))
                    IdsButton(
                        text = if (scanUnavailable) "Enter code manually" else "No camera? Enter code",
                        onClick = { mode = GroupOrderMode.JOIN_MANUAL },
                        size = IdsButtonSize.Medium,
                        modifier = Modifier.weight(1f),
                    )
                }
                error?.let { Text(it, color = Ids.colors.danger, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) }
            }
        }

        GroupOrderMode.JOIN_MANUAL -> {
            Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                Text("Join by code", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(10.dp))
                IdsTextField(value = joinCode, onValueChange = { joinCode = it.uppercase() }, label = "Join code", modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    IdsButton(text = "Cancel", onClick = { mode = GroupOrderMode.CLOSED }, size = IdsButtonSize.Medium, modifier = Modifier.weight(1f))
                    IdsButton(
                        text = if (busy) "Joining…" else "Join",
                        enabled = !busy && joinCode.isNotBlank(),
                        onClick = { submitJoinCode(joinCode) },
                        size = IdsButtonSize.Medium,
                        modifier = Modifier.weight(1f),
                    )
                }
                error?.let { Text(it, color = Ids.colors.danger, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) }
            }
        }

        GroupOrderMode.ACTIVE -> if (placedOrder != null) {
            Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                Text("Order placed", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(
                    "Real order #${placedOrder!!.id.takeLast(8)} placed for ${String.format(Locale.US, "%,.0f", placedOrder!!.totalAmount)} RWF. Every other participant with items in the cart has been sent a real Dutch-pay request via Split Bill.",
                    color = Ids.colors.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp),
                )
            }
        } else {
            val order = detail?.groupOrder
            Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    qrBitmap?.let { Image(bitmap = it, contentDescription = "QR code to join", modifier = Modifier.size(72.dp).clip(RoundedCornerShape(8.dp))) }
                    Column {
                        Text("Order together -- ${order?.joinCode ?: ""}", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("Send friends a link to join instantly, or let someone nearby scan the code.", color = Ids.colors.textSecondary, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(10.dp))
                IdsButton(
                    text = "Share join code",
                    onClick = {
                        val code = order?.joinCode ?: return@IdsButton
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "Order together on itunda -- use code $code in the Eats tab.")
                        }
                        context.startActivity(Intent.createChooser(intent, "Share invite code"))
                    },
                    size = IdsButtonSize.Medium,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(16.dp))
                Text("Add your own item", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.height(8.dp))
                (menu ?: emptyList()).forEach { item ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(item.name, color = Ids.colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("${String.format(Locale.US, "%,.0f", item.price)} RWF", color = Ids.colors.textSecondary, fontSize = 12.sp)
                        }
                        Text(
                            "Add", color = Ids.colors.brand, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                            modifier = Modifier.pressScaleClickable(enabled = !busy && groupOrderId != null) {
                                val id = groupOrderId ?: return@pressScaleClickable
                                busy = true
                                coroutineScope.launch {
                                    try {
                                        val nextItems = myItems + GroupEatsOrderItemRequest(item.id, 1)
                                        val res = NetworkClient.apiService.setGroupEatsOrderItems(id, SetGroupEatsOrderItemsRequest(nextItems))
                                        myItems = nextItems
                                        detail = res
                                    } catch (e: HttpException) {
                                        error = superAppErrorMessage(e)
                                    } catch (e: IOException) {
                                        error = "Couldn't reach itunda. Check your connection and try again."
                                    } finally {
                                        busy = false
                                    }
                                }
                            },
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    "Everyone's items -- ${String.format(Locale.US, "%,.0f", detail?.grandTotal ?: 0.0)} RWF total",
                    color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                )
                Spacer(Modifier.height(8.dp))
                (detail?.participants ?: emptyList()).forEach { p ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text(
                            "${if (p.userId == order?.hostUserId) "Host" else "Participant"} -- ${String.format(Locale.US, "%,.0f", p.subtotal)} RWF",
                            color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                        )
                        if (p.items.isEmpty()) {
                            Text("No items yet", color = Ids.colors.textSecondary, fontSize = 12.sp)
                        } else {
                            p.items.forEach { i ->
                                Text("${i.quantity}x ${i.productName} -- ${String.format(Locale.US, "%,.0f", i.lineTotal)} RWF", color = Ids.colors.textSecondary, fontSize = 12.sp)
                            }
                        }
                    }
                }
                IdsButton(text = "Refresh", onClick = { groupOrderId?.let { refresh(it) } }, size = IdsButtonSize.Medium, modifier = Modifier.fillMaxWidth())

                val isHost = order != null && currentUserId != null && order.hostUserId == currentUserId
                if (isHost) {
                    Spacer(Modifier.height(12.dp))
                    IdsButton(
                        text = if (busy) "…" else "Place the real order",
                        enabled = !busy && (detail?.grandTotal ?: 0.0) > 0.0,
                        onClick = {
                            val id = groupOrderId ?: return@IdsButton
                            busy = true
                            error = null
                            coroutineScope.launch {
                                try {
                                    val res = NetworkClient.apiService.finalizeGroupEatsOrder(id, UUID.randomUUID().toString())
                                    placedOrder = res.order
                                } catch (e: HttpException) {
                                    error = superAppErrorMessage(e)
                                } catch (e: IOException) {
                                    error = "Couldn't reach itunda. Check your connection and try again."
                                } finally {
                                    busy = false
                                }
                            }
                        },
                        size = IdsButtonSize.Medium,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    IdsButton(
                        text = "Cancel together order",
                        enabled = !busy,
                        onClick = {
                            val id = groupOrderId ?: return@IdsButton
                            busy = true
                            coroutineScope.launch {
                                try {
                                    NetworkClient.apiService.cancelGroupEatsOrder(id)
                                    groupOrderId = null
                                    detail = null
                                    myItems = emptyList()
                                    mode = GroupOrderMode.CLOSED
                                } catch (e: HttpException) {
                                    error = superAppErrorMessage(e)
                                } catch (e: IOException) {
                                    error = "Couldn't reach itunda. Check your connection and try again."
                                } finally {
                                    busy = false
                                }
                            }
                        },
                        size = IdsButtonSize.Medium,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                error?.let { Text(it, color = Ids.colors.danger, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) }
            }
        }
    }
}
