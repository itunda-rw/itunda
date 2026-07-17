package rw.itunda.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import rw.itunda.app.network.AuthResult
import rw.itunda.app.network.SessionManager
import rw.itunda.core.designsystem.components.TdsButton
import rw.itunda.core.designsystem.theme.Tds
import rw.itunda.core.designsystem.theme.TdsTheme
import rw.itunda.core.designsystem.theme.TdsTypography

/**
 * The login/register screen this app never had (see SessionManager.kt) -- gates
 * ItundaAppScreen in MainActivity.kt behind a real authenticated session instead of
 * rendering the whole app unconditionally against services/backend's real
 * /api/v1/auth/register and /api/v1/auth/login.
 */
@Composable
fun LoginScreen(onLoggedIn: () -> Unit) {
    TdsTheme {
        var isRegisterMode by remember { mutableStateOf(false) }
        var phoneNumber by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var firstName by remember { mutableStateOf("") }
        var lastName by remember { mutableStateOf("") }
        var referralCode by remember { mutableStateOf("") }
        var isSubmitting by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()

        fun submit() {
            errorMessage = null
            isSubmitting = true
            scope.launch {
                val result = if (isRegisterMode) {
                    SessionManager.register(
                        phoneNumber, password, firstName, lastName,
                        referralCode = referralCode.trim().ifBlank { null },
                    )
                } else {
                    SessionManager.login(phoneNumber, password)
                }
                isSubmitting = false
                when (result) {
                    is AuthResult.Success -> onLoggedIn()
                    is AuthResult.Failure -> errorMessage = result.message
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Tds.colors.background)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(text = "itunda", style = TdsTypography.Title1, color = Tds.colors.textPrimary)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (isRegisterMode) "Create your account" else "Log in to continue",
                style = TdsTypography.Body1,
                color = Tds.colors.textSecondary,
            )
            Spacer(modifier = Modifier.height(32.dp))

            if (isRegisterMode) {
                OutlinedTextField(
                    value = firstName,
                    onValueChange = { firstName = it },
                    label = { Text("First name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = tdsTextFieldColors(),
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = lastName,
                    onValueChange = { lastName = it },
                    label = { Text("Last name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = tdsTextFieldColors(),
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = referralCode,
                    onValueChange = { referralCode = it },
                    label = { Text("Referral code (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = tdsTextFieldColors(),
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            OutlinedTextField(
                value = phoneNumber,
                onValueChange = { phoneNumber = it },
                label = { Text("Phone number") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
                colors = tdsTextFieldColors(),
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
                colors = tdsTextFieldColors(),
            )

            errorMessage?.let {
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = it, style = TdsTypography.Body2, color = Tds.colors.danger)
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (isSubmitting) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(16.dp),
                    color = Tds.colors.brand,
                )
            } else {
                TdsButton(
                    text = if (isRegisterMode) "Create account" else "Log in",
                    onClick = { submit() },
                    enabled = phoneNumber.isNotBlank() && password.isNotBlank() &&
                        (!isRegisterMode || (firstName.isNotBlank() && lastName.isNotBlank())),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            TextButton(onClick = { isRegisterMode = !isRegisterMode; errorMessage = null }) {
                Text(
                    text = if (isRegisterMode) "Already have an account? Log in" else "New to itunda? Create an account",
                    style = TdsTypography.Body2,
                    color = Tds.colors.textBrand,
                )
            }
        }
    }
}

@Composable
private fun tdsTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Tds.colors.brand,
    unfocusedBorderColor = Tds.colors.divider,
    focusedLabelColor = Tds.colors.brand,
    unfocusedLabelColor = Tds.colors.textSecondary,
    focusedTextColor = Tds.colors.textPrimary,
    unfocusedTextColor = Tds.colors.textPrimary,
    cursorColor = Tds.colors.brand,
)
