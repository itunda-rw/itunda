package rw.itunda.feature.talk.impl

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import coil.compose.AsyncImage
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import rw.itunda.core.designsystem.components.IdsTextField
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.EmoticonDto
import rw.itunda.core.network.EmoticonPackDto
import rw.itunda.core.network.GiftEmoticonPackRequest
import rw.itunda.core.network.OwnedEmoticonPackDto
import rw.itunda.core.network.ProductSearchResultDto
import rw.itunda.core.network.PurchaseGiftVoucherRequest
import rw.itunda.core.network.NetworkClient
import java.util.UUID


// Real gift-voucher composer (item 137) -- search for a real product to gift (same
// real Kakao gifticon UX of searching for what to send, e.g. "스타벅스 아메리카노",
// rather than browsing a merchant catalog first), pick one, confirm with the
// recipient's phone number. Product-only v1 -- the flat-cash-amount-at-a-merchant
// path is a real, deliberately deferred follow-up. Mirrors bank-mfe's own
// GiftVoucherComposerPanel (item 134).
@Composable
internal fun GiftVoucherComposerPanel(onSent: () -> Unit, onCancel: () -> Unit) {
    var phone by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<ProductSearchResultDto>?>(null) }
    var searching by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<ProductSearchResultDto?>(null) }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Ids.colors.surfaceSoft)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("🎟️ Send a gift voucher", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Ids.colors.textPrimary)
        IdsTextField(
            value = phone,
            onValueChange = { phone = it },
            label = "Recipient phone number",
            keyboardType = KeyboardType.Phone,
            modifier = Modifier.fillMaxWidth(),
        )
        val currentSelected = selected
        if (currentSelected != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Ids.colors.surface)
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "${currentSelected.name} · ${currentSelected.merchantName} · %,.0f RWF".format(currentSelected.price),
                    fontSize = 13.sp, color = Ids.colors.textPrimary,
                )
                TextButton(onClick = { selected = null }) { Text("Change", fontSize = 12.sp, color = Ids.colors.brand) }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IdsTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = "Search a product to gift",
                    modifier = Modifier.weight(1f),
                )
                OfferActionButton(if (searching) "…" else "Search") {
                    if (query.trim().length < 2 || searching) return@OfferActionButton
                    searching = true
                    error = null
                    coroutineScope.launch {
                        try {
                            results = NetworkClient.apiService.searchProducts(query.trim()).products
                        } catch (_: Exception) {
                            error = "Could not search products."
                        } finally {
                            searching = false
                        }
                    }
                }
            }
            results?.let { list ->
                if (list.isEmpty()) {
                    Text("No products found.", fontSize = 12.sp, color = Ids.colors.textSecondary)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        list.forEach { p ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Ids.colors.surface)
                                    .clickable { selected = p }
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text("${p.name} · ${p.merchantName}", fontSize = 13.sp, color = Ids.colors.textPrimary)
                                Text("%,.0f RWF".format(p.price), fontSize = 13.sp, color = Ids.colors.textPrimary)
                            }
                        }
                    }
                }
            }
        }
        error?.let { Text(it, color = Ids.colors.danger, fontSize = 12.sp) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OfferActionButton(if (sending) "…" else "Send gift voucher") {
                val product = selected
                if (product == null || phone.isBlank() || sending) return@OfferActionButton
                sending = true
                error = null
                coroutineScope.launch {
                    try {
                        NetworkClient.apiService.purchaseGiftVoucher(
                            UUID.randomUUID().toString(),
                            PurchaseGiftVoucherRequest(phone.trim(), product.merchantId, product.id),
                        )
                        onSent()
                    } catch (_: Exception) {
                        error = "Could not send this gift voucher."
                    } finally {
                        sending = false
                    }
                }
            }
            OfferActionButton("Cancel") { onCancel() }
        }
    }
}

@Composable
internal fun EmoticonPickerPanel(onSend: (String) -> Unit, onOpenStore: () -> Unit) {
    var ownedPacks by remember { mutableStateOf<List<OwnedEmoticonPackDto>?>(null) }
    var packTitles by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var selectedPackId by remember { mutableStateOf<String?>(null) }
    var packEmoticons by remember { mutableStateOf<List<EmoticonDto>?>(null) }

    LaunchedEffect(Unit) {
        try {
            val owned = NetworkClient.apiService.getOwnedEmoticonPacks().packs
            val allPacks = NetworkClient.apiService.getEmoticonPacks().packs
            ownedPacks = owned
            packTitles = allPacks.associate { it.id to it.title }
            if (owned.isNotEmpty()) selectedPackId = owned.first().packId
        } catch (_: Exception) {
            ownedPacks = emptyList()
        }
    }

    LaunchedEffect(selectedPackId) {
        val packId = selectedPackId ?: return@LaunchedEffect
        packEmoticons = null
        packEmoticons = try { NetworkClient.apiService.getPackEmoticons(packId).emoticons } catch (_: Exception) { emptyList() }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Ids.colors.surfaceSoft)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val owned = ownedPacks
        if (owned == null) {
            Text("Loading…", color = Ids.colors.textSecondary, fontSize = 13.sp)
        } else if (owned.isEmpty()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text("You don't own any emoticon packs yet.", color = Ids.colors.textSecondary, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(8.dp))
                OfferActionButton("Browse Emoticon Store") { onOpenStore() }
            }
        } else {
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                owned.forEach { op ->
                    OfferActionButton(packTitles[op.packId] ?: op.packId) { selectedPackId = op.packId }
                }
                OfferActionButton("Get more") { onOpenStore() }
            }
            val emoticons = packEmoticons
            if (emoticons == null) {
                Text("Loading…", color = Ids.colors.textSecondary, fontSize = 12.sp)
            } else {
                LazyVerticalGrid(columns = GridCells.Fixed(4), modifier = Modifier.height(160.dp)) {
                    gridItems(emoticons, key = { it.id }) { e ->
                        Box(
                            modifier = Modifier.padding(4.dp).clickable { onSend(e.id) },
                            contentAlignment = Alignment.Center,
                        ) {
                            AsyncImage(model = e.imageUrl, contentDescription = "", modifier = Modifier.fillMaxWidth().aspectRatio(1f))
                        }
                    }
                }
            }
        }
    }
}

// Real Emoticon Store (item 135) -- browse every real active pack, buy (once-off
// purchase, same "buy it once, own it" model Shop/Insurance already use), or gift to
// a friend by phone number. Mirrors bank-mfe's own EmoticonStoreModal (item 133).
@Composable
internal fun EmoticonStoreDialog(onDismiss: () -> Unit) {
    var packs by remember { mutableStateOf<List<EmoticonPackDto>?>(null) }
    var ownedPackIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var busyPackId by remember { mutableStateOf<String?>(null) }
    var giftingPackId by remember { mutableStateOf<String?>(null) }
    var giftPhone by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    fun load() {
        coroutineScope.launch {
            try {
                packs = NetworkClient.apiService.getEmoticonPacks().packs
                ownedPackIds = NetworkClient.apiService.getOwnedEmoticonPacks().packs.map { it.packId }.toSet()
            } catch (_: Exception) {
                error = "Could not load the Emoticon Store."
            }
        }
    }
    LaunchedEffect(Unit) { load() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🛍 Emoticon Store") },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        text = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                error?.let { Text(it, color = Ids.colors.danger, fontSize = 13.sp) }
                message?.let { Text(it, color = Ids.colors.brand, fontSize = 13.sp) }
                val currentPacks = packs
                if (currentPacks == null) {
                    Text("Loading…", color = Ids.colors.textSecondary, fontSize = 13.sp)
                } else {
                    currentPacks.forEach { pack ->
                        val owned = ownedPackIds.contains(pack.id)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Ids.colors.surfaceSoft)
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                AsyncImage(model = pack.thumbnailUrl, contentDescription = "", modifier = Modifier.size(48.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(pack.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Ids.colors.textPrimary)
                                    Text("${pack.artistName} · %,.0f RWF".format(pack.price), fontSize = 12.sp, color = Ids.colors.textSecondary)
                                }
                                OfferActionButton(if (owned) "Owned" else if (busyPackId == pack.id) "…" else "Buy") {
                                    if (owned || busyPackId != null) return@OfferActionButton
                                    busyPackId = pack.id
                                    error = null
                                    coroutineScope.launch {
                                        try {
                                            NetworkClient.apiService.purchaseEmoticonPack(pack.id)
                                            load()
                                        } catch (_: Exception) {
                                            error = "Could not purchase this pack."
                                        } finally {
                                            busyPackId = null
                                        }
                                    }
                                }
                                OfferActionButton("Gift") { giftingPackId = if (giftingPackId == pack.id) null else pack.id }
                            }
                            if (giftingPackId == pack.id) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    IdsTextField(
                                        value = giftPhone,
                                        onValueChange = { giftPhone = it },
                                        label = "Recipient phone number",
                                        keyboardType = KeyboardType.Phone,
                                        modifier = Modifier.weight(1f),
                                    )
                                    OfferActionButton(if (busyPackId == pack.id) "…" else "Send gift") {
                                        if (giftPhone.isBlank() || busyPackId != null) return@OfferActionButton
                                        busyPackId = pack.id
                                        error = null
                                        message = null
                                        coroutineScope.launch {
                                            try {
                                                NetworkClient.apiService.giftEmoticonPack(pack.id, GiftEmoticonPackRequest(giftPhone.trim()))
                                                message = "Pack gifted!"
                                                giftingPackId = null
                                                giftPhone = ""
                                            } catch (_: Exception) {
                                                error = "Could not gift this pack."
                                            } finally {
                                                busyPackId = null
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
    )
}

