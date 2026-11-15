package rw.itunda.feature.banking.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.theme.Ids

/** Hub "Coming up" block: the next scheduled payment. Dummy data until the backend lands. */
@Composable
internal fun BankComingUp() {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Coming up", color = Ids.colors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(Ids.colors.chip), contentAlignment = Alignment.Center) {
                Text("\uD83E\uDD1D", fontSize = 20.sp)
            }
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text("Umurenge Savers", color = Ids.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text("Due Fri 20 Nov", color = Ids.colors.textTertiary, fontSize = 13.sp)
            }
            Text("10,000 RWF", color = Ids.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}
