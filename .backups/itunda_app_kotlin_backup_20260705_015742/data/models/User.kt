package com.itunda.app.data.models

import com.google.gson.annotations.SerializedName

data class User(
    val id: String = "",
    @SerializedName("firstName")
    val firstName: String = "",
    @SerializedName("lastName")
    val lastName: String = "",
    @SerializedName("phoneNumber")
    val phone: String = "",
    val email: String? = null,
    val avatar: String? = null,
    val tier: String = "standard",
    val creditScore: Int = 0,
    val joinedDate: String = ""
) {
    /** Convenience property — matches the single-name field used across the UI. */
    val name: String get() = "$firstName $lastName".trim()
}

data class LoginRequest(
    @SerializedName("phoneNumber")
    val phone: String,
    val password: String
)

data class RegisterRequest(
    @SerializedName("firstName")
    val firstName: String,
    @SerializedName("lastName")
    val lastName: String,
    @SerializedName("phoneNumber")
    val phone: String,
    val password: String
)

data class AuthResponse(
    val success: Boolean,
    @SerializedName("accessToken")
    val token: String?,
    val user: User?,
    val message: String?
)

data class CreditScoreData(
    val score: Int,
    val change: Int,
    val level: String,
    val history: List<ScoreHistory>
)

data class ScoreHistory(
    val date: String,
    val score: Int
)
