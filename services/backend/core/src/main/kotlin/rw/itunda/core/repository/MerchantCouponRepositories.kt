package rw.itunda.core.repository

import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.MerchantCoupon
import rw.itunda.core.domain.MerchantCouponRedemption

interface MerchantCouponRepository : JpaRepository<MerchantCoupon, String> {
    fun findByMerchantIdOrderByCreatedAtDesc(merchantId: String): List<MerchantCoupon>
    fun findByMerchantIdAndActiveTrueOrderByCreatedAtDesc(merchantId: String): List<MerchantCoupon>
}

interface MerchantCouponRedemptionRepository : JpaRepository<MerchantCouponRedemption, String> {
    fun existsByCouponIdAndCustomerId(couponId: String, customerId: String): Boolean
    fun findByCustomerIdOrderByRedeemedAtDesc(customerId: String): List<MerchantCouponRedemption>
}
