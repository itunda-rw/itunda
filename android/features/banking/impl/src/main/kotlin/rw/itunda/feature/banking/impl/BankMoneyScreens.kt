package rw.itunda.feature.banking.impl

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids

private const val DEMO_BALANCE = 842_300L

@Composable
private fun AmountInput(value: Long, onChange: (Long) -> Unit) {
    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
        BasicTextField(
            value = if (value == 0L) "" else rwf(value), onValueChange = { onChange(it.filter(Char::isDigit).take(9).toLongOrNull() ?: 0L) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = TextStyle(color = Ids.colors.textPrimary, fontSize = 44.sp, fontWeight = FontWeight.ExtraBold),
            modifier = Modifier.weight(1f),
        )
        Text("RWF", color = Ids.colors.textTertiary, fontSize = 20.sp, modifier = Modifier.padding(bottom = 8.dp))
    }
}

@Composable
private fun AddChips(steps: List<Long>, onAdd: (Long) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        steps.forEach { BankChip("+${rwf(it)}", false, { onAdd(it) }, Modifier.weight(1f), compact = true) }
    }
}

@Composable
internal fun BankDepositScreen(onBack: () -> Unit) {
    var amount by remember { mutableStateOf(20_000L) }
    var from by remember { mutableStateOf(0) }
    var done by remember { mutableStateOf(false) }
    if (done) { BankDone("Deposit requested", "${rwf(amount)} RWF is on its way to itunda Bank.", onBack); return }
    Column(Modifier.fillMaxSize().background(Ids.colors.background)) {
        BankSubTopBar("Deposit to itunda Bank", onBack)
        Column(Modifier.weight(1f).padding(horizontal = 20.dp)) {
            Text("Amount", color = Ids.colors.textTertiary, fontSize = 14.sp)
            AmountInput(amount) { amount = it }
            Text("Free and instant.", color = Ids.colors.textTertiary, fontSize = 14.sp)
            Spacer(Modifier.height(16.dp))
            AddChips(listOf(10_000, 20_000, 50_000, 100_000)) { amount += it }
            BankSectionLabel("From")
            listOf("MTN MoMo" to Color(0xFFFFCC00), "Airtel Money" to Color(0xFFE4262B)).forEachIndexed { i, (name, c) ->
                Row(Modifier.fillMaxWidth().pressScaleClickable { from = i }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(36.dp).clip(CircleShape).background(c))
                    Text(name, color = Ids.colors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(start = 16.dp))
                    Box(Modifier.size(24.dp).clip(CircleShape).background(if (from == i) Ids.colors.brand else Ids.colors.surfaceSoft))
                }
            }
        }
        BankSubCta("Deposit ${rwf(amount)} RWF", enabled = amount > 0) { done = true }
    }
}

@Composable
internal fun BankSendScreen(onBack: () -> Unit) {
    val banks = listOf("Bank of Kigali", "I&M Bank", "Equity Bank", "Access Bank")
    var bank by remember { mutableStateOf(0) }
    var account by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf(10_000L) }
    var done by remember { mutableStateOf(false) }
    if (done) { BankDone("Transfer sent", "${rwf(amount)} RWF to ${banks[bank]} ••${account.takeLast(4)}. Arrives within minutes.", onBack); return }
    Column(Modifier.fillMaxSize().background(Ids.colors.background)) {
        BankSubTopBar("Send to a bank account", onBack)
        Column(Modifier.weight(1f).padding(horizontal = 20.dp)) {
            BankSectionLabel("Bank")
            banks.chunked(2).forEachIndexed { r, pair ->
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEachIndexed { c, b -> BankChip(b, bank == r * 2 + c, { bank = r * 2 + c }, Modifier.weight(1f)) }
                }
            }
            BankSectionLabel("Account number")
            BasicTextField(
                value = account, onValueChange = { account = it.filter(Char::isDigit).take(10) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = TextStyle(color = Ids.colors.textPrimary, fontSize = 24.sp, fontWeight = FontWeight.SemiBold),
                decorationBox = { inner -> if (account.isEmpty()) Text("0000000000", color = Ids.colors.divider, fontSize = 24.sp) else inner(); },
            )
            Text("${account.length}/10", color = Ids.colors.textTertiary, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            BankSectionLabel("Amount")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(10_000L, 50_000L, 100_000L, 500_000L).forEach { BankChip("${rwf(it)} RWF", amount == it, { amount = it }, Modifier.weight(1f), compact = true) }
            }
            Row(Modifier.fillMaxWidth().padding(top = 28.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Arrives", color = Ids.colors.textTertiary, fontSize = 15.sp)
                Text("Within minutes", color = Ids.colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        BankSubCta("Send", enabled = account.length == 10) { done = true }
    }
}

@Composable
internal fun BankMoveScreen(onBack: () -> Unit) {
    var amount by remember { mutableStateOf(5_000L) }
    var flip by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    val from = if (flip) "itunda Pay" else "itunda Bank"
    val to = if (flip) "itunda Bank" else "itunda Pay"
    if (done) { BankDone("Money moved", "${rwf(amount)} RWF from $from to $to.", onBack); return }
    Column(Modifier.fillMaxSize().background(Ids.colors.background)) {
        BankSubTopBar("Move money", onBack)
        Column(Modifier.weight(1f).padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AccountBox("From", from, Modifier.weight(1f))
                Box(Modifier.size(44.dp).clip(CircleShape).background(Ids.colors.surfaceSoft).pressScaleClickable { flip = !flip }, contentAlignment = Alignment.Center) {
                    Text("⇄", color = Ids.colors.textBrand, fontSize = 20.sp)
                }
                AccountBox("To", to, Modifier.weight(1f))
            }
            BankSectionLabel("Amount")
            AmountInput(amount) { amount = it }
            Text("Available · ${rwf(DEMO_BALANCE)} RWF", color = Ids.colors.textTertiary, fontSize = 14.sp)
            Spacer(Modifier.height(16.dp))
            AddChips(listOf(1_000, 5_000, 20_000, 50_000)) { amount += it }
        }
        BankSubCta("Move ${rwf(amount)} RWF", enabled = amount in 1..DEMO_BALANCE) { done = true }
    }
}

@Composable
private fun AccountBox(label: String, name: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(Ids.colors.surfaceSoft).padding(16.dp)) {
        Text(label, color = Ids.colors.textTertiary, fontSize = 13.sp)
        Text(name, color = Ids.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}
