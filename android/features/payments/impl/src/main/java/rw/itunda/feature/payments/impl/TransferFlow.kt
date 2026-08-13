package rw.itunda.feature.payments.impl

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.ArrowBackIosNew
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import rw.itunda.core.designsystem.components.EmptyState
import rw.itunda.core.designsystem.components.IdsButton
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
data class ContactUi(val name: String, val phoneNumber: String, val bank: String)

// Real Toss 사기계좌 조회-style pre-transfer warning (2026-07-31) -- see
// rw.itunda.p2p.ScamReportService's own doc comment on the backend. A warning, not a
// hard block, same as bank-mfe's own ReportScamLink/scamCheck (BankDashboard.tsx):
// itunda has no fraud-reimbursement protection scheme to withdraw for proceeding
// anyway, so this is simply the sender's own informed choice. Plain UI-facing shape,
// same "keep this feature module independent of :app's NetworkClient" convention
// ContactUi above already establishes -- the actual check/report calls happen in
// ItundaAppScreen.kt.
data class ScamWarningUi(val reportCount: Int)

@Composable
fun RecipientEntryScreen(
    onBack: () -> Unit,
    onNext: (accountNumber: String) -> Unit,
    contacts: List<ContactUi> = emptyList(),
    onAddContact: (name: String, phoneNumber: String) -> Unit = { _, _ -> },
) {
    var accountNumber by rememberSaveable { mutableStateOf("") }
    var showAddContactForm by rememberSaveable { mutableStateOf(false) }
    var newContactName by rememberSaveable { mutableStateOf("") }
    var newContactPhone by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ids.colors.background)
    ) {
        FlowTopBar(onBack)

        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Text(
                stringResource(R.string.transfer_recipient_headline),
                color = Ids.colors.textPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 34.sp
            )
            Spacer(modifier = Modifier.height(28.dp))
            // Copy widened 2026-07-20: this same digit keypad now also accepts a real
            // phone number (a local "07XXXXXXXX" or international "2507XXXXXXXX" shape
            // is recognized and normalized client-side -- see MainViewModel.
            // normalizeRecipientIdentifier), not just an account number, now that
            // MainViewModel.sendTransfer calls the real rw.itunda.p2p.sendDirect.
            Text(stringResource(R.string.transfer_recipient_input_label), color = Ids.colors.brand, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(6.dp))
            // Fixed (2026-07-11): the only form input in this app had no accessible
            // label at all -- BasicTextField, unlike a View-based TextInputLayout,
            // doesn't auto-associate the visible "Enter account number" Text() above
            // it (Compose doesn't merge sibling composables into one accessible node
            // unless told to), so TalkBack announced this as a bare, unlabeled edit
            // field. docs/ACCESSIBILITY.md flagged form labels as an open, unaudited
            // item -- this was the field that audit needed to find.
            val recipientInputDescription = stringResource(R.string.transfer_recipient_input_description)
            BasicTextField(
                value = accountNumber,
                onValueChange = { input -> accountNumber = input.filter { it.isDigit() }.take(16) },
                textStyle = TextStyle(color = Ids.colors.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Ids.colors.brand),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = recipientInputDescription }
            )
            Spacer(modifier = Modifier.height(8.dp))
            androidx.compose.material3.Divider(color = Ids.colors.brand, thickness = 2.dp)

            Spacer(modifier = Modifier.height(28.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { }
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(stringResource(R.string.transfer_select_bank), color = Ids.colors.textTertiary, fontSize = 17.sp)
                    // Real Toss auto-detects the bank from the account number's real
                    // BIN registry -- itunda has no such registry to check against, so
                    // this doesn't claim to (2026-07-12 fix: the previous copy here,
                    // "We'll find the bank once you enter the account number," implied
                    // detection that was never wired to anything -- the backend's
                    // transfer/quote endpoint takes a single opaque `recipient` string,
                    // not a resolved bank). A picker is a real, honest affordance for a
                    // future release; for now this is just a label.
                    Text(
                        stringResource(R.string.transfer_select_bank_hint),
                        color = Ids.colors.textTertiary,
                        fontSize = 13.sp
                    )
                }
                Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = Ids.colors.textTertiary)
            }

            // Added 2026-07-11 against a real Toss reference screenshot found this
            // session (user-provided, 2026-07-10, matching this file's own header) --
            // the real "어디로 돈을 보낼까요?" recipient screen leads with a "최근 보낸
            // 계좌" (recently sent accounts) list above manual account entry, which
            // this screen didn't have. Purely additive: the existing manual-entry
            // flow (already real, tested, and wired to the biometric-gated confirm
            // step) is unchanged, this just adds the shortcut the reference shows.
            // "TUYIZERE Eric" / BK is the same demo recipient identity already used
            // in ItundaAppScreen.kt's CashbackChanceCard -- and is, per that
            // screenshot's own visible "TUYIZERE E" recent-recipient row, the real
            // reference identity, not an arbitrary placeholder.
            // Real saved contacts (2026-07-22), replacing the single hardcoded demo
            // row this section used to show -- see rw.itunda.contacts.
            // ContactsController's own doc comment; this was a real, live-fetched
            // GET /api/v1/contacts with zero client UI anywhere until now.
            if (accountNumber.isEmpty()) {
                Spacer(modifier = Modifier.height(28.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.transfer_contacts_label), color = Ids.colors.textSecondary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (showAddContactForm) stringResource(R.string.transfer_cancel) else stringResource(R.string.transfer_add_contact),
                        color = Ids.colors.brand,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable { showAddContactForm = !showAddContactForm },
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                if (showAddContactForm) {
                    val contactNamePlaceholder = stringResource(R.string.transfer_contact_name_placeholder)
                    val contactNameDescription = stringResource(R.string.transfer_contact_name_description)
                    BasicTextField(
                        value = newContactName,
                        onValueChange = { newContactName = it },
                        textStyle = TextStyle(color = Ids.colors.textPrimary, fontSize = 16.sp),
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = contactNameDescription },
                        decorationBox = { inner -> if (newContactName.isEmpty()) Text(contactNamePlaceholder, color = Ids.colors.textTertiary, fontSize = 16.sp); inner() },
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val contactPhonePlaceholder = stringResource(R.string.transfer_contact_phone_placeholder)
                    val contactPhoneDescription = stringResource(R.string.transfer_contact_phone_description)
                    BasicTextField(
                        value = newContactPhone,
                        onValueChange = { input -> newContactPhone = input.filter { it.isDigit() || it == '+' } },
                        textStyle = TextStyle(color = Ids.colors.textPrimary, fontSize = 16.sp),
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = contactPhoneDescription },
                        decorationBox = { inner -> if (newContactPhone.isEmpty()) Text(contactPhonePlaceholder, color = Ids.colors.textTertiary, fontSize = 16.sp); inner() },
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.transfer_save_contact),
                        color = if (newContactName.isNotBlank() && newContactPhone.isNotBlank()) Ids.colors.brand else Ids.colors.textTertiary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable(enabled = newContactName.isNotBlank() && newContactPhone.isNotBlank()) {
                            onAddContact(newContactName, newContactPhone)
                            newContactName = ""; newContactPhone = ""; showAddContactForm = false
                        },
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
                if (contacts.isEmpty() && !showAddContactForm) {
                    EmptyState(stringResource(R.string.transfer_no_contacts), icon = Icons.Outlined.PersonOutline)
                } else {
                    contacts.forEach { contact ->
                        RecentRecipientRow(name = contact.name, bankAndAccount = "${contact.bank} - ${contact.phoneNumber}") {
                            accountNumber = contact.phoneNumber.filter { it.isDigit() }.take(16)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        if (accountNumber.length >= 4) {
            FlowNextBar(enabled = true, label = stringResource(R.string.transfer_next)) { onNext(accountNumber) }
        }
        NumericKeypad(
            onDigit = { d -> if (accountNumber.length < 16) accountNumber += d },
            onDelete = { if (accountNumber.isNotEmpty()) accountNumber = accountNumber.dropLast(1) }
        )
    }
}

@Composable
fun TransferAmountScreen(
    recipientAccountNumber: String,
    onBack: () -> Unit,
    onConfirm: (amountRwf: Long) -> Unit,
    // Real wallet balance + real in-flight state (2026-07-12) -- previously this
    // screen hardcoded "RWF 112,242" regardless of the actual signed-in user's
    // balance, and had no way to show that a real network call was in progress.
    availableBalance: Double = 0.0,
    isSubmitting: Boolean = false,
    scamWarning: ScamWarningUi? = null,
    scamReported: Boolean = false,
    onReportScam: () -> Unit = {},
) {
    // rememberSaveable (2026-07-12), same reasoning as ItundaAppScreen.kt's
    // TransferStep -- confirmed live on-device that without this, a process kill
    // mid-transfer restored the right screen but reset the typed amount to 0.
    var digits by rememberSaveable { mutableStateOf("") }
    val amount = digits.toLongOrNull() ?: 0L
    val availableBalanceLong = availableBalance.toLong()
    // Real gap found live (2026-08-10), applying Toss Tech's own "the best error is
    // one that never occurs" principle (toss.tech/article/21021): this screen already
    // knows the real balance (it renders it right above and even offers a "Max" chip
    // that fills it in exactly), yet previously let a too-large amount round-trip to
    // the backend's 422 before saying anything -- catch it here instead of after a
    // wasted network call.
    val insufficientBalance = amount > 0 && amount > availableBalanceLong

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ids.colors.background)
    ) {
        FlowTopBar(onBack)

        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            TransferPartyRow(
                label = stringResource(R.string.transfer_from_wallet),
                sublabel = stringResource(R.string.transfer_available_balance, rwfFormatter.format(availableBalanceLong)),
                icon = Icons.Outlined.AccountBalanceWallet
            )
            Spacer(modifier = Modifier.height(2.dp))
            Box(
                modifier = Modifier
                    .padding(start = 21.dp)
                    .width(2.dp)
                    .height(20.dp)
                    .background(Ids.colors.divider)
            )
            Spacer(modifier = Modifier.height(2.dp))
            TransferPartyRow(
                label = stringResource(R.string.transfer_to_account, recipientAccountNumber),
                sublabel = stringResource(R.string.transfer_new_recipient),
                icon = Icons.Outlined.Savings
            )
            if (scamWarning != null && scamWarning.reportCount > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Ids.colors.dangerTint)
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text(stringResource(R.string.transfer_scam_warning_title), color = Ids.colors.danger, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(R.string.transfer_scam_warning_body, scamWarning.reportCount),
                        color = Ids.colors.danger,
                        fontSize = 12.sp,
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                if (scamReported) stringResource(R.string.transfer_scam_reported_thanks) else stringResource(R.string.transfer_report_scam),
                color = Ids.colors.textTertiary,
                fontSize = 12.sp,
                modifier = Modifier.clickable(enabled = !scamReported, onClick = onReportScam),
            )
        }

        Spacer(modifier = Modifier.height(40.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(stringResource(R.string.transfer_amount_question), color = Ids.colors.textSecondary, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = if (digits.isEmpty()) "0 RWF" else "${rwfFormatter.format(amount)} RWF",
                fontSize = if (digits.isEmpty()) 32.sp else 42.sp,
                fontWeight = FontWeight.Bold,
                color = if (digits.isEmpty()) Ids.colors.textTertiary else Ids.colors.textPrimary,
                textAlign = TextAlign.Center
            )
            if (insufficientBalance) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    stringResource(R.string.transfer_amount_insufficient, rwfFormatter.format(availableBalanceLong)),
                    color = Ids.colors.danger,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            QuickAmountChip("+10,000") { digits = ((digits.toLongOrNull() ?: 0L) + 10_000L).toString() }
            QuickAmountChip("+100,000") { digits = ((digits.toLongOrNull() ?: 0L) + 100_000L).toString() }
            QuickAmountChip(stringResource(R.string.transfer_amount_max)) { digits = availableBalanceLong.toString() }
        }

        if (isSubmitting) {
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                androidx.compose.material3.CircularProgressIndicator(color = Ids.colors.brand)
            }
        } else {
            FlowNextBar(enabled = digits.isNotEmpty() && amount > 0 && !insufficientBalance, label = stringResource(R.string.transfer_send)) { onConfirm(amount) }
            NumericKeypad(
                onDigit = { d -> if (digits.length < 9) digits += d },
                onDelete = { if (digits.isNotEmpty()) digits = digits.dropLast(1) }
            )
        }
    }
}

/**
 * Real device binding step-up dialog (2026-07-21 port) -- shown wherever a
 * money-moving call real-403s with DEVICE_NOT_VERIFIED. Re-proves password ownership
 * on THIS device (resolved server-side from the caller's own JWT, never a
 * client-supplied id) and marks it trusted, matching the same real re-verification
 * Toss requires before a new device can move money. Mirrors bank-mfe's
 * DeviceStepUpPrompt (BankDashboard.tsx) exactly -- same copy, same shape (password
 * field, Cancel/Verify), ported to Compose rather than reinvented.
 */
@Composable
fun DeviceStepUpDialog(
    busy: Boolean,
    error: String?,
    onVerify: (password: String) -> Unit,
    onCancel: () -> Unit,
) {
    var password by rememberSaveable { mutableStateOf("") }
    // Real "Minimum Input" simplicity fix (docs/DESIGN_REFERENCES.md §11, rule #4): this
    // password field is the sole meaningful action on the entire dialog -- exactly the
    // "obvious, sole next action on its screen" case that criterion asks for. Matches the
    // same delay(80) timing fix LoginScreen.kt's own rememberAutoFocus documents ("requesting
    // focus in the same frame a composable enters can silently no-op if the node hasn't
    // attached yet").
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(80)
        focusRequester.requestFocus()
    }
    // Real "Minimum Input" simplicity addition (docs/DESIGN_REFERENCES.md §11/§12), matching
    // the identical same-day fix on the shared IdsTextField's own isPassword mode: a local
    // UI-only affordance, not a security control.
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    val passwordDescription = stringResource(R.string.transfer_password_description)
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.transfer_device_verify_title), color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    stringResource(R.string.transfer_device_verify_body),
                    color = Ids.colors.textSecondary,
                    fontSize = 13.sp,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Ids.colors.surfaceSoft, RoundedCornerShape(10.dp)),
                    contentAlignment = androidx.compose.ui.Alignment.CenterEnd,
                ) {
                    BasicTextField(
                        value = password,
                        onValueChange = { password = it },
                        textStyle = TextStyle(color = Ids.colors.textPrimary, fontSize = 16.sp),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(Ids.colors.brand),
                        visualTransformation = if (passwordVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        // Real bug found live (2026-08-10), directly from the user hitting
                        // it while trying to send money: this field had no KeyboardOptions
                        // at all, so it fell back to a plain KeyboardType.Text field. The
                        // device's own keyboard (autocorrect/auto-capitalize-first-letter,
                        // real Samsung Keyboard behavior on this exact test device) is then
                        // free to silently mutate what's typed -- invisible here since the
                        // field is masked with dots, so a genuinely-correct password could
                        // reach the server altered and real-401 as "Incorrect password."
                        // KeyboardType.Password is what actually tells the platform IME to
                        // suppress autocorrect/suggestions for this field, matching the
                        // real, already-correct convention LoginScreen.kt's own password
                        // field (via IdsTextField's isPassword mode) already established.
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp, horizontal = 12.dp)
                            .padding(end = 36.dp)
                            .focusRequester(focusRequester)
                            .semantics { contentDescription = passwordDescription }
                    )
                    IconButton(
                        onClick = { passwordVisible = !passwordVisible },
                        modifier = Modifier.padding(end = 4.dp),
                    ) {
                        Icon(
                            if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            contentDescription = if (passwordVisible) stringResource(R.string.transfer_hide_password) else stringResource(R.string.transfer_show_password),
                            tint = Ids.colors.textTertiary,
                        )
                    }
                }
                if (error != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(error, color = Ids.colors.danger, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = { onVerify(password) }, enabled = !busy && password.isNotEmpty()) {
                Text(if (busy) stringResource(R.string.transfer_verifying) else stringResource(R.string.transfer_verify_device), color = Ids.colors.brand, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onCancel, enabled = !busy) {
                Text(stringResource(R.string.transfer_cancel), color = Ids.colors.textSecondary)
            }
        },
        containerColor = Ids.colors.surface,
    )
}

@Composable
internal fun FlowTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.ArrowBackIosNew, contentDescription = "Back", modifier = Modifier.size(18.dp), tint = Ids.colors.textPrimary)
        }
    }
}

/**
 * A recent-recipient shortcut row, matching the real reference screenshot's
 * "최근 보낸 계좌" (recently sent accounts) list -- a circular initial avatar,
 * name, and bank/account line, tappable to prefill the account number field
 * above rather than typing it manually.
 */
@Composable
private fun RecentRecipientRow(name: String, bankAndAccount: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(44.dp).clip(CircleShape).background(Ids.colors.chip),
            contentAlignment = Alignment.Center
        ) {
            Text(name.take(1), color = Ids.colors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text(name, color = Ids.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(bankAndAccount, color = Ids.colors.textTertiary, fontSize = 13.sp)
        }
    }
}

@Composable
internal fun TransferPartyRow(label: String, sublabel: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(label, color = Ids.colors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(sublabel, color = Ids.colors.textTertiary, fontSize = 13.sp)
        }
        Box(
            modifier = Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(Ids.colors.chip),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = Ids.colors.textPrimary)
        }
    }
}

@Composable
internal fun QuickAmountChip(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Ids.colors.chip)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(label, color = Ids.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

// Real correction (2026-08-13, direct user-provided real Toss screenshots: Split
// bill, top-up/충전, "Enter workplace name"): this bespoke bar used a flat neutral
// grey for its disabled state and floated as an inset, rounded, margined card even
// though every screen that calls it (this file's amount/recipient steps,
// SavingsAmountScreen's deposit step) already shows a permanently-visible custom
// keypad below it -- exactly the real Toss "money amount entry" context the
// screenshots show, where the confirm bar sits flush and edge-to-edge directly on
// top of the keypad. Delegates to IdsButton instead: its disabled state is already a
// dim tint of the real brand blue (not neutral grey, see IdsButton's own doc comment
// citing its own separate real Toss screenshot), and it already has the real
// press-scale micro-interaction -- this bar was quietly missing both by not using
// the shared component at all.
@Composable
internal fun FlowNextBar(enabled: Boolean, label: String, onClick: () -> Unit) {
    IdsButton(
        text = label,
        onClick = onClick,
        enabled = enabled,
        shape = androidx.compose.ui.graphics.RectangleShape,
    )
}

@Composable
internal fun NumericKeypad(onDigit: (String) -> Unit, onDelete: () -> Unit) {
    val keys = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("00", "0", "DEL")
    )
    val deleteDescription = stringResource(R.string.transfer_delete_digit)
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        keys.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth().height(60.dp)) {
                row.forEach { key ->
                    // Real Toss micro-interaction (2026-08-13, direct user request:
                    // "toss made keypad, button have interactions as well") -- same
                    // press-scale IdsButton/IdsIconButton already use, applied here so
                    // this keypad -- the actual real Toss-style custom keypad, unlike
                    // the plain system IME used everywhere else -- gets the same
                    // tactile feedback instead of a bare default ripple.
                    val interactionSource = remember { MutableInteractionSource() }
                    val pressScale = rw.itunda.core.designsystem.components.rememberPressScale(interactionSource)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .scale(pressScale)
                            .clickable(
                                interactionSource = interactionSource,
                                indication = LocalIndication.current,
                            ) { if (key == "DEL") onDelete() else onDigit(key) }
                            // Digit keys' visible text is already their own accessible
                            // name; DEL's "⌫" glyph is not, so it needs an explicit one
                            // -- same reasoning as TopIconButton's fix elsewhere.
                            .then(if (key == "DEL") Modifier.semantics { contentDescription = deleteDescription } else Modifier),
                        contentAlignment = Alignment.Center
                    ) {
                        if (key == "DEL") {
                            Text("⌫", fontSize = 22.sp, color = Ids.colors.textPrimary)
                        } else {
                            Text(key, fontSize = 24.sp, fontWeight = FontWeight.Medium, color = Ids.colors.textPrimary)
                        }
                    }
                }
            }
        }
    }
}
