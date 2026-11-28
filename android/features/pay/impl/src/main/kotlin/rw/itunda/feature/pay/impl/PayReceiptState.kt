package rw.itunda.feature.pay.impl

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** A refund request on one receipt (prototype `r.case`): step 0 sent, 1 merchant reviewing, 2 decided. */
internal class PayRefundCase(val id: String, val reason: String, val amount: Long, val step: Int)

internal val PAY_REFUND_REASONS = listOf(
    "wrong" to "Wrong amount charged",
    "twice" to "Charged twice",
    "none" to "Item or service not received",
    "mind" to "Changed my mind",
)

/** Screen-spanning demo state for receipts: starred merchants (shared with Saved merchants) and refund cases by receipt index. */
internal object PayReceiptState {
    var savedTills by mutableStateOf(emptySet<String>())
    /** Amount of the payment that just completed, shown on the Paid screen. */
    var lastAmount by mutableStateOf(0L)
    val cases = mutableStateMapOf<Int, PayRefundCase>()

    fun toggleSaved(till: String) { savedTills = if (till in savedTills) savedTills - till else savedTills + till }

    fun request(index: Int, reason: String, amount: Long) {
        cases[index] = PayRefundCase("RF" + (100000..999999).random(), reason, amount, 0)
    }

    fun advance(index: Int) {
        val c = cases[index] ?: return
        if (c.step < 2) cases[index] = PayRefundCase(c.id, c.reason, c.amount, c.step + 1)
    }

    /** Receipt status label, or null for a plain paid receipt. */
    fun status(index: Int): String? = cases[index]?.let { c ->
        if (c.step < 2) "Refund requested" else if (c.reason == "mind") "Refund declined" else "Refunded"
    }
}
