package rw.itunda.core.designsystem.itundaface

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// itundaface Family/Warning/Parking/BarChart -- real new glyphs sourced
// 2026-08-26, same batch and pipeline as ItundaFaceWork.kt's own doc comment
// (github.com/googlefonts/noto-emoji, SIL OFL, path "d" data copy-pasted
// byte-identical from emoji_u1f46a/26a0/1f17f/1f4ca.svg, fetched fresh this
// session). Two real simplifications, consistent with itundaface's existing
// practice elsewhere (see e.g. ItundaFaceTravel.kt's globe doc comment):
// warning's two near-identical #424242->#212121 linearGradients are flattened
// to one flat midpoint color (both stops are already close greys, not a
// meaningful gradient), and both this file's chart-style glyphs (BarChart)
// drop the source SVG's plain white background rect + thin outer frame
// border, same real "frame is decorative, not load-bearing" call already
// made for ChartIncreasingGlyph.

private val familyShapes = listOf(
    Shape2D.FilledPath("M116,4H12c-4.42,0-8,3.58-8,8v104c0,4.42,3.58,8,8,8h104c4.42,0,8-3.58,8-8V12C124,7.58,120.42,4,116,4z", 0xFFBDBDBD),
    Shape2D.FilledPath(
        "M109.7,4H11.5C7.37,4.03,4.03,7.37,4,11.5v97.9c-0.01,4.14,3.34,7.49,7.48,7.5c0.01,0,0.01,0,0.02,0h98.1c4.14,0.01,7.49-3.34,7.5-7.48c0-0.01,0-0.01,0-0.02V11.5c0.09-4.05-3.13-7.41-7.18-7.5C109.85,4,109.77,4,109.7,4z",
        0xFFE0E0E0,
    ),
    Shape2D.FilledPath(
        "M39.7,12.9c0-2.3-1.6-3-10.8-2.7c-7.7,0.3-11.5,1.2-13.8,4c-1.9,2.3-2.3,5.6-2.6,8.4c-0.2,2.2-2.2,14.9,3.5,11.2c0.68-0.45,1.23-1.07,1.6-1.8c1.2-2.1,1.9-3.5,3.3-5.6C26.2,17.8,39.7,15.9,39.7,12.9z",
        0xFFFFFFFF,
    ),
    Shape2D.FilledPath(
        "M53.72,98.07c-4.01-3.05-6.62-7.85-6.62-13.27c0-8.4,6.25-15.34,14.33-16.49c-0.57-7.97-7.08-11.6-18.85-11.6h-7.61c-10.64,0-17.35,4.71-17.35,13.93v25.13c0,3.83,3.11,6.94,6.94,6.94h22.22h0C52.01,102.72,53.72,98.07,53.72,98.07z",
        0xFF000000,
    ),
    Shape2D.FilledCircle(39.55f, 37.88f, 15.22f, 0xFF000000),
    Shape2D.FilledPath(
        "M92.56,56.72h-7.61c-11.77,0-18.28,3.64-18.85,11.6c8.09,1.14,14.35,8.09,14.35,16.49c0,5.41-2.61,10.22-6.62,13.27c0,0,1.7,4.64,6.94,4.64h0h22.21c3.83,0,6.94-3.11,6.94-6.94V70.65C109.91,61.43,103.19,56.72,92.56,56.72z",
        0xFF000000,
    ),
    Shape2D.FilledCircle(87.97f, 37.88f, 15.22f, 0xFF000000),
    Shape2D.FilledCircle(63.79f, 84.81f, 12.27f, 0xFF000000),
    Shape2D.FilledPath(
        "M51.6,110.77c-1,0-1.75-0.95-1.49-1.91c1.36-4.97,6.95-9.01,13.65-9.01c6.73,0,12.33,3.93,13.67,9.02c0.25,0.96-0.49,1.91-1.49,1.91C75.94,110.77,51.6,110.77,51.6,110.77z",
        0xFF000000,
    ),
)

private val warningShapes = listOf(
    Shape2D.FilledPath(
        "M57.16,8.42l-52,104c-1.94,4.02-0.26,8.85,3.75,10.79c1.08,0.52,2.25,0.8,3.45,0.81h104c4.46-0.04,8.05-3.69,8.01-8.15c-0.01-1.19-0.29-2.37-0.81-3.45l-52-104c-2.01-3.98-6.87-5.57-10.84-3.56C59.18,5.64,57.94,6.89,57.16,8.42z",
        0xFFF2A600,
    ),
    Shape2D.FilledPath(
        "M53.56,15.72l-48.8,97.4c-1.83,3.77-0.25,8.31,3.52,10.14c0.99,0.48,2.08,0.74,3.18,0.76h97.5c4.17-0.04,7.52-3.45,7.48-7.62c-0.01-1.14-0.28-2.26-0.78-3.28l-48.7-97.4c-1.79-3.7-6.23-5.25-9.93-3.47C55.51,12.99,54.29,14.21,53.56,15.72z",
        0xFFFFCC32,
    ),
    // Real flat-color resolution of the source's #424242->#212121 gradient (see file doc comment).
    Shape2D.FilledPath(
        "M64.36,34.02c4.6,0,8.3,3.7,8,8l-3.4,48c-0.38,2.54-2.74,4.3-5.28,3.92c-2.03-0.3-3.62-1.89-3.92-3.92l-3.4-48C56.06,37.72,59.76,34.02,64.36,34.02z",
        0xFF313131,
    ),
    Shape2D.FilledCircle(64.36f, 104.02f, 6f, 0xFF313131),
    Shape2D.FilledPath(
        "M53.56,23.02c-1.2,1.5-21.4,41-21.4,41s-1.8,3,0.7,4.7c2.3,1.6,4.4-0.3,5.3-1.8s19.2-36.9,19.9-38.6c0.6-1.87,0.18-3.91-1.1-5.4C55.66,21.72,54.36,21.92,53.56,23.02z",
        0xFFFFF170,
    ),
    Shape2D.FilledCircle(31.36f, 75.33f, 3.3f, 0xFFFFF170),
)

private val parkingShapes = listOf(
    Shape2D.FilledPath("M116,4H12c-4.42,0-8,3.58-8,8v104c0,4.42,3.58,8,8,8h104c4.42,0,8-3.58,8-8V12C124,7.58,120.42,4,116,4z", 0xFF427687),
    Shape2D.FilledPath(
        "M109.7,4H11.5C7.37,4.03,4.03,7.37,4,11.5v97.9c-0.01,4.14,3.34,7.49,7.48,7.5c0.01,0,0.01,0,0.02,0h98.1c4.14,0.01,7.49-3.34,7.5-7.48c0-0.01,0-0.01,0-0.02V11.5c0.09-4.05-3.13-7.41-7.18-7.5C109.85,4,109.77,4,109.7,4z",
        0xFF8CAFBF,
    ),
    Shape2D.FilledPath(
        "M46.4,77.3V106c0,1.1-0.9,2-2,2H33.1c-1.1,0-2-0.9-2-2V22c0-1.1,0.9-2,2-2h31.7c9.8,0,17.6,2.6,23.4,7.7s8.7,11.9,8.7,20.3c0,8.6-2.8,15.3-8.5,20.2s-13.6,7.2-23.8,7.2H48.4c-1.06-0.04-1.95,0.78-2,1.84C46.4,77.26,46.4,77.28,46.4,77.3z M46.4,61.1c0,1.1,0.9,2,2,2h16.4c5.4,0,9.6-1.3,12.4-3.8s4.3-6.3,4.3-11.1s-1.4-8.5-4.3-11.4s-6.9-4.3-12-4.4H48.4c-1.1,0-2,0.9-2,2V61.1z",
        0xFFFAFAFA,
    ),
    // Real flat-color resolution of a 0.5-opacity B4E1ED-over-8CAFBF fill (see file doc comment).
    Shape2D.FilledPath(
        "M39.7,12.9c0-2.3-1.6-3-10.8-2.7c-7.7,0.3-11.5,1.2-13.8,4s-2.9,8.5-3,15.3c0,4.8,0,9.3,2.5,9.3c3.4,0,3.4-7.9,6.2-12.3C26.2,17.8,39.7,15.9,39.7,12.9z",
        0xFFA0C8D6,
    ),
)

private val barChartShapes = listOf(
    Shape2D.StrokedLine(124f, 24.35f, 5.57f, 24.35f, 0xFFB0BEC5, 2f),
    Shape2D.StrokedLine(124f, 43.67f, 5.47f, 43.67f, 0xFFB0BEC5, 2f),
    Shape2D.StrokedLine(124f, 62.99f, 5.36f, 62.99f, 0xFFB0BEC5, 2f),
    Shape2D.StrokedLine(124f, 82.31f, 5.26f, 82.31f, 0xFFB0BEC5, 2f),
    Shape2D.StrokedLine(124f, 101.64f, 5.15f, 101.64f, 0xFFB0BEC5, 2f),
    Shape2D.FilledPath("M38.72,121.91H21.89V55.38c0-1.36,1.1-2.46,2.46-2.46h11.91c1.36,0,2.46,1.1,2.46,2.46V121.91z", 0xFF9CCC65),
    Shape2D.FilledPath("M72.42,121.91H55.58V74.84c0-1.36,1.1-2.46,2.46-2.46h11.91c1.36,0,2.46,1.1,2.46,2.46V121.91z", 0xFFF44336),
    Shape2D.FilledPath(
        "M103.64,121.91H91.73c-1.36,0-2.46-1.1-2.46-2.46V25.86c0-1.36,1.1-2.46,2.46-2.46h11.91c1.36,0,2.46,1.1,2.46,2.46v93.59C106.1,120.81,105,121.91,103.64,121.91z",
        0xFF0091EA,
    ),
)

@Composable fun FamilyGlyph(size: Dp = 24.dp, modifier: Modifier = Modifier) = ItundaFaceGlyphCanvas(size, 128f, familyShapes, modifier)
@Composable fun WarningGlyph(size: Dp = 24.dp, modifier: Modifier = Modifier) = ItundaFaceGlyphCanvas(size, 128f, warningShapes, modifier)
@Composable fun ParkingGlyph(size: Dp = 24.dp, modifier: Modifier = Modifier) = ItundaFaceGlyphCanvas(size, 128f, parkingShapes, modifier)
@Composable fun BarChartGlyph(size: Dp = 24.dp, modifier: Modifier = Modifier) = ItundaFaceGlyphCanvas(size, 128f, barChartShapes, modifier)
