package rw.itunda.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import rw.itunda.core.designsystem.components.pressScaleClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import rw.itunda.core.designsystem.components.IdsTextField
import rw.itunda.core.designsystem.components.SkeletonBlock
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import retrofit2.HttpException
import rw.itunda.core.designsystem.components.BackTopBar
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.GenerateP2pRequest
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.P2pPaymentRequestDto
import rw.itunda.core.network.isDeviceNotVerifiedError
import rw.itunda.core.network.superAppErrorMessage
import java.io.IOException
import rw.itunda.core.designsystem.components.EmptyState

// Real fixed-amount person-to-person payment request (item 170) -- the P2P counterpart
// to a merchant's own PaymentIntent (see backend P2pPaymentRequest.kt's own doc
// comment). Real (rate-limited, tested, live-verified against a running backend) but
// had zero mobile UI anywhere until now -- bank-mfe got its own client the same
// session. Same no-ViewModel, NetworkClient-direct shape as ForeignCurrencyScreen.kt/
// UpfrontDepositScreen.kt. `payRequest` carries a real Idempotency-Key and can real-403
// DEVICE_NOT_VERIFIED like every other money-moving endpoint -- originally surfaced as
// an honest text message (wiring a full step-up dialog into a screen outside
// MainViewModel's own MoneyActionResult state machine was named as a real, separate
// follow-up); closed 2026-07-29 (item 180) using DeviceStepUpHost.kt directly, since it
// already lives in this same :app module and needs no cross-module injection.
@Composable
fun RequestMoneyScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var requests by remember { mutableStateOf<List<P2pPaymentRequestDto>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableStateOf(0) }
    var created by remember { mutableStateOf<P2pPaymentRequestDto?>(null) }
    val coroutineScope = rememberCoroutineScope()

    fun load() {
        coroutineScope.launch {
            try {
                val res = NetworkClient.apiService.getMyP2pRequests()
                if (res.success) requests = res.requests
                error = null
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            }
        }
    }
    LaunchedEffect(refreshKey) { load() }

    Column(modifier = Modifier.fillMaxSize()) {
        BackTopBar(title = "Request money", onBack = onBack)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = Ids.layout.screenHorizontal, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { CreateRequestCard(onCreated = { created = it; refreshKey++ }) }
            created?.let { req ->
                item {
                    // Real fix (flat-design sweep): dropped the Card wrapper.
                    Column {
                            Text("Share this code -- expires in 15 minutes", color = Ids.colors.textSecondary, fontSize = 12.sp)
                            Text(req.id, color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }
            item { PayRequestCard(onPaid = { refreshKey++ }) }
            error?.let { item { Text(it, color = Ids.colors.danger, fontSize = 13.sp) } }
            item { Text("My requests", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp) }
            val list = requests
            if (list == null) {
                item { SkeletonBlock(height = 60.dp) }
            } else if (list.isEmpty()) {
                item { EmptyState("No requests yet — ask someone to pay you above.") }
            } else {
                items(list, key = { it.id }) { req ->
                    // Real fix (flat-design sweep): dropped the per-row Card.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                            Column {
                                Text("${formatMoneyRequest(req.amount.toDouble())} RWF", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                if (req.description.isNotBlank()) Text(req.description, color = Ids.colors.textSecondary, fontSize = 12.sp)
                            }
                            Text(
                                when (req.status) { "COMPLETED" -> "Paid"; "EXPIRED" -> "Expired"; else -> "Pending" },
                                color = if (req.status == "COMPLETED") Ids.colors.success else if (req.status == "EXPIRED") Ids.colors.textSecondary else Ids.colors.brand,
                                fontWeight = FontWeight.Bold, fontSize = 13.sp,
                            )
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateRequestCard(onCreated: (P2pPaymentRequestDto) -> Unit) {
    var amount by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    // Real fix (flat-design sweep): dropped the Card wrapper.
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("New request", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold)
            rw.itunda.core.designsystem.components.AmountKeypadInput(
                digits = amount, onDigitsChange = { amount = it },
                quickAmounts = listOf(10_000L, 100_000L),
            )
            IdsTextField(value = description, onValueChange = { description = it }, label = "What's it for? (optional)", modifier = Modifier.fillMaxWidth())
            error?.let { Text(it, color = Ids.colors.danger, fontSize = 12.sp) }
            Box(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .background(if (submitting) Ids.colors.textTertiary else Ids.colors.brand)
                    .pressScaleClickable(enabled = !submitting) {
                        val amountBd = amount.trim().toBigDecimalOrNull()
                        if (amountBd == null || amountBd <= java.math.BigDecimal.ZERO) {
                            error = "Enter a real amount."
                            return@pressScaleClickable
                        }
                        submitting = true
                        error = null
                        coroutineScope.launch {
                            try {
                                val res = NetworkClient.apiService.generateP2pRequest(GenerateP2pRequest(amountBd, description.trim()))
                                if (res.success) {
                                    onCreated(res.request)
                                    amount = ""
                                    description = ""
                                }
                            } catch (e: HttpException) {
                                error = superAppErrorMessage(e)
                            } catch (e: IOException) {
                                error = "Couldn't reach itunda. Check your connection and try again."
                            } finally {
                                submitting = false
                            }
                        }
                    }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) { Text(if (submitting) "Creating…" else "Create request", color = Color.White, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun PayRequestCard(onPaid: () -> Unit) {
    var code by remember { mutableStateOf("") }
    var paying by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // Real biometric device step-up (item 170's own named follow-up, closed 2026-07-29)
    // -- DeviceStepUpHost.kt lives in this same :app module (unlike the Feature-module
    // screens that need it injected), so it's used directly here rather than the
    // honest-text-message fallback this screen shipped with originally.
    var needsDeviceVerification by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    suspend fun pay() {
        paying = true
        error = null
        try {
            val res = NetworkClient.apiService.payP2pRequest(code.trim(), java.util.UUID.randomUUID().toString())
            if (res.success) {
                code = ""
                onPaid()
            }
        } catch (e: HttpException) {
            if (isDeviceNotVerifiedError(e)) {
                needsDeviceVerification = true
            } else {
                error = superAppErrorMessage(e)
            }
        } catch (e: IOException) {
            error = "Couldn't reach itunda. Check your connection and try again."
        } finally {
            paying = false
        }
    }

    // Real fix (flat-design sweep): dropped the Card wrapper.
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Pay a request", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold)
            IdsTextField(value = code, onValueChange = { code = it }, label = "Request code", modifier = Modifier.fillMaxWidth())
            error?.let { Text(it, color = Ids.colors.danger, fontSize = 12.sp) }
            Box(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .background(if (paying) Ids.colors.textTertiary else Ids.colors.brand)
                    .pressScaleClickable(enabled = !paying && code.isNotBlank()) { coroutineScope.launch { pay() } }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) { Text(if (paying) "Paying…" else "Pay", color = Color.White, fontWeight = FontWeight.Bold) }
    }
    DeviceStepUpHost(
        visible = needsDeviceVerification,
        onDismiss = { needsDeviceVerification = false },
        onVerified = { needsDeviceVerification = false; pay() },
    )
}

private fun formatMoneyRequest(value: Double): String {
    val rounded = Math.round(value * 100.0) / 100.0
    return if (rounded == Math.floor(rounded)) "%,d".format(rounded.toLong()) else "%,.2f".format(rounded)
}
