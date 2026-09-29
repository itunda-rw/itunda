package rw.itunda.feature.my.impl

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import rw.itunda.core.designsystem.components.IdsTextField
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids

// Real email/phone verification (item 169/178) -- see AuthApi.requestEmailVerification/
// requestPhoneVerification's own doc comment. A real code is delivered via a real
// in-app Notification + push, no real SMS/email gateway exists.
//
// Moved here from :app's ItundaAppScreen.kt (2026-09-02, My Feature-module
// decomposition) -- confirmed used only by MyTab, which moved to this same module
// in the same slice.
@Composable
internal fun VerificationCard() {
    var email by rememberSaveable { mutableStateOf<String?>(null) }
    var emailVerified by rememberSaveable { mutableStateOf(true) }
    var phoneVerified by rememberSaveable { mutableStateOf(true) }
    var loaded by rememberSaveable { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    fun load() {
        coroutineScope.launch {
            try {
                val user = rw.itunda.core.network.NetworkClient.authApi.getProfile().user
                email = user.email
                emailVerified = user.emailVerified
                phoneVerified = user.phoneVerified
            } catch (_: Exception) {
                // Best-effort, matching this card's own bank-mfe precedent.
            } finally {
                loaded = true
            }
        }
    }
    LaunchedEffect(Unit) { load() }

    if (!loaded || (emailVerified && phoneVerified)) return

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Verify your account", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        if (!phoneVerified) VerificationRow(kind = "phone", hasEmail = true, onVerified = ::load)
        if (!emailVerified) VerificationRow(kind = "email", hasEmail = email != null, onVerified = ::load)
    }
}

@Composable
internal fun VerificationRow(kind: String, hasEmail: Boolean, onVerified: () -> Unit) {
    var sent by rememberSaveable { mutableStateOf(false) }
    var code by rememberSaveable { mutableStateOf("") }
    var busy by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var verified by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val checkScale = remember { Animatable(0f) }
    val shakeOffset = remember { Animatable(0f) }
    val inputAlpha = remember { Animatable(1f) }
    LaunchedEffect(busy) {
        if (busy) {
            while (true) {
                inputAlpha.animateTo(0.55f, animationSpec = tween(450, easing = LinearEasing))
                inputAlpha.animateTo(1f, animationSpec = tween(450, easing = LinearEasing))
            }
        } else {
            inputAlpha.animateTo(1f, animationSpec = tween(150))
        }
    }

    if (kind == "email" && !hasEmail) {
        Text("No email address on file to verify.", color = Ids.colors.textSecondary, fontSize = 12.sp)
        return
    }

    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        if (verified) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier.size(22.dp).scale(checkScale.value).clip(CircleShape).background(Ids.colors.success),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp)) }
                Text(if (kind == "email") "Email verified" else "Phone number verified", color = Ids.colors.success, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        } else if (!sent) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(if (kind == "email") "Email not verified" else "Phone number not verified", color = Ids.colors.textPrimary, fontSize = 13.sp)
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (busy) Ids.colors.textTertiary else Ids.colors.brand)
                        .pressScaleClickable(enabled = !busy) {
                            busy = true
                            error = null
                            coroutineScope.launch {
                                try {
                                    if (kind == "email") rw.itunda.core.network.NetworkClient.authApi.requestEmailVerification() else rw.itunda.core.network.NetworkClient.authApi.requestPhoneVerification()
                                    sent = true
                                } catch (e: retrofit2.HttpException) {
                                    val code = rw.itunda.core.network.apiErrorCode(e)
                                    if (code == "EMAIL_ALREADY_VERIFIED" || code == "PHONE_ALREADY_VERIFIED") {
                                        onVerified()
                                    } else {
                                        error = rw.itunda.core.network.superAppErrorMessage(e)
                                    }
                                } catch (e: java.io.IOException) {
                                    error = "Couldn't reach itunda. Check your connection and try again."
                                } finally {
                                    busy = false
                                }
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) { Text(if (busy) "…" else "Send code", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            }
        } else {
            fun confirm() {
                if (busy || code.isBlank()) return
                busy = true
                error = null
                coroutineScope.launch {
                    try {
                        if (kind == "email") {
                            rw.itunda.core.network.NetworkClient.authApi.confirmEmailVerification(rw.itunda.core.network.ConfirmEmailVerificationRequest(code.trim()))
                        } else {
                            rw.itunda.core.network.NetworkClient.authApi.confirmPhoneVerification(rw.itunda.core.network.ConfirmPhoneVerificationRequest(code.trim()))
                        }
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        verified = true
                        checkScale.animateTo(1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
                        kotlinx.coroutines.delay(500)
                        onVerified()
                    } catch (e: retrofit2.HttpException) {
                        error = rw.itunda.core.network.superAppErrorMessage(e)
                        shakeOffset.animateTo(16f, animationSpec = tween(60))
                        shakeOffset.animateTo(-16f, animationSpec = tween(60))
                        shakeOffset.animateTo(0f, animationSpec = tween(60))
                    } catch (e: java.io.IOException) {
                        error = "Couldn't reach itunda. Check your connection and try again."
                        shakeOffset.animateTo(16f, animationSpec = tween(60))
                        shakeOffset.animateTo(-16f, animationSpec = tween(60))
                        shakeOffset.animateTo(0f, animationSpec = tween(60))
                    } finally {
                        busy = false
                    }
                }
            }
            LaunchedEffect(code) {
                if (code.trim().length == 6 && code.trim().all { it.isDigit() } && !busy) confirm()
            }
            Row(
                modifier = Modifier.fillMaxWidth().offset(x = shakeOffset.value.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IdsTextField(value = code, onValueChange = { code = it }, label = "Enter code", keyboardType = KeyboardType.Number, autoFocus = true, modifier = Modifier.weight(1f).alpha(inputAlpha.value))
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (busy || code.isBlank()) Ids.colors.textTertiary else Ids.colors.brand)
                        .pressScaleClickable(enabled = !busy && code.isNotBlank()) { confirm() }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) { Text(if (busy) "…" else if (kind == "email") "Verify email" else "Verify phone number", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            }
        }
        error?.let { Text(it, color = Ids.colors.danger, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)) }
    }
}
