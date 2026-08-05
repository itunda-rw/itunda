package rw.itunda.core.designsystem.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import kotlin.math.roundToInt
import retrofit2.HttpException
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.MapConfig
import rw.itunda.core.network.NetworkClient

// Real live delivery tracking (item 182, 2026-07-29) -- "the defining 'watch your order
// arrive' moment every real Coupang Eats/Uber Eats-style app has," per
// EatsOrderService.getRiderLocation's own doc comment (backend real since 2026-07-19).
// bank-mfe's LiveRiderMap.tsx has had this since 2026-07-20; Android never did (see
// docs/DESIGN_REFERENCES.md section 2, recommendation 4). Same self-hosted style as
// RouteMiniMap.kt (deliberately not reused directly -- this needs an extra live-polled
// rider layer RouteMiniMap has no slot for), plus a real, independently-polled rider
// marker on top, mirroring LiveRiderMap.tsx's own two-effect split (map+route once,
// rider position on its own 5s poll).
private val LIVE_MINI_STYLE_JSON: String
    get() {
        val tilesUrl = "${MapConfig.tilesBaseUrl}/rwanda/{z}/{x}/{y}.mvt"
        return """
{
  "version": 8,
  "sources": {
    "rwanda": { "type": "vector", "tiles": ["$tilesUrl"], "minzoom": 0, "maxzoom": 14 },
    "route": { "type": "geojson", "data": { "type": "FeatureCollection", "features": [] } },
    "from": { "type": "geojson", "data": { "type": "FeatureCollection", "features": [] } },
    "to": { "type": "geojson", "data": { "type": "FeatureCollection", "features": [] } },
    "rider": { "type": "geojson", "data": { "type": "FeatureCollection", "features": [] } }
  },
  "layers": [
    { "id": "background", "type": "background", "paint": { "background-color": "#f2efe9" } },
    { "id": "landcover", "type": "fill", "source": "rwanda", "source-layer": "landcover",
      "paint": { "fill-color": "#d8e8c8", "fill-opacity": 0.6 } },
    { "id": "water", "type": "fill", "source": "rwanda", "source-layer": "water",
      "paint": { "fill-color": "#a8d0e6" } },
    { "id": "transportation-minor", "type": "line", "source": "rwanda", "source-layer": "transportation",
      "filter": ["!", ["match", ["get", "class"], ["motorway", "trunk", "primary", "secondary"], true, false]],
      "paint": { "line-color": "#ffffff", "line-width": ["interpolate", ["linear"], ["zoom"], 8, 0.5, 16, 3] } },
    { "id": "transportation-major", "type": "line", "source": "rwanda", "source-layer": "transportation",
      "filter": ["match", ["get", "class"], ["motorway", "trunk", "primary", "secondary"], true, false],
      "paint": { "line-color": "#f5c96b", "line-width": ["interpolate", ["linear"], ["zoom"], 6, 1, 16, 5] } }
  ]
}
""".trimIndent()
    }

private const val RIDER_ICON_ID = "live-rider-icon"
private const val RIDER_SOURCE_ID = "rider"
private const val RIDER_LAYER_ID = "rider-symbol"

private fun createEmojiBitmap(density: Float, emoji: String): Bitmap {
    val size = (28f * density).roundToInt()
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size * 0.85f
        textAlign = Paint.Align.CENTER
    }
    val fm = paint.fontMetrics
    val y = size / 2f - (fm.ascent + fm.descent) / 2f
    canvas.drawText(emoji, size / 2f, y, paint)
    return bitmap
}

// A real, honest "how long ago" label from the rider's own last real location push --
// never disguised as instantaneous, since the rider app only pushes every real 15s.
private fun timeAgoLabel(iso: String): String {
    val updated = try {
        java.time.Instant.parse(iso)
    } catch (_: Exception) {
        return ""
    }
    val seconds = (java.time.Instant.now().epochSecond - updated.epochSecond).coerceAtLeast(0)
    return when {
        seconds < 5 -> "just now"
        seconds < 60 -> "${seconds}s ago"
        else -> "${seconds / 60}m ago"
    }
}

/**
 * A real, compact live delivery-tracking map: draws the restaurant→delivery route once
 * (same self-hosted OSRM directions RouteMiniMap already established) and polls the real
 * rider position every 5s, moving a real 🛵 marker on top. Straight port of bank-mfe's
 * LiveRiderMap.tsx.
 */
@Composable
fun LiveRiderMiniMap(orderId: String, fromLat: Double, fromLng: Double, toLat: Double, toLng: Double, fromLabel: String, toLabel: String) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { MapLibre.getInstance(context) }
    var error by remember { mutableStateOf<String?>(null) }
    var available by remember { mutableStateOf<Boolean?>(null) }
    var lastUpdatedAt by remember { mutableStateOf<String?>(null) }

    val mapView = remember { MapView(context) }
    var styleReady by remember { mutableStateOf(false) }
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
            map.uiSettings.setAllGesturesEnabled(false)
            map.setStyle(Style.Builder().fromJson(LIVE_MINI_STYLE_JSON)) { style ->
                style.addImage(RIDER_ICON_ID, createEmojiBitmap(context.resources.displayMetrics.density, "🛵"))
                style.addLayer(
                    LineLayer("route-line", "route").withProperties(
                        lineColor("#3182F6"), lineWidth(5f), lineOpacity(0.9f),
                        lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND),
                    ),
                )
                style.addLayer(
                    CircleLayer("from-circle", "from").withProperties(
                        circleRadius(7f), circleColor("#3182F6"), circleStrokeWidth(2f), circleStrokeColor("#ffffff"),
                    ),
                )
                style.addLayer(
                    CircleLayer("to-circle", "to").withProperties(
                        circleRadius(8f), circleColor("#E53935"), circleStrokeWidth(2f), circleStrokeColor("#ffffff"),
                    ),
                )
                style.addLayer(
                    SymbolLayer(RIDER_LAYER_ID, RIDER_SOURCE_ID).withProperties(iconImage(RIDER_ICON_ID), iconAllowOverlap(true)),
                )
                (style.getSourceAs<GeoJsonSource>("from"))
                    ?.setGeoJson(FeatureCollection.fromFeature(Feature.fromGeometry(Point.fromLngLat(fromLng, fromLat))))
                (style.getSourceAs<GeoJsonSource>("to"))
                    ?.setGeoJson(FeatureCollection.fromFeature(Feature.fromGeometry(Point.fromLngLat(toLng, toLat))))
                map.moveCamera(
                    CameraUpdateFactory.newLatLngBounds(
                        LatLngBounds.Builder().include(LatLng(fromLat, fromLng)).include(LatLng(toLat, toLng)).build(),
                        60,
                    ),
                )
                styleReady = true
            }
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    // Route drawn once, reusing the exact same real OSRM directions call RouteMiniMap
    // already established -- separate from the rider poll below since the route doesn't
    // change tick-to-tick, only the rider's own position does.
    LaunchedEffect(fromLat, fromLng, toLat, toLng, styleReady) {
        if (!styleReady) return@LaunchedEffect
        try {
            val result = NetworkClient.apiService.getDirections(fromLat, fromLng, toLat, toLng)
            mapView.getMapAsync { map ->
                val style = map.style ?: return@getMapAsync
                val points = result.route.geometry.map { (lat, lng) -> Point.fromLngLat(lng, lat) }
                (style.getSourceAs<GeoJsonSource>("route"))?.setGeoJson(FeatureCollection.fromFeature(Feature.fromGeometry(LineString.fromLngLats(points))))
            }
        } catch (_: HttpException) {
            // Real, non-critical -- the live rider dot below is the actual point of this
            // component; a missing route line just means slightly less context.
        } catch (_: Exception) {
        }
    }

    // Real independent poll for the rider's real live position, same 5s cadence as
    // bank-mfe's LiveRiderMap.tsx.
    LaunchedEffect(orderId, styleReady) {
        if (!styleReady) return@LaunchedEffect
        while (true) {
            try {
                val res = NetworkClient.apiService.getEatsRiderLocation(orderId)
                available = res.available
                error = null
                val loc = res.location
                if (loc != null) {
                    lastUpdatedAt = loc.updatedAt
                    mapView.getMapAsync { map ->
                        val style = map.style ?: return@getMapAsync
                        (style.getSourceAs<GeoJsonSource>(RIDER_SOURCE_ID))
                            ?.setGeoJson(FeatureCollection.fromFeature(Feature.fromGeometry(Point.fromLngLat(loc.longitude, loc.latitude))))
                    }
                }
            } catch (e: HttpException) {
                error = "Could not load your rider's location."
            } catch (_: Exception) {
                error = "Could not load your rider's location."
            }
            delay(5000)
        }
    }

    Column {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(12.dp)))
        error?.let { Text(it, fontSize = 12.sp, color = Ids.colors.danger) }
        if (available == false) {
            Text("Waiting for your rider's real location…", fontSize = 12.sp, color = Ids.colors.textSecondary)
        }
        lastUpdatedAt?.let { Text("🛵 Rider location updated ${timeAgoLabel(it)}", fontSize = 12.sp, color = Ids.colors.textSecondary) }
    }
}

private val SIMPLE_MINI_STYLE_JSON: String
    get() {
        val tilesUrl = "${MapConfig.tilesBaseUrl}/rwanda/{z}/{x}/{y}.mvt"
        return """
{
  "version": 8,
  "sources": {
    "rwanda": { "type": "vector", "tiles": ["$tilesUrl"], "minzoom": 0, "maxzoom": 14 },
    "rider": { "type": "geojson", "data": { "type": "FeatureCollection", "features": [] } }
  },
  "layers": [
    { "id": "background", "type": "background", "paint": { "background-color": "#f2efe9" } },
    { "id": "landcover", "type": "fill", "source": "rwanda", "source-layer": "landcover",
      "paint": { "fill-color": "#d8e8c8", "fill-opacity": 0.6 } },
    { "id": "water", "type": "fill", "source": "rwanda", "source-layer": "water",
      "paint": { "fill-color": "#a8d0e6" } },
    { "id": "transportation-minor", "type": "line", "source": "rwanda", "source-layer": "transportation",
      "filter": ["!", ["match", ["get", "class"], ["motorway", "trunk", "primary", "secondary"], true, false]],
      "paint": { "line-color": "#ffffff", "line-width": ["interpolate", ["linear"], ["zoom"], 8, 0.5, 16, 3] } },
    { "id": "transportation-major", "type": "line", "source": "rwanda", "source-layer": "transportation",
      "filter": ["match", ["get", "class"], ["motorway", "trunk", "primary", "secondary"], true, false],
      "paint": { "line-color": "#f5c96b", "line-width": ["interpolate", ["linear"], ["zoom"], 6, 1, 16, 5] } }
  ]
}
""".trimIndent()
    }

/**
 * Real live rider-location tracking for Commerce orders (item 230) -- found via a
 * defined-but-uncalled-endpoint sweep, see ApiService.getOrderRiderLocation's own doc
 * comment. A deliberately simpler sibling of LiveRiderMiniMap above: no route line, no
 * fixed from/to endpoints, since Commerce's OrderDto carries no delivery coordinates to
 * draw a route toward -- just the rider's own live position, re-centered as it updates.
 * Straight port of bank-mfe's SimpleLiveRiderMap.tsx (shipped first, 2026-08-05).
 */
@Composable
fun SimpleLiveRiderMiniMap(orderId: String) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { MapLibre.getInstance(context) }
    var error by remember { mutableStateOf<String?>(null) }
    var available by remember { mutableStateOf<Boolean?>(null) }
    var lastUpdatedAt by remember { mutableStateOf<String?>(null) }

    val mapView = remember { MapView(context) }
    var styleReady by remember { mutableStateOf(false) }
    var centered by remember { mutableStateOf(false) }
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
            map.uiSettings.setAllGesturesEnabled(false)
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(-1.9441, 30.0619), 12.0))
            map.setStyle(Style.Builder().fromJson(SIMPLE_MINI_STYLE_JSON)) { style ->
                style.addImage(RIDER_ICON_ID, createEmojiBitmap(context.resources.displayMetrics.density, "🛵"))
                style.addLayer(
                    SymbolLayer(RIDER_LAYER_ID, RIDER_SOURCE_ID).withProperties(iconImage(RIDER_ICON_ID), iconAllowOverlap(true)),
                )
                styleReady = true
            }
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    LaunchedEffect(orderId, styleReady) {
        if (!styleReady) return@LaunchedEffect
        while (true) {
            try {
                val res = NetworkClient.apiService.getOrderRiderLocation(orderId)
                available = res.available
                error = null
                val loc = res.location
                if (loc != null) {
                    lastUpdatedAt = loc.updatedAt
                    mapView.getMapAsync { map ->
                        val style = map.style ?: return@getMapAsync
                        (style.getSourceAs<GeoJsonSource>(RIDER_SOURCE_ID))
                            ?.setGeoJson(FeatureCollection.fromFeature(Feature.fromGeometry(Point.fromLngLat(loc.longitude, loc.latitude))))
                        if (!centered) {
                            centered = true
                            map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(loc.latitude, loc.longitude), 14.0))
                        }
                    }
                }
            } catch (e: HttpException) {
                error = "Could not load your rider's location."
            } catch (_: Exception) {
                error = "Could not load your rider's location."
            }
            delay(5000)
        }
    }

    Column {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(12.dp)))
        error?.let { Text(it, fontSize = 12.sp, color = Ids.colors.danger) }
        if (available == false) {
            Text("Waiting for your rider's real location…", fontSize = 12.sp, color = Ids.colors.textSecondary)
        }
        lastUpdatedAt?.let { Text("🛵 Rider location updated ${timeAgoLabel(it)}", fontSize = 12.sp, color = Ids.colors.textSecondary) }
    }
}
