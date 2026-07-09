package rw.itunda.pay.sdk.ui

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView

class PaymentMethodWidget @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val radioGroup: RadioGroup
    private var selectedMethod: String? = "MTN_MOMO"

    init {
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        
        val title = TextView(context).apply {
            text = "Select Payment Method (RWF)"
            textSize = 18f
            setPadding(0, 0, 0, 16)
        }
        layout.addView(title)
        
        radioGroup = RadioGroup(context).apply {
            orientation = RadioGroup.VERTICAL
        }
        
        val mtnRadio = RadioButton(context).apply {
            text = "MTN Mobile Money"
            id = 1
            isChecked = true
        }
        
        val airtelRadio = RadioButton(context).apply {
            text = "Airtel Money"
            id = 2
        }
        
        val cardRadio = RadioButton(context).apply {
            text = "Credit/Debit Card"
            id = 3
        }

        radioGroup.addView(mtnRadio)
        radioGroup.addView(airtelRadio)
        radioGroup.addView(cardRadio)

        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            selectedMethod = when (checkedId) {
                1 -> "MTN_MOMO"
                2 -> "AIRTEL_MONEY"
                3 -> "CARD"
                else -> null
            }
        }

        layout.addView(radioGroup)
        addView(layout)
    }

    fun getSelectedMethod(): String? = selectedMethod
    
    fun updateAmount(amount: Long, currency: String = "RWF") {
        // Here we could update UI to show amount or conditionally hide methods
    }
}
