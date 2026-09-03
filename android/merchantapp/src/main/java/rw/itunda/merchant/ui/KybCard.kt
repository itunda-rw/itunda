package rw.itunda.merchant.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import rw.itunda.core.designsystem.components.IdsButton
import rw.itunda.core.designsystem.components.IdsTextField
import rw.itunda.core.designsystem.components.StatusBadge
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.merchant.network.IdentitySubmissionDto
import rw.itunda.merchant.network.MerchantDto
import rw.itunda.merchant.network.NetworkClient
import rw.itunda.merchant.network.SubmitIdentityRequest

private val AUTO_CHECK_LABELS = mapOf(
    "MATCHED" to "Matched a real registered business TIN.",
    "NOT_FOUND" to "This TIN wasn't found -- a real reviewer will take a closer look.",
    "INVALID_FORMAT" to "This doesn't look like a real 9-digit TIN -- a real reviewer will take a closer look.",
)

/**
 * Real demo KYB structural pre-check -- ported from merchant-mfe's KybCard
 * (2026-09-03), see DemoKybVerificationService.kt's own doc comment on the backend.
 * Not a real RDB/RRA registry lookup, but a real 9-digit-TIN structural validator
 * plus a real human-review queue (the same ops-mfe Compliance queue personal KYC
 * already uses), never auto-decided.
 */
@Composable
internal fun KybCard(merchant: MerchantDto) {
    var submissions by remember { mutableStateOf<List<IdentitySubmissionDto>?>(null) }
    var tin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        submissions = try { NetworkClient.apiService.getIdentityStatus().submissions } catch (_: Exception) { emptyList() }
    }

    val latestKyb = submissions?.filter { it.documentType == "BUSINESS_TIN" }?.maxByOrNull { it.submittedAt }
    val pending = latestKyb?.status == "PENDING"

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.VerifiedUser, contentDescription = null, tint = if (merchant.kybVerified) Ids.colors.brand else Ids.colors.textTertiary)
                Text("Business verification", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }
            when {
                merchant.kybVerified -> StatusBadge("Verified")
                pending -> {
                    Text("Your submission is under review.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    latestKyb?.autoVerificationStatus?.let { status ->
                        Text(AUTO_CHECK_LABELS[status] ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                else -> {
                    IdsTextField(value = tin, onValueChange = { tin = it }, label = "Business TIN (9 digits)", modifier = Modifier.fillMaxWidth())
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    IdsButton(
                        text = if (submitting) "Submitting…" else "Submit for verification",
                        enabled = !submitting && tin.trim().length == 9,
                        onClick = {
                            submitting = true
                            error = null
                            scope.launch {
                                try {
                                    val trimmed = tin.trim()
                                    NetworkClient.apiService.submitIdentity(SubmitIdentityRequest("BUSINESS_TIN", trimmed, "TIN $trimmed"))
                                    tin = ""
                                    submissions = NetworkClient.apiService.getIdentityStatus().submissions
                                } catch (e: Exception) {
                                    error = "Could not submit. Try again."
                                } finally {
                                    submitting = false
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}
