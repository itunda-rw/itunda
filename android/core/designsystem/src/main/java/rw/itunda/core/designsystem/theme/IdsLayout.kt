package rw.itunda.core.designsystem.theme

import androidx.compose.ui.unit.dp

/** Canonical IDS spacing, shape and control anatomy.
 * Values correspond to packages/design-tokens/tokens.json. Platform-native
 * rendering remains Android-specific; these are shared product semantics.
 */
object IdsLayout {
    val screenHorizontal = 20.dp
    val screenVertical = 16.dp
    val sectionGap = 24.dp
    val cardGap = 16.dp
    val rowGap = 14.dp
    val inlineGap = 12.dp
    val tightGap = 8.dp
    /** Maximum readable content width for decision-focused screens. */
    val contentMeasure = 680.dp
    /** Maximum hero width before copy begins to feel visually heavy. */
    val heroMeasure = 760.dp
    /** Default separation before a primary decision/action group. */
    val decisionGap = 24.dp

    val controlHeightSm = 40.dp
    val controlHeightMd = 48.dp
    val controlHeightLg = 56.dp
    val controlRadius = 12.dp
    val buttonCornerRadius = 16.dp
    val cardCornerRadius = 24.dp
    val sectionCornerRadius = 16.dp
    val chipCornerRadius = 999.dp
    val iconCornerRadius = 12.dp

    val cardElevation = 2.dp
    val minTouchTarget = 48.dp
}