package rw.itunda.auth

import io.jsonwebtoken.Claims
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

data class DecodedToken(val userId: String, val isRefresh: Boolean, val jti: String, val expiresAt: Instant)

/**
 * Same shape as backend/src/controllers/auth.controller.ts / auth.middleware.ts: HS256,
 * `sub` = userId, a `type: "refresh"` claim distinguishing refresh from access tokens,
 * 24h access / 7d refresh expiry. Kept identical so the two backends are directly
 * comparable during migration, not just "similar." Every token now also carries a `jti`
 * (unique token id) — needed for Redis-backed revocation (see TokenBlocklistService):
 * you can't blacklist "the token," a JWT is never stored anywhere to compare against,
 * you can only blacklist an id extracted from it.
 */
@Service
class JwtService(
    @Value("\${itunda.jwt.secret:itunda-dev-secret-do-not-use-in-production}") secret: String,
) {
    // Same documented caveat as the Express backend's JWT_SECRET fallback (see
    // SECURITY.md): fine for local/demo, must be overridden before any real deployment.
    // HS256 requires a key of at least 256 bits (32 bytes); this throws at startup rather
    // than silently zero-padding a too-short secret into a weaker key.
    private val key: SecretKey = Keys.hmacShaKeyFor(secret.toByteArray(Charsets.UTF_8))

    fun issueAccessToken(userId: String, phoneNumber: String): String =
        Jwts.builder()
            .id(UUID.randomUUID().toString())
            .subject(userId)
            .claim("phone", phoneNumber)
            .issuedAt(Date())
            .expiration(Date(System.currentTimeMillis() + 24 * 60 * 60 * 1000))
            .signWith(key)
            .compact()

    fun issueRefreshToken(userId: String): String =
        Jwts.builder()
            .id(UUID.randomUUID().toString())
            .subject(userId)
            .claim("type", "refresh")
            .issuedAt(Date())
            .expiration(Date(System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000))
            .signWith(key)
            .compact()

    /** Returns null on any invalid/expired/malformed token instead of throwing, mirroring
     * requireAuth's catch-all 401 in the Express middleware. */
    fun verify(token: String): DecodedToken? = try {
        val claims: Claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).payload
        DecodedToken(
            userId = claims.subject,
            isRefresh = claims["type"] == "refresh",
            jti = claims.id,
            expiresAt = claims.expiration.toInstant(),
        )
    } catch (_: JwtException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}
