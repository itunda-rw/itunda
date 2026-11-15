package rw.itunda.app.ui

import android.content.ClipData
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.app.R
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.designsystem.components.pressScaleClickable

/**
 * Prototype "Manage account" summary block (v294): bank name, holder, account
 * number and two chips -- Copy number and Share. Both actions are real (system
 * clipboard / share sheet); nothing here is faked.
 */
@Composable
internal fun AccountSummaryBlock(holderName: String, formattedNumber: String, rawNumber: String) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val copied = stringResource(R.string.account_manage_copied)
    val shareText = if (holderName.isBlank()) formattedNumber else "$holderName · $formattedNumber"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Ids.colors.chip)
            .padding(18.dp),
    ) {
        Text(stringResource(R.string.account_manage_bank), color = Ids.colors.textSecondary, fontSize = 13.sp)
        if (holderName.isNotBlank()) {
            Text(
                holderName, color = Ids.colors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp),
            )
        }
        Text(
            formattedNumber, color = Ids.colors.textSecondary, fontSize = 16.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 2.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 14.dp)) {
            SummaryChip(stringResource(R.string.account_manage_copy)) {
                clipboard.setText(androidx.compose.ui.text.AnnotatedString(rawNumber))
                Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
            }
            SummaryChip(stringResource(R.string.account_manage_share)) {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, shareText)
                context.startActivity(Intent.createChooser(send, null))
            }
        }
    }
}

@Composable
private fun SummaryChip(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Ids.colors.background)
            .pressScaleClickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Ids.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}
