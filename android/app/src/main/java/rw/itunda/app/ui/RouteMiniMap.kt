package rw.itunda.app.ui

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
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import retrofit2.HttpException
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.designsystem.theme.Ids

// Same override mechanism as MapScreen.kt's TILES_URL -- this used to hardcode the
// private cloud's internal-only 192.168.252.3 bridge address directly, bypassing
// BuildConfig.TILES_BASE_URL entirely, so this mini-map stayed permanently blank on
// a physical device even after the main MapScreen fix (2026-07-21) that introduced
// the -PtilesBaseUrl override (found 2026-07-22 testing over the public endpoint).
private val ROUTE_MINI_TILES_URL = "${rw.itunda.app.BuildConfig.TILES_BASE_URL}/rwanda/{z}/{x}/{y}.mvt"
private val ROUTE_MINI_STYLE_JSON = """
{
  "version": 8,
  "sources": {
    "rwanda": { "type": "vector", "tiles": ["$ROUTE_MINI_TILES_URL"], "minzoom": 0, "maxzoom": 14 },
    "route": { "type": "geojson", "data": { "type": "FeatureCollection", "features": [] } },
    "from": { "type": "geojson", "data": { "type": "FeatureCollection", "features": [] } },
    "to": { "type": "geojson", "data": { "type": "FeatureCollection", "features": [] } }
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

/**
 * A real, compact, non-interactive drawn-route map -- reuses the exact same self-hosted
 * OSRM directions itunda's own Maps feature already exposes (see MapScreen.kt and the
 * backend `rw.itunda.maps.MapsService`), so Eats/Marketplace get a real route + distance/
 * duration instead of just a distance number, without duplicating any routing logic.
 * Straight port of bank-mfe's RouteMiniMap.tsx (item 8 on the Maps "100%" roadmap).
 */
@Composable
fun RouteMiniMap(fromLat: Double, fromLng: Double, toLat: Double, toLng: Double, fromLabel: String, toLabel: String) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { MapLibre.getInstance(context) }
    var distanceKm by remember { mutableStateOf<Double?>(null) }
    var durationMinutes by remember { mutableStateOf<Double?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }

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
            map.uiSettings.setAllGesturesEnabled(false)
            map.setStyle(Style.Builder().fromJson(ROUTE_MINI_STYLE_JSON)) { style ->
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
            }
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    LaunchedEffect(fromLat, fromLng, toLat, toLng) {
        loading = true
        error = null
        try {
            val result = NetworkClient.apiService.getDirections(fromLat, fromLng, toLat, toLng)
            distanceKm = result.route.distanceKm
            durationMinutes = result.route.durationMinutes
            mapView.getMapAsync { map ->
                val style = map.style ?: return@getMapAsync
                val points = result.route.geometry.map { (lat, lng) -> Point.fromLngLat(lng, lat) }
                (style.getSourceAs<GeoJsonSource>("route"))?.setGeoJson(FeatureCollection.fromFeature(Feature.fromGeometry(LineString.fromLngLats(points))))
                val bounds = LatLngBounds.Builder()
                points.forEach { p -> bounds.include(LatLng(p.latitude(), p.longitude())) }
                map.easeCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 40))
            }
        } catch (e: HttpException) {
            error = "Could not find a real route between these two points."
        } catch (e: Exception) {
            error = "Could not find a real route between these two points."
        } finally {
            loading = false
        }
    }

    Column {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(12.dp)))
        if (loading) Text("Finding the real road route…", fontSize = 12.sp, color = TossSecondary)
        error?.let { Text(it, fontSize = 12.sp, color = Ids.colors.danger) }
        val km = distanceKm
        val min = durationMinutes
        if (km != null && min != null) {
            Text(
                "🚗 ${"%.1f".format(km)} km · ${min.toInt()} min by real road, via itunda's own self-hosted OSRM",
                fontSize = 12.sp, color = TossSecondary,
            )
        }
    }
}
