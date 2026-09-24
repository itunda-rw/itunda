package rw.itunda.core.designsystem.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import rw.itunda.core.designsystem.theme.Ids

@Composable
fun IdsCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .semantics { role = Role.Checkbox },
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
        if (label != null) {
            Spacer(Modifier.size(Ids.layout.tightGap))
            Text(
                text = label,
                color = if (enabled) Ids.colors.textPrimary else Ids.colors.textTertiary,
            )
        }
    }
}

@Composable
fun IdsRadioButton(
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .semantics { role = Role.RadioButton },
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
            Text(
                text = label,
                color = if (enabled) Ids.colors.textPrimary else Ids.colors.textTertiary,
            )
        }
    }
}

@Composable
fun IdsSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .semantics { role = Role.Switch },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
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
            Text(
                text = label,
                color = if (enabled) Ids.colors.textPrimary else Ids.colors.textTertiary,
            )
        }
    }
}
