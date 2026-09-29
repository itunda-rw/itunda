package rw.itunda.core.observability

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

/**
 * Preserves the gateway trace across Spring handling and makes it available to the
 * logging context. Only a constrained UUID-shaped value is accepted so an arbitrary
 * request header cannot inject control characters or unbounded data into logs.
 *
 * Real gap found live (2026-09-14, sibling-asymmetry sweep): this is the ONLY place
 * in the whole backend that ever calls `MDC.put`, but it lived in
 * `rw.itunda.app.observability` -- a package none of the 14 independently-deployable
 * extracted services ever scan (same exact bug class the same day's
 * `IdempotencyExceptionHandler` fix already closed -- see that class's own doc
 * comment). Every one of the 14 services' own `application.yml` ships the identical
 * `[requestId=%X{requestId:-}]` logging pattern copy-pasted from `:app`'s, but with
 * this filter never registered outside the monolith, `requestId` was NEVER populated
 * in any of them -- every log line those 14 JVMs ever emit prints a permanently-empty
 * `[requestId=]`, and the `X-Request-ID` response header used for gateway/client-side
 * tracing was never set either. Moved into `rw.itunda.core.observability` (already
 * scanned by every service, including `:app` itself, whose own
 * `scanBasePackages = ["rw.itunda"]` is a superset) so the fix applies everywhere with
 * no per-service change needed.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RequestCorrelationFilter : OncePerRequestFilter() {
    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        val requestId = request.getHeader(REQUEST_ID_HEADER)?.takeIf(UUID_PATTERN::matches) ?: UUID.randomUUID().toString()
        response.setHeader(REQUEST_ID_HEADER, requestId)
        MDC.put(MDC_KEY, requestId)
        try {
            filterChain.doFilter(request, response)
        } finally {
            MDC.remove(MDC_KEY)
        }
    }

    companion object {
        const val REQUEST_ID_HEADER = "X-Request-ID"
        const val MDC_KEY = "requestId"
        private val UUID_PATTERN = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
    }
}
