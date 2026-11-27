package rw.itunda.feature.marketplace.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.IOException
import kotlinx.coroutines.launch
import retrofit2.HttpException
import rw.itunda.core.designsystem.components.EmptyState
import rw.itunda.core.designsystem.components.ErrorCard
import rw.itunda.core.designsystem.components.IdsButton
import rw.itunda.core.designsystem.components.IdsTextField
import rw.itunda.core.designsystem.components.ListingActionButton
import rw.itunda.core.designsystem.components.SkeletonBlock
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.designsystem.theme.IdsIcons
import rw.itunda.core.network.AddKeywordAlertRequest
import rw.itunda.core.network.CreateListingRequest
import rw.itunda.core.network.KeywordAlertDto
import rw.itunda.core.network.KeywordAlertQuietHoursDto
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.SetKeywordAlertQuietHoursRequest
import rw.itunda.core.network.superAppErrorMessage

// Real fix (2026-08-26): split out of MarketplaceScreen.kt once that file grew
// past its file-size-lint baseline. Self-contained sub-screens (keyword alerts, new
// listing form) only rendered inside MarketplaceContent's own sheet/tab flow. Same
// package, so zero import changes anywhere. ListingWishlistView/HiddenListingsView
// moved out to MarketplaceSavedListingsViews.kt (2026-09-08) once this file crossed
// the 500-line file-size-lint guideline.

// Real 당근마켓-style Keyword Alert -- first Android client for this feature (item 115,
// found via a content-grep sweep confirming zero client anywhere; bank-mfe ported it
// the same day as item 114). Real, published Karrot 30-keyword-per-user cap enforced
// server-side, surfaced via the backend's own KEYWORD_ALERT_CAP_REACHED error.
@Composable
internal fun KeywordAlertsView() {
    var alerts by remember { mutableStateOf<List<KeywordAlertDto>?>(null) }
    var keyword by remember { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    var removingId by remember { mutableStateOf<String?>(null) }
    var quietHours by remember { mutableStateOf<KeywordAlertQuietHoursDto?>(null) }
    var quietHoursLoaded by remember { mutableStateOf(false) }
    var quietStart by remember { mutableStateOf("22:00") }
    var quietEnd by remember { mutableStateOf("08:00") }
    var savingQuietHours by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    fun load() {
        coroutineScope.launch {
            try {
                val res = NetworkClient.apiService.getKeywordAlerts()
                if (res.success) alerts = res.alerts
                error = null
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            }
            try {
                val qh = NetworkClient.apiService.getKeywordAlertQuietHours().quietHours
                quietHours = qh
                if (qh != null) { quietStart = qh.startTime; quietEnd = qh.endTime }
            } catch (e: Exception) {
                // Non-critical -- the quiet-hours card just stays hidden.
            } finally {
                quietHoursLoaded = true
            }
        }
    }
    LaunchedEffect(Unit) { load() }

    fun addAlert() {
        if (keyword.isBlank()) return
        adding = true
        error = null
        coroutineScope.launch {
            try {
                NetworkClient.apiService.addKeywordAlert(AddKeywordAlertRequest(keyword.trim()))
                keyword = ""
                load()
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                adding = false
            }
        }
    }

    fun removeAlert(alertId: String) {
        removingId = alertId
        coroutineScope.launch {
            try {
                NetworkClient.apiService.removeKeywordAlert(alertId)
                alerts = alerts?.filterNot { it.id == alertId }
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                removingId = null
            }
        }
    }

    fun saveQuietHours(enabled: Boolean) {
        savingQuietHours = true
        error = null
        coroutineScope.launch {
            try {
                val res = NetworkClient.apiService.setKeywordAlertQuietHours(SetKeywordAlertQuietHoursRequest(quietStart, quietEnd, enabled))
                quietHours = res.quietHours
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                savingQuietHours = false
            }
        }
    }

    // Real fix (2026-08-24, flat-design sweep): dropped all 3 Card wrappers in this
    // function -- add-alert form (lone), the alert list (an entity list a user
    // manages, no divider, matching GroupAccountScreen), and Quiet hours (its own
    // bold title already marks the section boundary, no divider needed)
    // (docs/UI_UX_GUIDELINES.md §10).
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IdsTextField(
                value = keyword, onValueChange = { keyword = it }, label = "Alert me for (e.g. iPhone 15)",
                singleLine = true, modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(8.dp))
            ListingActionButton(if (adding) "…" else "Add", adding, filled = true) { addAlert() }
        }
        error?.let { Text(it, color = Ids.colors.danger, fontSize = 13.sp) }
        when {
            alerts == null -> SkeletonBlock()
            alerts!!.isEmpty() -> EmptyState("No keyword alerts yet -- add one to get notified when a matching listing is posted.", icon = IdsIcons.Bell)
            else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                alerts!!.forEach { a ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(a.keyword, color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        ListingActionButton(if (removingId == a.id) "Removing…" else "Remove", removingId == a.id) { removeAlert(a.id) }
                    }
                }
            }
        }
        if (quietHoursLoaded) {
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Quiet hours", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("Don't send alert notifications during these hours.", color = Ids.colors.textSecondary, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IdsTextField(value = quietStart, onValueChange = { quietStart = it }, label = "Start (HH:mm)", modifier = Modifier.weight(1f))
                    IdsTextField(value = quietEnd, onValueChange = { quietEnd = it }, label = "End (HH:mm)", modifier = Modifier.weight(1f))
                }
                ListingActionButton(
                    if (savingQuietHours) "…" else if (quietHours?.enabled == true) "Turn off quiet hours" else "Turn on quiet hours",
                    savingQuietHours, filled = quietHours?.enabled != true,
                ) { saveQuietHours(quietHours?.enabled != true) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewListingForm(onCreated: (String, String) -> Unit, onCancel: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Electronics") }
    var condition by remember { mutableStateOf("Good") }
    var details by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val priceValue = price.filter(Char::isDigit).toDoubleOrNull()
    val canPost = title.isNotBlank() && priceValue != null && priceValue > 0.0
    val categories = listOf("Electronics", "Phones", "Home", "Fashion", "Kids", "Sports")
    val conditions = listOf("New", "Like new", "Good", "Fair")

    ModalBottomSheet(onDismissRequest = onCancel) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Sell something", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            IdsTextField(
                value = title,
                onValueChange = { title = it },
                label = "Title",
                placeholder = "What are you selling?",
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            IdsTextField(
                value = price,
                onValueChange = { price = it },
                label = "Price (RWF)",
                placeholder = "25,000",
                singleLine = true,
                keyboardType = KeyboardType.Number,
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Category", color = Ids.colors.textSecondary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            categories.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { value -> MarketplaceSellChip(value, category == value) { category = value } }
                }
            }
            Text("Condition", color = Ids.colors.textSecondary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            conditions.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { value -> MarketplaceSellChip(value, condition == value) { condition = value } }
                }
            }
            IdsTextField(
                value = details,
                onValueChange = { details = it },
                label = "Details (optional)",
                placeholder = "Any extra information for buyers?",
                singleLine = false,
                modifier = Modifier.fillMaxWidth(),
            )
            error?.let { Text(it, color = Ids.colors.danger, fontSize = 12.sp) }
            IdsButton(
                text = "Post",
                onClick = {
                    val amount = price.filter(Char::isDigit).toDoubleOrNull()
                    if (canPost && amount != null) {
                        submitting = true
                        error = null
                        coroutineScope.launch {
                            try {
                                val response = NetworkClient.apiService.createListing(
                                    CreateListingRequest(
                                        title = title.trim(),
                                        description = details.trim().ifBlank { "Contact the seller to arrange pickup." },
                                        price = amount,
                                        category = category,
                                    ),
                                )
                                if (response.success) onCreated(response.listing.id, condition)
                                else error = "Couldn't post your listing. Try again."
                            } catch (e: HttpException) {
                                error = superAppErrorMessage(e)
                            } catch (e: IOException) {
                                error = "Couldn't reach itunda. Check your connection and try again."
                            } finally {
                                submitting = false
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = canPost && !submitting,
                loading = submitting,
            )
        }
    }
}

@Composable
private fun MarketplaceSellChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (selected) Ids.colors.textPrimary else Ids.colors.textSecondary,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(RoundedCornerShape(14.dp))
            .background(if (selected) Ids.colors.surfaceSoft else Ids.colors.surface)
            .pressScaleClickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
    )
}
