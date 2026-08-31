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
import rw.itunda.core.designsystem.components.pressScaleClickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.isSystemInDarkTheme
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
import rw.itunda.core.network.MapPlaceDetailDto
import rw.itunda.core.network.MoveMapBookmarkRequest
import rw.itunda.core.network.MapsDirectionsResponse
import rw.itunda.core.network.ItineraryDirectionsRequest
import rw.itunda.core.network.ItineraryWaypointRequest
import rw.itunda.core.network.NearbyPlaceDto
import rw.itunda.core.network.TrendingPlaceDto
import rw.itunda.core.designsystem.components.EmptyState
import rw.itunda.core.designsystem.components.IdsTextField
import rw.itunda.core.designsystem.itundaface.LinkGlyph
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.designsystem.theme.IdsIcons
import rw.itunda.core.network.MapConfig
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.SetMapFolderVisibilityRequest
import rw.itunda.core.network.TokenStore
import rw.itunda.core.network.PlaceSearchResultDto
import rw.itunda.core.network.RecentMapSearchesStore
import rw.itunda.core.network.RouteResultDto
import rw.itunda.core.network.BusTripDto
import rw.itunda.core.network.KigaliWeatherDto
import rw.itunda.core.network.TransitJourneyDto
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

// Real distance-measurement (ruler) tool, bookmark-folder defaults, map style JSON,
// pin-bitmap drawing, source/layer id constants, and the small BusTripResultsView/
// PlaceActionPill/splitPlaceName leaf composables all moved out to MapStyle.kt /
// MapUiComponents.kt (2026-08-19) -- see MapStyle.kt's own header comment for why.
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
fun MapScreen(
    onBack: () -> Unit,
    initialCategory: String? = null,
    initialSearchQuery: String? = null,
    // itunda://maps/shared/{userId}/{folderName} -- receiving half of toggleFolderShare's
    // own share sheet below. Optional/no-op default so no other call site breaks.
    initialSharedFolder: Pair<String, String>? = null,
    // Real "배달" (Delivery) pill (2026-08-09) -- itunda's Feature-module isolation forbids
    // Maps depending on Eats directly, so the app shell supplies this callback instead.
    onOrderDelivery: (merchantId: String, businessName: String) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    // Real dark map style (2026-08-10) -- read once via `remember`, like every other
    // one-shot MapView setup below.
    val isDarkMap = isSystemInDarkTheme()
    val styleJson = remember(isDarkMap) { mapStyleJson(isDarkMap) }
    // The cash-out flow deliberately arrives with the public agent network selected.
    val isAgentCashDiscovery = initialCategory == "ITUNDA_AGENT"
    // Must run synchronously during composition, not LaunchedEffect -- `remember { MapView(context) }`
    // below runs synchronously in this same initial composition, so MapView was being
    // constructed before MapLibre.getInstance() ever ran, crashing every time.
    remember { MapLibre.getInstance(context) }

    var merchants by remember { mutableStateOf<List<ShoppingMerchantDto>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<PlaceSearchResultDto>?>(null) }
    var searching by remember { mutableStateOf(false) }
    val recentSearchesStore = remember { RecentMapSearchesStore(context) } // ported from bank-mfe
    var recentSearches by remember { mutableStateOf<List<PlaceSearchResultDto>>(emptyList()) }
    var searchFocused by remember { mutableStateOf(false) }
    var selectedPlace by remember { mutableStateOf<PlaceSearchResultDto?>(null) }
    var bookingService by remember { mutableStateOf<MerchantProductDto?>(null) } // moved from Shop, see MapsBooking.kt
    // Real "Itunda Places" (2026-08-09) -- per-merchant products/reviews/detail, fetched
    // only for a real itunda merchant match; each tab renders only once its own fetch returns content.
    var placeTab by remember { mutableStateOf(PlaceTab.HOME) }
    var placeProducts by remember { mutableStateOf<List<MerchantProductDto>?>(null) }
    var placeReviews by remember { mutableStateOf<List<EatsReviewDto>?>(null) }
    var placeDetail by remember { mutableStateOf<MapPlaceDetailDto?>(null) }
    val selectedMerchant = selectedPlace?.let { place -> merchants.find { it.latitude == place.latitude && it.longitude == place.longitude } }
    LaunchedEffect(selectedMerchant?.merchantId) {
        placeTab = PlaceTab.HOME
        placeProducts = null
        placeReviews = null
        placeDetail = null
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
        // Real consolidated Photos/News/AI-summary/tag-aggregate (2026-08-28).
        try { placeDetail = NetworkClient.apiService.getMapPlaceDetail(merchantId).place } catch (_: Exception) { /* real, honest: absent means the tabs below stay silent, never fabricated */ }
    }
    if (MerchantBookingGate(selectedMerchant, bookingService) { bookingService = null }) return
    var route by remember { mutableStateOf<MapsDirectionsResponse?>(null) }
    // Real alternative routes (2026-07-22) -- see MapsDirectionsAlternativesResponse's
    // Real alternative routes (2026-07-22) -- often a single-element list, OSRM itself
    // decides whether a real alternative exists for a given trip.
    var routeAlternatives by remember { mutableStateOf<List<RouteResultDto>?>(null) }
    var selectedRouteIndex by remember { mutableStateOf(0) }
    // Bounded itinerary builder: the real Maps endpoint accepts start + 1-6 ordered
    // places. Search results are the picker so these are genuine geocoded places.
    var itineraryBuilding by remember { mutableStateOf(false) }
    var itineraryStops by remember { mutableStateOf<List<PlaceSearchResultDto>>(emptyList()) }
    var showingItineraryRoute by remember { mutableStateOf(false) }
    var travelMode by remember { mutableStateOf("DRIVING") } // real driving/walking/biking toggle, see OsrmRoutingClient.route
    // Real "each mode shows its own precomputed time" (2026-08-09) -- a background fetch
    // for the one mode NOT currently active; null until it resolves (or forever if it
    // errors -- an honest omission, never a guessed number).
    var otherModeEtaMinutes by remember { mutableStateOf<Double?>(null) }
    var busTrips by remember { mutableStateOf<List<BusTripDto>?>(null) } // real intercity coach marketplace, BusService.kt
    var busSearching by remember { mutableStateOf(false) }
    fun searchBus(destination: String) {
        busSearching = true
        busTrips = null
        coroutineScope.launch {
            try {
                val res = NetworkClient.apiService.searchBusTrips(destination = destination)
                if (res.success) busTrips = res.trips
            } catch (e: Exception) {
                busTrips = emptyList()
            } finally {
                busSearching = false
            }
        }
    }
    var transitJourneys by remember { mutableStateOf<List<TransitJourneyDto>?>(null) } // real Kigali GTFS journeys, separate from the intercity-bus tab above
    var transitSearching by remember { mutableStateOf(false) }
    var showSteps by remember { mutableStateOf(false) }
    var routing by remember { mutableStateOf(false) }
    var locating by remember { mutableStateOf(false) }
    var myLocation by remember { mutableStateOf<Pair<Double, Double>?>(null) } // lat, lng
    fun searchTransit(toLat: Double, toLng: Double) {
        val (fromLat, fromLng) = myLocation ?: return
        transitSearching = true
        transitJourneys = null
        coroutineScope.launch {
            try {
                val res = NetworkClient.apiService.getTransitDirections(fromLat, fromLng, toLat, toLng)
                if (res.success) transitJourneys = res.journeys
            } catch (e: Exception) {
                transitJourneys = emptyList()
            } finally {
                transitSearching = false
            }
        }
    }
    var weather by remember { mutableStateOf<KigaliWeatherDto?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var activeCategory by remember { mutableStateOf<String?>(null) }
    var categoryLoading by remember { mutableStateOf(false) }
    var categoryResults by remember { mutableStateOf<List<NearbyPlaceDto>?>(null) }
    var bookmarks by remember { mutableStateOf<List<MapBookmarkDto>>(emptyList()) }
    var bookmarking by remember { mutableStateOf(false) }
    var sharedFolderBookmarks by remember { mutableStateOf<List<MapBookmarkDto>?>(null) } // opened from a real itunda://maps/shared/... link
    var sharedFolderError by remember { mutableStateOf<String?>(null) }
    var loadingSharedFolder by remember { mutableStateOf(false) }
    var subscribingSharedFolder by remember { mutableStateOf(false) } // real "구독" (subscribe), write half of a shared folder
    var subscribedSharedFolderCount by remember { mutableStateOf<Int?>(null) }
    // Real "알림받기" (Notify/Follow) pill (2026-08-09) -- the backend + Retrofit endpoints
    // already existed (ported for bank-mfe) but had zero Android UI until this pass.
    var followedMerchantIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var following by remember { mutableStateOf(false) }
    val currentUserId = remember { NetworkClient.currentTokenStore().let(TokenStore::getUserId) }
    var sharingFolder by remember { mutableStateOf<String?>(null) }
    var shareConfirmation by remember { mutableStateOf<String?>(null) }
    var savingToFolder by remember { mutableStateOf<PlaceSearchResultDto?>(null) } // real save-time folder/color picker, ported from bank-mfe
    var folderNameInput by remember { mutableStateOf(DEFAULT_BOOKMARK_FOLDER) }
    var folderColorInput by remember { mutableStateOf(BOOKMARK_COLOR_PALETTE[0]) }
    var movingBookmark by remember { mutableStateOf<MapBookmarkDto?>(null) } // real "move to folder", PATCH /api/v1/maps/bookmarks
    var moveFolderNameInput by remember { mutableStateOf("") }
    var moveFolderColorInput by remember { mutableStateOf(BOOKMARK_COLOR_PALETTE[0]) }

    var measuring by remember { mutableStateOf(false) } // real distance-measurement (ruler) tool, tap-order (lat,lng) pairs
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

    // The receiving half of toggleFolderShare below (2026-08-14). The share sheet has
    // handed out itunda://maps/shared/... links since 2026-08-04, but nothing ever
    // resolved them -- getSharedMapFolder existed with zero call sites, so a recipient
    // tapping a shared link landed on a blank map. The endpoint is permitAll'd on the
    // backend, so this deliberately works for a recipient who isn't signed in too.
    LaunchedEffect(initialSharedFolder) {
        val (ownerId, folderName) = initialSharedFolder ?: return@LaunchedEffect
        loadingSharedFolder = true
        sharedFolderError = null
        try {
            val res = NetworkClient.apiService.getSharedMapFolder(ownerId, folderName)
            sharedFolderBookmarks = res.bookmarks
            // Center on the shared list rather than leaving the recipient wherever they
            // happen to be -- the whole point of opening the link is to see these places.
            res.bookmarks.firstOrNull()?.let { first ->
                selectedPlace = PlaceSearchResultDto(first.displayName, first.latitude, first.longitude)
            }
        } catch (e: HttpException) {
            sharedFolderError = superAppErrorMessage(e)
        } catch (e: Exception) {
            sharedFolderError = "Couldn't reach itunda. Check your connection and try again."
        } finally {
            loadingSharedFolder = false
        }
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

    // Real Kigali weather chip (2026-08-28, itunda Maps redesign) -- see
    // KigaliWeatherClient's own doc comment on the backend, real 30-minute server
    // cache; a real once-on-load fetch here is enough, not a client-side poll.
    // `weather` stays null (chip doesn't render) if the real upstream is
    // unreachable -- never fabricated.
    LaunchedEffect(Unit) {
        try {
            weather = NetworkClient.apiService.getKigaliWeather().weather
        } catch (e: Exception) {
            // Honest partial failure, same discipline as merchants/bookmarks above.
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
    LaunchedEffect(Unit) {
        try {
            followedMerchantIds = NetworkClient.apiService.getMyFollowedMerchants().follows.map { it.merchantId }.toSet()
        } catch (e: Exception) {
            // Honest partial failure, same as bookmarks above.
        }
    }
    fun toggleFollow(merchantId: String) {
        coroutineScope.launch {
            following = true
            try {
                if (merchantId in followedMerchantIds) {
                    NetworkClient.apiService.unfollowMerchant(merchantId)
                    followedMerchantIds = followedMerchantIds - merchantId
                } else {
                    NetworkClient.apiService.followMerchant(merchantId)
                    followedMerchantIds = followedMerchantIds + merchantId
                }
            } catch (_: Exception) {
                // Best-effort -- the pill just stays at its pre-tap state on failure.
            } finally {
                following = false
            }
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

    // Real Kakao Map-style "구독" (subscribe) -- the write half of a shared folder, ported
    // from bank-mfe's own handleSubscribeToSharedFolder (2026-08-18): real-copies the
    // owner's public places into the caller's own bookmarks, then refreshes `bookmarks`
    // so the new folder shows up immediately in "Your saved places" (matching the
    // staleness fix bank-mfe's own version needed for the identical reason).
    fun handleSubscribeToSharedFolder() {
        val (ownerId, folderName) = initialSharedFolder ?: return
        subscribingSharedFolder = true
        sharedFolderError = null
        coroutineScope.launch {
            try {
                val res = NetworkClient.apiService.subscribeToSharedMapFolder(ownerId, folderName)
                subscribedSharedFolderCount = res.copiedCount
                bookmarks = NetworkClient.apiService.getMyMapBookmarks().bookmarks
            } catch (e: HttpException) {
                sharedFolderError = superAppErrorMessage(e)
            } catch (e: Exception) {
                sharedFolderError = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                subscribingSharedFolder = false
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
                // Real fix (2026-08-09), direct user report ("why do we have maplibre
                // watermark"): MapLibre's own logo mark is on by default and was never
                // touched -- itunda self-hosts its own tiles/style/data, so showing the
                // library's own branding reads as an unfinished third-party wrapper, not
                // itunda's real product. Attribution stays ON, deliberately -- itunda's
                // tiles are genuinely built from real OpenStreetMap data (Geofabrik Rwanda
                // extract), and OSM's ODbL license requires real credit; only the logo
                // mark (MapLibre's own branding, not a data-license requirement) is hidden.
                map.uiSettings.isLogoEnabled = false
                map.setStyle(Style.Builder().fromJson(styleJson)) { style ->
                    val pinDensity = context.resources.displayMetrics.density
                    style.addImage(MERCHANT_ICON_ID, createPinBitmap(pinDensity, "#7472F4"))
                    // Real per-category merchant pin (2026-08-09) -- see merchantPinIconId's
                    // own doc comment above for why this is a keyword bucket, not an enum.
                    style.addImage(MERCHANT_FOOD_ICON_ID, createPinBitmap(pinDensity, "#FFA000"))
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
                            iconImage(org.maplibre.android.style.expressions.Expression.get("pinIcon")),
                            iconAnchor(Property.ICON_ANCHOR_BOTTOM),
                            iconAllowOverlap(true), iconSize(0.85f),
                        ),
                    )
                    // Real drawn route (2026-07-19) -- rendered before the location/
                    // destination pins so they paint on top of the line.
                    style.addSource(GeoJsonSource(ROUTE_SOURCE_ID, FeatureCollection.fromFeatures(emptyArray())))
                    style.addLayer(
                        LineLayer(ROUTE_LAYER_ID, ROUTE_SOURCE_ID).withProperties(
                            lineColor("#7472F4"), lineWidth(5f), lineOpacity(0.9f),
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
                            circleRadius(18f), circleColor("#7472F4"), circleOpacity(0.16f),
                        ),
                    )
                    style.addLayer(
                        CircleLayer(MY_LOCATION_LAYER_ID, MY_LOCATION_SOURCE_ID).withProperties(
                            circleRadius(7f), circleColor("#7472F4"), circleStrokeWidth(3f), circleStrokeColor("#ffffff"),
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
                    val featureCollection = FeatureCollection.fromFeatures(currentMerchants.map { m -> merchantFeature(m) })
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
                // Real "long-press to drop a pin" (2026-08-10) -- a universal, decades-old
                // convention on Google Maps, Apple Maps, Kakao Maps, and Naver Maps: a
                // long-press on any bare map point (not a real feature) drops a pin there
                // and opens the same detail sheet a search result does, so a user can get
                // directions to or share a spot that has no listed place. Shows immediately
                // with a placeholder name (matching how all four reference apps render the
                // pin before the reverse-geocode call returns) rather than waiting.
                map.addOnMapLongClickListener { latLng ->
                    if (!currentMeasuring) {
                        val dropped = PlaceSearchResultDto("Dropped pin", latLng.latitude, latLng.longitude)
                        selectPlace(dropped)
                        coroutineScope.launch {
                            try {
                                val name = NetworkClient.apiService.reverseGeocode(latLng.latitude, latLng.longitude).placeName
                                if (name != null && selectedPlace?.latitude == latLng.latitude && selectedPlace?.longitude == latLng.longitude) {
                                    selectedPlace = PlaceSearchResultDto(name, latLng.latitude, latLng.longitude)
                                }
                            } catch (_: Exception) {
                                // Non-critical -- the pin stays labeled "Dropped pin"; still fully usable for directions.
                            }
                        }
                        true
                    } else {
                        false
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
                source.setGeoJson(FeatureCollection.fromFeatures(merchants.map { m -> merchantFeature(m) }))
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
            // Real fix (2026-08-09), direct user report ("bottom sheet hangs in middle of
            // screen instead of raising from bottom"): `updateAnchors(anchors)` without an
            // explicit target defaults to `sheetState.currentValue` at call time -- but a
            // separate `LaunchedEffect(selectedPlace)` above can call `animateTo(Peek)`
            // before real anchors ever exist (on first composition, before
            // BoxWithConstraints has measured a real screen height), animating against an
            // empty anchor set with nowhere real to land. Passing the target explicitly
            // here, keyed to itunda's own current selection state, makes the sheet's
            // first-ever real settle deterministic instead of depending on which
            // LaunchedEffect happens to run first.
            LaunchedEffect(sheetAnchors) {
                sheetState.updateAnchors(sheetAnchors, if (selectedPlace != null) MapSheetValue.Half else MapSheetValue.Peek)
            }

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
            MapTopChrome(
                onBack = onBack,
                query = query,
                searching = searching,
                activeCategory = activeCategory,
                categoryLoading = categoryLoading,
                categoryResults = categoryResults,
                itineraryBuilding = itineraryBuilding,
                itineraryStopCount = itineraryStops.size,
                selectedPlace = selectedPlace,
                searchResults = searchResults,
                error = error,
                aroundMePlaces = aroundMePlaces,
                trendingPlaces = trendingPlaces,
                searchFocused = searchFocused,
                recentSearches = recentSearches,
                isAgentCashDiscovery = isAgentCashDiscovery,
                onQueryChange = { query = it },
                onRunSearch = { runSearch() },
                onClearQuery = { query = ""; searchResults = null },
                onSearchFocusChange = { searchFocused = it },
                onCategoryClick = { categoryId -> searchNearbyCategory(categoryId) },
                onToggleItinerary = {
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
                },
                onSelectPlace = { place -> selectPlace(place) },
                onSearchResultTap = { place ->
                    recentSearches = recentSearchesStore.add(place)
                    if (itineraryBuilding) {
                        addItineraryStop(place)
                    } else {
                        selectPlace(place)
                    }
                },
                onClearRecentSearches = { recentSearchesStore.clear(); recentSearches = emptyList() },
            )
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
                            modifier = Modifier.pressScaleClickable { measurePoints = measurePoints.dropLast(1); lastMeasuredPlaceName = null },
                        )
                    }
                    if (measurePoints.size >= 2) {
                        Text(
                            if (routing) "Routing…" else "Route itinerary",
                            fontSize = 12.sp, fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color.White,
                            modifier = Modifier
                                .background(Ids.colors.brand, RoundedCornerShape(999.dp))
                                .pressScaleClickable(enabled = !routing) { routeMeasuredItinerary() }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                    Text(
                        "Done", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ids.colors.textTertiary,
                        modifier = Modifier.pressScaleClickable { measuring = false; measurePoints = emptyList(); lastMeasuredPlaceName = null },
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
                        modifier = Modifier.size(44.dp).pressScaleClickable {
                            mapView.getMapAsync { map -> map.easeCamera(CameraUpdateFactory.zoomIn()) }
                        },
                        contentAlignment = Alignment.Center,
                    ) { Icon(IdsIcons.Add, contentDescription = "Zoom in", tint = Ids.colors.textPrimary, modifier = Modifier.size(18.dp)) }
                    Box(modifier = Modifier.width(44.dp).height(1.dp).background(Ids.colors.divider))
                    Box(
                        modifier = Modifier.size(44.dp).pressScaleClickable {
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
                        .pressScaleClickable(enabled = !locating) { requestMyLocation() },
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
                        .pressScaleClickable { toggleMeasuring() },
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
                            // Real driving/walking mode toggle (2026-07-22) -- same real
                            // Naver/Kakao Maps convention of picking a travel mode before/
                            // after a route is drawn. Switching mode while a route is already
                            // shown re-fetches against itunda's own separately-deployed
                            // foot-profile OSRM instance.
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
                                        otherModeEtaMinutes = null
                                        launch {
                                            try {
                                                val otherMode = if (mode == "DRIVING") "WALKING" else "DRIVING"
                                                val otherResponse = NetworkClient.apiService.getDirections(origin.first, origin.second, place.latitude, place.longitude, otherMode)
                                                otherModeEtaMinutes = otherResponse.route.durationMinutes
                                            } catch (_: Exception) { /* honest omission, not a guessed number */ }
                                        }
                                    } catch (e: HttpException) {
                                        error = superAppErrorMessage(e)
                                    } catch (e: Exception) {
                                        error = "Couldn't reach itunda. Check your connection and try again."
                                    } finally {
                                        routing = false
                                    }
                                }
                            }
                            fun clearRoute() {
                                route = null
                                routeAlternatives = null
                                selectedRouteIndex = 0
                                otherModeEtaMinutes = null
                                showSteps = false
                            }
                            PlaceDetailAndRouteView(
                                place = place,
                                selectedMerchant = selectedMerchant,
                                placeProducts = placeProducts,
                                placeReviews = placeReviews,
                                placeDetail = placeDetail,
                                placeTab = placeTab,
                                followedMerchantIds = followedMerchantIds,
                                following = following,
                                isBookmarked = isBookmarked(place),
                                bookmarking = bookmarking,
                                savingToFolder = savingToFolder,
                                folderNameInput = folderNameInput,
                                folderColorInput = folderColorInput,
                                isAgentCashDiscovery = isAgentCashDiscovery,
                                activeCategory = activeCategory,
                                routing = routing,
                                route = route,
                                navigating = navigating,
                                travelMode = travelMode,
                                otherModeEtaMinutes = otherModeEtaMinutes,
                                busSearching = busSearching,
                                busTrips = busTrips,
                                transitSearching = transitSearching,
                                transitJourneys = transitJourneys,
                                routeAlternatives = routeAlternatives,
                                selectedRouteIndex = selectedRouteIndex,
                                showSteps = showSteps,
                                currentStepIndex = currentStepIndex,
                                voiceEnabled = voiceEnabled,
                                onBack = onBack,
                                onOrderDelivery = onOrderDelivery,
                                onBookService = { bookingService = it },
                                onFetchDirections = { mode -> fetchDirections(mode) },
                                onClearRoute = { clearRoute() },
                                onToggleBookmark = { toggleBookmark(place) },
                                onToggleFollow = { merchantId -> toggleFollow(merchantId) },
                                onPlaceTabChange = { tab -> placeTab = tab },
                                onFolderNameChange = { folderNameInput = it },
                                onFolderColorChange = { folderColorInput = it },
                                onConfirmSaveToFolder = { confirmSaveToFolder() },
                                onCancelSaveToFolder = { savingToFolder = null },
                                onSearchBus = { destination -> travelMode = "BUS"; searchBus(destination) },
                                onSearchTransit = { travelMode = "TRANSIT"; searchTransit(place.latitude, place.longitude) },
                                onSelectRouteAlternative = { i, alt ->
                                    selectedRouteIndex = i
                                    route = MapsDirectionsResponse(success = true, route = alt)
                                },
                                onToggleShowSteps = { showSteps = !showSteps },
                                onStartNavigation = {
                                    currentStepIndex = 0
                                    // Itinerary routes have no single `selectedPlace` (the destination is the
                                    // last stop in itineraryStops instead) -- covers both real Start
                                    // Navigation entry points with the one real destination each carries.
                                    navigationDestination = selectedPlace?.let { it.latitude to it.longitude }
                                        ?: itineraryStops.lastOrNull()?.let { it.latitude to it.longitude }
                                    navigating = true
                                    requestMyLocation()
                                },
                                onEndNavigation = { navigating = false },
                                onToggleVoice = {
                                    voiceEnabled = !voiceEnabled
                                    if (!voiceEnabled) tts.stop()
                                },
                            )
                        } else {
                            if (itineraryBuilding) {
                                ItineraryBuilderCard(
                                    itineraryStops = itineraryStops,
                                    routing = routing,
                                    travelMode = travelMode,
                                    showingItineraryRoute = showingItineraryRoute,
                                    route = route,
                                    showSteps = showSteps,
                                    hasMyLocation = myLocation != null,
                                    onFetchItinerary = { fetchItinerary() },
                                    onModeClick = { mode ->
                                        if (mode != travelMode) {
                                            if (showingItineraryRoute) fetchItinerary(mode) else travelMode = mode
                                        }
                                    },
                                    onToggleShowSteps = { showSteps = !showSteps },
                                    onRemoveStop = { index ->
                                        itineraryStops = itineraryStops.filterIndexed { itemIndex, _ -> itemIndex != index }
                                        route = null
                                        showingItineraryRoute = false
                                    },
                                )
                            }
                            // Real "one thing per page" fix (2026-08-09) -- same complaint,
                            // same fix as the place-detail sheet above: this "Around you"
                            // browse/bookmarks content used to stay visible underneath the
                            // multi-stop itinerary builder card, so planning a trip and
                            // browsing/bookmark-managing were on screen simultaneously.
                            // Mutually exclusive now, matching itunda's own real Naver Map
                            // Smart Around sheet reference (below) with one real function at
                            // a time.
                            if (!itineraryBuilding) {
                                AroundYouSection(
                                    weather = weather,
                                    bookmarks = bookmarks,
                                    activeCategory = activeCategory,
                                    categoryResults = categoryResults,
                                    isAgentCashDiscovery = isAgentCashDiscovery,
                                    merchants = merchants,
                                    initialSharedFolder = initialSharedFolder,
                                    loadingSharedFolder = loadingSharedFolder,
                                    sharedFolderError = sharedFolderError,
                                    sharedFolderBookmarks = sharedFolderBookmarks,
                                    subscribingSharedFolder = subscribingSharedFolder,
                                    subscribedSharedFolderCount = subscribedSharedFolderCount,
                                    shareConfirmation = shareConfirmation,
                                    sharingFolder = sharingFolder,
                                    movingBookmark = movingBookmark,
                                    moveFolderNameInput = moveFolderNameInput,
                                    moveFolderColorInput = moveFolderColorInput,
                                    onSelectAndRoute = { place -> selectAndRoute(place) },
                                    onSelectPlace = { place ->
                                        selectedPlace = place
                                        route = null
                                        routeAlternatives = null
                                        selectedRouteIndex = 0
                                        savingToFolder = null
                                    },
                                    onSubscribeSharedFolder = ::handleSubscribeToSharedFolder,
                                    onToggleFolderShare = { folderName, makePublic -> toggleFolderShare(folderName, makePublic) },
                                    onToggleMovingBookmark = { bookmark ->
                                        if (movingBookmark?.let { it.latitude == bookmark.latitude && it.longitude == bookmark.longitude } == true) {
                                            movingBookmark = null
                                        } else {
                                            movingBookmark = bookmark
                                            moveFolderNameInput = bookmark.folderName
                                            moveFolderColorInput = bookmark.color
                                        }
                                    },
                                    onMoveFolderNameChange = { moveFolderNameInput = it },
                                    onMoveFolderColorChange = { moveFolderColorInput = it },
                                    onConfirmMove = {
                                        val target = movingBookmark
                                        if (target != null) {
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
                                        }
                                    },
                                )
                            }
                            Box(modifier = Modifier.height(24.dp))
                        }
                    }
                }
            }
        } // end full-bleed map BoxWithConstraints
    }
}

// Extracted rather than inlined into MapScreen's own bottom-sheet lambda: that lambda
// was already close enough to the JVM's 64KB per-method bytecode ceiling that adding
// this section inline overflowed it (real MethodTooLargeException, 2026-08-14).
@Composable
internal fun SharedFolderSection(
    folderName: String,
    loading: Boolean,
    error: String?,
    sharedBookmarks: List<MapBookmarkDto>?,
    subscribing: Boolean,
    subscribedCount: Int?,
    onSubscribe: () -> Unit,
    onOpenPlace: (MapBookmarkDto) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 8.dp)) {
        LinkGlyph(size = 11.dp)
        Text(
            "Shared with you · $folderName",
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = Ids.colors.textSecondary,
        )
    }
    when {
        loading -> Text("Loading shared places…", fontSize = 12.sp, color = Ids.colors.textSecondary)
        error != null -> Text(error, fontSize = 12.sp, color = Ids.colors.danger)
        sharedBookmarks?.isEmpty() == true ->
            Text("This shared list is empty, or is no longer public.", fontSize = 12.sp, color = Ids.colors.textSecondary)
        else -> {
        sharedBookmarks.orEmpty().forEach { shared ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .pressScaleClickable { onOpenPlace(shared) }
                    .padding(vertical = 6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            try { androidx.compose.ui.graphics.Color(AndroidColor.parseColor(shared.color)) } catch (_: Exception) { androidx.compose.ui.graphics.Color(0xFFF5A623) },
                            CircleShape,
                        ),
                )
                Text(shared.displayName, fontSize = 13.sp, color = Ids.colors.textPrimary, modifier = Modifier.weight(1f))
            }
        }
        // Real Kakao Map-style "구독" (subscribe) button -- the write half of a shared
        // folder, ported from bank-mfe's own identical "Save to my places (N)" button.
        if (subscribedCount != null) {
            Text(
                "✓ Saved $subscribedCount new place${if (subscribedCount == 1) "" else "s"} to your own bookmarks",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Ids.colors.brand,
                modifier = Modifier.padding(top = 10.dp),
            )
        } else {
            Text(
                if (subscribing) "Saving…" else "Save to my places (${sharedBookmarks.orEmpty().size})",
                fontSize = 13.sp, fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color.White,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .background(Ids.colors.brand, RoundedCornerShape(10.dp))
                    .pressScaleClickable(enabled = !subscribing) { onSubscribe() }
                    .padding(vertical = 10.dp),
            )
        }
        }
    }
}
