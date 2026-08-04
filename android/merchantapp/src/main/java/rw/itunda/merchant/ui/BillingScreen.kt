package rw.itunda.merchant.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Autorenew
import kotlinx.coroutines.launch
import rw.itunda.core.designsystem.components.EmptyState
import rw.itunda.merchant.network.CreateBillingPlanRequest
import rw.itunda.merchant.network.MerchantBillingPlanDto
import rw.itunda.merchant.network.NetworkClient

// Real Kakao Pay 정기결제/Toss Payments 빌링키-style recurring merchant billing (item 144)
// -- see rw.itunda.merchant.MerchantBillingController's own doc comment. Owner-facing
// plan-management half only; customer subscribe/cancel is already real on all 3
// consumer clients. merchant-mfe already has this (BillingScreen.tsx); this is the
// first native client.
@Composable
fun BillingTab() {
    var plans by remember { mutableStateOf<List<MerchantBillingPlanDto>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun load() {
        error = null
        scope.launch {
            try {
                plans = NetworkClient.apiService.getMyBillingPlans().plans
            } catch (e: Exception) {
                error = "Could not load your billing plans."
            }
        }
    }
    LaunchedEffect(Unit) { load() }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { CreatePlanCard(onCreated = ::load) }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Your billing plans", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "A customer who subscribes is charged immediately, then again automatically every cycle until they cancel.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    val list = plans
                    when {
                        list == null -> Text("Loading…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        list.isEmpty() -> EmptyState("No billing plans yet — create one above for recurring charges.", icon = Icons.Outlined.Autorenew)
                    }
                }
            }
        }
        val list = plans
        if (list != null) {
            items(list, key = { it.id }) { plan -> PlanRow(plan = plan, onChanged = ::load) }
        }
    }
}

@Composable
private fun CreatePlanCard(onCreated: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var intervalDays by remember { mutableStateOf("30") }
    var error by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Create a billing plan", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            IdsTextField(value = name, onValueChange = { name = it }, label = "Plan name", modifier = Modifier.fillMaxWidth())
            IdsTextField(value = description, onValueChange = { description = it }, label = "Description (optional)", modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IdsTextField(value = amount, onValueChange = { amount = it }, label = "Amount (RWF)", modifier = Modifier.fillMaxWidth())
            }
            IdsTextField(value = intervalDays, onValueChange = { intervalDays = it }, label = "Every (days)", modifier = Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            IdsButton(
                text = if (submitting) "Creating…" else "Create plan",
                enabled = !submitting,
                onClick = {
                    val amt = amount.toBigDecimalOrNull()
                    val days = intervalDays.toIntOrNull()
                    if (name.isBlank() || amt == null || amt <= java.math.BigDecimal.ZERO || days == null || days <= 0) {
                        error = "Enter a real plan name, amount, and interval."
                        return@IdsButton
                    }
                    submitting = true
                    error = null
                    scope.launch {
                        try {
                            NetworkClient.apiService.createBillingPlan(
                                CreateBillingPlanRequest(name.trim(), description.trim().ifBlank { null }, amt, days),
                            )
                            name = ""; description = ""; amount = ""; intervalDays = "30"
                            onCreated()
                        } catch (e: Exception) {
                            error = "Could not create this plan."
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
private fun PlanRow(plan: MerchantBillingPlanDto, onChanged: () -> Unit) {
    var deactivating by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(plan.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                Text(
                    if (plan.active) "Active" else "Deactivated",
                    color = if (plan.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold,
                )
            }
            plan.description?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Text(
                "${"%,.0f".format(plan.amount)} RWF every ${plan.intervalDays} day${if (plan.intervalDays == 1) "" else "s"}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            if (plan.active) {
                IdsButton(
                    text = if (deactivating) "…" else "Deactivate",
                    enabled = !deactivating,
                    variant = IdsButtonVariant.Tinted,
                    size = IdsButtonSize.Small,
                    onClick = {
                        deactivating = true
                        error = null
                        scope.launch {
                            try {
                                NetworkClient.apiService.deactivateBillingPlan(plan.id)
                                onChanged()
                            } catch (e: Exception) {
                                error = "Could not deactivate this plan."
                            } finally {
                                deactivating = false
                            }
                        }
                    },
                )
            }
        }
    }
}
