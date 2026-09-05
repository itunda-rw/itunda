package rw.itunda.app

import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Component
import rw.itunda.core.account.AccountNumberGenerator
import rw.itunda.core.domain.Account
import rw.itunda.core.domain.AccountType
import rw.itunda.core.repository.AccountRepository
import rw.itunda.core.repository.UserRepository
import java.math.BigDecimal
import java.util.UUID

/**
 * Real, one-time-per-user backfill for the 2026-08-21 Toss Bank/Toss Pay separation --
 * see AccountType.PAY's own doc comment for the architecture. AuthService.register only
 * provisions a PAY account for BRAND-NEW registrations from that date forward; every
 * user who registered before this shipped has no PAY account row at all, so their first
 * real payment attempt (MerchantService.collect, now debiting PAY instead of MAIN) would
 * throw a raw AccountNotFoundException("No itunda Pay money found for this account")
 * instead of a graceful, expected error. Idempotent and additive-only (only INSERTs a
 * missing PAY row, never touches an existing one), so safe to run on every startup --
 * same one-time-per-row shape as SeedDataRunner's own backfill block, just scoped to
 * every real user instead of the 5 demo merchants.
 *
 * Ordering relative to SeedDataRunner is unspecified (neither declares @Order) -- harmless
 * either way, since this is idempotent and re-runs every startup: if it happens to run
 * before SeedDataRunner on a fresh environment, any demo users SeedDataRunner just created
 * simply get backfilled on the very next restart instead of this one. Real (non-seed) users
 * are unaffected by ordering since AuthService.register already provisions their PAY
 * account inline at signup time -- this runner only ever touches PRE-EXISTING users.
 */
@Component
class PayAccountBackfillRunner(
    private val userRepository: UserRepository,
    private val accountRepository: AccountRepository,
    private val accountNumberGenerator: AccountNumberGenerator,
) : CommandLineRunner {
    private val log = LoggerFactory.getLogger(PayAccountBackfillRunner::class.java)

    override fun run(vararg args: String?) {
        val allUsers = userRepository.findAll()
        if (allUsers.isEmpty()) return

        val userIds = allUsers.map { it.id }
        val usersWithPay = accountRepository.findByUserIdInAndType(userIds, AccountType.PAY)
            .map { it.userId }
            .toSet()
        val missing = allUsers.filter { it.id !in usersWithPay }
        if (missing.isEmpty()) return

        missing.forEach { user ->
            accountRepository.save(
                Account(
                    id = "account_${UUID.randomUUID()}",
                    userId = user.id,
                    accountNumber = accountNumberGenerator.generate(2024100000L),
                    // Real gap found 2026-09-05: this reads firstName from a
                    // PRE-EXISTING user row, so AuthService.register's own firstName
                    // length bound (added this same session) can't protect a user who
                    // registered before that fix shipped -- a legacy firstName between
                    // 236 and 255 characters would still overflow this real
                    // Hibernate-default-255 accountName column once "'s itunda Pay
                    // Money" (19 chars) is appended. Truncating rather than skipping:
                    // this is a one-time batch backfill, and a user missing their PAY
                    // account entirely (skipped) is a worse real outcome than a
                    // truncated display name for what's realistically dummy/seed data
                    // anyway.
                    accountName = "${user.firstName.take(236)}'s itunda Pay Money",
                    type = AccountType.PAY,
                    balance = BigDecimal.ZERO,
                    availableBalance = BigDecimal.ZERO,
                ),
            )
        }
        log.info("PayAccountBackfillRunner: provisioned {} missing PAY accounts for pre-existing users", missing.size)
    }
}
