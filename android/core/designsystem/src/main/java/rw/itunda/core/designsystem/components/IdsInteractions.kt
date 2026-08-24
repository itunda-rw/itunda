package rw.itunda.core.designsystem.components

import androidx.compose.animation.core.animateFloatAsState
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
import rw.itunda.core.designsystem.theme.IdsMotion

// Extracted 2026-08-11 from IdsButton.kt's own press-scale fix (2026-08-11, "시각적
// 신호가 탭이 발생하는 정확한 순간에 햅틱/사용자 액션과 동기화되어야 한다") -- that
// fix only reached IdsButton itself, but IdsListRow and IdsIconButton are at least as
// widely tapped (every settings/menu row, every top-bar icon) and still had zero
// press feedback, the exact "one screen has motion, everything else stays flat
// static" inconsistency that fix's own doc comment warned about. Shared here so the
// same spring spec can't drift between call sites the way the three duplicate button
// composables (PrimaryAction/SmallBlueButton/TopIconButton) already once did.
// Real fix (2026-08-24, Toss motion-curve sourcing pass): was Compose's own generic
// Spring.DampingRatioMediumBouncy/StiffnessHigh presets -- now IdsMotion.springQuick,
// the real Toss "quick" spring preset (see IdsMotion.kt's own sourcing comment),
// matching web's identical button:active timing and iOS's press-feedback spring.
// Real fix (2026-08-24/25, direct user follow-up thread on the account ledger:
// "presable spring effect is working but not as smooth as toss spring effect ...
// user finger touch presable components while scroll user can feel that spring
// effect", then "implant that into our designs and apply it across our
// ecosystems"). extraPressed OR's in a second, externally-supplied press signal --
// originally built for ScrollPressTracker.kt's trackScrollPressedKey (a
// non-consuming pointer tracker that reports which row a dragging finger is
// currently over, since Modifier.clickable's own interaction source only shows a
// press for a stationary tap-down and is cancelled the instant a touch is
// recognized as a scroll). Default false keeps every one of this function's other
// ~15 existing call sites (IdsButton/IdsListRow/HoodShared/AmountKeypadInput/
// AccountPinPad/PinScreen/TransferFlow) byte-for-byte unchanged.
@Composable
fun rememberPressScale(interactionSource: InteractionSource, enabled: Boolean = true, extraPressed: Boolean = false): Float {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if ((pressed || extraPressed) && enabled) 0.96f else 1f,
        animationSpec = IdsMotion.springQuick(),
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
// isScrollTouched (2026-08-25, see rememberPressScale's own doc comment above):
// pass `touchedKey.value == thisRow.id` from a list wrapped in
// ScrollPressTracker.kt's trackScrollPressedKey to get the same live,
// finger-follows-through-a-scroll-drag spring feedback the account ledger row
// shipped first. Defaults to false -- every existing call site is unaffected.
fun Modifier.pressScaleClickable(enabled: Boolean = true, isScrollTouched: Boolean = false, onClick: () -> Unit): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val pressScale = rememberPressScale(interactionSource, enabled, isScrollTouched)
    this
        .scale(pressScale)
        .clickable(interactionSource = interactionSource, indication = LocalIndication.current, enabled = enabled, onClick = onClick)
}
