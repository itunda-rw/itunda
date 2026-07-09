package com.itunda.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itunda.app.data.api.RetrofitClient
import com.itunda.app.data.models.Contact
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class PayUiState(
    val isLoading: Boolean = false,
    val contacts: List<Contact> = emptyList(),
    val selectedContact: Contact? = null,
    val amount: String = "",
    val isSending: Boolean = false,
    val sendResult: String? = null,
    val error: String? = null
)

class PayViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(PayUiState())
    val uiState: StateFlow<PayUiState> = _uiState

    init { loadContacts() }

    fun loadContacts() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val response = RetrofitClient.getApi().getContacts()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    contacts = response.body()?.contacts ?: emptyList()
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to load contacts: ${e.message}"
                )
            }
        }
    }

    fun selectContact(contact: Contact) {
        _uiState.value = _uiState.value.copy(selectedContact = contact)
    }

    fun updateAmount(amount: String) {
        _uiState.value = _uiState.value.copy(amount = amount)
    }

    fun sendMoney() {
        viewModelScope.launch {
            val contact = _uiState.value.selectedContact ?: return@launch
            val amount = _uiState.value.amount.toDoubleOrNull() ?: return@launch
            _uiState.value = _uiState.value.copy(isSending = true, error = null)
            try {
                val response = RetrofitClient.getApi().transfer(
                    com.itunda.app.data.models.TransferRequest(
                        fromWalletId = "w1",
                        toContactId = contact.id,
                        amount = amount,
                        memo = null
                    )
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    _uiState.value = _uiState.value.copy(
                        isSending = false,
                        sendResult = "Sent $amount RWF to ${contact.name}",
                        selectedContact = null,
                        amount = ""
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isSending = false,
                        error = response.body()?.message ?: "Transfer failed"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSending = false,
                    error = "Network error: ${e.message}"
                )
            }
        }
    }

    fun clearResult() {
        _uiState.value = _uiState.value.copy(sendResult = null, error = null)
    }
}
