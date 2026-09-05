package rw.itunda.feature.pay.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlinx.coroutines.launch
import rw.itunda.core.designsystem.components.IdsButton
import rw.itunda.core.designsystem.components.IdsButtonSize
import rw.itunda.core.designsystem.components.IdsButtonVariant
import rw.itunda.core.designsystem.components.IdsIconButton
import rw.itunda.core.designsystem.components.dashedBorder
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.designsystem.theme.IdsIcons

// Real fix (2026-08-11, itunda Pay research pass -- user-provided KakaoPay/Toss
// Pay screenshots): this whole tab was a decorative mockup -- zero calls to any
// real Pay backend endpoint. The REAL payment-collection UI (pay-by-code,
// pay-by-static-QR, Face Pay -- all genuinely wired to
// rw.itunda.merchant.MerchantService.collect) already existed, just misfiled
// inside the unrelated Shop feature where the Pay tab/Home QR icon could never
// reach it. This is that real UI, moved to where "Pay" actually means pay.
//
// Moved into :features:pay:impl (2026-09-02, Pay Feature-module decomposition,
// following the user's explicit "Continue into HomeTab next" direction which also
// covered Pay/Menu/My) -- three real prerequisites resolved first, see
// [[project_itunda_feature_isolation]]: DeviceStepUpDialog + LocalRealActivity +
// DeviceStepUpHost promoted to :core:designsystem, LedgerFormatting promoted to
// :core:designsystem, and PayAMerchantSection (owned by :features:shop:impl)
// switched to an injected composable slot rather than a direct cross-Feature
// import, matching the deviceStepUpHost pattern TalkTab already established.
enum class PayTabMode { MY_CODE, PAY_MERCHANT }

@Composable
fun PayTab(
    transactions: List<rw.itunda.core.network.TransactionDto>,
    onSend: () -> Unit,
    onCashOutAtAgent: () -> Unit,
    // Real narrowing (2026-09-02) -- was `onSwitchTab: (ItundaTab) -> Unit`,
    // taking :app's own `internal enum class ItundaTab` (ItundaAppScreen.kt),
    // an :app-only type a Feature module can't reference. Both real call sites
    // only ever switched to one fixed tab each, so two plain callbacks replace
    // the one generic one -- no behavior change, no type crossing the boundary.
    onOpenSettings: () -> Unit,
    onBrowseMerchants: () -> Unit,
    onOpenSupport: () -> Unit,
    onOpenCard: () -> Unit,
    payAMerchantSection: @Composable () -> Unit,
    onOpenRewardsMiniApp: () -> Unit,
) {
    var facePayEnrolled by remember { mutableStateOf(false) }
    var facePayBusy by remember { mutableStateOf(false) }
    var rewardTasks by remember { mutableStateOf<List<rw.itunda.core.network.RewardTaskDto>>(emptyList()) }
    var rewardsTotal by remember { mutableStateOf(0.0) }
    var claimingRewardId by remember { mutableStateOf<String?>(null) }
    var hasCard by remember { mutableStateOf<Boolean?>(null) }
    var cardLast4 by remember { mutableStateOf<String?>(null) }
    var cardFrozen by remember { mutableStateOf(false) }
    var showCouponBox by remember { mutableStateOf(false) }
    var showMembership by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        try {
            facePayEnrolled = rw.itunda.core.network.NetworkClient.apiService.getFacePayStatus().enrolled
        } catch (e: Exception) {
            // Non-critical -- the row just keeps showing the last-known state.
        }
        try {
            val result = rw.itunda.core.network.NetworkClient.apiService.getRewardTasks()
            rewardTasks = result.tasks
            rewardsTotal = result.rewardsTotal
        } catch (e: Exception) {
            // Non-critical -- the preview section just stays hidden.
        }
        try {
            val card = rw.itunda.core.network.NetworkClient.apiService.getMyCard().card
            hasCard = true
            cardLast4 = card.last4
            cardFrozen = card.frozen
        } catch (e: Exception) {
            hasCard = false
        }
    }
    val nearbyMerchants = rememberNearbyMerchants()
    val payTabTransactions = transactions
    var mode by remember { mutableStateOf(PayTabMode.MY_CODE) }
    var accounts by remember { mutableStateOf<List<rw.itunda.core.network.Account>>(emptyList()) }
    var selectedAccountId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        try {
            val pay = rw.itunda.core.network.NetworkClient.apiService.getAccounts().accounts.filter { it.type == "PAY" }
            val foreign = rw.itunda.core.network.NetworkClient.apiService.getForeignAccounts().accounts
            accounts = pay + foreign
        } catch (e: Exception) {
            // Real, non-critical -- MyPaymentCodeCard falls back to the backend's own
            // PAY default when accounts never load.
        }
    }
    LaunchedEffect(accounts) {
        if (selectedAccountId == null) selectedAccountId = accounts.firstOrNull { it.type == "PAY" }?.id
    }
    var openAccountDetail by remember { mutableStateOf<rw.itunda.core.network.Account?>(null) }
    if (openAccountDetail != null) {
        PayMoneyDetailScreen(
            account = openAccountDetail!!,
            onBack = { openAccountDetail = null },
            onSend = { openAccountDetail = null; onSend() },
            onAddMoney = { openAccountDetail = null },
        )
        return
    }
    if (showCouponBox) {
        CouponBoxScreen(onBack = { showCouponBox = false }, onBrowseMerchants = { showCouponBox = false; onBrowseMerchants() })
        return
    }
    if (showMembership) {
        MembershipScreen(
            onBack = { showMembership = false },
            onOpenPayMoney = { showMembership = false; openAccountDetail = accounts.find { it.type == "PAY" } },
            onOpenRewardsMiniApp = onOpenRewardsMiniApp,
        )
        return
    }
    val coroutineScope = rememberCoroutineScope()
    val handleFacePayToggle: () -> Unit = {
        coroutineScope.launch {
            facePayBusy = true
            try {
                if (facePayEnrolled) rw.itunda.core.network.NetworkClient.apiService.revokeFacePay() else rw.itunda.core.network.NetworkClient.apiService.enrollFacePay()
                facePayEnrolled = !facePayEnrolled
            } catch (e: Exception) {
                // Non-critical -- the row just keeps showing the last-known state.
            } finally {
                facePayBusy = false
            }
        }
    }
    val handleClaimReward: (String) -> Unit = { taskId ->
        coroutineScope.launch {
            claimingRewardId = taskId
            try {
                rw.itunda.core.network.NetworkClient.apiService.claimRewardTask(
                    java.util.UUID.randomUUID().toString(),
                    rw.itunda.core.network.ClaimRewardTaskRequest(taskId),
                )
                val result = rw.itunda.core.network.NetworkClient.apiService.getRewardTasks()
                rewardTasks = result.tasks
                rewardsTotal = result.rewardsTotal
            } catch (e: retrofit2.HttpException) {
                if (rw.itunda.core.network.apiErrorCode(e) == "REWARD_TASK_ALREADY_CLAIMED") {
                    val result = rw.itunda.core.network.NetworkClient.apiService.getRewardTasks()
                    rewardTasks = result.tasks
                    rewardsTotal = result.rewardsTotal
                }
            } catch (e: Exception) {
                // Non-critical -- the row just stays claimable, retryable on next tap.
            } finally {
                claimingRewardId = null
            }
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(start = Ids.layout.screenHorizontal, end = Ids.layout.screenHorizontal, top = Ids.layout.screenVertical),
        verticalArrangement = Arrangement.spacedBy(Ids.layout.cardGap)
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Pay", color = Ids.colors.textPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                IdsIconButton(icon = Icons.Outlined.Settings, contentDescription = "Pay settings", onClick = onOpenSettings)
            }
        }
        item { NearbyMerchantsBanner(merchants = nearbyMerchants) }
        item {
            FacePayStatusRow(
                enrolled = facePayEnrolled,
                busy = facePayBusy,
                cashbackRatePercent = nearbyMerchants.takeIf { it.isNotEmpty() }?.let { list -> list.sumOf { it.cashbackRate } / list.size * 100.0 },
                onToggle = handleFacePayToggle,
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                IdsButton(stringResource(R.string.home_cash_out), onClick = onCashOutAtAgent, modifier = Modifier.weight(1f), variant = IdsButtonVariant.Tinted, size = IdsButtonSize.Medium, icon = IdsIcons.Add)
                IdsButton(stringResource(R.string.home_send), onClick = onSend, modifier = Modifier.weight(1f), variant = IdsButtonVariant.Filled, size = IdsButtonSize.Medium, icon = IdsIcons.Send)
            }
        }
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(999.dp)).background(Ids.colors.surfaceSoft).padding(4.dp),
            ) {
                listOf(PayTabMode.MY_CODE to "My code", PayTabMode.PAY_MERCHANT to "Pay a merchant").forEach { (m, label) ->
                    val active = mode == m
                    Text(
                        label, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (active) Ids.colors.surface else Color.Transparent)
                            .pressScaleClickable { mode = m }
                            .padding(vertical = 10.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        when (mode) {
            PayTabMode.MY_CODE -> {
                item {
                    MyPaymentCodeCard(
                        accounts = accounts,
                        selectedAccountId = selectedAccountId,
                        onSelectAccount = { selectedAccountId = it },
                        onOpenAccountDetail = { openAccountDetail = it },
                        onOpenCard = onOpenCard,
                    )
                }
                if (accounts.size > 1) {
                    item {
                        AccountCardCarousel(
                            accounts = accounts,
                            selectedAccountId = selectedAccountId,
                            onSelect = { selectedAccountId = it },
                        )
                    }
                }
            }
            PayTabMode.PAY_MERCHANT -> item { payAMerchantSection() }
        }
        item { RewardsSummaryRow(rewardsTotal = rewardsTotal, payBalance = accounts.find { it.type == "PAY" }?.balance) }
        if (hasCard != null) {
            item {
                if (hasCard == true) {
                    Row(
                        Modifier.fillMaxWidth().pressScaleClickable(onClick = onOpenCard),
                        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(stringResource(R.string.overview_card_number, cardLast4 ?: ""), color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                if (cardFrozen) stringResource(R.string.overview_card_frozen) else stringResource(R.string.overview_card_active),
                                color = Ids.colors.textSecondary, fontSize = 12.sp,
                            )
                        }
                        Icon(IdsIcons.ChevronRight, contentDescription = null, modifier = Modifier.size(16.dp), tint = Ids.colors.textSecondary)
                    }
                } else {
                    Row(
                        Modifier.fillMaxWidth().dashedBorder(Ids.colors.divider).padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.overview_teaser_cards), color = Ids.colors.textSecondary, fontSize = 13.sp)
                        IdsButton(text = stringResource(R.string.overview_teaser_cards_cta), variant = IdsButtonVariant.Tinted, size = IdsButtonSize.Small, onClick = onOpenCard)
                    }
                }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().pressScaleClickable { showMembership = true },
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.pay_points_pay_money_row), color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(String.format(Locale.US, "%,.0f RWF", rewardsTotal + (accounts.find { it.type == "PAY" }?.balance ?: 0.0)), color = Ids.colors.brand, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Icon(IdsIcons.ChevronRight, contentDescription = null, modifier = Modifier.size(16.dp), tint = Ids.colors.textSecondary)
                }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().pressScaleClickable { showCouponBox = true },
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.pay_your_coupons_row), color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Icon(IdsIcons.ChevronRight, contentDescription = null, modifier = Modifier.size(16.dp), tint = Ids.colors.textSecondary)
            }
        }
        item { RewardsPreviewSection(tasks = rewardTasks, claimingId = claimingRewardId, onClaim = handleClaimReward) }
        item { PaymentHistorySection(transactions = payTabTransactions) }
        item { GetHelpLinks(onOpenSupport = onOpenSupport) }
    }
}
