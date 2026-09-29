package rw.itunda.feature.banking.impl

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import retrofit2.HttpException
import rw.itunda.core.designsystem.components.AmountKeypadInput
import rw.itunda.core.designsystem.components.FixedBottomCta
import rw.itunda.core.designsystem.components.IdsButton
import rw.itunda.core.designsystem.components.IdsTextField
import rw.itunda.core.designsystem.components.IdsToast
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.CreateGrow31SavingsPlanRequest
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.superAppErrorMessage
import java.io.IOException
import java.math.BigDecimal
import java.util.UUID

// Split out of Grow31SavingsScreen.kt (2026-09-05) once the module extraction pushed
// that file back over the file-size-lint threshold -- same shape as
// WeeklySavingsCreateContent.kt's own split, and this exact file's own doc comment
// already established this precedent on 2026-08-26 for Grow31IntroContent.kt.
@Composable
internal fun Grow31CreateContent(onCreated: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var dailyAmount by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    fun submit() {
        val amountBd = dailyAmount.trim().toBigDecimalOrNull()
        if (name.isBlank()) {
            error = "Give your plan a name."
            return
        }
        if (amountBd == null || amountBd <= BigDecimal.ZERO) {
            error = "Enter a real daily amount."
            return
        }
        submitting = true
        coroutineScope.launch {
            try {
                val idempotencyKey = UUID.randomUUID().toString()
                val request = CreateGrow31SavingsPlanRequest(name = name.trim(), dailyAmount = amountBd)
                NetworkClient.apiService.createGrow31SavingsPlan(idempotencyKey, request)
                error = null
                onCreated()
                IdsToast.show(coroutineScope, "31-day plan started.")
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
                "Pick a small amount you can realistically save every single day for $TERM_DAYS days. Miss a day and " +
                    "your streak resets -- but your longest streak still locks in a bonus rate at maturity, up to +10% " +
                    "for a full unbroken run.",
                color = Ids.colors.textSecondary, fontSize = 13.sp,
            )
            IdsTextField(value = name, onValueChange = { name = it }, label = "Plan name", modifier = Modifier.fillMaxWidth())
            Text("Daily amount", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            AmountKeypadInput(
                digits = dailyAmount, onDigitsChange = { dailyAmount = it },
                quickAmounts = listOf(500L, 1_000L),
            )
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
