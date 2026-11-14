package rw.itunda.feature.front.impl

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.ItundaArt
import rw.itunda.core.designsystem.components.ItundaArtSet
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids

private const val DEMO_BALANCE = 120_500

/** Prototype Front table flow (Menu, Bill, Pay, Done) at demo Table 7; all data local until the backend exists. */
@Composable
fun FrontTableFlow(onExit: () -> Unit, initialStep: Int = 0, initialQty: Map<String, Int> = emptyMap()) {
    var step by rememberSaveable { mutableIntStateOf(initialStep) }
    var qty by rememberSaveable { mutableStateOf(initialQty) }
    var tip by rememberSaveable { mutableIntStateOf(0) }
    var split by rememberSaveable { mutableIntStateOf(0) }
    val subtotal = frontDishes.sumOf { (qty[it.id] ?: 0) * it.price }
    val tipAmount = subtotal * listOf(0, 5, 10)[tip] / 100
    val total = subtotal + tipAmount
    val share = total / listOf(1, 2, 3, 4)[split]
    BackHandler { if (step in 1..2) step-- else onExit() }

    if (step == 3) {
        FrontDone(share, onMore = { step = 0 }, onDone = onExit)
        return
    }
    Column(Modifier.fillMaxSize().background(Ids.colors.background)) {
        FrontStepHeader(
            step,
            title = when (step) { 0 -> "Table 7"; 1 -> "Your bill"; else -> "Pay" },
            subtitle = if (step == 0) null else "Table 7 ·",
            onBack = { if (step == 0) onExit() else step-- },
        )
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            when (step) {
                0 -> { Spacer(Modifier.height(20.dp)); FrontMenuStep(qty) { id, n -> qty = qty + (id to n.coerceAtLeast(0)) } }
                1 -> FrontBill(subtotal, tipAmount, total, tip, split, { tip = it }, { split = it })
                else -> FrontPayStep(share)
            }
        }
        val count = qty.values.sum()
        when (step) {
            0 -> if (count == 0) FrontCta("Pick something to order", enabled = false) {} else FrontCta("View bill · ${rwf(subtotal)}") { step = 1 }
            1 -> FrontCta("Pay · ${rwf(share)}") { step = 2 }
            else -> FrontCta("Pay ${rwf(share)}", enabled = share <= DEMO_BALANCE) { step = 3 }
        }
    }
}

@Composable
private fun FrontBill(subtotal: Int, tipAmount: Int, total: Int, tip: Int, split: Int, onTip: (Int) -> Unit, onSplit: (Int) -> Unit) {
    Spacer(Modifier.height(16.dp))
    Text("Total", color = Ids.colors.textTertiary, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 28.dp))
    Text(rwf(total), color = Ids.colors.textPrimary, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 28.dp))
    Spacer(Modifier.height(16.dp))
    FrontSoftRow {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Tip", color = Ids.colors.textPrimary, fontSize = 16.sp)
            Text(rwf(tipAmount), color = Ids.colors.textPrimary, fontSize = 16.sp)
        }
    }
    Spacer(Modifier.height(32.dp))
    Text("Tip", color = Ids.colors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 28.dp))
    Spacer(Modifier.height(12.dp))
    FrontSegments(listOf("No tip", "5%", "10%"), tip, onTip)
    Spacer(Modifier.height(32.dp))
    Text("Split", color = Ids.colors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 28.dp))
    Spacer(Modifier.height(12.dp))
    FrontSegments(listOf("Pay all", "÷ 2", "÷ 3", "÷ 4"), split, onSplit)
}

@Composable
private fun FrontPayStep(share: Int) {
    Spacer(Modifier.height(16.dp))
    Text("To pay", color = Ids.colors.textTertiary, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 28.dp))
    Text(rwf(share), color = Ids.colors.textPrimary, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 28.dp))
    Spacer(Modifier.height(16.dp))
    FrontSoftRow {
        Text("Pay with", color = Ids.colors.textTertiary, fontSize = 13.sp)
        Text("itunda Pay", color = Ids.colors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
        Text("Balance ${rwf(DEMO_BALANCE)}", color = Ids.colors.textTertiary, fontSize = 13.sp)
    }
    Spacer(Modifier.height(12.dp))
    FrontSoftRow {
        Text("Points and offers", color = Ids.colors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
        Text("Nothing selected", color = Ids.colors.textTertiary, fontSize = 13.sp)
    }
    Spacer(Modifier.height(12.dp))
    Row(
        Modifier.padding(horizontal = 24.dp).clip(RoundedCornerShape(22.dp)).background(Ids.colors.surfaceSoft).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ItundaArt("bell", ItundaArtSet.Color, 20.dp)
        Text("  Call staff", color = Ids.colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun FrontDone(paid: Int, onMore: () -> Unit, onDone: () -> Unit) {
    var stars by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().background(Ids.colors.background).padding(horizontal = 28.dp)) {
        Spacer(Modifier.height(72.dp))
        Box(Modifier.height(56.dp).clip(RoundedCornerShape(28.dp)).background(Ids.colors.brand).padding(14.dp)) { ItundaArt("check", ItundaArtSet.Line, 28.dp, tint = Color.White) }
        Spacer(Modifier.height(20.dp))
        Text("${rwf(paid)} paid", color = Ids.colors.textPrimary, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        Text("Paid with itunda Pay", color = Ids.colors.textTertiary, fontSize = 15.sp)
        Spacer(Modifier.height(24.dp))
        FrontDoneRow("Your table", "Table 7")
        FrontDoneRow("We will bring it to you.", "Order received")
        FrontDoneRow("Receipt", "In my app")
        Spacer(Modifier.height(24.dp))
        Text("How was it?", color = Ids.colors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
        Row(Modifier.padding(top = 8.dp)) {
            (1..5).forEach { n -> Text("★", color = if (n <= stars) Color(0xFFFFB800) else Ids.colors.surfaceSoft, fontSize = 36.sp, modifier = Modifier.pressScaleClickable { stars = n }.padding(end = 6.dp)) }
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(16.dp)).background(Ids.colors.surfaceSoft).pressScaleClickable(onClick = onMore), contentAlignment = Alignment.Center) {
                Text("Order more", color = Ids.colors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
            Box(Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(16.dp)).background(Ids.colors.brand).pressScaleClickable(onClick = onDone), contentAlignment = Alignment.Center) {
                Text("Done", color = Ids.colors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun FrontDoneRow(left: String, right: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(left, color = Ids.colors.textTertiary, fontSize = 15.sp)
        Text(right, color = Ids.colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}
