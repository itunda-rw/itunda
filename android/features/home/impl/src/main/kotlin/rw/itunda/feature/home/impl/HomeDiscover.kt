package rw.itunda.feature.home.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.IdsCard
import rw.itunda.core.designsystem.components.IdsIconButton
import rw.itunda.core.designsystem.theme.AccentIndigo
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.designsystem.theme.IdsIcons

// Real cross-vertical feed entry (2026-08-14) -- see HomeTab's own Naver-redesign
// doc comment. A plain discriminated shape, not a sealed hierarchy: the 4 real
// source DTOs (ListingDto/CommunityPostDto/JobPostDto/PropertyListingDto) share no
// common interface, and this is only ever used to sort+render, not to dispatch
// type-specific business logic.
//
// Moved here from :app's ItundaAppScreen.kt (2026-09-02, Home Feature-module
// decomposition) -- confirmed used only by HomeTab, which moved to this same module
// in the same slice.
internal data class HomeFeedEntry(
    val id: String,
    val kind: String,
    val title: String,
    val subtitle: String,
    val createdAt: String,
    val photoUrl: String? = null,
)

// Slim recommendation row (prototype v941, 2026-11-07). The old large illustrated card
// (2026-08-11, modelled on a Toss Bank reference) is replaced by the prototype's one
// slim row under the balance summary: small brand icon, ONE line of text, close
// button, tap anywhere to open. The backend still ranks server-side (see HomeTab's
// heroDiscoverItem); only the presentation got quieter. The title is the one line;
// the long subtitle/description/first-name CTA no longer render here.
@Composable
internal fun PersonalRecommendationCard(item: rw.itunda.core.network.DiscoverItem, onOpenAction: () -> Unit) {
    var dismissed by remember(item.id) { mutableStateOf(false) }
    val accentColor = try {
        Color(android.graphics.Color.parseColor(item.color))
    } catch (_: IllegalArgumentException) {
        AccentIndigo
    }
    if (dismissed) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Ids.colors.surfaceSoft)
            .clickable(onClick = onOpenAction)
            .padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(accentColor.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.CardGiftcard, contentDescription = null, modifier = Modifier.size(18.dp), tint = accentColor)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            item.title,
            color = Ids.colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IdsIconButton(
            IdsIcons.Close,
            contentDescription = stringResource(R.string.home_dismiss_recommendation),
            onClick = { dismissed = true },
            modifier = Modifier.size(40.dp),
        )
    }
}

@Composable
internal fun DiscoverSection(items: List<rw.itunda.core.network.DiscoverItem>) {
    Column {
        Text(stringResource(R.string.home_discover), color = Ids.colors.textPrimary, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
        items.forEach { discoverItem ->
            val accentColor = try {
                Color(android.graphics.Color.parseColor(discoverItem.color))
            } catch (_: IllegalArgumentException) {
                AccentIndigo
            }
            IdsCard(
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(accentColor))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(discoverItem.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Ids.colors.textPrimary)
                            if (discoverItem.isNew) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.home_new_badge), color = accentColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Text(discoverItem.subtitle, fontSize = 14.sp, color = Ids.colors.textSecondary)
                    }
                    discoverItem.badge?.let { badge ->
                        Text(badge, color = accentColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
