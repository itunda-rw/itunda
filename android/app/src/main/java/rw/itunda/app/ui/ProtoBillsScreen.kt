package rw.itunda.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import rw.itunda.core.designsystem.components.BackTopBar
import rw.itunda.core.designsystem.components.IdsTextField
import rw.itunda.core.designsystem.components.ItundaArt
import rw.itunda.core.designsystem.components.ItundaArtSet
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids

/** Prototype v1038 bill providers (sample data): label, digits needed, quick amounts, electricity token. */
private data class BillProvider(val name: String, val label: String, val digits: Int, val amounts: List<Int>, val token: Boolean = false)

private val BILL_PROVIDERS = listOf(
    BillProvider("REG electricity", "Meter number", 11, listOf(2000, 5000, 10000, 18500), token = true),
    BillProvider("WASAC water", "Water meter number", 9, listOf(2000, 5000, 10000, 20000)),
    BillProvider("MTN airtime", "Phone number", 9, listOf(500, 1000, 2000, 5000)),
    BillProvider("Airtel airtime", "Phone number", 9, listOf(500, 1000, 2000, 5000)),
    BillProvider("RRA taxes", "Tax ID (TIN)", 9, listOf(5000, 10000, 25000, 50000)),
    BillProvider("School fees", "Student ID", 6, listOf(20000, 50000, 100000, 120000)),
    BillProvider("Irembo", "Application number", 8, listOf(1000, 5000, 10000, 20000)),
)

private fun rwf(n: Int) = "%,d".format(n)

/**
 * Prototype Pay bills flow with dummy providers and a local sample balance (UX test build, per product
 * direction: backend wiring comes later). Nothing is sent anywhere, and the screen says so.
 */
@Composable
fun ProtoBillsScreen(onBack: () -> Unit) {
    var providerIdx by rememberSaveable { mutableStateOf(0) }
    var number by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf(BILL_PROVIDERS[0].amounts[1]) }
    var balance by rememberSaveable { mutableStateOf(250_000) }
    var confirming by rememberSaveable { mutableStateOf(false) }
    var done by rememberSaveable { mutableStateOf(false) }
    val p = BILL_PROVIDERS[providerIdx]
    BackHandler { if (done) { done = false; number = "" } else onBack() }

    if (done) { BillDone(p, number, amount, onDone = { done = false; number = ""; onBack() }); return }

    val tooMuch = amount > balance
    val ready = number.length == p.digits && !tooMuch
    Column(Modifier.fillMaxSize().background(Ids.colors.surface)) {
        BackTopBar(title = "Pay bills", onBack = onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BILL_PROVIDERS.forEachIndexed { i, bp ->
                    val on = i == providerIdx
                    Text(
                        bp.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (on) Ids.colors.textPrimary else Ids.colors.textTertiary,
                        modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(if (on) Ids.colors.brand else Ids.colors.surfaceSoft)
                            .pressScaleClickable { providerIdx = i; number = ""; amount = bp.amounts[1] }.padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }
            Column(Modifier.padding(horizontal = 20.dp)) {
                IdsTextField(
                    value = number, onValueChange = { number = it.filter(Char::isDigit).take(p.digits) }, label = p.label,
                    keyboardType = KeyboardType.Number, modifier = Modifier.fillMaxWidth(),
                    supportingText = "${number.length}/${p.digits} digits" + if (p.label == "Phone number") " (without the 0)" else "",
                )
                Text("Amount", color = Ids.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 20.dp, bottom = 10.dp))
                p.amounts.chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        pair.forEach { a ->
                            val on = a == amount
                            Box(
                                Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(16.dp)).background(if (on) Ids.colors.brand else Ids.colors.surfaceSoft).pressScaleClickable { amount = a },
                                contentAlignment = Alignment.Center,
                            ) { Text("${rwf(a)} RWF", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (on) Ids.colors.textPrimary else Ids.colors.textTertiary) }
                        }
                    }
                }
                Text(
                    if (tooMuch) "Not enough in itunda Bank (${rwf(balance)} RWF available)" else "From itunda Bank · ${rwf(balance)} RWF available",
                    color = if (tooMuch) Ids.colors.danger else Ids.colors.textTertiary, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp),
                )
                Text("Providers, numbers and balance are sample data. No real payment is made.", color = Ids.colors.textTertiary, fontSize = 13.sp, modifier = Modifier.padding(top = 20.dp, bottom = 20.dp))
            }
        }
        Box(
            Modifier.padding(20.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (ready) Ids.colors.brand else Ids.colors.surfaceSoft)
                .pressScaleClickable(enabled = ready) { confirming = true }.padding(vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) { Text("Pay ${rwf(amount)} RWF", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (ready) Ids.colors.textPrimary else Ids.colors.textTertiary) }
    }
    if (confirming) {
        Dialog(onDismissRequest = { confirming = false }) {
            Column(Modifier.clip(RoundedCornerShape(28.dp)).background(Ids.colors.surface).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                ItundaArt("verified3d", ItundaArtSet.Proto, 60.dp)
                Text("Confirm it's you", color = Ids.colors.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 12.dp))
                Text("Pay ${rwf(amount)} RWF to ${p.name} · $number", color = Ids.colors.textTertiary, fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp, bottom = 24.dp))
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Ids.colors.brand).pressScaleClickable { confirming = false; balance -= amount; done = true }.padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("Confirm", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ids.colors.textPrimary) }
            }
        }
    }
}

@Composable
private fun BillDone(p: BillProvider, number: String, amount: Int, onDone: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Ids.colors.surface).padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(72.dp))
        ItundaArt("success3d", ItundaArtSet.Proto, 132.dp)
        Text("${rwf(amount)} RWF", color = Ids.colors.textPrimary, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 28.dp))
        Text("Paid to ${p.name}", color = Ids.colors.textTertiary, fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp))
        Column(Modifier.fillMaxWidth().padding(top = 44.dp).clip(RoundedCornerShape(20.dp)).background(Ids.colors.surfaceSoft).padding(horizontal = 20.dp, vertical = 4.dp)) {
            DoneLine(p.label, number)
            if (p.token) DoneLine("Token (sample)", "4821 0937 6612 3358")
            DoneLine("Status", "Paid (sample)")
        }
        Spacer(Modifier.weight(1f))
        Box(Modifier.padding(vertical = 20.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Ids.colors.brand).pressScaleClickable(onClick = onDone).padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
            Text("Done", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ids.colors.textPrimary)
        }
    }
}

@Composable
private fun DoneLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Ids.colors.textTertiary, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text(value, color = Ids.colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}
