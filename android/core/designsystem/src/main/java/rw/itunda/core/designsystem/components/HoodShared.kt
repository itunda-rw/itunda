package rw.itunda.core.designsystem.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.launch
import retrofit2.HttpException
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.designsystem.theme.IdsIcons
import rw.itunda.core.network.CreateHoodReportRequest
import rw.itunda.core.network.HoodReviewDto
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.SetNeighborhoodRequest
import rw.itunda.core.network.superAppErrorMessage
import java.io.IOException

// Relocated 2026-07-22 from app/ui/SuperAppTabs.kt while extracting Marketplace into
// :features:marketplace:impl -- these are the true cross-feature UI atoms Marketplace,
// Community, Jobs, and Property all share (verified via a repo-wide call-site audit
// before moving anything). Living here in :core:designsystem, rather than in :app or
// duplicated per Feature module, is what lets :features:marketplace:impl depend on them
// without depending on :app -- the same Gradle-module-boundary mechanism the existing
// :features:payments:impl relies on ("this Feature module can't depend back on App").
// Every call site across the app (Community/Jobs/Property, still in :app for now) was
// repointed to this single shared copy rather than left duplicated.

@Composable
fun SkeletonBlock(height: Dp = 120.dp, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val offset by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(animation = tween(1000, easing = LinearEasing), repeatMode = RepeatMode.Restart),
        label = "skeletonOffset",
    )
    Card(
        shape = RoundedCornerShape(Ids.layout.cardCornerRadius),
        modifier = modifier.fillMaxWidth().height(height),
        colors = CardDefaults.cardColors(containerColor = Ids.colors.surfaceSoft),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.linearGradient(
                    colors = listOf(Ids.colors.surfaceSoft, Ids.colors.surface, Ids.colors.surfaceSoft),
                    start = Offset(offset * 600f, 0f),
                    end = Offset(offset * 600f + 400f, 400f),
                ),
            ),
        )
    }
}

@Composable
fun EmptyState(
    message: String,
    icon: ImageVector = Icons.Outlined.Inbox,
    // Real fix (2026-08-15): four call sites (Marketplace/Jobs/Property/Community's
    // NEARBY/NEIGHBORHOOD empty states) told the user to "try Browse to see X from
    // everywhere" -- but EmptyState was plain, non-interactive text with no way to
    // actually reach Browse. A user with location permission granted (the common
    // case) who lands on an empty nearby/neighborhood view had no path forward at
    // all. Optional so every other EmptyState call site (favorites, alerts, search
    // results) that has no real action stays exactly as plain as before.
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.size(56.dp).clip(CircleShape).background(Ids.colors.surfaceSoft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = Ids.colors.textSecondary)
        }
        Text(message, color = Ids.colors.textSecondary, fontSize = 14.sp, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            val interactionSource = remember { MutableInteractionSource() }
            val pressScale = rememberPressScale(interactionSource)
            Text(
                actionLabel,
                color = Ids.colors.brand,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier
                    .scale(pressScale)
                    .clickable(interactionSource = interactionSource, indication = LocalIndication.current, onClick = onAction),
            )
        }
    }
}

// Real fix, found live 2026-08-05 (same audit thread that found the design-system
// gaps this session already closed): EmptyState got a real icon-in-a-soft-circle
// treatment, but its own sibling ErrorCard -- shown right next to it in the exact
// same load-failure branches across the app -- stayed plain red text + a bare
// "Retry" text link. Mirrors EmptyState's layout exactly (centered icon circle,
// message below), using IdsColors.dangerTint for the circle since this is an error,
// not a neutral empty state, and a real IdsButton for Retry instead of a plain
// clickable Text.
@Composable
fun ErrorCard(message: String, onRetry: () -> Unit) {
    Card(
        shape = RoundedCornerShape(Ids.layout.cardCornerRadius),
        colors = CardDefaults.cardColors(containerColor = Ids.colors.surface),
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier.size(56.dp).clip(CircleShape).background(Ids.colors.dangerTint),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.ErrorOutline, contentDescription = null, modifier = Modifier.size(24.dp), tint = Ids.colors.danger)
            }
            Text(message, color = Ids.colors.textSecondary, fontSize = 14.sp, textAlign = TextAlign.Center)
            IdsButton(text = "Retry", onClick = onRetry, size = IdsButtonSize.Small)
        }
    }
}

@Composable
fun TabHeader(title: String) {
    Text(title, color = Ids.colors.textPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
}

@Composable
fun ListingActionButton(
    label: String,
    disabled: Boolean,
    filled: Boolean = false,
    // Optional itundaface glyph slot (2026-08-22) -- null for every pre-existing
    // caller (no behavior change), used by real callers whose label used to bake
    // a raw emoji into the string itself (lock/heart CTAs).
    icon: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressScale = rememberPressScale(interactionSource, enabled = !disabled)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .scale(pressScale)
            .clip(RoundedCornerShape(12.dp))
            .background(if (filled) Ids.colors.brand else Ids.colors.surfaceSoft)
            .clickable(interactionSource = interactionSource, indication = LocalIndication.current, enabled = !disabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        icon?.invoke()
        Text(label, color = if (filled) Color.White else Ids.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

// Real Karrot-Score-style numeric trust/reputation badge (2026-07-24) -- backend
// (User.trustScore, TrustScoreService) and the trustScores map on every Hood browse
// endpoint have existed since 2026-07-21, but no client rendered it anywhere -- closes
// docs/DESIGN_REFERENCES.md Section 4 recommendation #1. Deliberately a plain 0-1000
// number, never a manner-temperature/Celsius metaphor (see backend User.kt's own doc
// comment on why that's specifically wrong for a non-Korean market).
@Composable
fun TrustBadge(score: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Ids.colors.surfaceSoft)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text("Trust $score", color = Ids.colors.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

// Real shared status/urgency badge (2026-08-04) -- modeled on Coupang's own real
// two-tier badge precedent (a plain Rocket Delivery badge vs. a Rocket WOW badge for
// members-only extra benefits): a badge is tied to a real, named status/benefit, never
// a decorative label. Closes docs/DESIGN_REFERENCES.md Section 9 recommendation #4 --
// itunda's own real Time Deal discount and merchant verification status both rendered
// as plain colored text before this, with no consistent pill treatment anywhere.
@Composable
fun StatusBadge(text: String, filled: Boolean = true, tint: Color = Ids.colors.brand, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (filled) tint else tint.copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(text, color = if (filled) Color.White else tint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

// Real persistent edge-fade ("scroll fog") -- Seed Design's documented, always-rendered
// gradient hint that content continues below the fold (closes docs/DESIGN_REFERENCES.md
// Section 4 recommendation #5). Purely decorative, non-interactive: a sibling Box drawn
// on top of a scrolling list's bottom edge, not part of the list's own content/padding.
@Composable
fun ScrollFog(modifier: Modifier = Modifier, height: Dp = 24.dp) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(Brush.verticalGradient(colors = listOf(Color.Transparent, Ids.colors.background))),
    )
}

// Relocated 2026-07-23 from app/ui/ItundaAppScreen.kt while extracting Community into
// :features:community:impl -- not Hood-specific (IdentityScreen/SupportScreen/
// CertificateScreen and others in :app already use it too), but colocated here rather
// than a new file since it's the same "promoted so a Feature module can reach it"
// story as everything else above.
@Composable
fun BackTopBar(title: String, onBack: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressScale = rememberPressScale(interactionSource)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(Ids.layout.minTouchTarget)
                .scale(pressScale)
                .clip(CircleShape)
                .clickable(interactionSource = interactionSource, indication = LocalIndication.current, onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(IdsIcons.Back, contentDescription = "Back", modifier = Modifier.size(18.dp), tint = Ids.colors.textPrimary)
        }
        Text(title, color = Ids.colors.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

// Relocated 2026-07-23 from app/ui/SuperAppTabs.kt while extracting Shop/Commerce into
// :features:shop:impl -- shared with Eats (still in :app), same "promoted so a Feature
// module can reach it" story as everything above.
val StarGold = Color(0xFFF5A623)

@Composable
fun StarRatingRow(value: Int, onChange: (Int) -> Unit) {
    Row {
        for (n in 1..5) {
            val interactionSource = remember { MutableInteractionSource() }
            val pressScale = rememberPressScale(interactionSource)
            Icon(
                IdsIcons.Star,
                contentDescription = "$n star${if (n == 1) "" else "s"}",
                tint = if (n <= value) StarGold else Ids.colors.textTertiary,
                modifier = Modifier
                    .size(26.dp)
                    .scale(pressScale)
                    .clickable(interactionSource = interactionSource, indication = LocalIndication.current) { onChange(n) },
            )
        }
    }
}

// Real shared browse-header component (2026-07-21) -- extracted from Eats' OrderFoodContent
// (the only place this pattern previously existed) so Shop's merchant browse can reuse the
// identical search+chips interaction instead of a second bespoke implementation. Callers own
// their own debounce/state; this just renders the field + optional chip row.
@Composable
fun SearchAndCategoryChips(
    searchInput: String,
    onSearchChange: (String) -> Unit,
    placeholder: String,
    categories: List<String>,
    selectedCategory: String?,
    onSelectCategory: (String?) -> Unit,
) {
    Column {
        IdsTextField(
            value = searchInput,
            onValueChange = onSearchChange,
            label = placeholder,
            modifier = Modifier.fillMaxWidth(),
        )
        if (categories.isNotEmpty()) {
            Spacer(modifier = Modifier.height(Ids.layout.cardGap))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf<String?>(null).plus(categories).forEach { c ->
                    val selected = c == selectedCategory
                    val interactionSource = remember { MutableInteractionSource() }
                    val pressScale = rememberPressScale(interactionSource)
                    Text(
                        c ?: "All",
                        color = if (selected) Color.White else Ids.colors.textSecondary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .scale(pressScale)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (selected) Ids.colors.brand else Ids.colors.surfaceSoft)
                            .clickable(interactionSource = interactionSource, indication = LocalIndication.current) { onSelectCategory(c) }
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

// Relocated 2026-07-23 from app/ui/SuperAppTabs.kt while extracting Shop/Commerce into
// :features:shop:impl -- shared with Eats (still in :app), same story as everything else.
@Composable
fun QtyButton(label: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressScale = rememberPressScale(interactionSource)
    Box(
        modifier = Modifier
            .size(30.dp)
            .scale(pressScale)
            .clip(CircleShape)
            .background(Ids.colors.surfaceSoft)
            .clickable(interactionSource = interactionSource, indication = LocalIndication.current, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold) }
}
