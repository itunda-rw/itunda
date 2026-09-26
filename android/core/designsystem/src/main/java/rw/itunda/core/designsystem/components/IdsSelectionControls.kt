package rw.itunda.core.designsystem.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.ui.state.ToggleableState
import rw.itunda.core.designsystem.theme.Ids

@Composable
fun IdsCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: String? = null,
    indeterminate: Boolean = false,
    description: String? = null,
    errorText: String? = null,
) {
    val state = if (indeterminate) ToggleableState.Indeterminate else if (checked) ToggleableState.On else ToggleableState.Off
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .semantics {
                role = Role.Checkbox
                stateDescription = when (state) {
                    ToggleableState.On -> "Checked"
                    ToggleableState.Off -> "Unchecked"
                    ToggleableState.Indeterminate -> "Mixed"
                }
                if (errorText != null) error(errorText)
            },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (indeterminate) {
                TriStateCheckbox(
                    state = state,
                    onClick = { onCheckedChange?.invoke(true) },
                    enabled = enabled,
                    colors = CheckboxDefaults.colors(
                        checkedColor = Ids.colors.brand,
                        uncheckedColor = Ids.colors.iconSecondary,
                        checkmarkColor = Ids.colors.background,
                        disabledCheckedColor = Ids.colors.brand.copy(alpha = 0.4f),
                        disabledUncheckedColor = Ids.colors.iconSecondary.copy(alpha = 0.4f),
                    ),
                )
            } else {
                Checkbox(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    enabled = enabled,
                    colors = CheckboxDefaults.colors(
                        checkedColor = Ids.colors.brand,
                        uncheckedColor = Ids.colors.iconSecondary,
                        checkmarkColor = Ids.colors.background,
                        disabledCheckedColor = Ids.colors.brand.copy(alpha = 0.4f),
                        disabledUncheckedColor = Ids.colors.iconSecondary.copy(alpha = 0.4f),
                    ),
                )
            }
            if (label != null) {
                Spacer(Modifier.size(Ids.layout.tightGap))
                Text(label, color = if (enabled) Ids.colors.textPrimary else Ids.colors.textTertiary)
            }
        }
        if (description != null) Text(description, color = Ids.colors.textSecondary, modifier = Modifier.padding(start = 48.dp))
        if (errorText != null) Text(errorText, color = Ids.colors.danger, modifier = Modifier.padding(start = 48.dp))
    }
}

@Composable
fun IdsRadioButton(
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: String? = null,
    description: String? = null,
    errorText: String? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .semantics {
                role = Role.RadioButton
                stateDescription = if (selected) "Selected" else "Not selected"
                if (errorText != null) error(errorText)
            },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = selected,
                onClick = onClick,
                enabled = enabled,
                colors = RadioButtonDefaults.colors(
                    selectedColor = Ids.colors.brand,
                    unselectedColor = Ids.colors.iconSecondary,
                    disabledSelectedColor = Ids.colors.brand.copy(alpha = 0.4f),
                    disabledUnselectedColor = Ids.colors.iconSecondary.copy(alpha = 0.4f),
                ),
            )
            if (label != null) {
                Spacer(Modifier.size(Ids.layout.tightGap))
                Text(label, color = if (enabled) Ids.colors.textPrimary else Ids.colors.textTertiary)
            }
        }
        if (description != null) Text(description, color = Ids.colors.textSecondary, modifier = Modifier.padding(start = 48.dp))
        if (errorText != null) Text(errorText, color = Ids.colors.danger, modifier = Modifier.padding(start = 48.dp))
    }
}

@Composable
fun IdsSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: String? = null,
    loading: Boolean = false,
    errorText: String? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .semantics {
                role = Role.Switch
                stateDescription = if (loading) "Updating" else if (checked) "On" else "Off"
                if (errorText != null) error(errorText)
            },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled && !loading,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Ids.colors.background,
                    checkedTrackColor = Ids.colors.brand,
                    checkedBorderColor = Ids.colors.brand,
                    uncheckedThumbColor = Ids.colors.background,
                    uncheckedTrackColor = Ids.colors.surfaceSoft,
                    uncheckedBorderColor = Ids.colors.divider,
                    disabledCheckedThumbColor = Ids.colors.background.copy(alpha = 0.7f),
                    disabledCheckedTrackColor = Ids.colors.brand.copy(alpha = 0.35f),
                    disabledUncheckedThumbColor = Ids.colors.background.copy(alpha = 0.7f),
                    disabledUncheckedTrackColor = Ids.colors.surfaceSoft.copy(alpha = 0.6f),
                ),
            )
            if (label != null) {
                Spacer(Modifier.size(Ids.layout.tightGap))
                Text(label, color = if (enabled) Ids.colors.textPrimary else Ids.colors.textTertiary)
            }
            if (loading) {
                Spacer(Modifier.size(8.dp))
                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Ids.colors.brand, strokeWidth = 2.dp)
            }
        }
        if (errorText != null) Text(errorText, color = Ids.colors.danger, modifier = Modifier.padding(start = 56.dp))
    }
}
