package rw.itunda.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
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
internal fun ItundaBottomBar(selectedTab: ItundaTab, onSelect: (ItundaTab) -> Unit, messagesUnreadCount: Long = 0) {
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
                    Box {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.label,
                            modifier = Modifier.size(24.dp),
                            tint = if (selected) Ids.colors.brand else Ids.colors.textTertiary
                        )
                        // Real cross-platform-parity gap found live (2026-09-13) -- web
                        // already shows this exact numeric badge (BankDashboard.tsx,
                        // capped "99+") on the Messages tab; Android had none at all.
                        if (tab == ItundaTab.Messages && messagesUnreadCount > 0) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 7.dp, y = (-4).dp)
                                    .heightIn(min = 16.dp)
                                    .defaultMinSize(minWidth = 16.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(Ids.colors.danger)
                                    .padding(horizontal = 3.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = if (messagesUnreadCount > 99) "99+" else messagesUnreadCount.toString(),
                                    color = androidx.compose.ui.graphics.Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = 10.sp,
                                )
                            }
                        }
                    }
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

