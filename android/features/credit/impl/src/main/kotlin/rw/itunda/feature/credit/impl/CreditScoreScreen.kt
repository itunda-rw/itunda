package rw.itunda.feature.credit.impl

import rw.itunda.core.designsystem.components.BackTopBar
import rw.itunda.core.designsystem.components.SkeletonBlock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.CreditScoreResponse
import rw.itunda.core.network.CreditScoreSuggestionDto
import rw.itunda.core.network.NetworkClient

// Real "alternative data" credit score -- computed from a user's own account activity,
// not a bureau report. The score presentation follows the prototype's calm, open layout.
@Composable
fun CreditScoreScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var score by remember { mutableStateOf<CreditScoreResponse?>(null) }
    var suggestions by remember { mutableStateOf<List<CreditScoreSuggestionDto>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            score = NetworkClient.apiService.getCreditScore()
        } catch (_: Exception) {
            error = "Could not load your credit score."
        }
        try {
            suggestions = NetworkClient.apiService.getCreditScoreSuggestions().suggestions
        } catch (_: Exception) {
            suggestions = emptyList()
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(20.dp)) {
            BackTopBar(title = "Credit score", onBack = onBack)
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            val current = score
            if (current == null) {
                if (error == null) item { SkeletonBlock() }
            } else {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("Your score", style = MaterialTheme.typography.labelMedium, color = Ids.colors.textSecondary)
                        CreditScoreGauge(current.score)
                        Text(
                            "Range 300 to 850",
                            style = MaterialTheme.typography.bodySmall,
                            color = Ids.colors.textSecondary,
                        )
                        Text(
                            "Based on your own account activity, not a bureau report.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Ids.colors.textSecondary,
                        )
                    }
                }
                item {
                    Text("What makes up your score", style = MaterialTheme.typography.titleMedium)
                }
                val maxFactorPoints = current.factors.maxOfOrNull { it.points }?.coerceAtLeast(1) ?: 1
                items(current.factors, key = { it.name }) { factor ->
                    FlatBlock {
                        Column(
                            Modifier.fillMaxWidth().padding(vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(factor.name, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        factor.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Ids.colors.textSecondary,
                                    )
                                }
                                Text("+${factor.points}", style = MaterialTheme.typography.bodyLarge)
                            }
                            val fraction = (factor.points.toFloat() / maxFactorPoints.toFloat()).coerceIn(0f, 1f)
                            Box(
                                Modifier.fillMaxWidth().height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Ids.colors.surfaceSoft),
                            ) {
                                Box(
                                    Modifier.fillMaxWidth(fraction).fillMaxHeight()
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(Ids.colors.brand),
                                )
                            }
                        }
                    }
                }
                val suggestionList = suggestions
                if (!suggestionList.isNullOrEmpty()) {
                    item { Text("Ways to raise your score", style = MaterialTheme.typography.titleMedium) }
                    items(suggestionList, key = { it.action }) { suggestion ->
                        FlatBlock {
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(suggestion.action, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        suggestion.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Ids.colors.textSecondary,
                                    )
                                }
                                Text("+${suggestion.pointsGain}", style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CreditScoreGauge(score: Int) {
    val progress = ((score - 300).toFloat() / 550f).coerceIn(0f, 1f)
    Box(
        Modifier.fillMaxWidth().height(116.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(width = 240.dp, height = 112.dp)) {
            val strokeWidth = 14.dp.toPx()
            val arcSize = androidx.compose.ui.geometry.Size(
                width = size.width - strokeWidth,
                height = (size.width - strokeWidth),
            )
            val topLeft = androidx.compose.ui.geometry.Offset(strokeWidth / 2f, 0f)
            drawArc(
                color = Ids.colors.surfaceSoft,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
            drawArc(
                color = Ids.colors.brand,
                startAngle = 180f,
                sweepAngle = 180f * progress,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
        }
        Text(
            text = score.toString(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 0.dp),
            style = MaterialTheme.typography.headlineLarge,
            color = Ids.colors.textPrimary,
        )
    }
}

/** Feature composition entry point. Keeps the app shell independent of concrete screen names. */
@Composable
fun CreditScoreEntryPoint(onBack: () -> Unit) {
    CreditScoreScreen(onBack)
}
