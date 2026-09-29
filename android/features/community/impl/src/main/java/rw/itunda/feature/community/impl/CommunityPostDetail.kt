package rw.itunda.feature.community.impl

import androidx.compose.foundation.background
import rw.itunda.core.designsystem.components.pressScaleClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Comment
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import retrofit2.HttpException
import rw.itunda.core.designsystem.components.BackTopBar
import rw.itunda.core.designsystem.components.EmptyState
import rw.itunda.core.designsystem.components.IdsButton
import rw.itunda.core.designsystem.components.IdsButtonSize
import rw.itunda.core.designsystem.components.IdsButtonVariant
import rw.itunda.core.designsystem.components.IdsTextField
import rw.itunda.core.designsystem.components.ListingActionButton
import rw.itunda.core.designsystem.itundaface.WishlistHeart
import rw.itunda.core.designsystem.components.SkeletonBlock
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.AddCommunityCommentRequest
import rw.itunda.core.network.CommunityCommentWithAuthorDto
import rw.itunda.core.network.CommunityPostDto
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.TokenStore
import rw.itunda.core.network.superAppErrorMessage
import java.io.IOException

// Real fix (2026-09-13, file-size-lint): extracted out of CommunityScreen.kt once that
// file grew past its recorded baseline (the comment-thread pagination fix below pushed
// it there) -- this composable was already fully self-contained (only postId/onBack
// params, no shared state with any sibling in that file), same extraction shape as this
// module's own CommunityMeetupsGroupBuy.kt split.
@Composable
internal fun CommunityPostDetailScreen(postId: String, onBack: () -> Unit) {
    val currentUserId = remember { NetworkClient.currentTokenStore().let(TokenStore::getUserId) }
    var post by remember { mutableStateOf<CommunityPostDto?>(null) }
    var authorName by remember { mutableStateOf("") }
    var likedByMe by remember { mutableStateOf(false) }
    var comments by remember { mutableStateOf<List<CommunityCommentWithAuthorDto>?>(null) }
    // Real pagination-discard fix (2026-09-13, see project_itunda_pagination_discard_sweep
    // memory) -- getCommunityComments silently capped this thread at its oldest 20
    // comments (backend sorts ascending by createdAt), so a post with 20+ comments never
    // showed any newer ones at all -- including a user's own comment right after posting it.
    var commentsPage by remember { mutableStateOf(0) }
    var commentsHasMore by remember { mutableStateOf(false) }
    var loadingMoreComments by remember { mutableStateOf(false) }
    var myName by remember { mutableStateOf<String?>(null) }
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
                val res = NetworkClient.apiService.getCommunityComments(postId, page = 0)
                comments = res.comments
                commentsPage = 0
                commentsHasMore = res.page + 1 < res.totalPages
            } catch (e: Exception) { /* non-critical -- the post itself still renders */ }
            try {
                val profile = NetworkClient.authApi.getProfile().user
                myName = "${profile.firstName} ${profile.lastName}"
            } catch (e: Exception) { /* non-critical -- only needed for optimistic comment display */ }
        }
    }
    LaunchedEffect(postId) { load() }

    fun loadMoreComments() {
        val nextPage = commentsPage + 1
        loadingMoreComments = true
        coroutineScope.launch {
            try {
                val res = NetworkClient.apiService.getCommunityComments(postId, page = nextPage)
                val existingIds = comments?.map { it.comment.id }?.toSet() ?: emptySet()
                comments = (comments ?: emptyList()) + res.comments.filter { it.comment.id !in existingIds }
                commentsPage = nextPage
                commentsHasMore = res.page + 1 < res.totalPages
            } catch (_: Exception) {
                // Non-critical -- the already-loaded comments stay visible; the
                // user can retry by tapping "Load more" again.
            } finally {
                loadingMoreComments = false
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = Ids.layout.screenHorizontal, vertical = Ids.layout.screenVertical)) {
        BackTopBar("Post", onBack)
        Spacer(modifier = Modifier.height(12.dp))
        error?.let { Text(it, color = Ids.colors.danger, fontSize = 13.sp) }
        post?.let { p ->
            // Real fix (flat-design sweep): dropped the Card wrapper -- this is the
            // screen's own main content, not a separate module.
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(p.title, color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text("by $authorName", color = Ids.colors.textSecondary, fontSize = 12.sp)
                    Text(p.body, color = Ids.colors.textPrimary, fontSize = 14.sp)
                    ListingActionButton("${p.likeCount}", liking, icon = { WishlistHeart(favorited = likedByMe, size = 14.dp) }) {
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
        post?.takeIf { it.category == "meetup" }?.let { p -> MeetupSessionsSection(post = p, currentUserId = currentUserId) }
        post?.takeIf { it.category == "group_buy" }?.let { p -> GroupBuyFinalizeSection(post = p, currentUserId = currentUserId) }
        Spacer(modifier = Modifier.height(16.dp))
        Text("Comments", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (comments == null) {
                item { SkeletonBlock() }
            } else if (comments!!.isEmpty()) {
                item { EmptyState("No comments yet -- be the first to reply.", icon = Icons.AutoMirrored.Outlined.Comment) }
            } else {
                items(comments!!, key = { it.comment.id }) { c ->
                    // Real fix (flat-design sweep): dropped the per-row Card -- a
                    // comment list separates entries with spacing alone.
                    Column {
                            Text(c.authorName, color = Ids.colors.textSecondary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            Text(c.comment.body, color = Ids.colors.textPrimary, fontSize = 13.sp)
                    }
                }
                if (commentsHasMore) {
                    item {
                        IdsButton(
                            text = if (loadingMoreComments) "Loading…" else "Load more comments",
                            onClick = ::loadMoreComments,
                            enabled = !loadingMoreComments,
                            variant = IdsButtonVariant.Tinted,
                            size = IdsButtonSize.Medium,
                        )
                    }
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IdsTextField(
                value = commentBody, onValueChange = { commentBody = it }, label = "Add a comment",
                singleLine = true, modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .background(if (commentBody.isBlank()) Ids.colors.textSecondary else Ids.colors.brand, RoundedCornerShape(10.dp))
                    .pressScaleClickable(enabled = !commenting && commentBody.isNotBlank()) {
                        commenting = true
                        coroutineScope.launch {
                            try {
                                val newComment = NetworkClient.apiService.addCommunityComment(postId, AddCommunityCommentRequest(commentBody)).comment
                                commentBody = ""
                                // Real fix: append directly rather than reloading page 0 --
                                // page 0 only ever holds the OLDEST comments (ascending
                                // sort), so once a post has 20+ comments, reloading page 0
                                // would make the user's own just-posted comment disappear.
                                comments = (comments ?: emptyList()) + CommunityCommentWithAuthorDto(newComment, myName ?: "You")
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
