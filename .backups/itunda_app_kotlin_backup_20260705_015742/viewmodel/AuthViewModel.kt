package com.itunda.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.itunda.app.data.api.RetrofitClient
import com.itunda.app.data.models.LoginRequest
import com.itunda.app.data.models.RegisterRequest
import com.itunda.app.data.models.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class AuthUiState(
    val isLoading: Boolean = false,
    val isLoggedIn: Boolean = false,
    val user: User? = null,
    val error: String? = null,
    val isRegistering: Boolean = false
)

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState

    init {
        if (RetrofitClient.isLoggedIn()) {
            loadProfile()
        }
    }

    fun login(phone: String, password: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val response = RetrofitClient.getApi().login(LoginRequest(phone, password))
                if (response.isSuccessful && response.body()?.success == true) {
                    val body = response.body()!!
                    RetrofitClient.saveToken(body.token ?: "")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isLoggedIn = true,
                        user = body.user,
                        error = null
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = response.body()?.message ?: "Login failed"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Network error: ${e.message}"
                )
            }
        }
    }

    fun register(name: String, phone: String, password: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val parts = name.trim().split(" ", limit = 2)
                val firstName = parts.getOrElse(0) { name }
                val lastName = parts.getOrElse(1) { "" }
                val response = RetrofitClient.getApi().register(RegisterRequest(firstName, lastName, phone, password))
                if (response.isSuccessful && response.body()?.success == true) {
                    val body = response.body()!!
                    RetrofitClient.saveToken(body.token ?: "")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isLoggedIn = true,
                        user = body.user,
                        error = null
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = response.body()?.message ?: "Registration failed"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Network error: ${e.message}"
                )
            }
        }
    }

    fun loadProfile() {
        viewModelScope.launch {
            try {
                val response = RetrofitClient.getApi().getProfile()
                if (response.isSuccessful && response.body()?.success == true) {
                    val body = response.body()!!
                    _uiState.value = _uiState.value.copy(
                        isLoggedIn = true,
                        user = body.user
                    )
                } else {
                    RetrofitClient.clearToken()
                    _uiState.value = AuthUiState()
                }
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(isLoggedIn = true)
            }
        }
    }

    fun toggleRegister() {
        _uiState.value = _uiState.value.copy(
            isRegistering = !_uiState.value.isRegistering,
            error = null
        )
    }

    fun openDemo() {
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            isLoggedIn = true,
            user = User(
                id = "demo_user",
                firstName = "Jean",
                lastName = "Baptiste",
                phone = "+250788123456",
                email = "demo@itunda.rw",
                tier = "prime",
                creditScore = 842,
                joinedDate = "2026-07-04"
            ),
            error = null
        )
    }

    fun logout() {
        RetrofitClient.clearToken()
        _uiState.value = AuthUiState()
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
