package rw.itunda.feature.talk.impl

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import rw.itunda.core.designsystem.components.pressScaleClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.WebSocket
import retrofit2.HttpException
import rw.itunda.core.designsystem.components.BackTopBar
import rw.itunda.core.designsystem.components.IdsTextField
import rw.itunda.core.designsystem.components.SkeletonBlock
import rw.itunda.core.designsystem.components.shouldShowChatTimestamp
import rw.itunda.core.designsystem.itundaface.CameraGlyph
import rw.itunda.core.designsystem.itundaface.GiftThemeGlyph
import rw.itunda.core.designsystem.itundaface.PinGlyph
import rw.itunda.core.designsystem.itundaface.VoucherTicket
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.ConversationSummaryDto
import rw.itunda.core.network.CreateChatReportRequest
import rw.itunda.core.network.GiftDto
import rw.itunda.core.network.GIFT_THEME_LABELS
import rw.itunda.core.network.GiftVoucherDto
import rw.itunda.core.network.SendEmoticonRequest
import rw.itunda.core.network.MessageDto
import rw.itunda.core.network.MessagingSocketPush
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.PriceOfferDto
import rw.itunda.core.network.PropertyPriceOfferDto
import rw.itunda.core.network.RespondToOfferRequest
import rw.itunda.core.network.RespondToPropertyOfferRequest
import rw.itunda.core.network.SendGiftInConversationRequest
import rw.itunda.core.network.SendMessageRequest
import rw.itunda.core.network.SetConversationQuietRequest
import rw.itunda.core.network.ToggleReactionRequest
import rw.itunda.core.network.TokenStore
import rw.itunda.core.network.isDeviceNotVerifiedError
import rw.itunda.core.network.superAppErrorMessage
import java.io.IOException
import java.util.UUID


@Composable
internal fun ChatThreadView(
    conversation: ConversationSummaryDto,
    onBack: () -> Unit,
    deviceStepUpHost: @Composable (Boolean, () -> Unit, suspend () -> Unit) -> Unit,
) {
    // Real system-back interception (2026-07-18, found live on-device: pressing back
    // here fell through to the Activity's default back behavior and exited the app
    // instead of returning to the conversation list) -- matches the BackHandler
    // convention every other multi-step flow in ItundaAppScreen.kt already uses
    // (TransferStep/SavingsFlowStep/showSettings/etc).
    BackHandler(onBack = onBack)
    var messages by remember { mutableStateOf<List<MessageDto>?>(null) }
    var offersByMessageId by remember { mutableStateOf<Map<String, OfferBubbleData>>(emptyMap()) }
    var giftsByMessageId by remember { mutableStateOf<Map<String, GiftDto>>(emptyMap()) }
    var draft by remember { mutableStateOf("") }
    var replyingTo by remember { mutableStateOf<MessageDto?>(null) }
    var pinnedMessage by remember { mutableStateOf<MessageDto?>(null) }
    var updatingPin by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var blockConfirmationOpen by remember { mutableStateOf(false) }
    var blocking by remember { mutableStateOf(false) }
    var isBlocked by remember { mutableStateOf(false) }
    var quiet by remember { mutableStateOf(false) }
    var updatingQuiet by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<MessageDto>?>(null) }
    var searching by remember { mutableStateOf(false) }
    // Real device binding step-up (2026-07-21) -- Gift send/claim was a real gap:
    // already correctly enforced server-side (a real 403 DEVICE_NOT_VERIFIED) but
    // showed only a generic error, same fix already applied to Transfer/Savings/
    // Interest via MainViewModel.
    var needsDeviceVerification by remember { mutableStateOf(false) }
    var pendingDeviceRetry by remember { mutableStateOf<(suspend () -> Unit)?>(null) }
    var otherOnline by remember { mutableStateOf<Boolean?>(null) }
    var otherTyping by remember { mutableStateOf(false) }
    var giftComposerOpen by remember { mutableStateOf(false) }
    var giftAmount by remember { mutableStateOf("") }
    var giftNote by remember { mutableStateOf("") }
    var giftTheme by remember { mutableStateOf<String?>(null) }
    var sendingGift by remember { mutableStateOf(false) }
    // Real KakaoTalk Emoticon Store (item 135) -- see EmoticonPickerPanel's own doc
    // comment.
    var emoticonPickerOpen by remember { mutableStateOf(false) }
    var emoticonStoreOpen by remember { mutableStateOf(false) }
    // Real itundaface emoji picker -- see ItundaFaceEmoji.kt's own doc comment.
    // Distinct from emoticonPickerOpen above: inserts a real Unicode character
    // into `draft`, not a separate sticker message.
    var emojiPickerOpen by remember { mutableStateOf(false) }
    var emoticonImageById by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    // Real message forwarding (2026-08-04) -- see ForwardDestinationDialog's own doc
    // comment.
    var forwardingMessageId by remember { mutableStateOf<String?>(null) }
    // Real KakaoTalk-style 기프티콘 gift voucher (item 137) -- see
    // GiftVoucherComposerPanel's own doc comment.
    var vouchersByMessageId by remember { mutableStateOf<Map<String, GiftVoucherDto>>(emptyMap()) }
    var voucherComposerOpen by remember { mutableStateOf(false) }
    // Real per-thread shared-media gallery (2026-08-04) -- see GroupThreadView's own
    // doc comment for the full sourced account.
    var showMediaGallery by remember { mutableStateOf(false) }
    // Real 1:1-chat split-bill (2026-08-09) -- see DirectSplitBillsView's own doc
    // comment; mirrors GroupThreadView's own identical showSplitBills toggle.
    var showSplitBills by remember { mutableStateOf(false) }
    // Real Thread support (2026-08-05) -- see docs/DESIGN_REFERENCES.md Talk section
    // recommendation #3's own account. A message with real replies opens its own
    // sub-conversation view here, not just the inline "replying to" tag replyingTo above
    // already provides.
    var openThreadFor by remember { mutableStateOf<MessageDto?>(null) }
    // Real attach ("+") menu + photo send (2026-08-04) -- see SendMessageRequest's own
    // doc comment. Consolidates the previously-separate always-visible 🎁/😊/🎟️ icons
    // (plus the new 📷) into one real Kakao-style "+" menu -- References table: "'+'
    // opens a multi-function attach menu".
    var attachMenuOpen by remember { mutableStateOf(false) }
    var uploadingPhoto by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var typingClearJob by remember { mutableStateOf<Job?>(null) }
    var socket by remember { mutableStateOf<WebSocket?>(null) }
    var lastTypingSentAt by remember { mutableStateOf(0L) }
    val coroutineScope = rememberCoroutineScope()
    val listState: LazyListState = rememberLazyListState()
    val currentUserId = remember { NetworkClient.currentTokenStore().let(TokenStore::getUserId) }

    LaunchedEffect(conversation.otherUserId) {
        try {
            val res = NetworkClient.apiService.getPresence(listOf(conversation.otherUserId))
            if (res.success) otherOnline = res.presence[conversation.otherUserId]
        } catch (e: Exception) { /* real, non-critical -- only backs the header subtitle */ }
    }
    LaunchedEffect(conversation.conversationId) {
        try { quiet = NetworkClient.apiService.getConversationQuiet(conversation.conversationId).quiet } catch (_: Exception) { }
        try { pinnedMessage = NetworkClient.apiService.getPinnedConversationMessage(conversation.conversationId).message } catch (_: Exception) { }
    }

    // Real KakaoTalk Emoticon Store (item 135) -- no GET-emoticon-by-id endpoint
    // exists, so rendering a received emoticon needs a client-built id->imageUrl map
    // across the small, curated, server-seeded catalog. Mirrors bank-mfe's own
    // fetchEmoticonImageMap (item 133), just not module-level memoized here yet.
    LaunchedEffect(Unit) {
        try {
            val packs = NetworkClient.apiService.getEmoticonPacks().packs
            val map = packs.flatMap { pack ->
                try { NetworkClient.apiService.getPackEmoticons(pack.id).emoticons } catch (_: Exception) { emptyList() }
            }.associate { it.id to it.imageUrl }
            emoticonImageById = map
        } catch (_: Exception) {
            // Real, non-critical -- only backs the inline emoticon bubble.
        }
    }

    // Real-fetches both Marketplace and Real Estate offer history for this conversation
    // -- a given real conversation only ever carries one type in practice, but fetching
    // both is cheap and correct rather than guessing which one applies (mirrors
    // bank-mfe's own ConversationThread.loadOffers).
    suspend fun loadOffers() {
        val marketplaceOffers: List<PriceOfferDto> = try {
            NetworkClient.apiService.getOffersForConversation(conversation.conversationId).offers
        } catch (_: Exception) {
            emptyList()
        }
        val propertyOffers: List<PropertyPriceOfferDto> = try {
            NetworkClient.apiService.getPropertyOffersForConversation(conversation.conversationId).offers
        } catch (_: Exception) {
            emptyList()
        }
        offersByMessageId = (marketplaceOffers.map { it.toBubbleData() to it.messageId } + propertyOffers.map { it.toBubbleData() to it.messageId })
            .associate { (data, messageId) -> messageId to data }
    }

    suspend fun loadGifts() {
        try {
            val res = NetworkClient.apiService.getGiftsForConversation(conversation.conversationId)
            if (res.success) giftsByMessageId = res.gifts.associateBy { it.messageId }
        } catch (_: Exception) {
            // Real, non-critical -- only backs the inline gift bubble.
        }
    }

    suspend fun loadVouchers() {
        try {
            val res = NetworkClient.apiService.getGiftVouchersForConversation(conversation.conversationId)
            if (res.success) vouchersByMessageId = res.vouchers.associateBy { it.messageId }
        } catch (_: Exception) {
            // Real, non-critical -- only backs the inline gift-voucher bubble.
        }
    }

    suspend fun refresh() {
        try {
            val res = NetworkClient.apiService.getMessages(conversation.conversationId)
            if (res.success) messages = res.messages.reversed()
        } catch (_: Exception) {
            // Keep showing the last-known messages rather than blanking the thread
            // on a transient poll failure.
        }
        loadOffers()
        loadGifts()
        loadVouchers()
    }

    // Real poll, kept as an always-correct fallback delivery path alongside the real
    // WebSocket push below -- matches bank-mfe's own ConversationThread exactly (poll
    // interval unchanged, push appended live on top).
    LaunchedEffect(conversation.conversationId) {
        while (true) {
            refresh()
            delay(4000)
        }
    }
    // Real WebSocket live-transport (2026-07-18) -- see NetworkClient.connectMessagingSocket's
    // own doc comment. Pushed messages are de-duped by id against whatever the poll
    // already fetched, and appended on the main thread since OkHttp's listener callback
    // runs on its own background thread, not safe to mutate Compose state from directly.
    DisposableEffect(conversation.conversationId) {
        val ws = NetworkClient.connectMessagingSocket { push ->
            when {
                push is MessagingSocketPush.DirectMessage && push.message.conversationId == conversation.conversationId -> {
                    coroutineScope.launch(Dispatchers.Main) {
                        otherTyping = false
                        val current = messages ?: emptyList()
                        if (current.none { it.id == push.message.id }) {
                            messages = current + push.message
                        }
                        // A pushed message might be a real offer/counter/accept/reject --
                        // refresh so it renders as an offer bubble immediately.
                        loadOffers()
                        loadGifts()
                        loadVouchers()
                    }
                }
                push is MessagingSocketPush.PresenceChange && push.userId == conversation.otherUserId -> {
                    coroutineScope.launch(Dispatchers.Main) { otherOnline = push.online }
                }
                push is MessagingSocketPush.ReactionChange && push.conversationId == conversation.conversationId -> {
                    coroutineScope.launch(Dispatchers.Main) {
                        messages = messages?.map { if (it.id == push.messageId) it.copy(reactions = push.reactions) else it }
                    }
                }
                push is MessagingSocketPush.TypingChange && push.conversationId == conversation.conversationId && push.userId == conversation.otherUserId -> {
                    coroutineScope.launch(Dispatchers.Main) {
                        otherTyping = true
                        typingClearJob?.cancel()
                        typingClearJob = coroutineScope.launch {
                            delay(3000)
                            otherTyping = false
                        }
                    }
                }
            }
        }
        socket = ws
        onDispose {
            ws.close(1000, "leaving chat thread")
            socket = null
            typingClearJob?.cancel()
        }
    }
    LaunchedEffect(messages?.size) {
        val count = messages?.size ?: 0
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = Ids.layout.screenHorizontal, vertical = Ids.layout.screenVertical)) {
        BackTopBar(conversation.otherUserName, onBack)
        if (showMediaGallery) {
            MediaGalleryView(imageUrls = (messages ?: emptyList()).mapNotNull { it.imageUrl }.reversed(), onBack = { showMediaGallery = false })
            return@Column
        }
        if (showSplitBills) {
            DirectSplitBillsView(
                otherUserId = conversation.otherUserId,
                otherUserName = conversation.otherUserName,
                currentUserId = currentUserId,
                onBack = { showSplitBills = false },
            )
            return@Column
        }
        openThreadFor?.let { root ->
            RepliesThreadView(
                rootMessage = root,
                currentUserId = currentUserId,
                fetchThreadMessages = { NetworkClient.apiService.getThread(conversation.conversationId, root.id).messages },
                onSend = { body -> NetworkClient.apiService.sendMessage(conversation.conversationId, SendMessageRequest(body, root.id)) },
                onBack = { openThreadFor = null; coroutineScope.launch { refresh() } },
            )
            return@Column
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { showMediaGallery = true }) { Text("Photos", color = Ids.colors.textSecondary) }
            TextButton(onClick = { showSplitBills = true }) { Text("Split a bill", color = Ids.colors.textSecondary) }
            TextButton(
                onClick = {
                    if (isBlocked) {
                        blocking = true
                        coroutineScope.launch {
                            try { NetworkClient.apiService.unblockConversationParticipant(conversation.conversationId); isBlocked = false; error = "You unblocked ${conversation.otherUserName}." }
                            catch (e: HttpException) { error = superAppErrorMessage(e) }
                            catch (_: IOException) { error = "Couldn't reach itunda. Check your connection and try again." }
                            finally { blocking = false }
                        }
                    } else {
                        blockConfirmationOpen = true
                    }
                },
                enabled = !blocking,
            ) {
                Text(if (blocking) "…" else if (isBlocked) "Unblock" else "Block", color = Ids.colors.danger)
            }
            TextButton(onClick = {
                updatingQuiet = true
                coroutineScope.launch {
                    try { quiet = NetworkClient.apiService.setConversationQuiet(conversation.conversationId, SetConversationQuietRequest(!quiet)).quiet }
                    catch (_: IOException) { error = "Couldn't update this quiet room. Check your connection and try again." }
                    finally { updatingQuiet = false }
                }
            }, enabled = !updatingQuiet) { Text(if (updatingQuiet) "…" else if (quiet) "Resume alerts" else "Quiet room", color = Ids.colors.textSecondary) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            IdsTextField(value = searchQuery, onValueChange = { searchQuery = it; if (it.isBlank()) searchResults = null }, label = "Search this conversation", modifier = Modifier.weight(1f))
            TextButton(onClick = {
                val query = searchQuery.trim(); if (query.length < 2) { error = "Enter at least 2 characters to search."; return@TextButton }
                searching = true
                coroutineScope.launch {
                    try { searchResults = NetworkClient.apiService.searchMessages(conversation.conversationId, query).messages }
                    catch (e: HttpException) { error = superAppErrorMessage(e) }
                    catch (_: IOException) { error = "Couldn't search this conversation. Check your connection and try again." }
                    finally { searching = false }
                }
            }, enabled = !searching) { Text(if (searching) "…" else "Search") }
        }
        searchResults?.let { Text("${it.size} matching message${if (it.size == 1) "" else "s"}", color = Ids.colors.textSecondary, fontSize = 12.sp) }
        pinnedMessage?.let { pinned ->
            Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Ids.colors.surfaceSoft).padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                    PinGlyph(size = 12.dp)
                    Text(pinned.body, color = Ids.colors.textPrimary, fontSize = 12.sp, maxLines = 1)
                }
                TextButton(onClick = {
                    updatingPin = true
                    coroutineScope.launch { try { NetworkClient.apiService.unpinConversationMessage(conversation.conversationId); pinnedMessage = null } catch (_: Exception) { error = "Couldn't unpin this message." } finally { updatingPin = false } }
                }, enabled = !updatingPin) { Text("Unpin", fontSize = 11.sp) }
            }
        }
        otherOnline?.let { online ->
            Text(
                if (online) "Online" else "Offline",
                color = if (online) Ids.colors.success else Ids.colors.textSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(state = listState, modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val msgs = searchResults ?: messages
            if (msgs == null) {
                item { SkeletonBlock(height = 72.dp) }
            } else if (msgs.isEmpty()) {
                item { Text("Say hello — no messages yet.", color = Ids.colors.textSecondary, fontSize = 13.sp) }
            } else {
                itemsIndexed(msgs, key = { _, m -> m.id }) { index, m ->
                    MessageBubble(
                        m,
                        isMine = m.senderId == currentUserId,
                        currentUserId = currentUserId,
                        offer = offersByMessageId[m.id],
                        gift = giftsByMessageId[m.id],
                        voucher = vouchersByMessageId[m.id],
                        emoticonImageUrl = m.emoticonId?.let(emoticonImageById::get),
                        // Never collapsed when showing search hits -- adjacent results
                        // aren't temporally adjacent in the real conversation, so each
                        // one needs its own explicit timestamp regardless of sender/time.
                        showTimestamp = searchResults != null || shouldShowChatTimestamp(msgs, index, { it.senderId }, { it.sentAt }),
                        onExtendVoucher = { voucherId ->
                            coroutineScope.launch {
                                try {
                                    NetworkClient.apiService.extendGiftVoucherExpiry(voucherId)
                                    loadVouchers()
                                } catch (_: Exception) {
                                    error = "Couldn't extend this voucher. Try again."
                                }
                            }
                        },
                        onReply = { replyingTo = it },
                        onOpenThread = { openThreadFor = it },
                        onDelete = { messageId -> coroutineScope.launch {
                            try { NetworkClient.apiService.deleteMessage(conversation.conversationId, messageId); refresh() }
                            catch (_: Exception) { error = "Couldn't delete this message." }
                        } },
                        onPin = { message ->
                            updatingPin = true
                            coroutineScope.launch { try { NetworkClient.apiService.pinConversationMessage(conversation.conversationId, message.id); pinnedMessage = message } catch (_: Exception) { error = "Couldn't pin this message." } finally { updatingPin = false } }
                        },
                        onForward = { message -> forwardingMessageId = message.id },
                        onClaimGift = { giftId ->
                            coroutineScope.launch {
                                try {
                                    NetworkClient.apiService.claimGift(giftId, UUID.randomUUID().toString())
                                    loadGifts()
                                } catch (e: HttpException) {
                                    if (isDeviceNotVerifiedError(e)) {
                                        pendingDeviceRetry = {
                                            try { NetworkClient.apiService.claimGift(giftId, UUID.randomUUID().toString()); loadGifts() }
                                            catch (e2: HttpException) { error = superAppErrorMessage(e2) }
                                            catch (_: IOException) { error = "Couldn't reach itunda. Check your connection and try again." }
                                        }
                                        needsDeviceVerification = true
                                    } else {
                                        error = superAppErrorMessage(e)
                                    }
                                } catch (_: IOException) {
                                    error = "Couldn't reach itunda. Check your connection and try again."
                                }
                            }
                        },
                        onToggleReaction = { emoji ->
                            coroutineScope.launch {
                                try {
                                    val res = NetworkClient.apiService.toggleReaction(m.id, ToggleReactionRequest(emoji))
                                    if (res.success) messages = messages?.map { if (it.id == m.id) it.copy(reactions = res.reactions) else it }
                                } catch (_: Exception) {
                                    // Best-effort -- a failed toggle just leaves the badge as it was.
                                }
                            }
                        },
                        onRespondToOffer = { offerId, action, counterAmount ->
                            coroutineScope.launch {
                                try {
                                    // Real offer ids are stably prefixed by their real owning
                                    // service ("price_offer_"/"property_offer_") -- a reliable
                                    // dispatch key, matching bank-mfe's own ConversationThread.
                                    if (offerId.startsWith("property_offer_")) {
                                        NetworkClient.apiService.respondToPropertyOffer(offerId, RespondToPropertyOfferRequest(action, counterAmount))
                                    } else {
                                        NetworkClient.apiService.respondToOffer(offerId, RespondToOfferRequest(action, counterAmount))
                                    }
                                    loadOffers()
                                    refresh()
                                } catch (e: HttpException) {
                                    error = superAppErrorMessage(e)
                                } catch (_: IOException) {
                                    error = "Couldn't reach itunda. Check your connection and try again."
                                }
                            }
                        },
                        onReportMessage = { messageId, reason ->
                            coroutineScope.launch {
                                try { NetworkClient.apiService.reportChatMessage(CreateChatReportRequest(messageId, reason)); error = "Thanks. Your report was sent for review." }
                                catch (e: HttpException) { error = superAppErrorMessage(e) }
                                catch (_: IOException) { error = "Couldn't reach itunda. Check your connection and try again." }
                            }
                        },
                    )
                }
            }
        }
        forwardingMessageId?.let { messageId ->
            ForwardDestinationDialog(sourceMessageId = messageId, isGroupSource = false, onDismiss = { forwardingMessageId = null })
        }
        if (otherTyping) {
            Text("${conversation.otherUserName} is typing…", color = Ids.colors.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
        }
        deviceStepUpHost(
            needsDeviceVerification,
            { needsDeviceVerification = false; pendingDeviceRetry = null },
            {
                needsDeviceVerification = false
                val retry = pendingDeviceRetry
                pendingDeviceRetry = null
                retry?.invoke()
            },
        )
        error?.let { Text(it, color = Ids.colors.danger, fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp)) }
        if (blockConfirmationOpen) {
            AlertDialog(
                onDismissRequest = { blockConfirmationOpen = false },
                title = { Text("Block ${conversation.otherUserName}?") },
                text = { Text("They will no longer be able to message you. You can unblock them later from this conversation.") },
                confirmButton = {
                    TextButton(onClick = {
                        blockConfirmationOpen = false; blocking = true
                        coroutineScope.launch {
                            try { NetworkClient.apiService.blockConversationParticipant(conversation.conversationId); isBlocked = true; error = "${conversation.otherUserName} is blocked." }
                            catch (e: HttpException) { error = superAppErrorMessage(e) }
                            catch (_: IOException) { error = "Couldn't reach itunda. Check your connection and try again." }
                            finally { blocking = false }
                        }
                    }) { Text("Block", color = Ids.colors.danger) }
                },
                dismissButton = { TextButton(onClick = { blockConfirmationOpen = false }) { Text("Cancel") } },
            )
        }
        if (giftComposerOpen) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Ids.colors.surfaceSoft)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    GiftThemeGlyph(giftTheme, size = 16.dp)
                    Text("Send a gift", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Ids.colors.textPrimary)
                }
                IdsTextField(
                    value = giftAmount,
                    onValueChange = { giftAmount = it },
                    label = "Amount (RWF)",
                    keyboardType = KeyboardType.Number,
                    isAmount = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                IdsTextField(
                    value = giftNote,
                    onValueChange = { if (it.length <= 200) giftNote = it },
                    label = "Add a note (optional)",
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val themeOptions = listOf<Pair<String?, String>>(null to "No theme") + GIFT_THEME_LABELS.entries.map { it.key to it.value.replaceFirst(Regex("^\\S+\\s*"), "") }
                    themeOptions.forEach { (value, label) ->
                        val selected = giftTheme == value
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) Ids.colors.brand else Ids.colors.surface)
                                .pressScaleClickable { giftTheme = value }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            GiftThemeGlyph(value, size = 14.dp)
                            Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = if (selected) Color.White else Ids.colors.textPrimary)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val amountValue = giftAmount.toDoubleOrNull()
                    OfferActionButton(if (sendingGift) "Sending…" else "Send gift") {
                        if (amountValue == null || amountValue <= 0 || sendingGift) return@OfferActionButton
                        sendingGift = true
                        error = null
                        needsDeviceVerification = false
                        val sendGift: suspend () -> Unit = {
                            NetworkClient.apiService.sendGiftInConversation(
                                conversation.conversationId,
                                UUID.randomUUID().toString(),
                                SendGiftInConversationRequest(amountValue, giftNote.trim().ifBlank { null }, giftTheme),
                            )
                            giftAmount = ""
                            giftNote = ""
                            giftTheme = null
                            giftComposerOpen = false
                            refresh()
                        }
                        coroutineScope.launch {
                            try {
                                sendGift()
                            } catch (e: HttpException) {
                                if (isDeviceNotVerifiedError(e)) {
                                    pendingDeviceRetry = sendGift
                                    needsDeviceVerification = true
                                } else {
                                    error = superAppErrorMessage(e)
                                }
                            } catch (_: IOException) {
                                error = "Couldn't reach itunda. Check your connection and try again."
                            } finally {
                                sendingGift = false
                            }
                        }
                    }
                    OfferActionButton("Cancel") { giftComposerOpen = false }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        if (emojiPickerOpen) {
            ItundaFaceEmojiPicker(onPick = { emoji -> draft += emoji })
            Spacer(modifier = Modifier.height(8.dp))
        }
        if (emoticonPickerOpen) {
            EmoticonPickerPanel(
                onSend = { emoticonId ->
                    coroutineScope.launch {
                        try {
                            NetworkClient.apiService.sendEmoticon(conversation.conversationId, SendEmoticonRequest(emoticonId))
                            emoticonPickerOpen = false
                            refresh()
                        } catch (_: Exception) {
                            error = "Couldn't send this emoticon."
                        }
                    }
                },
                onOpenStore = { emoticonStoreOpen = true },
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        if (emoticonStoreOpen) {
            EmoticonStoreDialog(onDismiss = { emoticonStoreOpen = false })
        }
        if (voucherComposerOpen) {
            GiftVoucherComposerPanel(
                onSent = { voucherComposerOpen = false; coroutineScope.launch { refresh() } },
                onCancel = { voucherComposerOpen = false },
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        replyingTo?.let { reply ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text("Replying to: ${reply.body.take(80)}", color = Ids.colors.textSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 1)
                TextButton(onClick = { replyingTo = null }) { Text("×", color = Ids.colors.textSecondary) }
            }
        }
        val pickChatPhoto = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri == null) return@rememberLauncherForActivityResult
            uploadingPhoto = true
            error = null
            coroutineScope.launch {
                try {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    if (bytes == null) {
                        error = "Couldn't read that photo."
                        return@launch
                    }
                    val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
                    val part = MultipartBody.Part.createFormData("file", "photo.jpg", bytes.toRequestBody(mimeType.toMediaTypeOrNull()))
                    val photoUrl = NetworkClient.apiService.uploadPhoto(part).url
                    val res = NetworkClient.apiService.sendMessage(conversation.conversationId, SendMessageRequest("", replyingTo?.id, photoUrl))
                    if (res.success) {
                        replyingTo = null
                        messages = (messages ?: emptyList()) + res.message
                    }
                } catch (e: HttpException) {
                    error = superAppErrorMessage(e)
                } catch (e: IOException) {
                    error = "Couldn't upload that photo. Check your connection and try again."
                } finally {
                    uploadingPhoto = false
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            Box {
                Box(
                    modifier = Modifier
                        .size(Ids.layout.minTouchTarget)
                        .clip(CircleShape)
                        .background(Ids.colors.surfaceSoft)
                        .pressScaleClickable(enabled = !uploadingPhoto) { attachMenuOpen = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (uploadingPhoto) "…" else "+", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ids.colors.textPrimary)
                }
                // Real attach menu (2026-08-04) -- Kakao's own real "+"-opens-a-menu
                // pattern, consolidating what used to be 3 separate always-visible icons.
                DropdownMenu(expanded = attachMenuOpen, onDismissRequest = { attachMenuOpen = false }) {
                    DropdownMenuItem(text = { Text("Photo") }, leadingIcon = { CameraGlyph(size = 18.dp) }, onClick = { attachMenuOpen = false; pickChatPhoto.launch("image/*") })
                    DropdownMenuItem(text = { Text("Emoji") }, leadingIcon = { SmileySlight(size = 18.dp) }, onClick = { attachMenuOpen = false; emojiPickerOpen = !emojiPickerOpen })
                    DropdownMenuItem(text = { Text("😊 Emoticon") }, onClick = { attachMenuOpen = false; emoticonPickerOpen = !emoticonPickerOpen })
                    DropdownMenuItem(text = { Text("Gift") }, leadingIcon = { GiftThemeGlyph(null, size = 18.dp) }, onClick = { attachMenuOpen = false; giftComposerOpen = !giftComposerOpen })
                    DropdownMenuItem(text = { Text("Gift voucher") }, leadingIcon = { VoucherTicket(size = 18.dp) }, onClick = { attachMenuOpen = false; voucherComposerOpen = !voucherComposerOpen })
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            IdsTextField(
                value = draft,
                onValueChange = { newValue ->
                    draft = newValue
                    // Real typing indicator send (2026-07-19), client-throttled to match
                    // the server's own 1-per-2s rate limit.
                    val now = System.currentTimeMillis()
                    if (now - lastTypingSentAt > 2000) {
                        lastTypingSentAt = now
                        socket?.let { NetworkClient.sendTyping(it, conversationId = conversation.conversationId) }
                    }
                },
                label = "Message",
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .size(Ids.layout.minTouchTarget)
                    .clip(CircleShape)
                    .background(if (draft.isBlank() || sending) Ids.colors.textTertiary else Ids.colors.brand)
                    .pressScaleClickable(enabled = draft.isNotBlank() && !sending) {
                        val body = draft.trim()
                        sending = true
                        error = null
                        coroutineScope.launch {
                            try {
                                val res = NetworkClient.apiService.sendMessage(conversation.conversationId, SendMessageRequest(body, replyingTo?.id))
                                if (res.success) {
                                draft = ""
                                replyingTo = null
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

