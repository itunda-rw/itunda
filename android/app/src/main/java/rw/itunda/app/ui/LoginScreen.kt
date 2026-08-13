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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
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
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import rw.itunda.app.R
import rw.itunda.core.network.AppLocalePreference
import rw.itunda.core.network.AuthResult
import rw.itunda.core.network.SessionManager
import rw.itunda.core.designsystem.components.IdsKeyboardDockedButton
import rw.itunda.core.designsystem.components.IdsTextField
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.designsystem.theme.IdsTheme
import rw.itunda.core.designsystem.theme.IdsTypography

// Real per-app language override -- found live on the emulator (not just a clean
// compile) that AppCompatDelegate.setApplicationLocales, the "normal" per-app
// language API, silently no-ops here: MainActivity extends FragmentActivity, not
// AppCompatActivity, so there's no AppCompatDelegate instance attached to it to
// notice the change and recreate. A Configuration-overridden Context via
// createConfigurationContext works with any Activity type instead.
//
// Corrected 2026-08-08: the actual state + persistence now lives in
// rw.itunda.core.network.AppLocalePreference, not here -- this screen originally
// wrapped only its own subtree in the CompositionLocalProvider, which meant the
// switcher below silently never affected anything past the login screen itself (see
// AppLocalePreference's own doc comment for the full story). MainActivity.kt now
// wraps the real app root in it; this screen just reads/writes the shared StateFlow
// like SettingsScreen.kt's own copy of the same switcher does.

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
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LoginScreen(onLoggedIn: () -> Unit) {
    val baseContext = LocalContext.current
    val locale by AppLocalePreference.locale.collectAsState()
    val isImeVisible = WindowInsets.isImeVisible

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
                                contentDescription = stringResource(R.string.login_back),
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
                    LanguageSwitcher(
                        locale = locale,
                        onLocaleChange = { next -> AppLocalePreference.set(baseContext, next) },
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = Ids.layout.screenHorizontal),
                    // Real fix, found live 2026-08-05 on a real emulator screenshot: with
                    // Arrangement.Top, every step's headline+field sat pinned at the very
                    // top of this weighted column while the button sat pinned to the
                    // bottom of its own column below -- leaving roughly half the screen as
                    // dead gray space on both the phone-number and password steps. Real
                    // Toss/Kakao onboarding centers the one active question vertically in
                    // the available viewport instead of stranding it at the top.
                    verticalArrangement = Arrangement.Center,
                ) {
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
                                    headline = stringResource(if (registerMode) R.string.login_headline_phone_register else R.string.login_headline_phone_login),
                                    subtitle = stringResource(if (registerMode) R.string.login_subtitle_phone_register else R.string.login_subtitle_phone_login),
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

                // Real Toss keyboard-docking reference (2026-08-12, direct user
                // screenshot) -- the primary "Next"/"Log in"/"Create account" button
                // now handles its own horizontal bleed via IdsKeyboardDockedButton
                // (flush, sharp-cornered, full-width the moment the keyboard opens;
                // normal rounded+inset otherwise), so this outer Column no longer
                // applies a fixed horizontal inset -- only the secondary "switch
                // mode" text link below still wants one, applied directly to it.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = if (isImeVisible) 0.dp else Ids.layout.screenVertical),
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
                        IdsKeyboardDockedButton(
                            text = if (isLastStep) stringResource(if (isRegisterMode) R.string.login_button_create_account else R.string.login_button_log_in) else stringResource(R.string.login_button_next),
                            onClick = { goNext() },
                            enabled = currentStepValid,
                        )
                    }

                    if (step == 0) {
                        Spacer(modifier = Modifier.height(Ids.layout.rowGap))
                        TextButton(
                            onClick = { switchMode(!isRegisterMode) },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = Ids.layout.screenHorizontal),
                        ) {
                            Text(
                                text = stringResource(if (isRegisterMode) R.string.login_switch_to_login else R.string.login_switch_to_register),
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

/**
 * Real first in-app language switcher (2026-08-08) -- see values/strings.xml's and
 * LoginScreen's own doc comments for the full context, including why this is
 * Configuration-override-based rather than AppCompatDelegate-based (found live on the
 * emulator that the latter silently no-ops against a FragmentActivity). Only 2 locales
 * exist right now, so a simple toggle (shows the current selection, tap switches to the
 * other) is the honest minimum -- matching bank-mfe's own "don't add weight this scope
 * doesn't need yet" reasoning, not a full language picker for locales that don't exist yet.
 */
@Composable
private fun LanguageSwitcher(locale: String, onLocaleChange: (String) -> Unit) {
    val isKinyarwanda = locale == "rw"
    val label = stringResource(R.string.login_language_switcher_label)
    Text(
        text = if (isKinyarwanda) "RW" else "EN",
        style = IdsTypography.Body2.copy(fontWeight = FontWeight.SemiBold),
        color = Ids.colors.textSecondary,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onLocaleChange(if (isKinyarwanda) "en" else "rw") }
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .semantics { contentDescription = label },
    )
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

// Real Toss reference (2026-08-13, direct user comparison against a live screenshot
// of itunda's own login screen): the phone-number step used to trigger the
// platform's own software keyboard (KeyboardType.Phone) -- on a device whose system
// language differs from itunda's own selected app language (a real, live case: a
// Korean-system-locale device running itunda in English), this meant the phone's
// entire own numeric keypad -- "완료"/"*+#" labels -- popped up underneath an
// English "Log in with your phone number" screen, breaking the fully-branded,
// no-stray-system-chrome look every real Toss screen maintains (see
// toss.tech/article/toss-signup-process's own "1 thing/1 page" discipline, which
// only works if the screen stays under the app's own visual control end to end).
// A custom in-app keypad, not the system IME, is how Toss actually avoids this --
// same reason a security/PIN keypad is never the system keyboard on any real
// banking app. Field itself stays a real IdsTextField (same focus ring/label/cursor
// styling as every other step) via readOnly, driven entirely by this keypad's own
// digit/backspace taps instead of direct typing.
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
            onValueChange = {},
            label = stringResource(R.string.login_label_phone_number),
            keyboardType = KeyboardType.Phone,
            readOnly = true,
            modifier = Modifier.focusRequester(focusRequester),
        )
        Spacer(modifier = Modifier.height(Ids.layout.sectionGap))
        NumericKeypad(
            onDigit = { digit -> onPhoneNumberChange((phoneNumber + digit).take(15)) },
            onBackspace = { if (phoneNumber.isNotEmpty()) onPhoneNumberChange(phoneNumber.dropLast(1)) },
        )
    }
}

/**
 * A custom, itunda-branded numeric keypad -- see PhoneStep's own doc comment for why
 * this exists instead of the platform IME. Plain digit-centered cells (no visible
 * per-key background/border), matching real Toss/Kakao security-keypad references:
 * minimal chrome, the number itself is the whole visual. Backspace occupies the
 * bottom-right cell (same position every real phone dialer/PIN pad uses), bottom-left
 * left empty rather than filled with a decorative glyph nothing here needs.
 */
@Composable
private fun NumericKeypad(onDigit: (String) -> Unit, onBackspace: () -> Unit) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        rows.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                row.forEach { digit -> KeypadKey(label = digit, onClick = { onDigit(digit) }) }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Box(modifier = Modifier.size(72.dp))
            KeypadKey(label = "0", onClick = { onDigit("0") })
            Box(modifier = Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.login_keypad_backspace),
                    tint = Ids.colors.textSecondary,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onBackspace)
                        .padding(12.dp),
                )
            }
        }
    }
}

@Composable
private fun KeypadKey(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = IdsTypography.Title1, color = Ids.colors.textPrimary)
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
        StepHeadline(stringResource(R.string.login_headline_name), stringResource(R.string.login_subtitle_name))
        IdsTextField(
            value = firstName,
            onValueChange = onFirstNameChange,
            label = stringResource(R.string.login_label_first_name),
            modifier = Modifier.focusRequester(focusRequester),
        )
        Spacer(modifier = Modifier.height(Ids.layout.inlineGap))
        IdsTextField(
            value = lastName,
            onValueChange = onLastNameChange,
            label = stringResource(R.string.login_label_last_name),
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
            stringResource(if (isRegisterMode) R.string.login_headline_password_register else R.string.login_headline_password_login),
            if (isRegisterMode) stringResource(R.string.login_subtitle_password_register) else null,
        )
        // Real "Minimum Input" simplicity fix (docs/DESIGN_REFERENCES.md §11/§12): switched
        // to IdsTextField's new isPassword mode (show/hide toggle) instead of an always-
        // masked PasswordVisualTransformation -- especially valuable in isRegisterMode,
        // where a silent typo here locks the new account behind a password the user
        // doesn't actually know.
        IdsTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = stringResource(R.string.login_label_password),
            isPassword = true,
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
                    label = stringResource(R.string.login_label_referral_code),
                )
            } else {
                TextButton(onClick = onShowReferralField) {
                    Text(
                        text = stringResource(R.string.login_referral_prompt),
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
