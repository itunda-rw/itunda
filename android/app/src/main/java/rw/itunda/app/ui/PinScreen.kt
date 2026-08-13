package rw.itunda.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import rw.itunda.core.designsystem.components.rememberPressScale
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.designsystem.theme.IdsTypography

private const val PIN_LENGTH = 6

/**
 * Real PIN app-unlock (2026-08-13) -- see TokenStore.setPin's own doc comment for why
 * this exists (biometric's real fallback, confirmed directly against the real Toss
 * app, not the "fake security" the original AppLockScreen.kt doc comment worried
 * about). Auto-submits at PIN_LENGTH digits -- no separate "confirm" button, matching
 * every real PIN entry screen's own convention (Toss, bank cards, phone lock
 * screens): the last digit tapped IS the confirm action.
 */
@Composable
fun PinEntryScreen(
    onUnlocked: () -> Unit,
    onUseBiometricInstead: (() -> Unit)? = null,
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val shakeOffset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    fun onDigit(digit: String) {
        if (pin.length >= PIN_LENGTH) return
        error = null
        pin += digit
        if (pin.length == PIN_LENGTH) {
            if (rw.itunda.core.network.NetworkClient.currentTokenStore().verifyPin(pin)) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onUnlocked()
            } else {
                error = "Incorrect PIN"
                scope.launch {
                    shakeOffset.animateTo(16f, animationSpec = androidx.compose.animation.core.tween(60))
                    shakeOffset.animateTo(-16f, animationSpec = androidx.compose.animation.core.tween(60))
                    shakeOffset.animateTo(0f, animationSpec = androidx.compose.animation.core.tween(60))
                }
                pin = ""
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Ids.colors.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(modifier = Modifier.height(Ids.layout.sectionGap))
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = Ids.layout.screenHorizontal),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("Enter your PIN", style = IdsTypography.Title1, color = Ids.colors.textPrimary)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    error ?: "Unlock itunda to continue",
                    style = IdsTypography.Body1,
                    color = if (error != null) Ids.colors.danger else Ids.colors.textSecondary,
                )
                Spacer(modifier = Modifier.height(32.dp))
                PinDots(filledCount = pin.length, offsetX = shakeOffset.value)
                if (onUseBiometricInstead != null) {
                    Spacer(modifier = Modifier.height(32.dp))
                    TextButton(onClick = onUseBiometricInstead) {
                        Text("Use biometric instead", style = IdsTypography.Body2.copy(fontWeight = FontWeight.SemiBold), color = Ids.colors.textBrand)
                    }
                }
            }
            PinKeypad(onDigit = ::onDigit, onBackspace = { if (pin.isNotEmpty()) { pin = pin.dropLast(1); error = null } })
        }
    }
}

/**
 * Real first-time PIN setup -- enter, then re-enter to confirm, matching every real
 * PIN-setup flow's own two-step convention (a typo on a screen with no visible
 * characters would otherwise lock a real user out of their own app-lock).
 */
@Composable
fun PinSetupScreen(onBack: () -> Unit, onPinSet: (String) -> Unit) {
    var firstEntry by remember { mutableStateOf<String?>(null) }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val shakeOffset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    fun onDigit(digit: String) {
        if (pin.length >= PIN_LENGTH) return
        error = null
        pin += digit
        if (pin.length == PIN_LENGTH) {
            val captured = firstEntry
            if (captured == null) {
                firstEntry = pin
                pin = ""
            } else if (captured == pin) {
                onPinSet(pin)
            } else {
                error = "PINs didn't match -- try again"
                scope.launch {
                    shakeOffset.animateTo(16f, animationSpec = androidx.compose.animation.core.tween(60))
                    shakeOffset.animateTo(-16f, animationSpec = androidx.compose.animation.core.tween(60))
                    shakeOffset.animateTo(0f, animationSpec = androidx.compose.animation.core.tween(60))
                }
                firstEntry = null
                pin = ""
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Ids.colors.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = Ids.layout.screenHorizontal, vertical = Ids.layout.screenVertical)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Ids.colors.textPrimary,
                    modifier = Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onBack).padding(8.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = Ids.layout.screenHorizontal),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    if (firstEntry == null) "Set a PIN" else "Re-enter your PIN",
                    style = IdsTypography.Title1,
                    color = Ids.colors.textPrimary,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    error ?: if (firstEntry == null) "Used instead of biometric to unlock itunda" else "Just to make sure you typed it right",
                    style = IdsTypography.Body1,
                    color = if (error != null) Ids.colors.danger else Ids.colors.textSecondary,
                )
                Spacer(modifier = Modifier.height(32.dp))
                PinDots(filledCount = pin.length, offsetX = shakeOffset.value)
            }
            PinKeypad(onDigit = ::onDigit, onBackspace = { if (pin.isNotEmpty()) { pin = pin.dropLast(1); error = null } })
        }
    }
}

@Composable
private fun PinDots(filledCount: Int, offsetX: Float) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.offset(x = offsetX.dp)) {
        repeat(PIN_LENGTH) { index ->
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(if (index < filledCount) Ids.colors.brand else Ids.colors.divider),
            )
        }
    }
}

/**
 * Same shape as TransferFlow.kt's real money-amount NumericKeypad (0-9 + backspace,
 * press-scale interaction) minus the "00" key -- a PIN is entered one digit at a
 * time, it never benefits from a double-zero shortcut the way a money amount does.
 */
@Composable
private fun PinKeypad(onDigit: (String) -> Unit, onBackspace: () -> Unit) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
    )
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        rows.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth().height(64.dp)) {
                row.forEach { digit -> PinKey(modifier = Modifier.weight(1f), label = digit, onClick = { onDigit(digit) }) }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().height(64.dp)) {
            Box(modifier = Modifier.weight(1f).fillMaxHeight())
            PinKey(modifier = Modifier.weight(1f), label = "0", onClick = { onDigit("0") })
            Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                val interactionSource = remember { MutableInteractionSource() }
                val pressScale = rememberPressScale(interactionSource)
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Delete last digit",
                    tint = Ids.colors.textSecondary,
                    modifier = Modifier
                        .scale(pressScale)
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(interactionSource = interactionSource, indication = androidx.compose.foundation.LocalIndication.current, onClick = onBackspace)
                        .padding(12.dp),
                )
            }
        }
    }
}

@Composable
private fun PinKey(modifier: Modifier = Modifier, label: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressScale = rememberPressScale(interactionSource)
    Box(
        modifier = modifier
            .fillMaxHeight()
            .scale(pressScale)
            .clickable(interactionSource = interactionSource, indication = androidx.compose.foundation.LocalIndication.current, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = IdsTypography.Title1, color = Ids.colors.textPrimary)
    }
}
