package rw.itunda.merchant

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.CouponDiscountType
import rw.itunda.core.domain.MerchantCoupon
import rw.itunda.core.domain.MerchantCouponRedemption
import rw.itunda.core.domain.Notification
import rw.itunda.core.domain.TransactionStatus
import rw.itunda.core.domain.TransactionType
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.MerchantCouponRedemptionRepository
import rw.itunda.core.repository.MerchantCouponRepository
import rw.itunda.core.pricing.ReminderWindows
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

// Real "Coupon box" cross-merchant browse (itunda Pay redesign, 2026-08-28) -- see
// MerchantCouponService.browseCoupons's own doc comment. Carries merchantName
// (CouponView doesn't, since every existing CouponView call site is already scoped
// to one known merchant) since a cross-merchant list is meaningless without it.
data class CouponBrowseView(val coupon: MerchantCoupon, val merchantName: String, val eligible: Boolean, val alreadyRedeemed: Boolean)

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
    private val rateLimiter: RateLimiter,
) {
    companion object {
        // itunda's own honest scoping choice -- see MerchantCoupon.kt's own doc comment.
        const val REGULAR_CUSTOMER_THRESHOLD = 3L

        // Consolidated 2026-09-06 into core/pricing/ReminderWindows -- see its own doc
        // comment. A coupon's real lifespan is typically much shorter than an insurance
        // policy or certificate, so a shorter honest window than InsurancePolicy's
        // 30 days/Certificate's 60 -- those stay their own separate constants.
        val EXPIRY_REMINDER_WINDOW: Duration = ReminderWindows.PRE_EXPIRY_REMINDER_WINDOW
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
        // Real gap found live (2026-09-14, sibling-asymmetry sweep): this class had no
        // RateLimiter at all -- every structurally identical "post a listing" sibling
        // elsewhere in this codebase rate-limits (MarketplaceService.createListing,
        // GroupEatsOrderService.create), and BusService.postTrip's own doc comment
        // names this exact "spam-listing flood" vector as the reason it does too. These
        // coupons feed the real cross-merchant "Coupon box" browse (browseCoupons).
        rateLimiter.checkLimit("merchant:coupon-create:$ownerUserId", limit = 10, window = Duration.ofHours(1))
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
        val coupons = merchantCouponRepository.findByMerchantIdAndActiveTrueOrderByCreatedAtDesc(merchantId)
            .filter { coupon -> coupon.expiresAt.let { it == null || it.isAfter(now) } }
        // Real N+1 fix (2026-09-12) -- see MerchantCouponRedemptionRepository's own doc
        // comment on findByCouponIdInAndCustomerId. Also hoisted isRegularCustomer out of
        // the loop below: its arguments (merchant.ownerUserId, customerId) never vary
        // across a single merchant's coupon list, so the old per-coupon call was running
        // the exact same query over and over, not just an N+1 in the classic sense.
        val redeemedCouponIds = if (coupons.isEmpty()) {
            emptySet()
        } else {
            merchantCouponRedemptionRepository.findByCouponIdInAndCustomerId(coupons.map { it.id }, customerId)
                .map { it.couponId }.toSet()
        }
        val isRegular = isRegularCustomer(merchant.ownerUserId, customerId)
        return coupons.map { coupon ->
            val alreadyRedeemed = coupon.id in redeemedCouponIds
            val eligible = !coupon.regularsOnly || isRegular
            CouponView(coupon, eligible, alreadyRedeemed)
        }
    }

    // Real "Coupon box" cross-merchant browse (itunda Pay redesign, 2026-08-28,
    // direct user reference: real Toss Pay Coupon box screen). Every other
    // customer-facing coupon read above (getCouponsForCustomer) is scoped to one
    // already-known merchant -- this is the first real read across every merchant
    // at once, enriched with businessName (batch-fetched, same
    // ShoppingController.getEligibleMerchants N+1-avoidance pattern) since a
    // cross-merchant list is meaningless without knowing which store each coupon
    // is from. Deliberately no curated "brand campaign" tabs (Online/Offline/특가
    // in the real reference) -- itunda has no such marketing partnerships; this is
    // one flat, honest list of itunda's own real merchant coupons.
    fun browseCoupons(customerId: String): List<CouponBrowseView> {
        val now = Instant.now()
        val coupons = merchantCouponRepository.findByActiveTrueOrderByCreatedAtDesc()
            .filter { coupon -> coupon.expiresAt.let { it == null || it.isAfter(now) } }
        val merchants = merchantRepository.findAllById(coupons.map { it.merchantId }.distinct()).associateBy { it.id }
        // Real N+1 fix (2026-09-12) -- this method's own doc comment already brags about
        // batch-fetching `merchants` above to avoid an N+1 there, but left the identical
        // shape unbatched for both the redemption check (once per coupon) and
        // isRegularCustomer (once per coupon, keyed by merchant owner) right below --
        // both real, on a real, frequently-hit cross-merchant browse per that same doc
        // comment. Same batch-then-filter shape, using MerchantCouponRedemptionRepository
        // .findByCouponIdInAndCustomerId and TransactionRepository
        // .countBySenderIdAndRecipientIdInAndTypeAndStatus (a GROUP BY, matching
        // EatsFavoriteRepository.getFavoriteCounts's own real precedent for this exact
        // "per-owner count on a browse page" shape).
        val redeemedCouponIds = if (coupons.isEmpty()) {
            emptySet()
        } else {
            merchantCouponRedemptionRepository.findByCouponIdInAndCustomerId(coupons.map { it.id }, customerId)
                .map { it.couponId }.toSet()
        }
        val ownerUserIds = merchants.values.map { it.ownerUserId }.distinct()
        val regularCustomerOwnerIds = if (ownerUserIds.isEmpty()) {
            emptySet()
        } else {
            transactionRepository.countBySenderIdAndRecipientIdInAndTypeAndStatus(
                customerId, ownerUserIds, TransactionType.PAYMENT, TransactionStatus.COMPLETED,
            ).filter { it.count >= REGULAR_CUSTOMER_THRESHOLD }.map { it.recipientId }.toSet()
        }
        return coupons.mapNotNull { coupon ->
            val merchant = merchants[coupon.merchantId] ?: return@mapNotNull null
            val alreadyRedeemed = coupon.id in redeemedCouponIds
            val eligible = !coupon.regularsOnly || merchant.ownerUserId in regularCustomerOwnerIds
            CouponBrowseView(coupon, merchant.businessName, eligible, alreadyRedeemed)
        }
    }

    // Real Coupon box "Used/expired" tab -- itunda had zero controller endpoint
    // exposing this repository read anywhere before this (confirmed by grepping
    // every @*Mapping in this service/controller); redemptions were previously
    // only ever checked internally (existsByCouponIdAndCustomerId), never listed.
    fun getMyRedemptions(customerId: String): List<MerchantCouponRedemption> =
        merchantCouponRedemptionRepository.findByCustomerIdOrderByRedeemedAtDesc(customerId)

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
        coupon.expiryReminderSentAt = Instant.now()
        merchantCouponRepository.save(coupon)
        // Real fix (2026-09-13, push-before-commit ordering sweep): the push used to
        // fire BEFORE expiryReminderSentAt was saved -- a rollback after the push
        // would leave the flag unset and the next scheduler pass would resend it.
        sendExpiryPushAfterCommit(merchant.ownerUserId, title, body, coupon.id)
    }

    private fun sendExpiryPushAfterCommit(userId: String, title: String, body: String, couponId: String) {
        val send = { pushNotificationService.sendToUser(userId, title, body, mapOf("couponId" to couponId)) }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            send()
            return
        }
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() = send()
        })
    }
}
