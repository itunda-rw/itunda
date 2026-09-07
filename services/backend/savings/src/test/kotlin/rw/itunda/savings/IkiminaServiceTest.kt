package rw.itunda.savings

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.Ikimina
import rw.itunda.core.domain.IkiminaMember
import rw.itunda.core.domain.IkiminaStatus
import rw.itunda.core.domain.User
import rw.itunda.core.domain.Account
import rw.itunda.core.domain.AccountType
import rw.itunda.core.ledger.LedgerPostResult
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.IkiminaContributionRepository
import rw.itunda.core.repository.IkiminaMemberRepository
import rw.itunda.core.repository.IkiminaRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.UserRepository
import rw.itunda.core.repository.AccountRepository
import rw.itunda.core.account.AccountNumberGenerator
import java.math.BigDecimal
import java.time.Instant
import java.util.Optional

/**
 * First test coverage for real ikimina -- Rwanda's own rotating savings & credit
 * association (ROSCA). See IkiminaService's own doc comment for the full sourced
 * account.
 */
class IkiminaServiceTest : BehaviorSpec({

    fun account(id: String, userId: String, type: AccountType = AccountType.MAIN, balance: BigDecimal = BigDecimal("100000")) = Account(
        id = id, userId = userId, accountNumber = "ACC-$id", accountName = "Test account",
        type = type, balance = balance, availableBalance = balance,
    )

    fun user(id: String) = User(
        id = id, phoneNumber = "+25078800$id".take(13), firstName = "Test", lastName = "User",
        passwordHash = "unused", createdAt = Instant.now(),
    )

    fun newService(
        ikiminaRepository: IkiminaRepository = mockk(),
        ikiminaMemberRepository: IkiminaMemberRepository = mockk(),
        ikiminaContributionRepository: IkiminaContributionRepository = mockk(),
        accountRepository: AccountRepository = mockk(),
        userRepository: UserRepository = mockk(),
        ledgerService: LedgerService = mockk(),
        rateLimiter: RateLimiter = mockk(relaxed = true),
        accountNumberGenerator: AccountNumberGenerator = mockk(relaxed = true),
        notificationRepository: NotificationRepository = mockk(relaxed = true),
        pushNotificationService: PushNotificationService = mockk(relaxed = true),
    ): IkiminaService {
        // Relaxed mockk's default synthesized return for JpaRepository's generic
        // `<S extends T> S save(S entity)` throws a real ClassCastException -- same
        // gotcha SavingsServiceTest's own notificationRepository mock already hit and
        // fixed. Stubbed unconditionally here (not per-Given-block) so every caller of
        // this factory, default or explicit mock, is covered.
        every { notificationRepository.save(any()) } answers { firstArg() }
        return IkiminaService(ikiminaRepository, ikiminaMemberRepository, ikiminaContributionRepository, accountRepository, userRepository, ledgerService, rateLimiter, accountNumberGenerator, notificationRepository, pushNotificationService)
    }

    Given("an organizer with a real account creating a new ikimina") {
        val ikiminaRepository = mockk<IkiminaRepository>()
        val ikiminaMemberRepository = mockk<IkiminaMemberRepository>()
        val accountRepository = mockk<AccountRepository>()
        val userRepository = mockk<UserRepository>()
        val service = newService(ikiminaRepository = ikiminaRepository, ikiminaMemberRepository = ikiminaMemberRepository, accountRepository = accountRepository, userRepository = userRepository)

        every { userRepository.findById("org_1") } returns Optional.of(user("org_1"))
        every { accountRepository.save(any()) } answers { firstArg() }
        every { ikiminaRepository.save(any()) } answers { firstArg() }
        every { ikiminaMemberRepository.save(any()) } answers { firstArg() }

        When("the organizer creates a real ikimina") {
            val result = service.createIkimina("org_1", "Umuryango Savings", BigDecimal("5000"), 7, 10)

            Then("a real ikimina is created with the organizer as payoutOrder 1") {
                result.organizerId shouldBe "org_1"
                result.status shouldBe IkiminaStatus.FORMING
                result.currentRound shouldBe 1
            }
        }

    }

    Given("an organizer providing a name that would overflow the settlement account's own accountName once \" (Ikimina)\" is appended") {
        val ikiminaRepository = mockk<IkiminaRepository>()
        val accountRepository = mockk<AccountRepository>()
        val userRepository = mockk<UserRepository>()
        val service = newService(ikiminaRepository = ikiminaRepository, accountRepository = accountRepository, userRepository = userRepository)
        every { userRepository.findById("org_2") } returns Optional.of(user("org_2"))

        When("the organizer tries to create it") {
            Then("it rejects at the real 245-char safe bound, not Ikimina.name's own (unenforced) 255") {
                try {
                    service.createIkimina("org_2", "x".repeat(246), BigDecimal("5000"), 7, 10)
                    error("expected IllegalArgumentException")
                } catch (e: IllegalArgumentException) {
                    verify(exactly = 0) { ikiminaRepository.save(any()) }
                }
            }
        }
    }

    Given("a FORMING ikimina an organizer wants to invite a new member into") {
        val ikiminaRepository = mockk<IkiminaRepository>()
        val ikiminaMemberRepository = mockk<IkiminaMemberRepository>()
        val userRepository = mockk<UserRepository>()
        val service = newService(ikiminaRepository = ikiminaRepository, ikiminaMemberRepository = ikiminaMemberRepository, userRepository = userRepository)

        val ikimina = Ikimina(id = "ikimina_1", name = "Test", organizerId = "org_1", accountId = "account_grp", contributionAmount = BigDecimal("5000"), cycleFrequencyDays = 7, memberCap = 10)
        every { ikiminaRepository.findById("ikimina_1") } returns Optional.of(ikimina)
        every { userRepository.findByPhoneNumber("+250788111111") } returns user("mem_1")
        every { ikiminaMemberRepository.findByIkiminaIdAndUserId("ikimina_1", "mem_1") } returns null
        every { ikiminaMemberRepository.countByIkiminaId("ikimina_1") } returns 1L
        every { ikiminaMemberRepository.save(any()) } answers { firstArg() }

        When("the organizer invites a real, unregistered-in-this-group phone number") {
            val result = service.inviteMember("org_1", "ikimina_1", "+250788111111")

            Then("a real member is added at the next real payout order") {
                result.userId shouldBe "mem_1"
                result.payoutOrder shouldBe 2
            }
        }
    }

    Given("a member trying to invite someone into an already-ACTIVE ikimina") {
        val ikiminaRepository = mockk<IkiminaRepository>()
        val service = newService(ikiminaRepository = ikiminaRepository)

        val ikimina = Ikimina(id = "ikimina_1", name = "Test", organizerId = "org_1", accountId = "account_grp", contributionAmount = BigDecimal("5000"), cycleFrequencyDays = 7, memberCap = 10, status = IkiminaStatus.ACTIVE)
        every { ikiminaRepository.findById("ikimina_1") } returns Optional.of(ikimina)

        When("the organizer tries to invite after the cycle has already started") {
            Then("the real invite is rejected") {
                try {
                    service.inviteMember("org_1", "ikimina_1", "+250788111111")
                    throw AssertionError("expected IkiminaNotFormingException")
                } catch (e: IkiminaNotFormingException) {
                    e.message shouldBe "Members can only be invited before the cycle starts"
                }
            }
        }
    }

    Given("a real member contributing for a round they already paid") {
        val ikiminaRepository = mockk<IkiminaRepository>()
        val ikiminaMemberRepository = mockk<IkiminaMemberRepository>()
        val ikiminaContributionRepository = mockk<IkiminaContributionRepository>()
        val service = newService(ikiminaRepository = ikiminaRepository, ikiminaMemberRepository = ikiminaMemberRepository, ikiminaContributionRepository = ikiminaContributionRepository)

        val ikimina = Ikimina(id = "ikimina_1", name = "Test", organizerId = "org_1", accountId = "account_grp", contributionAmount = BigDecimal("5000"), cycleFrequencyDays = 7, memberCap = 10, status = IkiminaStatus.ACTIVE)
        val member = IkiminaMember(id = "ikiminamem_1", ikiminaId = "ikimina_1", userId = "org_1", payoutOrder = 1)
        every { ikiminaRepository.findById("ikimina_1") } returns Optional.of(ikimina)
        every { ikiminaMemberRepository.findByIkiminaIdAndUserId("ikimina_1", "org_1") } returns member
        every { ikiminaContributionRepository.findByIkiminaIdAndMemberIdAndRound("ikimina_1", "ikiminamem_1", 1) } returns mockk()

        When("that same member tries to contribute again for the same round") {
            Then("the real double-contribution is rejected") {
                try {
                    service.contributeThisRound("org_1", "ikimina_1")
                    throw AssertionError("expected IkiminaAlreadyContributedException")
                } catch (e: IkiminaAlreadyContributedException) {
                    e.message shouldBe "You have already contributed for round 1"
                }
            }
        }
    }

    Given("a real 3-member ikimina where every member has contributed for round 1") {
        val ikiminaRepository = mockk<IkiminaRepository>()
        val ikiminaMemberRepository = mockk<IkiminaMemberRepository>()
        val ikiminaContributionRepository = mockk<IkiminaContributionRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = newService(
            ikiminaRepository = ikiminaRepository, ikiminaMemberRepository = ikiminaMemberRepository,
            ikiminaContributionRepository = ikiminaContributionRepository, accountRepository = accountRepository, ledgerService = ledgerService,
            notificationRepository = notificationRepository, pushNotificationService = pushNotificationService,
        )

        val ikimina = Ikimina(id = "ikimina_1", name = "Test", organizerId = "org_1", accountId = "account_grp", contributionAmount = BigDecimal("5000"), cycleFrequencyDays = 7, memberCap = 10, currentRound = 1, status = IkiminaStatus.ACTIVE)
        val members = listOf(
            IkiminaMember(id = "mem_1", ikiminaId = "ikimina_1", userId = "u1", payoutOrder = 1),
            IkiminaMember(id = "mem_2", ikiminaId = "ikimina_1", userId = "u2", payoutOrder = 2),
            IkiminaMember(id = "mem_3", ikiminaId = "ikimina_1", userId = "u3", payoutOrder = 3),
        )
        every { ikiminaRepository.findById("ikimina_1") } returns Optional.of(ikimina)
        every { ikiminaMemberRepository.findByIkiminaIdAndUserId("ikimina_1", "u1") } returns members[0]
        every { ikiminaMemberRepository.findByIkiminaId("ikimina_1") } returns members
        every { ikiminaContributionRepository.findByIkiminaIdAndRound("ikimina_1", 1) } returns members.map { mockk { every { memberId } returns it.id } }
        every { ikiminaMemberRepository.findByIkiminaIdAndPayoutOrder("ikimina_1", 1) } returns members[0]
        every { accountRepository.findById("account_grp") } returns Optional.of(account("account_grp", "org_1", AccountType.GROUP))
        every { accountRepository.findByUserIdAndType("u1", AccountType.MAIN) } returns account("account_u1", "u1")
        every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_1", emptyList())
        every { ikiminaMemberRepository.save(any()) } answers { firstArg() }
        every { ikiminaRepository.save(any()) } answers { firstArg() }

        When("any real member triggers the round-1 payout check") {
            val result = service.checkAndTriggerPayout("u1", "ikimina_1")

            Then("the real round-1 recipient (payoutOrder 1) is paid the full real pot and the round advances") {
                result.recipientUserId shouldBe "u1"
                result.amount shouldBe BigDecimal("15000")
                result.ikimina.currentRound shouldBe 2
                result.ikimina.status shouldBe IkiminaStatus.ACTIVE
            }

            // Real gap this pass closed: IkiminaService previously had ZERO
            // notification wiring at all. The recipient of a real payout must be
            // notified, but the cycle isn't complete yet (2 of 3 members still
            // haven't had their turn), so no completion notification should fire.
            Then("the recipient is notified of the real payout, but no cycle-completion notification fires yet") {
                verify(exactly = 1) { notificationRepository.save(match { it.type == "IKIMINA_PAYOUT_RECEIVED" && it.userId == "u1" }) }
                verify(exactly = 1) { pushNotificationService.sendToUser("u1", any(), any(), any(), "IKIMINA_PAYOUT_RECEIVED") }
                verify(exactly = 0) { notificationRepository.save(match { it.type == "IKIMINA_CYCLE_COMPLETED" }) }
            }
        }
    }

    // Real gap this pass closed: the cycle-completion transition itself (every member
    // has now received their turn) had no notification either, even though it's the
    // more celebratory of the two new events (matches SavingsService.notifyGoalCompleted's
    // real Toss-sourced "finding the hidden emotion" precedent). A 2-member ikimina
    // completes its full cycle on round 2's payout: round 1 already paid mem_1
    // (hasReceivedPayout = true), so round 2's payout to mem_2 is the one that should
    // flip the ikimina to COMPLETED and notify BOTH members, not just the recipient.
    Given("a real 2-member ikimina where round 2's payout will complete the full cycle") {
        val ikiminaRepository = mockk<IkiminaRepository>()
        val ikiminaMemberRepository = mockk<IkiminaMemberRepository>()
        val ikiminaContributionRepository = mockk<IkiminaContributionRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = newService(
            ikiminaRepository = ikiminaRepository, ikiminaMemberRepository = ikiminaMemberRepository,
            ikiminaContributionRepository = ikiminaContributionRepository, accountRepository = accountRepository, ledgerService = ledgerService,
            notificationRepository = notificationRepository, pushNotificationService = pushNotificationService,
        )

        val ikimina = Ikimina(id = "ikimina_1", name = "Test", organizerId = "u1", accountId = "account_grp", contributionAmount = BigDecimal("5000"), cycleFrequencyDays = 7, memberCap = 10, currentRound = 2, status = IkiminaStatus.ACTIVE)
        val members = listOf(
            IkiminaMember(id = "mem_1", ikiminaId = "ikimina_1", userId = "u1", payoutOrder = 1, hasReceivedPayout = true),
            IkiminaMember(id = "mem_2", ikiminaId = "ikimina_1", userId = "u2", payoutOrder = 2),
        )
        every { ikiminaRepository.findById("ikimina_1") } returns Optional.of(ikimina)
        every { ikiminaMemberRepository.findByIkiminaIdAndUserId("ikimina_1", "u1") } returns members[0]
        every { ikiminaMemberRepository.findByIkiminaId("ikimina_1") } returns members
        every { ikiminaContributionRepository.findByIkiminaIdAndRound("ikimina_1", 2) } returns members.map { mockk { every { memberId } returns it.id } }
        every { ikiminaMemberRepository.findByIkiminaIdAndPayoutOrder("ikimina_1", 2) } returns members[1]
        every { accountRepository.findById("account_grp") } returns Optional.of(account("account_grp", "u1", AccountType.GROUP))
        every { accountRepository.findByUserIdAndType("u2", AccountType.MAIN) } returns account("account_u2", "u2")
        every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_1", emptyList())
        every { ikiminaMemberRepository.save(any()) } answers { firstArg() }
        every { ikiminaRepository.save(any()) } answers { firstArg() }

        When("any real member triggers the round-2 payout check") {
            val result = service.checkAndTriggerPayout("u1", "ikimina_1")

            Then("the real round-2 recipient (mem_2) is paid and the ikimina is marked COMPLETED") {
                result.recipientUserId shouldBe "u2"
                result.ikimina.status shouldBe IkiminaStatus.COMPLETED
            }

            Then("the recipient gets a payout notification AND both real members get a cycle-completion notification") {
                verify(exactly = 1) { notificationRepository.save(match { it.type == "IKIMINA_PAYOUT_RECEIVED" && it.userId == "u2" }) }
                verify(exactly = 1) { notificationRepository.save(match { it.type == "IKIMINA_CYCLE_COMPLETED" && it.userId == "u1" }) }
                verify(exactly = 1) { notificationRepository.save(match { it.type == "IKIMINA_CYCLE_COMPLETED" && it.userId == "u2" }) }
                verify(exactly = 1) { pushNotificationService.sendToUser("u1", any(), any(), any(), "IKIMINA_CYCLE_COMPLETED") }
                verify(exactly = 1) { pushNotificationService.sendToUser("u2", any(), any(), any(), "IKIMINA_CYCLE_COMPLETED") }
            }
        }
    }

    // Real bug caught and fixed: the round's payout previously only ever fired from a
    // fully separate, manually-triggered endpoint -- nothing called it automatically
    // when the round-completing contribution landed. A real ikimina round could sit
    // indefinitely completed-but-unpaid until some member happened to tap a separate
    // button. This proves the fix: the LAST member's own contributeThisRound call now
    // auto-triggers the payout in the same call, without them ever calling the
    // separate payout endpoint themselves.
    Given("a real 2-member ikimina where the last member is about to contribute the round-completing amount") {
        val ikiminaRepository = mockk<IkiminaRepository>()
        val ikiminaMemberRepository = mockk<IkiminaMemberRepository>()
        val ikiminaContributionRepository = mockk<IkiminaContributionRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val service = newService(
            ikiminaRepository = ikiminaRepository, ikiminaMemberRepository = ikiminaMemberRepository,
            ikiminaContributionRepository = ikiminaContributionRepository, accountRepository = accountRepository, ledgerService = ledgerService,
        )

        val ikimina = Ikimina(id = "ikimina_1", name = "Test", organizerId = "u1", accountId = "account_grp", contributionAmount = BigDecimal("5000"), cycleFrequencyDays = 7, memberCap = 10, currentRound = 1, status = IkiminaStatus.ACTIVE)
        val members = listOf(
            IkiminaMember(id = "mem_1", ikiminaId = "ikimina_1", userId = "u1", payoutOrder = 1),
            IkiminaMember(id = "mem_2", ikiminaId = "ikimina_1", userId = "u2", payoutOrder = 2),
        )
        every { ikiminaRepository.findById("ikimina_1") } returns Optional.of(ikimina)
        every { ikiminaMemberRepository.findByIkiminaIdAndUserId("ikimina_1", "u2") } returns members[1]
        every { ikiminaMemberRepository.findByIkiminaId("ikimina_1") } returns members
        every { ikiminaMemberRepository.findByIkiminaIdAndPayoutOrder("ikimina_1", 1) } returns members[0]
        // Member u1 (payoutOrder 1) already contributed; u2 is about to be the second
        // and last real contribution this round.
        every { ikiminaContributionRepository.findByIkiminaIdAndMemberIdAndRound("ikimina_1", "mem_2", 1) } returns null
        every { ikiminaContributionRepository.findByIkiminaIdAndRound("ikimina_1", 1) } returns
            listOf(mockk { every { memberId } returns "mem_1" }, mockk { every { memberId } returns "mem_2" })
        every { accountRepository.findByUserIdAndType("u2", AccountType.MAIN) } returns account("account_u2", "u2")
        every { accountRepository.findByUserIdAndType("u1", AccountType.MAIN) } returns account("account_u1", "u1")
        every { accountRepository.findById("account_grp") } returns Optional.of(account("account_grp", "u1", AccountType.GROUP))
        every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_1", emptyList())
        every { ikiminaContributionRepository.save(any()) } answers { firstArg() }
        every { ikiminaMemberRepository.save(any()) } answers { firstArg() }
        every { ikiminaRepository.save(any()) } answers { firstArg() }

        When("the last member contributes, completing the round") {
            val result = service.contributeThisRound("u2", "ikimina_1")

            Then("the payout fires automatically in the same call -- no separate manual trigger needed") {
                result.payout shouldNotBe null
                result.payout?.recipientUserId shouldBe "u1"
                result.payout?.amount shouldBe BigDecimal("10000")
                result.ikimina.currentRound shouldBe 2
            }
        }
    }

    Given("a real ikimina where round 1's contributions are still incomplete") {
        val ikiminaRepository = mockk<IkiminaRepository>()
        val ikiminaMemberRepository = mockk<IkiminaMemberRepository>()
        val ikiminaContributionRepository = mockk<IkiminaContributionRepository>()
        val service = newService(ikiminaRepository = ikiminaRepository, ikiminaMemberRepository = ikiminaMemberRepository, ikiminaContributionRepository = ikiminaContributionRepository)

        val ikimina = Ikimina(id = "ikimina_1", name = "Test", organizerId = "org_1", accountId = "account_grp", contributionAmount = BigDecimal("5000"), cycleFrequencyDays = 7, memberCap = 10, currentRound = 1, status = IkiminaStatus.ACTIVE)
        val members = listOf(
            IkiminaMember(id = "mem_1", ikiminaId = "ikimina_1", userId = "u1", payoutOrder = 1),
            IkiminaMember(id = "mem_2", ikiminaId = "ikimina_1", userId = "u2", payoutOrder = 2),
        )
        every { ikiminaRepository.findById("ikimina_1") } returns Optional.of(ikimina)
        every { ikiminaMemberRepository.findByIkiminaIdAndUserId("ikimina_1", "u1") } returns members[0]
        every { ikiminaMemberRepository.findByIkiminaId("ikimina_1") } returns members
        every { ikiminaContributionRepository.findByIkiminaIdAndRound("ikimina_1", 1) } returns listOf(mockk { every { memberId } returns "mem_1" })

        When("a member checks the payout before everyone has contributed") {
            Then("the real payout is refused") {
                try {
                    service.checkAndTriggerPayout("u1", "ikimina_1")
                    throw AssertionError("expected IkiminaContributionsIncompleteException")
                } catch (e: IkiminaContributionsIncompleteException) {
                    e.message shouldBe "Not every member has contributed for round 1 yet"
                }
            }
        }
    }

    Given("a real caller who has already exceeded a real ikimina contribution rate limit") {
        val ikiminaRepository = mockk<IkiminaRepository>()
        val rateLimiter = mockk<RateLimiter>()
        val service = newService(ikiminaRepository = ikiminaRepository, rateLimiter = rateLimiter)
        every { rateLimiter.checkLimit("ikimina:contribute:user_1", limit = 30, window = any()) } throws RateLimitExceededException("Too many requests")

        When("contributing to a round") {
            Then("a real RateLimitExceededException fires before ever touching the real ikimina row") {
                try {
                    service.contributeThisRound("user_1", "ikimina_1")
                    throw AssertionError("expected RateLimitExceededException")
                } catch (e: RateLimitExceededException) {
                    verify(exactly = 0) { ikiminaRepository.findById(any()) }
                }
            }
        }
    }
})
