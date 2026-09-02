package rw.itunda.app.ui

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.designsystem.theme.IdsIcons

// Extracted from ItundaAppScreen.kt (2026-09-02, slice 1 of the Banking Feature-module
// decomposition) -- these three composables (bottom nav bar, the "All" tab's search
// bar, and its top link row) are pure, stateless UI with no MainViewModel dependency,
// unlike the tab-content composables (HomeTab/BankHubScreen/PayTab/MenuScreen/MyTab)
// that DO take `viewModel: MainViewModel` directly. Same package, zero import changes
// anywhere else -- this is the safe "move pure leaves out first" step the MapsScreen.kt
// internal decomposition already established, done here before the harder
// MainViewModel-dependent pieces (which need a real state-holder design first, see
// this file's own header note and [[project_itunda_feature_isolation]]).

/**
 * Toss's real bottom nav is a flat, edge-to-edge bar with a hairline top
 * divider and real icons -- not a floating rounded pill with letter-glyph
 * placeholders, which is what this was before and read as an unfinished
 * wireframe rather than an actual app (feedback from comparing directly
 * against real Toss screenshots, 2026-07-10).
 */
@Composable
internal fun ItundaBottomBar(selectedTab: ItundaTab, onSelect: (ItundaTab) -> Unit) {
    Column {
        Divider(color = Ids.colors.divider, thickness = 0.5.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // Real fix (2026-08-24): was Ids.colors.surface, which diverges from
                // Ids.colors.background in dark mode (0x202027 vs 0x17171C), making
                // the bottom bar visibly stand out from the page -- real Toss/Coupang
                // keep every chrome bar the same color as the content underneath it.
                .background(Ids.colors.background),
                // Real fix (2026-08-25, direct user comparison against the live real
                // Toss app on the same physical device -- uiautomator-measured):
                // this Row's own top/bottom padding used to stack with each tab
                // Column's own padding below, a real "padding on padding" bug that
                // alone added ~10dp of dead space no design called for. Toss's real
                // bar (measured the same way, same device) has exactly one padding
                // layer -- so does this one now.
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ItundaTab.entries.forEach { tab ->
                val selected = tab == selectedTab
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .pressScaleClickable { onSelect(tab) }
                        // Real fix (2026-08-25) -- matches Toss's own real measured
                        // ~8dp top / ~8dp bottom inset exactly (this Column is now the
                        // only padding layer, see the Row's own comment above).
                        .padding(top = 8.dp, bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label,
                        modifier = Modifier.size(24.dp),
                        tint = if (selected) Ids.colors.brand else Ids.colors.textTertiary
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = tab.label,
                        fontSize = 11.sp,
                        // Real fix (2026-08-25, same live-device comparison) -- Compose's
                        // Text defaults to the font's full built-in line-height metrics
                        // (~24dp measured for this 11sp label), well beyond the glyphs'
                        // own ink; Toss's real native-Android label renders the same text
                        // at ~14dp. lineHeight + includeFontPadding=false + a trimmed
                        // LineHeightStyle is Compose's own documented fix for exactly
                        // this gap, not a made-up workaround.
                        lineHeight = 13.sp,
                        style = LocalTextStyle.current.copy(
                            platformStyle = PlatformTextStyle(includeFontPadding = false),
                            lineHeightStyle = LineHeightStyle(
                                alignment = LineHeightStyle.Alignment.Center,
                                trim = LineHeightStyle.Trim.Both,
                            ),
                        ),
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (selected) Ids.colors.brand else Ids.colors.textTertiary
                    )
                }
            }
        }
    }
}

// Real fix (2026-08-10): this was pure decoration -- a Box with static text, no
// TextField, nothing typed into it ever did anything. Worse than no search bar at
// all: it promised a feature that wasn't there. Now backed by real state (see
// MenuScreen's own menuSearchQuery) that filters every FlatSection row by title --
// the ~17-category, 60+-row menu below this bar is a "find X" problem as much as a
// "browse by category" one.
@Composable
internal fun SearchBar(query: String, onQueryChange: (String) -> Unit, placeholder: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Ids.colors.surfaceSoft)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Icon(IdsIcons.Search, contentDescription = null, tint = Ids.colors.textTertiary, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(placeholder, color = Ids.colors.textSecondary, fontSize = 16.sp)
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(color = Ids.colors.textPrimary, fontSize = 16.sp),
                cursorBrush = SolidColor(Ids.colors.brand),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(22.dp)) {
                Icon(IdsIcons.Close, contentDescription = "Clear search", tint = Ids.colors.textTertiary, modifier = Modifier.size(16.dp))
            }
        }
    }
}

// CORRECTED 2026-08-12: an earlier pass here claimed "a text navbar ('ID | Support |
// Settings') is a website convention with no equivalent anywhere in real Toss" and
// replaced it with a bold username + single gear icon -- that claim was wrong,
// contradicted directly by a real user-provided screenshot of the actual Toss app's
// own All-tab header, which is exactly a 3-link text row: "Authentication | Help |
// Settings" (not "ID | Support | Settings" -- close but not the real labels either).
// No bold username shown on this specific screen in the real screenshot (that
// personalization lives on Home's own switcher header instead, a different real
// screenshot from the same batch, not duplicated here).
@Composable
internal fun AllTopBar(onOpenAuthentication: () -> Unit = {}, onOpenHelp: () -> Unit = {}, onOpenSettings: () -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Authentication",
            color = Ids.colors.textSecondary,
            fontSize = 15.sp,
            modifier = Modifier.pressScaleClickable(onClick = onOpenAuthentication),
        )
        androidx.compose.material3.VerticalDivider(
            modifier = Modifier.padding(horizontal = 10.dp).height(14.dp),
            color = Ids.colors.textTertiary,
        )
        Text(
            "Help",
            color = Ids.colors.textSecondary,
            fontSize = 15.sp,
            modifier = Modifier.pressScaleClickable(onClick = onOpenHelp),
        )
        androidx.compose.material3.VerticalDivider(
            modifier = Modifier.padding(horizontal = 10.dp).height(14.dp),
            color = Ids.colors.textTertiary,
        )
        Text(
            "Settings",
            color = Ids.colors.textSecondary,
            fontSize = 15.sp,
            modifier = Modifier.pressScaleClickable(onClick = onOpenSettings),
        )
    }
}
