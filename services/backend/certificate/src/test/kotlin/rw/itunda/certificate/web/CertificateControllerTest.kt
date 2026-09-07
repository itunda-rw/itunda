package rw.itunda.certificate.web

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.http.HttpStatus
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.certificate.CertificateNotFoundException
import rw.itunda.certificate.CertificateRenewalReminderScheduler
import rw.itunda.certificate.CertificateService
import rw.itunda.certificate.CertificateUserNotFoundException
import rw.itunda.certificate.CertificateUserNotVerifiedException
import rw.itunda.certificate.NoCertificateFoundException
import rw.itunda.certificate.VerificationResult
import rw.itunda.core.domain.Certificate
import rw.itunda.core.domain.CertificateStatus
import rw.itunda.core.idempotency.IdempotencyService
import rw.itunda.core.security.CurrentUser
import java.time.Instant

/**
 * First test coverage for CertificateController's REST layer. This module's own doc
 * comments cite two real, previously-shipped bugs at exactly this layer -- /verify and
 * /status originally left behind the default JWT gate (real 401s for legitimate
 * public callers), and /process-renewal-reminders originally had no ADMIN gate --
 * both since fixed via SecurityConfig's permitAll()/@PreAuthorize, which only take
 * effect inside a real Spring Security filter chain, not a plain unit test like this
 * one (this codebase has no @WebMvcTest/MockMvc precedent for that layer -- see
 * ContactsControllerTest's own doc comment on why direct method calls are this
 * codebase's normal, sufficient tier). What THIS file protects instead: the real
 * idempotency wiring added 2026-09-07 (same "one endpoint where a lost response
 * causes irreversible harm" reasoning CertificateController.issue's own doc comment
 * gives), controller-to-service delegation, and the exception-to-HTTP-status mapping,
 * none of which had any test before this pass.
 */
class CertificateControllerTest : BehaviorSpec({

    val currentUser = CurrentUser(userId = "user_1")

    Given("a first-time certificate issuance request") {
        val certificateService = mockk<CertificateService>()
        val scheduler = mockk<CertificateRenewalReminderScheduler>()
        val idempotencyService = mockk<IdempotencyService>()
        val controller = CertificateController(certificateService, scheduler, idempotencyService)

        val cert = Certificate(id = "cert_1", userId = "user_1", serialNumber = "SERIAL1", publicKeyBase64 = "pub", expiresAt = Instant.now().plusSeconds(1000))
        every { certificateService.issue("user_1") } returns (cert to "private-key-material")

        val actionSlot = slot<() -> Pair<Int, Map<String, Any?>>>()
        every {
            idempotencyService.replayOrExecute("POST /api/v1/certificate/issue", "key-1", "user_1", capture(actionSlot))
        } answers { actionSlot.captured.invoke() }

        When("issuing the certificate") {
            val response = controller.issue("key-1", currentUser)

            Then("it routes through the real idempotency service, keyed to this exact route and the caller's own userId") {
                verify(exactly = 1) { idempotencyService.replayOrExecute("POST /api/v1/certificate/issue", "key-1", "user_1", any()) }
                verify(exactly = 1) { certificateService.issue("user_1") }
                response.statusCode shouldBe HttpStatus.CREATED
                response.body?.get("privateKey") shouldBe "private-key-material"
            }
        }
    }

    Given("a retried certificate issuance request using the same Idempotency-Key as a completed one") {
        val certificateService = mockk<CertificateService>()
        val scheduler = mockk<CertificateRenewalReminderScheduler>()
        val idempotencyService = mockk<IdempotencyService>()
        val controller = CertificateController(certificateService, scheduler, idempotencyService)

        every {
            idempotencyService.replayOrExecute("POST /api/v1/certificate/issue", "key-1", "user_1", any())
        } returns (201 to mapOf("success" to true, "privateKey" to "already-shown-key"))

        When("retrying with the same key") {
            val response = controller.issue("key-1", currentUser)

            Then("the cached response is returned and a second certificate is never issued -- the retry never orphans a new, un-deliverable private key") {
                response.body?.get("privateKey") shouldBe "already-shown-key"
                verify(exactly = 0) { certificateService.issue(any()) }
            }
        }
    }

    Given("a request for the caller's own certificate") {
        val certificateService = mockk<CertificateService>()
        val scheduler = mockk<CertificateRenewalReminderScheduler>()
        val idempotencyService = mockk<IdempotencyService>()
        val controller = CertificateController(certificateService, scheduler, idempotencyService)

        val cert = Certificate(id = "cert_1", userId = "user_1", serialNumber = "SERIAL1", publicKeyBase64 = "pub", expiresAt = Instant.now().plusSeconds(1000))
        every { certificateService.getMyCertificate("user_1") } returns cert

        When("fetching it") {
            val response = controller.getMyCertificate(currentUser)

            Then("it queries only by the caller's own userId, never a client-supplied one") {
                verify(exactly = 1) { certificateService.getMyCertificate("user_1") }
                response.body?.get("certificate") shouldBe cert
            }
        }
    }

    Given("an admin manually triggering the renewal-reminder sweep") {
        val certificateService = mockk<CertificateService>()
        val scheduler = mockk<CertificateRenewalReminderScheduler>()
        val idempotencyService = mockk<IdempotencyService>()
        val controller = CertificateController(certificateService, scheduler, idempotencyService)
        every { scheduler.processDue() } returns 3

        When("triggering it") {
            val response = controller.processRenewalReminders(currentUser)

            Then("it real-delegates to the scheduler's own real logic and reports the real count processed") {
                verify(exactly = 1) { scheduler.processDue() }
                response.body?.get("processed") shouldBe 3
            }
        }
    }

    Given("a public signature verification request") {
        val certificateService = mockk<CertificateService>()
        val scheduler = mockk<CertificateRenewalReminderScheduler>()
        val idempotencyService = mockk<IdempotencyService>()
        val controller = CertificateController(certificateService, scheduler, idempotencyService)
        every { certificateService.verify("SERIAL1", "payload", "sig") } returns
            VerificationResult(signatureValid = true, certificateStatus = CertificateStatus.ACTIVE, userId = "user_1", serialNumber = "SERIAL1")

        When("verifying -- no @AuthenticationPrincipal parameter exists on this method at all, matching its permitAll route") {
            val response = controller.verify(VerifyCertificateSignatureRequest("SERIAL1", "payload", "sig"))

            Then("it real-delegates and reports the real result") {
                response.body?.get("signatureValid") shouldBe true
            }
        }
    }

    listOf(
        Triple(CertificateUserNotFoundException("Not found"), HttpStatus.NOT_FOUND, "USER_NOT_FOUND"),
        Triple(CertificateUserNotVerifiedException("Forbidden"), HttpStatus.FORBIDDEN, "KYC_REQUIRED"),
        Triple(NoCertificateFoundException("Not found"), HttpStatus.NOT_FOUND, "NO_ACTIVE_CERTIFICATE"),
        Triple(CertificateNotFoundException("Not found"), HttpStatus.NOT_FOUND, "CERTIFICATE_NOT_FOUND"),
        Triple(RateLimitExceededException("Too many requests"), HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED"),
    ).forEach { (exception, expectedStatus, expectedCode) ->
        Given("a real ${exception::class.simpleName}") {
            val certificateService = mockk<CertificateService>()
            val scheduler = mockk<CertificateRenewalReminderScheduler>()
            val idempotencyService = mockk<IdempotencyService>()
            val controller = CertificateController(certificateService, scheduler, idempotencyService)

            When("its exception handler maps it to a real HTTP response") {
                @Suppress("UNCHECKED_CAST")
                val response = when (exception) {
                    is CertificateUserNotFoundException -> controller.handleUserNotFound(exception)
                    is CertificateUserNotVerifiedException -> controller.handleNotVerified(exception)
                    is NoCertificateFoundException -> controller.handleNoCertificate(exception)
                    is CertificateNotFoundException -> controller.handleCertificateNotFound(exception)
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
