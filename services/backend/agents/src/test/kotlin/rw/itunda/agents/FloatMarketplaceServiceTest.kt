package rw.itunda.agents

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.bigdecimal.shouldBeEqualIgnoringScale
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jakarta.persistence.EntityManager
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.Agent
import rw.itunda.core.domain.AgentOperator
import rw.itunda.core.domain.AgentStatus
import rw.itunda.core.domain.FloatListing
import rw.itunda.core.domain.FloatListingStatus
import rw.itunda.core.domain.FloatTransferRequest
import rw.itunda.core.domain.FloatTransferRequestStatus
import rw.itunda.core.domain.Notification
import rw.itunda.core.fraud.FraudRuleEngine
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.AgentOperatorRepository
import rw.itunda.core.repository.AgentRepository
import rw.itunda.core.repository.FloatListingRepository
import rw.itunda.core.repository.FloatTransferRequestRepository
import rw.itunda.core.repository.LedgerAccountRepository
import rw.itunda.core.repository.NotificationRepository
import java.math.BigDecimal
import java.util.Optional

/**
 * Real Rwanda-native peer-to-peer agent float rebalancing marketplace -- see
 * FloatMarketplaceService's own doc comment for the full sourced account. Each
 * `Given` block below is its own, fully independent scenario with fresh mocks -- this
 * session's own hard-won Kotest `BehaviorSpec` lesson (sibling `When` blocks under one
 * `Given` share one mutable entity instance, so a prior `When`'s mutation leaks into a
 * later sibling) applied here from day one rather than discovered after a flaky test.
 */
class FloatMarketplaceServiceTest : BehaviorSpec({

    fun service(
        agentRepository: AgentRepository = mockk(),
        operatorRepository: AgentOperatorRepository = mockk(),
        listingRepository: FloatListingRepository = mockk(),
        requestRepository: FloatTransferRequestRepository = mockk(),
        ledgerAccountRepository: LedgerAccountRepository = mockk(),
        ledgerService: LedgerService = mockk(),
        rateLimiter: RateLimiter = mockk(relaxed = true),
        fraudRuleEngine: FraudRuleEngine = mockk(relaxed = true),
        // relaxed: refresh() is a real call this class now makes (see acceptRequest's
        // own 2026-08-09 lost-update fix comment) but has nothing meaningful to verify
        // against a mocked, already-fully-stubbed entity in these tests.
        entityManager: EntityManager = mockk(relaxed = true),
        notificationRepository: NotificationRepository = mockk<NotificationRepository>(relaxed = true).also { repo -> every { repo.save(any()) } answers { firstArg() } },
        pushNotificationService: PushNotificationService = mockk(relaxed = true),
    ) = FloatMarketplaceService(
        agentRepository, operatorRepository, listingRepository, requestRepository, ledgerAccountRepository, ledgerService,
        rateLimiter, fraudRuleEngine, entityManager, notificationRepository, pushNotificationService,
    )

    Given("a real active agent operator posting a listing") {
        val agentRepository = mockk<AgentRepository>()
        val operatorRepository = mockk<AgentOperatorRepository>()
        val listingRepository = mockk<FloatListingRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val svc = service(agentRepository = agentRepository, operatorRepository = operatorRepository, listingRepository = listingRepository, rateLimiter = rateLimiter)
        val agent = Agent("agent_1", "Kigali Central", "agent_cash_1", AgentStatus.ACTIVE, BigDecimal("100000"), BigDecimal("80000"))
        val operator = AgentOperator("operator_1", "agent_1", "user_1")
        every { operatorRepository.findByUserId("user_1") } returns operator
        every { agentRepository.findById("agent_1") } returns Optional.of(agent)
        every { listingRepository.save(any()) } answers { firstArg() }

        When("a valid amount is listed") {
            val listing = svc.postListing("user_1", BigDecimal("20000"))

            Then("a real OPEN listing is created for that agent") {
                listing.agentId shouldBe "agent_1"
                listing.status shouldBe FloatListingStatus.OPEN
                listing.amount shouldBeEqualIgnoringScale BigDecimal("20000")
            }

            Then("the per-agent creation rate limit is enforced") {
                verify(exactly = 1) { rateLimiter.checkLimit("float:listing:create:agent_1", limit = any(), window = any()) }
            }
        }

        When("a zero-or-negative amount is submitted") {
            Then("it real-400s before touching the repository") {
                shouldThrow<IllegalArgumentException> { svc.postListing("user_1", BigDecimal.ZERO) }
            }
        }
    }

    Given("an inactive (non-operator) account") {
        val operatorRepository = mockk<AgentOperatorRepository>()
        val svc = service(operatorRepository = operatorRepository)
        every { operatorRepository.findByUserId("user_2") } returns null

        When("that account tries to post a listing") {
            Then("it is real-rejected as not an active agent operator") {
                shouldThrow<AgentOperatorNotAuthorizedException> { svc.postListing("user_2", BigDecimal("5000")) }
            }
        }
    }

    Given("an OPEN listing with 20,000 available and a second agent wanting 25,000") {
        val agentRepository = mockk<AgentRepository>()
        val operatorRepository = mockk<AgentOperatorRepository>()
        val listingRepository = mockk<FloatListingRepository>()
        val svc = service(agentRepository = agentRepository, operatorRepository = operatorRepository, listingRepository = listingRepository)
        val operator = AgentOperator("operator_2", "agent_2", "user_2")
        val listing = FloatListing("floatlisting_1", "agent_1", BigDecimal("20000"))
        every { operatorRepository.findByUserId("user_2") } returns operator
        every { agentRepository.findById("agent_2") } returns Optional.of(Agent("agent_2", "Nyamirambo", "agent_cash_2", AgentStatus.ACTIVE, BigDecimal("100000"), BigDecimal("80000")))
        every { listingRepository.findById("floatlisting_1") } returns Optional.of(listing)

        When("that agent requests more than what remains") {
            Then("it real-422s, never silently clamping to what's available") {
                shouldThrow<FloatListingInsufficientRemainingException> { svc.requestFloat("user_2", "floatlisting_1", BigDecimal("25000")) }
            }
        }
    }

    // Real gap closed 2026-09-07 (Agents product-completeness pass): requestFloat had
    // zero rateLimiter.checkLimit call before this -- only postListing did. Same
    // "verify the real call happened" style postListing's own test above already
    // establishes for this file.
    Given("a real agent operator requesting float from another agent's open listing") {
        val agentRepository = mockk<AgentRepository>()
        val operatorRepository = mockk<AgentOperatorRepository>()
        val listingRepository = mockk<FloatListingRepository>()
        val requestRepository = mockk<FloatTransferRequestRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val svc = service(
            agentRepository = agentRepository, operatorRepository = operatorRepository, listingRepository = listingRepository,
            requestRepository = requestRepository, rateLimiter = rateLimiter,
        )
        val operator = AgentOperator("operator_2", "agent_2", "user_2")
        val listing = FloatListing("floatlisting_1", "agent_1", BigDecimal("20000"))
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        every { operatorRepository.findByUserId("user_2") } returns operator
        every { agentRepository.findById("agent_2") } returns Optional.of(Agent("agent_2", "Nyamirambo", "agent_cash_2", AgentStatus.ACTIVE, BigDecimal("100000"), BigDecimal("80000")))
        every { listingRepository.findById("floatlisting_1") } returns Optional.of(listing)
        every { requestRepository.save(any()) } answers { firstArg() }
        every { operatorRepository.findByAgentId("agent_1") } returns listOf(AgentOperator("operator_1", "agent_1", "owner_1"))
        every { notificationRepository.save(any()) } answers { firstArg() }
        val svcWithNotifications = service(
            agentRepository = agentRepository, operatorRepository = operatorRepository, listingRepository = listingRepository,
            requestRepository = requestRepository, rateLimiter = rateLimiter,
            notificationRepository = notificationRepository, pushNotificationService = pushNotificationService,
        )

        When("they request a valid amount") {
            val request = svc.requestFloat("user_2", "floatlisting_1", BigDecimal("10000"))

            Then("a real REQUESTED transfer request is created against that listing") {
                request.listingId shouldBe "floatlisting_1"
                request.requestingAgentId shouldBe "agent_2"
                request.status shouldBe FloatTransferRequestStatus.REQUESTED
            }

            Then("the per-agent request rate limit is enforced") {
                verify(exactly = 1) { rateLimiter.checkLimit("float:request:create:agent_2", limit = any(), window = any()) }
            }
        }

        // Real sibling-asymmetry fix (2026-09-13) -- see FloatMarketplaceService's own
        // notifyAgentOperators doc comment: the listing owner previously got zero
        // signal that a real request came in against their own listing.
        When("they request a valid amount, using the notification-wired service") {
            svcWithNotifications.requestFloat("user_2", "floatlisting_1", BigDecimal("10000"))

            Then("the listing owner's active operator is notified of the new request") {
                verify(exactly = 1) { notificationRepository.save(match { it.userId == "owner_1" && it.type == "float_marketplace" }) }
                verify(exactly = 1) { pushNotificationService.sendToUser("owner_1", "New float request", any()) }
            }
        }
    }

    Given("an agent trying to request float from their own listing") {
        val agentRepository = mockk<AgentRepository>()
        val operatorRepository = mockk<AgentOperatorRepository>()
        val listingRepository = mockk<FloatListingRepository>()
        val svc = service(agentRepository = agentRepository, operatorRepository = operatorRepository, listingRepository = listingRepository)
        val operator = AgentOperator("operator_1", "agent_1", "user_1")
        val listing = FloatListing("floatlisting_1", "agent_1", BigDecimal("20000"))
        every { operatorRepository.findByUserId("user_1") } returns operator
        every { agentRepository.findById("agent_1") } returns Optional.of(Agent("agent_1", "Kigali Central", "agent_cash_1", AgentStatus.ACTIVE, BigDecimal("100000"), BigDecimal("80000")))
        every { listingRepository.findById("floatlisting_1") } returns Optional.of(listing)

        When("they try to request their own listing's float") {
            Then("it is real-rejected as a self-transfer") {
                shouldThrow<FloatSelfTransferException> { svc.requestFloat("user_1", "floatlisting_1", BigDecimal("5000")) }
            }
        }
    }

    // Real gap closed 2026-09-07 (Agents product-completeness pass): declineRequest
    // had zero rateLimiter.checkLimit call before this -- same "verify the real call
    // happened" style already used above for postListing/requestFloat/acceptRequest.
    Given("a real pending request the listing owner is about to decline") {
        val agentRepository = mockk<AgentRepository>()
        val operatorRepository = mockk<AgentOperatorRepository>()
        val listingRepository = mockk<FloatListingRepository>()
        val requestRepository = mockk<FloatTransferRequestRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val svc = service(
            agentRepository = agentRepository, operatorRepository = operatorRepository, listingRepository = listingRepository,
            requestRepository = requestRepository, rateLimiter = rateLimiter,
            notificationRepository = notificationRepository, pushNotificationService = pushNotificationService,
        )
        val owningOperator = AgentOperator("operator_1", "agent_1", "owner_1")
        val listing = FloatListing("floatlisting_1", "agent_1", BigDecimal("20000"))
        val request = FloatTransferRequest("floattransferreq_1", "floatlisting_1", "agent_2", BigDecimal("10000"))
        every { operatorRepository.findByUserId("owner_1") } returns owningOperator
        every { agentRepository.findById("agent_1") } returns Optional.of(Agent("agent_1", "Kigali Central", "agent_cash_1", AgentStatus.ACTIVE, BigDecimal("100000"), BigDecimal("80000")))
        every { requestRepository.findById("floattransferreq_1") } returns Optional.of(request)
        every { requestRepository.findByIdForUpdate("floattransferreq_1") } returns Optional.of(request)
        every { listingRepository.findById("floatlisting_1") } returns Optional.of(listing)
        every { requestRepository.save(any()) } answers { firstArg() }
        every { operatorRepository.findByAgentId("agent_2") } returns listOf(AgentOperator("operator_2", "agent_2", "user_2"))
        every { notificationRepository.save(any()) } answers { firstArg() }

        When("the owner declines it") {
            val declined = svc.declineRequest("owner_1", "floattransferreq_1")

            Then("it is marked DECLINED") {
                declined.status shouldBe FloatTransferRequestStatus.DECLINED
            }

            Then("the per-agent decline rate limit is enforced") {
                verify(exactly = 1) { rateLimiter.checkLimit("float:request:decline:agent_1", limit = any(), window = any()) }
            }

            // Real sibling-asymmetry fix (2026-09-13) -- the requesting agent
            // previously got zero signal their own request was declined.
            Then("the requesting agent's own operator is notified their request was declined") {
                verify(exactly = 1) { notificationRepository.save(match { it.userId == "user_2" && it.type == "float_marketplace" }) }
                verify(exactly = 1) { pushNotificationService.sendToUser("user_2", "Float request declined", any()) }
            }
        }
    }

    // Real N+1 fix (2026-09-13, structural N+1 re-sweep): getNearbyListings used to
    // call agentRepository.findById once PER open listing instead of batching, same
    // fix shape MerchantFeeWaiverService.getRevocationCandidates already established.
    Given("two OPEN listings from two different, nearby agents") {
        val agentRepository = mockk<AgentRepository>()
        val operatorRepository = mockk<AgentOperatorRepository>()
        val listingRepository = mockk<FloatListingRepository>()
        val svc = service(agentRepository = agentRepository, operatorRepository = operatorRepository, listingRepository = listingRepository)
        val operator = AgentOperator("operator_own", "agent_own", "user_own")
        val agentA = Agent("agent_a", "Kigali Central", "agent_cash_a", AgentStatus.ACTIVE, BigDecimal("100000"), BigDecimal("80000"), latitude = -1.9536, longitude = 30.0605)
        val agentB = Agent("agent_b", "Nyamirambo", "agent_cash_b", AgentStatus.ACTIVE, BigDecimal("100000"), BigDecimal("80000"), latitude = -1.9606, longitude = 30.0705)
        val listingA = FloatListing("floatlisting_a", "agent_a", BigDecimal("20000"))
        val listingB = FloatListing("floatlisting_b", "agent_b", BigDecimal("15000"))
        every { operatorRepository.findByUserId("user_own") } returns operator
        every { agentRepository.findById("agent_own") } returns Optional.of(Agent("agent_own", "Own Agent", "agent_cash_own", AgentStatus.ACTIVE, BigDecimal("100000"), BigDecimal("80000")))
        every { listingRepository.findByStatus(FloatListingStatus.OPEN) } returns listOf(listingA, listingB)
        every { agentRepository.findAllById(match { it.toSet() == setOf("agent_a", "agent_b") }) } returns listOf(agentA, agentB)

        When("a nearby operator searches") {
            val results = svc.getNearbyListings("user_own", -1.95, 30.06, 20.0)

            Then("both listings' agents are batch-resolved in a single findAllById call, never a per-listing findById") {
                results.map { it.listing.id } shouldBe listOf("floatlisting_a", "floatlisting_b")
                verify(exactly = 1) { agentRepository.findAllById(any()) }
                // findById is only ever called once, for activeOperator's own caller-identity
                // check -- never per-listing for agent_a/agent_b.
                verify(exactly = 0) { agentRepository.findById("agent_a") }
                verify(exactly = 0) { agentRepository.findById("agent_b") }
            }
        }
    }

    Given("an agent with two of their own listings and requests against both") {
        val agentRepository = mockk<AgentRepository>()
        val operatorRepository = mockk<AgentOperatorRepository>()
        val listingRepository = mockk<FloatListingRepository>()
        val requestRepository = mockk<FloatTransferRequestRepository>()
        val svc = service(agentRepository = agentRepository, operatorRepository = operatorRepository, listingRepository = listingRepository, requestRepository = requestRepository)
        val owningOperator = AgentOperator("operator_1", "agent_1", "owner_1")
        val listingA = FloatListing("floatlisting_1", "agent_1", BigDecimal("20000"))
        val listingB = FloatListing("floatlisting_2", "agent_1", BigDecimal("5000"))
        val requestAgainstA = FloatTransferRequest("floattransferreq_1", "floatlisting_1", "agent_2", BigDecimal("10000"))
        val requestAgainstB = FloatTransferRequest("floattransferreq_2", "floatlisting_2", "agent_3", BigDecimal("2000"))
        every { operatorRepository.findByUserId("owner_1") } returns owningOperator
        every { agentRepository.findById("agent_1") } returns Optional.of(Agent("agent_1", "Kigali Central", "agent_cash_1", AgentStatus.ACTIVE, BigDecimal("100000"), BigDecimal("80000")))
        every { listingRepository.findByAgentIdOrderByCreatedAtDesc("agent_1") } returns listOf(listingB, listingA)
        every { requestRepository.findByListingIdInOrderByCreatedAtDesc(listOf("floatlisting_2", "floatlisting_1")) } returns listOf(requestAgainstB, requestAgainstA)

        When("they list requests against their own listings") {
            val incoming = svc.myIncomingRequests("owner_1")

            Then("both real incoming requests are returned, not just the requester's own outgoing view") {
                incoming shouldBe listOf(requestAgainstB, requestAgainstA)
            }
        }
    }

    Given("a real OPEN listing its own owner wants to cancel, with no pending requests against it") {
        val agentRepository = mockk<AgentRepository>()
        val operatorRepository = mockk<AgentOperatorRepository>()
        val listingRepository = mockk<FloatListingRepository>()
        val requestRepository = mockk<FloatTransferRequestRepository>()
        val svc = service(
            agentRepository = agentRepository, operatorRepository = operatorRepository, listingRepository = listingRepository,
            requestRepository = requestRepository,
        )
        val owningOperator = AgentOperator("operator_1", "agent_1", "owner_1")
        val listing = FloatListing("floatlisting_1", "agent_1", BigDecimal("20000"))
        every { operatorRepository.findByUserId("owner_1") } returns owningOperator
        every { agentRepository.findById("agent_1") } returns Optional.of(Agent("agent_1", "Kigali Central", "agent_cash_1", AgentStatus.ACTIVE, BigDecimal("100000"), BigDecimal("80000")))
        every { listingRepository.findByIdForUpdate("floatlisting_1") } returns Optional.of(listing)
        every { listingRepository.save(any()) } answers { firstArg() }
        every { requestRepository.findByListingIdAndStatus("floatlisting_1", FloatTransferRequestStatus.REQUESTED) } returns emptyList()

        When("the owner cancels it") {
            val cancelled = svc.cancelListing("owner_1", "floatlisting_1")

            Then("it is marked CANCELLED") {
                cancelled.status shouldBe FloatListingStatus.CANCELLED
            }
        }
    }

    // Real gap closed 2026-09-13: a cancelled listing left any still-REQUESTED request
    // against it permanently stuck (acceptRequest rejects a non-OPEN listing forever),
    // and its requester was never told the listing was gone.
    Given("a real OPEN listing with a real pending request against it, about to be cancelled") {
        val agentRepository = mockk<AgentRepository>()
        val operatorRepository = mockk<AgentOperatorRepository>()
        val listingRepository = mockk<FloatListingRepository>()
        val requestRepository = mockk<FloatTransferRequestRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val svc = service(
            agentRepository = agentRepository, operatorRepository = operatorRepository, listingRepository = listingRepository,
            requestRepository = requestRepository, notificationRepository = notificationRepository, pushNotificationService = pushNotificationService,
        )
        val owningOperator = AgentOperator("operator_1", "agent_1", "owner_1")
        val listing = FloatListing("floatlisting_1", "agent_1", BigDecimal("20000"))
        val pendingRequest = FloatTransferRequest("floattransferreq_1", "floatlisting_1", "agent_2", BigDecimal("10000"))
        every { operatorRepository.findByUserId("owner_1") } returns owningOperator
        every { agentRepository.findById("agent_1") } returns Optional.of(Agent("agent_1", "Kigali Central", "agent_cash_1", AgentStatus.ACTIVE, BigDecimal("100000"), BigDecimal("80000")))
        every { listingRepository.findByIdForUpdate("floatlisting_1") } returns Optional.of(listing)
        every { listingRepository.save(any()) } answers { firstArg() }
        every { requestRepository.findByListingIdAndStatus("floatlisting_1", FloatTransferRequestStatus.REQUESTED) } returns listOf(pendingRequest)
        every { requestRepository.save(any()) } answers { firstArg() }
        every { operatorRepository.findByAgentId("agent_2") } returns listOf(AgentOperator("operator_2", "agent_2", "user_2"))
        every { notificationRepository.save(any()) } answers { firstArg() }

        When("the owner cancels the listing") {
            svc.cancelListing("owner_1", "floatlisting_1")

            Then("the orphaned pending request is auto-declined, never left stuck forever against a cancelled listing") {
                pendingRequest.status shouldBe FloatTransferRequestStatus.DECLINED
            }

            Then("the requesting agent's own operator is notified their request was declined") {
                verify(exactly = 1) { notificationRepository.save(match { it.userId == "user_2" && it.type == "float_marketplace" }) }
                verify(exactly = 1) { pushNotificationService.sendToUser("user_2", "Float request declined", any()) }
            }
        }
    }
})
