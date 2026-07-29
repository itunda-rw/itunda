package rw.itunda.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
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

// Real fixed-amount person-to-person payment request (item 170) -- the P2P counterpart
// to a merchant's own PaymentIntent (see backend P2pPaymentRequest.kt's own doc
// comment). Real (rate-limited, tested, live-verified against a running backend) but
// had zero mobile UI anywhere until now -- bank-mfe got its own client the same
// session. Same no-ViewModel, NetworkClient-direct shape as ForeignCurrencyScreen.kt/
// UpfrontDepositScreen.kt. Honestly scoped: `payRequest` carries a real Idempotency-Key
// and can real-403 DEVICE_NOT_VERIFIED like every other money-moving endpoint, but the
// full biometric device step-up dialog is tightly coupled to MainViewModel's own
// MoneyActionResult state machine (see ItundaAppScreen.kt's sendTransfer call site) --
// wiring that into this standalone screen is a real, separate follow-up; for now a
// DEVICE_NOT_VERIFIED 403 here surfaces the real backend message honestly rather than
// pretending the payment worked or hiding why it didn't.
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
                    Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = TossCard), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Share this code -- expires in 15 minutes", color = TossSecondary, fontSize = 12.sp)
                            Text(req.id, color = TossText, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                }
            }
            item { PayRequestCard(onPaid = { refreshKey++ }) }
            error?.let { item { Text(it, color = Ids.colors.danger, fontSize = 13.sp) } }
            item { Text("My requests", color = TossText, fontWeight = FontWeight.Bold, fontSize = 15.sp) }
            val list = requests
            if (list == null) {
                item { Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), modifier = Modifier.fillMaxWidth().height(60.dp)) {} }
            } else if (list.isEmpty()) {
                item { Text("No requests yet.", color = TossSecondary, fontSize = 13.sp) }
            } else {
                items(list, key = { it.id }) { req ->
                    Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = TossCard), modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text("${formatMoneyRequest(req.amount.toDouble())} RWF", color = TossText, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                if (req.description.isNotBlank()) Text(req.description, color = TossSecondary, fontSize = 12.sp)
                            }
                            Text(
                                when (req.status) { "COMPLETED" -> "Paid"; "EXPIRED" -> "Expired"; else -> "Pending" },
                                color = if (req.status == "COMPLETED") Ids.colors.success else if (req.status == "EXPIRED") TossSecondary else TossBlue,
                                fontWeight = FontWeight.Bold, fontSize = 13.sp,
                            )
                        }
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

    Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = TossCard), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("New request", color = TossText, fontWeight = FontWeight.Bold)
            OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Amount (RWF)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("What's it for? (optional)") }, modifier = Modifier.fillMaxWidth())
            error?.let { Text(it, color = Ids.colors.danger, fontSize = 12.sp) }
            Box(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .background(if (submitting) Ids.colors.textTertiary else TossBlue)
                    .clickable(enabled = !submitting) {
                        val amountBd = amount.trim().toBigDecimalOrNull()
                        if (amountBd == null || amountBd <= java.math.BigDecimal.ZERO) {
                            error = "Enter a real amount."
                            return@clickable
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
}

@Composable
private fun PayRequestCard(onPaid: () -> Unit) {
    var code by remember { mutableStateOf("") }
    var paying by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = TossCard), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Pay a request", color = TossText, fontWeight = FontWeight.Bold)
            OutlinedTextField(value = code, onValueChange = { code = it }, label = { Text("Request code") }, modifier = Modifier.fillMaxWidth())
            error?.let { Text(it, color = Ids.colors.danger, fontSize = 12.sp) }
            Box(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .background(if (paying) Ids.colors.textTertiary else TossBlue)
                    .clickable(enabled = !paying && code.isNotBlank()) {
                        paying = true
                        error = null
                        coroutineScope.launch {
                            try {
                                val res = NetworkClient.apiService.payP2pRequest(code.trim(), java.util.UUID.randomUUID().toString())
                                if (res.success) {
                                    code = ""
                                    onPaid()
                                }
                            } catch (e: HttpException) {
                                error = if (isDeviceNotVerifiedError(e)) {
                                    "This device needs to be verified first -- send a real transfer once to verify it, then try this payment again."
                                } else {
                                    superAppErrorMessage(e)
                                }
                            } catch (e: IOException) {
                                error = "Couldn't reach itunda. Check your connection and try again."
                            } finally {
                                paying = false
                            }
                        }
                    }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) { Text(if (paying) "Paying…" else "Pay", color = Color.White, fontWeight = FontWeight.Bold) }
        }
    }
}

private fun formatMoneyRequest(value: Double): String {
    val rounded = Math.round(value * 100.0) / 100.0
    return if (rounded == Math.floor(rounded)) rounded.toLong().toString() else "%.2f".format(rounded)
}
