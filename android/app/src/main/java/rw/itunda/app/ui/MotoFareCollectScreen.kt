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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.launch
import retrofit2.HttpException
import rw.itunda.app.nfc.TransitNfcListener
import rw.itunda.core.designsystem.components.BackTopBar
import rw.itunda.core.designsystem.components.CameraQrScanner
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.CollectMotoFareRequest
import rw.itunda.core.network.MotoFareCollectResultDto
import rw.itunda.core.network.MotoFareTripDto
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.superAppErrorMessage
import java.io.IOException
import java.math.BigDecimal
import java.util.UUID

// Real "tap to pay your moto-taxi fare" (2026-08-27, direct user follow-up: "now we
// can make pay for tax and moto as well"). Mirrors TransitCollectScreen.kt's shape --
// same real CustomerPaymentCode primitive, same NFC-first/camera-fallback pattern
// (reuses TransitNfcListener/CameraQrScanner as-is: the wire protocol is a bare
// payment-code string, not transit-specific, despite the class name) -- but simpler:
// no operator picker (a Kigali moto-taxi driver is an individual, not a fixed-route
// company), and a different, real sourced fare range (400-6000 RWF vs transit's
// 200-500). See the backend's MotoFareTrip.kt doc comment for the full sourced
// account.
private val MIN_FARE = BigDecimal("400")
private val MAX_FARE = BigDecimal("6000")

// Real driver earnings summary (uncalled-endpoint sweep, 2026-08-29) -- mirrors
// ride-hailing's own "This week" earnings card in shape, but moto-fare's /earnings
// endpoint returns a flat trip list, not day-bucketed totals, so the aggregate is
// summed client-side over whatever page is fetched.
@Composable
private fun MotoFareEarningsSummary(trips: List<MotoFareTripDto>, totalElements: Long) {
    val totalFare = trips.sumOf { it.fare.toDouble() }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Your fares", fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("Fares collected", fontSize = 11.sp, color = Ids.colors.textSecondary)
                Text("$totalElements", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Column {
                Text("Total (last ${trips.size})", fontSize = 11.sp, color = Ids.colors.textSecondary)
                Text("${totalFare.toLong()} RWF", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ids.colors.success)
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        trips.take(5).forEach { trip ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(trip.createdAt, fontSize = 12.sp, color = Ids.colors.textSecondary)
                Text("${trip.fare.toPlainString()} RWF", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun MotoFareCollectScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var code by remember { mutableStateOf<String?>(null) }
    var nfcUnavailable by remember { mutableStateOf(false) }
    var fare by remember { mutableStateOf(MIN_FARE.toFloat()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var collected by remember { mutableStateOf<MotoFareCollectResultDto?>(null) }
    // Real gap found live (uncalled-endpoint sweep, 2026-08-29): this collect flow
    // existed with zero way for a driver to ever see what they'd collected.
    var earnings by remember { mutableStateOf<List<MotoFareTripDto>?>(null) }
    var earningsTotal by remember { mutableStateOf(0L) }
    val coroutineScope = rememberCoroutineScope()

    fun loadEarnings() {
        coroutineScope.launch {
            try {
                val result = NetworkClient.apiService.getMyMotoFareEarnings()
                earnings = result.trips
                earningsTotal = result.totalElements
            } catch (_: Exception) {
                // Non-critical -- the summary just won't render without it.
            }
        }
    }
    LaunchedEffect(Unit) { loadEarnings() }

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
                collected = NetworkClient.apiService.collectMotoFare(
                    UUID.randomUUID().toString(),
                    CollectMotoFareRequest(c, BigDecimal.valueOf(fare.toLong())),
                ).collected
                loadEarnings()
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
        BackTopBar(title = "Collect moto fare", onBack = onBack)
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
                            Text("${result.fare.toPlainString()} RWF", fontSize = 16.sp)
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
                            earnings?.takeIf { it.isNotEmpty() }?.let { trips ->
                                MotoFareEarningsSummary(trips = trips, totalElements = earningsTotal)
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            Text("Scan the rider's payment code", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "Ask the rider to open itunda and tap to show their payment code, then hold your phones back-to-back. No NFC? Point your camera at their QR instead. Fares go straight into your own itunda account -- no fee.",
                                fontSize = 11.sp, color = Ids.colors.textSecondary,
                            )
                            TransitNfcListener(onCodeRead = { code = it }, onUnavailable = { nfcUnavailable = true })
                            if (nfcUnavailable) {
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
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Slider(
                                    value = fare, onValueChange = { fare = it },
                                    valueRange = MIN_FARE.toFloat()..MAX_FARE.toFloat(), steps = 55,
                                    modifier = Modifier.weight(1f),
                                )
                                Text("${fare.toLong()} RWF", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                    .background(Ids.colors.brand).pressScaleClickable(enabled = !busy, onClick = ::collect)
                                    .padding(vertical = 14.dp),
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                Text(if (busy) "Collecting…" else "Collect ${fare.toLong()} RWF", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
