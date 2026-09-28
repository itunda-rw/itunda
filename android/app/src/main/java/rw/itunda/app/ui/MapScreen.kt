package rw.itunda.app.ui

import androidx.compose.runtime.Composable
import rw.itunda.feature.maps.impl.MapScreen as FeatureMapScreen

/**
 * Compatibility entry point for the app shell.
 *
 * The canonical map UI lives in :features:maps:impl. Keeping this thin adapter
 * preserves the existing app-shell navigation contract while avoiding a second
 * copy of the map implementation in :app.
 */
@Composable
fun MapScreen(onBack: () -> Unit) {
    FeatureMapScreen(onBack = onBack)
}
