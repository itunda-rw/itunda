package rw.itunda.core.designsystem.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import rw.itunda.core.designsystem.theme.Tds
import rw.itunda.core.designsystem.theme.TdsColors
import rw.itunda.core.designsystem.theme.TdsTypography

@Composable
fun TdsButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Tds.colors.brand,
            contentColor = TdsColors.White,
            disabledContainerColor = Tds.colors.divider,
            disabledContentColor = Tds.colors.textTertiary
        )
    ) {
        Text(text = text, style = TdsTypography.Button)
    }
}
