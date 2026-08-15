package rw.itunda.rider.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import retrofit2.HttpException
import rw.itunda.core.designsystem.components.IdsButton
import rw.itunda.core.designsystem.components.IdsTextField
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.designsystem.theme.IdsTypography
import rw.itunda.rider.network.DevicePlatform
import rw.itunda.rider.network.LoginRequest
import rw.itunda.rider.network.NetworkClient
import rw.itunda.rider.network.RegisterDeviceTokenRequest
import java.io.IOException

// Real French/Kinyarwanda/English localization (2026-08-15) -- this standalone app had
// zero localization of its own before this, matching the identical gap iOS's own
// RiderApp/Sources/LoginScreen.swift had until the same day (see that file's own doc
// comment). Self-contained enum + dictionary, same reasoning as :app's own
// AppLocalePreference/values-rw approach but scoped to this one screen, since
// riderapp has no broader locale infrastructure to plug into yet.
enum class RiderAppLocale { EN, RW, FR }

private val riderLoginStrings: Map<RiderAppLocale, Map<String, String>> = mapOf(
    RiderAppLocale.EN to mapOf(
        "title" to "Itunda Rider",
        "subtitle" to "Log in with your existing itunda account to start delivering.",
        "phoneNumber" to "Phone number",
        "password" to "Password",
        "loggingIn" to "Logging in…",
        "logIn" to "Log in",
        "noAccount" to "Don't have an itunda account yet? Register in the main itunda app first, then come back here to log in as a rider.",
        "emptyFields" to "Enter your phone number and password.",
        "incorrectCredentials" to "Incorrect phone number or password.",
        "connectionError" to "Couldn't reach itunda. Check your connection and try again.",
    ),
    RiderAppLocale.RW to mapOf(
        "title" to "Itunda Rider",
        "subtitle" to "Injira ukoresheje konti yawe ya itunda kugira ngo utangire gutwara ibicuruzwa.",
        "phoneNumber" to "Numero ya telefoni",
        "password" to "Ijambo ry'ibanga",
        "loggingIn" to "Kwinjira…",
        "logIn" to "Injira",
        "noAccount" to "Ntufite konti ya itunda? Banza wifungurire konti muri porogaramu nkuru ya itunda, hanyuma ugaruke hano winjire nka rider.",
        "emptyFields" to "Andika numero yawe ya telefoni n'ijambo ry'ibanga.",
        "incorrectCredentials" to "Numero ya telefoni cyangwa ijambo ry'ibanga sibyo.",
        "connectionError" to "Ntibishoboka kugera kuri itunda. Reba interineti yawe hanyuma ugerageze nanone.",
    ),
    RiderAppLocale.FR to mapOf(
        "title" to "Itunda Rider",
        "subtitle" to "Connectez-vous avec votre compte itunda existant pour commencer à livrer.",
        "phoneNumber" to "Numéro de téléphone",
        "password" to "Mot de passe",
        "loggingIn" to "Connexion en cours…",
        "logIn" to "Se connecter",
        "noAccount" to "Vous n'avez pas encore de compte itunda ? Inscrivez-vous d'abord dans l'application principale itunda, puis revenez ici pour vous connecter en tant que livreur.",
        "emptyFields" to "Entrez votre numéro de téléphone et votre mot de passe.",
        "incorrectCredentials" to "Numéro de téléphone ou mot de passe incorrect.",
        "connectionError" to "Impossible de joindre itunda. Vérifiez votre connexion et réessayez.",
    ),
)

private const val RIDER_LOCALE_KEY = "itunda_rider_locale"
private val riderSupportedLocales = listOf(RiderAppLocale.EN, RiderAppLocale.RW, RiderAppLocale.FR)

private fun loadRiderStoredLocale(context: android.content.Context): RiderAppLocale {
    val prefs = context.getSharedPreferences("itunda_rider_locale_prefs", android.content.Context.MODE_PRIVATE)
    val stored = prefs.getString(RIDER_LOCALE_KEY, null)
    if (stored != null) {
        return try { RiderAppLocale.valueOf(stored) } catch (_: IllegalArgumentException) { RiderAppLocale.EN }
    }
    return when (java.util.Locale.getDefault().language) {
        "rw" -> RiderAppLocale.RW
        "fr" -> RiderAppLocale.FR
        else -> RiderAppLocale.EN
    }
}

@Composable
fun LoginScreen(onLoggedIn: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var locale by remember { mutableStateOf(loadRiderStoredLocale(context)) }
    fun t(key: String) = riderLoginStrings[locale]?.get(key) ?: riderLoginStrings[RiderAppLocale.EN]?.get(key) ?: key

    var phoneNumber by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(t("title"), style = IdsTypography.Title1, color = Ids.colors.textPrimary)
            Text(
                locale.name,
                style = IdsTypography.Body2,
                color = Ids.colors.textSecondary,
                modifier = Modifier.clickable {
                    val currentIndex = riderSupportedLocales.indexOf(locale).coerceAtLeast(0)
                    locale = riderSupportedLocales[(currentIndex + 1) % riderSupportedLocales.size]
                    context.getSharedPreferences("itunda_rider_locale_prefs", android.content.Context.MODE_PRIVATE)
                        .edit().putString(RIDER_LOCALE_KEY, locale.name).apply()
                },
            )
        }
        Text(t("subtitle"), style = IdsTypography.Body1, color = Ids.colors.textSecondary)

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(24.dp))

        IdsTextField(
            value = phoneNumber,
            onValueChange = { phoneNumber = it },
            label = t("phoneNumber"),
            keyboardType = KeyboardType.Phone,
            modifier = Modifier.fillMaxWidth(),
        )
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(12.dp))
        IdsTextField(
            value = password,
            onValueChange = { password = it },
            label = t("password"),
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )

        error?.let {
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(12.dp))
            Text(it, color = Ids.colors.danger, style = IdsTypography.Body2)
        }

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(20.dp))
        IdsButton(
            text = if (busy) t("loggingIn") else t("logIn"),
            enabled = !busy,
            onClick = {
                if (phoneNumber.isBlank() || password.isBlank()) {
                    error = t("emptyFields")
                    return@IdsButton
                }
                busy = true
                error = null
                scope.launch {
                    try {
                        val res = NetworkClient.authApi.login(LoginRequest(phoneNumber.trim(), password))
                        NetworkClient.currentTokenStore().saveSession(res.user.id, res.accessToken, res.refreshToken)
                        // Real push device-token registration (item 130) -- best-effort,
                        // fire-and-forget: a registration failure must never block an
                        // otherwise successful login. See ApiService.kt's own doc comment.
                        scope.launch {
                            try {
                                NetworkClient.apiService.registerDeviceToken(
                                    RegisterDeviceTokenRequest(DevicePlatform.ANDROID, NetworkClient.currentDeviceStore().getOrCreateDeviceId()),
                                )
                            } catch (e: Exception) {
                                // Best-effort, see doc comment above.
                            }
                        }
                        onLoggedIn()
                    } catch (e: HttpException) {
                        // Real Toss-style error handling (2026-08-12) -- 401 stays a
                        // real, friendly, specific message, but every OTHER real HTTP
                        // error (429 rate limit, 403 suspended, 5xx, etc.) was
                        // discarding any real backend message for a vague "Couldn't
                        // reach itunda" -- wrong on two counts: not a reachability
                        // problem if a real response came back, and it silently
                        // dropped whatever specific reason the backend actually gave.
                        error = if (e.code() == 401) {
                            t("incorrectCredentials")
                        } else {
                            rw.itunda.rider.network.parseApiError(e).message
                                ?: "itunda is having a brief hiccup on our end -- not something you did. Try again in a moment."
                        }
                    } catch (e: IOException) {
                        error = t("connectionError")
                    } finally {
                        busy = false
                    }
                }
            },
        )

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))
        Text(
            t("noAccount"),
            style = IdsTypography.Typography7,
            color = Ids.colors.textSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
