package rw.itunda.rider.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
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
import java.io.IOException

/**
 * Shown once for any logged-in itunda user who hasn't registered as a rider yet --
 * registerRider() is a real, cheap, idempotent-on-repeat-visit call (RiderService
 * throws RiderAlreadyRegisteredException on a second attempt, which this screen
 * never triggers since MainActivity only shows it before a real rider profile
 * exists).
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
            "Deliver real Eats orders and get paid straight to your itunda wallet after every delivery.",
            style = MaterialTheme.typography.bodyMedium,
        )
        error?.let {
            Spacer(modifier = Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = {
                busy = true
                error = null
                scope.launch {
                    try {
                        NetworkClient.apiService.registerRider()
                        onRegistered()
                    } catch (e: IOException) {
                        error = "Couldn't reach itunda. Check your connection and try again."
                    } catch (e: Exception) {
                        error = "Couldn't register as a rider right now. Try again."
                    } finally {
                        busy = false
                    }
                }
            },
            enabled = !busy,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text(if (busy) "Registering…" else "Become a rider")
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "Log out",
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).clickable(onClick = onLogout),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}
