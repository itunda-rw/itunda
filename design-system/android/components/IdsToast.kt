package rw.itunda.core.designsystem.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Real Toss TDS Toast component (Simplicity research, docs/DESIGN_REFERENCES.md
// Section 14): brief, auto-dismissing feedback -- 3000ms default, 5000ms when it
// carries an action button -- rather than a persistent banner the user has to
// dismiss themselves. Ported from bank-mfe's own Toast.tsx / iOS's own Toast.swift
// (both already real, shipped this same initiative) -- Android had NO equivalent
// branded feedback: only a handful of raw system `Toast.makeText` call sites
// (JobsScreen/PropertyScreen favorite-toggle confirmations, ItundaAppScreen's
// savings deposit/claim flow), which render as the plain grey OS-styled toast, not
// itunda's own look, and every real create-flow success path elsewhere
// (GroupAccountScreen/IkiminaScreen's create(), WeeklySavings/Grow31Savings'
// submit()) showed nothing at all -- the exact same silent-success gap web and iOS
// both had before their own Toast port.
//
// Compose has no built-in system toast either, so this mirrors both siblings'
// approach: a small app-wide singleton (plain mutableStateOf, read by one
// Composable mounted once at the app root -- no DI/ViewModel needed for something
// this simple) driving a single overlay. Same "one toast at a time, a new one
// replaces whatever's showing" real Toss convention as both other platforms.
object IdsToast {
    var message by mutableStateOf<String?>(null)
        private set
    var actionLabel by mutableStateOf<String?>(null)
        private set
    private var onAction: (() -> Unit)? = null
    private var dismissJob: Job? = null

    fun show(scope: CoroutineScope, text: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
        dismissJob?.cancel()
        message = text
        this.actionLabel = actionLabel
        this.onAction = onAction
        val duration = if (actionLabel != null) 5000L else 3000L
        dismissJob = scope.launch {
            delay(duration)
            dismiss()
        }
    }

    fun runAction() {
        onAction?.invoke()
        dismiss()
    }

    fun dismiss() {
        dismissJob?.cancel()
        message = null
        actionLabel = null
        onAction = null
    }
}

/**
 * Mounts once at the app root (ItundaAppScreen.kt's own top-level Box), the same
 * way bank-mfe's `<OverlayProvider>` and iOS's `ToastOverlay` are each mounted once
 * at their own app root rather than per-screen.
 */
@Composable
fun IdsToastHost(modifier: Modifier = Modifier) {
    val message = IdsToast.message
    Box(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = message != null,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
            enter = fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 8 },
            exit = fadeOut(tween(220)) + slideOutVertically(tween(220)) { it / 8 },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .wrapContentHeight()
                    .shadow(elevation = 12.dp, shape = RoundedCornerShape(14.dp), ambientColor = Color.Black.copy(alpha = 0.24f))
                    .clip(RoundedCornerShape(14.dp))
                    // Deliberately a fixed literal, not Ids.colors.surface/textPrimary --
                    // those flip with the app's own light/dark theme, but this pill must
                    // stay ONE fixed dark surface regardless, for consistent max-contrast
                    // ephemeral visibility -- matches Toss's own real toast, and the exact
                    // same real bug both web and iOS's own ports of this component caught
                    // first (a theme-reactive token here inverts to white-on-white in dark
                    // mode).
                    .background(Color(0xFF191F28))
                    .padding(horizontal = 18.dp, vertical = 14.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            ) {
                Text(
                    message ?: "",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 19.6.sp,
                )
                IdsToast.actionLabel?.let { label ->
                    Text(
                        label,
                        // Fixed, theme-invariant accent -- matches iOS's own literal
                        // (0x5C55D8), itunda's real indigo at its dark-mode value, kept
                        // as a literal here for the same reason the pill's own background
                        // is fixed rather than semantic.
                        color = Color(0xFF5C55D8),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.pressScaleClickable { IdsToast.runAction() },
                    )
                }
            }
        }
    }
}
