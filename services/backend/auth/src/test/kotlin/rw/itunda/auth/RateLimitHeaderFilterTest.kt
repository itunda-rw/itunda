package rw.itunda.auth

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class RateLimitHeaderFilterTest {
    private val filter = RateLimitHeaderFilter()

    @Test
    fun `reports the real remaining count and reset seconds, and clears the context afterwards`() {
        val request = MockHttpServletRequest()
        val response = MockHttpServletResponse()

        filter.doFilter(request, response) { _, _ ->
            RateLimitContext.set(RateLimitInfo(limit = 30, remaining = 18, resetSeconds = 1800))
        }

        assertEquals("30", response.getHeader("X-RateLimit-Limit"))
        assertEquals("18", response.getHeader("X-RateLimit-Remaining"))
        assertEquals("1800", response.getHeader("X-RateLimit-Reset"))
        assertNull(RateLimitContext.get())
    }

    @Test
    fun `also sets the standard Retry-After header on a real 429`() {
        val request = MockHttpServletRequest()
        val response = MockHttpServletResponse()

        filter.doFilter(request, response) { _, res ->
            RateLimitContext.set(RateLimitInfo(limit = 30, remaining = 0, resetSeconds = 900))
            (res as MockHttpServletResponse).status = 429
        }

        assertEquals("900", response.getHeader("Retry-After"))
    }

    @Test
    fun `does not set Retry-After on a real successful response`() {
        val request = MockHttpServletRequest()
        val response = MockHttpServletResponse()

        filter.doFilter(request, response) { _, _ ->
            RateLimitContext.set(RateLimitInfo(limit = 30, remaining = 29, resetSeconds = 3599))
        }

        assertNull(response.getHeader("Retry-After"))
    }

    @Test
    fun `a request that never called checkLimit gets no rate-limit headers at all`() {
        val request = MockHttpServletRequest()
        val response = MockHttpServletResponse()

        filter.doFilter(request, response) { _, _ -> Unit }

        assertNull(response.getHeader("X-RateLimit-Limit"))
    }
}
