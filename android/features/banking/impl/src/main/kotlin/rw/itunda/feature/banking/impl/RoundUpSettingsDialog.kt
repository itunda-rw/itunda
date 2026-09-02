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
@Composable
internal fun RoundUpSettingsDialog(
    settings: rw.itunda.core.network.RoundUpSettingsDto?,
    goals: List<rw.itunda.core.network.SavingsGoal>,
    onDismiss: () -> Unit,
    onSave: (enabled: Boolean, roundToNearest: Long, goalId: String?) -> Unit,
) {
    var enabled by remember { mutableStateOf(settings?.enabled ?: false) }
    var increment by remember { mutableStateOf(settings?.roundToNearest?.toLong() ?: 100L) }
    var selectedGoalId by remember { mutableStateOf(settings?.targetGoalId ?: goals.firstOrNull()?.id) }

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
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = { onSave(enabled, increment, if (enabled) selectedGoalId else null) }) { Text(stringResource(R.string.home_save)) }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text(stringResource(R.string.home_cancel)) }
        }
    )
}
