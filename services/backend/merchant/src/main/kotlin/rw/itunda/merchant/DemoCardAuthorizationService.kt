package rw.itunda.merchant

import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.time.YearMonth

enum class CardAuthorizationStatus { APPROVED, DECLINED_INSUFFICIENT_FUNDS, DECLINED_GENERIC, DECLINED_EXPIRED_CARD, DECLINED_INVALID_CARD }

data class CardAuthorizationResult(val status: CardAuthorizationStatus, val detail: String, val last4: String)

/**
 * A real, honestly-scoped demo card authorization -- not a real PSP/card-network
 * integration (that remains genuinely blocked on an actual commercial PSP relationship,
 * see docs/TOSS_PARITY_MATRIX.md's Merchant row: "POS, card processing, B2B payroll ...
 * genuinely need real PSP-level infrastructure this repo has no path to certify"), but a
 * real Luhn-algorithm card-number validator (the actual, standard checksum every real
 * card issuer uses -- not invented) plus a deterministic simulated authorization
 * outcome. Same "real simulation, not a real integration" discipline
 * SimulatedProviderConnector (MTN/Airtel/bank rails) and DemoNidaVerificationService
 * (identity) already established.
 *
 * A small set of fixed test numbers (itunda's own demo convention, modeled on how real
 * PSPs like Stripe publish fixed test cards for exactly this purpose -- not itunda
 * actually using Stripe) give a predictable, repeatable outcome for testing specific
 * paths; any other Luhn-valid number gets a deterministic outcome (hashed from the card
 * number itself, not random) so the same card always behaves the same way on
 * resubmission, matching how a real issuer's decision on a given real card would.
 */
@Service
class DemoCardAuthorizationService {

    companion object {
        const val TEST_CARD_APPROVE = "4242424242424242"
        const val TEST_CARD_DECLINE_GENERIC = "4000000000000002"
        const val TEST_CARD_DECLINE_INSUFFICIENT_FUNDS = "4000000000009995"
    }

    fun authorize(cardNumber: String, expiryMonth: Int, expiryYear: Int, cvc: String): CardAuthorizationResult {
        val digits = cardNumber.filter { it.isDigit() }
        val last4 = digits.takeLast(4)

        if (digits.length !in 13..19 || digits != cardNumber.filter { !it.isWhitespace() } || !isLuhnValid(digits)) {
            return CardAuthorizationResult(CardAuthorizationStatus.DECLINED_INVALID_CARD, "Card number failed Luhn checksum validation", last4)
        }
        if (cvc.length !in 3..4 || !cvc.all { it.isDigit() }) {
            return CardAuthorizationResult(CardAuthorizationStatus.DECLINED_INVALID_CARD, "Invalid CVC", last4)
        }
        val expiry = try {
            YearMonth.of(expiryYear, expiryMonth)
        } catch (e: Exception) {
            return CardAuthorizationResult(CardAuthorizationStatus.DECLINED_INVALID_CARD, "Invalid expiry date", last4)
        }
        if (expiry.isBefore(YearMonth.now())) {
            return CardAuthorizationResult(CardAuthorizationStatus.DECLINED_EXPIRED_CARD, "Card has expired", last4)
        }

        return when (digits) {
            TEST_CARD_APPROVE -> CardAuthorizationResult(CardAuthorizationStatus.APPROVED, "Approved (demo test card)", last4)
            TEST_CARD_DECLINE_GENERIC -> CardAuthorizationResult(CardAuthorizationStatus.DECLINED_GENERIC, "Declined by issuer (demo test card)", last4)
            TEST_CARD_DECLINE_INSUFFICIENT_FUNDS -> CardAuthorizationResult(CardAuthorizationStatus.DECLINED_INSUFFICIENT_FUNDS, "Insufficient funds (demo test card)", last4)
            else -> simulateOutcome(digits, last4)
        }
    }

    private fun simulateOutcome(digits: String, last4: String): CardAuthorizationResult {
        val digest = MessageDigest.getInstance("SHA-256").digest(digits.toByteArray())
        val bucket = (digest[0].toInt() and 0xFF) % 100
        return when {
            bucket < 85 -> CardAuthorizationResult(CardAuthorizationStatus.APPROVED, "Approved", last4)
            bucket < 93 -> CardAuthorizationResult(CardAuthorizationStatus.DECLINED_INSUFFICIENT_FUNDS, "Insufficient funds", last4)
            else -> CardAuthorizationResult(CardAuthorizationStatus.DECLINED_GENERIC, "Declined by issuer", last4)
        }
    }

    // The real, standard Luhn checksum algorithm every real card issuer's numbers
    // satisfy -- not invented, not vendor-specific.
    private fun isLuhnValid(digits: String): Boolean {
        var sum = 0
        var alternate = false
        for (i in digits.length - 1 downTo 0) {
            var n = digits[i] - '0'
            if (alternate) {
                n *= 2
                if (n > 9) n -= 9
            }
            sum += n
            alternate = !alternate
        }
        return sum % 10 == 0
    }
}
