package rw.itunda.core.designsystem.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import rw.itunda.core.designsystem.theme.Ids

/**
 * Real, sourced Toss Bank "explain the product before you commit" pattern (2026-08-24,
 * direct user-supplied reference: 8 real Toss Bank Debit Card screenshots -- hero
 * headline over a card illustration, a real feature-benefit list, "clear UX, clear
 * graphics, smooth animation" -- plus a direct follow-up: "improve a kind of products
 * like this that needs it," pointing at YouthAccountScreen's own real pre-open state).
 *
 * Promoted to core/designsystem (mirrors IdsCelebrationScreen's own "promote once a
 * second real call site needs it" precedent, not built speculatively): both
 * YouthAccountScreen and CardScreen had the identical shape of gap -- a bare
 * informational paragraph plus one button, no illustration, no motion, before a real
 * money-adjacent commitment. Every real screen using this stays honest about its own
 * actual capabilities -- this component only lays out whatever [features] the caller
 * supplies; it never invents copy itself.
 */
@Composable
fun ProductExplainerScreen(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    features: List<Pair<@Composable () -> Unit, String>>,
    ctaLabel: String,
    ctaEnabled: Boolean,
    onCta: () -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        delay(30)
        visible = true
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        AnimatedVisibility(visible = visible, enter = fadeIn(tween(280)) + slideInVertically(tween(280)) { it / 4 }) {
            Box(
                modifier = Modifier.size(width = 176.dp, height = 110.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Ids.colors.brand)
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                icon()
            }
        }
        Spacer(Modifier.height(20.dp))

        AnimatedVisibility(visible = visible, enter = fadeIn(tween(280, delayMillis = 60)) + slideInVertically(tween(280, delayMillis = 60)) { it / 4 }) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Ids.colors.textPrimary, textAlign = TextAlign.Center)
                Spacer(Modifier.height(6.dp))
                Text(subtitle, fontSize = 13.sp, color = Ids.colors.textSecondary, textAlign = TextAlign.Center)
            }
        }
        Spacer(Modifier.height(24.dp))

        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            features.forEachIndexed { i, (featureIcon, label) ->
                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(tween(220, delayMillis = 120 + i * 55)) + slideInVertically(tween(220, delayMillis = 120 + i * 55)) { it / 3 },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Box(
                            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Ids.colors.brand.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center,
                        ) { featureIcon() }
                        Spacer(Modifier.width(12.dp))
                        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ids.colors.textPrimary)
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))

        AnimatedVisibility(visible = visible, enter = fadeIn(tween(220, delayMillis = 120 + features.size * 55))) {
            Box(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .background(Ids.colors.brand).pressScaleClickable(enabled = ctaEnabled, onClick = onCta)
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(ctaLabel, color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}
