package rw.itunda.core.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.theme.Ids

/** Prototype `.bar`: 70dp tall, 48dp back button at x=6 (top 12), 20sp/700 title at x=58 with -0.3sp tracking. */
@Composable
fun ProtoTopBar(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(70.dp).padding(start = 6.dp, end = 12.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).pressScaleClickable(onClick = onBack), contentAlignment = Alignment.Center) {
            ItundaArt("back", ItundaArtSet.Line, 24.dp, tint = Ids.colors.textPrimary)
        }
        Text(title, color = Ids.colors.textPrimary, fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp, modifier = Modifier.padding(start = 4.dp))
    }
}
