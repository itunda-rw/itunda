package rw.itunda.saronite.brownfield

import android.app.Activity
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.ArgumentMatchers.eq

class SaroniteBrownfieldModuleTrustTest {
    @Test
    fun untrustedHostIsRejectedBeforeAuthOrNetworkConfigurationIsRead() {
        val host = object : SaroniteHostBridge {
            override fun getApiBaseUrl(): String =
                throw AssertionError("Untrusted host must not reach URL construction")

            override fun getAuthToken(): String? =
                throw AssertionError("Untrusted host must be rejected before reading credentials")

            override fun isFirstParty(): Boolean = false
            override fun getSchemeUri(): String = "itunda://saronite"
            override fun onCloseView(activity: Activity?) = Unit
        }
        val context = mock(ReactApplicationContext::class.java)
        val promise = mock(Promise::class.java)
        val module = SaroniteBrownfieldModule(context, host)

        module.getWalletBalance(promise)

        verify(promise).reject(
            eq("SARONITE_SCOPE_DENIED"),
            eq("Wallet balance is available only to first-party mini-apps"),
        )
    }
}
