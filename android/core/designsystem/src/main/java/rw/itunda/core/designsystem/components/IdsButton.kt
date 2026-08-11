package rw.itunda.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
    // Real Toss reference (user-provided, 2026-08-03): the actual Toss Home wallet
    // card's own two buttons are "+ 채우기" / "↗ 보내기", a leading glyph before the
    // label on both -- not a decoration specific to those two, real TDS buttons take
    // an optional leading icon generally. Default null preserves every existing call
    // site (this app's own icon-less "Send"/"Log in"/etc. buttons) unchanged.
    icon: ImageVector? = null,
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
        shape = RoundedCornerShape(if (size == IdsButtonSize.Small) 10.dp else 12.dp),
        contentPadding = horizontalPadding,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = Ids.colors.divider,
            disabledContentColor = Ids.colors.textTertiary
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
