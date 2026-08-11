package rw.itunda.core.designsystem.components

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Real Toss-style live thousand-separator amount formatting (2026-08-12) -- the user's
 * own direct research request ("toss/kakao simplicity, interactions... let's do exactly
 * as they did") surfaced a real, concrete, currently-missing gap: every amount field in
 * this app (TransferFlow, Loans, WeeklySavings, Grow31Savings, etc.) shows raw typed
 * digits with no formatting at all ("50000"), while Toss's own actual, widely-observed
 * amount-entry screens live-format the value with comma separators as you type
 * ("50,000") -- one of the most recognizable, real "feels convenient" details of its
 * transfer/payment UX, distinct from (and never covered by) this repo's own earlier
 * DESIGN_REFERENCES.md §11 "minimum input" research, which focused on eliminating
 * clicks/keystrokes rather than formatting the ones already typed.
 *
 * Only affects DISPLAY -- the underlying text field value the caller's own
 * `onValueChange` receives stays pure digits, exactly as before this existed. Standard
 * `VisualTransformation` + `OffsetMapping` pattern (the same shape Compose's own
 * official credit-card-number sample uses): edits are interpreted against the real
 * untransformed digit string, the offset mapping only repositions the visual cursor so
 * it lands correctly relative to inserted commas.
 */
object AmountVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text.filter { it.isDigit() }
        if (digits.isEmpty()) return TransformedText(text, OffsetMapping.Identity)

        val formatted = StringBuilder()
        // transformedIndexAfterDigit[i] = the formatted-string index immediately after
        // the i-th original digit has been appended (i.e. including any comma placed
        // right before it) -- used to map a raw cursor offset to its visual position.
        val transformedIndexAfterDigit = IntArray(digits.length)
        for (i in digits.indices) {
            // A comma belongs immediately before this digit whenever the count of
            // digits from here to the end (inclusive) is a multiple of 3 -- i.e. this
            // digit starts a new group of 3 counted from the right, and it isn't the
            // very first digit typed.
            if (i != 0 && (digits.length - i) % 3 == 0) formatted.append(',')
            formatted.append(digits[i])
            transformedIndexAfterDigit[i] = formatted.length
        }
        val formattedString = formatted.toString()

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                val clamped = offset.coerceIn(0, digits.length)
                return if (clamped == 0) 0 else transformedIndexAfterDigit[clamped - 1]
            }

            override fun transformedToOriginal(offset: Int): Int {
                val clamped = offset.coerceIn(0, formattedString.length)
                var digitCount = 0
                for (j in 0 until clamped) {
                    if (formattedString[j] != ',') digitCount++
                }
                return digitCount.coerceIn(0, digits.length)
            }
        }
        return TransformedText(AnnotatedString(formattedString), offsetMapping)
    }
}
