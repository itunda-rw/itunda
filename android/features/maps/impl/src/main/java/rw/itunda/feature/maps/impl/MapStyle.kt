package rw.itunda.feature.maps.impl

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Color as AndroidColor
import kotlin.math.roundToInt
import org.maplibre.geojson.Feature
import org.maplibre.geojson.Point
import rw.itunda.core.network.MapConfig
import rw.itunda.core.network.ShoppingMerchantDto

// Extracted from MapsScreen.kt (2026-08-19) -- pure constants/functions/enums with zero
// dependency on MapScreen's own composable state (tile/glyph URLs, source/layer ids,
// merchant pin color+bitmap drawing, the map style JSON, and the two small UI-state
// enums). MapsScreen.kt was a 3,090-line single file (one composable already hit a real
// JVM "Method too large" compile error once, see BusTripResultsView's own doc comment in
// MapUiComponents.kt) -- this is the first, zero-risk slice of splitting it: everything
// here is `internal` (module-visible) rather than the file-private it was before, since
// MapsScreen.kt itself is now a different file needing to see these declarations, but no
// behavior changes at all. The much harder remaining slices (search+autocomplete,
// navigation+routing+voice guidance, bookmarks+folders+sharing, nearby-category
// browsing, the place-detail sheet) share 40+ interdependent `remember` state variables
// and need a real state-holder design before they can be safely extracted the same way --
// deliberately not attempted in this same pass.

internal const val RWANDA_CENTER_LAT = -1.9441
internal const val RWANDA_CENTER_LNG = 30.0619
// Real bookmark-folder defaults/palette (2026-07-22) -- kept in sync by hand with
// MapsService.DEFAULT_BOOKMARK_FOLDER/DEFAULT_BOOKMARK_COLOR on the backend, same plain-
// literal convention as bank-mfe's own copy. A small fixed palette rather than a full
// color picker, matching this app's own design-system palette.
internal const val DEFAULT_BOOKMARK_FOLDER = "Saved places"
internal val BOOKMARK_COLOR_PALETTE = listOf("#F5A623", "#3182F6", "#8B5CF6", "#E53935", "#22B07D", "#4E5968")

// Both driven by BuildConfig now (2026-07-21), not hardcoded to a private-cloud address
// directly -- see app/build.gradle.kts' TILES_BASE_URL/GLYPHS_BASE_URL doc comment for
// why a physical device on the public HTTPS endpoint got a permanently blank map
// otherwise, and for the 2026-07-27 dc-b->dc-a address correction.
internal val TILES_URL: String get() = "${MapConfig.tilesBaseUrl}/rwanda/{z}/{x}/{y}.mvt"
// Real self-hosted glyphs (font PBF) server (2026-07-19) -- closes item 5, the last item
// on the Maps "100%" roadmap. See bank-mfe's lib/maps.ts GLYPHS_URL doc comment for the
// full account (real pre-generated Noto Sans Regular/Bold glyph PBFs, served statically
// by nginx on itunda-dc-b, ~14MB RSS -- an order of magnitude lighter than OSRM/
// Nominatim despite being this host's fourth persistent private-cloud service).
internal val GLYPHS_URL: String get() = "${MapConfig.glyphsBaseUrl}/{fontstack}/{range}.pbf"
internal const val MERCHANTS_SOURCE_ID = "merchants"
internal const val MERCHANTS_LAYER_ID = "merchants-circle"
internal const val MERCHANT_FOOD_ICON_ID = "merchant-pin-food"
// Real per-category merchant pin color (2026-08-09) -- the last item named in Section 27's
// "deliberately not attempted" list. itunda's real merchant `category` field is merchant-set
// free text (confirmed live against the backend: "Coffee & Bakery", "Fast Food", "Rwandan",
// "Electronics", "Fashion" today), not a fixed enum -- so this is a small, honestly-labeled
// keyword bucket rather than a clean enum switch, same spirit as MAP_CATEGORY_ICONS' own
// curated client-side lookup above. Matches the real reference screenshots' own orange-for-
// food/cafe convention (Section 27); every other category keeps the existing default blue
// rather than guessing more buckets from 5 real observed values.
internal val MERCHANT_FOOD_KEYWORDS = listOf("food", "coffee", "bakery", "rwandan", "restaurant", "cafe", "grill", "kitchen")
internal fun isFoodMerchantCategory(category: String?): Boolean =
    category != null && MERCHANT_FOOD_KEYWORDS.any { category.contains(it, ignoreCase = true) }
internal fun merchantPinIconId(category: String?): String =
    if (isFoodMerchantCategory(category)) MERCHANT_FOOD_ICON_ID else MERCHANT_ICON_ID
internal fun merchantFeature(m: ShoppingMerchantDto): Feature {
    val props = com.google.gson.JsonObject().apply { addProperty("pinIcon", merchantPinIconId(m.category)) }
    return Feature.fromGeometry(Point.fromLngLat(m.longitude!!, m.latitude!!), props)
}
internal const val MY_LOCATION_SOURCE_ID = "my-location"
internal const val MY_LOCATION_LAYER_ID = "my-location-circle"
internal const val DESTINATION_SOURCE_ID = "destination"
internal const val DESTINATION_LAYER_ID = "destination-circle"
internal const val ROUTE_SOURCE_ID = "route"
internal const val ROUTE_LAYER_ID = "route-line"
internal const val NEARBY_SOURCE_ID = "nearby-places"
internal const val NEARBY_LAYER_ID = "nearby-places-circle"
internal const val MERCHANT_ICON_ID = "merchant-pin"
internal const val DESTINATION_ICON_ID = "destination-pin"
internal const val NEARBY_ICON_ID = "nearby-pin"
// Real distance-measurement (ruler) tool (2026-07-23) -- ported from bank-mfe's own
// real MapView.tsx tool. A dashed line, deliberately a different color from the real
// drawn road route above, so the two are never visually confused: one is a real OSRM
// road route, the other a plain straight-line measurement between tapped points.
internal const val MEASURE_SOURCE_ID = "measure"
internal const val MEASURE_POINTS_LAYER_ID = "measure-points"
internal const val MEASURE_LINE_LAYER_ID = "measure-line"

// Per-category glyphs for the chip row (2026-07-21, raw emoji originally; ported
// 2026-08-22 to itundaface's own hand-drawn PlaceGlyph in ItundaFacePlaces.kt --
// see that file's own doc comment). No icon field exists on the backend's
// `MapPlaceCategory` DTO -- this is a client-side-only lookup by id, honestly scoped to
// display, never sent back to the server.

// Real teardrop pin markers (2026-07-21), replacing the flat, unlabeled `CircleLayer`
// dots this screen used before -- MapLibre has no vector marker primitive of its own, so
// the shape is drawn once at runtime straight into a Bitmap (no drawable asset needed)
// and registered via `Style.addImage`, matching the real Naver Map/Kakao Map/Google Maps
// pin silhouette (a circle head + a pointed tail anchored at the actual coordinate)
// instead of a dot that reads as a generic data point. Drawn as an oversized white
// "border" shape first, then the real color on top, rather than stroking a single
// circle+triangle path directly -- stroking that combined path leaves a visible seam
// where the triangle's edges cross the circle's, since the triangle's own corners don't
// land exactly on the circle's boundary.
internal fun teardropPath(cx: Float, cy: Float, r: Float): Path = Path().apply {
    addCircle(cx, cy, r, Path.Direction.CW)
    moveTo(cx - r * 0.58f, cy + r * 0.58f)
    lineTo(cx, cy + r * 1.35f)
    lineTo(cx + r * 0.58f, cy + r * 0.58f)
    close()
}

internal fun createPinBitmap(density: Float, fillColorHex: String): Bitmap {
    val stroke = 2f * density
    val w = (30f * density).roundToInt()
    val h = (38f * density).roundToInt()
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val cx = w / 2f
    val r = w / 2f - stroke
    val cy = r + stroke
    val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.WHITE; style = Paint.Style.FILL }
    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.parseColor(fillColorHex); style = Paint.Style.FILL }
    canvas.drawPath(teardropPath(cx, cy, r + stroke), whitePaint)
    canvas.drawPath(teardropPath(cx, cy, r), fillPaint)
    canvas.drawCircle(cx, cy, r * 0.34f, whitePaint)
    return bitmap
}

// Real 3-state (peek/half/full) draggable bottom sheet (2026-07-21) -- replaces the
// static Card that only ever appeared/vanished at whatever height its content
// dictated. Mirrors the exact real engineering gap both Apple's own
// UISheetPresentationController (offers `.medium()`/`.large()` detents out of the box)
// and Google Maps' own documented need for teams to hand-build a custom
// BottomSheetBehavior extension (Compose's stock BottomSheetScaffold only gives 2
// states) confirm is genuine, nontrivial work -- see docs/DESIGN_REFERENCES.md
// section 1, recommendation 1.
internal enum class MapSheetValue { Peek, Half, Full }

// Real "Itunda Places" tabs -- Home is unconditional; Menu/Reviews/Photos/News only
// ever appear once real content is confirmed. INFO (2026-08-09) was a real, confirmed
// dead-code bug until 2026-08-28's itunda Maps redesign fixed it -- defined here but
// never actually added to the rendered tab row in MapPlaceDetailView.kt. PHOTOS/NEWS
// added 2026-08-28 (direct Naver Map reference), backed by the new consolidated
// MapPlaceDetailDto.
internal enum class PlaceTab { HOME, MENU, REVIEWS, PHOTOS, NEWS, INFO }

// A real, minimal MapLibre style over itunda's own self-hosted vector tiles -- mirrors
// bank-mfe's MapView.tsx MAP_STYLE constant exactly (same source, same layer set, no
// text labels yet since that needs a separate self-hosted glyphs server). Kept as a
// single JSON string here since MapLibre Android's style DSL doesn't offer a typed
// builder as concise as the web SDK's; this is the same style spec format either way.
// Declares the real, empty-until-populated `route` source the directions feature below
// writes into (the merchants/my-location/destination sources are added at runtime once
// the style loads, same as before).
// Real dark map style (2026-08-10) -- Google Maps/Apple Maps/Naver Maps all switch
// their own tile rendering to a dark variant under system dark mode; itunda's
// self-hosted style was hard-coded light-only (#f2efe9 background), a real, likely
// highly visible mismatch given this session's own device defaults to system dark
// mode. Chosen once at map-open time (MapScreen reads isSystemInDarkTheme() below) --
// deliberately not live-reactive to a theme change while the map is already open,
// since every source/layer for merchants/route/destination/etc. is added inside the
// same setStyle() callback this JSON feeds; making that also survive a live style swap
// would mean restructuring where that setup runs, real extra risk this session's own
// "can't visually verify rendering" constraint (FLAG_SECURE blocks screenshots) isn't
// the moment to take on for a system setting a user changes rarely, not mid-session.
internal fun mapStyleJson(dark: Boolean): String {
    val c = if (dark) {
        MapStyleColors(
            background = "#1d2330", landcover = "#26301f", landcoverOpacity = 0.5,
            park = "#1f3018", parkOpacity = 0.45, water = "#16222e",
            residential = "#232838", residentialOpacity = 0.4,
            building = "#2a2f40", buildingOutline = "#3a4058",
            roadMinor = "#3a4058", roadMajor = "#c9974f",
            boundary = "#7a68a0", waterLabel = "#7fa8c9", waterLabelHalo = "#0e1620",
            roadLabel = "#c9b98a", roadLabelHalo = "#000000",
            poiLabel = "#a8a296", poiLabelHalo = "#000000",
            placeMinor = "#c8c8c8", placeMinorHalo = "#000000",
            placeMajor = "#f0f0f0", placeMajorHalo = "#000000",
        )
    } else {
        MapStyleColors(
            background = "#f2efe9", landcover = "#d8e8c8", landcoverOpacity = 0.6,
            park = "#c8e0b0", parkOpacity = 0.5, water = "#a8d0e6",
            residential = "#e6e1d8", residentialOpacity = 0.5,
            building = "#dcd4c6", buildingOutline = "#c8bfae",
            roadMinor = "#ffffff", roadMajor = "#f5c96b",
            boundary = "#a08ccb", waterLabel = "#3d6e8f", waterLabelHalo = "#ffffff",
            roadLabel = "#6b5a2a", roadLabelHalo = "#ffffff",
            poiLabel = "#5a5044", poiLabelHalo = "#ffffff",
            placeMinor = "#3d3d3d", placeMinorHalo = "#ffffff",
            placeMajor = "#1f1f1f", placeMajorHalo = "#ffffff",
        )
    }
    return """
{
  "version": 8,
  "glyphs": "$GLYPHS_URL",
  "sources": {
    "rwanda": { "type": "vector", "tiles": ["$TILES_URL"], "minzoom": 0, "maxzoom": 14 }
  },
  "layers": [
    { "id": "background", "type": "background", "paint": { "background-color": "${c.background}" } },
    { "id": "landcover", "type": "fill", "source": "rwanda", "source-layer": "landcover",
      "paint": { "fill-color": "${c.landcover}", "fill-opacity": ${c.landcoverOpacity} } },
    { "id": "park", "type": "fill", "source": "rwanda", "source-layer": "park",
      "paint": { "fill-color": "${c.park}", "fill-opacity": ${c.parkOpacity} } },
    { "id": "water", "type": "fill", "source": "rwanda", "source-layer": "water",
      "paint": { "fill-color": "${c.water}" } },
    { "id": "landuse-residential", "type": "fill", "source": "rwanda", "source-layer": "landuse",
      "filter": ["==", ["get", "class"], "residential"],
      "paint": { "fill-color": "${c.residential}", "fill-opacity": ${c.residentialOpacity} } },
    { "id": "building", "type": "fill", "source": "rwanda", "source-layer": "building", "minzoom": 13,
      "paint": { "fill-color": "${c.building}", "fill-outline-color": "${c.buildingOutline}" } },
    { "id": "transportation-minor", "type": "line", "source": "rwanda", "source-layer": "transportation",
      "filter": ["!", ["match", ["get", "class"], ["motorway", "trunk", "primary", "secondary"], true, false]],
      "paint": { "line-color": "${c.roadMinor}", "line-width": ["interpolate", ["linear"], ["zoom"], 8, 0.5, 16, 3] } },
    { "id": "transportation-major", "type": "line", "source": "rwanda", "source-layer": "transportation",
      "filter": ["match", ["get", "class"], ["motorway", "trunk", "primary", "secondary"], true, false],
      "paint": { "line-color": "${c.roadMajor}", "line-width": ["interpolate", ["linear"], ["zoom"], 6, 1, 16, 5] } },
    { "id": "boundary", "type": "line", "source": "rwanda", "source-layer": "boundary",
      "filter": ["<=", ["get", "admin_level"], 4],
      "paint": { "line-color": "${c.boundary}", "line-width": 1, "line-dasharray": [2, 1] } },
    { "id": "water-label", "type": "symbol", "source": "rwanda", "source-layer": "water_name", "minzoom": 7,
      "layout": { "text-field": ["get", "name"], "text-font": ["Noto Sans Regular"], "text-size": 12 },
      "paint": { "text-color": "${c.waterLabel}", "text-halo-color": "${c.waterLabelHalo}", "text-halo-width": 1 } },
    { "id": "road-label", "type": "symbol", "source": "rwanda", "source-layer": "transportation_name", "minzoom": 12,
      "layout": { "text-field": ["get", "name"], "text-font": ["Noto Sans Regular"], "text-size": 12,
        "symbol-placement": "line", "text-letter-spacing": 0.05 },
      "paint": { "text-color": "${c.roadLabel}", "text-halo-color": "${c.roadLabelHalo}", "text-halo-width": 1.2 } },
    { "id": "poi-label", "type": "symbol", "source": "rwanda", "source-layer": "poi", "minzoom": 14,
      "layout": { "text-field": ["get", "name"], "text-font": ["Noto Sans Regular"], "text-size": 11 },
      "paint": { "text-color": "${c.poiLabel}", "text-halo-color": "${c.poiLabelHalo}", "text-halo-width": 1 } },
    { "id": "place-label-minor", "type": "symbol", "source": "rwanda", "source-layer": "place", "minzoom": 10,
      "filter": ["!", ["match", ["get", "class"], ["city", "town"], true, false]],
      "layout": { "text-field": ["get", "name"], "text-font": ["Noto Sans Regular"], "text-size": 12 },
      "paint": { "text-color": "${c.placeMinor}", "text-halo-color": "${c.placeMinorHalo}", "text-halo-width": 1.2 } },
    { "id": "place-label-major", "type": "symbol", "source": "rwanda", "source-layer": "place",
      "filter": ["match", ["get", "class"], ["city", "town"], true, false],
      "layout": { "text-field": ["get", "name"], "text-font": ["Noto Sans Bold"],
        "text-size": ["interpolate", ["linear"], ["zoom"], 4, 12, 10, 18] },
      "paint": { "text-color": "${c.placeMajor}", "text-halo-color": "${c.placeMajorHalo}", "text-halo-width": 1.5 } }
  ]
}
""".trimIndent()
}

internal data class MapStyleColors(
    val background: String, val landcover: String, val landcoverOpacity: Double,
    val park: String, val parkOpacity: Double, val water: String,
    val residential: String, val residentialOpacity: Double,
    val building: String, val buildingOutline: String,
    val roadMinor: String, val roadMajor: String,
    val boundary: String, val waterLabel: String, val waterLabelHalo: String,
    val roadLabel: String, val roadLabelHalo: String,
    val poiLabel: String, val poiLabelHalo: String,
    val placeMinor: String, val placeMinorHalo: String,
    val placeMajor: String, val placeMajorHalo: String,
)
