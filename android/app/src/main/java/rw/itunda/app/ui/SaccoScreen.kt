package rw.itunda.app.ui

import rw.itunda.core.designsystem.components.formatMoney
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import rw.itunda.core.designsystem.components.pressScaleClickable
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
private const val SHARE_PRICE = 5_000L

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
                // Prototype v1038: big share count with RWF value, soft facts block, Buy 1 / Buy 5.
                // The backend counts shares in RWF, so a "share" here is SHARE_PRICE RWF.
                val held = sharesHeld ?: BigDecimal.ZERO
                val count = held.divide(BigDecimal(SHARE_PRICE), 0, java.math.RoundingMode.DOWN)
                if (count.signum() == 0) { SaccoIntro(formatMoney(SHARE_PRICE.toBigDecimal())); return@item }
                Column(Modifier.padding(top = 16.dp)) {
                    Text("Your shares", color = Ids.colors.textTertiary, fontSize = 14.sp)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("$count", color = Ids.colors.textPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 32.sp)
                        Text(" (${formatMoney(currentValue ?: held)} RWF)", color = Ids.colors.textPrimary, fontSize = 20.sp, modifier = Modifier.padding(bottom = 3.dp))
                    }
                    Text("Total contributed: ${formatMoney(totalContributed ?: BigDecimal.ZERO)} RWF", color = Ids.colors.success, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                    Text(
                        "Own a part of your cooperative. Each share is ${formatMoney(SHARE_PRICE.toBigDecimal())} RWF and earns a dividend when the SACCO makes a surplus.",
                        color = Ids.colors.textTertiary, fontSize = 15.sp, modifier = Modifier.padding(top = 16.dp),
                    )
                }
            }
            item {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Ids.colors.surfaceSoft).padding(horizontal = 20.dp, vertical = 4.dp)) {
                    Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Share price", color = Ids.colors.textTertiary, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        Text("${formatMoney(SHARE_PRICE.toBigDecimal())} RWF", color = Ids.colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(1, 5).forEachIndexed { i, n ->
                        Box(
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                                .background(if (i == 0) Ids.colors.brand else Ids.colors.surfaceSoft)
                                .pressScaleClickable(enabled = !busy) { amount = (SHARE_PRICE * n).toString(); buy() }
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(if (busy) "…" else "Buy $n", color = if (i == 0) Ids.colors.textPrimary else Ids.colors.textBrand, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
            }
            item {
                Text(
                    "Redeem 1 share", color = Ids.colors.textBrand, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.pressScaleClickable(enabled = !busy) { amount = SHARE_PRICE.toString(); redeem() }.padding(vertical = 4.dp),
                )
            }
            error?.let { msg -> item { Text(msg, color = Ids.colors.danger, fontSize = 13.sp) } }
            item { Text("Dividend history", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
            when {
                dividends == null -> item {
                    SkeletonBlock(height = 48.dp)
                }
                dividends!!.isEmpty() -> item {
                    EmptyState("No dividends declared yet.")
                }
                else -> items(dividends!!, key = { it.id }) { d ->
                    // Real fix (flat-design sweep): dropped the per-row Card.
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(d.createdAt.take(10), color = Ids.colors.textSecondary, fontSize = 12.sp)
                            Text("+${formatMoney(d.amount)} RWF", color = Ids.colors.success, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }
}

