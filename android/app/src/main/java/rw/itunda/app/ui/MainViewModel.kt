package rw.itunda.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import rw.itunda.app.network.Wallet
import rw.itunda.app.network.DiscoverItem
import rw.itunda.app.network.NetworkClient

class MainViewModel : ViewModel() {
    private val _primaryWallet = MutableStateFlow<Wallet?>(null)
    val primaryWallet: StateFlow<Wallet?> = _primaryWallet

    private val _discoverItems = MutableStateFlow<List<DiscoverItem>>(emptyList())
    val discoverItems: StateFlow<List<DiscoverItem>> = _discoverItems

    init {
        fetchData()
    }

    private fun fetchData() {
        viewModelScope.launch {
            try {
                // Try fetching real data from Kotlin Spring Boot
                val walletRes = NetworkClient.apiService.getWallets()
                if (walletRes.success) {
                    _primaryWallet.value = walletRes.wallets.firstOrNull { it.isPrimary } ?: walletRes.wallets.firstOrNull()
                }

                val discoverRes = NetworkClient.apiService.getDiscoverItems()
                if (discoverRes.success) {
                    _discoverItems.value = discoverRes.items
                }
                
            } catch (e: Exception) {
                // Fallback to static mockup data if backend is offline or unauthorized (no token)
                _primaryWallet.value = Wallet(
                    id = "w_local_fallback",
                    userId = "u_1",
                    currency = "RWF",
                    balance = 112242.0,
                    isPrimary = true
                )
                
                _discoverItems.value = listOf(
                    DiscoverItem("d_1", "government", "Irembo Services", "Pay government fees instantly", "Access 100+ services", "#0066FF", false, null),
                    DiscoverItem("d_3", "rewards", "itunda Points", "Earn on every transaction", "Earn 1 point per 100 RWF spent", "#FFB300", false, "1,240 pts"),
                    DiscoverItem("d_5", "lifestyle", "Yego Vouchers", "Exclusive partner deals", "Discounts at partners", "#E91E63", true, "Hot")
                )
            }
        }
    }
}
