package rw.itunda.core.designsystem.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

// Real fix (2026-08-24, direct user follow-up after the first ledger-row press-
// feedback pass: "presable spring effect is working but not as smooth as toss
// spring effect ... user finger touch presable components while scroll user can
// feel that spring effect"). A deep search (WebSearch, this same session) for a
// published Toss engineering writeup on this exact interaction turned up nothing
// concrete -- unlike IdsMotion's own spring constants, which ARE a real sourced
// Toss npm package, this specific mechanism is itunda's own engineered
// implementation of the DESCRIBED behavior, not a ported Toss algorithm.
//
// The real gap in the first pass: Modifier.clickable (what pressScaleClickable
// wraps) only ever shows a pressed state for a stationary tap-down -- the instant a
// touch moves far enough to be recognized as a scroll drag, Android's own
// touch-slop cancellation fires PressInteraction.Cancel and the row snaps straight
// back to unpressed. During an active scroll drag, every row a moving finger
// passes over shows nothing at all, which reads as "not smooth" compared to a
// reference that (per the user's own description) keeps giving spring feedback as
// the finger travels. This tracker is a second, independent, NON-CONSUMING signal:
// it watches raw pointer position (PointerEventPass.Initial, never calling
// change.consume()) against the LazyColumn's own live layoutInfo -- so it stays
// correct as the list itself scrolls under the finger -- and reports which visible
// item's key is currently under the touch point, continuously, through an entire
// drag, not just on a stationary press. Because it never consumes, it cannot
// interfere with the LazyColumn's own scroll handling or a row's own click
// handling (confirmed against Android's own pointer-input docs, developer.android.
// com/develop/ui/compose/touch-input/pointer-input/scroll: don't call consume() on
// events you want to keep propagating to the scroll handler).
fun Modifier.trackScrollPressedKey(listState: LazyListState, touchedKey: MutableState<Any?>): Modifier =
    this.pointerInput(listState) {
        awaitEachGesture {
            fun keyAt(y: Float): Any? =
                listState.layoutInfo.visibleItemsInfo.firstOrNull { y >= it.offset && y < it.offset + it.size }?.key

            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            touchedKey.value = keyAt(down.position.y)
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) {
                    touchedKey.value = null
                    break
                }
                touchedKey.value = keyAt(change.position.y)
            }
        }
    }
