package rw.itunda.feature.banking.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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

/** Hub "Coming up" block (prototype `.sec`): 20sp heading with 28dp above, then a 64dp row for the next scheduled payment. Dummy data until the backend lands. */
@Composable
internal fun BankComingUp() {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Text("Coming up", color = Ids.colors.textPrimary, fontSize = 20.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp, modifier = Modifier.padding(top = 28.dp, bottom = 12.dp))
        Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp)).background(Ids.colors.surfaceSoft), contentAlignment = Alignment.Center) {
                rw.itunda.core.designsystem.components.ItundaArt("ikimina", rw.itunda.core.designsystem.components.ItundaArtSet.Proto, 32.dp, fit = true)
            }
            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                Text("Umurenge Savers", color = Ids.colors.textPrimary, fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp)
                Text("Due Fri 27 Nov", color = Ids.colors.textTertiary, fontSize = 14.sp, lineHeight = 21.sp, letterSpacing = (-0.2).sp, modifier = Modifier.padding(top = 3.dp))
            }
            Text("10,000", color = Ids.colors.textPrimary, fontSize = 14.sp, lineHeight = 19.6.sp, fontWeight = FontWeight.Bold)
            Text(" RWF", color = Ids.colors.textTertiary, fontSize = 14.sp, lineHeight = 19.6.sp)
        }
    }
}
