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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import rw.itunda.core.designsystem.components.IdsTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import rw.itunda.core.network.UpdateVehicleMileageRequest
import rw.itunda.core.network.VehicleDto
import rw.itunda.core.network.VehicleValuationDto
import rw.itunda.core.network.superAppErrorMessage
import java.io.IOException
import java.math.BigDecimal
import java.time.Year
import rw.itunda.core.designsystem.components.EmptyState

// Real Toss 내 차 시세 (my car's market value)-style vehicle value estimator -- see
// rw.itunda.vehicle.VehicleValuationService's own doc comment for the full sourced
// account and honest scope boundary: no real used-car pricing database partnership
// exists here, this is itunda's own documented general depreciation estimate. bank-mfe
// already has this (MyVehiclesCard); this is the first Android client. Mirrors
// bank-mfe's own register/list/valuation/remove flow, including its own "Update km"
// action (bank-mfe uses a raw browser `window.prompt`, ported here as a real dialog
// instead -- `updateVehicleMileage` had already been added to ApiService.kt but never
// actually called from any screen, found via a fresh sweep).
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
    var editingMileage by remember { mutableStateOf<VehicleDto?>(null) }
    var editMileageText by remember { mutableStateOf("") }
    var editMileageBusy by remember { mutableStateOf(false) }
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

    fun updateMileage(id: String, mileage: Int) {
        editMileageBusy = true
        coroutineScope.launch {
            try {
                NetworkClient.apiService.updateVehicleMileage(id, UpdateVehicleMileageRequest(mileage))
                editingMileage = null
                load()
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } finally {
                editMileageBusy = false
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
                            IdsTextField(value = make, onValueChange = { make = it }, label = "Make (e.g. Toyota)", modifier = Modifier.fillMaxWidth())
                            IdsTextField(value = model, onValueChange = { model = it }, label = "Model (e.g. RAV4)", modifier = Modifier.fillMaxWidth())
                            IdsTextField(value = modelYear, onValueChange = { modelYear = it }, label = "Model year", modifier = Modifier.fillMaxWidth())
                            IdsTextField(value = purchasePrice, onValueChange = { purchasePrice = it }, label = "Purchase price (RWF)", modifier = Modifier.fillMaxWidth())
                            IdsTextField(value = purchaseDate, onValueChange = { purchaseDate = it }, label = "Purchase date (YYYY-MM-DD)", modifier = Modifier.fillMaxWidth())
                            IdsTextField(value = mileageKm, onValueChange = { mileageKm = it }, label = "Current mileage (km)", modifier = Modifier.fillMaxWidth())
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
                item { EmptyState("No vehicles added yet.") }
            } else {
                items(list, key = { it.id }) { v ->
                    val valuation = valuations[v.id]
                    Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = Ids.colors.surface), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Column {
                                    Text("${v.modelYear} ${v.make} ${v.model}", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(
                                        buildString {
                                            append("${"%,d".format(v.mileageKm)} km")
                                            valuation?.let { append(" · ${it.ageYears} ${if (it.ageYears == 1) "year" else "years"} old · expected ${"%,d".format(it.expectedMileageKm)} km") }
                                        },
                                        color = Ids.colors.textSecondary, fontSize = 12.sp,
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Box(
                                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Ids.colors.surfaceSoft)
                                            .clickable { editingMileage = v; editMileageText = v.mileageKm.toString() }.padding(horizontal = 12.dp, vertical = 8.dp),
                                    ) { Text("Update km", color = Ids.colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                                    Box(
                                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Ids.colors.surfaceSoft)
                                            .clickable(enabled = busyId != v.id) { remove(v.id) }.padding(horizontal = 12.dp, vertical = 8.dp),
                                    ) { Text(if (busyId == v.id) "…" else "Remove", color = Ids.colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                                }
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

    editingMileage?.let { v ->
        AlertDialog(
            onDismissRequest = { if (!editMileageBusy) editingMileage = null },
            title = { Text("Update mileage") },
            text = {
                IdsTextField(value = editMileageText, onValueChange = { editMileageText = it }, label = "Current mileage (km)", modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                TextButton(
                    enabled = !editMileageBusy,
                    onClick = { editMileageText.toIntOrNull()?.takeIf { it >= 0 }?.let { updateMileage(v.id, it) } },
                ) { Text(if (editMileageBusy) "Saving…" else "Save") }
            },
            dismissButton = { TextButton(enabled = !editMileageBusy, onClick = { editingMileage = null }) { Text("Cancel") } },
        )
    }
}
