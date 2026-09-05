package rw.itunda.feature.home.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import java.util.Locale
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.components.rememberCountUp
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.designsystem.theme.IdsIcons

// Moved here from :app's HomeTabWidgets.kt (2026-09-02, Home Feature-module
// decomposition) -- confirmed used only by HomeTab, which moved to this same module
// in the same slice.

@Composable
internal fun HomeSearchBar(query: String, onQueryChange: (String) -> Unit, onClear: () -> Unit) {
    val searchDescription = stringResource(R.string.home_search_description)
    val searchPlaceholder = stringResource(R.string.home_search_placeholder)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Ids.colors.chip)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(IdsIcons.Search, contentDescription = null, tint = Ids.colors.textTertiary, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(10.dp))
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            textStyle = TextStyle(color = Ids.colors.textPrimary, fontSize = 15.sp),
            cursorBrush = SolidColor(Ids.colors.brand),
            singleLine = true,
            modifier = Modifier.weight(1f).semantics { contentDescription = searchDescription },
            decorationBox = { inner -> if (query.isEmpty()) Text(searchPlaceholder, color = Ids.colors.textTertiary, fontSize = 15.sp); inner() },
        )
        if (query.isNotEmpty()) {
            Icon(
                IdsIcons.Close, contentDescription = stringResource(R.string.home_search_clear),
                tint = Ids.colors.textTertiary, modifier = Modifier.size(16.dp).pressScaleClickable(onClick = onClear),
            )
        }
    }
}

@Composable
internal fun HomeSearchSectionHeader(label: String) {
    Text(
        label, color = Ids.colors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

@Composable
internal fun HomeSearchResultRow(result: rw.itunda.core.network.ProductSearchResultDto, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().pressScaleClickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(Ids.colors.chip))
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(result.name, color = Ids.colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(result.merchantName, color = Ids.colors.textTertiary, fontSize = 12.sp)
        }
        Text(String.format(Locale.US, "%,.0f RWF", result.price), color = Ids.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun HomeMarketWidgetRow(
    primaryAccount: rw.itunda.core.network.Account?,
    stocks: List<rw.itunda.core.network.StockDto>,
    onOpenBank: () -> Unit,
    onOpenInvest: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (primaryAccount != null) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Ids.colors.chip)
                    .pressScaleClickable(onClick = onOpenBank)
                    .padding(14.dp),
            ) {
                Text(stringResource(R.string.bank_account_account), color = Ids.colors.textSecondary, fontSize = 12.sp)
                val animatedBalance = rememberCountUp(primaryAccount.balance)
                Text(String.format(Locale.US, "%,.0f ${primaryAccount.currency}", animatedBalance), color = Ids.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
        stocks.forEach { stock ->
            val positive = stock.change >= 0.0
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Ids.colors.chip)
                    .pressScaleClickable(onClick = onOpenInvest)
                    .padding(14.dp),
            ) {
                Text(stock.symbol, color = Ids.colors.textSecondary, fontSize = 12.sp, maxLines = 1)
                Text(String.format(Locale.US, "%,.0f", stock.price), color = Ids.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(
                    "${if (positive) "▲" else "▼"} ${String.format(Locale.US, "%.2f", kotlin.math.abs(stock.changePercent))}%",
                    color = if (positive) Ids.colors.brand else Ids.colors.danger, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
internal fun HomeTrendingGrid(listings: List<rw.itunda.core.network.ListingDto>, onOpenMarketplace: () -> Unit) {
    Column {
        Text(
            stringResource(R.string.home_trending_title),
            color = Ids.colors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        listings.chunked(2).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { listing ->
                    Column(modifier = Modifier.weight(1f).pressScaleClickable(onClick = onOpenMarketplace)) {
                        AsyncImage(
                            model = listing.photoUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(14.dp)).background(Ids.colors.chip),
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(listing.title, color = Ids.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text(String.format(Locale.US, "%,.0f RWF", listing.price), color = Ids.colors.textSecondary, fontSize = 12.sp)
                    }
                }
                if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
internal fun HomeFeedRow(entry: HomeFeedEntry, onClick: () -> Unit) {
    val (icon, tint) = when (entry.kind) {
        "community" -> Icons.Outlined.Forum to Ids.colors.brand
        "jobs" -> Icons.Outlined.Work to Ids.colors.success
        "property" -> Icons.Outlined.Home to Ids.colors.textSecondary
        else -> Icons.Outlined.Storefront to Ids.colors.textSecondary
    }
    Row(
        modifier = Modifier.fillMaxWidth().pressScaleClickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (entry.kind == "marketplace" && !entry.photoUrl.isNullOrBlank()) {
            AsyncImage(
                model = entry.photoUrl, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(Ids.colors.chip),
            )
        } else {
            Box(
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp)) }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.title, color = Ids.colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(entry.subtitle, color = Ids.colors.textTertiary, fontSize = 12.sp, maxLines = 1)
        }
    }
}
