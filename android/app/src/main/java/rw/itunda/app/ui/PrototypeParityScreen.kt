package rw.itunda.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Consumer app entry surface.
 *
 * The former implementation attempted to load an HTML prototype from
 * android/app/src/main/assets, but that asset directory/file is not present in
 * the repository. That left the default logged-in launch path dependent on a
 * missing file and could render a blank/error WebView instead of the app.
 *
 * Keep the launch path on the real native consumer UI. ItundaAppScreen owns the
 * five primary destinations (Home, Pay, Explore, Messages, You), native
 * navigation, accessibility semantics, and the existing feature entry points.
 * Prototype parity work should be implemented in those native screens rather
 * than relying on an untracked local HTML asset.
 */
@Composable
fun PrototypeParityScreen(modifier: Modifier = Modifier) {
    ItundaAppScreen()
}
