package rw.itunda.feature.jobs.impl

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import java.util.Locale
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
import androidx.compose.ui.res.stringResource
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

// Real fix (2026-08-26): split out of JobsScreen.kt once that file grew past its
// file-size-lint baseline. These 3 sub-screens (wishlist, my applications, new job
// post form) are self-contained, only rendered inside JobsContent's own sheet/tab
// flow -- distinct from the main feed (JobsContent) and the JobPostCard row item
// left behind. Same package, so zero import changes anywhere.

@Composable
internal fun JobPostWishlistView(onRemoved: () -> Unit) {
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
                        Text(String.format(Locale.US, "${favorite.category} · %,.0f RWF", favorite.payAmount), color = Ids.colors.textSecondary, fontSize = 12.sp)
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
internal fun MyJobApplicationsView() {
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
internal fun NewJobPostForm(categories: List<JobCategoryDto>, onCreated: () -> Unit, onCancel: () -> Unit) {
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
            IdsTextField(value = title, onValueChange = { title = it }, label = stringResource(R.string.jobs_title_placeholder), singleLine = true, modifier = Modifier.fillMaxWidth())
            IdsTextField(value = description, onValueChange = { description = it }, label = stringResource(R.string.jobs_description_placeholder), modifier = Modifier.fillMaxWidth())
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
                ) { Text(if (submitting) stringResource(R.string.jobs_posting) else stringResource(R.string.jobs_post_job), color = Color.White, fontWeight = FontWeight.Bold) }
            }
    }
}
