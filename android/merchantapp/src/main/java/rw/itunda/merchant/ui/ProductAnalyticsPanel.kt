package rw.itunda.merchant.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
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
import rw.itunda.core.designsystem.components.SkeletonBlock
import rw.itunda.merchant.network.NetworkClient
import rw.itunda.merchant.network.ProductAnalyticsResponse

/**
 * Real Coupang WING 상품분석 (product analytics) -- ported from merchant-mfe
 * (2026-09-03), see MerchantProductService.getProductAnalytics's own doc comment.
 * Extracted into its own file rather than growing CatalogScreen.kt's own ProductRow,
 * which had only ~9 lines of headroom left under its file-size-lint threshold.
 */
@Composable
internal fun ProductAnalyticsPanel(productId: String) {
    var analytics by remember(productId) { mutableStateOf<ProductAnalyticsResponse?>(null) }
    var error by remember(productId) { mutableStateOf<String?>(null) }

    LaunchedEffect(productId) {
        try {
            analytics = NetworkClient.apiService.getProductAnalytics(productId)
        } catch (e: retrofit2.HttpException) {
            error = rw.itunda.merchant.network.apiErrorMessage(e) ?: "Couldn't load analytics."
        } catch (e: Exception) {
            error = "Couldn't load analytics."
        }
    }

    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        val current = analytics
        when {
            error != null -> Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            current == null -> SkeletonBlock()
            else -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                Column {
                    Text("${current.viewCount}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text("Views", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column {
                    Text("${current.orderCount}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text("Orders", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
