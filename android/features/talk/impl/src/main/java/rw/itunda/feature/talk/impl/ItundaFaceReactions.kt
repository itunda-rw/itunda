package rw.itunda.feature.talk.impl

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import rw.itunda.core.designsystem.itundaface.ItundaFaceGlyphCanvas
import rw.itunda.core.designsystem.itundaface.Shape2D

// itundaface: chat quick-reaction glyphs (2026-08-22), ported from bank-mfe's
// icons/ItundaFace.tsx / github.com/itunda-rw/itundaface's svg/flat/*.svg --
// itundaface's own flagship group (the first one ever built), now closing the
// same real gap on Android that ItundaFacePlaces.kt closed for map categories.
// Replaces TalkMessageBubbles.kt's own QUICK_REACTIONS raw-emoji list at both
// real render points: the per-reaction count badge and the quick-react picker.
//
// Every path's "d" data below is copy-pasted byte-identical from the real,
// already-shipped web SVGs (the flat variant -- these render at 11-18sp here,
// itundaface's own established small-size-stays-flat rule, matching the web
// reaction badges' own flat/3D split). `heart` keeps its real shared-seam
// two-facet construction (two overlapping paths, not one single-fill shape)
// -- itundaface's own real, provable identity signature, not just a recolor.
// Compile-verified only this pass; no physical device was connected this
// session, so on-device visual confirmation is still owed as a follow-up.

private val thumbsUpShapes = listOf(
    Shape2D.FilledPath("M28,32 V72 C28,74.2 26.2,76 24,76 H16 C13.8,76 12,74.2 12,72 V38 C12,35.8 13.8,34 16,34 H24 Z", 0xFFE0A655),
    Shape2D.FilledPath(
        "M32,34 H56 C60,34 63,37.2 63,41.2 C63,42.6 62.6,44 61.9,45.1 C64.3,46.1 66,48.5 66,51.2 C66,53.4 64.9,55.3 63.2,56.6 " +
            "C64.3,58 65,59.8 65,61.7 C65,64.5 63.4,66.9 61.1,68.1 C61.4,68.9 61.6,69.8 61.6,70.7 C61.6,74.7 58.3,78 54.3,78 H36 C33.8,78 32,76.2 32,74 V34 Z",
        0xFFFFCC4D,
    ),
    Shape2D.StrokedPath("M32,34 L38,16 C39,12.6 42.1,10.3 45.6,10.3 C47,10.3 48,11.5 47.8,12.9 L45.6,28", 0xFFFFCC4D, 6f, StrokeCap.Round),
)

// Real shared-seam two-facet construction (project_itunda_own_icons_graphics.md's
// own "itundaface's own signature" section) -- one outer heart silhouette split by
// a shared seam into a lit right facet + a shaded left facet, the same technique
// itunda's own app-icon mark uses. Not a single gradient fill.
private val heartShapes = listOf(
    Shape2D.FilledPath("M40,18.4 C42,14.4 46.4,10 53.4,10 C62.9,10 72,17.7 72,29.6 C72,52.6 40,74 40,74 L40,18.4 Z", 0xFFEF4A63),
    Shape2D.FilledPath("M40,18.4 C38,14.4 33.6,10 26.6,10 C17.1,10 8,17.7 8,29.6 C8,52.6 40,74 40,74 L40,18.4 Z", 0xFFC72E4C),
)

private val laughingShapes = listOf(
    Shape2D.FilledCircle(40f, 40f, 34f, 0xFFFFCC4D),
    Shape2D.StrokedPath("M18,32 C21,26 27,26 30,32", 0xFF664500, 4.4f, StrokeCap.Round),
    Shape2D.StrokedPath("M50,32 C53,26 59,26 62,32", 0xFF664500, 4.4f, StrokeCap.Round),
    Shape2D.FilledPath("M16,48 C16,48 24,66 40,66 C56,66 64,48 64,48 C64,48 56,54 40,54 C24,54 16,48 16,48 Z", 0xFF66471B),
    // Real itunda-indigo tear accent (identity-signature work) -- a tear's exact
    // hue isn't meaning-bearing the way the heart's red is, so it's the one
    // deliberate real-brand-color touch on a secondary element.
    Shape2D.StrokedPath("M23,52 C23,52 26,60 25,66", 0xFF7472F4, 4f, StrokeCap.Round),
)

private val wowShapes = listOf(
    Shape2D.FilledCircle(40f, 40f, 34f, 0xFFFFCC4D),
    Shape2D.FilledCircle(26f, 34f, 5f, 0xFF664500),
    Shape2D.FilledCircle(54f, 34f, 5f, 0xFF664500),
    Shape2D.FilledEllipse(40f, 56f, 9f, 11f, 0xFF66471B),
)

private val sadShapes = listOf(
    Shape2D.FilledCircle(40f, 40f, 34f, 0xFFFFCC4D),
    Shape2D.StrokedPath("M20,32 C23,36 29,36 32,32", 0xFF664500, 4.4f, StrokeCap.Round),
    Shape2D.StrokedPath("M48,32 C51,36 57,36 60,32", 0xFF664500, 4.4f, StrokeCap.Round),
    Shape2D.StrokedPath("M26,62 C30,54 50,54 54,62", 0xFF664500, 4.4f, StrokeCap.Round),
    Shape2D.FilledPath("M48,40 C51,44 54,49 54,53.6 C54,57.6 51,60.6 48,60.6 C45,60.6 42,57.6 42,53.6 C42,49 45,44 48,40 Z", 0xFF7472F4),
)

@Composable fun ReactionThumbsUp(size: Dp = 24.dp) = ItundaFaceGlyphCanvas(size, 80f, thumbsUpShapes)
@Composable fun ReactionHeart(size: Dp = 24.dp) = ItundaFaceGlyphCanvas(size, 80f, heartShapes)
@Composable fun ReactionLaughing(size: Dp = 24.dp) = ItundaFaceGlyphCanvas(size, 80f, laughingShapes)
@Composable fun ReactionWow(size: Dp = 24.dp) = ItundaFaceGlyphCanvas(size, 80f, wowShapes)
@Composable fun ReactionSad(size: Dp = 24.dp) = ItundaFaceGlyphCanvas(size, 80f, sadShapes)

// Maps QUICK_REACTIONS' own real emoji identifiers to itundaface's glyphs --
// display-layer only, the stored/toggled `emoji` identifier sent to the reaction
// toggle API is unchanged.
private val ITUNDAFACE_REACTIONS: Map<String, List<Shape2D>> = mapOf(
    "👍" to thumbsUpShapes,
    "❤️" to heartShapes,
    "😂" to laughingShapes,
    "😮" to wowShapes,
    "😢" to sadShapes,
)

/** Renders itundaface's own glyph for a known reaction emoji, falling back to
 * nothing (no shapes drawn) for anything outside the 5 known quick-reactions --
 * there's no raw-emoji-glyph fallback path left to match once the source string
 * itself no longer round-trips through a real Unicode glyph on screen. */
@Composable
fun ReactionGlyph(emoji: String, size: Dp = 24.dp, modifier: Modifier = Modifier) {
    val shapes = ITUNDAFACE_REACTIONS[emoji] ?: return
    ItundaFaceGlyphCanvas(size, 80f, shapes, modifier)
}
