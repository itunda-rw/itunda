package rw.itunda.rider

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import rw.itunda.core.designsystem.components.IdsLoading
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import retrofit2.HttpException
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import rw.itunda.core.designsystem.theme.IdsTheme
import rw.itunda.rider.network.CommerceOrderDto
import rw.itunda.rider.network.NetworkClient
import rw.itunda.rider.ui.BecomeRiderScreen
import rw.itunda.rider.ui.CommerceDeliveryDetailScreen
import rw.itunda.rider.ui.DeliveryDetailScreen
import rw.itunda.rider.ui.LoginScreen
import rw.itunda.rider.ui.RiderHomeScreen

private sealed class RiderScreen {
    object Loading : RiderScreen()
    object Login : RiderScreen()
    object BecomeRider : RiderScreen()
    object Error : RiderScreen()
    object Home : RiderScreen()
    data class Delivery(val orderId: String) : RiderScreen()
    // Real Commerce/Shop package delivery -- see CommerceDeliveryDetailScreen's own
    // doc comment on why this carries the full order object rather than just an id.
    data class CommerceDelivery(val order: CommerceOrderDto) : RiderScreen()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Real screenshot/screen-recording protection (2026-08-09), same Toss-parity fix as
        // the customer app's MainActivity.kt -- this app shows real delivery-fee/order payout
        // amounts on nearly every screen.
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
        )
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
        } catch (e: HttpException) {
            if (e.code() == 404) RiderScreen.BecomeRider else RiderScreen.Error
        } catch (e: Exception) {
            RiderScreen.Error
        }
    }

    LaunchedEffect(Unit) { resolveStartScreen() }

    when (val current = screen) {
        is RiderScreen.Loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { IdsLoading() }
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
                } catch (e: HttpException) {
                    if (e.code() != 404) screen = RiderScreen.Error
                } catch (e: Exception) {
                    screen = RiderScreen.Error
                }
            }
            BecomeRiderScreen(
                onRegistered = { screen = RiderScreen.Home },
                onLogout = { NetworkClient.currentTokenStore().clearSession(); screen = RiderScreen.Login },
            )
        }
        is RiderScreen.Error -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
                androidx.compose.material3.Text(text = "Couldn't check your rider status", style = MaterialTheme.typography.titleMedium)
                androidx.compose.material3.Text(text = "Check your connection and try again.", style = MaterialTheme.typography.bodyMedium)
                androidx.compose.material3.Button(onClick = { screen = RiderScreen.Loading }) {
                    androidx.compose.material3.Text("Retry")
                }
            }
        }
        is RiderScreen.Home -> RiderHomeScreen(
            onOpenDelivery = { orderId -> screen = RiderScreen.Delivery(orderId) },
            onOpenCommerceDelivery = { order -> screen = RiderScreen.CommerceDelivery(order) },
            onLogout = { screen = RiderScreen.Login },
        )
        is RiderScreen.Delivery -> DeliveryDetailScreen(
            orderId = current.orderId,
            onBack = { screen = RiderScreen.Home },
        )
        is RiderScreen.CommerceDelivery -> CommerceDeliveryDetailScreen(
            initialOrder = current.order,
            onBack = { screen = RiderScreen.Home },
        )
    }
}
