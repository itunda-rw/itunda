package rw.itunda.family.web

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.http.HttpStatus
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.core.domain.FamilyLink
import rw.itunda.core.security.CurrentUser
import rw.itunda.family.FamilyLinkAlreadyExistsException
import rw.itunda.family.FamilyLinkChildNotFoundException
import rw.itunda.family.FamilyLinkInvalidSpendLimitException
import rw.itunda.family.FamilyLinkNotActiveException
import rw.itunda.family.FamilyLinkNotFoundException
import rw.itunda.family.FamilyLinkNotPendingException
import rw.itunda.family.FamilyLinkSelfException
import rw.itunda.family.FamilyLinkService
import rw.itunda.family.FamilyLinkUnauthorizedException
import java.math.BigDecimal

/**
 * First test coverage for FamilyLinkController -- previously untested despite
 * FamilyLinkServiceTest.kt already having full service-layer coverage. Covers real
 * delegation (caller-scoped userId, never client-supplied) for all 8 endpoints and
 * every real exception-handler mapping, matching this sweep's established
 * BehaviorSpec/MockK shape (see StocksControllerTest.kt).
 */
class FamilyLinkControllerTest : BehaviorSpec({

    val currentUser = CurrentUser(userId = "user_1")

    fun controller(familyLinkService: FamilyLinkService = mockk()) = FamilyLinkController(familyLinkService)

    Given("a real invite request") {
        val familyLinkService = mockk<FamilyLinkService>()
        val ctl = controller(familyLinkService)
        val link = FamilyLink(id = "familylink_1", guardianUserId = "user_1", childUserId = "child_1")
        every { familyLinkService.inviteChild("user_1", "+250788000002") } returns link

        When("inviting a child by phone number") {
            val response = ctl.invite(InviteChildRequest("+250788000002"), currentUser)
            Then("it delegates scoped to the caller's own userId, never a client-supplied one") {
                verify(exactly = 1) { familyLinkService.inviteChild("user_1", "+250788000002") }
                response.statusCode shouldBe HttpStatus.CREATED
            }
        }
    }

    Given("a real invites-list request") {
        val familyLinkService = mockk<FamilyLinkService>()
        val ctl = controller(familyLinkService)
        every { familyLinkService.getMyInvitesAsChild("user_1") } returns emptyList()

        When("fetching pending invites") {
            ctl.myInvites(currentUser)
            Then("it queries scoped to the caller's own userId") {
                verify(exactly = 1) { familyLinkService.getMyInvitesAsChild("user_1") }
            }
        }
    }

    Given("a real respond-to-invite request") {
        val familyLinkService = mockk<FamilyLinkService>()
        val ctl = controller(familyLinkService)
        val link = FamilyLink(id = "familylink_1", guardianUserId = "guardian_1", childUserId = "user_1")
        every { familyLinkService.respondToInvite("user_1", "familylink_1", true) } returns link

        When("accepting it") {
            ctl.respond("familylink_1", RespondToInviteRequest(true), currentUser)
            Then("it delegates scoped to the caller's own userId, never a client-supplied one") {
                verify(exactly = 1) { familyLinkService.respondToInvite("user_1", "familylink_1", true) }
            }
        }
    }

    Given("a real children-list request") {
        val familyLinkService = mockk<FamilyLinkService>()
        val ctl = controller(familyLinkService)
        every { familyLinkService.getMyChildren("user_1") } returns emptyList()

        When("fetching linked children") {
            ctl.myChildren(currentUser)
            Then("it queries scoped to the caller's own userId") {
                verify(exactly = 1) { familyLinkService.getMyChildren("user_1") }
            }
        }
    }

    Given("a real guardians-list request") {
        val familyLinkService = mockk<FamilyLinkService>()
        val ctl = controller(familyLinkService)
        every { familyLinkService.getMyGuardians("user_1") } returns emptyList()

        When("fetching linked guardians") {
            ctl.myGuardians(currentUser)
            Then("it queries scoped to the caller's own userId") {
                verify(exactly = 1) { familyLinkService.getMyGuardians("user_1") }
            }
        }
    }

    Given("a real child-overview request") {
        val familyLinkService = mockk<FamilyLinkService>()
        val ctl = controller(familyLinkService)
        val overview = mockk<rw.itunda.family.ChildOverview>(relaxed = true)
        every { familyLinkService.getChildOverview("user_1", "child_1") } returns overview

        When("viewing a real child's overview") {
            ctl.childOverview("child_1", currentUser)
            Then("it delegates scoped to the caller's own userId as the guardian, never a client-supplied one") {
                verify(exactly = 1) { familyLinkService.getChildOverview("user_1", "child_1") }
            }
        }
    }

    Given("a real spend-limit set request") {
        val familyLinkService = mockk<FamilyLinkService>()
        val ctl = controller(familyLinkService)
        val link = FamilyLink(id = "familylink_1", guardianUserId = "user_1", childUserId = "child_1")
        every { familyLinkService.setSpendLimit("user_1", "child_1", BigDecimal("5000")) } returns link

        When("setting it") {
            ctl.setSpendLimit("child_1", SetSpendLimitRequest(BigDecimal("5000")), currentUser)
            Then("it delegates scoped to the caller's own userId as the guardian, never a client-supplied one") {
                verify(exactly = 1) { familyLinkService.setSpendLimit("user_1", "child_1", BigDecimal("5000")) }
            }
        }
    }

    Given("a real revoke request") {
        val familyLinkService = mockk<FamilyLinkService>()
        val ctl = controller(familyLinkService)
        val link = FamilyLink(id = "familylink_1", guardianUserId = "user_1", childUserId = "child_1")
        every { familyLinkService.revokeLink("user_1", "familylink_1") } returns link

        When("revoking it") {
            ctl.revoke("familylink_1", currentUser)
            Then("it delegates scoped to the caller's own userId, never a client-supplied one") {
                verify(exactly = 1) { familyLinkService.revokeLink("user_1", "familylink_1") }
            }
        }
    }

    listOf(
        Triple(FamilyLinkNotFoundException("Not found") as RuntimeException, HttpStatus.NOT_FOUND, "FAMILY_LINK_NOT_FOUND"),
        Triple(FamilyLinkChildNotFoundException("Not found"), HttpStatus.NOT_FOUND, "FAMILY_LINK_ACCOUNT_NOT_FOUND"),
        Triple(FamilyLinkSelfException("Bad request"), HttpStatus.BAD_REQUEST, "SELF_LINK_NOT_ALLOWED"),
        Triple(FamilyLinkAlreadyExistsException("Conflict"), HttpStatus.CONFLICT, "FAMILY_LINK_ALREADY_EXISTS"),
        Triple(FamilyLinkNotPendingException("Conflict"), HttpStatus.CONFLICT, "FAMILY_LINK_NOT_PENDING"),
        Triple(FamilyLinkNotActiveException("Conflict"), HttpStatus.CONFLICT, "FAMILY_LINK_NOT_ACTIVE"),
        Triple(FamilyLinkUnauthorizedException("Forbidden"), HttpStatus.FORBIDDEN, "FAMILY_LINK_UNAUTHORIZED"),
        Triple(FamilyLinkInvalidSpendLimitException("Bad request"), HttpStatus.BAD_REQUEST, "INVALID_SPEND_LIMIT"),
        Triple(RateLimitExceededException("Too many requests"), HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED"),
    ).forEach { (exception, expectedStatus, expectedCode) ->
        Given("a real ${exception::class.simpleName}") {
            val ctl = controller()

            When("its exception handler maps it to a real HTTP response") {
                val response = when (exception) {
                    is FamilyLinkNotFoundException -> ctl.handleNotFound(exception)
                    is FamilyLinkChildNotFoundException -> ctl.handleChildNotFound(exception)
                    is FamilyLinkSelfException -> ctl.handleSelf(exception)
                    is FamilyLinkAlreadyExistsException -> ctl.handleAlreadyExists(exception)
                    is FamilyLinkNotPendingException -> ctl.handleNotPending(exception)
                    is FamilyLinkNotActiveException -> ctl.handleNotActive(exception)
                    is FamilyLinkUnauthorizedException -> ctl.handleUnauthorized(exception)
                    is FamilyLinkInvalidSpendLimitException -> ctl.handleInvalidSpendLimit(exception)
                    is RateLimitExceededException -> ctl.handleRateLimit(exception)
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
