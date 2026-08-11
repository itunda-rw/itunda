package rw.itunda.core.designsystem.components

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.designsystem.theme.IdsTypography

@Composable
fun IdsListRow(
    title: String,
    subtitle: String? = null,
    rightText: String? = null,
    onClick: () -> Unit
) {
    // Real Toss micro-interaction reference (2026-08-11) -- see IdsButton.kt's own
    // press-scale doc comment; this row is at least as widely tapped (every settings/
    // account list across the app uses it) and had zero press feedback before this.
    val interactionSource = remember { MutableInteractionSource() }
    val pressScale = rememberPressScale(interactionSource)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(pressScale)
            .clickable(interactionSource = interactionSource, indication = LocalIndication.current, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = IdsTypography.Subtitle1,
                color = Ids.colors.textPrimary
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = IdsTypography.Body2,
                    color = Ids.colors.textSecondary
                )
            }
        }
        if (rightText != null) {
            Text(
                text = rightText,
                style = IdsTypography.Subtitle1,
                color = Ids.colors.textPrimary
            )
        }
    }
}
