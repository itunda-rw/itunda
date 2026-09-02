package rw.itunda.feature.home.impl

import androidx.compose.foundation.background
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.IdsButton
import rw.itunda.core.designsystem.components.IdsButtonSize
import rw.itunda.core.designsystem.components.IdsButtonVariant
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

// Real personalized recommendation card -- see HomeTab's own doc comment on
// heroDiscoverItem for the full account of what real Toss reference screenshot this
// was compared against and what it promotes. Real fetch is scoped locally, not
// through MainViewModel.profile, same "each screen fetches its own minimal real
// data" precedent this codebase uses throughout.
@Composable
internal fun PersonalRecommendationCard(item: rw.itunda.core.network.DiscoverItem, onOpenAction: () -> Unit) {
    var firstName by remember { mutableStateOf<String?>(null) }
    var dismissed by remember(item.id) { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        try {
            val profile = rw.itunda.core.network.NetworkClient.authApi.getProfile()
            if (profile.success) firstName = profile.user.firstName
        } catch (_: Exception) {
            // Best-effort -- the card still works with a generic CTA if this fails.
        }
    }
    val accentColor = try {
        Color(android.graphics.Color.parseColor(item.color))
    } catch (_: IllegalArgumentException) {
        AccentIndigo
    }
    if (dismissed) return
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Ids.layout.cardCornerRadius))
            .background(accentColor.copy(alpha = 0.10f)),
    ) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(accentColor.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.CardGiftcard, contentDescription = null, modifier = Modifier.size(24.dp), tint = accentColor)
                }
                if (item.isNew) {
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        stringResource(R.string.home_new_badge), color = accentColor, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(accentColor.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
            Text(item.title, color = Ids.colors.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 28.sp)
            Text(item.subtitle, color = Ids.colors.textSecondary, fontSize = 14.sp)
            IdsButton(
                firstName?.let { stringResource(R.string.home_recommendation_cta_named, it, item.description) } ?: item.description,
                onClick = onOpenAction,
                variant = IdsButtonVariant.Filled,
                size = IdsButtonSize.Large,
            )
        }
        IdsIconButton(
            IdsIcons.Close,
            contentDescription = stringResource(R.string.home_dismiss_recommendation),
            onClick = { dismissed = true },
            modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).size(32.dp),
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
