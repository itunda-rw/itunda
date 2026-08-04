package rw.itunda.merchant.ui

import androidx.compose.foundation.clickable
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
import java.io.IOException

/** Shown once for any logged-in itunda user who hasn't registered a business yet. */
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
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        Spacer(modifier = Modifier.height(20.dp))
        IdsButton(
            text = if (busy) "Registering…" else "Register my business",
            enabled = !busy,
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
                    } catch (e: IOException) {
                        error = "Couldn't reach itunda. Check your connection and try again."
                    } catch (e: Exception) {
                        error = "Couldn't register your business right now. Try again."
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
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).clickable(onClick = onLogout),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}
