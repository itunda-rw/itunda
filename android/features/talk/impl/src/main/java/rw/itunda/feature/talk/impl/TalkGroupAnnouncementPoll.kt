package rw.itunda.feature.talk.impl

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import rw.itunda.core.designsystem.components.BackTopBar
import rw.itunda.core.designsystem.components.IdsButton
import rw.itunda.core.designsystem.components.IdsButtonSize
import rw.itunda.core.designsystem.components.IdsTextField
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.CreateGroupPollRequest
import rw.itunda.core.network.GroupPollResponse
import rw.itunda.core.network.GroupPollWithVotesDto
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.PostGroupAnnouncementRequest
import rw.itunda.core.network.VoteGroupPollRequest
import rw.itunda.core.network.superAppErrorMessage
import retrofit2.HttpException
import java.io.IOException

// Real group 공지/투표 (announcement + poll) (itunda Talk redesign, 2026-08-28) --
// deliberately open to ANY group member, matching every other current group action
// in this codebase (see backend GroupPollAnnouncementService's own doc comment on
// why no admin/role model exists yet -- not invented here as a side effect).
@Composable
internal fun GroupAnnouncementPollView(groupId: String, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var announcement by remember { mutableStateOf<String?>(null) }
    var polls by remember { mutableStateOf<List<GroupPollWithVotesDto>?>(null) }
    var draftAnnouncement by remember { mutableStateOf("") }
    var postingAnnouncement by remember { mutableStateOf(false) }
    var showNewPoll by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    suspend fun load() {
        try {
            val a = NetworkClient.talkApi.getGroupAnnouncement(groupId)
            if (a.success) announcement = a.announcement?.body
            val p = NetworkClient.talkApi.getGroupPolls(groupId)
            if (p.success) polls = p.polls
        } catch (_: Exception) {
            error = "Could not load announcements or polls."
        }
    }
    LaunchedEffect(Unit) { load() }

    if (showNewPoll) {
        NewGroupPollForm(
            onCancel = { showNewPoll = false },
            onCreated = { showNewPoll = false; coroutineScope.launch { load() } },
            createPoll = { question, options -> NetworkClient.talkApi.createGroupPoll(groupId, CreateGroupPollRequest(question, options)) },
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        BackTopBar("Announcement & polls", onBack)
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
            error?.let { item { Text(it, color = Ids.colors.danger, fontSize = 12.sp) } }
            item { Text("공지 Announcement", fontWeight = FontWeight.SemiBold, fontSize = 15.sp) }
            item {
                if (announcement != null) {
                    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Ids.layout.cardCornerRadius)).background(Ids.colors.surfaceSoft).padding(12.dp)) {
                        Text(announcement!!, color = Ids.colors.textPrimary, fontSize = 14.sp)
                    }
                } else {
                    Text("No announcement yet.", color = Ids.colors.textSecondary, fontSize = 13.sp)
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IdsTextField(value = draftAnnouncement, onValueChange = { draftAnnouncement = it }, label = "New announcement", modifier = Modifier.weight(1f))
                    Spacer(modifier = Modifier.width(8.dp))
                    IdsButton(
                        text = if (postingAnnouncement) "…" else "Post",
                        enabled = !postingAnnouncement && draftAnnouncement.isNotBlank(),
                        size = IdsButtonSize.Medium,
                        onClick = {
                            val body = draftAnnouncement.trim()
                            if (body.isEmpty()) return@IdsButton
                            postingAnnouncement = true
                            coroutineScope.launch {
                                try {
                                    NetworkClient.talkApi.postGroupAnnouncement(groupId, PostGroupAnnouncementRequest(body))
                                    draftAnnouncement = ""
                                    load()
                                } catch (e: HttpException) {
                                    error = superAppErrorMessage(e)
                                } catch (e: IOException) {
                                    error = "Couldn't reach itunda. Check your connection and try again."
                                } finally { postingAnnouncement = false }
                            }
                        },
                    )
                }
            }
            item { Spacer(modifier = Modifier.height(8.dp)) }
            item { Text("투표 Polls", fontWeight = FontWeight.SemiBold, fontSize = 15.sp) }
            polls?.let { list ->
                if (list.isEmpty()) item { Text("No polls yet.", color = Ids.colors.textSecondary, fontSize = 13.sp) }
                items(list, key = { it.poll.id }) { poll ->
                    GroupPollCard(poll, onVote = { optionId ->
                        coroutineScope.launch {
                            try {
                                val res = NetworkClient.talkApi.voteGroupPoll(groupId, poll.poll.id, VoteGroupPollRequest(optionId))
                                if (res.success) polls = polls.orEmpty().map { if (it.poll.id == poll.poll.id) res.poll else it }
                            } catch (e: HttpException) {
                                error = superAppErrorMessage(e)
                            } catch (e: IOException) {
                                error = "Couldn't reach itunda. Check your connection and try again."
                            }
                        }
                    })
                }
            }
            item { IdsButton(text = "New poll", onClick = { showNewPoll = true }, size = IdsButtonSize.Medium) }
        }
    }
}

@Composable
private fun GroupPollCard(poll: GroupPollWithVotesDto, onVote: (String) -> Unit) {
    val totalVotes = poll.voteCountByOptionId.values.sum().coerceAtLeast(1)
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Ids.layout.cardCornerRadius)).background(Ids.colors.surfaceSoft).padding(12.dp),
    ) {
        Text(poll.poll.question, color = Ids.colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(6.dp))
        poll.options.forEach { option ->
            val count = poll.voteCountByOptionId[option.id] ?: 0
            val mine = poll.myVoteOptionIds.contains(option.id)
            val percent = (count * 100 / totalVotes)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (mine) Ids.colors.brand.copy(alpha = 0.15f) else Color.Transparent)
                    .pressScaleClickable { onVote(option.id) }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(option.text, color = if (mine) Ids.colors.brand else Ids.colors.textPrimary, fontSize = 13.sp)
                Text("$percent% ($count)", color = Ids.colors.textSecondary, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun NewGroupPollForm(
    onCancel: () -> Unit,
    onCreated: () -> Unit,
    createPoll: suspend (question: String, options: List<String>) -> GroupPollResponse,
) {
    BackHandler(onBack = onCancel)
    var question by remember { mutableStateOf("") }
    var optionsText by remember { mutableStateOf(listOf("", "")) }
    var creating by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        BackTopBar("New poll", onCancel)
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
            error?.let { item { Text(it, color = Ids.colors.danger, fontSize = 12.sp) } }
            item { IdsTextField(value = question, onValueChange = { question = it }, label = "Question", modifier = Modifier.fillMaxWidth()) }
            itemsIndexed(optionsText) { index, value ->
                IdsTextField(
                    value = value,
                    onValueChange = { newValue -> optionsText = optionsText.toMutableList().also { it[index] = newValue } },
                    label = "Option ${index + 1}",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Text(
                    "+ Add option", color = Ids.colors.brand, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                    modifier = Modifier.pressScaleClickable { optionsText = optionsText + "" }.padding(vertical = 6.dp),
                )
            }
            item {
                IdsButton(
                    text = if (creating) "Creating…" else "Create poll",
                    enabled = !creating && question.isNotBlank() && optionsText.count { it.isNotBlank() } >= 2,
                    onClick = {
                        creating = true
                        coroutineScope.launch {
                            try {
                                createPoll(question.trim(), optionsText.map { it.trim() }.filter { it.isNotBlank() })
                                onCreated()
                            } catch (e: HttpException) {
                                error = superAppErrorMessage(e)
                            } catch (e: IOException) {
                                error = "Couldn't reach itunda. Check your connection and try again."
                                creating = false
                            }
                        }
                    },
                )
            }
        }
    }
}
