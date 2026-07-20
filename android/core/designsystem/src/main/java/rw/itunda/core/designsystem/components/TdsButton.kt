package rw.itunda.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import rw.itunda.core.designsystem.theme.Tds
import rw.itunda.core.designsystem.theme.TdsColors
import rw.itunda.core.designsystem.theme.TdsTypography

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
 * `TdsSemanticColors.kt`'s real `pressed` dark-mode token at the moment it was
 * written, with nothing keeping the two in sync if the token ever changes.
 *
 * Matches Toss's own real solution shape: a single "Flat," props-based API (not a
 * Compound/sub-component API -- real TDS docs describe button variants as props,
 * not slots, unlike Card) that covers every real shape found in this codebase
 * through `variant`/`size` instead of a new one-off composable per screen.
 */
enum class TdsButtonVariant { Filled, Tinted }
enum class TdsButtonSize { Large, Medium, Small }

@Composable
fun TdsButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    variant: TdsButtonVariant = TdsButtonVariant.Filled,
    size: TdsButtonSize = TdsButtonSize.Large,
) {
    val heightDp = when (size) {
        TdsButtonSize.Large -> 56.dp
        TdsButtonSize.Medium -> 48.dp
        TdsButtonSize.Small -> 36.dp
    }
    val horizontalPadding = when (size) {
        TdsButtonSize.Large, TdsButtonSize.Medium -> ButtonDefaults.ContentPadding
        TdsButtonSize.Small -> androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 0.dp)
    }
    val widthModifier = if (size == TdsButtonSize.Large) Modifier.fillMaxWidth() else Modifier
    val (containerColor, contentColor) = when (variant) {
        TdsButtonVariant.Filled -> Tds.colors.brand to TdsColors.White
        TdsButtonVariant.Tinted -> Tds.colors.pressed to Tds.colors.textBrand
    }

    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .then(widthModifier)
            .height(heightDp),
        shape = RoundedCornerShape(if (size == TdsButtonSize.Small) 10.dp else 12.dp),
        contentPadding = horizontalPadding,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = Tds.colors.divider,
            disabledContentColor = Tds.colors.textTertiary
        )
    ) {
        Text(
            text = text,
            style = if (size == TdsButtonSize.Small) TdsTypography.Body2 else TdsTypography.Button
        )
    }
}

/** The icon-only circular shape `TopIconButton` used to duplicate locally in ItundaAppScreen.kt. */
@Composable
fun TdsIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(Tds.layout.minTouchTarget)
            .clickable(onClick = onClick)
            .background(Tds.colors.surfaceSoft, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(20.dp), tint = Tds.colors.textPrimary)
    }
}
