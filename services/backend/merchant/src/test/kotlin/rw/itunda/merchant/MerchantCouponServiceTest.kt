package rw.itunda.merchant

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.core.domain.CouponDiscountType
import rw.itunda.core.domain.Merchant
import rw.itunda.core.domain.MerchantCoupon
import rw.itunda.core.domain.MerchantStatus
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.MerchantCouponRedemptionRepository
import rw.itunda.core.repository.MerchantCouponRepository
import rw.itunda.core.repository.MerchantRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.TransactionRepository
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Optional

class MerchantCouponServiceTest : BehaviorSpec({

    fun couponWithExpiry(id: String, merchantId: String, expiresAt: Instant?, active: Boolean = true, expiryReminderSentAt: Instant? = null) = MerchantCoupon(
        id = id, merchantId = merchantId, title = "10% off", discountType = CouponDiscountType.PERCENT,
        discountValue = BigDecimal.TEN, active = active, expiresAt = expiresAt, expiryReminderSentAt = expiryReminderSentAt,
    )

    Given("real active merchant coupons at various points in their real expiry-reminder window") {
        val merchantRepository = mockk<MerchantRepository>()
        val merchantCouponRepository = mockk<MerchantCouponRepository>()
        val merchantCouponRedemptionRepository = mockk<MerchantCouponRedemptionRepository>()
        val transactionRepository = mockk<TransactionRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = MerchantCouponService(
            merchantRepository, merchantCouponRepository, merchantCouponRedemptionRepository,
            transactionRepository, notificationRepository, pushNotificationService,
        )

        val merchant = Merchant(id = "merchant_1", ownerUserId = "seller_1", accountId = "account_1", businessName = "Kigali Coffee", status = MerchantStatus.ACTIVE)

        When("a real active coupon's real expiresAt is already inside the 3-day reminder window") {
            val soon = couponWithExpiry("coupon_soon", "merchant_1", Instant.now().plus(1, ChronoUnit.DAYS))
            every { merchantCouponRepository.findByActiveTrueAndExpiryReminderSentAtIsNull() } returns listOf(soon)

            Then("it is a real due candidate") {
                service.getCouponsDueForExpiryReminder().map { it.id } shouldBe listOf("coupon_soon")
            }
        }

        When("a real active coupon's real expiresAt is genuinely still outside the reminder window") {
            val far = couponWithExpiry("coupon_far", "merchant_1", Instant.now().plus(30, ChronoUnit.DAYS))
            every { merchantCouponRepository.findByActiveTrueAndExpiryReminderSentAtIsNull() } returns listOf(far)

            Then("it is real-excluded -- not due yet") {
                service.getCouponsDueForExpiryReminder() shouldBe emptyList()
            }
        }

        When("a real active coupon genuinely never expires") {
            val forever = couponWithExpiry("coupon_forever", "merchant_1", null)
            every { merchantCouponRepository.findByActiveTrueAndExpiryReminderSentAtIsNull() } returns listOf(forever)

            Then("it is real-excluded -- there is nothing to remind about") {
                service.getCouponsDueForExpiryReminder() shouldBe emptyList()
            }
        }

        When("sending a real expiry reminder for a due coupon") {
            val coupon = couponWithExpiry("coupon_due", "merchant_1", Instant.now().plus(2, ChronoUnit.DAYS))
            every { merchantCouponRepository.findById("coupon_due") } returns Optional.of(coupon)
            every { merchantRepository.findById("merchant_1") } returns Optional.of(merchant)
            every { merchantCouponRepository.save(any()) } answers { firstArg() }
            every { notificationRepository.save(any()) } answers { firstArg() }

            service.sendExpiryReminder("coupon_due")

            Then("it real-notifies the merchant owner once and real-marks expiryReminderSentAt") {
                verify(exactly = 1) { notificationRepository.save(match { it.type == "MERCHANT_COUPON_EXPIRING_SOON" && it.userId == "seller_1" }) }
                coupon.expiryReminderSentAt shouldNotBe null
            }
        }

        When("sending a reminder for a coupon that was already reminded") {
            val coupon = couponWithExpiry("coupon_already", "merchant_1", Instant.now().plus(1, ChronoUnit.DAYS), expiryReminderSentAt = Instant.now())
            every { merchantCouponRepository.findById("coupon_already") } returns Optional.of(coupon)

            service.sendExpiryReminder("coupon_already")

            Then("it real-skips -- no double notification for the same real coupon") {
                verify(exactly = 0) { notificationRepository.save(any()) }
            }
        }

        When("sending a reminder for a coupon that's already been deactivated") {
            val coupon = couponWithExpiry("coupon_inactive", "merchant_1", Instant.now().plus(1, ChronoUnit.DAYS), active = false)
            every { merchantCouponRepository.findById("coupon_inactive") } returns Optional.of(coupon)

            service.sendExpiryReminder("coupon_inactive")

            Then("it real-skips -- an inactive coupon is not genuinely expiring on customers") {
                verify(exactly = 0) { notificationRepository.save(any()) }
            }
        }
    }

    // Real "Coupon box" cross-merchant browse (itunda Pay redesign, 2026-08-28) --
    // the first real read of MerchantCoupon across every merchant at once, see
    // browseCoupons's own doc comment.
    Given("real active coupons from two different real merchants, one expired, one regulars-only") {
        val merchantRepository = mockk<MerchantRepository>()
        val merchantCouponRepository = mockk<MerchantCouponRepository>()
        val merchantCouponRedemptionRepository = mockk<MerchantCouponRedemptionRepository>()
        val transactionRepository = mockk<TransactionRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = MerchantCouponService(
            merchantRepository, merchantCouponRepository, merchantCouponRedemptionRepository,
            transactionRepository, notificationRepository, pushNotificationService,
        )

        val merchant1 = Merchant(id = "merchant_1", ownerUserId = "seller_1", accountId = "account_1", businessName = "Kigali Coffee", status = MerchantStatus.ACTIVE)
        val merchant2 = Merchant(id = "merchant_2", ownerUserId = "seller_2", accountId = "account_2", businessName = "Nyamirambo Bakery", status = MerchantStatus.ACTIVE)

        val openCoupon = couponWithExpiry("coupon_open", "merchant_1", Instant.now().plus(10, ChronoUnit.DAYS))
        val expiredCoupon = couponWithExpiry("coupon_expired", "merchant_2", Instant.now().minus(1, ChronoUnit.DAYS))
        val regularsOnlyCoupon = MerchantCoupon(
            id = "coupon_regulars", merchantId = "merchant_2", title = "Regulars only 20% off", discountType = CouponDiscountType.PERCENT,
            discountValue = BigDecimal("20"), active = true, regularsOnly = true, expiresAt = null,
        )

        every { merchantCouponRepository.findByActiveTrueOrderByCreatedAtDesc() } returns listOf(openCoupon, expiredCoupon, regularsOnlyCoupon)
        every { merchantRepository.findAllById(listOf("merchant_1", "merchant_2")) } returns listOf(merchant1, merchant2)
        every { merchantCouponRedemptionRepository.existsByCouponIdAndCustomerId(any(), "customer_1") } returns false
        every { transactionRepository.countBySenderIdAndRecipientIdAndTypeAndStatus("customer_1", "seller_2", any(), any()) } returns 0L

        When("a customer who is not a regular anywhere browses real coupons across every merchant") {
            val results = service.browseCoupons("customer_1")

            Then("the real expired coupon is excluded entirely, and each real remaining coupon carries its real merchant name") {
                results.map { it.coupon.id } shouldBe listOf("coupon_open", "coupon_regulars")
                results.find { it.coupon.id == "coupon_open" }?.merchantName shouldBe "Kigali Coffee"
                results.find { it.coupon.id == "coupon_regulars" }?.merchantName shouldBe "Nyamirambo Bakery"
            }
            Then("the real regulars-only coupon is marked ineligible for this non-regular customer") {
                results.find { it.coupon.id == "coupon_regulars" }?.eligible shouldBe false
                results.find { it.coupon.id == "coupon_open" }?.eligible shouldBe true
            }
        }
    }

    Given("a customer with real coupon redemption history") {
        val merchantRepository = mockk<MerchantRepository>()
        val merchantCouponRepository = mockk<MerchantCouponRepository>()
        val merchantCouponRedemptionRepository = mockk<MerchantCouponRedemptionRepository>()
        val transactionRepository = mockk<TransactionRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = MerchantCouponService(
            merchantRepository, merchantCouponRepository, merchantCouponRedemptionRepository,
            transactionRepository, notificationRepository, pushNotificationService,
        )
        val redemption = rw.itunda.core.domain.MerchantCouponRedemption(
            id = "redemption_1", couponId = "coupon_1", merchantId = "merchant_1", customerId = "customer_1",
            transactionId = "txn_1", discountAmount = BigDecimal("500"),
        )
        every { merchantCouponRedemptionRepository.findByCustomerIdOrderByRedeemedAtDesc("customer_1") } returns listOf(redemption)

        When("fetching the real Coupon box 'Used/expired' tab") {
            Then("it returns the real redemption history untouched -- this previously had zero controller endpoint anywhere") {
                service.getMyRedemptions("customer_1") shouldBe listOf(redemption)
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
