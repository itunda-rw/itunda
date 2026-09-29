package rw.itunda.loans

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.core.domain.PostpaidCreditLine
import java.math.BigDecimal

/**
 * First test coverage for PostpaidCreditPaymentReminderScheduler -- and a real,
 * previously-live bug fix, not just a missing test. This file's own doc comment
 * claimed to already avoid the "transaction-poisoning pitfall," but that claim
 * addressed a different concern (self-invocation) than the one that actually
 * applied: `sendPaymentReminder` had no try/catch of its own, and this loop had none
 * either. Fixed alongside this test (same commit) with a per-line try/catch.
 */
class PostpaidCreditPaymentReminderSchedulerTest : BehaviorSpec({

    fun line(id: String) = PostpaidCreditLine(
        id = id, userId = "user_$id", accountId = "account_$id", creditLimit = BigDecimal("100000"),
    )

    Given("3 lines due soon for a payment reminder, where sending the middle one fails") {
        val postpaidCreditService = mockk<PostpaidCreditService>()
        val l1 = line("l1")
        val l2 = line("l2")
        val l3 = line("l3")
        every { postpaidCreditService.getLinesDueSoonForPaymentReminder() } returns listOf(l1, l2, l3)
        every { postpaidCreditService.sendPaymentReminder("l1") } returns Unit
        every { postpaidCreditService.sendPaymentReminder("l2") } throws RuntimeException("messaging service unreachable")
        every { postpaidCreditService.sendPaymentReminder("l3") } returns Unit
        val scheduler = PostpaidCreditPaymentReminderScheduler(postpaidCreditService)

        When("the sweep runs") {
            val processed = scheduler.processDue()

            Then("all 3 are still attempted -- the middle failure doesn't stop the rest") {
                verify(exactly = 1) { postpaidCreditService.sendPaymentReminder("l1") }
                verify(exactly = 1) { postpaidCreditService.sendPaymentReminder("l2") }
                verify(exactly = 1) { postpaidCreditService.sendPaymentReminder("l3") }
            }
            Then("the real due count still reflects all 3, regardless of the middle failure") {
                processed shouldBe 3
            }
        }
    }

    Given("no lines due soon") {
        val postpaidCreditService = mockk<PostpaidCreditService>()
        every { postpaidCreditService.getLinesDueSoonForPaymentReminder() } returns emptyList()
        val scheduler = PostpaidCreditPaymentReminderScheduler(postpaidCreditService)

        When("the sweep runs") {
            scheduler.run()

            Then("nothing is sent, no exception is thrown") {
                verify(exactly = 0) { postpaidCreditService.sendPaymentReminder(any()) }
            }
        }
    }
})
