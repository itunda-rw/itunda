package rw.itunda.core.repository

import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.MerchantCoupon
import rw.itunda.core.domain.MerchantCouponRedemption

interface MerchantCouponRepository : JpaRepository<MerchantCoupon, String> {
    fun findByMerchantIdOrderByCreatedAtDesc(merchantId: String): List<MerchantCoupon>
    fun findByMerchantIdAndActiveTrueOrderByCreatedAtDesc(merchantId: String): List<MerchantCoupon>
    fun findByActiveTrueAndExpiryReminderSentAtIsNull(): List<MerchantCoupon>

    // Real cross-merchant "Coupon box" browse (itunda Pay redesign, 2026-08-28) --
    // every other query above is scoped to one merchant at a time; this is the first
    // real unscoped-across-every-merchant read, backing MerchantCouponService.browseCoupons.
    fun findByActiveTrueOrderByCreatedAtDesc(): List<MerchantCoupon>
}

interface MerchantCouponRedemptionRepository : JpaRepository<MerchantCouponRedemption, String> {
    fun existsByCouponIdAndCustomerId(couponId: String, customerId: String): Boolean
    fun findByCustomerIdOrderByRedeemedAtDesc(customerId: String): List<MerchantCouponRedemption>
}
