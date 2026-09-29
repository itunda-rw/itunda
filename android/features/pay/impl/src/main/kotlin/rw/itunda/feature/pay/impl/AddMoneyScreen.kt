package rw.itunda.feature.pay.impl

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import rw.itunda.core.designsystem.components.IdsButton
import rw.itunda.core.designsystem.components.IdsTextField
import rw.itunda.core.designsystem.components.formatMoney
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.designsystem.theme.IdsIcons
import rw.itunda.core.network.Account
import rw.itunda.core.network.InternalTransferRequest
import rw.itunda.core.network.NetworkClient
import java.util.UUID

/**
 * Real Toss "충전하기" (top up) reference (2026-09-12, direct user-supplied Toss Pay
 * screenshots) -- closes the exact gap PayTab.kt's own onAddMoney call site
 * previously left dead (`onAddMoney = { openAccountDetail = null }`, matching web's
 * identical disclosed gap in PayHub.tsx). Picks a source from the caller's OTHER
 * real accounts (never a fabricated external MyData-style balance -- itunda has no
 * such integration) and a real amount, then posts the same internal-transfer
 * endpoint the reverse "옮기기" direction would also use. Own file, matching this
 * screen family's established "genuinely distinct flow, own file" convention.
 */
private val QUICK_AMOUNTS = listOf(1000, 5000, 10000)

@Composable
fun AddMoneyScreen(destination: Account, sourceOptions: List<Account>, onBack: () -> Unit, onDone: () -> Unit) {
    BackHandler(onBack = onBack)
    var sourceId by remember { mutableStateOf(sourceOptions.firstOrNull()?.id) }
    var amountInput by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val genericError = stringResource(R.string.add_money_error)

    fun submit() {
        val source = sourceOptions.find { it.id == sourceId } ?: return
        val amount = amountInput.trim().toBigDecimalOrNull()
        if (amount == null || amount <= java.math.BigDecimal.ZERO) return
        busy = true
        error = null
        scope.launch {
            try {
                NetworkClient.apiService.internalTransfer(
                    UUID.randomUUID().toString(),
                    InternalTransferRequest(source.id, destination.id, amount),
                )
                onDone()
            } catch (e: Exception) {
                error = e.message ?: genericError
            } finally {
                busy = false
            }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Ids.layout.screenHorizontal, vertical = 12.dp)) {
            Box(
                modifier = Modifier.size(Ids.layout.minTouchTarget).clip(CircleShape).pressScaleClickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(IdsIcons.Back, contentDescription = stringResource(R.string.back), modifier = Modifier.size(18.dp), tint = Ids.colors.textPrimary)
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = Ids.layout.screenHorizontal)) {
            Text(
                stringResource(R.string.add_money_title), fontWeight = FontWeight.Bold, fontSize = 20.sp,
                modifier = Modifier.padding(bottom = 20.dp),
            )

            if (sourceOptions.isEmpty()) {
                Text(stringResource(R.string.add_money_no_source), color = Ids.colors.textSecondary, fontSize = 13.sp)
            } else {
                Text(stringResource(R.string.add_money_from), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
                sourceOptions.forEach { account ->
                    Row(
                        modifier = Modifier.fillMaxWidth().pressScaleClickable(onClick = { sourceId = account.id }).padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = sourceId == account.id, onClick = { sourceId = account.id })
                            Text(account.nickname ?: account.accountName, fontSize = 14.sp)
                        }
                        Text("${formatMoney(account.balance)} RWF", color = Ids.colors.textSecondary, fontSize = 13.sp)
                    }
                }

                Text(
                    stringResource(R.string.add_money_amount), fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                )
                IdsTextField(value = amountInput, onValueChange = { amountInput = it }, label = stringResource(R.string.add_money_amount), isAmount = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    QUICK_AMOUNTS.forEach { quick ->
                        IdsButton(
                            text = "+${formatMoney(quick.toDouble())}",
                            onClick = { amountInput = ((amountInput.toIntOrNull() ?: 0) + quick).toString() },
                            variant = rw.itunda.core.designsystem.components.IdsButtonVariant.Tinted,
                            size = rw.itunda.core.designsystem.components.IdsButtonSize.Small,
                        )
                    }
                }

                error?.let { Text(it, color = Ids.colors.danger, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp)) }

                Box(modifier = Modifier.padding(top = 20.dp, bottom = 24.dp)) {
                    IdsButton(
                        text = stringResource(if (busy) R.string.add_money_submitting else R.string.add_money_submit),
                        enabled = !busy && sourceId != null && amountInput.toBigDecimalOrNull()?.let { it > java.math.BigDecimal.ZERO } == true,
                        onClick = ::submit,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
