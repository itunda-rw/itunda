package rw.itunda.auth

/** The most recent [RateLimiter.checkLimit] result for the current request thread --
 * see [RateLimitHeaderFilter] for why this exists instead of changing checkLimit's
 * return type. A request can call checkLimit more than once (rate-limited on more than
 * one key); the filter reports whichever call happened LAST, matching the intuition
 * that the final gate actually applied to this specific response. */
data class RateLimitInfo(val limit: Int, val remaining: Long, val resetSeconds: Long)

object RateLimitContext {
    private val holder = ThreadLocal<RateLimitInfo?>()

    fun set(info: RateLimitInfo) = holder.set(info)

    fun get(): RateLimitInfo? = holder.get()

    fun clear() = holder.remove()
}
