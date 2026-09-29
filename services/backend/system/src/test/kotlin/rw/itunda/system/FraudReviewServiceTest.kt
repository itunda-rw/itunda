package rw.itunda.system

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.Lock
import rw.itunda.core.domain.FraudFlag
import rw.itunda.core.domain.FraudFlagDecision
import rw.itunda.core.domain.FraudRule
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.FraudFlagRepository
import rw.itunda.core.repository.FraudQueueRuleSummaryRow
import rw.itunda.core.repository.NotificationRepository
import java.math.BigDecimal
import java.time.Instant
import java.util.Optional
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.transaction.support.TransactionSynchronizationManager

class FraudReviewServiceTest : BehaviorSpec({

    Given("a real unreviewed fraud flag") {
        val fraudFlagRepository = mockk<FraudFlagRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = FraudReviewService(fraudFlagRepository, notificationRepository, pushNotificationService)

        val flag = FraudFlag(id = "flag_1", userId = "user_1", transactionId = "txn_1", rule = FraudRule.HIGH_VALUE, description = "test", amount = BigDecimal("150000"))
        every { fraudFlagRepository.findByIdForUpdate("flag_1") } returns Optional.of(flag)
        every { fraudFlagRepository.save(any()) } answers { firstArg() }
        every { notificationRepository.save(any()) } answers { firstArg() }

        When("an admin clears it") {
            val decided = service.decide("flag_1", "admin_1", FraudFlagDecision.CLEARED)

            Then("it's marked reviewed with the real reviewer and decision recorded") {
                decided.reviewed shouldBe true
                decided.decision shouldBe FraudFlagDecision.CLEARED
                decided.reviewedBy shouldBe "admin_1"
            }

            Then("it real-sends no notification or push -- a cleared false positive shouldn't bother the account owner") {
                verify(exactly = 0) { notificationRepository.save(any()) }
                verify(exactly = 0) { pushNotificationService.sendToUser(any(), any(), any(), any()) }
            }
        }

        When("an admin confirms it as real fraud") {
            val decided = service.decide("flag_1", "admin_1", FraudFlagDecision.CONFIRMED)

            Then("it real-alerts the account owner in-app AND via push, the Toss FDS-style security notification this row was missing") {
                decided.decision shouldBe FraudFlagDecision.CONFIRMED
                verify(exactly = 1) { notificationRepository.save(match { it.userId == "user_1" && it.type == "FRAUD_CONFIRMED" }) }
                verify(exactly = 1) { pushNotificationService.sendToUser("user_1", any(), any(), any(), type = "FRAUD_ALERT") }
            }
        }

        When("an admin records a decision rationale") {
            val decided = service.decide("flag_1", "admin_1", FraudFlagDecision.CLEARED, "  Customer verified the transfer.  ")

            Then("the trimmed note is retained with the reviewer decision") {
                decided.reviewNote shouldBe "Customer verified the transfer."
            }
        }
    }

    Given("a flag that's already been reviewed") {
        val fraudFlagRepository = mockk<FraudFlagRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = FraudReviewService(fraudFlagRepository, notificationRepository, pushNotificationService)

        val flag = FraudFlag(id = "flag_2", userId = "user_2", transactionId = "txn_2", rule = FraudRule.VELOCITY, description = "test", amount = BigDecimal("100"), reviewed = true)
        every { fraudFlagRepository.findByIdForUpdate("flag_2") } returns Optional.of(flag)

        When("trying to decide it again") {
            Then("it throws FraudFlagAlreadyReviewedException rather than silently re-deciding") {
                try {
                    service.decide("flag_2", "admin_1", FraudFlagDecision.CONFIRMED)
                    error("expected FraudFlagAlreadyReviewedException")
                } catch (e: FraudFlagAlreadyReviewedException) {
                    // expected
                }
            }
        }
    }

    Given("an oversized fraud review note") {
        val fraudFlagRepository = mockk<FraudFlagRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = FraudReviewService(fraudFlagRepository, notificationRepository, pushNotificationService)
        val flag = FraudFlag(id = "flag_long_note", userId = "user_1", transactionId = "txn_1", rule = FraudRule.HIGH_VALUE, description = "test", amount = BigDecimal("150000"))
        every { fraudFlagRepository.findByIdForUpdate(flag.id) } returns Optional.of(flag)

        When("a reviewer submits more than 2000 characters") {
            Then("the decision is rejected before it is persisted") {
                try {
                    service.decide(flag.id, "admin_1", FraudFlagDecision.CLEARED, "x".repeat(2001))
                    error("expected FraudReviewNoteInvalidException")
                } catch (e: FraudReviewNoteInvalidException) {
                    // expected
                }
                flag.reviewed shouldBe false
            }
        }
    }

    Given("the fraud review repository") {
        When("a reviewer claims a flag") {
            val lock = FraudFlagRepository::class.java
                .getMethod("findByIdForUpdate", String::class.java)
                .getAnnotation(Lock::class.java)

            Then("the flag is exclusively locked for its decision transaction") {
                lock.value shouldBe LockModeType.PESSIMISTIC_WRITE
            }
        }
    }

    Given("an unreviewed fraud queue") {
        val fraudFlagRepository = mockk<FraudFlagRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = FraudReviewService(fraudFlagRepository, notificationRepository, pushNotificationService)
        val pageable = PageRequest.of(0, 20)
        every { fraudFlagRepository.findUnreviewedPrioritized(pageable) } returns PageImpl(emptyList())

        When("an operator requests review work") {
            service.getQueue(pageable)

            Then("it uses the risk-prioritized queue query") {
                verify(exactly = 1) { fraudFlagRepository.findUnreviewedPrioritized(pageable) }
            }
        }
    }

    Given("a confirmed fraud decision inside a transaction") {
        val fraudFlagRepository = mockk<FraudFlagRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = FraudReviewService(fraudFlagRepository, notificationRepository, pushNotificationService)
        val flag = FraudFlag(id = "flag_after_commit", userId = "user_1", transactionId = "txn_1", rule = FraudRule.HIGH_VALUE, description = "test", amount = BigDecimal("150000"))
        every { fraudFlagRepository.findByIdForUpdate(flag.id) } returns Optional.of(flag)
        every { fraudFlagRepository.save(any()) } answers { firstArg() }
        every { notificationRepository.save(any()) } answers { firstArg() }

        When("the review is recorded") {
            Then("the durable alert is saved, and its external push waits for commit") {
                TransactionSynchronizationManager.initSynchronization()
                try {
                    service.decide(flag.id, "admin_1", FraudFlagDecision.CONFIRMED)
                    verify(exactly = 1) { notificationRepository.save(any()) }
                    verify(exactly = 0) { pushNotificationService.sendToUser(any(), any(), any(), any(), type = any()) }

                    TransactionSynchronizationManager.getSynchronizations().forEach { it.afterCommit() }
                    verify(exactly = 1) { pushNotificationService.sendToUser("user_1", any(), any(), any(), type = "FRAUD_ALERT") }
                } finally {
                    TransactionSynchronizationManager.clearSynchronization()
                }
            }
        }
    }

    Given("a confirmed fraud decision whose push provider is unavailable after commit") {
        val fraudFlagRepository = mockk<FraudFlagRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>()
        val service = FraudReviewService(fraudFlagRepository, notificationRepository, pushNotificationService)
        val flag = FraudFlag(id = "flag_push_failure", userId = "user_1", transactionId = "txn_1", rule = FraudRule.HIGH_VALUE, description = "test", amount = BigDecimal("150000"))
        every { fraudFlagRepository.findByIdForUpdate(flag.id) } returns Optional.of(flag)
        every { fraudFlagRepository.save(any()) } answers { firstArg() }
        every { notificationRepository.save(any()) } answers { firstArg() }
        every { pushNotificationService.sendToUser(any(), any(), any(), any(), type = any()) } throws IllegalStateException("provider unavailable")

        Then("the already-persisted review remains successful even when the best-effort push fails") {
            TransactionSynchronizationManager.initSynchronization()
            try {
                service.decide(flag.id, "admin_1", FraudFlagDecision.CONFIRMED)
                runCatching {
                    TransactionSynchronizationManager.getSynchronizations().single().afterCommit()
                }.isSuccess shouldBe true
                flag.reviewed shouldBe true
                flag.decision shouldBe FraudFlagDecision.CONFIRMED
            } finally {
                TransactionSynchronizationManager.clearSynchronization()
            }
        }
    }

    Given("a fraud queue spanning multiple rules") {
        val fraudFlagRepository = mockk<FraudFlagRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = FraudReviewService(fraudFlagRepository, notificationRepository, pushNotificationService)
        val oldest = Instant.parse("2026-07-01T00:00:00Z")
        every { fraudFlagRepository.summarizeUnreviewedByRule() } returns listOf(
            object : FraudQueueRuleSummaryRow {
                override val rule = FraudRule.HIGH_VALUE
                override val unreviewed = 15L
                override val oldestUnreviewedAt = oldest
            },
            object : FraudQueueRuleSummaryRow {
                override val rule = FraudRule.NEW_RECIPIENT
                override val unreviewed = 79L
                override val oldestUnreviewedAt = Instant.parse("2026-07-02T00:00:00Z")
            },
        )

        Then("the reviewer summary exposes the total, oldest flag, and per-rule backlog") {
            val summary = service.getQueueSummary()
            summary.unreviewed shouldBe 94L
            summary.oldestUnreviewedAt shouldBe oldest
            summary.byRule.map { it.rule to it.unreviewed } shouldBe listOf(
                FraudRule.HIGH_VALUE to 15L,
                FraudRule.VELOCITY to 0L,
                FraudRule.NEW_RECIPIENT to 79L,
            )
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
