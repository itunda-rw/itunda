package rw.itunda.feature.home.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.RequestQuote
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids

/**
 * Home's primary account card, aligned to the clickable prototype's large balance-first
 * account row. The displayed balance comes from the authenticated account response;
 * actions navigate to existing product screens rather than simulating transactions.
 */
@Composable
internal fun HomeAccountHeroCard(
    account: rw.itunda.core.network.Account,
    payAccount: rw.itunda.core.network.Account?,
    onOpenBank: () -> Unit,
    onOpenPay: () -> Unit,
) {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.fillMaxWidth().pressScaleClickable(onClick = onOpenBank),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                stringResource(R.string.bank_account_account),
                color = Ids.colors.textSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                String.format(Locale.US, "%,.0f", account.balance) + " " + account.currency,
                color = Ids.colors.textPrimary,
                fontSize = 32.sp,
                lineHeight = 38.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
            )
        }
        if (payAccount != null) Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .pressScaleClickable(onClick = onOpenPay)
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.home_pay_shortcut),
                color = Ids.colors.textSecondary,
                fontSize = 15.sp,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                String.format(Locale.US, "%,.0f", payAccount!!.balance) + " " + payAccount.currency,
                color = Ids.colors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                androidx.compose.material.icons.Icons.Outlined.AccountBalanceWallet,
                contentDescription = null,
                tint = Ids.colors.textTertiary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/**
 * Prototype's secondary Pay row: a distinct live balance with a clear next action.
 * Values are sourced from the account response, never local demo data.
 */
@Composable
internal fun HomePayBalanceRow(
    account: rw.itunda.core.network.Account,
    onOpenPay: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .pressScaleClickable(onClick = onOpenPay)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.home_pay_shortcut),
            color = Ids.colors.textSecondary,
            fontSize = 15.sp,
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            String.format(Locale.US, "%,.0f %s", account.balance, account.currency),
            color = Ids.colors.textPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.width(6.dp))
        Icon(
            androidx.compose.material.icons.Icons.Outlined.AccountBalanceWallet,
            contentDescription = null,
            tint = Ids.colors.textTertiary,
            modifier = Modifier.size(16.dp),
        )
    }
}

/** Primary two-button row follows the prototype's send/add-money hierarchy. */
@Composable
internal fun HomePrimaryActions(
    onSend: () -> Unit,
    onAddMoney: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(Ids.colors.brand)
                .pressScaleClickable(onClick = onSend)
                .padding(vertical = 15.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(R.string.home_send_money),
                color = Ids.colors.onBrand,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(Ids.colors.chip)
                .pressScaleClickable(onClick = onAddMoney)
                .padding(vertical = 15.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(R.string.home_add_money),
                color = Ids.colors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
internal fun HomeQuickActions(
    onSend: () -> Unit,
    onScanToPay: () -> Unit,
    onPayBills: () -> Unit,
    onAirtime: () -> Unit,
) {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.home_quick_actions),
            color = Ids.colors.textPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            HomeQuickAction(Icons.Outlined.Send, stringResource(R.string.home_send_money), onSend)
            HomeQuickAction(Icons.Outlined.QrCodeScanner, stringResource(R.string.home_scan_to_pay), onScanToPay)
            HomeQuickAction(Icons.Outlined.RequestQuote, stringResource(R.string.home_pay_bills), onPayBills)
            HomeQuickAction(Icons.Outlined.PhoneAndroid, stringResource(R.string.home_airtime), onAirtime)
        }
    }
}

@Composable
internal fun HomeRecentActivity(
    transactions: List<rw.itunda.core.network.TransactionDto>,
    onSeeAll: () -> Unit,
    onOpenTransaction: () -> Unit,
) {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.home_recent_activity),
                color = Ids.colors.textPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                stringResource(R.string.home_see_all),
                color = Ids.colors.brand,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.pressScaleClickable(onClick = onSeeAll).padding(6.dp),
            )
        }
        if (transactions.isEmpty()) {
            Text(
                stringResource(R.string.home_no_recent_activity),
                color = Ids.colors.textSecondary,
                fontSize = 14.sp,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        } else {
            transactions.take(3).forEach { transaction ->
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .pressScaleClickable(onClick = onOpenTransaction)
                        .padding(vertical = 10.dp, horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier.size(42.dp).clip(RoundedCornerShape(14.dp))
                            .background(Ids.colors.chip),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.AccountBalanceWallet,
                            contentDescription = null,
                            tint = Ids.colors.textPrimary,
                            modifier = Modifier.size(21.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
                        Text(
                            transaction.description.ifBlank { transaction.type },
                            color = Ids.colors.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                        Text(
                            transaction.status,
                            color = Ids.colors.textSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        String.format(Locale.US, "%,.0f %s", transaction.amount, transaction.currency),
                        color = Ids.colors.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
internal fun HomeRwandaServices(
    onOpenService: () -> Unit,
) {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.home_rwanda_services),
            color = Ids.colors.textPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            HomeServiceAction(Icons.Outlined.Home, stringResource(R.string.home_irembo), onOpenService, Modifier.weight(1f))
            HomeServiceAction(Icons.Outlined.ReceiptLong, stringResource(R.string.home_rra_taxes), onOpenService, Modifier.weight(1f))
            HomeServiceAction(Icons.Outlined.Bolt, stringResource(R.string.home_reg_electricity), onOpenService, Modifier.weight(1f))
            HomeServiceAction(Icons.Outlined.WaterDrop, stringResource(R.string.home_wasac_water), onOpenService, Modifier.weight(1f))
            HomeServiceAction(Icons.Outlined.School, stringResource(R.string.home_school_fees), onOpenService, Modifier.weight(1f))
        }
    }
}

@Composable
private fun HomeServiceAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.layout.Column(
        modifier = modifier.clip(RoundedCornerShape(14.dp)).pressScaleClickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Ids.colors.chip),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Ids.colors.textPrimary, modifier = Modifier.size(21.dp))
        }
        Text(
            label,
            color = Ids.colors.textSecondary,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun HomeQuickAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier
            .width(72.dp)
            .clip(RoundedCornerShape(16.dp))
            .pressScaleClickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Ids.colors.chip),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Ids.colors.textPrimary, modifier = Modifier.size(23.dp))
        }
        Text(
            label,
            color = Ids.colors.textSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
        )
    }
}
