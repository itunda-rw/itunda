package rw.itunda.identity

import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.time.Year

enum class NidaVerificationStatus { MATCHED, NOT_FOUND, INVALID_FORMAT, UNSUPPORTED_DOCUMENT_TYPE }

data class NidaVerificationResult(
    val status: NidaVerificationStatus,
    val detail: String,
    val citizenshipStatus: String? = null,
    val birthYear: Int? = null,
    val gender: String? = null,
)

/**
 * A real, honestly-scoped demo verification against Rwanda's real, publicly documented
 * National ID (Indangamuntu) number structure -- not a real NIDA database lookup (that
 * remains genuinely blocked on a government/vendor relationship, unchanged -- see
 * docs/TOSS_PARITY_MATRIX.md's Compliance row), but a real structural validator against
 * the actual documented format, plus a deterministic simulated "found in the database or
 * not" outcome so the same ID always behaves the same way (not randomly flaky on
 * resubmission). Same "real simulation, not a real integration" discipline
 * SimulatedProviderConnector already established for MTN/Airtel/bank rails, applied here
 * for the first time to identity -- lets a real admin reviewer, and the person testing
 * this flow, see what an automated pre-check would actually look like end to end.
 *
 * Real, sourced structure (Rwanda's National ID Agency's own public documentation and
 * Rwandan press coverage of the 16-digit Indangamuntu number, not invented): digit 1 =
 * citizenship status (1 = citizen, 2 = refugee, 3 = foreigner), digits 2-5 = year of
 * birth, digit 6 = gender (8 = male, 7 = female), digits 7-13 = sequential issuance
 * order within that birth year, digit 14 = reissuance count, digits 15-16 = a security
 * checksum whose real algorithm is not public (known only to NIDA itself) -- this demo
 * validates everything a real client-side/pre-check step reasonably could and honestly
 * cannot validate that final checksum, rather than silently pretending to.
 */
@Service
class DemoNidaVerificationService {

    fun verify(documentType: String, documentNumber: String): NidaVerificationResult {
        if (!documentType.equals("NATIONAL_ID", ignoreCase = true)) {
            return NidaVerificationResult(
                NidaVerificationStatus.UNSUPPORTED_DOCUMENT_TYPE,
                "Automated verification only covers NATIONAL_ID -- this document type needs manual review",
            )
        }

        val digits = documentNumber.trim()
        if (digits.length != 16 || !digits.all { it.isDigit() }) {
            return NidaVerificationResult(NidaVerificationStatus.INVALID_FORMAT, "A real Rwandan National ID is exactly 16 digits")
        }

        val citizenshipStatus = when (digits[0]) {
            '1' -> "CITIZEN"
            '2' -> "REFUGEE"
            '3' -> "FOREIGNER"
            else -> null
        } ?: return NidaVerificationResult(NidaVerificationStatus.INVALID_FORMAT, "Digit 1 must be 1 (citizen), 2 (refugee), or 3 (foreigner)")

        val birthYear = digits.substring(1, 5).toIntOrNull()
        val currentYear = Year.now().value
        if (birthYear == null || birthYear < 1900 || birthYear > currentYear) {
            return NidaVerificationResult(NidaVerificationStatus.INVALID_FORMAT, "Digits 2-5 must be a plausible birth year")
        }

        val gender = when (digits[5]) {
            '8' -> "MALE"
            '7' -> "FEMALE"
            else -> null
        } ?: return NidaVerificationResult(NidaVerificationStatus.INVALID_FORMAT, "Digit 6 must be 8 (male) or 7 (female)")

        // Deterministic, not random: hashing the ID itself means the same number always
        // gets the same simulated outcome across repeated submissions, matching how a
        // real database lookup behaves (unlike SimulatedProviderConnector's per-call dice
        // roll, which models a live network call, not a lookup against a fixed record).
        val digest = MessageDigest.getInstance("SHA-256").digest(digits.toByteArray())
        val bucket = (digest[0].toInt() and 0xFF) % 100
        // ~90% of structurally-valid IDs "match" -- close to a real system's overwhelming-
        // majority-succeeds shape, while still leaving a real, testable decline path
        // rather than every submission trivially passing.
        return if (bucket < 90) {
            NidaVerificationResult(
                NidaVerificationStatus.MATCHED, "Structurally valid and found in the simulated NIDA registry",
                citizenshipStatus, birthYear, gender,
            )
        } else {
            NidaVerificationResult(
                NidaVerificationStatus.NOT_FOUND, "Structurally valid but no matching record in the simulated NIDA registry",
                citizenshipStatus, birthYear, gender,
            )
        }
    }
}
