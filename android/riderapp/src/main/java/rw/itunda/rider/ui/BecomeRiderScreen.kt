package rw.itunda.rider.ui

import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import rw.itunda.core.designsystem.components.IdsButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import rw.itunda.rider.network.NetworkClient
import rw.itunda.rider.network.parseApiError
import java.io.IOException

/**
 * Shown for any logged-in itunda user MainActivity doesn't yet know is a rider.
 *
 * Real bug found live (2026-08-10): the doc comment here used to claim this screen
 * "never triggers" a second registerRider() call, but that's only true of
 * MainActivity's own *local* rider-profile check -- a fresh install/reinstall (or any
 * state loss) has no memory of a real prior registration until the server says so, so
 * this screen can absolutely be reached by an account that's already a real registered
 * rider. Before this fix, that real, specific RIDER_ALREADY_REGISTERED backend error
 * was being caught by the generic `catch (e: Exception)` below and replaced with a
 * hard-coded, unhelpful "couldn't register... try again" message that a user would
 * hit on every retry, forever, with no way forward -- exactly the friction Toss's own
 * error-handling philosophy is about eliminating (resolve it for the user when the
 * "failure" isn't actually one from their perspective, rather than leaving them
 * stuck on a technicality the way a plain OS-level error dialog would).
 */
@Composable
fun BecomeRiderScreen(onRegistered: () -> Unit, onLogout: () -> Unit) {
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Become an itunda Rider", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "Deliver real Eats orders and get paid straight to your itunda account after every delivery.",
            style = MaterialTheme.typography.bodyMedium,
        )
        error?.let {
            Spacer(modifier = Modifier.height(12.dp))
            Text(it, color = Ids.colors.danger)
        }
        Spacer(modifier = Modifier.height(20.dp))
        IdsButton(
            text = if (busy) "Registering…" else "Become a rider",
            enabled = !busy,
            loading = busy,
            onClick = {
                busy = true
                error = null
                scope.launch {
                    try {
                        NetworkClient.apiService.registerRider()
                        onRegistered()
                    } catch (e: retrofit2.HttpException) {
                        val parsed = parseApiError(e)
                        if (parsed.code == "RIDER_ALREADY_REGISTERED") {
                            // Real Toss-style resolution, not a dead-end error: the
                            // account genuinely IS already a registered rider, so move
                            // them forward instead of showing an error for something
                            // that isn't actually wrong.
                            onRegistered()
                        } else {
                            error = parsed.message ?: "Couldn't register as a rider right now. Try again."
                        }
                    } catch (e: IOException) {
                        error = "Couldn't reach itunda. Check your connection and try again."
                    } finally {
                        busy = false
                    }
                }
            },
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "Log out",
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).pressScaleClickable(onClick = onLogout),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}
