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
import rw.itunda.core.network.GroupAccountAmountRequest
import rw.itunda.core.network.GroupAccountDetailResponse
import rw.itunda.core.network.GroupAccountDto
import rw.itunda.core.network.GroupAccountDuesDto
import rw.itunda.core.network.InviteMemberRequest
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.SetDuesAmountRequest
import rw.itunda.core.network.isDeviceNotVerifiedError
import rw.itunda.core.network.superAppErrorMessage
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.designsystem.components.BackTopBar
import java.io.IOException
import java.math.BigDecimal
import java.util.UUID
import rw.itunda.core.designsystem.components.EmptyState

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
                NetworkClient.apiService.createGroupAccount(rw.itunda.core.network.CreateGroupAccountRequest(name.trim()))
                name = ""
                showCreate = false
                load()
                rw.itunda.core.designsystem.components.IdsToast.show(coroutineScope, "Group account created.")
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
                rw.itunda.core.designsystem.components.IdsButton(text = "+ New group account", onClick = { showCreate = true })
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    IdsTextField(value = name, onValueChange = { name = it }, label = "Group name (e.g. Roommates)", modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rw.itunda.core.designsystem.components.IdsButton(
                            text = "Cancel", onClick = { showCreate = false; name = "" },
                            variant = rw.itunda.core.designsystem.components.IdsButtonVariant.Tinted, modifier = Modifier.weight(1f),
                        )
                        rw.itunda.core.designsystem.components.IdsButton(
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
                            Text("${formatMoneyGroup(it)} RWF / month dues", color = Ids.colors.textSecondary, fontSize = 12.sp)
                        }
                }
            }
        }
    }
}

// Real "one thing, one page" fix (2026-08-10) -- see GroupAccountDetailContent's own
// doc comment on the mode picker this backs.
private enum class GroupAccountActionMode { MONEY, DUES, INVITE }

@Composable
private fun GroupAccountDetailContent(id: String) {
    var detail by remember { mutableStateOf<GroupAccountDetailResponse?>(null) }
    var dues by remember { mutableStateOf<GroupAccountDuesDto?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var amount by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var duesAmountInput by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var duesBusy by remember { mutableStateOf(false) }
    var remindedCount by remember { mutableStateOf<Int?>(null) }
    var needsDeviceVerification by remember { mutableStateOf(false) }
    // Real fix (2026-08-10): found live-testing bank-mfe's identical Group account
    // screen -- deposit and withdraw share this one flag+dialog, but onVerified below
    // unconditionally called deposit(). If a user was actually WITHDRAWING and hit
    // DEVICE_NOT_VERIFIED, verifying would silently DEPOSIT the same amount instead --
    // the opposite of what they asked for, not just a friction gap. Tracks which
    // action was actually pending so the retry redoes the right one.
    var pendingDeviceAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    // Real "one thing, one page" fix (2026-08-10) -- found via the same
    // audit that already fixed ShopScreen's PayAMerchantSection: this screen
    // unconditionally stacked three unrelated action flows (manage monthly
    // dues, deposit/withdraw, invite a member), each its own real form, all
    // visible at once for an owner. Mutually exclusive now via a real mode
    // picker, matching that same fix.
    var actionMode by remember { mutableStateOf(GroupAccountActionMode.MONEY) }
    val myUserId = NetworkClient.currentTokenStore().getUserId()
    val coroutineScope = rememberCoroutineScope()

    fun loadDues() {
        coroutineScope.launch {
            try {
                dues = NetworkClient.apiService.getGroupAccountDues(id).dues
            } catch (_: Exception) {
                // Non-critical -- dues card just stays in its loading state.
            }
        }
    }

    fun load() {
        coroutineScope.launch {
            try {
                detail = NetworkClient.apiService.getGroupAccount(id)
                error = null
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            }
        }
        loadDues()
    }
    LaunchedEffect(id) { load() }

    val isOwner = detail?.groupAccount?.ownerId == myUserId

    fun deposit() {
        val parsedAmount = amount.toBigDecimalOrNull()
        if (parsedAmount == null || parsedAmount <= BigDecimal.ZERO) return
        busy = true
        needsDeviceVerification = false
        coroutineScope.launch {
            try {
                NetworkClient.apiService.depositToGroupAccount(id, GroupAccountAmountRequest(parsedAmount), UUID.randomUUID().toString())
                amount = ""
                error = null
                load()
            } catch (e: HttpException) {
                if (isDeviceNotVerifiedError(e)) { pendingDeviceAction = { deposit() }; needsDeviceVerification = true } else error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                busy = false
            }
        }
    }

    fun withdraw() {
        val parsedAmount = amount.toBigDecimalOrNull()
        if (parsedAmount == null || parsedAmount <= BigDecimal.ZERO) return
        busy = true
        needsDeviceVerification = false
        coroutineScope.launch {
            try {
                NetworkClient.apiService.withdrawFromGroupAccount(id, GroupAccountAmountRequest(parsedAmount), UUID.randomUUID().toString())
                amount = ""
                error = null
                load()
            } catch (e: HttpException) {
                if (isDeviceNotVerifiedError(e)) { pendingDeviceAction = { withdraw() }; needsDeviceVerification = true } else error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                busy = false
            }
        }
    }

    fun invite() {
        if (phoneNumber.isBlank()) return
        busy = true
        coroutineScope.launch {
            try {
                NetworkClient.apiService.inviteGroupAccountMember(id, InviteMemberRequest(phoneNumber.trim()))
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

    fun setDues(newAmount: BigDecimal?) {
        duesBusy = true
        coroutineScope.launch {
            try {
                NetworkClient.apiService.setGroupAccountDuesAmount(id, SetDuesAmountRequest(newAmount))
                duesAmountInput = ""
                loadDues()
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                duesBusy = false
            }
        }
    }

    fun remindUnpaid() {
        duesBusy = true
        remindedCount = null
        coroutineScope.launch {
            try {
                remindedCount = NetworkClient.apiService.requestUnpaidGroupAccountDues(id).remindedCount
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                duesBusy = false
            }
        }
    }

    val current = detail
    if (current == null) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            error?.let { Text(it, color = Ids.colors.danger, fontSize = 13.sp) }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Ids.layout.screenHorizontal, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            // Real fix (flat-design sweep): dropped the Card wrapper -- the screen's
            // own main content.
            Column {
                    Text(current.groupAccount.name, color = Ids.colors.textSecondary, fontSize = 13.sp)
                    val animatedBalance = rememberCountUp(current.balance.toDouble())
                    Text("${formatMoneyGroup(java.math.BigDecimal.valueOf(animatedBalance))} RWF", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 28.sp)
                    Text("${current.members.size} member${if (current.members.size == 1) "" else "s"}", color = Ids.colors.textSecondary, fontSize = 12.sp)
            }
        }
        item {
            Column {
                    Text("Members", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    current.members.forEach { m ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${m.firstName} ${m.lastName}${if (m.userId == myUserId) " (you)" else ""}", fontSize = 13.sp)
                            if (m.isOwner) Text("Organizer", color = Ids.colors.brand, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                listOfNotNull(
                    GroupAccountActionMode.MONEY to "Deposit & withdraw",
                    GroupAccountActionMode.DUES to "Monthly dues",
                    (GroupAccountActionMode.INVITE to "Invite").takeIf { isOwner },
                ).forEach { (m, label) ->
                    val active = actionMode == m
                    Text(
                        label, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        color = if (active) Color.White else Ids.colors.textPrimary,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (active) Ids.colors.brand else Ids.colors.surfaceSoft)
                            .pressScaleClickable { actionMode = m }
                            .padding(vertical = 10.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
        if (actionMode == GroupAccountActionMode.DUES) {
        item {
            // Real fix (flat-design sweep): dropped the Card wrapper -- a section
            // on an otherwise-flat detail screen.
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Monthly dues", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    val currentDues = dues
                    when {
                        currentDues == null -> SkeletonBlock(height = 40.dp)
                        currentDues.duesAmount == null && isOwner -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IdsTextField(value = duesAmountInput, onValueChange = { duesAmountInput = it }, label = "Monthly dues (RWF)", modifier = Modifier.weight(1f))
                            Box(
                                modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Ids.colors.brand)
                                    .pressScaleClickable(enabled = !duesBusy) { duesAmountInput.toBigDecimalOrNull()?.let { setDues(it) } }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                            ) { Text(if (duesBusy) "…" else "Set", color = Color.White, fontWeight = FontWeight.Bold) }
                        }
                        currentDues.duesAmount == null -> Text("The organizer hasn't set a monthly dues amount.", color = Ids.colors.textSecondary, fontSize = 13.sp)
                        else -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            val duesAmount = currentDues.duesAmount!!
                            Text("${formatMoneyGroup(duesAmount)} RWF / month · ${currentDues.cycleMonth}", fontSize = 13.sp)
                            currentDues.members.forEach { m ->
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("${m.firstName} ${m.lastName}${if (m.userId == myUserId) " (you)" else ""}", fontSize = 13.sp)
                                    Text(
                                        if (m.paid) "✓ Paid" else "${formatMoneyGroup(m.contributedAmount)} / ${formatMoneyGroup(duesAmount)}",
                                        color = if (m.paid) Ids.colors.success else Ids.colors.textSecondary,
                                        fontWeight = if (m.paid) FontWeight.Bold else FontWeight.Normal, fontSize = 13.sp,
                                    )
                                }
                            }
                            if (isOwner) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Box(
                                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(Ids.colors.surfaceSoft)
                                            .pressScaleClickable(enabled = !duesBusy) { remindUnpaid() }.padding(vertical = 12.dp),
                                        contentAlignment = Alignment.Center,
                                    ) { Text(if (duesBusy) "…" else "Remind unpaid members", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                                    Box(
                                        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Ids.colors.surfaceSoft)
                                            .pressScaleClickable(enabled = !duesBusy) { setDues(null) }.padding(horizontal = 16.dp, vertical = 12.dp),
                                    ) { Text("Clear", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                                }
                            }
                            remindedCount?.let {
                                Text(
                                    if (it == 0) "Everyone has already paid or been reminded this month." else "Reminded $it member${if (it == 1) "" else "s"}.",
                                    color = Ids.colors.textSecondary, fontSize = 12.sp,
                                )
                            }
                        }
                    }
            }
        }
        }
        if (actionMode == GroupAccountActionMode.MONEY) {
        item {
            if (needsDeviceVerification) {
                DeviceStepUpHost(
                    visible = true,
                    onDismiss = { pendingDeviceAction = null; needsDeviceVerification = false },
                    onVerified = { val action = pendingDeviceAction; pendingDeviceAction = null; action?.invoke() },
                )
            } else {
                // Real fix (flat-design sweep): dropped the Card wrapper -- a
                // section on an otherwise-flat detail screen.
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (isOwner) "Deposit or withdraw" else "Deposit", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        rw.itunda.core.designsystem.components.AmountKeypadInput(
                            digits = amount, onDigitsChange = { amount = it },
                            quickAmounts = listOf(10_000L, 100_000L),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rw.itunda.core.designsystem.components.IdsButton(
                                text = if (busy) "…" else "Deposit", onClick = { deposit() },
                                enabled = !busy && amount.isNotBlank(), modifier = Modifier.weight(1f),
                            )
                            if (isOwner) {
                                rw.itunda.core.designsystem.components.IdsButton(
                                    text = if (busy) "…" else "Withdraw", onClick = { withdraw() },
                                    enabled = !busy && amount.isNotBlank(), modifier = Modifier.weight(1f),
                                    variant = rw.itunda.core.designsystem.components.IdsButtonVariant.Tinted,
                                )
                            }
                        }
                }
            }
        }
        }
        if (isOwner && actionMode == GroupAccountActionMode.INVITE) {
            item {
                // Real fix (flat-design sweep): dropped the Card wrapper -- a
                // section on an otherwise-flat detail screen.
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Invite a member", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IdsTextField(value = phoneNumber, onValueChange = { phoneNumber = it }, label = "Phone number", modifier = Modifier.weight(1f))
                            Box(
                                modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Ids.colors.brand)
                                    .pressScaleClickable(enabled = !busy && phoneNumber.isNotBlank()) { invite() }
                                    .padding(horizontal = 20.dp, vertical = 14.dp),
                            ) { Text(if (busy) "…" else "Invite", color = Color.White, fontWeight = FontWeight.Bold) }
                        }
                }
            }
        }
        error?.let { msg -> item { Text(msg, color = Ids.colors.danger, fontSize = 13.sp) } }
        item { Spacer(modifier = Modifier.height(8.dp)) }
    }
}

private fun formatMoneyGroup(value: BigDecimal): String {
    val rounded = value.stripTrailingZeros()
    return if (rounded.scale() <= 0) "%,d".format(rounded.toBigInteger()) else "%,.2f".format(rounded)
}
