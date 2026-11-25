package rw.itunda.feature.maps.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Feature composition entry point. Keeps the app shell independent of concrete screen names.
 * `initialHub` ("places" | "directions") starts on the prototype's hub list; picking a result
 * opens the real map with that category/query, and Back from the map returns to the hub.
 */
@Composable
fun MapsEntryPoint(
    onBack: () -> Unit,
    initialCategory: String? = null,
    initialSearchQuery: String? = null,
    initialSharedFolder: Pair<String, String>? = null,
    onOrderDelivery: (merchantId: String, businessName: String) -> Unit = { _, _ -> },
    initialHub: String? = null,
) {
    var hub by remember { mutableStateOf(initialHub) }
    var category by remember { mutableStateOf(initialCategory) }
    var query by remember { mutableStateOf(initialSearchQuery) }
    when (hub) {
        "places" -> PlacesHubScreen(onBack) { c, q -> category = c; query = q; hub = null }
        "directions" -> DirectionsHubScreen(onBack) { q -> category = null; query = q; hub = null }
        else -> key(category, query) {
            MapScreen(if (initialHub != null) ({ hub = initialHub }) else onBack, category, query, initialSharedFolder, onOrderDelivery)
        }
    }
}
