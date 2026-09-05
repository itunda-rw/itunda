package rw.itunda.feature.pay.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import rw.itunda.core.designsystem.components.BankCardChip
import rw.itunda.core.designsystem.components.CardContactlessGlyph
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.designsystem.theme.IdsColors

// Real swipeable funding-source cards (2026-08-11) -- see PayTab's own doc comment on
// `accounts`/`selectedAccountId` for why these are itunda's own real accounts (MAIN +
// any opened foreign-currency ones) and not fabricated membership/deal cards.
// Settling the pager on a card is a real selection, not cosmetic: it's propagated
// back up to PayTab and becomes the accountId MyPaymentCodeCard's QR is generated
// against, matching the "swipe to choose what you pay with" real KakaoPay behavior
// the user's own reference screenshot showed.
//
// Split out of ItundaAppScreen.kt (2026-08-26) to stay under that file's own
// file-size-lint baseline -- same "extract instead of bumping the baseline"
// convention NewScheduledTransferScreen.kt already established. Not `private`
// (Kotlin `private` is FILE-scoped, not module-scoped) since PayTab in
// ItundaAppScreen.kt still calls this directly.
fun accountCardColor(currency: String): androidx.compose.ui.graphics.Color = when (currency) {
    "RWF" -> IdsColors.Blue600
    "USD" -> IdsColors.Green500
    "EUR" -> androidx.compose.ui.graphics.Color(0xFF7C5CFC)
    "GBP" -> androidx.compose.ui.graphics.Color(0xFF00898A)
    else -> IdsColors.Gray700
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun AccountCardCarousel(
    accounts: List<rw.itunda.core.network.Account>,
    selectedAccountId: String?,
    onSelect: (String) -> Unit,
) {
    val initialPage = accounts.indexOfFirst { it.id == selectedAccountId }.coerceAtLeast(0)
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(initialPage = initialPage) { accounts.size }
    LaunchedEffect(pagerState) {
        androidx.compose.runtime.snapshotFlow { pagerState.settledPage }.collect { page ->
            accounts.getOrNull(page)?.let { onSelect(it.id) }
        }
    }
    Column {
        androidx.compose.foundation.pager.HorizontalPager(
            state = pagerState,
            pageSpacing = 12.dp,
            contentPadding = PaddingValues(horizontal = 56.dp),
            // Real physical-card proportions (roughly the 1.586:1 ISO/IEC 7810 ID-1
            // ratio a real bank card uses) rather than the earlier thin banner shape --
            // closer to the user's own KakaoPay reference screenshot's card thumbnails.
            modifier = Modifier.fillMaxWidth().height(148.dp),
        ) { page ->
            val w = accounts[page]
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        androidx.compose.ui.graphics.Brush.linearGradient(
                            listOf(accountCardColor(w.currency), accountCardColor(w.currency), androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.18f)),
                        ),
                    ),
            ) {
                // Diagonal sheen behind the chip/wordmark/balance -- the same "flat
                // color read as a card" fix (2026-08-26, direct user instruction:
                // "all cards designs should resemble real card") applied to every
                // card-shaped visual in this app.
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            androidx.compose.ui.graphics.Brush.linearGradient(
                                listOf(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.18f), androidx.compose.ui.graphics.Color.Transparent),
                            ),
                        ),
                )
                Column(
                    modifier = Modifier.fillMaxSize().padding(18.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        BankCardChip(size = 30.dp)
                        CardContactlessGlyph(size = 18.dp)
                    }
                    Column {
                        Text(
                            if (w.type == "PAY") "itunda Pay" else "itunda Pay ${w.currency}",
                            color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                        )
                        Text(
                            "${w.currency} ${
                                if (w.currency == "RWF") String.format(Locale.US, "%,.0f", w.availableBalance) else String.format(Locale.US, "%,.2f", w.availableBalance)
                            }",
                            color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold, fontSize = 19.sp,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            accounts.indices.forEach { i ->
                val active = i == pagerState.currentPage
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .size(if (active) 8.dp else 6.dp)
                        .clip(CircleShape)
                        .background(if (active) Ids.colors.brand else Ids.colors.divider),
                )
            }
        }
    }
}
