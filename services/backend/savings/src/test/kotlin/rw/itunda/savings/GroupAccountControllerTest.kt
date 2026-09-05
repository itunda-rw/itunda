package rw.itunda.savings

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.http.HttpStatus
import rw.itunda.core.idempotency.IdempotencyService
import rw.itunda.core.security.CurrentUser

/**
 * Real gap found and fixed 2026-09-05 (feedback_idempotency_key_sweep, same class as
 * StudentLoanController.apply's identical fix): before the fix, `invite` called
 * GroupAccountService.inviteMember directly with no Idempotency-Key protection -- a
 * lost response after a successful invite would resubmit here and hit
 * GroupAccountAlreadyMemberException on the retry, a confusing conflict for an
 * invite that actually already succeeded. `deposit`/`withdraw` were already
 * protected; this create endpoint was the outlier. This file exists to make sure
 * that wiring can't silently regress.
 */
class GroupAccountControllerTest : BehaviorSpec({

    val currentUser = CurrentUser(userId = "user_1")

    Given("a first-time member invite") {
        val service = mockk<GroupAccountService>()
        val idempotencyService = mockk<IdempotencyService>()
        val controller = GroupAccountController(service, idempotencyService)

        val member = mockk<GroupAccountMemberView>(relaxed = true)
        every { service.inviteMember("user_1", "group_1", "+250788111222") } returns member

        val actionSlot = slot<() -> Pair<Int, Map<String, Any?>>>()
        every {
            idempotencyService.replayOrExecute(
                "POST /api/v1/group-accounts/group_1/members",
                "key-1",
                any(),
                capture(actionSlot),
            )
        } answers { actionSlot.captured.invoke() }

        When("inviting the member") {
            val response = controller.invite("group_1", InviteMemberRequest("+250788111222"), "key-1", currentUser)

            Then("it routes through the real idempotency service, keyed to this exact route") {
                verify(exactly = 1) {
                    idempotencyService.replayOrExecute("POST /api/v1/group-accounts/group_1/members", "key-1", any(), any())
                }
                verify(exactly = 1) { service.inviteMember("user_1", "group_1", "+250788111222") }
                response.statusCode shouldBe HttpStatus.CREATED
                response.body?.get("member") shouldBe member
            }
        }
    }

    Given("a retried member invite using the same Idempotency-Key as a completed one") {
        val service = mockk<GroupAccountService>()
        val idempotencyService = mockk<IdempotencyService>()
        val controller = GroupAccountController(service, idempotencyService)

        every {
            idempotencyService.replayOrExecute("POST /api/v1/group-accounts/group_1/members", "key-1", any(), any())
        } returns (201 to mapOf("success" to true, "member" to "cached-result"))

        When("retrying with the same key") {
            val response = controller.invite("group_1", InviteMemberRequest("+250788111222"), "key-1", currentUser)

            Then("the cached response is returned and the member is never invited again") {
                response.body?.get("member") shouldBe "cached-result"
                verify(exactly = 0) { service.inviteMember(any(), any(), any()) }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
