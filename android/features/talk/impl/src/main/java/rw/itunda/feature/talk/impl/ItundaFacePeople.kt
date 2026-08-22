package rw.itunda.feature.talk.impl

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import rw.itunda.core.designsystem.itundaface.ItundaFaceGlyphCanvas
import rw.itunda.core.designsystem.itundaface.Shape2D

// itundaface People & Body -- Android port of the same phase-2 batch shipped
// to bank-mfe same session. See ItundaFacePeople.tsx's own doc comment for
// the full real sourcing story: after 6 hand-authored iteration passes still
// didn't reach real quality, researched and confirmed Google's real Noto
// Emoji (github.com/googlefonts/noto-emoji) is SIL Open Font License 1.1 --
// genuinely permissive, unlike TossFace's restrictive terms itundaface has
// deliberately avoided all session. The 7 hand-gesture silhouette paths
// below are Noto's own real, professionally-drawn outer-silhouette path
// data, fetched directly from their public repo and recolored to itunda's
// palette -- not hand-approximated. Kept Noto's native 128x128 viewBox
// (ItundaFaceGlyphCanvas already supports an arbitrary viewBoxSize) rather
// than transforming every coordinate, so the path data stays byte-identical
// to the real source, same "provably identical to source" discipline as
// every itundaface glyph's own "d" data.
//
// REQUIRED ATTRIBUTION: see /NOTICE_THIRD_PARTY.md at the repo root (OFL
// clause 2 requires the license notice travel with any copy).
//
// Eyes keeps its original hand-authored construction -- it already read
// clearly from the first pass, no Noto source was needed there.

private const val SKIN = 0xFFFFCF87L
private const val INDIGO = 0xFF7472F4L

private val eyesShapes = listOf(
    Shape2D.StrokedEllipse(24f, 42f, 17f, 14f, 0xFFB8B4AAL, 2f),
    Shape2D.FilledEllipse(24f, 42f, 17f, 14f, 0xFFFFFFFFL),
    Shape2D.StrokedEllipse(56f, 42f, 17f, 14f, 0xFFB8B4AAL, 2f),
    Shape2D.FilledEllipse(56f, 42f, 17f, 14f, 0xFFFFFFFFL),
    Shape2D.FilledCircle(26f, 42f, 7f, INDIGO),
    Shape2D.FilledCircle(58f, 42f, 7f, INDIGO),
    Shape2D.FilledCircle(28f, 39f, 2f, 0xFFFFFFFFL),
    Shape2D.FilledCircle(60f, 39f, 2f, 0xFFFFFFFFL),
)

// Real Noto Emoji silhouette (u1F44B.svg), recolored.
private val wavingHandShapes = listOf(
    Shape2D.FilledPath(
        "M93.3,60c0.2,0.3,0.7,0.1,0.7-0.2c0.6-5.4,2.2-20.3,12.8-23.5c3.4-1,6.8,1.4,7.2,4.5c0.7,5-5,17.9-4.5,29.7c0.1,1.9,3.3,22-5.2,33.9s-28.7,24-48.8,5.6c-10.4-9.5-10.4-13.3-23.3-26.6c-2.6-2.6-13-14-15.8-17.5c-3.7-4.7,2.2-10.9,6.7-7.7c2.1,1.5,20.7,17.1,21.4,17.8c1.4,1.2,3.1-0.5,2.1-1.7c-11.4-15-22.4-28.5-25.4-33.7s4.2-10.8,8.4-6.3c2.9,3,24.4,28.1,25.4,29.2s2.7-0.3,2.1-1.7C56.4,60.5,39,30.2,35.9,23.3c-2.7-6.1,6.3-11.8,10.5-5.5c3.4,5,22.4,36.6,23.1,37.7c0.9,1.6,2.9,0.6,2.1-1.2C71,53,59.3,21,58.4,17.7c-1.6-5.8,6.7-10.6,10.6-4C74.3,22.8,84.8,50.6,93.3,60z",
        SKIN,
    ),
    Shape2D.StrokedPath("M30,70 C40,80 55,80 65,72", INDIGO, 3.5f, StrokeCap.Round, alpha = 0.55f),
)

// Real Noto Emoji silhouette (u270A.svg), recolored.
private val fistShapes = listOf(
    Shape2D.FilledPath(
        "M105.5,38.7c-0.1-4.5,0-18-0.8-22.7c-0.8-4.6-6-7.6-11.4-7.9C88,7.8,82,9.9,80.3,16.7C78.2,9.8,73,8.5,67.9,8.9C61.7,9.3,55.7,12.4,55.6,19c-1.7-3.9-6-5.6-12.2-4.1c-5,1.2-10.1,4-10.5,12.3c-4.5-3.3-20.7-2.1-20.3,17.3c0.1,5.8-1.9,8-1.6,18.1c1,26.2,15.1,48,31.3,53.7c10.9,3.9,27.1,7.1,44.7-2.7c19.2-10.7,27.9-33.7,29.2-37.5c3.4-9.5,3.8-18.6-2.8-27.4C110.3,44.4,105.5,38.7,105.5,38.7z",
        SKIN,
    ),
    Shape2D.StrokedPath("M35,55 C45,65 60,68 72,60", INDIGO, 4f, StrokeCap.Round, alpha = 0.55f),
)

// Real Noto Emoji silhouette (u270C.svg), recolored.
private val victoryHandShapes = listOf(
    Shape2D.FilledPath(
        "M94.6,75.6c3,3.4,5.8,8.8,4.1,16.8C98,95.7,92,114,78.5,120.1c-15.1,6.8-41.8,6.2-50-24c-4-14.5-2.2-18.3-2.1-30.9c0-9,7.8-14.8,15.3-8.2c-0.8-5,1.7-9.5,5.9-10.8c3.8-1.2,7.3-0.8,10.3,4.3c1-2.2-3.3-26.9-3.3-39.9c0-7.7,11.3-8.8,12.7-2.1c2,9.4,6.8,40.3,7.9,46c0.4,1.9,2.1,1.4,2.4,0.4c1.1-3.6,9.3-36.6,10.5-40.3c2.8-9,14.7-5.8,13.6,3.1c-1.8,13.7-5.5,29.8-7.7,45.5C93.3,67.7,94,71.6,94.6,75.6z",
        SKIN,
    ),
    Shape2D.StrokedPath("M35,90 C45,98 60,98 70,90", INDIGO, 4f, StrokeCap.Round, alpha = 0.55f),
)

// Real Noto Emoji silhouette (u1F44C.svg), recolored.
private val okHandShapes = listOf(
    Shape2D.FilledPath(
        "M60.3,47.1c-2.2-7-5.9-10.8-6.8-14.1c-1.1-3.8,3.3-6.4,5.7-3.3c-1.2-3-5-5.6-5.4-10.8c-0.1-1.6,0.5-4.4,4.2-4.9c-0.7-4.1,0.7-6,2-6.8c1.7-1,3.5-0.5,4.6,0.3c10,7.6,24.3,24.8,31.3,49.1c0.7,2.4,1.2,4.5,1.3,6.6c0.1,1.8,0.3,3.9,0.3,8.3c0,7.8-0.8,17.3-1.8,32.6c0,0.7,0.1,1.7,0.3,2.3c0.3,0.6,0.4,1.2-0.2,2c-4.6,5.1-19.7,13.2-29.3,14.6c-1,0.1-1.4-0.3-1.8-0.7c-4.7-4.2-11.5-4.3-17.9-9.1c-8.4-6.4-15.6-14.6-18.7-30.7c-0.5-2.8,2.1-7,6.9-5.6c10.8,3.3,5.5,14.7,18.8,18.5c10.1,2.9,19.9-5.7,19.8-14c-0.1-6.8-2.4-14.2-11.4-16.3c-11.7-2.8-18.8,11.3-25.6,10.8c-4.4-0.4-6.2-2.4-6.6-4.7C29.5,68.3,32.9,64,42.2,56C46,52.8,52.6,48.6,60.3,47.1z",
        SKIN,
    ),
    Shape2D.StrokedPath("M40,95 C50,102 65,100 75,90", INDIGO, 4f, StrokeCap.Round, alpha = 0.55f),
)

// Real Noto Emoji silhouette (u1F4AA.svg), recolored.
private val muscleShapes = listOf(
    Shape2D.FilledPath(
        "M79.5,28.9c1.9,2,2.3,4.5,1.5,5.9c-0.9,1.6-2,2.4-3.7,2.4c-0.7,0-4-0.1-5.8,1c-2,1.2-6.3,3.3-11.3,2.8c-3.3-0.3-7.6-3.6-9.6-1.2c-1,1.2-2.6,5.7-2.7,12c-0.1,4.4,0.6,19.9-1.4,29.8C46.4,82.3,47,83,47.7,83c1.9-0.1,4.1-0.9,6-5.5c10.1-24.7,51.5-23.1,57.6,4.7c2.3,10.2,5.4,31.2-14.7,37.2c-14.9,4.4-35.9,0.8-47.1-0.1c-7-0.6-32.4-1.7-34.6-15.9c-0.7-4.1,0.3-11.2,1.6-20.6c0.4-3.2,1.4-12.2,4.7-22.1C24,52.7,28,45.3,33,37c1.8-3,4-7.9,9.8-13.9c8.1-8.4,18-14.2,23.7-16.8c4.2-1.9,6.6,1.4,7.7,4c0.9,2.2,6.1,9.2,8.5,12.5c0.7,1,1.2,3.2-0.3,4.2C81,28.1,79.5,28.9,79.5,28.9z",
        SKIN,
    ),
    Shape2D.StrokedPath("M55,45 C62,50 70,50 76,44", INDIGO, 4f, StrokeCap.Round, alpha = 0.55f),
)

// Real Noto Emoji silhouette (u1F64F.svg), recolored.
private val prayShapes = listOf(
    Shape2D.FilledPath(
        "M102.8,89.9l-15.7,20.3c0,0-14.6-8-18.4-10S64,91.4,64,91.4s-0.9,6.8-4.7,8.8s-18.4,10-18.4,10L25.2,89.9c5.4-2.5,8.7-5,10.9-6.8c2.2-1.8,4-4.8,4.8-7.8c0.7-3,3.7-17.6,3.8-20s0.8-5.4,2.3-7.8s2-6.6,3-11.8c1.7-9,3-13.5,3.3-15.7c0.3-2.1,0.5-7.1,0.6-8.7C54,9.9,55.6,8.1,58.2,8c4.1-0.2,5.8,2.2,5.8,5.4c0-3.3,1.7-5.6,5.8-5.4c2.6,0.1,4.1,1.9,4.3,3.5c0.1,1.6,0.3,6.6,0.6,8.7c0.3,2.1,1.6,6.7,3.3,15.7c1,5.2,1.6,9.5,3,11.8s2.2,5.4,2.3,7.8c0.1,2.4,3.1,17,3.8,20c0.8,3,2.6,6,4.8,7.8C94.1,85,97.4,87.4,102.8,89.9z",
        SKIN,
    ),
    Shape2D.StrokedPath("M64,15 L64,105", INDIGO, 3f, StrokeCap.Round, alpha = 0.45f),
)

// Real Noto Emoji silhouette (u1F44F.svg), recolored -- motion lines recolored
// from Noto's own grey to itunda indigo (same non-semantic-accent rule as
// every other secondary element in itundaface, not a literal palette copy).
private val clappingHandsShapes = listOf(
    Shape2D.FilledPath(
        "M99,44.7c1.9-4.5,5.5-7.7,9.7-6.7c2.9,0.6,3.5,3.3,3.3,4.9c-2.3,15.9,1.1,28.1,0.2,39.9c-1.1,14.9-10.5,30.3-30.2,31.7c-12.5,3-26.4,0.9-37.6-10c-6.2-6-11.6-10.6-19.1-22.8c-3.1-5.1-7.9-13.2-7.9-18.5c0-4.5,4.5-5.9,6.8-3.8c-3-4.8-4.4-8.3-4.5-12.4c-0.1-3.4,4.1-5.8,6.8-3.2c-2.3-3.7-5.5-10.2-0.7-13.5c1.5-1,5.3-2.3,9.9,3.5c-1.2-2.3-2.5-6.2,0.7-8.8c1.7-1.4,5-1.8,7.8,1.4c-0.2-1.2,1.5-4.2,4.3-4.3c3.4-0.2,6.1,2.9,8.1,5.2c-2-8,6.3-10.3,10.9-4.9c2.4,2.7,8,9.5,16.4,23.5c-0.2-8.4,3.9-15.8,9.9-14.7c2.1,0.4,4.2,1.8,4.4,5.7C98.6,40.4,99,44.7,99,44.7z",
        SKIN,
    ),
    Shape2D.FilledPath("M19.7,85.2c-1.2,0.1-10.6,0.5-12,0.6c-2.6,0.2-2.4,4.6,0.2,4.5c1.4-0.1,12-0.6,12-0.6C22.7,89.4,22.4,85,19.7,85.2z", 0x997472F4L),
    Shape2D.FilledPath("M29,100.2c-0.4,1.2-3.4,10-3.9,11.4c-0.8,2.5,3.4,3.9,4.2,1.4c0.5-1.4,3.9-11.4,3.9-11.4C34,99.1,29.8,97.7,29,100.2z", 0x997472F4L),
    Shape2D.FilledPath("M111.4,18.4c-0.9,0.8-8.2,6.7-9.2,7.7c-2,1.7,0.8,5.1,2.8,3.4c1.1-0.9,9.2-7.7,9.2-7.7C116.3,20,113.4,16.6,111.4,18.4z", 0x997472F4L),
    Shape2D.FilledPath("M81,12.2c0.4,1.2,2.9,10.2,3.3,11.5c0.7,2.5,5,1.3,4.3-1.2C88.2,21.1,85.3,11,85.3,11C84.5,8.4,80.2,9.6,81,12.2z", 0x997472F4L),
    Shape2D.FilledPath("M23.1,93.1c-1,0.7-14,9.8-15.2,10.7c-2.1,1.5,0.5,5.2,2.6,3.6c1.2-0.9,15.2-10.7,15.2-10.7C27.9,95,25.2,91.4,23.1,93.1z", 0x997472F4L),
    Shape2D.FilledPath("M99.6,5.8C99.2,7,93.8,22,93.4,23.4c-0.8,2.5,3.4,4,4.2,1.5c0.5-1.4,6.2-17.6,6.2-17.6C104.6,4.7,100.4,3.3,99.6,5.8z", 0x997472F4L),
)

@Composable fun PeopleEyes(size: Dp = 24.dp, modifier: Modifier = Modifier) = ItundaFaceGlyphCanvas(size, 80f, eyesShapes, modifier)
@Composable fun PeopleWavingHand(size: Dp = 24.dp, modifier: Modifier = Modifier) = ItundaFaceGlyphCanvas(size, 128f, wavingHandShapes, modifier)
@Composable fun PeopleFist(size: Dp = 24.dp, modifier: Modifier = Modifier) = ItundaFaceGlyphCanvas(size, 128f, fistShapes, modifier)
@Composable fun PeopleVictoryHand(size: Dp = 24.dp, modifier: Modifier = Modifier) = ItundaFaceGlyphCanvas(size, 128f, victoryHandShapes, modifier)
@Composable fun PeopleOkHand(size: Dp = 24.dp, modifier: Modifier = Modifier) = ItundaFaceGlyphCanvas(size, 128f, okHandShapes, modifier)
@Composable fun PeopleMuscle(size: Dp = 24.dp, modifier: Modifier = Modifier) = ItundaFaceGlyphCanvas(size, 128f, muscleShapes, modifier)
@Composable fun PeoplePray(size: Dp = 24.dp, modifier: Modifier = Modifier) = ItundaFaceGlyphCanvas(size, 128f, prayShapes, modifier)
@Composable fun PeopleClappingHands(size: Dp = 24.dp, modifier: Modifier = Modifier) = ItundaFaceGlyphCanvas(size, 128f, clappingHandsShapes, modifier)
