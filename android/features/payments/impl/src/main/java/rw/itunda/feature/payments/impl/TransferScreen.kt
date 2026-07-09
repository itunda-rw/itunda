package rw.itunda.feature.payments.impl

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import rw.itunda.core.designsystem.components.TdsButton
import rw.itunda.core.designsystem.components.TdsListRow
import rw.itunda.core.designsystem.theme.TdsColors
import rw.itunda.core.designsystem.theme.TdsTypography

/**
 * Toss-style Transfer Quote Screen
 */
@Composable
fun TransferQuoteScreen(
    recipientName: String,
    amount: String,
    fee: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(top = 40.dp)) {
        Text(
            text = "Transfer to $recipientName",
            style = TdsTypography.Title1,
            color = TdsColors.Gray900,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        TdsListRow(
            title = "Transfer Amount",
            rightText = "$amount RWF",
            onClick = {}
        )
        
        TdsListRow(
            title = "Fee",
            rightText = if (fee == "0") "Free" else "$fee RWF",
            onClick = {}
        )
        
        Spacer(modifier = Modifier.weight(1f))
        
        Row(modifier = Modifier.padding(24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            TdsButton(
                text = "Cancel",
                onClick = onCancel,
                modifier = Modifier.weight(1f),
                enabled = true
            )
            TdsButton(
                text = "Confirm & Send",
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
                enabled = true
            )
        }
    }
}
