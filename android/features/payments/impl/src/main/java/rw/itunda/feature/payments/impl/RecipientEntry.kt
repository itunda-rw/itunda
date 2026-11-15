package rw.itunda.feature.payments.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.EmptyState
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.designsystem.theme.IdsIcons

/**
 * Prototype "Send to" screen (v1038): one screen, no tabs. A single search field
 * takes a name or a phone/account number; saved contacts list underneath; Next at
 * the bottom lights up once the field holds a number of at least 4 digits. This
 * replaces the earlier Friends/Account tab pair + on-screen keypad -- typing an
 * account number into the search field covers the same case, and the same
 * `onNext(digits)` contract (and `normalizeRecipientIdentifier` downstream) is kept.
 */
@Composable
fun RecipientEntryScreen(
    onBack: () -> Unit,
    onNext: (accountNumber: String) -> Unit,
    contacts: List<ContactUi> = emptyList(),
    onAddContact: (name: String, phoneNumber: String) -> Unit = { _, _ -> },
    activeStepIndex: Int = 0,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var showAddContactForm by rememberSaveable { mutableStateOf(false) }
    var newContactName by rememberSaveable { mutableStateOf("") }
    var newContactPhone by rememberSaveable { mutableStateOf("") }

    val typedDigits = if (query.isNotBlank() && query.all { it.isDigit() || it == '+' || it == ' ' }) {
        query.filter { it.isDigit() }.take(16)
    } else {
        ""
    }
    val canContinue = typedDigits.length >= 4
    val filtered = if (query.isBlank() || canContinue) contacts else contacts.filter { it.name.contains(query, ignoreCase = true) }

    Column(modifier = Modifier.fillMaxSize().background(Ids.colors.background)) {
        FlowTopBar(onBack)
        Text(
            "Send to", color = Ids.colors.textPrimary, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(horizontal = Ids.layout.screenHorizontal, vertical = 8.dp),
        )
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(8.dp))
            val description = stringResource(R.string.transfer_search_friends_description)
            val placeholder = stringResource(R.string.transfer_search_recipient_placeholder)
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Ids.colors.surfaceSoft)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(IdsIcons.Search, contentDescription = null, tint = Ids.colors.textTertiary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
                BasicTextField(
                    value = query,
                    onValueChange = { query = it.take(40) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    textStyle = TextStyle(color = Ids.colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
                    cursorBrush = SolidColor(Ids.colors.brand),
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = description },
                    decorationBox = { inner ->
                        if (query.isEmpty()) Text(placeholder, color = Ids.colors.textTertiary, fontSize = 18.sp)
                        inner()
                    },
                )
            }
            Spacer(Modifier.height(16.dp))
            if (filtered.isEmpty() && !showAddContactForm && !canContinue) {
                EmptyState(stringResource(R.string.transfer_no_contacts), icon = Icons.Outlined.PersonOutline)
            } else {
                filtered.forEach { c -> FriendRecipientRow(c) { onNext(c.phoneNumber.filter { it.isDigit() }.take(16)) } }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                if (showAddContactForm) stringResource(R.string.transfer_cancel) else stringResource(R.string.transfer_add_contact),
                color = Ids.colors.brand, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.pressScaleClickable { showAddContactForm = !showAddContactForm }.padding(vertical = 8.dp),
            )
            if (showAddContactForm) {
                NewContactForm(
                    name = newContactName, onName = { newContactName = it },
                    phone = newContactPhone, onPhone = { v -> newContactPhone = v.filter { it.isDigit() || it == '+' } },
                ) {
                    onAddContact(newContactName, newContactPhone)
                    newContactName = ""; newContactPhone = ""; showAddContactForm = false
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FlowNextBar(enabled = canContinue, label = stringResource(R.string.transfer_next)) { onNext(typedDigits) }
        }
    }
}

@Composable
private fun NewContactForm(name: String, onName: (String) -> Unit, phone: String, onPhone: (String) -> Unit, onSave: () -> Unit) {
    val namePlaceholder = stringResource(R.string.transfer_contact_name_placeholder)
    val nameDescription = stringResource(R.string.transfer_contact_name_description)
    val phonePlaceholder = stringResource(R.string.transfer_contact_phone_placeholder)
    val phoneDescription = stringResource(R.string.transfer_contact_phone_description)
    val style = TextStyle(color = Ids.colors.textPrimary, fontSize = 16.sp)
    BasicTextField(
        value = name, onValueChange = onName, textStyle = style,
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = nameDescription },
        decorationBox = { inner -> if (name.isEmpty()) Text(namePlaceholder, color = Ids.colors.textTertiary, fontSize = 16.sp); inner() },
    )
    Spacer(Modifier.height(8.dp))
    BasicTextField(
        value = phone, onValueChange = onPhone, textStyle = style,
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = phoneDescription },
        decorationBox = { inner -> if (phone.isEmpty()) Text(phonePlaceholder, color = Ids.colors.textTertiary, fontSize = 16.sp); inner() },
    )
    Spacer(Modifier.height(8.dp))
    val ready = name.isNotBlank() && phone.isNotBlank()
    Text(
        stringResource(R.string.transfer_save_contact),
        color = if (ready) Ids.colors.brand else Ids.colors.textTertiary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.pressScaleClickable(enabled = ready, onClick = onSave),
    )
}
