package rw.itunda.feature.marketplace.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.compose.AsyncImagePainter
import java.time.Instant
import java.util.Locale
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.components.relativeTimeAgo
import rw.itunda.core.designsystem.itundaface.WishlistHeart
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.ListingDto

/**
 * Marketplace's v1038 browse tile: two-column image-first grid, quiet metadata,
 * price below the title, Safe pay label, and a heart over the photo. Keep the
 * card itself free of a filled container; the prototype uses only the image tile.
 */
@Composable
internal fun MarketplaceGridCard(
    listing: ListingDto,
    isMine: Boolean,
    favorited: Boolean,
    favoriteBusy: Boolean,
    onToggleFavorite: () -> Unit,
    liked: Boolean,
    onToggleLike: () -> Unit,
    hiding: Boolean,
    onHide: () -> Unit,
    viewerLocation: Pair<Double, Double>?,
    onOpen: () -> Unit,
    isScrollTouched: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .pressScaleClickable(isScrollTouched = isScrollTouched, onClick = onOpen),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(20.dp))
                .background(Ids.colors.surfaceSoft),
        ) {
            if (!listing.photoUrl.isNullOrBlank()) {
                SubcomposeAsyncImage(
                    model = listing.photoUrl,
                    contentDescription = listing.title,
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

            when (listing.status.uppercase(Locale.ROOT)) {
                "SOLD" -> Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("SOLD", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                "RESERVED" -> Box(
                    modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Ids.colors.surface)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text("Reserved", color = Ids.colors.textPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            if (isBoosted(listing.boostedUntil)) {
                Box(
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Ids.colors.brand)
                        .padding(horizontal = 7.dp, vertical = 4.dp),
                ) {
                    Text("AD", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (!isMine) {
                Box(
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
                        .size(36.dp).clip(CircleShape).background(Ids.colors.surface)
                        .semantics {
                            contentDescription = if (favorited) "Remove from wishlist" else "Add to wishlist"
                        }
                        .pressScaleClickable(enabled = !favoriteBusy, onClick = onToggleFavorite),
                    contentAlignment = Alignment.Center,
                ) {
                    WishlistHeart(favorited = favorited, size = 20.dp)
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = listing.title,
                color = Ids.colors.textPrimary,
                fontSize = 14.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            val viewerLat = viewerLocation?.first
            val viewerLng = viewerLocation?.second
            val listingLat = listing.latitude
            val listingLng = listing.longitude
            val distance = if (viewerLat != null && viewerLng != null && listingLat != null && listingLng != null) {
                val km = distanceKm(viewerLat, viewerLng, listingLat, listingLng)
                if (km < 1.0) "${(km * 1000).toInt()}m" else String.format(Locale.US, "%.1fkm", km)
            } else null
            val metadata = listOfNotNull(
                listing.neighborhood?.takeIf { it.isNotBlank() },
                distance,
                relativeTimeAgo(listing.createdAt),
            ).joinToString(" · ")
            if (metadata.isNotBlank()) {
                Text(
                    text = metadata,
                    color = Ids.colors.textSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Text(
                text = if (listing.price <= 0.0) "Free 🧡" else String.format(Locale.US, "%,.0f RWF", listing.price),
                color = if (listing.price <= 0.0) Ids.colors.success else Ids.colors.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )

            if (!isMine && listing.price > 0.0 && listing.status.equals("ACTIVE", ignoreCase = true)) {
                Text(
                    text = "Safe pay",
                    color = Ids.colors.textBrand,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!isMine) {
                    Text(
                        text = if (hiding) "…" else "Hide",
                        color = Ids.colors.textSecondary,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .semantics { contentDescription = "Hide this listing -- you won't see it again" }
                            .pressScaleClickable(enabled = !hiding, onClick = onHide),
                    )
                } else {
                    Box(modifier = Modifier.size(1.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WishlistHeart(
                        favorited = liked,
                        size = 16.dp,
                        modifier = Modifier
                            .semantics { contentDescription = if (liked) "Unlike" else "Like" }
                            .pressScaleClickable(onClick = onToggleLike),
                    )
                    Text(
                        text = listing.likeCount.toString(),
                        color = Ids.colors.textTertiary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
        }
    }
}

private fun isBoosted(boostedUntil: String?): Boolean {
    if (boostedUntil.isNullOrBlank()) return false
    return try {
        Instant.parse(boostedUntil).isAfter(Instant.now())
    } catch (_: Exception) {
        false
    }
}

private fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val earthRadiusKm = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = kotlin.math.sin(dLat / 2).let { it * it } +
        kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
        kotlin.math.sin(dLon / 2).let { it * it }
    return earthRadiusKm * 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
}
