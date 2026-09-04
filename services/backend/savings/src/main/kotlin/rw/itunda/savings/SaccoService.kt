package rw.itunda.savings

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.SaccoDividendDistribution
import rw.itunda.core.domain.SaccoDividendPayout
import rw.itunda.core.domain.SaccoShareholding
import rw.itunda.core.domain.Account
import rw.itunda.core.domain.AccountType
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.SaccoDividendDistributionRepository
import rw.itunda.core.repository.SaccoDividendPayoutRepository
import rw.itunda.core.repository.SaccoShareholdingRepository
import rw.itunda.core.repository.UserRepository
import rw.itunda.core.repository.AccountRepository
import rw.itunda.core.account.AccountNumberGenerator
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class SaccoNoAccountException(message: String) : RuntimeException(message)
class SaccoNoShareholdingException(message: String) : RuntimeException(message)
class SaccoInsufficientSharesException(message: String) : RuntimeException(message)
class SaccoNoSharesOutstandingException(message: String) : RuntimeException(message)

data class SaccoShareholdingView(val shareholding: SaccoShareholding, val currentValue: BigDecimal)

// Real per-user sentinel that owns the one shared "itunda SACCO" pool account -- not a
// real registered account. `Account.userId` carries no DB foreign-key constraint (a
// plain VARCHAR, confirmed by reading Account.kt directly before choosing this), so a
// fixed non-user sentinel is safe: exactly matching how a real SACCO has its own
// cooperative legal identity separate from any individual member.
private const val SACCO_POOL_USER_ID = "sacco_pool_system"

/**
 * Real Umurenge SACCO-style shares & dividends -- see SaccoShareholding.kt's own doc
 * comment for the full sourced account (Rwanda's real 416-sector government-backed
 * cooperative savings model). Distinct from `IkiminaService` (informal rotating-pot
 * ROSCA, no shares/dividends) and from `SavingsService`'s own fixed-rate savings goal.
 * Reuses the exact real ledger-movement shape every other money-moving feature in this
 * backend already uses -- a real Account(type=GROUP) pool, contributions/redemptions/
 * dividend payouts are all real ACCOUNT-to-ACCOUNT ledger transactions.
 */
@Service
class SaccoService(
    private val shareholdingRepository: SaccoShareholdingRepository,
    private val distributionRepository: SaccoDividendDistributionRepository,
    private val payoutRepository: SaccoDividendPayoutRepository,
    private val accountRepository: AccountRepository,
    private val userRepository: UserRepository,
    private val ledgerService: LedgerService,
    private val rateLimiter: RateLimiter,
    private val accountNumberGenerator: AccountNumberGenerator,
) {
    private val log = LoggerFactory.getLogger(SaccoService::class.java)

    companion object {
        // Reuses SavingsService's own real 7.5% annual savings-goal rate as the SACCO's
        // declared annual dividend rate, rather than fabricating a new number --
        // prorated by real elapsed days at distribution time, not paid in full
        // annually regardless of how often declareDividend is actually called.
        val ANNUAL_DIVIDEND_RATE: BigDecimal = BigDecimal("0.075")
    }

    @Transactional
    fun getOrCreatePoolAccount(): Account =
        accountRepository.findByUserIdAndType(SACCO_POOL_USER_ID, AccountType.GROUP)
            ?: accountRepository.save(
                Account(
                    id = "account_${UUID.randomUUID()}",
                    userId = SACCO_POOL_USER_ID,
                    accountNumber = accountNumberGenerator.generate(2024300000L),
                    accountName = "itunda SACCO pool",
                    type = AccountType.GROUP,
                    balance = BigDecimal.ZERO,
                    availableBalance = BigDecimal.ZERO,
                ),
            )

    private fun getOrCreateShareholding(userId: String): SaccoShareholding {
        shareholdingRepository.findByUserId(userId)?.let { return it }
        userRepository.findById(userId).orElseThrow { SaccoNoAccountException("Account not found") }
        val account = accountRepository.findByUserIdAndType(userId, AccountType.MAIN)
            ?: throw SaccoNoAccountException("No account found for this account")
        return shareholdingRepository.save(
            SaccoShareholding(id = "saccoshare_${UUID.randomUUID()}", userId = userId, accountId = account.id),
        )
    }

    @Transactional
    fun buyShares(userId: String, amount: BigDecimal): SaccoShareholdingView {
        require(amount > BigDecimal.ZERO) { "Contribution amount must be greater than zero" }
        rateLimiter.checkLimit("sacco:buy:$userId", limit = 20, window = Duration.ofHours(1))

        val shareholding = getOrCreateShareholding(userId)
        val memberAccount = accountRepository.findById(shareholding.accountId).orElseThrow { SaccoNoAccountException("Account not found") }
        val poolAccount = getOrCreatePoolAccount()

        ledgerService.postLedgerTransaction(
            memberAccount.currency,
            listOf(
                LedgerLeg(memberAccount.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, amount, "SACCO shares purchased"),
                LedgerLeg(poolAccount.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, amount, "SACCO shares purchased"),
            ),
        )
        // Shares mint 1:1 with contribution -- the simplest honest model, no
        // speculative share-price fluctuation (see SaccoShareholding.kt's own
        // doc comment for why).
        shareholding.sharesHeld = shareholding.sharesHeld.add(amount)
        shareholding.totalContributed = shareholding.totalContributed.add(amount)
        val saved = shareholdingRepository.save(shareholding)
        return SaccoShareholdingView(saved, saved.sharesHeld)
    }

    @Transactional
    fun redeemShares(userId: String, amount: BigDecimal): SaccoShareholdingView {
        require(amount > BigDecimal.ZERO) { "Redemption amount must be greater than zero" }
        rateLimiter.checkLimit("sacco:redeem:$userId", limit = 20, window = Duration.ofHours(1))

        val shareholding = shareholdingRepository.findByUserId(userId)
            ?: throw SaccoNoShareholdingException("You have no SACCO shares to redeem")
        if (shareholding.sharesHeld < amount) {
            throw SaccoInsufficientSharesException("Insufficient shares -- you hold ${shareholding.sharesHeld}")
        }
        val memberAccount = accountRepository.findById(shareholding.accountId).orElseThrow { SaccoNoAccountException("Account not found") }
        val poolAccount = getOrCreatePoolAccount()

        // Redeem at par (the original contributed value) -- dividend GAINS are
        // distributed separately via declareDividend, not baked into a fluctuating
        // share price. See SaccoShareholding.kt's own doc comment.
        ledgerService.postLedgerTransaction(
            memberAccount.currency,
            listOf(
                LedgerLeg(poolAccount.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, amount, "SACCO shares redeemed"),
                LedgerLeg(memberAccount.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, amount, "SACCO shares redeemed"),
            ),
        )
        shareholding.sharesHeld = shareholding.sharesHeld.subtract(amount)
        shareholding.totalContributed = shareholding.totalContributed.subtract(amount).max(BigDecimal.ZERO)
        val saved = shareholdingRepository.save(shareholding)
        return SaccoShareholdingView(saved, saved.sharesHeld)
    }

    fun getMyShareholding(userId: String): SaccoShareholdingView? {
        val shareholding = shareholdingRepository.findByUserId(userId) ?: return null
        return SaccoShareholdingView(shareholding, shareholding.sharesHeld)
    }

    fun getMyDividendHistory(userId: String): List<SaccoDividendPayout> {
        val shareholding = shareholdingRepository.findByUserId(userId) ?: return emptyList()
        return payoutRepository.findByShareholdingIdOrderByCreatedAtDesc(shareholding.id)
    }

    /**
     * Real periodic dividend declaration -- computes a real rate (this SACCO's own
     * declared `ANNUAL_DIVIDEND_RATE`, prorated by the real elapsed days since the
     * last distribution, or since the pool's own creation if this is the first one)
     * and distributes it pro-rata across every real shareholder by
     * `sharesHeld / totalSharesOutstanding`. Real-throws if there are no shares
     * outstanding yet rather than silently no-op'ing on an empty pool.
     *
     * Real bug caught during this feature's own build-time review (2026-08-02), before
     * it ever shipped: the first draft paid every dividend straight out of the pool
     * account -- which is funded ONLY by members' own 1:1 share purchases (`buyShares`)
     * and pays out redemptions 1:1 at par (`redeemShares`). Since `sharesHeld` (a
     * member's redemption entitlement) is never reduced by a dividend payout, repeatedly
     * draining the pool to pay dividends would silently push `poolAccount.balance` below
     * `sum(sharesHeld)` -- a real, growing insolvency where the SACCO promises
     * redemption at par but can no longer honor it for every member. Fixed by funding
     * dividends from `INTEREST_EXPENSE` instead, the exact same real "itunda pays this
     * out of its own P&L, not out of other members' principal" shape
     * `SavingsService.claimInterest`/`WeeklySavingsService`/`UpfrontInterestDepositService`
     * already establish for every other yield-paying feature in this backend -- the pool
     * account is now only ever debited by a real member `redeemShares` call, keeping
     * `poolAccount.balance == sum(sharesHeld)` a true invariant at all times.
     *
     * Real fix (found via project_itunda_zero_test_coverage_sweep's scheduler audit,
     * broadened past @Scheduled classes to any batch-over-independent-rows loop,
     * 2026-09-05): this whole method is `@Transactional`, and the per-shareholder
     * loop below had no try/catch -- a single bad shareholding (a since-deleted
     * account) would throw uncaught, and since the exception propagates out of this
     * `@Transactional` method, Spring rolls back the ENTIRE transaction -- undoing
     * the ledger postings already made to every OTHER, perfectly good shareholder in
     * the same run, and leaving the whole SACCO's dividend undeclared for that
     * period. This is more severe than the equivalent scheduler bug class (each
     * scheduler's own per-item delegate method has its OWN separate transaction, so
     * a poisoned item there only blocks the REST of that tick's batch, not rows
     * already committed) -- one bad shareholder here blocked dividends for everyone.
     * Fixed with a per-shareholder try/catch: since the exception is now caught
     * INSIDE this transactional method rather than propagating out, Spring commits
     * the transaction normally at the end, with only the failed shareholder skipped
     * (and logged) rather than the whole distribution rolled back.
     *
     * Real proactive hardening (2026-09-05, applying this session's own "@Version /
     * a real lock from day one, don't wait to find the race live" precedent -- see
     * Cooperative.kt's own doc comment for where that precedent was set): `declareDividend`
     * has no real caller anywhere in this codebase yet (confirmed via a repo-wide
     * grep -- `GET /dividends/me` reads dividend HISTORY, but nothing ever creates
     * one; a real, disclosed, half-built-feature gap, not something this pass wires
     * up unilaterally -- WHEN a cooperative declares a dividend is a governance
     * decision, not an engineering one). But the classic TOCTOU this method's own
     * shape invites was worth closing now regardless of who eventually calls it:
     * two concurrent `declareDividend()` calls would both read the SAME
     * `lastDistribution` (neither has committed yet), compute the SAME period/rate,
     * and both pay out a FULL dividend to every shareholder -- a real double-pay.
     * Fixed by re-fetching the pool account via a real row lock
     * (`AccountRepository.findByIdForUpdate`) before reading `lastDistribution`: a
     * second concurrent call now blocks until the first's transaction commits, then
     * correctly sees the first's already-saved distribution on its own fresh read.
     */
    @Transactional
    fun declareDividend(): SaccoDividendDistribution {
        val allShareholdings = shareholdingRepository.findAll().filter { it.sharesHeld > BigDecimal.ZERO }
        val totalShares = allShareholdings.fold(BigDecimal.ZERO) { acc, s -> acc.add(s.sharesHeld) }
        if (totalShares <= BigDecimal.ZERO) {
            throw SaccoNoSharesOutstandingException("No SACCO shares outstanding -- nothing to declare a dividend on")
        }
        val unlockedPoolAccount = getOrCreatePoolAccount()
        val poolAccount = accountRepository.findByIdForUpdate(unlockedPoolAccount.id).orElseThrow { SaccoNoAccountException("Account not found") }

        val lastDistribution = distributionRepository.findAllByOrderByDistributionDateDesc().firstOrNull()
        val periodStart = lastDistribution?.distributionDate ?: poolAccount.createdAt
        val elapsedDays = ChronoUnit.DAYS.between(periodStart, Instant.now()).coerceAtLeast(1)
        val periodRate = ANNUAL_DIVIDEND_RATE.multiply(BigDecimal(elapsedDays)).divide(BigDecimal(365), 10, RoundingMode.HALF_UP)

        val distribution = distributionRepository.save(
            SaccoDividendDistribution(
                id = "saccodist_${UUID.randomUUID()}", totalPoolValue = poolAccount.balance,
                dividendRate = periodRate, totalDividendPaid = BigDecimal.ZERO,
            ),
        )

        var totalPaid = BigDecimal.ZERO
        allShareholdings.forEach { shareholding ->
            val payoutAmount = shareholding.sharesHeld.multiply(periodRate).setScale(2, RoundingMode.HALF_UP)
            if (payoutAmount <= BigDecimal.ZERO) return@forEach
            try {
                val memberAccount = accountRepository.findById(shareholding.accountId).orElseThrow { SaccoNoAccountException("Account not found") }
                val result = ledgerService.postLedgerTransaction(
                    memberAccount.currency,
                    listOf(
                        LedgerLeg("interest_expense", LedgerAccountType.INTEREST_EXPENSE, LedgerDirection.DEBIT, payoutAmount, "SACCO dividend"),
                        LedgerLeg(memberAccount.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, payoutAmount, "SACCO dividend"),
                    ),
                )
                payoutRepository.save(
                    SaccoDividendPayout(
                        id = "saccopayout_${UUID.randomUUID()}", distributionId = distribution.id, shareholdingId = shareholding.id,
                        amount = payoutAmount, payoutTransactionId = result.transactionId,
                    ),
                )
                totalPaid = totalPaid.add(payoutAmount)
            } catch (e: Exception) {
                log.error("SACCO dividend payout failed for shareholding {}", shareholding.id, e)
            }
        }
        distribution.totalDividendPaid = totalPaid
        return distributionRepository.save(distribution)
    }
}
