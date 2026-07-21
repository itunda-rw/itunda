package rw.itunda.app.ui

import android.Manifest
import android.content.pm.PackageManager
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
import rw.itunda.app.network.AddMapBookmarkRequest
import rw.itunda.app.network.MAP_NEARBY_CATEGORIES
import rw.itunda.app.network.MapBookmarkDto
import rw.itunda.app.network.MapsDirectionsResponse
import rw.itunda.app.network.NearbyPlaceDto
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.app.network.NetworkClient
import rw.itunda.app.network.PlaceSearchResultDto
import rw.itunda.app.network.ShoppingMerchantDto

// Real itunda-hosted Rwanda coordinates -- Kigali, same default center every other real
// coordinate fixture in this codebase (backend tests, bank-mfe's MapView.tsx) uses.
private const val RWANDA_CENTER_LAT = -1.9441
private const val RWANDA_CENTER_LNG = 30.0619
// Both driven by BuildConfig now (2026-07-21), not hardcoded to the private cloud's
// internal-only 192.168.252.3 address -- see app/build.gradle.kts' TILES_BASE_URL/
// GLYPHS_BASE_URL doc comment for why a physical device on the public HTTPS endpoint
// got a permanently blank map otherwise.
private val TILES_URL = "${rw.itunda.app.BuildConfig.TILES_BASE_URL}/rwanda/{z}/{x}/{y}.mvt"
// Real self-hosted glyphs (font PBF) server (2026-07-19) -- closes item 5, the last item
// on the Maps "100%" roadmap. See bank-mfe's lib/maps.ts GLYPHS_URL doc comment for the
// full account (real pre-generated Noto Sans Regular/Bold glyph PBFs, served statically
// by nginx on itunda-dc-b, ~14MB RSS -- an order of magnitude lighter than OSRM/
// Nominatim despite being this host's fourth persistent private-cloud service).
private val GLYPHS_URL = "${rw.itunda.app.BuildConfig.GLYPHS_BASE_URL}/{fontstack}/{range}.pbf"
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

// Real per-category glyphs for the chip row (2026-07-21) -- plain emoji, matching this
// screen's own existing convention of emoji over icon-font glyphs for real content (the
// 🚗/★/☆ already used below), not a new pattern. No icon field exists on the backend's
// `MapPlaceCategory` DTO -- this is a client-side-only lookup by id, honestly scoped to
// display, never sent back to the server.
private val MAP_CATEGORY_ICONS = mapOf(
    "RESTAURANT" to "🍽️", "CAFE" to "☕", "HOSPITAL" to "🏥", "PHARMACY" to "💊",
    "BANK" to "🏦", "ATM" to "🏧", "HOTEL" to "🏨", "SUPERMARKET" to "🛒",
    "GAS_STATION" to "⛽", "SCHOOL" to "🏫", "ITUNDA_AGENT" to "💜",
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

// A real, minimal MapLibre style over itunda's own self-hosted vector tiles -- mirrors
// bank-mfe's MapView.tsx MAP_STYLE constant exactly (same source, same layer set, no
// text labels yet since that needs a separate self-hosted glyphs server). Kept as a
// single JSON string here since MapLibre Android's style DSL doesn't offer a typed
// builder as concise as the web SDK's; this is the same style spec format either way.
// Declares the real, empty-until-populated `route` source the directions feature below
// writes into (the merchants/my-location/destination sources are added at runtime once
// the style loads, same as before).
private val MAP_STYLE_JSON = """
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
fun MapScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
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
    var selectedPlace by remember { mutableStateOf<PlaceSearchResultDto?>(null) }
    var route by remember { mutableStateOf<MapsDirectionsResponse?>(null) }
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

    // Shared by both the search field's own leading icon and its keyboard "search"
    // IME action -- previously only reachable via a separate colored "Search" button.
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

    fun isBookmarked(place: PlaceSearchResultDto): Boolean =
        bookmarks.any { it.latitude == place.latitude && it.longitude == place.longitude }

    fun toggleBookmark(place: PlaceSearchResultDto) {
        coroutineScope.launch {
            bookmarking = true
            error = null
            try {
                if (isBookmarked(place)) {
                    NetworkClient.apiService.removeMapBookmark(place.latitude, place.longitude)
                    bookmarks = bookmarks.filterNot { it.latitude == place.latitude && it.longitude == place.longitude }
                } else {
                    val saved = NetworkClient.apiService.addMapBookmark(
                        AddMapBookmarkRequest(place.displayName, place.latitude, place.longitude),
                    ).bookmark
                    bookmarks = listOf(saved) + bookmarks
                }
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: Exception) {
                error = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                bookmarking = false
            }
        }
    }

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
                    style.addImage(NEARBY_ICON_ID, createPinBitmap(pinDensity, "#8B5CF6"))

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
                }
            }
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                mapView.onPause()
                mapView.onStop()
                mapView.onDestroy()
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
                map.easeCamera(org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(LatLng(location.first, location.second), 14.0))
            }
        }
        LaunchedEffect(selectedPlace) {
            val place = selectedPlace
            mapView.getMapAsync { map ->
                val style = map.style ?: return@getMapAsync
                val source = style.getSourceAs<GeoJsonSource>(DESTINATION_SOURCE_ID) ?: return@getMapAsync
                if (place == null) {
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
                        .background(TossCard, CircleShape)
                        .clip(CircleShape)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.ArrowBackIosNew, contentDescription = "Back", modifier = Modifier.size(16.dp), tint = TossText)
                }

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .shadow(3.dp, RoundedCornerShape(999.dp))
                        .background(TossCard, RoundedCornerShape(999.dp))
                        .padding(start = 14.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.Search,
                        contentDescription = "Search",
                        tint = if (searching) TossSecondary else TossBlue,
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
                        modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
                    )
                    if (query.isNotBlank()) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = "Clear search",
                            tint = TossSecondary,
                            modifier = Modifier.size(16.dp).clickable { query = ""; searchResults = null },
                        )
                        Box(modifier = Modifier.width(6.dp))
                    }
                }
            }

            // Real category-chip "nearby places" search (Naver/Kakao's own convention) --
            // mirrors bank-mfe's MapView.tsx chip row, now with a per-category emoji glyph
            // (MAP_CATEGORY_ICONS) so chips read at a glance instead of as text-only pills.
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MAP_NEARBY_CATEGORIES.forEach { category ->
                    val active = activeCategory == category.id
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .shadow(if (active) 3.dp else 1.dp, RoundedCornerShape(999.dp))
                            .background(if (active) androidx.compose.ui.graphics.Color(0xFF8B5CF6) else TossCard, RoundedCornerShape(999.dp))
                            .clickable(enabled = !categoryLoading || active) { searchNearbyCategory(category.id) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text(MAP_CATEGORY_ICONS[category.id] ?: "📍", fontSize = 13.sp)
                        Text(
                            if (active && categoryLoading) "…" else category.label,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (active) androidx.compose.ui.graphics.Color.White else TossText,
                        )
                    }
                }
            }

            if ((activeCategory != null && categoryResults != null) || searchResults != null || error != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(3.dp, RoundedCornerShape(Ids.layout.sectionCornerRadius))
                        .background(TossCard, RoundedCornerShape(Ids.layout.sectionCornerRadius))
                        .padding(vertical = 4.dp),
                ) {
                    if (activeCategory != null && categoryResults != null) {
                        val label = MAP_NEARBY_CATEGORIES.firstOrNull { it.id == activeCategory }?.label?.lowercase()
                        Text(
                            if (categoryResults!!.isEmpty()) "No real matches found nearby for that category."
                            else "${categoryResults!!.size} real $label found nearby, closest first.",
                            color = TossSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        )
                    }

                    searchResults?.let { results ->
                        if (results.isEmpty()) {
                            Text("No real places found for that search.", color = TossSecondary, fontSize = 13.sp, modifier = Modifier.padding(14.dp))
                        } else {
                            results.forEach { place ->
                                Text(
                                    place.displayName,
                                    fontSize = 13.sp,
                                    color = TossText,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedPlace = place
                                            searchResults = null
                                            route = null
                                            showSteps = false
                                        }
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                )
                            }
                        }
                    }

                    error?.let { Text(it, color = Ids.colors.danger, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) }
                }
            }
            } // end floating top panel

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
                        .background(TossCard, RoundedCornerShape(14.dp)),
                ) {
                    Box(
                        modifier = Modifier.size(44.dp).clickable {
                            mapView.getMapAsync { map -> map.easeCamera(CameraUpdateFactory.zoomIn()) }
                        },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Outlined.Add, contentDescription = "Zoom in", tint = TossText, modifier = Modifier.size(18.dp)) }
                    Box(modifier = Modifier.width(44.dp).height(1.dp).background(TossLine))
                    Box(
                        modifier = Modifier.size(44.dp).clickable {
                            mapView.getMapAsync { map -> map.easeCamera(CameraUpdateFactory.zoomOut()) }
                        },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Outlined.Remove, contentDescription = "Zoom out", tint = TossText, modifier = Modifier.size(18.dp)) }
                }
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .shadow(3.dp, CircleShape)
                        .background(TossCard, CircleShape)
                        .clip(CircleShape)
                        .clickable(enabled = !locating) { requestMyLocation() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.MyLocation,
                        contentDescription = "Find my real location",
                        tint = if (locating) TossSecondary else TossBlue,
                        modifier = Modifier.size(20.dp),
                    )
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
                        .background(TossCard, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)),
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
                            .background(TossSecondary.copy(alpha = 0.4f), RoundedCornerShape(2.dp)),
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
                            Row(verticalAlignment = Alignment.Top) {
                                Text(
                                    place.displayName,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = TossText,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    if (isBookmarked(place)) "★" else "☆",
                                    fontSize = 20.sp,
                                    color = if (isBookmarked(place)) androidx.compose.ui.graphics.Color(0xFFF5A623) else TossSecondary,
                                    modifier = Modifier.clickable(enabled = !bookmarking) { toggleBookmark(place) },
                                )
                            }
                            val currentRoute = route
                            if (currentRoute != null) {
                                Column {
                                    Text(
                                        "🚗 ${"%.1f".format(currentRoute.route.distanceKm)} km · ${currentRoute.route.durationMinutes.toInt()} min by real road, via itunda's own self-hosted OSRM",
                                        fontSize = 13.sp, color = TossSecondary,
                                    )
                                    if (currentRoute.route.steps.isNotEmpty()) {
                                        Text(
                                            if (showSteps) "Hide turn-by-turn directions" else "Show turn-by-turn directions (${currentRoute.route.steps.size} steps)",
                                            fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TossBlue,
                                            modifier = Modifier.padding(top = 4.dp).clickable { showSteps = !showSteps },
                                        )
                                    }
                                    if (showSteps) {
                                        Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            currentRoute.route.steps.forEachIndexed { i, step ->
                                                Text(
                                                    "${i + 1}. ${step.instruction}" + if (step.distanceMeters >= 10) " (${step.distanceMeters.toInt()} m)" else "",
                                                    fontSize = 12.sp, color = TossSecondary,
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .background(TossBlue, RoundedCornerShape(12.dp))
                                        .clickable(enabled = !routing) {
                                            coroutineScope.launch {
                                                routing = true
                                                error = null
                                                try {
                                                    val origin = myLocation ?: (RWANDA_CENTER_LAT to RWANDA_CENTER_LNG)
                                                    route = NetworkClient.apiService.getDirections(origin.first, origin.second, place.latitude, place.longitude)
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
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                ) { Text(if (routing) "Finding real route…" else "Directions", color = androidx.compose.ui.graphics.Color.White, fontSize = 13.sp) }
                            }
                            // A little breathing room below so the drag-to-Full state
                            // doesn't cut the last line off against the screen edge.
                            Box(modifier = Modifier.height(24.dp))
                        } else {
                            // Real default "around me" state (2026-07-21) -- Naver Map's
                            // own Smart Around sheet keeps a non-modal panel permanently
                            // docked with real curated content even before any search,
                            // rather than only ever appearing once a place is selected.
                            // itunda has no editorial "today's pick"/"worth visiting"
                            // content to curate, so this surfaces real data it already
                            // has instead: the active category's real results (if any),
                            // a real merchant count, and real saved places -- honest
                            // functional content, not a fabricated curated feed.
                            Text("Around you", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TossText)
                            if (activeCategory != null && categoryResults != null) {
                                val label = MAP_NEARBY_CATEGORIES.firstOrNull { it.id == activeCategory }?.label?.lowercase() ?: "places"
                                if (categoryResults!!.isEmpty()) {
                                    Text("No real matches found nearby for $label.", color = TossSecondary, fontSize = 13.sp)
                                } else {
                                    categoryResults!!.forEach { nearby ->
                                        Text(
                                            "${nearby.displayName} · ${"%.1f".format(nearby.distanceKm)} km",
                                            fontSize = 13.sp,
                                            color = TossText,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    selectedPlace = PlaceSearchResultDto(nearby.displayName, nearby.latitude, nearby.longitude)
                                                    route = null
                                                }
                                                .padding(vertical = 6.dp),
                                        )
                                    }
                                }
                            } else {
                                Text(
                                    if (merchants.isEmpty()) "Search a real place or pick a category above to explore Rwanda."
                                    else "${merchants.size} real merchant${if (merchants.size == 1) "" else "s"} on the map. Search a place or pick a category above to explore.",
                                    color = TossSecondary,
                                    fontSize = 13.sp,
                                )
                            }

                            Text(
                                "★ Your saved places",
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                fontSize = 12.sp,
                                color = TossSecondary,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                            if (bookmarks.isEmpty()) {
                                Text("No saved places yet -- tap ☆ on a place to save it.", fontSize = 12.sp, color = TossSecondary)
                            } else {
                                bookmarks.forEach { bookmark ->
                                    Text(
                                        bookmark.displayName,
                                        fontSize = 13.sp,
                                        color = TossText,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                selectedPlace = PlaceSearchResultDto(bookmark.displayName, bookmark.latitude, bookmark.longitude)
                                                route = null
                                            }
                                            .padding(vertical = 6.dp),
                                    )
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
