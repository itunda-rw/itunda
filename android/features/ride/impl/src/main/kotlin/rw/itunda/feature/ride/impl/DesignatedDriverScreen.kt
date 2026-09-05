package rw.itunda.feature.ride.impl

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import rw.itunda.core.designsystem.components.pressScaleClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.BackTopBar
import rw.itunda.core.designsystem.theme.Ids

// Real Kakao T 대리운전 (designated driver, item 221) -- a professional driver comes to
// the customer's location and drives the CUSTOMER'S OWN CAR home for them, distinct
// from RideScreen.kt's ride-hailing (driver uses their own vehicle). bank-mfe already
// has this; this is the first Android client, mirroring its Get-a-driver/Drive toggle
// exactly. Same honest v1 scope-down RideScreen.kt already established for this app:
// manual dropoff address/lat/lng entry (no autocomplete search integration), one-tap
// "Use my location" for pickup via the shared rememberRealLocationRequester helper.
//
// Split into DesignatedDriverRequestScreen.kt/DesignatedDriverDriveScreen.kt
// (2026-09-05) once the module extraction pushed this file back over the
// file-size-lint threshold -- same shape as RideScreen.kt's own split.
private enum class DesignatedDriverTab { REQUEST, DRIVE }

@Composable
fun DesignatedDriverScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var tab by remember { mutableStateOf(DesignatedDriverTab.REQUEST) }

    Column(modifier = Modifier.fillMaxSize()) {
        BackTopBar(title = "Designated driver", onBack = onBack)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Ids.layout.screenHorizontal, vertical = 8.dp)
                .clip(RoundedCornerShape(10.dp)).background(Ids.colors.surfaceSoft).padding(4.dp),
        ) {
            listOf(DesignatedDriverTab.REQUEST to "Get a driver", DesignatedDriverTab.DRIVE to "Drive").forEach { (value, label) ->
                val selected = tab == value
                Box(
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                        .background(if (selected) Ids.colors.brand else Color.Transparent)
                        .pressScaleClickable { tab = value }.padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, color = if (selected) Color.White else Ids.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (tab == DesignatedDriverTab.REQUEST) DesignatedDriverRequestContent() else DesignatedDriverDriveContent()
    }
}
