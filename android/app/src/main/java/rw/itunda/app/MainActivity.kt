package rw.itunda.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import rw.itunda.app.ui.ItundaAppScreen
import rw.itunda.core.risk.RootDetection

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Root/FDS gate on the real app entry point, ported from
        // mobile_clients/android's BankActivity (see ARCHITECTURE.md §3) --
        // money-moving screens should not render on a compromised device.
        if (!RootDetection.verifyDeviceIntegrity()) {
            setContent {
                Text("Itunda can't run on a rooted or compromised device.")
            }
            return
        }

        setContent {
            ItundaAppScreen()
        }
    }
}
