package rw.itunda.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.app.R
import rw.itunda.core.designsystem.components.IdsButton
import rw.itunda.core.designsystem.components.IdsButtonSize
import rw.itunda.core.designsystem.components.IdsButtonVariant
import rw.itunda.core.designsystem.components.dashedBorder
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.Account
import rw.itunda.core.network.LinkedAccountEntityDto
import rw.itunda.core.network.NetworkClient

private enum class PickerTab { RECENT, ACCOUNT, CARD }

// Real QR/FacePay funding-source picker (itunda Pay redesign, 2026-08-28, direct
// user reference: real Toss Pay "Facepay · QR Payment" bottom sheet, Recent/
// Account/Card tabs). Additive to the existing swipeable AccountCardCarousel, not
// a replacement -- see this file's own web sibling (PayFundingSourcePicker.tsx)
// for the full account of why. "Card" tab routes to Card management rather than
// letting you select itunda's own card as a funding source -- CardService.
// chargeWithCard is a structurally separate ledger path from the QR/FacePay
// MerchantService.collect flow this code funds (confirmed via a full backend
// grep before building this: no LinkedCard/ExternalCard concept exists either).
@Composable
fun PayFundingSourcePicker(
    accounts: List<Account>,
    selectedAccountId: String?,
    onSelectAccount: (String) -> Unit,
    onDismiss: () -> Unit,
    onOpenCard: () -> Unit,
) {
    var tab by remember { mutableStateOf(PickerTab.RECENT) }
    var linkedAccounts by remember { mutableStateOf<List<LinkedAccountEntityDto>>(emptyList()) }
    var hasCard by remember { mutableStateOf<Boolean?>(null) }
    var cardLast4 by remember { mutableStateOf<String?>(null) }
    var cardFrozen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            linkedAccounts = NetworkClient.apiService.getLinkedAccounts().linkedAccounts.filter { it.status == "LINKED" }
        } catch (e: Exception) {
            // Real, non-critical.
        }
        try {
            val card = NetworkClient.apiService.getMyCard().card
            hasCard = true
            cardLast4 = card.last4
            cardFrozen = card.frozen
        } catch (e: Exception) {
            hasCard = false
        }
    }

    val selected = accounts.find { it.id == selectedAccountId } ?: accounts.firstOrNull()

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Ids.colors.background).padding(20.dp),
        ) {
            Text(stringResource(R.string.pay_picker_title), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Ids.colors.textPrimary)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                listOf(
                    PickerTab.RECENT to stringResource(R.string.pay_picker_recent),
                    PickerTab.ACCOUNT to stringResource(R.string.pay_picker_account),
                    PickerTab.CARD to stringResource(R.string.pay_picker_card),
                ).forEach { (key, label) ->
                    val active = tab == key
                    Text(
                        label, fontSize = 14.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        color = if (active) Ids.colors.textPrimary else Ids.colors.textSecondary,
                        modifier = Modifier.pressScaleClickable { tab = key },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            when (tab) {
                PickerTab.RECENT -> selected?.let { account ->
                    Row(
                        Modifier.fillMaxWidth().pressScaleClickable { onSelectAccount(account.id); onDismiss() },
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text(accountLabel(account), color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("${account.currency} %,.0f".format(account.balance), color = Ids.colors.textSecondary, fontSize = 12.sp)
                        }
                        Text("✓", color = Ids.colors.brand, fontWeight = FontWeight.Bold)
                    }
                }
                PickerTab.ACCOUNT -> Column {
                    accounts.forEach { account ->
                        Row(
                            Modifier.fillMaxWidth().pressScaleClickable { onSelectAccount(account.id); onDismiss() }.padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column {
                                Text(accountLabel(account), color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("${account.currency} %,.0f".format(account.balance), color = Ids.colors.textSecondary, fontSize = 12.sp)
                            }
                            if (account.id == selectedAccountId) Text("✓", color = Ids.colors.brand, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (linkedAccounts.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.pay_picker_linked_accounts), color = Ids.colors.textSecondary, fontSize = 12.sp)
                        // Real, deliberately non-selectable -- an external linked
                        // account is a real, honest demo-balance display, never a real
                        // funding source MerchantService.collect can actually debit.
                        linkedAccounts.forEach { linked ->
                            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                Text(linked.provider, color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(linked.externalAccountNumberMasked, color = Ids.colors.textSecondary, fontSize = 12.sp)
                            }
                        }
                    }
                }
                PickerTab.CARD -> when (hasCard) {
                    null -> CircularProgressIndicator(color = Ids.colors.brand)
                    true -> Row(
                        Modifier.fillMaxWidth().pressScaleClickable { onDismiss(); onOpenCard() },
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text(stringResource(R.string.overview_card_number, cardLast4 ?: ""), color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(if (cardFrozen) stringResource(R.string.overview_card_frozen) else stringResource(R.string.overview_card_active), color = Ids.colors.textSecondary, fontSize = 12.sp)
                        }
                    }
                    false -> Column(
                        Modifier.fillMaxWidth().dashedBorder(Ids.colors.divider).padding(20.dp),
                    ) {
                        Text(stringResource(R.string.overview_teaser_cards), color = Ids.colors.textSecondary, fontSize = 13.sp)
                        Spacer(Modifier.height(10.dp))
                        IdsButton(text = stringResource(R.string.overview_teaser_cards_cta), variant = IdsButtonVariant.Tinted, size = IdsButtonSize.Small, onClick = { onDismiss(); onOpenCard() })
                    }
                }
            }
        }
    }
}

private fun accountLabel(account: Account): String = when (account.type) {
    "PAY" -> "itunda Pay"
    "MAIN" -> "itunda Bank"
    else -> "itunda Pay ${account.currency}"
}
