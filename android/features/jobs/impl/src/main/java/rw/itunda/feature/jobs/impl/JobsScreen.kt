package rw.itunda.feature.jobs.impl

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import rw.itunda.core.designsystem.components.pressScaleClickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import retrofit2.HttpException
import rw.itunda.core.designsystem.components.EmptyState
import rw.itunda.core.designsystem.components.ErrorCard
import rw.itunda.core.designsystem.components.HoodReportAction
import rw.itunda.core.designsystem.components.HoodReviewForm
import rw.itunda.core.designsystem.components.HoodReviewResultView
import rw.itunda.core.designsystem.components.IdsTextField
import rw.itunda.core.designsystem.components.ListingActionButton
import rw.itunda.core.designsystem.components.NeighborhoodSetupPrompt
import rw.itunda.core.designsystem.components.RouteMiniMap
import rw.itunda.core.designsystem.components.ScrollFog
import rw.itunda.core.designsystem.components.SkeletonBlock
import rw.itunda.core.designsystem.components.TrustBadge
import rw.itunda.core.designsystem.components.relativeTimeAgo
import rw.itunda.core.designsystem.components.rememberRealLocationRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import rw.itunda.core.designsystem.itundaface.WishlistHeart
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.ApplyToJobRequest
import rw.itunda.core.network.CreateJobPostRequest
import rw.itunda.core.network.FavoriteJobPostDto
import rw.itunda.core.network.JobApplicationDto
import rw.itunda.core.network.JobCategoryDto
import rw.itunda.core.network.JobPostDto
import rw.itunda.core.network.MarkFilledRequest
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.RespondToApplicationRequest
import rw.itunda.core.network.SubmitHoodReviewRequest
import rw.itunda.core.network.TokenStore
import rw.itunda.core.network.superAppErrorMessage
import java.io.IOException

// Second Feature extraction (2026-07-23) after Marketplace, same template -- see
// features/marketplace/impl/.../MarketplaceScreen.kt's own header comment for the full
// account of why this module owns its own NetworkClient calls directly instead of a
// "dumb view" shape. RouteMiniMap imports directly from core/designsystem (see that
// file's own header comment) rather than being injected.

// Real "Jobs I did" (2026-07-25) -- closes docs/DESIGN_REFERENCES.md Section 4
// recommendation #6, see backend JobPostRepository's own doc comment.
private enum class JobsView { BROWSE, NEARBY, NEIGHBORHOOD, MINE, WORKED, SAVED, APPLICATIONS }

@Composable
fun JobsContent(
    onMessagePoster: (String) -> Unit,
    // Real hamburger-menu hand-off (2026-08-03), mirroring MarketplaceContent's own
    // requestedView -- see that Composable's doc comment. HoodTab's menu now carries
    // My posts/Jobs I did/My applications/Saved entries for Jobs mode using this same
    // signal shape.
    requestedView: Pair<Int, String> = 0 to "",
    // Real default-feed auto-detect (2026-08-03), mirroring MarketplaceContent's own
    // neighborhoodRefreshSignal -- see that Composable's doc comment for the real,
    // WebSearch/WebFetch-verified sourcing (no Browse/Near-me/Neighborhood chip trio
    // in real Karrot; the feed auto-scopes and you tap the neighborhood name to
    // change it).
    neighborhoodRefreshSignal: Int = 0,
) {
    var view by remember { mutableStateOf(JobsView.BROWSE) }
    var categories by remember { mutableStateOf<List<JobCategoryDto>>(emptyList()) }
    var activeCategory by remember { mutableStateOf<String?>(null) }
    var posts by remember { mutableStateOf<List<JobPostDto>?>(null) }
    // Real Karrot-Score trust badge (2026-07-24) -- see TrustBadge's own doc comment.
    var trustScores by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var error by remember { mutableStateOf<String?>(null) }
    var showNewPost by remember { mutableStateOf(false) }
    var neighborhoodName by remember { mutableStateOf<String?>(null) }
    var neighborhoodChecked by remember { mutableStateOf(false) }
    var favoriteIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var favoritingId by remember { mutableStateOf<String?>(null) }
    var nearbyRadiusKm by remember { mutableStateOf(3.0) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val currentUserId = remember { NetworkClient.currentTokenStore().let(TokenStore::getUserId) }
    val requestNearbyLocation = rememberRealLocationRequester(
        onLocating = {},
        onSuccess = { lat, lng ->
            posts = null
            coroutineScope.launch {
                try {
                    val res = NetworkClient.apiService.getNearbyJobPosts(lat, lng, nearbyRadiusKm)
                    if (res.success) { posts = res.posts; trustScores = res.trustScores }
                    error = null
                } catch (e: HttpException) {
                    error = superAppErrorMessage(e)
                    posts = emptyList()
                } catch (e: IOException) {
                    error = "Couldn't load nearby work. Check your connection and try again."
                    posts = emptyList()
                }
            }
        },
        onError = { message -> error = "$message You can still use Find work or Neighborhood."; posts = emptyList() },
    )

    LaunchedEffect(Unit) {
        try { categories = NetworkClient.apiService.getJobCategories().categories } catch (e: Exception) { /* chips just won't render */ }
        try { favoriteIds = NetworkClient.apiService.getMyFavoriteJobPosts().favorites.map { it.jobPostId }.toSet() } catch (e: Exception) { /* non-critical */ }
    }

    LaunchedEffect(requestedView) {
        val (signal, key) = requestedView
        if (signal > 0) {
            view = when (key) {
                "MINE" -> JobsView.MINE
                "WORKED" -> JobsView.WORKED
                "APPLICATIONS" -> JobsView.APPLICATIONS
                "SAVED" -> JobsView.SAVED
                else -> view
            }
        }
    }

    LaunchedEffect(neighborhoodRefreshSignal) {
        try {
            val profileRes = NetworkClient.authApi.getProfile()
            if (profileRes.user.neighborhood != null) {
                view = JobsView.NEIGHBORHOOD
                return@LaunchedEffect
            }
        } catch (_: Exception) {
            // Best-effort -- falls through to the next real signal below.
        }
        val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        view = if (hasPermission) JobsView.NEARBY else JobsView.BROWSE
    }

    fun load() {
        posts = null
        if (view == JobsView.SAVED) { posts = emptyList(); error = null; return }
        if (view == JobsView.APPLICATIONS) { posts = emptyList(); error = null; return }
        if (view == JobsView.NEARBY) {
            requestNearbyLocation()
            return
        }
        if (view == JobsView.NEIGHBORHOOD) {
            neighborhoodChecked = false
            coroutineScope.launch {
                try {
                    val profileRes = NetworkClient.authApi.getProfile()
                    val res = NetworkClient.apiService.getJobPostsMyNeighborhood(activeCategory)
                    neighborhoodName = profileRes.user.neighborhood
                    if (res.success) { posts = res.posts; trustScores = res.trustScores }
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
                val res = when (view) {
                    JobsView.BROWSE -> NetworkClient.apiService.browseJobPosts(activeCategory)
                    JobsView.WORKED -> NetworkClient.apiService.getMyWorkedJobPosts()
                    else -> NetworkClient.apiService.getMyJobPosts()
                }
                if (res.success) { posts = res.posts; trustScores = res.trustScores }
                error = null
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            }
        }
    }
    LaunchedEffect(view, activeCategory) { load() }

    // Real relevance-ranked search (2026-08-14) -- see backend JobPostService
    // .search's own doc comment; same "uncalled endpoint" gap class as
    // MarketplaceContent's own identical addition, see its doc comment for the full
    // account.
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<JobPostDto>?>(null) }
    LaunchedEffect(searchQuery) {
        if (searchQuery.isBlank()) {
            searchResults = null
            return@LaunchedEffect
        }
        delay(rw.itunda.core.network.SEARCH_DEBOUNCE_MS)
        try {
            val res = NetworkClient.apiService.searchJobPosts(searchQuery)
            if (res.success) {
                searchResults = res.posts
                trustScores = res.trustScores
            }
        } catch (_: Exception) {
            searchResults = emptyList()
        }
    }
    val isSearching = searchQuery.isNotBlank()

    if (showNewPost) {
        BackHandler { showNewPost = false }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = Ids.layout.screenHorizontal, vertical = Ids.layout.screenVertical),
        verticalArrangement = Arrangement.spacedBy(Ids.layout.cardGap),
    ) {
        // Real fix, 2026-08-03: the Find work/Near me/Neighborhood/My posts/Jobs I
        // did/My applications/Saved chip row that used to render here is gone -- see
        // MarketplaceContent's own doc comment for the sourcing. Feed source is now
        // auto-detected (see neighborhoodRefreshSignal's own doc comment above) and
        // the personal-management views moved to HoodTab's hamburger menu
        // (requestedView above).
        item {
            IdsTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = "Search",
                placeholder = "Search jobs",
            )
        }
        if (isSearching) {
            if (searchResults == null) {
                item { SkeletonBlock() }
            } else if (searchResults!!.isEmpty()) {
                item { Text("No jobs match \"$searchQuery\".", color = Ids.colors.textSecondary, fontSize = 14.sp) }
            } else {
                items(searchResults!!, key = { it.id }) { post ->
                    JobPostCard(
                        post = post,
                        categoryLabel = categories.firstOrNull { it.id == post.category }?.label ?: post.category,
                        isMine = post.posterId == currentUserId,
                        posterTrustScore = trustScores[post.posterId],
                        currentUserId = currentUserId,
                        onChanged = ::load,
                        onContact = {
                            coroutineScope.launch {
                                try {
                                    val res = NetworkClient.apiService.contactPoster(post.id)
                                    if (res.success) onMessagePoster(res.conversation.id)
                                } catch (e: HttpException) {
                                    error = superAppErrorMessage(e)
                                } catch (e: IOException) {
                                    error = "Couldn't reach itunda. Check your connection and try again."
                                }
                            }
                        },
                        favorited = post.id in favoriteIds,
                        favoriteBusy = favoritingId == post.id,
                        onToggleFavorite = {
                            favoritingId = post.id
                            coroutineScope.launch {
                                try {
                                    if (post.id in favoriteIds) { NetworkClient.apiService.removeJobPostFavorite(post.id); favoriteIds = favoriteIds - post.id; rw.itunda.core.designsystem.components.IdsToast.show(coroutineScope, "Removed from saved jobs") }
                                    else { NetworkClient.apiService.addJobPostFavorite(post.id); favoriteIds = favoriteIds + post.id; rw.itunda.core.designsystem.components.IdsToast.show(coroutineScope, "Saved to your jobs list") }
                                } catch (e: Exception) { error = "Couldn't update your saved jobs. Check your connection and try again." }
                                finally { favoritingId = null }
                            }
                        },
                    )
                }
            }
        } else {
        if (view == JobsView.NEARBY) item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(1.0, 3.0, 5.0, 10.0).forEach { radius ->
                val active = nearbyRadiusKm == radius
                Text("${radius.toInt()} km", color = if (active) Color.White else Ids.colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(if (active) Ids.colors.brand else Ids.colors.surfaceSoft).pressScaleClickable { nearbyRadiusKm = radius; requestNearbyLocation() }.padding(horizontal = 12.dp, vertical = 7.dp))
            } }
        }
        if ((view == JobsView.BROWSE || view == JobsView.NEIGHBORHOOD) && categories.isNotEmpty()) {
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
                                .pressScaleClickable { activeCategory = if (active) null else c.id }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        ) { Text(c.label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (active) Color.White else Ids.colors.textPrimary) }
                    }
                }
            }
        }
        if (view == JobsView.MINE) {
            item {
                if (!showNewPost) {
                    Box(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Ids.colors.brand).pressScaleClickable { showNewPost = true }.padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text("+ Post a job", color = Color.White, fontWeight = FontWeight.Bold) }
                } else {
                    NewJobPostForm(categories, onCreated = { showNewPost = false; load() }, onCancel = { showNewPost = false })
                }
            }
        }
        if (view == JobsView.NEIGHBORHOOD && neighborhoodChecked && neighborhoodName == null) {
            item { NeighborhoodSetupPrompt(onDone = { load() }) }
        }
        if (view == JobsView.NEIGHBORHOOD && neighborhoodName != null) {
            item { Text("Your neighborhood: $neighborhoodName", color = Ids.colors.textSecondary, fontSize = 13.sp) }
        }
        if (view == JobsView.APPLICATIONS) {
            item { MyJobApplicationsView() }
        } else if (view == JobsView.SAVED) {
            item { JobPostWishlistView(onRemoved = { coroutineScope.launch { favoriteIds = NetworkClient.apiService.getMyFavoriteJobPosts().favorites.map { it.jobPostId }.toSet() } }) }
        } else if (error != null) {
            item { ErrorCard(error!!, onRetry = ::load) }
        } else if (posts == null) {
            item { SkeletonBlock() }
        } else if (posts!!.isEmpty() && (view != JobsView.NEIGHBORHOOD || neighborhoodName != null)) {
            item {
                EmptyState(
                    // Real copy-voice fix (item 244, round 5 of the empty-state pass --
                    // docs/COPY_VOICE.md's rules): say what's missing AND what fixes it,
                    // per this screen's own real "+ Post a job" button above in MINE.
                    when (view) {
                        JobsView.BROWSE -> "No jobs posted yet — check back soon, or post one yourself."
                        JobsView.NEARBY -> "No jobs near you yet — try Browse to see jobs from everywhere."
                        JobsView.NEIGHBORHOOD -> "No jobs in your neighborhood yet — try Browse to see jobs from everywhere."
                        JobsView.MINE -> "You haven't posted any jobs yet — tap \"+ Post a job\" above to post your first one."
                        JobsView.WORKED -> "No completed jobs recorded yet — jobs you complete will show up here."
                        JobsView.SAVED -> ""
                        JobsView.APPLICATIONS -> ""
                    },
                    icon = Icons.Outlined.Work,
                    // Real fix (2026-08-15): the copy above told the user to "try
                    // Browse", but there was never any way to actually reach it -- this
                    // was plain Text, not even the shared EmptyState. See
                    // EmptyState's own doc comment for the full cross-feature account.
                    actionLabel = if (view == JobsView.NEARBY || view == JobsView.NEIGHBORHOOD) "Browse everywhere" else null,
                    onAction = if (view == JobsView.NEARBY || view == JobsView.NEIGHBORHOOD) { { view = JobsView.BROWSE } } else null,
                )
            }
        } else if (posts!!.isNotEmpty()) {
            items(posts!!, key = { it.id }) { post ->
                JobPostCard(
                    post = post,
                    categoryLabel = categories.firstOrNull { it.id == post.category }?.label ?: post.category,
                    isMine = view == JobsView.MINE || post.posterId == currentUserId,
                    posterTrustScore = trustScores[post.posterId],
                    currentUserId = currentUserId,
                    onChanged = ::load,
                    onContact = {
                        coroutineScope.launch {
                            try {
                                val res = NetworkClient.apiService.contactPoster(post.id)
                                if (res.success) onMessagePoster(res.conversation.id)
                            } catch (e: HttpException) {
                                error = superAppErrorMessage(e)
                            } catch (e: IOException) {
                                error = "Couldn't reach itunda. Check your connection and try again."
                            }
                        }
                    },
                    favorited = post.id in favoriteIds,
                    favoriteBusy = favoritingId == post.id,
                    onToggleFavorite = {
                        favoritingId = post.id
                        coroutineScope.launch {
                            try {
                                if (post.id in favoriteIds) { NetworkClient.apiService.removeJobPostFavorite(post.id); favoriteIds = favoriteIds - post.id; rw.itunda.core.designsystem.components.IdsToast.show(coroutineScope, "Removed from saved jobs") }
                                else { NetworkClient.apiService.addJobPostFavorite(post.id); favoriteIds = favoriteIds + post.id; rw.itunda.core.designsystem.components.IdsToast.show(coroutineScope, "Saved to your jobs list") }
                            } catch (e: Exception) { error = "Couldn't update your saved jobs. Check your connection and try again." }
                            finally { favoritingId = null }
                        }
                    },
                )
            }
        }
        }
    }
        ScrollFog(modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun JobPostWishlistView(onRemoved: () -> Unit) {
    var favorites by remember { mutableStateOf<List<FavoriteJobPostDto>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var removingId by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    fun load() = scope.launch {
        try { favorites = NetworkClient.apiService.getMyFavoriteJobPosts().favorites; error = null }
        catch (e: Exception) { error = "Couldn't load your saved jobs. Check your connection and try again." }
    }
    LaunchedEffect(Unit) { load() }
    when {
        error != null -> ErrorCard(error!!, onRetry = ::load)
        favorites == null -> SkeletonBlock()
        favorites!!.isEmpty() -> Text("No saved jobs yet — tap ♡ on a job to keep it here.", color = Ids.colors.textSecondary, fontSize = 14.sp)
        else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Real fix (2026-08-24, flat-design sweep): dropped the per-row Card --
            // an entity list a user manages (saved jobs), no divider, matching
            // GroupAccountScreen's precedent (docs/UI_UX_GUIDELINES.md §10).
            favorites!!.forEach { favorite ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(favorite.title, color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold)
                        Text("${favorite.category} · %,.0f RWF".format(favorite.payAmount), color = Ids.colors.textSecondary, fontSize = 12.sp)
                    }
                    Text("Remove", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.pressScaleClickable(enabled = removingId == null) {
                        removingId = favorite.jobPostId
                        scope.launch {
                            try { NetworkClient.apiService.removeJobPostFavorite(favorite.jobPostId); favorites = favorites!!.filterNot { it.jobPostId == favorite.jobPostId }; onRemoved() }
                            catch (e: Exception) { error = "Couldn't remove this saved job. Check your connection and try again." }
                            finally { removingId = null }
                        }
                    })
                }
            }
        }
    }
}

// Real "my job applications" status view (item 196) -- found via the same
// defined-but-uncalled-method sweep as items 192-195: GET /api/v1/jobs/my-applications
// (JobApplicationService.getMyApplications) had zero client anywhere on any platform --
// an applicant could submit a real structured application (see JobApplication.kt's own
// doc comment) and message the poster, but never see whether it was still pending,
// accepted, or declined. JobApplicationDto carries no job-post title snapshot (unlike
// FavoriteJobPostDto above), so this fans out one real getJobPost fetch per
// application -- an honest N+1 for this app's real per-user application volume, same
// discipline items 187-189's per-product review fan-out already established, rather
// than inventing a new aggregate backend endpoint for this pass.
@Composable
private fun MyJobApplicationsView() {
    var applications by remember { mutableStateOf<List<Pair<JobApplicationDto, JobPostDto?>>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    fun load() = scope.launch {
        try {
            val apps = NetworkClient.apiService.getMyJobApplications().applications
            applications = apps.map { app ->
                app to try { NetworkClient.apiService.getJobPost(app.jobPostId).post } catch (e: Exception) { null }
            }
            error = null
        } catch (e: Exception) {
            error = "Couldn't load your applications. Check your connection and try again."
        }
    }
    LaunchedEffect(Unit) { load() }
    when {
        error != null -> ErrorCard(error!!, onRetry = ::load)
        applications == null -> SkeletonBlock()
        applications!!.isEmpty() -> Text("You haven't applied to any jobs yet.", color = Ids.colors.textSecondary, fontSize = 14.sp)
        else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Real fix (2026-08-24, flat-design sweep): dropped the per-row Card --
            // an application-status history log, kept the per-row Divider
            // convention (docs/DESIGN_REFERENCES.md §274).
            applications!!.forEach { (app, post) ->
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(post?.title ?: "Job post", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold)
                        Text(
                            when (app.status) { "ACCEPTED" -> "Accepted"; "DECLINED" -> "Declined"; else -> "Pending" },
                            color = when (app.status) { "ACCEPTED" -> Ids.colors.brand; "DECLINED" -> Ids.colors.danger; else -> Ids.colors.textSecondary },
                            fontWeight = FontWeight.Bold, fontSize = 12.sp,
                        )
                    }
                    Text(app.message, color = Ids.colors.textSecondary, fontSize = 13.sp)
                }
                Divider(color = Ids.colors.divider, thickness = 0.5.dp)
            }
        }
    }
}

@Composable
private fun NewJobPostForm(categories: List<JobCategoryDto>, onCreated: () -> Unit, onCancel: () -> Unit) {
    var category by remember { mutableStateOf(categories.firstOrNull()?.id ?: "") }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var payType by remember { mutableStateOf("HOURLY") }
    var payAmount by remember { mutableStateOf("") }
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

    // Real fix (2026-08-24, flat-design sweep): dropped the Card wrapper -- this
    // screen's own main content, a lone form (docs/UI_UX_GUIDELINES.md §10).
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Post a job", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                categories.forEach { c ->
                    val selected = category == c.id
                    Box(
                        modifier = Modifier
                            .background(if (selected) Ids.colors.brand else Ids.colors.surfaceSoft, RoundedCornerShape(999.dp))
                            .pressScaleClickable { category = c.id }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) { Text(c.label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (selected) Color.White else Ids.colors.textPrimary) }
                }
            }
            IdsTextField(value = title, onValueChange = { title = it }, label = "What do you need done?", singleLine = true, modifier = Modifier.fillMaxWidth())
            IdsTextField(value = description, onValueChange = { description = it }, label = "Describe the work", modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("HOURLY" to "Per hour", "FIXED" to "Fixed price").forEach { (v, label) ->
                    val selected = payType == v
                    Text(
                        label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (selected) Color.White else Ids.colors.textPrimary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) Ids.colors.brand else Ids.colors.surfaceSoft)
                            .pressScaleClickable { payType = v }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
                IdsTextField(
                    value = payAmount, onValueChange = { payAmount = it }, label = "Pay (RWF)", singleLine = true,
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                    modifier = Modifier.weight(1f),
                )
            }
            Box(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Ids.colors.surfaceSoft)
                    .pressScaleClickable(enabled = !locating) { if (shareLocation) shareLocation = false else requestLocation() }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) { Text(if (locating) "Finding your real location…" else if (shareLocation) "📍 Work location shared with nearby applicants" else "📍 Share work location for nearby applicants (optional)", fontSize = 13.sp, color = if (shareLocation) Ids.colors.brand else Ids.colors.textSecondary) }
            error?.let { Text(it, color = Ids.colors.danger, fontSize = 12.sp) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Ids.colors.brand)
                        .pressScaleClickable(enabled = !submitting) {
                            val amount = payAmount.toDoubleOrNull()
                            if (title.isBlank() || description.isBlank() || category.isBlank() || amount == null || amount <= 0) {
                                error = "Fill in every field with a real pay amount."
                                return@pressScaleClickable
                            }
                            submitting = true
                            error = null
                            coroutineScope.launch {
                                try {
                                    val loc = if (shareLocation) myLocation else null
                                    val res = NetworkClient.apiService.createJobPost(CreateJobPostRequest(category, title, description, payType, amount, loc?.first, loc?.second))
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
                ) { Text(if (submitting) "Posting…" else "Post job", color = Color.White, fontWeight = FontWeight.Bold) }
            }
    }
}

@Composable
private fun JobPostCard(
    post: JobPostDto, categoryLabel: String, isMine: Boolean, onChanged: () -> Unit, onContact: () -> Unit,
    favorited: Boolean = false, favoriteBusy: Boolean = false, onToggleFavorite: () -> Unit = {},
    posterTrustScore: Int? = null,
    currentUserId: String? = null,
) {
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    var myLocation by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var showRoute by remember { mutableStateOf(false) }
    var locating by remember { mutableStateOf(false) }
    val requestLocation = rememberRealLocationRequester(
        onLocating = { locating = it },
        onSuccess = { lat, lng -> myLocation = lat to lng; showRoute = true },
        onError = { error = it },
    )
    val payLabel = "%,.0f RWF".format(post.payAmount) + if (post.payType == "HOURLY") "/hr" else ""

    // Real optional worker identification at mark-filled time (2026-07-24) -- see
    // backend JobPostService.markFilled's own doc comment. Confirm with a phone
    // number or Skip, either way the post completes.
    var markingFilled by remember { mutableStateOf(false) }
    var workerPhone by remember { mutableStateOf("") }

    // Real post-transaction review with asymmetric public/private visibility
    // (2026-07-24) -- see backend HoodReviewService's own doc comment.
    var showReviewSheet by remember { mutableStateOf(false) }
    var selectedGoodPoints by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedUncomfortablePoints by remember { mutableStateOf<Set<String>>(emptySet()) }
    var submittingReview by remember { mutableStateOf(false) }
    var reviewSubmitted by remember { mutableStateOf(false) }
    // Real read-back for the review above (item 192/198) -- see bank-mfe's
    // HoodReviewResultView (item 192) for the full account.
    var hoodReviews by remember { mutableStateOf<List<rw.itunda.core.network.HoodReviewDto>?>(null) }
    LaunchedEffect(post.id, post.status, post.workerId, isMine) {
        if (isMine && post.status == "FILLED" && post.workerId != null) {
            try {
                val reviews = NetworkClient.apiService.getJobPostReviews(post.id).reviews
                hoodReviews = reviews
                if (reviews.any { it.reviewerId == currentUserId }) reviewSubmitted = true
            } catch (e: Exception) {
                // Real, non-critical -- the review form itself still works without this.
            }
        }
    }

    // Real 당근알바-style structured application (2026-07-25) -- see backend
    // JobApplicationService's own doc comment. Applying is additive alongside "Message
    // poster", not a replacement -- see JobApplication's own doc comment for why.
    var applying by remember { mutableStateOf(false) }
    var applicationMessage by remember { mutableStateOf("") }
    var applicationSubmitted by remember { mutableStateOf(false) }
    var submittingApplication by remember { mutableStateOf(false) }

    // Poster's real "review applicants, then decide" step -- lazily fetched only when
    // opened, same on-demand pattern as the review sheet above.
    var showApplicants by remember { mutableStateOf(false) }
    var applications by remember { mutableStateOf<List<JobApplicationDto>?>(null) }
    var respondingToId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(showApplicants) {
        if (showApplicants && applications == null) {
            try {
                applications = NetworkClient.apiService.getApplicationsForJobPost(post.id).applications
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            }
        }
    }

    Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = Ids.colors.surface), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Leads with the real hyperlocal neighborhood, same shape Hood's own
                    // listing rows use -- "where" matters as much as "when" for a job
                    // that's realistically only reachable nearby. Same class of gap as
                    // Property's identical fix, see [[project_itunda_full_ecosystem_polish]].
                    val categoryAndLocation = listOfNotNull(categoryLabel, post.neighborhood, relativeTimeAgo(post.createdAt)).joinToString(" · ")
                    Text(categoryAndLocation, color = Ids.colors.brand, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    if (post.status == "FILLED") {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Ids.colors.surfaceSoft).padding(horizontal = 8.dp, vertical = 2.dp)) {
                            Text("FILLED", color = Ids.colors.textSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Real icon consistency fix (2026-08-03) -- was a plain "♥"/"♡"
                    // text glyph, the only one of the 4 Hood modes still doing that
                    // after ListingCard's own real-Karrot-reference rewrite switched to
                    // a proper Icon.
                    if (!isMine) {
                        WishlistHeart(
                            favorited = favorited,
                            size = 20.dp,
                            modifier = Modifier.semantics { contentDescription = if (favorited) "Remove from wishlist" else "Add to wishlist" }
                                .pressScaleClickable(enabled = !favoriteBusy) { onToggleFavorite() }.padding(end = 8.dp),
                        )
                    }
                    Text(payLabel, color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
            Text(post.title, color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            // Real Karrot-Score trust badge (2026-07-24) -- see TrustBadge's own doc
            // comment. Only shown for someone else's post -- a trust score about
            // yourself is meaningless here.
            if (!isMine && posterTrustScore != null) {
                TrustBadge(posterTrustScore)
            }
            Text(post.description, color = Ids.colors.textSecondary, fontSize = 13.sp)
            error?.let { Text(it, color = Ids.colors.danger, fontSize = 12.sp) }
            // Real optional "who did you hire?" prompt (2026-07-24) -- see backend
            // JobPostService.markFilled's own doc comment.
            if (markingFilled) {
                IdsTextField(
                    value = workerPhone,
                    onValueChange = { workerPhone = it },
                    label = "Worker's phone (optional)",
                    singleLine = true,
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ListingActionButton("Skip", busy) {
                        busy = true
                        coroutineScope.launch {
                            try { NetworkClient.apiService.markJobPostFilled(post.id); markingFilled = false; onChanged() }
                            catch (e: HttpException) { error = superAppErrorMessage(e) }
                            finally { busy = false }
                        }
                    }
                    ListingActionButton("Confirm", busy, filled = true) {
                        busy = true
                        coroutineScope.launch {
                            try {
                                NetworkClient.apiService.markJobPostFilled(post.id, MarkFilledRequest(workerPhone.trim().ifBlank { null }))
                                markingFilled = false
                                onChanged()
                            } catch (e: HttpException) { error = superAppErrorMessage(e) }
                            finally { busy = false }
                        }
                    }
                }
            }
            if (isMine && post.status == "FILLED" && post.workerId != null && reviewSubmitted) {
                hoodReviews?.let { HoodReviewResultView(it, currentUserId) }
            }
            // Real post-transaction review, preset checklist with asymmetric public/
            // private visibility (2026-07-24) -- see backend HoodReviewService's own
            // doc comment. Only offered once a real worker was recorded at mark-filled
            // time.
            if (isMine && post.status == "FILLED" && post.workerId != null && !reviewSubmitted) {
                if (showReviewSheet) {
                    HoodReviewForm(
                        selectedGoodPoints = selectedGoodPoints,
                        onToggleGoodPoint = { p -> selectedGoodPoints = if (p in selectedGoodPoints) selectedGoodPoints - p else selectedGoodPoints + p },
                        selectedUncomfortablePoints = selectedUncomfortablePoints,
                        onToggleUncomfortablePoint = { p -> selectedUncomfortablePoints = if (p in selectedUncomfortablePoints) selectedUncomfortablePoints - p else selectedUncomfortablePoints + p },
                        submitting = submittingReview,
                        onCancel = { showReviewSheet = false },
                        onSubmit = {
                            submittingReview = true
                            coroutineScope.launch {
                                try {
                                    val res = NetworkClient.apiService.submitJobPostReview(
                                        post.id,
                                        SubmitHoodReviewRequest(selectedGoodPoints.toList(), selectedUncomfortablePoints.toList()),
                                    )
                                    reviewSubmitted = true
                                    showReviewSheet = false
                                    hoodReviews = (hoodReviews ?: emptyList()) + res.review
                                } catch (e: HttpException) {
                                    error = superAppErrorMessage(e)
                                } finally {
                                    submittingReview = false
                                }
                            }
                        },
                    )
                } else {
                    ListingActionButton("Rate this worker", busy, filled = true) { showReviewSheet = true }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (isMine) {
                    if (post.status == "OPEN" && !markingFilled) {
                        ListingActionButton("Mark filled", busy) { markingFilled = true }
                    }
                    if (post.status != "REMOVED") {
                        ListingActionButton("Remove", busy) {
                            busy = true
                            coroutineScope.launch {
                                try { NetworkClient.apiService.removeJobPost(post.id); onChanged() }
                                catch (e: HttpException) { error = superAppErrorMessage(e) }
                                finally { busy = false }
                            }
                        }
                    }
                } else if (post.status == "OPEN") {
                    ListingActionButton("Message poster", busy, onClick = onContact)
                    if (!applicationSubmitted && !applying) {
                        ListingActionButton("Apply", busy, filled = true) { applying = true }
                    }
                }
            }
            // Real 당근알바-style structured application (2026-07-25) -- the applicant's
            // real self-introduction, not a bare DM. See backend JobApplicationService's
            // own doc comment.
            if (!isMine && applying) {
                IdsTextField(
                    value = applicationMessage,
                    onValueChange = { applicationMessage = it },
                    label = "Why should the poster pick you? (required)",
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ListingActionButton("Cancel", submittingApplication) { applying = false }
                    ListingActionButton("Submit application", submittingApplication || applicationMessage.isBlank(), filled = true) {
                        submittingApplication = true
                        coroutineScope.launch {
                            try {
                                NetworkClient.apiService.applyToJob(post.id, ApplyToJobRequest(applicationMessage.trim()))
                                applying = false
                                applicationSubmitted = true
                            } catch (e: HttpException) {
                                error = superAppErrorMessage(e)
                            } finally {
                                submittingApplication = false
                            }
                        }
                    }
                }
            }
            if (!isMine && applicationSubmitted) {
                Text("Application sent — you'll hear back once the poster reviews it", color = Ids.colors.textSecondary, fontSize = 12.sp)
            }
            // Poster's real "review applicants, then decide" step (2026-07-25) -- see
            // backend JobApplicationService's own doc comment.
            if (isMine && post.status == "OPEN") {
                ListingActionButton(if (showApplicants) "Hide applicants" else "View applicants", busy) { showApplicants = !showApplicants }
                if (showApplicants) {
                    applications?.let { apps ->
                        if (apps.isEmpty()) {
                            Text("No applications yet", color = Ids.colors.textSecondary, fontSize = 12.sp)
                        }
                        apps.filter { it.status == "PENDING" }.forEach { app ->
                            Column(
                                modifier = Modifier.fillMaxWidth().background(Ids.colors.surfaceSoft, RoundedCornerShape(Ids.layout.cardCornerRadius)).padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(app.message, color = Ids.colors.textPrimary, fontSize = 13.sp)
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    ListingActionButton("Decline", respondingToId == app.id) {
                                        respondingToId = app.id
                                        coroutineScope.launch {
                                            try {
                                                NetworkClient.apiService.respondToJobApplication(app.id, RespondToApplicationRequest(accept = false))
                                                applications = applications?.filterNot { it.id == app.id }
                                            } catch (e: HttpException) {
                                                error = superAppErrorMessage(e)
                                            } finally {
                                                respondingToId = null
                                            }
                                        }
                                    }
                                    ListingActionButton("Accept & message", respondingToId == app.id, filled = true) {
                                        respondingToId = app.id
                                        coroutineScope.launch {
                                            try {
                                                NetworkClient.apiService.respondToJobApplication(app.id, RespondToApplicationRequest(accept = true))
                                                applications = applications?.filterNot { it.id == app.id }
                                                rw.itunda.core.designsystem.components.IdsToast.show(coroutineScope, "Conversation started")
                                            } catch (e: HttpException) {
                                                error = superAppErrorMessage(e)
                                            } finally {
                                                respondingToId = null
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } ?: SkeletonBlock()
                }
            }
            if (!isMine && post.status == "OPEN") {
                HoodReportAction(targetType = "JOB_POST", targetId = post.id)
            }
            val postLat = post.latitude
            val postLng = post.longitude
            if (!isMine && post.status == "OPEN" && postLat != null && postLng != null) {
                ListingActionButton(if (locating) "Finding your real location…" else if (showRoute) "Hide directions" else "🚗 Directions to this work", locating) {
                    if (showRoute) showRoute = false else if (myLocation != null) showRoute = true else requestLocation()
                }
                myLocation?.let { loc ->
                    if (showRoute) RouteMiniMap(loc.first, loc.second, postLat, postLng, "You", post.title)
                }
            }
        }
    }
}
