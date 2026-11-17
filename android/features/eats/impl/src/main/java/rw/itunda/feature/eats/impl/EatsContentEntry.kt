package rw.itunda.feature.eats.impl

import androidx.compose.runtime.Composable

// Consumer Eats entry point. Rider delivery surfaces are kept in their own app.

@Composable
fun EatsContent(
    deviceStepUpHost: @Composable (visible: Boolean, onDismiss: () -> Unit, onVerified: suspend () -> Unit) -> Unit,
    pendingMerchantId: String? = null,
    pendingMerchantName: String? = null,
    onPendingMerchantConsumed: () -> Unit = {},
    onReportIssue: (String) -> Unit = {},
) {
    OrderFoodContent(
        deviceStepUpHost,
        pendingMerchantId = pendingMerchantId,
        pendingMerchantName = pendingMerchantName,
        onPendingMerchantConsumed = onPendingMerchantConsumed,
        onReportIssue = onReportIssue,
    )
}

/** Feature composition entry point. Keeps the app shell independent of concrete screen names. */
@Composable
fun EatsEntryPoint(
    deviceStepUpHost: @Composable (visible: Boolean, onDismiss: () -> Unit, onVerified: suspend () -> Unit) -> Unit,
    pendingMerchantId: String? = null,
    pendingMerchantName: String? = null,
    onPendingMerchantConsumed: () -> Unit = {},
    onReportIssue: (String) -> Unit = {},
) {
    EatsContent(deviceStepUpHost, pendingMerchantId, pendingMerchantName, onPendingMerchantConsumed, onReportIssue)
}
