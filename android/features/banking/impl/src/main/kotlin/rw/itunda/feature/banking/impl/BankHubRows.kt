package rw.itunda.feature.banking.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.ItundaArt
import rw.itunda.core.designsystem.components.ItundaArtSet
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids

/** Art for a hub row: prototype Proto-set file name, or an Orig-set name where the prototype art has no Proto file. */
internal class HubArt(val name: String, val set: ItundaArtSet = ItundaArtSet.Proto)

internal fun hubArtFor(title: String): HubArt? = when {
    title.startsWith("Interest jar", true) -> HubArt("interest-jar")
    title.startsWith("26-week") -> HubArt("svc-26-week-savings", ItundaArtSet.Orig)
    title.startsWith("31-day") -> HubArt("31-day-savings")
    title.startsWith("12-month") -> HubArt("12-month-deposit")
    title.contains("SACCO", true) -> HubArt("sacco-shares")
    title.startsWith("Investments") -> HubArt("investments")
    title.startsWith("School fees") -> HubArt("school-fees")
    title.startsWith("New savings goal") -> HubArt("new-savings-goal")
    title.startsWith("Round-up", true) || title.startsWith("Round up", true) -> HubArt("round-up-saving")
    title.startsWith("Group") -> HubArt("group-accounts")
    title.startsWith("Youth") -> HubArt("svc-youth-account", ItundaArtSet.Orig)
    title.contains("Ikimina", true) -> HubArt("ikimina")
    title.startsWith("Get a loan") -> HubArt("get-a-loan")
    title.startsWith("Harvest advance") -> HubArt("harvest-advance")
    title.startsWith("Student loan") -> HubArt("student-loan")
    title.startsWith("VUP") -> HubArt("vup-financial-services")
    title.startsWith("Moto") -> HubArt("moto-taxi-ownership")
    title.startsWith("Move to itunda Pay") -> HubArt("move-to-itunda-pay")
    title.startsWith("Send to a bank") -> HubArt("send-to-a-bank-account")
    title.startsWith("Payees") -> HubArt("payees")
    title.startsWith("Scheduled") -> HubArt("scheduled-transfers")
    title.startsWith("Statements") -> HubArt("statements")
    title.startsWith("Spending") -> HubArt("spending")
    title.startsWith("Budget") -> HubArt("budget")
    title.startsWith("Limits") -> HubArt("limits-alerts")
    title.startsWith("Account details") -> HubArt("account-details")
    title.startsWith("Documents") -> HubArt("documents-letters")
    title.startsWith("Report a problem") -> HubArt("report-a-problem")
    title.startsWith("My credit score", true) -> HubArt("my-credit-score")
    title == "Card" -> HubArt("card")
    title == "Pockets" -> HubArt("pockets")
    title.startsWith("Auto-transfer") -> HubArt("auto-transfer")
    else -> null
}

/** Prototype `.b7r`: 60dp row, 40dp slot (r14 soft tile with 26dp art only on the three top rows, otherwise bare 32dp art), 17sp/600 title at x=74, optional value (+ caption) and 18dp chevron. */
@Composable
internal fun HubRow(
    art: HubArt?, title: String, value: String = "", caption: String = "", badge: String? = null, chevron: Boolean = false, tile: Boolean = false, onClick: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().height(60.dp).then(if (onClick != null) Modifier.pressScaleClickable(onClick = onClick) else Modifier).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).then(if (tile) Modifier.clip(RoundedCornerShape(14.dp)).background(Ids.colors.surfaceSoft) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            if (art != null) ItundaArt(art.name, art.set, if (tile) 26.dp else 32.dp, fit = art.set == ItundaArtSet.Proto)
        }
        Row(Modifier.weight(1f).padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Ids.colors.textPrimary, fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp, maxLines = 1)
            if (badge != null) Box(Modifier.padding(start = 8.dp).size(48.dp, 24.dp).clip(RoundedCornerShape(12.dp)).background(Ids.colors.success.copy(alpha = 0.16f)), Alignment.Center) {
                Text(badge, color = Ids.colors.success, fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (value.isNotEmpty()) Column(horizontalAlignment = Alignment.End) {
            Text(value, color = Ids.colors.textTertiary, fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.3).sp, textAlign = TextAlign.End)
            if (caption.isNotEmpty()) Text(caption, color = Ids.colors.textTertiary, fontSize = 13.sp, lineHeight = 18.sp, textAlign = TextAlign.End)
        }
        if (chevron) {
            Spacer(Modifier.size(if (value.isNotEmpty()) 14.dp else 0.dp))
            ItundaArt("chev", ItundaArtSet.Line, 18.dp, tint = Ids.colors.textTertiary)
        }
    }
}

/** Prototype `.b7h`: 22sp/700 section heading with 32dp above and 6dp below. */
@Composable
internal fun HubHeading(title: String) {
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, top = 32.dp, bottom = 6.dp)) {
        Text(title, color = Ids.colors.textPrimary, fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)
    }
}
