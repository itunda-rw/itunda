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

    fun estimateDeliveryMinutes(distanceKm: Double): Int {
        val travelMinutes = (distanceKm / ASSUMED_AVG_SPEED_KMH) * 60.0
        val total = BASE_PREP_MINUTES + travelMinutes
        val rounded = (Math.round(total / 5.0) * 5).toInt()
        return rounded.coerceIn(MIN_DELIVERY_MINUTES, MAX_DELIVERY_MINUTES)
    }
}
