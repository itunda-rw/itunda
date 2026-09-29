package rw.itunda.feature.talk.impl

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import rw.itunda.core.designsystem.itundaface.ItundaFaceGlyphCanvas
import rw.itunda.core.designsystem.itundaface.Shape2D

// itundaface Flags -- phase 9 batch 2 of the "reach TossFace's 3,600-glyph
// scale" initiative, same real picosvg-based extraction pipeline as batch 1
// (ItundaFaceFlags.kt's own doc comment has the full sourcing/technique
// story -- not repeated here). New file since batch 1 already used most of
// its real headroom under the 500-line cap (421 lines for 9 flags).
//
// 10 more real flags, prioritized the same "real itunda relevance first"
// way as batch 1: South Sudan + Somalia complete the East African Community
// (EAC)'s full real 8-member roster started in batch 1 (Rwanda, Kenya,
// Uganda, Tanzania, Burundi, DR Congo already shipped); China, India,
// Belgium, Germany, France, South Africa, Nigeria, and Ethiopia are real
// major global/continental economic and diplomatic partners of Rwanda
// specifically (Belgium: Rwanda's real former colonial administrator;
// Germany: a real, substantial bilateral aid/trade partner; China: major
// real infrastructure investment; South Africa/Nigeria: the continent's two
// largest economies; Ethiopia: RwandAir's real regional hub).
private val flagSouthSudanShapes = listOf(
    Shape2D.FilledPath("M6,35 L122,35 L122,93 L6,93 L6,35 Z", 0xFF078930L),
    Shape2D.FilledPath("M6,35 L122,35 L122,75.6 L6,75.6 L6,35 Z", 0xFFFFFFFFL),
    Shape2D.FilledPath("M6,35 L122,35 L122,52.4 L6,52.4 L6,35 Z", 0xFF000000L),
    Shape2D.FilledPath("M6,55.3 L122,55.3 L122,72.7 L6,72.7 L6,55.3 Z", 0xFFDA121AL),
    Shape2D.FilledPath("M6,35 L56.23,64 L6,93 Z", 0xFF0F47AFL),
    Shape2D.FilledPath("M13.46,64 L30.25,69.45 L19.87,55.17 L19.87,72.83 L30.25,58.55 Z", 0xFFFCDD09L),
)

private val flagSomaliaShapes = listOf(
    Shape2D.FilledPath("M6,25.33 L122,25.33 L122,102.67 L6,102.67 L6,25.33 Z", 0xFF4189DDL),
    Shape2D.FilledPath("M64,45.38 L58.25,63.09 L67.1,65.96 Z", 0xFFFFFFFFL),
    Shape2D.FilledPath("M64,45.38 L69.75,63.09 L60.9,65.96 Z", 0xFFFFFFFFL),
    Shape2D.FilledPath("M81.71,58.25 L63.09,58.25 L63.09,67.56 Z", 0xFFFFFFFFL),
    Shape2D.FilledPath("M81.71,58.25 L66.65,69.19 L61.17,61.66 Z", 0xFFFFFFFFL),
    Shape2D.FilledPath("M46.29,58.25 L61.35,69.19 L66.83,61.66 Z", 0xFFFFFFFFL),
    Shape2D.FilledPath("M46.29,58.25 L64.91,58.25 L64.91,67.56 Z", 0xFFFFFFFFL),
    Shape2D.FilledPath("M74.94,79.06 L69.19,61.35 L60.34,64.23 Z", 0xFFFFFFFFL),
    Shape2D.FilledPath("M74.94,79.06 L59.88,68.12 L65.35,60.59 Z", 0xFFFFFFFFL),
    Shape2D.FilledPath("M53.06,79.06 L68.12,68.12 L62.65,60.59 Z", 0xFFFFFFFFL),
    Shape2D.FilledPath("M53.06,79.06 L58.81,61.35 L67.66,64.23 Z", 0xFFFFFFFFL),
)

private val flagChinaShapes = listOf(
    Shape2D.FilledPath("M6,25.33 L122,25.33 L122,102.67 L6,102.67 L6,25.33 Z", 0xFFDE2910L),
    Shape2D.FilledPath("M25.33,33.07 L32.15,54.05 L14.3,41.08 L36.36,41.08 L18.52,54.05 Z", 0xFFFFDE00L),
    Shape2D.FilledPath("M46.18,29.51 L45.53,36.84 L41.75,30.53 L48.52,33.41 L41.35,35.05 Z", 0xFFFFDE00L),
    Shape2D.FilledPath("M55.18,38.11 L51.74,44.61 L50.7,37.33 L55.82,42.61 L48.57,41.35 Z", 0xFFFFDE00L),
    Shape2D.FilledPath("M56.03,51.07 L50.24,55.61 L52.26,48.54 L54.78,55.44 L48.68,51.34 Z", 0xFFFFDE00L),
    Shape2D.FilledPath("M46.03,56.51 L45.69,63.86 L41.65,57.72 L48.53,60.31 L41.44,62.26 Z", 0xFFFFDE00L),
)

private val flagIndiaShapes = listOf(
    Shape2D.FilledPath("M6,25.33 L122,25.33 L122,102.67 L6,102.67 L6,25.33 Z", 0xFFFF9933L),
    Shape2D.FilledPath("M6,51.11 L122,51.11 L122,76.89 L6,76.89 L6,51.11 Z", 0xFFFFFFFFL),
    Shape2D.FilledPath("M6,76.89 L122,76.89 L122,102.67 L6,102.67 L6,76.89 Z", 0xFF128807L),
    Shape2D.FilledPath("M74.31,64 C74.31,69.69 69.69,74.31 64,74.31 C58.31,74.31 53.69,69.69 53.69,64 C53.69,58.31 58.31,53.69 64,53.69 C69.69,53.69 74.31,58.31 74.31,64 Z", 0xFF000088L),
    Shape2D.FilledPath("M73.02,64 C73.02,68.98 68.98,73.02 64,73.02 C59.02,73.02 54.98,68.98 54.98,64 C54.98,59.02 59.02,54.98 64,54.98 C68.98,54.98 73.02,59.02 73.02,64 Z", 0xFFFFFFFFL),
    Shape2D.FilledPath("M65.8,64 C65.8,65 65,65.8 64,65.8 C63,65.8 62.2,65 62.2,64 C62.2,63 63,62.2 64,62.2 C65,62.2 65.8,63 65.8,64 Z", 0xFF000088L),
    Shape2D.FilledPath("M73.39,65.24 C73.36,65.48 73.13,65.66 72.89,65.63 C72.64,65.59 72.47,65.37 72.5,65.12 C72.53,64.87 72.76,64.7 73,64.73 C73.25,64.76 73.42,64.99 73.39,65.24 Z", 0xFF000088L),
    Shape2D.FilledPath("M64,73.02 L64.31,67.61 C64.31,67.61 64,65.03 64,65.03 C64,65.03 63.69,67.61 63.69,67.61 L64,73.02 Z", 0xFF000088L),
    Shape2D.FilledPath("M72.75,67.63 C72.66,67.86 72.39,67.96 72.16,67.87 C71.93,67.77 71.82,67.51 71.92,67.28 C72.01,67.05 72.28,66.94 72.51,67.04 C72.74,67.13 72.85,67.39 72.75,67.63 Z", 0xFF000088L),
    Shape2D.FilledPath("M61.67,72.71 L63.36,67.57 C63.36,67.57 63.73,65 63.73,65 C63.73,65 62.77,67.41 62.77,67.41 L61.67,72.71 Z", 0xFF000088L),
    Shape2D.FilledPath("M71.52,69.77 C71.36,69.96 71.08,70 70.88,69.85 C70.69,69.7 70.65,69.42 70.8,69.22 C70.95,69.02 71.23,68.98 71.43,69.13 C71.63,69.29 71.67,69.57 71.52,69.77 Z", 0xFF000088L),
    Shape2D.FilledPath("M59.49,71.81 L62.46,67.28 C62.46,67.28 63.48,64.89 63.48,64.89 C63.48,64.89 61.93,66.97 61.93,66.97 L59.49,71.81 Z", 0xFF000088L),
    Shape2D.FilledPath("M69.77,71.52 C69.57,71.67 69.29,71.63 69.13,71.43 C68.98,71.23 69.02,70.95 69.22,70.8 C69.42,70.65 69.7,70.69 69.85,70.88 C70,71.08 69.96,71.36 69.77,71.52 Z", 0xFF000088L),
    Shape2D.FilledPath("M57.62,70.38 L61.67,66.77 C61.67,66.77 63.27,64.73 63.27,64.73 C63.27,64.73 61.23,66.33 61.23,66.33 L57.62,70.38 Z", 0xFF000088L),
    Shape2D.FilledPath("M67.63,72.75 C67.39,72.85 67.13,72.74 67.04,72.51 C66.94,72.28 67.05,72.01 67.28,71.92 C67.51,71.82 67.77,71.93 67.87,72.16 C67.96,72.39 67.86,72.66 67.63,72.75 Z", 0xFF000088L),
    Shape2D.FilledPath("M56.19,68.51 L61.03,66.07 C61.03,66.07 63.11,64.52 63.11,64.52 C63.11,64.52 60.72,65.54 60.72,65.54 L56.19,68.51 Z", 0xFF000088L),
    Shape2D.FilledPath("M65.24,73.39 C64.99,73.42 64.76,73.25 64.73,73 C64.7,72.76 64.87,72.53 65.12,72.5 C65.37,72.47 65.59,72.64 65.63,72.89 C65.66,73.13 65.48,73.36 65.24,73.39 Z", 0xFF000088L),
    Shape2D.FilledPath("M55.29,66.33 L60.59,65.23 C60.59,65.23 63,64.27 63,64.27 C63,64.27 60.43,64.64 60.43,64.64 L55.29,66.33 Z", 0xFF000088L),
    Shape2D.FilledPath("M62.76,73.39 C62.52,73.36 62.34,73.13 62.37,72.89 C62.41,72.64 62.63,72.47 62.88,72.5 C63.13,72.53 63.3,72.76 63.27,73 C63.24,73.25 63.01,73.42 62.76,73.39 Z", 0xFF000088L),
    Shape2D.FilledPath("M54.98,64 L60.39,64.31 C60.39,64.31 62.97,64 62.97,64 C62.97,64 60.39,63.69 60.39,63.69 L54.98,64 Z", 0xFF000088L),
    Shape2D.FilledPath("M60.37,72.75 C60.14,72.66 60.04,72.39 60.13,72.16 C60.23,71.93 60.49,71.82 60.72,71.92 C60.95,72.01 61.06,72.28 60.96,72.51 C60.87,72.74 60.61,72.85 60.37,72.75 Z", 0xFF000088L),
    Shape2D.FilledPath("M55.29,61.67 L60.43,63.36 C60.43,63.36 63,63.73 63,63.73 C63,63.73 60.59,62.77 60.59,62.77 L55.29,61.67 Z", 0xFF000088L),
    Shape2D.FilledPath("M58.23,71.52 C58.04,71.36 58,71.08 58.15,70.88 C58.3,70.69 58.58,70.65 58.78,70.8 C58.98,70.95 59.02,71.23 58.87,71.43 C58.71,71.63 58.43,71.67 58.23,71.52 Z", 0xFF000088L),
    Shape2D.FilledPath("M56.19,59.49 L60.72,62.46 C60.72,62.46 63.11,63.48 63.11,63.48 C63.11,63.48 61.03,61.93 61.03,61.93 L56.19,59.49 Z", 0xFF000088L),
    Shape2D.FilledPath("M56.48,69.77 C56.33,69.57 56.37,69.29 56.57,69.13 C56.77,68.98 57.05,69.02 57.2,69.22 C57.35,69.42 57.31,69.7 57.12,69.85 C56.92,70 56.64,69.96 56.48,69.77 Z", 0xFF000088L),
    Shape2D.FilledPath("M57.62,57.62 L61.23,61.67 C61.23,61.67 63.27,63.27 63.27,63.27 C63.27,63.27 61.67,61.23 61.67,61.23 L57.62,57.62 Z", 0xFF000088L),
    Shape2D.FilledPath("M55.25,67.63 C55.15,67.39 55.26,67.13 55.49,67.04 C55.72,66.94 55.99,67.05 56.08,67.28 C56.18,67.51 56.07,67.77 55.84,67.87 C55.61,67.96 55.34,67.86 55.25,67.63 Z", 0xFF000088L),
    Shape2D.FilledPath("M59.49,56.19 L61.93,61.03 C61.93,61.03 63.48,63.11 63.48,63.11 C63.48,63.11 62.46,60.72 62.46,60.72 L59.49,56.19 Z", 0xFF000088L),
    Shape2D.FilledPath("M54.61,65.24 C54.58,64.99 54.75,64.76 55,64.73 C55.24,64.7 55.47,64.87 55.5,65.12 C55.53,65.37 55.36,65.59 55.11,65.63 C54.87,65.66 54.64,65.48 54.61,65.24 Z", 0xFF000088L),
    Shape2D.FilledPath("M61.67,55.29 L62.77,60.59 C62.77,60.59 63.73,63 63.73,63 C63.73,63 63.36,60.43 63.36,60.43 L61.67,55.29 Z", 0xFF000088L),
    Shape2D.FilledPath("M54.61,62.76 C54.64,62.52 54.87,62.34 55.11,62.37 C55.36,62.41 55.53,62.63 55.5,62.88 C55.47,63.13 55.24,63.3 55,63.27 C54.75,63.24 54.58,63.01 54.61,62.76 Z", 0xFF000088L),
    Shape2D.FilledPath("M64,54.98 L63.69,60.39 C63.69,60.39 64,62.97 64,62.97 C64,62.97 64.31,60.39 64.31,60.39 L64,54.98 Z", 0xFF000088L),
    Shape2D.FilledPath("M55.25,60.37 C55.34,60.14 55.61,60.04 55.84,60.13 C56.07,60.23 56.18,60.49 56.08,60.72 C55.99,60.95 55.72,61.06 55.49,60.96 C55.26,60.87 55.15,60.61 55.25,60.37 Z", 0xFF000088L),
    Shape2D.FilledPath("M66.33,55.29 L64.64,60.43 C64.64,60.43 64.27,63 64.27,63 C64.27,63 65.23,60.59 65.23,60.59 L66.33,55.29 Z", 0xFF000088L),
    Shape2D.FilledPath("M56.48,58.23 C56.64,58.04 56.92,58 57.12,58.15 C57.31,58.3 57.35,58.58 57.2,58.78 C57.05,58.98 56.77,59.02 56.57,58.87 C56.37,58.71 56.33,58.43 56.48,58.23 Z", 0xFF000088L),
    Shape2D.FilledPath("M68.51,56.19 L65.54,60.72 C65.54,60.72 64.52,63.11 64.52,63.11 C64.52,63.11 66.07,61.03 66.07,61.03 L68.51,56.19 Z", 0xFF000088L),
    Shape2D.FilledPath("M58.23,56.48 C58.43,56.33 58.71,56.37 58.87,56.57 C59.02,56.77 58.98,57.05 58.78,57.2 C58.58,57.35 58.3,57.31 58.15,57.12 C58,56.92 58.04,56.64 58.23,56.48 Z", 0xFF000088L),
    Shape2D.FilledPath("M70.38,57.62 L66.33,61.23 C66.33,61.23 64.73,63.27 64.73,63.27 C64.73,63.27 66.77,61.67 66.77,61.67 L70.38,57.62 Z", 0xFF000088L),
    Shape2D.FilledPath("M60.37,55.25 C60.61,55.15 60.87,55.26 60.96,55.49 C61.06,55.72 60.95,55.99 60.72,56.08 C60.49,56.18 60.23,56.07 60.13,55.84 C60.04,55.61 60.14,55.34 60.37,55.25 Z", 0xFF000088L),
    Shape2D.FilledPath("M71.81,59.49 L66.97,61.93 C66.97,61.93 64.89,63.48 64.89,63.48 C64.89,63.48 67.28,62.46 67.28,62.46 L71.81,59.49 Z", 0xFF000088L),
    Shape2D.FilledPath("M62.76,54.61 C63.01,54.58 63.24,54.75 63.27,55 C63.3,55.24 63.13,55.47 62.88,55.5 C62.63,55.53 62.41,55.36 62.37,55.11 C62.34,54.87 62.52,54.64 62.76,54.61 Z", 0xFF000088L),
    Shape2D.FilledPath("M72.71,61.67 L67.41,62.77 C67.41,62.77 65,63.73 65,63.73 C65,63.73 67.57,63.36 67.57,63.36 L72.71,61.67 Z", 0xFF000088L),
    Shape2D.FilledPath("M65.24,54.61 C65.48,54.64 65.66,54.87 65.63,55.11 C65.59,55.36 65.37,55.53 65.12,55.5 C64.87,55.47 64.7,55.24 64.73,55 C64.76,54.75 64.99,54.58 65.24,54.61 Z", 0xFF000088L),
    Shape2D.FilledPath("M73.02,64 L67.61,63.69 C67.61,63.69 65.03,64 65.03,64 C65.03,64 67.61,64.31 67.61,64.31 L73.02,64 Z", 0xFF000088L),
    Shape2D.FilledPath("M67.63,55.25 C67.86,55.34 67.96,55.61 67.87,55.84 C67.77,56.07 67.51,56.18 67.28,56.08 C67.05,55.99 66.94,55.72 67.04,55.49 C67.13,55.26 67.39,55.15 67.63,55.25 Z", 0xFF000088L),
    Shape2D.FilledPath("M72.71,66.33 L67.57,64.64 C67.57,64.64 65,64.27 65,64.27 C65,64.27 67.41,65.23 67.41,65.23 L72.71,66.33 Z", 0xFF000088L),
    Shape2D.FilledPath("M69.77,56.48 C69.96,56.64 70,56.92 69.85,57.12 C69.7,57.31 69.42,57.35 69.22,57.2 C69.02,57.05 68.98,56.77 69.13,56.57 C69.29,56.37 69.57,56.33 69.77,56.48 Z", 0xFF000088L),
    Shape2D.FilledPath("M71.81,68.51 L67.28,65.54 C67.28,65.54 64.89,64.52 64.89,64.52 C64.89,64.52 66.97,66.07 66.97,66.07 L71.81,68.51 Z", 0xFF000088L),
    Shape2D.FilledPath("M71.52,58.23 C71.67,58.43 71.63,58.71 71.43,58.87 C71.23,59.02 70.95,58.98 70.8,58.78 C70.65,58.58 70.69,58.3 70.88,58.15 C71.08,58 71.36,58.04 71.52,58.23 Z", 0xFF000088L),
    Shape2D.FilledPath("M70.38,70.38 L66.77,66.33 C66.77,66.33 64.73,64.73 64.73,64.73 C64.73,64.73 66.33,66.77 66.33,66.77 L70.38,70.38 Z", 0xFF000088L),
    Shape2D.FilledPath("M72.75,60.37 C72.85,60.61 72.74,60.87 72.51,60.96 C72.28,61.06 72.01,60.95 71.92,60.72 C71.82,60.49 71.93,60.23 72.16,60.13 C72.39,60.04 72.66,60.14 72.75,60.37 Z", 0xFF000088L),
    Shape2D.FilledPath("M68.51,71.81 L66.07,66.97 C66.07,66.97 64.52,64.89 64.52,64.89 C64.52,64.89 65.54,67.28 65.54,67.28 L68.51,71.81 Z", 0xFF000088L),
    Shape2D.FilledPath("M73.39,62.76 C73.42,63.01 73.25,63.24 73,63.27 C72.76,63.3 72.53,63.13 72.5,62.88 C72.47,62.63 72.64,62.41 72.89,62.37 C73.13,62.34 73.36,62.52 73.39,62.76 Z", 0xFF000088L),
    Shape2D.FilledPath("M66.33,72.71 L65.23,67.41 C65.23,67.41 64.27,65 64.27,65 C64.27,65 64.64,67.57 64.64,67.57 L66.33,72.71 Z", 0xFF000088L),
)

private val flagBelgiumShapes = listOf(
    Shape2D.FilledPath("M6,13.73 L122,13.73 L122,114.27 L6,114.27 L6,13.73 Z", 0xFFED2939L),
    Shape2D.FilledPath("M6,13.73 L83.33,13.73 L83.33,114.27 L6,114.27 L6,13.73 Z", 0xFFFAE042L),
    Shape2D.FilledPath("M6,13.73 L44.67,13.73 L44.67,114.27 L6,114.27 L6,13.73 Z", 0xFF000000L),
)

private val flagGermanyShapes = listOf(
    Shape2D.FilledPath("M6,29.2 L122,29.2 L122,98.8 L6,98.8 L6,29.2 Z", 0xFF000000L),
    Shape2D.FilledPath("M6,52.4 L122,52.4 L122,98.8 L6,98.8 L6,52.4 Z", 0xFFDD0000L),
    Shape2D.FilledPath("M6,75.6 L122,75.6 L122,98.8 L6,98.8 L6,75.6 Z", 0xFFFFCE00L),
)

private val flagFranceShapes = listOf(
    Shape2D.FilledPath("M6,25.33 L122,25.33 L122,102.67 L6,102.67 L6,25.33 Z", 0xFFED2939L),
    Shape2D.FilledPath("M6,25.33 L83.33,25.33 L83.33,102.67 L6,102.67 L6,25.33 Z", 0xFFFFFFFFL),
    Shape2D.FilledPath("M6,25.33 L44.67,25.33 L44.67,102.67 L6,102.67 L6,25.33 Z", 0xFF002395L),
)

private val flagSouthAfricaShapes = listOf(
    Shape2D.FilledPath("M6,25.33 L122,25.33 L122,102.67 L6,102.67 Z", 0xFF002395L),
    Shape2D.FilledPath("M6,25.33 L122,25.33 L122,64 L6,64 Z", 0xFFDE3831L),
    Shape2D.FilledPath("M6,25.33 L64,64 L6,102.67 Z", 0xFF000000L),
    Shape2D.FilledPath("M6,25.33 L29.24,25.33 L67.91,51.11 L122,51.11 L122,76.89 L67.91,76.89 L29.24,102.67 L6,102.67 L6,87.17 L40.76,64 L6,40.83 Z", 0xFFFFFFFFL),
    Shape2D.FilledPath("M6,25.33 L64,64 L6,102.67 Z", 0xFF000000L),
    Shape2D.FilledPath("M6,25.33 L64,64 L6,102.67 L6,87.17 L40.76,64 L6,40.83 Z", 0xFFFFB612L),
    Shape2D.FilledPath("M6,25.33 L19.95,25.33 L66.35,56.27 L122,56.27 L122,71.73 L66.35,71.73 L19.95,102.67 L6,102.67 L6,93.37 L50.05,64 L6,34.63 Z", 0xFF007A4DL),
)

private val flagNigeriaShapes = listOf(
    Shape2D.FilledPath("M6,35 L122,35 L122,93 L6,93 L6,35 Z", 0xFF008751L),
    Shape2D.FilledPath("M44.67,35 L83.33,35 L83.33,93 L44.67,93 L44.67,35 Z", 0xFFFFFFFFL),
)

private val flagEthiopiaShapes = listOf(
    Shape2D.FilledPath("M6,35 L122,35 L122,93 L6,93 L6,35 Z", 0xFFDA121AL),
    Shape2D.FilledPath("M6,35 L122,35 L122,73.67 L6,73.67 L6,35 Z", 0xFFFCDD09L),
    Shape2D.FilledPath("M6,35 L122,35 L122,54.33 L6,54.33 L6,35 Z", 0xFF078930L),
    Shape2D.FilledPath("M83.33,64 C83.33,74.68 74.68,83.33 64,83.33 C53.32,83.33 44.67,74.68 44.67,64 C44.67,53.32 53.32,44.67 64,44.67 C74.68,44.67 83.33,53.32 83.33,64 Z", 0xFF0F47AFL),
    Shape2D.FilledPath("M64,48.53 L63.32,50.62 L66.12,59.22 L62.39,59.22 L61.97,60.51 L73.92,60.51 L75.69,59.22 L67.47,59.22 Z", 0xFFFCDD09L),
    Shape2D.FilledPath("M67.91,58.08 L72.83,51.3 L73.35,51.68 L68.43,58.45 L67.91,58.08 Z", 0xFFFCDD09L),
    Shape2D.FilledPath("M78.71,59.22 L76.52,59.22 L69.2,64.54 L68.05,60.99 L66.69,60.99 L70.39,72.36 L72.16,73.65 L69.62,65.83 Z", 0xFFFCDD09L),
    Shape2D.FilledPath("M70.84,65.88 L78.81,68.47 L78.61,69.09 L70.64,66.5 L70.84,65.88 Z", 0xFFFCDD09L),
    Shape2D.FilledPath("M73.09,76.51 L72.41,74.43 L65.1,69.11 L68.11,66.92 L67.69,65.63 L58.03,72.66 L57.35,74.74 L64,69.91 Z", 0xFFFCDD09L),
    Shape2D.FilledPath("M64.32,71.09 L64.32,79.47 L63.68,79.47 L63.68,71.09 L64.32,71.09 Z", 0xFFFCDD09L),
    Shape2D.FilledPath("M54.91,76.51 L56.68,75.22 L59.48,66.62 L62.49,68.81 L63.59,68.02 L53.92,60.99 L51.73,60.99 L58.38,65.83 Z", 0xFFFCDD09L),
    Shape2D.FilledPath("M57.36,66.5 L49.39,69.09 L49.19,68.47 L57.16,65.88 L57.36,66.5 Z", 0xFFFCDD09L),
    Shape2D.FilledPath("M49.29,59.22 L51.06,60.51 L60.11,60.51 L58.96,64.05 L60.05,64.85 L63.75,53.49 L63.07,51.4 L60.53,59.22 Z", 0xFFFCDD09L),
    Shape2D.FilledPath("M59.57,58.45 L54.65,51.68 L55.17,51.3 L60.09,58.08 L59.57,58.45 Z", 0xFFFCDD09L),
)

@Composable fun FlagSouthSudan(size: Dp = 24.dp, modifier: Modifier = Modifier) = ItundaFaceGlyphCanvas(size, 128f, flagSouthSudanShapes, modifier)
@Composable fun FlagSomalia(size: Dp = 24.dp, modifier: Modifier = Modifier) = ItundaFaceGlyphCanvas(size, 128f, flagSomaliaShapes, modifier)
@Composable fun FlagChina(size: Dp = 24.dp, modifier: Modifier = Modifier) = ItundaFaceGlyphCanvas(size, 128f, flagChinaShapes, modifier)
@Composable fun FlagIndia(size: Dp = 24.dp, modifier: Modifier = Modifier) = ItundaFaceGlyphCanvas(size, 128f, flagIndiaShapes, modifier)
@Composable fun FlagBelgium(size: Dp = 24.dp, modifier: Modifier = Modifier) = ItundaFaceGlyphCanvas(size, 128f, flagBelgiumShapes, modifier)
@Composable fun FlagGermany(size: Dp = 24.dp, modifier: Modifier = Modifier) = ItundaFaceGlyphCanvas(size, 128f, flagGermanyShapes, modifier)
@Composable fun FlagFrance(size: Dp = 24.dp, modifier: Modifier = Modifier) = ItundaFaceGlyphCanvas(size, 128f, flagFranceShapes, modifier)
@Composable fun FlagSouthAfrica(size: Dp = 24.dp, modifier: Modifier = Modifier) = ItundaFaceGlyphCanvas(size, 128f, flagSouthAfricaShapes, modifier)
@Composable fun FlagNigeria(size: Dp = 24.dp, modifier: Modifier = Modifier) = ItundaFaceGlyphCanvas(size, 128f, flagNigeriaShapes, modifier)
@Composable fun FlagEthiopia(size: Dp = 24.dp, modifier: Modifier = Modifier) = ItundaFaceGlyphCanvas(size, 128f, flagEthiopiaShapes, modifier)
