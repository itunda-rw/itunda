package rw.itunda.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import java.util.Locale
import kotlinx.coroutines.launch
import retrofit2.HttpException
import rw.itunda.app.nfc.TransitNfcListener
import rw.itunda.core.designsystem.components.BackTopBar
import rw.itunda.core.designsystem.components.CameraQrScanner
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.TapFareByCodeRequest
import rw.itunda.core.network.TransitCollectResultDto
import rw.itunda.core.network.superAppErrorMessage
import java.io.IOException
import java.math.BigDecimal
import java.util.UUID

// Real "agent collects a fare from a rider's presented code" flow (2026-08-27, direct
// user follow-up: "for simplification we need nfc"). Reuses the same
// CustomerPaymentCode every user already generates and shows as a QR/barcode via "My
// payment code" -- see lib backend TransitService.tapFareByCode's own doc comment for
// why this isn't a new token system. Any logged-in user may act as a collector; itunda
// has no real relationship with actual Kigali conductors to gate this against.
//
// Two real transports for the same code: an NFC tap (TransitNfcListener, Android-only
// on both sides -- see its own doc comment for why) is tried first; a rider on iOS (or
// any device without working NFC) falls back to the same CameraQrScanner
// (core/designsystem, already shared with merchant-payment scanning) already used
// elsewhere in this app.
private val TRANSIT_OPERATORS = listOf("Kigali Bus Services", "Royal Express")
private val MIN_FARE = BigDecimal("200")
private val MAX_FARE = BigDecimal("500")

@Composable
fun TransitCollectScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var code by remember { mutableStateOf<String?>(null) }
    var nfcUnavailable by remember { mutableStateOf(false) }
    var operator by remember { mutableStateOf(TRANSIT_OPERATORS[0]) }
    var fare by remember { mutableStateOf(MIN_FARE.toFloat()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var collected by remember { mutableStateOf<TransitCollectResultDto?>(null) }
    val coroutineScope = rememberCoroutineScope()

    fun reset() {
        code = null
        collected = null
        error = null
    }

    fun collect() {
        val c = code ?: return
        busy = true
        error = null
        coroutineScope.launch {
            try {
                collected = NetworkClient.apiService.tapTransitFareByCode(
                    UUID.randomUUID().toString(),
                    TapFareByCodeRequest(c, operator, BigDecimal.valueOf(fare.toLong())),
                ).collected
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                busy = false
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        BackTopBar(title = "Collect fare", onBack = onBack)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = Ids.layout.screenHorizontal, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                when {
                    collected != null -> {
                        val result = collected!!
                        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Collected", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Ids.colors.success)
                            Text("${formatMoneyTransitCollect(result.fare)} RWF · ${result.operator}", fontSize = 16.sp)
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 16.dp).clip(RoundedCornerShape(10.dp))
                                    .background(Ids.colors.brand).pressScaleClickable(onClick = ::reset).padding(vertical = 14.dp),
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                Text("Collect next fare", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    code == null -> {
                        Column {
                            Text("Scan the rider's payment code", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "Ask the rider to open itunda and tap to show their payment code, then hold your phones back-to-back. No NFC? Point your camera at their QR instead.",
                                fontSize = 11.sp, color = Ids.colors.textSecondary,
                            )
                            TransitNfcListener(onCodeRead = { code = it }, onUnavailable = { nfcUnavailable = true })
                            if (nfcUnavailable) {
                                error?.let { Text(it, color = Ids.colors.danger, fontSize = 13.sp) }
                                Column(modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp))) {
                                    CameraQrScanner(onScanned = { code = it }, modifier = Modifier.fillMaxSize())
                                }
                            }
                        }
                    }
                    else -> {
                        Column {
                            Text("Collect fare", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            error?.let { Text(it, color = Ids.colors.danger, fontSize = 13.sp) }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TRANSIT_OPERATORS.forEach { op ->
                                    val isSelected = operator == op
                                    Row(
                                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                                            .background(if (isSelected) Ids.colors.brand else Ids.colors.surfaceSoft)
                                            .pressScaleClickable(onClick = { operator = op }).padding(vertical = 10.dp),
                                        horizontalArrangement = Arrangement.Center,
                                    ) {
                                        Text(op, fontSize = 12.sp, color = if (isSelected) Color.White else Ids.colors.textPrimary)
                                    }
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Slider(
                                    value = fare, onValueChange = { fare = it },
                                    valueRange = MIN_FARE.toFloat()..MAX_FARE.toFloat(), steps = 5,
                                    modifier = Modifier.weight(1f),
                                )
                                Text("${formatMoneyTransitCollect(fare)} RWF", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                    .background(Ids.colors.brand).pressScaleClickable(enabled = !busy, onClick = ::collect)
                                    .padding(vertical = 14.dp),
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                Text(if (busy) "Collecting…" else "Collect ${formatMoneyTransitCollect(fare)} RWF", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

// Real gap found 2026-08-30 (project_itunda_money_formatting_sweep's own standing
// convention -- comma thousands-separator for every whole-number RWF amount --
// never reached this file). Same shape BikeRentalScreen.kt/BusScreen.kt already use.
private fun formatMoneyTransitCollect(value: Number): String = String.format(Locale.US, "%,d", value.toLong())
