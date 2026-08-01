package rw.itunda.agents

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.bigdecimal.shouldBeEqualIgnoringScale
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.Agent
import rw.itunda.core.domain.AgentOperator
import rw.itunda.core.domain.AgentStatus
import rw.itunda.core.domain.FloatListing
import rw.itunda.core.domain.FloatListingStatus
import rw.itunda.core.domain.FloatTransferRequest
import rw.itunda.core.domain.FloatTransferRequestStatus
import rw.itunda.core.domain.LedgerAccount
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerPostResult
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.AgentOperatorRepository
import rw.itunda.core.repository.AgentRepository
import rw.itunda.core.repository.FloatListingRepository
import rw.itunda.core.repository.FloatTransferRequestRepository
import rw.itunda.core.repository.LedgerAccountRepository
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
    ) = FloatMarketplaceService(agentRepository, operatorRepository, listingRepository, requestRepository, ledgerAccountRepository, ledgerService, rateLimiter)

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

    Given("a real 10,000-RWF request the listing owner is about to accept, with real, sufficient cash on hand") {
        val agentRepository = mockk<AgentRepository>()
        val operatorRepository = mockk<AgentOperatorRepository>()
        val listingRepository = mockk<FloatListingRepository>()
        val requestRepository = mockk<FloatTransferRequestRepository>()
        val ledgerAccountRepository = mockk<LedgerAccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val svc = service(
            agentRepository = agentRepository, operatorRepository = operatorRepository, listingRepository = listingRepository,
            requestRepository = requestRepository, ledgerAccountRepository = ledgerAccountRepository, ledgerService = ledgerService,
        )
        val owningOperator = AgentOperator("operator_1", "agent_1", "owner_1")
        val listingAgent = Agent("agent_1", "Kigali Central", "agent_cash_1", AgentStatus.ACTIVE, BigDecimal("100000"), BigDecimal("80000"))
        val requestingAgent = Agent("agent_2", "Nyamirambo", "agent_cash_2", AgentStatus.ACTIVE, BigDecimal("100000"), BigDecimal("80000"))
        val listing = FloatListing("floatlisting_1", "agent_1", BigDecimal("20000"))
        val request = FloatTransferRequest("floattransferreq_1", "floatlisting_1", "agent_2", BigDecimal("10000"))

        every { operatorRepository.findByUserId("owner_1") } returns owningOperator
        every { agentRepository.findById("agent_1") } returns Optional.of(listingAgent)
        every { agentRepository.findById("agent_2") } returns Optional.of(requestingAgent)
        every { requestRepository.findById("floattransferreq_1") } returns Optional.of(request)
        every { listingRepository.findById("floatlisting_1") } returns Optional.of(listing)
        every { listingRepository.findByIdForUpdate("floatlisting_1") } returns Optional.of(listing)
        // Real cash on hand: 30,000 (stored as -30000, negated per this codebase's own
        // AGENT_CASH convention -- see AgentService.cashOut's own comment).
        every { ledgerAccountRepository.findByIdForUpdate("agent_cash_1") } returns Optional.of(LedgerAccount("agent_cash_1", "Agent cash", BigDecimal("-30000")))
        every { ledgerAccountRepository.findByIdForUpdate("agent_cash_2") } returns Optional.of(LedgerAccount("agent_cash_2", "Agent cash", BigDecimal("-5000")))
        every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_1", emptyList())
        every { requestRepository.save(any()) } answers { firstArg() }
        every { listingRepository.save(any()) } answers { firstArg() }

        When("the listing owner accepts the request") {
            val accepted = svc.acceptRequest("owner_1", "floattransferreq_1")

            Then("the requesting agent's till is debited (increased) and the listing agent's till is credited (decreased) -- and cash_vault is never touched") {
                val legs = slot<List<LedgerLeg>>()
                verify(exactly = 1) { ledgerService.postLedgerTransaction("RWF", capture(legs)) }
                legs.captured.size shouldBe 2
                legs.captured[0].accountId shouldBe "agent_cash_2"
                legs.captured[0].accountType shouldBe LedgerAccountType.AGENT_CASH
                legs.captured[0].direction shouldBe LedgerDirection.DEBIT
                legs.captured[0].amount shouldBeEqualIgnoringScale BigDecimal("10000")
                legs.captured[1].accountId shouldBe "agent_cash_1"
                legs.captured[1].accountType shouldBe LedgerAccountType.AGENT_CASH
                legs.captured[1].direction shouldBe LedgerDirection.CREDIT
                legs.captured[1].amount shouldBeEqualIgnoringScale BigDecimal("10000")
                legs.captured.none { it.accountId == "cash_vault" } shouldBe true
            }

            Then("the request is marked ACCEPTED with the real transaction id") {
                accepted.status shouldBe FloatTransferRequestStatus.ACCEPTED
                accepted.transactionId shouldBe "ledgertxn_1"
            }

            Then("the listing's claimedAmount reflects the accepted amount and stays OPEN (10,000 of 20,000 claimed)") {
                listing.claimedAmount shouldBeEqualIgnoringScale BigDecimal("10000")
                listing.status shouldBe FloatListingStatus.OPEN
            }
        }
    }

    Given("a real request for the FULL listing amount") {
        val agentRepository = mockk<AgentRepository>()
        val operatorRepository = mockk<AgentOperatorRepository>()
        val listingRepository = mockk<FloatListingRepository>()
        val requestRepository = mockk<FloatTransferRequestRepository>()
        val ledgerAccountRepository = mockk<LedgerAccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val svc = service(
            agentRepository = agentRepository, operatorRepository = operatorRepository, listingRepository = listingRepository,
            requestRepository = requestRepository, ledgerAccountRepository = ledgerAccountRepository, ledgerService = ledgerService,
        )
        val owningOperator = AgentOperator("operator_1", "agent_1", "owner_1")
        val listingAgent = Agent("agent_1", "Kigali Central", "agent_cash_1", AgentStatus.ACTIVE, BigDecimal("100000"), BigDecimal("80000"))
        val requestingAgent = Agent("agent_2", "Nyamirambo", "agent_cash_2", AgentStatus.ACTIVE, BigDecimal("100000"), BigDecimal("80000"))
        val listing = FloatListing("floatlisting_1", "agent_1", BigDecimal("20000"))
        val request = FloatTransferRequest("floattransferreq_1", "floatlisting_1", "agent_2", BigDecimal("20000"))

        every { operatorRepository.findByUserId("owner_1") } returns owningOperator
        every { agentRepository.findById("agent_1") } returns Optional.of(listingAgent)
        every { agentRepository.findById("agent_2") } returns Optional.of(requestingAgent)
        every { requestRepository.findById("floattransferreq_1") } returns Optional.of(request)
        every { listingRepository.findById("floatlisting_1") } returns Optional.of(listing)
        every { listingRepository.findByIdForUpdate("floatlisting_1") } returns Optional.of(listing)
        every { ledgerAccountRepository.findByIdForUpdate("agent_cash_1") } returns Optional.of(LedgerAccount("agent_cash_1", "Agent cash", BigDecimal("-30000")))
        every { ledgerAccountRepository.findByIdForUpdate("agent_cash_2") } returns Optional.of(LedgerAccount("agent_cash_2", "Agent cash", BigDecimal("-5000")))
        every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_2", emptyList())
        every { requestRepository.save(any()) } answers { firstArg() }
        every { listingRepository.save(any()) } answers { firstArg() }

        When("the owner accepts it") {
            svc.acceptRequest("owner_1", "floattransferreq_1")

            Then("the listing is fully claimed and marked FULFILLED") {
                listing.claimedAmount shouldBeEqualIgnoringScale BigDecimal("20000")
                listing.status shouldBe FloatListingStatus.FULFILLED
            }
        }
    }

    Given("a listing owned by a different agent than the caller") {
        val agentRepository = mockk<AgentRepository>()
        val operatorRepository = mockk<AgentOperatorRepository>()
        val listingRepository = mockk<FloatListingRepository>()
        val requestRepository = mockk<FloatTransferRequestRepository>()
        val svc = service(agentRepository = agentRepository, operatorRepository = operatorRepository, listingRepository = listingRepository, requestRepository = requestRepository)
        val notTheOwnerOperator = AgentOperator("operator_3", "agent_3", "stranger_1")
        val listing = FloatListing("floatlisting_1", "agent_1", BigDecimal("20000"))
        val request = FloatTransferRequest("floattransferreq_1", "floatlisting_1", "agent_2", BigDecimal("10000"))
        every { operatorRepository.findByUserId("stranger_1") } returns notTheOwnerOperator
        every { agentRepository.findById("agent_3") } returns Optional.of(Agent("agent_3", "Kimironko", "agent_cash_3", AgentStatus.ACTIVE, BigDecimal("100000"), BigDecimal("80000")))
        every { requestRepository.findById("floattransferreq_1") } returns Optional.of(request)
        every { listingRepository.findById("floatlisting_1") } returns Optional.of(listing)

        When("a non-owner tries to accept a request against someone else's listing") {
            Then("it real-404s -- never a 403 that would confirm the request exists") {
                shouldThrow<FloatTransferRequestNotFoundException> { svc.acceptRequest("stranger_1", "floattransferreq_1") }
            }
        }
    }

    Given("a listing agent whose real till no longer has enough cash on hand") {
        val agentRepository = mockk<AgentRepository>()
        val operatorRepository = mockk<AgentOperatorRepository>()
        val listingRepository = mockk<FloatListingRepository>()
        val requestRepository = mockk<FloatTransferRequestRepository>()
        val ledgerAccountRepository = mockk<LedgerAccountRepository>()
        val svc = service(
            agentRepository = agentRepository, operatorRepository = operatorRepository, listingRepository = listingRepository,
            requestRepository = requestRepository, ledgerAccountRepository = ledgerAccountRepository,
        )
        val owningOperator = AgentOperator("operator_1", "agent_1", "owner_1")
        val listingAgent = Agent("agent_1", "Kigali Central", "agent_cash_1", AgentStatus.ACTIVE, BigDecimal("100000"), BigDecimal("80000"))
        val requestingAgent = Agent("agent_2", "Nyamirambo", "agent_cash_2", AgentStatus.ACTIVE, BigDecimal("100000"), BigDecimal("80000"))
        val listing = FloatListing("floatlisting_1", "agent_1", BigDecimal("20000"))
        val request = FloatTransferRequest("floattransferreq_1", "floatlisting_1", "agent_2", BigDecimal("10000"))

        every { operatorRepository.findByUserId("owner_1") } returns owningOperator
        every { agentRepository.findById("agent_1") } returns Optional.of(listingAgent)
        every { agentRepository.findById("agent_2") } returns Optional.of(requestingAgent)
        every { requestRepository.findById("floattransferreq_1") } returns Optional.of(request)
        every { listingRepository.findById("floatlisting_1") } returns Optional.of(listing)
        every { listingRepository.findByIdForUpdate("floatlisting_1") } returns Optional.of(listing)
        // Listing promised 20,000 but the till (spent down by unrelated cash-outs since
        // the listing was posted) really only has 4,000 on hand right now.
        every { ledgerAccountRepository.findByIdForUpdate("agent_cash_1") } returns Optional.of(LedgerAccount("agent_cash_1", "Agent cash", BigDecimal("-4000")))
        every { ledgerAccountRepository.findByIdForUpdate("agent_cash_2") } returns Optional.of(LedgerAccount("agent_cash_2", "Agent cash", BigDecimal("-5000")))

        When("the owner tries to accept a 10,000 request anyway") {
            Then("it real-422s instead of posting an overdrawing ledger transaction") {
                shouldThrow<FloatListingInsufficientCashException> { svc.acceptRequest("owner_1", "floattransferreq_1") }
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

    Given("a real OPEN listing its own owner wants to cancel") {
        val agentRepository = mockk<AgentRepository>()
        val operatorRepository = mockk<AgentOperatorRepository>()
        val listingRepository = mockk<FloatListingRepository>()
        val svc = service(agentRepository = agentRepository, operatorRepository = operatorRepository, listingRepository = listingRepository)
        val owningOperator = AgentOperator("operator_1", "agent_1", "owner_1")
        val listing = FloatListing("floatlisting_1", "agent_1", BigDecimal("20000"))
        every { operatorRepository.findByUserId("owner_1") } returns owningOperator
        every { agentRepository.findById("agent_1") } returns Optional.of(Agent("agent_1", "Kigali Central", "agent_cash_1", AgentStatus.ACTIVE, BigDecimal("100000"), BigDecimal("80000")))
        every { listingRepository.findByIdForUpdate("floatlisting_1") } returns Optional.of(listing)
        every { listingRepository.save(any()) } answers { firstArg() }

        When("the owner cancels it") {
            val cancelled = svc.cancelListing("owner_1", "floatlisting_1")

            Then("it is marked CANCELLED") {
                cancelled.status shouldBe FloatListingStatus.CANCELLED
            }
        }
    }
})
