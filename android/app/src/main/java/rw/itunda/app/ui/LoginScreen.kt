package rw.itunda.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import rw.itunda.core.designsystem.components.pressScaleClickable
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
import rw.itunda.core.identity.DeviceKeyManager
import rw.itunda.core.network.AppLocalePreference
import rw.itunda.core.network.AuthResult
import rw.itunda.core.network.SessionManager
import rw.itunda.core.designsystem.components.IdsKeyboardDockedButton
import rw.itunda.core.designsystem.components.IdsTextField
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.designsystem.theme.IdsIcons
import rw.itunda.core.designsystem.theme.IdsTheme
import rw.itunda.core.designsystem.theme.IdsTypography
import java.util.Base64

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
 * Real Toss reverse-stacking redesign, 2026-08-13 -- direct user correction after the
 * previous version (2026-08-03, still in git history) turned out to only borrow
 * Toss's "one thing at a time" framing, not the actual documented mechanism. The real
 * source (toss.tech/article/toss-signup-process, an official Toss Tech writeup on
 * their own 2018 signup redesign) describes something more specific than "one field
 * per full-screen step": when a question is answered, the NEXT question's field is
 * inserted ABOVE it -- not a full-screen navigation to a new page -- so fields
 * accumulate in reverse-chronological order on ONE continuous screen (newest/active
 * field always at the top with focus, already-answered fields stacked, still
 * visible, beneath it). Toss's own designer built this specifically to keep the CTA
 * button reachable (the bug their old form had: fields appended at the BOTTOM could
 * push the button below the keyboard) while still only showing one live input at a
 * time -- and user testing found people don't consciously notice the reverse
 * ordering because attention stays on the top field's cursor (the article's own
 * "invisible gorilla" reference).
 *
 * This rebuilds that literally: `step` still drives which single field is active
 * (auto-focused, editable) and which are "answered" (compactStepRows, tap to go back
 * and re-edit), but there is no longer a full-screen slide transition between
 * steps -- previously-answered fields stay on screen, stacked below the active one,
 * exactly matching the real mechanism instead of only its stated goal.
 * - Login: phone -> password (2 steps)
 * - Register: phone -> name -> password, with referral code folded behind an
 *   optional "Have a referral code?" link on the password step rather than its own
 *   mandatory step -- matching Toss's own real practice of hiding optional fields
 *   instead of forcing every user through them.
 * Password is never shown in a compact answered-row (it's always the final step in
 * both modes, so it never needs to be) -- only phone and, in register mode, name
 * ever stack below the active field.
 *
 * Gates ItundaAppScreen in MainActivity.kt behind a real authenticated session
 * instead of rendering the whole app unconditionally against services/backend's real
 * /api/v1/auth/register and /api/v1/auth/login.
 */
/** See LoginScreen's own doc comment for the real Toss unified phone-first flow this
 * drives. NOT_FOUND is a real interstitial (not a data-entry step): the account
 * lookup came back negative and the user is being told that honestly, with a clear
 * path forward, rather than either a dead end or silently assuming signup intent. */
private enum class AuthStage { PHONE, NOT_FOUND, NAME, PASSWORD }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LoginScreen(onLoggedIn: () -> Unit) {
    val baseContext = LocalContext.current
    val locale by AppLocalePreference.locale.collectAsState()
    val isImeVisible = WindowInsets.isImeVisible

    IdsTheme {
        // Real Toss unified phone-first entry (2026-08-13, direct user description
        // of the real flow): itunda used to make the user pick "Log in" vs "Create
        // account" upfront -- real Toss doesn't ask. A single phone-number question
        // is checked against the backend (SessionManager.checkPhoneExists), and the
        // flow branches automatically: an existing account goes straight to a
        // password prompt; no account gets an honest "no itunda account found" screen
        // with a clear path into signup, not a dead end. See AuthController
        // .checkPhone's own doc comment on the backend for the account-existence
        // check itself.
        var stage by remember { mutableStateOf(AuthStage.PHONE) }
        var isRegisterMode by remember { mutableStateOf(false) }
        var phoneNumber by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var firstName by remember { mutableStateOf("") }
        var lastName by remember { mutableStateOf("") }
        var referralCode by remember { mutableStateOf("") }
        var showReferralField by remember { mutableStateOf(false) }
        var isSubmitting by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()
        val checkingPhoneError = stringResource(R.string.login_checking_phone_error)

        // Real Toss-sourced passwordless-login rollout (2026-08-23) -- see
        // AccountPinPad's own doc comment for the full sourced account. `pinFirstEntry`
        // holds the register-mode "create" step's PIN while the "confirm" step is
        // shown; `attemptingPasswordless` gates a silent biometric-first login attempt
        // (see the LaunchedEffect below) before ever showing the PIN pad at all --
        // real Toss's own actual day-to-day login is Face ID/fingerprint, with the PIN
        // only as its standing fallback.
        var pinFirstEntry by remember { mutableStateOf<String?>(null) }
        var attemptingPasswordless by remember { mutableStateOf(false) }
        val deviceKeyManager = remember { DeviceKeyManager() }
        val activity = LocalRealActivity.current

        LaunchedEffect(stage) {
            if (stage != AuthStage.PASSWORD || isRegisterMode || !deviceKeyManager.hasKey()) return@LaunchedEffect
            attemptingPasswordless = true
            try {
                val challenge = SessionManager.loginDeviceChallenge(phoneNumber)
                deviceKeyManager.signChallenge(
                    activity = activity,
                    challenge = Base64.getDecoder().decode(challenge),
                    reason = "Sign in to itunda",
                ) { signatureBase64, _ ->
                    if (signatureBase64 == null) {
                        attemptingPasswordless = false
                        return@signChallenge
                    }
                    scope.launch {
                        when (val result = SessionManager.loginWithDeviceSignature(phoneNumber, signatureBase64)) {
                            is AuthResult.Success -> onLoggedIn()
                            // Falls through silently to the PIN pad already rendered
                            // below -- a declined/failed biometric attempt must never
                            // strand the user with no other way in.
                            is AuthResult.Failure -> attemptingPasswordless = false
                        }
                    }
                }
            } catch (_: Exception) {
                attemptingPasswordless = false
            }
        }

        val stepIndex = when (stage) {
            AuthStage.PHONE, AuthStage.NOT_FOUND -> 0
            AuthStage.NAME -> 1
            AuthStage.PASSWORD -> if (isRegisterMode) 2 else 1
        }
        val stepCount = if (isRegisterMode) 3 else 2

        fun submit() {
            errorMessage = null
            isSubmitting = true
            scope.launch {
                // Real Toss-sourced passwordless-login rollout (2026-08-23) -- publish
                // this device's Keystore key alongside the password/PIN submission
                // (same real key item 246 already established, see
                // DeviceKeyManager.exportPublicKeyIfPresent's own doc comment), so the
                // NEXT login can skip straight to the biometric attempt above with no
                // separate Settings-toggle trip required.
                val devicePublicKey = deviceKeyManager.exportPublicKeyIfPresent() ?: deviceKeyManager.generateKeyPair()
                val result = if (isRegisterMode) {
                    SessionManager.register(
                        phoneNumber, password, firstName, lastName,
                        referralCode = referralCode.trim().ifBlank { null },
                        devicePublicKey = devicePublicKey,
                    )
                } else {
                    SessionManager.login(phoneNumber, password, devicePublicKey = devicePublicKey)
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
            when (stage) {
                AuthStage.PHONE -> {
                    isSubmitting = true
                    scope.launch {
                        try {
                            val exists = SessionManager.checkPhoneExists(phoneNumber)
                            isSubmitting = false
                            if (exists) {
                                isRegisterMode = false
                                stage = AuthStage.PASSWORD
                            } else {
                                stage = AuthStage.NOT_FOUND
                            }
                        } catch (e: Exception) {
                            isSubmitting = false
                            errorMessage = checkingPhoneError
                        }
                    }
                }
                AuthStage.NOT_FOUND -> {
                    isRegisterMode = true
                    stage = AuthStage.NAME
                }
                AuthStage.NAME -> stage = AuthStage.PASSWORD
                AuthStage.PASSWORD -> submit()
            }
        }

        fun goBack() {
            errorMessage = null
            pinFirstEntry = null
            attemptingPasswordless = false
            stage = when (stage) {
                AuthStage.PHONE -> AuthStage.PHONE
                AuthStage.NOT_FOUND -> AuthStage.PHONE
                AuthStage.NAME -> AuthStage.PHONE
                AuthStage.PASSWORD -> if (isRegisterMode) AuthStage.NAME else AuthStage.PHONE
            }
        }

        val currentStepValid = when (stage) {
            AuthStage.PHONE -> phoneNumber.isNotBlank()
            AuthStage.NOT_FOUND -> true
            AuthStage.NAME -> firstName.isNotBlank() && lastName.isNotBlank()
            AuthStage.PASSWORD -> password.isNotBlank()
        }

        // Real bug found live FIVE times in a row (2026-08-13) -- finally root-caused
        // with a direct `adb shell uiautomator dump` bounds check instead of visual
        // screenshot inspection (which can't distinguish "not rendered" from
        // "rendered but covered by another window"): removing imePadding() (this
        // comment's own previous, WRONG theory) left the button at real, valid,
        // on-screen coordinates -- just underneath the real keyboard's own window.
        // `dumpsys window windows` on the real InputMethod window confirms
        // `sim={adjust=pan}` -- this Activity's window never resizes when the
        // keyboard opens (no windowSoftInputMode="adjustResize" is declared, and the
        // platform's default resolved to pan here), so nothing shrinks the space
        // Compose sees automatically; imePadding() is the one thing that actually
        // reserves that space manually, and removing it was a real regression, not a
        // fix. Restored.
        Box(modifier = Modifier.fillMaxSize().background(Ids.colors.background).imePadding()) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top bar: back chevron (once past the first step) + step-progress dots.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Ids.layout.screenHorizontal, vertical = Ids.layout.screenVertical),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.size(40.dp)) {
                        if (stage != AuthStage.PHONE) {
                            Icon(
                                imageVector = IdsIcons.Back,
                                contentDescription = stringResource(R.string.login_back),
                                tint = Ids.colors.textPrimary,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .pressScaleClickable { goBack() }
                                    .padding(8.dp),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    StepDots(total = stepCount, current = stepIndex)
                    Spacer(modifier = Modifier.weight(1f))
                    LanguageSwitcher(
                        locale = locale,
                        onLocaleChange = { next -> AppLocalePreference.set(baseContext, next) },
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Ids.layout.screenHorizontal),
                    // Real fix (2026-08-13, replacing the previous Arrangement.Center):
                    // now that answered fields stack and stay visible below the active
                    // one instead of the screen swapping to a new page each step,
                    // content grows from the top like any real Toss reverse-stacking
                    // screen -- centering would make the active field visually jump as
                    // the stack beneath it grows.
                    //
                    // Real bug found live TWICE in a row (2026-08-13): first with
                    // weight(1f) (fill=true, the default) + verticalScroll on this same
                    // Column, which claimed its full natural height regardless of the
                    // space actually left after imePadding() reserved room for the
                    // keyboard, pushing the docked button off the bottom of the screen
                    // entirely. Then, after trying fill=false + a separate weighted
                    // Spacer below to close the gap that left, the SAME missing-button
                    // bug came back -- two weighted siblings in one Column (this one at
                    // fill=false, the Spacer at the default fill=true) don't split
                    // leftover space predictably here. This Column now carries no
                    // weight and no scroll at all -- it's never realistically taller
                    // than the screen (at most a headline, one field, and two compact
                    // completed-field rows) -- so there's nothing left to fight over;
                    // the single weighted Spacer below is the only element pushing the
                    // button down to dock against the keyboard.
                    verticalArrangement = Arrangement.Top,
                ) {
                    if (stage == AuthStage.PHONE) {
                        BrandMark()
                        Spacer(modifier = Modifier.height(Ids.layout.sectionGap))
                    }

                    // Real Toss reverse-stacking mechanism -- see this composable's own
                    // doc comment for the full sourced account. AnimatedContent is
                    // scoped to ONLY the active field (not the whole screen, and no
                    // horizontal slide -- a subtle fade/rise instead, since this isn't a
                    // page navigation), so previously-answered rows below never
                    // re-transition when a new question appears above them.
                    AnimatedContent(
                        targetState = stage,
                        transitionSpec = {
                            (slideInVertically(tween(200)) { it / 6 } + fadeIn(tween(200))) togetherWith
                                fadeOut(tween(120))
                        },
                        label = "auth-active-step",
                    ) { currentStage ->
                        Column {
                            when (currentStage) {
                                AuthStage.PHONE -> PhoneStep(
                                    phoneNumber = phoneNumber,
                                    onPhoneNumberChange = { phoneNumber = it },
                                    headline = stringResource(R.string.login_headline_phone),
                                    subtitle = stringResource(R.string.login_subtitle_phone),
                                )
                                AuthStage.NOT_FOUND -> NotFoundStep()
                                AuthStage.NAME -> NameStep(
                                    firstName = firstName,
                                    onFirstNameChange = { firstName = it },
                                    lastName = lastName,
                                    onLastNameChange = { lastName = it },
                                )
                                AuthStage.PASSWORD -> PasswordStep(
                                    isRegisterMode = isRegisterMode,
                                    pinFirstEntry = pinFirstEntry,
                                    attemptingPasswordless = attemptingPasswordless,
                                    isSubmitting = isSubmitting,
                                    errorMessage = errorMessage,
                                    showReferralField = showReferralField,
                                    onShowReferralField = { showReferralField = true },
                                    referralCode = referralCode,
                                    onReferralCodeChange = { referralCode = it },
                                    onPinCreated = { entered -> errorMessage = null; pinFirstEntry = entered },
                                    onPinConfirmed = { entered ->
                                        if (entered == pinFirstEntry) {
                                            password = entered
                                            submit()
                                        } else {
                                            pinFirstEntry = null
                                            errorMessage = "That didn't match. Try again."
                                        }
                                    },
                                    onPinEntered = { entered ->
                                        password = entered
                                        submit()
                                    },
                                )
                            }
                        }
                    }

                    // Answered fields, most-recently-answered first -- directly below
                    // the active field, exactly where Toss's real mechanism puts them.
                    // Tapping one jumps back to re-edit it (real Toss lets you correct
                    // an earlier answer without restarting the whole flow).
                    val phoneLabel = stringResource(R.string.login_label_phone_number)
                    val nameLabel = stringResource(R.string.login_headline_name)
                    if (stage == AuthStage.NAME || stage == AuthStage.PASSWORD) {
                        Spacer(modifier = Modifier.height(Ids.layout.sectionGap))
                        Column(verticalArrangement = Arrangement.spacedBy(Ids.layout.inlineGap)) {
                            if (isRegisterMode && stage == AuthStage.PASSWORD) {
                                CompletedFieldRow(label = nameLabel, value = "$firstName $lastName", onClick = { stage = AuthStage.NAME })
                            }
                            CompletedFieldRow(label = phoneLabel, value = phoneNumber, onClick = { stage = AuthStage.PHONE })
                        }
                    }
                }
            }

            // Real Toss keyboard-docking reference (2026-08-12, direct user
            // screenshot) -- the primary "Next"/"Log in"/"Create account" button
            // handles its own horizontal bleed via IdsKeyboardDockedButton (flush,
            // sharp-cornered, full-width the moment the keyboard opens; normal
            // rounded+inset otherwise). No mode-switch link below it any more -- see
            // this whole screen's own doc comment for why login vs signup is no
            // longer a manual choice. Pinned to the bottom of the outer Box (see its
            // own doc comment for why this replaced a Column-weight approach) so it
            // sits flush against the real keyboard exactly like the real Toss
            // reference, with no gap and no risk of vanishing.
            // Real Toss-sourced passwordless-login rollout (2026-08-23): PASSWORD
            // stage no longer renders this docked button at all -- AccountPinPad
            // auto-submits on its own at 6 digits (see PasswordStep below), the same
            // real convention PinScreen.kt's own PIN entry already used.
            if (stage != AuthStage.PASSWORD) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
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
                        val buttonLabel = when (stage) {
                            AuthStage.PHONE, AuthStage.NAME -> stringResource(R.string.login_button_next)
                            AuthStage.NOT_FOUND -> stringResource(R.string.login_button_create_account)
                            AuthStage.PASSWORD -> ""
                        }
                        IdsKeyboardDockedButton(
                            text = buttonLabel,
                            onClick = { goNext() },
                            enabled = currentStepValid,
                        )
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
 * emulator that the latter silently no-ops against a FragmentActivity). Originally a
 * simple EN/RW toggle when only 2 locales existed.
 *
 * Widened to a 3-way cycle (2026-08-15) when French (values-fr/) was added as itunda's
 * third real locale, matching Rwanda's own three official languages -- a binary toggle
 * can't represent 3 states, and a dropdown would be heavier UI than this compact corner
 * control needs. Same "honest minimum for how many locales actually exist" reasoning
 * as before, just re-applied to 3 instead of 2. A real gap this closes: adding
 * values-fr/strings.xml alone would have been dead weight with zero way to reach it --
 * the resources existing doesn't mean the UI offers them.
 */
private val SUPPORTED_LOCALES = listOf("en", "rw", "fr")

@Composable
private fun LanguageSwitcher(locale: String, onLocaleChange: (String) -> Unit) {
    val label = stringResource(R.string.login_language_switcher_label)
    val currentIndex = SUPPORTED_LOCALES.indexOf(locale).coerceAtLeast(0)
    Text(
        text = locale.uppercase(),
        style = IdsTypography.Body2.copy(fontWeight = FontWeight.SemiBold),
        color = Ids.colors.textSecondary,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .pressScaleClickable { onLocaleChange(SUPPORTED_LOCALES[(currentIndex + 1) % SUPPORTED_LOCALES.size]) }
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

/**
 * One already-answered question, stacked below the active field -- see
 * LoginScreen's own doc comment for why this exists (the real Toss reverse-stacking
 * mechanism, not a full-screen step swap). Tapping it re-opens that step for
 * editing, same real affordance the sourced Toss redesign describes.
 */
@Composable
private fun CompletedFieldRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Ids.colors.surfaceSoft)
            .pressScaleClickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = IdsTypography.Typography7, color = Ids.colors.textTertiary)
            Text(text = value, style = IdsTypography.Body1, color = Ids.colors.textPrimary)
        }
        Icon(
            imageVector = IdsIcons.ChevronRight,
            contentDescription = null,
            tint = Ids.colors.textTertiary,
        )
    }
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

// Real correction (2026-08-13, direct user-provided screenshots of the actual Toss
// app): reverts an earlier same-day change here, which replaced this field's system
// keyboard with a custom digit keypad based on an inference that turned out wrong.
// Real Toss screenshots (Split bill amount, company-name search) confirm the actual
// rule is narrower than "no system IME anywhere" -- Toss reserves custom keypads for
// money amounts and PINs specifically (see TransferFlow.kt's real NumericKeypad --
// a calculator-style pad with a "00" key and quick-amount chips, not a plain phone
// dial pad -- and PinScreen.kt's PIN keypad), and uses the real system keyboard
// everywhere else, including login/signup. This field goes back to that.
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
            label = stringResource(R.string.login_label_phone_number),
            keyboardType = KeyboardType.Phone,
            modifier = Modifier.focusRequester(focusRequester),
        )
    }
}

/**
 * Real "no account found" interstitial -- see LoginScreen's own doc comment for the
 * full real Toss-described flow this closes out. Not a dead end: the docked button
 * below this step (wired to "Create account" copy at this stage) is the honest, clear
 * path forward the user asked for, reusing the phone number already entered rather
 * than asking for it again.
 */
@Composable
private fun NotFoundStep() {
    Column {
        StepHeadline(
            stringResource(R.string.login_not_found_headline),
            stringResource(R.string.login_not_found_body),
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

// Real Toss-sourced passwordless-login rollout (2026-08-23, direct user correction
// after "tosses password less flow?" -> "we need that simplification" -> "search
// clear how toss do it and let's do it as they do"): replaces the free-form
// password IdsTextField this step used to render with AccountPinPad -- see that
// composable's own doc comment for the full sourced account. Login shows a single
// PIN pad (auto-submits at 6 digits, real biometric attempt already tried silently
// first -- see LoginScreen's own LaunchedEffect(stage)); register shows a real
// create-then-confirm pair, same two-step convention PinScreen.kt's own
// PinSetupScreen already established for the separate local app-lock PIN, catching a
// mistyped PIN before it's permanent (this step's own previous doc comment named
// this exact risk for the free-form password it's replacing).
@Composable
private fun PasswordStep(
    isRegisterMode: Boolean,
    pinFirstEntry: String?,
    attemptingPasswordless: Boolean,
    isSubmitting: Boolean,
    errorMessage: String?,
    showReferralField: Boolean,
    onShowReferralField: () -> Unit,
    referralCode: String,
    onReferralCodeChange: (String) -> Unit,
    onPinCreated: (String) -> Unit,
    onPinConfirmed: (String) -> Unit,
    onPinEntered: (String) -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        if (attemptingPasswordless) {
            Spacer(modifier = Modifier.height(48.dp))
            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Ids.colors.brand, strokeWidth = 3.dp)
            Spacer(modifier = Modifier.height(16.dp))
            Text(stringResource(R.string.login_checking_device), style = IdsTypography.Body1, color = Ids.colors.textSecondary)
        } else if (isRegisterMode) {
            if (pinFirstEntry == null) {
                AccountPinPad(
                    headline = stringResource(R.string.login_headline_password_register),
                    subtitle = stringResource(R.string.login_subtitle_password_register),
                    errorMessage = errorMessage,
                    onComplete = onPinCreated,
                )
            } else {
                AccountPinPad(
                    headline = stringResource(R.string.login_headline_password_confirm),
                    errorMessage = errorMessage,
                    busy = isSubmitting,
                    onComplete = onPinConfirmed,
                )
            }
            if (pinFirstEntry == null) {
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
        } else {
            AccountPinPad(
                headline = stringResource(R.string.login_headline_password_login),
                errorMessage = errorMessage,
                busy = isSubmitting,
                onComplete = onPinEntered,
            )
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
