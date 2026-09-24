package rw.itunda.core.designsystem.components

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.designsystem.theme.IdsComponentTokens
import rw.itunda.core.designsystem.theme.IdsTypography

@Composable
fun IdsListRow(
    title: String,
    subtitle: String? = null,
    rightText: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    loading: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val interactive = enabled && !loading
    val pressScale = rememberPressScale(interactionSource, interactive)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .scale(pressScale)
            .clickable(
                enabled = interactive,
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
            )
            .semantics {
                role = Role.Button
                this.selected = selected
            }
            .padding(horizontal = Ids.layout.screenHorizontal, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = IdsTypography.Subtitle1,
                color = if (enabled) Ids.colors.textPrimary else Ids.colors.textTertiary,
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(Ids.layout.tightGap / 2))
                Text(
                    text = subtitle,
                    style = IdsTypography.Body2,
                    color = Ids.colors.textSecondary,
                )
            }
        }

        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(IdsComponentTokens.Button.smallIconSize),
                color = Ids.colors.brand,
                strokeWidth = 2.dp,
            )
        } else if (rightText != null) {
            Text(
                text = rightText,
                style = IdsTypography.Subtitle1,
                color = if (enabled) Ids.colors.textPrimary else Ids.colors.textTertiary,
            )
        }
    }
}
