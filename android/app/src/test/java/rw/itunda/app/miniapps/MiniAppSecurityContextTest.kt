package rw.itunda.app.miniapps

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniAppSecurityContextTest {
    @After
    fun resetToFirstPartyContext() {
        MiniAppSecurityContext.activeScopes = null
    }

    @Test
    fun firstPartyContextAllowsWalletProjection() {
        MiniAppSecurityContext.activeScopes = null

        // getWalletBalance() uses requireScope(null): only the first-party context
        // may invoke a bridge method that has no partner-grantable scope.
        assertTrue(MiniAppSecurityContext.isAllowed(null))
    }

    @Test
    fun partnerWithAccountReadCannotInvokeFirstPartyOnlyWalletProjection() {
        MiniAppSecurityContext.activeScopes = setOf("account:read")

        assertTrue(MiniAppSecurityContext.isAllowed("account:read"))
        assertFalse(MiniAppSecurityContext.isAllowed(null))
    }

    @Test
    fun partnerWithNoScopesCannotInvokeWalletProjection() {
        MiniAppSecurityContext.activeScopes = emptySet()

        assertFalse(MiniAppSecurityContext.isAllowed(null))
    }
}
