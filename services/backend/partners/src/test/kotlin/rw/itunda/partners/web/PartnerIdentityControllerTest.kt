package rw.itunda.partners.web

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.http.HttpStatus
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.core.domain.IdentityVerificationRequest
import rw.itunda.core.domain.IdentityVerificationStatus
import rw.itunda.partners.IdentityVerificationRequestNotFoundException
import rw.itunda.partners.IdentityVerificationService
import rw.itunda.partners.InvalidApiKeyException
import rw.itunda.partners.PartnerSuspendedException
import java.time.Instant

/**
 * First test coverage for PartnerIdentityController's REST layer -- the real
 * partner-facing side of "verify with itunda" (a partner has no itunda-user JWT,
 * it authenticates with its own API key, same permitAll-at-Spring-Security-layer
 * shape PartnerControllerTest already establishes).
 */
class PartnerIdentityControllerTest : BehaviorSpec({

    Given("a real partner creating a new verification request") {
        val service = mockk<IdentityVerificationService>()
        val objectMapper = ObjectMapper()
        val controller = PartnerIdentityController(service, objectMapper)
        val request = IdentityVerificationRequest(id = "idverify_1", partnerId = "partner_1", expiresAt = Instant.now().plusSeconds(300))
        every { service.createRequest("sk_test_key") } returns request

        When("creating it") {
            val response = controller.createRequest("sk_test_key")

            Then("it real-delegates and returns a real itunda://verify deep link for the real request id") {
                verify(exactly = 1) { service.createRequest("sk_test_key") }
                response.statusCode shouldBe HttpStatus.CREATED
                response.body?.get("verifyUrl") shouldBe "itunda://verify/idverify_1"
            }
        }
    }

    Given("a real partner polling a not-yet-approved request") {
        val service = mockk<IdentityVerificationService>()
        val objectMapper = ObjectMapper()
        val controller = PartnerIdentityController(service, objectMapper)
        val request = IdentityVerificationRequest(
            id = "idverify_1", partnerId = "partner_1", status = IdentityVerificationStatus.PENDING,
            expiresAt = Instant.now().plusSeconds(300),
        )
        every { service.getForPartner("idverify_1", "sk_test_key") } returns request

        When("fetching it") {
            val response = controller.getRequest("idverify_1", "sk_test_key")

            Then("it never leaks a disclosed payload/signature that doesn't exist yet") {
                verify(exactly = 1) { service.getForPartner("idverify_1", "sk_test_key") }
                response.body?.get("identity") shouldBe null
                response.body?.get("signature") shouldBe null
            }
        }
    }

    Given("a real partner polling an APPROVED request") {
        val service = mockk<IdentityVerificationService>()
        val objectMapper = ObjectMapper()
        val controller = PartnerIdentityController(service, objectMapper)
        val request = IdentityVerificationRequest(
            id = "idverify_1", partnerId = "partner_1", status = IdentityVerificationStatus.APPROVED,
            expiresAt = Instant.now().plusSeconds(300),
            disclosedPayloadJson = """{"firstName":"Uwase"}""", signature = "real-signature-bytes",
        )
        every { service.getForPartner("idverify_1", "sk_test_key") } returns request

        When("fetching it") {
            val response = controller.getRequest("idverify_1", "sk_test_key")

            Then("it real-discloses the real payload and signature the user actually consented to") {
                @Suppress("UNCHECKED_CAST")
                val identity = response.body?.get("identity") as Map<String, Any?>
                identity["firstName"] shouldBe "Uwase"
                response.body?.get("signature") shouldBe "real-signature-bytes"
            }
        }
    }

    Given("a request for the real Ed25519 public key") {
        val service = mockk<IdentityVerificationService>()
        val objectMapper = ObjectMapper()
        val controller = PartnerIdentityController(service, objectMapper)
        every { service.publicKeyBase64() } returns "base64-public-key"

        When("fetching it") {
            val response = controller.getPublicKey()

            Then("it real-delegates and reports the real key, no auth required") {
                response.body?.get("publicKey") shouldBe "base64-public-key"
                response.body?.get("algorithm") shouldBe "Ed25519"
            }
        }
    }

    listOf(
        Triple(IdentityVerificationRequestNotFoundException("Not found") as RuntimeException, HttpStatus.NOT_FOUND, "VERIFICATION_REQUEST_NOT_FOUND"),
        Triple(InvalidApiKeyException("Unauthorized"), HttpStatus.UNAUTHORIZED, "INVALID_API_KEY"),
        Triple(PartnerSuspendedException("Forbidden"), HttpStatus.FORBIDDEN, "PARTNER_SUSPENDED"),
        Triple(RateLimitExceededException("Too many requests"), HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED"),
    ).forEach { (exception, expectedStatus, expectedCode) ->
        Given("a real ${exception::class.simpleName}") {
            val service = mockk<IdentityVerificationService>()
            val objectMapper = ObjectMapper()
            val controller = PartnerIdentityController(service, objectMapper)

            When("its exception handler maps it to a real HTTP response") {
                val response = when (exception) {
                    is IdentityVerificationRequestNotFoundException -> controller.handleNotFound(exception)
                    is InvalidApiKeyException -> controller.handleInvalidApiKey(exception)
                    is PartnerSuspendedException -> controller.handleSuspended(exception)
                    is RateLimitExceededException -> controller.handleRateLimit(exception)
                    else -> error("unexpected exception type")
                }

                Then("it maps to $expectedStatus with code $expectedCode, not a generic 500") {
                    response.statusCode shouldBe expectedStatus
                    response.body?.code shouldBe expectedCode
                }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
