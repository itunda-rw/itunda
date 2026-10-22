package rw.itunda.feature.home.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.theme.Ids

@Composable
internal fun HomePrototypeSectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        color = Ids.colors.textPrimary,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.25).sp,
        modifier = modifier,
    )
}

@Composable
internal fun HomePrototypePrimaryAction(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Ids.colors.brand)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun HomePrototypeSecondaryAction(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Ids.colors.chip)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Ids.colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun HomePrototypeActionTile(
    symbol: String,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(17.dp))
                .background(Ids.colors.chip),
            contentAlignment = Alignment.Center,
        ) {
            Text(symbol, color = Ids.colors.textPrimary, fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(
            text = label,
            color = Ids.colors.textSecondary,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 7.dp),
            maxLines = 2,
        )
    }
}

@Composable
internal fun HomePrototypeNavigationRow(
    symbol: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Ids.colors.chip),
            contentAlignment = Alignment.Center,
        ) {
            Text(symbol, color = Ids.colors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(title, color = Ids.colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Ids.colors.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
        }
        Text("›", color = Ids.colors.textTertiary, fontSize = 24.sp)
    }
}
