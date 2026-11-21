package rw.itunda.feature.community.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.ItundaArt
import rw.itunda.core.designsystem.components.ItundaArtSet
import rw.itunda.core.designsystem.theme.Ids

/** Human label for a post category id: the prototype calls questions "Ask". */
fun communityCategoryTag(category: String): String =
    if (category == "question") "Ask" else category.replace('_', ' ').replaceFirstChar { it.uppercase() }

/** Prototype post header: community avatar, author name, "neighbourhood · time", then a small category tag. */
@Composable
fun CommunityPostHeader(authorName: String, meta: String, categoryTag: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0xFFE4F3E8)), contentAlignment = Alignment.Center) { ItundaArt("community", ItundaArtSet.Color, 28.dp) }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(authorName, color = Ids.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(meta, color = Ids.colors.textSecondary, fontSize = 13.sp)
            }
        }
        Text(
            categoryTag, color = Ids.colors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 10.dp).clip(RoundedCornerShape(10.dp)).background(Ids.colors.surfaceSoft).padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/** One reply: bold author, body, then a quiet Reply affordance row. */
@Composable
fun CommunityReplyRow(author: String, body: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(author, color = Ids.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(body, color = Ids.colors.textPrimary, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp))
    }
}
