package rw.itunda.feature.talk.impl

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.filled.PushPin as PushPinFilled
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.filled.Star as StarFilled
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import retrofit2.HttpException
import rw.itunda.core.designsystem.components.EmptyState
import rw.itunda.core.designsystem.components.ErrorCard
import rw.itunda.core.designsystem.components.IdsAvatar
import rw.itunda.core.designsystem.components.IdsButton
import rw.itunda.core.designsystem.components.IdsButtonSize
import rw.itunda.core.designsystem.components.IdsButtonVariant
import rw.itunda.core.designsystem.components.IdsTextField
import rw.itunda.core.designsystem.components.SkeletonBlock
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.ConversationSummaryDto
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.SetConversationArchivedRequest
import rw.itunda.core.network.SetConversationFavoriteRequest
import rw.itunda.core.network.SetConversationPinnedToTopRequest
import rw.itunda.core.network.StartConversationRequest
import rw.itunda.core.network.TalkContactDto
import rw.itunda.core.network.superAppErrorMessage
import java.io.IOException


@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun DirectMessagesList(
    conversations: List<ConversationSummaryDto>?,
    archivedConversations: List<ConversationSummaryDto>?,
    error: String?,
    presence: Map<String, Boolean>,
    onRetry: () -> Unit,
    onStarted: (String) -> Unit,
    onOpen: (String) -> Unit,
    onArchiveChanged: () -> Unit,
    // Real pagination-discard fix (same systemic gap fixed on web, 2026-09-11)
    // -- independent hasMore/loadingMore per list (active vs archived), since
    // a user can exhaust one while the other still has more pages.
    hasMore: Boolean = false,
    archivedHasMore: Boolean = false,
    loadingMore: Boolean = false,
    archivedLoadingMore: Boolean = false,
    onLoadMore: () -> Unit = {},
    onLoadMoreArchived: () -> Unit = {},
) {
    var startPhoneNumber by remember { mutableStateOf("") }
    var startError by remember { mutableStateOf<String?>(null) }
    var starting by remember { mutableStateOf(false) }
    var contacts by remember { mutableStateOf<List<TalkContactDto>?>(null) }
    // Real fix, found live 2026-08-05: this used to filter on `quiet` (mute) and
    // mislabel the result "Archived" -- there was no real archive concept on the
    // backend yet, so muting was repurposed to also hide a conversation from the
    // main list. Now that a real, distinct `archived` field exists
    // (ConversationPreference.archived), muted conversations stay visible in the
    // main list (matching real KakaoTalk: muting only silences notifications, it
    // never hides a room) and this toggle switches to the real archived list.
    var showArchived by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    fun setArchived(conversationId: String, archived: Boolean) {
        coroutineScope.launch {
            try {
                NetworkClient.apiService.setConversationArchived(conversationId, SetConversationArchivedRequest(archived))
                onArchiveChanged()
            } catch (_: Exception) {
                // Real, non-critical -- a failed archive/unarchive just leaves the row
                // where it was; the user can retry the swipe.
            }
        }
    }

    // Real KakaoTalk 채팅방 상단 고정 (2026-08-18) -- see ConversationSummaryDto
    // .pinnedToTop's own doc comment. Same fire-and-refresh shape as setArchived above.
    fun togglePinnedToTop(conversationId: String, pinned: Boolean) {
        coroutineScope.launch {
            try {
                NetworkClient.apiService.setConversationPinnedToTop(conversationId, SetConversationPinnedToTopRequest(pinned))
                onArchiveChanged()
            } catch (_: Exception) {
                // Real, non-critical -- a failed pin/unpin just leaves the row where it
                // was; the user can retry the tap.
            }
        }
    }

    // Real KakaoTalk favorite chat toggle (itunda Talk redesign, 2026-08-28) -- see
    // ConversationSummaryDto.favorite's own doc comment. Same fire-and-refresh shape
    // as togglePinnedToTop above.
    fun toggleFavorite(conversationId: String, favorite: Boolean) {
        coroutineScope.launch {
            try {
                NetworkClient.talkApi.setConversationFavorite(conversationId, SetConversationFavoriteRequest(favorite))
                onArchiveChanged()
            } catch (_: Exception) {
                // Real, non-critical -- a failed favorite toggle just leaves the row
                // where it was; the user can retry the tap.
            }
        }
    }

    LaunchedEffect(Unit) {
        contacts = try {
            NetworkClient.apiService.getTalkContacts().takeIf { it.success }?.contacts ?: emptyList()
        } catch (_: Exception) {
            // Starting by phone remains available when the contact directory cannot load.
            emptyList()
        }
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(Ids.layout.cardGap)) {
        item {
            // Real fix (2026-08-24, flat-design sweep): dropped the Card wrapper --
            // this screen's own main content, a lone form (docs/UI_UX_GUIDELINES.md §10).
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                    Text("New chat", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Choose a saved contact, or enter their phone number.", color = Ids.colors.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp, bottom = 12.dp))
                    if (!contacts.isNullOrEmpty()) {
                        Text("Your contacts", color = Ids.colors.textSecondary, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(top = 4.dp, bottom = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            contacts.orEmpty().forEach { contact ->
                                TextButton(
                                    enabled = !starting,
                                    onClick = {
                                        starting = true
                                        startError = null
                                        coroutineScope.launch {
                                            try {
                                                val res = NetworkClient.apiService.startConversation(
                                                    StartConversationRequest(otherUserId = contact.userId),
                                                )
                                                if (res.success) onStarted(res.conversation.id)
                                            } catch (e: HttpException) {
                                                startError = superAppErrorMessage(e)
                                            } catch (e: IOException) {
                                                startError = "Couldn't reach itunda. Check your connection and try again."
                                            } finally {
                                                starting = false
                                            }
                                        }
                                    },
                                ) { Text(contact.name, maxLines = 1) }
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IdsTextField(
                            value = startPhoneNumber,
                            onValueChange = { startPhoneNumber = it },
                            label = "Phone number",
                            placeholder = "+250788123456",
                            keyboardType = KeyboardType.Phone,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(Ids.colors.brand)
                                .pressScaleClickable(enabled = !starting && startPhoneNumber.isNotBlank()) {
                                    starting = true
                                    startError = null
                                    coroutineScope.launch {
                                        try {
                                            val res = NetworkClient.apiService.startConversation(StartConversationRequest(phoneNumber = startPhoneNumber.trim()))
                                            if (res.success) {
                                                startPhoneNumber = ""
                                                onStarted(res.conversation.id)
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
                    startError?.let { Text(it, color = Ids.colors.danger, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) }
            }
        }
        val archivedCount = archivedConversations?.size ?: 0
        if (archivedCount > 0) item {
            TextButton(onClick = { showArchived = !showArchived }) {
                Text(if (showArchived) "Show active chats" else "Archived ($archivedCount)", color = Ids.colors.textSecondary)
            }
        }
        // Real KakaoTalk pinned-rooms-float-to-top behavior (2026-08-18) -- see
        // ConversationSummaryDto.pinnedToTop's own doc comment. sortedByDescending uses
        // a stable sort (Kotlin delegates to Collections.sort/TimSort on List), so
        // unpinned rows keep their existing lastMessageAt order beneath the pinned ones,
        // same real behavior bank-mfe's own DirectMessagesList already established.
        // Archived rooms are never re-sorted -- pinning only ever applies to the active
        // list, matching setArchived's own active-list-only gating below.
        val visibleList = if (showArchived) archivedConversations else conversations?.sortedByDescending { it.pinnedToTop }
        if (error != null) {
            item { ErrorCard(error, onRetry = onRetry) }
        } else if (visibleList == null) {
            item { SkeletonBlock() }
        } else if (visibleList.isEmpty()) {
            item {
                EmptyState(
                    if (showArchived) "You haven't archived any chats." else "No conversations yet — start one from Friends, or say hi to someone you already know.",
                    icon = Icons.Outlined.ChatBubbleOutline,
                )
            }
        } else {
            items(visibleList, key = { it.conversationId }) { c ->
                // Real Toss-sourced "layering illusion" reorder animation
                // (2026-08-29, toss.tech/article/interaction's own real "Account
                // Organization Animation" example -- reordering a list should
                // animate the move, not jump). Pinning/unpinning used to snap this
                // row to its new position with zero motion; animateItemPlacement()
                // auto-animates the position change since `key` above was already
                // stable, no other logic change needed.
                SwipeableConversationRow(
                    conversation = c,
                    online = presence[c.otherUserId] == true,
                    isArchived = showArchived,
                    onClick = { onOpen(c.conversationId) },
                    onArchiveToggle = { setArchived(c.conversationId, !showArchived) },
                    onPinToggle = { togglePinnedToTop(c.conversationId, !c.pinnedToTop) },
                    onFavoriteToggle = { toggleFavorite(c.conversationId, !c.favorite) },
                    modifier = Modifier.animateItemPlacement(),
                )
            }
            val listHasMore = if (showArchived) archivedHasMore else hasMore
            val listLoadingMore = if (showArchived) archivedLoadingMore else loadingMore
            if (listHasMore) {
                item {
                    IdsButton(
                        text = if (listLoadingMore) "Loading…" else "Load more",
                        onClick = if (showArchived) onLoadMoreArchived else onLoadMore,
                        enabled = !listLoadingMore,
                        variant = IdsButtonVariant.Tinted,
                        size = IdsButtonSize.Medium,
                    )
                }
            }
        }
    }
}

// Real swipe actions (2026-08-05) -- closes docs/DESIGN_REFERENCES.md Talk
// recommendation #4's remaining swipe-gesture half (archive itself closed above, in
// DirectMessagesList/setArchived). Real KakaoTalk supports swipe in both directions
// (favorite/pin one way, archive/leave the other); scoped honestly to a single
// swipe-to-archive action here, matching this app's own real archive feature -- pin
// already has its own long-press-menu entry point on individual messages, and
// itunda's Talk has no per-conversation "favorite" concept to wire a second swipe to.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SwipeableConversationRow(
    conversation: ConversationSummaryDto,
    online: Boolean,
    isArchived: Boolean,
    onClick: () -> Unit,
    onArchiveToggle: () -> Unit,
    onPinToggle: () -> Unit,
    onFavoriteToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onArchiveToggle()
                true
            } else {
                false
            }
        },
    )
    SwipeToDismissBox(
        modifier = modifier,
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(Ids.layout.cardCornerRadius))
                    .background(if (isArchived) Ids.colors.brand else Ids.colors.danger)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    if (isArchived) Icons.Outlined.Unarchive else Icons.Outlined.Archive,
                    contentDescription = if (isArchived) "Unarchive" else "Archive",
                    tint = Color.White,
                )
            }
        },
    ) {
        // Real fix, found live 2026-08-05: ConversationRow has no opaque background of
        // its own -- it always relied on sitting directly against the screen's own
        // background color. SwipeToDismissBox's foreground content needs real opacity
        // to actually cover backgroundContent at rest; without this the red/blue swipe
        // reveal showed through underneath the row even when not swiping.
        Box(modifier = Modifier.background(Ids.colors.background)) {
            ConversationRow(
                conversation,
                online = online,
                onClick = onClick,
                // Real KakaoTalk pin-to-top toggle (2026-08-18) -- only offered on the
                // active list, matching the swipe-to-archive action above and bank-mfe's
                // own active-list-only gating (pinning an archived room to the top of
                // the active list would be a confusing, silently-unarchiving side effect).
                onPinToggle = if (isArchived) null else onPinToggle,
                onFavoriteToggle = if (isArchived) null else onFavoriteToggle,
            )
        }
    }
}

@Composable
// Real structural fix, 2026-07-21: this row used to be shaped like this app's own
// banking/dashboard cards (rounded, backgrounded, individually spaced) -- the wrong
// reference for a chat list. A messaging list is scanned quickly and often, at real
// volume, unlike a dashboard someone glances at occasionally -- KakaoTalk's own real,
// sourced friend-list-row spec (48px rounded-square avatar/12px radius, 16px/14px
// text, 64dp fixed row height, 16dp horizontal padding) optimizes specifically for
// that fast, repeated scanning, which this app's chat list needs just as much as the
// real KakaoTalk does. Brand color is deliberately NOT changed to match (unread
// badge stays itunda's own blue, not Kakao yellow) -- a real, connected ecosystem
// like Kakao's own keeps one consistent brand color across every surface; only the
// row's real, sourced *structure* is worth borrowing here, not its color.
internal fun ConversationRow(
    conversation: ConversationSummaryDto,
    online: Boolean,
    onClick: () -> Unit,
    onPinToggle: (() -> Unit)? = null,
    onFavoriteToggle: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .pressScaleClickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.BottomEnd) {
            // Real fix, found live 2026-08-05: this rendered Icons.Outlined.Send -- the
            // SEND-message glyph -- as the other person's avatar, not a person at all.
            // itunda has no other-user profile photo on ConversationSummaryDto yet (a
            // real, separate backend+DTO gap, not fixed here); IdsAvatar's colored-
            // initials fallback is still correct and honest, unlike a send icon.
            IdsAvatar(name = conversation.otherUserName)
            if (online) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(Ids.colors.success),
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(conversation.otherUserName, color = Ids.colors.textPrimary, fontWeight = FontWeight.Medium, fontSize = 16.sp)
                // listConversations already carries real per-row quiet/pinnedMessageId
                // (backend resolves both from ConversationPreference on every call), but
                // the row rendered neither until 2026-08-14 -- the state was only ever
                // re-fetched lazily once a thread was already open, so the list itself
                // could never show why a muted chat stayed silent. Same 🔇/📌 at-a-glance
                // treatment real KakaoTalk gives its chat list.
                if (conversation.quiet) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        Icons.Outlined.NotificationsOff,
                        contentDescription = "Muted",
                        tint = Ids.colors.textTertiary,
                        modifier = Modifier.size(14.dp),
                    )
                }
                if (conversation.pinnedMessageId != null) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        Icons.Outlined.PushPin,
                        contentDescription = "Has a pinned message",
                        tint = Ids.colors.textTertiary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(conversation.lastMessagePreview ?: "No messages yet", color = Ids.colors.textSecondary, fontSize = 14.sp, maxLines = 1)
        }
        // Real KakaoTalk 채팅방 상단 고정 (pin chat room to top) (2026-08-18) -- see
        // ConversationSummaryDto.pinnedToTop's own doc comment. Always-visible icon
        // button, same convention bank-mfe's own DirectMessagesList row already
        // established for this action (distinct from the swipe-to-archive gesture,
        // which stays on the background layer beneath this row).
        if (onPinToggle != null) {
            IconButton(onClick = onPinToggle, modifier = Modifier.size(32.dp)) {
                Icon(
                    if (conversation.pinnedToTop) Icons.Filled.PushPinFilled else Icons.Outlined.PushPin,
                    contentDescription = if (conversation.pinnedToTop) "Unpin from top" else "Pin to top",
                    tint = if (conversation.pinnedToTop) Ids.colors.brand else Ids.colors.textTertiary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        // Real KakaoTalk favorite chat toggle (itunda Talk redesign, 2026-08-28) --
        // see ConversationSummaryDto.favorite's own doc comment. Same always-visible
        // icon-button convention as the pin-to-top toggle just above.
        if (onFavoriteToggle != null) {
            IconButton(onClick = onFavoriteToggle, modifier = Modifier.size(32.dp)) {
                Icon(
                    if (conversation.favorite) Icons.Filled.StarFilled else Icons.Outlined.StarBorder,
                    contentDescription = if (conversation.favorite) "Remove from favorites" else "Add to favorites",
                    tint = if (conversation.favorite) Ids.colors.warning else Ids.colors.textTertiary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        if (conversation.unreadCount > 0) {
            // A muted chat's badge stays neutral rather than brand-blue -- it still
            // reports the real count, but doesn't compete for attention the user
            // explicitly asked this conversation not to demand. Same distinction
            // KakaoTalk draws between a muted and an unmuted unread badge.
            val badgeColor = if (conversation.quiet) Ids.colors.textTertiary else Ids.colors.brand
            Box(modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(badgeColor).padding(horizontal = 8.dp, vertical = 3.dp)) {
                Text(conversation.unreadCount.toString(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

