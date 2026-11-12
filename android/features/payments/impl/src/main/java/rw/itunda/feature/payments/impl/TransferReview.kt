package rw.itunda.feature.payments.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.IdsButton
import rw.itunda.core.designsystem.components.IdsLoading
import rw.itunda.core.designsystem.theme.Ids

/** Prototype "Send X RWF to Y?" check step (v1038): To / From / Amount, then Send. */
@Composable
internal fun TransferReview(amount: Long, to: String, from: String, isSubmitting: Boolean, onBack: () -> Unit, onSend: () -> Unit) {
    val amountText = rwfFormatter.format(amount)
    Column(modifier = Modifier.fillMaxSize().background(Ids.colors.background)) {
        FlowTopBar(onBack)
        Column(modifier = Modifier.weight(1f).padding(horizontal = 24.dp)) {
            Text(
                stringResource(R.string.transfer_review_title, amountText, to),
                color = Ids.colors.textPrimary, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(top = 24.dp, bottom = 32.dp),
            )
            ReviewRow(stringResource(R.string.transfer_review_to), to)
            ReviewRow(stringResource(R.string.transfer_review_from), from)
            ReviewRow(stringResource(R.string.transfer_review_amount), "$amountText RWF")
        }
        if (isSubmitting) {
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) { IdsLoading() }
        } else {
            Box(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
                IdsButton(text = stringResource(R.string.transfer_send), onClick = onSend)
            }
        }
    }
}

@Composable
private fun ReviewRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Ids.colors.textSecondary, fontSize = 15.sp)
        Text(value, color = Ids.colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
