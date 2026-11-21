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
        if (title.isNotEmpty()) HubHeading(title)
        rows.forEach { row ->
            val art = row.art?.let { HubArt(it, ItundaArtSet.Color) } ?: hubArtFor(row.title)
            val value = when {
                row.action == ">" || row.action == "+" -> ""
                row.action.isNotBlank() && (row.caption.isNotEmpty() || row.action.any { it.isDigit() } || row.action.length <= 12) -> row.action
                else -> ""
            }
            // Prototype shows a chevron after the value on drill-down rows, but not on pure rate rows (jar, 26-week, 31-day, 12-month, SACCO).
            val rateRow = row.caption.isNotEmpty() || row.title.startsWith("31-day") || (row.title.startsWith("Student") && value.isNotEmpty())
            HubRow(art, row.title, value, row.caption, row.badge, chevron = row.onClick != null && !rateRow, onClick = row.onClick)
        }
    }
}
