package rw.itunda.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.designsystem.theme.IdsColors
import rw.itunda.core.designsystem.theme.IdsTypography

/**
 * Real fix, 2026-07-21, grounded in Toss's own published account of this exact
 * failure mode ("디자인 시스템 다시 생각해보기" / "Rethinking Design System",
 * toss.tech/article/rethinking-design-system): a design system that only offers one
 * rigid shape stops being adopted -- teams fork/rewrite locally instead, and the
 * fork silently drifts from the real tokens over time. That's not hypothetical here:
 * before this fix, `ItundaAppScreen.kt` had three separate local button composables
 * (`PrimaryAction`, `SmallBlueButton`, `TopIconButton`) that never touched this file
 * at all -- and one of them, `PrimaryAction`'s "unfilled" variant, hardcoded
 * `Color(0xFF1F3053)` as a raw literal that happened to equal
 * `IdsSemanticColors.kt`'s real `pressed` dark-mode token at the moment it was
 * written, with nothing keeping the two in sync if the token ever changes.
 *
 * Matches Toss's own real solution shape: a single "Flat," props-based API (not a
 * Compound/sub-component API -- real TDS docs describe button variants as props,
 * not slots, unlike Card) that covers every real shape found in this codebase
 * through `variant`/`size` instead of a new one-off composable per screen.
 */
enum class IdsButtonVariant { Filled, Tinted }
enum class IdsButtonSize { Large, Medium, Small }

@Composable
fun IdsButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    variant: IdsButtonVariant = IdsButtonVariant.Filled,
    size: IdsButtonSize = IdsButtonSize.Large,
    // Real Toss reference (user-provided, 2026-08-03): the actual Toss Home account
    // card's own two buttons are "+ 채우기" / "↗ 보내기", a leading glyph before the
    // label on both -- not a decoration specific to those two, real TDS buttons take
    // an optional leading icon generally. Default null preserves every existing call
    // site (this app's own icon-less "Send"/"Log in"/etc. buttons) unchanged.
    icon: ImageVector? = null,
    // Real keyboard-docking reference (2026-08-12) -- see
    // IdsKeyboardDockedButton's own doc comment. Null (the default) preserves this
    // button's own normal rounded corners for every existing call site; only a
    // caller that specifically wants the flush, sharp-cornered "docked to the
    // keyboard" look passes one in.
    shape: androidx.compose.ui.graphics.Shape? = null,
) {
    val heightDp = when (size) {
        IdsButtonSize.Large -> 56.dp
        IdsButtonSize.Medium -> 48.dp
        IdsButtonSize.Small -> 36.dp
    }
    val horizontalPadding = when (size) {
        IdsButtonSize.Large, IdsButtonSize.Medium -> ButtonDefaults.ContentPadding
        IdsButtonSize.Small -> androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 0.dp)
    }
    val widthModifier = if (size == IdsButtonSize.Large) Modifier.fillMaxWidth() else Modifier
    val (containerColor, contentColor) = when (variant) {
        IdsButtonVariant.Filled -> Ids.colors.brand to IdsColors.White
        IdsButtonVariant.Tinted -> Ids.colors.pressed to Ids.colors.textBrand
    }
    // Real Toss disabled-CTA reference (2026-08-12, direct user screenshot: a bottom
    // "Confirm" bar that's a dim TINT of the same brand blue while its required input
    // is empty, turning fully solid the moment it's valid) -- a neutral gray disabled
    // state (the old unconditional Ids.colors.divider below) reads as "broken/
    // unavailable" rather than "not ready yet, same action, just waiting on you."
    // Only applies to Filled -- Tinted's own disabled state (a secondary style, not
    // shown in the reference) keeps the existing neutral fallback.
    val (disabledContainerColor, disabledContentColor) = when (variant) {
        IdsButtonVariant.Filled -> Ids.colors.brand.copy(alpha = 0.35f) to IdsColors.White
        IdsButtonVariant.Tinted -> Ids.colors.divider to Ids.colors.textTertiary
    }
    // Real Toss micro-interaction reference (2026-08-11) -- "시각적 신호가 탭이 발생하는
    //정확한 순간에 햅틱/사용자 액션과 동기화되어야 한다" (visual cues synchronized
    // precisely with the tap): a subtle press-scale on every primary button in the app,
    // not just the transfer-success moment, so the whole app reads as tactile/alive
    // rather than one screen having motion and everything else staying flat static.
    // Applied here (not per call site) so it's automatically consistent everywhere
    // IdsButton is already used, matching this file's own "single Flat props-based API"
    // design-system principle instead of a one-off per screen.
    val interactionSource = remember { MutableInteractionSource() }
    val pressScale = rememberPressScale(interactionSource, enabled)

    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = modifier
            .then(widthModifier)
            .scale(pressScale)
            .height(heightDp),
        shape = shape ?: RoundedCornerShape(if (size == IdsButtonSize.Small) 10.dp else 12.dp),
        contentPadding = horizontalPadding,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = disabledContainerColor,
            disabledContentColor = disabledContentColor
        )
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(if (size == IdsButtonSize.Small) 14.dp else 18.dp))
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(6.dp))
        }
        Text(
            text = text,
            style = if (size == IdsButtonSize.Small) IdsTypography.Body2 else IdsTypography.Button
        )
    }
}

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

/** The icon-only circular shape `TopIconButton` used to duplicate locally in ItundaAppScreen.kt. */
@Composable
fun IdsIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressScale = rememberPressScale(interactionSource)
    Box(
        modifier = modifier
            .size(Ids.layout.minTouchTarget)
            .scale(pressScale)
            .clickable(interactionSource = interactionSource, indication = LocalIndication.current, onClick = onClick)
            .background(Ids.colors.surfaceSoft, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(20.dp), tint = Ids.colors.textPrimary)
    }
}
