package rw.itunda.core.designsystem.itundaface

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Dp
import androidx.core.graphics.PathParser

// itundaface: shared glyph-drawing primitives (2026-08-22), promoted out of
// maps/impl's original ItundaFacePlaces.kt so every feature module can draw
// itundaface glyphs without a forbidden impl-to-impl cross-feature import
// (Konsist enforces feature-module isolation in this repo -- see root
// CLAUDE.md). This is the reusable foundation the web/itundaface repo's own
// SVG "d" path data plugs into directly: every path string below is
// copy-pasted byte-identical from the real, already-shipped web SVGs and
// parsed via PathParser.createPathFromPathData + .asComposePath() instead of
// hand-re-deriving Bezier coordinates by eye, keeping Android's shapes
// provably identical to web's, not just "close."
//
// Each glyph is authored as a flat list of Shape2D primitives against the
// SAME local coordinate space its source SVG's viewBox uses (60x60 for
// place-category icons, 80x80 for reaction glyphs, etc.) -- GlyphCanvas scales
// that space to the requested render size, so the shape data itself never
// needs unit conversion.

sealed interface Shape2D {
    data class FilledPath(val d: String, val color: Long) : Shape2D
    data class StrokedPath(
        val d: String,
        val color: Long,
        val width: Float,
        val cap: StrokeCap = StrokeCap.Butt,
        val join: StrokeJoin = StrokeJoin.Miter,
        val alpha: Float = 1f,
    ) : Shape2D
    data class FilledCircle(val cx: Float, val cy: Float, val r: Float, val color: Long) : Shape2D
    data class StrokedCircle(val cx: Float, val cy: Float, val r: Float, val color: Long, val width: Float) : Shape2D
    data class FilledEllipse(val cx: Float, val cy: Float, val rx: Float, val ry: Float, val color: Long) : Shape2D
    data class StrokedEllipse(val cx: Float, val cy: Float, val rx: Float, val ry: Float, val color: Long, val width: Float) : Shape2D
    data class FilledRect(val x: Float, val y: Float, val w: Float, val h: Float, val rx: Float, val color: Long) : Shape2D
    data class StrokedLine(
        val x1: Float,
        val y1: Float,
        val x2: Float,
        val y2: Float,
        val color: Long,
        val width: Float,
        val cap: StrokeCap = StrokeCap.Butt,
        val alpha: Float = 1f,
        val dashOn: Float = 0f,
        val dashOff: Float = 0f,
    ) : Shape2D
    data class RotatedGroup(val degrees: Float, val pivotX: Float, val pivotY: Float, val shapes: List<Shape2D>) : Shape2D
}

fun DrawScope.drawItundaFaceShape(shape: Shape2D) {
    when (shape) {
        is Shape2D.FilledPath ->
            drawPath(PathParser.createPathFromPathData(shape.d).asComposePath(), Color(shape.color))
        is Shape2D.StrokedPath ->
            drawPath(
                PathParser.createPathFromPathData(shape.d).asComposePath(),
                Color(shape.color),
                alpha = shape.alpha,
                style = Stroke(width = shape.width, cap = shape.cap, join = shape.join),
            )
        is Shape2D.FilledCircle ->
            drawCircle(Color(shape.color), radius = shape.r, center = Offset(shape.cx, shape.cy))
        is Shape2D.StrokedCircle ->
            drawCircle(Color(shape.color), radius = shape.r, center = Offset(shape.cx, shape.cy), style = Stroke(width = shape.width))
        is Shape2D.FilledEllipse ->
            drawOval(
                Color(shape.color),
                topLeft = Offset(shape.cx - shape.rx, shape.cy - shape.ry),
                size = Size(shape.rx * 2, shape.ry * 2),
            )
        is Shape2D.StrokedEllipse ->
            drawOval(
                Color(shape.color),
                topLeft = Offset(shape.cx - shape.rx, shape.cy - shape.ry),
                size = Size(shape.rx * 2, shape.ry * 2),
                style = Stroke(width = shape.width),
            )
        is Shape2D.FilledRect ->
            drawRoundRect(
                Color(shape.color),
                topLeft = Offset(shape.x, shape.y),
                size = Size(shape.w, shape.h),
                cornerRadius = CornerRadius(shape.rx, shape.rx),
            )
        is Shape2D.StrokedLine ->
            drawLine(
                Color(shape.color),
                Offset(shape.x1, shape.y1),
                Offset(shape.x2, shape.y2),
                strokeWidth = shape.width,
                cap = shape.cap,
                alpha = shape.alpha,
                pathEffect = if (shape.dashOn > 0f) PathEffect.dashPathEffect(floatArrayOf(shape.dashOn, shape.dashOff)) else null,
            )
        is Shape2D.RotatedGroup ->
            withTransform({ rotate(shape.degrees, pivot = Offset(shape.pivotX, shape.pivotY)) }) {
                shape.shapes.forEach { drawItundaFaceShape(it) }
            }
    }
}

@Composable
fun ItundaFaceGlyphCanvas(size: Dp, viewBoxSize: Float = 60f, shapes: List<Shape2D>, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) {
        val scale = this.size.width / viewBoxSize
        withTransform({ scale(scale, scale, pivot = Offset.Zero) }) {
            shapes.forEach { drawItundaFaceShape(it) }
        }
    }
}
