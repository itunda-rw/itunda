package rw.itunda.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.SaccoAmountRequest
import rw.itunda.core.network.SaccoDividendPayoutDto
import rw.itunda.core.network.superAppErrorMessage
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.designsystem.components.BackTopBar
import java.io.IOException
import java.math.BigDecimal
import java.util.UUID
import rw.itunda.core.designsystem.components.EmptyState

/**
 * Real Umurenge SACCO-style shares & dividends -- Rwanda's own government-backed
 * cooperative savings model. See the backend's SaccoShareholding.kt doc comment for
 * the full sourced account (416 real government-backed cooperatives, one per
 * administrative sector, established 2008/2009 -- 4M+ members, RWF 200B+ deposits as
 * of 2024). Distinct from IkiminaScreen.kt (informal rotating-pot ROSCA, no shares/
 * dividends): a SACCO member buys real shares and receives periodic real dividend
 * distributions tied to the pool's real performance. The second feature in this
 * codebase not sourced from Toss/Kakao/Naver/Coupang. Mirrors bank-mfe's
 * SaccoSharesSection exactly, same no-ViewModel, direct-NetworkClient-call convention
 * IkiminaScreen.kt already established.
 */
@Composable
fun SaccoScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var sharesHeld by remember { mutableStateOf<BigDecimal?>(null) }
    var totalContributed by remember { mutableStateOf<BigDecimal?>(null) }
    var currentValue by remember { mutableStateOf<BigDecimal?>(null) }
    var dividends by remember { mutableStateOf<List<SaccoDividendPayoutDto>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var amount by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    fun load() {
        coroutineScope.launch {
            try {
                val res = NetworkClient.apiService.getMySaccoShareholding()
                sharesHeld = res.shareholding?.sharesHeld
                totalContributed = res.shareholding?.totalContributed
                currentValue = res.currentValue
                error = null
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            }
            try {
                dividends = NetworkClient.apiService.getMySaccoDividendHistory().payouts
            } catch (_: Exception) {
                // Non-critical -- shareholding is the primary view.
            }
        }
    }
    LaunchedEffect(Unit) { load() }

    fun buy() {
        val value = amount.toBigDecimalOrNull()
        if (value == null || value.signum() <= 0) return
        busy = true
        coroutineScope.launch {
            try {
                NetworkClient.apiService.buySaccoShares(SaccoAmountRequest(value), UUID.randomUUID().toString())
                amount = ""
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

    fun redeem() {
        val value = amount.toBigDecimalOrNull()
        if (value == null || value.signum() <= 0) return
        busy = true
        coroutineScope.launch {
            try {
                NetworkClient.apiService.redeemSaccoShares(SaccoAmountRequest(value), UUID.randomUUID().toString())
                amount = ""
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

    Column(modifier = Modifier.fillMaxSize()) {
        BackTopBar(title = "SACCO shares", onBack = onBack)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = Ids.layout.screenHorizontal, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "Buy real cooperative shares and earn a real periodic dividend, matching Rwanda's own Umurenge SACCO model.",
                    color = TossSecondary, fontSize = 12.sp,
                )
            }
            item {
                Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = TossCard), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("Shares held", color = TossSecondary, fontSize = 13.sp)
                        Text("${formatMoneySacco(sharesHeld ?: BigDecimal.ZERO)} RWF", color = TossText, fontWeight = FontWeight.Bold, fontSize = 28.sp)
                        Text("Total contributed: ${formatMoneySacco(totalContributed ?: BigDecimal.ZERO)} RWF", color = TossSecondary, fontSize = 12.sp)
                    }
                }
            }
            item {
                Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = TossCard), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        IdsTextField(value = amount, onValueChange = { amount = it }, label = "Amount (RWF)", modifier = Modifier.fillMaxWidth())
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(TossBlue)
                                    .clickable(enabled = !busy && amount.toBigDecimalOrNull()?.signum() == 1) { buy() }
                                    .padding(vertical = 14.dp),
                                contentAlignment = Alignment.Center,
                            ) { Text(if (busy) "…" else "Buy shares", color = Color.White, fontWeight = FontWeight.Bold) }
                            Box(
                                modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(TossCardSoft)
                                    .clickable(enabled = !busy && amount.toBigDecimalOrNull()?.signum() == 1) { redeem() }
                                    .padding(vertical = 14.dp),
                                contentAlignment = Alignment.Center,
                            ) { Text(if (busy) "…" else "Redeem", color = TossText, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
            error?.let { msg -> item { Text(msg, color = Ids.colors.danger, fontSize = 13.sp) } }
            item { Text("Dividend history", color = TossText, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
            when {
                dividends == null -> item {
                    Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), modifier = Modifier.fillMaxWidth().height(48.dp)) {}
                }
                dividends!!.isEmpty() -> item {
                    EmptyState("No dividends declared yet.")
                }
                else -> items(dividends!!) { d ->
                    Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = TossCard), modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(d.createdAt.take(10), color = TossSecondary, fontSize = 12.sp)
                            Text("+${formatMoneySacco(d.amount)} RWF", color = Ids.colors.success, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }
}

private fun formatMoneySacco(value: BigDecimal): String {
    val rounded = value.stripTrailingZeros()
    return if (rounded.scale() <= 0) rounded.toBigInteger().toString() else "%,.2f".format(rounded)
}
