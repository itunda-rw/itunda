package rw.itunda.feature.banking.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import rw.itunda.core.designsystem.components.IdsTextField
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.MoneyActionResult

// Moved here from :app's ItundaAppScreen.kt (2026-09-02, Banking Feature-module
// decomposition slice 5) -- confirmed used only by BankHubScreen (this same module).
// targetDate is a plain YYYY-MM-DD text field rather than a picker: the backend takes
// it as a nullable String and bank-mfe passes it the same way, so a picker would be
// inventing a stricter contract than the real one -- and the field is genuinely
// optional.
@Composable
internal fun NewSavingsGoalDialog(
    onDismiss: () -> Unit,
    onCreate: suspend (name: String, targetAmountRwf: Long, monthlyRwf: Long?, targetDate: String?) -> MoneyActionResult,
    onCreated: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var targetAmount by remember { mutableStateOf("") }
    var monthly by remember { mutableStateOf("") }
    var targetDate by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val parsedTarget = targetAmount.filter { it.isDigit() }.toLongOrNull()

    androidx.compose.material3.AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = Ids.colors.surface,
        title = { Text(stringResource(R.string.savings_new_goal_title), color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                IdsTextField(value = name, onValueChange = { name = it }, label = stringResource(R.string.savings_new_goal_name_label), modifier = Modifier.fillMaxWidth())
                IdsTextField(
                    value = targetAmount,
                    onValueChange = { targetAmount = it.filter { c -> c.isDigit() } },
                    label = stringResource(R.string.savings_new_goal_target_label),
                    modifier = Modifier.fillMaxWidth(),
                )
                IdsTextField(
                    value = monthly,
                    onValueChange = { monthly = it.filter { c -> c.isDigit() } },
                    label = stringResource(R.string.savings_new_goal_monthly_label),
                    modifier = Modifier.fillMaxWidth(),
                )
                IdsTextField(value = targetDate, onValueChange = { targetDate = it }, label = stringResource(R.string.savings_new_goal_date_label), modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, color = Ids.colors.danger, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                enabled = !busy && name.isNotBlank() && parsedTarget != null && parsedTarget > 0,
                onClick = {
                    busy = true
                    error = null
                    coroutineScope.launch {
                        when (
                            val result = onCreate(
                                name.trim(),
                                parsedTarget ?: 0L,
                                monthly.toLongOrNull(),
                                targetDate.trim().ifBlank { null },
                            )
                        ) {
                            is MoneyActionResult.Success -> {
                                busy = false
                                onCreated()
                                rw.itunda.core.designsystem.components.IdsToast.show(coroutineScope, "Savings goal created.")
                            }
                            is MoneyActionResult.Failure -> { busy = false; error = result.message }
                            else -> busy = false
                        }
                    }
                },
            ) { Text(if (busy) "…" else stringResource(R.string.savings_new_goal_create), color = Ids.colors.brand, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss, enabled = !busy) {
                Text(stringResource(R.string.scam_report_cancel), color = Ids.colors.textSecondary)
            }
        },
    )
}
