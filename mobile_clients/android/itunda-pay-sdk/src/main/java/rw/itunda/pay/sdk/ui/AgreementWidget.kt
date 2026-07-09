package rw.itunda.pay.sdk.ui

import android.content.Context
import android.util.AttributeSet
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.LinearLayout

class AgreementWidget @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val termsCheckBox: CheckBox
    private val privacyCheckBox: CheckBox

    init {
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        
        termsCheckBox = CheckBox(context).apply {
            text = "I agree to Itunda Pay Terms of Service"
            isChecked = true
        }
        
        privacyCheckBox = CheckBox(context).apply {
            text = "I agree to the Privacy Policy"
            isChecked = true
        }
        
        layout.addView(termsCheckBox)
        layout.addView(privacyCheckBox)
        
        addView(layout)
    }

    fun isAllAgreed(): Boolean {
        return termsCheckBox.isChecked && privacyCheckBox.isChecked
    }
}
