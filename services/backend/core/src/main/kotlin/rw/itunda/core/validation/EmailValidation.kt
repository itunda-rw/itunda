package rw.itunda.core.validation

/**
 * Real gap found 2026-09-05: neither `AuthService.register`'s optional `email` nor
 * `PartnerService.register`'s required `contactEmail` ever checked the value was even
 * shaped like an email address -- confirmed via a repo-wide grep for `@Email`/
 * `EMAIL_PATTERN`, zero hits anywhere in this backend. This isn't full deliverability
 * verification (that's a real, separate, deliberately-deferred decision, see
 * `project_itunda_partner_registration_unvetted` memory) -- just the same kind of cheap,
 * obvious input-shape check `PIN_PATTERN` already applies to a PIN. Deliberately not a
 * pedantic RFC 5322 implementation -- "has an @, something on both sides, a dot after
 * the @" is the same practical bar most real systems (including browsers' own
 * `<input type="email">`) apply, not an attempt to reject every technically-invalid-but-
 * real-world-common address shape.
 */
private val EMAIL_PATTERN = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

fun isValidEmail(value: String): Boolean = EMAIL_PATTERN.matches(value)
