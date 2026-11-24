package rw.itunda.feature.my.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.designsystem.theme.IdsIcons

/** Prototype `.glx` card on the You tab: white, 24dp radius, 20dp above the title and 6dp below the last row. */
@Composable
internal fun YouCard(modifier: Modifier = Modifier, topPadding: androidx.compose.ui.unit.Dp = 20.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Ids.colors.background).padding(top = topPadding, bottom = 6.dp), content = content)
}

/** Card title (20sp/800) with an optional 13sp caption under it. */
@Composable
internal fun YouCardTitle(title: String, caption: String? = null) {
    Text(title, color = Ids.colors.textPrimary, fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 20.dp, bottom = 6.dp))
    if (caption != null) Text(caption, color = Ids.colors.textTertiary, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(start = 20.dp, bottom = 8.dp))
}

/** Prototype row: 60dp, 40dp soft tile at x=20, 17sp/700 title, 14sp/600 muted value and a chevron. */
@Composable
internal fun YouRow(title: String, trailing: String? = null, onClick: (() -> Unit)? = null, subtitle: String? = null, glyph: @Composable () -> Unit) {
    Row(
        Modifier.padding(horizontal = 12.dp).fillMaxWidth().height(60.dp).clip(RoundedCornerShape(16.dp))
            .then(if (onClick != null) Modifier.pressScaleClickable(onClick = onClick) else Modifier).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(0.dp))
        Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) { glyph() }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(title, color = Ids.colors.textPrimary, fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold)
            if (subtitle != null) Text(subtitle, color = Ids.colors.textTertiary, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)
        }
        if (trailing != null) Text(trailing, color = Ids.colors.textTertiary, fontSize = 14.sp, lineHeight = 19.6.sp, fontWeight = FontWeight.SemiBold)
        Icon(IdsIcons.ChevronRight, contentDescription = null, tint = Ids.colors.textTertiary, modifier = Modifier.padding(start = 4.dp).size(16.dp))
    }
}

/** Prototype verified tile: soft 40dp square with a green check disc. */
@Composable
internal fun VerifiedTile() {
    Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Ids.colors.surfaceSoft), contentAlignment = Alignment.Center) {
        Box(Modifier.size(26.dp).clip(CircleShape).background(Color(0xFF30B27F)), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }
}
