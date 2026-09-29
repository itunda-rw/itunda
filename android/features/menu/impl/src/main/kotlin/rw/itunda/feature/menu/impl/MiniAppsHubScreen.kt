package rw.itunda.feature.menu.impl

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import rw.itunda.core.designsystem.components.BackTopBar
import rw.itunda.core.designsystem.components.FlatRow
import rw.itunda.core.designsystem.components.FlatSection
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids

// Real Toss/Kakao mini-app-store reference (2026-09-11, 7 real Kakao 미니앱
// screenshots) -- the real partner mini-app catalog (rw.itunda.partners) used
// to be a small teaser buried inside MenuScreen's own Explore tab. This is its
// own dedicated screen now, matching bank-mfe's identical MiniAppsHubScreen.tsx
// split: MenuScreen keeps a 3-item capped teaser + "See all" opening this.
//
// Real category taxonomy (deliberately small and generic -- no real submitted
// partner apps yet to justify more granularity) matches the backend's own
// PartnerMiniAppCategory enum exactly. Search is client-side over the fetched
// (category-filtered) catalog -- the real catalog is genuinely tiny today (no
// seed data, no real onboarded partners), so real backend full-text search
// infra would be building ahead of real need.
private val MINI_APP_CATEGORIES = listOf("Finance", "Shopping", "Productivity", "Lifestyle", "Other")

@Composable
fun MiniAppsHubScreen(
    onBack: () -> Unit,
    onLaunchPartnerMiniApp: suspend (activity: android.app.Activity, app: rw.itunda.core.network.PartnerMiniAppDto, onError: (String) -> Unit) -> Unit = { _, _, _ -> },
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var miniApps by remember { mutableStateOf<List<rw.itunda.core.network.PartnerMiniAppDto>?>(null) }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var loadingPartnerAppId by remember { mutableStateOf<String?>(null) }
    var partnerLoadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(selectedCategory) {
        try {
            val result = rw.itunda.core.network.NetworkClient.apiService.getMiniAppCatalog(category = selectedCategory?.uppercase())
            if (result.success) miniApps = result.miniApps
        } catch (_: Exception) {
            miniApps = emptyList()
        }
    }

    val query = searchQuery.trim()
    val filtered = (miniApps ?: emptyList()).filter {
        query.isBlank() || it.name.contains(query, ignoreCase = true) || it.description.contains(query, ignoreCase = true)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        BackTopBar(title = "Mini apps", onBack = onBack)
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = Ids.layout.screenHorizontal),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search mini apps") },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 10.dp),
                    singleLine = true,
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CategoryChip(label = "All", selected = selectedCategory == null, onClick = { selectedCategory = null })
                    MINI_APP_CATEGORIES.forEach { category ->
                        CategoryChip(
                            label = category,
                            selected = selectedCategory == category,
                            onClick = { selectedCategory = if (selectedCategory == category) null else category },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }
            if (miniApps != null) {
                item {
                    FlatSection(
                        title = selectedCategory ?: "All mini apps",
                        rows = filtered.map { app ->
                            FlatRow(
                                title = app.name,
                                subtitle = if (loadingPartnerAppId == app.id) "Loading..." else app.description,
                                glyph = { PartnerMiniAppGlyph(app.iconUrl) },
                                onClick = {
                                    if (loadingPartnerAppId == null) {
                                        loadingPartnerAppId = app.id
                                        coroutineScope.launch {
                                            onLaunchPartnerMiniApp(context as android.app.Activity, app) { message -> partnerLoadError = message }
                                            loadingPartnerAppId = null
                                        }
                                    }
                                }
                            )
                        }
                    )
                    if (filtered.isEmpty()) {
                        Text(
                            "No mini apps match yet -- try a different category or search term.",
                            color = Ids.colors.textTertiary, fontSize = 14.sp, modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }

    val currentPartnerLoadError = partnerLoadError
    if (currentPartnerLoadError != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { partnerLoadError = null },
            title = { Text("Couldn't load mini-app") },
            text = { Text(currentPartnerLoadError) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { partnerLoadError = null }) { Text("OK") }
            }
        )
    }
}

@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .pressScaleClickable(onClick = onClick)
            .background(if (selected) Ids.colors.brand else Ids.colors.chip, RoundedCornerShape(999.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(label, color = if (selected) Color.White else Ids.colors.textSecondary, fontSize = 12.sp)
    }
}
