package rw.itunda.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import rw.itunda.app.R
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.designsystem.theme.IdsIcons

// Real Toss Bank 관리 (Manage) screen (2026-09-01, direct user-supplied Toss
// screenshots of that exact screen) -- ports web's own already-built
// AccountManageScreen.tsx (services/micro-frontends/bank-mfe/src/AccountManageScreen.tsx)
// so Android's gear icon opens an account-scoped hub instead of jumping straight
// to the generic app-wide Settings screen (see ItundaAppScreen.kt's own
// onOpenManage wiring for the previous behavior). Every row below routes to an
// already-built, real itunda screen or feature -- never a fabricated destination.
// Android is actually ahead of web/iOS here for two rows web/iOS can't reach
// directly: "Manage devices" deep-links straight to DeviceListScreen (now
// `internal`, ItundaAppScreen.kt's own showDeviceList) instead of web's
// redirect-through-Settings, and "Scheduled transfers" (Toss's real 예약송금) has
// its own top-level screen already (ScheduledTransferListScreen) that this can
// jump straight to.
//
// Real, named, deliberately NOT built here (matching AccountManageScreen.tsx's own
// disclosure) -- these are genuinely Korea-specific banking infrastructure/
// regulation (Open Banking/firm banking, tax-free limits, telecom fraud-sharing,
// ATM limits, Credit Information Usage Policy) or a real itunda gap not rushed
// into a UI pass (changing your account password, an account nickname, closing
// your account) -- not silently dropped, see the disclosure text at the bottom.
@Composable
fun AccountManageScreen(
    accountNumber: String,
    onBack: () -> Unit,
    onOpenCard: () -> Unit,
    onOpenDevices: () -> Unit,
    onOpenAutoTransfer: () -> Unit,
    onOpenScheduledTransfers: () -> Unit,
    onOpenForeignCurrency: () -> Unit,
    onOpenBills: () -> Unit,
    onOpenSupport: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val formattedAccountNumber = accountNumber.chunked(4).joinToString("-")

    Column(modifier = Modifier.fillMaxSize().background(Ids.colors.background).verticalScroll(rememberScrollState())) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = Ids.layout.screenHorizontal, vertical = 12.dp)) {
            Box(
                modifier = Modifier.size(Ids.layout.minTouchTarget).clip(CircleShape).pressScaleClickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(IdsIcons.Back, contentDescription = stringResource(R.string.back), modifier = Modifier.size(18.dp), tint = Ids.colors.textPrimary)
            }
        }
        Column(modifier = Modifier.padding(horizontal = Ids.layout.screenHorizontal)) {
            Text(
                stringResource(R.string.account_manage_caption, formattedAccountNumber),
                color = Ids.colors.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(bottom = 24.dp),
            )

            FlatSection(title = stringResource(R.string.account_manage_section_account), rows = listOf(
                FlatRow(title = stringResource(R.string.account_manage_debit_card), showChevron = true, onClick = onOpenCard),
            ))
            SectionSpacer()
            FlatSection(title = stringResource(R.string.account_manage_section_security), rows = listOf(
                FlatRow(title = stringResource(R.string.account_manage_devices), showChevron = true, onClick = onOpenDevices),
            ))
            SectionSpacer()
            FlatSection(title = stringResource(R.string.account_manage_section_transfer), rows = listOf(
                FlatRow(title = stringResource(R.string.account_manage_auto_transfer), showChevron = true, onClick = onOpenAutoTransfer),
                FlatRow(title = stringResource(R.string.account_manage_scheduled_transfers), showChevron = true, onClick = onOpenScheduledTransfers),
            ))
            SectionSpacer()
            FlatSection(title = stringResource(R.string.account_manage_section_foreign_currency), rows = listOf(
                FlatRow(title = stringResource(R.string.account_manage_exchange_rates), showChevron = true, onClick = onOpenForeignCurrency),
            ))
            SectionSpacer()
            FlatSection(title = stringResource(R.string.account_manage_section_taxes_bills), rows = listOf(
                FlatRow(title = stringResource(R.string.account_manage_pay_taxes_bills), showChevron = true, onClick = onOpenBills),
            ))
            SectionSpacer()
            FlatSection(title = stringResource(R.string.account_manage_section_support), rows = listOf(
                FlatRow(title = stringResource(R.string.account_manage_get_help), showChevron = true, onClick = onOpenSupport),
            ))

            Text(
                stringResource(R.string.account_manage_disclosure),
                color = Ids.colors.textTertiary, fontSize = 11.sp,
                modifier = Modifier.padding(top = 28.dp, bottom = 24.dp),
            )
        }
    }
}

@Composable
private fun SectionSpacer() {
    Box(modifier = Modifier.padding(top = 20.dp))
}
