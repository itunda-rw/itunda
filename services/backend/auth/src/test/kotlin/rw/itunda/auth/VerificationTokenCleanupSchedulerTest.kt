package rw.itunda.auth

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.transaction.annotation.Transactional
import rw.itunda.core.repository.EmailVerificationTokenRepository
import rw.itunda.core.repository.PhoneVerificationTokenRepository
import java.time.Instant

class VerificationTokenCleanupSchedulerTest : BehaviorSpec({
    Given("the verification-token cleanup scheduler") {
        val emailRepository = mockk<EmailVerificationTokenRepository>()
        val phoneRepository = mockk<PhoneVerificationTokenRepository>()
        val scheduler = VerificationTokenCleanupScheduler(emailRepository, phoneRepository)

        When("it runs") {
            val emailCutoff = slot<Instant>()
            val phoneCutoff = slot<Instant>()
            every { emailRepository.deleteExpiredBefore(capture(emailCutoff)) } returns 2
            every { phoneRepository.deleteExpiredBefore(capture(phoneCutoff)) } returns 3

            scheduler.run()

            Then("it purges both challenge types against one cutoff") {
                verify(exactly = 1) { emailRepository.deleteExpiredBefore(any()) }
                verify(exactly = 1) { phoneRepository.deleteExpiredBefore(any()) }
                emailCutoff.captured shouldBe phoneCutoff.captured
            }
        }

        Then("it runs transactionally on a bounded cadence") {
            val run = VerificationTokenCleanupScheduler::class.java.getMethod("run")
            val scheduled = run.getAnnotation(Scheduled::class.java)

            (run.getAnnotation(Transactional::class.java) != null) shouldBe true
            scheduled.fixedDelayString shouldBe "\${itunda.auth.verification-token-cleanup-interval-ms:3600000}"
        }
    }
})
