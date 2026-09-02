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

// Real driver earnings / rider trip-history summary -- mirrors ride-hailing's own
// "This week" earnings card in shape, but moto-fare's /earnings and /trips endpoints
// both return a flat trip list, not day-bucketed totals, so the aggregate is summed
// client-side over whatever page is fetched. Shared between both roles
// (uncalled-endpoint sweep, 2026-09-02: the rider side of this same real component
// was never built, only the driver side was, since the driver's own
// uncalled-endpoint fix on 2026-08-29).
@Composable
private fun MotoFareTripSummary(trips: List<MotoFareTripDto>, totalElements: Long, isDriver: Boolean) {
    val totalFare = trips.sumOf { it.fare.toDouble() }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(if (isDriver) "Your fares" else "Your trips", fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(if (isDriver) "Fares collected" else "Trips paid", fontSize = 11.sp, color = Ids.colors.textSecondary)
                Text("$totalElements", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Column {
                Text("Total (last ${trips.size})", fontSize = 11.sp, color = Ids.colors.textSecondary)
                Text("${formatMoneyMotoFare(totalFare)} RWF", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = if (isDriver) Ids.colors.success else Ids.colors.textPrimary)
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        trips.take(5).forEach { trip ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(trip.createdAt, fontSize = 12.sp, color = Ids.colors.textSecondary)
                Text("${formatMoneyMotoFare(trip.fare)} RWF", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// Real rider-side trip history (uncalled-endpoint sweep, 2026-09-02) -- see
// getMyMotoFareTripsAsRider's own doc comment on ApiService.kt. A rider only ever
// pays by showing their existing payment code to a driver, so unlike the driver
// there's no scan/collect action here -- just their own past trips.
@Composable
private fun MotoFareRiderTripsContent() {
    var trips by remember { mutableStateOf<List<MotoFareTripDto>?>(null) }
    var totalElements by remember { mutableStateOf(0L) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        coroutineScope.launch {
            try {
                val result = NetworkClient.apiService.getMyMotoFareTripsAsRider()
                trips = result.trips
                totalElements = result.totalElements
            } catch (_: Exception) {
                trips = emptyList()
            }
        }
    }

    when {
        trips == null -> Text("Loading…", fontSize = 13.sp, color = Ids.colors.textSecondary)
        trips!!.isEmpty() -> Text(
            "No moto-taxi trips yet -- show your payment code to a driver next time you tap to pay.",
            fontSize = 13.sp, color = Ids.colors.textSecondary,
        )
        else -> MotoFareTripSummary(trips = trips!!, totalElements = totalElements, isDriver = false)
    }
}

@Composable
private fun MotoFareDriverCollectContent() {
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

    when {
        collected != null -> {
            val result = collected!!
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Collected", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Ids.colors.success)
                Text("${formatMoneyMotoFare(result.fare)} RWF", fontSize = 16.sp)
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
                    MotoFareTripSummary(trips = trips, totalElements = earningsTotal, isDriver = true)
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
                    Text("${formatMoneyMotoFare(fare)} RWF", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .background(Ids.colors.brand).pressScaleClickable(enabled = !busy, onClick = ::collect)
                        .padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(if (busy) "Collecting…" else "Collect ${formatMoneyMotoFare(fare)} RWF", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// Real rider/driver sub-tab toggle (uncalled-endpoint sweep, 2026-09-02), mirroring
// RidesView/DesignatedDriverView's own "customer side vs. provider side" pattern --
// most people opening this screen are riders checking their own trips, not drivers
// about to scan a code, so "My trips" is the default, not "Collect".
@Composable
fun MotoFareCollectScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var isDriverTab by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        BackTopBar(title = "Moto fare", onBack = onBack)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = Ids.layout.screenHorizontal, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Ids.colors.surfaceSoft).padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    listOf(false to "My trips", true to "Collect").forEach { (tab, label) ->
                        Row(
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                                .background(if (isDriverTab == tab) Ids.colors.brand else Color.Transparent)
                                .pressScaleClickable(onClick = { isDriverTab = tab }).padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (isDriverTab == tab) Color.White else Ids.colors.textSecondary)
                        }
                    }
                }
            }
            item {
                if (isDriverTab) MotoFareDriverCollectContent() else MotoFareRiderTripsContent()
            }
        }
    }
}

// Real gap found 2026-08-30 (project_itunda_money_formatting_sweep's own standing
// convention -- comma thousands-separator for every whole-number RWF amount --
// never reached this file). Same shape BikeRentalScreen.kt/BusScreen.kt already use.
private fun formatMoneyMotoFare(value: Number): String = "%,d".format(value.toLong())
