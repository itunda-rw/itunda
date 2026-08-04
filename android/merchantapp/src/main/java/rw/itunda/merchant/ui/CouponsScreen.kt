package rw.itunda.merchant.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocalOffer
import kotlinx.coroutines.launch
import rw.itunda.core.designsystem.components.EmptyState
import rw.itunda.merchant.network.CreateCouponRequest
import rw.itunda.merchant.network.MerchantCouponDto
import rw.itunda.merchant.network.NetworkClient

/**
 * Real merchant coupons + 단골 (regular customer) loyalty gating -- see
 * rw.itunda.merchant.MerchantCouponService's own doc comment. Merchant-owner-facing
 * create/list/deactivate half only; a coupon redeems against a real Pay-by-code
 * payment, not here. merchant-mfe already has this; this is the first Android client.
 */
@Composable
fun CouponsTab() {
    var coupons by remember { mutableStateOf<List<MerchantCouponDto>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun load() {
        error = null
        scope.launch {
            try {
                coupons = NetworkClient.apiService.getMyCoupons().coupons
            } catch (e: Exception) {
                error = "Could not load your coupons."
            }
        }
    }
    LaunchedEffect(Unit) { load() }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { CreateCouponCard(onCreated = ::load) }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Your coupons", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "A customer applies a coupon when paying by code -- it's redeemed once per customer.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    val list = coupons
                    when {
                        list == null -> Text("Loading…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        list.isEmpty() -> EmptyState("No coupons yet — create one above to give repeat customers a reason to come back.", icon = Icons.Outlined.LocalOffer)
                    }
                }
            }
        }
        val list = coupons
        if (list != null) {
            items(list, key = { it.id }) { coupon -> CouponRow(coupon = coupon, onChanged = ::load) }
        }
    }
}

@Composable
private fun CreateCouponCard(onCreated: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var percentType by remember { mutableStateOf(true) }
    var discountValue by remember { mutableStateOf("") }
    var regularsOnly by remember { mutableStateOf(false) }
    var expiresAt by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Create a coupon", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, placeholder = { Text("10% off your next visit") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description (optional)") }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { percentType = true }) { Text(if (percentType) "✓ Percent off" else "Percent off") }
                Button(onClick = { percentType = false }) { Text(if (!percentType) "✓ Fixed amount off" else "Fixed amount off") }
            }
            OutlinedTextField(
                value = discountValue, onValueChange = { discountValue = it },
                label = { Text(if (percentType) "Percent (1-100)" else "Amount (RWF)") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(value = expiresAt, onValueChange = { expiresAt = it }, label = { Text("Expires (YYYY-MM-DD, optional)") }, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.Checkbox(checked = regularsOnly, onCheckedChange = { regularsOnly = it })
                Text("Reserve for regular customers only (3+ past payments)", style = MaterialTheme.typography.bodySmall)
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = {
                    val value = discountValue.toBigDecimalOrNull()
                    if (title.isBlank() || value == null || value <= java.math.BigDecimal.ZERO) {
                        error = "Enter a real title and discount value."
                        return@Button
                    }
                    submitting = true
                    error = null
                    scope.launch {
                        try {
                            NetworkClient.apiService.createCoupon(
                                CreateCouponRequest(
                                    title = title.trim(),
                                    description = description.trim().ifBlank { null },
                                    discountType = if (percentType) "PERCENT" else "FIXED_AMOUNT",
                                    discountValue = value,
                                    regularsOnly = regularsOnly,
                                    expiresAt = expiresAt.trim().ifBlank { null }?.let { "${it}T00:00:00Z" },
                                ),
                            )
                            title = ""; description = ""; discountValue = ""; regularsOnly = false; expiresAt = ""
                            onCreated()
                        } catch (e: Exception) {
                            error = "Could not create this coupon."
                        } finally {
                            submitting = false
                        }
                    }
                },
                enabled = !submitting,
            ) { Text(if (submitting) "Creating…" else "Create coupon") }
        }
    }
}

@Composable
private fun CouponRow(coupon: MerchantCouponDto, onChanged: () -> Unit) {
    var deactivating by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(coupon.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                Text(
                    if (coupon.active) "Active" else "Deactivated",
                    color = if (coupon.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold,
                )
            }
            coupon.description?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Text(
                buildString {
                    append(if (coupon.discountType == "PERCENT") "${coupon.discountValue}% off" else "${"%,.0f".format(coupon.discountValue)} RWF off")
                    if (coupon.regularsOnly) append(" · Regulars only")
                    coupon.expiresAt?.let { append(" · Expires ${it.take(10)}") }
                },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            if (coupon.active) {
                Button(
                    onClick = {
                        deactivating = true
                        error = null
                        scope.launch {
                            try {
                                NetworkClient.apiService.deactivateCoupon(coupon.id)
                                onChanged()
                            } catch (e: Exception) {
                                error = "Could not deactivate this coupon."
                            } finally {
                                deactivating = false
                            }
                        }
                    },
                    enabled = !deactivating,
                ) { Text(if (deactivating) "…" else "Deactivate") }
            }
        }
    }
}
