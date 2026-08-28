package rw.itunda.merchant

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.core.domain.Merchant
import rw.itunda.core.geo.GeoUtils
import rw.itunda.core.repository.MerchantRepository
import java.math.BigDecimal

// Extracted from MerchantService.kt (itunda Maps redesign, 2026-08-28) -- that file
// crossed its frozen file-size-lint baseline once the real photo-gallery setter (see
// Merchant.photoUrls' own doc comment) was added. A real, natural single-
// responsibility split: this is every plain owner-self-service "get merchant,
// validate, set one field, save" profile/settings mutation, distinct from
// MerchantService's own much larger real payment/QR/checkout/reporting logic.
// Suspend/reactivate stay in MerchantService -- those are admin-moderation actions
// called from a different controller, not owner self-service.
@Service
class MerchantProfileService(
    private val merchantRepository: MerchantRepository,
) {
    fun getMyMerchant(ownerUserId: String): Merchant =
        merchantRepository.findByOwnerUserId(ownerUserId)
            ?: throw MerchantNotFoundException("This account is not registered as a merchant")

    @Transactional
    fun setWebhookUrl(ownerUserId: String, webhookUrl: String): Merchant {
        val merchant = getMyMerchant(ownerUserId)
        val trimmed = webhookUrl.trim()
        // Real bound (VARCHAR(500), this DB's real STRICT_TRANS_TABLES mode throws a
        // raw, unhandled 500 on an over-length insert).
        if (trimmed.length > 500) {
            throw InvalidWebhookUrlException("Webhook URL must be 500 characters or fewer")
        }
        WebhookUrlPolicy.parse(trimmed)
        merchant.webhookUrl = trimmed
        return merchantRepository.save(merchant)
    }

    // Real location (2026-07-18) -- the foundation of itunda's own self-hosted maps
    // effort. A separate settable field rather than a `register()` param so an existing
    // merchant can add a location later without re-registering.
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

    // Real Naver Pay-style boosted merchant cashback opt-in -- see
    // ShoppingCashbackService's own doc comment. Null resets to the flat default rate.
    @Transactional
    fun setCashbackRate(ownerUserId: String, rate: BigDecimal?): Merchant {
        if (rate != null && (rate <= BigDecimal.ZERO || rate > ShoppingCashbackService.MAX_CASHBACK_RATE)) {
            throw InvalidCashbackRateException("Cashback rate must be between 0 and ${ShoppingCashbackService.MAX_CASHBACK_RATE} (0-5%)")
        }
        val merchant = getMyMerchant(ownerUserId)
        merchant.cashbackRate = rate
        return merchantRepository.save(merchant)
    }

    // Real Baemin Club (배민클럽)-style participating-restaurant opt-in -- see
    // EatsMembership.kt's own doc comment. Never forced on.
    @Transactional
    fun setParticipatesInEatsMembership(ownerUserId: String, participates: Boolean): Merchant {
        val merchant = getMyMerchant(ownerUserId)
        merchant.participatesInEatsMembership = participates
        return merchantRepository.save(merchant)
    }

    // Real 배달의민족 예약주문 (scheduled ordering) opt-in -- see Merchant.kt's own doc
    // comment. Never forced on.
    @Transactional
    fun setAcceptsScheduledOrders(ownerUserId: String, accepts: Boolean): Merchant {
        val merchant = getMyMerchant(ownerUserId)
        merchant.acceptsScheduledOrders = accepts
        return merchantRepository.save(merchant)
    }

    // Real Baemin CEO app 영업일시중지 (temporarily pause business) -- see
    // Merchant.isAcceptingOrders's own doc comment. Explicit owner opt-out, never
    // forced; resuming is just as real and self-service (set true again).
    fun setAcceptingOrders(ownerUserId: String, accepting: Boolean): Merchant {
        val merchant = getMyMerchant(ownerUserId)
        merchant.isAcceptingOrders = accepting
        return merchantRepository.save(merchant)
    }

    // Real Baemin CEO app 휴무일 설정 (recurring weekly closed-day schedule) -- see
    // Merchant.closedWeekdays's own doc comment. Real values 1-7 (java.time.DayOfWeek's
    // own ISO-8601 numbering); an empty set clears back to "open every day".
    fun setClosedWeekdays(ownerUserId: String, weekdays: Set<Int>): Merchant {
        if (weekdays.any { it !in 1..7 }) {
            throw InvalidClosedWeekdaysException("Each weekday must be between 1 (Monday) and 7 (Sunday)")
        }
        val merchant = getMyMerchant(ownerUserId)
        merchant.closedWeekdays = if (weekdays.isEmpty()) null else weekdays.sorted().joinToString(",")
        return merchantRepository.save(merchant)
    }

    // Real category/cuisine -- powers restaurant categories + search/filter for Eats
    // (and Shopping, since both browse the same Merchant directory).
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

    // Real restaurant-card photo (2026-07-21) -- see Merchant.kt's own doc comment for
    // why this is a merchant-set URL, not an upload/storage pipeline.
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

    // Real photo gallery (itunda Maps redesign, 2026-08-28) -- see
    // Merchant.photoUrls' own doc comment. Same per-URL 500-char bound as setPhotoUrl;
    // capped at 20 photos (a real, small, genuinely-browsable gallery, not unbounded).
    @Transactional
    fun setPhotoUrls(ownerUserId: String, photoUrls: List<String>): Merchant {
        val trimmed = photoUrls.map { it.trim() }.filter { it.isNotEmpty() }
        if (trimmed.any { it.length > 500 }) throw InvalidPhotoUrlException("Each photo URL must be 500 characters or fewer")
        if (trimmed.size > 20) throw InvalidPhotoUrlException("At most 20 photos are supported")
        val merchant = getMyMerchant(ownerUserId)
        merchant.photoUrls = trimmed.joinToString(",").ifEmpty { null }
        return merchantRepository.save(merchant)
    }

    // Real merchant-set minimum order amount -- nullable; passing null explicitly
    // clears it back to "no minimum".
    @Transactional
    fun setMinOrderAmount(ownerUserId: String, minOrderAmount: BigDecimal?): Merchant {
        if (minOrderAmount != null && minOrderAmount < BigDecimal.ZERO) {
            throw InvalidMinOrderAmountException("Minimum order amount cannot be negative")
        }
        val merchant = getMyMerchant(ownerUserId)
        merchant.minOrderAmount = minOrderAmount
        return merchantRepository.save(merchant)
    }

    // Real merchant-set phone number -- see Merchant.kt's own doc comment for why this
    // is plain free text. Passing null explicitly clears it.
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

    // Real per-merchant kitchen-prep time -- see Merchant.avgPrepTimeMinutes's own doc
    // comment and DeliveryEtaEstimator's own doc comment for the full sourced account.
    // A real sanity bound: DeliveryEtaEstimator.MAX_DELIVERY_MINUTES already caps the
    // real customer-facing total at 90.
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
}
