package rw.itunda.merchant.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import rw.itunda.core.designsystem.components.IdsButton
import rw.itunda.core.designsystem.components.IdsTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import rw.itunda.merchant.network.RedeemedGiftVoucherDto
import rw.itunda.merchant.network.isDeviceNotVerifiedError

/**
 * Real KakaoTalk-style 기프티콘 (mobile gift voucher) merchant-side redemption --
 * ported field-for-field from merchant-mfe's own CollectScreen.tsx VoucherRedeem,
 * the customer shows the merchant their voucher (its real id, from their own itunda
 * app), the merchant enters it here to redeem -- never a self-serve redeem the
 * customer could fake. Real, shipped on the backend + merchant-mfe with zero
 * Android/iOS MerchantApp client until now -- found via a cross-platform-parity
 * check.
 */
@Composable
fun VoucherRedeemTab() {
    var voucherId by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<RedeemedGiftVoucherDto?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
    // Real device step-up -- same DeviceStepUpDialog convention PosScreen.kt's own
    // CardCheckout already establishes for money-moving merchant actions.
    var needsDeviceVerification by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun redeem() {
        val id = voucherId.trim()
        if (id.isEmpty()) return
        submitting = true
        error = null
        needsDeviceVerification = false
        scope.launch {
            try {
                result = NetworkClient.apiService.redeemGiftVoucher(id).voucher
            } catch (e: retrofit2.HttpException) {
                if (isDeviceNotVerifiedError(e)) {
                    needsDeviceVerification = true
                } else {
                    error = "Could not redeem this voucher."
                }
            } catch (e: Exception) {
                error = "Could not redeem this voucher."
            } finally {
                submitting = false
            }
        }
    }

    if (needsDeviceVerification) {
        DeviceStepUpDialog(
            onVerified = { redeem() },
            onCancel = { needsDeviceVerification = false },
        )
    }

    val redeemed = result
    if (redeemed != null) {
        Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(24.dp)) {
            Text("Voucher redeemed", fontWeight = FontWeight.Bold)
            Text(
                redeemed.productNameSnapshot ?: "%,.0f RWF".format(redeemed.amount),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
            )
            IdsButton(text = "Redeem another", onClick = { result = null; voucherId = "" })
        }
        return
    }

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Redeem a gift voucher", fontWeight = FontWeight.Bold)
        Text(
            "Ask the customer for their voucher id and enter it below to redeem it in person.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        IdsTextField(
            value = voucherId,
            onValueChange = { voucherId = it },
            label = "Voucher id",
            placeholder = "giftvoucher_...",
            singleLine = true,
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        IdsButton(
            text = if (submitting) "Redeeming…" else "Redeem",
            enabled = !submitting && voucherId.isNotBlank(),
            onClick = { redeem() },
        )
    }
}
