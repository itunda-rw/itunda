package rw.itunda.merchant.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import rw.itunda.core.designsystem.components.IdsButton
import rw.itunda.core.designsystem.components.IdsButtonSize
import rw.itunda.core.designsystem.components.IdsButtonVariant
import rw.itunda.core.designsystem.components.IdsTextField
import androidx.compose.material3.MaterialTheme
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
import rw.itunda.merchant.network.NetworkClient
import rw.itunda.merchant.network.SetMerchantPhotoUrlsRequest

// Real gap found live (uncalled-endpoint sweep, 2026-08-29/30): MerchantService.
// setPhotoUrls (a real, up-to-20-photo gallery, distinct from the older single cover
// photo this app already edits in BusinessAccountScreen.kt) has been live since the
// itunda Maps redesign, and merchant-mfe already has this real UI
// (screens/PhotoGalleryScreen.tsx) -- this is the native-merchantapp port.
//
// Real wire-shape gotcha ALREADY found + fixed once porting the same feature to
// merchant-mfe (2026-08-29): Merchant.photoUrls comes back from the backend as a
// single comma-joined STRING (matching the raw JPA column), not a JSON array --
// only the composed GET /maps/places/{merchantId} splits it server-side. This screen
// fetches its OWN merchant via getMyMerchant() and splits it the same way, rather
// than trusting an array shape.
@Composable
internal fun PhotosTab() {
    val urls = remember { mutableStateOf(listOf("")) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        try {
            val merchant = NetworkClient.apiService.getMyMerchant().merchant
            val existing = merchant.photoUrls?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
            if (existing.isNotEmpty()) urls.value = existing
        } catch (e: Exception) {
            // Non-critical -- the form just starts with one blank row.
        }
    }

    fun save() {
        saving = true
        error = null
        saved = false
        scope.launch {
            try {
                NetworkClient.apiService.setMerchantPhotoUrls(
                    SetMerchantPhotoUrlsRequest(urls.value.map { it.trim() }.filter { it.isNotEmpty() }),
                )
                saved = true
            } catch (e: retrofit2.HttpException) {
                error = rw.itunda.merchant.network.apiErrorMessage(e) ?: "Could not save your photos."
            } catch (e: Exception) {
                error = "Could not save your photos."
            } finally {
                saving = false
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Photo gallery", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            "Up to 20 photos — customers see these on your Maps page.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        if (saved) Text("Saved.", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)

        urls.value.forEachIndexed { index, url ->
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IdsTextField(
                    value = url,
                    onValueChange = { new -> urls.value = urls.value.toMutableList().also { it[index] = new } },
                    label = "Photo URL",
                    modifier = Modifier.weight(1f),
                )
                IdsButton(
                    text = "Remove",
                    onClick = { urls.value = urls.value.filterIndexed { i, _ -> i != index } },
                    variant = IdsButtonVariant.Tinted,
                    size = IdsButtonSize.Small,
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IdsButton(
                text = "Add photo",
                onClick = { urls.value = urls.value + "" },
                enabled = urls.value.size < 20,
                variant = IdsButtonVariant.Tinted,
                size = IdsButtonSize.Small,
            )
            IdsButton(
                text = if (saving) "Saving…" else "Save",
                onClick = ::save,
                enabled = !saving,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
