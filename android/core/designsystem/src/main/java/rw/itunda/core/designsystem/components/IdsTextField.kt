package rw.itunda.core.designsystem.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import rw.itunda.core.designsystem.theme.Ids
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
    singleLine: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardType: KeyboardType = KeyboardType.Text,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    Column(modifier = modifier) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label, style = IdsTypography.Body2) },
            placeholder = placeholder?.let { { Text(it, style = IdsTypography.Body1, color = Ids.colors.textTertiary) } },
            singleLine = singleLine,
            isError = isError,
            visualTransformation = visualTransformation,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            trailingIcon = trailingIcon,
            textStyle = IdsTypography.Body1,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
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
        if (isError && errorText != null) {
            Text(
                text = errorText,
                style = IdsTypography.Typography7,
                color = Ids.colors.danger,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp),
            )
        }
    }
}
