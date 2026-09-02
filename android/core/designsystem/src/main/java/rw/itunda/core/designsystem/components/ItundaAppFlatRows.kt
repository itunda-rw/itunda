package rw.itunda.core.designsystem.components

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.rememberPressScale
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.designsystem.theme.IdsIcons
import rw.itunda.core.designsystem.theme.AccentIndigo

// Extracted from ItundaAppScreen.kt (2026-09-02, slice 1 of the Banking Feature-module
// decomposition, see ItundaAppSharedUi.kt's own header note for the full account) --
// the two real Toss list-row shapes (a promotional "shell" row with an icon badge and
// action button, and the flat product-catalog row with an optional itundaface glyph)
// plus the small labeled-value row TransactionDetailScreen uses. All three are pure,
// stateless UI with no MainViewModel dependency. Kept `internal` (not `private`) since
// BankHubScreen/TransactionDetailScreen -- which DO need MainViewModel and stay in
// ItundaAppScreen.kt for now -- still construct/call these directly. Same package,
// zero import changes anywhere else.

@Composable
fun TransactionDetailRow(label: String, value: String) {
    // Real fix, applied proactively (this exact "two unweighted Texts in a Row can
    // overflow into each other" bug was just caught live in AccountLedgerRow above
    // this same session -- see that fix's own doc comment): the value gets
    // weight(1f) so a long description wraps within its own bounded space instead
    // of running past the label.
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = Ids.colors.textSecondary, fontSize = 14.sp)
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            value, color = Ids.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

// ShellRow/ShellSection moved to :features:banking:impl/BankHubShellRow.kt (2026-09-02,
// Banking Feature-module decomposition slice 5) -- confirmed used only by
// BankHubScreen, which moved to the same module in the same slice.

data class FlatRow(
    val title: String,
    val subtitle: String? = null,
    val trailing: String? = null,
    val trailingIsLink: Boolean = false,
    val icon: ImageVector? = null,
    val iconColor: Color = AccentIndigo,
    // Real Toss icon-language fix (2026-08-26, direct user screenshot comparison
    // against Toss's own All-tab): Toss never wraps its category icons in a
    // colored background square -- every glyph in TossFace is itself a distinct,
    // full-color illustration, so no chip is needed to differentiate rows. Takes
    // priority over icon/iconColor below when set; itundaface only covers a real
    // subset of this screen's ~90 rows today (see itundaface's own file doc
    // comments for sourcing), so rows without a real glyph yet keep the
    // Material-icon-in-a-square fallback rather than getting a fabricated one.
    val glyph: (@Composable () -> Unit)? = null,
    val showChevron: Boolean = false,
    // Real dead-tap fix (item 247 follow-up, docs/DESIGN_REFERENCES.md §14 recommendation
    // #2 -- Simplicity24's "사용자의 실수" lesson on misleading affordances): previously
    // defaulted to a no-op `{}`, so every row with showChevron = true but no real
    // destination (Notifications/Privacy policy/Terms & consent/FAQ/Live chat/Call
    // support/Announcements -- confirmed via SupportScreen.kt's own 2026-07-22 doc
    // comment that no backend exists for any of these) still consumed the tap silently
    // via FlatSection's unconditional .clickable() below, exactly the "looks tappable,
    // does nothing" bug already found and fixed once this session in bank-mfe's
    // QuickActions. Nullable now, so a row can only render as tappable when it truly is.
    val onClick: (() -> Unit)? = null
)

/**
 * The real Toss list pattern, matched directly against the reference
 * screenshots (user-provided, 2026-07-10) of 갈아타기/신용카드/체크카드/서비스/
 * 외화/목돈굴리기/연금/대출: a bold white section header (not a small gray
 * label), then plain rows with NO divider between them and NO card
 * background -- only a gap between different sections. Every row in that
 * product-list pattern carries a small colorful square icon (never a
 * chevron); a right-aligned value in brand blue appears only when there's
 * a real number/status to show (interest rate, discount). A second,
 * separate pattern exists for legal/settings lists (알림 및 동의, 고객센터):
 * no icon at all, plain chevron on the right -- selected per-row via
 * showChevron since both patterns can appear in the same screen.
 */
@Composable
fun FlatSection(title: String, rows: List<FlatRow>) {
    Column {
        Text(
            title,
            color = Ids.colors.textPrimary,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        rows.forEach { row -> FlatSectionRow(row) }
    }
}

// Extracted from FlatSection's own forEach body (2026-08-11) so the press-scale
// state below gets its own composable slot per row instead of sharing one across a
// loop. Real Toss micro-interaction reference -- see IdsButton.kt's own doc comment;
// this is the single most-tapped row shape in the app (every Home/Bank hub/Menu
// list uses it) and had zero press feedback before this.
@Composable
private fun FlatSectionRow(row: FlatRow) {
    val onClick = row.onClick
    val interactionSource = remember { MutableInteractionSource() }
    val pressScale = if (onClick != null) rememberPressScale(interactionSource) else 1f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(pressScale)
            .then(if (onClick != null) Modifier.clickable(interactionSource = interactionSource, indication = LocalIndication.current, onClick = onClick) else Modifier)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            if (row.glyph != null) {
                Box(modifier = Modifier.size(34.dp), contentAlignment = Alignment.Center) { row.glyph.invoke() }
                Spacer(modifier = Modifier.width(14.dp))
            } else if (row.icon != null) {
                Box(
                    modifier = Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(row.iconColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(row.icon, contentDescription = null, modifier = Modifier.size(19.dp), tint = Color.White)
                }
                Spacer(modifier = Modifier.width(14.dp))
            }
            Column {
                Text(row.title, color = Ids.colors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                if (row.subtitle != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(row.subtitle, color = Ids.colors.textTertiary, fontSize = 13.sp)
                }
            }
        }
        if (row.trailing != null) {
            Text(
                row.trailing,
                color = if (row.trailingIsLink) Ids.colors.brand else Ids.colors.textSecondary,
                fontSize = 15.sp,
                fontWeight = if (row.trailingIsLink) FontWeight.SemiBold else FontWeight.Normal
            )
        } else if (row.showChevron && onClick != null) {
            Icon(IdsIcons.ChevronRight, contentDescription = null, tint = Ids.colors.textTertiary)
        }
    }
}
