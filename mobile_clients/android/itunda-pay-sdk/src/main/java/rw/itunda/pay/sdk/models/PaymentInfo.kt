package rw.itunda.pay.sdk.models

data class PaymentInfo(
    val orderId: String,
    val orderName: String,
    val amount: Long,
    val currency: String = "RWF",
    val customerName: String? = null,
    val customerEmail: String? = null,
    val customerMobilePhone: String? = null,
    val successUrl: String? = null,
    val failUrl: String? = null
)
