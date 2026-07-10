package rw.itunda.sdk.pay

import android.content.Context
import android.content.Intent
import android.net.Uri

object ItundaPayments {
    private const val ITUNDA_PAYMENTS_SCHEME = "itundapayments://process"

    /**
     * Call this method to open the Itunda app to process a payment.
     * This is intended to be used by 3rd party apps integrating Itunda Payments.
     */
    fun processPayment(context: Context, amount: Double, merchantId: String, orderId: String) {
        val uri = Uri.parse("$ITUNDA_PAYMENTS_SCHEME?amount=$amount&merchantId=$merchantId&orderId=$orderId")
        val intent = Intent(Intent.ACTION_VIEW, uri)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        } else {
            // In a real SDK, you would open the Google Play Store here
            throw IllegalStateException("Itunda app is not installed.")
        }
    }
}
