package rw.itunda.merchant.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import rw.itunda.core.designsystem.components.IdsButton
import rw.itunda.core.designsystem.components.IdsSegmentedControl
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import rw.itunda.core.designsystem.components.IdsTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import rw.itunda.merchant.network.CreateAdRequest
import rw.itunda.merchant.network.MerchantAdDto
import rw.itunda.merchant.network.MerchantDto
import rw.itunda.merchant.network.NetworkClient
import rw.itunda.merchant.network.SetMerchantLocationRequest

private val AD_VALID_RADII_METERS = (300..1500 step 100).toList()
private data class AdDurationTier(val days: Int, val price: Int)
private val AD_DURATION_TIERS = listOf(AdDurationTier(3, 1500), AdDurationTier(7, 3000), AdDurationTier(14, 5500))

// Real 당근(Karrot) 반경 타기팅-style radius-targeted local ads (item 147) -- see
// MerchantAdController.kt's own doc comment. merchant-mfe already has this
// (AdsScreen.tsx); this is the first native client. A merchant's own registered
// location is required first (the real "pull, not push" model MerchantAd.kt's own
// doc comment establishes). Honest v1 scope-down: manual lat/lng entry rather than a
// real GPS capture, since neither native app has any location-permission plumbing
// yet -- same class of scope-down RideScreen/VehicleInspectionScreen already use for
// "hours from now" instead of a real calendar picker.
@Composable
fun AdsTab() {
    var merchant by remember { mutableStateOf<MerchantDto?>(null) }
    var ad by remember { mutableStateOf<MerchantAdDto?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun load() {
        error = null
        scope.launch {
            try {
                merchant = NetworkClient.apiService.getMyMerchant().merchant
            } catch (e: Exception) {
                error = "Could not load your business account."
            }
            try {
                ad = NetworkClient.apiService.getMyAd().ad
            } catch (e: Exception) {
                ad = null
            }
        }
    }
    LaunchedEffect(Unit) { load() }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        val current = merchant
        val activeAd = ad
        if (activeAd != null && runCatching { java.time.Instant.parse(activeAd.activeUntil).isAfter(java.time.Instant.now()) }.getOrDefault(false)) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Your active ad", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text(activeAd.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    activeAd.description?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    Text(
                        "${activeAd.radiusMeters}m radius · runs until ${activeAd.activeUntil.take(10)}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (current == null) {
            Text("Loading…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else if (current.latitude == null || current.longitude == null) {
            LocationSetupCard(onSaved = ::load)
        } else {
            CreateOrExtendAdCard(onCreated = ::load)
        }
    }
}

@Composable
private fun LocationSetupCard(onSaved: () -> Unit) {
    var latitude by remember { mutableStateOf("") }
    var longitude by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Set your business location", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(
                "A radius-targeted ad needs your business's real location to match nearby customers.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IdsTextField(value = latitude, onValueChange = { latitude = it }, label = "Latitude", modifier = Modifier.fillMaxWidth())
            IdsTextField(value = longitude, onValueChange = { longitude = it }, label = "Longitude", modifier = Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            IdsButton(
                text = if (submitting) "Saving…" else "Save location",
                enabled = !submitting,
                onClick = {
                    val lat = latitude.toDoubleOrNull()
                    val lng = longitude.toDoubleOrNull()
                    if (lat == null || lng == null) {
                        error = "Enter a real latitude and longitude."
                        return@IdsButton
                    }
                    submitting = true
                    error = null
                    scope.launch {
                        try {
                            NetworkClient.apiService.setMerchantLocation(SetMerchantLocationRequest(lat, lng))
                            onSaved()
                        } catch (e: Exception) {
                            error = "Could not save your location."
                        } finally {
                            submitting = false
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun CreateOrExtendAdCard(onCreated: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var radiusMeters by remember { mutableStateOf(AD_VALID_RADII_METERS.first()) }
    var days by remember { mutableStateOf(AD_DURATION_TIERS.first().days) }
    var error by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val selectedTier = AD_DURATION_TIERS.find { it.days == days } ?: AD_DURATION_TIERS.first()

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Run a local ad", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            IdsTextField(value = title, onValueChange = { title = it }, label = "Title", modifier = Modifier.fillMaxWidth())
            IdsTextField(value = description, onValueChange = { description = it }, label = "Description (optional)", modifier = Modifier.fillMaxWidth())
            Text("Radius: ${if (radiusMeters >= 1000) "%.1fkm".format(radiusMeters / 1000.0) else "${radiusMeters}m"}", style = MaterialTheme.typography.bodySmall)
            IdsSegmentedControl(
                options = listOf(300, 700, 1000, 1500).map { r -> r to (if (r >= 1000) "%.1fkm".format(r / 1000.0) else "${r}m") },
                selected = radiusMeters,
                onSelect = { radiusMeters = it },
            )
            IdsSegmentedControl(
                options = AD_DURATION_TIERS.map { it.days to "${it.days}d" },
                selected = days,
                onSelect = { days = it },
            )
            Text(
                "${formatMoneyAds(selectedTier.price)} RWF will be charged from your account. If you already have an active ad, this extends it.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            IdsButton(
                text = if (submitting) "Starting…" else "Pay ${formatMoneyAds(selectedTier.price)} RWF & run ad",
                enabled = !submitting,
                onClick = {
                    if (title.isBlank()) {
                        error = "Enter a real title."
                        return@IdsButton
                    }
                    submitting = true
                    error = null
                    scope.launch {
                        try {
                            NetworkClient.apiService.createOrExtendAd(
                                CreateAdRequest(title.trim(), description.trim().ifBlank { null }, radiusMeters, days),
                            )
                            title = ""; description = ""
                            onCreated()
                        } catch (e: Exception) {
                            error = "Could not create this ad."
                        } finally {
                            submitting = false
                        }
                    }
                },
            )
        }
    }
}

// Real gap found 2026-08-30 (project_itunda_money_formatting_sweep's own standing
// convention -- comma thousands-separator for every whole-number RWF amount --
// never reached the merchantapp module at all).
private fun formatMoneyAds(value: Int): String = "%,d".format(value)
