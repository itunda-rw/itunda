package rw.itunda.feature.banking.impl

import rw.itunda.core.designsystem.components.formatMoney

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import retrofit2.HttpException
import rw.itunda.core.designsystem.components.BackTopBar
import rw.itunda.core.designsystem.components.EmptyState
import rw.itunda.core.designsystem.components.IdsButton
import rw.itunda.core.designsystem.components.IdsTextField
import rw.itunda.core.designsystem.components.IdsToast
import rw.itunda.core.designsystem.components.SkeletonBlock
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.CreateGroupAccountRequest
import rw.itunda.core.network.GroupAccountDto
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.superAppErrorMessage
import java.io.IOException

// Real Kakao Bank 모임통장 (group/shared account) equivalent -- first Android client for
// this feature (item 104, found via a fresh matrix scan for still-open "zero client on
// mobile" gaps: bank-mfe has had this since well before this session, Android/iOS never
// did). Same no-ViewModel, direct-NetworkClient-call convention as
// WeeklySavingsScreen.kt/YouthAccountScreen.kt. Mirrors bank-mfe's
// GroupAccountsSection/GroupAccountDetailView/CreateGroupAccountForm exactly.
private enum class GroupAccountMode { LIST, INTRO, CREATE, DETAIL }

@Composable
fun GroupAccountScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var mode by remember { mutableStateOf(GroupAccountMode.LIST) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        val title = when (mode) {
            GroupAccountMode.DETAIL -> "Group account detail"
            GroupAccountMode.INTRO -> "Group account"
            GroupAccountMode.CREATE -> "New group account"
            GroupAccountMode.LIST -> "Group accounts"
        }
        val backAction: () -> Unit = when (mode) {
            GroupAccountMode.DETAIL -> { { mode = GroupAccountMode.LIST; selectedId = null; refreshKey++ } }
            GroupAccountMode.CREATE -> { { mode = GroupAccountMode.INTRO } }
            GroupAccountMode.INTRO -> { { mode = GroupAccountMode.LIST } }
            GroupAccountMode.LIST -> onBack
        }
        BackTopBar(title = title, onBack = backAction)

        when {
            mode == GroupAccountMode.DETAIL && selectedId != null -> GroupAccountDetailContent(id = selectedId!!)
            mode == GroupAccountMode.INTRO -> GroupAccountIntroContent(onContinue = { mode = GroupAccountMode.CREATE })
            mode == GroupAccountMode.CREATE -> GroupAccountCreateContent(onCreated = { mode = GroupAccountMode.LIST; refreshKey++ })
            else -> GroupAccountListContent(
                refreshKey = refreshKey,
                onOpen = { selectedId = it; mode = GroupAccountMode.DETAIL },
                onStartCreate = { mode = GroupAccountMode.INTRO },
            )
        }
    }
}

// Real Toss product-intro pattern (docs/UI_UX_GUIDELINES.md rule 13) -- see
// IkiminaScreen.kt's identical IkiminaIntroContent for the established convention.
// Real mechanics sourced from GroupAccountService.kt's own doc comment: Kakao Bank's
// real 모임통장 (shared account) -- the creator keeps real withdrawal authority,
// invited members can view and deposit but never withdraw, capped at a real 100
// members, with optional monthly dues tracking/reminders (setDuesAmount/getDuesStatus).
@Composable
private fun GroupAccountIntroContent(onContinue: () -> Unit) {
    rw.itunda.core.designsystem.components.FixedBottomCta(
        content = {
            Text(
                "One shared account, money everyone can see",
                color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp,
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Anyone you invite can deposit", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(
                    "Great for roommates, a family fund, or a shared trip -- everyone can add money and see the real balance and every contribution.",
                    color = Ids.colors.textSecondary, fontSize = 13.sp,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Only you can withdraw", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(
                    "As the creator, you keep sole withdrawal authority -- members can add money but never take it out, so the fund can't be drained by anyone but you.",
                    color = Ids.colors.textSecondary, fontSize = 13.sp,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Optional monthly dues, with automatic reminders", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(
                    "Set a monthly dues amount later if you want -- itunda will remind anyone who hasn't paid yet this cycle. Up to 100 members per group.",
                    color = Ids.colors.textSecondary, fontSize = 13.sp,
                )
            }
        },
        cta = {
            IdsButton(text = "Continue", onClick = onContinue)
        },
    )
}

@Composable
private fun GroupAccountCreateContent(onCreated: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    fun create() {
        if (name.isBlank()) return
        creating = true
        coroutineScope.launch {
            try {
                NetworkClient.apiService.createGroupAccount(CreateGroupAccountRequest(name.trim()))
                IdsToast.show(coroutineScope, "Group account created.")
                onCreated()
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                creating = false
            }
        }
    }

    rw.itunda.core.designsystem.components.FixedBottomCta(
        content = {
            IdsTextField(value = name, onValueChange = { name = it }, label = "Group name (e.g. Roommates)", modifier = Modifier.fillMaxWidth())
            error?.let { msg -> Text(msg, color = Ids.colors.danger, fontSize = 13.sp) }
        },
        cta = {
            IdsButton(text = if (creating) "Creating…" else "Create", onClick = { create() }, enabled = !creating)
        },
    )
}

@Composable
private fun GroupAccountListContent(refreshKey: Int, onOpen: (String) -> Unit, onStartCreate: () -> Unit) {
    var accounts by remember { mutableStateOf<List<GroupAccountDto>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    fun load() {
        coroutineScope.launch {
            try {
                val res = NetworkClient.apiService.getMyGroupAccounts()
                if (res.success) accounts = res.groupAccounts
                error = null
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            }
        }
    }
    LaunchedEffect(refreshKey) { load() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Ids.layout.screenHorizontal, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            IdsButton(text = "+ New group account", onClick = onStartCreate)
        }
        error?.let { msg -> item { Text(msg, color = Ids.colors.danger, fontSize = 13.sp) } }
        when {
            accounts == null -> item {
                SkeletonBlock(height = 80.dp)
            }
            accounts!!.isEmpty() -> item {
                EmptyState("No group accounts yet — start one to split a shared expense with roommates or friends.")
            }
            else -> items(accounts!!, key = { it.id }) { account ->
                // Real fix (flat-design sweep): dropped the per-row Card.
                Column(modifier = Modifier.fillMaxWidth().pressScaleClickable { onOpen(account.id) }) {
                        Text(account.name, color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        account.monthlyDuesAmount?.let {
                            Text("${formatMoney(it)} RWF / month dues", color = Ids.colors.textSecondary, fontSize = 12.sp)
                        }
                }
            }
        }
    }
}

