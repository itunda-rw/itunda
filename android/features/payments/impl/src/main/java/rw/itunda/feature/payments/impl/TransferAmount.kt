package rw.itunda.feature.payments.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.IdsButton
import rw.itunda.core.designsystem.components.IdsLoading
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.itundaface.GiftThemeGlyph
import rw.itunda.core.designsystem.theme.Ids

/**
 * Prototype "How much to send?" (v1038): left-aligned title, a "To ..." line, the
 * amount large with a small RWF unit, balance after, +5,000 / +10,000 / Max chips,
 * a rounded CTA and the keypad. The real extras this screen already carried (scam
 * warning/report, gift mode, memo, in-flight state, insufficient-balance guard)
 * are kept, in the scrolling area between the amount and the chips.
 */
@Composable
fun TransferAmountScreen(
    recipientAccountNumber: String,
    onBack: () -> Unit,
    // GiftService only resolves recipients by phone number, so gift mode carries an explicit note.
    onConfirm: (amountRwf: Long, isGift: Boolean, note: String?, theme: String?) -> Unit,
    availableBalance: Double = 0.0,
    isSubmitting: Boolean = false,
    scamWarning: ScamWarningUi? = null,
    scamReported: Boolean = false,
    onReportScam: () -> Unit = {},
    // null keeps the generic main-account label; set when opened from a specific account.
    fromAccountName: String? = null,
    activeStepIndex: Int = 1,
) {
    // rememberSaveable: a process kill mid-transfer must not reset the typed amount.
    var digits by rememberSaveable { mutableStateOf("") }
    var isGift by rememberSaveable { mutableStateOf(false) }
    var giftNote by rememberSaveable { mutableStateOf("") }
    var giftTheme by rememberSaveable { mutableStateOf<String?>(null) }
    val amount = digits.toLongOrNull() ?: 0L
    val balance = availableBalance.toLong()
    // The best error is the one that never occurs: catch an over-balance amount here.
    val insufficientBalance = amount > 0 && amount > balance
    var reviewing by rememberSaveable { mutableStateOf(false) }
    fun add(n: Long) { digits = ((digits.toLongOrNull() ?: 0L) + n).toString().take(9) }

    if (reviewing) {
        androidx.activity.compose.BackHandler { reviewing = false }
        TransferReview(
            amount = amount, to = recipientAccountNumber, from = fromAccountName ?: stringResource(R.string.transfer_review_bank),
            isSubmitting = isSubmitting, onBack = { reviewing = false },
            onSend = { onConfirm(amount, isGift, giftNote.trim().ifEmpty { null }, giftTheme) },
        )
        return
    }
    Column(modifier = Modifier.fillMaxSize().background(Ids.colors.background)) {
        FlowTopBar(onBack)
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            Text(stringResource(R.string.transfer_amount_question), color = Ids.colors.textPrimary, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 8.dp))
            Text(
                stringResource(R.string.transfer_to_account, recipientAccountNumber) +
                    (fromAccountName?.let { " · $it" } ?: ""),
                color = Ids.colors.textSecondary, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp),
            )
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 28.dp)) {
                Text(
                    if (digits.isEmpty()) "0" else rwfFormatter.format(amount),
                    fontSize = 44.sp, fontWeight = FontWeight.ExtraBold,
                    color = if (digits.isEmpty()) Ids.colors.textTertiary else Ids.colors.textPrimary,
                )
                Text("RWF", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ids.colors.textSecondary, modifier = Modifier.padding(start = 10.dp, bottom = 8.dp))
            }
            if (insufficientBalance) {
                Text(stringResource(R.string.transfer_amount_insufficient, rwfFormatter.format(balance)), color = Ids.colors.danger, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            } else {
                Text(
                    stringResource(R.string.transfer_balance_after, rwfFormatter.format(balance - amount)),
                    color = Ids.colors.textSecondary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp),
                )
            }
            if (scamWarning != null && scamWarning.reportCount > 0) {
                Column(
                    modifier = Modifier.padding(top = 16.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .background(Ids.colors.dangerTint).padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Text(stringResource(R.string.transfer_scam_warning_title), color = Ids.colors.danger, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.transfer_scam_warning_body, scamWarning.reportCount), color = Ids.colors.danger, fontSize = 12.sp)
                }
            }
            Text(
                if (scamReported) stringResource(R.string.transfer_scam_reported_thanks) else stringResource(R.string.transfer_report_scam),
                color = Ids.colors.textTertiary, fontSize = 12.sp,
                modifier = Modifier.padding(top = 12.dp).pressScaleClickable(enabled = !scamReported, onClick = onReportScam),
            )
            TransferNoteSection(isGift, { isGift = it }, giftNote, { giftNote = it }, giftTheme, { giftTheme = it })
            Spacer(Modifier.height(12.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            QuickAmountChip("+5,000") { add(5_000L) }
            QuickAmountChip("+10,000") { add(10_000L) }
            QuickAmountChip(stringResource(R.string.transfer_amount_max)) { digits = balance.toString() }
        }
        if (isSubmitting) {
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) { IdsLoading() }
        } else {
            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                IdsButton(
                    text = stringResource(R.string.transfer_review),
                    onClick = { reviewing = true },
                    enabled = amount > 0 && !insufficientBalance,
                )
            }
            NumericKeypad(
                onDigit = { d -> if (digits.length < 9) digits += d },
                onDelete = { if (digits.isNotEmpty()) digits = digits.dropLast(1) },
            )
        }
    }
}

/** Gift toggle + the memo/gift-note field and gift theme chips. */
@Composable
private fun TransferNoteSection(
    isGift: Boolean, onGift: (Boolean) -> Unit,
    note: String, onNote: (String) -> Unit,
    theme: String?, onTheme: (String?) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 12.dp).pressScaleClickable { onGift(!isGift) },
    ) {
        androidx.compose.material3.Switch(checked = isGift, onCheckedChange = onGift)
        Spacer(Modifier.width(8.dp))
        GiftThemeGlyph(null, size = 16.dp)
        Spacer(Modifier.width(6.dp))
        Text(stringResource(R.string.transfer_gift_toggle), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Ids.colors.textPrimary)
    }
    if (isGift) {
        Text(stringResource(R.string.transfer_gift_note), fontSize = 12.sp, color = Ids.colors.textTertiary, modifier = Modifier.padding(top = 6.dp))
    }
    androidx.compose.material3.OutlinedTextField(
        value = note,
        onValueChange = onNote,
        placeholder = { Text(stringResource(if (isGift) R.string.transfer_gift_note_hint else R.string.transfer_memo_hint)) },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        singleLine = true,
    )
    if (isGift) {
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            rw.itunda.core.network.GIFT_THEME_LABELS.forEach { (key, label) ->
                androidx.compose.material3.FilterChip(
                    selected = theme == key,
                    onClick = { onTheme(if (theme == key) null else key) },
                    label = { Text(label.replaceFirst(Regex("^\\S+\\s*"), ""), fontSize = 12.sp) },
                    leadingIcon = { GiftThemeGlyph(key, size = 14.dp) },
                )
            }
        }
    }
}
