package rw.itunda.pay.sdk

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rw.itunda.pay.sdk.models.PaymentInfo
import rw.itunda.pay.sdk.models.PaymentResult
import rw.itunda.pay.sdk.ui.AgreementWidget
import rw.itunda.pay.sdk.ui.PaymentMethodWidget

class PaymentWidget(
    private val context: Context,
    private val clientKey: String,
    private val customerKey: String
) {
    private var paymentMethodWidget: PaymentMethodWidget? = null
    private var agreementWidget: AgreementWidget? = null
    
    /**
     * Bind the payment method widget from the UI to this instance.
     */
    fun renderPaymentMethods(
        widget: PaymentMethodWidget,
        amount: Long,
        currency: String = "RWF"
    ) {
        this.paymentMethodWidget = widget
        widget.updateAmount(amount, currency)
    }
    
    /**
     * Bind the agreement widget from the UI to this instance.
     */
    fun renderAgreement(
        widget: AgreementWidget
    ) {
        this.agreementWidget = widget
    }
    
    /**
     * Start the payment request flow.
     */
    fun requestPayment(
        paymentInfo: PaymentInfo,
        callback: (PaymentResult) -> Unit
    ) {
        val selectedMethod = paymentMethodWidget?.getSelectedMethod()
        if (selectedMethod == null) {
            callback(PaymentResult.Failure("NO_PAYMENT_METHOD", "Please select a payment method", paymentInfo.orderId))
            return
        }
        
        if (agreementWidget?.isAllAgreed() != true) {
            callback(PaymentResult.Failure("AGREEMENT_REQUIRED", "Please agree to all terms", paymentInfo.orderId))
            return
        }

        // Mock network request to Itunda Pay Backend
        CoroutineScope(Dispatchers.IO).launch {
            delay(1500) // Simulate network latency
            
            if (paymentInfo.amount > 0) {
                // Simulate successful transaction for MTN or Airtel
                val mockPaymentKey = "itunda_pay_${System.currentTimeMillis()}"
                withContext(Dispatchers.Main) {
                    callback(
                        PaymentResult.Success(
                            paymentKey = mockPaymentKey,
                            orderId = paymentInfo.orderId,
                            amount = paymentInfo.amount
                        )
                    )
                }
            } else {
                withContext(Dispatchers.Main) {
                    callback(
                        PaymentResult.Failure(
                            code = "INVALID_AMOUNT",
                            message = "Payment amount must be greater than 0",
                            orderId = paymentInfo.orderId
                        )
                    )
                }
            }
        }
    }
}
