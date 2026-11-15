package rw.itunda.feature.pay.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlinx.coroutines.launch
import rw.itunda.core.designsystem.components.IdsButton
import rw.itunda.core.designsystem.components.IdsButtonSize
import rw.itunda.core.designsystem.components.IdsButtonVariant
import rw.itunda.core.designsystem.components.IdsAppBarTitle
import rw.itunda.core.designsystem.components.IdsIconButton
import rw.itunda.core.designsystem.components.dashedBorder
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.designsystem.theme.IdsIcons

// Real fix (2026-08-11, itunda Pay research pass -- user-provided KakaoPay/Toss
// Pay screenshots): this whole tab was a decorative mockup -- zero calls to any
// real Pay backend endpoint. The REAL payment-collection UI (pay-by-code,
// pay-by-static-QR, Face Pay -- all genuinely wired to
// rw.itunda.merchant.MerchantService.collect) already existed, just misfiled
// inside the unrelated Shop feature where the Pay tab/Home QR icon could never
// reach it. This is that real UI, moved to where "Pay" actually means pay.
//
// Moved into :features:pay:impl (2026-09-02, Pay Feature-module decomposition,
// following the user's explicit "Continue into HomeTab next" direction which also
// covered Pay/Menu/My) -- three real prerequisites resolved first, see
// [[project_itunda_feature_isolation]]: DeviceStepUpDialog + LocalRealActivity +
// DeviceStepUpHost promoted to :core:designsystem, LedgerFormatting promoted to
// :core:designsystem, and PayAMerchantSection (owned by :features:shop:impl)
// switched to an injected composable slot rather than a direct cross-Feature
// import, matching the deviceStepUpHost pattern TalkTab already established.
enum class PayTabMode { MY_CODE, PAY_MERCHANT }

@Composable
fun PayEntryPoint(
    transactions: List<rw.itunda.core.network.TransactionDto>,
    onSend: () -> Unit,
    onCashOutAtAgent: () -> Unit,
    // Real narrowing (2026-09-02) -- was `onSwitchTab: (ItundaTab) -> Unit`,
    // taking :app's own `internal enum class ItundaTab` (ItundaAppScreen.kt),
    // an :app-only type a Feature module can't reference. Both real call sites
    // only ever switched to one fixed tab each, so two plain callbacks replace
    // the one generic one -- no behavior change, no type crossing the boundary.
    onOpenSettings: () -> Unit,
    onBrowseMerchants: () -> Unit,
    onOpenSupport: () -> Unit,
    onOpenCard: () -> Unit,
    payAMerchantSection: @Composable () -> Unit,
    onOpenRewardsMiniApp: () -> Unit,
    // Real IA-consistency fix (2026-09-11, product-completeness cycle 2 --
    // see project_itunda_pay_product_completeness memory): bank-mfe's own
    // PayHub.tsx already surfaces Request Money directly in the Pay tab
    // (RequestMoneyCard); Android only ever reached it via the Explore/Menu
    // tab. Reuses the existing RequestMoneyScreen (:app) -- no new screen or
    // duplicated logic, just a second, real navigation entry point matching
    // web's own placement.
    onOpenRequestMoney: () -> Unit = {},
) {
    ProtoPayTab(
        onOpenPay = {}, onSettings = onOpenSettings, onChangeFunding = onOpenCard,
        onReceive = onOpenRequestMoney, scanContent = payAMerchantSection,
    )
}
