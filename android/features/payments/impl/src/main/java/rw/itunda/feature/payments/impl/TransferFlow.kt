package rw.itunda.feature.payments.impl

import androidx.compose.foundation.LocalIndication
import rw.itunda.core.designsystem.components.IdsLoading
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import rw.itunda.core.designsystem.components.pressScaleClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Savings
import rw.itunda.core.designsystem.components.EmptyState
import rw.itunda.core.designsystem.components.IdsButton
import rw.itunda.core.designsystem.itundaface.GiftThemeGlyph
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.designsystem.theme.IdsIcons
import java.text.NumberFormat
import java.util.Locale

/**
 * A real, from-scratch transfer flow built directly against the actual
 * Toss reference screenshots (user-provided, 2026-07-10) rather than
 * incrementally patching the earlier ported RecipientScreen/TransferScreen,
 * which were deleted -- this replaces them.
 *
 * Two screens, matching the reference exactly:
 *  - RecipientEntryScreen: "어떤 계좌로 보낼까요?" -- account number input,
 *    bank selector, numeric keypad, no chrome beyond a back arrow.
 *  - TransferAmountScreen: "얼마나 보낼까요?" -- from/to account summary with
 *    a connector line, a headline that goes from muted to bold once an
 *    amount is entered, quick-amount chips, a Next bar, and a keypad.
 *
 * Correction (2026-08-08, found stale while localizing this file): the paragraph below
 * describing this as "a UI shell, not wired to the backend" is out of date and was left
 * un-updated after this flow was actually wired up. `ItundaAppScreen.kt` now calls both
 * screens with real callbacks into `MainViewModel.sendTransfer` (real
 * `rw.itunda.p2p.sendDirect`), real device step-up, real biometric confirmation, and a
 * real scam-check warning -- this is itunda's actual, live send-money flow, not a shell.
 * Kept the original paragraph below for the historical "why these two screens exist"
 * account, since that part is still accurate.
 *
 * Originally backed by local state only, not TransferService -- itunda had no login
 * flow yet (see MainViewModel.kt / NetworkClient.kt) at the time this was written, so
 * there was no real session to quote a transfer against.
 */

internal val rwfFormatter = NumberFormat.getNumberInstance(Locale.US)

// Real saved-contacts list (2026-07-22) -- see rw.itunda.contacts.ContactsController's
// own doc comment on the backend. This tiny UI-facing shape (not the app module's own
// ContactDto) keeps this feature module's existing independence from :app's
// NetworkClient -- the actual fetch/add calls happen in ItundaAppScreen.kt, which
// already has API access, and are passed down here as plain data + callbacks.
data class ContactUi(
    val name: String,
    val phoneNumber: String,
    val bank: String,
    // Real per-contact avatar (2026-08-14) -- already stored server-side
    // (ContactRepository's own color/letter columns, set at creation time in
    // ContactsController.addContact) but never plumbed past ContactDto until the
    // Friends-tab redesign below needed a real colored avatar to match Talk's own
    // FriendsView row style instead of RecentRecipientRow's flat single-tone circle.
    val color: String = "#F5FAFF",
    val letter: String = "?",
)

// Real Toss 사기계좌 조회-style pre-transfer warning (2026-07-31) -- see
// rw.itunda.p2p.ScamReportService's own doc comment on the backend. A warning, not a
// hard block, same as bank-mfe's own ReportScamLink/scamCheck (BankDashboard.tsx):
// itunda has no fraud-reimbursement protection scheme to withdraw for proceeding
// anyway, so this is simply the sender's own informed choice. Plain UI-facing shape,
// same "keep this feature module independent of :app's NetworkClient" convention
// ContactUi above already establishes -- the actual check/report calls happen in
// ItundaAppScreen.kt.
data class ScamWarningUi(val reportCount: Int)


// Real Toss TDS ProgressStepper (docs/UI_UX_GUIDELINES.md product-feel research) -- see
// IdsProgressStepper.kt's own doc comment. Mirrors bank-mfe's identical
// TRANSFER_STEP_LABELS in BankDashboard.tsx (Recipient/Amount/Confirm) minus the
// Confirm step -- Android's own real send-money flow has no separate confirm SCREEN
// (confirmation happens via the biometric prompt overlaid on the Amount screen itself,
// not a third screen), so a 2-step indicator honestly matches this platform's real
// step count rather than forcing artificial parity with web's 3-step shape.
internal val TRANSFER_STEP_LABELS = listOf("Recipient", "Amount")



