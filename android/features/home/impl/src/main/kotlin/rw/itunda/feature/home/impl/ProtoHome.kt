package rw.itunda.feature.home.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.Divider
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import rw.itunda.core.designsystem.components.IdsAppBarTitle
import rw.itunda.core.designsystem.components.ItundaArt
import rw.itunda.core.designsystem.components.ItundaArtSet
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.designsystem.theme.IdsIcons

/**
 * Home, rebuilt from the HTML prototype (v1038) with its measured values: every
 * size/radius/weight below is a number read off the prototype's DOM at a 390dp-wide
 * viewport, not an approximation. Layout (top to bottom): app bar, balance card (two
 * accounts with Send/Add pills + Bills/Card/Savings/See all row), slim invite row,
 * spending card (month total, selected-day readout, 7 bars, upcoming bill), earned card.
 *
 * Stateless on purpose so the Roborazzi screenshot test can render it with fixed data
 * and compare it with the prototype (android/screenshots).
 */
data class ProtoBill(val title: String, val due: String, val amount: Double)

data class ProtoHomeState(
    val bankBalance: Double,
    val payBalance: Double,
    val interestDays: Int?,
    val spentThisMonth: Double,
    val monthName: String,
    val week: List<DaySpend>,
    val bill: ProtoBill?,
    val earned: Double,
    val today: java.time.LocalDate = java.time.LocalDate.now(),
)

private fun rwf(v: Double) = String.format(Locale.US, "%,.0f", v)

private val PillShape = RoundedCornerShape(18.dp)

@Composable
fun ProtoHomeContent(
    state: ProtoHomeState,
    hasUnread: Boolean,
    onOpenBank: () -> Unit,
    onOpenPay: () -> Unit,
    onOpenNotifications: () -> Unit,
    onSend: () -> Unit,
    onAdd: () -> Unit,
    onBills: () -> Unit,
    onCard: () -> Unit,
    onSavings: () -> Unit,
    onSeeAll: () -> Unit,
    onInvite: () -> Unit,
    onOpenSpending: () -> Unit,
    onPayBill: () -> Unit,
    onOpenRewards: () -> Unit,
) {
    var inviteDismissed by remember { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Ids.colors.surfaceSoft),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 15.dp, end = 15.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { HomeBar(hasUnread, onOpenBank, onOpenPay, onOpenNotifications) }
        item {
            Card(Modifier.drawBehind {
                drawRect(Brush.radialGradient(listOf(Color(0xFF8F89FF).copy(alpha = 0.22f), Color.Transparent), center = Offset(size.width, 0f), radius = size.width * 0.8f))
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color(0xFF8F89FF).copy(alpha = 0.10f)), startY = size.height * 0.7f, endY = size.height))
            }) {
                Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 36.dp)) {
                    AccountRow(
                        tile = { Icon(painterResource(rw.itunda.core.designsystem.R.drawable.itunda_mark), null, Modifier.size(22.dp), tint = Ids.colors.brand) },
                        amount = state.bankBalance,
                        subtitle = buildAnnotatedString {
                            append("itunda Bank")
                            state.interestDays?.let {
                                append(" ")
                                withStyle(SpanStyle(color = Ids.colors.textBrand)) { append("+interest $it") }
                            }
                        },
                        action = "Send", onAction = onSend,
                    )
                    Spacer(Modifier.height(34.dp))
                    AccountRow(
                        tile = { ItundaArt("wallet", ItundaArtSet.Color, 26.dp) },
                        amount = state.payBalance,
                        subtitle = buildAnnotatedString { append("itunda Pay") },
                        action = "Add", onAction = onAdd,
                    )
                    Spacer(Modifier.height(22.dp))
                    Divider(color = Ids.colors.divider, thickness = 0.5.dp)
                    Row(Modifier.fillMaxWidth().heightIn(min = 49.dp), verticalAlignment = Alignment.CenterVertically) {
                        ShortcutText("Bills", onBills)
                        Spacer(Modifier.width(18.dp))
                        ShortcutText("Card", onCard)
                        Spacer(Modifier.width(18.dp))
                        ShortcutText("Savings", onSavings)
                        Spacer(Modifier.weight(1f))
                        ShortcutText("See all", onSeeAll)
                    }
                }
            }
        }
        if (!inviteDismissed) {
            item {
                Card {
                    Row(
                        Modifier.fillMaxWidth().clickable(onClick = onInvite).padding(horizontal = 16.dp).height(76.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(listOf(Color(0xFFEEEDFF), Color(0xFFC9C5FF)))),
                            contentAlignment = Alignment.Center,
                        ) { ItundaArt("gift", ItundaArtSet.Color, 32.dp) }
                        Spacer(Modifier.width(14.dp))
                        Text(
                            buildAnnotatedString {
                                append("Invite friends, get ")
                                withStyle(SpanStyle(color = Ids.colors.textBrand)) { append("10,000 RWF") }
                            },
                            color = Ids.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            IdsIcons.Close, "Dismiss", Modifier.size(20.dp).pressScaleClickable { inviteDismissed = true },
                            tint = Ids.colors.textPrimary,
                        )
                    }
                }
            }
        }
        item {
            Card {
                Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 36.dp, bottom = 12.dp)) {
                    Row(Modifier.fillMaxWidth().clickable(onClick = onOpenSpending), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            AmountLine(state.spentThisMonth)
                            Text("Spent in ${state.monthName}", color = Ids.colors.textSecondary, fontSize = 14.sp)
                        }
                        Icon(IdsIcons.ChevronRight, null, Modifier.size(20.dp), tint = Ids.colors.textTertiary.copy(alpha = 0.7f))
                    }
                    ProtoWeekChart(state.week, today = state.today)
                    state.bill?.let { bill ->
                        Spacer(Modifier.height(20.dp))
                        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconTile { ItundaArt("bill", ItundaArtSet.Color, 26.dp) }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(bill.title, color = Ids.colors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                                Text("Due ${bill.due} · ${rwf(bill.amount)} RWF", color = Ids.colors.textSecondary, fontSize = 14.sp)
                            }
                            Pill("Pay", onPayBill)
                        }
                    }
                }
            }
        }
        item {
            Card {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(105.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconTile { Text("⭐", fontSize = 20.sp) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${rwf(state.earned)} RWF earned", color = Ids.colors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Text("Points and coupons", color = Ids.colors.textSecondary, fontSize = 14.sp)
                    }
                    Pill("View", onOpenRewards)
                }
            }
        }
    }
}

@Composable
private fun HomeBar(hasUnread: Boolean, onOpenBank: () -> Unit, onOpenPay: () -> Unit, onOpenNotifications: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(78.dp).padding(start = 10.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        IdsAppBarTitle(area = "bank", onClick = onOpenBank)
        Spacer(Modifier.weight(1f))
        Icon(
            Icons.Outlined.QrCodeScanner, "Pay", Modifier.size(28.dp).pressScaleClickable(onClick = onOpenPay),
            tint = Ids.colors.textTertiary,
        )
        Spacer(Modifier.width(20.dp))
        Box(Modifier.pressScaleClickable(onClick = onOpenNotifications).padding(end = 6.dp)) {
            Icon(IdsIcons.Bell, "Notifications", Modifier.size(28.dp), tint = Ids.colors.textTertiary)
            if (hasUnread) {
                Box(Modifier.align(Alignment.TopEnd).size(9.dp).clip(CircleShape).background(Ids.colors.danger))
            }
        }
    }
}

@Composable
private fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Ids.colors.surface).then(modifier)) { content() }
}

@Composable
private fun WalletGlyph() {
    androidx.compose.foundation.Canvas(Modifier.size(24.dp)) {
        val u = size.width / 24f
        drawRoundRect(Color(0xFFFFC83D), Offset(4 * u, 3 * u), androidx.compose.ui.geometry.Size(13 * u, 8 * u), androidx.compose.ui.geometry.CornerRadius(3 * u))
        drawRoundRect(Color(0xFF7B74F5), Offset(2 * u, 7 * u), androidx.compose.ui.geometry.Size(20 * u, 14 * u), androidx.compose.ui.geometry.CornerRadius(4 * u))
        drawCircle(Color(0xFFFFC83D), 2 * u, Offset(17 * u, 14 * u))
    }
}

@Composable
private fun IconTile(content: @Composable () -> Unit) {
    Box(Modifier.size(36.dp).clip(RoundedCornerShape(14.dp)).background(Ids.colors.surfaceSoft), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun AmountLine(amount: Double) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(rwf(amount), color = Ids.colors.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        Text(" RWF", color = Ids.colors.textSecondary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 2.dp))
    }
}

@Composable
private fun AccountRow(tile: @Composable () -> Unit, amount: Double, subtitle: androidx.compose.ui.text.AnnotatedString, action: String, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconTile(tile)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            AmountLine(amount)
            Text(subtitle, color = Ids.colors.textSecondary, fontSize = 14.sp)
        }
        Pill(action, onAction)
    }
}

@Composable
private fun Pill(label: String, onClick: () -> Unit) {
    Box(
        Modifier.height(44.dp).widthIn(min = 64.dp).clip(PillShape).background(Ids.colors.brand.copy(alpha = 0.14f)).pressScaleClickable(onClick = onClick).padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = Ids.colors.textBrand, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun ShortcutText(label: String, onClick: () -> Unit) {
    Text(label, color = Ids.colors.textSecondary, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.pressScaleClickable(onClick = onClick).padding(vertical = 14.dp))
}
