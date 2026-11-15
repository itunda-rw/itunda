package rw.itunda.feature.jobs.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.JobPostDto

private fun tileFor(category: String): Pair<String, Color> = when {
    category.contains("food", true) || category.contains("kitchen", true) -> "☕" to Color(0xFFEDE4F7)
    category.contains("shop", true) || category.contains("retail", true) -> "🛍" to Color(0xFFF9E3D8)
    category.contains("tutor", true) || category.contains("teach", true) -> "🎓" to Color(0xFFDDEEFA)
    else -> "💼" to Color(0xFFFFF1CC)
}

/** Prototype Jobs row: title, place and time, pay per period, tags, and a category tile on the right. */
@Composable
fun JobPostListRow(
    post: JobPostDto,
    categoryLabel: String,
    timeAgo: String,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
    onOpen: () -> Unit,
) {
    val (emoji, tint) = tileFor(categoryLabel)
    val period = when (post.payType) { "HOURLY" -> " / hr"; "DAILY" -> " / day"; else -> " / task" }
    Row(modifier.fillMaxWidth().pressScaleClickable(onClick = onOpen).padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(post.title, color = Ids.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(post.neighborhood, timeAgo).joinToString(" · "), color = Ids.colors.textSecondary, fontSize = 12.sp)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(String.format(Locale.US, "%,.0f RWF", post.payAmount), color = Ids.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(period, color = Ids.colors.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(bottom = 1.dp))
            }
            Text("New · $categoryLabel", color = Ids.colors.textTertiary, fontSize = 12.sp)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(84.dp).clip(RoundedCornerShape(14.dp)).background(tint), contentAlignment = Alignment.Center) { Text(emoji, fontSize = 36.sp) }
            trailing()
        }
    }
}
