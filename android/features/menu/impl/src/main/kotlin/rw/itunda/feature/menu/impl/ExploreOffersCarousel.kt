package rw.itunda.feature.menu.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids

/** One offer card: white text on the brand gradient, with a big emoji on the right (prototype v1038). */
internal data class ExploreOffer(val title: String, val subtitle: String, val art: String)

// Placeholder offers until a real offers endpoint exists; they mirror the prototype's three
// cards. Nothing here claims a real campaign, and tapping one does nothing yet.
private val demoOffers = listOf(
    ExploreOffer("Invite friends,\nget 10,000 RWF", "Share your code", "color/gift-white"),
    ExploreOffer("3% back when you\npay with itunda Pay", "Scan to pay and earn 3%", "proto/card"),
    ExploreOffer("Save toward a goal\nwith Pockets", "Set a target and watch it fill", "proto/pockets"),
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
    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth()) { page ->
        val offer = offers[page]
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF5B54D6), Color(0xFF8F89FF))))
                .pressScaleClickable { onClick(offer) }
                .padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 16.dp),
        ) {
            Column(Modifier.align(Alignment.CenterStart)) {
                Text(offer.title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 26.sp)
                Text(offer.subtitle, color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp, bottom = 14.dp))
            }
            Box(Modifier.align(Alignment.CenterEnd)) { ExploreArt(offer.art, 84.dp) }
            Row(Modifier.align(Alignment.BottomStart), verticalAlignment = Alignment.CenterVertically) {
                offers.indices.forEach { i ->
                    val on = i == pagerState.currentPage
                    Box(
                        Modifier.padding(end = 4.dp).height(6.dp).width(if (on) 18.dp else 6.dp).clip(CircleShape)
                            .background(Color.White.copy(alpha = if (on) 1f else 0.5f)),
                    )
                }
            }
        }
    }
}
