package rw.itunda.core.designsystem.components

import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.R
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.designsystem.theme.IdsIcons

/**
 * The shared title of the Home / Pay / Explore app bars, measured from the prototype
 * (v1038, 390dp wide): the itunda knot (26dp, grey), "itunda" + the area name ("bank",
 * "pay", "mini apps") in one 24sp / 800 grey run, then a small chevron. No coloured
 * square behind the knot (an earlier version had one; the screenshot comparison
 * against the prototype showed that was wrong).
 */
@Composable
fun IdsAppBarTitle(area: String? = null, onClick: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Row(
        modifier = if (onClick != null) modifier.pressScaleClickable(onClick = onClick) else modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(R.drawable.itunda_mark), contentDescription = null,
            modifier = Modifier.size(26.dp), tint = Color(0xFF7C8594),
        )
        Spacer(Modifier.width(2.dp))
        Text(
            if (area != null) "itunda $area" else "itunda",
            color = Color(0xFF7C8594), fontWeight = FontWeight.ExtraBold, fontSize = 24.sp,
        )
        if (onClick != null) {
            Icon(IdsIcons.ChevronRight, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF7C8594))
        }
    }
}
