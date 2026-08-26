package rw.itunda.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Shield
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.BankCardChip
import rw.itunda.core.designsystem.components.PetalMark
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.itundaface.LockGlyph
import rw.itunda.core.designsystem.itundaface.MoneyBagGlyph
import rw.itunda.core.designsystem.theme.Ids

// Real Toss Bank "which color do you like?" issuance step (namu.wiki: 5 real named
// colorways; toss.tech's own engineering post on the picker's 3D touch-and-rotate
// interaction) -- direct user instruction 2026-08-27: "update itunda bank with all
// those cards designs allowing users to choose from those designs... that's how toss
// does it too". Picks from CardDesigns.ALL, itunda's own real front/back colorways
// validated in the standalone card-lineup design pass and already shipped identically
// in bank-mfe's CardExplainer.tsx. Front-only during picking, matching that same
// pass's own real-photo-sourced finding: the real card's front is color and chip,
// nothing else -- no fabricated printed number here either, same "fully masked, no
// card exists yet" reasoning the previous single-design mockup already established.
//
// Split out of CardScreen.kt (not left inline) since this screen's real need --
// interactive design selection driving both the mockup and the CTA label -- outgrew
// ProductExplainerScreen's static icon-slot shape (still real, still used as-is by
// YouthAccountScreen's own pre-open state; this doesn't touch that shared component).
@Composable
private fun FeatureRow(icon: @Composable () -> Unit, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Box(
            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Ids.colors.brand.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) { icon() }
        Spacer(Modifier.width(12.dp))
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ids.colors.textPrimary)
    }
}

@Composable
fun CardDesignPicker(busy: Boolean, onIssue: (design: String) -> Unit) {
    var selected by remember { mutableStateOf(CardDesigns.DEFAULT.id) }
    val design = CardDesigns.byId(selected)
    val lockupColor = if (design.frontLight) Color(0xFF191F28).copy(alpha = 0.5f) else Color.White.copy(alpha = 0.6f)

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            "Which finish do you like?", fontSize = 13.sp, fontWeight = FontWeight.Bold,
            color = Ids.colors.textSecondary, modifier = Modifier.padding(bottom = 14.dp),
        )

        Box(
            modifier = Modifier
                .size(width = 138.dp, height = 219.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(design.front)
                .then(if (design.frontLight) Modifier.border(1.dp, Color(0xFFE2E2DE), RoundedCornerShape(14.dp)) else Modifier),
        ) {
            // Diagonal sheen -- the same "flat color read as a card" fix applied to
            // every card-shaped visual in this app (see AccountCardCarousel.kt's own
            // doc comment).
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.18f), Color.Transparent))),
            )
            Box(modifier = Modifier.align(Alignment.TopStart).padding(start = 37.dp, top = 46.dp)) {
                BankCardChip(size = 30.dp)
            }
            Row(
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 18.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                PetalMark(size = 12.dp, color = lockupColor)
                Text("itunda bank", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = lockupColor)
            }
        }

        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CardDesigns.ALL.forEach { d ->
                val isSelected = d.id == selected
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .border(2.dp, if (isSelected) Ids.colors.brand else Color.Transparent, CircleShape)
                        .pressScaleClickable { selected = d.id },
                ) {
                    Row(Modifier.fillMaxWidth().fillMaxHeight().clip(CircleShape)) {
                        Box(Modifier.weight(1f).fillMaxHeight().background(d.front))
                        Box(Modifier.weight(1f).fillMaxHeight().background(d.back))
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Text("Your own itunda card, in seconds", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Ids.colors.textPrimary)
        Spacer(Modifier.height(6.dp))
        Text(
            "A real debit card for your itunda balance -- no paperwork, no waiting.",
            fontSize = 13.sp, color = Ids.colors.textSecondary,
        )
        Spacer(Modifier.height(24.dp))

        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            FeatureRow({ MoneyBagGlyph(size = 20.dp) }, "No annual fee, ever")
            FeatureRow({ Icon(Icons.Outlined.Bolt, contentDescription = null, tint = Ids.colors.brand, modifier = Modifier.size(20.dp)) }, "Issued instantly in the app -- no branch visit")
            FeatureRow({ Icon(Icons.Outlined.Shield, contentDescription = null, tint = Ids.colors.brand, modifier = Modifier.size(20.dp)) }, "Set your own daily and monthly spend limits")
            FeatureRow({ LockGlyph(size = 20.dp) }, "One-tap freeze if it's ever lost")
        }
        Spacer(Modifier.height(20.dp))

        Box(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                .background(Ids.colors.brand).pressScaleClickable(enabled = !busy, onClick = { onIssue(selected) })
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (busy) "Issuing…" else "Get your ${design.displayName} card",
                color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp,
            )
        }
    }
}
