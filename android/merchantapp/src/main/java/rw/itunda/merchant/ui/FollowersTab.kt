package rw.itunda.merchant.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import rw.itunda.core.designsystem.components.IdsButton
import rw.itunda.core.designsystem.components.IdsTextField
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
import rw.itunda.merchant.network.BroadcastToFollowersRequest
import rw.itunda.merchant.network.NetworkClient

// Split out of MerchantHomeScreen.kt (2026-08-30, same file-size-lint "extract instead
// of growing a baselined file" discipline used repeatedly this session) -- that file
// was already at its exact recorded baseline with zero headroom, needed to make real
// room for UpdatesTab/PhotosTab (see those files' own doc comments). Original
// broadcast-to-followers feature (item 118) unchanged.
@Composable
internal fun FollowersTab() {
    var count by remember { mutableStateOf<Int?>(null) }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var sentCount by remember { mutableStateOf<Int?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        try {
            count = NetworkClient.apiService.getFollowerCount().count
        } catch (e: Exception) {
            count = 0
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Followers", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            if (count == null) "Loading…" else "${count} customer${if (count == 1) "" else "s"} following your store",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        IdsTextField(value = title, onValueChange = { title = it }, label = "Title", modifier = Modifier.fillMaxWidth())
        IdsTextField(value = body, onValueChange = { body = it }, label = "Tell your followers what's new.", modifier = Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        val hasFollowers = (count ?: 0) > 0
        IdsButton(
            text = if (sending) "Sending…" else "Broadcast to followers",
            enabled = !sending && hasFollowers && title.isNotBlank() && body.isNotBlank(),
            onClick = {
                sending = true
                error = null
                sentCount = null
                scope.launch {
                    try {
                        val recipients = NetworkClient.apiService.broadcastToFollowers(
                            BroadcastToFollowersRequest(title.trim(), body.trim()),
                        ).recipientCount
                        sentCount = recipients
                        title = ""
                        body = ""
                    } catch (e: retrofit2.HttpException) {
                        error = rw.itunda.merchant.network.apiErrorMessage(e) ?: "Could not send this broadcast."
                    } catch (e: Exception) {
                        error = "Could not send this broadcast."
                    } finally {
                        sending = false
                    }
                }
            },
        )
        if (!hasFollowers) {
            Text(
                "You need at least one follower to send a broadcast.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        sentCount?.let {
            Text(
                "Sent to $it follower${if (it == 1) "" else "s"}.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
