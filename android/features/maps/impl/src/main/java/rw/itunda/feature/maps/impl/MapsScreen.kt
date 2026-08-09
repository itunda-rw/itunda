package rw.itunda.feature.maps.impl

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.speech.tts.TextToSpeech
import coil.compose.AsyncImage
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Color as AndroidColor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBackIosNew
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleOpacity
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconAnchor
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.iconSize
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineDasharray
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import kotlin.math.roundToInt
import retrofit2.HttpException
import rw.itunda.core.network.AddMapBookmarkRequest
import rw.itunda.core.network.EatsReviewDto
import rw.itunda.core.network.MerchantProductDto
import rw.itunda.core.network.MAP_NEARBY_CATEGORIES
import rw.itunda.core.network.MapBookmarkDto
import rw.itunda.core.network.MoveMapBookmarkRequest
import rw.itunda.core.network.MapsDirectionsResponse
import rw.itunda.core.network.ItineraryDirectionsRequest
import rw.itunda.core.network.ItineraryWaypointRequest
import rw.itunda.core.network.NearbyPlaceDto
import rw.itunda.core.network.TrendingPlaceDto
import rw.itunda.core.designsystem.components.EmptyState
import rw.itunda.core.designsystem.components.IdsTextField
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.MapConfig
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.SetMapFolderVisibilityRequest
import rw.itunda.core.network.TokenStore
import rw.itunda.core.network.PlaceSearchResultDto
import rw.itunda.core.network.RecentMapSearchesStore
import rw.itunda.core.network.RouteResultDto
import rw.itunda.core.network.ShoppingMerchantDto
import rw.itunda.core.network.superAppErrorMessage

// Eighth Feature extraction (2026-07-23), after the seven-module pass this same session
// already completed -- see features/marketplace/impl/.../MarketplaceScreen.kt's own
// header comment for the full account of the Toss Microfeatures pattern this follows.
// MapScreen was fully self-contained already (confirmed via a dependency audit before
// moving anything: no MainViewModel/:app-only coupling beyond Toss color tokens and two
// BuildConfig fields) -- the only real blocker was rw.itunda.app.BuildConfig.
// TILES_BASE_URL/GLYPHS_BASE_URL, closed the same way RouteMiniMap's identical blocker
// was closed a moment earlier this session: MapConfig.kt (core/network) now holds both,
// set once from :app's ItundaApplication.onCreate() alongside NetworkClient.init(). No
// injected callback slots needed here at all -- unlike the Hood-mode/Shop/Eats/Talk
// modules, Maps doesn't call into DeviceStepUpDialog or any other Feature module's UI.

private const val RWANDA_CENTER_LAT = -1.9441
private const val RWANDA_CENTER_LNG = 30.0619
// Real bookmark-folder defaults/palette (2026-07-22) -- kept in sync by hand with
// MapsService.DEFAULT_BOOKMARK_FOLDER/DEFAULT_BOOKMARK_COLOR on the backend, same plain-
// literal convention as bank-mfe's own copy. A small fixed palette rather than a full
// color picker, matching this app's own design-system palette.
private const val DEFAULT_BOOKMARK_FOLDER = "Saved places"
private val BOOKMARK_COLOR_PALETTE = listOf("#F5A623", "#3182F6", "#8B5CF6", "#E53935", "#22B07D", "#4E5968")

// Real Naver Map place-card layout (2026-08-04) -- confirmed live against itunda's own
// self-hosted Nominatim (previously misdiagnosed as "no real Rwanda POI data" while this
// screen's own backend restart was missing NOMINATIM_BASE_URL; re-verified with it
// actually configured and it returns rich real results, e.g. "Miracle Pharmacy, KN 81
// Street, Nyarugenge, Nyarugenge District, City of Kigali, Rwanda"). Real Naver place
// cards show a bold name with a muted address line below, not one long run-on string --
// itunda's own `displayName` already carries the full real address, just unsplit. Splits
// on the first comma only (Nominatim's own convention: segment 0 is always the specific
// place/building name, everything after is the real address hierarchy) -- no new backend
// field, no new data, just real presentation of what's already there.
private fun splitPlaceName(displayName: String): Pair<String, String?> {
    val comma = displayName.indexOf(',')
    return if (comma < 0) displayName to null else displayName.substring(0, comma).trim() to displayName.substring(comma + 1).trim()
}
// Both driven by BuildConfig now (2026-07-21), not hardcoded to a private-cloud address
// directly -- see app/build.gradle.kts' TILES_BASE_URL/GLYPHS_BASE_URL doc comment for
// why a physical device on the public HTTPS endpoint got a permanently blank map
// otherwise, and for the 2026-07-27 dc-b->dc-a address correction.
private val TILES_URL: String get() = "${MapConfig.tilesBaseUrl}/rwanda/{z}/{x}/{y}.mvt"
// Real self-hosted glyphs (font PBF) server (2026-07-19) -- closes item 5, the last item
// on the Maps "100%" roadmap. See bank-mfe's lib/maps.ts GLYPHS_URL doc comment for the
// full account (real pre-generated Noto Sans Regular/Bold glyph PBFs, served statically
// by nginx on itunda-dc-b, ~14MB RSS -- an order of magnitude lighter than OSRM/
// Nominatim despite being this host's fourth persistent private-cloud service).
private val GLYPHS_URL: String get() = "${MapConfig.glyphsBaseUrl}/{fontstack}/{range}.pbf"
private const val MERCHANTS_SOURCE_ID = "merchants"
private const val MERCHANTS_LAYER_ID = "merchants-circle"
private const val MY_LOCATION_SOURCE_ID = "my-location"
private const val MY_LOCATION_LAYER_ID = "my-location-circle"
private const val DESTINATION_SOURCE_ID = "destination"
private const val DESTINATION_LAYER_ID = "destination-circle"
private const val ROUTE_SOURCE_ID = "route"
private const val ROUTE_LAYER_ID = "route-line"
private const val NEARBY_SOURCE_ID = "nearby-places"
private const val NEARBY_LAYER_ID = "nearby-places-circle"
private const val MERCHANT_ICON_ID = "merchant-pin"
private const val DESTINATION_ICON_ID = "destination-pin"
private const val NEARBY_ICON_ID = "nearby-pin"
// Real distance-measurement (ruler) tool (2026-07-23) -- ported from bank-mfe's own
// real MapView.tsx tool. A dashed line, deliberately a different color from the real
// drawn road route above, so the two are never visually confused: one is a real OSRM
// road route, the other a plain straight-line measurement between tapped points.
private const val MEASURE_SOURCE_ID = "measure"
private const val MEASURE_POINTS_LAYER_ID = "measure-points"
private const val MEASURE_LINE_LAYER_ID = "measure-line"

// Real per-category glyphs for the chip row (2026-07-21) -- plain emoji, matching this
// screen's own existing convention of emoji over icon-font glyphs for real content (the
// 🚗/★/☆ already used below), not a new pattern. No icon field exists on the backend's
// `MapPlaceCategory` DTO -- this is a client-side-only lookup by id, honestly scoped to
// display, never sent back to the server.
private val MAP_CATEGORY_ICONS = mapOf(
    "RESTAURANT" to "🍽️", "CAFE" to "☕", "HOSPITAL" to "🏥", "PHARMACY" to "💊",
    "BANK" to "🏦", "ATM" to "🏧", "HOTEL" to "🏨", "SUPERMARKET" to "🛒",
    "GAS_STATION" to "⛽", "SCHOOL" to "🏫", "ITUNDA_AGENT" to "💜",
    "MARKET" to "🧺", "BUS_STOP" to "🚌",
)

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
private fun teardropPath(cx: Float, cy: Float, r: Float): Path = Path().apply {
    addCircle(cx, cy, r, Path.Direction.CW)
    moveTo(cx - r * 0.58f, cy + r * 0.58f)
    lineTo(cx, cy + r * 1.35f)
    lineTo(cx + r * 0.58f, cy + r * 0.58f)
    close()
}

private fun createPinBitmap(density: Float, fillColorHex: String): Bitmap {
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
private enum class MapSheetValue { Peek, Half, Full }

// Real "Itunda Places" tabs -- see the `placeTab` state's own doc comment for why only
// Home/Info are unconditional (Menu/Reviews only ever appear once real content is confirmed).
private enum class PlaceTab { HOME, MENU, REVIEWS, INFO }

// A real, minimal MapLibre style over itunda's own self-hosted vector tiles -- mirrors
// bank-mfe's MapView.tsx MAP_STYLE constant exactly (same source, same layer set, no
// text labels yet since that needs a separate self-hosted glyphs server). Kept as a
// single JSON string here since MapLibre Android's style DSL doesn't offer a typed
// builder as concise as the web SDK's; this is the same style spec format either way.
// Declares the real, empty-until-populated `route` source the directions feature below
// writes into (the merchants/my-location/destination sources are added at runtime once
// the style loads, same as before).
private val MAP_STYLE_JSON: String get() = """
{
  "version": 8,
  "glyphs": "$GLYPHS_URL",
  "sources": {
    "rwanda": { "type": "vector", "tiles": ["$TILES_URL"], "minzoom": 0, "maxzoom": 14 }
  },
  "layers": [
    { "id": "background", "type": "background", "paint": { "background-color": "#f2efe9" } },
    { "id": "landcover", "type": "fill", "source": "rwanda", "source-layer": "landcover",
      "paint": { "fill-color": "#d8e8c8", "fill-opacity": 0.6 } },
    { "id": "park", "type": "fill", "source": "rwanda", "source-layer": "park",
      "paint": { "fill-color": "#c8e0b0", "fill-opacity": 0.5 } },
    { "id": "water", "type": "fill", "source": "rwanda", "source-layer": "water",
      "paint": { "fill-color": "#a8d0e6" } },
    { "id": "landuse-residential", "type": "fill", "source": "rwanda", "source-layer": "landuse",
      "filter": ["==", ["get", "class"], "residential"],
      "paint": { "fill-color": "#e6e1d8", "fill-opacity": 0.5 } },
    { "id": "building", "type": "fill", "source": "rwanda", "source-layer": "building", "minzoom": 13,
      "paint": { "fill-color": "#dcd4c6", "fill-outline-color": "#c8bfae" } },
    { "id": "transportation-minor", "type": "line", "source": "rwanda", "source-layer": "transportation",
      "filter": ["!", ["match", ["get", "class"], ["motorway", "trunk", "primary", "secondary"], true, false]],
      "paint": { "line-color": "#ffffff", "line-width": ["interpolate", ["linear"], ["zoom"], 8, 0.5, 16, 3] } },
    { "id": "transportation-major", "type": "line", "source": "rwanda", "source-layer": "transportation",
      "filter": ["match", ["get", "class"], ["motorway", "trunk", "primary", "secondary"], true, false],
      "paint": { "line-color": "#f5c96b", "line-width": ["interpolate", ["linear"], ["zoom"], 6, 1, 16, 5] } },
    { "id": "boundary", "type": "line", "source": "rwanda", "source-layer": "boundary",
      "filter": ["<=", ["get", "admin_level"], 4],
      "paint": { "line-color": "#a08ccb", "line-width": 1, "line-dasharray": [2, 1] } },
    { "id": "water-label", "type": "symbol", "source": "rwanda", "source-layer": "water_name", "minzoom": 7,
      "layout": { "text-field": ["get", "name"], "text-font": ["Noto Sans Regular"], "text-size": 12 },
      "paint": { "text-color": "#3d6e8f", "text-halo-color": "#ffffff", "text-halo-width": 1 } },
    { "id": "road-label", "type": "symbol", "source": "rwanda", "source-layer": "transportation_name", "minzoom": 12,
      "layout": { "text-field": ["get", "name"], "text-font": ["Noto Sans Regular"], "text-size": 12,
        "symbol-placement": "line", "text-letter-spacing": 0.05 },
      "paint": { "text-color": "#6b5a2a", "text-halo-color": "#ffffff", "text-halo-width": 1.2 } },
    { "id": "poi-label", "type": "symbol", "source": "rwanda", "source-layer": "poi", "minzoom": 14,
      "layout": { "text-field": ["get", "name"], "text-font": ["Noto Sans Regular"], "text-size": 11 },
      "paint": { "text-color": "#5a5044", "text-halo-color": "#ffffff", "text-halo-width": 1 } },
    { "id": "place-label-minor", "type": "symbol", "source": "rwanda", "source-layer": "place", "minzoom": 10,
      "filter": ["!", ["match", ["get", "class"], ["city", "town"], true, false]],
      "layout": { "text-field": ["get", "name"], "text-font": ["Noto Sans Regular"], "text-size": 12 },
      "paint": { "text-color": "#3d3d3d", "text-halo-color": "#ffffff", "text-halo-width": 1.2 } },
    { "id": "place-label-major", "type": "symbol", "source": "rwanda", "source-layer": "place",
      "filter": ["match", ["get", "class"], ["city", "town"], true, false],
      "layout": { "text-field": ["get", "name"], "text-font": ["Noto Sans Bold"],
        "text-size": ["interpolate", ["linear"], ["zoom"], 4, 12, 10, 18] },
      "paint": { "text-color": "#1f1f1f", "text-halo-color": "#ffffff", "text-halo-width": 1.5 } }
  ]
}
""".trimIndent()

/**
 * Real interactive Rwanda map -- itunda's own self-hosted Kakao Maps/Naver Maps-style
 * mapping, ported here from bank-mfe's MapView.tsx (same self-hosted PMTiles tile server,
 * same hand-written style, same real merchant markers). Reached from My's Quick links,
 * matching the Pay/Benefits precedent, since neither bottom-nav row has a free slot.
 *
 * Real search + directions + "my location" added 2026-07-19, at the user's direct
 * request ("make sure our maps is fully 100% like naver maps/kakao maps for rwanda") --
 * ported field-for-field from bank-mfe's own same-day build: search backed by itunda's
 * self-hosted Nominatim (via the new general-purpose `rw.itunda.maps` module, not the
 * Eats-checkout-scoped autocomplete), a real blue dot via Android's
 * `FusedLocationProviderClient` (runtime-permission-gated, never assumed granted), and
 * real turn-by-turn-capable directions drawing the actual road-following route via
 * itunda's self-hosted OSRM, not a straight line.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MapScreen(onBack: () -> Unit, initialCategory: String? = null, initialSearchQuery: String? = null) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    // The cash-out flow deliberately arrives with the public agent network selected.
    // Keep that intent visible while the customer explores the general-purpose map.
    val isAgentCashDiscovery = initialCategory == "ITUNDA_AGENT"
    // Must run synchronously during composition, not in a LaunchedEffect -- LaunchedEffect
    // only fires after composition commits, but `remember { MapView(context) }` below runs
    // synchronously during this same initial composition, so MapView was being constructed
    // before MapLibre.getInstance() ever ran, crashing every time with
    // MapLibreConfigurationException the moment Map was opened.
    remember { MapLibre.getInstance(context) }

    var merchants by remember { mutableStateOf<List<ShoppingMerchantDto>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<PlaceSearchResultDto>?>(null) }
    var searching by remember { mutableStateOf(false) }
    // Real recent-searches list (2026-07-22) -- see RecentMapSearchesStore's own doc
    // comment; ported from bank-mfe's own real localStorage-backed feature.
    val recentSearchesStore = remember { RecentMapSearchesStore(context) }
    var recentSearches by remember { mutableStateOf<List<PlaceSearchResultDto>>(emptyList()) }
    var searchFocused by remember { mutableStateOf(false) }
    var selectedPlace by remember { mutableStateOf<PlaceSearchResultDto?>(null) }
    // Real "Itunda Places" (2026-08-09), directly requested after 16 real Naver Places
    // screenshots: "like naver places we should have itunda places." itunda already has
    // real underlying data for a genuine tabbed business-profile page -- not fabricated for
    // this: real per-merchant products (MerchantProductDto, the same catalog Commerce/Eats
    // checkout already uses) and real transaction-verified reviews (EatsReviewDto, real
    // text + rating + optional photo + real owner replies, already used by Eats' own review
    // UI on all 3 platforms). Both fetched only for a real itunda merchant match (never for
    // a generic OSM/Nominatim place, which has neither) and both tabs only ever render if
    // the real fetch actually returned content -- no empty/fake tab shown while loading or
    // for a merchant that genuinely has none yet.
    var placeTab by remember { mutableStateOf(PlaceTab.HOME) }
    var placeProducts by remember { mutableStateOf<List<MerchantProductDto>?>(null) }
    var placeReviews by remember { mutableStateOf<List<EatsReviewDto>?>(null) }
    // Computed once here (shared by the fetch effect below and the detail-sheet render
    // block) rather than duplicating the same coordinate-match lookup in both places.
    val selectedMerchant = selectedPlace?.let { place -> merchants.find { it.latitude == place.latitude && it.longitude == place.longitude } }
    LaunchedEffect(selectedMerchant?.merchantId) {
        placeTab = PlaceTab.HOME
        placeProducts = null
        placeReviews = null
        val merchantId = selectedMerchant?.merchantId ?: return@LaunchedEffect
        try {
            placeProducts = NetworkClient.apiService.getMerchantProducts(merchantId).products.filter { it.active }
        } catch (_: Exception) {
            // Real, honest failure mode: a merchant with no real Commerce/Eats catalog
            // (a 404, or simply none) leaves the Menu tab silently absent, same as an
            // empty list -- never a fabricated placeholder menu.
        }
        try {
            placeReviews = NetworkClient.apiService.getRestaurantReviews(merchantId).reviews
        } catch (_: Exception) {
            // Same honesty: a merchant with no Eats review history (not a restaurant, or
            // genuinely zero reviews yet) leaves the Reviews tab silently absent.
        }
    }
    var route by remember { mutableStateOf<MapsDirectionsResponse?>(null) }
    // Real alternative routes (2026-07-22) -- see MapsDirectionsAlternativesResponse's
    // own doc comment on the network client. Often just a single-element list -- OSRM
    // itself decides whether a real alternative exists for a given trip.
    var routeAlternatives by remember { mutableStateOf<List<RouteResultDto>?>(null) }
    var selectedRouteIndex by remember { mutableStateOf(0) }
    // A deliberately bounded itinerary builder: the real Maps endpoint accepts the
    // start plus one to six ordered places (2–7 stops total). Search results are used
    // as the picker so these are genuine geocoded Rwanda places, not typed coordinates.
    var itineraryBuilding by remember { mutableStateOf(false) }
    var itineraryStops by remember { mutableStateOf<List<PlaceSearchResultDto>>(emptyList()) }
    var showingItineraryRoute by remember { mutableStateOf(false) }
    // Real driving/walking toggle (2026-07-22) -- see OsrmRoutingClient.route's own doc
    // comment on the backend for the real, separately-deployed foot-profile OSRM
    // instance this reaches.
    var travelMode by remember { mutableStateOf("DRIVING") }
    var showSteps by remember { mutableStateOf(false) }
    var routing by remember { mutableStateOf(false) }
    var locating by remember { mutableStateOf(false) }
    var myLocation by remember { mutableStateOf<Pair<Double, Double>?>(null) } // lat, lng
    var error by remember { mutableStateOf<String?>(null) }
    var activeCategory by remember { mutableStateOf<String?>(null) }
    var categoryLoading by remember { mutableStateOf(false) }
    var categoryResults by remember { mutableStateOf<List<NearbyPlaceDto>?>(null) }
    var bookmarks by remember { mutableStateOf<List<MapBookmarkDto>>(emptyList()) }
    var bookmarking by remember { mutableStateOf(false) }
    // Real Naver Map-style public/private folder + share (2026-08-04) -- see
    // SetMapFolderVisibilityRequest's own doc comment on the backend.
    val currentUserId = remember { NetworkClient.currentTokenStore().let(TokenStore::getUserId) }
    var sharingFolder by remember { mutableStateOf<String?>(null) }
    var shareConfirmation by remember { mutableStateOf<String?>(null) }
    // Real folder/color picker (2026-07-22) -- see MapBookmarkDto's own doc comment;
    // ported from bank-mfe's own real save-time picker. `savingToFolder` holds whichever
    // real place's picker is currently expanded (null = closed).
    var savingToFolder by remember { mutableStateOf<PlaceSearchResultDto?>(null) }
    var folderNameInput by remember { mutableStateOf(DEFAULT_BOOKMARK_FOLDER) }
    var folderColorInput by remember { mutableStateOf(BOOKMARK_COLOR_PALETTE[0]) }
    // Real "move to folder" (found 2026-07-22 fully built on the backend,
    // PATCH /api/v1/maps/bookmarks, with zero UI anywhere) -- movingBookmark holds
    // whichever real bookmark's move-picker is currently expanded (null = closed).
    var movingBookmark by remember { mutableStateOf<MapBookmarkDto?>(null) }
    var moveFolderNameInput by remember { mutableStateOf("") }
    var moveFolderColorInput by remember { mutableStateOf(BOOKMARK_COLOR_PALETTE[0]) }

    // Real distance-measurement (ruler) tool state (2026-07-23) -- plain (lat, lng)
    // pairs in tap order, same convention bank-mfe's own MapView.tsx uses.
    var measuring by remember { mutableStateOf(false) }
    var measurePoints by remember { mutableStateOf<List<Pair<Double, Double>>>(emptyList()) }
    var lastMeasuredPlaceName by remember { mutableStateOf<String?>(null) }

    // Real draggable bottom-sheet state (peek/half/full) -- see `MapSheetValue`'s own
    // doc comment. `density` is needed both here (for the velocity threshold, in real
    // pixels) and again below once `BoxWithConstraints` supplies the real measured
    // screen height to compute the sheet's anchors.
    val density = LocalDensity.current
    val sheetState = remember {
        AnchoredDraggableState(
            initialValue = MapSheetValue.Peek,
            positionalThreshold = { distance: Float -> distance * 0.5f },
            velocityThreshold = { with(density) { 125.dp.toPx() } },
            animationSpec = tween(),
        )
    }
    // A newly-selected place should be immediately visible without requiring a manual
    // drag -- expands to Half; clearing the selection (e.g. a fresh search) relaxes
    // back to Peek rather than staying pinned open over an empty card.
    LaunchedEffect(selectedPlace) {
        if (selectedPlace != null) sheetState.animateTo(MapSheetValue.Half) else sheetState.animateTo(MapSheetValue.Peek)
    }

    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    fun fetchRealLocation() {
        locating = true
        error = null
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
            .addOnSuccessListener { location ->
                locating = false
                if (location != null) {
                    myLocation = location.latitude to location.longitude
                } else {
                    error = "Could not access your real location right now."
                }
            }
            .addOnFailureListener {
                locating = false
                error = "Could not access your real location right now."
            }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) fetchRealLocation() else error = "Location permission was denied."
    }

    fun requestMyLocation() {
        val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (hasPermission) fetchRealLocation() else locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    // Real straight-line distance -- the same Haversine great-circle formula
    // rw.itunda.core.geo.GeoUtils.haversineKm implements on the backend, kept as a
    // plain local function here since the ruler tool and navigation both need it to update
    // live, not once per API call. Declared here (moved up from its original position near
    // the ruler tool below) since "Start Navigation"'s rerouting effect needs it earlier in
    // this composable than a local function's declaration order otherwise allows.
    fun haversineKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = kotlin.math.sin(dLat / 2).let { it * it } +
            kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
            kotlin.math.sin(dLng / 2).let { it * it }
        return r * 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
    }

    // Real "Start Navigation" mode (2026-08-09) -- found live on the physical device: a
    // computed route + a static, all-at-once step list ("doesn't feel like real navigation as
    // Naver Maps or other maps") is a route planner, not a navigator. This closes that gap: a
    // live-tracked mode that polls the device's real GPS fix every 4s (reusing
    // fetchRealLocation's existing one-shot fetch, same pattern RiderLocationPusher already
    // uses for continuous tracking elsewhere in this codebase), follows the user with the
    // camera, and auto-advances the highlighted current step as they actually travel --
    // instead of a flat list read once before setting off.
    var navigating by remember { mutableStateOf(false) }
    var currentStepIndex by remember { mutableStateOf(0) }
    // Real destination captured at the moment navigation starts (2026-08-09) -- needed by
    // rerouting below, since `selectedPlace`/`itineraryStops` can change shape (itinerary vs.
    // single place) while `route` itself doesn't carry a destination coordinate back.
    var navigationDestination by remember { mutableStateOf<Pair<Double, Double>?>(null) }

    LaunchedEffect(navigating) {
        while (navigating) {
            fetchRealLocation()
            delay(4000)
        }
    }

    // Real live rerouting-on-deviation (2026-08-09) -- explicitly scoped out of the first
    // navigation pass as a real follow-up, now built: real turn-by-turn apps recompute the
    // route the moment you actually miss a turn, instead of leaving you following a line that
    // no longer matches where you are. Reuses currentStepIndexFor's own nearest-point distance
    // (below) -- if the user's live GPS fix is more than 60m from the route polyline, re-fetch
    // directions from their real current position to the same real destination.
    var rerouting by remember { mutableStateOf(false) }
    LaunchedEffect(myLocation, navigating) {
        if (!navigating || rerouting) return@LaunchedEffect
        val (lat, lng) = myLocation ?: return@LaunchedEffect
        val activeRoute = route?.route ?: return@LaunchedEffect
        val dest = navigationDestination ?: return@LaunchedEffect
        val nearestKm = activeRoute.geometry.minOfOrNull { (glat, glng) -> haversineKm(glat, glng, lat, lng) } ?: return@LaunchedEffect
        if (nearestKm * 1000.0 <= 60.0) return@LaunchedEffect
        rerouting = true
        try {
            val response = NetworkClient.apiService.getDirections(lat, lng, dest.first, dest.second, travelMode)
            route = MapsDirectionsResponse(success = response.success, route = response.route)
            routeAlternatives = null
            currentStepIndex = 0
        } catch (_: Exception) {
            // A failed reroute attempt shouldn't interrupt navigation -- the stale route stays
            // on screen and the next location update simply tries again.
        } finally {
            rerouting = false
        }
    }

    // Real voice guidance (2026-08-09) -- explicitly scoped out of the first navigation pass
    // as "a real, separate, larger feature," now built. Plain android.speech.tts.TextToSpeech,
    // no new dependency. Initialized once for the composable's lifetime; a failed init (some
    // devices genuinely have no TTS engine installed) silently disables voice rather than
    // crashing or showing an error for a non-essential feature.
    var voiceEnabled by remember { mutableStateOf(true) }
    var ttsReady by remember { mutableStateOf(false) }
    val tts = remember {
        arrayOfNulls<TextToSpeech>(1).also { holder ->
            holder[0] = TextToSpeech(context) { status -> ttsReady = status == TextToSpeech.SUCCESS }
        }[0]!!
    }
    DisposableEffect(Unit) {
        onDispose { tts.stop(); tts.shutdown() }
    }
    LaunchedEffect(currentStepIndex, navigating) {
        if (!navigating || !ttsReady || !voiceEnabled) return@LaunchedEffect
        val instruction = route?.route?.steps?.getOrNull(currentStepIndex)?.instruction ?: return@LaunchedEffect
        tts.speak(instruction, TextToSpeech.QUEUE_FLUSH, null, "itunda-nav-step-$currentStepIndex")
    }

    // Real safety net: `route` is cleared to null at 8 separate call sites (new search,
    // cleared selection, itinerary edits, etc.) -- rather than touching every one of them to
    // also reset navigating/currentStepIndex, react to the one thing they all have in common.
    LaunchedEffect(route) {
        if (route == null) {
            navigating = false
            currentStepIndex = 0
            navigationDestination = null
        }
    }

    // Shared by both the search field's own leading icon and its keyboard "search"
    // IME action -- runs immediately, bypassing the debounce below.
    fun runSearch() {
        if (searching || query.isBlank()) return
        coroutineScope.launch {
            searching = true
            error = null
            try {
                searchResults = NetworkClient.apiService.searchPlaces(query).results
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: Exception) {
                error = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                searching = false
            }
        }
    }

    // Authenticated app-to-map handoff (2026-07-22): `itunda://maps/search?query=`
    // opens the same real Nominatim-backed search users get from the field, rather than
    // making another Itunda surface recreate map search locally.
    LaunchedEffect(initialSearchQuery) {
        initialSearchQuery?.trim()?.takeIf { it.isNotEmpty() }?.let { query = it }
    }

    // Real search-as-you-type autocomplete (2026-07-22) -- ported from bank-mfe's own
    // real debounced live-search. `LaunchedEffect(query)` gives this the exact debounce
    // behavior for free: Compose automatically cancels the in-flight coroutine from a
    // stale keystroke the moment `query` changes again, so a slower "Kigal" response can
    // never overwrite a newer "Kigali" one -- no manual request-id guard needed, unlike
    // bank-mfe's own JS setTimeout-based version.
    LaunchedEffect(query) {
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            searchResults = null
            return@LaunchedEffect
        }
        delay(350)
        searching = true
        error = null
        try {
            searchResults = NetworkClient.apiService.searchPlaces(trimmed).results
        } catch (e: HttpException) {
            error = superAppErrorMessage(e)
        } catch (e: Exception) {
            error = "Couldn't reach itunda. Check your connection and try again."
        } finally {
            searching = false
        }
    }

    LaunchedEffect(Unit) {
        recentSearches = recentSearchesStore.getAll()
    }

    LaunchedEffect(Unit) {
        try {
            val response = NetworkClient.apiService.getShoppingMerchants()
            merchants = response.merchants.filter { it.latitude != null && it.longitude != null }
        } catch (e: Exception) {
            // Honest partial failure -- the base map still renders even if the real
            // merchant overlay fails to load, never a blank screen for a real infra hiccup.
        }
    }
    LaunchedEffect(Unit) {
        try {
            bookmarks = NetworkClient.apiService.getMyMapBookmarks().bookmarks
        } catch (e: Exception) {
            // Honest partial failure -- bookmarks are a real-nice-to-have, never block the
            // rest of the Maps feature set from loading.
        }
    }
    val currentMerchants by rememberUpdatedState(merchants)
    val currentCategoryResults by rememberUpdatedState(categoryResults)
    val currentMeasuring by rememberUpdatedState(measuring)
    val currentMeasurePoints by rememberUpdatedState(measurePoints)

    fun isBookmarked(place: PlaceSearchResultDto): Boolean =
        bookmarks.any { it.latitude == place.latitude && it.longitude == place.longitude }

    fun toggleBookmark(place: PlaceSearchResultDto) {
        if (isBookmarked(place)) {
            coroutineScope.launch {
                bookmarking = true
                error = null
                try {
                    NetworkClient.apiService.removeMapBookmark(place.latitude, place.longitude)
                    bookmarks = bookmarks.filterNot { it.latitude == place.latitude && it.longitude == place.longitude }
                } catch (e: HttpException) {
                    error = superAppErrorMessage(e)
                } catch (e: Exception) {
                    error = "Couldn't reach itunda. Check your connection and try again."
                } finally {
                    bookmarking = false
                }
            }
            return
        }
        // Real folder/color picker (2026-07-22) -- opens inline rather than saving
        // straight to the default folder, defaulting to whichever real folder was used
        // last (ported from bank-mfe's own real save-time picker).
        folderNameInput = bookmarks.firstOrNull()?.folderName ?: DEFAULT_BOOKMARK_FOLDER
        folderColorInput = bookmarks.firstOrNull()?.color ?: BOOKMARK_COLOR_PALETTE[0]
        savingToFolder = place
    }

    // Real Naver Map-style public/private folder + share (2026-08-04) -- see
    // SetMapFolderVisibilityRequest's own doc comment on the backend. Toggles the whole
    // folder (every bookmark in it), matching what "Share" on a named list actually means
    // -- not a single pin. On making it public, opens Android's native share sheet with a
    // real itunda:// deep link, same real "genuinely resolves to real content" bar the
    // per-place 📤 share above deliberately doesn't clear (that one is plain text because
    // no public per-place page exists; a shared folder now genuinely has one).
    fun toggleFolderShare(folderName: String, makePublic: Boolean) {
        sharingFolder = folderName
        coroutineScope.launch {
            try {
                NetworkClient.apiService.setMapFolderVisibility(SetMapFolderVisibilityRequest(folderName, makePublic))
                bookmarks = bookmarks.map { if (it.folderName == folderName) it.copy(isPublic = makePublic) else it }
                if (makePublic && currentUserId != null) {
                    val link = "itunda://maps/shared/$currentUserId/${Uri.encode(folderName)}"
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "Check out my \"$folderName\" places on itunda Maps: $link")
                    }
                    context.startActivity(Intent.createChooser(intent, folderName))
                } else {
                    shareConfirmation = "\"$folderName\" is now private."
                }
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: Exception) {
                error = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                sharingFolder = null
            }
        }
    }

    fun confirmSaveToFolder() {
        val place = savingToFolder ?: return
        coroutineScope.launch {
            bookmarking = true
            error = null
            try {
                val saved = NetworkClient.apiService.addMapBookmark(
                    AddMapBookmarkRequest(place.displayName, place.latitude, place.longitude, folderNameInput, folderColorInput),
                ).bookmark
                bookmarks = listOf(saved) + bookmarks
                savingToFolder = null
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: Exception) {
                error = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                bookmarking = false
            }
        }
    }

    // Real "Smart Around"-style default state (2026-08-04) -- see MapsService's own
    // getAroundMe/getTrendingSavedPlaces doc comments on the backend for the real,
    // directly-re-fetched Naver Map source (brunch.co.kr/@bydot/4) and the honest scope
    // decision: itunda has real data for exactly 2 of Naver's 5 real default-state
    // sections ("주변"/nearby and a real cross-user "이번 주에 많이 저장한"/popular-this-week
    // aggregate) -- the other 3 imply editorial curation or a "date opened" signal itunda
    // has no real source for, so they're deliberately not built as fabricated lists.
    var aroundMePlaces by remember { mutableStateOf<List<NearbyPlaceDto>?>(null) }
    var trendingPlaces by remember { mutableStateOf<List<TrendingPlaceDto>?>(null) }
    fun loadAroundMe() {
        val center = myLocation ?: (RWANDA_CENTER_LAT to RWANDA_CENTER_LNG)
        coroutineScope.launch {
            try { aroundMePlaces = NetworkClient.apiService.getMapAroundMe(center.first, center.second).places } catch (e: Exception) { /* best-effort -- the rest of the default state still works */ }
        }
        coroutineScope.launch {
            try { trendingPlaces = NetworkClient.apiService.getMapTrending().places } catch (e: Exception) { /* best-effort */ }
        }
    }
    // Re-runs once a real device location lands so "around me" reflects it instead of
    // staying pinned to the Kigali-center fallback for the whole session.
    LaunchedEffect(myLocation) { loadAroundMe() }

    fun searchNearbyCategory(categoryId: String) {
        if (activeCategory == categoryId) {
            activeCategory = null
            categoryResults = null
            return
        }
        val center = myLocation ?: (RWANDA_CENTER_LAT to RWANDA_CENTER_LNG)
        activeCategory = categoryId
        coroutineScope.launch {
            categoryLoading = true
            error = null
            try {
                categoryResults = if (categoryId == "ITUNDA_AGENT") {
                    NetworkClient.apiService.searchNearbyAgents(center.first, center.second).agents.map {
                        NearbyPlaceDto(it.displayName, it.latitude, it.longitude, it.distanceKm)
                    }
                } else {
                    NetworkClient.apiService.searchNearbyPlaces(categoryId, center.first, center.second).places
                }
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
                activeCategory = null
            } catch (e: Exception) {
                error = "Couldn't reach itunda. Check your connection and try again."
                activeCategory = null
            } finally {
                categoryLoading = false
            }
        }
    }

    // Real "just select, don't auto-route" (2026-07-23) -- the same lighter behavior
    // bank-mfe's own MapView.tsx selectPlace already has, factored out of the two
    // inline copies already in this file (search results, recent searches) so a third
    // real call site (tapping a merchant/nearby pin directly on the map) doesn't need a
    // fourth copy. Unlike selectAndRoute below, this never fetches directions --
    // tapping an already-visible pin shouldn't immediately start routing to it.
    fun selectPlace(place: PlaceSearchResultDto) {
        showingItineraryRoute = false
        selectedPlace = place
        searchResults = null
        route = null
        routeAlternatives = null
        selectedRouteIndex = 0
        showSteps = false
        savingToFolder = null
    }

    fun selectAndRoute(place: PlaceSearchResultDto) {
        showingItineraryRoute = false
        selectedPlace = place
        route = null; routeAlternatives = null; selectedRouteIndex = 0; savingToFolder = null
        coroutineScope.launch {
            routing = true; error = null
            try {
                val origin = myLocation ?: (RWANDA_CENTER_LAT to RWANDA_CENTER_LNG)
                val response = NetworkClient.apiService.getDirectionsAlternatives(origin.first, origin.second, place.latitude, place.longitude, travelMode)
                routeAlternatives = response.routes; selectedRouteIndex = 0
                route = MapsDirectionsResponse(success = true, route = response.routes[0]); showSteps = false
            } catch (e: HttpException) { error = superAppErrorMessage(e) }
            catch (e: Exception) { error = "Couldn't reach itunda. Check your connection and try again." }
            finally { routing = false }
        }
    }

    fun addItineraryStop(place: PlaceSearchResultDto) {
        if (itineraryStops.any { it.latitude == place.latitude && it.longitude == place.longitude }) {
            error = "That stop is already in this itinerary."
            return
        }
        if (itineraryStops.size >= 6) {
            error = "An itinerary can have up to 7 stops including your start."
            return
        }
        itineraryStops = itineraryStops + place
        selectedPlace = null
        route = null
        routeAlternatives = null
        showingItineraryRoute = false
        searchResults = null
        query = ""
        searchFocused = false
        error = null
    }

    fun fetchItinerary(mode: String = travelMode) {
        if (itineraryStops.isEmpty()) {
            error = "Add at least one destination to plan an itinerary."
            return
        }
        coroutineScope.launch {
            routing = true
            error = null
            try {
                val origin = myLocation ?: (RWANDA_CENTER_LAT to RWANDA_CENTER_LNG)
                val response = NetworkClient.apiService.getItineraryDirections(
                    ItineraryDirectionsRequest(
                        waypoints = listOf(ItineraryWaypointRequest(origin.first, origin.second)) +
                            itineraryStops.map { ItineraryWaypointRequest(it.latitude, it.longitude) },
                        mode = mode,
                    ),
                )
                travelMode = mode
                route = MapsDirectionsResponse(success = response.success, route = response.route)
                routeAlternatives = null
                selectedRouteIndex = 0
                showingItineraryRoute = true
                showSteps = false
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: Exception) {
                error = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                routing = false
            }
        }
    }

    // Real distance-measurement (ruler) tool (2026-07-23) -- ported from bank-mfe's own
    // real MapView.tsx toggleMeasuring/handleRouteItinerary. Genuinely distinct from
    // Directions: no road route, no OSRM call to enter/build it -- just the plain
    // straight-line distance between tapped points, until "Route itinerary" is tapped.
    fun toggleMeasuring() {
        measuring = !measuring
        measurePoints = emptyList()
        lastMeasuredPlaceName = null
        itineraryStops = emptyList()
    }

    // haversineKm moved above (before the "Start Navigation" state block) since rerouting
    // needs it earlier in this composable than its original position here.

    val measureTotalKm = measurePoints.zipWithNext().sumOf { (a, b) -> haversineKm(a.first, a.second, b.first, b.second) }

    // Real step-matching for "Start Navigation" mode -- OSRM's own route response has no
    // explicit step-to-geometry-index mapping (RouteStepDto is just instruction/distance/
    // streetName), so this does the same map-matching simplification real turn-by-turn
    // apps use under the hood: find the route's own geometry point nearest the user's live
    // GPS fix, sum the polyline distance up to that point ("distance traveled along the
    // route so far"), then find which step that distance falls into by walking each step's
    // own distanceMeters in order. Steps and geometry come from the same OSRM response, so
    // their total distances line up closely enough for this to track well in practice.
    fun currentStepIndexFor(activeRoute: RouteResultDto, userLat: Double, userLng: Double): Int {
        if (activeRoute.geometry.isEmpty() || activeRoute.steps.isEmpty()) return 0
        var nearestIdx = 0
        var nearestDistKm = Double.MAX_VALUE
        var cumulativeKm = 0.0
        val cumulativeAtIndex = DoubleArray(activeRoute.geometry.size)
        for (i in activeRoute.geometry.indices) {
            val (lat, lng) = activeRoute.geometry[i]
            if (i > 0) {
                val (prevLat, prevLng) = activeRoute.geometry[i - 1]
                cumulativeKm += haversineKm(prevLat, prevLng, lat, lng)
            }
            cumulativeAtIndex[i] = cumulativeKm
            val distToUser = haversineKm(lat, lng, userLat, userLng)
            if (distToUser < nearestDistKm) {
                nearestDistKm = distToUser
                nearestIdx = i
            }
        }
        val distanceTraveledKm = cumulativeAtIndex[nearestIdx]
        var stepCumulativeKm = 0.0
        activeRoute.steps.forEachIndexed { i, step ->
            stepCumulativeKm += step.distanceMeters / 1000.0
            if (distanceTraveledKm <= stepCumulativeKm) return i
        }
        return activeRoute.steps.size - 1
    }

    LaunchedEffect(myLocation, navigating, route) {
        if (!navigating) return@LaunchedEffect
        val (lat, lng) = myLocation ?: return@LaunchedEffect
        val activeRoute = route?.route ?: return@LaunchedEffect
        currentStepIndex = currentStepIndexFor(activeRoute, lat, lng)
    }

    // Converts the ruler's tapped points directly into a real driving/walking route --
    // unlike fetchItinerary above, this does NOT prepend myLocation as an implicit
    // origin: the ruler's own first tapped point IS the start, matching bank-mfe's own
    // handleRouteItinerary exactly.
    fun routeMeasuredItinerary(mode: String = travelMode) {
        if (measurePoints.size < 2 || measurePoints.size > 7) return
        coroutineScope.launch {
            routing = true
            error = null
            try {
                val response = NetworkClient.apiService.getItineraryDirections(
                    ItineraryDirectionsRequest(
                        waypoints = measurePoints.map { ItineraryWaypointRequest(it.first, it.second) },
                        mode = mode,
                    ),
                )
                travelMode = mode
                route = MapsDirectionsResponse(success = response.success, route = response.route)
                routeAlternatives = null
                selectedRouteIndex = 0
                itineraryStops = measurePoints.map { PlaceSearchResultDto("Measured point", it.first, it.second) }
                showingItineraryRoute = true
                showSteps = false
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: Exception) {
                error = "Could not find a route through these stops."
            } finally {
                routing = false
            }
        }
    }

    // A cash-out customer arrives here with the store network already selected.
    // This is intentionally the same nearby-search path as the map chips, so it
    // uses the public agent-discovery API rather than a duplicate client-side list.
    LaunchedEffect(initialCategory) {
        initialCategory?.takeIf { it in MAP_NEARBY_CATEGORIES.map { category -> category.id } }?.let {
            searchNearbyCategory(it)
        }
    }

    Scaffold { padding ->
        val mapView = remember { MapView(context) }
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_CREATE -> mapView.onCreate(null)
                    Lifecycle.Event.ON_START -> mapView.onStart()
                    Lifecycle.Event.ON_RESUME -> mapView.onResume()
                    Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                    Lifecycle.Event.ON_STOP -> mapView.onStop()
                    Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                    else -> {}
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            mapView.onCreate(null)
            mapView.getMapAsync { map ->
                map.cameraPosition = CameraPosition.Builder()
                    .target(LatLng(RWANDA_CENTER_LAT, RWANDA_CENTER_LNG))
                    .zoom(12.0)
                    .build()
                map.setStyle(Style.Builder().fromJson(MAP_STYLE_JSON)) { style ->
                    val pinDensity = context.resources.displayMetrics.density
                    style.addImage(MERCHANT_ICON_ID, createPinBitmap(pinDensity, "#3182F6"))
                    style.addImage(DESTINATION_ICON_ID, createPinBitmap(pinDensity, "#E53935"))
                    // Real fix (2026-08-09), same UI/UX cleanup as the category chips: this was
                    // the same stray purple (#8B5CF6), not itunda's real palette anywhere --
                    // used for EVERY category-search pin (restaurants, hospitals, banks, all of
                    // them), a very frequently-seen element. Real Naver Maps uses a warm
                    // amber/orange for exactly this kind of general "place" pin (see its own
                    // food/cafe category badges) -- matches Ids.colors.warning (#FFA000) here,
                    // a real, already-defined semantic token, not a new invented color.
                    style.addImage(NEARBY_ICON_ID, createPinBitmap(pinDensity, "#FFA000"))

                    style.addSource(GeoJsonSource(MERCHANTS_SOURCE_ID, FeatureCollection.fromFeatures(emptyArray())))
                    style.addLayer(
                        SymbolLayer(MERCHANTS_LAYER_ID, MERCHANTS_SOURCE_ID).withProperties(
                            iconImage(MERCHANT_ICON_ID), iconAnchor(Property.ICON_ANCHOR_BOTTOM),
                            iconAllowOverlap(true), iconSize(0.85f),
                        ),
                    )
                    // Real drawn route (2026-07-19) -- rendered before the location/
                    // destination pins so they paint on top of the line.
                    style.addSource(GeoJsonSource(ROUTE_SOURCE_ID, FeatureCollection.fromFeatures(emptyArray())))
                    style.addLayer(
                        LineLayer(ROUTE_LAYER_ID, ROUTE_SOURCE_ID).withProperties(
                            lineColor("#3182F6"), lineWidth(5f), lineOpacity(0.9f),
                            lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND),
                        ),
                    )
                    style.addSource(GeoJsonSource(DESTINATION_SOURCE_ID, FeatureCollection.fromFeatures(emptyArray())))
                    style.addLayer(
                        SymbolLayer(DESTINATION_LAYER_ID, DESTINATION_SOURCE_ID).withProperties(
                            iconImage(DESTINATION_ICON_ID), iconAnchor(Property.ICON_ANCHOR_BOTTOM),
                            iconAllowOverlap(true), iconSize(1f),
                        ),
                    )
                    // Real "my location" blue dot with a soft translucent accuracy halo
                    // beneath it -- a distinct style from the merchant/destination pins,
                    // matching Google Maps/Naver/Kakao's own real convention that the
                    // user's own position is a plain dot, never a pin.
                    style.addSource(GeoJsonSource(MY_LOCATION_SOURCE_ID, FeatureCollection.fromFeatures(emptyArray())))
                    style.addLayer(
                        CircleLayer("$MY_LOCATION_LAYER_ID-halo", MY_LOCATION_SOURCE_ID).withProperties(
                            circleRadius(18f), circleColor("#3182F6"), circleOpacity(0.16f),
                        ),
                    )
                    style.addLayer(
                        CircleLayer(MY_LOCATION_LAYER_ID, MY_LOCATION_SOURCE_ID).withProperties(
                            circleRadius(7f), circleColor("#3182F6"), circleStrokeWidth(3f), circleStrokeColor("#ffffff"),
                        ),
                    )
                    // Real "nearby places" category-search markers (2026-07-19) -- a
                    // distinct violet pin, same accent color as bank-mfe's MapView.tsx
                    // category chips.
                    style.addSource(GeoJsonSource(NEARBY_SOURCE_ID, FeatureCollection.fromFeatures(emptyArray())))
                    style.addLayer(
                        SymbolLayer(NEARBY_LAYER_ID, NEARBY_SOURCE_ID).withProperties(
                            iconImage(NEARBY_ICON_ID), iconAnchor(Property.ICON_ANCHOR_BOTTOM),
                            iconAllowOverlap(true), iconSize(0.85f),
                        ),
                    )
                    val featureCollection = FeatureCollection.fromFeatures(
                        currentMerchants.map { m -> Feature.fromGeometry(Point.fromLngLat(m.longitude!!, m.latitude!!)) },
                    )
                    (style.getSourceAs<GeoJsonSource>(MERCHANTS_SOURCE_ID))?.setGeoJson(featureCollection)

                    // Real distance-measurement (ruler) tool line -- dashed, and a
                    // deliberately different color from ROUTE_LAYER_ID above, so a real
                    // OSRM road route and a plain straight-line measurement are never
                    // visually confused.
                    style.addSource(GeoJsonSource(MEASURE_SOURCE_ID, FeatureCollection.fromFeatures(emptyArray())))
                    style.addLayer(
                        LineLayer(MEASURE_LINE_LAYER_ID, MEASURE_SOURCE_ID).withProperties(
                            lineColor("#E53935"), lineWidth(3f), lineDasharray(arrayOf(2f, 1.5f)),
                            lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND),
                        ),
                    )
                    style.addLayer(
                        CircleLayer(MEASURE_POINTS_LAYER_ID, MEASURE_SOURCE_ID).withProperties(
                            circleRadius(6f), circleColor("#E53935"), circleStrokeWidth(2f), circleStrokeColor("#ffffff"),
                        ),
                    )
                }

                // Real map-tap infrastructure (2026-07-23) -- MapLibre has no per-marker
                // click handler for GeoJsonSource-backed pins (unlike bank-mfe's own web
                // maplibregl.Marker, a real DOM element per merchant with its own click
                // listener) -- a tap must query which rendered feature, if any, sits under
                // it. Two real behaviors share this one listener: while measuring, every
                // tap adds a ruler point; otherwise a tap that lands on a real merchant or
                // nearby-place pin opens the same detail sheet a search result tap does
                // (matched back to the loaded list by coordinate, the same technique
                // bank-mfe's own selectedMerchant lookup already uses).
                map.addOnMapClickListener { latLng ->
                    if (currentMeasuring) {
                        if (currentMeasurePoints.size < 7) {
                            val newPoint = latLng.latitude to latLng.longitude
                            measurePoints = currentMeasurePoints + newPoint
                            lastMeasuredPlaceName = "Finding area…"
                            coroutineScope.launch {
                                try {
                                    val name = NetworkClient.apiService.reverseGeocode(newPoint.first, newPoint.second).placeName
                                    if (measurePoints.lastOrNull() == newPoint) lastMeasuredPlaceName = name
                                } catch (e: Exception) {
                                    if (measurePoints.lastOrNull() == newPoint) lastMeasuredPlaceName = null
                                }
                            }
                        }
                        true
                    } else {
                        val screenPoint = map.projection.toScreenLocation(latLng)
                        val tapped = map.queryRenderedFeatures(screenPoint, MERCHANTS_LAYER_ID, NEARBY_LAYER_ID).firstOrNull()
                        val point = tapped?.geometry() as? Point
                        if (point != null) {
                            val lat = point.latitude()
                            val lng = point.longitude()
                            val merchant = currentMerchants.find { it.latitude == lat && it.longitude == lng }
                            val nearby = currentCategoryResults?.find { it.latitude == lat && it.longitude == lng }
                            val place = when {
                                merchant != null -> PlaceSearchResultDto(merchant.businessName, lat, lng)
                                nearby != null -> PlaceSearchResultDto(nearby.displayName, lat, lng)
                                else -> null
                            }
                            if (place != null) selectPlace(place)
                            place != null
                        } else {
                            false
                        }
                    }
                }
            }
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                mapView.onPause()
                mapView.onStop()
                mapView.onDestroy()
            }
        }
        // Real distance-measurement (ruler) tool -- keeps the measure GeoJSON source (a
        // dot per tapped point, a dashed line once there are 2+) in sync with real
        // tapped points, and clears it whenever measuring is turned off.
        LaunchedEffect(measurePoints) {
            mapView.getMapAsync { map ->
                val style = map.style ?: return@getMapAsync
                val source = style.getSourceAs<GeoJsonSource>(MEASURE_SOURCE_ID) ?: return@getMapAsync
                val points = measurePoints.map { (lat, lng) -> Feature.fromGeometry(Point.fromLngLat(lng, lat)) }
                val line = if (measurePoints.size > 1) {
                    listOf(Feature.fromGeometry(LineString.fromLngLats(measurePoints.map { (lat, lng) -> Point.fromLngLat(lng, lat) })))
                } else {
                    emptyList()
                }
                source.setGeoJson(FeatureCollection.fromFeatures(points + line))
            }
        }
        // Real merchants loading after the style is already built re-populates the
        // existing GeoJSON source rather than re-adding the whole style.
        LaunchedEffect(merchants) {
            mapView.getMapAsync { map ->
                val style = map.style ?: return@getMapAsync
                val source = style.getSourceAs<GeoJsonSource>(MERCHANTS_SOURCE_ID) ?: return@getMapAsync
                source.setGeoJson(
                    FeatureCollection.fromFeatures(
                        merchants.map { m -> Feature.fromGeometry(Point.fromLngLat(m.longitude!!, m.latitude!!)) },
                    ),
                )
            }
        }
        LaunchedEffect(myLocation) {
            val location = myLocation ?: return@LaunchedEffect
            mapView.getMapAsync { map ->
                val style = map.style ?: return@getMapAsync
                val source = style.getSourceAs<GeoJsonSource>(MY_LOCATION_SOURCE_ID) ?: return@getMapAsync
                source.setGeoJson(FeatureCollection.fromFeatures(arrayOf(Feature.fromGeometry(Point.fromLngLat(location.second, location.first)))))
                // Real navigation-mode camera (2026-08-09): a closer, street-level zoom while
                // actively navigating (matching Naver/Kakao's own turn-by-turn framing) instead
                // of the general-purpose "locate me" overview zoom every other caller of this
                // same location update wants.
                val zoom = if (navigating) 17.5 else 14.0
                map.easeCamera(org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(LatLng(location.first, location.second), zoom))
            }
        }
        LaunchedEffect(selectedPlace, itineraryStops, itineraryBuilding) {
            val place = selectedPlace
            mapView.getMapAsync { map ->
                val style = map.style ?: return@getMapAsync
                val source = style.getSourceAs<GeoJsonSource>(DESTINATION_SOURCE_ID) ?: return@getMapAsync
                val itineraryPins = if (itineraryBuilding) itineraryStops else emptyList()
                if (itineraryPins.isNotEmpty()) {
                    source.setGeoJson(
                        FeatureCollection.fromFeatures(
                            itineraryPins.map { stop -> Feature.fromGeometry(Point.fromLngLat(stop.longitude, stop.latitude)) },
                        ),
                    )
                    val finalStop = itineraryPins.last()
                    map.easeCamera(org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(LatLng(finalStop.latitude, finalStop.longitude), 14.0))
                } else if (place == null) {
                    source.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
                } else {
                    source.setGeoJson(FeatureCollection.fromFeatures(arrayOf(Feature.fromGeometry(Point.fromLngLat(place.longitude, place.latitude)))))
                    map.easeCamera(org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(LatLng(place.latitude, place.longitude), 15.0))
                }
                // A new destination needs a fresh "Directions" tap -- clear any
                // previously-drawn route.
                (style.getSourceAs<GeoJsonSource>(ROUTE_SOURCE_ID))?.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
            }
        }
        LaunchedEffect(categoryResults) {
            val places = categoryResults
            mapView.getMapAsync { map ->
                val style = map.style ?: return@getMapAsync
                val source = style.getSourceAs<GeoJsonSource>(NEARBY_SOURCE_ID) ?: return@getMapAsync
                source.setGeoJson(
                    FeatureCollection.fromFeatures(
                        (places ?: emptyList()).map { p -> Feature.fromGeometry(Point.fromLngLat(p.longitude, p.latitude)) },
                    ),
                )
            }
        }
        LaunchedEffect(route) {
            val result = route
            mapView.getMapAsync { map ->
                val style = map.style ?: return@getMapAsync
                val source = style.getSourceAs<GeoJsonSource>(ROUTE_SOURCE_ID) ?: return@getMapAsync
                if (result == null) {
                    source.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
                } else {
                    val points = result.route.geometry.map { (lat, lng) -> Point.fromLngLat(lng, lat) }
                    source.setGeoJson(FeatureCollection.fromFeature(Feature.fromGeometry(LineString.fromLngLats(points))))
                }
            }
        }

        // Full-bleed map with floating overlays (Box, not Column) -- matches the real Naver
        // Map/Kakao Map pattern where the map always fills the screen and search/details
        // panels float on top of it, rather than pushing it around in normal document flow.
        // Was previously a plain Column stacking search -> chips -> map -> details/bookmarks
        // in sequence, which could squeeze the map to a sliver or push bookmarks off-screen
        // entirely once a place was selected -- a real bug, not just a cosmetic mismatch.
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Real anchors for the draggable sheet below, computed from this
            // composable's own real measured height -- Peek shows just enough for a
            // drag handle + one summary line, Half covers roughly the lower half of the
            // map (enough to read a place's directions/steps without losing the whole
            // map), Full leaves a real gap at the top so the search bar/category chips
            // (which float above the sheet in z-order) always stay reachable.
            val fullHeightPx = with(density) { maxHeight.toPx() }
            val peekHeightPx = with(density) { 128.dp.toPx() }
            val fullTopGapPx = with(density) { 96.dp.toPx() }
            val sheetAnchors = remember(fullHeightPx) {
                DraggableAnchors {
                    MapSheetValue.Peek at (fullHeightPx - peekHeightPx)
                    MapSheetValue.Half at (fullHeightPx * 0.55f)
                    MapSheetValue.Full at fullTopGapPx
                }
            }
            LaunchedEffect(sheetAnchors) { sheetState.updateAnchors(sheetAnchors) }

            AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

            // Real floating chrome (2026-07-21 redesign) -- previously one flat,
            // edge-to-edge, opaque `Column` that visually read as a fixed toolbar
            // rather than floating over the map (the header comment above already
            // named "floating overlays" as the goal; the implementation didn't match
            // it). Now every piece -- back button, search pill, chip row -- is its own
            // individually-shadowed rounded surface with real map visible between them,
            // matching the actual Naver Map/Kakao Map/Google Maps chrome convention.
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .shadow(3.dp, CircleShape)
                        .background(Ids.colors.surface, CircleShape)
                        .clip(CircleShape)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.ArrowBackIosNew, contentDescription = "Back", modifier = Modifier.size(16.dp), tint = Ids.colors.textPrimary)
                }

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .shadow(3.dp, RoundedCornerShape(999.dp))
                        .background(Ids.colors.surface, RoundedCornerShape(999.dp))
                        .padding(start = 14.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.Search,
                        contentDescription = "Search",
                        tint = if (searching) Ids.colors.textSecondary else Ids.colors.brand,
                        modifier = Modifier.size(18.dp).clickable(enabled = !searching && query.isNotBlank()) { runSearch() },
                    )
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Search a real place in Rwanda", fontSize = 13.sp) },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                            unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                            disabledBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { runSearch() }),
                        modifier = Modifier.weight(1f).padding(horizontal = 6.dp)
                            .onFocusChanged { searchFocused = it.isFocused },
                    )
                    if (query.isNotBlank()) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = "Clear search",
                            tint = Ids.colors.textSecondary,
                            modifier = Modifier.size(16.dp).clickable { query = ""; searchResults = null },
                        )
                        Box(modifier = Modifier.width(6.dp))
                    }
                }
            }

            // Real category-chip "nearby places" search (Naver/Kakao's own convention) --
            // mirrors bank-mfe's MapView.tsx chip row, now with a per-category emoji glyph
            // (MAP_CATEGORY_ICONS) so chips read at a glance instead of as text-only pills.
            //
            // Real UI/UX fix (2026-08-09), found live after direct user feedback ("not good,
            // not simplicity"): the active chip used a hardcoded purple (0xFF8B5CF6) instead
            // of the app's real brand blue (Ids.colors.brand, Toss blue #3182F6) -- every
            // OTHER "active" element on this same screen (route-alternative picker, Start
            // Navigation card) correctly used the brand token, making this chip row visually
            // disjointed from the rest of the app. This and several other hardcoded hex colors
            // below also never adapted to dark mode (this session's own test device defaults
            // to system dark mode) while everything using Ids.colors.* correctly does --
            // very likely the real, concrete cause of "doesn't look good," not a vague
            // aesthetic complaint. Swept the whole file for the same pattern and fixed each.
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MAP_NEARBY_CATEGORIES.forEach { category ->
                    val active = activeCategory == category.id
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .shadow(if (active) 3.dp else 1.dp, RoundedCornerShape(999.dp))
                            .background(if (active) Ids.colors.brand else Ids.colors.surface, RoundedCornerShape(999.dp))
                            .clickable(enabled = !categoryLoading || active) { searchNearbyCategory(category.id) }
                            .padding(start = if (active) 12.dp else 6.dp, end = 12.dp, top = if (active) 8.dp else 6.dp, bottom = if (active) 8.dp else 6.dp),
                    ) {
                        // Real colored-circle icon badge (2026-08-09), matching real Naver
                        // Maps' own category-chip style (see 발견 tab's own 음식점/카페 chips) --
                        // itunda's chips previously had a plain inline emoji with no badge
                        // treatment at all. Only in the inactive state; the active state's
                        // solid blue fill + white label already reads clearly on its own,
                        // matching Naver's own selected-chip treatment.
                        if (active) {
                            Text(MAP_CATEGORY_ICONS[category.id] ?: "📍", fontSize = 13.sp)
                        } else {
                            Box(
                                modifier = Modifier.size(24.dp).clip(CircleShape).background(Ids.colors.warningTint),
                                contentAlignment = androidx.compose.ui.Alignment.Center,
                            ) {
                                Text(MAP_CATEGORY_ICONS[category.id] ?: "📍", fontSize = 12.sp)
                            }
                        }
                        Text(
                            if (active && categoryLoading) "…" else category.label,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (active) androidx.compose.ui.graphics.Color.White else Ids.colors.textPrimary,
                        )
                    }
                }
            }

            // A real multi-stop planner, not a second fake map mode. While active,
            // search results become ordered stops for the bounded OSRM itinerary API.
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .shadow(1.dp, RoundedCornerShape(999.dp))
                    .background(Ids.colors.surface, RoundedCornerShape(999.dp))
                    .clickable {
                        if (itineraryBuilding) {
                            itineraryBuilding = false
                            itineraryStops = emptyList()
                            showingItineraryRoute = false
                            route = null
                        } else {
                            itineraryBuilding = true
                            selectedPlace = null
                            route = null
                            routeAlternatives = null
                            showingItineraryRoute = false
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(if (itineraryBuilding) "✓ Planning ${itineraryStops.size + 1} stops" else "＋ Plan multi-stop trip", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (itineraryBuilding) Ids.colors.brand else Ids.colors.textPrimary)
                if (itineraryBuilding) Text("Tap to cancel", fontSize = 11.sp, color = Ids.colors.textSecondary)
            }

            // Real "Smart Around"-style default state (2026-08-04) -- see loadAroundMe's own
            // doc comment for the real, re-verified Naver Map sourcing and honest scope.
            // Gated to the true empty state: no place/category/search/itinerary active, so
            // this never competes with a result the user actually asked for.
            if (selectedPlace == null && activeCategory == null && searchResults == null && !searchFocused && !itineraryBuilding) {
                if (!aroundMePlaces.isNullOrEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(3.dp, RoundedCornerShape(Ids.layout.sectionCornerRadius))
                            .background(Ids.colors.surface, RoundedCornerShape(Ids.layout.sectionCornerRadius))
                            .padding(vertical = 8.dp),
                    ) {
                        Text("주변 · Nearby", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ids.colors.textSecondary, modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp))
                        Row(modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            aroundMePlaces!!.forEach { place ->
                                Column(
                                    modifier = Modifier
                                        .width(140.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Ids.colors.surfaceSoft)
                                        .clickable { selectPlace(PlaceSearchResultDto(place.displayName, place.latitude, place.longitude)) }
                                        .padding(10.dp),
                                ) {
                                    Text(splitPlaceName(place.displayName).first, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Ids.colors.textPrimary, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                    Text("%.1f km".format(place.distanceKm), fontSize = 11.sp, color = Ids.colors.textSecondary, modifier = Modifier.padding(top = 2.dp))
                                }
                            }
                        }
                    }
                }
                if (!trendingPlaces.isNullOrEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(3.dp, RoundedCornerShape(Ids.layout.sectionCornerRadius))
                            .background(Ids.colors.surface, RoundedCornerShape(Ids.layout.sectionCornerRadius))
                            .padding(vertical = 8.dp),
                    ) {
                        Text("이번 주에 많이 저장한 · Popular this week", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ids.colors.textSecondary, modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp))
                        Row(modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            trendingPlaces!!.forEach { place ->
                                Column(
                                    modifier = Modifier
                                        .width(140.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Ids.colors.surfaceSoft)
                                        .clickable { selectPlace(PlaceSearchResultDto(place.displayName, place.latitude, place.longitude)) }
                                        .padding(10.dp),
                                ) {
                                    Text(splitPlaceName(place.displayName).first, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Ids.colors.textPrimary, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                    Text("★ saved by ${place.saveCount}", fontSize = 11.sp, color = Ids.colors.textSecondary, modifier = Modifier.padding(top = 2.dp))
                                }
                            }
                        }
                    }
                }
            }

            if ((activeCategory != null && categoryResults != null) || searchResults != null || error != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(3.dp, RoundedCornerShape(Ids.layout.sectionCornerRadius))
                        .background(Ids.colors.surface, RoundedCornerShape(Ids.layout.sectionCornerRadius))
                        .padding(vertical = 4.dp),
                ) {
                    if (activeCategory != null && categoryResults != null) {
                        val label = MAP_NEARBY_CATEGORIES.firstOrNull { it.id == activeCategory }?.label?.lowercase()
                        Text(
                            if (categoryResults!!.isEmpty()) "No real matches found nearby for that category."
                            else if (isAgentCashDiscovery && activeCategory == "ITUNDA_AGENT") {
                                "${categoryResults!!.size} Itunda agents found nearby, closest first. Select one for directions."
                            } else "${categoryResults!!.size} real $label found nearby, closest first.",
                            color = Ids.colors.textSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        )
                    }

                    searchResults?.let { results ->
                        if (results.isEmpty()) {
                            Text("No real places found for that search.", color = Ids.colors.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(14.dp))
                        } else {
                            results.forEach { place ->
                                val (name, address) = splitPlaceName(place.displayName)
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            recentSearches = recentSearchesStore.add(place)
                                            if (itineraryBuilding) {
                                                addItineraryStop(place)
                                            } else {
                                                selectPlace(place)
                                            }
                                        }
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                ) {
                                    Text(name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ids.colors.textPrimary)
                                    if (address != null) {
                                        Text(address, fontSize = 11.sp, color = Ids.colors.textSecondary, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.padding(top = 1.dp))
                                    }
                                }
                            }
                        }
                    }

                    error?.let { Text(it, color = Ids.colors.danger, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) }
                }
            }

            // Real recent-searches list (2026-07-22) -- its own card, separately gated
            // from the search-results/category-results card above (that one only renders
            // when there's a real result set; this one renders instead of it, only while
            // the search box is focused and empty). Same real Naver/Kakao Maps convention
            // bank-mfe's own version already follows.
            if (searchFocused && query.isBlank() && recentSearches.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(3.dp, RoundedCornerShape(Ids.layout.sectionCornerRadius))
                        .background(Ids.colors.surface, RoundedCornerShape(Ids.layout.sectionCornerRadius))
                        .padding(vertical = 4.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                    ) {
                        Text("Recent searches", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Ids.colors.textSecondary)
                        Text(
                            "Clear", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Ids.colors.brand,
                            modifier = Modifier.clickable { recentSearchesStore.clear(); recentSearches = emptyList() },
                        )
                    }
                    recentSearches.forEach { place ->
                        Text(
                            "🕐 ${place.displayName}",
                            fontSize = 13.sp,
                            color = Ids.colors.textPrimary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    recentSearches = recentSearchesStore.add(place)
                                    if (itineraryBuilding) {
                                        addItineraryStop(place)
                                    } else {
                                        selectPlace(place)
                                    }
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                        )
                    }
                }
            }
            } // end floating top panel

            // Real distance-measurement (ruler) tool info badge (2026-07-23) -- only
            // shown while active, floats below the search chrome so it never fights the
            // docked bottom sheet for space. Ported from bank-mfe's own real version.
            if (measuring) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 76.dp)
                        .shadow(3.dp, RoundedCornerShape(999.dp))
                        .background(Ids.colors.surface, RoundedCornerShape(999.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(
                        when {
                            measurePoints.isEmpty() -> "Tap the map to add 2–7 stops"
                            measurePoints.size == 1 -> "Add 1 more stop to route it"
                            else -> "${measurePoints.size} stops · ${"%.2f".format(measureTotalKm)} km straight-line"
                        },
                        fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Ids.colors.textPrimary,
                    )
                    lastMeasuredPlaceName?.let {
                        Text(it, fontSize = 12.sp, color = Ids.colors.textSecondary, maxLines = 1)
                    }
                    if (measurePoints.isNotEmpty()) {
                        Text(
                            "Undo", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ids.colors.brand,
                            modifier = Modifier.clickable { measurePoints = measurePoints.dropLast(1); lastMeasuredPlaceName = null },
                        )
                    }
                    if (measurePoints.size >= 2) {
                        Text(
                            if (routing) "Routing…" else "Route itinerary",
                            fontSize = 12.sp, fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color.White,
                            modifier = Modifier
                                .background(Ids.colors.brand, RoundedCornerShape(999.dp))
                                .clickable(enabled = !routing) { routeMeasuredItinerary() }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                    Text(
                        "Done", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ids.colors.textTertiary,
                        modifier = Modifier.clickable { measuring = false; measurePoints = emptyList(); lastMeasuredPlaceName = null },
                    )
                }
            }

            // Real floating right-side controls (2026-07-21) -- zoom +/- and a dedicated
            // "locate me" button, matching the standard Google Maps/Naver Map/Kakao Map
            // convention of a vertical control stack on the right, distinct from the
            // search bar (which previously carried the locate icon inline, unlike any
            // real map app). Anchored above the sheet's own peek height so it's never
            // covered at rest.
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp)
                    .offset { IntOffset(0, -(peekHeightPx + with(density) { 16.dp.toPx() }).roundToInt()) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(
                    modifier = Modifier
                        .shadow(3.dp, RoundedCornerShape(14.dp))
                        .background(Ids.colors.surface, RoundedCornerShape(14.dp)),
                ) {
                    Box(
                        modifier = Modifier.size(44.dp).clickable {
                            mapView.getMapAsync { map -> map.easeCamera(CameraUpdateFactory.zoomIn()) }
                        },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Outlined.Add, contentDescription = "Zoom in", tint = Ids.colors.textPrimary, modifier = Modifier.size(18.dp)) }
                    Box(modifier = Modifier.width(44.dp).height(1.dp).background(Ids.colors.divider))
                    Box(
                        modifier = Modifier.size(44.dp).clickable {
                            mapView.getMapAsync { map -> map.easeCamera(CameraUpdateFactory.zoomOut()) }
                        },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Outlined.Remove, contentDescription = "Zoom out", tint = Ids.colors.textPrimary, modifier = Modifier.size(18.dp)) }
                }
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .shadow(3.dp, CircleShape)
                        .background(Ids.colors.surface, CircleShape)
                        .clip(CircleShape)
                        .clickable(enabled = !locating) { requestMyLocation() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.MyLocation,
                        contentDescription = "Find my real location",
                        tint = if (locating) Ids.colors.textSecondary else Ids.colors.brand,
                        modifier = Modifier.size(20.dp),
                    )
                }
                // Real distance-measurement (ruler) tool toggle (2026-07-23) -- Naver/
                // Kakao Maps' own real "measure distance" action, ported from bank-mfe's
                // own real MapView.tsx. Tap to enter measure mode, then tap points on the
                // map to build a straight-line path and see the real cumulative distance.
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .shadow(3.dp, CircleShape)
                        .background(if (measuring) Ids.colors.danger else Ids.colors.surface, CircleShape)
                        .clip(CircleShape)
                        .clickable { toggleMeasuring() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("📏", fontSize = 18.sp, color = if (measuring) androidx.compose.ui.graphics.Color.White else Ids.colors.textSecondary)
                }
            }

            // Real draggable peek/half/full bottom sheet (2026-07-21) -- a persistent,
            // non-modal panel docked over the map that the user can drag between three
            // real states, instead of a static Card that only ever appeared/vanished at
            // whatever height its content happened to need. Height is fixed to this
            // screen's own full measured height (`maxHeight`) and slid down via a real
            // pixel offset driven by `sheetState` -- only the bottom `peekHeightPx` of
            // it is visible at rest, matching Naver Map's Smart Around sheet.
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .height(maxHeight)
                    .offset {
                        // requireOffset() throws on the very first layout pass: updateAnchors()
                        // above only runs once its LaunchedEffect's coroutine is dispatched,
                        // which is after this frame's layout already ran once. Fall back to the
                        // peek position (this state's own initial value) for that one frame.
                        val offset = sheetState.offset.let { if (it.isNaN()) fullHeightPx - peekHeightPx else it }
                        IntOffset(0, offset.roundToInt())
                    }
                    .anchoredDraggable(sheetState, Orientation.Vertical),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Ids.colors.surface, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)),
                ) {
                    // Drag handle -- the real Naver Map/Kakao Map/iOS sheet convention
                    // signaling draggability at a glance, since nothing else about a
                    // docked (non-modal) panel otherwise implies it can be dragged.
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(top = 10.dp, bottom = 6.dp)
                            .width(36.dp)
                            .height(4.dp)
                            .background(Ids.colors.textSecondary.copy(alpha = 0.4f), RoundedCornerShape(2.dp)),
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val place = selectedPlace
                        if (place != null) {
                            val (placeName, placeAddress) = splitPlaceName(place.displayName)
                            // Real rich merchant detail (2026-08-09) -- found live: tapping a
                            // merchant pin already had real rating/photo/category/cashback data
                            // sitting in `merchants` (ShoppingMerchantDto, the exact same DTO
                            // Shop's own browse cards already render this way), but the map's
                            // click handler collapsed it down to a bare name+coordinate
                            // PlaceSearchResultDto before this sheet ever saw it. Matches back
                            // by coordinate -- the same technique the click handler itself
                            // already uses -- rather than threading a second selected-merchant
                            // state through the whole file.
                            val matchedMerchant = selectedMerchant
                            Row(verticalAlignment = Alignment.Top) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        placeName,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = Ids.colors.textPrimary,
                                    )
                                    if (placeAddress != null) {
                                        Text(placeAddress, fontSize = 12.sp, color = Ids.colors.textSecondary, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                                    }
                                }
                                // Real "share this place" (2026-07-22) -- ported from
                                // bank-mfe's own real Web Share/clipboard action. Plain
                                // name+coordinate text via Android's native share sheet,
                                // not a link into itunda's own domain -- there's no public
                                // per-place page a recipient outside this app could open.
                                Text(
                                    "📤",
                                    fontSize = 18.sp,
                                    modifier = Modifier.padding(end = 8.dp).clickable {
                                        val text = "${place.displayName} (${"%.6f".format(place.latitude)}, ${"%.6f".format(place.longitude)})"
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, text)
                                        }
                                        context.startActivity(Intent.createChooser(intent, place.displayName))
                                    },
                                )
                                Text(
                                    if (isBookmarked(place)) "★" else "☆",
                                    fontSize = 20.sp,
                                    color = if (isBookmarked(place)) androidx.compose.ui.graphics.Color(0xFFF5A623) else Ids.colors.textSecondary,
                                    modifier = Modifier.clickable(enabled = !bookmarking) { toggleBookmark(place) },
                                )
                            }
                            if (matchedMerchant != null) {
                                // Real "Itunda Places" tab row (2026-08-09) -- Menu/Reviews only
                                // appear once the real fetch in the LaunchedEffect above actually
                                // returned content, never as an empty promise. Home always shows
                                // the existing at-a-glance summary below.
                                val showMenuTab = !placeProducts.isNullOrEmpty()
                                val showReviewsTab = !placeReviews.isNullOrEmpty()
                                if (showMenuTab || showReviewsTab) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 8.dp)) {
                                        listOfNotNull(
                                            PlaceTab.HOME,
                                            PlaceTab.MENU.takeIf { showMenuTab },
                                            PlaceTab.REVIEWS.takeIf { showReviewsTab },
                                        ).forEach { tab ->
                                            val label = when (tab) {
                                                PlaceTab.HOME -> "Home"
                                                PlaceTab.MENU -> "Menu (${placeProducts?.size ?: 0})"
                                                PlaceTab.REVIEWS -> "Reviews (${placeReviews?.size ?: 0})"
                                                PlaceTab.INFO -> "Info"
                                            }
                                            val active = placeTab == tab
                                            Column(
                                                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                                                modifier = Modifier.clickable { placeTab = tab },
                                            ) {
                                                Text(
                                                    label, fontSize = 13.sp,
                                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (active) Ids.colors.brand else Ids.colors.textSecondary,
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .padding(top = 4.dp)
                                                        .height(2.dp)
                                                        .width(if (active) 20.dp else 0.dp)
                                                        .background(Ids.colors.brand, RoundedCornerShape(1.dp)),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            if (matchedMerchant != null && placeTab == PlaceTab.MENU) {
                                // Real per-merchant menu (2026-08-09) -- the exact same
                                // MerchantProductDto Commerce/Eats checkout already uses, not new
                                // or invented data.
                                Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    placeProducts.orEmpty().forEach { product ->
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            if (product.imageUrl != null) {
                                                AsyncImage(
                                                    model = product.imageUrl,
                                                    contentDescription = product.name,
                                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                                    modifier = Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)),
                                                )
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(product.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Ids.colors.textPrimary, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                                val original = product.originalPrice
                                                if (original != null && original > product.price) {
                                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                        Text("RWF ${original.toInt()}", fontSize = 11.sp, color = Ids.colors.textTertiary, textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough)
                                                        Text("RWF ${product.price.toInt()}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Ids.colors.danger)
                                                    }
                                                } else {
                                                    Text("RWF ${product.price.toInt()}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Ids.colors.textPrimary)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            if (matchedMerchant != null && placeTab == PlaceTab.REVIEWS) {
                                // Real transaction-verified reviews (2026-08-09) -- the exact
                                // same EatsReviewDto Eats' own review UI already renders
                                // (real text, real rating, optional real photo, real owner
                                // reply). No reviewer identity shown -- matches the existing
                                // Eats review UI's own convention exactly, not a new choice.
                                Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    placeReviews.orEmpty().forEach { review ->
                                        Column {
                                            Text("⭐".repeat(review.restaurantRating), fontSize = 12.sp)
                                            val comment = review.restaurantComment
                                            if (!comment.isNullOrBlank()) {
                                                Text(comment, fontSize = 13.sp, color = Ids.colors.textPrimary, modifier = Modifier.padding(top = 2.dp))
                                            }
                                            if (review.photoUrl != null) {
                                                AsyncImage(
                                                    model = review.photoUrl,
                                                    contentDescription = null,
                                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                                    modifier = Modifier.padding(top = 4.dp).size(width = 120.dp, height = 80.dp).clip(RoundedCornerShape(8.dp)),
                                                )
                                            }
                                            val ownerReply = review.ownerReply
                                            if (!ownerReply.isNullOrBlank()) {
                                                Column(
                                                    modifier = Modifier
                                                        .padding(top = 6.dp)
                                                        .background(Ids.colors.surfaceSoft, RoundedCornerShape(8.dp))
                                                        .padding(8.dp),
                                                ) {
                                                    Text("Owner's reply", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Ids.colors.textSecondary)
                                                    Text(ownerReply, fontSize = 12.sp, color = Ids.colors.textPrimary, modifier = Modifier.padding(top = 2.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            if (matchedMerchant != null && placeTab == PlaceTab.HOME) {
                                // Real simplicity fix (2026-08-09), found live after direct user
                                // feedback ("not simplicity at all"): this used to be up to 7
                                // separate stacked Text rows, one fact per line -- rating,
                                // category, cashback, min-order, distance, hours, phone, each
                                // its own row. Real Naver/Kakao Maps group related "at a glance"
                                // facts onto one line with middle-dot separators instead, and
                                // only give a genuine action (call) its own row. Grouped into 3
                                // lines: (category · rating · distance), (cashback · min order),
                                // (hours), plus phone as the one real tappable action.
                                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    if (matchedMerchant.photoUrl != null) {
                                        AsyncImage(
                                            model = matchedMerchant.photoUrl,
                                            contentDescription = matchedMerchant.businessName,
                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)),
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        val glanceLine = listOfNotNull(
                                            matchedMerchant.category,
                                            matchedMerchant.rating?.let { r ->
                                                "⭐ ${"%.1f".format(r)}" + if (matchedMerchant.reviewCount > 0) " (${matchedMerchant.reviewCount})" else ""
                                            },
                                            matchedMerchant.distanceKm?.let { d ->
                                                val eta = matchedMerchant.deliveryTimeMinutes?.let { " · ~$it min" } ?: ""
                                                "${"%.1f".format(d)} km$eta"
                                            },
                                        ).joinToString(" · ")
                                        if (glanceLine.isNotEmpty()) {
                                            Text(glanceLine, fontSize = 12.sp, color = Ids.colors.textSecondary)
                                        }
                                        val valueLine = listOfNotNull(
                                            "${matchedMerchant.cashbackRate} cashback",
                                            matchedMerchant.minOrderAmount?.let { "Min. RWF ${it.toInt()}" },
                                        ).joinToString(" · ")
                                        Text(valueLine, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ids.colors.brand)
                                        val openingHours = matchedMerchant.openingHours
                                        if (openingHours != null) {
                                            Text("🕒 $openingHours", fontSize = 11.sp, color = Ids.colors.textSecondary)
                                        }
                                        val phoneNumber = matchedMerchant.phoneNumber
                                        if (phoneNumber != null) {
                                            Text(
                                                "📞 $phoneNumber",
                                                fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ids.colors.brand,
                                                modifier = Modifier.padding(top = 2.dp).clickable {
                                                    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber")))
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                            // Real folder/color picker (2026-07-22) -- only expanded for
                            // the place actually being saved right now, ported from
                            // bank-mfe's own real save-time picker.
                            if (savingToFolder != null && savingToFolder!!.latitude == place.latitude && savingToFolder!!.longitude == place.longitude) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Ids.colors.surfaceSoft, RoundedCornerShape(8.dp))
                                        .padding(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    IdsTextField(
                                        value = folderNameInput,
                                        onValueChange = { folderNameInput = it },
                                        label = "Folder name (e.g. Favorites)",
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        listOf("Home", "Work").forEach { preset ->
                                            val active = folderNameInput.equals(preset, ignoreCase = true)
                                            Text(preset, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (active) androidx.compose.ui.graphics.Color.White else Ids.colors.textPrimary, modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(if (active) Ids.colors.brand else Ids.colors.surfaceSoft).clickable { folderNameInput = preset }.padding(horizontal = 10.dp, vertical = 6.dp))
                                        }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        BOOKMARK_COLOR_PALETTE.forEach { c ->
                                            val color = try { androidx.compose.ui.graphics.Color(AndroidColor.parseColor(c)) } catch (_: Exception) { androidx.compose.ui.graphics.Color(0xFFF5A623) }
                                            Box(
                                                modifier = Modifier
                                                    .size(22.dp)
                                                    .background(color, CircleShape)
                                                    .then(
                                                        if (folderColorInput == c) Modifier.border(2.dp, Ids.colors.textPrimary, CircleShape) else Modifier,
                                                    )
                                                    .clickable { folderColorInput = c },
                                            )
                                        }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .background(Ids.colors.brand, RoundedCornerShape(8.dp))
                                                .clickable(enabled = !bookmarking) { confirmSaveToFolder() }
                                                .padding(vertical = 8.dp),
                                            contentAlignment = androidx.compose.ui.Alignment.Center,
                                        ) { Text(if (bookmarking) "Saving…" else "Save", color = androidx.compose.ui.graphics.Color.White, fontSize = 13.sp) }
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable(enabled = !bookmarking) { savingToFolder = null }
                                                .padding(vertical = 8.dp),
                                            contentAlignment = androidx.compose.ui.Alignment.Center,
                                        ) { Text("Cancel", color = Ids.colors.textSecondary, fontSize = 13.sp) }
                                    }
                                }
                            }
                            if (isAgentCashDiscovery && activeCategory == "ITUNDA_AGENT") {
                                Text(
                                    "This is an Itunda agent location. Confirm the cash is ready before showing your withdrawal code.",
                                    fontSize = 12.sp,
                                    color = Ids.colors.textSecondary,
                                )
                            }
                            // Real driving/walking mode toggle (2026-07-22) -- same real
                            // Naver/Kakao Maps convention of picking a travel mode
                            // before/after a route is drawn. Switching mode while a route
                            // is already shown re-fetches against itunda's own
                            // separately-deployed foot-profile OSRM instance.
                            fun fetchDirections(mode: String) {
                                coroutineScope.launch {
                                    routing = true
                                    error = null
                                    try {
                                        val origin = myLocation ?: (RWANDA_CENTER_LAT to RWANDA_CENTER_LNG)
                                        val response = NetworkClient.apiService.getDirectionsAlternatives(
                                            origin.first, origin.second, place.latitude, place.longitude, mode,
                                        )
                                        travelMode = mode
                                        routeAlternatives = response.routes
                                        selectedRouteIndex = 0
                                        route = MapsDirectionsResponse(success = true, route = response.routes[0])
                                        showSteps = false
                                    } catch (e: HttpException) {
                                        error = superAppErrorMessage(e)
                                    } catch (e: Exception) {
                                        error = "Couldn't reach itunda. Check your connection and try again."
                                    } finally {
                                        routing = false
                                    }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                listOf("DRIVING" to "🚗 Driving", "WALKING" to "🚶 Walking").forEach { (mode, label) ->
                                    val active = travelMode == mode
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .background(if (active) Ids.colors.brand else Ids.colors.surfaceSoft, RoundedCornerShape(8.dp))
                                            .clickable(enabled = !routing) {
                                                if (mode != travelMode) {
                                                    if (route != null) fetchDirections(mode) else travelMode = mode
                                                }
                                            }
                                            .padding(vertical = 6.dp),
                                        contentAlignment = androidx.compose.ui.Alignment.Center,
                                    ) {
                                        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (active) androidx.compose.ui.graphics.Color.White else Ids.colors.textSecondary)
                                    }
                                }
                            }
                            val currentRoute = route
                            if (currentRoute != null) {
                                Column {
                                    Text(
                                        "${if (travelMode == "DRIVING") "🚗" else "🚶"} ${"%.1f".format(currentRoute.route.distanceKm)} km · ${currentRoute.route.durationMinutes.toInt()} min by real road, via itunda's own self-hosted OSRM",
                                        fontSize = 13.sp, color = Ids.colors.textSecondary,
                                    )
                                    // Real alternative-route picker (2026-07-22) -- only
                                    // rendered when OSRM genuinely offered more than one
                                    // real route for this trip. See
                                    // MapsDirectionsAlternativesResponse's own doc comment.
                                    val alternatives = routeAlternatives
                                    if (alternatives != null && alternatives.size > 1) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp)) {
                                            alternatives.forEachIndexed { i, alt ->
                                                val active = selectedRouteIndex == i
                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .background(if (active) Ids.colors.brand else Ids.colors.surfaceSoft, RoundedCornerShape(8.dp))
                                                        .clickable {
                                                            selectedRouteIndex = i
                                                            route = MapsDirectionsResponse(success = true, route = alt)
                                                        }
                                                        .padding(vertical = 5.dp),
                                                    contentAlignment = androidx.compose.ui.Alignment.Center,
                                                ) {
                                                    Text(
                                                        "Route ${i + 1} · ${"%.1f".format(alt.distanceKm)}km · ${alt.durationMinutes.toInt()}min",
                                                        fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                                        color = if (active) androidx.compose.ui.graphics.Color.White else Ids.colors.textSecondary,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    // Real "Start Navigation" mode (2026-08-09) -- see
                                    // currentStepIndexFor's own doc comment above for why. While
                                    // active, this replaces the flat steps list with a single,
                                    // prominent current-step card (the same "just the next turn,
                                    // nothing else" framing Naver/Kakao/Google's own turn-by-turn
                                    // view uses) instead of a scrollable wall of every step at
                                    // once.
                                    if (navigating) {
                                        val steps = currentRoute.route.steps
                                        val stepIdx = currentStepIndex.coerceIn(0, (steps.size - 1).coerceAtLeast(0))
                                        val activeStep = steps.getOrNull(stepIdx)
                                        val remainingKm = steps.drop(stepIdx + 1).sumOf { it.distanceMeters } / 1000.0 +
                                            (activeStep?.distanceMeters ?: 0.0) / 1000.0
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 8.dp)
                                                .background(Ids.colors.brand, RoundedCornerShape(14.dp))
                                                .padding(16.dp),
                                        ) {
                                            Column {
                                                Text(
                                                    activeStep?.instruction ?: "Arriving at your destination",
                                                    fontSize = 17.sp, fontWeight = FontWeight.Bold,
                                                    color = androidx.compose.ui.graphics.Color.White,
                                                )
                                                Text(
                                                    "Step ${stepIdx + 1} of ${steps.size} · ${"%.1f".format(remainingKm)} km remaining",
                                                    fontSize = 12.sp,
                                                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f),
                                                    modifier = Modifier.padding(top = 4.dp),
                                                )
                                                Row(modifier = Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Text(
                                                        "End navigation",
                                                        fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                                        color = androidx.compose.ui.graphics.Color.White,
                                                        modifier = Modifier
                                                            .background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                                            .clickable { navigating = false }
                                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                                    )
                                                    Text(
                                                        if (voiceEnabled) "🔊 Voice on" else "🔇 Voice off",
                                                        fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                                        color = androidx.compose.ui.graphics.Color.White,
                                                        modifier = Modifier
                                                            .background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                                            .clickable {
                                                                voiceEnabled = !voiceEnabled
                                                                if (!voiceEnabled) tts.stop()
                                                            }
                                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                                    )
                                                }
                                            }
                                        }
                                    } else {
                                        // Real full-width prominent CTA (2026-08-09), matching
                                        // real Naver Maps' own "안내시작" (Start guide) bottom bar
                                        // -- this used to be a small pill squeezed into the same
                                        // row as the steps-toggle text, easy to miss as the
                                        // screen's actual primary action. The steps toggle is now
                                        // its own row above; Start Navigation gets real visual
                                        // weight matching what it actually does.
                                        if (currentRoute.route.steps.isNotEmpty()) {
                                            Text(
                                                if (showSteps) "Hide turn-by-turn directions" else "Show turn-by-turn directions (${currentRoute.route.steps.size} steps)",
                                                fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ids.colors.brand,
                                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp).clickable { showSteps = !showSteps },
                                            )
                                        }
                                        Row(
                                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 8.dp)
                                                .background(Ids.colors.brand, RoundedCornerShape(12.dp))
                                                .clickable {
                                                    currentStepIndex = 0
                                                    // Itinerary routes have no single `selectedPlace` (the destination is the
                                                    // last stop in itineraryStops instead) -- covers both real Start
                                                    // Navigation entry points with the one real destination each carries.
                                                    navigationDestination = selectedPlace?.let { it.latitude to it.longitude }
                                                        ?: itineraryStops.lastOrNull()?.let { it.latitude to it.longitude }
                                                    navigating = true
                                                    requestMyLocation()
                                                }
                                                .padding(vertical = 13.dp),
                                        ) {
                                            Text(
                                                "▶  Start navigation",
                                                fontSize = 14.sp, fontWeight = FontWeight.Bold,
                                                color = androidx.compose.ui.graphics.Color.White,
                                            )
                                        }
                                        if (showSteps) {
                                            Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                currentRoute.route.steps.forEachIndexed { i, step ->
                                                    Text(
                                                        "${i + 1}. ${step.instruction}" + if (step.distanceMeters >= 10) " (${step.distanceMeters.toInt()} m)" else "",
                                                        fontSize = 12.sp, color = Ids.colors.textSecondary,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .background(Ids.colors.brand, RoundedCornerShape(12.dp))
                                        .clickable(enabled = !routing) { fetchDirections(travelMode) }
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                ) { Text(if (routing) "Finding real route…" else "Directions", color = androidx.compose.ui.graphics.Color.White, fontSize = 13.sp) }
                            }
                            if (isAgentCashDiscovery && activeCategory == "ITUNDA_AGENT") {
                                Text(
                                    "Back to cash-out codes",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Ids.colors.brand,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(onClick = onBack)
                                        .padding(vertical = 8.dp),
                                )
                            }
                            // A little breathing room below so the drag-to-Full state
                            // doesn't cut the last line off against the screen edge.
                            Box(modifier = Modifier.height(24.dp))
                        } else {
                            if (itineraryBuilding) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Ids.colors.successTint, RoundedCornerShape(12.dp))
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text("Multi-stop trip", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ids.colors.textPrimary)
                                    Text(
                                        "Start: ${if (myLocation != null) "your current location" else "Kigali map center"}. Search and tap places in the order you want to visit them.",
                                        fontSize = 12.sp,
                                        color = Ids.colors.textSecondary,
                                    )
                                    if (itineraryStops.isEmpty()) {
                                        Text("Add 1–6 destinations to make a real road itinerary.", fontSize = 12.sp, color = Ids.colors.textSecondary)
                                    } else {
                                        itineraryStops.forEachIndexed { index, stop ->
                                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                                Text("${index + 2}. ${stop.displayName}", fontSize = 13.sp, color = Ids.colors.textPrimary, modifier = Modifier.weight(1f))
                                                Text("Remove", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ids.colors.brand, modifier = Modifier.clickable {
                                                    itineraryStops = itineraryStops.filterIndexed { itemIndex, _ -> itemIndex != index }
                                                    route = null
                                                    showingItineraryRoute = false
                                                })
                                            }
                                        }
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Ids.colors.brand, RoundedCornerShape(9.dp))
                                            .clickable(enabled = itineraryStops.isNotEmpty() && !routing) { fetchItinerary() }
                                            .padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            if (routing) "Finding real itinerary…" else "Route ${itineraryStops.size + 1} stops",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = androidx.compose.ui.graphics.Color.White,
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                        listOf("DRIVING" to "🚗 Driving", "WALKING" to "🚶 Walking").forEach { (mode, label) ->
                                            val active = travelMode == mode
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .background(if (active) Ids.colors.brand else Ids.colors.surfaceSoft, RoundedCornerShape(8.dp))
                                                    .clickable(enabled = !routing) {
                                                        if (mode != travelMode) {
                                                            if (showingItineraryRoute) fetchItinerary(mode) else travelMode = mode
                                                        }
                                                    }
                                                    .padding(vertical = 6.dp),
                                                contentAlignment = Alignment.Center,
                                            ) { Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (active) androidx.compose.ui.graphics.Color.White else Ids.colors.textSecondary) }
                                        }
                                    }
                                    val itineraryRoute = route.takeIf { showingItineraryRoute }
                                    if (itineraryRoute != null) {
                                        Text(
                                            "${if (travelMode == "DRIVING") "🚗" else "🚶"} ${"%.1f".format(itineraryRoute.route.distanceKm)} km · ${itineraryRoute.route.durationMinutes.toInt()} min by real road",
                                            fontSize = 13.sp,
                                            color = Ids.colors.textSecondary,
                                        )
                                        Text("Legs", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ids.colors.textPrimary)
                                        val legLabels = listOf(if (myLocation != null) "Your location" else "Kigali map center") + itineraryStops.map { it.displayName }
                                        legLabels.zipWithNext().forEachIndexed { index, (from, to) ->
                                            Text("${index + 1}. $from → $to", fontSize = 12.sp, color = Ids.colors.textSecondary)
                                        }
                                        if (itineraryRoute.route.steps.isNotEmpty()) {
                                            Text(
                                                if (showSteps) "Hide turn-by-turn directions" else "Show turn-by-turn directions (${itineraryRoute.route.steps.size} steps)",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Ids.colors.brand,
                                                modifier = Modifier.clickable { showSteps = !showSteps },
                                            )
                                            if (showSteps) itineraryRoute.route.steps.forEachIndexed { index, step ->
                                                Text("${index + 1}. ${step.instruction}", fontSize = 12.sp, color = Ids.colors.textSecondary)
                                            }
                                        }
                                    }
                                }
                            }
                            // Real default "around me" state (2026-07-21) -- Naver Map's
                            // own Smart Around sheet keeps a non-modal panel permanently
                            // docked with real curated content even before any search,
                            // rather than only ever appearing once a place is selected.
                            // itunda has no editorial "today's pick"/"worth visiting"
                            // content to curate, so this surfaces real data it already
                            // has instead: the active category's real results (if any),
                            // a real merchant count, and real saved places -- honest
                            // functional content, not a fabricated curated feed.
                            Text("Around you", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Ids.colors.textPrimary)
                            val home = bookmarks.firstOrNull { it.folderName.equals("Home", ignoreCase = true) }
                            val work = bookmarks.firstOrNull { it.folderName.equals("Work", ignoreCase = true) }
                            if (home != null || work != null) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (home != null) Text("⌂ Home", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ids.colors.textPrimary, modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(Ids.colors.surfaceSoft).clickable { selectAndRoute(PlaceSearchResultDto(home.displayName, home.latitude, home.longitude)) }.padding(horizontal = 12.dp, vertical = 8.dp))
                                    if (work != null) Text("▣ Work", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ids.colors.textPrimary, modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(Ids.colors.surfaceSoft).clickable { selectAndRoute(PlaceSearchResultDto(work.displayName, work.latitude, work.longitude)) }.padding(horizontal = 12.dp, vertical = 8.dp))
                                }
                            }
                            if (activeCategory != null && categoryResults != null) {
                                val label = MAP_NEARBY_CATEGORIES.firstOrNull { it.id == activeCategory }?.label?.lowercase() ?: "places"
                                if (categoryResults!!.isEmpty()) {
                                    Text("No real matches found nearby for $label.", color = Ids.colors.textSecondary, fontSize = 13.sp)
                                } else {
                                    categoryResults!!.forEach { nearby ->
                                        val (nearbyName, nearbyAddress) = splitPlaceName(nearby.displayName)
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    selectedPlace = PlaceSearchResultDto(nearby.displayName, nearby.latitude, nearby.longitude)
                                                    route = null
                                                    routeAlternatives = null
                                                    selectedRouteIndex = 0
                                                    savingToFolder = null
                                                }
                                                .padding(vertical = 6.dp),
                                        ) {
                                            Text(
                                                "${if (isAgentCashDiscovery && activeCategory == "ITUNDA_AGENT") "Itunda agent · " else ""}$nearbyName",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Ids.colors.textPrimary,
                                            )
                                            Text(
                                                listOfNotNull(nearbyAddress, "${"%.1f".format(nearby.distanceKm)} km").joinToString(" · "),
                                                fontSize = 11.sp,
                                                color = Ids.colors.textSecondary,
                                                maxLines = 1,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                                modifier = Modifier.padding(top = 1.dp),
                                            )
                                        }
                                    }
                                }
                            } else {
                                Text(
                                    if (merchants.isEmpty()) "Search a real place or pick a category above to explore Rwanda."
                                    else "${merchants.size} real merchant${if (merchants.size == 1) "" else "s"} on the map. Search a place or pick a category above to explore.",
                                    color = Ids.colors.textSecondary,
                                    fontSize = 13.sp,
                                )
                            }

                            Text(
                                "★ Your saved places",
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Ids.colors.textSecondary,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                            shareConfirmation?.let {
                                Text(it, fontSize = 11.sp, color = Ids.colors.textSecondary, modifier = Modifier.padding(top = 4.dp))
                            }
                            if (bookmarks.isEmpty()) {
                                EmptyState("No saved places yet — tap ☆ on a place to save it here.", icon = Icons.Outlined.BookmarkBorder)
                            } else {
                                // Real "My Places" folder grouping (2026-07-22) --
                                // ported from bank-mfe's own real grouping. groupBy
                                // preserves encounter order, so a folder's position here
                                // is simply wherever its most-recently-saved place falls
                                // (bookmarks is already createdAt-desc), not a separate
                                // alphabetic re-sort.
                                val bookmarksByFolder = bookmarks.groupBy { it.folderName }
                                bookmarksByFolder.forEach { (folderName, folderBookmarks) ->
                                    // Real Naver Map-style public/private folder + share
                                    // (2026-08-04) -- see toggleFolderShare's own doc
                                    // comment. Always shown (not gated on >1 folder like
                                    // the name label below) since even the single default
                                    // folder is real and shareable.
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                    ) {
                                        if (bookmarksByFolder.size > 1) {
                                            Text(folderName, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Ids.colors.textSecondary)
                                        } else {
                                            Box(modifier = Modifier)
                                        }
                                        val isPublic = folderBookmarks.any { it.isPublic }
                                        Text(
                                            if (sharingFolder == folderName) "…" else if (isPublic) "🌐 Public · Share" else "🔒 Private · Share",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isPublic) Ids.colors.brand else Ids.colors.textSecondary,
                                            modifier = Modifier.clickable(enabled = sharingFolder == null) { toggleFolderShare(folderName, !isPublic) },
                                        )
                                    }
                                    folderBookmarks.forEach { bookmark ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    selectedPlace = PlaceSearchResultDto(bookmark.displayName, bookmark.latitude, bookmark.longitude)
                                                    route = null
                                                    routeAlternatives = null
                                                    selectedRouteIndex = 0
                                                    savingToFolder = null
                                                }
                                                .padding(vertical = 6.dp),
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .background(
                                                        try { androidx.compose.ui.graphics.Color(AndroidColor.parseColor(bookmark.color)) } catch (_: Exception) { androidx.compose.ui.graphics.Color(0xFFF5A623) },
                                                        CircleShape,
                                                    ),
                                            )
                                            Text(bookmark.displayName, fontSize = 13.sp, color = Ids.colors.textPrimary, modifier = Modifier.weight(1f))
                                            Text(
                                                "Move", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Ids.colors.textSecondary,
                                                modifier = Modifier.clickable {
                                                    if (movingBookmark?.let { it.latitude == bookmark.latitude && it.longitude == bookmark.longitude } == true) {
                                                        movingBookmark = null
                                                    } else {
                                                        movingBookmark = bookmark
                                                        moveFolderNameInput = bookmark.folderName
                                                        moveFolderColorInput = bookmark.color
                                                    }
                                                },
                                            )
                                        }
                                        if (movingBookmark?.let { it.latitude == bookmark.latitude && it.longitude == bookmark.longitude } == true) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(Ids.colors.surfaceSoft, RoundedCornerShape(8.dp))
                                                    .padding(8.dp),
                                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                            ) {
                                                IdsTextField(
                                                    value = moveFolderNameInput,
                                                    onValueChange = { moveFolderNameInput = it },
                                                    label = "Folder name",
                                                    modifier = Modifier.fillMaxWidth(),
                                                )
                                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    BOOKMARK_COLOR_PALETTE.forEach { c ->
                                                        val color = try { androidx.compose.ui.graphics.Color(AndroidColor.parseColor(c)) } catch (_: Exception) { androidx.compose.ui.graphics.Color(0xFFF5A623) }
                                                        Box(
                                                            modifier = Modifier
                                                                .size(22.dp)
                                                                .background(color, CircleShape)
                                                                .then(if (moveFolderColorInput == c) Modifier.border(2.dp, Ids.colors.textPrimary, CircleShape) else Modifier)
                                                                .clickable { moveFolderColorInput = c },
                                                        )
                                                    }
                                                }
                                                Text(
                                                    "Save", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Ids.colors.brand,
                                                    modifier = Modifier.clickable(enabled = moveFolderNameInput.isNotBlank()) {
                                                        val target = movingBookmark ?: return@clickable
                                                        coroutineScope.launch {
                                                            try {
                                                                NetworkClient.apiService.moveMapBookmark(
                                                                    target.latitude, target.longitude,
                                                                    MoveMapBookmarkRequest(moveFolderNameInput, moveFolderColorInput),
                                                                )
                                                                bookmarks = NetworkClient.apiService.getMyMapBookmarks().bookmarks
                                                                movingBookmark = null
                                                            } catch (_: Exception) {
                                                                // Best-effort -- leaves the picker open so the user can retry.
                                                            }
                                                        }
                                                    },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            Box(modifier = Modifier.height(24.dp))
                        }
                    }
                }
            }
        } // end full-bleed map BoxWithConstraints
    }
}
