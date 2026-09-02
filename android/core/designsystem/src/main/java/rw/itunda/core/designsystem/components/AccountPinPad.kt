package rw.itunda.core.designsystem.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import rw.itunda.core.designsystem.components.rememberPressScale
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.designsystem.theme.IdsIcons
import rw.itunda.core.designsystem.theme.IdsTypography

private const val ACCOUNT_PIN_LENGTH = 6

/**
 * Real Toss-sourced passwordless-login rollout (2026-08-23) -- see AuthDtos
 * .RegisterRequest/LoginRequest's own doc comment on the backend for the full
 * research (support.toss.im/toss.im/tosscert): a real 6-digit "비밀번호" (Toss's own
 * literal term), not a fabricated concept, replacing the free-form password field
 * LoginScreen.kt's PasswordStep used to render. Used by LoginScreen (login +
 * register credential entry) and PinUpgradeCard (existing pre-PIN-era account
 * upgrade). Deliberately its own component, not a share with PinScreen.kt's private
 * PinKeypad/PinDots -- that pair backs TokenStore's completely separate, local,
 * device-only app-unlock PIN, and conflating the two would risk a real client-side
 * mix-up between "unlock this phone" and "prove this is my itunda account."
 * Auto-submits at [ACCOUNT_PIN_LENGTH] digits, matching every real PIN entry
 * convention already established in this codebase (PinScreen.kt) and Toss's own --
 * no separate confirm button.
 */
@Composable
fun AccountPinPad(
    headline: String,
    subtitle: String? = null,
    errorMessage: String? = null,
    busy: Boolean = false,
    onComplete: (String) -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    val shakeOffset = remember { Animatable(0f) }
    // Real Toss-style "confirming" pulse (60fps.design's own real catalog of Toss's
    // named interactions, 2026-08-29) -- the dots had zero feedback the instant the
    // 6th digit landed; this pulses them while the real network round trip below is
    // in flight, resolving into either the existing shake (wrong PIN) or the caller
    // simply navigating away (correct PIN).
    val dotsScale = remember { Animatable(1f) }
    var completedTick by remember { mutableStateOf(0) }

    // Real gap this closes vs. a purely local PIN check (PinScreen.kt's own
    // PinEntryScreen): submission here is a network round trip, so the pad can't know
    // success/failure the instant the 6th digit lands -- it waits for the caller to
    // report back via `errorMessage` (clearing itself for a retry) or by simply
    // navigating away on success.
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            shakeOffset.animateTo(16f, animationSpec = tween(60))
            shakeOffset.animateTo(-16f, animationSpec = tween(60))
            shakeOffset.animateTo(0f, animationSpec = tween(60))
            pin = ""
        }
    }
    LaunchedEffect(completedTick) {
        if (completedTick > 0) {
            dotsScale.animateTo(1.15f, animationSpec = tween(120))
            dotsScale.animateTo(1f, animationSpec = tween(120))
        }
    }

    fun onDigit(digit: String) {
        if (busy || pin.length >= ACCOUNT_PIN_LENGTH) return
        pin += digit
        if (pin.length == ACCOUNT_PIN_LENGTH) {
            completedTick++
            onComplete(pin)
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(headline, style = IdsTypography.Title1, color = Ids.colors.textPrimary)
        if (subtitle != null || errorMessage != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                errorMessage ?: subtitle.orEmpty(),
                style = IdsTypography.Body1,
                color = if (errorMessage != null) Ids.colors.danger else Ids.colors.textSecondary,
            )
        }
        Spacer(modifier = Modifier.height(32.dp))
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Ids.colors.brand, strokeWidth = 3.dp)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.offset(x = shakeOffset.value.dp).scale(dotsScale.value)) {
                repeat(ACCOUNT_PIN_LENGTH) { index ->
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(if (index < pin.length) Ids.colors.brand else Ids.colors.divider),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
        AccountPinKeypad(enabled = !busy, onDigit = ::onDigit, onBackspace = { if (pin.isNotEmpty()) pin = pin.dropLast(1) })
    }
}

@Composable
private fun AccountPinKeypad(enabled: Boolean, onDigit: (String) -> Unit, onBackspace: () -> Unit) {
    val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"))
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        rows.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth().height(64.dp)) {
                row.forEach { digit -> AccountPinKey(modifier = Modifier.weight(1f), label = digit, enabled = enabled, onClick = { onDigit(digit) }) }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().height(64.dp)) {
            Box(modifier = Modifier.weight(1f).fillMaxHeight())
            AccountPinKey(modifier = Modifier.weight(1f), label = "0", enabled = enabled, onClick = { onDigit("0") })
            Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                val interactionSource = remember { MutableInteractionSource() }
                val pressScale = rememberPressScale(interactionSource)
                Icon(
                    imageVector = IdsIcons.Back,
                    contentDescription = "Delete last digit",
                    tint = Ids.colors.textSecondary,
                    modifier = Modifier
                        .scale(pressScale)
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(interactionSource = interactionSource, indication = LocalIndication.current, enabled = enabled, onClick = onBackspace)
                        .padding(12.dp),
                )
            }
        }
    }
}

@Composable
private fun AccountPinKey(modifier: Modifier = Modifier, label: String, enabled: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressScale = rememberPressScale(interactionSource)
    Box(
        modifier = modifier
            .fillMaxHeight()
            .scale(pressScale)
            .clickable(interactionSource = interactionSource, indication = LocalIndication.current, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = IdsTypography.Title1, color = Ids.colors.textPrimary)
    }
}
