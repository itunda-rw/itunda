package rw.itunda.merchant.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import rw.itunda.core.designsystem.components.EmptyState
import rw.itunda.core.designsystem.components.IdsButton
import rw.itunda.core.designsystem.components.IdsButtonSize
import rw.itunda.core.designsystem.components.IdsButtonVariant
import rw.itunda.core.designsystem.components.IdsTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import rw.itunda.merchant.network.MerchantUpdateDto
import rw.itunda.merchant.network.NetworkClient
import rw.itunda.merchant.network.PostMerchantUpdateRequest

// Real gap found live (uncalled-endpoint sweep, 2026-08-29/30): MerchantUpdateController
// has been fully built on the backend since the itunda Maps redesign (2026-08-28), and
// merchant-mfe already has this real UI (screens/UpdatesScreen.tsx) -- this is the
// native-merchantapp port, same real create+list feature, periodStart/periodEnd still
// deliberately omitted (both genuinely optional/independent per the backend entity's
// own doc comment).
private val LABELS = listOf("NOTICE", "EVENT", "PROMO")

@Composable
internal fun UpdatesTab(merchantId: String) {
    var updates by remember { mutableStateOf<List<MerchantUpdateDto>?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var label by remember { mutableStateOf("NOTICE") }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var posting by remember { mutableStateOf(false) }
    var postError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun load() {
        scope.launch {
            try {
                updates = NetworkClient.apiService.getMerchantUpdates(merchantId).updates
                loadError = null
            } catch (e: Exception) {
                loadError = "Could not load your updates."
            }
        }
    }
    LaunchedEffect(Unit) { load() }

    fun post() {
        if (title.isBlank() || body.isBlank()) return
        posting = true
        postError = null
        scope.launch {
            try {
                NetworkClient.apiService.postMerchantUpdate(PostMerchantUpdateRequest(label, title.trim(), body.trim()))
                title = ""
                body = ""
                label = "NOTICE"
                load()
            } catch (e: retrofit2.HttpException) {
                postError = rw.itunda.merchant.network.apiErrorMessage(e) ?: "Could not post your update."
            } catch (e: Exception) {
                postError = "Could not post your update."
            } finally {
                posting = false
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Business updates", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            "Post a notice, event, or promo — customers see this on your Maps page.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        postError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LABELS.forEach { l ->
                IdsButton(
                    text = l.lowercase().replaceFirstChar { it.uppercase() },
                    onClick = { label = l },
                    variant = if (label == l) IdsButtonVariant.Filled else IdsButtonVariant.Tinted,
                    size = IdsButtonSize.Small,
                )
            }
        }
        IdsTextField(value = title, onValueChange = { title = it }, label = "Title", modifier = Modifier.fillMaxWidth())
        IdsTextField(value = body, onValueChange = { body = it }, label = "What's the update?", modifier = Modifier.fillMaxWidth())
        IdsButton(
            text = if (posting) "Posting…" else "Post update",
            onClick = ::post,
            enabled = !posting && title.isNotBlank() && body.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        )

        when {
            loadError != null -> Text(loadError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            updates == null -> CircularProgressIndicator()
            updates!!.isEmpty() -> EmptyState(message = "No updates yet — post one and it shows up on your Maps page right away.")
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(updates!!) { update ->
                    Column {
                        Text(update.label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text(update.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text(update.body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "${update.likeCount} likes",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
