package rw.itunda.feature.eats.impl

import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.theme.PretendardFontFamily

// Consumer Eats entry point. Rider delivery surfaces are kept in their own app.

@Composable
fun EatsContent(
    deviceStepUpHost: @Composable (visible: Boolean, onDismiss: () -> Unit, onVerified: suspend () -> Unit) -> Unit,
    pendingMerchantId: String? = null,
    pendingMerchantName: String? = null,
    onPendingMerchantConsumed: () -> Unit = {},
    onReportIssue: (String, String?) -> Unit = { _, _ -> },
) {
    // Match the v1038 EATS text contract instead of inheriting platform Roboto metrics.
    ProvideTextStyle(
        TextStyle(
            fontFamily = PretendardFontFamily,
            // v1038 body metrics are consistently 1.4 line-height with -0.176px tracking.
            lineHeight = 1.4.em,
            letterSpacing = (-0.176).sp,
            lineHeightStyle = LineHeightStyle(
                LineHeightStyle.Alignment.Center,
                LineHeightStyle.Trim.None,
            ),
            platformStyle = PlatformTextStyle(includeFontPadding = false),
        ),
    ) {
        OrderFoodContent(
            deviceStepUpHost,
            pendingMerchantId = pendingMerchantId,
            pendingMerchantName = pendingMerchantName,
            onPendingMerchantConsumed = onPendingMerchantConsumed,
            onReportIssue = onReportIssue,
        )
    }
}

/** Feature composition entry point. Keeps the app shell independent of concrete screen names. */
@Composable
fun EatsEntryPoint(
    deviceStepUpHost: @Composable (visible: Boolean, onDismiss: () -> Unit, onVerified: suspend () -> Unit) -> Unit,
    pendingMerchantId: String? = null,
    pendingMerchantName: String? = null,
    onPendingMerchantConsumed: () -> Unit = {},
    onReportIssue: (String, String?) -> Unit = { _, _ -> },
) {
    EatsContent(deviceStepUpHost, pendingMerchantId, pendingMerchantName, onPendingMerchantConsumed, onReportIssue)
}
