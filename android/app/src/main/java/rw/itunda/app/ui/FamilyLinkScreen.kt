package rw.itunda.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import rw.itunda.core.network.ChildOverviewDto
import rw.itunda.core.network.FamilyLinkDto
import rw.itunda.core.network.FamilyLinkViewDto
import rw.itunda.core.network.InviteChildRequest
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.RespondToInviteRequest
import rw.itunda.core.network.superAppErrorMessage
import java.io.IOException
import rw.itunda.core.designsystem.components.EmptyState

// Real Toss 유스 (Toss Youth)-style guardian-child account link -- see
// rw.itunda.family.FamilyLinkService's own doc comment for the full sourced account and
// honest scope boundary: real read-only spending oversight only, no new allowance
// mechanism (point an existing AutoTransfer/ScheduledTransfer at the child's phone number
// instead). bank-mfe already has this (FamilyLinkCard); this is the first Android client,
// mirroring it exactly.
@Composable
fun FamilyLinkScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var invites by remember { mutableStateOf<List<FamilyLinkDto>?>(null) }
    var children by remember { mutableStateOf<List<FamilyLinkViewDto>?>(null) }
    var guardians by remember { mutableStateOf<List<FamilyLinkViewDto>?>(null) }
    var showInvite by remember { mutableStateOf(false) }
    var childPhone by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var busyId by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var openOverviewFor by remember { mutableStateOf<String?>(null) }
    var overview by remember { mutableStateOf<ChildOverviewDto?>(null) }
    // Real spend-limit enforcement (2026-08-04) -- see FamilyLinkDto.dailySpendLimit's
    // own doc comment: already enforced server-side on every P2P send a child makes, but
    // a guardian had no way to ever set one until now.
    var editingLimitFor by remember { mutableStateOf<String?>(null) }
    var limitInput by remember { mutableStateOf("") }
    var limitBusyId by remember { mutableStateOf<String?>(null) }
    // Real Naver Pay "가족 공유 자산 관리" -- instant transfer to a linked family
    // member, see ApiService.sendToFamilyMember's own doc comment.
    var sendAmountInput by remember { mutableStateOf("") }
    var sendBusy by remember { mutableStateOf(false) }
    var sendDone by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    fun load() {
        coroutineScope.launch {
            try { invites = NetworkClient.apiService.getMyFamilyInvites().invites } catch (e: Exception) { invites = emptyList() }
            try { children = NetworkClient.apiService.getMyFamilyChildren().children } catch (e: Exception) { children = emptyList() }
            try { guardians = NetworkClient.apiService.getMyFamilyGuardians().guardians } catch (e: Exception) { guardians = emptyList() }
        }
    }
    LaunchedEffect(Unit) { load() }

    fun invite() {
        if (childPhone.isBlank()) return
        busy = true
        error = null
        coroutineScope.launch {
            try {
                NetworkClient.apiService.inviteFamilyChild(InviteChildRequest(childPhone.trim()))
                childPhone = ""
                showInvite = false
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

    fun respond(id: String, accept: Boolean) {
        busyId = id
        coroutineScope.launch {
            try {
                NetworkClient.apiService.respondToFamilyInvite(id, RespondToInviteRequest(accept))
                load()
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } finally {
                busyId = null
            }
        }
    }

    fun revoke(id: String) {
        busyId = id
        coroutineScope.launch {
            try {
                NetworkClient.apiService.revokeFamilyLink(id)
                openOverviewFor = null
                load()
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } finally {
                busyId = null
            }
        }
    }

    fun setSpendLimit(childUserId: String, limit: java.math.BigDecimal?) {
        limitBusyId = childUserId
        coroutineScope.launch {
            try {
                NetworkClient.apiService.setFamilySpendLimit(childUserId, rw.itunda.core.network.SetSpendLimitRequest(limit))
                editingLimitFor = null
                limitInput = ""
                load()
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                limitBusyId = null
            }
        }
    }

    fun toggleOverview(childUserId: String) {
        if (openOverviewFor == childUserId) {
            openOverviewFor = null
            return
        }
        openOverviewFor = childUserId
        sendAmountInput = ""
        sendDone = false
        coroutineScope.launch {
            try {
                overview = NetworkClient.apiService.getChildOverview(childUserId).overview
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            }
        }
    }

    fun sendToChild(childUserId: String) {
        val amount = sendAmountInput.trim().toBigDecimalOrNull()
        if (amount == null || amount <= java.math.BigDecimal.ZERO) return
        sendBusy = true
        error = null
        coroutineScope.launch {
            try {
                NetworkClient.apiService.sendToFamilyMember(
                    idempotencyKey = java.util.UUID.randomUUID().toString(),
                    request = rw.itunda.core.network.SendToFamilyMemberRequest(childUserId, amount, "Sent from Family"),
                )
                sendDone = true
                sendAmountInput = ""
                overview = NetworkClient.apiService.getChildOverview(childUserId).overview
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                sendBusy = false
            }
        }
    }

    val hasAnything = !invites.isNullOrEmpty() || !children.isNullOrEmpty() || !guardians.isNullOrEmpty()

    Column(modifier = Modifier.fillMaxSize()) {
        BackTopBar(title = "Family", onBack = onBack)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = Ids.layout.screenHorizontal, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Link a family member", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Ids.colors.surfaceSoft)
                            .clickable { showInvite = !showInvite }.padding(horizontal = 12.dp, vertical = 8.dp),
                    ) { Text(if (showInvite) "Cancel" else "+ Link", color = Ids.colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                }
            }
            if (showInvite) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        IdsTextField(value = childPhone, onValueChange = { childPhone = it }, label = "Phone number", modifier = Modifier.weight(1f))
                        Box(
                            modifier = Modifier.clip(RoundedCornerShape(10.dp))
                                .background(if (busy || childPhone.isBlank()) Ids.colors.textTertiary else Ids.colors.brand)
                                .clickable(enabled = !busy && childPhone.isNotBlank()) { invite() }.padding(horizontal = 20.dp, vertical = 14.dp),
                        ) { Text(if (busy) "…" else "Invite", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                    }
                }
            }
            error?.let { item { Text(it, color = Ids.colors.danger, fontSize = 13.sp) } }

            val pending = invites
            if (!pending.isNullOrEmpty()) {
                item { Text("Pending invitations", color = Ids.colors.textSecondary, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                items(pending, key = { "inv_${it.id}" }) { inv ->
                    Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = Ids.colors.surface), modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Family link request", color = Ids.colors.textPrimary, fontSize = 13.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(
                                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Ids.colors.brand)
                                        .clickable(enabled = busyId != inv.id) { respond(inv.id, true) }.padding(horizontal = 12.dp, vertical = 8.dp),
                                ) { Text("Accept", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                                Box(
                                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Ids.colors.surfaceSoft)
                                        .clickable(enabled = busyId != inv.id) { respond(inv.id, false) }.padding(horizontal = 12.dp, vertical = 8.dp),
                                ) { Text("Decline", color = Ids.colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                            }
                        }
                    }
                }
            }

            val childList = children
            if (!childList.isNullOrEmpty()) {
                item { Text("Linked children", color = Ids.colors.textSecondary, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                items(childList, key = { "child_${it.link.id}" }) { c ->
                    Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = Ids.colors.surface), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text(c.childName, color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Box(
                                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Ids.colors.surfaceSoft)
                                            .clickable { toggleOverview(c.link.childUserId) }.padding(horizontal = 12.dp, vertical = 8.dp),
                                    ) { Text(if (openOverviewFor == c.link.childUserId) "Hide" else "View", color = Ids.colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                                    Box(
                                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Ids.colors.surfaceSoft)
                                            .clickable(enabled = busyId != c.link.id) { revoke(c.link.id) }.padding(horizontal = 12.dp, vertical = 8.dp),
                                    ) { Text("Unlink", color = Ids.colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    c.link.dailySpendLimit?.let { "Daily limit: ${"%,.0f".format(it)} RWF" } ?: "No daily spend limit set",
                                    color = Ids.colors.textSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f),
                                )
                                Text(
                                    if (editingLimitFor == c.link.childUserId) "Cancel" else "Edit",
                                    color = Ids.colors.brand, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.clickable {
                                        if (editingLimitFor == c.link.childUserId) {
                                            editingLimitFor = null
                                        } else {
                                            editingLimitFor = c.link.childUserId
                                            limitInput = c.link.dailySpendLimit?.toPlainString() ?: ""
                                        }
                                    },
                                )
                            }
                            if (editingLimitFor == c.link.childUserId) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    IdsTextField(value = limitInput, onValueChange = { limitInput = it }, label = "Daily limit (RWF, blank = no limit)", modifier = Modifier.weight(1f))
                                    Box(
                                        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Ids.colors.brand)
                                            .clickable(enabled = limitBusyId != c.link.childUserId) {
                                                setSpendLimit(c.link.childUserId, limitInput.trim().ifBlank { null }?.toBigDecimalOrNull())
                                            }
                                            .padding(horizontal = 16.dp, vertical = 14.dp),
                                    ) { Text(if (limitBusyId == c.link.childUserId) "…" else "Save", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                                }
                            }
                            if (openOverviewFor == c.link.childUserId) {
                                val o = overview
                                if (o != null && o.childUserId == c.link.childUserId) {
                                    Text("Balance: ${"%,.0f".format(o.walletBalance)} RWF", color = Ids.colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    if (o.recentTransactions.isEmpty()) {
                                        Text("No transactions yet.", color = Ids.colors.textSecondary, fontSize = 12.sp)
                                    } else {
                                        o.recentTransactions.take(5).forEach { t ->
                                            Text("${t.description} · ${"%,.0f".format(t.amount)} RWF", color = Ids.colors.textSecondary, fontSize = 12.sp)
                                        }
                                    }
                                    // Real Naver Pay "family shared asset management" --
                                    // instant transfer to this linked family member.
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                        IdsTextField(value = sendAmountInput, onValueChange = { sendAmountInput = it; sendDone = false }, label = "Amount (RWF)", modifier = Modifier.weight(1f))
                                        Box(
                                            modifier = Modifier.clip(RoundedCornerShape(10.dp))
                                                .background(if (sendBusy || sendAmountInput.isBlank()) Ids.colors.textTertiary else Ids.colors.brand)
                                                .clickable(enabled = !sendBusy && sendAmountInput.isNotBlank()) { sendToChild(c.link.childUserId) }
                                                .padding(horizontal = 16.dp, vertical = 14.dp),
                                        ) { Text(if (sendBusy) "…" else "Send", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                                    }
                                    if (sendDone) {
                                        Text("Sent.", color = Ids.colors.success, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            val guardianList = guardians
            if (!guardianList.isNullOrEmpty()) {
                item { Text("Your guardians", color = Ids.colors.textSecondary, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                items(guardianList, key = { "guardian_${it.link.id}" }) { g ->
                    Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = Ids.colors.surface), modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(g.guardianName, color = Ids.colors.textPrimary, fontSize = 13.sp)
                            Box(
                                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Ids.colors.surfaceSoft)
                                    .clickable(enabled = busyId != g.link.id) { revoke(g.link.id) }.padding(horizontal = 12.dp, vertical = 8.dp),
                            ) { Text("Unlink", color = Ids.colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }

            if (!hasAnything) {
                item { EmptyState("No family members linked yet — invite one above.") }
            }
        }
    }
}
