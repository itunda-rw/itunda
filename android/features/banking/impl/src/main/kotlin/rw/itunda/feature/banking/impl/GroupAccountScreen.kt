package rw.itunda.feature.banking.impl

import rw.itunda.core.designsystem.components.formatMoney

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import rw.itunda.core.designsystem.components.IdsButtonVariant
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
private enum class GroupAccountMode { LIST, DETAIL }

@Composable
fun GroupAccountScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var mode by remember { mutableStateOf(GroupAccountMode.LIST) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        val backAction: () -> Unit = if (mode == GroupAccountMode.DETAIL) {
            { mode = GroupAccountMode.LIST; selectedId = null; refreshKey++ }
        } else onBack
        BackTopBar(title = "Group accounts", onBack = backAction)

        if (mode == GroupAccountMode.DETAIL && selectedId != null) {
            GroupAccountDetailContent(id = selectedId!!)
        } else {
            GroupAccountListContent(
                refreshKey = refreshKey,
                onOpen = { selectedId = it; mode = GroupAccountMode.DETAIL },
            )
        }
    }
}

@Composable
private fun GroupAccountListContent(refreshKey: Int, onOpen: (String) -> Unit) {
    var accounts by remember { mutableStateOf<List<GroupAccountDto>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
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

    fun create() {
        if (name.isBlank()) return
        creating = true
        coroutineScope.launch {
            try {
                NetworkClient.apiService.createGroupAccount(CreateGroupAccountRequest(name.trim()))
                name = ""
                showCreate = false
                load()
                IdsToast.show(coroutineScope, "Group account created.")
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
            if (!showCreate) {
                IdsButton(text = "+ New group account", onClick = { showCreate = true })
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    IdsTextField(value = name, onValueChange = { name = it }, label = "Group name (e.g. Roommates)", modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IdsButton(
                            text = "Cancel", onClick = { showCreate = false; name = "" },
                            variant = IdsButtonVariant.Tinted, modifier = Modifier.weight(1f),
                        )
                        IdsButton(
                            text = if (creating) "Creating…" else "Create", onClick = { create() },
                            enabled = !creating, modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
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

