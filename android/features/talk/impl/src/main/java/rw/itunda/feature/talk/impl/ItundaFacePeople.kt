package rw.itunda.feature.talk.impl

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import rw.itunda.core.designsystem.itundaface.ItundaFaceGlyphCanvas
import rw.itunda.core.designsystem.itundaface.Shape2D

// itundaface People & Body -- Android port of the same phase-2 batch shipped
// to bank-mfe same session (icons/ItundaFacePeople.tsx), part of the "reach
// TossFace's 3,600-glyph scale" initiative. See that file's own doc comment
// for the real design process (2 rsvg-convert iteration passes, 2 of the
// original 8 glyphs cut for not reading clearly) -- every shape's coordinate
// data here is copy-pasted byte-identical from the already-verified web
// source, not re-derived. `RotatedGroup` (already part of the shared
// `Shape2D` vocabulary, added for the globe glyph's meridian) is the direct
// Android equivalent of SVG's `transform="rotate(deg cx cy)"` used throughout
// the web version's finger/hand placement.
//
// Same shared skin-tone palette (fill 0xFFFFCF87, cuff/shade 0xFFE0A655) as
// ItundaFaceReactions.kt's existing ReactionThumbsUp -- itundaface's own
// established hand-glyph convention. Eyes' iris uses real itunda indigo (a
// non-semantic accent, same signature rule as the reaction tear accents).

private const val SKIN = 0xFFFFCF87L
private const val CUFF = 0xFFE0A655L

private val eyesShapes = listOf(
    Shape2D.StrokedEllipse(24f, 42f, 17f, 14f, 0xFFB8B4AAL, 2f),
    Shape2D.FilledEllipse(24f, 42f, 17f, 14f, 0xFFFFFFFFL),
    Shape2D.StrokedEllipse(56f, 42f, 17f, 14f, 0xFFB8B4AAL, 2f),
    Shape2D.FilledEllipse(56f, 42f, 17f, 14f, 0xFFFFFFFFL),
    Shape2D.FilledCircle(26f, 42f, 7f, 0xFF7472F4L),
    Shape2D.FilledCircle(58f, 42f, 7f, 0xFF7472F4L),
    Shape2D.FilledCircle(28f, 39f, 2f, 0xFFFFFFFFL),
    Shape2D.FilledCircle(60f, 39f, 2f, 0xFFFFFFFFL),
)

private val fistShapes = listOf(
    Shape2D.FilledPath("M28,32 V72 C28,74.2 26.2,76 24,76 H16 C13.8,76 12,74.2 12,72 V38 C12,35.8 13.8,34 16,34 H24 Z", CUFF),
    Shape2D.FilledPath(
        "M32,34 H56 C60,34 63,37.2 63,41.2 C63,42.6 62.6,44 61.9,45.1 C64.3,46.1 66,48.5 66,51.2 C66,53.4 64.9,55.3 63.2,56.6 " +
            "C64.3,58 65,59.8 65,61.7 C65,64.5 63.4,66.9 61.1,68.1 C61.4,68.9 61.6,69.8 61.6,70.7 C61.6,74.7 58.3,78 54.3,78 H36 C33.8,78 32,76.2 32,74 V34 Z",
        SKIN,
    ),
    Shape2D.FilledPath("M32,40 C27,38 23,42 24,47 C25,51 31,53 35,50 C38,47 37,42 32,40 Z", SKIN),
)

private val wavingHandShapes = listOf(
    Shape2D.FilledRect(26f, 64f, 28f, 16f, 8f, CUFF),
    Shape2D.FilledEllipse(40f, 50f, 17f, 20f, SKIN),
    Shape2D.RotatedGroup(-55f, 15f, 53f, listOf(Shape2D.FilledRect(10f, 42f, 10f, 22f, 5f, SKIN))),
    Shape2D.RotatedGroup(-22f, 22.5f, 27f, listOf(Shape2D.FilledRect(18f, 14f, 9f, 26f, 4.5f, SKIN))),
    Shape2D.RotatedGroup(-8f, 32.5f, 23f, listOf(Shape2D.FilledRect(28f, 8f, 9f, 30f, 4.5f, SKIN))),
    Shape2D.RotatedGroup(6f, 43.5f, 22f, listOf(Shape2D.FilledRect(39f, 6f, 9f, 32f, 4.5f, SKIN))),
    Shape2D.RotatedGroup(20f, 54.5f, 24f, listOf(Shape2D.FilledRect(50f, 10f, 9f, 28f, 4.5f, SKIN))),
)

private val victoryHandShapes = listOf(
    Shape2D.FilledRect(24f, 66f, 28f, 14f, 7f, CUFF),
    Shape2D.FilledEllipse(38f, 54f, 19f, 17f, SKIN),
    Shape2D.FilledEllipse(22f, 56f, 7f, 10f, SKIN),
    Shape2D.RotatedGroup(-8f, 31.5f, 28f, listOf(Shape2D.FilledRect(26f, 10f, 11f, 36f, 5.5f, SKIN))),
    Shape2D.RotatedGroup(8f, 45.5f, 28f, listOf(Shape2D.FilledRect(40f, 10f, 11f, 36f, 5.5f, SKIN))),
)

private val okHandShapes = listOf(
    Shape2D.FilledRect(34f, 60f, 28f, 14f, 7f, CUFF),
    Shape2D.FilledEllipse(46f, 46f, 19f, 18f, SKIN),
    Shape2D.StrokedCircle(30f, 28f, 13f, SKIN, 11f),
    Shape2D.RotatedGroup(-16f, 49.5f, 25f, listOf(Shape2D.FilledRect(44f, 10f, 11f, 30f, 5.5f, SKIN))),
    Shape2D.RotatedGroup(2f, 60.5f, 24f, listOf(Shape2D.FilledRect(55f, 8f, 11f, 32f, 5.5f, SKIN))),
    Shape2D.RotatedGroup(20f, 71.5f, 27f, listOf(Shape2D.FilledRect(66f, 12f, 11f, 30f, 5.5f, SKIN))),
)

private val clappingHandsShapes = listOf(
    Shape2D.RotatedGroup(-25f, 26f, 46f, listOf(Shape2D.FilledEllipse(26f, 46f, 16f, 20f, SKIN))),
    Shape2D.RotatedGroup(25f, 54f, 46f, listOf(Shape2D.FilledEllipse(54f, 46f, 16f, 20f, SKIN))),
    Shape2D.RotatedGroup(-30f, 19.5f, 24f, listOf(Shape2D.FilledRect(16f, 14f, 7f, 20f, 3.5f, SKIN))),
    Shape2D.RotatedGroup(-12f, 27.5f, 19f, listOf(Shape2D.FilledRect(24f, 8f, 7f, 22f, 3.5f, SKIN))),
    Shape2D.RotatedGroup(12f, 52.5f, 19f, listOf(Shape2D.FilledRect(49f, 8f, 7f, 22f, 3.5f, SKIN))),
    Shape2D.RotatedGroup(30f, 60.5f, 24f, listOf(Shape2D.FilledRect(57f, 14f, 7f, 20f, 3.5f, SKIN))),
    Shape2D.StrokedLine(40f, 2f, 40f, 12f, 0xFF7472F4L, 3f, StrokeCap.Round),
    Shape2D.StrokedLine(30f, 6f, 35f, 14f, 0xFF7472F4L, 3f, StrokeCap.Round),
    Shape2D.StrokedLine(50f, 6f, 45f, 14f, 0xFF7472F4L, 3f, StrokeCap.Round),
)

@Composable fun PeopleEyes(size: Dp = 24.dp, modifier: Modifier = Modifier) = ItundaFaceGlyphCanvas(size, 80f, eyesShapes, modifier)
@Composable fun PeopleFist(size: Dp = 24.dp, modifier: Modifier = Modifier) = ItundaFaceGlyphCanvas(size, 80f, fistShapes, modifier)
@Composable fun PeopleWavingHand(size: Dp = 24.dp, modifier: Modifier = Modifier) = ItundaFaceGlyphCanvas(size, 80f, wavingHandShapes, modifier)
@Composable fun PeopleVictoryHand(size: Dp = 24.dp, modifier: Modifier = Modifier) = ItundaFaceGlyphCanvas(size, 80f, victoryHandShapes, modifier)
@Composable fun PeopleOkHand(size: Dp = 24.dp, modifier: Modifier = Modifier) = ItundaFaceGlyphCanvas(size, 80f, okHandShapes, modifier)
@Composable fun PeopleClappingHands(size: Dp = 24.dp, modifier: Modifier = Modifier) = ItundaFaceGlyphCanvas(size, 80f, clappingHandsShapes, modifier)
