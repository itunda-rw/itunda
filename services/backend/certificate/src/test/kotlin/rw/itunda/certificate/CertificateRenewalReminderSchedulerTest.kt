package rw.itunda.certificate

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.core.domain.Certificate
import java.time.Instant

/**
 * First test coverage for CertificateRenewalReminderScheduler -- and a real,
 * previously-live bug fix, not just a missing test. This file's own doc comment
 * claimed to already avoid the "transaction-poisoning pitfall," but that claim
 * addressed a different concern (self-invocation) than the one that actually
 * applied: `sendRenewalReminder` had no try/catch of its own, and this loop had none
 * either. Fixed alongside this test (same commit) with a per-certificate try/catch.
 */
class CertificateRenewalReminderSchedulerTest : BehaviorSpec({

    fun cert(id: String) = Certificate(
        id = id, userId = "user_$id", serialNumber = "SERIAL_$id", publicKeyBase64 = "x", expiresAt = Instant.now().plusSeconds(1000),
    )

    Given("3 certificates due for a renewal reminder, where sending the middle one fails") {
        val certificateService = mockk<CertificateService>()
        val c1 = cert("c1")
        val c2 = cert("c2")
        val c3 = cert("c3")
        every { certificateService.getCertificatesDueForRenewalReminder() } returns listOf(c1, c2, c3)
        every { certificateService.sendRenewalReminder("c1") } returns Unit
        every { certificateService.sendRenewalReminder("c2") } throws RuntimeException("messaging service unreachable")
        every { certificateService.sendRenewalReminder("c3") } returns Unit
        val scheduler = CertificateRenewalReminderScheduler(certificateService)

        When("the sweep runs") {
            val processed = scheduler.processDue()

            Then("all 3 are still attempted -- the middle failure doesn't stop the rest") {
                verify(exactly = 1) { certificateService.sendRenewalReminder("c1") }
                verify(exactly = 1) { certificateService.sendRenewalReminder("c2") }
                verify(exactly = 1) { certificateService.sendRenewalReminder("c3") }
            }
            Then("the real due count still reflects all 3, regardless of the middle failure") {
                processed shouldBe 3
            }
        }
    }

    Given("no certificates due for a reminder") {
        val certificateService = mockk<CertificateService>()
        every { certificateService.getCertificatesDueForRenewalReminder() } returns emptyList()
        val scheduler = CertificateRenewalReminderScheduler(certificateService)

        When("the sweep runs") {
            scheduler.run()

            Then("nothing is sent, no exception is thrown") {
                verify(exactly = 0) { certificateService.sendRenewalReminder(any()) }
            }
        }
    }
})
