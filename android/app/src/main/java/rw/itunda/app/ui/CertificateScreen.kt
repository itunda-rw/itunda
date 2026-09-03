package rw.itunda.app.ui

import rw.itunda.core.designsystem.components.BackTopBar
import rw.itunda.core.designsystem.components.SkeletonBlock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import rw.itunda.core.designsystem.components.IdsButton
import rw.itunda.core.designsystem.components.IdsButtonVariant
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import rw.itunda.core.designsystem.components.IdsTextField
import rw.itunda.core.designsystem.theme.Ids
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
import rw.itunda.core.network.superAppErrorMessage
import rw.itunda.core.network.CertificateDto
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.VerifyCertificateSignatureRequest
import rw.itunda.core.network.VerifyCertificateSignatureResponse
import rw.itunda.core.network.isKycRequiredError
import java.io.IOException

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
    var holderName by remember { mutableStateOf<String?>(null) }
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
    LaunchedEffect(Unit) {
        load()
        // Real itunda-branded "Verified ID" credential card (2026-08-30) -- see
        // ItundaCertificateCard's own doc comment. Best-effort: a name-load failure
        // just falls back to a generic label, never blocks the certificate itself.
        holderName = try {
            NetworkClient.authApi.getProfile().user.let { "${it.firstName} ${it.lastName}" }
        } catch (_: Exception) {
            null
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(20.dp)) {
            BackTopBar(title = "Itunda Certificate", onBack = onBack)
        }
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            if (!loaded) {
                SkeletonBlock()
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
                            ItundaCertificateCard(certificate = current, holderName = holderName)
                            Spacer(Modifier.height(12.dp))
                            IdsButton(
                                text = if (busy) "Revoking…" else "Revoke certificate",
                                enabled = !busy,
                                variant = IdsButtonVariant.Tinted,
                                onClick = {
                                    busy = true
                                    scope.launch {
                                        try {
                                            certificate = NetworkClient.apiService.revokeCertificate().certificate
                                            issuedPrivateKey = null
                                            error = null
                                        } catch (e: HttpException) {
                                            error = superAppErrorMessage(e)
                                        } catch (e: IOException) {
                                            error = "Couldn't reach itunda. Check your connection and try again."
                                        } finally { busy = false }
                                    }
                                },
                            )
                        } else {
                            if (current != null) {
                                Text(
                                    "Your previous certificate was ${current.status.lowercase()}.",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                            IdsButton(
                                text = if (busy) "Issuing…" else "Issue a certificate",
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
                                                superAppErrorMessage(e)
                                            }
                                        } catch (e: IOException) {
                                            error = "Couldn't reach itunda. Check your connection and try again."
                                        } finally { busy = false }
                                    }
                                },
                            )
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
                Spacer(Modifier.height(20.dp))
                VerifyCertificateCard()
            }
        }
    }
}

// Real itunda-branded "Verified ID" credential card (2026-08-30, market-readiness
// audit) -- the visual touchpoint real Toss 인증서/Apple Wallet/Google Wallet all give
// a digital certificate that this screen never had (previously just a bare
// monospace serial number + text status, no card at all -- see bank-mfe's own
// identical CertificateView.tsx doc comment for the mirrored web version). itunda's
// own card design (indigo brand gradient, itunda's own wordmark), never a replica of
// any government-issued ID -- shows only data the user already gave itunda (their
// name) plus certificate metadata itunda itself generated.
@Composable
private fun ItundaCertificateCard(certificate: CertificateDto, holderName: String?) {
    val isActive = certificate.status == "ACTIVE"
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
            .background(
                androidx.compose.ui.graphics.Brush.linearGradient(
                    if (isActive) {
                        listOf(Ids.colors.brand, androidx.compose.ui.graphics.Color(0xFF5C55D8))
                    } else {
                        listOf(androidx.compose.ui.graphics.Color(0xFF9A9AA5), androidx.compose.ui.graphics.Color(0xFF6E6E78))
                    },
                ),
            )
            .padding(22.dp),
    ) {
        Column {
            Row(verticalAlignment = androidx.compose.ui.Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text("itunda", color = androidx.compose.ui.graphics.Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.Black, fontSize = 15.sp)
                    Text("Verified Certificate", color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f), fontSize = 11.sp)
                }
                androidx.compose.material3.Icon(rw.itunda.core.designsystem.theme.IdsIcons.ShieldCheck, contentDescription = null, tint = androidx.compose.ui.graphics.Color.White)
            }
            Spacer(Modifier.height(28.dp))
            Text(holderName ?: "itunda user", color = androidx.compose.ui.graphics.Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween) {
                Column {
                    Text("SERIAL", color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.75f), fontSize = 11.sp)
                    Text(
                        "${certificate.serialNumber.take(4)} •••• ${certificate.serialNumber.takeLast(4)}",
                        color = androidx.compose.ui.graphics.Color.White, fontSize = 12.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    )
                }
                Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                    Text(if (isActive) "VALID THRU" else certificate.status, color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.75f), fontSize = 11.sp)
                    if (isActive) {
                        Text(certificate.expiresAt.take(7), color = androidx.compose.ui.graphics.Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

// Real public certificate status/verify (2026-08-04) -- see ApiService.kt's own doc
// comment on getCertificateStatus/verifyCertificateSignature: the two endpoints that
// answer "does this signed thing check out," found via a fresh backend-endpoint
// sweep with zero client anywhere. Deliberately separate from the card above -- that
// one manages the caller's own certificate; this one checks someone else's.
@Composable
private fun VerifyCertificateCard() {
    var serialNumber by remember { mutableStateOf("") }
    var payload by remember { mutableStateOf("") }
    var signature by remember { mutableStateOf("") }
    var statusResult by remember { mutableStateOf<CertificateDto?>(null) }
    var verifyResult by remember { mutableStateOf<VerifyCertificateSignatureResponse?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Verify a certificate", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                "Check whether a certificate serial number is still active, or verify a document someone signed with theirs.",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(12.dp))
            IdsTextField(value = serialNumber, onValueChange = { serialNumber = it; statusResult = null; verifyResult = null; error = null }, label = "Serial number", modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            IdsButton(
                text = if (busy) "Checking…" else "Check status",
                enabled = !busy && serialNumber.isNotBlank(),
                onClick = {
                    busy = true
                    scope.launch {
                        try {
                            statusResult = NetworkClient.apiService.getCertificateStatus(serialNumber).certificate
                            verifyResult = null
                            error = null
                        } catch (_: Exception) {
                            statusResult = null
                            error = "No certificate found with that serial number."
                        } finally { busy = false }
                    }
                },
            )
            statusResult?.let { cert ->
                Spacer(Modifier.height(8.dp))
                Text("Status: ${cert.status}", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                Text("Expires ${cert.expiresAt}", style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(16.dp))
            Text("Verify a signature", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            IdsTextField(value = payload, onValueChange = { payload = it; verifyResult = null }, label = "Payload (the exact text they signed)", modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            IdsTextField(value = signature, onValueChange = { signature = it; verifyResult = null }, label = "Signature (base64)", modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            IdsButton(
                text = if (busy) "Verifying…" else "Verify signature",
                enabled = !busy && serialNumber.isNotBlank() && payload.isNotBlank() && signature.isNotBlank(),
                onClick = {
                    busy = true
                    scope.launch {
                        try {
                            verifyResult = NetworkClient.apiService.verifyCertificateSignature(
                                VerifyCertificateSignatureRequest(serialNumber, payload, signature),
                            )
                            error = null
                        } catch (e: HttpException) {
                            verifyResult = null
                            error = superAppErrorMessage(e)
                        } catch (e: IOException) {
                            verifyResult = null
                            error = "Couldn't reach itunda. Check your connection and try again."
                        } finally { busy = false }
                    }
                },
            )
            verifyResult?.let { result ->
                Spacer(Modifier.height(8.dp))
                Text(
                    if (result.signatureValid) "✓ Signature is valid" else "✗ Signature does not match",
                    color = if (result.signatureValid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                )
                Text("Certificate status: ${result.certificateStatus}", style = MaterialTheme.typography.bodySmall)
            }
            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
