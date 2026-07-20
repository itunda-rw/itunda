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

data class DecodedToken(
    val userId: String,
    val isRefresh: Boolean,
    val jti: String,
    val expiresAt: Instant,
    val role: String,
    // Real device binding (2026-07-20) -- null for a token minted before this feature
    // existed, or for any client that still doesn't send a deviceId at login/register
    // (an intentional, honest, gradual-rollout gap: see DeviceVerificationFilter's own
    // doc comment for why a null deviceId is treated as "this feature doesn't apply
    // yet," never as an automatic pass).
    val deviceId: String? = null,
)

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

    fun issueAccessToken(userId: String, phoneNumber: String, role: String, deviceId: String? = null): String {
        val builder = Jwts.builder()
            .id(UUID.randomUUID().toString())
            .subject(userId)
            .claim("phone", phoneNumber)
            .claim("role", role)
            .issuedAt(Date())
            .expiration(Date(System.currentTimeMillis() + 24 * 60 * 60 * 1000))
        if (deviceId != null) builder.claim("deviceId", deviceId)
        return builder.signWith(key).compact()
    }

    fun issueRefreshToken(userId: String, deviceId: String? = null): String {
        val builder = Jwts.builder()
            .id(UUID.randomUUID().toString())
            .subject(userId)
            .claim("type", "refresh")
            .issuedAt(Date())
            .expiration(Date(System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000))
        // Carried forward so a later refresh() can re-mint an access token with the same
        // real deviceId claim without the client needing to resend it every time.
        if (deviceId != null) builder.claim("deviceId", deviceId)
        return builder.signWith(key).compact()
    }

    /** Returns null on any invalid/expired/malformed token instead of throwing, mirroring
     * requireAuth's catch-all 401 in the Express middleware. */
    fun verify(token: String): DecodedToken? = try {
        val claims: Claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).payload
        DecodedToken(
            userId = claims.subject,
            isRefresh = claims["type"] == "refresh",
            jti = claims.id,
            expiresAt = claims.expiration.toInstant(),
            // Refresh tokens never carried a role claim (they're not used to authorize
            // requests, only to mint a fresh access token) -- default to the least
            // privilege rather than throwing, since callers that care about role never
            // call verify() on a refresh token's result expecting authorization data.
            role = claims["role"] as? String ?: "USER",
            deviceId = claims["deviceId"] as? String,
        )
    } catch (_: JwtException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}
