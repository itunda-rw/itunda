package rw.itunda.merchant

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import rw.itunda.core.domain.CouponDiscountType
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import java.math.BigDecimal
import java.time.Instant

data class CreateCouponRequest(
    val title: String,
    val description: String? = null,
    val discountType: CouponDiscountType,
    val discountValue: BigDecimal,
    val regularsOnly: Boolean = false,
    val expiresAt: Instant? = null,
)

// Real merchant coupons + 단골 loyalty gating -- see MerchantCoupon.kt's own doc
// comment. Coupon creation/management isn't money-moving itself (no Idempotency-Key,
// same discipline MerchantBookingController already established for its own
// non-money-moving writes); actual redemption happens inside MerchantController.collect,
// which already requires one.
@RestController
@RequestMapping("/api/v1/merchant")
class MerchantCouponController(
    private val merchantCouponService: MerchantCouponService,
    private val merchantCouponExpiryReminderScheduler: MerchantCouponExpiryReminderScheduler,
    private val merchantLoyaltyPointsService: MerchantLoyaltyPointsService,
) {
    // Real merchant coupon expiry-reminder manual trigger -- same "expose the
    // scheduler's own real logic as a callable endpoint" convention
    // SavingsController/InsuranceController/CertificateController/GiftVoucherController
    // already establish, so a real coupon's real expiresAt can be verified without
    // waiting actual wall-clock days for it to enter the reminder window.
    // Real gap found live (2026-08-31, market-readiness audit): this fires the
    // reminder job for EVERY merchant's expiring coupons system-wide, yet had no ADMIN
    // gate -- any authenticated user could call it. ADMIN-gated the same
    // @PreAuthorize("hasRole('ADMIN')") way WeeklySavingsController.processDue already
    // is (this route doesn't live under /api/v1/system/**, so it doesn't inherit
    // SecurityConfig's blanket ADMIN gate there).
    @PostMapping("/coupons/process-expiry-reminders")
    @PreAuthorize("hasRole('ADMIN')")
    fun processExpiryReminders(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> {
        val processed = merchantCouponExpiryReminderScheduler.processDue()
        return ResponseEntity.ok(mapOf("success" to true, "processed" to processed))
    }

    @PostMapping("/coupons")
    fun createCoupon(
        @RequestBody request: CreateCouponRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val coupon = merchantCouponService.createCoupon(
            currentUser.userId, request.title, request.description, request.discountType,
            request.discountValue, request.regularsOnly, request.expiresAt,
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "coupon" to coupon))
    }

    @GetMapping("/coupons")
    fun getMyCoupons(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "coupons" to merchantCouponService.getMyCoupons(currentUser.userId)))

    @PostMapping("/coupons/{couponId}/deactivate")
    fun deactivateCoupon(
        @PathVariable couponId: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "coupon" to merchantCouponService.deactivateCoupon(currentUser.userId, couponId)))

    @GetMapping("/{merchantId}/coupons")
    fun getCouponsForCustomer(
        @PathVariable merchantId: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "coupons" to merchantCouponService.getCouponsForCustomer(merchantId, currentUser.userId)))

    // Real "Coupon box" browse (itunda Pay redesign, 2026-08-28) -- see
    // MerchantCouponService.browseCoupons's own doc comment.
    @GetMapping("/coupons/browse")
    fun browseCoupons(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "coupons" to merchantCouponService.browseCoupons(currentUser.userId)))

    @GetMapping("/coupons/my-redemptions")
    fun getMyRedemptions(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "redemptions" to merchantCouponService.getMyRedemptions(currentUser.userId)))

    // Real Membership-screen "Store points" row (itunda Pay redesign, 2026-08-28) --
    // see MerchantLoyaltyPointsService.getMyBalances's own doc comment. Lives on
    // this controller since it already owns customer-facing merchant-loyalty reads.
    @GetMapping("/loyalty/my-balances")
    fun getMyLoyaltyBalances(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> {
        val balances = merchantLoyaltyPointsService.getMyBalances(currentUser.userId)
        return ResponseEntity.ok(mapOf("success" to true, "balances" to balances, "total" to balances.fold(BigDecimal.ZERO) { acc, b -> acc + b.pointBalance }))
    }

    @ExceptionHandler(MerchantNotFoundException::class)
    fun handleMerchantNotFound(ex: MerchantNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("MERCHANT_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(InvalidCouponException::class)
    fun handleInvalidCoupon(ex: InvalidCouponException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_COUPON", ex.message ?: "Bad request"))

    @ExceptionHandler(CouponNotFoundException::class)
    fun handleCouponNotFound(ex: CouponNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("COUPON_NOT_FOUND", ex.message ?: "Not found"))
}
