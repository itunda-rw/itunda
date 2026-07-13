package rw.itunda.core.batch

/**
 * Real plugin point for the offline-queue batch endpoint (`offline` module,
 * `POST /api/v1/actions/batch`) -- see docs/TOSS_PARITY_MATRIX.md's Offline row. Lives in
 * :core (like every other cross-module contract, e.g. ProviderConnector) so feature modules
 * can each implement it for their own action types without depending on each other or on
 * `offline` directly; Spring's component scan (`scanBasePackages = ["rw.itunda"]` in
 * ItundaApplication) picks up every implementation across the whole app regardless of which
 * Gradle module declares it, and the batch controller just injects `List<BatchActionHandler>`.
 *
 * A handler never throws for a normal business-level decline (insufficient funds, provider
 * decline, idempotency conflict) -- it catches its own domain's exceptions and maps them to
 * the same status code its own REST controller already uses for that error, so one action
 * failing inside a batch doesn't take the rest of the batch down with it.
 */
interface BatchActionHandler {
    val actionType: String

    fun handle(userId: String, idempotencyKey: String, body: Map<String, Any?>): Pair<Int, Map<String, Any?>>
}

fun Map<String, Any?>.requiredString(key: String): String =
    this[key]?.toString() ?: throw IllegalArgumentException("Missing required field: $key")

fun Map<String, Any?>.optionalString(key: String): String? = this[key]?.toString()

fun Map<String, Any?>.requiredBigDecimal(key: String): java.math.BigDecimal =
    this[key]?.let { java.math.BigDecimal(it.toString()) } ?: throw IllegalArgumentException("Missing required field: $key")
