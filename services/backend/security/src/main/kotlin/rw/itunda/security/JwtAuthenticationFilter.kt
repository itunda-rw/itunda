package rw.itunda.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import rw.itunda.auth.JwtService
import rw.itunda.auth.TokenBlocklistService
import rw.itunda.core.security.CurrentUser

/**
 * Direct port of backend/src/middleware/auth.middleware.ts's requireAuth: rejects a
 * missing/malformed/expired/wrong-secret token, and rejects a refresh token used as an
 * access token. Unlike the Express version, this is wired globally from day one (see
 * SecurityConfig) instead of being written once and left unmounted on most routes.
 *
 * Also rejects a token whose `jti` is in the Redis revocation list — otherwise "logout"
 * would be purely cosmetic: a stateless JWT that's still within its expiry window would
 * keep authenticating successfully forever, no matter what a logout endpoint claims to do.
 */
@Component
class JwtAuthenticationFilter(
    private val jwtService: JwtService,
    private val tokenBlocklistService: TokenBlocklistService,
) : OncePerRequestFilter() {

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        val header = request.getHeader("Authorization")
        if (header != null && header.startsWith("Bearer ")) {
            val token = header.removePrefix("Bearer ")
            val decoded = jwtService.verify(token)
            if (decoded != null && !decoded.isRefresh && !tokenBlocklistService.isBlacklisted(decoded.jti)) {
                // ROLE_ prefix is Spring Security's own convention for hasRole("ADMIN") to
                // match against -- see SecurityConfig's /api/v1/system/** rule.
                val authorities = listOf(SimpleGrantedAuthority("ROLE_${decoded.role}"))
                val authentication = UsernamePasswordAuthenticationToken(CurrentUser(decoded.userId, decoded.role, decoded.deviceId), null, authorities)
                SecurityContextHolder.getContext().authentication = authentication
            }
        }
        filterChain.doFilter(request, response)
    }
}
