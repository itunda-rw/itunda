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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.IdsButton
import rw.itunda.core.designsystem.components.ItundaArt
import rw.itunda.core.designsystem.components.ItundaArtSet
import rw.itunda.core.designsystem.components.IdsButtonSize
import rw.itunda.core.designsystem.components.IdsButtonVariant
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.AccentIndigo
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.designsystem.theme.IdsIcons

// Moved here from :app's ItundaAppFlatRows.kt (2026-09-02, Banking Feature-module
// decomposition slice 5) -- confirmed used only by BankHubScreen (this same module),
// so it moves alongside it rather than staying `internal` in a module BankHubScreen no
// longer lives in. FlatRow/FlatSection/TransactionDetailRow stayed in :app (used by
// other screens too).

internal data class ShellRow(
    val title: String,
    val subtitle: String,
    val action: String,
    val icon: ImageVector,
    val iconColor: Color = AccentIndigo,
    val onClick: (() -> Unit)? = null,
    val secondaryAction: String? = null,
    val onSecondaryClick: (() -> Unit)? = null,
    val caption: String = "",
    val art: String? = null,
    val badge: String? = null,
)

/** Prototype art tile for the hub's known rows; null falls back to the Material icon. */
private fun shellArtFor(title: String): String? = when {
    title.startsWith("Interest jar", true) -> "interest-jar"
    title.startsWith("26-week") -> "svc-26-week-savings"
    title.startsWith("31-day") -> "savings-31"
    title.startsWith("12-month") -> "deposit-12"
    title.contains("SACCO", true) -> "sacco"
    title.startsWith("Investments") -> "investments"
    title.startsWith("School fees") -> "school-fees"
    title.startsWith("Youth") -> "youth"
    title.startsWith("Group") -> "group-accounts"
    title.contains("Ikimina", true) -> "ikimina"
    title.startsWith("New savings goal") -> "savings-goal"
    title.startsWith("Round-up", true) || title.startsWith("Round up", true) -> "round-up"
    title.startsWith("Get a loan") -> "loan"
    title.startsWith("Student loan") -> "student-loan"
    title.startsWith("VUP") -> "vup"
    title.startsWith("Credit score", true) -> "credit-score"
    else -> null
}

@Composable
internal fun ShellSection(title: String, rows: List<ShellRow>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (title.isNotEmpty()) {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ids.colors.textPrimary)
            Spacer(modifier = Modifier.height(4.dp))
        }
        rows.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (row.onClick != null) Modifier.pressScaleClickable(onClick = row.onClick) else Modifier)
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val art = row.art ?: shellArtFor(row.title)
                if (art != null) {
                    Box(Modifier.size(40.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp)).background(Ids.colors.surfaceSoft), contentAlignment = Alignment.Center) {
                        if (art == "svc-26-week-savings") ItundaArt(art, ItundaArtSet.Orig, 26.dp) else ItundaArt(art, ItundaArtSet.Color, 26.dp)
                    }
                } else Box(modifier = Modifier.size(38.dp).clip(CircleShape).background(row.iconColor), contentAlignment = Alignment.Center) {
                    Icon(row.icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(row.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Ids.colors.textPrimary)
                        if (row.badge != null) Text(row.badge, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ids.colors.success, modifier = Modifier.padding(start = 8.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp)).background(Ids.colors.success.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 3.dp))
                    }
                    if (row.subtitle.isNotEmpty()) {
                        Text(row.subtitle, fontSize = 14.sp, color = Ids.colors.textSecondary)
                    }
                }
                if (row.action == ">") {
                    Icon(IdsIcons.ChevronRight, contentDescription = null, tint = Ids.colors.textTertiary)
                } else if (row.caption.isNotEmpty() || (row.action.isNotBlank() && row.action.any { it.isDigit() })) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(row.action, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Ids.colors.textSecondary)
                        if (row.caption.isNotEmpty()) Text(row.caption, fontSize = 12.sp, color = Ids.colors.textTertiary)
                    }
                    if (row.onClick != null) Icon(IdsIcons.ChevronRight, contentDescription = null, tint = Ids.colors.textTertiary)
                } else if (row.action.isNotBlank()) {
                    IdsButton(row.action, onClick = row.onClick ?: {}, variant = IdsButtonVariant.Tinted, size = IdsButtonSize.Small)
                }
                if (row.secondaryAction != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    IdsButton(row.secondaryAction, onClick = row.onSecondaryClick ?: {}, variant = IdsButtonVariant.Tinted, size = IdsButtonSize.Small)
                }
            }
        }
    }
}
