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
import java.util.Locale
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
import androidx.compose.material.icons.outlined.Add
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
import rw.itunda.core.network.BusTripDto
import rw.itunda.core.network.ShoppingMerchantDto
import rw.itunda.core.network.superAppErrorMessage

// Extracted from MapScreen's own bottom-sheet lambda (2026-08-20), same real reason
// SharedFolderSection was already extracted before it (see that function's own doc
// comment): MapScreen's single Composable body was long enough to risk the same real
// MethodTooLargeException hit there. Pure "values in, callbacks out" rendering --
// zero state ownership moves, MapScreen still owns every var this reads; only the
// UI tree itself lives here now.
@Composable
internal fun ItineraryBuilderCard(
    itineraryStops: List<PlaceSearchResultDto>,
    routing: Boolean,
    travelMode: String,
    showingItineraryRoute: Boolean,
    route: MapsDirectionsResponse?,
    showSteps: Boolean,
    hasMyLocation: Boolean,
    onFetchItinerary: () -> Unit,
    onModeClick: (String) -> Unit,
    onToggleShowSteps: () -> Unit,
    onRemoveStop: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Ids.colors.successTint, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Multi-stop trip", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ids.colors.textPrimary)
        Text(
            "Start: ${if (hasMyLocation) "your current location" else "Kigali map center"}. Search and tap places in the order you want to visit them.",
            fontSize = 12.sp,
            color = Ids.colors.textSecondary,
        )
        if (itineraryStops.isEmpty()) {
            Text("Add 1–6 destinations to make a real road itinerary.", fontSize = 12.sp, color = Ids.colors.textSecondary)
        } else {
            itineraryStops.forEachIndexed { index, stop ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("${index + 2}. ${stop.displayName}", fontSize = 13.sp, color = Ids.colors.textPrimary, modifier = Modifier.weight(1f))
                    Text("Remove", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ids.colors.brand, modifier = Modifier.pressScaleClickable { onRemoveStop(index) })
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Ids.colors.brand, RoundedCornerShape(9.dp))
                    .pressScaleClickable(enabled = itineraryStops.isNotEmpty() && !routing) { onFetchItinerary() }
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
                            .pressScaleClickable(enabled = !routing) { onModeClick(mode) }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (active) androidx.compose.ui.graphics.Color.White else Ids.colors.textSecondary) }
                }
            }
            val itineraryRoute = route.takeIf { showingItineraryRoute }
            if (itineraryRoute != null) {
                Text(
                    "${if (travelMode == "DRIVING") "🚗" else "🚶"} ${String.format(Locale.US, "%.1f", itineraryRoute.route.distanceKm)} km · ${itineraryRoute.route.durationMinutes.toInt()} min by real road",
                    fontSize = 13.sp,
                    color = Ids.colors.textSecondary,
                )
                Text("Legs", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ids.colors.textPrimary)
                val legLabels = listOf(if (hasMyLocation) "Your location" else "Kigali map center") + itineraryStops.map { it.displayName }
                legLabels.zipWithNext().forEachIndexed { index, (from, to) ->
                    Text("${index + 1}. $from → $to", fontSize = 12.sp, color = Ids.colors.textSecondary)
                }
                if (itineraryRoute.route.steps.isNotEmpty()) {
                    Text(
                        if (showSteps) "Hide turn-by-turn directions" else "Show turn-by-turn directions (${itineraryRoute.route.steps.size} steps)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Ids.colors.brand,
                        modifier = Modifier.pressScaleClickable { onToggleShowSteps() },
                    )
                    if (showSteps) itineraryRoute.route.steps.forEachIndexed { index, step ->
                        Text("${index + 1}. ${step.instruction}", fontSize = 12.sp, color = Ids.colors.textSecondary)
                    }
                }
            }
        }
    }
}

