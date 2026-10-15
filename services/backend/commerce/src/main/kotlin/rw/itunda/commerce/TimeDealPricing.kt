package rw.itunda.commerce

import rw.itunda.core.repository.TimeDealRepository
import java.math.BigDecimal
import java.time.Instant

/**
 * Resolves the price for one commerce order line while reserving time-deal inventory
 * atomically. The caller supplies the existing normal-price calculation as a fallback,
 * so time deals cannot change ordinary price-tier behavior.
 *
 * Must run inside the caller's order transaction: a successful reservation is committed
 * or rolled back with the order, and the conditional update prevents concurrent orders
 * from claiming the same remaining deal quantity.
 */
internal fun resolveTimeDealUnitPrice(
    repository: TimeDealRepository,
    productId: String,
    quantity: Int,
    now: Instant,
    fallbackPrice: () -> BigDecimal,
): BigDecimal {
    val activeDeal = repository.findActiveDealForProduct(productId, now)
        ?: return fallbackPrice()

    if (activeDeal.remainingQuantity < quantity) return fallbackPrice()

    // The read selects the candidate deal; only the conditional UPDATE grants inventory.
    // If another checkout wins first, this line uses normal pricing instead of overselling.
    val reserved = repository.reserveQuantityIfAvailable(activeDeal.id, quantity, now) == 1
    return if (reserved) activeDeal.dealPrice else fallbackPrice()
}
