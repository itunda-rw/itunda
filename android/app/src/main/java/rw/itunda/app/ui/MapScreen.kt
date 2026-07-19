package rw.itunda.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import rw.itunda.app.network.NetworkClient
import rw.itunda.app.network.ShoppingMerchantDto

// Real itunda-hosted Rwanda coordinates -- Kigali, same default center every other real
// coordinate fixture in this codebase (backend tests, bank-mfe's MapView.tsx) uses.
private const val RWANDA_CENTER_LAT = -1.9441
private const val RWANDA_CENTER_LNG = 30.0619
private const val TILES_URL = "http://192.168.252.3:8090/rwanda/{z}/{x}/{y}.mvt"
private const val MERCHANTS_SOURCE_ID = "merchants"
private const val MERCHANTS_LAYER_ID = "merchants-circle"

// A real, minimal MapLibre style over itunda's own self-hosted vector tiles -- mirrors
// bank-mfe's MapView.tsx MAP_STYLE constant exactly (same source, same layer set, no
// text labels yet since that needs a separate self-hosted glyphs server). Kept as a
// single JSON string here since MapLibre Android's style DSL doesn't offer a typed
// builder as concise as the web SDK's; this is the same style spec format either way.
private val MAP_STYLE_JSON = """
{
  "version": 8,
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
      "paint": { "line-color": "#a08ccb", "line-width": 1, "line-dasharray": [2, 1] } }
  ]
}
""".trimIndent()

/**
 * Real interactive Rwanda map -- itunda's own self-hosted Kakao Maps/Naver Maps-style
 * mapping, ported here from bank-mfe's MapView.tsx (same self-hosted PMTiles tile server,
 * same hand-written style, same real merchant markers). Reached from My's Quick links,
 * matching the Pay/Benefits precedent, since neither bottom-nav row has a free slot.
 */
@Composable
fun MapScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { MapLibre.getInstance(context) }

    var merchants by remember { mutableStateOf<List<ShoppingMerchantDto>>(emptyList()) }
    LaunchedEffect(Unit) {
        try {
            val response = NetworkClient.apiService.getShoppingMerchants()
            merchants = response.merchants.filter { it.latitude != null && it.longitude != null }
        } catch (e: Exception) {
            // Honest partial failure -- the base map still renders even if the real
            // merchant overlay fails to load, never a blank screen for a real infra hiccup.
        }
    }
    val currentMerchants by rememberUpdatedState(merchants)

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
                    val featureCollection = FeatureCollection.fromFeatures(
                        currentMerchants.map { m ->
                            Feature.fromGeometry(Point.fromLngLat(m.longitude!!, m.latitude!!))
                        },
                    )
                    style.addSource(GeoJsonSource(MERCHANTS_SOURCE_ID, featureCollection))
                    style.addLayer(
                        CircleLayer(MERCHANTS_LAYER_ID, MERCHANTS_SOURCE_ID).withProperties(
                            circleRadius(8f),
                            circleColor("#3182F6"),
                            circleStrokeWidth(2f),
                            circleStrokeColor("#ffffff"),
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

        Column(modifier = Modifier.padding(padding)) {
            Box(modifier = Modifier.padding(16.dp)) {
                BackTopBar("Map", onBack)
            }
            AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        }
    }
}
