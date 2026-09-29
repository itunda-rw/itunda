package rw.itunda.core.account

import org.springframework.stereotype.Component
import rw.itunda.core.repository.AccountRepository
import kotlin.random.Random

/**
 * Real, collision-safe account-number generation -- see Toss Bank's own published
 * account-number design philosophy (SLASH21, "토스뱅크의 데이터 설계사상"): a real
 * numeric range per account type, sized against real projected volume, with a real
 * check against existing rows before handing a number out, not a hope-it-doesn't-
 * collide random draw.
 *
 * Found via a repo-wide sweep (docs/DESIGN_REFERENCES.md §13): 9 separate services
 * each carried their own private `generateAccountNumber()` copy, every one built on
 * `Math.random()` with zero check against the real `UNIQUE(account_number)` constraint
 * on `accounts` (V1__init_schema.sql, `uq_accounts_account_number`) -- three of them
 * (AuthService, GroupAccountService, YouthAccountService) even shared the exact same
 * 2024100000..2024999999 range, so a collision there was never just a same-account-
 * type risk. A collision on any of the 9 would have surfaced as a raw, unhandled
 * `DataIntegrityViolationException` on whichever request lost the race, not a
 * graceful retry.
 */
@Component
class AccountNumberGenerator(private val accountRepository: AccountRepository) {
    /** [prefix] is the account-type-specific 10-digit base (e.g. 2025200000 for
     * WEEKLY_SAVINGS) -- kept per-caller, not centralized here, since the prefix IS
     * itunda's own real account-type encoding and each call site already documents its
     * own choice of range. */
    fun generate(prefix: Long, rangeSize: Long = 900_000L): String {
        repeat(10) {
            val candidate = (prefix + Random.nextLong(rangeSize)).toString()
            if (accountRepository.findByAccountNumber(candidate) == null) return candidate
        }
        throw IllegalStateException("Could not generate a unique account number in the range starting at $prefix after 10 attempts")
    }
}
