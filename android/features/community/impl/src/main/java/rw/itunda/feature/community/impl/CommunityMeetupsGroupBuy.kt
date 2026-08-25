package rw.itunda.feature.community.impl

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Comment
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import rw.itunda.core.designsystem.components.BackTopBar
import rw.itunda.core.designsystem.components.EmptyState
import rw.itunda.core.designsystem.components.ErrorCard
import rw.itunda.core.designsystem.components.IdsTextField
import rw.itunda.core.designsystem.components.HoodReportAction
import rw.itunda.core.designsystem.components.ListingActionButton
import rw.itunda.core.designsystem.itundaface.HeartFilled
import rw.itunda.core.designsystem.itundaface.SpeechBubbleGlyph
import rw.itunda.core.designsystem.itundaface.WishlistHeart
import rw.itunda.core.designsystem.components.NeighborhoodSetupPrompt
import rw.itunda.core.designsystem.components.ScrollFog
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

// Real fix (2026-08-26): split out of CommunityScreen.kt once that file grew past its
// file-size-lint baseline. Both sections are real post-detail sub-features
// (recurring meetup scheduling/check-in, group-buy finalization), only rendered
// inside CommunityPostDetailScreen for their respective post categories. Same
// package, so zero import changes at any call site.

/**
 * Real 당근모임 (Karrot Meetups) recurring schedule + attendance check-in -- see
 * rw.itunda.community.CommunityService.scheduleMeetupSessions/checkIntoSession's own doc
 * comments. Only rendered for a real category == "meetup" post; the author gets a real
 * schedule form, any real joined member gets a real per-session check-in button.
 * bank-mfe already has this; this is the first Android client.
 */
@Composable
internal fun MeetupSessionsSection(post: CommunityPostDto, currentUserId: String?) {
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
        SkeletonBlock()
    } else if (list.isEmpty()) {
        Text("No sessions scheduled yet.", color = Ids.colors.textSecondary, fontSize = 13.sp)
    } else {
        // Real fix (flat-design sweep): dropped the per-row Card -- a session list
        // separates entries with spacing alone.
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            list.forEach { s ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
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
    if (isAuthor) {
        Spacer(modifier = Modifier.height(8.dp))
        // Real fix (flat-design sweep): dropped the Card wrapper -- a section on an
        // otherwise-flat detail screen.
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Schedule sessions (up to 6)", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                dates.forEachIndexed { i, d ->
                    IdsTextField(
                        value = d, onValueChange = { dates[i] = it },
                        label = "Hours from now", singleLine = true,
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number, modifier = Modifier.fillMaxWidth(),
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
internal fun GroupBuyFinalizeSection(post: CommunityPostDto, currentUserId: String?) {
    if (currentUserId == null || currentUserId != post.authorId) return
    var totalAmount by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    Spacer(modifier = Modifier.height(16.dp))
    if (done) {
        Text(
            "Split request sent -- see it in your group chat's Split bill tab.",
            color = Ids.colors.brand, fontWeight = FontWeight.Bold, fontSize = 13.sp,
        )
        return
    }
    // Real fix (flat-design sweep): dropped the Card wrapper -- a section on an
    // otherwise-flat detail screen.
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Split the cost", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(
                "Enter what you paid up front -- every real member who joined will be asked for their even share.",
                color = Ids.colors.textSecondary, fontSize = 12.sp,
            )
            IdsTextField(value = totalAmount, onValueChange = { totalAmount = it }, label = "Total amount (RWF)", keyboardType = androidx.compose.ui.text.input.KeyboardType.Number, isAmount = true, modifier = Modifier.fillMaxWidth())
            IdsTextField(value = description, onValueChange = { description = it }, label = "What was this for?", modifier = Modifier.fillMaxWidth())
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
                            NetworkClient.apiService.finalizeGroupBuy(post.id, java.util.UUID.randomUUID().toString(), FinalizeGroupBuyRequest(amount, description.trim()))
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
