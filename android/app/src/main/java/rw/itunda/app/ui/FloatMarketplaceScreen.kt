package rw.itunda.app.ui

import rw.itunda.core.designsystem.components.BackTopBar
import rw.itunda.core.designsystem.components.rememberRealLocationRequester
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import rw.itunda.core.designsystem.components.IdsButton
import rw.itunda.core.designsystem.components.IdsButtonSize
import rw.itunda.core.designsystem.components.IdsButtonVariant
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import rw.itunda.core.network.FloatListingDto
import rw.itunda.core.network.FloatTransferRequestDto
import rw.itunda.core.network.NearbyFloatListingDto
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.PostFloatListingRequest
import rw.itunda.core.network.RequestFloatRequest
import rw.itunda.core.network.superAppErrorMessage
import java.util.UUID
import rw.itunda.core.designsystem.components.EmptyState

// Real Rwanda-native peer-to-peer agent float rebalancing marketplace -- sourced
// beyond this session's usual Toss/Kakao/Naver/Coupang reference ecosystems. Running
// out of e-float or physical cash is a documented top-2 operational challenge for
// mobile money agents across Africa; the real existing rebalancing path is traveling
// to a central point, often impossible on a weekend. See the backend's
// FloatMarketplaceService.kt doc comment for the full account. bank-mfe already has
// this (FloatMarketplaceSection); this is the first native client (Android/iOS).
@Composable
fun FloatMarketplaceScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var nearby by remember { mutableStateOf<List<NearbyFloatListingDto>>(emptyList()) }
    var myListings by remember { mutableStateOf<List<FloatListingDto>>(emptyList()) }
    var myRequests by remember { mutableStateOf<List<FloatTransferRequestDto>>(emptyList()) }
    var incomingRequests by remember { mutableStateOf<List<FloatTransferRequestDto>>(emptyList()) }
    var locating by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun loadMine() {
        scope.launch {
            myListings = try { NetworkClient.apiService.getMyFloatListings().listings } catch (e: Exception) { emptyList() }
            myRequests = try { NetworkClient.apiService.getMyFloatRequests().requests } catch (e: Exception) { emptyList() }
            incomingRequests = try { NetworkClient.apiService.getIncomingFloatRequests().requests } catch (e: Exception) { emptyList() }
        }
    }
    LaunchedEffect(Unit) { loadMine() }

    val requestLocation = rememberRealLocationRequester(
        onLocating = { locating = it },
        onSuccess = { lat, lng ->
            scope.launch {
                try {
                    nearby = NetworkClient.apiService.getNearbyFloatListings(lat, lng).listings
                } catch (e: retrofit2.HttpException) {
                    error = superAppErrorMessage(e)
                } catch (e: Exception) {
                    error = "Could not load nearby float listings."
                }
            }
        },
        onError = { error = it },
    )

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(20.dp)) {
            BackTopBar(title = "Float marketplace", onBack = onBack)
        }
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            message?.let { item { Text(it, color = MaterialTheme.colorScheme.primary) } }

            item {
                PostListingCard(
                    busy = busy,
                    onPost = { amount ->
                        busy = true; error = null; message = null
                        scope.launch {
                            try {
                                NetworkClient.apiService.postFloatListing(PostFloatListingRequest(amount))
                                message = "Listing posted — other nearby agents can now request this float."
                                loadMine()
                            } catch (e: retrofit2.HttpException) {
                                error = superAppErrorMessage(e)
                            } catch (e: Exception) {
                                error = "Could not post this listing."
                            } finally {
                                busy = false
                            }
                        }
                    },
                )
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Nearby agents with float to spare", style = MaterialTheme.typography.titleMedium)
                        IdsButton(
                            text = if (locating) "Finding…" else "Find nearby listings",
                            enabled = !locating,
                            variant = IdsButtonVariant.Tinted,
                            size = IdsButtonSize.Medium,
                            onClick = { requestLocation() },
                        )
                        if (nearby.isEmpty()) {
                            EmptyState("No nearby listings loaded yet.")
                        }
                        nearby.forEach { n ->
                            NearbyListingRow(
                                listing = n,
                                busy = busy,
                                onRequest = { amount ->
                                    busy = true; error = null; message = null
                                    scope.launch {
                                        try {
                                            NetworkClient.apiService.requestFloat(n.listing.id, RequestFloatRequest(amount))
                                            message = "Request sent — the listing owner will accept or decline it."
                                            loadMine()
                                        } catch (e: retrofit2.HttpException) {
                                            error = superAppErrorMessage(e)
                                        } catch (e: Exception) {
                                            error = "Could not send this request."
                                        } finally {
                                            busy = false
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }

            item { Text("My listings", style = MaterialTheme.typography.titleMedium) }
            if (myListings.isEmpty()) {
                item { EmptyState("No float listings yet — post one to let nearby agents claim your spare cash.") }
            }
            items(myListings, key = { it.id }) { l ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${"%,.0f".format(l.amount)} RWF offered · ${"%,.0f".format(l.claimedAmount)} claimed · ${l.status}", style = MaterialTheme.typography.bodySmall)
                        if (l.status == "OPEN") {
                            IdsButton(
                                text = "Cancel",
                                enabled = !busy,
                                variant = IdsButtonVariant.Tinted,
                                size = IdsButtonSize.Small,
                                onClick = {
                                    busy = true
                                    scope.launch {
                                        try {
                                            NetworkClient.apiService.cancelFloatListing(l.id)
                                            loadMine()
                                        } catch (e: retrofit2.HttpException) {
                                            error = superAppErrorMessage(e)
                                        } catch (e: Exception) {
                                            error = "Could not cancel this listing."
                                        } finally {
                                            busy = false
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }

            item { Text("Requests against my listings", style = MaterialTheme.typography.titleMedium) }
            if (incomingRequests.isEmpty()) {
                item { EmptyState("No requests yet — they'll show up here once another agent claims from your listing.") }
            }
            items(incomingRequests, key = { it.id }) { r ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${"%,.0f".format(r.amount)} RWF · ${r.status}", style = MaterialTheme.typography.bodySmall)
                        if (r.status == "REQUESTED") {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                IdsButton(
                                    text = "Accept",
                                    enabled = !busy,
                                    size = IdsButtonSize.Small,
                                    onClick = {
                                        busy = true; error = null; message = null
                                        scope.launch {
                                            try {
                                                NetworkClient.apiService.acceptFloatRequest(r.id, UUID.randomUUID().toString())
                                                message = "Float transferred to the requesting agent."
                                                loadMine()
                                            } catch (e: retrofit2.HttpException) {
                                                error = superAppErrorMessage(e)
                                            } catch (e: Exception) {
                                                error = "Could not accept this request."
                                            } finally {
                                                busy = false
                                            }
                                        }
                                    },
                                )
                                IdsButton(
                                    text = "Decline",
                                    enabled = !busy,
                                    variant = IdsButtonVariant.Tinted,
                                    size = IdsButtonSize.Small,
                                    onClick = {
                                        busy = true
                                        scope.launch {
                                            try {
                                                NetworkClient.apiService.declineFloatRequest(r.id)
                                                loadMine()
                                            } catch (e: retrofit2.HttpException) {
                                                error = superAppErrorMessage(e)
                                            } catch (e: Exception) {
                                                error = "Could not decline this request."
                                            } finally {
                                                busy = false
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }

            item { Text("My requests", style = MaterialTheme.typography.titleMedium) }
            if (myRequests.isEmpty()) {
                item { EmptyState("No requests yet — claim from a nearby listing above and it'll show up here.") }
            }
            items(myRequests, key = { it.id }) { r ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${"%,.0f".format(r.amount)} RWF", style = MaterialTheme.typography.bodySmall)
                    Text(r.status, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun PostListingCard(busy: Boolean, onPost: (java.math.BigDecimal) -> Unit) {
    var amount by remember { mutableStateOf("") }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Offer surplus float", style = MaterialTheme.typography.titleMedium)
            IdsTextField(value = amount, onValueChange = { amount = it }, label = "Amount to offer (RWF)", isAmount = true, modifier = Modifier.fillMaxWidth())
            IdsButton(
                text = if (busy) "Working…" else "Post listing",
                enabled = !busy,
                onClick = {
                    val value = amount.toBigDecimalOrNull()
                    if (value == null || value <= java.math.BigDecimal.ZERO) return@IdsButton
                    onPost(value)
                    amount = ""
                },
            )
        }
    }
}

@Composable
private fun NearbyListingRow(listing: NearbyFloatListingDto, busy: Boolean, onRequest: (java.math.BigDecimal) -> Unit) {
    var amount by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${listing.agentDisplayName} · ${"%.1f".format(listing.distanceKm)} km", style = MaterialTheme.typography.bodySmall)
            Text("${"%,.0f".format(listing.remainingAmount)} RWF available", style = MaterialTheme.typography.bodySmall)
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IdsTextField(value = amount, onValueChange = { amount = it }, label = "Amount to request", isAmount = true, modifier = Modifier.fillMaxWidth().padding(end = 8.dp))
            IdsButton(
                text = "Request",
                enabled = !busy,
                size = IdsButtonSize.Medium,
                onClick = {
                    val value = amount.toBigDecimalOrNull()
                    if (value == null || value <= java.math.BigDecimal.ZERO || value > listing.remainingAmount) return@IdsButton
                    onRequest(value)
                    amount = ""
                },
            )
        }
    }
}
