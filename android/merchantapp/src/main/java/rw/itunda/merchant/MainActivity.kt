package rw.itunda.merchant

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
import rw.itunda.merchant.network.MerchantDto
import rw.itunda.merchant.network.NetworkClient
import rw.itunda.merchant.ui.BecomeMerchantScreen
import rw.itunda.merchant.ui.LoginScreen
import rw.itunda.merchant.ui.MerchantHomeScreen

private sealed class MerchantScreen {
    object Loading : MerchantScreen()
    object Login : MerchantScreen()
    object BecomeMerchant : MerchantScreen()
    data class Home(val merchant: MerchantDto) : MerchantScreen()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Real shared brand theme (2026-07-24), same fix as riderapp's own
            // MainActivity.kt -- this app also depended on :core:designsystem
            // without ever applying it, rendering in generic Material colors
            // instead of itunda's real brand.
            IdsTheme {
                Surface(modifier = androidx.compose.ui.Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MerchantApp()
                }
            }
        }
    }
}

@Composable
private fun MerchantApp() {
    var screen by remember { mutableStateOf<MerchantScreen>(MerchantScreen.Loading) }

    suspend fun resolveStartScreen() {
        if (!NetworkClient.currentTokenStore().hasSession()) {
            screen = MerchantScreen.Login
            return
        }
        screen = try {
            MerchantScreen.Home(NetworkClient.apiService.getMyMerchant().merchant)
        } catch (e: Exception) {
            MerchantScreen.BecomeMerchant
        }
    }

    LaunchedEffect(Unit) { resolveStartScreen() }

    when (val current = screen) {
        is MerchantScreen.Loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        is MerchantScreen.Login -> LoginScreen(onLoggedIn = { screen = MerchantScreen.BecomeMerchant })
        is MerchantScreen.BecomeMerchant -> {
            LaunchedEffect(current) {
                // Re-check on entry: a returning merchant who just logged in again
                // should skip straight to Home, not be shown "register your
                // business" every time.
                try {
                    screen = MerchantScreen.Home(NetworkClient.apiService.getMyMerchant().merchant)
                } catch (e: Exception) { /* genuinely not registered yet -- show the real screen */ }
            }
            BecomeMerchantScreen(
                onRegistered = { merchant -> screen = MerchantScreen.Home(merchant) },
                onLogout = { NetworkClient.currentTokenStore().clearSession(); screen = MerchantScreen.Login },
            )
        }
        is MerchantScreen.Home -> MerchantHomeScreen(
            merchant = current.merchant,
            onLogout = { screen = MerchantScreen.Login },
        )
    }
}
