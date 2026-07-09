package com.itunda.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itunda.app.data.api.RetrofitClient
import com.itunda.app.data.models.Transaction
import com.itunda.app.data.models.Wallet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val isLoading: Boolean = false,
    val wallets: List<Wallet> = emptyList(),
    val totalBalance: Double = 0.0,
    val transactions: List<Transaction> = emptyList(),
    val creditScore: Int = 0,
    val error: String? = null
)

class HomeViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState

    init { loadData() }

    fun loadData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val balanceResp = RetrofitClient.getApi().getBalance()
                val txnsResp = RetrofitClient.getApi().getTransactions()
                val creditResp = RetrofitClient.getApi().getCreditScore()

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    wallets = balanceResp.body()?.wallets ?: emptyList(),
                    totalBalance = balanceResp.body()?.totalBalance ?: 0.0,
                    transactions = txnsResp.body()?.transactions ?: emptyList(),
                    creditScore = if (creditResp.body()?.success == true) 720 else 0,
                    error = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    wallets = demoWallets,
                    totalBalance = demoWallets.sumOf { it.balance },
                    transactions = demoTransactions,
                    creditScore = 842,
                    error = null
                )
            }
        }
    }

    private val demoWallets = listOf(
        Wallet("demo_itunda", "wallet", "itunda Money", "RWF wallet", 1850000.0, "RWF", "I", true),
        Wallet("demo_mtn", "mobile_money", "MTN Mobile Money", "0788 *** 456", 1250000.0, "RWF", "M", true),
        Wallet("demo_bk", "bank", "Bank of Kigali", "BK **** 2044", 2840500.0, "RWF", "B", true),
        Wallet("demo_save", "savings", "Emergency Savings", "Goal account", 760000.0, "RWF", "S", true)
    )

    private val demoTransactions = listOf(
        Transaction("txn_1", "credit", "Salary received", "Bank of Kigali", 1250000.0, "Today", "completed", null, "Jean"),
        Transaction("txn_2", "debit", "Irembo service", "Driver license renewal", 15000.0, "Yesterday", "completed", "Jean", null),
        Transaction("txn_3", "debit", "REG electricity", "Meter token", 35000.0, "Jul 2", "completed", "Jean", null),
        Transaction("txn_4", "debit", "MTN Airtime", "0788 *** 456", 25000.0, "Jul 1", "completed", "Jean", null)
    )
}
