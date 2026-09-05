package rw.itunda.feature.banking.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import retrofit2.HttpException
import rw.itunda.core.designsystem.components.AmountKeypadInput
import rw.itunda.core.designsystem.components.FixedBottomCta
import rw.itunda.core.designsystem.components.IdsButton
import rw.itunda.core.designsystem.components.IdsTextField
import rw.itunda.core.designsystem.components.IdsToast
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.CreateWeeklySavingsPlanRequest
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.superAppErrorMessage
import java.io.IOException
import java.math.BigDecimal

// Split out of WeeklySavingsScreen.kt (2026-09-05) once the module extraction pushed
// that file back over the file-size-lint threshold -- same "split out the create-flow
// content, keep the list/detail screen behind" shape WeeklySavingsIntroContent.kt's
// own doc comment already established for this exact file back on 2026-08-26.

internal data class EscalationOption(val rate: BigDecimal, val label: String)

internal val escalationOptions = listOf(
    EscalationOption(BigDecimal("0.00"), "Flat"),
    EscalationOption(BigDecimal("0.10"), "+10%"),
    EscalationOption(BigDecimal("0.20"), "+20%"),
    EscalationOption(BigDecimal("0.30"), "+30%"),
    EscalationOption(BigDecimal("0.50"), "+50%"),
    EscalationOption(BigDecimal("1.00"), "+100%"),
)

@Composable
internal fun WeeklySavingsCreateContent(onCreated: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var baseAmount by remember { mutableStateOf("") }
    var selectedRate by remember { mutableStateOf(escalationOptions.first()) }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    fun submit() {
        val amountBd = baseAmount.trim().toBigDecimalOrNull()
        if (name.isBlank()) {
            error = "Give your plan a name."
            return
        }
        if (amountBd == null || amountBd <= BigDecimal.ZERO) {
            error = "Enter a real weekly amount."
            return
        }
        submitting = true
        coroutineScope.launch {
            try {
                val request = CreateWeeklySavingsPlanRequest(
                    name = name.trim(),
                    baseWeeklyAmount = amountBd,
                    escalationRate = selectedRate.rate,
                )
                NetworkClient.apiService.createWeeklySavingsPlan(request)
                error = null
                onCreated()
                IdsToast.show(coroutineScope, "26-week plan started.")
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                submitting = false
            }
        }
    }

    FixedBottomCta(
        content = {
            Text(
                "The weekly amount steps up automatically every $ESCALATION_STEP_WEEKS weeks by your chosen rate, " +
                    "and keeping an unbroken streak all the way to week $TERM_WEEKS earns a bonus interest rate on " +
                    "top of the base rate.",
                color = Ids.colors.textSecondary, fontSize = 13.sp,
            )
            IdsTextField(value = name, onValueChange = { name = it }, label = "Plan name", modifier = Modifier.fillMaxWidth())
            Text("Base weekly amount", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            AmountKeypadInput(
                digits = baseAmount, onDigitsChange = { baseAmount = it },
                quickAmounts = listOf(1_000L, 5_000L),
            )
            Text("Escalation rate", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                escalationOptions.chunked(3).forEach { rowOptions ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowOptions.forEach { option ->
                            val selected = option.rate == selectedRate.rate
                            Box(
                                modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                                    .background(if (selected) Ids.colors.brand else Ids.colors.surfaceSoft)
                                    .pressScaleClickable { selectedRate = option }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(option.label, color = if (selected) Color.White else Ids.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            error?.let { Text(it, color = Ids.colors.danger, fontSize = 13.sp) }
        },
        cta = {
            IdsButton(
                text = if (submitting) "Working…" else "Start plan",
                onClick = { submit() },
                enabled = !submitting,
            )
        },
    )
}
