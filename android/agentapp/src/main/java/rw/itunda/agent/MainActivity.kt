package rw.itunda.agent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import rw.itunda.agent.network.NetworkClient
import rw.itunda.agent.ui.AgentHomeScreen
import rw.itunda.agent.ui.LoginScreen
import rw.itunda.core.designsystem.theme.IdsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { IdsTheme { Surface(Modifier.fillMaxSize()) { AgentApp() } } }
    }
}

@Composable
private fun AgentApp() {
    var loggedIn by remember { mutableStateOf(NetworkClient.session().hasSession()) }
    if (loggedIn) AgentHomeScreen(onLogout = { NetworkClient.session().clear(); loggedIn = false })
    else LoginScreen(onLoggedIn = { loggedIn = true })
}
