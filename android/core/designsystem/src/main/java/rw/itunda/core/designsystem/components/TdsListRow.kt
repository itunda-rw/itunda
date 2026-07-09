package rw.itunda.core.designsystem.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import rw.itunda.core.designsystem.theme.TdsColors
import rw.itunda.core.designsystem.theme.TdsTypography

@Composable
fun TdsListRow(
    title: String,
    subtitle: String? = null,
    rightText: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = TdsTypography.Subtitle1,
                color = TdsColors.Gray900
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = TdsTypography.Body2,
                    color = TdsColors.Gray600
                )
            }
        }
        if (rightText != null) {
            Text(
                text = rightText,
                style = TdsTypography.Subtitle1,
                color = TdsColors.Gray900
            )
        }
    }
}
