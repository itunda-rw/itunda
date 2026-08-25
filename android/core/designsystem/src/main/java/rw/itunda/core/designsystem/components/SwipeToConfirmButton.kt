package rw.itunda.core.designsystem.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.designsystem.theme.IdsIcons
import rw.itunda.core.designsystem.theme.IdsMotion
import kotlin.math.roundToInt

/**
 * Real Toss "밀어서 결제하기" (swipe to pay) primitive (2026-08-25, direct user
 * screenshot of Toss Shopping's real checkout sheet) -- Toss's own signature payment
 * gesture: a deliberate, hard-to-mis-tap drag instead of a single tap, used at every
 * real money-moving confirmation across their apps. Built as a shared design-system
 * primitive (not inlined into Shop's checkout) since it's a real, reusable
 * confirmation pattern any future checkout/high-stakes-confirm screen can adopt the
 * same way `pressScaleClickable` became this repo's shared tap primitive.
 *
 * Caller contract mirrors a normal button: `enabled` gates whether the handle can be
 * dragged at all, `busy` freezes it mid-swipe (label swaps to `busyLabel`) while an
 * async action runs. If `busy` clears without the caller navigating away (a real
 * failure, not a success), the handle springs back to the start so the buyer can
 * retry -- it never gets stuck pinned at the end from a failed attempt.
 */
@Composable
fun SwipeToConfirmButton(
    label: String,
    busyLabel: String,
    enabled: Boolean,
    busy: Boolean,
    onConfirm: () -> Unit,
) {
    val trackHeight = 56.dp
    val handleSize = 48.dp
    val handlePadding = 4.dp
    var trackWidthPx by remember { mutableStateOf(0f) }
    val offsetX = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val maxOffsetPx = with(density) {
        (trackWidthPx - handleSize.toPx() - handlePadding.toPx() * 2).coerceAtLeast(0f)
    }

    // Real retry-after-failure reset -- see this function's own doc comment. Only
    // fires when the handle is actually mid-track (a real failed attempt), not on
    // every recomposition.
    LaunchedEffect(busy, enabled) {
        if (!busy && enabled && offsetX.value > 0f) {
            offsetX.animateTo(0f, IdsMotion.springBounce())
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(trackHeight)
            .onSizeChanged { trackWidthPx = it.width.toFloat() }
            .clip(RoundedCornerShape(trackHeight / 2))
            .background(if (enabled || busy) Ids.colors.brand else Ids.colors.textTertiary),
    ) {
        Text(
            if (busy) busyLabel else label,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxSize().align(Alignment.Center).padding(start = handleSize),
        )
        Box(
            modifier = Modifier
                .padding(handlePadding)
                .size(handleSize)
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .clip(CircleShape)
                .background(Color.White)
                .then(
                    if (enabled && !busy) {
                        Modifier.pointerInput(maxOffsetPx) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    coroutineScope.launch {
                                        if (maxOffsetPx > 0f && offsetX.value >= maxOffsetPx * 0.8f) {
                                            offsetX.animateTo(maxOffsetPx, IdsMotion.springQuick())
                                            onConfirm()
                                        } else {
                                            offsetX.animateTo(0f, IdsMotion.springBounce())
                                        }
                                    }
                                },
                                onHorizontalDrag = { change, dragAmount ->
                                    change.consume()
                                    coroutineScope.launch {
                                        offsetX.snapTo((offsetX.value + dragAmount).coerceIn(0f, maxOffsetPx))
                                    }
                                },
                            )
                        }
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(IdsIcons.ChevronRight, contentDescription = "Swipe to confirm", tint = Ids.colors.brand)
        }
    }
}
