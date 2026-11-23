package rw.itunda.feature.pay.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.pressScaleClickable

@Composable
private fun Avatar(letter: String, bg: Color, fg: Color) {
    Box(Modifier.size(48.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        Text(letter, color = fg, fontSize = 19.sp, lineHeight = 26.6.sp, fontWeight = FontWeight.ExtraBold)
    }
}

/** Prototype section header: 15sp bold, 16dp above (not on the first) and 12dp below. */
@Composable
private fun DayHeader(p: PayPalette, text: String, first: Boolean = false) {
    Text(text, color = p.text, fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = if (first) 16.dp else 24.dp, bottom = if (first) 16.dp else 4.dp))
}

/** Prototype "Payment history": month total, search, filter pills, rows grouped by day (dummy data). */
@Composable
fun PayHistoryScreen(onBack: () -> Unit, onOpen: (Int) -> Unit = {}) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(0) }
    PaySubScreen(onBack = onBack, title = "Payment history") { p ->
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Text("Spent this month", color = p.tt, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
            Text("28,700 RWF", color = p.text, fontSize = 34.sp, lineHeight = 47.6.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp, modifier = Modifier.padding(top = 2.dp))
            BasicTextField(
                value = query, onValueChange = { query = it }, singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                textStyle = TextStyle(color = p.text, fontSize = 16.sp, lineHeight = 22.4.sp), cursorBrush = SolidColor(p.brandText),
                modifier = Modifier.padding(top = 4.dp).fillMaxWidth().height(44.dp).clip(RoundedCornerShape(14.dp)).background(p.soft),
                decorationBox = { inner ->
                    Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.foundation.Canvas(Modifier.size(18.dp)) {
                            val st = androidx.compose.ui.graphics.drawscope.Stroke(1.8.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                            drawCircle(p.tt, 6.dp.toPx(), androidx.compose.ui.geometry.Offset(7.5.dp.toPx(), 7.5.dp.toPx()), style = st)
                            drawLine(p.tt, androidx.compose.ui.geometry.Offset(12.dp.toPx(), 12.dp.toPx()), androidx.compose.ui.geometry.Offset(16.dp.toPx(), 16.dp.toPx()), 1.8.dp.toPx(), androidx.compose.ui.graphics.StrokeCap.Round)
                        }
                        Box(Modifier.padding(start = 12.dp).weight(1f)) {
                            if (query.isEmpty()) Text("Search merchant, note or reference", color = p.tt, fontSize = 16.sp, lineHeight = 22.4.sp)
                            inner()
                        }
                        if (query.isNotEmpty()) Text("×", color = p.tt, fontSize = 22.sp, modifier = Modifier.padding(start = 8.dp).pressScaleClickable { query = "" })
                    }
                },
            )
            Row(Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("All", "Paid", "Refunds").forEachIndexed { i, label ->
                    val on = filter == i
                    Box(
                        Modifier.width(114.dp).height(44.dp).clip(RoundedCornerShape(20.dp)).background(if (on) Color(0xFF191F28).let { if (p.page == Color(0xFF17171C)) p.text else it } else p.soft)
                            .pressScaleClickable { filter = i },
                        contentAlignment = Alignment.Center,
                    ) { Text(label, color = if (on) (if (p.page == Color(0xFF17171C)) Color(0xFF17171C) else Color.White) else p.text, fontSize = 14.sp, lineHeight = 19.6.sp, fontWeight = FontWeight.Bold) }
                }
            }
            val rows = if (filter == 2) emptyList() else payHistory.filter { row ->
                val term = query.trim()
                term.isEmpty() || listOf(row.name, row.till, row.date, row.time, row.amount, row.ref, row.day)
                    .any { it.contains(term, ignoreCase = true) }
            }
            if (rows.isEmpty()) Text("Nothing to show.", color = p.tt, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 24.dp))
            rows.groupBy { it.day }.entries.forEachIndexed { gi, (day, dayRows) ->
                DayHeader(p, day, first = gi == 0)
                dayRows.forEach { r ->
                    Row(Modifier.fillMaxWidth().height(72.dp).pressScaleClickable { onOpen(payHistory.indexOf(r)) }, verticalAlignment = Alignment.CenterVertically) {
                        Avatar(r.name.take(1), p.soft, p.text)
                        Column(Modifier.weight(1f).padding(start = 16.dp)) {
                            Text(r.name, color = p.text, fontSize = 17.sp, lineHeight = 23.8.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp)
                            Text("${r.time} · itunda Pay", color = p.tt, fontSize = 14.sp, lineHeight = 20.sp)
                        }
                        Text("−${r.amount}", color = p.text, fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
    }
}

/** Prototype "Saved merchants": starred tills plus recent ones. */
@Composable
fun PaySavedMerchantsScreen(onBack: () -> Unit) {
    var savedTills by remember { mutableStateOf(emptySet<String>()) }
    PaySubScreen(onBack = onBack, title = "Saved merchants") { p ->
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            DayHeader(p, "Saved", first = true)
            val savedMerchants = payMerchants.filter { it.till in savedTills }
            if (savedMerchants.isEmpty()) {
                Text("Tap ☆ on a merchant to keep it here. Paying again takes two taps.", color = p.tt, fontSize = 16.sp, lineHeight = 22.sp, modifier = Modifier.padding(bottom = 16.dp))
            } else {
                savedMerchants.forEach { merchant ->
                    SavedMerchantRow(merchant, p, isSaved = true) { savedTills = savedTills - merchant.till }
                }
            }
            DayHeader(p, "Recent")
            payMerchants.forEach { merchant ->
                SavedMerchantRow(merchant, p, isSaved = merchant.till in savedTills) {
                    savedTills = if (merchant.till in savedTills) savedTills - merchant.till else savedTills + merchant.till
                }
            }
        }
    }
}

@Composable
private fun SavedMerchantRow(merchant: PayMerchant, p: PayPalette, isSaved: Boolean, onToggleSaved: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(72.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(merchant.name.take(1), p.soft, p.text)
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            Text(merchant.name, color = p.text, fontSize = 17.sp, lineHeight = 23.8.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp)
            Text("Till ${merchant.till} · Last ${merchant.last} RWF", color = p.tt, fontSize = 14.sp, lineHeight = 20.sp)
        }
        Text(if (isSaved) "★" else "☆", color = if (isSaved) p.brandText else p.tt, fontSize = 24.sp, modifier = Modifier.padding(end = 12.dp).pressScaleClickable(onClick = onToggleSaved))
    }
}
/** Prototype "Points": connected balances (dummy). */
@Composable
fun PayPointsScreen(onBack: () -> Unit) {
    PaySubScreen(onBack = onBack, title = "Points") { p ->
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(16.dp)).background(p.soft).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar("%", p.brand, Color.White)
                Column(Modifier.padding(start = 12.dp)) {
                    Text("itunda Points", color = p.tt, fontSize = 13.sp, lineHeight = 18.sp)
                    Text("See extra earning and discounts", color = p.brandText, fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold)
                }
            }
            Text("7 points connected", color = p.tt, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 20.dp))
            Text("120,668 RWF", color = p.text, fontSize = 30.sp, lineHeight = 40.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.6).sp)
            Spacer(Modifier.height(8.dp))
            payPoints.forEach { r ->
                Row(Modifier.fillMaxWidth().height(60.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(r.letter, Color(r.color), Color.White)
                    Text(r.name, color = p.text, fontSize = 17.sp, lineHeight = 23.8.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp, modifier = Modifier.weight(1f).padding(start = 16.dp))
                    Text(r.value, color = if (r.value == "0") p.tt else p.brandText, fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.ExtraBold)
                    Text("  ›", color = p.tt, fontSize = 20.sp)
                }
            }
        }
    }
}
