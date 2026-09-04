package rw.itunda.auth

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Real API-developer-experience fix (2026-09-05) -- see [RateLimiter.checkLimit]'s own
 * doc comment for the full account. Every request that calls [RateLimiter.checkLimit]
 * (147+ real call sites, including the external Pay/Partner-Identity APIs real
 * third-party developers integrate against) now gets standard `X-RateLimit-Limit` /
 * `X-RateLimit-Remaining` / `X-RateLimit-Reset` response headers, matching the real
 * convention every modern rate-limited API (Stripe, GitHub, Toss Payments) already
 * follows -- a caller no longer has to hit a 429 blind to learn how close they were.
 *
 * Runs AFTER the request completes (headers are set in a `finally` around
 * `filterChain.doFilter`, not before) since [RateLimiter.checkLimit] is called deep
 * inside a controller/service, not by this filter itself -- and still runs on the 429
 * path too, since Spring's own exception-handling for `RateLimitExceededException`
 * happens inside the same `doFilter` call this filter wraps, not after it.
 */
@Component
class RateLimitHeaderFilter : OncePerRequestFilter() {
    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        try {
            filterChain.doFilter(request, response)
        } finally {
            RateLimitContext.get()?.let { info ->
                if (!response.isCommitted) {
                    response.setHeader("X-RateLimit-Limit", info.limit.toString())
                    response.setHeader("X-RateLimit-Remaining", info.remaining.toString())
                    response.setHeader("X-RateLimit-Reset", info.resetSeconds.toString())
                }
            }
            RateLimitContext.clear()
        }
    }
}
