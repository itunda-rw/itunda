package rw.itunda.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.components.LocalRealActivity
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
private enum class AuthStage { PHONE, NOT_FOUND, NAME, TERMS, PASSWORD }

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

        // Real Toss/Korean-fintech-style 약관 동의 (terms consent) -- see
        // RegisterRequest.acceptedTermsIds' own doc comment. Fetched once on first
        // entry to register mode; starts empty on purpose (see TermsStep's own doc
        // comment -- never pre-ticked).
        var terms by remember { mutableStateOf<List<rw.itunda.core.network.TermsDocument>>(emptyList()) }
        var acceptedTermsIds by remember { mutableStateOf<Set<String>>(emptySet()) }
        LaunchedEffect(isRegisterMode) {
            if (isRegisterMode && terms.isEmpty()) {
                terms = try { SessionManager.getTerms() } catch (_: Exception) { emptyList() }
            }
        }

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
            AuthStage.TERMS -> 2
            AuthStage.PASSWORD -> if (isRegisterMode) 3 else 1
        }
        val stepCount = if (isRegisterMode) 4 else 2

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
                        acceptedTermsIds = acceptedTermsIds.toList(),
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
                AuthStage.NAME -> stage = AuthStage.TERMS
                AuthStage.TERMS -> stage = AuthStage.PASSWORD
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
                AuthStage.TERMS -> AuthStage.NAME
                AuthStage.PASSWORD -> if (isRegisterMode) AuthStage.TERMS else AuthStage.PHONE
            }
        }

        val requiredTermsAccepted = terms.filter { it.required }.let { it.isNotEmpty() && it.all { t -> acceptedTermsIds.contains(t.id) } }
        val currentStepValid = when (stage) {
            AuthStage.PHONE -> phoneNumber.isNotBlank()
            AuthStage.NOT_FOUND -> true
            AuthStage.NAME -> firstName.isNotBlank() && lastName.isNotBlank()
            AuthStage.TERMS -> requiredTermsAccepted
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
                                AuthStage.TERMS -> TermsStep(
                                    terms = terms,
                                    acceptedTermsIds = acceptedTermsIds,
                                    onToggleTerm = { id ->
                                        acceptedTermsIds = if (acceptedTermsIds.contains(id)) acceptedTermsIds - id else acceptedTermsIds + id
                                    },
                                    onToggleAll = {
                                        val allAccepted = terms.isNotEmpty() && terms.all { acceptedTermsIds.contains(it.id) }
                                        acceptedTermsIds = if (allAccepted) emptySet() else terms.map { it.id }.toSet()
                                    },
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
                    if (stage == AuthStage.NAME || stage == AuthStage.TERMS || stage == AuthStage.PASSWORD) {
                        Spacer(modifier = Modifier.height(Ids.layout.sectionGap))
                        Column(verticalArrangement = Arrangement.spacedBy(Ids.layout.inlineGap)) {
                            if (isRegisterMode && (stage == AuthStage.TERMS || stage == AuthStage.PASSWORD)) {
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
                            AuthStage.PHONE, AuthStage.NAME, AuthStage.TERMS -> stringResource(R.string.login_button_next)
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

