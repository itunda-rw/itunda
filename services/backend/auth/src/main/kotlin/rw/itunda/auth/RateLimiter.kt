package rw.itunda.auth

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Service
import java.time.Duration

class RateLimitExceededException(message: String) : RuntimeException(message)

/**
 * Fixed-window rate limiting via a single atomic Redis Lua script (INCR + conditional
 * EXPIRE) — the same class of mechanism Toss's own public Gateway write-up
 * (toss.tech/article/22910) lists as one of the Gateway's core responsibilities
 * alongside auth and circuit breaking, applied directly at each call site instead of a
 * separate gateway service — itunda doesn't have one, and standing one up purely for
 * this would be solving a problem this app doesn't have yet at its current
 * single-service scale.
 *
 * Originally closed SECURITY.md gap #4 ("No rate limiting... no brute-force protection
 * on login") scoped to just login/register; real coverage has since grown far past
 * that (147 call sites across 83 files as of 2026-09-04's Toss security research
 * sweep — savings/loans/p2p/messaging/merchant/rideshare/marketplace/stocks/account/
 * bills/etc. — with every real ledger-touching module now individually verified, not
 * just grep-counted), so treat this class as this app's general-purpose per-key rate
 * limiter, not a login-only tool. This count will keep drifting as new money-moving
 * endpoints ship — don't trust it as exact, re-grep `rateLimiter.checkLimit(` before
 * citing a number in a future audit.
 */
@Service
class RateLimiter(private val redisTemplate: StringRedisTemplate) {
    // Also returns the key's real remaining TTL (not just the configured window) so
    // RateLimitHeaderFilter can report an accurate X-RateLimit-Reset -- a caller mid-
    // window has less time left than the full window, and reporting the full window on
    // every response would overstate it more the closer a client gets to the reset.
    private val script = DefaultRedisScript(
        """
        local current = redis.call('INCR', KEYS[1])
        if tonumber(current) == 1 then
            redis.call('EXPIRE', KEYS[1], ARGV[1])
        end
        local ttl = redis.call('TTL', KEYS[1])
        return {current, ttl}
        """.trimIndent(),
        List::class.java,
    )

    /** Throws once more than `limit` calls for the same `key` land within `window`;
     * otherwise lets the call through silently. The INCR+EXPIRE pair runs as one atomic
     * EVAL, so two concurrent requests can't both observe "count == 1" and both skip
     * setting the TTL, which would otherwise leave the key permanently uncapped.
     *
     * Real API-developer-experience gap found 2026-09-05: none of this app's 147+ real
     * rate-limited endpoints (including the external Pay/Partner-Identity APIs real
     * third-party developers integrate against) ever exposed standard X-RateLimit-*
     * response headers -- a partner had no way to know they were close to a limit until
     * they were already rejected. Stashes the result here (a per-request ThreadLocal,
     * cleared by the filter itself) rather than changing this function's return type,
     * so none of the 147 existing call sites need to change to opt in. */
    fun checkLimit(key: String, limit: Int, window: Duration) {
        @Suppress("UNCHECKED_CAST")
        val result = redisTemplate.execute(script, listOf(key), window.seconds.toString()) as List<Long>
        val current = result[0]
        val resetSeconds = result[1].coerceAtLeast(0)
        RateLimitContext.set(RateLimitInfo(limit = limit, remaining = (limit - current).coerceAtLeast(0), resetSeconds = resetSeconds))
        if (current > limit) throw RateLimitExceededException("Too many attempts, please try again later")
    }
}
