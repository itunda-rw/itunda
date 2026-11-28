package rw.itunda.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale
import rw.itunda.core.designsystem.components.ItundaArt
import rw.itunda.core.designsystem.components.ItundaArtSet
import rw.itunda.core.designsystem.components.ProtoTopBar
import rw.itunda.core.designsystem.components.SkeletonBlock
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.designsystem.theme.IdsIcons
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.OverviewResponse

private fun rwf(v: BigDecimal): String = NumberFormat.getInstance(Locale.US).format(v)

/** Prototype "My assets": net worth header, then flat Accounts / Cards / Loans lists and an open-an-account row. */
@Composable
fun OverviewScreen(onBack: () -> Unit, onOpenBank: () -> Unit, onOpenPay: () -> Unit, onOpenCard: () -> Unit, onOpenLoans: () -> Unit) {
    BackHandler(onBack = onBack)
    var overview by remember { mutableStateOf<OverviewResponse?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { try { overview = NetworkClient.apiService.getOverview() } catch (_: Exception) { failed = true } }

    Column(Modifier.fillMaxSize().background(Ids.colors.background)) {
        ProtoTopBar("My assets", onBack)
        val o = overview
        if (o == null) {
            if (failed) Text("Couldn't load your assets. Try again in a moment.", color = Ids.colors.textSecondary, fontSize = 14.sp, modifier = Modifier.padding(24.dp))
            else Box(Modifier.padding(24.dp)) { SkeletonBlock() }
            return@Column
        }
        val main = o.accounts.filter { it.type == "MAIN" }
        val pockets = o.accounts.filter { it.type != "MAIN" }
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 8.dp)) {
                    Text("Net worth in itunda", color = Ids.colors.textSecondary, fontSize = 14.sp, lineHeight = 20.sp)
                    Text("${rwf(o.netWorth)} RWF", color = Ids.colors.textPrimary, fontSize = 44.sp, lineHeight = 52.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp)
                    Text("Assets ${rwf(o.netWorth)} RWF", color = Ids.colors.textSecondary, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }
            item { AssetsSection("Accounts") {
                AssetRow("bank", "itunda Bank", "Main account", rwf(main.fold(BigDecimal.ZERO) { a, x -> a + x.balance }), onClick = onOpenBank)
                AssetRow("wallet", "itunda Pay", "Everyday payments", rwf(o.points.payMoneyBalance), onClick = onOpenPay)
                AssetRow("wallet", "Pockets", "${pockets.size} pockets", rwf(pockets.fold(BigDecimal.ZERO) { a, x -> a + x.balance }), onClick = onOpenBank)
            } }
            item { AssetsSection("Cards") {
                AssetRow("wallet", "itunda card", if (o.cards.hasCard) "•••• ${o.cards.last4 ?: ""}" else "Not issued yet", null, onClick = onOpenCard)
            } }
            item { AssetsSection("Loans") {
                AssetRow("wallet", if (o.loans.activeCount > 0) "${o.loans.activeCount} active loans" else "No active loans", if (o.loans.activeCount > 0) "${rwf(o.loans.totalOutstanding)} RWF outstanding" else "See what you can borrow", null, onClick = onOpenLoans)
            } }
            item { AssetsSection(null) {
                AssetRow("bank", "Open an account", "Savings, deposit, investments, card", null, onClick = onOpenBank)
            } }
        }
    }
}

@Composable
private fun AssetsSection(title: String?, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = if (title != null) 28.dp else 8.dp)) {
        if (title != null) Text(title, color = Ids.colors.textPrimary, fontSize = 18.sp, lineHeight = 26.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp))
        content()
    }
}

@Composable
private fun AssetRow(art: String, title: String, subtitle: String, amount: String?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).clickable(onClick = onClick).padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(Ids.colors.surfaceSoft), contentAlignment = Alignment.Center) { ItundaArt(art, ItundaArtSet.Proto, 31.dp, fit = true) }
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            Text(title, color = Ids.colors.textPrimary, fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Ids.colors.textSecondary, fontSize = 14.sp, lineHeight = 21.sp)
        }
        if (amount != null) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(amount, color = Ids.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text("RWF", color = Ids.colors.textTertiary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        } else androidx.compose.material3.Icon(IdsIcons.ChevronRight, contentDescription = null, tint = Ids.colors.textTertiary, modifier = Modifier.size(16.dp))
    }
}
