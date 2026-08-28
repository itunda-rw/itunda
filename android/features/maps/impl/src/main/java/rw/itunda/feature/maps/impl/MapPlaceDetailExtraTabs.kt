package rw.itunda.feature.maps.impl

import coil.compose.AsyncImage
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.MapPlaceDetailDto

// Real Photos/News tabs + AI-summary/tag-aggregate cards (itunda Maps redesign,
// 2026-08-28, direct Naver Map reference: the place-detail Photos/News tabs) --
// extracted into their own file so MapPlaceDetailView.kt's own real Home/Menu/Reviews
// tab logic doesn't have to grow to make room for these. All read the one real
// consolidated MapPlaceDetailDto (MapPlaceDetailService on the backend).

// Real preset-tag display labels -- mirrors EatsReviewService.EATS_GOOD_POINTS' own
// real vocab on the backend. A local copy, not a cross-module import from
// :features:eats:impl (Konsist forbids cross-Feature-module impl-to-impl imports) --
// same "each module independently re-implements what it needs" convention this
// session's own web port (maps-mfe vs. bank-mfe) already established.
private val EATS_GOOD_POINT_LABELS = mapOf(
    "GREAT_FOOD" to "🍽️ Great food", "GREAT_DESSERT" to "🍰 Great dessert", "NICE_INTERIOR" to "🛋️ Nice interior",
    "GREAT_DRINKS" to "🥤 Great drinks", "GOOD_FOR_CONVERSATION" to "💬 Good for conversation",
)

@Composable
internal fun PlaceAiSummaryCard(detail: MapPlaceDetailDto?) {
    val summary = detail?.aiSummary ?: return
    Row(
        verticalAlignment = androidx.compose.ui.Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .background(Ids.colors.surfaceSoft, RoundedCornerShape(10.dp))
            .padding(10.dp),
    ) {
        Text(
            "AI", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White,
            modifier = Modifier.background(Ids.colors.brand, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
        )
        Text(summary, fontSize = 13.sp, color = Ids.colors.textPrimary)
    }
}

@Composable
internal fun PlaceGoodPointsRow(detail: MapPlaceDetailDto?) {
    val counts = detail?.goodPointCounts?.takeIf { it.isNotEmpty() } ?: return
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        counts.entries.sortedByDescending { it.value }.forEach { (tag, count) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Ids.colors.surfaceSoft, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(EATS_GOOD_POINT_LABELS[tag] ?: tag, fontSize = 13.sp, color = Ids.colors.textPrimary)
                Text("$count", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Ids.colors.textPrimary)
            }
        }
    }
}

@Composable
internal fun PlaceInfoTab(category: String?, openingHours: String?, phoneNumber: String?) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        category?.let { Text("Category: $it", fontSize = 13.sp, color = Ids.colors.textPrimary) }
        openingHours?.let { Text("Hours: $it", fontSize = 13.sp, color = Ids.colors.textPrimary) }
        phoneNumber?.let { Text("Phone: $it", fontSize = 13.sp, color = Ids.colors.textPrimary) }
    }
}

@Composable
internal fun PlacePhotosTab(detail: MapPlaceDetailDto?) {
    val cover = detail?.photoUrl
    val gallery = detail?.photoUrls.orEmpty()
    val photos = (listOfNotNull(cover) + gallery.filter { it != cover }).distinct()
    if (photos.isEmpty()) {
        Text("No photos yet.", fontSize = 13.sp, color = Ids.colors.textTertiary, modifier = Modifier.padding(top = 8.dp))
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(photos) { url ->
            AsyncImage(
                model = url, contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.aspectRatio(1f).clip(RoundedCornerShape(8.dp)),
            )
        }
    }
}

@Composable
internal fun PlaceNewsTab(detail: MapPlaceDetailDto?) {
    val updates = detail?.updates.orEmpty()
    if (updates.isEmpty()) {
        Text("No updates yet.", fontSize = 13.sp, color = Ids.colors.textTertiary, modifier = Modifier.padding(top = 8.dp))
        return
    }
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        updates.forEach { update ->
            Column {
                Text(
                    update.label, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    color = if (update.label == "PROMO") Ids.colors.brand else Ids.colors.success,
                )
                Text(update.title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Ids.colors.textPrimary, modifier = Modifier.padding(top = 2.dp))
                Text(update.body, fontSize = 12.sp, color = Ids.colors.textSecondary, modifier = Modifier.padding(top = 2.dp))
                val period = listOfNotNull(update.periodStart?.take(10), update.periodEnd?.take(10)).joinToString(" ~ ")
                if (period.isNotEmpty()) {
                    Text(period, fontSize = 11.sp, color = Ids.colors.textTertiary, modifier = Modifier.padding(top = 2.dp))
                }
                Text("♡ ${update.likeCount}", fontSize = 11.sp, color = Ids.colors.textTertiary, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}
