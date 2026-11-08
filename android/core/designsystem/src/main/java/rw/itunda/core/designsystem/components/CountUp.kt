package rw.itunda.core.designsystem.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Prototype v1008: balances count up from 0 the first time they appear and roll to the
 * new value when it changes (Toss-style), instead of snapping. Returns the value to
 * display right now; format it with your usual money formatter.
 */
@Composable
fun rememberCountUp(target: Double, durationMillis: Int = 700): Double {
    var started by remember { mutableStateOf(false) }
    val progress by animateFloatAsState(
        targetValue = if (started) target.toFloat() else 0f,
        animationSpec = tween(durationMillis, easing = FastOutSlowInEasing),
        label = "countUp",
    )
    // Flip once, on first composition, so the first frame starts at 0 and animates to target.
    androidx.compose.runtime.LaunchedEffect(Unit) { started = true }
    return if (started) progress.toDouble() else 0.0
}
