package rw.itunda.feature.home.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.ItundaArt
import rw.itunda.core.designsystem.components.ItundaArtSet
import rw.itunda.core.designsystem.theme.Ids

// Prototype v1038 Home pieces: "Bank >" header, accounts card, invite banner, rows, sections.

@Composable
internal fun HomeProtoHeader(hasUnread: Boolean, onBank: () -> Unit, onPay: () -> Unit, onBell: () -> Unit, bellLabel: String) {
    Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.clickable(onClick = onBank), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Icon(androidx.compose.ui.res.painterResource(rw.itunda.core.designsystem.R.drawable.itunda_mark), null, Modifier.size(28.dp), tint = Ids.colors.textTertiary.copy(alpha = 0.6f))
            Spacer(Modifier.width(6.dp))
            Text("Bank", color = Ids.colors.textTertiary, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            ItundaArt("chev", ItundaArtSet.Line, 16.dp, tint = Ids.colors.textTertiary)
        }
        Spacer(Modifier.weight(1f))
        Box(Modifier.size(44.dp).clickable(onClick = onPay), contentAlignment = Alignment.Center) { PayScanGlyph(26.dp, Ids.colors.textPrimary) }
        Box(Modifier.size(44.dp).clickable(onClick = onBell).semantics { contentDescription = bellLabel }, contentAlignment = Alignment.Center) {
            ItundaArt("bell", ItundaArtSet.Line, 24.dp, tint = Ids.colors.textPrimary)
            if (hasUnread) Box(Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 8.dp).size(8.dp).clip(CircleShape).background(Ids.colors.danger))
        }
    }
}

@Composable
private fun HomePill(label: String, onClick: () -> Unit) {
    Box(
        Modifier.height(44.dp).clip(RoundedCornerShape(14.dp)).background(Ids.colors.brand.copy(alpha = 0.16f)).clickable(onClick = onClick).padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = Ids.colors.textBrand, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun HomeBalanceRow(art: String, amount: Double, label: String, extra: String?, action: String, onOpen: () -> Unit, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(16.dp)).background(Ids.colors.brand.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) { if (art == "bank") androidx.compose.material3.Icon(androidx.compose.ui.res.painterResource(rw.itunda.core.designsystem.R.drawable.itunda_mark), null, Modifier.size(26.dp), tint = Ids.colors.brand) else ItundaArt(art, ItundaArtSet.Proto, 28.dp) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(homeRwf(amount), color = Ids.colors.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                Text(" RWF", color = Ids.colors.textSecondary, fontSize = 14.sp, modifier = Modifier.padding(bottom = 2.dp))
            }
            Row { Text(label, color = Ids.colors.textSecondary, fontSize = 14.sp); if (extra != null) Text(" $extra", color = Ids.colors.textBrand, fontSize = 14.sp) }
        }
        HomePill(action, onAction)
    }
}

@Composable
internal fun HomeAccountsCard(
    bank: Double, pay: Double, interestDays: Int?, onBank: () -> Unit, onPay: () -> Unit, onSend: () -> Unit, onAdd: () -> Unit,
    onBills: () -> Unit, onCard: () -> Unit, onSavings: () -> Unit, onSeeAll: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp).clip(RoundedCornerShape(28.dp))
            .background(Brush.verticalGradient(listOf(Ids.colors.brand.copy(alpha = 0.18f), Ids.colors.surface))).padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        HomeBalanceRow("bank", bank, "itunda Bank", interestDays?.let { "+interest $it" }, "Send", onBank, onSend)
        HomeBalanceRow("wallet", pay, "itunda Pay", null, "Add", onPay, onAdd)
        Box(Modifier.fillMaxWidth().padding(horizontal = 4.dp).height(1.dp).background(Ids.colors.divider))
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Bills", Modifier.clickable(onClick = onBills), color = Ids.colors.textPrimary, fontSize = 16.sp)
            Text("Card", Modifier.clickable(onClick = onCard), color = Ids.colors.textPrimary, fontSize = 16.sp)
            Text("Savings", Modifier.clickable(onClick = onSavings), color = Ids.colors.textSecondary, fontSize = 16.sp)
            Spacer(Modifier.weight(1f))
            Text("See all", Modifier.clickable(onClick = onSeeAll), color = Ids.colors.textSecondary, fontSize = 16.sp)
        }
    }
}

@Composable
internal fun HomeInviteBanner(onInvite: () -> Unit, onDismiss: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).clickable(onClick = onInvite), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(Ids.colors.brand.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) { ItundaArt("gift", ItundaArtSet.Proto, 30.dp) }
            Spacer(Modifier.width(14.dp))
            Text(androidx.compose.ui.text.buildAnnotatedString {
                append("Invite friends, get ")
                pushStyle(androidx.compose.ui.text.SpanStyle(color = Ids.colors.textBrand)); append("10,000 RWF"); pop()
            }, color = Ids.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Box(Modifier.size(44.dp).clickable(onClick = onDismiss).semantics { contentDescription = "Dismiss" }, contentAlignment = Alignment.Center) {
            Text("✕", color = Ids.colors.textPrimary, fontSize = 14.sp)
        }
    }
}

@Composable
internal fun HomeRow(art: String, title: String, subtitle: String, action: String, onAction: () -> Unit, artSet: ItundaArtSet = ItundaArtSet.Proto, artTint: Color? = null, amountBelow: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(16.dp)).background(Ids.colors.brand.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) { ItundaArt(art, artSet, 28.dp, tint = artTint) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            if (amountBelow) {
                Text(title, color = Ids.colors.textSecondary, fontSize = 14.sp)
                Text(subtitle, color = Ids.colors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            } else {
                Text(title, color = Ids.colors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, color = Ids.colors.textSecondary, fontSize = 14.sp)
            }
        }
        HomePill(action, onAction)
    }
}

@Composable
internal fun HomeSectionHeader(title: String, action: String?, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 28.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), color = Ids.colors.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        if (action != null) Text(action, Modifier.clickable(onClick = onAction), color = Ids.colors.textSecondary, fontSize = 15.sp)
    }
}

@Composable
internal fun HomeNavRow(art: String, title: String, subtitle: String, onClick: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 24.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(16.dp)).background(Ids.colors.surfaceSoft), contentAlignment = Alignment.Center) { ItundaArt(art, ItundaArtSet.Proto, 28.dp) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Ids.colors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Ids.colors.textSecondary, fontSize = 14.sp)
        }
        if (onClick != null) ItundaArt("chev", ItundaArtSet.Line, 16.dp, tint = Ids.colors.textTertiary)
    }
}

@Composable
internal fun HomeActivityRow(art: String, title: String, subtitle: String, amount: String, earned: String?) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(16.dp)).background(Ids.colors.surfaceSoft), contentAlignment = Alignment.Center) { ItundaArt(art, ItundaArtSet.Proto, 28.dp) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Ids.colors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Ids.colors.textSecondary, fontSize = 14.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(amount, color = Ids.colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            if (earned != null) Text(earned, color = Ids.colors.textBrand, fontSize = 13.sp)
        }
    }
}

@Composable
internal fun HomeSettingsButton(onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(top = 28.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.clip(RoundedCornerShape(24.dp)).background(Ids.colors.surfaceSoft).clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp)) {
            Text("Home settings", color = Ids.colors.textSecondary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
