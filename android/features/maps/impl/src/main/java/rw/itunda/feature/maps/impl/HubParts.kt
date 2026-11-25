package rw.itunda.feature.maps.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.theme.Ids

/** Exact (trimmed) line boxes so fixed line heights match the prototype even with fallback glyphs. */
internal val hubTrim = androidx.compose.ui.text.TextStyle(lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center, androidx.compose.ui.text.style.LineHeightStyle.Trim.Both))

/** Prototype section heading (19sp / 800) used by the Places and Directions hubs. */
@Composable
internal fun HubHeading(title: String) {
    Text(title, color = Ids.colors.textPrimary, fontSize = 19.sp, lineHeight = 26.sp, fontWeight = FontWeight.ExtraBold, style = hubTrim, modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 14.dp))
}

/** Prototype grey pill: 36dp tall filter chip; filled dark when selected. */
@Composable
internal fun HubPill(label: String, selected: Boolean = false, height: Int = 36, fontSize: Int = 14, suffix: String? = null, onClick: () -> Unit) {
    val bg = if (selected) Ids.colors.textPrimary else Ids.colors.surfaceSoft
    val fg = if (selected) Ids.colors.background else if (height > 36) Ids.colors.textPrimary else Ids.colors.textSecondary
    Box(Modifier.heightIn(min = height.dp).clip(RoundedCornerShape((height / 2).dp)).background(bg).clickable(onClick = onClick).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
        Text(androidx.compose.ui.text.buildAnnotatedString {
            append(label)
            if (suffix != null) withStyle(androidx.compose.ui.text.SpanStyle(color = Ids.colors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)) { append(" $suffix") }
        }, color = fg, fontSize = fontSize.sp, fontWeight = FontWeight.Bold)
    }
}

/** 42dp grey circle + title (+ optional subtitle) row, the hubs' shared list row. */
@Composable
internal fun HubRow(title: String, subtitle: String? = null, trailing: String? = null, onClick: () -> Unit, icon: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = if (subtitle != null) 16.dp else 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(Ids.colors.surfaceSoft), contentAlignment = Alignment.Center) { icon() }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(title, color = Ids.colors.textPrimary, fontSize = if (subtitle != null) 17.sp else 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold, style = hubTrim)
            if (subtitle != null) Text(subtitle, color = Ids.colors.textSecondary, fontSize = 14.sp, lineHeight = 17.sp, style = hubTrim)
        }
        if (trailing != null) Text(trailing, color = Ids.colors.textSecondary, fontSize = 14.sp)
    }
}

/** Grey line glyph used inside the hubs' circles. */
@Composable
internal fun HubGlyph(name: String) {
    rw.itunda.core.designsystem.components.ItundaArt(name, rw.itunda.core.designsystem.components.ItundaArtSet.Line, 22.dp, tint = Ids.colors.textTertiary)
}
