package rw.itunda.merchant.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import rw.itunda.core.designsystem.components.IdsButton
import rw.itunda.core.designsystem.components.IdsButtonSize
import rw.itunda.core.designsystem.components.IdsButtonVariant
import rw.itunda.core.designsystem.components.IdsTextField
import rw.itunda.merchant.network.MerchantDto
import rw.itunda.merchant.network.NetworkClient
import rw.itunda.merchant.network.SetAcceptingOrdersRequest
import rw.itunda.merchant.network.SetClosedWeekdaysRequest
import rw.itunda.merchant.network.SetMerchantAvgPrepTimeMinutesRequest
import rw.itunda.merchant.network.SetMerchantOpeningHoursRequest
import rw.itunda.merchant.network.SetMerchantPhoneNumberRequest
import rw.itunda.merchant.network.SetMerchantPickupDiscountRequest

private val WEEKDAY_LABELS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

/**
 * Real merchant phone/hours/prep-time/pickup-discount/accepting-orders/closed-
 * weekdays settings -- ported from merchant-mfe (2026-09-03), found via a scripted
 * lib/merchant.ts parity scan: all six were fully built on the backend and wired on
 * merchant-mfe, with zero client on either native merchant app. Extracted into its
 * own file rather than growing StoreSettingsCard's own BusinessAccountScreen.kt,
 * which was already sitting exactly at its file-size-lint baseline.
 */
@Composable
internal fun MoreStoreSettingsCard(merchant: MerchantDto, onUpdated: (MerchantDto) -> Unit) {
    var phoneNumber by remember(merchant.id) { mutableStateOf(merchant.phoneNumber ?: "") }
    var openingHours by remember(merchant.id) { mutableStateOf(merchant.openingHours ?: "") }
    var prepTime by remember(merchant.id) { mutableStateOf(merchant.avgPrepTimeMinutes?.toString() ?: "") }
    var pickupDiscount by remember(merchant.id) { mutableStateOf(merchant.pickupDiscountPercent?.toString() ?: "") }
    var closedWeekdays by remember(merchant.id) { mutableStateOf(merchant.closedWeekdays?.split(",")?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }
    var acceptingBusy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Contact & schedule", fontWeight = FontWeight.Bold)
            IdsTextField(value = phoneNumber, onValueChange = { phoneNumber = it; saved = false }, label = "Phone number", modifier = Modifier.fillMaxWidth())
            IdsTextField(value = openingHours, onValueChange = { openingHours = it; saved = false }, label = "Opening hours (e.g. Mon-Sat 8am-9pm)", modifier = Modifier.fillMaxWidth())
            IdsTextField(value = prepTime, onValueChange = { prepTime = it; saved = false }, label = "Average prep time (minutes)", modifier = Modifier.fillMaxWidth())
            // Real Baemin 포장할인 (pickup discount) -- a customer collecting their own
            // order skips the real delivery-fee cost, so merchants can pass some of
            // that saving back as a percent off.
            IdsTextField(value = pickupDiscount, onValueChange = { pickupDiscount = it; saved = false }, label = "Pickup discount % (blank = none)", modifier = Modifier.fillMaxWidth())

            Text("Closed on", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                WEEKDAY_LABELS.forEachIndexed { index, label ->
                    val weekday = index + 1 // java.time.DayOfWeek numbering, 1=Monday
                    val selected = closedWeekdays.contains(weekday)
                    Text(
                        label,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable {
                                saved = false
                                closedWeekdays = if (selected) closedWeekdays - weekday else closedWeekdays + weekday
                            }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    )
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            if (saved) Text("Saved.", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            IdsButton(
                text = if (busy) "Saving…" else "Save",
                enabled = !busy,
                onClick = {
                    val prepTimeValue = prepTime.trim().let { if (it.isEmpty()) null else it.toIntOrNull() }
                    if (prepTime.trim().isNotEmpty() && prepTimeValue == null) { error = "Enter a real prep time."; return@IdsButton }
                    val pickupDiscountValue = pickupDiscount.trim().let { if (it.isEmpty()) null else it.toIntOrNull() }
                    if (pickupDiscount.trim().isNotEmpty() && pickupDiscountValue == null) { error = "Enter a real pickup discount percent."; return@IdsButton }
                    busy = true
                    error = null
                    saved = false
                    scope.launch {
                        try {
                            var updated = NetworkClient.apiService.setMerchantPhoneNumber(SetMerchantPhoneNumberRequest(phoneNumber.trim().ifBlank { null })).merchant
                            updated = NetworkClient.apiService.setMerchantOpeningHours(SetMerchantOpeningHoursRequest(openingHours.trim().ifBlank { null })).merchant
                            updated = NetworkClient.apiService.setMerchantAvgPrepTimeMinutes(SetMerchantAvgPrepTimeMinutesRequest(prepTimeValue)).merchant
                            updated = NetworkClient.apiService.setMerchantPickupDiscount(SetMerchantPickupDiscountRequest(pickupDiscountValue)).merchant
                            updated = NetworkClient.apiService.setClosedWeekdays(SetClosedWeekdaysRequest(closedWeekdays.sorted())).merchant
                            onUpdated(updated)
                            saved = true
                        } catch (e: Exception) {
                            error = "Could not save."
                        } finally {
                            busy = false
                        }
                    }
                },
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Accepting orders", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    Text("Temporarily pause your store without changing your hours.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IdsButton(
                    text = if (acceptingBusy) "…" else if (merchant.isAcceptingOrders) "On" else "Off",
                    enabled = !acceptingBusy,
                    variant = if (merchant.isAcceptingOrders) IdsButtonVariant.Filled else IdsButtonVariant.Tinted,
                    size = IdsButtonSize.Small,
                    onClick = {
                        acceptingBusy = true
                        scope.launch {
                            try {
                                onUpdated(NetworkClient.apiService.setAcceptingOrders(SetAcceptingOrdersRequest(!merchant.isAcceptingOrders)).merchant)
                            } catch (e: Exception) {
                                error = "Could not save."
                            } finally {
                                acceptingBusy = false
                            }
                        }
                    },
                )
            }
        }
    }
}
