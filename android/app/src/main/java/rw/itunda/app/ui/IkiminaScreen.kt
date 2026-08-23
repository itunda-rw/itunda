package rw.itunda.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.components.rememberCountUp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import rw.itunda.core.designsystem.components.IdsTextField
import rw.itunda.core.designsystem.components.SkeletonBlock
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
import rw.itunda.core.network.CreateIkiminaRequest
import rw.itunda.core.network.IkiminaDetailResponse
import rw.itunda.core.network.IkiminaDto
import rw.itunda.core.network.InviteIkiminaMemberRequest
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.superAppErrorMessage
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.designsystem.components.BackTopBar
import java.io.IOException
import java.util.UUID
import rw.itunda.core.designsystem.components.EmptyState

/**
 * Real ikimina -- Rwanda's own rotating savings & credit association (ROSCA). See the
 * backend's Ikimina.kt doc comment for the full sourced account (real ROSCA
 * literature, and a real existing Rwandan startup, smartikimina.rw, already
 * digitizing this exact mechanic via mobile money). Distinct from GroupAccountScreen.kt
 * (Kakao Bank 모임통장): that feature has one permanent owner with sole withdrawal
 * authority; an ikimina rotates the full pot to a different member each real round,
 * until everyone has been paid exactly once. Genuinely the first feature in this
 * codebase not sourced from Toss/Kakao/Naver/Coupang. Mirrors bank-mfe's
 * IkiminaSection/IkiminaDetailView/CreateIkiminaForm exactly, same no-ViewModel,
 * direct-NetworkClient-call convention GroupAccountScreen.kt already established.
 */
private enum class IkiminaMode { LIST, DETAIL }

@Composable
fun IkiminaScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var mode by remember { mutableStateOf(IkiminaMode.LIST) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        val backAction: () -> Unit = if (mode == IkiminaMode.DETAIL) {
            { mode = IkiminaMode.LIST; selectedId = null; refreshKey++ }
        } else onBack
        BackTopBar(title = "Ikimina (rotating savings)", onBack = backAction)

        if (mode == IkiminaMode.DETAIL && selectedId != null) {
            IkiminaDetailContent(id = selectedId!!)
        } else {
            IkiminaListContent(
                refreshKey = refreshKey,
                onOpen = { selectedId = it; mode = IkiminaMode.DETAIL },
            )
        }
    }
}

@Composable
private fun IkiminaListContent(refreshKey: Int, onOpen: (String) -> Unit) {
    var ikiminas by remember { mutableStateOf<List<IkiminaDto>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var contributionAmount by remember { mutableStateOf("") }
    var cycleFrequencyDays by remember { mutableStateOf(30) }
    var memberCap by remember { mutableStateOf("10") }
    var frequencyMenuOpen by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    fun load() {
        coroutineScope.launch {
            try {
                val res = NetworkClient.apiService.getMyIkiminas()
                if (res.success) ikiminas = res.ikiminas
                error = null
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            }
        }
    }
    LaunchedEffect(refreshKey) { load() }

    fun create() {
        val amount = contributionAmount.toBigDecimalOrNull()
        val cap = memberCap.toIntOrNull()
        if (name.isBlank() || amount == null || amount.signum() <= 0 || cap == null || cap < 2) return
        creating = true
        coroutineScope.launch {
            try {
                NetworkClient.apiService.createIkimina(CreateIkiminaRequest(name.trim(), amount, cycleFrequencyDays, cap))
                name = ""; contributionAmount = ""; memberCap = "10"
                showCreate = false
                load()
                rw.itunda.core.designsystem.components.IdsToast.show(coroutineScope, "Ikimina group created.")
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                creating = false
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Ids.layout.screenHorizontal, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                "Everyone contributes the same amount each round; one member takes home the full pot, in turn.",
                color = Ids.colors.textSecondary, fontSize = 12.sp,
            )
        }
        item {
            if (!showCreate) {
                Box(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Ids.colors.surfaceSoft)
                        .pressScaleClickable { showCreate = true }.padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("+ New ikimina", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold) }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    IdsTextField(value = name, onValueChange = { name = it }, label = "Group name (e.g. Umuryango)", modifier = Modifier.fillMaxWidth())
                    IdsTextField(value = contributionAmount, onValueChange = { contributionAmount = it }, label = "Contribution per round (RWF)", modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Ids.colors.surfaceSoft)
                                    .pressScaleClickable { frequencyMenuOpen = true }.padding(vertical = 14.dp, horizontal = 12.dp),
                            ) { Text(if (cycleFrequencyDays == 7) "Weekly" else "Monthly", fontSize = 14.sp) }
                            DropdownMenu(expanded = frequencyMenuOpen, onDismissRequest = { frequencyMenuOpen = false }) {
                                DropdownMenuItem(text = { Text("Weekly") }, onClick = { cycleFrequencyDays = 7; frequencyMenuOpen = false })
                                DropdownMenuItem(text = { Text("Monthly") }, onClick = { cycleFrequencyDays = 30; frequencyMenuOpen = false })
                            }
                        }
                        IdsTextField(value = memberCap, onValueChange = { memberCap = it }, label = "Max members", modifier = Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(Ids.colors.surfaceSoft)
                                .pressScaleClickable { showCreate = false }.padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text("Cancel", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold) }
                        Box(
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(Ids.colors.brand)
                                .pressScaleClickable(enabled = !creating) { create() }.padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text(if (creating) "Creating…" else "Create", color = Color.White, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
        error?.let { msg -> item { Text(msg, color = Ids.colors.danger, fontSize = 13.sp) } }
        when {
            ikiminas == null -> item {
                SkeletonBlock(height = 64.dp)
            }
            ikiminas!!.isEmpty() -> item {
                EmptyState("No ikimina groups yet — start one with people you trust.")
            }
            else -> items(ikiminas!!, key = { it.id }) { k ->
                Card(
                    shape = RoundedCornerShape(Ids.layout.cardCornerRadius),
                    colors = CardDefaults.cardColors(containerColor = Ids.colors.surface),
                    modifier = Modifier.fillMaxWidth().pressScaleClickable { onOpen(k.id) },
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(k.name, color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(
                            when (k.status) {
                                "FORMING" -> "Forming — invite members before starting"
                                "ACTIVE" -> "Round ${k.currentRound}"
                                else -> "Completed"
                            },
                            color = Ids.colors.textSecondary, fontSize = 12.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IkiminaDetailContent(id: String) {
    var detail by remember { mutableStateOf<IkiminaDetailResponse?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var phoneNumber by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var payoutMessage by remember { mutableStateOf<String?>(null) }
    val myUserId = NetworkClient.currentTokenStore().getUserId()
    val coroutineScope = rememberCoroutineScope()

    fun load() {
        coroutineScope.launch {
            try {
                detail = NetworkClient.apiService.getIkimina(id)
                error = null
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            }
        }
    }
    LaunchedEffect(id) { load() }

    val current = detail
    if (current == null) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            error?.let { Text(it, color = Ids.colors.danger, fontSize = 13.sp) }
        }
        return
    }

    val isOrganizer = current.ikimina.organizerId == myUserId
    val iContributed = current.currentRoundContributions.find { it.userId == myUserId }?.contributed ?: false
    val allContributed = current.currentRoundContributions.isNotEmpty() && current.currentRoundContributions.all { it.contributed }
    val pot = current.ikimina.contributionAmount.multiply(java.math.BigDecimal(current.members.size))

    fun invite() {
        if (phoneNumber.isBlank()) return
        busy = true
        coroutineScope.launch {
            try {
                NetworkClient.apiService.inviteIkiminaMember(id, InviteIkiminaMemberRequest(phoneNumber.trim()))
                phoneNumber = ""
                error = null
                load()
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                busy = false
            }
        }
    }

    fun start() {
        busy = true
        coroutineScope.launch {
            try {
                NetworkClient.apiService.startIkiminaCycle(id)
                error = null
                load()
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                busy = false
            }
        }
    }

    fun contribute() {
        busy = true
        coroutineScope.launch {
            try {
                NetworkClient.apiService.contributeToIkimina(id, UUID.randomUUID().toString())
                error = null
                load()
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                busy = false
            }
        }
    }

    fun triggerPayout() {
        busy = true
        payoutMessage = null
        coroutineScope.launch {
            try {
                val result = NetworkClient.apiService.triggerIkiminaPayout(id, UUID.randomUUID().toString())
                payoutMessage = "${formatMoneyGroup(result.amount)} RWF paid out for round ${result.ikimina.currentRound - 1}."
                error = null
                load()
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                busy = false
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Ids.layout.screenHorizontal, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = Ids.colors.surface), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(current.ikimina.name, color = Ids.colors.textSecondary, fontSize = 13.sp)
                    val animatedBalance = rememberCountUp(current.balance.toDouble())
                    Text("${formatMoneyGroup(java.math.BigDecimal.valueOf(animatedBalance))} RWF", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 28.sp)
                    Text(
                        when (current.ikimina.status) {
                            "FORMING" -> "Forming · ${current.members.size} member${if (current.members.size == 1) "" else "s"}"
                            "ACTIVE" -> "Round ${current.ikimina.currentRound} · pot ${formatMoneyGroup(pot)} RWF"
                            else -> "Completed"
                        },
                        color = Ids.colors.textSecondary, fontSize = 12.sp,
                    )
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = Ids.colors.surface), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Members & payout order", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    current.members.sortedBy { it.payoutOrder }.forEach { m ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("${m.payoutOrder}. ${m.firstName} ${m.lastName}${if (m.userId == myUserId) " (you)" else ""}", fontSize = 13.sp)
                                if (current.ikimina.status == "ACTIVE") {
                                    val contributed = current.currentRoundContributions.find { it.userId == m.userId }?.contributed ?: false
                                    Text(
                                        if (contributed) "✓ Contributed this round" else "Not yet contributed",
                                        color = if (contributed) Ids.colors.success else Ids.colors.textSecondary, fontSize = 11.sp,
                                    )
                                }
                            }
                            if (m.hasReceivedPayout) Text("✓ Paid", color = Ids.colors.success, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            else if (m.isOrganizer) Text("Organizer", color = Ids.colors.brand, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
        if (current.ikimina.status == "FORMING" && isOrganizer) {
            item {
                Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = Ids.colors.surface), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Invite a member", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IdsTextField(value = phoneNumber, onValueChange = { phoneNumber = it }, label = "Phone number", modifier = Modifier.weight(1f))
                            Box(
                                modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Ids.colors.brand)
                                    .pressScaleClickable(enabled = !busy && phoneNumber.isNotBlank()) { invite() }
                                    .padding(horizontal = 20.dp, vertical = 14.dp),
                            ) { Text(if (busy) "…" else "Invite", color = Color.White, fontWeight = FontWeight.Bold) }
                        }
                        Box(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Ids.colors.brand)
                                .pressScaleClickable(enabled = !busy && current.members.size >= 2) { start() }.padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text(if (busy) "…" else "Start the cycle", color = Color.White, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
        if (current.ikimina.status == "ACTIVE") {
            item {
                Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = Ids.colors.surface), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                .background(if (iContributed) Ids.colors.surfaceSoft else Ids.colors.brand)
                                .pressScaleClickable(enabled = !busy && !iContributed) { contribute() }.padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                if (iContributed) "✓ You've contributed this round" else if (busy) "…" else "Contribute ${formatMoneyGroup(current.ikimina.contributionAmount)} RWF",
                                color = if (iContributed) Ids.colors.textPrimary else Color.White, fontWeight = FontWeight.Bold,
                            )
                        }
                        if (allContributed) {
                            Box(
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Ids.colors.brand)
                                    .pressScaleClickable(enabled = !busy) { triggerPayout() }.padding(vertical = 14.dp),
                                contentAlignment = Alignment.Center,
                            ) { Text(if (busy) "…" else "Trigger this round's payout", color = Color.White, fontWeight = FontWeight.Bold) }
                        }
                        payoutMessage?.let { Text(it, color = Ids.colors.success, fontSize = 13.sp) }
                    }
                }
            }
        }
        error?.let { msg -> item { Text(msg, color = Ids.colors.danger, fontSize = 13.sp) } }
        item { Spacer(modifier = Modifier.height(8.dp)) }
    }
}

private fun formatMoneyGroup(value: java.math.BigDecimal): String {
    val rounded = value.stripTrailingZeros()
    return if (rounded.scale() <= 0) rounded.toBigInteger().toString() else "%,.2f".format(rounded)
}
