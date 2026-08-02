package rw.itunda.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import rw.itunda.core.network.AuthResult
import rw.itunda.core.network.SessionManager
import rw.itunda.core.designsystem.components.IdsButton
import rw.itunda.core.designsystem.components.IdsTextField
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.designsystem.theme.IdsTheme
import rw.itunda.core.designsystem.theme.IdsTypography

/**
 * Real Toss-inspired step redesign, 2026-08-03 -- the previous version (still a real
 * improvement over the original bare form, see git history) put every field on one
 * screen inside a single card. Real Toss onboarding doesn't do that: Toss's own
 * design team has written publicly about "한 번에 하나만" ("only one thing at a
 * time") -- each real Toss signup screen asks exactly one direct question, shows one
 * auto-focused input, and pins one big pill button at the bottom, with a step
 * progressing forward rather than a form scrolling down. This rebuilds the same
 * behavior (still one real `SessionManager.login`/`register` call at the end, same
 * backend contract, same fields) as an in-screen step flow instead of a stacked form:
 * - Login: phone -> password (2 steps)
 * - Register: phone -> name -> password, with referral code folded behind an
 *   optional "Have a referral code?" link on the password step rather than its own
 *   mandatory step -- matching Toss's own real practice of hiding optional fields
 *   instead of forcing every user through them.
 *
 * Each step auto-focuses its own field (`FocusRequester` + a `LaunchedEffect` on step
 * change -- the keyboard should already be up when a user lands on a new question,
 * not require an extra tap first, matching the real Toss feel this is inspired by).
 *
 * Gates ItundaAppScreen in MainActivity.kt behind a real authenticated session
 * instead of rendering the whole app unconditionally against services/backend's real
 * /api/v1/auth/register and /api/v1/auth/login.
 */
@Composable
fun LoginScreen(onLoggedIn: () -> Unit) {
    IdsTheme {
        var isRegisterMode by remember { mutableStateOf(false) }
        var step by remember { mutableStateOf(0) }
        var phoneNumber by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var firstName by remember { mutableStateOf("") }
        var lastName by remember { mutableStateOf("") }
        var referralCode by remember { mutableStateOf("") }
        var showReferralField by remember { mutableStateOf(false) }
        var isSubmitting by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()

        val stepCount = if (isRegisterMode) 3 else 2

        fun switchMode(registerMode: Boolean) {
            isRegisterMode = registerMode
            step = 0
            errorMessage = null
        }

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

        fun goNext() {
            errorMessage = null
            if (step < stepCount - 1) step++ else submit()
        }

        val currentStepValid = when {
            !isRegisterMode && step == 0 -> phoneNumber.isNotBlank()
            !isRegisterMode && step == 1 -> password.isNotBlank()
            isRegisterMode && step == 0 -> phoneNumber.isNotBlank()
            isRegisterMode && step == 1 -> firstName.isNotBlank() && lastName.isNotBlank()
            isRegisterMode && step == 2 -> password.isNotBlank()
            else -> false
        }

        Box(modifier = Modifier.fillMaxSize().background(Ids.colors.background)) {
            Column(modifier = Modifier.fillMaxSize().imePadding()) {
                // Top bar: back chevron (once past the first step) + step-progress dots.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Ids.layout.screenHorizontal, vertical = Ids.layout.screenVertical),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.size(40.dp)) {
                        if (step > 0) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Ids.colors.textPrimary,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .clickable { step-- }
                                    .padding(8.dp),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    StepDots(total = stepCount, current = step)
                    Spacer(modifier = Modifier.weight(1f))
                    Spacer(modifier = Modifier.size(40.dp))
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = Ids.layout.screenHorizontal),
                    verticalArrangement = Arrangement.Top,
                ) {
                    Spacer(modifier = Modifier.height(24.dp))

                    if (step == 0) {
                        BrandMark()
                        Spacer(modifier = Modifier.height(Ids.layout.sectionGap))
                    }

                    AnimatedContent(
                        targetState = isRegisterMode to step,
                        transitionSpec = {
                            val forward = targetState.second >= initialState.second
                            (slideInHorizontally(tween(220)) { if (forward) it / 4 else -it / 4 } + fadeIn(tween(220))) togetherWith
                                (slideOutHorizontally(tween(220)) { if (forward) -it / 4 else it / 4 } + fadeOut(tween(180)))
                        },
                        label = "auth-step",
                    ) { (registerMode, currentStep) ->
                        Column {
                            when {
                                currentStep == 0 -> PhoneStep(
                                    phoneNumber = phoneNumber,
                                    onPhoneNumberChange = { phoneNumber = it },
                                    headline = if (registerMode) "What's your phone number?" else "Log in with your phone number",
                                    subtitle = if (registerMode) "We'll use this to keep your account secure." else "Enter the number you signed up with.",
                                )
                                registerMode && currentStep == 1 -> NameStep(
                                    firstName = firstName,
                                    onFirstNameChange = { firstName = it },
                                    lastName = lastName,
                                    onLastNameChange = { lastName = it },
                                )
                                (registerMode && currentStep == 2) || (!registerMode && currentStep == 1) -> PasswordStep(
                                    password = password,
                                    onPasswordChange = { password = it },
                                    isRegisterMode = registerMode,
                                    errorMessage = errorMessage,
                                    showReferralField = showReferralField,
                                    onShowReferralField = { showReferralField = true },
                                    referralCode = referralCode,
                                    onReferralCodeChange = { referralCode = it },
                                )
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Ids.layout.screenHorizontal, vertical = Ids.layout.screenVertical),
                ) {
                    if (isSubmitting) {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp).padding(vertical = 14.dp),
                                color = Ids.colors.brand,
                                strokeWidth = 3.dp,
                            )
                        }
                    } else {
                        val isLastStep = step == stepCount - 1
                        IdsButton(
                            text = if (isLastStep) (if (isRegisterMode) "Create account" else "Log in") else "Next",
                            onClick = { goNext() },
                            enabled = currentStepValid,
                        )
                    }

                    if (step == 0) {
                        Spacer(modifier = Modifier.height(Ids.layout.rowGap))
                        TextButton(
                            onClick = { switchMode(!isRegisterMode) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = if (isRegisterMode) "Already have an account? Log in" else "New to itunda? Create an account",
                                style = IdsTypography.Body2.copy(fontWeight = FontWeight.SemiBold),
                                color = Ids.colors.textBrand,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepDots(total: Int, current: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(total) { index ->
            Box(
                modifier = Modifier
                    .size(if (index == current) 8.dp else 6.dp)
                    .clip(CircleShape)
                    .background(if (index == current) Ids.colors.brand else Ids.colors.divider),
            )
        }
    }
}

@Composable
private fun StepHeadline(headline: String, subtitle: String? = null) {
    Text(text = headline, style = IdsTypography.Title1, color = Ids.colors.textPrimary)
    if (subtitle != null) {
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = subtitle, style = IdsTypography.Body1, color = Ids.colors.textSecondary)
    }
    Spacer(modifier = Modifier.height(32.dp))
}

/** Every focus-requesting step waits one frame before requesting focus -- a real,
 * common Compose timing fix: requesting focus in the same frame a composable enters
 * can silently no-op if the node hasn't attached yet. */
@Composable
private fun rememberAutoFocus(key: Any?): FocusRequester {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(key) {
        delay(80)
        focusRequester.requestFocus()
    }
    return focusRequester
}

@Composable
private fun PhoneStep(
    phoneNumber: String,
    onPhoneNumberChange: (String) -> Unit,
    headline: String,
    subtitle: String,
) {
    val focusRequester = rememberAutoFocus("phone")
    Column {
        StepHeadline(headline, subtitle)
        IdsTextField(
            value = phoneNumber,
            onValueChange = onPhoneNumberChange,
            label = "Phone number",
            keyboardType = KeyboardType.Phone,
            modifier = Modifier.focusRequester(focusRequester),
        )
    }
}

@Composable
private fun NameStep(
    firstName: String,
    onFirstNameChange: (String) -> Unit,
    lastName: String,
    onLastNameChange: (String) -> Unit,
) {
    val focusRequester = rememberAutoFocus("name")
    Column {
        StepHeadline("What's your name?", "This is how you'll appear to friends and merchants.")
        IdsTextField(
            value = firstName,
            onValueChange = onFirstNameChange,
            label = "First name",
            modifier = Modifier.focusRequester(focusRequester),
        )
        Spacer(modifier = Modifier.height(Ids.layout.inlineGap))
        IdsTextField(
            value = lastName,
            onValueChange = onLastNameChange,
            label = "Last name",
        )
    }
}

@Composable
private fun PasswordStep(
    password: String,
    onPasswordChange: (String) -> Unit,
    isRegisterMode: Boolean,
    errorMessage: String?,
    showReferralField: Boolean,
    onShowReferralField: () -> Unit,
    referralCode: String,
    onReferralCodeChange: (String) -> Unit,
) {
    val focusRequester = rememberAutoFocus("password")
    Column {
        StepHeadline(
            if (isRegisterMode) "Create a password" else "Enter your password",
            if (isRegisterMode) "Use at least 8 characters." else null,
        )
        IdsTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = "Password",
            visualTransformation = PasswordVisualTransformation(),
            keyboardType = KeyboardType.Password,
            isError = errorMessage != null,
            errorText = errorMessage,
            modifier = Modifier.focusRequester(focusRequester),
        )
        if (isRegisterMode) {
            Spacer(modifier = Modifier.height(Ids.layout.inlineGap))
            if (showReferralField) {
                IdsTextField(
                    value = referralCode,
                    onValueChange = onReferralCodeChange,
                    label = "Referral code",
                )
            } else {
                TextButton(onClick = onShowReferralField) {
                    Text(
                        text = "Have a referral code?",
                        style = IdsTypography.Body2,
                        color = Ids.colors.textSecondary,
                    )
                }
            }
        }
    }
}

/**
 * A Compose-only brand mark: a rounded brand-colored badge with a stylized "i", next
 * to the "itunda" wordmark -- no launcher/logo drawable exists in this app's
 * resources (confirmed via a repo-wide search before writing this), so this is built
 * entirely from existing design tokens rather than referencing an asset that doesn't
 * exist.
 */
@Composable
private fun BrandMark() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(Ids.layout.iconCornerRadius))
                .background(Ids.colors.brand),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "i",
                style = IdsTypography.Title2,
                color = Color.White,
                fontWeight = FontWeight.Black,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "itunda",
            style = IdsTypography.Title1,
            color = Ids.colors.textPrimary,
            fontWeight = FontWeight.Black,
        )
    }
}
