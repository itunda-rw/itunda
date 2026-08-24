package rw.itunda.core.designsystem.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring

/**
 * Real Toss motion primitives (2026-08-24), fetched directly from their own published
 * npm packages -- `@toss/tds-easings@0.0.1` and `@toss/tds-spring-easing@0.0.1`'s real
 * bundled source (`npm pack`, read `dist/esm/index.js`), not guessed or approximated.
 * Same 5 bezier curves ported to web (`packages/design-tokens/tokens.css`'s
 * `--itunda-ease-*`) and iOS (`IDS.Motion`'s `Animation.timingCurve`); same 8 spring
 * presets ported to web (`lib/motion.ts`'s `itundaSpring`, for Framer Motion) and iOS
 * (`IDS.Motion`'s `Animation.interpolatingSpring`, which takes the raw stiffness/
 * damping/mass directly -- no conversion needed there). All 3 platforms now share
 * identical timing curves and spring feel, not just identical colors/type.
 *
 * Compose's `spring()` takes a damping RATIO, not the raw damping value Toss
 * publishes -- each preset's `dampingRatio` below is computed directly from Toss's own
 * real stiffness/damping/mass via the standard formula
 * `dampingRatio = damping / (2 * sqrt(stiffness * mass))`, not re-guessed. Compose's
 * own built-in presets (`Spring.DampingRatioNoBouncy` = 1f, `...LowBouncy` = 0.75f,
 * `...MediumBouncy` = 0.5f, `...HighBouncy` = 0.2f) sit in the same 0.2-1.2 range these
 * computed values land in, confirming the conversion produces physically sensible
 * Compose-native numbers rather than something out of range.
 */
object IdsMotion {
    val easeLinear: Easing = CubicBezierEasing(0f, 0f, 1f, 1f)
    val easeStandard: Easing = CubicBezierEasing(0.6f, 0f, 0f, 0.6f)
    val easeOut: Easing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)
    val easeExpo: Easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
    val easeBack: Easing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)

    // dampingRatio computed from Toss's real (stiffness, damping, mass) via
    // damping / (2 * sqrt(stiffness * mass)):
    fun <T> springBasic(): FiniteAnimationSpec<T> = spring(dampingRatio = 1.0607f, stiffness = 200f)
    fun <T> springSmall(): FiniteAnimationSpec<T> = spring(dampingRatio = 1.1411f, stiffness = 480f)
    fun <T> springQuick(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.9723f, stiffness = 800f)
    fun <T> springMedium(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.7608f, stiffness = 270f)
    fun <T> springLarge(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.75f, stiffness = 100f)
    fun <T> springSlow(): FiniteAnimationSpec<T> = spring(dampingRatio = 1.1952f, stiffness = 70f)
    fun <T> springRapid(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.8698f, stiffness = 1000f)
    fun <T> springBounce(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.4330f, stiffness = 300f)
}
