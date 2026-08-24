package rw.itunda.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathData
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.unit.dp

/**
 * itunda's own navigation/action icon set (2026-08-24), direct user follow-up: "let's
 * search how toss archived [cross-platform consistency] ... let's itunda look the
 * same across all three platforms as toss do". Real research: Toss's own TDS icon
 * system is private (confirmed via their own docs nav -- only Colors/Typography have
 * public Foundation pages), but their engineering blog's generalizable lesson is
 * architectural: one custom icon set exported natively per platform, not each
 * platform reaching for its own stock library. Confirmed that's itunda's real gap by
 * auditing directly: web used lucide-react, Android used Material Icons
 * (`Icons.Outlined.X`), iOS uses SF Symbols -- the same UI concept rendered as 3
 * visually different glyphs. This is that gap closed for Android, byte-identical
 * geometry to the web set (`icons/ItundaIcons.tsx`, same 0-24 coordinate space) and
 * iOS's own port (`IDS.Icons`).
 *
 * Unlike itundaface's illustration-style glyphs (Shape2D/ItundaFaceGlyphCanvas, filled
 * shapes rendered via Compose's Canvas/DrawScope), these are simple STROKE icons --
 * built as real `ImageVector`s via `ImageVector.Builder`'s stroke path support, so
 * each one is a direct drop-in replacement for `Icons.Outlined.X` at every real call
 * site (`Icon(IdsIcons.Back, contentDescription = ..., tint = ...)`), no new rendering
 * primitive needed. 24x24 viewBox, 2.4dp stroke, round caps/joins -- matches web's
 * exact construction (`ItundaIcons.tsx`'s own `IconBase`).
 *
 * Phase 2 covers the 5 highest-value, most universal concepts, prioritized by real
 * combined cross-platform usage frequency measured directly from the codebase: Back
 * (Android's own ArrowBackIosNew+ArrowBack, 18 combined real sites), ChevronRight (13),
 * Search (5), Close (4), Add (3). ArrowBackIosNew and ArrowBack -- two different
 * Material icons Android's own code drew for the same "go back" action -- both unify
 * to this single Back glyph, closing a real inconsistency that predated this fix.
 */
object IdsIcons {
    private fun strokeIcon(name: String, build: androidx.compose.ui.graphics.vector.ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply(build).build()

    private fun androidx.compose.ui.graphics.vector.ImageVector.Builder.strokePath(pathData: List<PathNode>) {
        addPath(
            pathData = pathData,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2.4f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }

    val Back: ImageVector = strokeIcon("IdsIcons.Back") {
        strokePath(PathData { moveTo(15f, 4f); lineTo(7f, 12f); lineTo(15f, 20f) })
    }

    val ChevronRight: ImageVector = strokeIcon("IdsIcons.ChevronRight") {
        strokePath(PathData { moveTo(9f, 4f); lineTo(17f, 12f); lineTo(9f, 20f) })
    }

    val Close: ImageVector = strokeIcon("IdsIcons.Close") {
        strokePath(PathData { moveTo(5f, 5f); lineTo(19f, 19f) })
        strokePath(PathData { moveTo(19f, 5f); lineTo(5f, 19f) })
    }

    val Search: ImageVector = strokeIcon("IdsIcons.Search") {
        strokePath(
            PathData {
                moveTo(17.5f, 10.5f)
                curveTo(17.5f, 14.366f, 14.366f, 17.5f, 10.5f, 17.5f)
                curveTo(6.634f, 17.5f, 3.5f, 14.366f, 3.5f, 10.5f)
                curveTo(3.5f, 6.634f, 6.634f, 3.5f, 10.5f, 3.5f)
                curveTo(14.366f, 3.5f, 17.5f, 6.634f, 17.5f, 10.5f)
                close()
            },
        )
        strokePath(PathData { moveTo(20f, 20f); lineTo(15.3f, 15.3f) })
    }

    val Add: ImageVector = strokeIcon("IdsIcons.Add") {
        strokePath(PathData { moveTo(12f, 4f); lineTo(12f, 20f) })
        strokePath(PathData { moveTo(4f, 12f); lineTo(20f, 12f) })
    }
}
