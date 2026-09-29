package rw.itunda.core.observability

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.slf4j.MDC
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import java.util.UUID

class RequestCorrelationFilterTest {
    private val filter = RequestCorrelationFilter()

    @Test
    fun `preserves a valid gateway request id and clears MDC afterwards`() {
        val requestId = UUID.randomUUID().toString()
        val request = MockHttpServletRequest().apply { addHeader(RequestCorrelationFilter.REQUEST_ID_HEADER, requestId) }
        val response = MockHttpServletResponse()

        filter.doFilter(request, response) { _, _ ->
            assertEquals(requestId, MDC.get(RequestCorrelationFilter.MDC_KEY))
        }

        assertEquals(requestId, response.getHeader(RequestCorrelationFilter.REQUEST_ID_HEADER))
        assertNull(MDC.get(RequestCorrelationFilter.MDC_KEY))
    }

    @Test
    fun `replaces an unsafe request id`() {
        val request = MockHttpServletRequest().apply { addHeader(RequestCorrelationFilter.REQUEST_ID_HEADER, "unsafe\nvalue") }
        val response = MockHttpServletResponse()

        filter.doFilter(request, response) { _, _ -> Unit }

        assertTrue(UUID_PATTERN.matches(response.getHeader(RequestCorrelationFilter.REQUEST_ID_HEADER)!!))
    }

    companion object {
        private val UUID_PATTERN = Regex("^[0-9a-f-]{36}$")
    }
}
