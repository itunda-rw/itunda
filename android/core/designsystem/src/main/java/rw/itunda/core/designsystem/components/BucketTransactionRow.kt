package rw.itunda.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.theme.AccentIndigo
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.BucketTransactionDto

// Promoted out of BucketDetailScreen.kt (:app) so it can be shared with any Feature
// module's own bucket-account detail screen, not just :app-internal callers -- same
// "shared UI piece with multiple real callers becomes a cross-Feature boundary
// violation once one of those callers moves to a Feature module" shape this session's
// LedgerFormatting/TransactionDetailRow promotions already established. Still used by
// BucketDetailScreen.kt/Grow31SavingsScreen.kt/UpfrontDepositScreen.kt in :app.
@Composable
fun BucketTransactionRow(tx: BucketTransactionDto) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(38.dp).clip(CircleShape).background(if (tx.isCredit) AccentIndigo else Ids.colors.textTertiary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (tx.isCredit) Icons.Outlined.ArrowDownward else Icons.Outlined.ArrowUpward,
                contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(tx.description, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Ids.colors.textPrimary)
            Text(ledgerFullDateTime(tx.createdAt), fontSize = 12.sp, color = Ids.colors.textSecondary, modifier = Modifier.padding(top = 2.dp))
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                "${if (tx.isCredit) "+" else "-"}%,.0f".format(tx.amount),
                fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (tx.isCredit) AccentIndigo else Ids.colors.textPrimary,
            )
            Text("%,.0f".format(tx.balanceAfter), fontSize = 11.sp, color = Ids.colors.textTertiary, modifier = Modifier.padding(top = 2.dp))
        }
    }
}
