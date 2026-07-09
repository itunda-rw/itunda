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
 * alongside auth and circuit breaking, just scoped here to the specific hot path that
 * actually needs it (login/register) instead of a separate gateway service — itunda
 * doesn't have one, and standing one up purely to rate-limit two endpoints would be
 * solving a problem this app doesn't have yet at its current single-service scale.
 *
 * Closes SECURITY.md gap #4: "No rate limiting... no brute-force protection on login."
 */
@Service
class RateLimiter(private val redisTemplate: StringRedisTemplate) {
    private val script = DefaultRedisScript(
        """
        local current = redis.call('INCR', KEYS[1])
        if tonumber(current) == 1 then
            redis.call('EXPIRE', KEYS[1], ARGV[1])
        end
        return current
        """.trimIndent(),
        Long::class.java,
    )

    /** Throws once more than `limit` calls for the same `key` land within `window`;
     * otherwise lets the call through silently. The INCR+EXPIRE pair runs as one atomic
     * EVAL, so two concurrent requests can't both observe "count == 1" and both skip
     * setting the TTL, which would otherwise leave the key permanently uncapped. */
    fun checkLimit(key: String, limit: Int, window: Duration) {
        val current = redisTemplate.execute(script, listOf(key), window.seconds.toString())
        if (current > limit) throw RateLimitExceededException("Too many attempts, please try again later")
    }
}
