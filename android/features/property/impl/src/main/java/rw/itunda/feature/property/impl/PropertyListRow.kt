package rw.itunda.feature.property.impl

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import rw.itunda.core.designsystem.components.ItundaArt
import rw.itunda.core.designsystem.components.ItundaArtSet
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.PropertyListingDto

/** Prototype Property list row: house tile, title, place and time, price per period, type and size. */
@Composable
fun PropertyListRow(
    listing: PropertyListingDto,
    typeLabel: String,
    timeAgo: String,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
    onOpen: () -> Unit,
) {
    val isRent = listing.listingType == "RENT"
    val facts = listOfNotNull(
        typeLabel,
        listing.bedrooms?.let { if (it == 1) "1 bed" else "$it beds" },
        listing.sizeSqm?.let { String.format(Locale.US, "%.0f m²", it) },
    ).joinToString(" · ")
    Row(
        modifier.fillMaxWidth().pressScaleClickable(onClick = onOpen).padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(96.dp).clip(RoundedCornerShape(16.dp)).background(Ids.colors.surfaceSoft), contentAlignment = Alignment.Center) {
            ItundaArt("house", ItundaArtSet.Proto, 56.dp, fit = true)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(listing.title, color = Ids.colors.textPrimary, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                trailing()
            }
            Text(listOfNotNull(listing.neighborhood, timeAgo).joinToString(" · "), color = Ids.colors.textSecondary, fontSize = 13.sp, lineHeight = 18.sp)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(String.format(Locale.US, "%,.0f RWF", listing.price), color = Ids.colors.textPrimary, fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold)
                if (isRent) Text(" / month", color = Ids.colors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 2.dp))
            }
            Text(facts, color = Ids.colors.textSecondary, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
