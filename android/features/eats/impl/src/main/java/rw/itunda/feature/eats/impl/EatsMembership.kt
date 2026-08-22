package rw.itunda.feature.eats.impl

import androidx.compose.foundation.background
import rw.itunda.core.designsystem.components.pressScaleClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
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
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.EATS_MEMBERSHIP_TIERS
import rw.itunda.core.network.PLATFORM_MEMBERSHIP_TIERS
import rw.itunda.core.network.PlatformMembershipDto
import rw.itunda.core.network.SubscribePlatformMembershipRequest
import rw.itunda.core.network.EatsMembershipDto
import rw.itunda.core.network.SubscribeEatsMembershipRequest
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.superAppErrorMessage
import java.io.IOException
import java.util.UUID




// Real Coupang 와우 (Wow)-style unconditional delivery-fee waiver (item 211) -- see
// PlatformMembershipDto's own doc comment. bank-mfe already has this; this is the
// first Android client. Deliberately a separate card from EatsMembershipCard below,
// not a replacement: waives the fee at every restaurant, no merchant opt-in required.
@Composable
internal fun PlatformMembershipCard() {
    var membership by remember { mutableStateOf<PlatformMembershipDto?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    fun load() {
        coroutineScope.launch {
            try {
                membership = NetworkClient.apiService.getMyPlatformMembership().membership
            } catch (e: Exception) {
                // Real, non-critical -- the rest of Eats still works without this card.
            } finally {
                loaded = true
            }
        }
    }
    LaunchedEffect(Unit) { load() }

    val current = membership
    if (!loaded) return

    Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("itunda Plus", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            error?.let { Text(it, color = Ids.colors.danger, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)) }
            if (current != null && java.time.Instant.parse(current.activeUntil).isAfter(java.time.Instant.now())) {
                val activeUntilDate = java.time.Instant.parse(current.activeUntil).let {
                    java.time.LocalDateTime.ofInstant(it, java.time.ZoneId.systemDefault()).toLocalDate()
                }
                Text(
                    "Free delivery active until $activeUntilDate at every restaurant, no participation required.",
                    color = Ids.colors.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp),
                )
            } else {
                Text(
                    "Free delivery at every restaurant -- no minimum order, no restaurant opt-in required.",
                    color = Ids.colors.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PLATFORM_MEMBERSHIP_TIERS.forEach { tier ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Ids.colors.brand)
                                .pressScaleClickable(enabled = !busy) {
                                    busy = true
                                    error = null
                                    coroutineScope.launch {
                                        try {
                                            NetworkClient.apiService.subscribePlatformMembership(
                                                java.util.UUID.randomUUID().toString(),
                                                SubscribePlatformMembershipRequest(tier.days),
                                            )
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
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                if (busy) "…" else "${tier.days} days -- %,d RWF".format(tier.priceRwf),
                                color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

// Real Baemin Club (배민클럽)-style free-delivery membership (item 209) -- see
// EatsMembershipDto's own doc comment. First Android client; bank-mfe already has this
// (item 102).
@Composable
internal fun EatsMembershipCard() {
    var membership by remember { mutableStateOf<EatsMembershipDto?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    fun load() {
        coroutineScope.launch {
            try {
                membership = NetworkClient.apiService.getMyEatsMembership().membership
            } catch (e: Exception) {
                // Real, non-critical -- the rest of Eats still works without this card.
            } finally {
                loaded = true
            }
        }
    }
    LaunchedEffect(Unit) { load() }

    val current = membership
    if (!loaded) return

    Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Eats Club", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            error?.let { Text(it, color = Ids.colors.danger, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)) }
            if (current != null && java.time.Instant.parse(current.activeUntil).isAfter(java.time.Instant.now())) {
                val activeUntilDate = java.time.Instant.parse(current.activeUntil).let {
                    java.time.LocalDateTime.ofInstant(it, java.time.ZoneId.systemDefault()).toLocalDate()
                }
                Text(
                    "Free delivery active until $activeUntilDate at participating restaurants.",
                    color = Ids.colors.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp),
                )
            } else {
                Text(
                    "Free delivery at participating restaurants -- no minimum order.",
                    color = Ids.colors.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EATS_MEMBERSHIP_TIERS.forEach { tier ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Ids.colors.brand)
                                .pressScaleClickable(enabled = !busy) {
                                    busy = true
                                    error = null
                                    coroutineScope.launch {
                                        try {
                                            NetworkClient.apiService.subscribeEatsMembership(
                                                java.util.UUID.randomUUID().toString(),
                                                SubscribeEatsMembershipRequest(tier.days),
                                            )
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
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                if (busy) "…" else "${tier.days} days -- %,d RWF".format(tier.priceRwf),
                                color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

