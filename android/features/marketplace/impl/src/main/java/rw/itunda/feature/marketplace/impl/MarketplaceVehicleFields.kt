package rw.itunda.feature.marketplace.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import rw.itunda.core.designsystem.components.IdsTextField
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.ListingDto

// Real 당근카 (Karrot Vehicles) listing-creation fields (itunda Hood redesign,
// 2026-08-28) -- extracted into its own file since MarketplaceSubScreens.kt had only
// ~50 lines of headroom against the flat 500-line new-file cap. A vehicle is still a
// regular Listing (see backend Listing.vehicleIsLeaseTakeover's own doc comment) --
// this is purely the extra fields NewListingForm shows/sends when the seller marks a
// listing as a vehicle, reusing every other Marketplace field/flow unchanged.
internal class VehicleListingState {
    var isVehicle by mutableStateOf(false)
    var mileageKm by mutableStateOf("")
    var insuranceClaimCount by mutableStateOf("")
    var isLeaseTakeover by mutableStateOf(false)
    var leaseTotalAcquisitionCost by mutableStateOf("")
    var leaseRemainingMonths by mutableStateOf("")
    var leaseTotalMonths by mutableStateOf("")
    var leaseMonthlyPayment by mutableStateOf("")
    var leaseSubsidyAmount by mutableStateOf("")
    var leaseReturnFee by mutableStateOf("")
}

@Composable
internal fun rememberVehicleListingState(): VehicleListingState = remember { VehicleListingState() }

@Composable
private fun ToggleChip(label: String, active: Boolean, onClick: () -> Unit) {
    Text(
        label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        color = if (active) Color.White else Ids.colors.textPrimary,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (active) Ids.colors.brand else Ids.colors.surfaceSoft)
            .pressScaleClickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

@Composable
internal fun VehicleListingFieldsSection(state: VehicleListingState) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ToggleChip("🚗 This is a vehicle (당근카)", state.isVehicle) { state.isVehicle = !state.isVehicle }
        if (state.isVehicle) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IdsTextField(value = state.mileageKm, onValueChange = { state.mileageKm = it }, label = "Mileage (km)", singleLine = true, keyboardType = KeyboardType.Number, modifier = Modifier.weight(1f))
                IdsTextField(value = state.insuranceClaimCount, onValueChange = { state.insuranceClaimCount = it }, label = "Insurance claims", singleLine = true, keyboardType = KeyboardType.Number, modifier = Modifier.weight(1f))
            }
            ToggleChip("Lease takeover (렌트 승계)", state.isLeaseTakeover) { state.isLeaseTakeover = !state.isLeaseTakeover }
            if (state.isLeaseTakeover) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    IdsTextField(value = state.leaseTotalAcquisitionCost, onValueChange = { state.leaseTotalAcquisitionCost = it }, label = "Total acquisition cost", singleLine = true, keyboardType = KeyboardType.Number, modifier = Modifier.weight(1f))
                    IdsTextField(value = state.leaseMonthlyPayment, onValueChange = { state.leaseMonthlyPayment = it }, label = "Monthly payment", singleLine = true, keyboardType = KeyboardType.Number, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    IdsTextField(value = state.leaseRemainingMonths, onValueChange = { state.leaseRemainingMonths = it }, label = "Months remaining", singleLine = true, keyboardType = KeyboardType.Number, modifier = Modifier.weight(1f))
                    IdsTextField(value = state.leaseTotalMonths, onValueChange = { state.leaseTotalMonths = it }, label = "Total lease months", singleLine = true, keyboardType = KeyboardType.Number, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    IdsTextField(value = state.leaseSubsidyAmount, onValueChange = { state.leaseSubsidyAmount = it }, label = "Subsidy amount", singleLine = true, keyboardType = KeyboardType.Number, modifier = Modifier.weight(1f))
                    IdsTextField(value = state.leaseReturnFee, onValueChange = { state.leaseReturnFee = it }, label = "Return fee at end", singleLine = true, keyboardType = KeyboardType.Number, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

// Real lease-takeover cost breakdown shown on a vehicle listing's detail card --
// mirrors the reference's own 인수비/월 납입금/승계 지원금/만기후 반납 rows.
@Composable
internal fun VehicleDetailSection(listing: ListingDto) {
    if (listing.vehicleMileageKm == null && listing.vehicleInsuranceClaimCount == null) return
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Ids.colors.surfaceSoft).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Vehicle info", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        listing.vehicleMileageKm?.let { VehicleInfoRow("Mileage", "${it.toString().reversed().chunked(3).joinToString(",").reversed()} km") }
        listing.vehicleInsuranceClaimCount?.let { VehicleInfoRow("Insurance claims", it.toString()) }
        if (listing.vehicleIsLeaseTakeover) {
            Text("Lease takeover", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
            listing.leaseTotalAcquisitionCost?.let { VehicleInfoRow("Total acquisition cost", "${formatMoneyVehicle(it)} RWF") }
            listing.leaseMonthlyPayment?.let { VehicleInfoRow("Monthly payment", "${formatMoneyVehicle(it)} RWF") }
            if (listing.leaseRemainingMonths != null && listing.leaseTotalMonths != null) {
                VehicleInfoRow("Remaining", "${listing.leaseRemainingMonths} / ${listing.leaseTotalMonths} months")
            }
            listing.leaseSubsidyAmount?.let { VehicleInfoRow("Subsidy", "${formatMoneyVehicle(it)} RWF") }
            listing.leaseReturnFee?.let { VehicleInfoRow("Return fee at end", "${formatMoneyVehicle(it)} RWF") }
        }
    }
}

@Composable
private fun VehicleInfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Ids.colors.textSecondary, fontSize = 13.sp)
        Text(value, color = Ids.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

// Real gap found 2026-08-30 (project_itunda_money_formatting_sweep's own standing
// convention -- comma thousands-separator for every whole-number RWF amount --
// never reached this file). Same shape BikeRentalScreen.kt/BusScreen.kt already use.
private fun formatMoneyVehicle(value: Number): String = String.format(Locale.US, "%,d", value.toLong())
