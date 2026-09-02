package rw.itunda.core.designsystem.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.designsystem.theme.IdsColors
import rw.itunda.core.designsystem.theme.IdsTypography

/**
 * Real shared money-success/celebration screen (promoted 2026-08-12 out of
 * `ItundaAppScreen.kt`'s own private `MoneySuccessScreen`/`ConfettiBurst`, built
 * 2026-08-11 for the Send/Savings-deposit/claim-interest flows) -- the same "promote
 * once a second real call site needs it" pattern this module's own `HoodShared.kt`/
 * `IdsInteractions.kt` already established, not a fresh invention. See this session's
 * real Toss motion research (toss.tech/article/interaction, toss.im/tossfeed/article/
 * why-motion-in-finance): "적립금 증가, 월급날 같은 긍정적인 순간에 색종이 효과를 사용해
 * 행복한 순간을 극적으로 만든다" (confetti for positive moments like a credit-score
 * increase or payday, to make the happy moment dramatic) -- `celebratory` opts a
 * genuinely-earned/milestone moment into the confetti burst; a routine transfer or
 * deposit stays at the plain checkmark.
 *
 * Found live during this pass: itunda's own 31-day (Grow31) and 26-week (Weekly)
 * savings-plan maturity withdrawals -- a real completed-challenge milestone, arguably
 * a closer match to Toss's own "payday" example than the one moment (claimed
 * interest) this already covered -- had ZERO success acknowledgment at all before
 * this, not even the plain (non-celebratory) checkmark: the withdraw() success path
 * just silently re-rendered the same screen with an updated "Withdrawn" status label.
 */
@Composable
fun IdsCelebrationScreen(
    headline: String,
    message: String,
    onDone: () -> Unit,
    celebratory: Boolean = false,
    // Real Toss "Sent" success-screen reference (2026-08-23, user-supplied
    // screenshot): a money-transfer-specific "To [name]" line and a Share action,
    // neither of which apply to this screen's other real callers (a savings deposit
    // or claimed interest has no "recipient" and nothing worth sharing). Both default
    // to null so every existing call site (GroupAccountScreen/IkiminaScreen's
    // deposit/claim, Grow31/WeeklySavingsScreen's maturity withdrawal) is completely
    // unaffected -- additive, not a signature change those callers need to touch.
    recipientLabel: String? = null,
    onShare: (() -> Unit)? = null,
    // Real Toss "Fraud Suspicion Siren" post-payment notice (2026-09-02, Toss
    // security research thread) -- purely informational, the transfer this is
    // attached to has already completed. Empty by default so every other real
    // caller (Group/Ikimina/Grow31/WeeklySavings deposit-claim) is unaffected;
    // only sendDirect's own transfer-success screen ever populates this.
    fraudWarnings: List<String> = emptyList(),
) {
    BackHandler(onBack = onDone)
    val haptics = LocalHapticFeedback.current
    val checkScale = remember { Animatable(0f) }
    val confettiProgress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        // HapticFeedbackType.Confirm (the semantically-correct one for this moment)
        // isn't available in this project's pinned Compose UI version -- LongPress is
        // the real one every version since Compose UI's initial haptics API supports,
        // and reads as a single confident buzz here same as Confirm would.
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        checkScale.animateTo(1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
        if (celebratory) {
            confettiProgress.animateTo(1f, animationSpec = tween(durationMillis = 1600, easing = LinearEasing))
        }
        // Real Toss-style auto-advance -- the screen is a real acknowledgment moment,
        // not a dialog someone has to dismiss to get their money moving; "Done" below
        // still works immediately for anyone who doesn't want to wait it out.
        delay(if (celebratory) 800 else 2200)
        onDone()
    }
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Ids.colors.background)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .scale(checkScale.value)
                    .clip(CircleShape)
                    .background(Ids.colors.success),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = IdsColors.White, modifier = Modifier.size(48.dp))
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(headline, style = IdsTypography.LargeAmount, color = Ids.colors.textPrimary)
            if (message.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(message, fontSize = 15.sp, color = Ids.colors.textSecondary, textAlign = TextAlign.Center)
            }
            if (recipientLabel != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text("To $recipientLabel", fontSize = 18.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = Ids.colors.textPrimary)
            }
            if (fraudWarnings.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Ids.colors.dangerTint)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    fraudWarnings.forEachIndexed { index, warning ->
                        if (index > 0) Spacer(modifier = Modifier.height(4.dp))
                        Text(warning, color = Ids.colors.danger, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
            Spacer(modifier = Modifier.height(40.dp))
            if (onShare != null) {
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    IdsButton(text = "Share", onClick = onShare, variant = IdsButtonVariant.Tinted, modifier = Modifier.weight(1f))
                    IdsButton(text = "Done", onClick = onDone, modifier = Modifier.weight(1f))
                }
            } else {
                IdsButton(text = "Done", onClick = onDone, modifier = Modifier.fillMaxWidth())
            }
        }
        if (celebratory) {
            ConfettiBurst(progress = confettiProgress.value)
        }
    }
}

// Real, pure-Compose confetti burst -- no Lottie/asset pipeline, matching this
// session's Toss motion research on "resource efficiency" (2D+Lottie in Toss's own
// app; a Canvas particle burst is itunda's equivalent at this app's actual scale for
// one screen, not disproportionate). A single shared `progress` (0f-1f) drives every
// particle's fall/drift/fade -- one Animatable, not one per particle, same efficiency
// principle applied to the implementation itself.
@Composable
private fun ConfettiBurst(progress: Float) {
    val colors = listOf(Ids.colors.brand, Ids.colors.success, ConfettiAccentOrange, ConfettiAccentPurple, ConfettiAccentTeal)
    val particles = remember {
        List(28) {
            ConfettiParticle(
                startX = kotlin.random.Random.nextFloat(),
                fallSpeed = 0.7f + kotlin.random.Random.nextFloat() * 0.6f,
                drift = (kotlin.random.Random.nextFloat() - 0.5f) * 0.3f,
                colorIndex = kotlin.random.Random.nextInt(colors.size),
                rotationSpeed = (kotlin.random.Random.nextFloat() - 0.5f) * 720f,
                sizeDp = 6f + kotlin.random.Random.nextFloat() * 6f,
                delay = kotlin.random.Random.nextFloat() * 0.25f,
            )
        }
    }
    Canvas(modifier = Modifier.fillMaxSize()) {
        particles.forEach { p ->
            val localProgress = ((progress - p.delay) / (1f - p.delay)).coerceIn(0f, 1f)
            if (localProgress <= 0f) return@forEach
            val fallen = localProgress * p.fallSpeed
            val x = (p.startX + p.drift * localProgress) * size.width
            val y = fallen * size.height * 1.1f
            val alpha = (1f - localProgress).coerceIn(0f, 1f)
            rotate(degrees = p.rotationSpeed * localProgress, pivot = Offset(x, y)) {
                drawRect(
                    color = colors[p.colorIndex].copy(alpha = alpha),
                    topLeft = Offset(x - p.sizeDp / 2, y - p.sizeDp / 2),
                    size = Size(p.sizeDp, p.sizeDp),
                )
            }
        }
    }
}

private data class ConfettiParticle(
    val startX: Float,
    val fallSpeed: Float,
    val drift: Float,
    val colorIndex: Int,
    val rotationSpeed: Float,
    val sizeDp: Float,
    val delay: Float,
)

// Same literal values as ItundaAppScreen.kt's own (now-superseded) internal
// AccentOrange/AccentPurple/AccentTeal -- kept local to this component rather than
// wired cross-module (:app can't be a dependency of :core:designsystem) since these
// three are only ever used for confetti-particle variety, not a real themeable token.
private val ConfettiAccentTeal = Color(0xFF14AE85)
private val ConfettiAccentPurple = Color(0xFF7C5CFC)
private val ConfettiAccentOrange = Color(0xFFF2A93B)
