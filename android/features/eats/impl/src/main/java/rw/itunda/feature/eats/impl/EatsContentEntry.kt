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

// Eats ordering owns the v1038 browse header; the optional rider fallback remains available.

@Composable
fun EatsContent(
    deviceStepUpHost: @Composable (visible: Boolean, onDismiss: () -> Unit, onVerified: suspend () -> Unit) -> Unit,
    pendingMerchantId: String? = null,
    pendingMerchantName: String? = null,
    onPendingMerchantConsumed: () -> Unit = {},
) {
    var mode by remember { mutableStateOf(EatsMode.ORDER) }
    LaunchedEffect(pendingMerchantId) {
        if (pendingMerchantId != null) mode = EatsMode.ORDER
    }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Ids.layout.screenHorizontal).padding(top = 8.dp),
            horizontalArrangement = Arrangement.End,
        ) {
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
            EatsMode.DELIVER -> Column(Modifier.fillMaxSize().padding(horizontal = Ids.layout.screenHorizontal)) {
                DeliverContent()
            }
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
