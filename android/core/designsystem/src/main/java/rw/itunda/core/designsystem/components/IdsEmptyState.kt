package rw.itunda.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import rw.itunda.core.designsystem.theme.IdsTypography

@Composable
fun IdsEmptyState(
    title: String,
    message: String,
    primaryActionLabel: String? = null,
    onPrimaryAction: (() -> Unit)? = null,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp)
            .semantics { contentDescription = "$title. $message" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = IdsTypography.Title)
        Text(message, style = IdsTypography.Body)
        if (primaryActionLabel != null && onPrimaryAction != null) {
            IdsButton(text = primaryActionLabel, onClick = onPrimaryAction, modifier = Modifier.fillMaxWidth())
        }
        if (secondaryActionLabel != null && onSecondaryAction != null) {
            IdsButton(text = secondaryActionLabel, onClick = onSecondaryAction, modifier = Modifier.fillMaxWidth(), variant = IdsButtonVariant.Tinted)
        }
    }
}
