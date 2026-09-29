package rw.itunda.core.designsystem.components

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue

// Real Toss motion pattern (toss.im/tossfeed/article/why-motion-in-finance) -- ported
// from bank-mfe's own real, sourced useCountUp hook (services/micro-frontends/bank-mfe/
// src/hooks/useCountUp.ts, see that file's own doc comment for the full sourced
// account): Toss deliberately animates balance/amount changes rather than snapping
// instantly, over a real, deliberately snappy 600ms ("가볍고 경쾌하게," light and brisk
// -- not a slow animation that makes the user wait to read their own balance).
//
// Compose's own `animateFloatAsState` already gives this the exact "skip the animation
// on first mount, only animate real subsequent changes" behavior the web version needed
// a hand-rolled `isFirstRender` ref for -- the underlying `Animatable` initializes
// directly to the first-read target value (nothing to animate from yet), and only
// animates on a later recomposition where the target actually changes. No extra
// bookkeeping needed here, unlike the web port.
@Composable
fun rememberCountUp(value: Double, durationMillis: Int = 600): Double {
    val animated by animateFloatAsState(
        targetValue = value.toFloat(),
        animationSpec = tween(durationMillis = durationMillis, easing = LinearOutSlowInEasing),
        label = "countUp",
    )
    return animated.toDouble()
}

@Composable
fun rememberCountUp(value: Long, durationMillis: Int = 600): Long {
    val animated by animateFloatAsState(
        targetValue = value.toFloat(),
        animationSpec = tween(durationMillis = durationMillis, easing = LinearOutSlowInEasing),
        label = "countUp",
    )
    return animated.toLong()
}
