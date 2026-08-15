package rw.itunda.agent.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import rw.itunda.agent.network.DevicePlatform
import rw.itunda.agent.network.LoginRequest
import rw.itunda.agent.network.NetworkClient
import rw.itunda.agent.network.RegisterDeviceTokenRequest
import rw.itunda.core.designsystem.components.IdsButton
import rw.itunda.core.designsystem.components.IdsTextField
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.designsystem.theme.IdsTypography

// Real French/Kinyarwanda/English localization (2026-08-15) -- this standalone app had
// zero localization of its own before this, matching riderapp's/merchantapp's
// identical fix (see riderapp's LoginScreen.kt for the full reasoning) and iOS
// AgentApp's own same-day fix.
enum class AgentAppLocale { EN, RW, FR }

private val agentLoginStrings: Map<AgentAppLocale, Map<String, String>> = mapOf(
    AgentAppLocale.EN to mapOf(
        "agent" to "Agent",
        "subtitle" to "Use the itunda account assigned to this store.",
        "phoneNumber" to "Phone number",
        "password" to "Password",
        "signingIn" to "Signing in…",
        "signIn" to "Sign in",
        "emptyFields" to "Enter your phone number and password.",
        "notAgentOrUnreachable" to "This account is not an active agent operator, or the service could not be reached.",
        "connectionError" to "Couldn't reach itunda. Check your connection and try again.",
    ),
    AgentAppLocale.RW to mapOf(
        "agent" to "Umukozi",
        "subtitle" to "Koresha konti ya itunda yahawe iri duka.",
        "phoneNumber" to "Numero ya telefoni",
        "password" to "Ijambo ry'ibanga",
        "signingIn" to "Kwinjira…",
        "signIn" to "Injira",
        "emptyFields" to "Andika numero yawe ya telefoni n'ijambo ry'ibanga.",
        "notAgentOrUnreachable" to "Iyi konti si iy'umukozi ukora, cyangwa serivisi ntiyagezweho.",
        "connectionError" to "Ntibishoboka kugera kuri itunda. Reba interineti yawe hanyuma ugerageze nanone.",
    ),
    AgentAppLocale.FR to mapOf(
        "agent" to "Agent",
        "subtitle" to "Utilisez le compte itunda attribué à cette boutique.",
        "phoneNumber" to "Numéro de téléphone",
        "password" to "Mot de passe",
        "signingIn" to "Connexion en cours…",
        "signIn" to "Se connecter",
        "emptyFields" to "Entrez votre numéro de téléphone et votre mot de passe.",
        "notAgentOrUnreachable" to "Ce compte n'est pas un agent opérateur actif, ou le service n'a pas pu être joint.",
        "connectionError" to "Impossible de joindre itunda. Vérifiez votre connexion et réessayez.",
    ),
)

private const val AGENT_LOCALE_KEY = "itunda_agent_locale"
private val agentSupportedLocales = listOf(AgentAppLocale.EN, AgentAppLocale.RW, AgentAppLocale.FR)

private fun loadAgentStoredLocale(context: android.content.Context): AgentAppLocale {
    val prefs = context.getSharedPreferences("itunda_agent_locale_prefs", android.content.Context.MODE_PRIVATE)
    val stored = prefs.getString(AGENT_LOCALE_KEY, null)
    if (stored != null) {
        return try { AgentAppLocale.valueOf(stored) } catch (_: IllegalArgumentException) { AgentAppLocale.EN }
    }
    return when (java.util.Locale.getDefault().language) {
        "rw" -> AgentAppLocale.RW
        "fr" -> AgentAppLocale.FR
        else -> AgentAppLocale.EN
    }
}

@Composable
fun LoginScreen(onLoggedIn: () -> Unit) {
    val context = LocalContext.current
    var locale by remember { mutableStateOf(loadAgentStoredLocale(context)) }
    fun t(key: String) = agentLoginStrings[locale]?.get(key) ?: agentLoginStrings[AgentAppLocale.EN]?.get(key) ?: key

    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("itunda", style = IdsTypography.Title1, color = Ids.colors.textPrimary)
            Text(
                locale.name,
                style = IdsTypography.Body2,
                color = Ids.colors.textSecondary,
                modifier = Modifier.clickable {
                    val currentIndex = agentSupportedLocales.indexOf(locale).coerceAtLeast(0)
                    locale = agentSupportedLocales[(currentIndex + 1) % agentSupportedLocales.size]
                    context.getSharedPreferences("itunda_agent_locale_prefs", android.content.Context.MODE_PRIVATE)
                        .edit().putString(AGENT_LOCALE_KEY, locale.name).apply()
                },
            )
        }
        Text(t("agent"), style = IdsTypography.Title2, color = Ids.colors.textSecondary)
        Text(t("subtitle"), style = IdsTypography.Body1, color = Ids.colors.textSecondary)
        Spacer(Modifier.height(24.dp))
        IdsTextField(phone, { phone = it }, label = t("phoneNumber"), modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        IdsTextField(password, { password = it }, label = t("password"), visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        error?.let { Text(it, color = Ids.colors.danger, style = IdsTypography.Body2, modifier = Modifier.padding(top = 12.dp)) }
        Spacer(Modifier.height(20.dp))
        IdsButton(enabled = !busy, onClick = {
            if (phone.isBlank() || password.isBlank()) { error = t("emptyFields"); return@IdsButton }
            busy = true; error = null
            scope.launch {
                try {
                    val auth = NetworkClient.authApi.login(LoginRequest(phone.trim(), password))
                    NetworkClient.session().save(auth.accessToken)
                    // Validate the role before leaving the sign-in screen. A normal
                    // consumer login must not look like a usable cashier session.
                    NetworkClient.agentApi.me()
                    // Real push device-token registration (item 130) -- best-effort,
                    // fire-and-forget: a registration failure must never block an
                    // otherwise successful login. See NetworkClient.kt's own doc comment.
                    scope.launch {
                        try {
                            NetworkClient.notificationsApi.registerDeviceToken(
                                RegisterDeviceTokenRequest(DevicePlatform.ANDROID, NetworkClient.device().getOrCreateDeviceId()),
                            )
                        } catch (e: Exception) {
                            // Best-effort, see doc comment above.
                        }
                    }
                    onLoggedIn()
                } catch (e: retrofit2.HttpException) {
                    // Real Toss-style error handling (2026-08-12) -- this catch used
                    // to collapse a real login failure, a real role-check failure
                    // (agentApi.me() rejecting a non-agent account), AND a network
                    // failure into one generic sentence, discarding any real backend
                    // message. A real HTTP response means it wasn't unreachable --
                    // surface what the backend actually said when it said something.
                    NetworkClient.session().clear()
                    error = rw.itunda.agent.network.apiErrorMessage(e)
                        ?: t("notAgentOrUnreachable")
                } catch (_: Exception) {
                    NetworkClient.session().clear()
                    error = t("connectionError")
                } finally { busy = false }
            }
        }, text = if (busy) t("signingIn") else t("signIn"))
    }
}
