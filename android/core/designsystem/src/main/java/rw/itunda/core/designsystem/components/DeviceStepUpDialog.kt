package rw.itunda.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import rw.itunda.core.designsystem.R
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.designsystem.theme.IdsIcons

/**
 * Real device binding step-up dialog (2026-07-21 port, promoted here 2026-09-02) --
 * shown wherever a money-moving call real-403s with DEVICE_NOT_VERIFIED. Re-proves
 * password ownership on THIS device and marks it trusted, matching the same real
 * re-verification Toss requires before a new device can move money. Mirrors
 * bank-mfe's DeviceStepUpPrompt (BankDashboard.tsx) exactly.
 *
 * Promoted from :features:payments:impl/TransferFlow.kt into :core:designsystem so
 * :app's DeviceStepUpHost.kt (which wraps this with the real biometric-first +
 * password-fallback flow) can be shared by any Feature module, not just ones that
 * can afford a direct :features:payments:impl dependency (which Konsist's
 * feature-isolation test forbids as a cross-Feature impl-to-impl import). Not
 * transfer-specific despite its original name/location -- also used by Gift
 * send/claim, Commerce/Eats checkout, Stocks buy/sell (see DeviceStepUpHost.kt's own
 * doc comment).
 */
@Composable
fun DeviceStepUpDialog(
    busy: Boolean,
    error: String?,
    onVerify: (password: String) -> Unit,
    onCancel: () -> Unit,
) {
    var password by rememberSaveable { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(80)
        focusRequester.requestFocus()
    }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    val passwordDescription = stringResource(R.string.device_step_up_password_description)
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.device_step_up_title), color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    stringResource(R.string.device_step_up_body),
                    color = Ids.colors.textSecondary,
                    fontSize = 13.sp,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Ids.colors.surfaceSoft, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    BasicTextField(
                        value = password,
                        onValueChange = { password = it },
                        textStyle = TextStyle(color = Ids.colors.textPrimary, fontSize = 16.sp),
                        cursorBrush = SolidColor(Ids.colors.brand),
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
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
                            if (passwordVisible) IdsIcons.EyeOff else IdsIcons.Eye,
                            contentDescription = if (passwordVisible) stringResource(R.string.device_step_up_hide_password) else stringResource(R.string.device_step_up_show_password),
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
                Text(if (busy) stringResource(R.string.device_step_up_verifying) else stringResource(R.string.device_step_up_verify), color = Ids.colors.brand, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onCancel, enabled = !busy) {
                Text(stringResource(R.string.device_step_up_cancel), color = Ids.colors.textSecondary)
            }
        },
        containerColor = Ids.colors.surface,
    )
}
