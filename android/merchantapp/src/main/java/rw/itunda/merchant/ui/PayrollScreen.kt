package rw.itunda.merchant.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import rw.itunda.core.designsystem.components.IdsButton
import rw.itunda.core.designsystem.components.IdsButtonSize
import rw.itunda.core.designsystem.components.IdsButtonVariant
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import rw.itunda.core.designsystem.components.IdsTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Groups
import rw.itunda.core.designsystem.components.EmptyState
import rw.itunda.merchant.network.AddPayrollEmployeeRequest
import rw.itunda.merchant.network.NetworkClient
import rw.itunda.merchant.network.PayrollEmployeeDto
import rw.itunda.merchant.network.PayrollRunResponse
import rw.itunda.merchant.network.isDeviceNotVerifiedError

// Real B2B payroll -- see rw.itunda.merchant.PayrollService's own doc comment for why
// this is real wallet-to-wallet money movement, not a demo. merchant-mfe already has
// this (PayrollScreen.tsx); this is the first native client (Android/iOS).
@Composable
fun PayrollTab() {
    var roster by remember { mutableStateOf<List<PayrollEmployeeDto>?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var runResult by remember { mutableStateOf<PayrollRunResponse?>(null) }
    val scope = rememberCoroutineScope()

    fun load() {
        loadError = null
        scope.launch {
            try {
                roster = NetworkClient.apiService.getPayrollRoster().employees
            } catch (e: Exception) {
                loadError = "Could not load the payroll roster."
            }
        }
    }
    LaunchedEffect(Unit) { load() }

    val result = runResult
    if (result != null) {
        PayrollRunConfirmation(result = result, onDone = { runResult = null; load() })
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { AddEmployeeCard(onAdded = ::load) }
        item {
            RosterHeaderCard(
                roster = roster,
                error = loadError,
                onReload = ::load,
                onRunPayroll = { runResult = it },
            )
        }
        val list = roster
        if (list != null) {
            items(list, key = { it.id }) { employee -> EmployeeRow(employee = employee, onChanged = ::load) }
        }
    }
}

@Composable
private fun AddEmployeeCard(onAdded: () -> Unit) {
    var phoneNumber by remember { mutableStateOf("") }
    var salaryAmount by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Add an employee", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(
                "Must be an existing Itunda user's phone number -- payroll pays directly into their wallet.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IdsTextField(value = phoneNumber, onValueChange = { phoneNumber = it }, label = "Phone number", modifier = Modifier.fillMaxWidth())
            IdsTextField(value = salaryAmount, onValueChange = { salaryAmount = it }, label = "Monthly salary (RWF)", modifier = Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            IdsButton(
                text = if (submitting) "Adding…" else "Add",
                enabled = !submitting,
                onClick = {
                    val salary = salaryAmount.toBigDecimalOrNull()
                    if (phoneNumber.isBlank() || salary == null || salary <= java.math.BigDecimal.ZERO) {
                        error = "Enter a real phone number and salary."
                        return@IdsButton
                    }
                    submitting = true
                    error = null
                    scope.launch {
                        try {
                            NetworkClient.apiService.addPayrollEmployee(AddPayrollEmployeeRequest(phoneNumber.trim(), salary))
                            phoneNumber = ""; salaryAmount = ""
                            onAdded()
                        } catch (e: Exception) {
                            error = "Could not add this employee."
                        } finally {
                            submitting = false
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun RosterHeaderCard(
    roster: List<PayrollEmployeeDto>?,
    error: String?,
    onReload: () -> Unit,
    onRunPayroll: (PayrollRunResponse) -> Unit,
) {
    var runError by remember { mutableStateOf<String?>(null) }
    var running by remember { mutableStateOf(false) }
    var needsDeviceVerification by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Real fix (2026-08-10): found live-testing bank-mfe's identical device-
    // verification flow -- onVerified below used to just clear the flag, so
    // completing the password prompt did nothing; the merchant still had to find and
    // tap "Run payroll" a second time for the exact batch they'd already confirmed.
    fun runPayroll() {
        running = true
        runError = null
        needsDeviceVerification = false
        scope.launch {
            try {
                val result = NetworkClient.apiService.runPayroll()
                onRunPayroll(result)
            } catch (e: retrofit2.HttpException) {
                if (isDeviceNotVerifiedError(e)) {
                    needsDeviceVerification = true
                } else {
                    runError = "Could not run payroll."
                }
            } catch (e: Exception) {
                runError = "Could not run payroll."
            } finally {
                running = false
            }
        }
    }

    if (needsDeviceVerification) {
        DeviceStepUpDialog(
            onVerified = { runPayroll() },
            onCancel = { needsDeviceVerification = false },
        )
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (error != null) {
                Text(error, color = MaterialTheme.colorScheme.error)
                IdsButton(text = "Retry", variant = IdsButtonVariant.Tinted, size = IdsButtonSize.Medium, onClick = onReload)
                return@Column
            }
            if (roster == null) {
                Text("Loading…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }
            val total = roster.fold(java.math.BigDecimal.ZERO) { acc, e -> acc + e.salaryAmount }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Roster (${roster.size})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }
            runError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            IdsButton(
                text = if (running) "Running…" else "Run payroll (${"%,.0f".format(total)} RWF)",
                enabled = !running && roster.isNotEmpty(),
                onClick = { runPayroll() },
            )
            if (roster.isEmpty()) {
                EmptyState("No employees on the roster yet — add one above to start running payroll.", icon = Icons.Outlined.Groups)
            }
        }
    }
}

@Composable
private fun EmployeeRow(employee: PayrollEmployeeDto, onChanged: () -> Unit) {
    var removing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(employee.employeeName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                Text("${"%,.0f".format(employee.salaryAmount)} RWF", style = MaterialTheme.typography.bodyMedium)
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            IdsButton(
                text = if (removing) "…" else "Remove",
                enabled = !removing,
                variant = IdsButtonVariant.Tinted,
                size = IdsButtonSize.Small,
                onClick = {
                    removing = true
                    error = null
                    scope.launch {
                        try {
                            NetworkClient.apiService.removePayrollEmployee(employee.id)
                            onChanged()
                        } catch (e: Exception) {
                            error = "Could not remove this employee."
                        } finally {
                            removing = false
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun PayrollRunConfirmation(result: PayrollRunResponse, onDone: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Payroll paid", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
        Text("${"%,.0f".format(result.totalAmount)} RWF", style = MaterialTheme.typography.headlineMedium)
        Text("${result.employeeCount} employees paid", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(result.payslips) { p ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(p.employeeName, style = MaterialTheme.typography.bodySmall)
                    Text("${"%,.0f".format(p.amount)} RWF", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        IdsButton(text = "Back to roster", onClick = onDone)
    }
}
