package rw.itunda.core.designsystem.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.designsystem.theme.IdsTypography

/**
 * IDS confirmation dialog.
 *
 * Keep dialogs short and action-oriented: one clear title, optional supporting
 * message, and explicit dismiss/confirm actions. Screen-specific dialogs should
 * use this contract instead of styling AlertDialog independently.
 */
@Composable
fun IdsDialog(
    title: String,
    onDismissRequest: () -> Unit,
    confirmLabel: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
    dismissLabel: String? = null,
    confirmEnabled: Boolean = true,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        containerColor = Ids.colors.surface,
        titleContentColor = Ids.colors.textPrimary,
        textContentColor = Ids.colors.textSecondary,
        title = {
            Text(
                text = title,
                style = IdsTypography.Title,
            )
        },
        text = message?.let {
            {
                Text(
                    text = it,
                    style = IdsTypography.Body,
                )
            }
        },
        dismissButton = dismissLabel?.let { label ->
            {
                TextButton(
                    onClick = onDismissRequest,
                    modifier = Modifier.semantics { role = Role.Button },
                ) {
                    Text(
                        text = label,
                        color = Ids.colors.textSecondary,
                        style = IdsTypography.Label,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = confirmEnabled,
                modifier = Modifier.semantics { role = Role.Button },
            ) {
                Text(
                    text = confirmLabel,
                    color = if (confirmEnabled) Ids.colors.brand else Ids.colors.textTertiary,
                    style = IdsTypography.Label,
                )
            }
        },
    )
}
