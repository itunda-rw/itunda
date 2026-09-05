package rw.itunda.feature.talk.impl

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import coil.compose.AsyncImage
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import rw.itunda.core.designsystem.components.pressScaleClickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddReaction
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.filled.PushPin as PushPinFilled
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import rw.itunda.core.designsystem.components.EmptyState
import rw.itunda.core.designsystem.components.ErrorCard
import rw.itunda.core.designsystem.components.IdsAvatar
import rw.itunda.core.designsystem.components.IdsButton
import rw.itunda.core.designsystem.components.IdsButtonSize
import rw.itunda.core.designsystem.components.IdsButtonVariant
import rw.itunda.core.designsystem.components.IdsSegmentedControl
import rw.itunda.core.designsystem.components.IdsTextField
import rw.itunda.core.designsystem.components.SkeletonBlock
import rw.itunda.core.designsystem.components.TabHeader
import rw.itunda.core.designsystem.components.chatMessageTime
import rw.itunda.core.designsystem.components.shouldShowChatTimestamp
import rw.itunda.core.designsystem.itundaface.DiceGlyph
import rw.itunda.core.designsystem.itundaface.GiftSettleUp
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.AddGroupMemberRequest
import rw.itunda.core.network.MessageResponse
import rw.itunda.core.network.GroupMessageResponse
import rw.itunda.core.network.SetGroupDescriptionRequest
import rw.itunda.core.network.SetGroupPhotoUrlRequest
import rw.itunda.core.network.AttachSplitBillReceiptRequest
import rw.itunda.core.network.ConversationSummaryDto
import rw.itunda.core.network.CreateChatReportRequest
import rw.itunda.core.network.CreateGroupRequest
import rw.itunda.core.network.CreateDirectSplitBillRequest
import rw.itunda.core.network.CreateSplitBillRequest
import rw.itunda.core.network.EmoticonDto
import rw.itunda.core.network.EmoticonPackDto
import rw.itunda.core.network.GiftDto
import rw.itunda.core.network.GIFT_THEME_LABELS
import rw.itunda.core.network.GiftEmoticonPackRequest
import rw.itunda.core.network.GiftVoucherDto
import rw.itunda.core.network.OwnedEmoticonPackDto
import rw.itunda.core.network.ProductSearchResultDto
import rw.itunda.core.network.PurchaseGiftVoucherRequest
import rw.itunda.core.network.SendEmoticonRequest
import rw.itunda.core.network.GroupMemberDto
import rw.itunda.core.network.ForwardMessageRequest
import rw.itunda.core.network.GroupMessageDto
import rw.itunda.core.network.GroupSummaryDto
import rw.itunda.core.network.MessageDto
import rw.itunda.core.network.MessagingSocketPush
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.PriceOfferDto
import rw.itunda.core.network.PropertyPriceOfferDto
import rw.itunda.core.network.ReactionGroupDto
import rw.itunda.core.network.RespondToOfferRequest
import rw.itunda.core.network.RespondToPropertyOfferRequest
import rw.itunda.core.network.SendGiftInConversationRequest
import rw.itunda.core.network.SendGroupMessageRequest
import rw.itunda.core.network.SendMessageRequest
import rw.itunda.core.network.SetConversationArchivedRequest
import rw.itunda.core.network.SetConversationPinnedToTopRequest
import rw.itunda.core.network.SetConversationQuietRequest
import rw.itunda.core.network.SplitBillWithParticipants
import rw.itunda.core.network.StartConversationRequest
import rw.itunda.core.network.TalkContactDto
import rw.itunda.core.network.ToggleReactionRequest
import rw.itunda.core.network.TokenStore
import rw.itunda.core.network.isDeviceNotVerifiedError
import rw.itunda.core.network.superAppErrorMessage
import java.io.IOException
import java.time.Instant
import java.util.UUID

// Split from TalkSplitBills.kt (2026-08-19, same pass) -- that file itself
// crossed file-size-lint.py's 500-line guideline (mostly its own copied import
// list, ~158 lines -- an import-trim attempt caused real, non-obvious Kotlin
// overload-resolution errors specific to DirectSplitBillsView's items() call, not
// worth chasing blind; reverted rather than risk a real bug). Splitting
// Group/Direct into their own files, each keeping the full safe import list, was
// the lower-risk fix -- both land comfortably under the guideline on their own.
// Real KakaoPay-style split bill (2026-07-22) -- found fully built on the backend
// (rw.itunda.splitbill) with zero client UI anywhere, despite group chat itself
// being fully wired. A flat, even split among picked group members (excluding the
// organizer); each participant pays their own share directly to the organizer via a
// real account-to-account push, no escrow -- see SplitBill.kt's own doc comment.
@Composable
internal fun GroupSplitBillsView(
    groupConversationId: String,
    members: List<GroupMemberDto>,
    currentUserId: String?,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    var splitBills by remember { mutableStateOf<List<SplitBillWithParticipants>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busyId by remember { mutableStateOf<String?>(null) }
    var showNewForm by remember { mutableStateOf(false) }
    var amountText by remember { mutableStateOf("") }
    var descriptionText by remember { mutableStateOf("") }
    var selectedParticipantIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    // Real KakaoPay 사다리타기 (ladder-game) mode (2026-07-25) -- see backend
    // SplitBillService.ladderSplit's own doc comment for the 3 variance levels.
    var ladderMode by remember { mutableStateOf(false) }
    var varianceLevel by remember { mutableStateOf(1) }
    var receiptUrlDrafts by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    val coroutineScope = rememberCoroutineScope()

    suspend fun refresh() {
        try {
            splitBills = NetworkClient.apiService.getSplitBillsForGroup(groupConversationId).splitBills
            error = null
        } catch (_: Exception) {
            error = "Could not load split bills."
        }
    }
    LaunchedEffect(groupConversationId) { refresh() }

    val otherMembers = members.filter { it.userId != currentUserId }

    Column(modifier = Modifier.fillMaxSize()) {
        BackTopBar("Split bills", onBack)
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            error?.let { item { Text(it, color = Ids.colors.danger, fontSize = 13.sp) } }
            item {
                if (!showNewForm) {
                    IdsButton(text = "Split a bill", onClick = { showNewForm = true })
                } else {
                    Column {
                        IdsTextField(amountText, { amountText = it }, label = "Total amount (RWF)", keyboardType = KeyboardType.Number, isAmount = true, modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(8.dp))
                        IdsTextField(descriptionText, { descriptionText = it }, label = "What was it for?", modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Split with", fontSize = 13.sp, color = Ids.colors.textSecondary)
                        otherMembers.forEach { member ->
                            Row(
                                modifier = Modifier.fillMaxWidth().pressScaleClickable {
                                    selectedParticipantIds = if (member.userId in selectedParticipantIds) {
                                        selectedParticipantIds - member.userId
                                    } else {
                                        selectedParticipantIds + member.userId
                                    }
                                }.padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(member.name, fontSize = 14.sp)
                                Text(if (member.userId in selectedParticipantIds) "Selected" else "Tap to add", fontSize = 12.sp, color = Ids.colors.textSecondary)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth().pressScaleClickable { ladderMode = !ladderMode }.padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                DiceGlyph(size = 14.dp)
                                Text("Ladder game (randomized split)", fontSize = 13.sp)
                            }
                            Text(if (ladderMode) "On" else "Off", fontSize = 12.sp, color = if (ladderMode) Ids.colors.brand else Ids.colors.textSecondary, fontWeight = FontWeight.Bold)
                        }
                        if (ladderMode) {
                            Text(
                                when (varianceLevel) {
                                    3 -> "One random person pays the whole thing -- everyone else pays nothing."
                                    2 -> "Wider random spread -- shares can differ a lot."
                                    else -> "Mild random spread around an even split."
                                },
                                fontSize = 12.sp, color = Ids.colors.textSecondary,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                                listOf(1, 2, 3).forEach { level ->
                                    val selected = varianceLevel == level
                                    Box(
                                        modifier = Modifier.clip(RoundedCornerShape(8.dp))
                                            .background(if (selected) Ids.colors.brand else Ids.colors.surfaceSoft)
                                            .pressScaleClickable { varianceLevel = level }
                                            .padding(horizontal = 14.dp, vertical = 8.dp),
                                    ) {
                                        Text("Level $level", color = if (selected) Color.White else Ids.colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        IdsButton(
                            text = if (busyId == "new") "Creating…" else "Create split bill",
                            enabled = busyId == null && amountText.toBigDecimalOrNull()?.let { it > java.math.BigDecimal.ZERO } == true &&
                                descriptionText.isNotBlank() && selectedParticipantIds.isNotEmpty(),
                            onClick = {
                                val amount = amountText.toBigDecimalOrNull() ?: return@IdsButton
                                busyId = "new"
                                coroutineScope.launch {
                                    try {
                                        NetworkClient.apiService.createSplitBill(
                                            groupConversationId,
                                            UUID.randomUUID().toString(),
                                            CreateSplitBillRequest(
                                                amount, descriptionText, selectedParticipantIds.toList(),
                                                mode = if (ladderMode) "LADDER" else "EVEN",
                                                ladderVarianceLevel = if (ladderMode) varianceLevel else null,
                                            ),
                                        )
                                        amountText = ""; descriptionText = ""; selectedParticipantIds = emptySet(); showNewForm = false; ladderMode = false
                                        refresh()
                                    } catch (e: HttpException) {
                                        error = superAppErrorMessage(e)
                                    } catch (e: IOException) {
                                        error = "Couldn't reach itunda. Check your connection and try again."
                                    } finally { busyId = null }
                                }
                            },
                        )
                    }
                }
            }
            val current = splitBills
            if (current == null) item { SkeletonBlock() }
            else if (current.isEmpty()) item { Text("No split bills in this group yet.", color = Ids.colors.textSecondary, fontSize = 13.sp) }
            else items(current, key = { it.splitBill.id }) { entry ->
                val myShare = entry.participants.find { it.userId == currentUserId }
                val isOrganizer = entry.splitBill.organizerId == currentUserId
                val hasPending = entry.participants.any { it.status == "PENDING" }
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(entry.splitBill.description, fontWeight = FontWeight.SemiBold)
                        val roundLabel = if (entry.splitBill.currentRound > 1) " · Round ${entry.splitBill.currentRound}" else ""
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("Total ${"%,.0f".format(entry.splitBill.totalAmount)} RWF · ${entry.splitBill.status}", fontSize = 13.sp, color = Ids.colors.textSecondary)
                            if (entry.splitBill.mode == "LADDER") {
                                Text(" ·", fontSize = 13.sp, color = Ids.colors.textSecondary)
                                DiceGlyph(size = 11.dp)
                                Text("Ladder L${entry.splitBill.ladderVarianceLevel}", fontSize = 13.sp, color = Ids.colors.textSecondary)
                            }
                            if (roundLabel.isNotEmpty()) Text(roundLabel, fontSize = 13.sp, color = Ids.colors.textSecondary)
                        }
                        entry.participants.forEach { participant ->
                            val name = members.find { it.userId == participant.userId }?.name ?: participant.userId.take(8)
                            Text("$name: ${"%,.0f".format(participant.shareAmount)} RWF (${participant.status})", fontSize = 13.sp)
                        }
                        entry.splitBill.receiptImageUrl?.let { url ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                GiftSettleUp(size = 12.dp)
                                Text("Receipt: $url", fontSize = 12.sp, color = Ids.colors.brand)
                            }
                        }
                        if (myShare != null && myShare.status == "PENDING") {
                            Spacer(modifier = Modifier.height(8.dp))
                            IdsButton(
                                text = if (busyId == entry.splitBill.id) "Paying…" else "Pay my share (${"%,.0f".format(myShare.shareAmount)} RWF)",
                                enabled = busyId == null,
                                onClick = {
                                    busyId = entry.splitBill.id
                                    coroutineScope.launch {
                                        try {
                                            NetworkClient.apiService.paySplitBillShare(entry.splitBill.id, UUID.randomUUID().toString())
                                            refresh()
                                        } catch (e: retrofit2.HttpException) {
                                            error = superAppErrorMessage(e)
                                        } catch (e: IOException) {
                                            error = "Couldn't reach itunda. Check your connection and try again."
                                        } finally { busyId = null }
                                    }
                                },
                            )
                        }
                        if (isOrganizer && entry.splitBill.receiptImageUrl == null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                IdsTextField(
                                    receiptUrlDrafts[entry.splitBill.id] ?: "",
                                    { receiptUrlDrafts = receiptUrlDrafts + (entry.splitBill.id to it) },
                                    label = "Receipt photo URL",
                                    modifier = Modifier.weight(1f),
                                )
                                IdsButton(
                                    text = "Attach",
                                    enabled = busyId == null && !(receiptUrlDrafts[entry.splitBill.id].isNullOrBlank()),
                                    size = IdsButtonSize.Small,
                                    onClick = {
                                        val url = receiptUrlDrafts[entry.splitBill.id] ?: return@IdsButton
                                        busyId = entry.splitBill.id
                                        coroutineScope.launch {
                                            try {
                                                NetworkClient.apiService.attachSplitBillReceipt(entry.splitBill.id, AttachSplitBillReceiptRequest(url))
                                                receiptUrlDrafts = receiptUrlDrafts - entry.splitBill.id
                                                refresh()
                                            } catch (e: HttpException) {
                                                error = superAppErrorMessage(e)
                                            } catch (e: IOException) {
                                                error = "Couldn't reach itunda. Check your connection and try again."
                                            } finally { busyId = null }
                                        }
                                    },
                                )
                            }
                        }
                        if (isOrganizer && entry.splitBill.status == "OPEN" && hasPending && entry.splitBill.currentRound < 5) {
                            Spacer(modifier = Modifier.height(8.dp))
                            IdsButton(
                                text = "Nudge unpaid → round ${entry.splitBill.currentRound + 1}",
                                enabled = busyId == null,
                                variant = IdsButtonVariant.Tinted,
                                onClick = {
                                    busyId = entry.splitBill.id
                                    coroutineScope.launch {
                                        try {
                                            NetworkClient.apiService.requestSplitBillNextRound(entry.splitBill.id, java.util.UUID.randomUUID().toString())
                                            refresh()
                                        } catch (e: HttpException) {
                                            error = superAppErrorMessage(e)
                                        } catch (e: IOException) {
                                            error = "Couldn't reach itunda. Check your connection and try again."
                                        } finally { busyId = null }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
