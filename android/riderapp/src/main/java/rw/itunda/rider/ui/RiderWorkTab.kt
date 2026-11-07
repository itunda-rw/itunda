package rw.itunda.rider.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import rw.itunda.core.designsystem.components.IdsButton
import rw.itunda.core.designsystem.components.IdsButtonSize
import rw.itunda.core.designsystem.components.IdsButtonVariant
import rw.itunda.core.designsystem.components.IdsErrorText
import rw.itunda.core.designsystem.components.IdsLoading
import rw.itunda.core.designsystem.components.IdsSwitch
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.designsystem.theme.IdsTypography
import rw.itunda.rider.network.NotificationDto
import rw.itunda.rider.network.RiderDto

/** Everything the Work tab shows, hoisted so the shell owns loading and polling. */
data class RiderWorkState(
    val rider: RiderDto?,
    val togglingAvailability: Boolean,
    val offers: List<Pair<NotificationDto, String>>,
    val inProgress: List<RiderDelivery>,
    /** null while the first load is still in flight. */
    val available: List<RiderDelivery>?,
    val merchantNames: Map<String, String>,
    val error: String?,
)

@Composable
fun RiderWorkTab(
    state: RiderWorkState,
    onToggleAvailability: (Boolean) -> Unit,
    onAcceptOffer: (NotificationDto, String) -> Unit,
    onDeclineOffer: (NotificationDto) -> Unit,
    onClaim: (RiderDelivery) -> Unit,
    onOpen: (RiderDelivery) -> Unit,
) {
    val online = state.rider?.available == true
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Column(modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp)) {
                Text("Work", style = IdsTypography.Title1, color = Ids.colors.textPrimary)
                Spacer(modifier = Modifier.height(16.dp))
                OnlineBanner(online = online, enabled = !state.togglingAvailability && state.rider != null, onToggle = onToggleAvailability)
            }
        }
        state.error?.let { message ->
            item { IdsErrorText(message, modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) }
        }
        items(state.offers, key = { "offer:${it.first.id}" }) { (notification, orderId) ->
            OfferBanner(notification, onAccept = { onAcceptOffer(notification, orderId) }, onDecline = { onDeclineOffer(notification) })
        }
        if (state.inProgress.isNotEmpty()) {
            item { SectionTitle("In progress") }
            items(state.inProgress, key = { "mine:${it.source}:${it.id}" }) { delivery ->
                RiderDeliveryRow(
                    delivery = delivery,
                    merchantName = state.merchantNames[delivery.merchantId] ?: delivery.merchantId,
                    showClaim = false,
                    onClaim = {},
                    onOpen = { onOpen(delivery) },
                )
            }
        }
        item { SectionTitle("Open deliveries") }
        val open = state.available
        when {
            open == null -> item { Box(modifier = Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { IdsLoading() } }
            open.isEmpty() -> item {
                // AVAILABLE is genuinely passive (new deliveries just appear) -- say so, and
                // say how to get them if offline (COPY_VOICE: name the object and the fix).
                Text(
                    if (online) "No open deliveries right now. New ones appear here automatically."
                    else "You're offline, so new deliveries won't show up. Go online to start getting offers.",
                    style = IdsTypography.Body1,
                    color = Ids.colors.textSecondary,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
            else -> items(open, key = { "open:${it.source}:${it.id}" }) { delivery ->
                RiderDeliveryRow(
                    delivery = delivery,
                    merchantName = state.merchantNames[delivery.merchantId] ?: delivery.merchantId,
                    showClaim = true,
                    onClaim = { onClaim(delivery) },
                    onOpen = {},
                )
            }
        }
        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = IdsTypography.Subtitle1,
        color = Ids.colors.textPrimary,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun OnlineBanner(online: Boolean, enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (online) Ids.colors.successTint else Ids.colors.surfaceSoft)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                if (online) "You're online" else "You're offline",
                style = IdsTypography.Subtitle1,
                color = Ids.colors.textPrimary,
            )
            Text(
                if (online) "Offers can reach you now." else "Go online to get delivery offers.",
                style = IdsTypography.Body2,
                color = Ids.colors.textSecondary,
            )
        }
        IdsSwitch(checked = online, enabled = enabled, onCheckedChange = onToggle, label = "Availability")
    }
}

@Composable
private fun OfferBanner(notification: NotificationDto, onAccept: () -> Unit, onDecline: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Ids.colors.surfaceSoft)
            .padding(16.dp),
    ) {
        Text(notification.title, style = IdsTypography.Subtitle1, color = Ids.colors.textPrimary)
        Text(notification.body, style = IdsTypography.Body2, color = Ids.colors.textSecondary)
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IdsButton(text = "Accept", size = IdsButtonSize.Medium, onClick = onAccept)
            IdsButton(text = "Decline", variant = IdsButtonVariant.Tinted, size = IdsButtonSize.Medium, onClick = onDecline)
        }
    }
}
