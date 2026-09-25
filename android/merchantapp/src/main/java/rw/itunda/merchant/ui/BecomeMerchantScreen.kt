package rw.itunda.merchant.ui

import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import rw.itunda.core.designsystem.components.IdsButton
import androidx.compose.material3.MaterialTheme
import rw.itunda.core.designsystem.components.IdsTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import rw.itunda.merchant.network.MerchantDto
import rw.itunda.merchant.network.NetworkClient
import rw.itunda.merchant.network.RegisterMerchantRequest
import rw.itunda.merchant.network.apiErrorCode
import rw.itunda.merchant.network.apiErrorMessage
import java.io.IOException

/**
 * Shown once for any logged-in itunda user who hasn't registered a business yet.
 *
 * Real gap found 2026-08-15 (backend AlreadyX audit): a fresh install/reinstall has no
 * local memory of a prior registration, so this screen is reachable by an account
 * that's already a real registered merchant. That real, specific
 * MERCHANT_ALREADY_REGISTERED backend error used to be caught by a plain
 * `catch (e: Exception)` and replaced with a hardcoded "couldn't register, try again"
 * message -- a dead loop on every retry. Same Toss-style resolve-forward fix as
 * riderapp's BecomeRiderScreen: load the existing merchant profile and proceed.
 */
@Composable
fun BecomeMerchantScreen(onRegistered: (MerchantDto) -> Unit, onLogout: () -> Unit) {
    var businessName by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Register your business", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "Accept real payments, manage your menu, and handle incoming Eats orders from one app.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(16.dp))
        IdsTextField(value = businessName, onValueChange = { businessName = it }, label = "Business name", modifier = Modifier.fillMaxWidth())
        error?.let {
            Spacer(modifier = Modifier.height(12.dp))
            Text(it, color = Ids.colors.danger)
        }
        Spacer(modifier = Modifier.height(20.dp))
        IdsButton(
            text = if (busy) "Registering…" else "Register my business",
            enabled = !busy,
            loading = busy,
            onClick = {
                if (businessName.isBlank()) {
                    error = "Enter your business name."
                    return@IdsButton
                }
                busy = true
                error = null
                scope.launch {
                    try {
                        val merchant = NetworkClient.apiService.registerMerchant(RegisterMerchantRequest(businessName.trim())).merchant
                        onRegistered(merchant)
                    } catch (e: retrofit2.HttpException) {
                        if (apiErrorCode(e) == "MERCHANT_ALREADY_REGISTERED") {
                            // Real Toss-style resolution, not a dead-end error: the
                            // account genuinely IS already a registered merchant, so
                            // move them forward instead of erroring on every retry.
                            try {
                                onRegistered(NetworkClient.apiService.getMyMerchant().merchant)
                            } catch (e2: Exception) {
                                error = "You're already registered, but we couldn't load your business right now. Try again."
                            }
                        } else {
                            error = apiErrorMessage(e)
                                ?: "itunda is having a brief hiccup on our end -- not something you did. Try again in a moment."
                        }
                    } catch (e: IOException) {
                        error = "Couldn't reach itunda. Check your connection and try again."
                    } finally {
                        busy = false
                    }
                }
            },
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "Log out",
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).pressScaleClickable(onClick = onLogout),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}
