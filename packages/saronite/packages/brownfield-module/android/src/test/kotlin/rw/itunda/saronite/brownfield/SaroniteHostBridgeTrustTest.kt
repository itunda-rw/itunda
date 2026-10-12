package rw.itunda.saronite.brownfield

import android.app.Activity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SaroniteHostBridgeTrustTest {
    private class DefaultHostBridge : SaroniteHostBridge {
        override fun getApiBaseUrl(): String = "https://api.example.invalid/"
        override fun getAuthToken(): String? = "test-token"
        override fun getSchemeUri(): String = "itunda://saronite"
        override fun onCloseView(activity: Activity?) = Unit
    }

    private class FirstPartyHostBridge : DefaultHostBridgeWithTrust() {
        override fun isFirstParty(): Boolean = true
    }

    private open class DefaultHostBridgeWithTrust : SaroniteHostBridge {
        override fun getApiBaseUrl(): String = "https://api.example.invalid/"
        override fun getAuthToken(): String? = "test-token"
        override fun getSchemeUri(): String = "itunda://saronite"
        override fun onCloseView(activity: Activity?) = Unit
    }

    @Test
    fun hostIsUntrustedByDefaultEvenWithAnAuthToken() {
        val host = DefaultHostBridge()
        assertFalse(host.isFirstParty())
        assertTrue("A session token must not implicitly grant first-party trust", host.getAuthToken() != null)
    }

    @Test
    fun firstPartyHostMustExplicitlyOptIn() {
        assertTrue(FirstPartyHostBridge().isFirstParty())
    }
}
