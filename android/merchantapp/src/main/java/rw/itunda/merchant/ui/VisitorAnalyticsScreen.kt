package rw.itunda.merchant.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.merchant.network.MerchantProfileViewDto
import rw.itunda.merchant.network.NetworkClient
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// Real 비즈프로필 (Karrot Business Profile) visitor-count trend (itunda Hood redesign,
// 2026-08-28) -- see backend MerchantProfileView.kt's own doc comment. Every real
// consumer open of this merchant's place-detail (already built this session for Maps)
// counts as one real visit; this is the owner-facing "방문수 추이" chart reading it.
@Composable
fun VisitorAnalyticsTab() {
    var trend by remember { mutableStateOf<List<MerchantProfileViewDto>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try { trend = NetworkClient.apiService.getProfileViewTrend(7).trend; error = null }
        catch (e: Exception) { error = "Couldn't reach itunda. Check your connection and try again." }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Visitors", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        val days = trend
        if (days == null) {
            Box(modifier = Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }
        val totalVisits = days.sumOf { it.viewCount }
        Text("$totalVisits visits in the last 7 days", color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (days.isEmpty() || totalVisits == 0L) {
            Box(modifier = Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                Text("No real visits yet -- once shoppers open your place on Maps, they'll show up here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Column
        }
        VisitorTrendChart(days)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            days.forEach { d ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(LocalDate.parse(d.viewDate).format(DateTimeFormatter.ofPattern("MMM d")), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${d.viewCount}", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun VisitorTrendChart(days: List<MerchantProfileViewDto>) {
    val maxCount = (days.maxOfOrNull { it.viewCount } ?: 1L).coerceAtLeast(1L)
    val brandColor = Ids.colors.brand
    Canvas(modifier = Modifier.fillMaxWidth().height(120.dp)) {
        if (days.size < 2) return@Canvas
        val stepX = size.width / (days.size - 1)
        val points = days.mapIndexed { i, d ->
            Offset(x = i * stepX, y = size.height - (d.viewCount.toFloat() / maxCount.toFloat()) * size.height)
        }
        for (i in 0 until points.size - 1) {
            drawLine(color = brandColor, start = points[i], end = points[i + 1], strokeWidth = 4f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        }
        points.forEach { p -> drawCircle(color = brandColor, radius = 5f, center = p) }
    }
}
