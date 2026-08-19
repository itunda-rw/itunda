package rw.itunda.feature.talk.impl

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.activity.compose.BackHandler
import coil.compose.AsyncImage
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import rw.itunda.core.designsystem.components.BackTopBar
import rw.itunda.core.designsystem.components.IdsButton
import rw.itunda.core.designsystem.components.IdsButtonSize
import rw.itunda.core.designsystem.components.IdsTextField
import rw.itunda.core.designsystem.components.SkeletonBlock
import rw.itunda.core.designsystem.components.chatMessageTime
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.MessageResponse
import rw.itunda.core.network.GiftDto
import rw.itunda.core.network.GIFT_THEME_LABELS
import rw.itunda.core.network.GiftVoucherDto
import rw.itunda.core.network.MessageDto
import rw.itunda.core.network.PriceOfferDto
import rw.itunda.core.network.PropertyPriceOfferDto
import rw.itunda.core.network.SendMessageRequest
import java.time.Instant


// Real Thread support (2026-08-05) -- see docs/DESIGN_REFERENCES.md Talk section
// recommendation #3's own account and MessagingController.getThread's backend doc
// comment for the full sourced Kakao account. A real sub-conversation view: the root
// message, every direct reply oldest-first, and a composer that replies straight into
// this same thread. Named "Replies" rather than reusing "Thread" to avoid colliding
// with this file's own existing ChatThreadView/GroupThreadView naming (those are the
// whole conversation screen, a different real concept).
@Composable
internal fun RepliesThreadView(
    rootMessage: MessageDto,
    currentUserId: String?,
    fetchThreadMessages: suspend () -> List<MessageDto>,
    onSend: suspend (String) -> MessageResponse,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    var messages by remember { mutableStateOf<List<MessageDto>?>(null) }
    var draft by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    fun load() {
        coroutineScope.launch {
            try { messages = fetchThreadMessages() } catch (_: Exception) { error = "Could not load this thread." }
        }
    }
    LaunchedEffect(rootMessage.id) { load() }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = Ids.layout.screenHorizontal, vertical = Ids.layout.screenVertical)) {
        BackTopBar("Thread", onBack)
        error?.let { Text(it, color = Ids.colors.danger, fontSize = 12.sp) }
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val msgs = messages
            if (msgs == null) {
                item { SkeletonBlock(height = 72.dp) }
            } else {
                itemsIndexed(msgs, key = { _, m -> m.id }) { index, m ->
                    val isMine = m.senderId == currentUserId
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (index == 0) {
                            Text("Original message", color = Ids.colors.textSecondary, fontSize = 10.sp, modifier = Modifier.padding(bottom = 2.dp))
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start) {
                            Box(
                                modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(if (isMine) Ids.colors.brand else Ids.colors.surfaceSoft).padding(horizontal = 14.dp, vertical = 10.dp),
                            ) {
                                Text(if (m.deletedAt == null) m.body else "This message was deleted", color = if (isMine) Color.White else Ids.colors.textPrimary, fontSize = 14.sp)
                            }
                        }
                        Text(
                            chatMessageTime(m.sentAt), color = Ids.colors.textSecondary, fontSize = 10.sp,
                            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                            textAlign = if (isMine) TextAlign.End else TextAlign.Start,
                        )
                    }
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IdsTextField(value = draft, onValueChange = { draft = it }, label = "Reply in thread", modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(8.dp))
            IdsButton(
                text = if (sending) "…" else "Send",
                enabled = !sending && draft.isNotBlank(),
                size = IdsButtonSize.Medium,
                onClick = {
                    val body = draft.trim()
                    if (body.isEmpty()) return@IdsButton
                    sending = true
                    coroutineScope.launch {
                        try { onSend(body); draft = ""; load() } catch (_: Exception) { error = "Could not send this reply." } finally { sending = false }
                    }
                },
            )
        }
    }
}

// Real 당근-style offer bubble (2026-07-19) -- see PriceOfferService's own doc comment.
// Renders inline wherever a message carries a real offer, replacing the plain-text
// bubble with amount + status + real Accept/Decline/Counter actions (only shown to
// whichever participant did NOT propose the current pending amount).
// Real minimal shape both PriceOfferDto (Marketplace) and PropertyPriceOfferDto (Real
// Estate) get mapped into for display -- narrowed to just the fields OfferBubble
// actually reads (id/amount/status/proposedByUserId), so this one component renders
// both offer types without duplication. Mirrors bank-mfe's own OfferBubbleData
// narrowing (2026-07-19).
internal data class OfferBubbleData(val id: String, val amount: Double, val status: String, val proposedByUserId: String)
internal fun PriceOfferDto.toBubbleData() = OfferBubbleData(id, amount, status, proposedByUserId)
internal fun PropertyPriceOfferDto.toBubbleData() = OfferBubbleData(id, amount, status, proposedByUserId)

@Composable
internal fun OfferBubble(offer: OfferBubbleData, isMine: Boolean, currentUserId: String?, onRespond: (String, String, Double?) -> Unit) {
    var countering by remember { mutableStateOf(false) }
    var counterAmount by remember { mutableStateOf("") }
    val canRespond = offer.status == "PENDING" && currentUserId != null && currentUserId != offer.proposedByUserId
    val statusLabel = when (offer.status) {
        "PENDING" -> "Pending"; "ACCEPTED" -> "Accepted"; "REJECTED" -> "Declined"; "COUNTERED" -> "Countered"
        else -> offer.status
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (isMine) Ids.colors.brand else Ids.colors.surfaceSoft)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("💰 %,.0f RWF".format(offer.amount), color = if (isMine) Color.White else Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(statusLabel, color = if (isMine) Color.White.copy(alpha = 0.85f) else Ids.colors.textSecondary, fontSize = 12.sp)
            if (canRespond && !countering) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OfferActionButton("Accept") { onRespond(offer.id, "ACCEPT", null) }
                    OfferActionButton("Decline") { onRespond(offer.id, "REJECT", null) }
                    OfferActionButton("Counter") { countering = true }
                }
            }
            if (canRespond && countering) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IdsTextField(
                        value = counterAmount,
                        onValueChange = { counterAmount = it },
                        label = "Counter (RWF)",
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.width(140.dp),
                    )
                    OfferActionButton("Send") {
                        val amount = counterAmount.toDoubleOrNull() ?: return@OfferActionButton
                        countering = false
                        counterAmount = ""
                        onRespond(offer.id, "COUNTER", amount)
                    }
                }
            }
        }
    }
}

@Composable
internal fun OfferActionButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Ids.colors.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Ids.colors.textPrimary)
    }
}

// Real KakaoTalk-style gift bubble (2026-07-20) -- see GiftService's own doc comment.
// Renders inline wherever a message carries a real gift, with a real Open/Claim button
// shown only to the recipient of a still-PENDING, not-yet-expired gift.
@Composable
internal fun GiftBubble(gift: GiftDto, isMine: Boolean, currentUserId: String?, onClaim: (String) -> Unit) {
    val canClaim = gift.status == "PENDING" && currentUserId == gift.recipientId &&
        runCatching { Instant.parse(gift.expiresAt).isAfter(Instant.now()) }.getOrDefault(true)
    val statusLabel = when (gift.status) {
        "PENDING" -> if (isMine) "Waiting to be opened" else "Tap to open"
        "CLAIMED" -> "Opened"
        "EXPIRED" -> "Expired — refunded"
        else -> gift.status
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (isMine) Ids.colors.brand else Ids.colors.surfaceSoft)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val prefix = gift.theme?.let { GIFT_THEME_LABELS[it] } ?: "🎁"
            Text("$prefix %,.0f RWF".format(gift.amount), color = if (isMine) Color.White else Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            gift.note?.let { Text("\"$it\"", color = if (isMine) Color.White.copy(alpha = 0.9f) else Ids.colors.textSecondary, fontSize = 12.sp) }
            Text(statusLabel, color = if (isMine) Color.White.copy(alpha = 0.85f) else Ids.colors.textSecondary, fontSize = 12.sp)
            if (canClaim) {
                OfferActionButton("Open gift") { onClaim(gift.id) }
            }
        }
    }
}

// Real KakaoTalk-style 기프티콘 gift voucher bubble (item 137) -- see
// GiftVoucherComposerPanel's own doc comment. Redemption is merchant-side only
// (GiftVoucherService.redeemVoucher's own doc comment), so this bubble is mostly
// status-only. Real one-time "extend expiry" action added item 194 (found via a
// defined-but-uncalled-method sweep: extendGiftVoucherExpiry existed on all 3
// platforms' network layers, wired on bank-mfe since 2026-07-27, never called here) --
// only offered once (voucher.extended), and only within a real 30-day window of the
// current expiry, matching bank-mfe's own GIFT_VOUCHER_EXTENSION_WINDOW_MS exactly.
@Composable
internal fun GiftVoucherBubble(voucher: GiftVoucherDto, isMine: Boolean, onExtend: () -> Unit = {}) {
    val statusLabel = when (voucher.status) {
        "ACTIVE" -> "Present this at the store to redeem"
        "REDEEMED" -> "Redeemed"
        "EXPIRED" -> "Expired"
        else -> voucher.status
    }
    val withinExtensionWindow = try {
        java.time.Instant.parse(voucher.expiresAt).toEpochMilli() - System.currentTimeMillis() <= 30L * 24 * 60 * 60 * 1000
    } catch (_: Exception) {
        false
    }
    val canExtend = voucher.status == "ACTIVE" && !voucher.extended && withinExtensionWindow
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (isMine) Ids.colors.brand else Ids.colors.surfaceSoft)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "🎟️ ${voucher.productNameSnapshot ?: "%,.0f RWF voucher".format(voucher.amount)}",
                color = if (isMine) Color.White else Ids.colors.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
            )
            Text(statusLabel, color = if (isMine) Color.White.copy(alpha = 0.85f) else Ids.colors.textSecondary, fontSize = 12.sp)
            if (voucher.status == "ACTIVE") {
                Text(
                    "Expires ${voucher.expiresAt.take(10)}",
                    color = if (isMine) Color.White.copy(alpha = 0.7f) else Ids.colors.textTertiary,
                    fontSize = 11.sp,
                )
            }
            if (canExtend) {
                Text(
                    "Extend expiry",
                    color = if (isMine) Color.White else Ids.colors.brand,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier.clickable(onClick = onExtend),
                )
            }
        }
    }
}

// Real KakaoTalk-style long-press message menu (2026-08-04) -- see the References table's
// "KakaoTalk — 2025 reply/thread redesign" row. itunda's real Reply/Pin/Delete/Report
// actions already existed (backend-complete, per this file's own doc history) but as
// permanently-visible TextButtons under every single bubble -- a real, sourced KakaoTalk
// UX mismatch, not a missing-capability one: real KakaoTalk reveals this exact toolkit
// only on long-press, keeping the bubble itself clean. Copy is new (real
// LocalClipboardManager, zero backend needed) -- the one item from Kakao's real toolkit
// itunda had no equivalent for at all. Forward (to up to 10 destinations, closed
// 2026-08-04) and Thread (expanding a reply into its own sub-conversation, closed
// 2026-08-05) both shipped -- see MessagingController.getThread's own doc comment on
// the backend for the full sourced account of the last one.
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MessageBubble(
    message: MessageDto, isMine: Boolean, currentUserId: String?, offer: OfferBubbleData?, gift: GiftDto?,
    voucher: GiftVoucherDto? = null,
    emoticonImageUrl: String? = null,
    showTimestamp: Boolean = true,
    onToggleReaction: (String) -> Unit, onRespondToOffer: (String, String, Double?) -> Unit, onClaimGift: (String) -> Unit,
    onExtendVoucher: (String) -> Unit = {},
    onReply: (MessageDto) -> Unit = {},
    onOpenThread: (MessageDto) -> Unit = {},
    onDelete: (String) -> Unit = {},
    onPin: (MessageDto) -> Unit = {},
    onForward: (MessageDto) -> Unit = {},
    onReportMessage: (String, String) -> Unit = { _, _ -> },
) {
    var reportOpen by remember { mutableStateOf(false) }
    var reportReason by remember { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val isPlainTextBubble = gift == null && voucher == null && offer == null && message.emoticonId == null && message.imageUrl == null
    Column(modifier = Modifier.fillMaxWidth()) {
        if (message.forwardedFromMessageId != null) {
            // The backend stamps a real DIRECT/GROUP origin on every forward
            // (MessageForwardService), which this label ignored until 2026-08-14 --
            // "forwarded from a group" is materially different context for the reader
            // than a forward out of a 1:1 chat, same distinction Telegram/KakaoTalk draw.
            val forwardedLabel = when (message.forwardedFromType) {
                "GROUP" -> "↪ Forwarded from a group chat"
                "DIRECT" -> "↪ Forwarded from a chat"
                else -> "↪ Forwarded"
            }
            Text(forwardedLabel, color = Ids.colors.textSecondary, fontSize = 10.sp, modifier = Modifier.fillMaxWidth(), textAlign = if (isMine) TextAlign.End else TextAlign.Start)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start) {
            // Long-press trigger on the outer wrapper, not just the plain-text bubble --
            // Reply/Pin/Delete/Report must stay reachable for gift/voucher/offer/emoticon
            // messages too (a real regression risk in this refactor otherwise: those
            // bubble types have their own internal Claim/Extend/Respond buttons, which
            // still consume ordinary taps first, so this outer long-press only fires on a
            // long-press that isn't already claimed by one of those).
            Box(modifier = Modifier.combinedClickable(onClick = {}, onLongClick = { menuOpen = true })) {
                if (gift != null) {
                    GiftBubble(gift, isMine, currentUserId, onClaimGift)
                } else if (voucher != null) {
                    GiftVoucherBubble(voucher, isMine, onExtend = { onExtendVoucher(voucher.id) })
                } else if (offer != null) {
                    OfferBubble(offer, isMine, currentUserId, onRespondToOffer)
                } else if (message.emoticonId != null) {
                    EmoticonBubble(emoticonImageUrl)
                } else if (message.imageUrl != null) {
                    // Real photo message (2026-08-04) -- see SendMessageRequest's own doc
                    // comment. imageUrl is always a real /api/v1/uploads/ URL (backend-
                    // enforced), never a placeholder.
                    AsyncImage(
                        model = message.imageUrl,
                        contentDescription = "Photo",
                        modifier = Modifier.widthIn(max = 220.dp).clip(RoundedCornerShape(16.dp)),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isMine) Ids.colors.brand else Ids.colors.surfaceSoft)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        Text(message.body, color = if (isMine) Color.White else Ids.colors.textPrimary, fontSize = 14.sp)
                    }
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    if (isPlainTextBubble) {
                        DropdownMenuItem(text = { Text("Copy") }, onClick = { clipboardManager.setText(AnnotatedString(message.body)); menuOpen = false })
                    }
                    DropdownMenuItem(text = { Text("Reply") }, onClick = { onReply(message); menuOpen = false })
                    DropdownMenuItem(text = { Text("Pin") }, onClick = { onPin(message); menuOpen = false })
                    DropdownMenuItem(text = { Text("Forward") }, onClick = { onForward(message); menuOpen = false })
                    if (isMine && message.deletedAt == null) {
                        DropdownMenuItem(text = { Text("Delete") }, onClick = { onDelete(message.id); menuOpen = false })
                    }
                    if (!isMine) {
                        DropdownMenuItem(text = { Text("Report message") }, onClick = { reportOpen = true; menuOpen = false })
                    }
                }
            }
        }
        MessageReactionsRow(message.reactions, currentUserId, isMine, onToggleReaction)
        val statusParts = buildList {
            if (isMine && message.readAt == null) add("1")
            if (showTimestamp) add(chatMessageTime(message.sentAt))
        }
        if (statusParts.isNotEmpty()) {
            Text(
                statusParts.joinToString(" · "),
                color = Ids.colors.textSecondary,
                fontSize = 10.sp,
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                textAlign = if (isMine) androidx.compose.ui.text.style.TextAlign.End else androidx.compose.ui.text.style.TextAlign.Start,
            )
        }
        // Real Thread support (2026-08-05) -- a real "N replies" affordance opening its
        // own sub-conversation view, matching Kakao's confirmed real reply-thread
        // pattern (docs/DESIGN_REFERENCES.md Talk section recommendation #3).
        if (message.replyCount > 0) {
            Text(
                "${message.replyCount} ${if (message.replyCount == 1L) "reply" else "replies"} →",
                color = Ids.colors.brand,
                fontSize = 11.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp).clickable { onOpenThread(message) },
                textAlign = if (isMine) androidx.compose.ui.text.style.TextAlign.End else androidx.compose.ui.text.style.TextAlign.Start,
            )
        }
    }
    if (reportOpen) AlertDialog(
        onDismissRequest = { reportOpen = false },
        title = { Text("Report message") },
        text = { IdsTextField(value = reportReason, onValueChange = { if (it.length <= 180) reportReason = it }, label = "Reason") },
        confirmButton = { TextButton(onClick = { if (reportReason.trim().length >= 3) { onReportMessage(message.id, reportReason.trim()); reportReason = ""; reportOpen = false } }) { Text("Send") } },
        dismissButton = { TextButton(onClick = { reportOpen = false }) { Text("Cancel") } },
    )
}

// ============================== HOOD (Marketplace) ==============================
