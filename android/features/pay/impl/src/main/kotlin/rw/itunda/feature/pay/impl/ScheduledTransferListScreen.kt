package rw.itunda.feature.pay.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import rw.itunda.core.designsystem.components.BackTopBar
import rw.itunda.core.designsystem.components.IdsButton
import rw.itunda.core.designsystem.components.IdsButtonSize
import rw.itunda.core.designsystem.components.IdsButtonVariant
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.ScheduledTransferDto
import rw.itunda.core.network.superAppErrorMessage

/**
 * Real Toss 예약송금 (scheduled/reserved one-time transfer) -- see
 * ScheduledTransferDto's own doc comment on the backend. Genuinely distinct from
 * AutoTransferListScreen above (recurring): a single future-dated transfer, real
 * scheduler-executed at 9am on that date via P2pService.sendDirect (unmodified),
 * cancellable any time while still PENDING. Mirrors AutoTransferListScreen's exact
 * shape/conventions.
 */
@Composable
fun ScheduledTransferListScreen(onBack: () -> Unit) {
    var scheduledTransfers by remember { mutableStateOf<List<ScheduledTransferDto>?>(null) }
    var showNewForm by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun load() {
        try {
            scheduledTransfers = NetworkClient.apiService.getMyScheduledTransfers().scheduledTransfers
            error = null
        } catch (e: Exception) {
            error = "Couldn't reach itunda. Check your connection and try again."
        }
    }
    LaunchedEffect(Unit) { load() }

    if (showNewForm) {
        NewScheduledTransferScreen(
            onBack = { showNewForm = false },
            onCreated = {
                showNewForm = false
                scope.launch { load() }
            },
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize().background(Ids.colors.background)) {
        BackTopBar("Scheduled transfer", onBack)
        Column(modifier = Modifier.padding(horizontal = Ids.layout.screenHorizontal, vertical = Ids.layout.screenVertical)) {
            IdsButton(
                "+ New scheduled transfer",
                onClick = { showNewForm = true },
                modifier = Modifier.fillMaxWidth(),
                variant = IdsButtonVariant.Filled,
                size = IdsButtonSize.Medium,
            )
        }
        error?.let {
            Text(it, color = Ids.colors.danger, fontSize = 13.sp, modifier = Modifier.padding(horizontal = Ids.layout.screenHorizontal))
        }
        val list = scheduledTransfers
        if (list == null) {
            // Loading -- no fabricated content shown meanwhile.
        } else if (list.isEmpty()) {
            Text(
                "No scheduled transfers yet.",
                color = Ids.colors.textSecondary,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = Ids.layout.screenHorizontal, vertical = 24.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = Ids.layout.screenHorizontal),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(list, key = { it.id }) { transfer ->
                    ScheduledTransferCard(
                        transfer = transfer,
                        onCancel = {
                            scope.launch {
                                try {
                                    NetworkClient.apiService.cancelScheduledTransfer(transfer.id)
                                    load()
                                } catch (e: retrofit2.HttpException) {
                                    error = superAppErrorMessage(e)
                                } catch (e: Exception) {
                                    error = "Couldn't reach itunda. Check your connection and try again."
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ScheduledTransferCard(transfer: ScheduledTransferDto, onCancel: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Ids.layout.cardCornerRadius)).background(Ids.colors.surface).padding(16.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(transfer.recipientName, color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text("%,.0f RWF".format(transfer.amount), color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
        Text("Sends ${transfer.scheduledDate}", color = Ids.colors.textSecondary, fontSize = 12.sp)
        Text(transfer.status, color = if (transfer.status == "FAILED") Ids.colors.danger else Ids.colors.textTertiary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        transfer.failureReason?.let {
            Text("Failed: $it", color = Ids.colors.danger, fontSize = 12.sp)
        }
        if (transfer.status == "PENDING") {
            Spacer(Modifier.height(10.dp))
            IdsButton("Cancel", onClick = onCancel, variant = IdsButtonVariant.Tinted, size = IdsButtonSize.Small)
        }
    }
}
