package rw.itunda.core.geo

// Promoted out of ShoppingController's own private estimateDeliveryMinutes (2026-07-21)
// -- a fresh Uber Eats research pass (restaurantdive.com's coverage of Uber Eats' own
// delivery-tracker redesign: a real customer-facing "Latest Arrival By" time, shown
// alongside the delivery ETA, sourced from real internal research across nine countries)
// found the exact same real distance-based formula was needed a second time, for a
// real in-flight order's own estimated arrival once a rider is assigned -- not just the
// pre-order browse-time estimate ShoppingController already showed. Shared here rather
// than duplicated, same discipline FullTextSearchUtil already established for this
// codebase's own "same real formula needed in two places" pattern.
object DeliveryEtaEstimator {
    // Real, labeled ESTIMATE -- not measured historical delivery time (this backend has
    // never recorded one), same "computed from real distance, never fabricated"
    // discipline EatsOrderService.computeDeliveryFee already established for the
    // delivery fee itself. A real kitchen-prep floor plus a real distance/speed travel
    // estimate, rounded to the nearest 5 minutes the way every real delivery app's ETA
    // badge is displayed.
    private const val BASE_PREP_MINUTES = 15.0
    private const val ASSUMED_AVG_SPEED_KMH = 20.0
    private const val MIN_DELIVERY_MINUTES = 15
    private const val MAX_DELIVERY_MINUTES = 90

    // Real Uber Eats "busy merchant" delay bump (2026-08-16) -- see Uber's own official
    // "Managing busy delivery times" merchant help article: a busy restaurant's real
    // in-kitchen order backlog is a genuine, sourced cause of delivery delay, not a
    // fabricated formula. A flat, honest add-on to the existing estimate rather than a
    // second, separate delay model. BUSY_ORDER_THRESHOLD is itunda's own chosen
    // threshold (this backend has no historical throughput data to derive one from),
    // same "itunda's own chosen policy, not a claimed real figure this project has no
    // way to verify" honesty GiftVoucherController's own customerCodeValidity comment
    // already models.
    private const val BUSY_DELAY_MINUTES = 10.0
    const val BUSY_ORDER_THRESHOLD = 5L

    // Real per-merchant prep time (2026-08-16, Baemin's own "가게배달 배달시간 AI 예측") --
    // prepTimeMinutes is the merchant's own real self-reported value
    // (Merchant.avgPrepTimeMinutes); null (unset, or a merchant not passed at all)
    // falls back to the previous flat BASE_PREP_MINUTES constant, fully
    // backward-compatible with every existing caller/merchant. isBusy defaults false,
    // also fully backward-compatible with every existing call site.
    fun estimateDeliveryMinutes(distanceKm: Double, prepTimeMinutes: Int? = null, isBusy: Boolean = false): Int {
        val travelMinutes = (distanceKm / ASSUMED_AVG_SPEED_KMH) * 60.0
        val prep = prepTimeMinutes?.toDouble() ?: BASE_PREP_MINUTES
        val busyBump = if (isBusy) BUSY_DELAY_MINUTES else 0.0
        val total = prep + travelMinutes + busyBump
        val rounded = (Math.round(total / 5.0) * 5).toInt()
        return rounded.coerceIn(MIN_DELIVERY_MINUTES, MAX_DELIVERY_MINUTES)
    }
}
