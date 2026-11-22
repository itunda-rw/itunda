package rw.itunda.feature.banking.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.AmountKeypadInput
import rw.itunda.core.designsystem.components.IdsTextField
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids

// Prototype v1038 Bank service screens. All data is local dummy state; nothing leaves the device.

private val banks = listOf("Bank of Kigali", "I&M Bank", "Equity Bank", "Access Bank")

private class Payee(val name: String, val bank: String, val last4: String, val star: Boolean = false, val holder: String = name)

@Composable
private fun EmptyBlock(title: String, body: String) {
    Column(Modifier.fillMaxSize().padding(start = 59.dp, end = 59.dp, top = 108.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, color = Ids.colors.textPrimary, fontSize = 20.sp, lineHeight = 28.sp, letterSpacing = (-0.3).sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        Text(body, color = Ids.colors.textTertiary, fontSize = 15.sp, lineHeight = 22.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp))
    }
}

@Composable
internal fun BankPayeesScreen(onBack: () -> Unit) {
    val payees = remember { mutableStateListOf(Payee("Landlord", "Bank of Kigali", "6789", true, "Diane M."), Payee("Kigali Tutors", "Equity Bank", "3789")) }
    var adding by rememberSaveable { mutableStateOf(false) }
    if (adding) { BankAddPayee({ adding = false }) { payees.add(it) }; return }
    Column(Modifier.fillMaxSize().background(Ids.colors.background)) {
        BankSubTopBar("Payees", onBack)
        BankSubCta("+ Add a payee", top = 12.dp, bottom = 0.dp) { adding = true }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            payees.forEach { p ->
                Row(Modifier.fillMaxWidth().height(80.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(Color(0xFFEEEDFF)), contentAlignment = Alignment.Center) {
                        Text(p.holder.split(" ").take(2).joinToString("") { it.take(1) }.uppercase(), color = Ids.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Column(Modifier.padding(start = 16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) { Text(p.name, color = Ids.colors.textPrimary, fontSize = 17.sp, lineHeight = 24.sp, letterSpacing = (-0.2).sp, fontWeight = FontWeight.SemiBold); if (p.star) Text(" ★", color = Color(0xFFFFB800), fontSize = 16.sp) }
                        Text("${p.holder} · ${p.bank} ••${p.last4}", color = Ids.colors.textPrimary, fontSize = 14.sp, lineHeight = 21.sp, letterSpacing = (-0.2).sp, modifier = Modifier.padding(top = 3.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun BankAddPayee(onBack: () -> Unit, onSave: (Payee) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var bank by rememberSaveable { mutableStateOf(banks[0]) }
    var account by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize().background(Ids.colors.background)) {
        BankSubTopBar("Add a payee", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            IdsTextField(value = name, onValueChange = { name = it.take(40) }, label = "Name", modifier = Modifier.fillMaxWidth())
            BankSectionLabel("Bank")
            BankChoice(banks, bank) { bank = it }
            Spacer(Modifier.height(16.dp))
            IdsTextField(value = account, onValueChange = { account = it.filter(Char::isDigit).take(10) }, label = "Account number", supportingText = "${account.length}/10", modifier = Modifier.fillMaxWidth())
        }
        BankSubCta("Save payee", enabled = name.isNotBlank() && account.length == 10) { onSave(Payee(name.trim(), bank, account.takeLast(4))); onBack() }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun BankChoice(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { BankChip(it, it == selected, { onSelect(it) }) }
    }
}

private class Scheduled(val payee: String, val amount: Long, val every: String)

@Composable
internal fun BankScheduledScreen(onBack: () -> Unit) {
    val items = remember { mutableStateListOf<Scheduled>() }
    var adding by rememberSaveable { mutableStateOf(false) }
    if (adding) { BankNewScheduled({ adding = false }) { items.add(it) }; return }
    Column(Modifier.fillMaxSize().background(Ids.colors.background)) {
        BankSubTopBar("Scheduled transfers", onBack)
        if (items.isEmpty()) Box(Modifier.weight(1f)) { EmptyBlock("No scheduled transfers yet", "Rent, school fees or a monthly gift. Set it once, we remind you each time.") }
        else Column(Modifier.weight(1f).padding(horizontal = 24.dp)) {
            items.forEach { s ->
                Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                    Text(s.payee, color = Ids.colors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text("${rwf(s.amount)} RWF · ${s.every}", color = Ids.colors.textTertiary, fontSize = 14.sp)
                }
            }
        }
        BankSubCta("+ New scheduled transfer") { adding = true }
    }
}

@Composable
private fun BankNewScheduled(onBack: () -> Unit, onSave: (Scheduled) -> Unit) {
    var payee by rememberSaveable { mutableStateOf("Landlord") }
    var every by rememberSaveable { mutableStateOf("Monthly") }
    var digits by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize().background(Ids.colors.background)) {
        BankSubTopBar("New scheduled transfer", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            BankSectionLabel("Payee")
            BankChoice(listOf("Landlord", "Kigali Tutors"), payee) { payee = it }
            BankSectionLabel("Repeat")
            BankChoice(listOf("Weekly", "Monthly"), every) { every = it }
            AmountKeypadInput(digits = digits, onDigitsChange = { digits = it }, quickAmounts = listOf(10_000L, 50_000L, 100_000L))
        }
        BankSubCta("Schedule", enabled = (digits.toLongOrNull() ?: 0L) > 0) { onSave(Scheduled(payee, digits.toLong(), every)); onBack() }
    }
}

@Composable
internal fun BankBudgetScreen(onBack: () -> Unit) {
    var budget by rememberSaveable { mutableStateOf(0L) }
    var alert by rememberSaveable { mutableStateOf(true) }
    val spent = 260_600L
    Column(Modifier.fillMaxSize().background(Ids.colors.background)) {
        BankSubTopBar("Budget", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(16.dp))
            Text("Spent in your activity", color = Ids.colors.textTertiary, fontSize = 14.sp, lineHeight = 20.sp)
            Text("${rwf(spent)} RWF", color = Ids.colors.textPrimary, fontSize = 34.sp, lineHeight = 47.6.sp, letterSpacing = (-0.8).sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
            Text(if (budget == 0L) "Set a monthly budget to see how much is left." else "${rwf((budget - spent).coerceAtLeast(0))} RWF left of ${rwf(budget)}", color = Ids.colors.textTertiary, fontSize = 14.sp, lineHeight = 20.sp)
            Text("Monthly budget", color = Ids.colors.textPrimary, fontSize = 17.sp, lineHeight = 23.8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 22.dp, bottom = 6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0L, 100_000L, 300_000L, 500_000L, 1_000_000L).forEach { v -> BankChip(if (v == 0L) "None" else rwf(v), budget == v, { budget = v }, Modifier.width(62.dp), compact = true, largeText = true) }
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp).height(52.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Alert me at 80% of my budget", Modifier.weight(1f), color = Ids.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                BudgetSwitch(alert) { alert = it }
            }
            Text("By category", color = Ids.colors.textPrimary, fontSize = 17.sp, lineHeight = 23.8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 22.dp, bottom = 4.dp))
            listOf("Transfers" to 225_000L, "Other" to 35_000L, "Transport" to 600L).forEach { (label, v) ->
                Column(Modifier.fillMaxWidth().height(53.dp).padding(top = 10.dp)) {
                    Row { Text(label, Modifier.weight(1f), color = Ids.colors.textPrimary, fontSize = 15.sp, lineHeight = 21.sp); Text("${rwf(v)} RWF", color = Ids.colors.textPrimary, fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold) }
                    Box(Modifier.fillMaxWidth().padding(top = 6.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Ids.colors.surfaceSoft)) {
                        Box(Modifier.fillMaxWidth(v.toFloat() / 260_600f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Ids.colors.textBrand))
                    }
                }
            }
        }
    }
}

/** Prototype toggle: 46x28 pill, 22dp white knob, deep-brand when on. */
@Composable
private fun BudgetSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    Box(
        Modifier.size(width = 46.dp, height = 28.dp).clip(RoundedCornerShape(14.dp))
            .background(if (checked) Ids.colors.textBrand else Ids.colors.surfaceSoft)
            .pressScaleClickable { onChange(!checked) }.padding(3.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) { Box(Modifier.size(22.dp).clip(CircleShape).background(Color.White)) }
}

@Composable
internal fun BankCasesScreen(onBack: () -> Unit) {
    val cases = remember { mutableStateListOf<String>() }
    var adding by rememberSaveable { mutableStateOf(false) }
    if (adding) { BankNewCase({ adding = false }) { cases.add(it) }; return }
    Column(Modifier.fillMaxSize().background(Ids.colors.background)) {
        BankSubTopBar("Report a problem", onBack)
        if (cases.isEmpty()) Box(Modifier.weight(1f)) { EmptyBlock("No open cases", "Charged twice or a payment you do not recognise? Tell us and track the case here.") }
        else Column(Modifier.weight(1f).padding(horizontal = 24.dp)) {
            cases.forEach { c -> Column(Modifier.padding(vertical = 12.dp)) { Text(c, color = Ids.colors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold); Text("Open · under review", color = Ids.colors.textTertiary, fontSize = 14.sp) } }
        }
        BankSubCta("+ Dispute a payment") { adding = true }
    }
}

@Composable
private fun BankNewCase(onBack: () -> Unit, onSave: (String) -> Unit) {
    val options = listOf("Rent −150,000 RWF", "Jean Paul −30,000 RWF", "Kigali Bus Services −300 RWF")
    var picked by rememberSaveable { mutableStateOf(options[0]) }
    Column(Modifier.fillMaxSize().background(Ids.colors.background)) {
        BankSubTopBar("Dispute a payment", onBack)
        Column(Modifier.weight(1f).padding(horizontal = 24.dp)) { BankSectionLabel("Which payment?"); BankChoice(options, picked) { picked = it } }
        BankSubCta("Send report") { onSave(picked); onBack() }
    }
}

@Composable
internal fun BankDocsScreen(onBack: () -> Unit, onOpen: (BankSub) -> Unit) {
    var done by rememberSaveable { mutableStateOf<String?>(null) }
    done?.let { BankDone(it, "Demo only. No document was generated.") { done = null }; return }
    val rows = listOf(
        Triple("Account confirmation letter", "Confirms you hold an account", "bill"),
        Triple("Proof of balance", "Your balance today", "money"),
        Triple("Interest certificate", "Interest earned this year", "history"),
        Triple("Statement", "Pick a period, CSV or PDF", "receipt"),
    )
    Column(Modifier.fillMaxSize().background(Ids.colors.background)) {
        BankSubTopBar("Documents & letters", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(16.dp))
            Text("Proof of your account, ready in seconds. Use it for rent, visas, school or employers.", color = Ids.colors.textPrimary, fontSize = 15.sp, lineHeight = 22.sp)
            Spacer(Modifier.height(8.dp))
            rows.forEach { (title, sub, art) ->
                Row(Modifier.fillMaxWidth().height(80.dp).pressScaleClickable { if (title == "Statement") onOpen(BankSub.STATEMENT) else done = title }, verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(44.dp).clip(RoundedCornerShape(16.dp)).background(Ids.colors.surfaceSoft),
                        contentAlignment = Alignment.Center,
                    ) { rw.itunda.core.designsystem.components.ItundaArt(art, rw.itunda.core.designsystem.components.ItundaArtSet.Proto, 31.dp, fit = true) }
                    Column(Modifier.weight(1f).padding(start = 16.dp)) {
                        Text(title, color = Ids.colors.textPrimary, fontSize = 17.sp, lineHeight = 24.sp, letterSpacing = (-0.2).sp, fontWeight = FontWeight.SemiBold)
                        Text(sub, color = Ids.colors.textTertiary, fontSize = 14.sp, lineHeight = 21.sp, letterSpacing = (-0.2).sp, modifier = Modifier.padding(top = 3.dp))
                    }
                    rw.itunda.core.designsystem.components.ItundaArt("chev", rw.itunda.core.designsystem.components.ItundaArtSet.Line, 16.dp, tint = Ids.colors.textTertiary.copy(alpha = 0.55f))
                }
            }
        }
    }
}

@Composable
internal fun BankDetailsScreen(onBack: () -> Unit) {
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    Column(Modifier.fillMaxSize().background(Ids.colors.background)) {
        BankSubTopBar("Account details", onBack)
        Column(Modifier.weight(1f).padding(horizontal = 24.dp)) {
            listOf("Holder" to "Amina Uwase", "Account number" to "0214-3058-1980", "Bank" to "itunda Bank", "Branch" to "Kigali", "Currency" to "RWF").forEach { (k, v) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 14.dp)) { Text(k, Modifier.weight(1f), color = Ids.colors.textTertiary, fontSize = 16.sp); Text(v, color = Ids.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
            }
            Spacer(Modifier.height(8.dp))
            BankChip("Copy number", false, { clipboard.setText(androidx.compose.ui.text.AnnotatedString("0214-3058-1980")) })
        }
    }
}
