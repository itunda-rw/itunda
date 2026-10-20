package rw.itunda.feature.home.impl

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import rw.itunda.core.designsystem.components.EmptyState
import rw.itunda.core.designsystem.theme.Ids

internal fun LazyListScope.homeSearchResults(
    products: List<rw.itunda.core.network.ProductSearchResultDto>?,
    marketplace: List<HomeFeedEntry>?,
    community: List<HomeFeedEntry>?,
    jobs: List<HomeFeedEntry>?,
    property: List<HomeFeedEntry>?,
    onOpenShop: () -> Unit,
    onOpenMarketplace: () -> Unit,
    onOpenCommunity: () -> Unit,
    onOpenJobs: () -> Unit,
    onOpenProperty: () -> Unit,
) {
    val stillLoading = products == null || marketplace == null || community == null || jobs == null || property == null
    val total = (products?.size ?: 0) + (marketplace?.size ?: 0) + (community?.size ?: 0) +
        (jobs?.size ?: 0) + (property?.size ?: 0)
    if (stillLoading && total == 0) {
        item {
            Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Ids.colors.brand)
            }
        }
    } else if (!stillLoading && total == 0) {
        item { EmptyState("No results found. Try another search.") }
    } else {
        products?.takeIf { it.isNotEmpty() }?.let { results ->
            item { HomeSearchSectionHeader("Products") }
            items(results, key = { "product_${it.id}" }) { HomeSearchResultRow(it, onClick = onOpenShop) }
        }
        marketplace?.takeIf { it.isNotEmpty() }?.let { results ->
            item { HomeSearchSectionHeader("Marketplace") }
            items(results, key = { "marketplace_${it.id}" }) { HomeFeedRow(it, onClick = onOpenMarketplace) }
        }
        community?.takeIf { it.isNotEmpty() }?.let { results ->
            item { HomeSearchSectionHeader("Community") }
            items(results, key = { "community_${it.id}" }) { HomeFeedRow(it, onClick = onOpenCommunity) }
        }
        jobs?.takeIf { it.isNotEmpty() }?.let { results ->
            item { HomeSearchSectionHeader("Jobs") }
            items(results, key = { "jobs_${it.id}" }) { HomeFeedRow(it, onClick = onOpenJobs) }
        }
        property?.takeIf { it.isNotEmpty() }?.let { results ->
            item { HomeSearchSectionHeader("Property") }
            items(results, key = { "property_${it.id}" }) { HomeFeedRow(it, onClick = onOpenProperty) }
        }
    }
}
