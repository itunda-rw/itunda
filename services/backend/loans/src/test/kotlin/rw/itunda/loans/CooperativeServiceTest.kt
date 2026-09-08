package rw.itunda.loans

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.Cooperative
import rw.itunda.core.domain.CooperativeMembership
import rw.itunda.core.domain.HarvestAdvance
import rw.itunda.core.domain.HarvestAdvanceStatus
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.Account
import rw.itunda.core.domain.AccountType
import rw.itunda.core.ledger.LedgerPostResult
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.CooperativeMembershipRepository
import rw.itunda.core.repository.CooperativeRepository
import rw.itunda.core.repository.HarvestAdvanceRepository
import rw.itunda.core.repository.AccountRepository
import rw.itunda.core.repository.NotificationRepository
import java.math.BigDecimal
import java.time.Instant
import java.util.Optional

/**
 * First test coverage for real Rwanda coffee-cooperative harvest-advance / input
 * financing -- see CooperativeService's own doc comment for the full sourced account.
 * The ledger-account-correctness test specifically guards against the same real
 * solvency-bug class this session caught and fixed in SaccoService.declareDividend
 * (a payout accidentally funded from a shared/pooled account instead of itunda's own
 * capital) -- this feature was built to never have that bug in the first place, and
 * this test proves it stays that way.
 */
class CooperativeServiceTest : BehaviorSpec({

    fun account(id: String, userId: String) = Account(
        id = id, userId = userId, accountNumber = "ACC-$id", accountName = "Test account",
        type = AccountType.MAIN, balance = BigDecimal("100000"), availableBalance = BigDecimal("100000"),
    )

    fun newService(
        cooperativeRepository: CooperativeRepository = mockk(),
        membershipRepository: CooperativeMembershipRepository = mockk(),
        advanceRepository: HarvestAdvanceRepository = mockk(),
        accountRepository: AccountRepository = mockk(),
        ledgerService: LedgerService = mockk(),
        rateLimiter: RateLimiter = mockk(relaxed = true),
        notificationRepository: NotificationRepository = mockk(relaxed = true),
        pushNotificationService: PushNotificationService = mockk(relaxed = true),
    ) = CooperativeService(
        cooperativeRepository, membershipRepository, advanceRepository, accountRepository, ledgerService, rateLimiter,
        notificationRepository, pushNotificationService,
    )

    Given("a cooperative registration") {
        val cooperativeRepository = mockk<CooperativeRepository>()
        val service = newService(cooperativeRepository = cooperativeRepository)
        val savedSlot = slot<Cooperative>()
        every { cooperativeRepository.save(capture(savedSlot)) } answers { firstArg() }

        When("registering with a real name") {
            val result = service.registerCooperative("Nyamasheke Coffee Washing Station", "COFFEE", "RCA-12345")

            Then("a real cooperative row is saved") {
                result.name shouldBe "Nyamasheke Coffee Washing Station"
                result.cropType shouldBe "COFFEE"
                savedSlot.captured.registrationNumber shouldBe "RCA-12345"
            }
        }

        When("registering with a blank name") {
            Then("it real-400s before touching the repository") {
                shouldThrow<CooperativeInvalidNameException> { service.registerCooperative("  ", "COFFEE", null) }
            }
        }
    }

    Given("a user joining a cooperative") {
        val cooperativeRepository = mockk<CooperativeRepository>()
        val membershipRepository = mockk<CooperativeMembershipRepository>()
        val accountRepository = mockk<AccountRepository>()
        val service = newService(cooperativeRepository = cooperativeRepository, membershipRepository = membershipRepository, accountRepository = accountRepository)

        every { cooperativeRepository.findById("coop_1") } returns Optional.of(Cooperative(id = "coop_1", name = "Coop", cropType = "COFFEE", registrationNumber = null))
        every { accountRepository.findByUserIdAndType("user_1", AccountType.MAIN) } returns account("account_1", "user_1")
        every { membershipRepository.save(any()) } answers { firstArg() }

        When("joining for the first time") {
            every { membershipRepository.findByCooperativeIdAndUserId("coop_1", "user_1") } returns null
            val result = service.joinCooperative("user_1", "coop_1")

            Then("a real membership is saved") {
                result.userId shouldBe "user_1"
                result.accountId shouldBe "account_1"
            }
        }

        When("joining a second time") {
            every { membershipRepository.findByCooperativeIdAndUserId("coop_1", "user_1") } returns
                CooperativeMembership(id = "coopmem_1", cooperativeId = "coop_1", userId = "user_1", accountId = "account_1")

            Then("the duplicate-membership guard fires") {
                shouldThrow<AlreadyMemberException> { service.joinCooperative("user_1", "coop_1") }
            }
        }
    }

    Given("a real cooperative member requesting a harvest advance") {
        val membershipRepository = mockk<CooperativeMembershipRepository>()
        val advanceRepository = mockk<HarvestAdvanceRepository>()
        val service = newService(membershipRepository = membershipRepository, advanceRepository = advanceRepository)

        val membership = CooperativeMembership(id = "coopmem_1", cooperativeId = "coop_1", userId = "user_1", accountId = "account_1")
        every { membershipRepository.findById("coopmem_1") } returns Optional.of(membership)
        val savedSlot = slot<HarvestAdvance>()
        every { advanceRepository.save(capture(savedSlot)) } answers { firstArg() }

        When("requesting a real, valid advance") {
            val result = service.requestAdvance("user_1", "coopmem_1", BigDecimal("50000"), "INPUT_FINANCING", Instant.now())

            Then("a real REQUESTED advance is saved") {
                result.status shouldBe HarvestAdvanceStatus.REQUESTED
                savedSlot.captured.principalAmount shouldBe BigDecimal("50000")
            }
        }

        When("requesting an amount over the real cap") {
            Then("it real-400s before touching the repository") {
                shouldThrow<HarvestAdvanceInvalidAmountException> {
                    service.requestAdvance("user_1", "coopmem_1", BigDecimal("999999999"), "INPUT_FINANCING", Instant.now())
                }
            }
        }
    }

    Given("a real REQUESTED harvest advance being disbursed") {
        val membershipRepository = mockk<CooperativeMembershipRepository>()
        val advanceRepository = mockk<HarvestAdvanceRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val service = newService(
            membershipRepository = membershipRepository, advanceRepository = advanceRepository,
            accountRepository = accountRepository, ledgerService = ledgerService,
        )

        val membership = CooperativeMembership(id = "coopmem_1", cooperativeId = "coop_1", userId = "user_1", accountId = "account_1")
        val advance = HarvestAdvance(
            id = "harvestadv_1", membershipId = "coopmem_1", accountId = "account_1", principalAmount = BigDecimal("50000"),
            purpose = "INPUT_FINANCING", expectedHarvestDate = Instant.now(), repaymentDueDate = Instant.now(),
        )
        every { advanceRepository.findByIdForUpdate("harvestadv_1") } returns Optional.of(advance)
        every { membershipRepository.findById("coopmem_1") } returns Optional.of(membership)
        every { accountRepository.findById("account_1") } returns Optional.of(account("account_1", "user_1"))
        every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_1", emptyList())
        every { advanceRepository.save(any()) } answers { firstArg() }

        When("itunda disburses it") {
            val result = service.disburseAdvance("user_1", "harvestadv_1")

            Then("real bug class this feature was built to avoid: disbursement must come from itunda's own loan_payable receivable, never a shared pool") {
                result.status shouldBe HarvestAdvanceStatus.DISBURSED
                verify {
                    ledgerService.postLedgerTransaction(any(), match { legs ->
                        legs.size == 2 &&
                            legs.any { it.accountId == "account_1" && it.direction == LedgerDirection.CREDIT } &&
                            legs.any { it.accountId == "loan_payable" && it.accountType == LedgerAccountType.LOAN_PAYABLE && it.direction == LedgerDirection.DEBIT }
                    })
                }
            }
        }

        When("attempting to disburse it a second time") {
            val alreadyDisbursed = HarvestAdvance(
                id = "harvestadv_2", membershipId = "coopmem_1", accountId = "account_1", principalAmount = BigDecimal("50000"),
                purpose = "INPUT_FINANCING", expectedHarvestDate = Instant.now(), repaymentDueDate = Instant.now(),
                status = HarvestAdvanceStatus.DISBURSED,
            )
            every { advanceRepository.findByIdForUpdate("harvestadv_2") } returns Optional.of(alreadyDisbursed)

            Then("the status guard fires") {
                shouldThrow<HarvestAdvanceInvalidStatusException> { service.disburseAdvance("user_1", "harvestadv_2") }
            }
        }
    }

    Given("a real DISBURSED harvest advance being repaid") {
        val membershipRepository = mockk<CooperativeMembershipRepository>()
        val advanceRepository = mockk<HarvestAdvanceRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val service = newService(
            membershipRepository = membershipRepository, advanceRepository = advanceRepository,
            accountRepository = accountRepository, ledgerService = ledgerService,
        )

        val membership = CooperativeMembership(id = "coopmem_1", cooperativeId = "coop_1", userId = "user_1", accountId = "account_1")
        val advance = HarvestAdvance(
            id = "harvestadv_1", membershipId = "coopmem_1", accountId = "account_1", principalAmount = BigDecimal("50000"),
            purpose = "INPUT_FINANCING", expectedHarvestDate = Instant.now(), repaymentDueDate = Instant.now(),
            status = HarvestAdvanceStatus.DISBURSED,
        )
        every { advanceRepository.findByIdForUpdate("harvestadv_1") } returns Optional.of(advance)
        every { membershipRepository.findById("coopmem_1") } returns Optional.of(membership)
        every { accountRepository.findById("account_1") } returns Optional.of(account("account_1", "user_1"))
        every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_repay", emptyList())
        every { advanceRepository.save(any()) } answers { firstArg() }

        When("the farmer repays in full") {
            val result = service.repayAdvance("user_1", "harvestadv_1", BigDecimal("50000"))

            Then("the real repayment correctly reverses the loan_payable receivable and marks REPAID") {
                result.status shouldBe HarvestAdvanceStatus.REPAID
                verify {
                    ledgerService.postLedgerTransaction(any(), match { legs ->
                        legs.any { it.accountId == "account_1" && it.direction == LedgerDirection.DEBIT } &&
                            legs.any { it.accountId == "loan_payable" && it.accountType == LedgerAccountType.LOAN_PAYABLE && it.direction == LedgerDirection.CREDIT }
                    })
                }
            }
        }

    }

    // Real bug caught during this feature's own build-time review, before it ever
    // shipped: the first draft accepted any positive amount and unconditionally marked
    // the advance REPAID -- a token repayment would have silently forgiven the rest of
    // a real debt with zero write-off decision. A separate Given block (not a sibling
    // When under the block above) -- Kotest's BehaviorSpec shares the same mutable
    // `advance` instance across sibling Whens under one Given, and the prior block's
    // own successful repayment already mutates it to REPAID in place.
    Given("a second real DISBURSED harvest advance, and a farmer trying to repay only part of it") {
        val membershipRepository = mockk<CooperativeMembershipRepository>()
        val advanceRepository = mockk<HarvestAdvanceRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val service = newService(
            membershipRepository = membershipRepository, advanceRepository = advanceRepository,
            accountRepository = accountRepository, ledgerService = ledgerService,
        )

        val membership = CooperativeMembership(id = "coopmem_1", cooperativeId = "coop_1", userId = "user_1", accountId = "account_1")
        val advance = HarvestAdvance(
            id = "harvestadv_2", membershipId = "coopmem_1", accountId = "account_1", principalAmount = BigDecimal("50000"),
            purpose = "INPUT_FINANCING", expectedHarvestDate = Instant.now(), repaymentDueDate = Instant.now(),
            status = HarvestAdvanceStatus.DISBURSED,
        )
        every { advanceRepository.findByIdForUpdate("harvestadv_2") } returns Optional.of(advance)
        every { membershipRepository.findById("coopmem_1") } returns Optional.of(membership)

        When("repaying just 1 RWF of the real 50,000 principal") {
            Then("the partial-repayment guard fires before touching the ledger") {
                shouldThrow<HarvestAdvanceInvalidAmountException> {
                    service.repayAdvance("user_1", "harvestadv_2", BigDecimal("1"))
                }
                verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
            }
        }
    }

    // Real overdue detection + admin review queue (Bank product-completeness pass,
    // cycle 2, 2026-09-08) -- HarvestAdvanceStatus.OVERDUE existed and was already
    // defensively checked in repayAdvance's own guard, but nothing ever set it.
    // Mirrors VupLoanServiceTest's equivalent cases 1:1.
    Given("harvest advances due for an overdue check") {
        val advanceRepository = mockk<HarvestAdvanceRepository>()
        val service = newService(advanceRepository = advanceRepository)

        val overdueOne = HarvestAdvance(
            id = "harvestadv_overdue", membershipId = "coopmem_1", accountId = "account_1", principalAmount = BigDecimal("50000"),
            purpose = "INPUT_FINANCING", expectedHarvestDate = Instant.now(), repaymentDueDate = Instant.now().minusSeconds(3600),
            status = HarvestAdvanceStatus.DISBURSED,
        )
        val notYetDue = HarvestAdvance(
            id = "harvestadv_future", membershipId = "coopmem_1", accountId = "account_1", principalAmount = BigDecimal("50000"),
            purpose = "INPUT_FINANCING", expectedHarvestDate = Instant.now(), repaymentDueDate = Instant.now().plusSeconds(3600),
            status = HarvestAdvanceStatus.DISBURSED,
        )
        val alreadyRepaid = HarvestAdvance(
            id = "harvestadv_repaid", membershipId = "coopmem_1", accountId = "account_1", principalAmount = BigDecimal("50000"),
            purpose = "INPUT_FINANCING", expectedHarvestDate = Instant.now(), repaymentDueDate = Instant.now().minusSeconds(3600),
            status = HarvestAdvanceStatus.REPAID,
        )
        every { advanceRepository.findAll() } returns listOf(overdueOne, notYetDue, alreadyRepaid)

        When("checking which advances are due for an overdue flag") {
            val due = service.getAdvancesDueForOverdueCheck()

            Then("it includes only the DISBURSED advance whose real due date has already passed") {
                due.map { it.id } shouldBe listOf("harvestadv_overdue")
            }
        }
    }

    Given("a real DISBURSED harvest advance being marked overdue") {
        val membershipRepository = mockk<CooperativeMembershipRepository>()
        val advanceRepository = mockk<HarvestAdvanceRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = newService(
            membershipRepository = membershipRepository, advanceRepository = advanceRepository,
            notificationRepository = notificationRepository, pushNotificationService = pushNotificationService,
        )
        val membership = CooperativeMembership(id = "coopmem_1", cooperativeId = "coop_1", userId = "user_1", accountId = "account_1")
        val advance = HarvestAdvance(
            id = "harvestadv_1", membershipId = "coopmem_1", accountId = "account_1", principalAmount = BigDecimal("50000"),
            purpose = "INPUT_FINANCING", expectedHarvestDate = Instant.now(), repaymentDueDate = Instant.now().minusSeconds(3600),
            status = HarvestAdvanceStatus.DISBURSED,
        )
        every { membershipRepository.findById("coopmem_1") } returns Optional.of(membership)
        every { advanceRepository.save(any()) } answers { firstArg() }
        every { notificationRepository.save(any()) } answers { firstArg() }

        When("the scheduler flags it") {
            service.markOverdue(advance)

            Then("it flips the status and notifies the real farmer, not just a server log line") {
                advance.status shouldBe HarvestAdvanceStatus.OVERDUE
                verify(exactly = 1) { notificationRepository.save(match { it.userId == "user_1" && it.type == "HARVEST_ADVANCE_OVERDUE" }) }
                verify(exactly = 1) { pushNotificationService.sendToUser("user_1", any(), any(), any()) }
            }
        }
    }

    // Each scenario below gets its OWN Given block with a fresh advance instance --
    // same discipline this file's own repayAdvance tests already established
    // ("Kotest's BehaviorSpec shares the same mutable advance instance across
    // sibling Whens under one Given"): decide() mutates status in place, so two
    // sibling Whens sharing one instance would make each test's outcome depend on
    // declaration order.
    fun overdueAdvanceFixture() = HarvestAdvance(
        id = "harvestadv_overdue", membershipId = "coopmem_1", accountId = "account_1", principalAmount = BigDecimal("50000"),
        purpose = "INPUT_FINANCING", expectedHarvestDate = Instant.now(), repaymentDueDate = Instant.now().minusSeconds(3600),
        status = HarvestAdvanceStatus.OVERDUE,
    )
    val reviewerMembership = CooperativeMembership(id = "coopmem_1", cooperativeId = "coop_1", userId = "user_1", accountId = "account_1")

    Given("an OVERDUE harvest advance an admin just acknowledges") {
        val advanceRepository = mockk<HarvestAdvanceRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val service = newService(advanceRepository = advanceRepository, notificationRepository = notificationRepository)
        every { advanceRepository.findById("harvestadv_overdue") } returns Optional.of(overdueAdvanceFixture())
        every { advanceRepository.save(any()) } answers { firstArg() }

        When("the admin acknowledges it, without writing it off") {
            val result = service.decide("harvestadv_overdue", "admin_1", writeOff = false, note = "Farmer contacted, promised payment")

            Then("it records the review without touching the status") {
                result.status shouldBe HarvestAdvanceStatus.OVERDUE
                result.reviewedBy shouldBe "admin_1"
                verify(exactly = 0) { notificationRepository.save(any()) }
            }
        }
    }

    Given("an OVERDUE harvest advance an admin writes off") {
        val advanceRepository = mockk<HarvestAdvanceRepository>()
        val membershipRepository = mockk<CooperativeMembershipRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val service = newService(advanceRepository = advanceRepository, membershipRepository = membershipRepository, notificationRepository = notificationRepository)
        every { advanceRepository.findById("harvestadv_overdue") } returns Optional.of(overdueAdvanceFixture())
        every { membershipRepository.findById("coopmem_1") } returns Optional.of(reviewerMembership)
        every { advanceRepository.save(any()) } answers { firstArg() }
        every { notificationRepository.save(any()) } answers { firstArg() }

        When("the admin writes it off") {
            val result = service.decide("harvestadv_overdue", "admin_1", writeOff = true, note = null)

            Then("it marks WRITTEN_OFF and notifies the real farmer") {
                result.status shouldBe HarvestAdvanceStatus.WRITTEN_OFF
                verify(exactly = 1) { notificationRepository.save(match { it.userId == "user_1" && it.type == "HARVEST_ADVANCE_WRITTEN_OFF" }) }
            }
        }
    }

    Given("a non-OVERDUE harvest advance someone tries to review") {
        val advanceRepository = mockk<HarvestAdvanceRepository>()
        val service = newService(advanceRepository = advanceRepository)
        val disbursed = overdueAdvanceFixture().also { it.status = HarvestAdvanceStatus.DISBURSED }
        every { advanceRepository.findById("harvestadv_overdue") } returns Optional.of(disbursed)

        When("someone tries to review it here") {
            Then("it throws HarvestAdvanceNotOverdueException before touching anything") {
                shouldThrow<HarvestAdvanceNotOverdueException> {
                    service.decide("harvestadv_overdue", "admin_1", writeOff = false, note = null)
                }
                verify(exactly = 0) { advanceRepository.save(any()) }
            }
        }
    }

    Given("an OVERDUE harvest advance reviewed with a too-long note") {
        val advanceRepository = mockk<HarvestAdvanceRepository>()
        val service = newService(advanceRepository = advanceRepository)
        every { advanceRepository.findById("harvestadv_overdue") } returns Optional.of(overdueAdvanceFixture())

        When("the review note exceeds the real 255-char bound") {
            Then("it throws InvalidHarvestAdvanceReviewNoteException before touching anything") {
                shouldThrow<InvalidHarvestAdvanceReviewNoteException> {
                    service.decide("harvestadv_overdue", "admin_1", writeOff = false, note = "x".repeat(256))
                }
                verify(exactly = 0) { advanceRepository.save(any()) }
            }
        }
    }
})
