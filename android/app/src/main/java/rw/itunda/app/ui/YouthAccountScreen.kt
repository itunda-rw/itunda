package rw.itunda.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.components.rememberCountUp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import rw.itunda.core.designsystem.components.IdsTextField
import rw.itunda.core.designsystem.components.SkeletonBlock
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import retrofit2.HttpException
import rw.itunda.core.network.DepositYouthAccountRequest
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.SetBirthDateRequest
import rw.itunda.core.network.Account
import rw.itunda.core.network.apiErrorCode
import rw.itunda.core.network.superAppErrorMessage
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.designsystem.components.BackTopBar
import java.io.IOException
import java.util.UUID

// Real KakaoBank mini-style capped starter account (rw.itunda.account.YouthAccountService,
// 2026-07-28) -- first mobile client for this feature (item 100), a direct port of
// bank-mfe's YouthAccountCard (item 99) onto Android. Same no-ViewModel,
// NetworkClient-direct-from-Composable shape as WeeklySavingsScreen.kt.
private enum class YouthAccountMode { LOADING, NEEDS_BIRTH_DATE, NOT_OPEN, OPEN }

@Composable
fun YouthAccountScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var mode by remember { mutableStateOf(YouthAccountMode.LOADING) }
    var account by remember { mutableStateOf<Account?>(null) }
    var birthDate by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    fun load() {
        coroutineScope.launch {
            try {
                val accounts = NetworkClient.apiService.getAccounts().accounts
                val existing = accounts.find { it.type == "MINI" }
                account = existing
                mode = if (existing != null) YouthAccountMode.OPEN else YouthAccountMode.NOT_OPEN
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            }
        }
    }
    LaunchedEffect(Unit) { load() }

    fun openAccount() {
        busy = true
        error = null
        coroutineScope.launch {
            try {
                account = NetworkClient.apiService.openYouthAccount().account
                mode = YouthAccountMode.OPEN
            } catch (e: HttpException) {
                when (apiErrorCode(e)) {
                    "YOUTH_ACCOUNT_BIRTH_DATE_REQUIRED" -> mode = YouthAccountMode.NEEDS_BIRTH_DATE
                    "YOUTH_ACCOUNT_AGE_INELIGIBLE" -> error = "Youth accounts are only available for ages 7-18."
                    else -> error = superAppErrorMessage(e)
                }
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                busy = false
            }
        }
    }

    fun submitBirthDateAndOpen() {
        if (birthDate.isBlank()) return
        busy = true
        error = null
        coroutineScope.launch {
            try {
                NetworkClient.authApi.setBirthDate(SetBirthDateRequest(birthDate.trim()))
                openAccount()
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
                busy = false
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
                busy = false
            }
        }
    }

    fun deposit() {
        val parsedAmount = amount.trim().toBigDecimalOrNull()
        if (parsedAmount == null || parsedAmount <= java.math.BigDecimal.ZERO) {
            error = "Enter a real amount."
            return
        }
        busy = true
        error = null
        coroutineScope.launch {
            try {
                NetworkClient.apiService.depositYouthAccount(UUID.randomUUID().toString(), DepositYouthAccountRequest(parsedAmount))
                amount = ""
                load()
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                busy = false
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        BackTopBar(title = "Youth account", onBack = onBack)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = Ids.layout.screenHorizontal, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            error?.let { msg -> item { Text(msg, color = Ids.colors.danger, fontSize = 13.sp) } }
            when (mode) {
                YouthAccountMode.LOADING -> item {
                    SkeletonBlock(height = 120.dp)
                }
                YouthAccountMode.NOT_OPEN -> item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "A capped starter account for ages 7-18 -- a 500,000 RWF balance cap, 300,000 RWF " +
                                "daily and 2,000,000 RWF monthly deposit limits.",
                            color = Ids.colors.textSecondary, fontSize = 13.sp,
                        )
                        YouthAccountActionButton(if (busy) "Opening…" else "Open a Youth account", enabled = !busy) { openAccount() }
                    }
                }
                YouthAccountMode.NEEDS_BIRTH_DATE -> item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Enter your birth date to check eligibility.", color = Ids.colors.textSecondary, fontSize = 13.sp)
                        IdsTextField(value = birthDate, onValueChange = { birthDate = it }, label = "Birth date (YYYY-MM-DD)", modifier = Modifier.fillMaxWidth())
                        // Real CTA-label-clarity fix (item 244, docs/DESIGN_REFERENCES.md §11),
                        // matching the identical fix on web's own YouthAccountCard the same day:
                        // "Continue" doesn't say what happens -- the text above already names
                        // the real outcome.
                        YouthAccountActionButton(if (busy) "Checking…" else "Check eligibility", enabled = !busy) { submitBirthDateAndOpen() }
                    }
                }
                YouthAccountMode.OPEN -> {
                    item {
                        Card(
                            shape = RoundedCornerShape(Ids.layout.cardCornerRadius),
                            colors = CardDefaults.cardColors(containerColor = Ids.colors.surface),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                val animatedBalance = rememberCountUp(account?.balance ?: 0.0)
                                Text("${formatMoneyMini(animatedBalance)} RWF", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 28.sp)
                                Text(account?.accountNumber ?: "", color = Ids.colors.textSecondary, fontSize = 12.sp)
                            }
                        }
                    }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            rw.itunda.core.designsystem.components.AmountKeypadInput(
                                digits = amount, onDigitsChange = { amount = it },
                                quickAmounts = listOf(1_000L, 5_000L),
                            )
                            YouthAccountActionButton(if (busy) "Adding…" else "Add money", enabled = !busy) { deposit() }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun YouthAccountActionButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(Ids.colors.brand).pressScaleClickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

private fun formatMoneyMini(value: Double): String {
    val rounded = Math.round(value * 100.0) / 100.0
    return if (rounded == Math.floor(rounded)) rounded.toLong().toString() else "%.2f".format(rounded)
}
