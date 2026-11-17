package rw.itunda.feature.eats.impl

import androidx.compose.runtime.Composable

// The consumer Eats entry point is the ordering experience. Rider delivery work
// belongs to the dedicated rider app; browse fulfillment is selected in Eats itself.

@Composable
fun EatsContent(
    deviceStepUpHost: @Composable (visible: Boolean, onDismiss: () -> Unit, onVerified: suspend () -> Unit) -> Unit,
    pendingMerchantId: String? = null,
    pendingMerchantName: String? = null,
    onPendingMerchantConsumed: () -> Unit = {},
) {
    OrderFoodContent(
        deviceStepUpHost,
        pendingMerchantId = pendingMerchantId,
        pendingMerchantName = pendingMerchantName,
        onPendingMerchantConsumed = onPendingMerchantConsumed,
    )
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
