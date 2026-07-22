package rw.itunda.app.ui

import rw.itunda.core.designsystem.components.BackTopBar
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import retrofit2.HttpException
import rw.itunda.core.network.CertificateDto
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.isKycRequiredError

// Real digital identity/signing certificate (2026-07-22 port) -- this feature was
// already real and live-verified on bank-mfe (web) since 2026-07-17, but a full
// backend-vs-app audit found it completely absent from the Android app: a real
// platform-parity gap, not a never-built feature. Mirrors bank-mfe's own
// CertificateView exactly, including the "shown exactly once" private key handling
// -- see CertificateService.kt's own doc comment on why this backend never persists
// the private key and can never show it again after issuance.
@Composable
fun CertificateScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var certificate by remember { mutableStateOf<CertificateDto?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var issuedPrivateKey by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun load() {
        try {
            certificate = NetworkClient.apiService.getMyCertificate().certificate
            loaded = true
            error = null
        } catch (_: Exception) {
            error = "Could not load your certificate."
            loaded = true
        }
    }
    LaunchedEffect(Unit) { load() }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(20.dp)) {
            BackTopBar(title = "Itunda Certificate", onBack = onBack)
        }
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            if (!loaded) {
                Text("Loading…")
            } else {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "A digital certificate you can use to sign agreements in Itunda. You'll need a verified identity first.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.height(12.dp))
                        val current = certificate
                        if (current != null && current.status == "ACTIVE") {
                            Text("Active", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                            Text("Serial ${current.serialNumber}", style = MaterialTheme.typography.bodySmall)
                            Text("Expires ${current.expiresAt}", style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(12.dp))
                            Button(
                                enabled = !busy,
                                onClick = {
                                    busy = true
                                    scope.launch {
                                        try {
                                            certificate = NetworkClient.apiService.revokeCertificate().certificate
                                            issuedPrivateKey = null
                                            error = null
                                        } catch (_: Exception) {
                                            error = "Could not revoke your certificate."
                                        } finally { busy = false }
                                    }
                                },
                            ) { Text(if (busy) "Revoking…" else "Revoke certificate") }
                        } else {
                            if (current != null) {
                                Text(
                                    "Your previous certificate was ${current.status.lowercase()}.",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                            Button(
                                enabled = !busy,
                                onClick = {
                                    busy = true
                                    scope.launch {
                                        try {
                                            val result = NetworkClient.apiService.issueCertificate()
                                            certificate = result.certificate
                                            issuedPrivateKey = result.privateKey
                                            error = null
                                        } catch (e: HttpException) {
                                            error = if (isKycRequiredError(e)) {
                                                "You need a verified identity before you can issue a certificate."
                                            } else {
                                                "Could not issue a certificate."
                                            }
                                        } catch (_: Exception) {
                                            error = "Could not issue a certificate."
                                        } finally { busy = false }
                                    }
                                },
                            ) { Text(if (busy) "Issuing…" else "Issue a certificate") }
                        }
                    }
                }
                issuedPrivateKey?.let { privateKey ->
                    Spacer(Modifier.height(12.dp))
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                "Save this private key now — you won't be able to see it again.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(privateKey, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                error?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
