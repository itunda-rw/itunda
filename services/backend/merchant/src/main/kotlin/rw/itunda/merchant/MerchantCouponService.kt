package rw.itunda.merchant

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.core.domain.CouponDiscountType
import rw.itunda.core.domain.MerchantCoupon
import rw.itunda.core.domain.MerchantCouponRedemption
import rw.itunda.core.domain.Notification
import rw.itunda.core.domain.TransactionStatus
import rw.itunda.core.domain.TransactionType
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.MerchantCouponRedemptionRepository
import rw.itunda.core.repository.MerchantCouponRepository
import rw.itunda.core.repository.MerchantRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.TransactionRepository
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant
import java.util.UUID

class InvalidCouponException(message: String) : RuntimeException(message)
class CouponNotFoundException(message: String) : RuntimeException(message)
class CouponNotEligibleException(message: String) : RuntimeException(message)
class CouponAlreadyRedeemedException(message: String) : RuntimeException(message)

data class CouponView(val coupon: MerchantCoupon, val eligible: Boolean, val alreadyRedeemed: Boolean)

/**
 * Real merchant coupons + 단골 (regular customer) loyalty gating -- see
 * `MerchantCoupon`'s own doc comment for the full account, including why eligibility is
 * computed from real completed payments rather than bookings.
 */
@Service
class MerchantCouponService(
    private val merchantRepository: MerchantRepository,
    private val merchantCouponRepository: MerchantCouponRepository,
    private val merchantCouponRedemptionRepository: MerchantCouponRedemptionRepository,
    private val transactionRepository: TransactionRepository,
    private val notificationRepository: NotificationRepository,
    private val pushNotificationService: PushNotificationService,
) {
    companion object {
        // itunda's own honest scoping choice -- see MerchantCoupon.kt's own doc comment.
        const val REGULAR_CUSTOMER_THRESHOLD = 3L

        // Real merchant-console expiry-reminder window -- see
        // MerchantCoupon.expiryReminderSentAt's own doc comment for the real sourcing.
        // A coupon's real lifespan is typically much shorter than an insurance policy or
        // certificate, so a shorter honest window (itunda's own scoping choice, no exact
        // real number was published) than InsurancePolicy's 30 days/Certificate's 60.
        val EXPIRY_REMINDER_WINDOW: Duration = Duration.ofDays(3)
    }

    private fun getMyMerchant(ownerUserId: String) =
        merchantRepository.findByOwnerUserId(ownerUserId)
            ?: throw MerchantNotFoundException("This account is not registered as a merchant")

    fun isRegularCustomer(merchantOwnerUserId: String, customerId: String): Boolean =
        transactionRepository.countBySenderIdAndRecipientIdAndTypeAndStatus(
            customerId, merchantOwnerUserId, TransactionType.PAYMENT, TransactionStatus.COMPLETED,
        ) >= REGULAR_CUSTOMER_THRESHOLD

    @Transactional
    fun createCoupon(
        ownerUserId: String,
        title: String,
        description: String?,
        discountType: CouponDiscountType,
        discountValue: BigDecimal,
        regularsOnly: Boolean,
        expiresAt: Instant?,
    ): MerchantCoupon {
        val merchant = getMyMerchant(ownerUserId)
        val trimmedTitle = title.trim()
        if (trimmedTitle.isEmpty() || trimmedTitle.length > 100) {
            throw InvalidCouponException("Title must be 1-100 characters")
        }
        when (discountType) {
            CouponDiscountType.PERCENT -> if (discountValue <= BigDecimal.ZERO || discountValue > BigDecimal(100)) {
                throw InvalidCouponException("A percent discount must be between 1 and 100")
            }
            CouponDiscountType.FIXED_AMOUNT -> if (discountValue <= BigDecimal.ZERO) {
                throw InvalidCouponException("A fixed discount amount must be positive")
            }
        }
        if (expiresAt != null && expiresAt.isBefore(Instant.now())) {
            throw InvalidCouponException("expiresAt must be in the future")
        }
        return merchantCouponRepository.save(
            MerchantCoupon(
                id = "coupon_${UUID.randomUUID()}", merchantId = merchant.id, title = trimmedTitle,
                description = description?.trim()?.ifBlank { null }?.take(500),
                discountType = discountType, discountValue = discountValue, regularsOnly = regularsOnly, expiresAt = expiresAt,
            ),
        )
    }

    fun getMyCoupons(ownerUserId: String): List<MerchantCoupon> {
        val merchant = getMyMerchant(ownerUserId)
        return merchantCouponRepository.findByMerchantIdOrderByCreatedAtDesc(merchant.id)
    }

    @Transactional
    fun deactivateCoupon(ownerUserId: String, couponId: String): MerchantCoupon {
        val merchant = getMyMerchant(ownerUserId)
        val coupon = merchantCouponRepository.findById(couponId).orElseThrow { CouponNotFoundException("Coupon not found") }
        if (coupon.merchantId != merchant.id) throw CouponNotFoundException("Coupon not found")
        coupon.active = false
        return merchantCouponRepository.save(coupon)
    }

    // Real customer-facing coupon list -- annotates each real coupon with this specific
    // customer's real eligibility/redemption state rather than a flat, undifferentiated
    // catalog (a regulars-only coupon shown to a first-time customer as claimable would
    // be misleading, not honest).
    fun getCouponsForCustomer(merchantId: String, customerId: String): List<CouponView> {
        val merchant = merchantRepository.findById(merchantId).orElseThrow { MerchantNotFoundException("Merchant not found") }
        val now = Instant.now()
        return merchantCouponRepository.findByMerchantIdAndActiveTrueOrderByCreatedAtDesc(merchantId)
            .filter { coupon -> coupon.expiresAt.let { it == null || it.isAfter(now) } }
            .map { coupon ->
                val alreadyRedeemed = merchantCouponRedemptionRepository.existsByCouponIdAndCustomerId(coupon.id, customerId)
                val eligible = !coupon.regularsOnly || isRegularCustomer(merchant.ownerUserId, customerId)
                CouponView(coupon, eligible, alreadyRedeemed)
            }
    }

    // Real discount computation + validation, called from MerchantService.collect right
    // before it posts the payment's ledger legs -- see that method's own doc comment on
    // why this must throw on an invalid coupon rather than silently ignore it (unlike
    // RoundUpService's own auxiliary, never-block discipline: this directly changes the
    // amount actually charged, so a customer who submitted a couponId must get either the
    // real discount or a real error, never a silent full-price charge).
    fun validateAndComputeDiscount(merchant: rw.itunda.core.domain.Merchant, customerId: String, couponId: String, paymentAmount: BigDecimal): BigDecimal {
        val coupon = merchantCouponRepository.findById(couponId).orElseThrow { CouponNotFoundException("Coupon not found") }
        if (coupon.merchantId != merchant.id) throw CouponNotFoundException("Coupon not found")
        if (!coupon.active) throw CouponNotEligibleException("This coupon is no longer active")
        val expiresAt = coupon.expiresAt
        if (expiresAt != null && expiresAt.isBefore(Instant.now())) {
            throw CouponNotEligibleException("This coupon has expired")
        }
        if (coupon.regularsOnly && !isRegularCustomer(merchant.ownerUserId, customerId)) {
            throw CouponNotEligibleException("This coupon is reserved for regular customers")
        }
        if (merchantCouponRedemptionRepository.existsByCouponIdAndCustomerId(coupon.id, customerId)) {
            throw CouponAlreadyRedeemedException("You've already used this coupon")
        }
        val rawDiscount = when (coupon.discountType) {
            CouponDiscountType.PERCENT -> paymentAmount.multiply(coupon.discountValue).divide(BigDecimal(100), 2, RoundingMode.HALF_UP)
            CouponDiscountType.FIXED_AMOUNT -> coupon.discountValue
        }
        // Capped at the payment amount -- a coupon can never make a payment go negative
        // or free money appear.
        return rawDiscount.min(paymentAmount)
    }

    @Transactional
    fun recordRedemption(merchant: rw.itunda.core.domain.Merchant, customerId: String, couponId: String, transactionId: String, discountAmount: BigDecimal) {
        merchantCouponRedemptionRepository.save(
            MerchantCouponRedemption(
                id = "coupon_redemption_${UUID.randomUUID()}", couponId = couponId, merchantId = merchant.id,
                customerId = customerId, transactionId = transactionId, discountAmount = discountAmount,
            ),
        )
    }

    // Real "date field with no reminder" gap -- see MerchantCoupon.expiryReminderSentAt's
    // own doc comment for the real sourcing. Same shape as
    // CertificateService.getCertificatesDueForRenewalReminder/InsurancePolicy.endDate.
    fun getCouponsDueForExpiryReminder(): List<MerchantCoupon> {
        val cutoff = Instant.now().plus(EXPIRY_REMINDER_WINDOW)
        return merchantCouponRepository.findByActiveTrueAndExpiryReminderSentAtIsNull()
            .filter { val expiresAt = it.expiresAt; expiresAt != null && !expiresAt.isAfter(cutoff) }
    }

    /** One real expiry-reminder notification to the merchant owner, called per-coupon by
     * the scheduler -- re-checks `active`/`expiryReminderSentAt` right before sending so a
     * genuine race can't double-fire, same resilience discipline
     * CertificateService.sendRenewalReminder's own doc comment already establishes. */
    @Transactional
    fun sendExpiryReminder(couponId: String) {
        val coupon = merchantCouponRepository.findById(couponId).orElse(null) ?: return
        if (!coupon.active || coupon.expiryReminderSentAt != null) return
        val merchant = merchantRepository.findById(coupon.merchantId).orElse(null) ?: return

        val title = "Your coupon is expiring soon"
        val body = "Your coupon \"${coupon.title}\" for ${merchant.businessName} expires on ${coupon.expiresAt}. Extend or reissue it before then to keep offering it to customers."
        notificationRepository.save(
            Notification(
                id = "notif_${UUID.randomUUID()}", userId = merchant.ownerUserId, type = "MERCHANT_COUPON_EXPIRING_SOON",
                title = title, body = body, isRead = false, createdAt = Instant.now(), dataJson = "{\"couponId\":\"${coupon.id}\"}",
            ),
        )
        pushNotificationService.sendToUser(merchant.ownerUserId, title, body, mapOf("couponId" to coupon.id))
        coupon.expiryReminderSentAt = Instant.now()
        merchantCouponRepository.save(coupon)
    }
}
