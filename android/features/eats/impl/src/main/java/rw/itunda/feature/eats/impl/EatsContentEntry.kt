package rw.itunda.feature.eats.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids

// Real fix (2026-08-26): split out of EatsScreen.kt once that file grew past its
// file-size-lint baseline. Eats' own real top-level entry point (Order/Deliver
// mode switch), called cross-module from :app -- unrelated to the specific
// OrderFoodContent screen it dispatches to, which stays behind.

@Composable
fun EatsContent(
    deviceStepUpHost: @Composable (visible: Boolean, onDismiss: () -> Unit, onVerified: suspend () -> Unit) -> Unit,
    // Real "Delivery" pill deep-link from Maps (2026-08-09) -- see
    // ItundaAppScreen.kt's own doc comment on pendingEatsMerchantId for the full
    // account. Forces ORDER mode (not DELIVER) since a pending target is always a real
    // merchant to order FROM, never a rider-role entry point.
    pendingMerchantId: String? = null,
    pendingMerchantName: String? = null,
    onPendingMerchantConsumed: () -> Unit = {},
) {
    var mode by remember { mutableStateOf(EatsMode.ORDER) }
    LaunchedEffect(pendingMerchantId) {
        if (pendingMerchantId != null) mode = EatsMode.ORDER
    }
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = Ids.layout.screenHorizontal)) {
        // Real de-emphasis (2026-07-24) -- "Deliver" (the rider role) previously got
        // equal 50% visual weight next to "Order food" as a full segmented toggle,
        // even though itunda already ships a dedicated, separate riderapp
        // (rw.itunda.rider) for exactly this role. Stacked on top of Shop/Eats' own
        // toggle above and Restaurants/Favorites/My orders below, that read as three
        // full tiers of chrome before any real content -- most people opening Eats
        // are ordering, not delivering. Kept reachable (a rider without the separate
        // app installed can still use it here) as a small secondary link instead of
        // an equal peer tab.
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
            Text(
                text = if (mode == EatsMode.ORDER) "Deliver instead" else "Back to ordering",
                color = Ids.colors.textBrand,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                modifier = Modifier.pressScaleClickable { mode = if (mode == EatsMode.ORDER) EatsMode.DELIVER else EatsMode.ORDER },
            )
        }
        when (mode) {
            EatsMode.ORDER -> OrderFoodContent(
                deviceStepUpHost,
                pendingMerchantId = pendingMerchantId,
                pendingMerchantName = pendingMerchantName,
                onPendingMerchantConsumed = onPendingMerchantConsumed,
            )
            EatsMode.DELIVER -> DeliverContent()
        }
    }
}


/** Feature composition entry point. Keeps the app shell independent of concrete screen names. */
@Composable
fun EatsEntryPoint(
    deviceStepUpHost: @Composable (visible: Boolean, onDismiss: () -> Unit, onVerified: suspend () -> Unit) -> Unit,
    pendingMerchantId: String? = null,
    pendingMerchantName: String? = null,
    onPendingMerchantConsumed: () -> Unit = {},
) {
    EatsContent(deviceStepUpHost, pendingMerchantId, pendingMerchantName, onPendingMerchantConsumed)
}
