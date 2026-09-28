package rw.itunda.core.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import rw.itunda.core.designsystem.theme.Ids

// Real Toss reference (2026-08-12, direct user screenshot: "Enter workplace name" ->
// a single field + bottom "Confirm" bar): with the keyboard hidden, the bar is a
// normal rounded, inset button like everywhere else in the app; the moment the
// keyboard opens, it loses its rounding and side margins entirely and becomes a
// flush, edge-to-edge bar sitting directly on top of the keyboard -- visually
// "docking" into the keyboard's own flat surface instead of floating above it as a
// separate rounded card. A neutral inset+rounded button butted up against a hard
// flat keyboard edge reads as slightly disconnected from it; going flush removes
// that seam. Wraps IdsButton rather than baking this into it directly -- most real
// IdsButton call sites (inline in a card, a row, a non-keyboard screen) should never
// pick up this behavior automatically, only a screen that explicitly opts in by
// using this composable for its own bottom "single field -> confirm" pattern.
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IdsKeyboardDockedButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    horizontalPadding: androidx.compose.ui.unit.Dp = Ids.layout.screenHorizontal,
) {
    val imeVisible = WindowInsets.isImeVisible
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = if (imeVisible) 0.dp else horizontalPadding),
    ) {
        IdsButton(
            text = text,
            onClick = onClick,
            enabled = enabled,
            shape = if (imeVisible) androidx.compose.ui.graphics.RectangleShape else null,
        )
    }
}

// Real Toss FixedBottomCTA reference (2026-08-26, direct user follow-up against real
// Toss Bank product-intro screenshots: "toss always keep those confirm buttons in
// bottom in many cases regardless of contents... when contents are many users needs to
// scroll to see other contents they still see that buttons to click anytime they make
// up their mind"). Same real gap iOS had (see CoreDesignSystem's own FixedBottomCTA):
// itunda's own single-screen "create X" forms (Grow31SavingsScreen.kt's
// Grow31CreateContent, WeeklySavingsScreen.kt's WeeklySavingsCreateContent) put their
// primary button as the LAST item in a plain non-scrolling Column instead -- on a short
// screen the button just sits wherever the last field ends, and on a tall form or a
// small device there's no scroll at all, so overflow content is silently clipped
// instead of the button ever moving. Web's `FullScreenFlow` (BankDashboard.tsx) already
// solved this correctly: content scrolls in the available space, the CTA stays fixed
// below it. This ports that exact shape for Compose.
@Composable
fun FixedBottomCta(
    content: @Composable ColumnScope.() -> Unit,
    cta: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Ids.layout.screenHorizontal, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
        Divider(color = Ids.colors.divider, thickness = 0.5.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Ids.colors.background)
                .padding(horizontal = Ids.layout.screenHorizontal, vertical = 12.dp),
        ) {
            cta()
        }
    }
}
