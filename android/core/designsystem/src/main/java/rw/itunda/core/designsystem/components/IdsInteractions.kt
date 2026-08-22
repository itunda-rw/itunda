package rw.itunda.core.designsystem.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale

// Extracted 2026-08-11 from IdsButton.kt's own press-scale fix (2026-08-11, "시각적
// 신호가 탭이 발생하는 정확한 순간에 햅틱/사용자 액션과 동기화되어야 한다") -- that
// fix only reached IdsButton itself, but IdsListRow and IdsIconButton are at least as
// widely tapped (every settings/menu row, every top-bar icon) and still had zero
// press feedback, the exact "one screen has motion, everything else stays flat
// static" inconsistency that fix's own doc comment warned about. Shared here so the
// same spring spec can't drift between call sites the way the three duplicate button
// composables (PrimaryAction/SmallBlueButton/TopIconButton) already once did.
@Composable
fun rememberPressScale(interactionSource: InteractionSource, enabled: Boolean = true): Float {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
        label = "pressScale",
    )
    return scale
}

// Real one-line wrapper (2026-08-22, "toss interactions" sweep continuation) --
// every real fix above this point required manually threading a
// `MutableInteractionSource`, `rememberPressScale`, `.scale(...)`, and
// `.clickable(interactionSource = ..., indication = ..., ...)` through each call
// site by hand, a real ~4-line diff per site that made a repo-wide sweep past the
// first handful of shared components (IdsButton/IdsListRow/IdsIconButton/
// ListingActionButton/BackTopBar/etc.) slow and error-prone to keep doing one
// call site at a time. `Modifier.composed { }` is Compose's own real, idiomatic
// mechanism for giving a modifier internal composable state without the caller
// needing to hoist anything -- this collapses the entire pattern into a single
// chainable modifier, so any of the ~250+ real raw `.clickable(...)` sites still
// found repo-wide (found via a real grep sweep, not estimated) can pick up real
// press feedback with a one-line, low-risk substitution:
// `.clickable(onClick = x)` -> `.pressScaleClickable(onClick = x)`, instead of a
// 4-line rewrite that's more likely to introduce a real mistake in a business-
// critical screen (transfers, ride flows) than the animation itself is worth
// risking. Signature intentionally mirrors `Modifier.clickable`'s own most-used
// parameters (enabled/onClick) -- extend with onClickLabel/role/onLongClick etc.
// if a real call site needs them, following the same real-Compose-API shape
// rather than inventing a new one.
fun Modifier.pressScaleClickable(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val pressScale = rememberPressScale(interactionSource, enabled)
    this
        .scale(pressScale)
        .clickable(interactionSource = interactionSource, indication = LocalIndication.current, enabled = enabled, onClick = onClick)
}
