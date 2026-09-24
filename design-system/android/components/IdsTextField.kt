package rw.itunda.core.designsystem.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.designsystem.theme.IdsIcons
import rw.itunda.core.designsystem.theme.IdsTypography

/**
 * Real fix, 2026-08-03: this design system had colors/typography/layout/a real button
 * component, but no text field -- every screen that needed input (35 files, confirmed
 * via a repo-wide grep) reached for Material3's raw `OutlinedTextField` directly, and
 * only one of those 35 (`LoginScreen.kt`) even bothered to override its default colors
 * -- the other 34 render with Material3's stock light-theme purple/gray palette, which
 * doesn't match this app's real Toss-style dark theme at all. That mismatch, repeated
 * across most of the app's screens, is the real, systemic root of "everything looks
 * unstyled/awful," not any one screen's fault.
 *
 * Matches Toss's own real text field convention (a filled/tonal surface, not an
 * outlined border -- TDS's own docs and every real Toss screenshot use a soft filled
 * background that lifts slightly on focus, never a hairline rectangle) -- `surfaceSoft`
 * at rest, `surface` + a brand-colored indicator on focus, `dangerTint`/`danger` when
 * `isError` is set. 12dp corners, matching `IdsButton`'s own shape.
 */
@Composable
fun IdsTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    isError: Boolean = false,
    errorText: String? = null,
    // Real addition, 2026-08-03 -- Hood's own NewListingForm (Marketplace) had a real
    // neutral hint ("Use a public landmark, not a home address") that Material3's
    // OutlinedTextField supports via supportingText, which this component didn't
    // expose yet -- the gap that kept that one field on the raw, unstyled
    // OutlinedTextField this component exists to replace.
    supportingText: String? = null,
    singleLine: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardType: KeyboardType = KeyboardType.Text,
    trailingIcon: (@Composable () -> Unit)? = null,
    // Real "No More Loading"/"Minimum Input" simplicity addition (Toss's own researched,
    // sourced pattern -- toss.tech/article/4-ways-for-minimum-input, rule #4: "Activate focus
    // state upon entering the page so the keyboard appears automatically" -- see
    // docs/DESIGN_REFERENCES.md §11 recommendation #2). Defaults to false so every one of
    // this component's existing call sites keeps its current behavior unchanged -- only a
    // field that's the obvious, sole next action on its screen (e.g. a just-sent
    // verification code) should opt in.
    autoFocus: Boolean = false,
    // Real "Minimum Input" simplicity addition (docs/DESIGN_REFERENCES.md §11/§12): a
    // show/hide toggle cuts mistyped-password retries -- a local, client-only UI
    // affordance (the value never leaves this field either way), not a security
    // control, matching §12's own "security and simplicity together" framing. When
    // true, this component manages its own visualTransformation and renders the
    // toggle as the trailing icon itself, overriding any caller-supplied
    // visualTransformation/trailingIcon (no existing call site sets both this and
    // either of those together).
    isPassword: Boolean = false,
    // Real addition (2026-08-13, direct user research request comparing against real
    // Toss login screens): a value-only field driven by a custom on-screen keypad
    // rather than the platform IME -- see LoginScreen.kt's own NumericKeypad doc
    // comment for why. Compose's TextField already supports this exact shape
    // (readOnly still shows focus/cursor styling, just never raises the system
    // keyboard); this just exposes it, same opt-in convention as autoFocus/isPassword
    // above so every existing call site is unaffected.
    readOnly: Boolean = false,
    // Real Toss-style live thousand-separator formatting (docs/DESIGN_REFERENCES.md's
    // amount-entry research, 2026-08-12) -- see AmountVisualTransformation's own doc
    // comment. Defaults to false, same opt-in convention as isPassword/autoFocus above,
    // so every existing call site keeps its current unformatted behavior unless it
    // deliberately turns this on. When true, overrides any caller-supplied
    // visualTransformation and forces a numeric keyboard, matching how isPassword
    // already overrides visualTransformation/trailingIcon together.
    isAmount: Boolean = false,
) {
    val focusRequester = remember { FocusRequester() }
    var passwordVisible by remember { mutableStateOf(false) }
    Column(modifier = modifier) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label, style = IdsTypography.Body2) },
            placeholder = placeholder?.let { { Text(it, style = IdsTypography.Body1, color = Ids.colors.textTertiary) } },
            singleLine = singleLine,
            readOnly = readOnly,
            isError = isError,
            visualTransformation = if (isPassword) {
                if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation()
            } else if (isAmount) {
                AmountVisualTransformation
            } else {
                visualTransformation
            },
            keyboardOptions = KeyboardOptions(keyboardType = if (isAmount) KeyboardType.Number else keyboardType),
            trailingIcon = if (isPassword) {
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
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Ids.colors.surface,
                unfocusedContainerColor = Ids.colors.surfaceSoft,
                disabledContainerColor = Ids.colors.surfaceSoft,
                errorContainerColor = Ids.colors.surface,
                focusedIndicatorColor = if (isError) Ids.colors.danger else Ids.colors.brand,
                unfocusedIndicatorColor = Color.Transparent,
                errorIndicatorColor = Ids.colors.danger,
                disabledIndicatorColor = Color.Transparent,
                focusedLabelColor = if (isError) Ids.colors.danger else Ids.colors.brand,
                unfocusedLabelColor = Ids.colors.textSecondary,
                focusedTextColor = Ids.colors.textPrimary,
                unfocusedTextColor = Ids.colors.textPrimary,
                cursorColor = Ids.colors.brand,
                errorCursorColor = Ids.colors.danger,
            ),
        )
        if (autoFocus) {
            // Real fix, found auditing this addition against LoginScreen.kt's own pre-existing
            // rememberAutoFocus helper (which this component's autoFocus is meant to make
            // redundant): "requesting focus in the same frame a composable enters can silently
            // no-op if the node hasn't attached yet" -- that helper's own documented reasoning
            // for waiting one frame first. Matched here so this component doesn't reintroduce
            // the exact bug that helper was written to avoid.
            LaunchedEffect(Unit) {
                delay(80)
                focusRequester.requestFocus()
            }
        }
        if (isError && errorText != null) {
            Text(
                text = errorText,
                style = IdsTypography.Typography7,
                color = Ids.colors.danger,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp),
            )
        } else if (!isError && supportingText != null) {
            Text(
                text = supportingText,
                style = IdsTypography.Typography7,
                color = Ids.colors.textTertiary,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp),
            )
        }
    }
}
