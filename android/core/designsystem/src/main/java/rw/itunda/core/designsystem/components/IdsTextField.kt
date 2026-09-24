package rw.itunda.core.designsystem.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.designsystem.theme.IdsComponentTokens
import rw.itunda.core.designsystem.theme.IdsIcons
import rw.itunda.core.designsystem.theme.IdsTypography

@Composable
fun IdsTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    isError: Boolean = false,
    errorText: String? = null,
    isSuccess: Boolean = false,
    successText: String? = null,
    supportingText: String? = null,
    singleLine: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardType: KeyboardType = KeyboardType.Text,
    trailingIcon: (@Composable () -> Unit)? = null,
    autoFocus: Boolean = false,
    isPassword: Boolean = false,
    readOnly: Boolean = false,
    isAmount: Boolean = false,
    loading: Boolean = false,
    enabled: Boolean = true,
) {
    val focusRequester = remember { FocusRequester() }
    var passwordVisible by remember { mutableStateOf(false) }

    val resolvedError = isError
    val resolvedSuccess = !resolvedError && isSuccess
    val fieldEnabled = enabled && !loading

    Column(modifier = modifier) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            enabled = fieldEnabled,
            label = { Text(label, style = IdsTypography.Body2) },
            placeholder = placeholder?.let {
                { Text(it, style = IdsTypography.Body1, color = Ids.colors.textTertiary) }
            },
            singleLine = singleLine,
            readOnly = readOnly,
            isError = resolvedError,
            visualTransformation = if (isPassword) {
                if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation()
            } else if (isAmount) {
                AmountVisualTransformation
            } else {
                visualTransformation
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = if (isAmount) KeyboardType.Number else keyboardType,
            ),
            trailingIcon = if (loading) {
                {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(IdsComponentTokens.TextField.passwordIconSize / 2),
                        color = Ids.colors.brand,
                        strokeWidth = 2.dp,
                    )
                }
            } else if (isPassword) {
                {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            if (passwordVisible) IdsIcons.EyeOff else IdsIcons.Eye,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password",
                            tint = Ids.colors.textTertiary,
                        )
                    }
                }
            } else {
                trailingIcon
            },
            textStyle = IdsTypography.Body1,
            shape = RoundedCornerShape(IdsComponentTokens.TextField.radius),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .semantics {
                    if (loading) {
                        stateDescription = "Loading"
                    }
                },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Ids.colors.surface,
                unfocusedContainerColor = Ids.colors.surfaceSoft,
                disabledContainerColor = Ids.colors.surfaceSoft,
                errorContainerColor = Ids.colors.surface,
                focusedIndicatorColor = when {
                    resolvedError -> Ids.colors.danger
                    resolvedSuccess -> Ids.colors.success
                    else -> Ids.colors.brand
                },
                unfocusedIndicatorColor = Color.Transparent,
                errorIndicatorColor = Ids.colors.danger,
                disabledIndicatorColor = Color.Transparent,
                focusedLabelColor = when {
                    resolvedError -> Ids.colors.danger
                    resolvedSuccess -> Ids.colors.success
                    else -> Ids.colors.brand
                },
                unfocusedLabelColor = Ids.colors.textSecondary,
                focusedTextColor = Ids.colors.textPrimary,
                unfocusedTextColor = Ids.colors.textPrimary,
                disabledTextColor = Ids.colors.textTertiary,
                cursorColor = Ids.colors.brand,
                errorCursorColor = Ids.colors.danger,
            ),
        )

        if (autoFocus) {
            LaunchedEffect(Unit) {
                delay(80)
                focusRequester.requestFocus()
            }
        }

        when {
            resolvedError && errorText != null -> {
                Text(
                    text = errorText,
                    style = IdsTypography.Typography7,
                    color = Ids.colors.danger,
                    modifier = Modifier.padding(
                        start = IdsComponentTokens.TextField.supportingTextStart,
                        top = IdsComponentTokens.TextField.supportingTextTop,
                    ),
                )
            }
            resolvedSuccess && successText != null -> {
                Text(
                    text = successText,
                    style = IdsTypography.Typography7,
                    color = Ids.colors.success,
                    modifier = Modifier.padding(
                        start = IdsComponentTokens.TextField.supportingTextStart,
                        top = IdsComponentTokens.TextField.supportingTextTop,
                    ),
                )
            }
            supportingText != null -> {
                Text(
                    text = supportingText,
                    style = IdsTypography.Typography7,
                    color = Ids.colors.textTertiary,
                    modifier = Modifier.padding(
                        start = IdsComponentTokens.TextField.supportingTextStart,
                        top = IdsComponentTokens.TextField.supportingTextTop,
                    ),
                )
            }
        }
    }
}
