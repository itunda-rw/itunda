package rw.itunda.app.ui

import rw.itunda.core.designsystem.components.BackTopBar
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
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
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            score = NetworkClient.apiService.getCreditScore()
        } catch (_: Exception) {
            error = "Could not load your credit score."
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(20.dp)) {
            BackTopBar(title = "Credit score", onBack = onBack)
        }
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            val current = score
            if (current == null) {
                if (error == null) item { Text("Loading…") }
            } else {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Your score", style = MaterialTheme.typography.labelMedium)
                            Text("${current.score} / 850", style = MaterialTheme.typography.headlineMedium)
                            Text(
                                "Based on your own account activity, not a bureau report.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
                item { Text("What makes up your score", style = MaterialTheme.typography.titleMedium) }
                items(current.factors, key = { it.name }) { factor ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text(factor.name, style = MaterialTheme.typography.bodyLarge)
                                Text(factor.description, style = MaterialTheme.typography.bodySmall)
                            }
                            Text("+${factor.points}", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }
}
