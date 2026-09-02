package rw.itunda.core.designsystem.itundaface

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import rw.itunda.core.designsystem.itundaface.ItundaFaceGlyphCanvas
import rw.itunda.core.designsystem.itundaface.Shape2D

// itundaface: place-category glyphs, ported from bank-mfe's
// icons/ItundaFacePlaces.tsx / github.com/itunda-rw/itundaface's svg/places/*.svg
// (2026-08-22, real "port the web glyph set to Android/iOS" work from
// project_itunda_own_icons_graphics.md's own tracked roadmap -- itundaface's
// largest remaining real gap after web went essentially emoji-complete). This
// replaces MapStyle.kt's own MAP_CATEGORY_ICONS raw-Unicode map, the exact
// literal duplicate flagged when the web place icons first shipped.
//
// Every path's "d" data below is copy-pasted byte-identical from the real,
// already-shipped web SVGs, drawn via core/designsystem's shared
// Shape2D/ItundaFaceGlyphCanvas primitives (promoted there so other feature
// modules, e.g. talk/impl's reaction glyphs, can reuse the same drawing code
// without a forbidden impl-to-impl cross-feature import).
// Compiled-verified only this pass (`:features:maps:impl:compileDebugKotlin`)
// -- no physical device was connected this session (adb devices: empty), so
// on-device visual confirmation is still owed as a follow-up, unlike every
// web glyph batch which was live-rendered before shipping.

private fun badge(color: Long) = Shape2D.FilledCircle(30f, 30f, 28f, color)

private val restaurantShapes = listOf(
    badge(0xFFFEB6AA),
    Shape2D.StrokedPath("M20,14 V26 M24,14 V26 M22,14 V44", 0xFFA20800, 2.8f, StrokeCap.Round),
    Shape2D.StrokedPath("M20,26 C20,29.5 24,29.5 24,26", 0xFFA20800, 2.8f, StrokeCap.Round),
    Shape2D.StrokedPath("M40,14 C34,16 34,22 40,24 V44", 0xFFA20800, 2.8f, StrokeCap.Round),
)

private val cafeShapes = listOf(
    badge(0xFFECC38C),
    Shape2D.FilledPath("M16,26 H40 V38 C40,43.5 35.5,48 30,48 H26 C20.5,48 16,43.5 16,38 Z", 0xFF744C00),
    Shape2D.StrokedPath("M40,28 H45 C47.8,28 50,30.2 50,33 C50,35.8 47.8,38 45,38 H40", 0xFF744C00, 2.6f, StrokeCap.Round),
    Shape2D.StrokedPath("M22,20 C22,17 25,17 25,14 M29,20 C29,17 32,17 32,14", 0xFF744C00, 2.2f, StrokeCap.Round, alpha = 0.8f),
)

private val hospitalShapes = listOf(
    badge(0xFFFEB6AA),
    Shape2D.FilledRect(14f, 16f, 32f, 34f, 4f, 0xFFFFFFFF),
    Shape2D.StrokedPath("M30,22 V44 M19,33 H41", 0xFFA20800, 5f, StrokeCap.Round),
)

private val pharmacyShapes = listOf(
    badge(0xFF8BDECB),
    Shape2D.RotatedGroup(
        -40f, 30f, 30f,
        listOf(
            Shape2D.FilledRect(12f, 23f, 36f, 14f, 7f, 0xFFFFFFFF),
            Shape2D.FilledPath("M12,30 A7,7 0 0 1 19,23 H30 V37 H19 A7,7 0 0 1 12,30 Z", 0xFF006455),
        ),
    ),
)

private val bankShapes = listOf(
    badge(0xFFC0C6FF),
    Shape2D.FilledPath("M14,22 L30,12 L46,22 Z", 0xFF282565),
    Shape2D.FilledRect(14f, 22f, 32f, 4f, 0f, 0xFF282565),
    Shape2D.FilledRect(18f, 28f, 4f, 16f, 0f, 0xFF282565),
    Shape2D.FilledRect(26f, 28f, 4f, 16f, 0f, 0xFF282565),
    Shape2D.FilledRect(34f, 28f, 4f, 16f, 0f, 0xFF282565),
    Shape2D.FilledRect(42f, 28f, 4f, 16f, 0f, 0xFF282565),
    Shape2D.FilledRect(13f, 46f, 34f, 4f, 1f, 0xFF282565),
)

private val atmShapes = listOf(
    badge(0xFFC0C6FF),
    Shape2D.FilledRect(17f, 14f, 26f, 34f, 4f, 0xFF282565),
    Shape2D.FilledRect(21f, 19f, 18f, 12f, 1.5f, 0xFF7C7BFD),
    Shape2D.FilledRect(21f, 35f, 18f, 3f, 1.5f, 0xFF7C7BFD),
    Shape2D.FilledCircle(34f, 42f, 1.6f, 0xFF7C7BFD),
)

private val hotelShapes = listOf(
    badge(0xFFDCCB8A),
    Shape2D.StrokedPath("M14,44 V26 C14,24.3 15.3,23 17,23 H27 C28.7,23 30,24.3 30,26 V32", 0xFF665400, 2.6f, StrokeCap.Round, StrokeJoin.Round),
    Shape2D.StrokedPath("M30,32 H43 C44.7,32 46,33.3 46,35 V44", 0xFF665400, 2.6f, StrokeCap.Round, StrokeJoin.Round),
    Shape2D.FilledRect(14f, 32f, 32f, 4f, 1.5f, 0xFF665400),
    Shape2D.StrokedLine(12f, 44f, 12f, 38f, 0xFF665400, 2.6f, StrokeCap.Round),
    Shape2D.StrokedLine(48f, 44f, 48f, 38f, 0xFF665400, 2.6f, StrokeCap.Round),
)

private val supermarketShapes = listOf(
    badge(0xFFB9D79B),
    Shape2D.StrokedPath("M16,16 H21 L26,38 H43 L47,22 H24", 0xFF3E6200, 3f, StrokeCap.Round, StrokeJoin.Round),
    Shape2D.FilledCircle(29f, 45f, 3.4f, 0xFF3E6200),
    Shape2D.FilledCircle(41f, 45f, 3.4f, 0xFF3E6200),
)

private val gasStationShapes = listOf(
    badge(0xFFC0CCDD),
    Shape2D.FilledRect(17f, 16f, 18f, 32f, 3f, 0xFF415676),
    Shape2D.FilledRect(21f, 21f, 10f, 8f, 1.5f, 0xFFC0CCDD),
    Shape2D.StrokedPath("M35,26 H39 C41,26 42,27.5 42,29.5 V40 C42,41.5 43,42.5 44.5,42.5 C46,42.5 47,41.5 47,40 V32 L44,29", 0xFF415676, 2.6f, StrokeCap.Round, StrokeJoin.Round),
)

private val schoolShapes = listOf(
    badge(0xFF97D5F5),
    Shape2D.FilledPath("M30,16 L50,25 L30,34 L10,25 Z", 0xFF005D7F),
    Shape2D.StrokedPath("M20,29 V38 C20,41 24,44 30,44 C36,44 40,41 40,38 V29", 0xFF005D7F, 2.4f, StrokeCap.Round),
    Shape2D.StrokedLine(50f, 25f, 50f, 37f, 0xFF005D7F, 2.2f, StrokeCap.Round),
)

private val itundaAgentShapes = listOf(
    badge(0xFFC0C6FF),
    Shape2D.StrokedPath("M24,20 C24,16 27,13 30,13 C33,13 36,16 36,20", 0xFF483EB6, 2.6f, StrokeCap.Round),
    Shape2D.FilledPath("M20,22 H40 L44,38 C45,43 41,48 35,48 H25 C19,48 15,43 16,38 Z", 0xFF483EB6),
    Shape2D.StrokedCircle(30f, 34f, 6f, 0xFFC0C6FF, 2f),
    Shape2D.StrokedLine(30f, 30f, 30f, 38f, 0xFFC0C6FF, 2f, StrokeCap.Round),
)

private val marketShapes = listOf(
    badge(0xFFB3D5B9),
    Shape2D.FilledPath("M16,26 H44 L40,44 C39.5,46.3 37.5,48 35,48 H25 C22.5,48 20.5,46.3 20,44 Z", 0xFF156631),
    Shape2D.StrokedPath("M23,26 C23,20 26,16 30,16 C34,16 37,20 37,26", 0xFF156631, 2.6f, StrokeCap.Round),
    Shape2D.StrokedPath("M22,32 H38 M23,38 H37", 0xFFB3D5B9, 1.8f, alpha = 0.7f),
)

private val busStopShapes = listOf(
    badge(0xFFC0CCDD),
    Shape2D.FilledRect(14f, 18f, 32f, 22f, 5f, 0xFF253142),
    Shape2D.FilledRect(18f, 22f, 9f, 8f, 1.5f, 0xFFC0CCDD),
    Shape2D.FilledRect(33f, 22f, 9f, 8f, 1.5f, 0xFFC0CCDD),
    Shape2D.FilledCircle(21f, 43f, 3.4f, 0xFF253142),
    Shape2D.FilledCircle(39f, 43f, 3.4f, 0xFF253142),
)

@Composable fun PlaceRestaurant(size: Dp = 24.dp) = ItundaFaceGlyphCanvas(size, 60f, restaurantShapes)
@Composable fun PlaceCafe(size: Dp = 24.dp) = ItundaFaceGlyphCanvas(size, 60f, cafeShapes)
@Composable fun PlaceHospital(size: Dp = 24.dp) = ItundaFaceGlyphCanvas(size, 60f, hospitalShapes)
@Composable fun PlacePharmacy(size: Dp = 24.dp) = ItundaFaceGlyphCanvas(size, 60f, pharmacyShapes)
@Composable fun PlaceBank(size: Dp = 24.dp) = ItundaFaceGlyphCanvas(size, 60f, bankShapes)
@Composable fun PlaceAtm(size: Dp = 24.dp) = ItundaFaceGlyphCanvas(size, 60f, atmShapes)
@Composable fun PlaceHotel(size: Dp = 24.dp) = ItundaFaceGlyphCanvas(size, 60f, hotelShapes)
@Composable fun PlaceSupermarket(size: Dp = 24.dp) = ItundaFaceGlyphCanvas(size, 60f, supermarketShapes)
@Composable fun PlaceGasStation(size: Dp = 24.dp) = ItundaFaceGlyphCanvas(size, 60f, gasStationShapes)
@Composable fun PlaceSchool(size: Dp = 24.dp) = ItundaFaceGlyphCanvas(size, 60f, schoolShapes)
@Composable fun PlaceItundaAgent(size: Dp = 24.dp) = ItundaFaceGlyphCanvas(size, 60f, itundaAgentShapes)
@Composable fun PlaceMarket(size: Dp = 24.dp) = ItundaFaceGlyphCanvas(size, 60f, marketShapes)
@Composable fun PlaceBusStop(size: Dp = 24.dp) = ItundaFaceGlyphCanvas(size, 60f, busStopShapes)

private val ITUNDAFACE_PLACES: Map<String, List<Shape2D>> = mapOf(
    "RESTAURANT" to restaurantShapes,
    "CAFE" to cafeShapes,
    "HOSPITAL" to hospitalShapes,
    "PHARMACY" to pharmacyShapes,
    "BANK" to bankShapes,
    "ATM" to atmShapes,
    "HOTEL" to hotelShapes,
    "SUPERMARKET" to supermarketShapes,
    "GAS_STATION" to gasStationShapes,
    "SCHOOL" to schoolShapes,
    "ITUNDA_AGENT" to itundaAgentShapes,
    "MARKET" to marketShapes,
    "BUS_STOP" to busStopShapes,
)

/** Renders itundaface's own glyph for a known place-category id, falling back to a
 * plain indigo pin dot for anything outside the 13 known categories -- matches
 * MAP_CATEGORY_ICONS' own existing `?: "📍"` fallback. */
@Composable
fun PlaceGlyph(category: String, size: Dp = 24.dp) {
    val shapes = ITUNDAFACE_PLACES[category] ?: listOf(badge(0xFFC0C6FF), Shape2D.FilledCircle(30f, 24f, 8f, 0xFF483EB6))
    ItundaFaceGlyphCanvas(size, 60f, shapes)
}
