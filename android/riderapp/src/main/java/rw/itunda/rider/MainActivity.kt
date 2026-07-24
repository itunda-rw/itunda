package rw.itunda.rider

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import rw.itunda.core.designsystem.theme.IdsTheme
import rw.itunda.rider.network.NetworkClient
import rw.itunda.rider.ui.BecomeRiderScreen
import rw.itunda.rider.ui.DeliveryDetailScreen
import rw.itunda.rider.ui.LoginScreen
import rw.itunda.rider.ui.RiderHomeScreen

private sealed class RiderScreen {
    object Loading : RiderScreen()
    object Login : RiderScreen()
    object BecomeRider : RiderScreen()
    object Home : RiderScreen()
    data class Delivery(val orderId: String) : RiderScreen()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Real shared brand theme (2026-07-24), replacing a bare default
            // MaterialTheme -- this app depended on :core:designsystem already
            // (see build.gradle.kts's own comment) but never actually applied
            // it, so every screen rendered in generic Material purple/teal
            // instead of itunda's real brand colors. IdsTheme builds a real
            // Material3 ColorScheme from those brand tokens, so this one wrap
            // fixes every MaterialTheme.colorScheme reference in this app at
            // once, no per-screen changes needed.
            IdsTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    RiderApp()
                }
            }
        }
    }
}

@Composable
private fun RiderApp() {
    var screen by remember { mutableStateOf<RiderScreen>(RiderScreen.Loading) }

    suspend fun resolveStartScreen() {
        if (!NetworkClient.currentTokenStore().hasSession()) {
            screen = RiderScreen.Login
            return
        }
        screen = try {
            NetworkClient.apiService.getMyRiderProfile()
            RiderScreen.Home
        } catch (e: Exception) {
            RiderScreen.BecomeRider
        }
    }

    LaunchedEffect(Unit) { resolveStartScreen() }

    when (val current = screen) {
        is RiderScreen.Loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        is RiderScreen.Login -> LoginScreen(
            // Route through BecomeRider either way -- its own LaunchedEffect below
            // re-checks getMyRiderProfile() and redirects straight to Home for a
            // returning rider, rather than assuming every fresh login needs
            // registration.
            onLoggedIn = { screen = RiderScreen.BecomeRider },
        )
        is RiderScreen.BecomeRider -> {
            LaunchedEffect(current) {
                // Re-check on entry: a returning rider who just logged in again should
                // skip straight to Home, not be shown "become a rider" every time.
                try {
                    NetworkClient.apiService.getMyRiderProfile()
                    screen = RiderScreen.Home
                } catch (e: Exception) { /* genuinely not registered yet -- show the real screen */ }
            }
            BecomeRiderScreen(
                onRegistered = { screen = RiderScreen.Home },
                onLogout = { NetworkClient.currentTokenStore().clearSession(); screen = RiderScreen.Login },
            )
        }
        is RiderScreen.Home -> RiderHomeScreen(
            onOpenDelivery = { orderId -> screen = RiderScreen.Delivery(orderId) },
            onLogout = { screen = RiderScreen.Login },
        )
        is RiderScreen.Delivery -> DeliveryDetailScreen(
            orderId = current.orderId,
            onBack = { screen = RiderScreen.Home },
        )
    }
}
