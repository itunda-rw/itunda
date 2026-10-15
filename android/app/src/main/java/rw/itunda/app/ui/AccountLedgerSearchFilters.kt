package rw.itunda.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import rw.itunda.core.designsystem.Ids
import rw.itunda.core.network.TransactionDto

/**
 * Filters the existing account ledger without changing transaction data or balance snapshots.
 * The direction is inferred from the signed-in user's real sender ID.
 */
internal fun filterAccountLedgerTransactions(
    transactions: List<Pair<TransactionDto, Double>>,
    queryText: String,
    filter: String,
    currentUserId: String?,
): List<Pair<TransactionDto, Double>> {
    val query = queryText.trim().lowercase()
    return transactions.filter { (transaction, _) ->
        val isOutgoing = transaction.senderId == currentUserId
        val matchesDirection = when (filter) {
            "IN" -> !isOutgoing
            "OUT" -> isOutgoing
            else -> true
        }
        val matchesQuery = query.isBlank() ||
            transaction.description.lowercase().contains(query) ||
            transaction.type.lowercase().contains(query) ||
            transaction.status.lowercase().contains(query) ||
            transaction.createdAt.lowercase().contains(query)
        matchesDirection && matchesQuery
    }
}

@Composable
internal fun AccountLedgerSearchFilters(
    query: String,
    onQueryChange: (String) -> Unit,
    filter: String,
    onFilterChange: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Ids.colors.chip)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.Search,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = Ids.colors.textTertiary,
            )
            Spacer(modifier = Modifier.width(10.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        stringResource(R.string.account_ledger_search_placeholder),
                        color = Ids.colors.textTertiary,
                        fontSize = 14.sp,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(color = Ids.colors.textPrimary, fontSize = 14.sp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                "ALL" to R.string.account_ledger_filter_all,
                "IN" to R.string.account_ledger_filter_in,
                "OUT" to R.string.account_ledger_filter_out,
            ).forEach { (value, labelRes) ->
                val selected = filter == value
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (selected) Ids.colors.brand.copy(alpha = 0.14f) else Ids.colors.chip)
                        .clickable { onFilterChange(value) }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                ) {
                    Text(
                        stringResource(labelRes),
                        color = if (selected) Ids.colors.brand else Ids.colors.textSecondary,
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    )
                }
            }
        }
    }
}
