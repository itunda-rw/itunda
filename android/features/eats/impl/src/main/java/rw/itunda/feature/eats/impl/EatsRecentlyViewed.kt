package rw.itunda.feature.eats.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import rw.itunda.core.network.RecentlyViewedRestaurantDto
import rw.itunda.core.network.ShoppingMerchantDto

/**
 * Real "recently viewed restaurants" rail (2026-08-23) -- Baemin/Coupang Eats both show
 * this on the real Eats landing surface ("최근 본 가게"); itunda already shipped the
 * identical real feature for Shop's own product catalog (ShopScreen.kt's own recently-
 * viewed rail) and never ported it to this sibling product -- see
 * RecentlyViewedRestaurantsStore.kt's own doc comment for the full sourced account.
 * Extracted to its own file (not inline in EatsScreen.kt, already one of this codebase's
 * own oversized files) matching this session's established file-size-lint discipline.
 * Tapping a card jumps back to the real restaurant (same shortcut Shop's Deals/Recently
 * Viewed rails use) rather than reopening a possibly-stale cached snapshot.
 */
@Composable
internal fun RecentlyViewedRestaurantsRail(items: List<RecentlyViewedRestaurantDto>, onOpen: (ShoppingMerchantDto) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ClockGlyph(size = 16.dp)
            Text("Recently viewed", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items.forEach { rv ->
                Column(
                    modifier = Modifier.width(140.dp).clip(RoundedCornerShape(Ids.layout.cardCornerRadius)).background(Ids.colors.surface)
                        .pressScaleClickable {
                            onOpen(ShoppingMerchantDto(merchantId = rv.merchantId, businessName = rv.businessName, category = rv.category, cashbackRate = "1%"))
                        }
                        .padding(10.dp),
                ) {
                    Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp))) {
                        if (rv.photoUrl != null) {
                            SubcomposeAsyncImage(
                                model = rv.photoUrl,
                                contentDescription = rv.businessName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                when (painter.state) {
                                    is AsyncImagePainter.State.Success -> SubcomposeAsyncImageContent()
                                    else -> RestaurantPhotoPlaceholder()
                                }
                            }
                        } else {
                            RestaurantPhotoPlaceholder()
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(rv.businessName, color = Ids.colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 2)
                    rv.category?.let { category ->
                        Text(category, color = Ids.colors.textSecondary, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
