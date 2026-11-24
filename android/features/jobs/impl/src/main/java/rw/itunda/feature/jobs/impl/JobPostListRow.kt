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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import rw.itunda.core.designsystem.components.ItundaArt
import rw.itunda.core.designsystem.components.ItundaArtSet
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.JobPostDto

private fun tileFor(category: String): Pair<String, Color> = when {
    category.contains("food", true) || category.contains("kitchen", true) -> "coffee" to Color(0xFFE7DDF7)
    category.contains("shop", true) || category.contains("retail", true) -> "bag" to Color(0xFFF9E3D8)
    category.contains("tutor", true) || category.contains("teach", true) -> "school" to Color(0xFFDDEEFA)
    else -> "briefcase" to Color(0xFFFFF1CC)
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
    val (art, tint) = tileFor(categoryLabel)
    val period = when (post.payType) { "HOURLY" -> " / hr"; "DAILY" -> " / day"; else -> " / task" }
    val divider = Ids.colors.surfaceSoft
    Row(modifier.fillMaxWidth().drawBehind { drawLine(divider, androidx.compose.ui.geometry.Offset(0f, size.height), androidx.compose.ui.geometry.Offset(size.width, size.height), 1.dp.toPx()) }.pressScaleClickable(onClick = onOpen).padding(vertical = 18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(post.title, color = Ids.colors.textPrimary, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(post.neighborhood, timeAgo).joinToString(" · "), color = Ids.colors.textSecondary, fontSize = 13.sp, lineHeight = 18.sp)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(String.format(Locale.US, "%,.0f RWF", post.payAmount), color = Ids.colors.textPrimary, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.ExtraBold)
                Text(period, color = Ids.colors.textSecondary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 2.dp))
            }
            Text("New · $categoryLabel", color = Ids.colors.textSecondary, fontSize = 13.sp, lineHeight = 18.sp)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(76.dp).clip(RoundedCornerShape(22.dp)).background(tint), contentAlignment = Alignment.Center) { ItundaArt(art, ItundaArtSet.Proto, 46.dp, fit = true) }
            trailing()
        }
    }
}
