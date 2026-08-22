package rw.itunda.feature.talk.impl

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import rw.itunda.core.designsystem.itundaface.ItundaFaceGlyphCanvas
import rw.itunda.core.designsystem.itundaface.Shape2D

// itundaface Smileys & Emotion -- Android port of the same phase-1 batch shipped
// to bank-mfe same session (icons/ItundaFaceSmileys.tsx), part of the new
// "reach TossFace's 3,600-glyph scale" initiative (see
// project_itunda_own_icons_graphics.md's staged category roadmap -- this is
// NOT the older map-POI "Places" glyph group). Every shape's "d"/coordinate
// data is copy-pasted byte-identical from the real, already-rsvg-verified web
// source, same discipline as every prior Android itundaface batch. Lives in
// talk/impl (not core/designsystem) because, like ItundaFaceReactions.kt, chat
// is the one real consumer today -- same single-feature-module exception.
//
// Two small, deliberate simplifications vs. the web version, both cosmetic-only
// (neither affects legibility): SmileyCool's glass-glare highlight ellipses are
// dropped (Shape2D.FilledEllipse has no alpha channel, and adding one just for
// two decorative highlights isn't worth widening the shared primitive yet).

private val LINE = 0xFF664500L
private val TEAR = 0xFF7472F4L // itunda's own real indigo, same signature accent as ItundaFaceReactions' laughing/sad tear.
private fun face() = Shape2D.FilledCircle(40f, 40f, 34f, 0xFFFFCC4DL)

private val grinningShapes = listOf(
    face(),
    Shape2D.FilledCircle(26f, 34f, 4f, LINE),
    Shape2D.FilledCircle(54f, 34f, 4f, LINE),
    Shape2D.FilledPath("M20,48 C20,48 26,64 40,64 C54,64 60,48 60,48 C60,48 54,56 40,56 C26,56 20,48 20,48 Z", 0xFF66471BL),
    Shape2D.FilledPath("M25,49.5 C25,49.5 31,53.5 40,53.5 C49,53.5 55,49.5 55,49.5 L55,52.2 C55,52.2 49,55.5 40,55.5 C31,55.5 25,52.2 25,52.2 Z", 0xFFFFFFFFL),
)

private val grinningEyesShapes = listOf(
    face(),
    Shape2D.StrokedPath("M18,32 C21,26 27,26 30,32", LINE, 4.4f, StrokeCap.Round),
    Shape2D.StrokedPath("M50,32 C53,26 59,26 62,32", LINE, 4.4f, StrokeCap.Round),
    Shape2D.FilledPath("M22,48 C22,48 28,62 40,62 C52,62 58,48 58,48 C58,48 52,55 40,55 C28,55 22,48 22,48 Z", 0xFF66471BL),
)

private val slightShapes = listOf(
    face(),
    Shape2D.FilledCircle(26f, 34f, 3.6f, LINE),
    Shape2D.FilledCircle(54f, 34f, 3.6f, LINE),
    Shape2D.StrokedPath("M28,52 C32,58 48,58 52,52", LINE, 4.4f, StrokeCap.Round),
)

private val winkShapes = listOf(
    face(),
    Shape2D.FilledCircle(26f, 34f, 4f, LINE),
    Shape2D.StrokedPath("M48,32 C51,36 57,36 60,32", LINE, 4.4f, StrokeCap.Round),
    Shape2D.StrokedPath("M26,50 C30,58 50,58 54,50", LINE, 4.4f, StrokeCap.Round),
)

private val heartEyesShapes = listOf(
    face(),
    Shape2D.FilledPath("M26,30.6 C27.2,28.2 29.6,27 31.6,28.4 C33.6,29.8 33.6,32.6 31.6,34.6 C30,36.2 26,38.6 26,38.6 C26,38.6 22,36.2 20.4,34.6 C18.4,32.6 18.4,29.8 20.4,28.4 C22.4,27 24.8,28.2 26,30.6 Z", 0xFFEF4A63L),
    Shape2D.FilledPath("M54,30.6 C55.2,28.2 57.6,27 59.6,28.4 C61.6,29.8 61.6,32.6 59.6,34.6 C58,36.2 54,38.6 54,38.6 C54,38.6 50,36.2 48.4,34.6 C46.4,32.6 46.4,29.8 48.4,28.4 C50.4,27 52.8,28.2 54,30.6 Z", 0xFFEF4A63L),
    Shape2D.StrokedPath("M26,50 C30,58 50,58 54,50", LINE, 4.4f, StrokeCap.Round),
)

private val kissHeartShapes = listOf(
    face(),
    Shape2D.StrokedPath("M18,32 C21,26 27,26 30,32", LINE, 4.4f, StrokeCap.Round),
    Shape2D.FilledCircle(54f, 34f, 4f, LINE),
    Shape2D.FilledEllipse(38f, 54f, 5f, 4f, 0xFFC94F63L),
    Shape2D.FilledPath("M62,38 C63.5,35.6 66.6,34.7 69,36.2 C71.4,37.7 72.1,41 70.6,43.4 C68.6,46.6 62,50 62,50 C62,50 60.8,42.6 62,38 Z", 0xFFEF4A63L),
)

private val sleepingShapes = listOf(
    face(),
    Shape2D.StrokedPath("M19,34 C22,31.4 28,31.4 31,34", LINE, 4f, StrokeCap.Round),
    Shape2D.StrokedPath("M49,34 C52,31.4 58,31.4 61,34", LINE, 4f, StrokeCap.Round),
    Shape2D.StrokedPath("M32,54 C35,56.4 45,56.4 48,54", LINE, 3.6f, StrokeCap.Round),
    Shape2D.StrokedPath("M56,16 L64,16 L55,25 L64,25", LINE, 2.6f, StrokeCap.Round),
    Shape2D.StrokedPath("M64,10 L70,10 L63,17 L70,17", LINE, 2.2f, StrokeCap.Round, alpha = 0.7f),
)

private val loudlyCryingShapes = listOf(
    face(),
    Shape2D.StrokedPath("M19,35 C22,31 28,31 31,35", LINE, 4.2f, StrokeCap.Round),
    Shape2D.StrokedPath("M49,35 C52,31 58,31 61,35", LINE, 4.2f, StrokeCap.Round),
    Shape2D.FilledEllipse(40f, 58f, 11f, 9f, 0xFF5C3D15L),
    Shape2D.StrokedPath("M23,36 C23,36 21,50 15,58 C15,58 22,55 25,60", TEAR, 4.4f, StrokeCap.Round),
    Shape2D.StrokedPath("M57,36 C57,36 59,50 65,58 C65,58 58,55 55,60", TEAR, 4.4f, StrokeCap.Round),
)

private val angryShapes = listOf(
    face(),
    Shape2D.StrokedPath("M18,29 L32,34", 0xFF7A4D15L, 4.4f, StrokeCap.Round),
    Shape2D.StrokedPath("M62,29 L48,34", 0xFF7A4D15L, 4.4f, StrokeCap.Round),
    Shape2D.FilledCircle(27f, 37f, 3.6f, LINE),
    Shape2D.FilledCircle(53f, 37f, 3.6f, LINE),
    Shape2D.StrokedPath("M28,58 C32,53 48,53 52,58", LINE, 4.4f, StrokeCap.Round),
)

private val coolShapes = listOf(
    face(),
    Shape2D.FilledRect(15f, 30f, 21f, 12f, 6f, 0xFF20242CL),
    Shape2D.FilledRect(44f, 30f, 21f, 12f, 6f, 0xFF20242CL),
    Shape2D.StrokedPath("M28,52 C32,58 48,58 52,52", LINE, 4.4f, StrokeCap.Round),
)

@Composable fun SmileyGrinning(size: Dp = 24.dp, modifier: Modifier = Modifier) = ItundaFaceGlyphCanvas(size, 80f, grinningShapes, modifier)
@Composable fun SmileyGrinningEyes(size: Dp = 24.dp, modifier: Modifier = Modifier) = ItundaFaceGlyphCanvas(size, 80f, grinningEyesShapes, modifier)
@Composable fun SmileySlight(size: Dp = 24.dp, modifier: Modifier = Modifier) = ItundaFaceGlyphCanvas(size, 80f, slightShapes, modifier)
@Composable fun SmileyWink(size: Dp = 24.dp, modifier: Modifier = Modifier) = ItundaFaceGlyphCanvas(size, 80f, winkShapes, modifier)
@Composable fun SmileyHeartEyes(size: Dp = 24.dp, modifier: Modifier = Modifier) = ItundaFaceGlyphCanvas(size, 80f, heartEyesShapes, modifier)
@Composable fun SmileyKissHeart(size: Dp = 24.dp, modifier: Modifier = Modifier) = ItundaFaceGlyphCanvas(size, 80f, kissHeartShapes, modifier)
@Composable fun SmileySleeping(size: Dp = 24.dp, modifier: Modifier = Modifier) = ItundaFaceGlyphCanvas(size, 80f, sleepingShapes, modifier)
@Composable fun SmileyLoudlyCrying(size: Dp = 24.dp, modifier: Modifier = Modifier) = ItundaFaceGlyphCanvas(size, 80f, loudlyCryingShapes, modifier)
@Composable fun SmileyAngry(size: Dp = 24.dp, modifier: Modifier = Modifier) = ItundaFaceGlyphCanvas(size, 80f, angryShapes, modifier)
@Composable fun SmileyCool(size: Dp = 24.dp, modifier: Modifier = Modifier) = ItundaFaceGlyphCanvas(size, 80f, coolShapes, modifier)

/** Real Unicode codepoint -> shape-list registry, the actual data
 * ItundaFaceEmoji.kt's lookup/render/picker pipeline consumes. */
val ITUNDAFACE_SMILEYS: Map<String, List<Shape2D>> = mapOf(
    "😀" to grinningShapes, // 😀
    "😄" to grinningEyesShapes, // 😄
    "🙂" to slightShapes, // 🙂
    "😉" to winkShapes, // 😉
    "😍" to heartEyesShapes, // 😍
    "😘" to kissHeartShapes, // 😘
    "😴" to sleepingShapes, // 😴
    "😭" to loudlyCryingShapes, // 😭
    "😡" to angryShapes, // 😡
    "😎" to coolShapes, // 😎
)
