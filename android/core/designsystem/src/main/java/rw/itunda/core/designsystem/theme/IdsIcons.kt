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
 *
 * Phase 3 (same session) covers 6 more concepts, byte-identical path data to web's
 * phase 3 (`icons/ItundaIcons.tsx`), real Android usage confirmed by grep before
 * rollout: Star (`Icons.Filled/Outlined.Star`, 10 combined sites), Bell
 * (`Icons.Outlined.Notifications`, 7), Send (`Icons.Outlined.Send`, 3 real code sites),
 * ShieldCheck (`Icons.Outlined.Shield`, 2 -- distinct from `VerifiedUser`'s "person"
 * concept, left untouched), Eye/EyeOff (`Icons.Outlined.Visibility`/`VisibilityOff`, 4).
 * Circle primitives (Eye's pupil) approximated the same way as `Search`'s ring: 4 cubic
 * beziers with the standard kappa=0.5523 control-point ratio.
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

    // Real gap found live (2026-08-31, direct user-supplied real Toss keypad
    // screenshot -- "arrow icon should look like that"): the bare chevron this
    // used to draw (a plain angle-bracket with no shaft) doesn't match real
    // Toss's own back/backspace glyph, a true horizontal arrow -- a full shaft
    // plus an arrowhead. Redesigned to match that reference exactly (Feather
    // Icons' own real "arrow-left" shape). This is the ONE shared icon every
    // "back" button on all 3 platforms already renders through, plus the
    // numeric-keypad backspace key (an earlier same-day fix reused this same
    // component) -- redesigning the shape here fixes both at once, with no
    // call-site changes needed anywhere. Byte-identical geometry to web's own
    // IconBack (icons/ItundaIcons.tsx) and iOS's IDSBackShape.
    val Back: ImageVector = strokeIcon("IdsIcons.Back") {
        strokePath(PathData { moveTo(19f, 12f); lineTo(5f, 12f) })
        strokePath(PathData { moveTo(12f, 19f); lineTo(5f, 12f); lineTo(12f, 5f) })
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

    val Star: ImageVector = strokeIcon("IdsIcons.Star") {
        strokePath(
            PathData {
                moveTo(12f, 2.5f)
                lineTo(14.9f, 9f)
                lineTo(22f, 9.7f)
                lineTo(16.7f, 14.5f)
                lineTo(18.2f, 21.5f)
                lineTo(12f, 17.8f)
                lineTo(5.8f, 21.5f)
                lineTo(7.3f, 14.5f)
                lineTo(2f, 9.7f)
                lineTo(9.1f, 9f)
                close()
            },
        )
    }

    val Send: ImageVector = strokeIcon("IdsIcons.Send") {
        strokePath(
            PathData {
                moveTo(3f, 11f)
                lineTo(21f, 3f)
                lineTo(13f, 21f)
                lineTo(11f, 13f)
                lineTo(3f, 11f)
                close()
            },
        )
        strokePath(PathData { moveTo(11f, 13f); lineTo(21f, 3f) })
    }

    val Bell: ImageVector = strokeIcon("IdsIcons.Bell") {
        strokePath(
            PathData {
                moveTo(6f, 10.5f)
                curveTo(6f, 6.9f, 8.7f, 4f, 12f, 4f)
                curveTo(15.3f, 4f, 18f, 6.9f, 18f, 10.5f)
                verticalLineTo(14.5f)
                lineTo(20.2f, 17.5f)
                horizontalLineTo(3.8f)
                lineTo(6f, 14.5f)
                close()
            },
        )
        strokePath(
            PathData {
                moveTo(9.8f, 19.8f)
                curveTo(9.8f, 21f, 10.8f, 22f, 12f, 22f)
                curveTo(13.2f, 22f, 14.2f, 21f, 14.2f, 19.8f)
            },
        )
    }

    val ShieldCheck: ImageVector = strokeIcon("IdsIcons.ShieldCheck") {
        strokePath(
            PathData {
                moveTo(12f, 2.5f)
                lineTo(20f, 5.5f)
                verticalLineTo(11f)
                curveTo(20f, 16.2f, 16.6f, 20.4f, 12f, 21.8f)
                curveTo(7.4f, 20.4f, 4f, 16.2f, 4f, 11f)
                verticalLineTo(5.5f)
                close()
            },
        )
        strokePath(PathData { moveTo(8.5f, 12f); lineTo(11f, 14.5f); lineTo(15.5f, 9.5f) })
    }

    val Eye: ImageVector = strokeIcon("IdsIcons.Eye") {
        strokePath(
            PathData {
                moveTo(2f, 12f)
                curveTo(4f, 7f, 8f, 4.5f, 12f, 4.5f)
                curveTo(16f, 4.5f, 20f, 7f, 22f, 12f)
                curveTo(20f, 17f, 16f, 19.5f, 12f, 19.5f)
                curveTo(8f, 19.5f, 4f, 17f, 2f, 12f)
                close()
            },
        )
        strokePath(
            PathData {
                moveTo(15f, 12f)
                curveTo(15f, 13.657f, 13.657f, 15f, 12f, 15f)
                curveTo(10.343f, 15f, 9f, 13.657f, 9f, 12f)
                curveTo(9f, 10.343f, 10.343f, 9f, 12f, 9f)
                curveTo(13.657f, 9f, 15f, 10.343f, 15f, 12f)
                close()
            },
        )
    }

    val EyeOff: ImageVector = strokeIcon("IdsIcons.EyeOff") {
        strokePath(PathData { moveTo(4.2f, 4.2f); lineTo(19.8f, 19.8f) })
        strokePath(
            PathData {
                moveTo(10.3f, 5.1f)
                curveTo(10.9f, 4.9f, 11.4f, 4.8f, 12f, 4.8f)
                curveTo(16f, 4.8f, 20f, 7.3f, 22f, 12.3f)
                curveTo(21.4f, 13.7f, 20.7f, 14.9f, 19.8f, 15.9f)
            },
        )
        strokePath(
            PathData {
                moveTo(6.4f, 6.9f)
                curveTo(4.4f, 8.2f, 2.9f, 10.1f, 2f, 12.3f)
                curveTo(4f, 17.3f, 8f, 19.8f, 12f, 19.8f)
                curveTo(13.3f, 19.8f, 14.6f, 19.5f, 15.8f, 19f)
            },
        )
        strokePath(
            PathData {
                moveTo(9.6f, 10f)
                curveTo(9.2f, 10.5f, 9f, 11.1f, 9f, 11.8f)
                curveTo(9f, 13.5f, 10.3f, 14.8f, 12f, 14.8f)
                curveTo(12.7f, 14.8f, 13.3f, 14.6f, 13.8f, 14.2f)
            },
        )
    }
}
