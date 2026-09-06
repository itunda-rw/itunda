package rw.itunda.feature.banking.impl

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids

// Moved here from :app's HomeTabWidgets.kt (2026-09-02, Banking Feature-module
// decomposition slice 5) -- confirmed used only by BankHubScreen (this same module).
// Real stock-destination option (Wealth product-completeness pass, 2026-09-06) --
// see RoundUpSettingsDto.targetStockId's own doc comment: the backend has
// supported this since 2026-07-27, but every client (this one included) only ever
// wired the goal destination. `stocks` is fetched by the caller (BankHubScreen),
// same real Invest market-browse list, not a fabricated/duplicated catalog.
private enum class RoundUpDestination { GOAL, STOCK }

@Composable
internal fun RoundUpSettingsDialog(
    settings: rw.itunda.core.network.RoundUpSettingsDto?,
    goals: List<rw.itunda.core.network.SavingsGoal>,
    stocks: List<rw.itunda.core.network.StockDto>,
    onDismiss: () -> Unit,
    onSave: (enabled: Boolean, roundToNearest: Long, goalId: String?, stockId: String?) -> Unit,
) {
    var enabled by remember { mutableStateOf(settings?.enabled ?: false) }
    var increment by remember { mutableStateOf(settings?.roundToNearest?.toLong() ?: 100L) }
    var destination by remember { mutableStateOf(if (settings?.targetStockId != null) RoundUpDestination.STOCK else RoundUpDestination.GOAL) }
    var selectedGoalId by remember { mutableStateOf(settings?.targetGoalId ?: goals.firstOrNull()?.id) }
    var selectedStockId by remember { mutableStateOf(settings?.targetStockId ?: stocks.firstOrNull()?.id) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.home_round_up_title)) },
        text = {
            Column {
                Text(stringResource(R.string.home_round_up_body), color = Ids.colors.textSecondary, fontSize = 13.sp)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.home_round_up_enable), modifier = Modifier.weight(1f))
                    androidx.compose.material3.Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
                if (enabled) {
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.home_round_up_nearest), color = Ids.colors.textSecondary, fontSize = 13.sp)
                    Row(modifier = Modifier.fillMaxWidth()) {
                        listOf(100L, 500L, 1000L).forEach { option ->
                            Row(
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                modifier = Modifier.pressScaleClickable { increment = option }.padding(end = 8.dp)
                            ) {
                                androidx.compose.material3.RadioButton(selected = increment == option, onClick = { increment = option })
                                Text("$option RWF")
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Save into", color = Ids.colors.textSecondary, fontSize = 13.sp)
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            modifier = Modifier.pressScaleClickable { destination = RoundUpDestination.GOAL }.padding(end = 16.dp)
                        ) {
                            androidx.compose.material3.RadioButton(selected = destination == RoundUpDestination.GOAL, onClick = { destination = RoundUpDestination.GOAL })
                            Text("A savings goal")
                        }
                        Row(
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            modifier = Modifier.pressScaleClickable { destination = RoundUpDestination.STOCK }
                        ) {
                            androidx.compose.material3.RadioButton(selected = destination == RoundUpDestination.STOCK, onClick = { destination = RoundUpDestination.STOCK })
                            Text("A stock")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    if (destination == RoundUpDestination.GOAL) {
                        Text(stringResource(R.string.home_round_up_save_into), color = Ids.colors.textSecondary, fontSize = 13.sp)
                        goals.forEach { goal ->
                            Row(
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().pressScaleClickable { selectedGoalId = goal.id }
                            ) {
                                androidx.compose.material3.RadioButton(selected = selectedGoalId == goal.id, onClick = { selectedGoalId = goal.id })
                                Text(goal.name)
                            }
                        }
                    } else {
                        stocks.forEach { stock ->
                            Row(
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().pressScaleClickable { selectedStockId = stock.id }
                            ) {
                                androidx.compose.material3.RadioButton(selected = selectedStockId == stock.id, onClick = { selectedStockId = stock.id })
                                Text("${stock.symbol} · ${stock.name}")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = {
                val goalId = if (enabled && destination == RoundUpDestination.GOAL) selectedGoalId else null
                val stockId = if (enabled && destination == RoundUpDestination.STOCK) selectedStockId else null
                onSave(enabled, increment, goalId, stockId)
            }) { Text(stringResource(R.string.home_save)) }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text(stringResource(R.string.home_cancel)) }
        }
    )
}
