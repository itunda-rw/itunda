package rw.itunda.core.designsystem.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.designsystem.theme.IdsTypography

/**
 * Real shared draggable bottom-sheet primitive (2026-07-24) -- closes the gap
 * docs/DESIGN_REFERENCES.md §7 names explicitly: "the sheet primitive ... don't exist
 * as shared IDS components in itunda today -- each surface independently rebuilt a
 * version of them." The same need was found independently in three research threads
 * (Maps place-detail + around-me, Eats/Shop product-detail sheets, Hood
 * category/filter selection).
 *
 * This is a generalization of `MapsScreen.kt`'s own real, already-shipped
 * peek/half/full sheet (2026-07-21) -- NOT built on Material3's `BottomSheetScaffold`,
 * because that component only exposes two real detents (`PartiallyExpanded`/
 * `Expanded`), one short of the three peek/half/full states both Apple's
 * `UISheetPresentationController` and Google Maps' own custom `BottomSheetBehavior`
 * extension confirm are the real, necessary shape (see MapsScreen.kt's own header
 * comment on `MapSheetValue` for the sourcing). A first pass of this file wrapped
 * `BottomSheetScaffold` and silently mapped both `Peek` and `Half` onto the same
 * `PartiallyExpanded` value -- an honest-looking API hiding a fake third state. Rather
 * than ship that, this instead lifts Maps' real, working `AnchoredDraggableState`-based
 * mechanism (verified live on Maps' own place-detail sheet) into a reusable overlay,
 * so Eats/Shop/Hood get the actual same three-state behavior Maps already proved,
 * not a weaker lookalike.
 *
 * Two APIs sharing the same overlay, matching Toss's real dual-API pattern (see
 * IdsButton.kt's header comment for the sourcing, toss.tech/article/rethinking-design-system):
 * - Compound ([IdsDraggableSheetOverlay] + [IdsSheetHeader]/[IdsSheetActionsRow] slots)
 *   for surfaces needing a fully custom layout -- e.g. Maps' place-detail sheet needs a
 *   hero image plus a reserve/delivery/bookmark/call/directions action row up front
 *   (docs/DESIGN_REFERENCES.md §1, item 3), not a generic title+body.
 * - Flat ([IdsBottomSheetOverlay]) for the common title+content case.
 */
enum class IdsSheetDetent { Peek, Half, Full }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun rememberIdsSheetState(initial: IdsSheetDetent = IdsSheetDetent.Peek): AnchoredDraggableState<IdsSheetDetent> {
    val density = LocalDensity.current
    return remember {
        AnchoredDraggableState(
            initialValue = initial,
            positionalThreshold = { distance: Float -> distance * 0.5f },
            velocityThreshold = { with(density) { 125.dp.toPx() } },
            animationSpec = tween(),
        )
    }
}

@Composable
private fun ColumnScope.IdsSheetDragHandle() {
    Box(
        modifier = Modifier
            .align(Alignment.CenterHorizontally)
            .padding(top = 10.dp, bottom = 6.dp)
            .width(36.dp)
            .height(4.dp)
            .background(Ids.colors.textSecondary.copy(alpha = 0.4f), RoundedCornerShape(2.dp)),
    )
}

/**
 * Compound API: a non-modal, draggable panel docked over [background] (e.g. a map, or
 * a product list) that the user can drag between Peek/Half/Full -- the exact mechanic
 * Maps' own place-detail sheet already ships. [peekHeight]/[halfFraction]/[fullTopGap]
 * default to Maps' own live-tuned values but are overridable per surface.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IdsDraggableSheetOverlay(
    state: AnchoredDraggableState<IdsSheetDetent>,
    modifier: Modifier = Modifier,
    peekHeight: Dp = 128.dp,
    halfFraction: Float = 0.55f,
    fullTopGap: Dp = 96.dp,
    background: @Composable BoxScope.() -> Unit,
    sheetContent: @Composable ColumnScope.() -> Unit,
) {
    val density = LocalDensity.current
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val fullHeightPx = with(density) { maxHeight.toPx() }
        val peekHeightPx = with(density) { peekHeight.toPx() }
        val fullTopGapPx = with(density) { fullTopGap.toPx() }
        val anchors = remember(fullHeightPx, peekHeightPx, halfFraction, fullTopGapPx) {
            DraggableAnchors {
                IdsSheetDetent.Peek at (fullHeightPx - peekHeightPx)
                IdsSheetDetent.Half at (fullHeightPx * halfFraction)
                IdsSheetDetent.Full at fullTopGapPx
            }
        }
        LaunchedEffect(anchors) { state.updateAnchors(anchors) }

        background()

        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .height(maxHeight)
                .offset {
                    // requireOffset() throws on the very first layout pass: updateAnchors()
                    // above only runs once its LaunchedEffect's coroutine is dispatched,
                    // which is after this frame's layout already ran once -- fall back to
                    // the peek position (this state's own initial value) for that one frame.
                    val offset = state.offset.let { if (it.isNaN()) fullHeightPx - peekHeightPx else it }
                    IntOffset(0, offset.roundToInt())
                }
                .anchoredDraggable(state, Orientation.Vertical),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Ids.colors.surface, RoundedCornerShape(topStart = Ids.layout.cardCornerRadius, topEnd = Ids.layout.cardCornerRadius)),
            ) {
                IdsSheetDragHandle()
                sheetContent()
            }
        }
    }
}

/** Compound slot: a title (+ optional subtitle) header for the sheet's own content. */
@Composable
fun IdsSheetHeader(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = Ids.layout.screenHorizontal)) {
        Text(title, style = IdsTypography.Title2, color = Ids.colors.textPrimary)
        if (subtitle != null) {
            Text(subtitle, style = IdsTypography.Body2, color = Ids.colors.textSecondary)
        }
    }
}

/**
 * Compound slot: a horizontally-scrolling action row -- e.g. Maps' real
 * reserve/delivery/bookmark/call/directions row (docs/DESIGN_REFERENCES.md §1, item 3).
 */
@Composable
fun IdsSheetActionsRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Ids.layout.screenHorizontal, vertical = Ids.layout.tightGap),
        horizontalArrangement = Arrangement.spacedBy(Ids.layout.inlineGap),
        content = content,
    )
}

/** Flat API: the common title+body case, built on the same [IdsDraggableSheetOverlay]. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IdsBottomSheetOverlay(
    title: String,
    state: AnchoredDraggableState<IdsSheetDetent>,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    peekHeight: Dp = 128.dp,
    background: @Composable BoxScope.() -> Unit,
    sheetBody: @Composable ColumnScope.() -> Unit,
) {
    IdsDraggableSheetOverlay(
        state = state,
        modifier = modifier,
        peekHeight = peekHeight,
        background = background,
        sheetContent = {
            IdsSheetHeader(title, subtitle)
            sheetBody()
        },
    )
}
