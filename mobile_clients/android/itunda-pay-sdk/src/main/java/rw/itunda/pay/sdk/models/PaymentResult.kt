package rw.itunda.pay.sdk.models

sealed class PaymentResult {
    data class Success(val paymentKey: String, val orderId: String, val amount: Long) : PaymentResult()
    data class Failure(val code: String, val message: String, val orderId: String) : PaymentResult()
}
