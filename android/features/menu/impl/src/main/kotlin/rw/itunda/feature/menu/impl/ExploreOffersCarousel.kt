package rw.itunda.feature.menu.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids

/** One offer card. Colours are fixed pastels (like the prototype's) so the dark text stays readable in dark mode. */
internal data class ExploreOffer(val title: String, val subtitle: String, val background: Color)

// Placeholder offers shown until a real offers endpoint exists. They mirror the
// prototype's three illustrated cards; nothing here claims a real campaign, and tapping
// one does nothing yet (see the `onClick` default).
private val demoOffers = listOf(
    ExploreOffer("Invite a friend", "You both get 5,000 RWF", Color(0xFFE9E7FF)),
    ExploreOffer("Pay a bill, earn back", "1% back on bills this month", Color(0xFFDDF3E4)),
    ExploreOffer("Ride with itunda", "First 3 rides 500 RWF off", Color(0xFFFFE8D6)),
)

/**
 * Prototype v1019: offers carousel under the shortcut row. Swipe or wait (auto-advances
 * every 4s), dots show where you are.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun ExploreOffersCarousel(
    offers: List<ExploreOffer> = demoOffers,
    onClick: (ExploreOffer) -> Unit = {},
) {
    if (offers.isEmpty()) return
    val pagerState = rememberPagerState { offers.size }
    LaunchedEffect(pagerState, offers.size) {
        while (true) {
            delay(4_000)
            pagerState.animateScrollToPage((pagerState.currentPage + 1) % offers.size)
        }
    }
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth()) { page ->
            val offer = offers[page]
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(104.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(offer.background)
                    .pressScaleClickable { onClick(offer) }
                    .padding(20.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                // Fixed dark ink on a fixed pastel, independent of the theme.
                Text(offer.title, color = Color(0xFF1B1B21), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(offer.subtitle, color = Color(0xFF55555F), fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            offers.indices.forEach { i ->
                Box(
                    Modifier
                        .padding(horizontal = 3.dp)
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (i == pagerState.currentPage) Ids.colors.textPrimary else Ids.colors.surfaceSoft),
                )
            }
        }
    }
}
