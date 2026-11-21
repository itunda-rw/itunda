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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
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
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(56.dp)) {
        BasicTextField(
            value = if (value == 0L) "" else rwf(value), onValueChange = { onChange(it.filter(Char::isDigit).take(9).toLongOrNull() ?: 0L) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = TextStyle(color = Ids.colors.textPrimary, fontSize = 40.sp, lineHeight = 56.sp, letterSpacing = (-1).sp, fontWeight = FontWeight.Bold),
            modifier = Modifier.weight(1f),
        )
        Text("RWF", color = Ids.colors.textTertiary, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp, top = 14.dp))
    }
}

@Composable
private fun AddChips(steps: List<Long>, onAdd: (Long) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        steps.forEach { BankChip("+${rwf(it)}", false, { onAdd(it) }, Modifier.weight(1f), compact = true, largeText = true) }
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
        Column(Modifier.weight(1f).padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(12.dp))
            Text("Amount", color = Ids.colors.textPrimary, fontSize = 14.sp, lineHeight = 20.sp)
            Spacer(Modifier.height(6.dp))
            AmountInput(amount) { amount = it }
            Spacer(Modifier.height(6.dp))
            Text("Free and instant.", color = Ids.colors.textTertiary, fontSize = 14.sp, lineHeight = 20.sp)
            Spacer(Modifier.height(14.dp))
            AddChips(listOf(10_000, 20_000, 50_000, 100_000)) { amount += it }
            Text("From", color = Ids.colors.textPrimary, fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 28.dp, bottom = 6.dp))
            listOf("MTN MoMo" to Color(0xFFFFCC00), "Airtel Money" to Color(0xFFE40000)).forEachIndexed { i, (name, c) ->
                Row(Modifier.fillMaxWidth().height(72.dp).pressScaleClickable { from = i }, verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(c))
                    Text(name, color = Ids.colors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(start = 30.dp))
                    Box(Modifier.size(28.dp).clip(CircleShape).background(if (from == i) Ids.colors.brand else Ids.colors.surfaceSoft), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp), tint = if (from == i) Color.White else Ids.colors.textTertiary)
                    }
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
    var amount by remember { mutableStateOf(0L) }
    var done by remember { mutableStateOf(false) }
    if (done) { BankDone("Transfer sent", "${rwf(amount)} RWF to ${banks[bank]} ••${account.takeLast(4)}. Arrives within minutes.", onBack); return }
    Column(Modifier.fillMaxSize().background(Ids.colors.background)) {
        BankSubTopBar("Send to a bank account", onBack)
        Column(Modifier.weight(1f).padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(10.dp))
            BankSectionLabel("Bank")
            banks.chunked(2).forEachIndexed { r, pair ->
                Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEachIndexed { c, b -> BankChip(b, bank == r * 2 + c, { bank = r * 2 + c }, Modifier.weight(1f), art = "bank") }
                }
            }
            BankSectionLabel("Account number", top = 14.dp)
            BasicTextField(
                value = account, onValueChange = { account = it.filter(Char::isDigit).take(10) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = TextStyle(color = Ids.colors.textPrimary, fontSize = 24.sp, fontWeight = FontWeight.SemiBold),
                decorationBox = { inner -> if (account.isEmpty()) Text("0000000000", color = Ids.colors.textTertiary.copy(alpha = 0.75f), fontSize = 24.sp) else inner(); },
            )
            Text("${account.length}/10", color = Ids.colors.textTertiary, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            BankSectionLabel("Amount", top = 31.dp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(10_000L, 50_000L, 100_000L, 500_000L).forEach { BankChip("${rwf(it)} RWF", amount == it, { amount = it }, Modifier.weight(1f), compact = true) }
            }
            Row(Modifier.fillMaxWidth().padding(top = 28.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Arrives", color = Ids.colors.textTertiary, fontSize = 15.sp)
                Text("Within minutes", color = Ids.colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        BankSubCta("Send", enabled = account.length == 10 && amount > 0) { done = true }
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
                    Icon(Icons.Filled.Sync, contentDescription = "Swap accounts", modifier = Modifier.size(22.dp), tint = Ids.colors.brand)
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
