package rw.itunda.app.ui

import rw.itunda.core.designsystem.components.BackTopBar
import rw.itunda.core.designsystem.components.SkeletonBlock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import rw.itunda.core.network.KycSubmissionDto
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.designsystem.components.EmptyState
import rw.itunda.core.network.SubmitIdentityRequest

// Real KYC identity submission (2026-07-22) -- found fully built on the backend
// (rw.itunda.identity) with zero client UI anywhere. documentReference is a real,
// honest demo-mode stand-in for an uploaded ID scan/selfie -- this backend has no
// file-storage layer, so it's a free-text reference, not an actual image upload; see
// KycSubmission.kt's own doc comment. This screen only offers NATIONAL_ID/PASSPORT
// (personal KYC) -- BUSINESS_TIN/KYB is a separate merchant-onboarding concern.
private val DOCUMENT_TYPES = listOf("NATIONAL_ID", "PASSPORT")

@Composable
fun IdentityScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var submissions by remember { mutableStateOf<List<KycSubmissionDto>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var documentType by remember { mutableStateOf(DOCUMENT_TYPES.first()) }
    var documentNumber by remember { mutableStateOf("") }
    var documentReference by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    suspend fun refresh() {
        try {
            submissions = NetworkClient.apiService.getIdentityStatus().submissions
            error = null
        } catch (_: Exception) {
            error = "Could not load your identity status."
        }
    }
    LaunchedEffect(Unit) { refresh() }

    val hasPending = submissions?.any { it.status == "PENDING" } == true

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(20.dp)) {
            BackTopBar(title = "Verify your identity", onBack = onBack)
        }
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            item {
                if (hasPending) {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Submission pending review", style = MaterialTheme.typography.titleMedium)
                            Text("We'll update your status once it's reviewed.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                } else {
                    Column {
                        Text("Document type", style = MaterialTheme.typography.labelMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DOCUMENT_TYPES.forEach { type ->
                                Button(onClick = { documentType = type }) {
                                    Text(if (documentType == type) "✓ $type" else type)
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            documentNumber, { documentNumber = it },
                            label = { Text("Document number") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            documentReference, { documentReference = it },
                            label = { Text("Document reference (scan/photo reference)") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            enabled = !busy && documentNumber.isNotBlank() && documentReference.length >= 3,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                busy = true
                                scope.launch {
                                    try {
                                        NetworkClient.apiService.submitIdentity(
                                            SubmitIdentityRequest(documentType, documentNumber, documentReference),
                                        )
                                        documentNumber = ""; documentReference = ""
                                        refresh()
                                    } catch (_: Exception) {
                                        error = "That submission could not be completed."
                                    } finally { busy = false }
                                }
                            },
                        ) { Text(if (busy) "Submitting…" else "Submit for review") }
                    }
                }
            }
            item { Text("Your submissions", style = MaterialTheme.typography.titleMedium) }
            val current = submissions
            if (current == null) item { SkeletonBlock() }
            else if (current.isEmpty()) item { EmptyState("No submissions yet.") }
            else items(current, key = { it.id }) { submission -> SubmissionCard(submission) }
        }
    }
}

@Composable
private fun SubmissionCard(submission: KycSubmissionDto) = Card(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp)) {
        Text("${submission.documentType} · ${submission.documentNumber}", style = MaterialTheme.typography.titleMedium)
        Text("Status: ${submission.status}", style = MaterialTheme.typography.bodyMedium)
        submission.decisionReason?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        Text("Submitted: ${submission.submittedAt}", style = MaterialTheme.typography.bodySmall)
    }
}
