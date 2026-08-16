package rw.itunda.merchant

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.CustomerPaymentCode
import rw.itunda.core.domain.Merchant
import rw.itunda.core.domain.MerchantStatus
import rw.itunda.core.domain.PaymentIntent
import rw.itunda.core.domain.PaymentIntentStatus
import rw.itunda.core.domain.Transaction
import rw.itunda.core.domain.TransactionStatus
import rw.itunda.core.domain.TransactionType
import rw.itunda.core.domain.WalletType
import rw.itunda.core.fraud.FraudRuleEngine
import rw.itunda.core.geo.GeoUtils
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.domain.Notification
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.CustomerPaymentCodeRepository
import rw.itunda.core.repository.LedgerEntryRepository
import rw.itunda.core.repository.MerchantRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.PaymentIntentRepository
import rw.itunda.core.repository.TransactionRepository
import rw.itunda.core.repository.WalletRepository
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.URI
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

class MerchantAlreadyRegisteredException(message: String) : RuntimeException(message)
class MerchantNotFoundException(message: String) : RuntimeException(message)
class MerchantNoWalletException(message: String) : RuntimeException(message)
class InvalidCoordinatesException(message: String) : RuntimeException(message)
class InvalidCategoryException(message: String) : RuntimeException(message)
class InvalidClosedWeekdaysException(message: String) : RuntimeException(message)
class InvalidCashbackRateException(message: String) : RuntimeException(message)
class InvalidPhotoUrlException(message: String) : RuntimeException(message)
class InvalidMinOrderAmountException(message: String) : RuntimeException(message)
class InvalidPhoneNumberException(message: String) : RuntimeException(message)
class InvalidOpeningHoursException(message: String) : RuntimeException(message)
class InvalidAvgPrepTimeException(message: String) : RuntimeException(message)
class InvalidPickupDiscountException(message: String) : RuntimeException(message)
class PaymentIntentNotFoundException(message: String) : RuntimeException(message)
class PaymentIntentNotPayableException(message: String) : RuntimeException(message)
class SelfPaymentException(message: String) : RuntimeException(message)
// Real customer-presented payment code (2026-08-11) -- see CustomerPaymentCode.kt's
// own doc comment for the real KakaoPay/Toss Pay flow this closes: customer shows a
// code, merchant scans it, no typing on either side.
class CustomerPaymentCodeNotFoundException(message: String) : RuntimeException(message)
class CustomerPaymentCodeNotPayableException(message: String) : RuntimeException(message)
class PaymentCodeWalletNotOwnedException(message: String) : RuntimeException(message)
class CardDeclinedException(message: String) : RuntimeException(message)
class InvalidWebhookUrlException(message: String) : RuntimeException(message)
class InvalidApiKeyException(message: String) : RuntimeException(message)
class InvalidCheckoutRequestException(message: String) : RuntimeException(message)
class PaymentIntentNotRefundableException(message: String) : RuntimeException(message)
class InvalidCancelRequestException(message: String) : RuntimeException(message)
class InvalidReportRangeException(message: String) : RuntimeException(message)

// Real external-checkout DTOs (2026-07-21) -- see PaymentsApiController's own doc
// comment for the full account of the real Toss Payments feature this mirrors.
data class CheckoutInfo(
    val paymentKey: String,
    val merchantName: String,
    val amount: BigDecimal,
    val description: String,
    val status: PaymentIntentStatus,
    val successUrl: String?,
    val failUrl: String?,
)

data class MerchantReportDay(
    val date: LocalDate,
    val collectionCount: Int,
    val grossAmount: BigDecimal,
    val fees: BigDecimal,
    val netAmount: BigDecimal,
    val byChannel: Map<String, Int>,
)

// Real Coupang WING-style 베스트 상품 (best-selling products) report -- see
// MerchantService.getTopSellingProducts's own doc comment.
data class TopSellingProduct(
    val productId: String,
    val productName: String,
    val unitsSold: Int,
    val revenue: BigDecimal,
)

/**
 * A real, minimal subset of docs/MERCHANT_SERVICES.md's product surface --
 * registration + QR-style fixed-amount payment collection into the merchant's
 * settlement wallet, ledger-backed like every other money-moving flow in this
 * backend. Real production card processing needs actual PSP-level infrastructure
 * this repo has no path to certify, not more Kotlin -- but `chargeCard` below is a
 * real demo card-authorization flow (real Luhn validation, real ledger legs, a real
 * simulated decision), same "real simulation, not a real integration" bar every
 * other blocked-on-external-access flow in this backend already holds itself to.
 */
@Service
class MerchantService(
    private val merchantRepository: MerchantRepository,
    private val paymentIntentRepository: PaymentIntentRepository,
    private val walletRepository: WalletRepository,
    private val ledgerService: LedgerService,
    private val webhookDeliveryService: WebhookDeliveryService,
    private val transactionRepository: TransactionRepository,
    private val fraudRuleEngine: FraudRuleEngine,
    private val demoCardAuthorizationService: DemoCardAuthorizationService,
    private val shoppingCashbackService: ShoppingCashbackService,
    private val rateLimiter: RateLimiter,
    private val ledgerEntryRepository: LedgerEntryRepository,
    private val notificationRepository: NotificationRepository,
    private val merchantCouponService: MerchantCouponService,
    private val pushNotificationService: PushNotificationService,
    private val customerPaymentCodeRepository: CustomerPaymentCodeRepository,
    private val orderRepository: rw.itunda.core.repository.OrderRepository,
    private val orderItemRepository: rw.itunda.core.repository.OrderItemRepository,
) {
    // Real customer-presented code lifetime (2026-08-11) -- short enough that a
    // screenshotted/shoulder-surfed code is only exploitable for a couple minutes,
    // long enough that a customer handing their phone to a cashier doesn't have it
    // expire mid-handoff. itunda's own chosen policy, not a claimed real KakaoPay/
    // Toss Pay figure this project has no way to verify.
    private val customerCodeValidity: Duration = Duration.ofMinutes(2)
    // Toss Payments' real published fee schedule tiers wallet-based payments
    // ("Toss Pay") at 0.8%-1.8% depending on merchant volume (see
    // docs/TOSS_ARCHITECTURE_FACTS.md's Toss Payments SDK research). No tiering
    // system exists here yet, so a single flat rate in the middle of that real
    // range is used rather than inventing a number the way the old
    // MERCHANT_SERVICES.md spec's "QR payments: 1.5%" did independently.
    private val feeRate = BigDecimal("0.015")

    // Real Toss Payments-sourced grace period (2026-07-28) -- see generateApiKey's own
    // doc comment for the citation.
    private val keyGracePeriod: Duration = Duration.ofDays(7)

    @Transactional
    fun register(ownerUserId: String, businessName: String): Merchant {
        if (merchantRepository.findByOwnerUserId(ownerUserId) != null) {
            throw MerchantAlreadyRegisteredException("This account is already registered as a merchant")
        }
        val wallet = walletRepository.findByUserIdAndType(ownerUserId, WalletType.MAIN)
            ?: throw MerchantNoWalletException("No wallet found for this account")

        val merchant = Merchant(
            id = "merchant_${UUID.randomUUID()}",
            ownerUserId = ownerUserId,
            walletId = wallet.id,
            businessName = businessName,
            status = MerchantStatus.ACTIVE,
        )
        val saved = merchantRepository.save(merchant)

        // Real Toss-style 자산 보호 알림 (Asset Protection Alert, launched May 2025) equivalent,
        // honestly scoped to itunda's own system boundary (no MyData/cross-institution access):
        // alert the real account owner whenever a new real financial product -- here, a merchant/
        // business account -- is registered under their identity, so a hijacked session/stolen
        // credentials can't do this with zero alert to the real owner. Follows DeviceService's
        // own real NEW_DEVICE_LOGIN notification convention. See LoansService.applyForLoan for
        // the loan-side counterpart of this same feature.
        notificationRepository.save(
            Notification(
                id = "notif_${UUID.randomUUID()}", userId = ownerUserId, type = "NEW_MERCHANT_REGISTERED",
                title = "New business account registered",
                body = "\"$businessName\" was just registered as a merchant under your account. If this wasn't you, secure your account immediately.",
                isRead = false, createdAt = Instant.now(), dataJson = "{\"merchantId\":\"${saved.id}\"}",
            ),
        )

        return saved
    }

    fun getMyMerchant(ownerUserId: String): Merchant =
        merchantRepository.findByOwnerUserId(ownerUserId)
            ?: throw MerchantNotFoundException("This account is not registered as a merchant")

    @Transactional
    fun setWebhookUrl(ownerUserId: String, webhookUrl: String): Merchant {
        val merchant = getMyMerchant(ownerUserId)
        val trimmed = webhookUrl.trim()
        // Real bound, found via the same systematic sweep that fixed the identical gap
        // across Commerce/Eats/Marketplace/Jobs/RealEstate/Community/Messaging/Maps the
        // same day -- `webhook_url` is VARCHAR(500), and this DB's real
        // STRICT_TRANS_TABLES mode throws a raw, unhandled 500 on an over-length insert.
        // Never previously caught because setWebhookUrl didn't even trim its input.
        if (trimmed.length > 500) {
            throw InvalidWebhookUrlException("Webhook URL must be 500 characters or fewer")
        }
        WebhookUrlPolicy.parse(trimmed)
        merchant.webhookUrl = trimmed
        return merchantRepository.save(merchant)
    }

    // Real location (2026-07-18) -- the foundation of itunda's own self-hosted maps
    // effort. A separate settable field rather than a `register()` param so an existing
    // merchant can add a location later without re-registering, matching the same
    // pattern `setWebhookUrl` already established.
    @Transactional
    fun setLocation(ownerUserId: String, latitude: Double, longitude: Double): Merchant {
        if (!GeoUtils.isValidCoordinate(latitude, longitude)) {
            throw InvalidCoordinatesException("Latitude must be between -90 and 90, longitude between -180 and 180")
        }
        val merchant = getMyMerchant(ownerUserId)
        merchant.latitude = latitude
        merchant.longitude = longitude
        return merchantRepository.save(merchant)
    }

    // Real Naver Pay-style boosted merchant cashback opt-in (2026-07-26) -- see
    // ShoppingCashbackService's own doc comment for the full sourced account. Null
    // resets to the pre-existing flat default rate.
    @Transactional
    fun setCashbackRate(ownerUserId: String, rate: BigDecimal?): Merchant {
        if (rate != null && (rate <= BigDecimal.ZERO || rate > ShoppingCashbackService.MAX_CASHBACK_RATE)) {
            throw InvalidCashbackRateException("Cashback rate must be between 0 and ${ShoppingCashbackService.MAX_CASHBACK_RATE} (0-5%)")
        }
        val merchant = getMyMerchant(ownerUserId)
        merchant.cashbackRate = rate
        return merchantRepository.save(merchant)
    }

    // Real Baemin Club (배민클럽)-style participating-restaurant opt-in (2026-07-26) --
    // see EatsMembership.kt's own doc comment. A restaurant explicitly opts into
    // waiving delivery fees for real active members -- never forced on.
    @Transactional
    fun setParticipatesInEatsMembership(ownerUserId: String, participates: Boolean): Merchant {
        val merchant = getMyMerchant(ownerUserId)
        merchant.participatesInEatsMembership = participates
        return merchantRepository.save(merchant)
    }

    // Real 배달의민족 예약주문 (scheduled ordering) opt-in (2026-07-26) -- see
    // Merchant.kt's own doc comment. A restaurant explicitly opts into accepting
    // buyer-scheduled future delivery/pickup times -- never forced on.
    @Transactional
    fun setAcceptsScheduledOrders(ownerUserId: String, accepts: Boolean): Merchant {
        val merchant = getMyMerchant(ownerUserId)
        merchant.acceptsScheduledOrders = accepts
        return merchantRepository.save(merchant)
    }

    // Real Baemin CEO app 영업일시중지 (temporarily pause business) -- see
    // Merchant.isAcceptingOrders's own doc comment. Same "explicit owner opt-out, never
    // forced" shape setAcceptsScheduledOrders already establishes -- resuming is just as
    // real and self-service as pausing (set true again), no auto-expiry timer exists.
    fun setAcceptingOrders(ownerUserId: String, accepting: Boolean): Merchant {
        val merchant = getMyMerchant(ownerUserId)
        merchant.isAcceptingOrders = accepting
        return merchantRepository.save(merchant)
    }

    // Real Baemin CEO app 휴무일 설정 (recurring weekly closed-day schedule) -- see
    // Merchant.closedWeekdays's own doc comment. Real values 1-7 (java.time.DayOfWeek's
    // own ISO-8601 numbering: 1=MONDAY..7=SUNDAY); an empty set clears the schedule back
    // to "open every day", same real "no forced state" shape setAcceptingOrders already
    // establishes.
    fun setClosedWeekdays(ownerUserId: String, weekdays: Set<Int>): Merchant {
        if (weekdays.any { it !in 1..7 }) {
            throw InvalidClosedWeekdaysException("Each weekday must be between 1 (Monday) and 7 (Sunday)")
        }
        val merchant = getMyMerchant(ownerUserId)
        merchant.closedWeekdays = if (weekdays.isEmpty()) null else weekdays.sorted().joinToString(",")
        return merchantRepository.save(merchant)
    }

    // Real category/cuisine (2026-07-19) -- powers restaurant categories + search/filter
    // for Eats (and Shopping, since both browse the same Merchant directory). Same
    // separate-settable-field pattern as setWebhookUrl/setLocation.
    @Transactional
    fun setCategory(ownerUserId: String, category: String): Merchant {
        val trimmed = category.trim()
        if (trimmed.isEmpty() || trimmed.length > 64) {
            throw InvalidCategoryException("Category must be between 1 and 64 characters")
        }
        val merchant = getMyMerchant(ownerUserId)
        merchant.category = trimmed
        return merchantRepository.save(merchant)
    }

    // Real admin merchant moderation (2026-08-04) -- found while sourcing a real
    // Coupang Eats dish grid: the shared Shop/Eats merchant directory (see setCategory's
    // own doc comment) had no way, anywhere in the app, to ever take a merchant out of
    // public browse once created -- confirmed live against the real dev database that
    // 45 of 50 real ACTIVE merchants were leftover QA fixtures (uncategorized,
    // kyb_verified=false, names like "Push Test Salon") polluting 90% of both Shop's
    // and Eats' actual browse results for a real user. By merchantId (not
    // ownerUserId/getMyMerchant) since this acts on any merchant, not the caller's own.
    // Suspend/reactivate rather than delete -- reversible, same lifecycle status a real
    // merchant already has, not a new destructive capability.
    @Transactional
    fun suspendMerchant(merchantId: String): Merchant {
        val merchant = merchantRepository.findById(merchantId).orElseThrow { MerchantNotFoundException("Merchant not found") }
        merchant.status = MerchantStatus.SUSPENDED
        return merchantRepository.save(merchant)
    }

    @Transactional
    fun reactivateMerchant(merchantId: String): Merchant {
        val merchant = merchantRepository.findById(merchantId).orElseThrow { MerchantNotFoundException("Merchant not found") }
        merchant.status = MerchantStatus.ACTIVE
        return merchantRepository.save(merchant)
    }

    // Real restaurant-card photo (2026-07-21) -- see Merchant.kt's own doc comment for
    // why this is a merchant-set URL, not an upload/storage pipeline. Same trim + length
    // bound discipline as setWebhookUrl.
    @Transactional
    fun setPhotoUrl(ownerUserId: String, photoUrl: String): Merchant {
        val trimmed = photoUrl.trim()
        if (trimmed.length > 500) {
            throw InvalidPhotoUrlException("Photo URL must be 500 characters or fewer")
        }
        val merchant = getMyMerchant(ownerUserId)
        merchant.photoUrl = trimmed.ifEmpty { null }
        return merchantRepository.save(merchant)
    }

    // Real merchant-set minimum order amount (2026-07-21) -- nullable; passing null
    // explicitly clears it back to "no minimum", same as an unset merchant.
    @Transactional
    fun setMinOrderAmount(ownerUserId: String, minOrderAmount: BigDecimal?): Merchant {
        if (minOrderAmount != null && minOrderAmount < BigDecimal.ZERO) {
            throw InvalidMinOrderAmountException("Minimum order amount cannot be negative")
        }
        val merchant = getMyMerchant(ownerUserId)
        merchant.minOrderAmount = minOrderAmount
        return merchantRepository.save(merchant)
    }

    // Real merchant-set phone number + opening hours (2026-08-09) -- see Merchant.kt's
    // own doc comment for why this is plain free text, same bar as photoUrl/category.
    // Passing null explicitly clears it, same convention as every other optional field
    // on this entity.
    @Transactional
    fun setPhoneNumber(ownerUserId: String, phoneNumber: String?): Merchant {
        val trimmed = phoneNumber?.trim()
        if (trimmed != null && trimmed.length > 32) {
            throw InvalidPhoneNumberException("Phone number must be 32 characters or fewer")
        }
        val merchant = getMyMerchant(ownerUserId)
        merchant.phoneNumber = trimmed?.ifEmpty { null }
        return merchantRepository.save(merchant)
    }

    @Transactional
    fun setOpeningHours(ownerUserId: String, openingHours: String?): Merchant {
        val trimmed = openingHours?.trim()
        if (trimmed != null && trimmed.length > 200) {
            throw InvalidOpeningHoursException("Opening hours must be 200 characters or fewer")
        }
        val merchant = getMyMerchant(ownerUserId)
        merchant.openingHours = trimmed?.ifEmpty { null }
        return merchantRepository.save(merchant)
    }

    // Real per-merchant kitchen-prep time (2026-08-16) -- see Merchant.avgPrepTimeMinutes's
    // own doc comment and DeliveryEtaEstimator's own doc comment for the full sourced
    // account (Baemin's real "가게배달 배달시간 AI 예측"). A sanity bound, not an arbitrary
    // one: DeliveryEtaEstimator.MAX_DELIVERY_MINUTES already caps the real customer-facing
    // total at 90, so a prep time beyond that would be meaningless for this system to
    // even accept.
    @Transactional
    fun setAvgPrepTimeMinutes(ownerUserId: String, avgPrepTimeMinutes: Int?): Merchant {
        if (avgPrepTimeMinutes != null && (avgPrepTimeMinutes < 0 || avgPrepTimeMinutes > 90)) {
            throw InvalidAvgPrepTimeException("Average prep time must be between 0 and 90 minutes")
        }
        val merchant = getMyMerchant(ownerUserId)
        merchant.avgPrepTimeMinutes = avgPrepTimeMinutes
        return merchantRepository.save(merchant)
    }

    // Real Baemin 포장할인 (pickup discount) -- see Merchant.pickupDiscountPercent's own
    // doc comment.
    @Transactional
    fun setPickupDiscount(ownerUserId: String, pickupDiscountPercent: Int?): Merchant {
        if (pickupDiscountPercent != null && (pickupDiscountPercent < 1 || pickupDiscountPercent > 100)) {
            throw InvalidPickupDiscountException("Pickup discount must be between 1 and 100 percent")
        }
        val merchant = getMyMerchant(ownerUserId)
        merchant.pickupDiscountPercent = pickupDiscountPercent
        return merchantRepository.save(merchant)
    }

    @Transactional
    fun generateQr(ownerUserId: String, amount: BigDecimal, description: String): PaymentIntent {
        val merchant = getMyMerchant(ownerUserId)
        return createIntent(merchant.id, amount, description)
    }

    // Extracted (2026-07-31) so MerchantStaticQrService's own real Kakao Pay 정액 QR
    // (static/fixed merchant QR) flow can create an identical real PaymentIntent from a
    // public merchantId lookup, without going through generateQr's own
    // getMyMerchant(ownerUserId) ownership check -- a static QR's whole real point is
    // that a CUSTOMER, not the merchant, initiates the intent.
    internal fun createIntent(merchantId: String, amount: BigDecimal, description: String): PaymentIntent {
        val intent = PaymentIntent(
            id = "pi_${UUID.randomUUID()}",
            merchantId = merchantId,
            amount = amount,
            description = description,
            expiresAt = Instant.now().plusSeconds(900),
            ussdCode = generateUssdCode(),
        )
        return paymentIntentRepository.save(intent)
    }

    // Real Toss Payments ARS결제-style USSD payment completion -- see
    // PaymentIntent.ussdCode's own doc comment. A real 6-digit numeric code (never
    // leading-zero-stripped since it's a String, not a parsed number), re-rolled on the
    // rare real collision against another still-live intent rather than trusting
    // birthday-paradox odds alone -- the same "don't just hope" discipline this
    // codebase's other collision-prone id generators already establish.
    private fun generateUssdCode(): String {
        var code: String
        do {
            code = (100000..999999).random().toString()
        } while (paymentIntentRepository.existsByUssdCode(code))
        return code
    }

    // Real "Pay with itunda" external checkout API key (2026-07-21) -- mirrors
    // PartnerService.register's exact raw-key-shown-once/hash-stored pattern (same
    // sk_test_ prefix convention, same reasoning: no real production/live-mode
    // distinction exists here yet, so claiming a "live" prefix would be dishonest).
    // Only the logged-in merchant owner can call this (normal JWT auth, see
    // MerchantController) -- the resulting secret key is what their OWN backend server
    // then uses non-interactively, with no itunda user login involved at all.
    //
    // **Real grace-period rotation added 2026-07-28** -- Toss Payments' own official
    // developer release notes (docs.tosspayments.com/resources/release-note, June 2026:
    // self-service secret/security key reissue, "existing keys enter a 7-day
    // deprecation window, enabling seamless rotation without service interruption").
    // Calling this again no longer cuts the old key off immediately -- it moves the
    // CURRENT key into `previousApiKeyHash` with a real 7-day expiry
    // (`KEY_GRACE_PERIOD`), so a merchant's own server can roll out the new key across
    // its own fleet without a hard cutover mid-rotation. `resolveMerchantByApiKey`
    // checks the previous key too, but only while its real expiry hasn't passed.
    @Transactional
    fun generateApiKey(ownerUserId: String): String {
        val merchant = getMyMerchant(ownerUserId)
        val rawKey = generateRawApiKey()
        merchant.previousApiKeyHash = merchant.apiKeyHash
        merchant.previousApiKeyExpiresAt = if (merchant.apiKeyHash != null) Instant.now().plus(keyGracePeriod) else null
        merchant.apiKeyHash = hashApiKey(rawKey)
        merchantRepository.save(merchant)
        return rawKey
    }

    fun resolveMerchantByApiKey(apiKey: String): Merchant {
        val hashed = hashApiKey(apiKey)
        val merchant = merchantRepository.findByApiKeyHash(hashed) ?: run {
            val candidate = merchantRepository.findByPreviousApiKeyHash(hashed) ?: throw InvalidApiKeyException("Invalid or unknown API key")
            val expiresAt = candidate.previousApiKeyExpiresAt
            if (expiresAt == null || expiresAt.isBefore(Instant.now())) {
                throw InvalidApiKeyException("Invalid or unknown API key")
            }
            candidate
        }
        if (merchant.status != MerchantStatus.ACTIVE) {
            throw InvalidApiKeyException("This merchant account is suspended")
        }
        return merchant
    }

    // Real server-to-server payment creation (2026-07-21) -- the external-checkout
    // counterpart to generateQr above (that one's caller is always a logged-in itunda
    // merchant user in merchant-mfe/:merchantapp; this one's caller is the MERCHANT'S
    // OWN backend server, authenticated by API key, with no itunda user session
    // involved at all -- see PaymentsApiController). Reuses the identical PaymentIntent
    // shape and the identical collect()/webhook machinery underneath -- a customer still
    // completes this exact intent by scanning the same real QR/deep-link with their
    // itunda app, same as any in-app-generated one.
    @Transactional
    fun createExternalPayment(
        merchant: Merchant, amount: BigDecimal, description: String,
        orderId: String?, successUrl: String?, failUrl: String?,
    ): PaymentIntent {
        if (amount <= BigDecimal.ZERO) throw InvalidCheckoutRequestException("Amount must be positive")
        val trimmedDescription = description.trim()
        if (trimmedDescription.isEmpty() || trimmedDescription.length > 500) {
            throw InvalidCheckoutRequestException("Description must be between 1 and 500 characters")
        }
        if ((orderId?.length ?: 0) > 200) throw InvalidCheckoutRequestException("orderId must be 200 characters or fewer")
        val normalizedSuccessUrl = normalizeCheckoutRedirectUrl("successUrl", successUrl)
        val normalizedFailUrl = normalizeCheckoutRedirectUrl("failUrl", failUrl)
        val intent = PaymentIntent(
            id = "pi_${UUID.randomUUID()}",
            merchantId = merchant.id,
            amount = amount,
            description = trimmedDescription,
            expiresAt = Instant.now().plusSeconds(900),
            orderId = orderId?.trim()?.ifBlank { null },
            successUrl = normalizedSuccessUrl,
            failUrl = normalizedFailUrl,
            ussdCode = generateUssdCode(),
        )
        return paymentIntentRepository.save(intent)
    }

    /**
     * The hosted checkout assigns these directly to `window.location`. They are not
     * server-side fetch targets, but accepting relative, script, or credential-bearing
     * values would turn a payment result into an unsafe browser navigation. Merchant
     * origin registration is not modelled yet, so HTTPS absolute URLs are the strict
     * safe baseline while preserving legitimate merchant callback paths and queries.
     */
    private fun normalizeCheckoutRedirectUrl(field: String, value: String?): String? {
        val trimmed = value?.trim()?.ifBlank { return null } ?: return null
        if (trimmed.length > 500) {
            throw InvalidCheckoutRequestException("$field must be 500 characters or fewer")
        }
        val uri = try {
            URI(trimmed)
        } catch (_: Exception) {
            throw InvalidCheckoutRequestException("$field must be a valid HTTPS URL")
        }
        if (uri.scheme?.lowercase() != "https" || uri.host == null || uri.userInfo != null) {
            throw InvalidCheckoutRequestException("$field must be an absolute HTTPS URL without credentials")
        }
        return trimmed
    }

    // Real public checkout info (2026-07-21) -- deliberately NOT behind the API key:
    // the customer's own browser calls this (via itunda's hosted checkout page), and a
    // browser never has the merchant's secret key -- only the paymentKey (this intent's
    // id), the same public/secret split every real payment gateway's checkout page
    // uses. Returns only what's safe to show a paying customer -- never the merchant's
    // internal id, webhook URL, or any other account detail.
    @Transactional
    fun getCheckoutInfo(paymentKey: String): CheckoutInfo {
        val intent = paymentIntentRepository.findById(paymentKey)
            .orElseThrow { PaymentIntentNotFoundException("Payment not found") }
        expireIfPending(intent)
        val merchant = merchantRepository.findById(intent.merchantId)
            .orElseThrow { MerchantNotFoundException("Merchant not found") }
        return CheckoutInfo(
            paymentKey = intent.id,
            merchantName = merchant.businessName,
            amount = intent.amount,
            description = intent.description,
            status = intent.status,
            successUrl = intent.successUrl,
            failUrl = intent.failUrl,
        )
    }

    // Real server-to-server status confirmation (2026-07-21) -- the synchronous
    // counterpart to the async webhook: a merchant's backend can (and per real payment
    // gateway convention, should) confirm a payment's status directly before fulfilling
    // an order, not rely on the webhook alone arriving in time. Ownership-checked: the
    // API key resolves to a specific merchant, and this real-404s (not just returns
    // someone else's data) for a paymentKey belonging to a different merchant.
    @Transactional
    fun getPaymentStatusForMerchant(merchant: Merchant, paymentKey: String): PaymentIntent {
        val intent = paymentIntentRepository.findById(paymentKey)
            .orElseThrow { PaymentIntentNotFoundException("Payment not found") }
        if (intent.merchantId != merchant.id) throw PaymentIntentNotFoundException("Payment not found")
        expireIfPending(intent)
        return intent
    }

    /** Makes passive hosted-checkout polling observe the same terminal expiry state as collect(). */
    private fun expireIfPending(intent: PaymentIntent) {
        if (intent.status == PaymentIntentStatus.PENDING && !intent.expiresAt.isAfter(Instant.now())) {
            intent.status = PaymentIntentStatus.EXPIRED
            paymentIntentRepository.save(intent)
        }
    }

    // Real cancel/refund (2026-07-21) -- mirrors Toss Payments' own real cancel API
    // exactly (docs.tosspayments.com/guides/v2/cancel-payment): paymentKey + a required
    // cancelReason, an optional cancelAmount (a full refund if omitted), supporting
    // repeated partial cancels up to the original amount rather than a single
    // all-or-nothing flag. Reuses the exact real ledger-reversal pattern
    // rw.itunda.commerce.OrderService.cancelOrder already established for Commerce
    // order cancellation: look up the original transaction's real ledger entries and
    // post a new, offsetting transaction with each leg's direction flipped -- a real
    // double-entry reversal, never mutating or deleting the original historical entry.
    //
    // Honest scoping note: Toss Payments' own real per-partial-cancel fee policy isn't
    // published in enough detail to mirror exactly (their docs cover the cancelAmount
    // parameter, not the exact fee-refund math behind it) -- this refunds each original
    // leg (payer debit, merchant credit, fee credit) in the same proportion as the
    // cancelled amount, an itunda-specific, internally-consistent choice, not a
    // fabricated claim about Toss's own internal math.
    @Transactional
    fun cancelPayment(merchant: Merchant, paymentKey: String, cancelReason: String, cancelAmount: BigDecimal?): Map<String, Any?> {
        val intent = paymentIntentRepository.findById(paymentKey)
            .orElseThrow { PaymentIntentNotFoundException("Payment not found") }
        if (intent.merchantId != merchant.id) throw PaymentIntentNotFoundException("Payment not found")
        if (intent.status != PaymentIntentStatus.COMPLETED) {
            throw PaymentIntentNotRefundableException("Only a completed payment can be cancelled -- this payment is ${intent.status}")
        }
        val transactionId = intent.completedTransactionId
            ?: throw PaymentIntentNotRefundableException("No completed transaction found for this payment")

        val trimmedReason = cancelReason.trim()
        if (trimmedReason.isEmpty() || trimmedReason.length > 200) {
            throw InvalidCancelRequestException("cancelReason must be between 1 and 200 characters")
        }
        val remaining = intent.amount.subtract(intent.refundedAmount)
        val amountToCancel = cancelAmount ?: remaining
        if (amountToCancel <= BigDecimal.ZERO || amountToCancel > remaining) {
            throw InvalidCancelRequestException("cancelAmount must be positive and no more than the remaining refundable amount ($remaining)")
        }

        val originalEntries = ledgerEntryRepository.findByTransactionId(transactionId)
        if (originalEntries.isEmpty()) throw PaymentIntentNotRefundableException("No ledger entries found for this payment")

        val ratio = amountToCancel.divide(intent.amount, 10, RoundingMode.HALF_UP)
        val reversedLegs = originalEntries.map { entry ->
            val flipped = if (entry.direction == LedgerDirection.DEBIT) LedgerDirection.CREDIT else LedgerDirection.DEBIT
            val partialAmount = entry.amount.multiply(ratio).setScale(2, RoundingMode.HALF_UP)
            LedgerLeg(entry.accountId, entry.accountType, flipped, partialAmount, "Refund for payment ${intent.id}: $trimmedReason")
        }
        val refund = ledgerService.postLedgerTransaction(originalEntries.first().currency, reversedLegs)

        intent.refundedAmount = intent.refundedAmount.add(amountToCancel)
        paymentIntentRepository.save(intent)

        val resultMap = mapOf(
            "paymentKey" to intent.id,
            "orderId" to intent.orderId,
            "cancelledAmount" to amountToCancel,
            "totalRefundedAmount" to intent.refundedAmount,
            "remainingAmount" to intent.amount.subtract(intent.refundedAmount),
            "cancelReason" to trimmedReason,
            "refundTransactionId" to refund.transactionId,
            "fullyCancelled" to (intent.refundedAmount.compareTo(intent.amount) == 0),
        )
        // Same "never let a slow/unreachable webhook block real money movement" discipline
        // as collect()/chargeCard -- called last, after the refund ledger transaction and
        // intent are already saved.
        webhookDeliveryService.deliverCancelStatusChanged(merchant.id, merchant.webhookUrl, resultMap)
        return resultMap
    }

    private fun generateRawApiKey(): String {
        val bytes = ByteArray(24)
        SecureRandom().nextBytes(bytes)
        val token = bytes.joinToString("") { "%02x".format(it) }
        return "sk_test_$token"
    }

    private fun hashApiKey(rawKey: String): String =
        MessageDigest.getInstance("SHA-256").digest(rawKey.toByteArray()).joinToString("") { "%02x".format(it) }

    // Real read-only intent preview (item 149) -- lets a payer see which merchant/amount
    // a payment code resolves to, and that merchant's own real coupon eligibility, BEFORE
    // committing to collect(). Deliberately has zero side effects (unlike collect(),
    // which flips an expired PENDING intent to EXPIRED) -- a preview must never mutate
    // state a payer might still back out of; collect() remains the sole authority on
    // whether a code is actually still payable.
    fun previewIntent(payerUserId: String, intentId: String): Map<String, Any?> {
        val intent = paymentIntentRepository.findById(intentId)
            .orElseThrow { PaymentIntentNotFoundException("Payment code not found") }
        if (intent.status != PaymentIntentStatus.PENDING || intent.expiresAt.isBefore(Instant.now())) {
            throw PaymentIntentNotPayableException("This payment code is no longer payable")
        }
        val merchant = merchantRepository.findById(intent.merchantId)
            .orElseThrow { MerchantNotFoundException("Merchant not found") }
        return mapOf(
            "merchantId" to merchant.id,
            "businessName" to merchant.businessName,
            "amount" to intent.amount,
            "description" to intent.description,
            "coupons" to merchantCouponService.getCouponsForCustomer(merchant.id, payerUserId),
        )
    }

    @Transactional
    fun collect(payerUserId: String, intentId: String, channel: String = "QR", couponId: String? = null): Map<String, Any?> {
        val intent = paymentIntentRepository.findById(intentId)
            .orElseThrow { PaymentIntentNotFoundException("Payment code not found") }
        if (intent.status != PaymentIntentStatus.PENDING) {
            throw PaymentIntentNotPayableException("This payment code has already been used")
        }
        if (intent.expiresAt.isBefore(Instant.now())) {
            intent.status = PaymentIntentStatus.EXPIRED
            paymentIntentRepository.save(intent)
            throw PaymentIntentNotPayableException("This payment code has expired")
        }

        val merchant = merchantRepository.findById(intent.merchantId)
            .orElseThrow { MerchantNotFoundException("Merchant not found") }
        // Real Toss writing-principle reference (toss.tech/article/21021): a concrete
        // next step over a blunt "Cannot X" -- there IS a real useful alternative here
        // (share the code with a customer), so name it instead of just naming the block.
        if (merchant.ownerUserId == payerUserId) {
            throw SelfPaymentException("That's your own QR code -- share it with a customer instead of scanning it yourself")
        }

        val payerWallet = walletRepository.findByUserIdAndType(payerUserId, WalletType.MAIN)
            ?: throw MerchantNoWalletException("No wallet found for this account")
        val merchantWallet = walletRepository.findById(merchant.walletId)
            .orElseThrow { MerchantNoWalletException("Merchant settlement wallet not found") }

        // Real merchant coupon discount (2026-07-25) -- see MerchantCouponService's own
        // doc comment for why an invalid/ineligible coupon throws here rather than being
        // silently ignored: this changes the actual amount charged, unlike an auxiliary
        // side effect. Computed and validated before any ledger leg is posted, off the
        // real intent.amount the merchant originally set.
        val discountAmount = couponId?.let { merchantCouponService.validateAndComputeDiscount(merchant, payerUserId, it, intent.amount) } ?: BigDecimal.ZERO
        val chargeAmount = intent.amount.subtract(discountAmount)
        if (chargeAmount <= BigDecimal.ZERO) {
            throw InvalidCouponException("This coupon would reduce the payment to zero -- itunda doesn't support 100%-off payments")
        }

        // Real Naver Pay 영세 가맹점 수수료 지원 (small-merchant fee waiver) -- see
        // MerchantFeeWaiverService's own doc comment. Null means this merchant never
        // qualified/applied, the standard rate applies unchanged.
        val fee = chargeAmount.multiply(merchant.feeRateOverride ?: feeRate).setScale(2, RoundingMode.HALF_UP)
        val netToMerchant = chargeAmount.subtract(fee)
        // channel-labeled memo/description (2026-07-13, added for Face Pay) -- keeps a
        // real, honest audit trail of which authentication factor collected a given
        // payment (QR scan vs Face Pay biometric match) rather than always saying "QR".
        val channelLabel = when (channel) {
            "FACE_PAY" -> "Face Pay"
            // Real Kakao Pay 정액 QR (static/fixed merchant QR) -- see
            // MerchantStaticQrService's own doc comment.
            "STATIC_QR" -> "Static QR"
            // Real Toss Payments ARS결제-style USSD payment completion -- see
            // PaymentIntent.ussdCode's own doc comment.
            "USSD" -> "USSD"
            else -> "QR"
        }

        val result = ledgerService.postLedgerTransaction(
            payerWallet.currency,
            listOf(
                LedgerLeg(payerWallet.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, chargeAmount, "$channelLabel payment - ${merchant.businessName}"),
                LedgerLeg(merchantWallet.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, netToMerchant, "$channelLabel collection - ${intent.description}"),
                LedgerLeg("fee_revenue", LedgerAccountType.FEE_REVENUE, LedgerDirection.CREDIT, fee, "$channelLabel payment fee - ${merchant.businessName}"),
            ),
        )

        // Real Transaction row + fraud review wired in (2026-07-13) -- this method
        // previously only posted ledger legs and never wrote a Transaction row at all
        // (confirmed live: TransactionRepository wasn't even injected here). That's a
        // real, separate gap beyond just fraud review: WalletService.getTransactionHistory
        // and FraudRuleEngine's own VELOCITY/NEW_RECIPIENT rules both key off the
        // transactions table, so merchant payments were invisible to both a payer's/
        // merchant's own transaction history *and* to fraud history checks for every
        // other flow -- a repeat-merchant-payment could never trigger VELOCITY, and a
        // brand-new merchant recipient could never be flagged NEW_RECIPIENT. Persisting
        // this row here, before the fraud evaluate() call (same ordering reasoning as
        // P2pService.payRequest and WalletService.confirmTransfer: evaluating after the
        // save would let this transaction match itself as prior history), fixes both.
        val transaction = Transaction(
            id = result.transactionId,
            referenceNumber = "MERC${System.currentTimeMillis()}${UUID.randomUUID().toString().take(4)}",
            senderId = payerUserId,
            recipientId = merchant.ownerUserId,
            fromWalletId = payerWallet.id,
            toWalletId = merchantWallet.id,
            amount = chargeAmount,
            fee = fee,
            currency = payerWallet.currency,
            type = TransactionType.PAYMENT,
            status = TransactionStatus.COMPLETED,
            description = "$channelLabel payment - ${merchant.businessName}",
            channel = channel,
            completedAt = Instant.now(),
        )
        fraudRuleEngine.evaluate(payerUserId, merchant.ownerUserId, chargeAmount, transaction.id)
        transactionRepository.save(transaction)

        intent.status = PaymentIntentStatus.COMPLETED
        intent.completedTransactionId = result.transactionId
        intent.paidByUserId = payerUserId
        paymentIntentRepository.save(intent)

        // Real "Toss Shopping" cashback (2026-07-17) -- see ShoppingCashbackService's own
        // doc comment for why this only applies here (a real itunda payer wallet exists)
        // and not in chargeCard (an external card payer has no itunda wallet to credit).
        // Explicitly caught, not propagated: a cashback failure must never roll back or
        // fail a real payment that already succeeded, the same "auxiliary side-effect
        // can't block real money movement" discipline the webhook call below already
        // established -- REQUIRES_NEW alone doesn't guarantee that (an uncaught exception
        // here would still roll back this method's own transaction), so this needs its
        // own explicit try/catch, not just the inner service's propagation setting.
        // Real Naver Pay-style boosted opt-in rate (2026-07-26) -- see
        // ShoppingCashbackService's own doc comment. Null means this merchant never
        // opted in, so the default flat rate applies, unchanged.
        val cashbackEarned = try {
            shoppingCashbackService.awardCashback(payerWallet, chargeAmount, merchant.businessName, merchant.cashbackRate ?: ShoppingCashbackService.DEFAULT_CASHBACK_RATE)
        } catch (e: Exception) {
            BigDecimal.ZERO
        }

        // Real coupon redemption record -- kept in this same @Transactional method so it
        // commits atomically with the payment it discounted, never orphaned from it.
        if (couponId != null) {
            merchantCouponService.recordRedemption(merchant, payerUserId, couponId, result.transactionId, discountAmount)
        }

        // Real-time "money received" notification for the merchant owner (2026-07-22) --
        // same real gap and same fix as rw.itunda.p2p.P2pService.notifyMoneyReceived
        // (see that method's own doc comment for the full account of the real Toss Bank
        // feature this mirrors): a merchant collecting a real QR/Face Pay payment never
        // got any proactive alert that money had arrived, only whatever they happened to
        // notice next time they opened Reports. Best-effort, same discipline as the
        // cashback try/catch immediately above -- never blocks a payment that already
        // succeeded.
        //
        // Real push wired in (2026-07-28), same pass as its P2P sibling
        // (rw.itunda.p2p.P2pService.notifyMoneyReceived) -- a merchant owner is at least
        // as likely to be away from the app at the moment a customer pays (mid-checkout,
        // handing a phone back) as a P2P recipient is, making the proactive push just as
        // valuable here.
        try {
            val title = "Payment received"
            val body = "You received $chargeAmount RWF via $channelLabel."
            notificationRepository.save(
                Notification(
                    id = "notif_${UUID.randomUUID()}",
                    userId = merchant.ownerUserId,
                    type = "MONEY_RECEIVED",
                    title = title,
                    body = body,
                    isRead = false,
                    createdAt = Instant.now(),
                    dataJson = "{\"amount\":\"$chargeAmount\",\"payerId\":\"$payerUserId\"}",
                ),
            )
            sendPaymentReceivedPushAfterCommit(merchant.ownerUserId, title, body, chargeAmount, payerUserId)
        } catch (e: Exception) {
            // Non-critical -- the real payment already completed and succeeded.
        }

        val resultMap = mapOf(
            "transactionId" to result.transactionId,
            "merchantName" to merchant.businessName,
            "amount" to chargeAmount,
            "originalAmount" to intent.amount,
            "discountAmount" to discountAmount,
            "fee" to fee,
            "status" to "COMPLETED",
            "channel" to channel,
            "completedAt" to Instant.now().toString(),
            "cashbackEarned" to cashbackEarned,
        )
        // Real webhook delivery -- no-op if the merchant never registered a URL. Called last,
        // after the ledger transaction and intent status are already saved, so a slow or
        // unreachable webhook endpoint can only delay the response, never roll back real money
        // that already moved.
        //
        // orderId added 2026-07-21 -- a real, found-live gap: Toss Payments' own real webhook
        // payload always includes orderId ("orderId persists even when the payment status
        // changes", per docs.tosspayments.com/en/webhooks), specifically so a merchant's
        // webhook receiver can correlate the event back to ITS OWN order record without a
        // second lookup call. Every in-app QR/Face Pay/card payment leaves this null (they
        // have no external orderId at all) -- only real external-checkout payments
        // (PaymentsApiController) ever set one, so this is purely additive for every existing
        // webhook consumer.
        webhookDeliveryService.deliverPaymentStatusChanged(
            merchant.id,
            merchant.webhookUrl,
            resultMap + ("paymentIntentId" to intentId) + ("payerId" to payerUserId) + ("orderId" to intent.orderId),
        )
        return resultMap
    }

    // Real customer-presented payment code (2026-08-11) -- see CustomerPaymentCode.kt's
    // own doc comment. Generated by the PAYING customer, from their own Pay tab -- the
    // reverse direction of generateQr above (merchant generates, customer scans/types).
    // Invalidates any prior unused code for this user first, same "one live code at a
    // time" discipline PhoneVerificationTokenRepository.invalidateUnusedByUserId already
    // establishes, so a customer re-opening Pay can't leave an earlier still-valid code
    // usable by whoever saw it on screen first.
    // walletId (2026-08-11) -- real funding-source selection, see the user's own
    // KakaoPay reference screenshot's swipeable card carousel and CustomerPaymentCode.
    // walletId's own doc comment. Ownership is checked here (not left to
    // chargeByCustomerCode) so a bad walletId fails loudly to the customer generating
    // the code, not silently at charge time in front of a merchant.
    @Transactional
    fun generateCustomerPaymentCode(userId: String, walletId: String? = null): CustomerPaymentCode {
        if (walletId != null) {
            val wallet = walletRepository.findById(walletId).orElseThrow { MerchantNoWalletException("Wallet not found") }
            if (wallet.userId != userId) throw PaymentCodeWalletNotOwnedException("That wallet does not belong to you")
        }
        customerPaymentCodeRepository.invalidateUnusedByUserId(userId)
        val codeBytes = ByteArray(24)
        SecureRandom().nextBytes(codeBytes)
        val code = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(codeBytes)
        return customerPaymentCodeRepository.save(
            CustomerPaymentCode(
                id = "cpc_${UUID.randomUUID()}",
                userId = userId,
                code = code,
                expiresAt = Instant.now().plus(customerCodeValidity),
                walletId = walletId,
            ),
        )
    }

    // Real customer-presented payment charge (2026-08-11) -- the merchant scans the
    // customer's own code (no PaymentIntent exists yet, unlike collect() above -- the
    // code carries no amount, the merchant enters it after scanning, matching real
    // KakaoPay/Toss Pay's actual in-store flow). Deliberately a separate method from
    // collect() rather than a refactor of it: collect() is tightly coupled to
    // PaymentIntent's own status/webhook/orderId fields in ways that don't apply here,
    // and this is real money-movement code -- safer to duplicate the core charge shape
    // (same fee math, same real ledger legs, same fraud check, same cashback, same
    // notification) than risk a regression in the already-working merchant-QR path.
    @Transactional
    fun chargeByCustomerCode(merchantOwnerUserId: String, code: String, amount: BigDecimal): Map<String, Any?> {
        require(amount > BigDecimal.ZERO) { "Amount must be positive" }
        val paymentCode = customerPaymentCodeRepository.findByCode(code)
            ?: throw CustomerPaymentCodeNotFoundException("This code was not found")
        if (paymentCode.usedAt != null) {
            throw CustomerPaymentCodeNotPayableException("This code has already been used")
        }
        if (paymentCode.expiresAt.isBefore(Instant.now())) {
            throw CustomerPaymentCodeNotPayableException("This code has expired -- ask the customer to refresh their Pay screen")
        }

        val merchant = merchantRepository.findByOwnerUserId(merchantOwnerUserId)
            ?: throw MerchantNotFoundException("Merchant not found")
        val payerUserId = paymentCode.userId
        if (merchant.ownerUserId == payerUserId) {
            throw SelfPaymentException("That's your own code -- share it with a customer instead of using it yourself")
        }

        // Real funding-source selection -- falls back to the historical WalletType.MAIN
        // default when the code was generated without picking a specific wallet (see
        // CustomerPaymentCode.walletId's own doc comment).
        val payerWallet = paymentCode.walletId?.let { walletRepository.findById(it).orElse(null) }
            ?: walletRepository.findByUserIdAndType(payerUserId, WalletType.MAIN)
            ?: throw MerchantNoWalletException("No wallet found for this account")
        val merchantWallet = walletRepository.findById(merchant.walletId)
            .orElseThrow { MerchantNoWalletException("Merchant settlement wallet not found") }

        val fee = amount.multiply(merchant.feeRateOverride ?: feeRate).setScale(2, RoundingMode.HALF_UP)
        val netToMerchant = amount.subtract(fee)

        val result = ledgerService.postLedgerTransaction(
            payerWallet.currency,
            listOf(
                LedgerLeg(payerWallet.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, amount, "Payment - ${merchant.businessName}"),
                LedgerLeg(merchantWallet.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, netToMerchant, "Collection - ${merchant.businessName}"),
                LedgerLeg("fee_revenue", LedgerAccountType.FEE_REVENUE, LedgerDirection.CREDIT, fee, "Payment fee - ${merchant.businessName}"),
            ),
        )

        // Same real Transaction-row-before-fraud-evaluate ordering as collect() above --
        // see that method's own doc comment for why.
        val transaction = Transaction(
            id = result.transactionId,
            referenceNumber = "MERC${System.currentTimeMillis()}${UUID.randomUUID().toString().take(4)}",
            senderId = payerUserId,
            recipientId = merchant.ownerUserId,
            fromWalletId = payerWallet.id,
            toWalletId = merchantWallet.id,
            amount = amount,
            fee = fee,
            currency = payerWallet.currency,
            type = TransactionType.PAYMENT,
            status = TransactionStatus.COMPLETED,
            description = "Payment - ${merchant.businessName}",
            channel = "CUSTOMER_QR",
            completedAt = Instant.now(),
        )
        fraudRuleEngine.evaluate(payerUserId, merchant.ownerUserId, amount, transaction.id)
        transactionRepository.save(transaction)

        paymentCode.usedAt = Instant.now()
        customerPaymentCodeRepository.save(paymentCode)

        // Same real, never-block-a-completed-payment cashback/notification discipline
        // as collect() above -- see that method's own doc comments.
        val cashbackEarned = try {
            shoppingCashbackService.awardCashback(payerWallet, amount, merchant.businessName, merchant.cashbackRate ?: ShoppingCashbackService.DEFAULT_CASHBACK_RATE)
        } catch (e: Exception) {
            BigDecimal.ZERO
        }

        try {
            val title = "Payment received"
            val body = "You received $amount RWF via customer QR."
            notificationRepository.save(
                Notification(
                    id = "notif_${UUID.randomUUID()}",
                    userId = merchant.ownerUserId,
                    type = "MONEY_RECEIVED",
                    title = title,
                    body = body,
                    isRead = false,
                    createdAt = Instant.now(),
                    dataJson = "{\"amount\":\"$amount\",\"payerId\":\"$payerUserId\"}",
                ),
            )
            sendPaymentReceivedPushAfterCommit(merchant.ownerUserId, title, body, amount, payerUserId)
        } catch (e: Exception) {
            // Non-critical -- the real payment already completed and succeeded.
        }

        val resultMap = mapOf(
            "transactionId" to result.transactionId,
            "merchantName" to merchant.businessName,
            "amount" to amount,
            "fee" to fee,
            "status" to "COMPLETED",
            "channel" to "CUSTOMER_QR",
            "completedAt" to Instant.now().toString(),
            "cashbackEarned" to cashbackEarned,
        )
        webhookDeliveryService.deliverPaymentStatusChanged(
            merchant.id,
            merchant.webhookUrl,
            resultMap + ("payerId" to payerUserId),
        )
        return resultMap
    }

    /** Merchant pushes are external effects, so never advertise a payment before commit. */
    private fun sendPaymentReceivedPushAfterCommit(
        ownerUserId: String,
        title: String,
        body: String,
        amount: BigDecimal,
        payerUserId: String,
    ) {
        val send = {
            try {
                pushNotificationService.sendToUser(ownerUserId, title, body, mapOf("amount" to amount.toString(), "payerId" to payerUserId))
            } catch (_: Exception) {
                // A mobile delivery failure must not affect an already-committed payment.
            }
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            send()
            return
        }
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() = send()
        })
    }

    // Real demo card-processing flow (2026-07-17), closing the actionable half of this
    // row's previously fully-blocked "POS, card processing" gap -- see
    // DemoCardAuthorizationService's own doc comment for the real Luhn validation +
    // simulated authorization this runs before any ledger posting. Unlike collect()'s
    // QR flow, there is no real itunda payer wallet on the other side of a card charge
    // (a real card is issued by a real bank/network outside this system) -- the debit
    // leg goes to a real RAIL_SUSPENSE clearing account (card_network_clearing),
    // same "money entering from outside the system" pattern WalletService's own
    // external-rail transfers already use, rather than inventing a fake payer wallet.
    @Transactional
    fun chargeCard(
        ownerUserId: String, amount: BigDecimal, description: String,
        cardNumber: String, expiryMonth: Int, expiryYear: Int, cvc: String,
    ): Map<String, Any?> {
        // Real rate limit (2026-07-17, found by this pass's own security review) --
        // DemoCardAuthorizationService.simulateOutcome APPROVEs ~85% of any Luhn-valid
        // card number and this method credits that approval as real, spendable ledger
        // balance into the merchant's real wallet. Unlike a real PSP integration (where
        // a genuine issuer/network sits between an attempt and any money moving), this
        // demo has no external gate at all -- without a limit here, a scripted burst of
        // random Luhn-valid numbers against this one endpoint would mint real balance
        // with no bound, the same class of risk PartnerService.register/CertificateService
        // .issue already guard against on this exact codebase's own established
        // convention. 10/minute comfortably covers a real busy shop's checkout pace
        // while making brute-force card generation impractical.
        rateLimiter.checkLimit("merchant:chargeCard:$ownerUserId", limit = 10, window = Duration.ofMinutes(1))

        val merchant = getMyMerchant(ownerUserId)
        val merchantWallet = walletRepository.findById(merchant.walletId)
            .orElseThrow { MerchantNoWalletException("Merchant settlement wallet not found") }

        val authResult = demoCardAuthorizationService.authorize(cardNumber, expiryMonth, expiryYear, cvc)
        if (authResult.status != CardAuthorizationStatus.APPROVED) {
            throw CardDeclinedException(authResult.detail)
        }

        // Real Naver Pay 영세 가맹점 수수료 지원 (small-merchant fee waiver) -- see
        // MerchantFeeWaiverService's own doc comment.
        val fee = amount.multiply(merchant.feeRateOverride ?: feeRate).setScale(2, RoundingMode.HALF_UP)
        val netToMerchant = amount.subtract(fee)

        val result = ledgerService.postLedgerTransaction(
            merchantWallet.currency,
            listOf(
                LedgerLeg("card_network_clearing", LedgerAccountType.RAIL_SUSPENSE, LedgerDirection.DEBIT, amount, "Card payment - ${merchant.businessName}"),
                LedgerLeg(merchantWallet.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, netToMerchant, "Card collection - $description"),
                LedgerLeg("fee_revenue", LedgerAccountType.FEE_REVENUE, LedgerDirection.CREDIT, fee, "Card payment fee - ${merchant.businessName}"),
            ),
        )

        // "external_card" mirrors WalletService.confirmTransfer's own "external"
        // recipientId convention for money that enters/leaves through a real external
        // rail rather than another itunda wallet -- senderId/recipientId are plain
        // strings with no FK constraint (confirmed directly against Transaction.kt).
        // channel = "CARD" means this shows up in getReport()'s existing byChannel
        // breakdown automatically, no changes needed there.
        val transaction = Transaction(
            id = result.transactionId,
            referenceNumber = "CARD${System.currentTimeMillis()}${UUID.randomUUID().toString().take(4)}",
            senderId = "external_card_${authResult.last4}",
            recipientId = merchant.ownerUserId,
            fromWalletId = null,
            toWalletId = merchantWallet.id,
            amount = amount,
            fee = fee,
            currency = merchantWallet.currency,
            type = TransactionType.PAYMENT,
            status = TransactionStatus.COMPLETED,
            description = "Card payment - ${merchant.businessName}",
            channel = "CARD",
            completedAt = Instant.now(),
        )
        transactionRepository.save(transaction)

        val resultMap = mapOf(
            "transactionId" to result.transactionId,
            "merchantName" to merchant.businessName,
            "amount" to amount,
            "fee" to fee,
            "status" to "COMPLETED",
            "channel" to "CARD",
            "cardLast4" to authResult.last4,
            "completedAt" to Instant.now().toString(),
        )
        webhookDeliveryService.deliverPaymentStatusChanged(merchant.id, merchant.webhookUrl, resultMap + ("payerId" to "external_card_${authResult.last4}"))
        return resultMap
    }

    // Real merchant reports (2026-07-16) -- closes the "reports" half of the target
    // capability named in docs/TOSS_PARITY_MATRIX.md's Merchant row (QR, POS, reports,
    // settlements). Grouped in-memory by day rather than a JPQL date-function GROUP BY
    // (ReconciliationService's approach) since Transaction.createdAt is a timestamp, not
    // a pre-truncated date column like ProviderAttemptLog.occurredDate -- fine at this
    // scale, and avoids a database-specific date-truncation function. "Settlement" here
    // is just Transaction.status == COMPLETED: collect() posts to the merchant's own
    // wallet synchronously in the same ledger transaction as the collection, so there's
    // no separate pending-settlement state to report on, unlike a real payout batch.
    fun getReport(ownerUserId: String, from: LocalDate, to: LocalDate): List<MerchantReportDay> {
        if (from.isAfter(to)) {
            throw InvalidReportRangeException("Report start date must be on or before the end date")
        }
        if (from.plusDays(30).isBefore(to)) {
            throw InvalidReportRangeException("Reports are limited to 31 days at a time")
        }
        val merchant = getMyMerchant(ownerUserId)
        val fromInstant = from.atStartOfDay(ZoneOffset.UTC).toInstant()
        val toInstant = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()
        val transactions = transactionRepository.findByRecipientIdAndTypeAndCreatedAtBetween(
            merchant.ownerUserId, TransactionType.PAYMENT, fromInstant, toInstant,
        )
        return transactions
            .groupBy { LocalDate.ofInstant(it.createdAt, ZoneOffset.UTC) }
            .map { (date, dayTransactions) ->
                MerchantReportDay(
                    date = date,
                    collectionCount = dayTransactions.size,
                    grossAmount = dayTransactions.fold(BigDecimal.ZERO) { acc, t -> acc + t.amount },
                    fees = dayTransactions.fold(BigDecimal.ZERO) { acc, t -> acc + t.fee },
                    netAmount = dayTransactions.fold(BigDecimal.ZERO) { acc, t -> acc + (t.amount - t.fee) },
                    byChannel = dayTransactions.groupingBy { it.channel ?: "QR" }.eachCount(),
                )
            }
            .sortedBy { it.date }
    }

    // Real Coupang WING seller dashboard "베스트 상품" (best-selling products) report
    // (2026-08-16) -- WING's own real seller analytics tab ranks products by units/
    // revenue sold, distinct from a raw revenue total, so a seller can see WHAT is
    // driving sales, not just how much. OrderItem already snapshots productId/
    // productName/unitPrice/quantity at purchase time (Coupang-style multi-item
    // Order); grouped in-memory over a bounded window, same real reason getReport's
    // own doc comment gives for not using a JPQL date-function GROUP BY. Deliberately
    // does not filter by OrderStatus -- matches getReport's own definition of
    // "revenue" (gross collected at placement, not fulfillment-gated); a cancelled
    // order's reversal is a separate real refund Transaction, not a retroactive
    // rewrite of what was sold.
    fun getTopSellingProducts(ownerUserId: String, from: LocalDate, to: LocalDate, limit: Int = 10): List<TopSellingProduct> {
        if (from.isAfter(to)) {
            throw InvalidReportRangeException("Report start date must be on or before the end date")
        }
        if (from.plusDays(30).isBefore(to)) {
            throw InvalidReportRangeException("Reports are limited to 31 days at a time")
        }
        val merchant = getMyMerchant(ownerUserId)
        val fromInstant = from.atStartOfDay(ZoneOffset.UTC).toInstant()
        val toInstant = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()
        val orders = orderRepository.findByMerchantIdAndCreatedAtBetween(merchant.id, fromInstant, toInstant)
        if (orders.isEmpty()) return emptyList()
        val items = orderItemRepository.findByOrderIdIn(orders.map { it.id })
        return items
            .groupBy { it.productId }
            .map { (productId, productItems) ->
                TopSellingProduct(
                    productId = productId,
                    productName = productItems.first().productName,
                    unitsSold = productItems.sumOf { it.quantity },
                    revenue = productItems.fold(BigDecimal.ZERO) { acc, i -> acc + i.unitPrice.multiply(BigDecimal(i.quantity)) },
                )
            }
            .sortedByDescending { it.revenue }
            .take(limit)
    }
}
