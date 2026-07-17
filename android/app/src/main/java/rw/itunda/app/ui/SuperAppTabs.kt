package rw.itunda.app.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBackIosNew
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import retrofit2.HttpException
import rw.itunda.app.network.ConversationSummaryDto
import rw.itunda.app.network.CreateListingRequest
import rw.itunda.app.network.ListingDto
import rw.itunda.app.network.MerchantProductDto
import rw.itunda.app.network.MessageDto
import rw.itunda.app.network.NetworkClient
import rw.itunda.app.network.OrderDto
import rw.itunda.app.network.OrderItemRequest
import rw.itunda.app.network.PlaceOrderRequest
import rw.itunda.app.network.SendMessageRequest
import rw.itunda.app.network.ShoppingMerchantDto
import rw.itunda.app.network.StartConversationRequest
import rw.itunda.app.network.TokenStore
import rw.itunda.core.designsystem.theme.Tds
import java.io.IOException
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

private fun superAppErrorMessage(e: HttpException): String = when (e.code()) {
    401, 403 -> "You don't have access to do that."
    404 -> "That couldn't be found."
    409 -> "That's already been done, or is being processed."
    422 -> "Insufficient funds for this order."
    429 -> "Too many attempts -- please wait a moment and try again."
    else -> "Something went wrong. Please try again."
}

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit) {
    Card(
        shape = RoundedCornerShape(Tds.layout.cardCornerRadius),
        colors = CardDefaults.cardColors(containerColor = TossCard),
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(message, color = Tds.colors.danger, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(10.dp))
            Text("Retry", color = TossBlue, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable(onClick = onRetry))
        }
    }
}

@Composable
private fun TabHeader(title: String) {
    Text(title, color = TossText, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
}

// ============================== TALK (Messaging) ==============================

@Composable
internal fun TalkTab(initialConversationId: String?, onConsumedInitial: () -> Unit) {
    var conversations by remember { mutableStateOf<List<ConversationSummaryDto>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var openConversationId by remember { mutableStateOf<String?>(null) }
    var startPhoneNumber by remember { mutableStateOf("") }
    var startError by remember { mutableStateOf<String?>(null) }
    var starting by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    fun load() {
        coroutineScope.launch {
            try {
                val res = NetworkClient.apiService.getConversations()
                if (res.success) conversations = res.conversations
                error = null
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            }
        }
    }
    LaunchedEffect(Unit) { load() }

    // Real "message seller" hand-off from HoodTab -- opens straight into the real
    // chat thread once it shows up in this tab's own real conversation list, same
    // pattern bank-mfe's MessagesView/initialConversationId prop already established.
    LaunchedEffect(initialConversationId, conversations) {
        if (initialConversationId != null && conversations?.any { it.conversationId == initialConversationId } == true) {
            openConversationId = initialConversationId
            onConsumedInitial()
        }
    }

    val open = conversations?.find { it.conversationId == openConversationId }
    if (open != null) {
        ChatThreadView(conversation = open, onBack = { openConversationId = null; load() })
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = Tds.layout.screenHorizontal, vertical = Tds.layout.screenVertical),
        verticalArrangement = Arrangement.spacedBy(Tds.layout.cardGap),
    ) {
        item { TabHeader("Talk") }
        item {
            Card(
                shape = RoundedCornerShape(Tds.layout.cardCornerRadius),
                colors = CardDefaults.cardColors(containerColor = TossCard),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("New chat", color = TossText, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Enter their phone number to start a conversation.", color = TossSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp, bottom = 12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = startPhoneNumber,
                            onValueChange = { startPhoneNumber = it },
                            placeholder = { Text("+250788123456") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(TossBlue)
                                .clickable(enabled = !starting && startPhoneNumber.isNotBlank()) {
                                    starting = true
                                    startError = null
                                    coroutineScope.launch {
                                        try {
                                            val res = NetworkClient.apiService.startConversation(StartConversationRequest(phoneNumber = startPhoneNumber.trim()))
                                            if (res.success) {
                                                startPhoneNumber = ""
                                                load()
                                                openConversationId = res.conversation.id
                                            }
                                        } catch (e: HttpException) {
                                            startError = superAppErrorMessage(e)
                                        } catch (e: IOException) {
                                            startError = "Couldn't reach itunda. Check your connection and try again."
                                        } finally {
                                            starting = false
                                        }
                                    }
                                }
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                        ) {
                            Text(if (starting) "..." else "Chat", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                    startError?.let { Text(it, color = Tds.colors.danger, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) }
                }
            }
        }
        if (error != null) {
            item { ErrorCard(error!!, onRetry = ::load) }
        } else if (conversations == null) {
            item { Card(shape = RoundedCornerShape(Tds.layout.cardCornerRadius), modifier = Modifier.fillMaxWidth().height(120.dp)) {} }
        } else if (conversations!!.isEmpty()) {
            item { Text("No conversations yet.", color = TossSecondary, fontSize = 14.sp) }
        } else {
            items(conversations!!, key = { it.conversationId }) { c -> ConversationRow(c, onClick = { openConversationId = c.conversationId }) }
        }
    }
}

@Composable
private fun ConversationRow(conversation: ConversationSummaryDto, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Tds.layout.cardCornerRadius))
            .background(TossCard)
            .clickable(onClick = onClick)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(44.dp).clip(CircleShape).background(TossCardSoft), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Send, contentDescription = null, modifier = Modifier.size(18.dp), tint = TossBlue)
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(conversation.otherUserName, color = TossText, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(conversation.lastMessagePreview ?: "No messages yet", color = TossSecondary, fontSize = 12.sp, maxLines = 1)
        }
        if (conversation.unreadCount > 0) {
            Box(modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(TossBlue).padding(horizontal = 8.dp, vertical = 3.dp)) {
                Text(conversation.unreadCount.toString(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
    Spacer(modifier = Modifier.height(4.dp))
}

@Composable
private fun ChatThreadView(conversation: ConversationSummaryDto, onBack: () -> Unit) {
    // Real system-back interception (2026-07-18, found live on-device: pressing back
    // here fell through to the Activity's default back behavior and exited the app
    // instead of returning to the conversation list) -- matches the BackHandler
    // convention every other multi-step flow in ItundaAppScreen.kt already uses
    // (TransferStep/SavingsFlowStep/showSettings/etc).
    BackHandler(onBack = onBack)
    var messages by remember { mutableStateOf<List<MessageDto>?>(null) }
    var draft by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val listState: LazyListState = rememberLazyListState()
    val currentUserId = remember { NetworkClient.currentTokenStore().let(TokenStore::getUserId) }

    suspend fun refresh() {
        try {
            val res = NetworkClient.apiService.getMessages(conversation.conversationId)
            if (res.success) messages = res.messages.reversed()
        } catch (_: Exception) {
            // Keep showing the last-known messages rather than blanking the thread
            // on a transient poll failure.
        }
    }

    // Real poll-based "live" delivery -- MessagingService's own doc comment names
    // this as the honest current scope (no WebSocket/push transport yet). 4s matches
    // bank-mfe's own ConversationThread poll interval exactly.
    LaunchedEffect(conversation.conversationId) {
        while (true) {
            refresh()
            delay(4000)
        }
    }
    LaunchedEffect(messages?.size) {
        val count = messages?.size ?: 0
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = Tds.layout.screenHorizontal, vertical = Tds.layout.screenVertical)) {
        BackTopBar(conversation.otherUserName, onBack)
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(state = listState, modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val msgs = messages
            if (msgs == null) {
                item { Text("Loading…", color = TossSecondary, fontSize = 13.sp) }
            } else if (msgs.isEmpty()) {
                item { Text("Say hello — no messages yet.", color = TossSecondary, fontSize = 13.sp) }
            } else {
                items(msgs, key = { it.id }) { m -> MessageBubble(m, isMine = m.senderId == currentUserId) }
            }
        }
        error?.let { Text(it, color = Tds.colors.danger, fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp)) }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = { Text("Message") },
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .size(Tds.layout.minTouchTarget)
                    .clip(CircleShape)
                    .background(if (draft.isBlank() || sending) TossTertiary else TossBlue)
                    .clickable(enabled = draft.isNotBlank() && !sending) {
                        val body = draft.trim()
                        sending = true
                        error = null
                        coroutineScope.launch {
                            try {
                                val res = NetworkClient.apiService.sendMessage(conversation.conversationId, SendMessageRequest(body))
                                if (res.success) {
                                    draft = ""
                                    messages = (messages ?: emptyList()) + res.message
                                }
                            } catch (e: HttpException) {
                                error = superAppErrorMessage(e)
                            } catch (e: IOException) {
                                error = "Couldn't reach itunda. Check your connection and try again."
                            } finally {
                                sending = false
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Send, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun MessageBubble(message: MessageDto, isMine: Boolean) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(if (isMine) TossBlue else TossCardSoft)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(message.body, color = if (isMine) Color.White else TossText, fontSize = 14.sp)
        }
    }
}

// ============================== HOOD (Marketplace) ==============================

private enum class HoodView { BROWSE, MINE }

@Composable
internal fun HoodTab(onMessageSeller: (String) -> Unit) {
    var view by remember { mutableStateOf(HoodView.BROWSE) }
    var listings by remember { mutableStateOf<List<ListingDto>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var showNewListing by remember { mutableStateOf(false) }
    if (showNewListing) {
        BackHandler { showNewListing = false }
    }
    val coroutineScope = rememberCoroutineScope()
    val currentUserId = remember { NetworkClient.currentTokenStore().let(TokenStore::getUserId) }

    fun load() {
        listings = null
        coroutineScope.launch {
            try {
                val res = if (view == HoodView.BROWSE) NetworkClient.apiService.browseListings() else NetworkClient.apiService.getMyListings()
                if (res.success) listings = res.listings
                error = null
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            }
        }
    }
    LaunchedEffect(view) { load() }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = Tds.layout.screenHorizontal, vertical = Tds.layout.screenVertical),
        verticalArrangement = Arrangement.spacedBy(Tds.layout.cardGap),
    ) {
        item { TabHeader("Hood") }
        item {
            Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(TossCardSoft).padding(4.dp)) {
                listOf(HoodView.BROWSE to "Browse", HoodView.MINE to "My listings").forEach { (v, label) ->
                    val selected = v == view
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
                            .clickable { view = v }
                            .padding(vertical = 8.dp),
                    )
                }
            }
        }
        if (view == HoodView.MINE) {
            item {
                if (!showNewListing) {
                    Box(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(TossBlue).clickable { showNewListing = true }.padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text("+ List an item", color = Color.White, fontWeight = FontWeight.Bold) }
                } else {
                    NewListingForm(onCreated = { showNewListing = false; load() }, onCancel = { showNewListing = false })
                }
            }
        }
        if (error != null) {
            item { ErrorCard(error!!, onRetry = ::load) }
        } else if (listings == null) {
            item { Card(shape = RoundedCornerShape(Tds.layout.cardCornerRadius), modifier = Modifier.fillMaxWidth().height(120.dp)) {} }
        } else if (listings!!.isEmpty()) {
            item { Text(if (view == HoodView.BROWSE) "No listings yet." else "You haven't listed anything yet.", color = TossSecondary, fontSize = 14.sp) }
        } else {
            items(listings!!, key = { it.id }) { listing ->
                ListingCard(
                    listing = listing,
                    isMine = view == HoodView.MINE || listing.sellerId == currentUserId,
                    onChanged = ::load,
                    onMessageSeller = { id ->
                        coroutineScope.launch {
                            try {
                                val res = NetworkClient.apiService.contactSeller(id)
                                if (res.success) onMessageSeller(res.conversation.id)
                            } catch (e: HttpException) {
                                error = superAppErrorMessage(e)
                            } catch (e: IOException) {
                                error = "Couldn't reach itunda. Check your connection and try again."
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun NewListingForm(onCreated: () -> Unit, onCancel: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    Card(shape = RoundedCornerShape(Tds.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = TossCard), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("List an item", color = TossText, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            OutlinedTextField(value = title, onValueChange = { title = it }, placeholder = { Text("What are you selling?") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = description, onValueChange = { description = it }, placeholder = { Text("Description") }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = price, onValueChange = { price = it }, placeholder = { Text("Price (RWF)") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(value = category, onValueChange = { category = it }, placeholder = { Text("Category") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            error?.let { Text(it, color = Tds.colors.danger, fontSize = 12.sp) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(TossBlue)
                        .clickable(enabled = !submitting) {
                            val priceValue = price.toDoubleOrNull()
                            if (title.isBlank() || description.isBlank() || category.isBlank() || priceValue == null || priceValue <= 0) {
                                error = "Fill in every field with a real price."
                                return@clickable
                            }
                            submitting = true
                            error = null
                            coroutineScope.launch {
                                try {
                                    val res = NetworkClient.apiService.createListing(CreateListingRequest(title, description, priceValue, category))
                                    if (res.success) onCreated()
                                } catch (e: HttpException) {
                                    error = superAppErrorMessage(e)
                                } catch (e: IOException) {
                                    error = "Couldn't reach itunda. Check your connection and try again."
                                } finally {
                                    submitting = false
                                }
                            }
                        }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(if (submitting) "Listing…" else "List it", color = Color.White, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun ListingCard(listing: ListingDto, isMine: Boolean, onChanged: () -> Unit, onMessageSeller: (String) -> Unit) {
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    Card(shape = RoundedCornerShape(Tds.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = TossCard), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(listing.title, color = TossText, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        if (listing.status == "SOLD") {
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(TossCardSoft).padding(horizontal = 8.dp, vertical = 2.dp)) {
                                Text("SOLD", color = TossSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Text(listing.category, color = TossSecondary, fontSize = 12.sp)
                }
                Text("%,.0f RWF".format(listing.price), color = TossText, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            Text(listing.description, color = TossSecondary, fontSize = 13.sp)
            error?.let { Text(it, color = Tds.colors.danger, fontSize = 12.sp) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (isMine) {
                    if (listing.status == "ACTIVE") {
                        ListingActionButton("Mark sold", busy) {
                            busy = true
                            coroutineScope.launch {
                                try {
                                    NetworkClient.apiService.markListingSold(listing.id)
                                    onChanged()
                                } catch (e: HttpException) {
                                    error = superAppErrorMessage(e)
                                } finally {
                                    busy = false
                                }
                            }
                        }
                    }
                    if (listing.status != "REMOVED") {
                        ListingActionButton("Remove", busy) {
                            busy = true
                            coroutineScope.launch {
                                try {
                                    NetworkClient.apiService.removeListing(listing.id)
                                    onChanged()
                                } catch (e: HttpException) {
                                    error = superAppErrorMessage(e)
                                } finally {
                                    busy = false
                                }
                            }
                        }
                    }
                } else if (listing.status == "ACTIVE") {
                    ListingActionButton(if (busy) "Starting…" else "Message seller", busy, filled = true) {
                        busy = true
                        onMessageSeller(listing.id)
                        busy = false
                    }
                }
            }
        }
    }
}

@Composable
private fun ListingActionButton(label: String, disabled: Boolean, filled: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (filled) TossBlue else TossCardSoft)
            .clickable(enabled = !disabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(label, color = if (filled) Color.White else TossText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ============================== SHOP (Commerce) ==============================

@Composable
internal fun ShopTab() {
    var merchants by remember { mutableStateOf<List<ShoppingMerchantDto>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedMerchant by remember { mutableStateOf<ShoppingMerchantDto?>(null) }
    var products by remember { mutableStateOf<List<MerchantProductDto>?>(null) }
    val cart = remember { mutableStateMapOf<String, Int>() }
    var showCheckout by remember { mutableStateOf(false) }
    var confirmedOrder by remember { mutableStateOf<OrderDto?>(null) }
    val coroutineScope = rememberCoroutineScope()

    fun loadMerchants() {
        coroutineScope.launch {
            try {
                val res = NetworkClient.apiService.getShoppingMerchants()
                if (res.success) merchants = res.merchants
                error = null
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            }
        }
    }
    LaunchedEffect(Unit) { loadMerchants() }

    fun openMerchant(m: ShoppingMerchantDto) {
        selectedMerchant = m
        cart.clear()
        products = null
        coroutineScope.launch {
            try {
                val res = NetworkClient.apiService.getMerchantProducts(m.merchantId)
                if (res.success) products = res.products
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            }
        }
    }

    val confirmed = confirmedOrder
    if (confirmed != null) {
        OrderConfirmationView(confirmed, onDone = {
            confirmedOrder = null
            selectedMerchant = null
            products = null
            cart.clear()
            showCheckout = false
        })
        return
    }

    val merchant = selectedMerchant
    if (merchant != null) {
        if (showCheckout) {
            CheckoutView(
                merchant = merchant,
                cart = cart,
                products = products.orEmpty(),
                onBack = { showCheckout = false },
                onOrderPlaced = { order -> confirmedOrder = order },
            )
        } else {
            MerchantDetailView(
                merchant = merchant,
                products = products,
                cart = cart,
                onBack = { selectedMerchant = null },
                onCheckout = { showCheckout = true },
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = Tds.layout.screenHorizontal, vertical = Tds.layout.screenVertical),
        verticalArrangement = Arrangement.spacedBy(Tds.layout.cardGap),
    ) {
        item { TabHeader("Shop") }
        if (error != null) {
            item { ErrorCard(error!!, onRetry = ::loadMerchants) }
        } else if (merchants == null) {
            item { Card(shape = RoundedCornerShape(Tds.layout.cardCornerRadius), modifier = Modifier.fillMaxWidth().height(120.dp)) {} }
        } else if (merchants!!.isEmpty()) {
            item { Text("No stores registered yet.", color = TossSecondary, fontSize = 14.sp) }
        } else {
            items(merchants!!, key = { it.merchantId }) { m ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Tds.layout.cardCornerRadius))
                        .background(TossCard)
                        .clickable { openMerchant(m) }
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(TossCardSoft), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Storefront, contentDescription = null, modifier = Modifier.size(20.dp), tint = TossBlue)
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(m.businessName, color = TossText, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("${m.cashbackRate} cashback on QR/code payments", color = TossSecondary, fontSize = 12.sp)
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun MerchantDetailView(
    merchant: ShoppingMerchantDto,
    products: List<MerchantProductDto>?,
    cart: androidx.compose.runtime.snapshots.SnapshotStateMap<String, Int>,
    onBack: () -> Unit,
    onCheckout: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val cartCount = cart.values.sum()
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = Tds.layout.screenHorizontal, vertical = Tds.layout.screenVertical)) {
        BackTopBar(merchant.businessName, onBack)
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
            if (products == null) {
                item { Text("Loading…", color = TossSecondary, fontSize = 13.sp) }
            } else if (products.isEmpty()) {
                item { Text("No products yet.", color = TossSecondary, fontSize = 13.sp) }
            } else {
                items(products, key = { it.id }) { p ->
                    val qty = cart[p.id] ?: 0
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Tds.layout.cardCornerRadius)).background(TossCard).padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(p.name, color = TossText, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("%,.0f RWF".format(p.price), color = TossSecondary, fontSize = 13.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            QtyButton("-") { if (qty > 0) cart[p.id] = qty - 1 }
                            Text(qty.toString(), modifier = Modifier.width(28.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = TossText, fontWeight = FontWeight.Bold)
                            QtyButton("+") { cart[p.id] = qty + 1 }
                        }
                    }
                }
            }
        }
        if (cartCount > 0) {
            Box(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(TossBlue).clickable(onClick = onCheckout).padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.ShoppingCart, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Checkout ($cartCount item${if (cartCount == 1) "" else "s"})", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun QtyButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(30.dp).clip(CircleShape).background(TossCardSoft).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = TossText, fontWeight = FontWeight.Bold) }
}

@Composable
private fun CheckoutView(
    merchant: ShoppingMerchantDto,
    cart: Map<String, Int>,
    products: List<MerchantProductDto>,
    onBack: () -> Unit,
    onOrderPlaced: (OrderDto) -> Unit,
) {
    BackHandler(onBack = onBack)
    var address by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val idempotencyKey = remember { UUID.randomUUID().toString() }

    val lines = cart.filter { it.value > 0 }.mapNotNull { (productId, qty) -> products.find { it.id == productId }?.let { it to qty } }
    val total = lines.sumOf { (p, qty) -> p.price * qty }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = Tds.layout.screenHorizontal, vertical = Tds.layout.screenVertical)) {
        BackTopBar("Checkout", onBack)
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
            items(lines) { (p, qty) ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${p.name} x$qty", color = TossText, fontSize = 14.sp)
                    Text("%,.0f RWF".format(p.price * qty), color = TossText, fontSize = 14.sp)
                }
            }
            item { Divider(color = TossLine, modifier = Modifier.padding(vertical = 10.dp)) }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total", color = TossText, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("%,.0f RWF".format(total), color = TossText, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
            item { Spacer(modifier = Modifier.height(14.dp)) }
            item {
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    placeholder = { Text("Delivery address") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            error?.let { item { Text(it, color = Tds.colors.danger, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) } }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(if (submitting || address.isBlank()) TossTertiary else TossBlue)
                .clickable(enabled = !submitting && address.isNotBlank()) {
                    submitting = true
                    error = null
                    coroutineScope.launch {
                        try {
                            val res = NetworkClient.apiService.placeOrder(
                                idempotencyKey = idempotencyKey,
                                request = PlaceOrderRequest(
                                    merchantId = merchant.merchantId,
                                    items = lines.map { (p, qty) -> OrderItemRequest(p.id, qty) },
                                    deliveryAddress = address.trim(),
                                ),
                            )
                            if (res.success) onOrderPlaced(res.order)
                        } catch (e: HttpException) {
                            error = superAppErrorMessage(e)
                        } catch (e: IOException) {
                            error = "Couldn't reach itunda. Check your connection and try again."
                        } finally {
                            submitting = false
                        }
                    }
                }
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) { Text(if (submitting) "Placing order…" else "Place order", color = Color.White, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun OrderConfirmationView(order: OrderDto, onDone: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("Order placed") },
        text = {
            Column {
                Text("%,.0f RWF".format(order.totalAmount), color = TossText, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Text("Delivering to ${order.deliveryAddress}", color = TossSecondary, fontSize = 13.sp)
            }
        },
        confirmButton = { TextButton(onClick = onDone) { Text("Done") } },
    )
}
