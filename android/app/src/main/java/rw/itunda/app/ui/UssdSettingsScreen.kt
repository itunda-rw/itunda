package rw.itunda.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import retrofit2.HttpException
import rw.itunda.app.R
import rw.itunda.core.designsystem.components.BackTopBar
import rw.itunda.core.designsystem.components.IdsButton
import rw.itunda.core.designsystem.components.IdsTextField
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.SetUssdPinRequest
import rw.itunda.core.network.superAppErrorMessage
import java.io.IOException

// Real USSD basic-banking access (item 231, rw.itunda.ussd) -- the real menu, PIN
// check, and money transfer are fully built and working on the backend; only the real
// MNO/telco short-code partnership needed to dial *XXX# is missing, the same honest
// limitation ID verification has. This is the smartphone-side companion screen: a
// real, separate 4-6 digit PIN (not the account password, M-Pesa-style convention,
// see UssdPin.kt's own doc comment) a user sets here so they can later use any basic
// phone. First Android client for this -- bank-mfe's UssdSettingsView.tsx shipped
// first; content/copy mirrored from it 1:1.
@Composable
fun UssdSettingsScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun submit() {
        if (!Regex("^\\d{4,6}$").matches(pin)) {
            error = "PIN must be 4-6 digits."
            return
        }
        if (pin != confirmPin) {
            error = "PINs did not match."
            return
        }
        submitting = true
        error = null
        success = false
        scope.launch {
            try {
                NetworkClient.apiService.setUssdPin(SetUssdPinRequest(pin))
                pin = ""
                confirmPin = ""
                success = true
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                submitting = false
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(20.dp)) {
            BackTopBar(title = stringResource(R.string.ussd_settings_title), onBack = onBack)
        }
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.ussd_settings_description), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.ussd_settings_honest_scope), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    IdsTextField(
                        value = pin, onValueChange = { pin = it.filter(Char::isDigit).take(6) },
                        label = stringResource(R.string.ussd_settings_pin_placeholder), isPassword = true,
                        keyboardType = KeyboardType.NumberPassword, modifier = Modifier.fillMaxWidth(),
                    )
                    IdsTextField(
                        value = confirmPin, onValueChange = { confirmPin = it.filter(Char::isDigit).take(6) },
                        label = stringResource(R.string.ussd_settings_confirm_pin_placeholder), isPassword = true,
                        keyboardType = KeyboardType.NumberPassword, modifier = Modifier.fillMaxWidth(),
                    )
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    if (success) {
                        Text(stringResource(R.string.ussd_settings_saved), color = Ids.colors.success, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(4.dp))
                    IdsButton(
                        text = if (submitting) stringResource(R.string.ussd_settings_saving) else stringResource(R.string.ussd_settings_submit),
                        enabled = !submitting,
                        onClick = { submit() },
                    )
                }
            }
        }
    }
}
