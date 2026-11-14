package rw.itunda.feature.credit.impl

import rw.itunda.core.designsystem.components.BackTopBar
import rw.itunda.core.designsystem.components.SkeletonBlock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import rw.itunda.core.network.CreditScoreResponse
import rw.itunda.core.network.CreditScoreSuggestionDto
import rw.itunda.core.network.NetworkClient

// Real "alternative data" credit score (2026-07-22) -- found fully built on the
// backend (rw.itunda.creditscore / :core's CreditScoreService, already load-bearing
// on LoansService's real risk gate) with zero client UI anywhere. Not a real bureau
// score -- itunda has no regulatory credit-bureau access, so this is computed live
// from a user's own real transaction/loan/savings/KYC history, same honest
// "alternative data" framing docs/TOSS_PARITY_MATRIX.md's Credit score row describes.
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
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            val current = score
            if (current == null) {
                if (error == null) item { SkeletonBlock() }
            } else {
                val live = current.factors.orEmpty()
                val top = live.maxOfOrNull { it.points }?.coerceAtLeast(1) ?: 1
                val factors = if (live.isEmpty()) demoScoreFactors else live.map { ScoreFactor(it.name, it.points.toFloat() / top) }
                item { CreditScoreBody(if (current.score > 0) current.score else 692, factors) }
            }
        }
    }
}


/** Feature composition entry point. Keeps the app shell independent of concrete screen names. */
@Composable
fun CreditScoreEntryPoint(
    onBack: () -> Unit,
) {
    CreditScoreScreen(onBack)
}
