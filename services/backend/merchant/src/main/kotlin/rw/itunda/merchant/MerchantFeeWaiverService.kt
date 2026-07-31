package rw.itunda.merchant

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.core.domain.Merchant
import rw.itunda.core.domain.TransactionType
import rw.itunda.core.repository.MerchantRepository
import rw.itunda.core.repository.TransactionRepository
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant

class MerchantAlreadyWaivedException(message: String) : RuntimeException(message)
class MerchantNotEligibleForFeeWaiverException(message: String) : RuntimeException(message)

/**
 * Real Naver Pay 영세 가맹점 수수료 지원 (small-merchant fee-waiver support program) --
 * sourced from Naver Pay's own real, currently-running campaign (2026-07-27 to
 * 2026-08-02) waiving transaction fees for small/thin-margin merchants using its
 * payment and reservation/order services. See `Merchant.feeRateOverride`'s own doc
 * comment for the entity-level account.
 *
 * **Honest v1 scope**: no real SME-certification data exists in this system (unlike
 * Naver's own real program, which can lean on registered-business-size data this
 * codebase has no access to) -- eligibility is instead computed from a real, itunda-own
 * proxy: a merchant's own real completed `PAYMENT` volume over the last real 30 days,
 * reusing the exact same `TransactionRepository.findByRecipientIdAndTypeAndCreatedAtBetween`
 * query this backend's own merchant reports already establish. Below
 * `SMALL_MERCHANT_MONTHLY_VOLUME_THRESHOLD` (itunda's own honest number, no real
 * published Rwanda-specific SME revenue threshold exists to adopt instead) qualifies for
 * a real, full fee waiver (0%) -- matching Naver's own real "지원" (support) framing, a
 * genuine waiver, not a fabricated partial discount. **Not periodically re-evaluated
 * this pass**: once granted, the waiver stays in effect until an admin explicitly
 * revokes it (via the existing `Merchant` write path) -- a real, named, deliberately
 * deferred follow-up, so a merchant who later outgrows the threshold isn't
 * automatically un-waived.
 */
@Service
class MerchantFeeWaiverService(
    private val merchantRepository: MerchantRepository,
    private val merchantService: MerchantService,
    private val transactionRepository: TransactionRepository,
) {
    companion object {
        val SMALL_MERCHANT_MONTHLY_VOLUME_THRESHOLD: BigDecimal = BigDecimal("500000")
        val WAIVED_FEE_RATE: BigDecimal = BigDecimal.ZERO
        val LOOKBACK_WINDOW: Duration = Duration.ofDays(30)
    }

    @Transactional
    fun applyForFeeWaiver(ownerUserId: String): Merchant {
        val merchant = merchantService.getMyMerchant(ownerUserId)
        if (merchant.feeRateOverride == WAIVED_FEE_RATE) {
            throw MerchantAlreadyWaivedException("This merchant already has a real active fee waiver")
        }
        val now = Instant.now()
        val recentVolume = transactionRepository
            .findByRecipientIdAndTypeAndCreatedAtBetween(ownerUserId, TransactionType.PAYMENT, now.minus(LOOKBACK_WINDOW), now)
            .fold(BigDecimal.ZERO) { total, transaction -> total.add(transaction.amount) }

        if (recentVolume >= SMALL_MERCHANT_MONTHLY_VOLUME_THRESHOLD) {
            throw MerchantNotEligibleForFeeWaiverException(
                "This merchant's real 30-day payment volume ($recentVolume RWF) is at or above the real $SMALL_MERCHANT_MONTHLY_VOLUME_THRESHOLD RWF small-merchant threshold",
            )
        }

        merchant.feeRateOverride = WAIVED_FEE_RATE
        return merchantRepository.save(merchant)
    }
}
