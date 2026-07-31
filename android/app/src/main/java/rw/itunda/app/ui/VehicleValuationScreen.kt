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
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.RegisterVehicleRequest
import rw.itunda.core.network.VehicleDto
import rw.itunda.core.network.VehicleValuationDto
import rw.itunda.core.network.superAppErrorMessage
import java.io.IOException
import java.math.BigDecimal
import java.time.Year

// Real Toss 내 차 시세 (my car's market value)-style vehicle value estimator -- see
// rw.itunda.vehicle.VehicleValuationService's own doc comment for the full sourced
// account and honest scope boundary: no real used-car pricing database partnership
// exists here, this is itunda's own documented general depreciation estimate. bank-mfe
// already has this (MyVehiclesCard); this is the first Android client. Mirrors
// bank-mfe's own register/list/valuation/remove flow. Honest v1 scope-down: bank-mfe's
// own "Update km" action uses a raw browser `window.prompt`, which has no direct mobile
// equivalent -- deliberately not ported this pass rather than faking a dialog for it.
@Composable
fun VehicleValuationScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var vehicles by remember { mutableStateOf<List<VehicleDto>?>(null) }
    var valuations by remember { mutableStateOf<Map<String, VehicleValuationDto>>(emptyMap()) }
    var showCreate by remember { mutableStateOf(false) }
    var make by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var modelYear by remember { mutableStateOf(Year.now().value.toString()) }
    var purchasePrice by remember { mutableStateOf("") }
    var purchaseDate by remember { mutableStateOf("") }
    var mileageKm by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var busyId by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    fun load() {
        coroutineScope.launch {
            try {
                val list = NetworkClient.apiService.getMyVehicles().vehicles
                vehicles = list
                val pairs = list.mapNotNull { v ->
                    try {
                        v.id to NetworkClient.apiService.getVehicleValuation(v.id).valuation
                    } catch (e: Exception) {
                        null
                    }
                }
                valuations = pairs.toMap()
            } catch (e: Exception) {
                vehicles = emptyList()
            }
        }
    }
    LaunchedEffect(Unit) { load() }

    fun register() {
        val price = purchasePrice.toBigDecimalOrNull()
        val year = modelYear.toIntOrNull()
        val mileage = mileageKm.toIntOrNull()
        if (make.isBlank() || model.isBlank() || price == null || price <= BigDecimal.ZERO || year == null || purchaseDate.isBlank() || mileage == null || mileage < 0) {
            error = "Fill in every field with a real value."
            return
        }
        busy = true
        error = null
        coroutineScope.launch {
            try {
                NetworkClient.apiService.registerVehicle(RegisterVehicleRequest(make.trim(), model.trim(), year, price, purchaseDate.trim(), mileage))
                make = ""; model = ""; purchasePrice = ""; purchaseDate = ""; mileageKm = ""
                showCreate = false
                load()
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                busy = false
            }
        }
    }

    fun remove(id: String) {
        busyId = id
        coroutineScope.launch {
            try {
                NetworkClient.apiService.removeVehicle(id)
                load()
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } finally {
                busyId = null
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        BackTopBar(title = "My vehicles", onBack = onBack)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = Ids.layout.screenHorizontal, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Estimated resale value based on age and mileage -- itunda's own general estimate, not a market comp.",
                        color = Ids.colors.textSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f),
                    )
                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Ids.colors.surfaceSoft)
                            .clickable { showCreate = !showCreate }.padding(horizontal = 12.dp, vertical = 8.dp),
                    ) { Text(if (showCreate) "Cancel" else "+ Add", color = Ids.colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                }
            }
            if (showCreate) {
                item {
                    Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = Ids.colors.surface), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = make, onValueChange = { make = it }, label = { Text("Make (e.g. Toyota)") }, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(value = model, onValueChange = { model = it }, label = { Text("Model (e.g. RAV4)") }, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(value = modelYear, onValueChange = { modelYear = it }, label = { Text("Model year") }, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(value = purchasePrice, onValueChange = { purchasePrice = it }, label = { Text("Purchase price (RWF)") }, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(value = purchaseDate, onValueChange = { purchaseDate = it }, label = { Text("Purchase date (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(value = mileageKm, onValueChange = { mileageKm = it }, label = { Text("Current mileage (km)") }, modifier = Modifier.fillMaxWidth())
                            error?.let { Text(it, color = Ids.colors.danger, fontSize = 12.sp) }
                            Box(
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                    .background(if (busy) Ids.colors.textTertiary else Ids.colors.brand)
                                    .clickable(enabled = !busy) { register() }.padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) { Text(if (busy) "Adding…" else "Add vehicle", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                        }
                    }
                }
            }
            error?.let { if (!showCreate) item { Text(it, color = Ids.colors.danger, fontSize = 13.sp) } }
            val list = vehicles
            if (list == null) {
                item { Text("Loading…", color = Ids.colors.textSecondary, fontSize = 13.sp) }
            } else if (list.isEmpty()) {
                item { Text("No vehicles added yet.", color = Ids.colors.textSecondary, fontSize = 13.sp) }
            } else {
                items(list, key = { it.id }) { v ->
                    val valuation = valuations[v.id]
                    Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = Ids.colors.surface), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Column {
                                    Text("${v.modelYear} ${v.make} ${v.model}", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text("${"%,d".format(v.mileageKm)} km", color = Ids.colors.textSecondary, fontSize = 12.sp)
                                }
                                Box(
                                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Ids.colors.surfaceSoft)
                                        .clickable(enabled = busyId != v.id) { remove(v.id) }.padding(horizontal = 12.dp, vertical = 8.dp),
                                ) { Text(if (busyId == v.id) "…" else "Remove", color = Ids.colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                            }
                            if (valuation != null) {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("Now: ${"%,.0f".format(valuation.currentEstimatedValue)}", color = Ids.colors.textPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Text("+1y: ${"%,.0f".format(valuation.estimatedValueIn1Year)}", color = Ids.colors.textSecondary, fontSize = 11.sp)
                                    Text("+2y: ${"%,.0f".format(valuation.estimatedValueIn2Years)}", color = Ids.colors.textSecondary, fontSize = 11.sp)
                                    Text("+3y: ${"%,.0f".format(valuation.estimatedValueIn3Years)}", color = Ids.colors.textSecondary, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
