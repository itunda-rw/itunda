package rw.itunda.feature.payments.impl

import androidx.compose.foundation.LocalIndication
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

// Real fix (2026-08-26): split out of TransferFlow.kt once that file grew past its
// file-size-lint baseline. These are the small, generic shared UI atoms other flow
// screens in this module reuse (internal visibility) -- FlowTopBar/FlowNextBar/
// NumericKeypad/QuickAmountChip/TransferPartyRow/FriendRecipientRow -- distinct from
// the two large screen composables (RecipientEntryScreen, TransferAmountScreen) and
// the DeviceStepUpDialog left behind. Same package, so zero import changes anywhere.

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
                .pressScaleClickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(IdsIcons.Back, contentDescription = "Back", modifier = Modifier.size(18.dp), tint = Ids.colors.textPrimary)
        }
    }
}

/**
 * A friend row for the Friends tab, matching the real Toss "친구" list: a real
 * per-contact colored avatar circle (the same color/letter ContactRepository already
 * stores per contact, see ContactUi's own doc comment) rather than one flat tone for
 * every row, name, and a phone-number subtitle. Tapping proceeds straight to the
 * amount step, same as the old RecentRecipientRow this replaces.
 */
@Composable
internal fun FriendRecipientRow(contact: ContactUi, onClick: () -> Unit) {
    val avatarColor = remember(contact.color) {
        runCatching { Color(android.graphics.Color.parseColor(contact.color)) }.getOrDefault(Color(0xFFF5FAFF))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressScaleClickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(44.dp).clip(CircleShape).background(avatarColor),
            contentAlignment = Alignment.Center
        ) {
            Text(contact.letter.ifBlank { contact.name.take(1) }, color = Ids.colors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text(contact.name, color = Ids.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(contact.phoneNumber, color = Ids.colors.textTertiary, fontSize = 13.sp)
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
            .pressScaleClickable(onClick = onClick)
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
                            // Real gap found live (2026-08-31, direct user correction:
                            // "backspace button of keyboard should be horizontal arrow
                            // (toss style) instead of those weird icons") -- see
                            // AmountKeypadInput.kt's identical fix for the full account.
                            // contentDescription null -- the parent Box above already
                            // carries deleteDescription via .semantics{}, a second one
                            // here would double-announce to screen readers.
                            Icon(IdsIcons.Back, contentDescription = null, modifier = Modifier.size(20.dp))
                        } else {
                            Text(key, fontSize = 24.sp, fontWeight = FontWeight.Medium, color = Ids.colors.textPrimary)
                        }
                    }
                }
            }
        }
    }
}
