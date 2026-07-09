package rw.itunda.core.web

/**
 * Matches Toss Payments' own public API error shape
 * (docs.tosspayments.com/reference/error-codes): `{"code": "SCREAMING_SNAKE_CASE",
 * "message": "human-readable reason"}`, not a bare message string. Every controller in
 * this backend previously declared its own local `data class ApiError(val error:
 * String)` with just a message and no machine-readable code — centralized here so every
 * error response across every module has the same, Toss-shaped contract.
 */
data class ApiError(val code: String, val message: String)
