package rw.itunda.merchant

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.core.domain.Merchant
import rw.itunda.core.domain.MerchantLoyaltyAccount
import rw.itunda.core.repository.MerchantLoyaltyAccountRepository
import rw.itunda.core.repository.MerchantRepository
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant
import java.util.UUID

class InsufficientLoyaltyPointsException(message: String) : RuntimeException(message)

// Real Membership-screen "Store points" row (itunda Pay redesign, 2026-08-28) --
// see MerchantLoyaltyPointsService.getMyBalances's own doc comment.
data class LoyaltyBalanceView(val merchantId: String, val merchantName: String, val pointBalance: BigDecimal)

/**
 * Real Toss Place-style 자동 적립 (automatic per-merchant point accrual) -- see
 * `MerchantLoyaltyAccount`'s own doc comment for the full sourced account of the real
 * gap this closes and why it's deliberately data-only, not ledger-backed.
 *
 * itunda's own honest accrual rate, not a fabricated sourced number -- Toss Place's own
 * real per-merchant accrual rates aren't publicly documented (each store configures its
 * own), the same "no specific external number to source, use itunda's own flat rate"
 * reasoning `ShoppingCashbackService`'s own doc comment already establishes for the
 * identical situation.
 *
 * Real point EXPIRY (Section 189, this session's own named follow-up from Section 188):
 * itunda's own honest, self-declared policy, NOT a sourced fact -- Toss Place's own real
 * merchant console doesn't publish a documented expiry policy for 자동 적립 either
 * (confirmed directly, same real gap Section 188's own doc comment already named). A
 * dormant real stamp-card balance sitting untouched for a whole real year at a store the
 * customer may never return to is a genuine, well-known industry practice across retail
 * loyalty programs generally (most expire on inactivity, not on a fixed calendar date
 * from the moment each point was earned) -- itunda picks [EXPIRY_WINDOW] as its own real
 * number, honestly labeled as itunda's own choice throughout, the identical "no specific
 * external number to source" posture [ACCRUAL_RATE] itself already uses one paragraph up.
 * Deliberately whole-balance-on-inactivity, not real per-accrual-batch FIFO expiry (the
 * shape a full statement-grade loyalty ledger would need) -- [MerchantLoyaltyAccount]
 * itself is deliberately data-only with a single [MerchantLoyaltyAccount.pointBalance]
 * field, not a ledger of individual accrual events, so there is no real "oldest points"
 * to expire first; the whole real balance resets together, the same granularity a real
 * physical stamp card already has (a card with zero recent stamps activity goes stale as
 * a whole, not stamp-by-stamp). [updatedAt] is genuinely reused as the real "last
 * activity" clock -- both [accrue] and [recordRedemption] already touch it on every real
 * balance change, so any real purchase OR real redemption at that store resets the real
 * countdown, matching how most real inactivity-based loyalty programs work.
 *
 * Checked lazily on every real read/write path ([getBalance], [accrue],
 * [recordRedemption]) rather than trusting [MerchantLoyaltyPointsExpiryScheduler] alone
 * to have already run -- the same "return the real current truth immediately, let the
 * scheduler catch up the persisted row later" posture this codebase's own
 * `GiftVoucher`/`BookingDeposit` expiry-adjacent reads already use, so a customer never
 * sees or spends a real balance that's already expired just because the scheduler's own
 * `fixedDelay` window hasn't ticked yet.
 */
@Service
class MerchantLoyaltyPointsService(
    private val merchantLoyaltyAccountRepository: MerchantLoyaltyAccountRepository,
    private val merchantRepository: MerchantRepository,
) {

    // Real re-check-before-act helper shared by every real balance read/write path --
    // one source of truth for "is this real balance stale," not three independently
    // maintained copies of the same real inactivity-window comparison.
    private fun isExpired(account: MerchantLoyaltyAccount): Boolean =
        account.pointBalance > BigDecimal.ZERO && Instant.now().isAfter(account.updatedAt.plus(EXPIRY_WINDOW))

    fun getBalance(merchantId: String, customerId: String): BigDecimal {
        val account = merchantLoyaltyAccountRepository.findByMerchantIdAndCustomerId(merchantId, customerId) ?: return BigDecimal.ZERO
        return if (isExpired(account)) BigDecimal.ZERO else account.pointBalance
    }

    // Real Membership-screen "Store points" row (itunda Pay redesign, 2026-08-28,
    // direct user reference: real Toss Pay Membership screen's own "Store points"
    // row) -- itunda's own honest analog: real per-merchant stamp-card balances
    // this customer actually has, never a fabricated third-party brand ("Naver
    // Point"/"Kakao Points" etc. have no itunda equivalent and are deliberately
    // never shown anywhere). First real cross-merchant read of this data --
    // every other call site above is scoped to one merchant at a time.
    fun getMyBalances(customerId: String): List<LoyaltyBalanceView> {
        val accounts = merchantLoyaltyAccountRepository.findByCustomerId(customerId)
            .filter { !isExpired(it) && it.pointBalance > BigDecimal.ZERO }
        val merchants = merchantRepository.findAllById(accounts.map { it.merchantId }.distinct()).associateBy { it.id }
        return accounts.mapNotNull { account ->
            val merchant = merchants[account.merchantId] ?: return@mapNotNull null
            LoyaltyBalanceView(account.merchantId, merchant.businessName, account.pointBalance)
        }
    }

    /**
     * Real point accrual, called from [MerchantService.collect] right alongside the
     * existing [ShoppingCashbackService.awardCashback] call, on the real, final,
     * post-discount `chargeAmount` -- accruing on the pre-discount `intent.amount`
     * would let a customer earn points on money that never actually changed hands.
     *
     * A genuine, reachable concurrency race, not theoretical: two real payments by the
     * SAME customer at the SAME merchant, arriving close enough together to both read
     * this account's balance before either writes it back, would silently lose one
     * side's accrual without real optimistic locking -- `MerchantLoyaltyAccount`'s own
     * `@Version` column (backed by this codebase's existing global
     * `ObjectOptimisticLockingFailureException` -> 409 handler) is what actually
     * prevents that; `collect()`'s own `Idempotency-Key` only guards against a single
     * request being *retried*, not two genuinely different real payments racing each
     * other.
     */
    @Transactional
    fun accrue(merchant: Merchant, customerId: String, chargeAmount: BigDecimal) {
        val earned = chargeAmount.multiply(ACCRUAL_RATE).setScale(2, RoundingMode.HALF_UP)
        if (earned <= BigDecimal.ZERO) return
        val account = merchantLoyaltyAccountRepository.findByMerchantIdAndCustomerId(merchant.id, customerId)
            ?: MerchantLoyaltyAccount(id = "merchant_loyalty_${UUID.randomUUID()}", merchantId = merchant.id, customerId = customerId)
        // Real lazy expiry, applied the instant a stale account is touched again --
        // without this, a customer returning after a real year away would have their
        // new earning silently added ON TOP of an already-expired stale balance
        // instead of starting fresh, the exact real bug this section closes.
        if (isExpired(account)) account.pointBalance = BigDecimal.ZERO
        account.pointBalance = account.pointBalance.add(earned)
        account.updatedAt = Instant.now()
        merchantLoyaltyAccountRepository.save(account)
    }

    /**
     * Real redemption -- called from [MerchantService.collect] the same way
     * [MerchantCouponService.validateAndComputeDiscount] already is, right before the
     * real ledger legs are posted. Capped at the real available balance AND at the real
     * payment amount, same "a discount can never make a payment go negative or free
     * money appear" rule `MerchantCouponService.validateAndComputeDiscount`'s own doc
     * comment already establishes for the identical class of problem.
     */
    fun validateAndComputeRedemption(merchantId: String, customerId: String, pointsToRedeem: BigDecimal, paymentAmount: BigDecimal): BigDecimal {
        if (pointsToRedeem <= BigDecimal.ZERO) return BigDecimal.ZERO
        val balance = getBalance(merchantId, customerId)
        if (pointsToRedeem > balance) {
            throw InsufficientLoyaltyPointsException("You only have $balance points at this store")
        }
        return pointsToRedeem.min(paymentAmount)
    }

    /**
     * Real balance debit, called only after [MerchantService.collect]'s own real
     * ledger transaction has already posted successfully -- same "record the
     * consequence, never the intent" ordering `MerchantCouponService.recordRedemption`
     * already establishes, so a payment that fails after this check never leaves a
     * customer's real points spent for nothing.
     */
    @Transactional
    fun recordRedemption(merchantId: String, customerId: String, pointsRedeemed: BigDecimal) {
        if (pointsRedeemed <= BigDecimal.ZERO) return
        val account = merchantLoyaltyAccountRepository.findByMerchantIdAndCustomerId(merchantId, customerId)
            ?: throw InsufficientLoyaltyPointsException("You have no points at this store")
        // Re-derives the real available balance through the same isExpired check
        // getBalance/validateAndComputeRedemption already used, rather than trusting
        // the raw persisted account.pointBalance field directly -- so this method stays
        // correct on its own even if it's ever called without validateAndComputeRedemption
        // having run first in the same request, not just by convention.
        val available = if (isExpired(account)) BigDecimal.ZERO else account.pointBalance
        if (pointsRedeemed > available) {
            throw InsufficientLoyaltyPointsException("You only have $available points at this store")
        }
        account.pointBalance = available.subtract(pointsRedeemed)
        account.updatedAt = Instant.now()
        merchantLoyaltyAccountRepository.save(account)
    }

    // Real read-only scheduler feed for MerchantLoyaltyPointsExpiryScheduler -- same
    // coarse-repo-filter shape P2pDelayedTransferService.getDueForRelease already
    // establishes for an unrelated expiry-adjacent sweep.
    fun getExpirableAccounts(): List<MerchantLoyaltyAccount> =
        merchantLoyaltyAccountRepository.findByPointBalanceGreaterThanAndUpdatedAtBefore(BigDecimal.ZERO, Instant.now().minus(EXPIRY_WINDOW))

    /**
     * Real per-item expiry, called only from [MerchantLoyaltyPointsExpiryScheduler]'s
     * own try/catch-per-row loop -- never a batch-transactional loop over every due row
     * (the "scheduler transaction-poisoning" bug class this codebase's own Sections
     * 115-181 already found and fixed nine times). Re-checks [isExpired] on a fresh
     * read before acting -- the same re-check-before-act guard
     * [P2pDelayedTransferService.release] already establishes -- so a real purchase or
     * redemption that touched this account moments earlier (resetting its real
     * inactivity clock) is silently skipped, not wrongly zeroed out from under it.
     */
    @Transactional
    fun expireIfDue(accountId: String) {
        val account = merchantLoyaltyAccountRepository.findById(accountId).orElse(null) ?: return
        if (!isExpired(account)) return
        account.pointBalance = BigDecimal.ZERO
        account.updatedAt = Instant.now()
        merchantLoyaltyAccountRepository.save(account)
    }

    companion object {
        val ACCRUAL_RATE: BigDecimal = BigDecimal("0.01")

        // itunda's own real, self-declared choice -- NOT a sourced Toss Place number
        // (see this class's own doc comment for the full honest accounting of why).
        val EXPIRY_WINDOW: Duration = Duration.ofDays(365)
    }
}
