package com.itunda.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itunda.app.data.api.RetrofitClient
import com.itunda.app.data.models.BillProvider
import com.itunda.app.data.models.PendingBill
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class BillsUiState(
    val isLoading: Boolean = false,
    val providers: List<BillProvider> = emptyList(),
    val pendingBills: List<PendingBill> = emptyList(),
    val selectedProvider: BillProvider? = null,
    val airtimePhone: String = "",
    val airtimeAmount: String = "",
    val airtimeNetwork: String = "MTN",
    val isPaying: Boolean = false,
    val paymentResult: String? = null,
    val error: String? = null
)

class BillsViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(BillsUiState())
    val uiState: StateFlow<BillsUiState> = _uiState

    init { loadData() }

    fun loadData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val providersResp = RetrofitClient.getApi().getBillProviders()
                val billsResp = RetrofitClient.getApi().getPendingBills()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    providers = providersResp.body()?.providers ?: emptyList(),
                    pendingBills = billsResp.body()?.pendingBills ?: emptyList()
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to load bills: ${e.message}"
                )
            }
        }
    }

    fun selectProvider(provider: BillProvider) {
        _uiState.value = _uiState.value.copy(selectedProvider = provider)
    }

    fun updateAirtimePhone(phone: String) {
        _uiState.value = _uiState.value.copy(airtimePhone = phone)
    }

    fun updateAirtimeAmount(amount: String) {
        _uiState.value = _uiState.value.copy(airtimeAmount = amount)
    }

    fun updateAirtimeNetwork(network: String) {
        _uiState.value = _uiState.value.copy(airtimeNetwork = network)
    }

    fun payBill(billId: String, amount: Double) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isPaying = true, error = null)
            try {
                val response = RetrofitClient.getApi().payBill(
                    com.itunda.app.data.models.PayBillRequest(billId, amount, "w1")
                )
                _uiState.value = _uiState.value.copy(
                    isPaying = false,
                    paymentResult = response.body()?.message ?: "Payment successful"
                )
                loadData()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isPaying = false,
                    error = "Payment failed: ${e.message}"
                )
            }
        }
    }

    fun buyAirtime() {
        viewModelScope.launch {
            val amount = _uiState.value.airtimeAmount.toDoubleOrNull() ?: return@launch
            _uiState.value = _uiState.value.copy(isPaying = true, error = null)
            try {
                val response = RetrofitClient.getApi().buyAirtime(
                    com.itunda.app.data.models.AirtimeRequest(
                        phone = _uiState.value.airtimePhone,
                        amount = amount,
                        network = _uiState.value.airtimeNetwork
                    )
                )
                _uiState.value = _uiState.value.copy(
                    isPaying = false,
                    paymentResult = response.body()?.message ?: "Airtime purchase successful",
                    airtimePhone = "",
                    airtimeAmount = ""
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isPaying = false,
                    error = "Airtime purchase failed: ${e.message}"
                )
            }
        }
    }

    fun clearPaymentResult() {
        _uiState.value = _uiState.value.copy(paymentResult = null, error = null, selectedProvider = null)
    }
}
