package rw.itunda.core.designsystem.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue

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
