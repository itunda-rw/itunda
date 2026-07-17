package rw.itunda.identity

import java.security.MessageDigest
import org.springframework.stereotype.Service

enum class KybVerificationStatus { MATCHED, NOT_FOUND, INVALID_FORMAT }

data class KybVerificationResult(val status: KybVerificationStatus, val detail: String)

/**
 * A real, honestly-scoped demo verification against Rwanda's real, publicly documented
 * Taxpayer Identification Number (TIN) format -- not a real RDB/RRA business-registry
 * lookup (that stays genuinely blocked on a government/vendor relationship, same as
 * DemoNidaVerificationService's NIDA gap), but a real structural validator against the
 * actual documented format, plus a deterministic simulated "found in the registry or
 * not" outcome. Same "real simulation, not a real integration" discipline applied to
 * KYB for the first time, closing the Compliance row's other named gap alongside NIDA.
 *
 * Real, sourced structure (Rwanda Revenue Authority's own public TIN documentation --
 * see rra.gov.rw's "What is a Taxpayer Identification Number?" guidance, and RDB's own
 * business-registration procedure, which issues the TIN automatically as part of
 * company registration since Feb 2015): a Rwandan TIN is exactly 9 digits, numeric
 * only, used for both individual and business taxpayers. RRA does not publish the
 * internal check-digit/classification algorithm within those 9 digits (unlike, say,
 * Luhn for card numbers) -- this demo validates the one real, sourced fact (length and
 * numeric-only) and honestly leaves the rest unvalidated, rather than inventing a
 * checksum algorithm that doesn't exist.
 */
@Service
class DemoKybVerificationService {

    fun verify(tin: String): KybVerificationResult {
        val digits = tin.trim()
        if (digits.length != 9 || !digits.all { it.isDigit() }) {
            return KybVerificationResult(KybVerificationStatus.INVALID_FORMAT, "A real Rwandan TIN is exactly 9 digits")
        }

        // Deterministic, not random -- same reasoning as DemoNidaVerificationService's
        // own hash-based outcome: the same TIN always behaves the same way on resubmission.
        val digest = MessageDigest.getInstance("SHA-256").digest(digits.toByteArray())
        val bucket = (digest[0].toInt() and 0xFF) % 100
        return if (bucket < 90) {
            KybVerificationResult(KybVerificationStatus.MATCHED, "Structurally valid and found in the simulated RDB/RRA business registry")
        } else {
            KybVerificationResult(KybVerificationStatus.NOT_FOUND, "Structurally valid but no matching record in the simulated business registry")
        }
    }
}
