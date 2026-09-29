package rw.itunda.app.ui

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
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.TrustScoreResponse

// Real Karrot-Score-style numeric trust/reputation badge (item 152) -- found
// 2026-07-31 fully built on the backend (rw.itunda.trustscore's TrustScoreController,
// see its own doc comment) and real on bank-mfe (TrustScoreView) with zero native UI
// anywhere. Distinct from the per-listing trustScores batch map already used for
// seller/poster/lister badges on Hood cards -- this is the self-view of a user's own
// full factor breakdown, mirroring CreditScoreScreen's shape exactly.
@Composable
fun TrustScoreScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var score by remember { mutableStateOf<TrustScoreResponse?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            score = NetworkClient.apiService.getTrustScore()
        } catch (_: Exception) {
            error = "Could not load your trust score."
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(20.dp)) {
            BackTopBar(title = "Trust score", onBack = onBack)
        }
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            val current = score
            if (current == null) {
                if (error == null) item { SkeletonBlock() }
            } else {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Your trust score", style = MaterialTheme.typography.labelMedium)
                            Text("${current.score} / 1000", style = MaterialTheme.typography.headlineMedium)
                            Text(
                                "How your neighbors see you on Marketplace, Jobs, and Property.",
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
