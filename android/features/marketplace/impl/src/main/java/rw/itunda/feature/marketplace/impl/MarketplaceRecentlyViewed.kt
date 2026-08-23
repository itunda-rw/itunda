package rw.itunda.feature.marketplace.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.itundaface.ClockGlyph
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.RecentlyViewedListingDto

/**
 * Real "recently viewed listings" rail (2026-08-24) -- see
 * RecentlyViewedListingsStore.kt's own doc comment for the full sourced account (same
 * real pattern already shipped for Shop's product catalog and Eats' restaurant
 * browse). Extracted to its own file (not inline in MarketplaceScreen.kt, already one
 * of this codebase's own oversized files) matching this session's established
 * file-size-lint discipline. Tapping a card jumps back to the real listing via the
 * caller's own onOpen -- itunda's Marketplace listings are seller-authored and can go
 * stale (price change, sold, removed), so this always re-opens the live listing
 * rather than rendering a possibly-stale cached snapshot inline.
 */
@Composable
internal fun RecentlyViewedListingsRail(items: List<RecentlyViewedListingDto>, onOpen: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ClockGlyph(size = 16.dp)
            Text("Recently viewed", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items.forEach { rv ->
                Column(
                    modifier = Modifier.width(120.dp).clip(RoundedCornerShape(Ids.layout.cardCornerRadius)).background(Ids.colors.surface)
                        .pressScaleClickable { onOpen(rv.listingId) }
                        .padding(10.dp),
                ) {
                    Box(modifier = Modifier.size(100.dp).clip(RoundedCornerShape(10.dp))) {
                        if (rv.photoUrl != null) {
                            SubcomposeAsyncImage(
                                model = rv.photoUrl,
                                contentDescription = rv.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                when (painter.state) {
                                    is AsyncImagePainter.State.Success -> SubcomposeAsyncImageContent()
                                    else -> ListingPhotoPlaceholder()
                                }
                            }
                        } else {
                            ListingPhotoPlaceholder()
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(rv.title, color = Ids.colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 2)
                    Text("%,.0f RWF".format(rv.price), color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}
