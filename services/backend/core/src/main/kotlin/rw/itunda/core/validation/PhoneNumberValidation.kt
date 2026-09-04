package rw.itunda.core.validation

/**
 * Real gap found 2026-09-05, same shape as EmailValidation.kt's own doc comment:
 * `AuthService.register`'s `phoneNumber` was never checked for even being shaped like a
 * real phone number -- confirmed via a repo-wide grep, no phone-format check anywhere
 * in this backend. `User.phoneNumber`'s own column is `length = 32` (well past E.164's
 * real 15-digit maximum), confirming this schema was already built for general
 * international numbers, not a Rwanda-only assumption -- so this checks the real ITU-T
 * E.164 shape (a leading `+`, no leading zero in the country code, 8-15 total digits),
 * not a Rwanda-specific pattern, which would be a real, separate product-scope decision
 * this isn't.
 */
private val E164_PATTERN = Regex("^\\+[1-9]\\d{7,14}$")

fun isValidPhoneNumber(value: String): Boolean = E164_PATTERN.matches(value)
