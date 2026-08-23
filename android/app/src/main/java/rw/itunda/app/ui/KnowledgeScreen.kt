package rw.itunda.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import rw.itunda.core.designsystem.components.pressScaleClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import rw.itunda.core.designsystem.components.IdsTextField
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
import retrofit2.HttpException
import rw.itunda.core.designsystem.components.BackTopBar
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.KnowledgeAnswerDto
import rw.itunda.core.network.KnowledgeCategory
import rw.itunda.core.network.KnowledgeQuestionDto
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.PostKnowledgeAnswerRequest
import rw.itunda.core.network.PostKnowledgeQuestionRequest
import rw.itunda.core.network.superAppErrorMessage
import java.io.IOException
import rw.itunda.core.designsystem.components.EmptyState

// Real Naver 지식iN (Knowledge iN) open-topic community Q&A (item 225) -- a genuinely
// different shape from RideScreen.kt/DesignatedDriverScreen.kt/BikeRentalScreen.kt/
// ParkingScreen.kt/BusScreen.kt above: no account movement, no location -- just a real
// question -> competing answers -> asker-adopts-one-best-answer content flow. bank-mfe
// already has this; this is the first Android client, mirroring its Browse/Mine toggle
// exactly.
private enum class KnowledgeTab { BROWSE, MINE }

@Composable
fun KnowledgeScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var tab by remember { mutableStateOf(KnowledgeTab.BROWSE) }
    var categories by remember { mutableStateOf<List<KnowledgeCategory>>(emptyList()) }
    var activeCategory by remember { mutableStateOf<String?>(null) }
    var questions by remember { mutableStateOf<List<KnowledgeQuestionDto>?>(null) }
    var myAnswers by remember { mutableStateOf<List<KnowledgeAnswerDto>?>(null) }
    var reputation by remember { mutableStateOf<Int?>(null) }
    var openQuestionId by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    fun load() {
        error = null
        questions = null
        coroutineScope.launch {
            try {
                questions = if (tab == KnowledgeTab.MINE) {
                    myAnswers = NetworkClient.apiService.getMyKnowledgeAnswers().answers
                    NetworkClient.apiService.getMyKnowledgeQuestions().questions
                } else {
                    NetworkClient.apiService.getKnowledgeQuestions(activeCategory).questions
                }
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            }
        }
    }

    LaunchedEffect(Unit) {
        try {
            categories = NetworkClient.apiService.getKnowledgeCategories().categories
            reputation = NetworkClient.apiService.getMyKnowledgeReputation().adoptedAnswerCount
        } catch (_: Exception) {
            // Non-critical.
        }
    }
    LaunchedEffect(tab, activeCategory) { load() }

    val openId = openQuestionId
    if (openId != null) {
        KnowledgeQuestionDetailScreen(questionId = openId, onBack = { openQuestionId = null; load() })
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        BackTopBar(title = "Q&A", onBack = onBack)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = Ids.layout.screenHorizontal, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                // Real fix (flat-design sweep): dropped the Card wrapper -- a stat
                // row on an otherwise-flat screen.
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Your reputation", color = Ids.colors.textSecondary, fontSize = 13.sp)
                        val count = reputation
                        Text(
                            if (count == null) "…" else "$count adopted answer${if (count == 1) "" else "s"}",
                            color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                        )
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Ids.colors.surfaceSoft).padding(4.dp),
                ) {
                    listOf(KnowledgeTab.BROWSE to "Browse", KnowledgeTab.MINE to "Mine").forEach { (value, label) ->
                        val selected = tab == value
                        Box(
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                                .background(if (selected) Ids.colors.brand else Color.Transparent)
                                .pressScaleClickable { tab = value }.padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(label, color = if (selected) Color.White else Ids.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            if (tab == KnowledgeTab.BROWSE && categories.isNotEmpty()) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            modifier = Modifier.clip(RoundedCornerShape(8.dp))
                                .background(if (activeCategory == null) Ids.colors.brand else Ids.colors.surfaceSoft)
                                .pressScaleClickable { activeCategory = null }.padding(horizontal = 12.dp, vertical = 6.dp),
                        ) { Text("All", color = if (activeCategory == null) Color.White else Ids.colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        categories.forEach { c ->
                            val selected = activeCategory == c.id
                            Box(
                                modifier = Modifier.clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) Ids.colors.brand else Ids.colors.surfaceSoft)
                                    .pressScaleClickable { activeCategory = c.id }.padding(horizontal = 12.dp, vertical = 6.dp),
                            ) { Text(c.label, color = if (selected) Color.White else Ids.colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
            if (tab == KnowledgeTab.BROWSE) {
                item { KnowledgeAskCard(categories = categories, onAsked = ::load) }
            }
            error?.let { msg -> item { Text(msg, color = Ids.colors.danger, fontSize = 13.sp) } }
            val list = questions
            if (list == null) {
                item { Text("Loading…", color = Ids.colors.textSecondary, fontSize = 13.sp) }
            } else if (list.isEmpty()) {
                item { EmptyState("No questions yet — be the first to ask.") }
            } else {
                items(list, key = { it.id }) { q ->
                    // Real fix (flat-design sweep): dropped the per-row Card -- a
                    // question list separates entries with spacing alone.
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth().pressScaleClickable { openQuestionId = q.id },
                    ) {
                            Text(
                                (if (q.adoptedAnswerId != null) "✅ " else "") + q.title,
                                color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                            )
                            Text(categories.find { it.id == q.category }?.label ?: q.category, color = Ids.colors.textSecondary, fontSize = 12.sp)
                    }
                }
            }
            if (tab == KnowledgeTab.MINE) {
                val answers = myAnswers
                if (answers != null && answers.isNotEmpty()) {
                    item { Text("Your answers", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                    items(answers, key = { it.id }) { a ->
                        // Real fix (flat-design sweep): dropped the per-row Card.
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(if (a.isAdopted) "✅ Adopted" else "Pending", color = Ids.colors.textSecondary, fontSize = 12.sp)
                                Text(a.body, color = Ids.colors.textPrimary, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KnowledgeAskCard(categories: List<KnowledgeCategory>, onAsked: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    var category by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    if (!open) {
        Box(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Ids.colors.surfaceSoft)
                .pressScaleClickable { open = true }.padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) { Text("+ Ask a question", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
        return
    }

    // Real fix (flat-design sweep): dropped the Card wrapper -- an inline form
    // section on an otherwise-flat screen.
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                categories.forEach { c ->
                    val selected = category == c.id
                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(8.dp))
                            .background(if (selected) Ids.colors.brand else Ids.colors.surfaceSoft)
                            .pressScaleClickable { category = c.id }.padding(horizontal = 12.dp, vertical = 6.dp),
                    ) { Text(c.label, color = if (selected) Color.White else Ids.colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                }
            }
            IdsTextField(value = title, onValueChange = { title = it }, label = "Your question", modifier = Modifier.fillMaxWidth())
            IdsTextField(value = body, onValueChange = { body = it }, label = "Add more detail", modifier = Modifier.fillMaxWidth())
            error?.let { Text(it, color = Ids.colors.danger, fontSize = 12.sp) }
            Box(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .background(if (category.isNotEmpty() && title.isNotBlank() && body.isNotBlank()) Ids.colors.brand else Ids.colors.surfaceSoft)
                    .pressScaleClickable(enabled = !submitting && category.isNotEmpty() && title.isNotBlank() && body.isNotBlank()) {
                        submitting = true
                        error = null
                        coroutineScope.launch {
                            try {
                                NetworkClient.apiService.postKnowledgeQuestion(PostKnowledgeQuestionRequest(category, title.trim(), body.trim()))
                                category = ""; title = ""; body = ""; open = false
                                onAsked()
                            } catch (e: HttpException) {
                                error = superAppErrorMessage(e)
                            } catch (e: IOException) {
                                error = "Couldn't reach itunda. Check your connection and try again."
                            } finally {
                                submitting = false
                            }
                        }
                    }.padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) { Text(if (submitting) "Posting…" else "Post question", color = Color.White, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun KnowledgeQuestionDetailScreen(questionId: String, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var question by remember { mutableStateOf<KnowledgeQuestionDto?>(null) }
    var answers by remember { mutableStateOf<List<KnowledgeAnswerDto>?>(null) }
    var answerBody by remember { mutableStateOf("") }
    var answering by remember { mutableStateOf(false) }
    var busyAnswerId by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val currentUserId = NetworkClient.currentTokenStore().getUserId()

    fun load() {
        error = null
        coroutineScope.launch {
            try {
                question = NetworkClient.apiService.getKnowledgeQuestion(questionId).question
                answers = NetworkClient.apiService.getKnowledgeAnswers(questionId).answers
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            }
        }
    }
    LaunchedEffect(questionId) { load() }

    val isAsker = question != null && currentUserId != null && question?.askerId == currentUserId

    Column(modifier = Modifier.fillMaxSize()) {
        BackTopBar(title = "Question", onBack = onBack)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = Ids.layout.screenHorizontal, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            error?.let { msg -> item { Text(msg, color = Ids.colors.danger, fontSize = 13.sp) } }
            val q = question
            if (q != null) {
                item {
                    // Real fix (flat-design sweep): dropped the Card wrapper -- the
                    // screen's own main content.
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(q.title, color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Text(q.body, color = Ids.colors.textSecondary, fontSize = 14.sp)
                    }
                }
            }
            item { Text("Answers", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
            val list = answers
            if (list == null) {
                item { Text("Loading…", color = Ids.colors.textSecondary, fontSize = 13.sp) }
            } else if (list.isEmpty()) {
                item { EmptyState("No answers yet — be the first to help.") }
            } else {
                items(list, key = { it.id }) { a ->
                    // Real fix (flat-design sweep): dropped the per-row Card.
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (a.isAdopted) Text("✅ Adopted answer", color = Ids.colors.brand, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            Text(a.body, color = Ids.colors.textPrimary, fontSize = 13.sp)
                            if (isAsker && q?.adoptedAnswerId == null) {
                                Box(
                                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Ids.colors.surfaceSoft)
                                        .pressScaleClickable(enabled = busyAnswerId != a.id) {
                                            busyAnswerId = a.id
                                            error = null
                                            coroutineScope.launch {
                                                try {
                                                    NetworkClient.apiService.adoptKnowledgeAnswer(questionId, a.id)
                                                    load()
                                                } catch (e: HttpException) {
                                                    error = superAppErrorMessage(e)
                                                } catch (e: IOException) {
                                                    error = "Couldn't reach itunda. Check your connection and try again."
                                                } finally {
                                                    busyAnswerId = null
                                                }
                                            }
                                        }.padding(horizontal = 12.dp, vertical = 8.dp),
                                ) { Text(if (busyAnswerId == a.id) "…" else "Adopt this answer", color = Ids.colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                            }
                    }
                }
            }
            if (q?.adoptedAnswerId == null) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        IdsTextField(value = answerBody, onValueChange = { answerBody = it }, label = "Write an answer", modifier = Modifier.weight(1f))
                        Box(
                            modifier = Modifier.clip(RoundedCornerShape(10.dp))
                                .background(if (answerBody.isNotBlank()) Ids.colors.brand else Ids.colors.surfaceSoft)
                                .pressScaleClickable(enabled = !answering && answerBody.isNotBlank()) {
                                    answering = true
                                    error = null
                                    coroutineScope.launch {
                                        try {
                                            NetworkClient.apiService.postKnowledgeAnswer(questionId, PostKnowledgeAnswerRequest(answerBody.trim()))
                                            answerBody = ""
                                            load()
                                        } catch (e: HttpException) {
                                            error = superAppErrorMessage(e)
                                        } catch (e: IOException) {
                                            error = "Couldn't reach itunda. Check your connection and try again."
                                        } finally {
                                            answering = false
                                        }
                                    }
                                }.padding(horizontal = 16.dp, vertical = 14.dp),
                        ) { Text(if (answering) "…" else "Send", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                    }
                }
            }
        }
    }
}
