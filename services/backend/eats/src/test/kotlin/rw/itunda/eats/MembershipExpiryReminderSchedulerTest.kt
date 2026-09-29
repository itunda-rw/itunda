package rw.itunda.eats

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.core.domain.EatsMembership
import rw.itunda.core.domain.PlatformMembership
import java.time.Instant

class MembershipExpiryReminderSchedulerTest : BehaviorSpec({

    fun eatsMembership(id: String) = EatsMembership(id = id, userId = "user_$id", activeUntil = Instant.now())
    fun platformMembership(id: String) = PlatformMembership(id = id, userId = "user_$id", activeUntil = Instant.now())

    Given("2 due Eats Club memberships, where sending the first reminder fails") {
        val eatsMembershipService = mockk<EatsMembershipService>()
        val platformMembershipService = mockk<PlatformMembershipService>()
        val m1 = eatsMembership("m1")
        val m2 = eatsMembership("m2")
        every { eatsMembershipService.getMembershipsDueForExpiryReminder() } returns listOf(m1, m2)
        every { eatsMembershipService.sendExpiryReminder(m1.id) } throws RuntimeException("push provider down")
        every { eatsMembershipService.sendExpiryReminder(m2.id) } returns Unit
        every { platformMembershipService.getMembershipsDueForExpiryReminder() } returns emptyList()
        val scheduler = MembershipExpiryReminderScheduler(eatsMembershipService, platformMembershipService)

        When("the sweep runs") {
            val sentCount = scheduler.processDue()

            Then("the second membership is still reminded -- the first failure doesn't stop the rest") {
                verify(exactly = 1) { eatsMembershipService.sendExpiryReminder(m1.id) }
                verify(exactly = 1) { eatsMembershipService.sendExpiryReminder(m2.id) }
            }

            Then("only the real successes are counted") {
                sentCount shouldBe 1
            }
        }
    }

    Given("the Eats Club loop fails entirely, but a platform membership is also due") {
        val eatsMembershipService = mockk<EatsMembershipService>()
        val platformMembershipService = mockk<PlatformMembershipService>()
        val m1 = eatsMembership("m1")
        val p1 = platformMembership("p1")
        every { eatsMembershipService.getMembershipsDueForExpiryReminder() } returns listOf(m1)
        every { eatsMembershipService.sendExpiryReminder(m1.id) } throws RuntimeException("push provider down")
        every { platformMembershipService.getMembershipsDueForExpiryReminder() } returns listOf(p1)
        every { platformMembershipService.sendExpiryReminder(p1.id) } returns Unit
        val scheduler = MembershipExpiryReminderScheduler(eatsMembershipService, platformMembershipService)

        When("the sweep runs") {
            scheduler.processDue()

            Then("the platform membership loop still runs -- an Eats Club failure doesn't poison the whole sweep") {
                verify(exactly = 1) { platformMembershipService.sendExpiryReminder(p1.id) }
            }
        }
    }

    Given("no due memberships of either type") {
        val eatsMembershipService = mockk<EatsMembershipService>()
        val platformMembershipService = mockk<PlatformMembershipService>()
        every { eatsMembershipService.getMembershipsDueForExpiryReminder() } returns emptyList()
        every { platformMembershipService.getMembershipsDueForExpiryReminder() } returns emptyList()
        val scheduler = MembershipExpiryReminderScheduler(eatsMembershipService, platformMembershipService)

        When("the sweep runs") {
            val sentCount = scheduler.processDue()

            Then("nothing is sent, no exception is thrown") {
                sentCount shouldBe 0
            }
        }
    }
})
