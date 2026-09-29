package rw.itunda.feature.property.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import retrofit2.HttpException
import rw.itunda.core.designsystem.components.IdsTextField
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.NeighborhoodReviewDto
import rw.itunda.core.network.SubmitNeighborhoodReviewRequest
import rw.itunda.core.network.superAppErrorMessage
import java.io.IOException

// Real 살아본 후기 (Karrot "lived here" neighborhood reviews), itunda Hood redesign
// 2026-08-28 -- see backend NeighborhoodReview.kt's own doc comment. Distinct from
// HoodReviewForm (a buyer/seller transaction review) -- this is a public review of an
// area, shown on every property listing in that neighborhood.
@Composable
internal fun NeighborhoodReviewsSection(neighborhood: String) {
    var reviews by remember(neighborhood) { mutableStateOf<List<NeighborhoodReviewDto>?>(null) }
    var showForm by remember { mutableStateOf(false) }
    var body by remember { mutableStateOf("") }
    var years by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    fun load() {
        coroutineScope.launch {
            try { reviews = NetworkClient.hoodApi.getNeighborhoodReviews(neighborhood).reviews } catch (e: Exception) { reviews = emptyList() }
        }
    }
    LaunchedEffect(neighborhood) { load() }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("살아본 후기 · $neighborhood", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        val current = reviews
        if (current == null) {
            Text("Loading…", color = Ids.colors.textSecondary, fontSize = 12.sp)
        } else if (current.isEmpty()) {
            Text("No reviews yet — be the first to share what it's like living here.", color = Ids.colors.textSecondary, fontSize = 12.sp)
        } else {
            current.forEach { r ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    r.residencyYears?.let { Text("$it years living here", color = Ids.colors.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
                    Text(r.body, color = Ids.colors.textPrimary, fontSize = 13.sp)
                }
            }
        }
        if (!showForm) {
            TextButton(onClick = { showForm = true }) { Text("+ Write a review") }
        } else {
            IdsTextField(value = body, onValueChange = { body = it }, label = "What's it like living here?", singleLine = false, modifier = Modifier.fillMaxWidth())
            IdsTextField(value = years, onValueChange = { years = it }, label = "Years living here (optional)", singleLine = true, keyboardType = androidx.compose.ui.text.input.KeyboardType.Number, modifier = Modifier.fillMaxWidth())
            error?.let { Text(it, color = Ids.colors.danger, fontSize = 12.sp) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(onClick = { showForm = false; error = null }) { Text("Cancel") }
                TextButton(
                    enabled = !submitting && body.isNotBlank(),
                    onClick = {
                        submitting = true
                        coroutineScope.launch {
                            try {
                                NetworkClient.hoodApi.submitNeighborhoodReview(neighborhood, SubmitNeighborhoodReviewRequest(years.toIntOrNull(), body.trim()))
                                body = ""; years = ""; showForm = false; error = null
                                load()
                            } catch (e: HttpException) {
                                error = superAppErrorMessage(e)
                            } catch (e: IOException) {
                                error = "Couldn't reach itunda. Check your connection and try again."
                            } finally {
                                submitting = false
                            }
                        }
                    },
                ) { Text(if (submitting) "Saving…" else "Save") }
            }
        }
    }
}
