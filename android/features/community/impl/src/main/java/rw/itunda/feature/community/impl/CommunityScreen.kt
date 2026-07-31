package rw.itunda.feature.community.impl

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Comment
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import kotlinx.coroutines.launch
import retrofit2.HttpException
import rw.itunda.core.designsystem.components.BackTopBar
import rw.itunda.core.designsystem.components.EmptyState
import rw.itunda.core.designsystem.components.ErrorCard
import rw.itunda.core.designsystem.components.HoodReportAction
import rw.itunda.core.designsystem.components.ListingActionButton
import rw.itunda.core.designsystem.components.NeighborhoodSetupPrompt
import rw.itunda.core.designsystem.components.SkeletonBlock
import rw.itunda.core.designsystem.components.relativeTimeAgo
import rw.itunda.core.designsystem.components.rememberRealLocationRequester
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.AddCommunityCommentRequest
import rw.itunda.core.network.CommunityCategoryDto
import rw.itunda.core.network.CommunityCommentWithAuthorDto
import rw.itunda.core.network.CommunityPostDto
import rw.itunda.core.network.CreateCommunityPostRequest
import rw.itunda.core.network.FinalizeGroupBuyRequest
import rw.itunda.core.network.MeetupSessionDto
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.ScheduleMeetupSessionsRequest
import rw.itunda.core.network.TokenStore
import rw.itunda.core.network.superAppErrorMessage
import java.io.IOException

// Fourth and final Hood-mode Feature extraction (2026-07-23) after Marketplace/Jobs/
// Property, same template -- see features/marketplace/impl/.../MarketplaceScreen.kt's
// own header comment for the full account. Unlike the other three, Community has no
// lat/lng "directions" feature, so there's no routeMiniMap slot to inject here -- this
// module is fully self-sufficient with no exception.

private enum class CommunityView { BROWSE, NEARBY, NEIGHBORHOOD, MINE }

@Composable
fun CommunityContent(onOpenGroupChat: (String) -> Unit = {}) {
    var view by remember { mutableStateOf(CommunityView.BROWSE) }
    var categories by remember { mutableStateOf<List<CommunityCategoryDto>>(emptyList()) }
    var activeCategory by remember { mutableStateOf<String?>(null) }
    var posts by remember { mutableStateOf<List<CommunityPostDto>?>(null) }
    // Real 같이해요 (join-together) group join counts (2026-07-24) -- postId -> real
    // member count of that meetup's group chat, closing docs/DESIGN_REFERENCES.md
    // Section 4 recommendation #4.
    var joinedCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var joiningPostId by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var showNewPost by remember { mutableStateOf(false) }
    var openPostId by remember { mutableStateOf<String?>(null) }
    var neighborhoodName by remember { mutableStateOf<String?>(null) }
    var neighborhoodChecked by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val currentUserId = remember { NetworkClient.currentTokenStore().let(TokenStore::getUserId) }
    val requestNearbyLocation = rememberRealLocationRequester(
        onLocating = {},
        onSuccess = { lat, lng ->
            posts = null
            coroutineScope.launch {
                try {
                    val res = NetworkClient.apiService.getNearbyCommunityPosts(lat, lng)
                    if (res.success) { posts = res.posts; joinedCounts = res.joinedCounts }
                    error = null
                } catch (e: HttpException) {
                    error = superAppErrorMessage(e)
                    posts = emptyList()
                } catch (e: IOException) {
                    error = "Couldn't load nearby posts. Check your connection and try again."
                    posts = emptyList()
                }
            }
        },
        onError = { message -> error = "$message You can still use Feed or Neighborhood."; posts = emptyList() },
    )

    LaunchedEffect(Unit) {
        try { categories = NetworkClient.apiService.getCommunityCategories().categories } catch (e: Exception) { /* chips just won't render */ }
    }

    fun load() {
        posts = null
        if (view == CommunityView.NEARBY) {
            requestNearbyLocation()
            return
        }
        if (view == CommunityView.NEIGHBORHOOD) {
            neighborhoodChecked = false
            coroutineScope.launch {
                try {
                    val profileRes = NetworkClient.authApi.getProfile()
                    val res = NetworkClient.apiService.getCommunityPostsMyNeighborhood(activeCategory)
                    neighborhoodName = profileRes.user.neighborhood
                    if (res.success) { posts = res.posts; joinedCounts = res.joinedCounts }
                    error = null
                } catch (e: HttpException) {
                    if (e.code() == 400) {
                        neighborhoodName = null
                        posts = emptyList()
                        error = null
                    } else {
                        error = superAppErrorMessage(e)
                    }
                } catch (e: IOException) {
                    error = "Couldn't reach itunda. Check your connection and try again."
                } finally {
                    neighborhoodChecked = true
                }
            }
            return
        }
        coroutineScope.launch {
            try {
                val res = if (view == CommunityView.BROWSE) NetworkClient.apiService.browseCommunityPosts(activeCategory) else NetworkClient.apiService.getMyCommunityPosts()
                if (res.success) { posts = res.posts; joinedCounts = res.joinedCounts }
                error = null
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            }
        }
    }
    LaunchedEffect(view, activeCategory) { load() }

    // Real 같이해요 (join-together) explicit 참여하기 tap (2026-07-24) -- closes
    // docs/DESIGN_REFERENCES.md Section 4 recommendation #4. Reuses the exact same
    // onOpenGroupChat/onMessageSeller callback Marketplace/Jobs/Property already share
    // for "hand off to Talk" -- TalkScreen.kt's own initialConversationId effect was
    // extended to also check `groups`, so a real GroupConversation id works here too,
    // no new navigation plumbing needed.
    fun joinMeetup(postId: String) {
        joiningPostId = postId
        coroutineScope.launch {
            try {
                val res = NetworkClient.apiService.joinCommunityMeetup(postId)
                if (res.success) {
                    joinedCounts = joinedCounts + (postId to ((joinedCounts[postId] ?: 0) + 1))
                    onOpenGroupChat(res.groupId)
                }
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                joiningPostId = null
            }
        }
    }

    if (openPostId != null) {
        CommunityPostDetailScreen(postId = openPostId!!, onBack = { openPostId = null; load() })
        return
    }

    if (showNewPost) {
        BackHandler { showNewPost = false }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = Ids.layout.screenHorizontal, vertical = Ids.layout.screenVertical),
        verticalArrangement = Arrangement.spacedBy(Ids.layout.cardGap),
    ) {
        item {
            // Flat, horizontally-scrolling category strip (2026-07-24), same
            // Karrot/Toss-Shopping-style treatment as Marketplace's own Browse/
            // Near me/etc row -- replacing a filled-pill segmented control.
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                listOf(CommunityView.BROWSE to "Feed", CommunityView.NEARBY to "Near me", CommunityView.NEIGHBORHOOD to "Neighborhood", CommunityView.MINE to "My posts").forEach { (v, label) ->
                    val selected = v == view
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { view = v }) {
                        Text(
                            label,
                            color = if (selected) Ids.colors.textPrimary else Ids.colors.textSecondary,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 8.dp, bottom = 6.dp),
                        )
                        Box(
                            modifier = Modifier
                                .height(2.dp)
                                .width(18.dp)
                                .background(if (selected) Ids.colors.brand else Color.Transparent, RoundedCornerShape(1.dp)),
                        )
                    }
                }
            }
        }
        if ((view == CommunityView.BROWSE || view == CommunityView.NEIGHBORHOOD) && categories.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    categories.forEach { c ->
                        val active = activeCategory == c.id
                        Box(
                            modifier = Modifier
                                .background(if (active) Ids.colors.brand else Ids.colors.surface, RoundedCornerShape(999.dp))
                                .border(1.dp, if (active) Ids.colors.brand else Ids.colors.textSecondary.copy(alpha = 0.3f), RoundedCornerShape(999.dp))
                                .clickable { activeCategory = if (active) null else c.id }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        ) { Text(c.label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (active) Color.White else Ids.colors.textPrimary) }
                    }
                }
            }
        }
        if (view == CommunityView.MINE) {
            item {
                if (!showNewPost) {
                    Box(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Ids.colors.brand).clickable { showNewPost = true }.padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text("+ Write a post", color = Color.White, fontWeight = FontWeight.Bold) }
                } else {
                    NewCommunityPostForm(categories, onCreated = { showNewPost = false; load() }, onCancel = { showNewPost = false })
                }
            }
        }
        if (view == CommunityView.NEIGHBORHOOD && neighborhoodChecked && neighborhoodName == null) {
            item { NeighborhoodSetupPrompt(onDone = { load() }) }
        }
        if (view == CommunityView.NEIGHBORHOOD && neighborhoodName != null) {
            item { Text("Your neighborhood: $neighborhoodName", color = Ids.colors.textSecondary, fontSize = 13.sp) }
        }
        if (error != null) {
            item { ErrorCard(error!!, onRetry = ::load) }
        } else if (posts == null) {
            item { SkeletonBlock() }
        } else if (posts!!.isEmpty() && (view != CommunityView.NEIGHBORHOOD || neighborhoodName != null)) {
            item {
                Text(
                    when (view) {
                        CommunityView.BROWSE -> "No posts yet."
                        CommunityView.NEARBY -> "No posts near you yet."
                        CommunityView.NEIGHBORHOOD -> "No posts in your neighborhood yet."
                        CommunityView.MINE -> "You haven't posted anything yet."
                    },
                    color = Ids.colors.textSecondary, fontSize = 14.sp,
                )
            }
        } else if (posts!!.isNotEmpty()) {
            // Real 같이해요 (join-together) pinned mid-feed slot (2026-07-24) --
            // Karrot's real board gives meetup posts a dedicated slot instead of
            // mixing them purely chronologically into the rest of the feed (see
            // docs/DESIGN_REFERENCES.md Section 4 recommendation #4). "My posts"
            // stays plain chronological -- pinning your own management list would
            // just be noise, not a discovery aid.
            val (meetupsRaw, regular) = if (view != CommunityView.MINE) {
                posts!!.partition { it.category == "meetup" }
            } else {
                emptyList<CommunityPostDto>() to posts!!
            }
            // Real 당근모임-style soonest-first ordering (2026-07-25) -- a real
            // upcoming-events list reads by "what's happening soon," not by
            // when it was posted; a meetup with no real eventDate (created before
            // this field existed) sorts last, never dropped.
            val meetups = meetupsRaw.sortedBy { it.eventDate ?: "9999" }
            if (meetups.isNotEmpty()) {
                item {
                    Text("🎉 Meetups", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                items(meetups, key = { "meetup_${it.id}" }) { post ->
                    CommunityPostCard(
                        post = post,
                        categoryLabel = categories.firstOrNull { it.id == post.category }?.label ?: post.category,
                        isMine = post.authorId == currentUserId,
                        joinedCount = joinedCounts[post.id] ?: 0,
                        joining = joiningPostId == post.id,
                        onJoin = { joinMeetup(post.id) },
                        onOpen = { openPostId = post.id },
                        onRemoved = ::load,
                    )
                }
            }
            items(regular, key = { it.id }) { post ->
                CommunityPostCard(
                    post = post,
                    categoryLabel = categories.firstOrNull { it.id == post.category }?.label ?: post.category,
                    isMine = view == CommunityView.MINE || post.authorId == currentUserId,
                    joinedCount = joinedCounts[post.id] ?: 0,
                    joining = joiningPostId == post.id,
                    onJoin = { joinMeetup(post.id) },
                    onOpen = { openPostId = post.id },
                    onRemoved = ::load,
                )
            }
        }
    }
}

// Real 당근모임-style structured event date display (2026-07-25) -- falls back to the
// raw ISO string on any parse failure, never a fabricated date.
private fun formatMeetupDate(iso: String): String = try {
    val instant = java.time.Instant.parse(iso)
    val local = instant.atZone(java.time.ZoneOffset.UTC)
    "%04d-%02d-%02d %02d:%02d".format(local.year, local.monthValue, local.dayOfMonth, local.hour, local.minute)
} catch (e: Exception) {
    iso
}

@Composable
private fun NewCommunityPostForm(categories: List<CommunityCategoryDto>, onCreated: () -> Unit, onCancel: () -> Unit) {
    var category by remember { mutableStateOf(categories.firstOrNull()?.id ?: "") }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    // Real 당근모임-style mandatory date-setting + real capacity cap (2026-07-25) --
    // only meaningful/shown for the meetup category. See backend
    // CommunityService.createPost's own doc comment for why eventDate is required for
    // this one category.
    var eventDateText by remember { mutableStateOf("") }
    var eventTimeText by remember { mutableStateOf("") }
    var capacityText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    var shareLocation by remember { mutableStateOf(false) }
    var myLocation by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var locating by remember { mutableStateOf(false) }
    val requestLocation = rememberRealLocationRequester(
        onLocating = { locating = it },
        onSuccess = { lat, lng -> myLocation = lat to lng; shareLocation = true },
        onError = { error = it },
    )

    Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = Ids.colors.surface), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Write a post", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                categories.forEach { c ->
                    val selected = category == c.id
                    Box(
                        modifier = Modifier
                            .background(if (selected) Ids.colors.brand else Ids.colors.surfaceSoft, RoundedCornerShape(999.dp))
                            .clickable { category = c.id }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) { Text(c.label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (selected) Color.White else Ids.colors.textPrimary) }
                }
            }
            OutlinedTextField(value = title, onValueChange = { title = it }, placeholder = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = body, onValueChange = { body = it }, placeholder = { Text("What's going on in the neighborhood?") }, modifier = Modifier.fillMaxWidth())
            if (category == "meetup") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = eventDateText, onValueChange = { eventDateText = it },
                        placeholder = { Text("Date (YYYY-MM-DD)") }, singleLine = true, modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = eventTimeText, onValueChange = { eventTimeText = it },
                        placeholder = { Text("Time (HH:mm)") }, singleLine = true, modifier = Modifier.weight(1f),
                    )
                }
                OutlinedTextField(
                    value = capacityText, onValueChange = { capacityText = it },
                    placeholder = { Text("Max people (optional -- blank means unlimited)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
            }
            Box(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Ids.colors.surfaceSoft)
                    .clickable(enabled = !locating) { if (shareLocation) shareLocation = false else requestLocation() }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) { Text(if (locating) "Finding your real location…" else if (shareLocation) "📍 Location shared with nearby neighbors" else "📍 Share location for nearby neighbors (optional)", fontSize = 13.sp, color = if (shareLocation) Ids.colors.brand else Ids.colors.textSecondary) }
            error?.let { Text(it, color = Ids.colors.danger, fontSize = 12.sp) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Ids.colors.brand)
                        .clickable(enabled = !submitting) {
                            if (title.isBlank() || body.isBlank() || category.isBlank()) {
                                error = "Fill in every field."
                                return@clickable
                            }
                            var eventDateIso: String? = null
                            var capacity: Int? = null
                            if (category == "meetup") {
                                if (eventDateText.isBlank() || eventTimeText.isBlank()) {
                                    error = "A meetup needs a real date and time."
                                    return@clickable
                                }
                                eventDateIso = "${eventDateText.trim()}T${eventTimeText.trim()}:00Z"
                                try {
                                    java.time.Instant.parse(eventDateIso)
                                } catch (e: Exception) {
                                    error = "Enter a real date (YYYY-MM-DD) and time (HH:mm)."
                                    return@clickable
                                }
                                capacity = capacityText.trim().ifBlank { null }?.toIntOrNull()
                                if (capacityText.isNotBlank() && capacity == null) {
                                    error = "Max people must be a whole number."
                                    return@clickable
                                }
                            }
                            submitting = true
                            error = null
                            coroutineScope.launch {
                                try {
                                    val loc = if (shareLocation) myLocation else null
                                    val res = NetworkClient.apiService.createCommunityPost(
                                        CreateCommunityPostRequest(category, title, body, loc?.first, loc?.second, eventDateIso, capacity),
                                    )
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
                ) { Text(if (submitting) "Posting…" else "Post", color = Color.White, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun CommunityPostCard(
    post: CommunityPostDto, categoryLabel: String, isMine: Boolean, onOpen: () -> Unit, onRemoved: () -> Unit,
    joinedCount: Int = 0, joining: Boolean = false, onJoin: () -> Unit = {},
) {
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    Card(
        shape = RoundedCornerShape(Ids.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = Ids.colors.surface),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("$categoryLabel · ${relativeTimeAgo(post.createdAt)}", color = Ids.colors.brand, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                if (isMine) {
                    ListingActionButton("Remove", busy) {
                        busy = true
                        coroutineScope.launch {
                            try {
                                NetworkClient.apiService.removeCommunityPost(post.id)
                                onRemoved()
                            } catch (e: HttpException) {
                                error = superAppErrorMessage(e)
                            } finally {
                                busy = false
                            }
                        }
                    }
                }
            }
            Text(post.title, color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(post.body, color = Ids.colors.textSecondary, fontSize = 13.sp, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Text("❤️ ${post.likeCount} · 💬 ${post.commentCount}", color = Ids.colors.textSecondary, fontSize = 12.sp)
            if (!isMine && post.category == "question") {
                ListingActionButton("Answer this question", busy, onClick = onOpen)
            } else if (post.category == "meetup") {
                // Real 당근모임-style structured date/capacity (2026-07-25) -- see
                // backend CommunityPost.eventDate/capacity's own doc comment.
                val eventDate = post.eventDate
                if (eventDate != null) {
                    val capacityLabel = post.capacity?.let { " · $joinedCount/$it" } ?: ""
                    Text("🗓️ ${formatMeetupDate(eventDate)}$capacityLabel", color = Ids.colors.textSecondary, fontSize = 12.sp)
                }
                // Real 참여하기 (join) tap (2026-07-24) -- a real join, not just a
                // "view" navigation: it adds the tapper to a real GroupConversation
                // (see backend CommunityService.joinMeetup's own doc comment), shown
                // with a real "N joined" count rather than a bare label.
                if (isMine) {
                    ListingActionButton("View meetup", busy, onClick = onOpen)
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ListingActionButton("View meetup", busy, onClick = onOpen)
                        ListingActionButton(if (joining) "Joining…" else "참여하기 · $joinedCount joined", joining, filled = true, onClick = onJoin)
                    }
                }
            }
            if (!isMine) {
                HoodReportAction(targetType = "COMMUNITY_POST", targetId = post.id)
            }
            error?.let { Text(it, color = Ids.colors.danger, fontSize = 12.sp) }
        }
    }
}

@Composable
private fun CommunityPostDetailScreen(postId: String, onBack: () -> Unit) {
    val currentUserId = remember { NetworkClient.currentTokenStore().let(TokenStore::getUserId) }
    var post by remember { mutableStateOf<CommunityPostDto?>(null) }
    var authorName by remember { mutableStateOf("") }
    var likedByMe by remember { mutableStateOf(false) }
    var comments by remember { mutableStateOf<List<CommunityCommentWithAuthorDto>?>(null) }
    var commentBody by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var liking by remember { mutableStateOf(false) }
    var commenting by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    fun load() {
        coroutineScope.launch {
            try {
                val detail = NetworkClient.apiService.getCommunityPost(postId)
                post = detail.post; authorName = detail.authorName; likedByMe = detail.likedByMe
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            }
            try {
                comments = NetworkClient.apiService.getCommunityComments(postId).comments
            } catch (e: Exception) { /* non-critical -- the post itself still renders */ }
        }
    }
    LaunchedEffect(postId) { load() }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = Ids.layout.screenHorizontal, vertical = Ids.layout.screenVertical)) {
        BackTopBar("Post", onBack)
        Spacer(modifier = Modifier.height(12.dp))
        error?.let { Text(it, color = Ids.colors.danger, fontSize = 13.sp) }
        post?.let { p ->
            Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = Ids.colors.surface), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(p.title, color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text("by $authorName", color = Ids.colors.textSecondary, fontSize = 12.sp)
                    Text(p.body, color = Ids.colors.textPrimary, fontSize = 14.sp)
                    ListingActionButton(if (likedByMe) "❤️ ${p.likeCount}" else "🤍 ${p.likeCount}", liking) {
                        liking = true
                        coroutineScope.launch {
                            try {
                                val liked = NetworkClient.apiService.toggleCommunityLike(postId).liked
                                likedByMe = liked
                                post = p.copy(likeCount = p.likeCount + if (liked) 1 else -1)
                            } catch (e: HttpException) {
                                error = superAppErrorMessage(e)
                            } finally {
                                liking = false
                            }
                        }
                    }
                }
            }
        }
        post?.takeIf { it.category == "meetup" }?.let { p -> MeetupSessionsSection(post = p, currentUserId = currentUserId) }
        post?.takeIf { it.category == "group_buy" }?.let { p -> GroupBuyFinalizeSection(post = p, currentUserId = currentUserId) }
        Spacer(modifier = Modifier.height(16.dp))
        Text("Comments", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (comments == null) {
                item { Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), modifier = Modifier.fillMaxWidth().height(80.dp)) {} }
            } else if (comments!!.isEmpty()) {
                item { EmptyState("No comments yet -- be the first to reply.", icon = Icons.AutoMirrored.Outlined.Comment) }
            } else {
                items(comments!!, key = { it.comment.id }) { c ->
                    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Ids.colors.surfaceSoft), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(c.authorName, color = Ids.colors.textSecondary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            Text(c.comment.body, color = Ids.colors.textPrimary, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = commentBody, onValueChange = { commentBody = it }, placeholder = { Text("Add a comment") },
                singleLine = true, modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .background(if (commentBody.isBlank()) Ids.colors.textSecondary else Ids.colors.brand, RoundedCornerShape(10.dp))
                    .clickable(enabled = !commenting && commentBody.isNotBlank()) {
                        commenting = true
                        coroutineScope.launch {
                            try {
                                NetworkClient.apiService.addCommunityComment(postId, AddCommunityCommentRequest(commentBody))
                                commentBody = ""
                                load()
                            } catch (e: HttpException) {
                                error = superAppErrorMessage(e)
                            } finally {
                                commenting = false
                            }
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) { Text(if (commenting) "…" else "Send", color = Color.White, fontSize = 13.sp) }
        }
    }
}

/**
 * Real 당근모임 (Karrot Meetups) recurring schedule + attendance check-in -- see
 * rw.itunda.community.CommunityService.scheduleMeetupSessions/checkIntoSession's own doc
 * comments. Only rendered for a real category == "meetup" post; the author gets a real
 * schedule form, any real joined member gets a real per-session check-in button.
 * bank-mfe already has this; this is the first Android client.
 */
@Composable
private fun MeetupSessionsSection(post: CommunityPostDto, currentUserId: String?) {
    var sessions by remember { mutableStateOf<List<MeetupSessionDto>?>(null) }
    val dates = remember { mutableStateListOf("") }
    var scheduling by remember { mutableStateOf(false) }
    var checkingInId by remember { mutableStateOf<String?>(null) }
    var checkedInIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val isAuthor = currentUserId != null && currentUserId == post.authorId

    fun load() {
        coroutineScope.launch {
            try {
                sessions = NetworkClient.apiService.getMeetupSessions(post.id).sessions
            } catch (e: Exception) {
                sessions = emptyList()
            }
        }
    }
    LaunchedEffect(post.id) { load() }

    Spacer(modifier = Modifier.height(16.dp))
    Text("Sessions", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    Spacer(modifier = Modifier.height(8.dp))
    val list = sessions
    if (list == null) {
        Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), modifier = Modifier.fillMaxWidth().height(60.dp)) {}
    } else if (list.isEmpty()) {
        Text("No sessions scheduled yet.", color = Ids.colors.textSecondary, fontSize = 13.sp)
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            list.forEach { s ->
                Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = Ids.colors.surface), modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(s.scheduledFor.replace("T", " ").take(16), color = Ids.colors.textPrimary, fontSize = 13.sp)
                        ListingActionButton(
                            if (checkedInIds.contains(s.id)) "✓ Checked in" else if (checkingInId == s.id) "…" else "Check in",
                            checkingInId == s.id || checkedInIds.contains(s.id),
                        ) {
                            checkingInId = s.id
                            coroutineScope.launch {
                                try {
                                    NetworkClient.apiService.checkIntoMeetupSession(s.id)
                                    checkedInIds = checkedInIds + s.id
                                } catch (e: HttpException) {
                                    error = superAppErrorMessage(e)
                                } finally {
                                    checkingInId = null
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (isAuthor) {
        Spacer(modifier = Modifier.height(8.dp))
        Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = Ids.colors.surface), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Schedule sessions (up to 6)", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                dates.forEachIndexed { i, d ->
                    OutlinedTextField(
                        value = d, onValueChange = { dates[i] = it },
                        placeholder = { Text("Hours from now") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (dates.size < 6) {
                        ListingActionButton("+ Add date", false) { dates.add("") }
                    }
                    ListingActionButton(if (scheduling) "Scheduling…" else "Schedule", scheduling, filled = true) {
                        val isoDates = dates.mapNotNull { it.toDoubleOrNull() }
                            .map { hours -> java.time.Instant.now().plusSeconds((hours * 3600).toLong()).toString() }
                        if (isoDates.isEmpty()) {
                            error = "Add at least one session date."
                        } else {
                            scheduling = true
                            error = null
                            coroutineScope.launch {
                                try {
                                    NetworkClient.apiService.scheduleMeetupSessions(post.id, ScheduleMeetupSessionsRequest(isoDates))
                                    dates.clear(); dates.add("")
                                    load()
                                } catch (e: HttpException) {
                                    error = superAppErrorMessage(e)
                                } finally {
                                    scheduling = false
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    error?.let { Text(it, color = Ids.colors.danger, fontSize = 12.sp) }
}

/**
 * Real 당근마켓 같이사요 (Karrot "Let's Buy Together") -- see
 * rw.itunda.community.CommunityService.finalizeGroupBuy's own doc comment. Author-only:
 * once real participants have joined via the same 참여하기 flow a meetup already uses,
 * the organizer fronts the total cost and splits it via the already-real SplitBill
 * mechanic. bank-mfe already has this; this is the first Android client.
 */
@Composable
private fun GroupBuyFinalizeSection(post: CommunityPostDto, currentUserId: String?) {
    if (currentUserId == null || currentUserId != post.authorId) return
    var totalAmount by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    Spacer(modifier = Modifier.height(16.dp))
    if (done) {
        Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = Ids.colors.surface), modifier = Modifier.fillMaxWidth()) {
            Text(
                "Split request sent -- see it in your group chat's Split bill tab.",
                color = Ids.colors.brand, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                modifier = Modifier.padding(14.dp),
            )
        }
        return
    }
    Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = Ids.colors.surface), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Split the cost", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(
                "Enter what you paid up front -- every real member who joined will be asked for their even share.",
                color = Ids.colors.textSecondary, fontSize = 12.sp,
            )
            OutlinedTextField(value = totalAmount, onValueChange = { totalAmount = it }, placeholder = { Text("Total amount (RWF)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = description, onValueChange = { description = it }, placeholder = { Text("What was this for?") }, modifier = Modifier.fillMaxWidth())
            error?.let { Text(it, color = Ids.colors.danger, fontSize = 12.sp) }
            ListingActionButton(if (submitting) "Splitting…" else "Request even split", submitting, filled = true) {
                val amount = totalAmount.toBigDecimalOrNull()
                if (amount == null || amount <= java.math.BigDecimal.ZERO || description.isBlank()) {
                    error = "Enter a real total amount and a short description."
                } else {
                    submitting = true
                    error = null
                    coroutineScope.launch {
                        try {
                            NetworkClient.apiService.finalizeGroupBuy(post.id, FinalizeGroupBuyRequest(amount, description.trim()))
                            done = true
                        } catch (e: HttpException) {
                            error = superAppErrorMessage(e)
                        } finally {
                            submitting = false
                        }
                    }
                }
            }
        }
    }
}
