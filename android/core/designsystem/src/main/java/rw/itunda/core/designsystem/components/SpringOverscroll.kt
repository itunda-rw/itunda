package rw.itunda.core.designsystem.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.launch
import rw.itunda.core.designsystem.theme.IdsMotion
import kotlin.math.roundToInt

// Real Toss reference (direct user follow-up, 2026-08-24, watching itunda's own
// ledger list live on-device: "toss uses spring effect which users feel not only
// when they pressing a button but also when they are scrolling through the lists
// like those transactions which help user to know that it's pressable"). Stock
// Android/Compose list scrolling only has the platform's own Android 12+
// stretch/glow edge effect -- a subtle visual squish with no real spring-back
// motion -- while real Toss lists visibly pull past the edge on a hard drag, then
// spring back with the same bouncy feel as a button press. Reuses
// IdsMotion.springBounce() for the spring-back itself -- the real sourced Toss npm
// spring value (see IdsMotion.kt's own sourcing comment) already used for press
// feedback, not a separately invented bounce curve. The pull *resistance* and max
// pull distance below have no equivalent public Toss constant to source -- itunda's
// own reasonable engineering defaults, tuned by eye against the real reference.
//
// Implemented as a NestedScrollConnection + Animatable rather than Compose
// Foundation's newer OverscrollEffect/rememberOverscrollEffect API, which needs a
// compose-bom newer than this repo's pinned 2024.02.00 (android/app/build.gradle.kts)
// -- both NestedScrollConnection and Animatable have been stable since Compose 1.0,
// so this needed no version bump. onPostScroll only ever sees a nonzero `available`
// delta once the scrolling child itself can't consume any more of it, i.e. exactly
// at the true top/bottom edge -- this never fires mid-list.
@Composable
fun rememberSpringOverscrollModifier(): Modifier {
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val connection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.Drag || available.y == 0f) return Offset.Zero
                val resisted = available.y * OVERSCROLL_RESISTANCE
                val target = (offset.value + resisted).coerceIn(-MAX_PULL_PX, MAX_PULL_PX)
                scope.launch { offset.snapTo(target) }
                return available
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (offset.value == 0f) return Velocity.Zero
                scope.launch { offset.animateTo(0f, IdsMotion.springBounce()) }
                return available
            }
        }
    }
    return Modifier
        .nestedScroll(connection)
        .offset { IntOffset(0, offset.value.roundToInt()) }
}

private const val OVERSCROLL_RESISTANCE = 0.45f
private const val MAX_PULL_PX = 220f
