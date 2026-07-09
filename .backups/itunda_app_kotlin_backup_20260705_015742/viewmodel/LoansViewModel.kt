package com.itunda.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itunda.app.data.api.RetrofitClient
import com.itunda.app.data.models.ActiveLoan
import com.itunda.app.data.models.LoanOffer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class LoansUiState(
    val isLoading: Boolean = false,
    val offers: List<LoanOffer> = emptyList(),
    val activeLoans: List<ActiveLoan> = emptyList(),
    val selectedOffer: LoanOffer? = null,
    val applyAmount: String = "",
    val isProcessing: Boolean = false,
    val result: String? = null,
    val error: String? = null
)

class LoansViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(LoansUiState())
    val uiState: StateFlow<LoansUiState> = _uiState

    init { loadData() }

    fun loadData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val offersResp = RetrofitClient.getApi().getLoanOffers()
                val loansResp = RetrofitClient.getApi().getMyLoans()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    offers = offersResp.body()?.offers ?: emptyList(),
                    activeLoans = loansResp.body()?.loans ?: emptyList()
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to load loans: ${e.message}"
                )
            }
        }
    }

    fun selectOffer(offer: LoanOffer) {
        _uiState.value = _uiState.value.copy(selectedOffer = offer, applyAmount = offer.minAmount.toString())
    }

    fun updateApplyAmount(amount: String) {
        _uiState.value = _uiState.value.copy(applyAmount = amount)
    }

    fun applyLoan() {
        val offer = _uiState.value.selectedOffer ?: return
        val amount = _uiState.value.applyAmount.toDoubleOrNull() ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessing = true, error = null)
            try {
                val response = RetrofitClient.getApi().applyLoan(
                    com.itunda.app.data.models.LoanApplyRequest(offer.id, amount, offer.termMonths)
                )
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    result = response.body()?.message ?: "Loan application submitted",
                    selectedOffer = null
                )
                loadData()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    error = "Application failed: ${e.message}"
                )
            }
        }
    }

    fun repayLoan(loanId: String, amount: Double) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessing = true, error = null)
            try {
                val response = RetrofitClient.getApi().repayLoan(
                    com.itunda.app.data.models.RepayRequest(loanId, amount)
                )
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    result = response.body()?.message ?: "Repayment successful"
                )
                loadData()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    error = "Repayment failed: ${e.message}"
                )
            }
        }
    }

    fun clearResult() {
        _uiState.value = _uiState.value.copy(result = null, error = null, selectedOffer = null)
    }
}
