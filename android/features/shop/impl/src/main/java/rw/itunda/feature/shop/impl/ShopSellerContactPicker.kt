package rw.itunda.feature.shop.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import retrofit2.HttpException
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.SendMessageRequest
import rw.itunda.core.network.superAppErrorMessage
import java.io.IOException

// Real seller chat, real canned quick-reply categories (itunda Shopping redesign,
// 2026-08-28, direct user reference: real Toss Shopping seller-chat screenshots
// with canned inquiry categories). Wires Shop into the exact same real 1:1
// messaging system every other vertical (Marketplace/Community/Jobs/Property)
// already uses via its own onMessageSeller -- see this file's own web sibling
// (ShopSellerContactPicker.tsx) for the full account. Deliberately does NOT touch
// the shared conversation-thread UI at all: this is a small picker shown BEFORE
// entering it. Tapping a category is a real, honest compose-assist -- it calls the
// real contact-seller endpoint, sends a real first message with that category's
// own real label as the body, then hands the real conversation id up to the
// caller. "Just start chatting" opens the real conversation with no pre-sent
// message. Every message sent here is a real message in a real thread, no
// fabricated chat-bot layer. Hardcoded English copy, no string resources --
// matches this module's own existing convention (no stringResource usage
// anywhere else in features/shop/impl).
private val CONTACT_CATEGORIES = listOf(
    "Product inquiry", "Shipping inquiry", "Exchange inquiry",
    "Return inquiry", "Cancellation inquiry", "Other inquiry",
)

@Composable
fun ShopSellerContactPicker(
    merchantId: String,
    merchantName: String,
    onDismiss: () -> Unit,
    onOpened: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun start(firstMessage: String?) {
        sending = true
        error = null
        scope.launch {
            try {
                val res = NetworkClient.apiService.contactMerchantSeller(merchantId)
                if (res.success) {
                    if (firstMessage != null) {
                        NetworkClient.apiService.sendMessage(res.conversation.id, SendMessageRequest(firstMessage))
                    }
                    onOpened(res.conversation.id)
                } else {
                    sending = false
                }
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
                sending = false
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
                sending = false
            }
        }
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Ids.colors.background).padding(20.dp),
        ) {
            Text("What can we help with?", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Ids.colors.textPrimary)
            Text(merchantName, fontSize = 13.sp, color = Ids.colors.textSecondary, modifier = Modifier.padding(top = 2.dp, bottom = 14.dp))

            CONTACT_CATEGORIES.forEach { category ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pressScaleClickable(enabled = !sending) { start(category) }
                        .padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(category, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Ids.colors.textPrimary)
                    Text("›", fontSize = 14.sp, color = Ids.colors.textSecondary)
                }
            }

            Spacer(Modifier.height(4.dp))
            Text(
                "Just start chatting",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Ids.colors.textSecondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .pressScaleClickable(enabled = !sending) { start(null) }
                    .padding(vertical = 12.dp),
            )

            error?.let {
                Text(it, fontSize = 13.sp, color = Ids.colors.danger, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

// Real card-visual polish (itunda Shopping redesign, 2026-08-28) -- three small,
// shared trust-signal composables used across Shop's product cards/detail screen,
// matching the real Toss Shopping reference's card style without fabricating
// anything: isBestSeller is a real, derived signal (see backend
// ShoppingController.bestSellerProductIds' own doc comment); deliveryTimeMinutes
// is itunda's own real, already-computed delivery-ETA estimate -- deliberately
// NOT the reference's literal "Ships today" parcel-shipping copy, since itunda's
// real commerce fulfillment model is merchant-pickup/delivery-time-estimate.

@Composable
fun ShopMessageSellerButton(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Ids.colors.surface)
            .pressScaleClickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("💬", fontSize = 12.sp)
        Text("Message seller", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Ids.colors.textPrimary)
    }
}

@Composable
fun ShopBestSellerBadge() {
    Text(
        "Best seller",
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = androidx.compose.ui.graphics.Color.White,
        modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Ids.colors.brand).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
fun ShopDeliveryEtaPill(minutes: Int?) {
    if (minutes == null) return
    Text("🕒 ~$minutes min", fontSize = 11.sp, color = Ids.colors.textSecondary)
}
