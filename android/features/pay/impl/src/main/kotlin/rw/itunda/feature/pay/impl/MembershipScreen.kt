package rw.itunda.feature.pay.impl

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import rw.itunda.feature.pay.impl.R
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.designsystem.theme.IdsIcons
import rw.itunda.core.network.LoyaltyBalanceDto
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.RewardTaskDto

// Real "Membership" screen, adapted (itunda Pay redesign, 2026-08-28). The real
// Toss Pay reference aggregates points across many real THIRD-PARTY brands (Naver,
// Kakao, Coupang, CU, GS25...) -- itunda has no such partnerships, fabricating
// balances would violate this codebase's own standing honesty discipline.
// Confirmed with the user directly (AskUserQuestion): "Adapt Membership to
// itunda's own real points" -- real rewards points + real Pay Money balance + real
// per-merchant "Store points" (MerchantLoyaltyAccount, surfaced to a client for the
// first time this pass) only. See this file's own web sibling (MembershipView.tsx)
// for the full account.
@Composable
fun MembershipScreen(onBack: () -> Unit, onOpenPayMoney: () -> Unit, onOpenRewardsMiniApp: () -> Unit) {
    BackHandler(onBack = onBack)
    // Real "View all rewards" destination -- Android has no native Rewards screen
    // (RewardsService is otherwise reached only through the Saronite RN mini-app),
    // same real gap RewardsPreviewSection's own callers already work around
    // elsewhere on this tab. Injected as a plain callback (2026-09-02, Pay
    // Feature-module decomposition) rather than constructed here directly --
    // RewardTasksMiniAppActivity is an :app-only Android Activity class/manifest
    // entry, so this screen (now in :features:pay:impl) can't reference it, same
    // "inject what a Feature module can't reach" pattern deviceStepUpHost already
    // established for TalkTab/PayAMerchantSection.
    val openRewardsMiniApp = onOpenRewardsMiniApp
    var payBalance by remember { mutableStateOf<Double?>(null) }
    var rewardsTotal by remember { mutableStateOf<Double?>(null) }
    var rewardTasks by remember { mutableStateOf<List<RewardTaskDto>>(emptyList()) }
    var loyaltyBalances by remember { mutableStateOf<List<LoyaltyBalanceDto>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            val accounts = NetworkClient.apiService.getAccounts().accounts
            payBalance = accounts.find { it.type == "PAY" }?.balance ?: 0.0
            val rewards = NetworkClient.apiService.getRewardTasks()
            rewardsTotal = rewards.rewardsTotal
            rewardTasks = rewards.tasks
            loyaltyBalances = NetworkClient.apiService.getMyLoyaltyBalances().balances
        } catch (e: Exception) {
            error = "Could not load your membership details."
        }
    }

    val loading = payBalance == null || rewardsTotal == null || loyaltyBalances == null

    Column(modifier = Modifier.fillMaxSize().background(Ids.colors.background)) {
        Row(Modifier.padding(horizontal = Ids.layout.screenHorizontal, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(Ids.layout.minTouchTarget).clip(CircleShape).pressScaleClickable(onClick = onBack),
                contentAlignment = Alignment.CenterStart,
            ) {
                Icon(IdsIcons.Back, contentDescription = stringResource(R.string.back), modifier = Modifier.size(18.dp), tint = Ids.colors.textPrimary)
            }
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.pay_membership_title), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Ids.colors.textPrimary)
        }

        if (loading) {
            if (error != null) {
                Text(error!!, color = Ids.colors.danger, modifier = Modifier.padding(horizontal = Ids.layout.screenHorizontal))
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Ids.colors.brand) }
            }
            return
        }

        LazyColumn(modifier = Modifier.fillMaxWidth().padding(horizontal = Ids.layout.screenHorizontal)) {
            item {
                Text(stringResource(R.string.pay_membership_total), color = Ids.colors.textSecondary, fontSize = 13.sp)
                Text(String.format(Locale.US, "%,.0f RWF", (rewardsTotal ?: 0.0) + (payBalance ?: 0.0)), color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 32.sp)
                Spacer(Modifier.height(20.dp))
            }
            item {
                Row(
                    Modifier.fillMaxWidth().pressScaleClickable(onClick = openRewardsMiniApp).padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(R.string.pay_membership_available_points), color = Ids.colors.textPrimary, fontSize = 14.sp)
                    Text(String.format(Locale.US, "%,.0f RWF", rewardsTotal ?: 0.0), color = Ids.colors.brand, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
            item {
                Row(
                    Modifier.fillMaxWidth().pressScaleClickable(onClick = onOpenPayMoney).padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(R.string.pay_membership_pay_money), color = Ids.colors.textPrimary, fontSize = 14.sp)
                    Text(String.format(Locale.US, "%,.0f RWF", payBalance ?: 0.0), color = Ids.colors.brand, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
            val balances = loyaltyBalances ?: emptyList()
            if (balances.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.pay_membership_store_points), color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                items(balances, key = { it.merchantId }) { balance ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(balance.merchantName, color = Ids.colors.textPrimary, fontSize = 13.sp)
                        Text(String.format(Locale.US, "%,.0f", balance.pointBalance), color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
            // Real claiming happens on the full Rewards screen (REWARDS tab), not
            // inline here -- matches web's identical Membership-screen scope (a
            // preview + "View all"), see RewardsPreviewSection's own onViewAll on web.
            item { RewardsPreviewSection(tasks = rewardTasks, claimingId = null, onClaim = { _ -> openRewardsMiniApp() }) }
        }
    }
}
