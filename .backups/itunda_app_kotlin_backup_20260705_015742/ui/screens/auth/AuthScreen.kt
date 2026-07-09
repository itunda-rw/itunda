package com.itunda.app.ui.screens.auth

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itunda.app.ui.theme.*
import com.itunda.app.viewmodel.AuthViewModel

@Composable
fun AuthScreen(
    authViewModel: AuthViewModel,
    onLoginSuccess: () -> Unit
) {
    val state by authViewModel.uiState.collectAsState()
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(state.isLoggedIn) {
        if (state.isLoggedIn) onLoginSuccess()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(72.dp))

            // ── Brand ─────────────────────────────────────────────────────
            Text(
                text = "itunda",
                fontSize = 36.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Primary,
                letterSpacing = (-1).sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Rwanda's financial super app",
                fontSize = 15.sp,
                color = TextSecondary,
                fontWeight = FontWeight.Normal
            )

            Spacer(modifier = Modifier.height(52.dp))

            // ── Form ──────────────────────────────────────────────────────
            AnimatedVisibility(
                visible = state.isRegistering,
                enter = fadeIn() + expandVertically(),
                exit  = fadeOut() + shrinkVertically()
            ) {
                Column {
                    TossTextField(
                        value       = name,
                        onValueChange = { name = it },
                        label       = "Full name",
                        imeAction   = ImeAction.Next,
                        onNext      = { focusManager.moveFocus(FocusDirection.Down) }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            TossTextField(
                value         = phone,
                onValueChange = { phone = it },
                label         = "Phone number",
                prefix        = "+250",
                keyboardType  = KeyboardType.Phone,
                imeAction     = ImeAction.Next,
                onNext        = { focusManager.moveFocus(FocusDirection.Down) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            TossTextField(
                value               = password,
                onValueChange       = { password = it },
                label               = "Password",
                keyboardType        = KeyboardType.Password,
                imeAction           = ImeAction.Done,
                onDone              = { focusManager.clearFocus() },
                visualTransformation = if (passwordVisible)
                    VisualTransformation.None
                else
                    PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible)
                                Icons.Outlined.Visibility
                            else
                                Icons.Outlined.VisibilityOff,
                            contentDescription = null,
                            tint = TextSecondary
                        )
                    }
                }
            )

            Spacer(modifier = Modifier.height(28.dp))

            // ── Primary CTA ───────────────────────────────────────────────
            Button(
                onClick = {
                    if (state.isRegistering) authViewModel.register(name, phone, password)
                    else authViewModel.login(phone, password)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape  = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Primary,
                    disabledContainerColor = Primary.copy(alpha = 0.4f)
                ),
                enabled = !state.isLoading && phone.isNotBlank() && password.isNotBlank()
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier   = Modifier.size(22.dp),
                        color      = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text       = if (state.isRegistering) "Create account" else "Log in",
                        fontSize   = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Demo button ───────────────────────────────────────────────
            OutlinedButton(
                onClick  = { authViewModel.openDemo() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape  = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Primary),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, Primary.copy(alpha = 0.3f))
            ) {
                Text(
                    text       = "Try demo",
                    fontSize   = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Toggle register/login ─────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text     = if (state.isRegistering) "Already have an account?  " else "New to itunda?  ",
                    color    = TextSecondary,
                    fontSize = 14.sp
                )
                Text(
                    text     = if (state.isRegistering) "Log in" else "Create account",
                    color    = Primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { authViewModel.toggleRegister() }
                )
            }

            // ── Error ─────────────────────────────────────────────────────
            state.error?.let { error ->
                Spacer(modifier = Modifier.height(20.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(NegativeRed.copy(alpha = 0.08f))
                        .padding(16.dp)
                ) {
                    Text(
                        text      = error,
                        color     = NegativeRed,
                        fontSize  = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier  = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // ── Demo hint ─────────────────────────────────────────────────────
        Text(
            text     = "Demo: +250788123456 · password123",
            fontSize = 12.sp,
            color    = TextTertiary,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        )
    }
}

// ── Toss-style text field ──────────────────────────────────────────────────
@Composable
private fun TossTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    prefix: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onNext: (() -> Unit)? = null,
    onDone: (() -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    OutlinedTextField(
        value         = value,
        onValueChange = onValueChange,
        label         = { Text(label) },
        modifier      = Modifier.fillMaxWidth(),
        shape         = RoundedCornerShape(14.dp),
        singleLine    = true,
        visualTransformation = visualTransformation,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction    = imeAction
        ),
        keyboardActions = KeyboardActions(
            onNext = { onNext?.invoke() },
            onDone = { onDone?.invoke() }
        ),
        prefix = prefix?.let { { Text(it, color = TextSecondary, fontWeight = FontWeight.Medium) } },
        trailingIcon = trailingIcon,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor   = Primary,
            unfocusedBorderColor = CardBorder,
            focusedLabelColor    = Primary,
            unfocusedLabelColor  = TextSecondary,
            cursorColor          = Primary
        )
    )
}
