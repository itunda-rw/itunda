package rw.itunda.core.provider

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import java.time.Instant
import java.util.Base64
import java.util.UUID

class MtnMomoNotConfiguredException(message: String) : RuntimeException(message)
class MtnMomoRequestFailedException(message: String) : RuntimeException(message)

data class MtnMomoConnectivityResult(
    val referenceId: String,
    val status: String,
    val financialTransactionId: String?,
    val latencyMs: Long,
)

/**
 * A real client for MTN's actual MoMo Collection sandbox
 * (sandbox.momodeveloper.mtn.com), built against the real, documented sandbox flow
 * (momodeveloper.mtn.com/get-started): OAuth token → RequestToPay → poll status.
 * Free, self-service developer signup -- no vendor/regulatory approval needed, unlike
 * NIDA or a real production MTN OVA relationship (see docs/TOSS_PARITY_MATRIX.md's
 * Transfer row for that distinction).
 *
 * Deliberately NOT wired into `ProviderConnector`/`SimulatedProviderConnector` or any
 * real user-facing transfer/bill flow: MTN's sandbox only accepts EUR (not RWF) and
 * only behaves correctly against its own mocked EUM (End User Mock) test MSISDNs, not
 * real Rwandan phone numbers or real itunda user amounts -- silently substituting this
 * into real user flows once someone configures credentials would send real users' RWF
 * amounts and real phone numbers into an environment that isn't built to handle them,
 * which is worse than staying simulated, not better (same "don't trade a real gap for
 * a real bug" discipline the iOS mini-app host investigation already established). This
 * is instead a real, live, administratively-triggered connectivity proof -- see
 * MtnMomoAdminController -- confirming the integration genuinely reaches MTN's real
 * infrastructure, is a separate, deliberately-scoped-smaller claim than "itunda routes
 * real transfers through MTN," which remains correctly `demo`/blocked until a real
 * production MTN relationship (KYC, an OVA dashboard, RWF-capable production
 * credentials) exists.
 */
@Component
class MtnMomoSandboxClient(
    @Value("\${itunda.mtn-momo.subscription-key:}") private val subscriptionKey: String,
    @Value("\${itunda.mtn-momo.user-id:}") private val userId: String,
    @Value("\${itunda.mtn-momo.api-key:}") private val apiKey: String,
    @Value("\${itunda.mtn-momo.base-url:https://sandbox.momodeveloper.mtn.com}") baseUrl: String,
) {
    private val restClient = RestClient.create(baseUrl)

    val isConfigured: Boolean
        get() = subscriptionKey.isNotBlank() && userId.isNotBlank() && apiKey.isNotBlank()

    @Volatile private var cachedToken: String? = null
    @Volatile private var cachedTokenExpiresAt: Instant = Instant.EPOCH

    /**
     * A real end-to-end round trip against MTN's real sandbox: fetch a real OAuth
     * token (cached for its real `expires_in`, refreshed once expired), submit a real
     * RequestToPay against MTN's own documented EUM test payer, then poll its real
     * status a few times. Fixed EUR test amount -- the sandbox rejects any other
     * currency -- this proves connectivity/protocol correctness, not a real money
     * movement (itunda's own ledger is untouched by this call).
     */
    fun testConnectivity(): MtnMomoConnectivityResult {
        if (!isConfigured) {
            throw MtnMomoNotConfiguredException(
                "MTN MoMo sandbox credentials not configured -- set MTN_MOMO_SUBSCRIPTION_KEY/" +
                    "MTN_MOMO_USER_ID/MTN_MOMO_API_KEY (see momodeveloper.mtn.com for a free sandbox account)",
            )
        }
        val start = System.currentTimeMillis()
        val token = getAccessToken()
        val referenceId = UUID.randomUUID().toString()

        try {
            restClient.post()
                .uri("/collection/v1_0/requesttopay")
                .header("Authorization", "Bearer $token")
                .header("Ocp-Apim-Subscription-Key", subscriptionKey)
                .header("X-Target-Environment", "sandbox")
                .header("X-Reference-Id", referenceId)
                .header("Content-Type", "application/json")
                .body(
                    mapOf(
                        "amount" to "1.0",
                        "currency" to "EUR",
                        "externalId" to referenceId,
                        // MTN's own documented EUM (End User Mock) sandbox test payer --
                        // sandbox.momodeveloper.mtn.com only recognizes its own mocked test
                        // numbers, not real Rwandan MSISDNs (see class doc comment above).
                        "payer" to mapOf("partyIdType" to "MSISDN", "partyId" to "256774290781"),
                        "payerMessage" to "itunda real sandbox connectivity proof",
                        "payeeNote" to "itunda real sandbox connectivity proof",
                    ),
                )
                .retrieve()
                .toBodilessEntity()
        } catch (e: RestClientResponseException) {
            throw MtnMomoRequestFailedException("RequestToPay failed: ${e.statusCode} ${e.responseBodyAsString}")
        }

        var status = "PENDING"
        var financialTransactionId: String? = null
        // A handful of short polls -- the sandbox typically resolves within seconds;
        // this never blocks a real user request since it's only ever called from the
        // real ADMIN-only connectivity-test endpoint, not any user-facing flow.
        repeat(5) {
            if (status != "PENDING") return@repeat
            Thread.sleep(1500)
            val statusResponse = try {
                restClient.get()
                    .uri("/collection/v1_0/requesttopay/$referenceId")
                    .header("Authorization", "Bearer $token")
                    .header("Ocp-Apim-Subscription-Key", subscriptionKey)
                    .header("X-Target-Environment", "sandbox")
                    .retrieve()
                    .body(Map::class.java)
            } catch (e: RestClientResponseException) {
                throw MtnMomoRequestFailedException("Status check failed: ${e.statusCode} ${e.responseBodyAsString}")
            }
            status = statusResponse?.get("status") as? String ?: "PENDING"
            financialTransactionId = statusResponse?.get("financialTransactionId") as? String
        }

        return MtnMomoConnectivityResult(
            referenceId = referenceId,
            status = status,
            financialTransactionId = financialTransactionId,
            latencyMs = System.currentTimeMillis() - start,
        )
    }

    private fun getAccessToken(): String {
        cachedToken?.let { token -> if (Instant.now().isBefore(cachedTokenExpiresAt)) return token }

        val credentials = Base64.getEncoder().encodeToString("$userId:$apiKey".toByteArray())
        val response = try {
            restClient.post()
                .uri("/collection/token/")
                .header("Authorization", "Basic $credentials")
                .header("Ocp-Apim-Subscription-Key", subscriptionKey)
                .header("X-Target-Environment", "sandbox")
                .retrieve()
                .body(Map::class.java)
        } catch (e: RestClientResponseException) {
            throw MtnMomoRequestFailedException("Token request failed: ${e.statusCode} ${e.responseBodyAsString}")
        }

        val token = response?.get("access_token") as? String
            ?: throw MtnMomoRequestFailedException("Token response missing access_token")
        // expires_in is real seconds-until-expiry from MTN's own response, not assumed --
        // a 60s safety margin so a request in flight doesn't get a token that expires mid-call.
        val expiresInSeconds = (response["expires_in"] as? Number)?.toLong() ?: 3600L
        cachedToken = token
        cachedTokenExpiresAt = Instant.now().plusSeconds((expiresInSeconds - 60).coerceAtLeast(60))
        return token
    }
}
