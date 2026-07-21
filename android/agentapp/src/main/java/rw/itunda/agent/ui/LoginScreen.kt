package rw.itunda.agent.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import rw.itunda.agent.network.LoginRequest
import rw.itunda.agent.network.NetworkClient
import rw.itunda.core.designsystem.components.IdsButton
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.designsystem.theme.IdsTypography

@Composable
fun LoginScreen(onLoggedIn: () -> Unit) {
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("itunda", style = IdsTypography.Title1, color = Ids.colors.textPrimary)
        Text("Agent", style = IdsTypography.Title2, color = Ids.colors.textSecondary)
        Text("Use the Itunda account assigned to this store.", style = IdsTypography.Body1, color = Ids.colors.textSecondary)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(phone, { phone = it }, label = { Text("Phone number") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(password, { password = it }, label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        error?.let { Text(it, color = Ids.colors.danger, style = IdsTypography.Body2, modifier = Modifier.padding(top = 12.dp)) }
        Spacer(Modifier.height(20.dp))
        IdsButton(enabled = !busy, onClick = {
            if (phone.isBlank() || password.isBlank()) { error = "Enter your phone number and password."; return@IdsButton }
            busy = true; error = null
            scope.launch {
                try {
                    val auth = NetworkClient.authApi.login(LoginRequest(phone.trim(), password))
                    NetworkClient.session().save(auth.accessToken)
                    // Validate the role before leaving the sign-in screen. A normal
                    // consumer login must not look like a usable cashier session.
                    NetworkClient.agentApi.me()
                    onLoggedIn()
                } catch (_: Exception) {
                    NetworkClient.session().clear()
                    error = "This account is not an active agent operator, or the service could not be reached."
                } finally { busy = false }
            }
        }, text = if (busy) "Signing in…" else "Sign in")
    }
}
