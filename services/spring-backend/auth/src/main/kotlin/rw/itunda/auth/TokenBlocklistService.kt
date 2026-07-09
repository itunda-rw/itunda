package rw.itunda.auth

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant

/**
 * Real reason for Redis in this backend, not "Toss has one so we should too": JWTs are
 * stateless by design, so there is no server-side record to delete on logout — the only
 * way to revoke a still-unexpired token is a denylist that every request checks, and
 * that denylist must itself expire keys automatically (a token blacklisted forever would
 * leak memory as a pure function of login volume) — which is exactly Redis's TTL
 * feature, not something a relational table gives you for free. This closes what
 * SECURITY.md's Prioritized Remediation list calls out as open ("Token revocation...
 * not yet built") since before this, there was no logout endpoint at all.
 */
@Service
class TokenBlocklistService(private val redisTemplate: StringRedisTemplate) {

    fun blacklist(jti: String, expiresAt: Instant) {
        val ttl = Duration.between(Instant.now(), expiresAt)
        if (ttl.isNegative || ttl.isZero) return // already expired on its own, nothing to track
        redisTemplate.opsForValue().set(redisKey(jti), "revoked", ttl)
    }

    fun isBlacklisted(jti: String): Boolean = redisTemplate.hasKey(redisKey(jti))

    private fun redisKey(jti: String) = "itunda:revoked-jti:$jti"
}
