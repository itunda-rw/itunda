package rw.itunda.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.components.AccountPinPad
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import rw.itunda.core.network.TermsDocument
import rw.itunda.core.designsystem.components.IdsKeyboardDockedButton
import rw.itunda.core.designsystem.components.IdsTextField
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.designsystem.theme.IdsIcons
import rw.itunda.core.designsystem.theme.IdsTheme
import rw.itunda.core.designsystem.theme.IdsTypography
import java.util.Base64

// Real fix (2026-08-26): split out of LoginScreen.kt once that file grew past its
// file-size-lint baseline. The language switcher + per-step UI (phone/not-found/
// name/password steps, step dots, completed-field row, brand mark) are only
// rendered from LoginScreen's own AuthStage switch, which stays behind. Several
// flipped private -> internal since their callers stay in the original file.

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
internal fun LanguageSwitcher(locale: String, onLocaleChange: (String) -> Unit) {
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
internal fun StepDots(total: Int, current: Int) {
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
internal fun CompletedFieldRow(label: String, value: String, onClick: () -> Unit) {
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
internal fun PhoneStep(
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
internal fun NotFoundStep() {
    Column {
        StepHeadline(
            stringResource(R.string.login_not_found_headline),
            stringResource(R.string.login_not_found_body),
        )
    }
}

@Composable
internal fun NameStep(
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

// Real Toss/Korean-fintech-style 약관 동의 (terms consent) step -- see
// RegisterRequest.acceptedTermsIds' own doc comment for why this only exists now
// (bank-mfe's RegisterPage.tsx has carried the identical UX since 2026-08-18; this
// app's registration has been silently 400ing without it since that date). Every
// checkbox starts unchecked -- never pre-ticked, the exact dark pattern Korea's real
// 2025-02-14 전자상거래법 amendment bans -- and required terms are grouped before
// optional ones, mirroring the web version field-for-field.
@Composable
internal fun TermsStep(
    terms: List<TermsDocument>,
    acceptedTermsIds: Set<String>,
    onToggleTerm: (String) -> Unit,
    onToggleAll: () -> Unit,
) {
    var expandedTermsId by remember { mutableStateOf<String?>(null) }
    val orderedTerms = remember(terms) { terms.filter { it.required } + terms.filter { !it.required } }
    val allAccepted = terms.isNotEmpty() && terms.all { acceptedTermsIds.contains(it.id) }

    Column {
        StepHeadline(stringResource(R.string.login_headline_terms), stringResource(R.string.login_subtitle_terms))
        Row(
            modifier = Modifier.fillMaxWidth().pressScaleClickable(onClick = onToggleAll).padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = allAccepted, onCheckedChange = { onToggleAll() }, colors = CheckboxDefaults.colors(checkedColor = Ids.colors.brand))
            Text(stringResource(R.string.login_terms_agree_all), style = IdsTypography.Body1.copy(fontWeight = FontWeight.Bold), color = Ids.colors.textPrimary)
        }
        orderedTerms.forEach { term ->
            val expanded = expandedTermsId == term.id
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = acceptedTermsIds.contains(term.id),
                        onCheckedChange = { onToggleTerm(term.id) },
                        colors = CheckboxDefaults.colors(checkedColor = Ids.colors.brand),
                    )
                    Text(
                        text = if (term.required) stringResource(R.string.login_terms_required) else stringResource(R.string.login_terms_optional),
                        style = IdsTypography.Typography7.copy(fontWeight = FontWeight.Bold),
                        color = if (term.required) Ids.colors.danger else Ids.colors.textTertiary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (term.required) Ids.colors.dangerTint else Ids.colors.surfaceSoft)
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = term.title,
                        style = IdsTypography.Body2,
                        color = Ids.colors.textPrimary,
                        modifier = Modifier.weight(1f).pressScaleClickable { expandedTermsId = if (expanded) null else term.id },
                    )
                    Icon(
                        imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = null,
                        tint = Ids.colors.textTertiary,
                        modifier = Modifier.pressScaleClickable { expandedTermsId = if (expanded) null else term.id },
                    )
                }
                if (expanded) {
                    Text(
                        text = term.summary,
                        style = IdsTypography.Typography7,
                        color = Ids.colors.textSecondary,
                        modifier = Modifier.padding(start = 40.dp, bottom = 8.dp),
                    )
                }
            }
        }
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
internal fun PasswordStep(
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
internal fun BrandMark() {
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
