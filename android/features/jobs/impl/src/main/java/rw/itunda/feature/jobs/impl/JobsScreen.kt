package rw.itunda.feature.jobs.impl

import android.widget.Toast
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import retrofit2.HttpException
import rw.itunda.core.designsystem.components.ErrorCard
import rw.itunda.core.designsystem.components.HoodReportAction
import rw.itunda.core.designsystem.components.HoodReviewForm
import rw.itunda.core.designsystem.components.ListingActionButton
import rw.itunda.core.designsystem.components.NeighborhoodSetupPrompt
import rw.itunda.core.designsystem.components.RouteMiniMap
import rw.itunda.core.designsystem.components.SkeletonBlock
import rw.itunda.core.designsystem.components.TrustBadge
import rw.itunda.core.designsystem.components.relativeTimeAgo
import rw.itunda.core.designsystem.components.rememberRealLocationRequester
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.CreateJobPostRequest
import rw.itunda.core.network.FavoriteJobPostDto
import rw.itunda.core.network.JobCategoryDto
import rw.itunda.core.network.JobPostDto
import rw.itunda.core.network.MarkFilledRequest
import rw.itunda.core.network.NetworkClient
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
private enum class JobsView { BROWSE, NEARBY, NEIGHBORHOOD, MINE, WORKED, SAVED }

@Composable
fun JobsContent(
    onMessagePoster: (String) -> Unit,
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

    fun load() {
        posts = null
        if (view == JobsView.SAVED) { posts = emptyList(); error = null; return }
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
                listOf(JobsView.BROWSE to "Find work", JobsView.NEARBY to "Near me", JobsView.NEIGHBORHOOD to "Neighborhood", JobsView.MINE to "My posts", JobsView.WORKED to "Jobs I did", JobsView.SAVED to "Saved").forEach { (v, label) ->
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
        if (view == JobsView.NEARBY) item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(1.0, 3.0, 5.0, 10.0).forEach { radius ->
                val active = nearbyRadiusKm == radius
                Text("${radius.toInt()} km", color = if (active) Color.White else Ids.colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(if (active) Ids.colors.brand else Ids.colors.surfaceSoft).clickable { nearbyRadiusKm = radius; requestNearbyLocation() }.padding(horizontal = 12.dp, vertical = 7.dp))
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
                                .clickable { activeCategory = if (active) null else c.id }
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
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Ids.colors.brand).clickable { showNewPost = true }.padding(vertical = 14.dp),
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
        if (view == JobsView.SAVED) {
            item { JobPostWishlistView(onRemoved = { coroutineScope.launch { favoriteIds = NetworkClient.apiService.getMyFavoriteJobPosts().favorites.map { it.jobPostId }.toSet() } }) }
        } else if (error != null) {
            item { ErrorCard(error!!, onRetry = ::load) }
        } else if (posts == null) {
            item { SkeletonBlock() }
        } else if (posts!!.isEmpty() && (view != JobsView.NEIGHBORHOOD || neighborhoodName != null)) {
            item {
                Text(
                    when (view) {
                        JobsView.BROWSE -> "No jobs posted yet."
                        JobsView.NEARBY -> "No jobs near you yet."
                        JobsView.NEIGHBORHOOD -> "No jobs in your neighborhood yet."
                        JobsView.MINE -> "You haven't posted any jobs yet."
                        JobsView.WORKED -> "No completed jobs recorded yet."
                        JobsView.SAVED -> ""
                    },
                    color = Ids.colors.textSecondary, fontSize = 14.sp,
                )
            }
        } else if (posts!!.isNotEmpty()) {
            items(posts!!, key = { it.id }) { post ->
                JobPostCard(
                    post = post,
                    categoryLabel = categories.firstOrNull { it.id == post.category }?.label ?: post.category,
                    isMine = view == JobsView.MINE || post.posterId == currentUserId,
                    posterTrustScore = trustScores[post.posterId],
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
                                if (post.id in favoriteIds) { NetworkClient.apiService.removeJobPostFavorite(post.id); favoriteIds = favoriteIds - post.id; Toast.makeText(context, "Removed from saved jobs", Toast.LENGTH_SHORT).show() }
                                else { NetworkClient.apiService.addJobPostFavorite(post.id); favoriteIds = favoriteIds + post.id; Toast.makeText(context, "Saved to your jobs list", Toast.LENGTH_SHORT).show() }
                            } catch (e: Exception) { error = "Couldn't update your saved jobs. Check your connection and try again." }
                            finally { favoritingId = null }
                        }
                    },
                )
            }
        }
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
            favorites!!.forEach { favorite ->
                Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = Ids.colors.surface), modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(favorite.title, color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold)
                            Text("${favorite.category} · %,.0f RWF".format(favorite.payAmount), color = Ids.colors.textSecondary, fontSize = 12.sp)
                        }
                        Text("Remove", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.clickable(enabled = removingId == null) {
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

    Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = Ids.colors.surface), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                            .clickable { category = c.id }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) { Text(c.label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (selected) Color.White else Ids.colors.textPrimary) }
                }
            }
            OutlinedTextField(value = title, onValueChange = { title = it }, placeholder = { Text("What do you need done?") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = description, onValueChange = { description = it }, placeholder = { Text("Describe the work") }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("HOURLY" to "Per hour", "FIXED" to "Fixed price").forEach { (v, label) ->
                    val selected = payType == v
                    Text(
                        label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (selected) Color.White else Ids.colors.textPrimary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) Ids.colors.brand else Ids.colors.surfaceSoft)
                            .clickable { payType = v }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
                OutlinedTextField(
                    value = payAmount, onValueChange = { payAmount = it }, placeholder = { Text("Pay (RWF)") }, singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Box(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Ids.colors.surfaceSoft)
                    .clickable(enabled = !locating) { if (shareLocation) shareLocation = false else requestLocation() }
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
                        .clickable(enabled = !submitting) {
                            val amount = payAmount.toDoubleOrNull()
                            if (title.isBlank() || description.isBlank() || category.isBlank() || amount == null || amount <= 0) {
                                error = "Fill in every field with a real pay amount."
                                return@clickable
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
}

@Composable
private fun JobPostCard(
    post: JobPostDto, categoryLabel: String, isMine: Boolean, onChanged: () -> Unit, onContact: () -> Unit,
    favorited: Boolean = false, favoriteBusy: Boolean = false, onToggleFavorite: () -> Unit = {},
    posterTrustScore: Int? = null,
) {
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
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

    Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = Ids.colors.surface), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("$categoryLabel · ${relativeTimeAgo(post.createdAt)}", color = Ids.colors.brand, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    if (post.status == "FILLED") {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Ids.colors.surfaceSoft).padding(horizontal = 8.dp, vertical = 2.dp)) {
                            Text("FILLED", color = Ids.colors.textSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!isMine) Text(if (favorited) "♥" else "♡", color = if (favorited) Ids.colors.danger else Ids.colors.textSecondary, fontSize = 22.sp, modifier = Modifier.clickable(enabled = !favoriteBusy) { onToggleFavorite() }.padding(end = 8.dp))
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
                OutlinedTextField(
                    value = workerPhone,
                    onValueChange = { workerPhone = it },
                    placeholder = { Text("Worker's phone (optional)") },
                    singleLine = true,
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
            // Real post-transaction review, preset checklist with asymmetric public/
            // private visibility (2026-07-24) -- see backend HoodReviewService's own
            // doc comment. Only offered once a real worker was recorded at mark-filled
            // time; no pre-check for "already reviewed" (a real, honest v1 -- a second
            // attempt just surfaces the backend's own REVIEW_ALREADY_SUBMITTED error).
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
                                    NetworkClient.apiService.submitJobPostReview(
                                        post.id,
                                        SubmitHoodReviewRequest(selectedGoodPoints.toList(), selectedUncomfortablePoints.toList()),
                                    )
                                    reviewSubmitted = true
                                    showReviewSheet = false
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
                    ListingActionButton("Message poster", busy, filled = true, onClick = onContact)
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
