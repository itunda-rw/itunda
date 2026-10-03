package rw.itunda.app.miniapps

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MiniAppSecurityContextTest {

    @AfterEach
    fun tearDown() {
        MiniAppSecurityContext.activeScopes = null
    }

    @Test
    fun `first party context allows all bridge calls`() {
        MiniAppSecurityContext.activeScopes = null
        assertTrue(MiniAppSecurityContext.isAllowed(null))
        assertTrue(MiniAppSecurityContext.isAllowed("account:read"))
        assertTrue(MiniAppSecurityContext.isAllowed("transactions:read"))
    }

    @Test
    fun `partner context allows only approved scopes`() {
        MiniAppSecurityContext.activeScopes = setOf("account:read", "transactions:read")
        assertTrue(MiniAppSecurityContext.isAllowed("account:read"))
        assertTrue(MiniAppSecurityContext.isAllowed("transactions:read"))
        assertFalse(MiniAppSecurityContext.isAllowed("profile:write"))
        assertFalse(MiniAppSecurityContext.isAllowed(null))
    }

    @Test
    fun `empty partner scope set denies every bridge call`() {
        MiniAppSecurityContext.activeScopes = emptySet()
        assertFalse(MiniAppSecurityContext.isAllowed("account:read"))
        assertFalse(MiniAppSecurityContext.isAllowed("transactions:read"))
        assertFalse(MiniAppSecurityContext.isAllowed(null))
    }
}
