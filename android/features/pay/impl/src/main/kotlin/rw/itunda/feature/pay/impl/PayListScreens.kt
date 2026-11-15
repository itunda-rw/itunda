package rw.itunda.feature.pay.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.BackTopBar
import rw.itunda.core.designsystem.theme.Ids

@Composable
private fun Letter(letter: String, bg: Color, fg: Color) {
    Box(Modifier.size(40.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        Text(letter, color = fg, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

/** Prototype "Payment history": month total, filter pills, rows grouped by day (dummy data). */
@Composable
fun PayHistoryScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Ids.colors.background)) {
        BackTopBar("Payment history", onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Text("Spent this month", color = Ids.colors.textTertiary, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
            Text("28,700 RWF", color = Ids.colors.textPrimary, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
            Row(Modifier.padding(vertical = 16.dp)) {
                listOf("All", "Paid", "Refunds").forEachIndexed { i, label ->
                    Text(label, color = if (i == 0) Color.White else Ids.colors.textTertiary, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 8.dp).clip(RoundedCornerShape(20.dp)).background(if (i == 0) Ids.colors.textPrimary else Ids.colors.surfaceSoft).padding(horizontal = 22.dp, vertical = 10.dp))
                }
            }
            payHistory.groupBy { it.day }.forEach { (day, rows) ->
                Text(day, color = Ids.colors.textTertiary, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                rows.forEach { r ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Letter(r.name.take(1), Ids.colors.surfaceSoft, Ids.colors.textPrimary)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(r.name, color = Ids.colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text("${r.time} · itunda Pay", color = Ids.colors.textTertiary, fontSize = 12.sp)
                        }
                        Text("−${r.amount}", color = Ids.colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/** Prototype "Saved merchants": starred tills plus recent ones. */
@Composable
fun PaySavedMerchantsScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Ids.colors.background)) {
        BackTopBar("Saved merchants", onBack)
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text("Saved", color = Ids.colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
            Text("Tap ☆ on a merchant to keep it here. Paying again takes two taps.", color = Ids.colors.textTertiary, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp))
            Text("Recent", color = Ids.colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
            payMerchants.forEach { m ->
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Letter(m.name.take(1), Ids.colors.surfaceSoft, Ids.colors.textPrimary)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(m.name, color = Ids.colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text("Till ${m.till} · Last ${m.last} RWF", color = Ids.colors.textTertiary, fontSize = 12.sp)
                    }
                    Text("☆", color = Ids.colors.textTertiary, fontSize = 20.sp)
                }
            }
        }
    }
}

/** Prototype "Points": connected balances (dummy). */
@Composable
fun PayPointsScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Ids.colors.background)) {
        BackTopBar("Points", onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(16.dp)).background(Ids.colors.surfaceSoft).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Letter("%", Ids.colors.brand, Color.White)
                Column(Modifier.padding(start = 12.dp)) {
                    Text("itunda Points", color = Ids.colors.textTertiary, fontSize = 12.sp)
                    Text("See extra earning and discounts", color = Ids.colors.textBrand, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
            Text("7 points connected", color = Ids.colors.textTertiary, fontSize = 13.sp, modifier = Modifier.padding(top = 20.dp))
            Text("120,668 RWF", color = Ids.colors.textPrimary, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
            payPoints.forEach { p ->
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Letter(p.letter, Color(p.color), Color.White)
                    Text(p.name, color = Ids.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(start = 12.dp))
                    Text(p.value, color = if (p.value == "0") Ids.colors.textTertiary else Ids.colors.textBrand, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("  ›", color = Ids.colors.textTertiary, fontSize = 20.sp)
                }
            }
        }
    }
}
