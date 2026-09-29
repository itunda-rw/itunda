package rw.itunda.feature.talk.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import retrofit2.HttpException
import rw.itunda.core.designsystem.components.IdsTextField
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.GroupMessageDto
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.superAppErrorMessage
import java.io.IOException

// Real group-chat message search (Talk product-completeness pass, 2026-09-06) --
// extracted into its own file so TalkGroupThread.kt stays under the file-size-lint
// 500-line guideline; mirrors TalkChatThread.kt's own 1:1 search UI/logic exactly.
@Composable
internal fun GroupSearchBar(
    groupId: String,
    onResultsChange: (List<GroupMessageDto>?) -> Unit,
    onError: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var count by remember { mutableStateOf<Int?>(null) }
    val coroutineScope = rememberCoroutineScope()

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        IdsTextField(
            value = query,
            onValueChange = { query = it; if (it.isBlank()) { count = null; onResultsChange(null) } },
            label = "Search this group",
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = {
            val trimmed = query.trim()
            if (trimmed.length < 2) { onError("Enter at least 2 characters to search."); return@TextButton }
            searching = true
            coroutineScope.launch {
                try {
                    val results = NetworkClient.apiService.searchGroupMessages(groupId, trimmed).messages
                    count = results.size
                    onResultsChange(results)
                } catch (e: HttpException) {
                    onError(superAppErrorMessage(e))
                } catch (_: IOException) {
                    onError("Couldn't search this group. Check your connection and try again.")
                } finally {
                    searching = false
                }
            }
        }, enabled = !searching) { Text(if (searching) "…" else "Search") }
    }
    count?.let { Text("$it matching message${if (it == 1) "" else "s"}", color = Ids.colors.textSecondary, fontSize = 12.sp) }
}
