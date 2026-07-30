package rw.itunda.app.web

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class IdempotencyExceptionHandlerTest {
    @Test
    fun `a stale optimistic-lock write returns a retryable conflict`() {
        val response = IdempotencyExceptionHandler().handleConcurrentUpdate()

        assertEquals(409, response.statusCode.value())
        assertEquals("RESOURCE_STATE_CHANGED", response.body?.code)
    }
}
