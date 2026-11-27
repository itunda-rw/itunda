package rw.itunda.feature.my.impl

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import rw.itunda.core.designsystem.components.ProtoTopBar
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.designsystem.theme.IdsIcons

/**
 * Prototype v1038 Security screen. Security-sensitive operations remain delegated to
 * the app layer: the screen owns presentation and local prototype preferences, while
 * app lock and hardware-backed device verification use their real existing managers.
 */
@Composable
fun SettingsSecurityScreen(
    language: String,
    pinCheckIndex: Int,
    onPinCheckIndexChanged: () -> Unit,
    quickSignInEnabled: Boolean,
    onQuickSignInChanged: (Boolean) -> Unit,
    biometricAvailable: Boolean,
    appLockEnabled: Boolean,
    onAppLockChanged: (Boolean) -> Unit,
    hasDeviceKey: Boolean,
    onRemoveDeviceKey: () -> Unit,
    onRegisterDeviceKey: (String, (Boolean, String?) -> Unit) -> Unit,
    onChangePin: () -> Unit,
    onOpenDevices: () -> Unit,
    onBack: () -> Unit,
) {
    val copy = securityCopy(language)
    val context = LocalContext.current
    var showPasskeysInfo by remember { mutableStateOf(false) }
    var showPasswordPrompt by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var keyRegisterError by remember { mutableStateOf<String?>(null) }
    var keyRegistering by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().background(Ids.colors.background),
    ) {
        ProtoTopBar(title = "", onBack = onBack)
        Text(
            text = copy.title,
            color = Ids.colors.textPrimary,
            fontSize = 26.sp,
            lineHeight = 34.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.8).sp,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 22.dp, bottom = 14.dp),
        )
        SecurityNavigationRow(
            title = copy.pinCheck,
            trailingValue = copy.pinCheckValues.getOrElse(pinCheckIndex) { copy.pinCheckValues.first() },
            onClick = onPinCheckIndexChanged,
        )
        SecurityNavigationRow(
            title = copy.passkeys,
            trailingValue = "1",
            onClick = { showPasskeysInfo = true },
        )
        SecurityToggleRow(
            title = copy.quickSignIn,
            checked = quickSignInEnabled,
            onCheckedChange = onQuickSignInChanged,
            infoAction = { Toast.makeText(context, copy.quickSignInInfo, Toast.LENGTH_SHORT).show() },
        )
        SecurityToggleRow(
            title = copy.unlockBiometrics,
            subtitle = copy.unlockBiometricsBody,
            checked = appLockEnabled,
            enabled = biometricAvailable,
            onCheckedChange = onAppLockChanged,
        )
        SecurityToggleRow(
            title = copy.verifyDevice,
            subtitle = copy.verifyDeviceBody,
            checked = hasDeviceKey,
            enabled = biometricAvailable,
            onCheckedChange = { enabled ->
                if (enabled) {
                    keyRegisterError = null
                    password = ""
                    showPasswordPrompt = true
                } else {
                    onRemoveDeviceKey()
                }
            },
        )
        SecurityNavigationRow(title = copy.changePin, onClick = onChangePin)
        SecurityNavigationRow(
            title = copy.devices,
            subtitle = copy.trustedDevice,
            onClick = onOpenDevices,
        )
    }

    if (showPasskeysInfo) {
        AlertDialog(
            onDismissRequest = { showPasskeysInfo = false },
            title = { Text(copy.passkeys) },
            text = { Text(copy.passkeysUnavailable) },
            confirmButton = {
                TextButton(onClick = { showPasskeysInfo = false }) { Text(copy.gotIt) }
            },
        )
    }
    if (showPasswordPrompt) {
        AlertDialog(
            onDismissRequest = {
                if (!keyRegistering) {
                    showPasswordPrompt = false
                    password = ""
                    keyRegisterError = null
                }
            },
            title = { Text(copy.confirmPassword) },
            text = {
                Column {
                    Text(copy.confirmPasswordBody, color = Ids.colors.textSecondary, fontSize = 13.sp)
                    Spacer(Modifier.size(8.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        label = { Text(copy.password) },
                    )
                    keyRegisterError?.let {
                        Spacer(Modifier.size(4.dp))
                        Text(it, color = Ids.colors.danger, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = password.isNotBlank() && !keyRegistering,
                    onClick = {
                        keyRegistering = true
                        onRegisterDeviceKey(password) { success, error ->
                            keyRegistering = false
                            if (success) {
                                showPasswordPrompt = false
                                password = ""
                                keyRegisterError = null
                            } else {
                                keyRegisterError = error ?: copy.verificationFailed
                            }
                        }
                    },
                ) {
                    Text(if (keyRegistering) copy.verifying else copy.confirm)
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !keyRegistering,
                    onClick = {
                        showPasswordPrompt = false
                        password = ""
                        keyRegisterError = null
                    },
                ) { Text(copy.cancel) }
            },
        )
    }
}

@Composable
private fun SecurityNavigationRow(
    title: String,
    subtitle: String? = null,
    trailingValue: String? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .pressScaleClickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Ids.colors.textPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.3).sp,
            )
            subtitle?.let {
                Text(it, color = Ids.colors.textSecondary, fontSize = 13.sp, lineHeight = 18.sp)
            }
        }
        trailingValue?.let {
            Text(it, color = Ids.colors.textSecondary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(8.dp))
        }
        Icon(
            imageVector = IdsIcons.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = Ids.colors.textTertiary,
        )
    }
}

@Composable
private fun SecurityToggleRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    infoAction: (() -> Unit)? = null,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    color = Ids.colors.textPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.3).sp,
                )
                if (infoAction != null) {
                    Box(
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(20.dp)
                            .clip(CircleShape)
                            .border(1.dp, Ids.colors.textTertiary, CircleShape)
                            .clickable(onClick = infoAction),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("?", color = Ids.colors.textTertiary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            subtitle?.let {
                Text(it, color = Ids.colors.textSecondary, fontSize = 13.sp, lineHeight = 18.sp)
            }
        }
        SecuritySwitch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SecuritySwitch(
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 23.dp else 3.dp,
        animationSpec = tween(durationMillis = 220),
    )
    Box(
        modifier = Modifier
            .width(48.dp)
            .height(28.dp)
            .clip(CircleShape)
            .background(if (checked) Ids.colors.brand else Ids.colors.divider)
            .alpha(if (enabled) 1f else 0.45f)
            .semantics {
                role = Role.Switch
                stateDescription = if (checked) "On" else "Off"
            }
            .clickable(enabled = enabled) { onCheckedChange(!checked) },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(22.dp)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

private data class SecurityCopy(
    val title: String,
    val pinCheck: String,
    val pinCheckValues: List<String>,
    val passkeys: String,
    val quickSignIn: String,
    val quickSignInInfo: String,
    val unlockBiometrics: String,
    val unlockBiometricsBody: String,
    val verifyDevice: String,
    val verifyDeviceBody: String,
    val changePin: String,
    val devices: String,
    val trustedDevice: String,
    val passkeysUnavailable: String,
    val confirmPassword: String,
    val confirmPasswordBody: String,
    val password: String,
    val verifying: String,
    val confirm: String,
    val cancel: String,
    val gotIt: String,
    val verificationFailed: String,
)

private fun securityCopy(language: String): SecurityCopy = when (language) {
    "rw" -> SecurityCopy(
        title = "Umutekano",
        pinCheck = "Kugenzura PIN",
        pinCheckValues = listOf("Igihe cyose ufunguye", "Rimwe ku munsi", "Buri minsi 7"),
        passkeys = "Passkeys",
        quickSignIn = "Kwinjira vuba",
        quickSignInInfo = "Injira udashyizemo PIN ku bikoresho wizeye",
        unlockBiometrics = "Fungura ukoresheje ibimenyetso by'umubiri",
        unlockBiometricsBody = "Saba Face/Fingerprint kugira ngo ufungure itunda",
        verifyDevice = "Emeza iki gikoresho ukoresheje ibimenyetso by'umubiri",
        verifyDeviceBody = "Simbuka kwandika ijambo ry'ibanga ku kwemeza",
        changePin = "Guhindura PIN",
        devices = "Ibikoresho",
        trustedDevice = "Byemewe — gishobora kohereza amafaranga",
        passkeysUnavailable = "Passkeys ntibiraboneka.",
        confirmPassword = "Emeza ijambo ry'ibanga",
        confirmPasswordBody = "Kubw'umutekano wawe, andika ijambo ry'ibanga kugira ngo wemeze iki gikoresho.",
        password = "Ijambo ry'ibanga",
        verifying = "Birimo kugenzurwa…",
        confirm = "Emeza",
        cancel = "Hagarika",
        gotIt = "Nibyo",
        verificationFailed = "Ntibyashobotse kwemeza iki gikoresho. Ongera ugerageze.",
    )
    "fr" -> SecurityCopy(
        title = "Sécurité",
        pinCheck = "Vérification du PIN",
        pinCheckValues = listOf("À chaque ouverture", "Une fois par jour", "Tous les 7 jours"),
        passkeys = "Passkeys",
        quickSignIn = "Connexion rapide",
        quickSignInInfo = "Connexion sans PIN sur les appareils de confiance",
        unlockBiometrics = "Déverrouiller avec la biométrie",
        unlockBiometricsBody = "Exiger Face/Empreinte pour ouvrir Itunda",
        verifyDevice = "Vérifier cet appareil avec la biométrie",
        verifyDeviceBody = "Évitez de retaper votre mot de passe pour la vérification renforcée",
        changePin = "Changer le PIN",
        devices = "Appareils",
        trustedDevice = "Approuvé — peut envoyer de l'argent",
        passkeysUnavailable = "Les clés d’accès ne sont pas encore disponibles.",
        confirmPassword = "Confirmer le mot de passe",
        confirmPasswordBody = "Pour votre sécurité, saisissez votre mot de passe pour vérifier cet appareil.",
        password = "Mot de passe",
        verifying = "Vérification…",
        confirm = "Confirmer",
        cancel = "Annuler",
        gotIt = "Compris",
        verificationFailed = "Impossible de vérifier cet appareil. Réessayez.",
    )
    else -> SecurityCopy(
        title = "Security",
        pinCheck = "PIN check",
        pinCheckValues = listOf("Every time you open", "Once a day", "Every 7 days"),
        passkeys = "Passkeys",
        quickSignIn = "Quick sign-in",
        quickSignInInfo = "Sign in without typing your PIN on trusted devices",
        unlockBiometrics = "Unlock with biometrics",
        unlockBiometricsBody = "Require Face/Fingerprint to open Itunda",
        verifyDevice = "Verify this device with biometrics",
        verifyDeviceBody = "Skip retyping your password for step-up verification",
        changePin = "Change PIN",
        devices = "Devices",
        trustedDevice = "Trusted — can send money",
        passkeysUnavailable = "Passkeys aren't available yet.",
        confirmPassword = "Confirm password",
        confirmPasswordBody = "For your security, enter your password to verify this device.",
        password = "Password",
        verifying = "Verifying…",
        confirm = "Confirm",
        cancel = "Cancel",
        gotIt = "Got it",
        verificationFailed = "Couldn't verify this device. Try again.",
    )
}
