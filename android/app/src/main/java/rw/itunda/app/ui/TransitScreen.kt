package rw.itunda.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlinx.coroutines.launch
import retrofit2.HttpException
import rw.itunda.app.R
import rw.itunda.core.designsystem.components.BackTopBar
import rw.itunda.core.designsystem.components.EmptyState
import rw.itunda.core.designsystem.components.IdsTextField
import rw.itunda.core.designsystem.components.SkeletonBlock
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.TapFareRequest
import rw.itunda.core.network.TopUpTransitRequest
import rw.itunda.core.network.TransitBalanceDto
import rw.itunda.core.network.TransitTripDto
import rw.itunda.core.network.apiErrorCode
import rw.itunda.core.network.superAppErrorMessage
import java.io.IOException
import java.math.BigDecimal
import java.util.UUID

// Real Kigali public-transit stored-value balance (2026-08-27, direct user follow-up
// after the card-design-picker feature: "after this we will build transit features").
// See the backend's TransitBalance.kt doc comment for the full sourced account of
// Kigali's real Tap&Go fare system (AC Group Ltd, Kigali Bus Services, Royal Express)
// and the honest boundary this simulates -- itunda has no real partnership with any of
// them, so this screen never uses their "Tap&Go" name for itunda's own product. Same
// no-ViewModel, NetworkClient-direct-from-Composable shape as CardScreen.kt.
private enum class TransitMode { LOADING, NO_BALANCE, ACTIVE }

private val TRANSIT_OPERATORS = listOf("Kigali Bus Services", "Royal Express")
private val MIN_FARE = BigDecimal("200")
private val MAX_FARE = BigDecimal("500")

@Composable
fun TransitScreen(onBack: () -> Unit, onOpenCollect: () -> Unit) {
    BackHandler(onBack = onBack)
    var mode by remember { mutableStateOf(TransitMode.LOADING) }
    var balance by remember { mutableStateOf<TransitBalanceDto?>(null) }
    var trips by remember { mutableStateOf<List<TransitTripDto>>(emptyList()) }
    var topUpAmount by remember { mutableStateOf("1000") }
    var operator by remember { mutableStateOf(TRANSIT_OPERATORS[0]) }
    var fare by remember { mutableStateOf(MIN_FARE.toFloat()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var tapMessage by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    // Prefetched here (LoginScreen.kt's checkingPhoneError convention): stringResource()
    // can only be called during composition, not inside these coroutine-launch bodies.
    val networkError = stringResource(R.string.transit_network_error)
    val topUpValidationError = stringResource(R.string.transit_top_up_error)
    val tapMessageTemplate = stringResource(R.string.transit_tap_message)

    fun load() {
        coroutineScope.launch {
            try {
                balance = NetworkClient.apiService.getMyTransitBalance().balance
                mode = TransitMode.ACTIVE
                trips = runCatching { NetworkClient.apiService.getTransitTrips().trips }.getOrDefault(emptyList())
            } catch (e: HttpException) {
                if (apiErrorCode(e) == "TRANSIT_NO_ACCOUNT") {
                    mode = TransitMode.NO_BALANCE
                } else {
                    error = superAppErrorMessage(e)
                }
            } catch (e: IOException) {
                error = networkError
            }
        }
    }
    LaunchedEffect(Unit) { load() }

    fun topUp() {
        val amount = topUpAmount.trim().toBigDecimalOrNull()
        if (amount == null || amount <= BigDecimal.ZERO) {
            error = topUpValidationError
            return
        }
        busy = true
        error = null
        coroutineScope.launch {
            try {
                balance = NetworkClient.apiService.topUpTransit(UUID.randomUUID().toString(), TopUpTransitRequest(amount)).balance
                mode = TransitMode.ACTIVE
                load()
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = networkError
            } finally {
                busy = false
            }
        }
    }

    fun tapFare() {
        val fareAmount = BigDecimal.valueOf(fare.toLong())
        busy = true
        tapMessage = null
        coroutineScope.launch {
            try {
                val result = NetworkClient.apiService.tapTransitFare(UUID.randomUUID().toString(), TapFareRequest(operator, fareAmount))
                balance = result.balance
                tapMessage = String.format(tapMessageTemplate, formatMoneyTransit(result.trip.fare), result.trip.operator)
                load()
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = networkError
            } finally {
                busy = false
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        BackTopBar(title = stringResource(R.string.transit_title), onBack = onBack)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = Ids.layout.screenHorizontal, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            error?.let { msg -> item { Text(msg, color = Ids.colors.danger, fontSize = 13.sp) } }
            when (mode) {
                TransitMode.LOADING -> item { SkeletonBlock(height = 120.dp) }
                TransitMode.NO_BALANCE, TransitMode.ACTIVE -> {
                    item {
                        Column {
                            Text(stringResource(R.string.transit_balance_label), fontSize = 13.sp, color = Ids.colors.textSecondary)
                            Text(
                                "${formatMoneyTransit(balance?.balance ?: BigDecimal.ZERO)} RWF",
                                fontSize = 28.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold,
                            )
                        }
                    }
                    item {
                        Text(
                            stringResource(R.string.transit_disclosure),
                            fontSize = 11.sp, color = Ids.colors.textSecondary,
                        )
                        Text(
                            stringResource(R.string.transit_open_collector_cta),
                            fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            color = Ids.colors.brand,
                            modifier = Modifier.padding(top = 8.dp).pressScaleClickable(onClick = onOpenCollect),
                        )
                    }
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Ids.layout.cardCornerRadius))
                                .padding(16.dp),
                        ) {
                            Text(stringResource(R.string.transit_top_up_title), fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                IdsTextField(
                                    label = stringResource(R.string.transit_amount_placeholder), value = topUpAmount, onValueChange = { topUpAmount = it },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                    .background(Ids.colors.brand)
                                    .pressScaleClickable(enabled = !busy, onClick = ::topUp)
                                    .padding(vertical = 14.dp),
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                Text(
                                    if (busy) stringResource(R.string.transit_topping_up) else stringResource(R.string.transit_top_up),
                                    color = androidx.compose.ui.graphics.Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                )
                            }
                        }
                    }
                    if (mode == TransitMode.ACTIVE) {
                        item {
                            Column(
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Ids.layout.cardCornerRadius))
                                    .padding(16.dp),
                            ) {
                                Text(stringResource(R.string.transit_tap_title), fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                Text(
                                    stringResource(R.string.transit_tap_disclosure),
                                    fontSize = 11.sp, color = Ids.colors.textSecondary,
                                )
                                tapMessage?.let { Text(it, fontSize = 12.sp, color = Ids.colors.success) }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TRANSIT_OPERATORS.forEach { op ->
                                        Row(
                                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                                                .background(if (operator == op) Ids.colors.brand else Ids.colors.surfaceSoft)
                                                .pressScaleClickable(onClick = { operator = op })
                                                .padding(vertical = 10.dp),
                                            horizontalArrangement = Arrangement.Center,
                                        ) {
                                            Text(op, fontSize = 12.sp, color = if (operator == op) androidx.compose.ui.graphics.Color.White else Ids.colors.textPrimary)
                                        }
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Slider(
                                        value = fare, onValueChange = { fare = it },
                                        valueRange = MIN_FARE.toFloat()..MAX_FARE.toFloat(), steps = 5,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text("${formatMoneyTransit(fare)} RWF", fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                }
                                val insufficientBalance = (balance?.balance ?: BigDecimal.ZERO) < BigDecimal.valueOf(fare.toLong())
                                Row(
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                        .background(if (insufficientBalance) Ids.colors.surfaceSoft else Ids.colors.brand)
                                        .pressScaleClickable(enabled = !busy && !insufficientBalance, onClick = ::tapFare)
                                        .padding(vertical = 14.dp),
                                    horizontalArrangement = Arrangement.Center,
                                ) {
                                    Text(
                                        when {
                                            insufficientBalance -> stringResource(R.string.transit_tap_balance_too_low)
                                            busy -> stringResource(R.string.transit_tapping)
                                            else -> String.format(stringResource(R.string.transit_tap_fare), formatMoneyTransit(fare))
                                        },
                                        color = if (insufficientBalance) Ids.colors.textSecondary else androidx.compose.ui.graphics.Color.White,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                    )
                                }
                            }
                        }
                        item { Text(stringResource(R.string.transit_ride_history), fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) }
                        if (trips.isEmpty()) {
                            item { EmptyState(message = stringResource(R.string.transit_no_taps_yet)) }
                        } else {
                            items(trips) { trip ->
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column {
                                        Text(trip.operator, fontSize = 13.sp)
                                        Text(trip.createdAt, fontSize = 11.sp, color = Ids.colors.textSecondary)
                                    }
                                    Text("${formatMoneyTransit(trip.fare)} RWF", fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                }
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
private fun formatMoneyTransit(value: Number): String = String.format(Locale.US, "%,d", value.toLong())
