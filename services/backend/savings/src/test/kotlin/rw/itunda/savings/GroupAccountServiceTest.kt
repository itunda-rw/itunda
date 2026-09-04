package rw.itunda.savings

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.GroupAccount
import rw.itunda.core.domain.GroupAccountContribution
import rw.itunda.core.domain.GroupAccountMember
import rw.itunda.core.domain.User
import rw.itunda.core.domain.Account
import rw.itunda.core.domain.AccountType
import rw.itunda.core.ledger.InsufficientFundsException
import rw.itunda.core.ledger.LedgerPostResult
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.GroupAccountContributionRepository
import rw.itunda.core.repository.GroupAccountDuesReminderRepository
import rw.itunda.core.repository.GroupAccountMemberRepository
import rw.itunda.core.repository.GroupAccountRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.UserRepository
import rw.itunda.core.repository.AccountRepository
import rw.itunda.core.account.AccountNumberGenerator
import java.math.BigDecimal
import java.time.Instant
import java.util.Optional

/** First test coverage for the real Kakao Bank 모임통장 (group account) equivalent. */
class GroupAccountServiceTest : BehaviorSpec({

    fun account(id: String, userId: String, type: AccountType = AccountType.MAIN, balance: BigDecimal = BigDecimal("100000")) = Account(
        id = id, userId = userId, accountNumber = "ACC-$id", accountName = "Test account",
        type = type, balance = balance, availableBalance = balance,
    )

    fun user(id: String, firstName: String = "Test") = User(
        id = id, phoneNumber = "+25078800$id".take(13), firstName = firstName, lastName = "User",
        passwordHash = "unused", createdAt = Instant.now(),
    )

    Given("creating a group account") {
        val groupAccountRepository = mockk<GroupAccountRepository>()
        val groupAccountMemberRepository = mockk<GroupAccountMemberRepository>()
        val groupAccountContributionRepository = mockk<GroupAccountContributionRepository>()
        val groupAccountDuesReminderRepository = mockk<GroupAccountDuesReminderRepository>()
        val accountRepository = mockk<AccountRepository>()
        val userRepository = mockk<UserRepository>()
        val notificationRepository = mockk<NotificationRepository>()
        val ledgerService = mockk<LedgerService>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val accountNumberGenerator = mockk<AccountNumberGenerator>(relaxed = true)
        val service = GroupAccountService(
            groupAccountRepository, groupAccountMemberRepository, groupAccountContributionRepository, groupAccountDuesReminderRepository,
            accountRepository, userRepository, notificationRepository, ledgerService, rateLimiter, pushNotificationService, accountNumberGenerator,
        )

        When("an owner creates a new group account") {
            every { userRepository.findById("owner_1") } returns Optional.of(user("owner_1"))
            every { accountRepository.save(any()) } answers { firstArg() }
            every { groupAccountRepository.save(any()) } answers { firstArg() }
            val memberSlot = mutableListOf<GroupAccountMember>()
            every { groupAccountMemberRepository.save(capture(memberSlot)) } answers { firstArg() }

            val account = service.createGroupAccount("owner_1", "Roommates")

            Then("it provisions a real zero-balance GROUP account and adds the owner as a real member") {
                account.ownerId shouldBe "owner_1"
                account.name shouldBe "Roommates"
                val accountSlot = mutableListOf<Account>()
                verify(exactly = 1) { accountRepository.save(capture(accountSlot)) }
                accountSlot.single().type shouldBe AccountType.GROUP
                accountSlot.single().balance shouldBe BigDecimal.ZERO
                memberSlot.single().userId shouldBe "owner_1"
                verify(exactly = 1) { rateLimiter.checkLimit("group-account:create:owner_1", limit = 10, window = any()) }
            }
        }
    }

    Given("a group account with an owner and one invited member") {
        val groupAccountRepository = mockk<GroupAccountRepository>()
        val groupAccountMemberRepository = mockk<GroupAccountMemberRepository>()
        val groupAccountContributionRepository = mockk<GroupAccountContributionRepository>()
        val groupAccountDuesReminderRepository = mockk<GroupAccountDuesReminderRepository>()
        val accountRepository = mockk<AccountRepository>()
        val userRepository = mockk<UserRepository>()
        val notificationRepository = mockk<NotificationRepository>()
        val ledgerService = mockk<LedgerService>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val accountNumberGenerator = mockk<AccountNumberGenerator>(relaxed = true)
        val service = GroupAccountService(
            groupAccountRepository, groupAccountMemberRepository, groupAccountContributionRepository, groupAccountDuesReminderRepository,
            accountRepository, userRepository, notificationRepository, ledgerService, rateLimiter, pushNotificationService, accountNumberGenerator,
        )

        val account = GroupAccount(id = "grp_1", name = "Roommates", ownerId = "owner_1", accountId = "account_grp_1")

        When("the owner invites a real itunda user by phone number") {
            every { groupAccountRepository.findById("grp_1") } returns Optional.of(account)
            every { userRepository.findByPhoneNumber("+250788000002") } returns user("member_2", "Alice")
            every { groupAccountMemberRepository.findByGroupAccountIdAndUserId("grp_1", "member_2") } returns null
            every { groupAccountMemberRepository.countByGroupAccountId("grp_1") } returns 1L
            every { groupAccountMemberRepository.save(any()) } answers { firstArg() }
            every { notificationRepository.save(any()) } answers { firstArg() }

            val member = service.inviteMember("owner_1", "grp_1", "+250788000002")

            Then("it adds a real member row and notifies the invitee") {
                member.userId shouldBe "member_2"
                member.isOwner shouldBe false
                verify(exactly = 1) { notificationRepository.save(match { it.userId == "member_2" && it.type == "GROUP_ACCOUNT_INVITE" }) }
            }

            Then("the invitee also gets a real mobile push notification, not just the in-app one") {
                verify(exactly = 1) { pushNotificationService.sendToUser("member_2", "Added to \"Roommates\"", any(), any()) }
            }
        }

        When("someone who isn't the owner tries to invite a member") {
            every { groupAccountRepository.findById("grp_1") } returns Optional.of(account)

            Then("it throws GroupAccountNotFoundException before touching the member repository") {
                try {
                    service.inviteMember("member_2", "grp_1", "+250788000003")
                    error("expected GroupAccountNotFoundException")
                } catch (e: GroupAccountNotFoundException) {
                    verify(exactly = 0) { groupAccountMemberRepository.save(any()) }
                }
            }
        }

        When("inviting a phone number with no matching itunda account") {
            every { groupAccountRepository.findById("grp_1") } returns Optional.of(account)
            every { userRepository.findByPhoneNumber("+250788999999") } returns null

            Then("it throws a real, honest GroupAccountRecipientNotFoundException, not a silent no-op") {
                try {
                    service.inviteMember("owner_1", "grp_1", "+250788999999")
                    error("expected GroupAccountRecipientNotFoundException")
                } catch (e: GroupAccountRecipientNotFoundException) {
                    verify(exactly = 0) { groupAccountMemberRepository.save(any()) }
                }
            }
        }

        When("inviting someone who is already a member") {
            every { groupAccountRepository.findById("grp_1") } returns Optional.of(account)
            every { userRepository.findByPhoneNumber("+250788000002") } returns user("member_2")
            every { groupAccountMemberRepository.findByGroupAccountIdAndUserId("grp_1", "member_2") } returns
                GroupAccountMember(id = "grpmem_x", groupAccountId = "grp_1", userId = "member_2")

            Then("it throws GroupAccountAlreadyMemberException") {
                try {
                    service.inviteMember("owner_1", "grp_1", "+250788000002")
                    error("expected GroupAccountAlreadyMemberException")
                } catch (e: GroupAccountAlreadyMemberException) {
                    verify(exactly = 0) { groupAccountMemberRepository.save(any()) }
                }
            }
        }

        When("the group account is already at the real 100-member cap") {
            every { groupAccountRepository.findById("grp_1") } returns Optional.of(account)
            every { userRepository.findByPhoneNumber("+250788000002") } returns user("member_2")
            every { groupAccountMemberRepository.findByGroupAccountIdAndUserId("grp_1", "member_2") } returns null
            every { groupAccountMemberRepository.countByGroupAccountId("grp_1") } returns 100L

            Then("it throws GroupAccountFullException, matching Kakao Bank's own published 100-member limit") {
                try {
                    service.inviteMember("owner_1", "grp_1", "+250788000002")
                    error("expected GroupAccountFullException")
                } catch (e: GroupAccountFullException) {
                    verify(exactly = 0) { groupAccountMemberRepository.save(any()) }
                }
            }
        }

        When("a member (not the owner) deposits into the group account") {
            every { groupAccountRepository.findById("grp_1") } returns Optional.of(account)
            every { groupAccountMemberRepository.findByGroupAccountIdAndUserId("grp_1", "member_2") } returns
                GroupAccountMember(id = "grpmem_x", groupAccountId = "grp_1", userId = "member_2")
            every { accountRepository.findByUserIdAndType("member_2", AccountType.MAIN) } returns account("account_member_2", "member_2")
            every { accountRepository.findById("account_grp_1") } returns Optional.of(account("account_grp_1", "owner_1", AccountType.GROUP, BigDecimal("5000")))
            every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_1", emptyList())
            every { groupAccountMemberRepository.findByGroupAccountId("grp_1") } returns listOf(
                GroupAccountMember(id = "m1", groupAccountId = "grp_1", userId = "owner_1"),
                GroupAccountMember(id = "m2", groupAccountId = "grp_1", userId = "member_2"),
            )
            every { userRepository.findAllById(any<List<String>>()) } returns listOf(user("owner_1"), user("member_2"))
            every { notificationRepository.save(any()) } answers { firstArg() }
            every { groupAccountContributionRepository.save(any()) } answers { firstArg() }

            val result = service.deposit("member_2", "grp_1", BigDecimal("3000"))

            Then("it posts a real ledger transfer from the member's own MAIN account and notifies the other real members, not the depositor") {
                verify(exactly = 1) { ledgerService.postLedgerTransaction(any(), any()) }
                verify(exactly = 1) { notificationRepository.save(match { it.userId == "owner_1" && it.type == "GROUP_ACCOUNT_ACTIVITY" }) }
                verify(exactly = 0) { notificationRepository.save(match { it.userId == "member_2" && it.type == "GROUP_ACCOUNT_ACTIVITY" }) }
                result.members.size shouldBe 2
            }
            Then("it records a real per-cycle dues contribution for the depositing member") {
                verify(exactly = 1) { groupAccountContributionRepository.save(match { it.userId == "member_2" && it.groupAccountId == "grp_1" && it.amount == BigDecimal("3000") }) }
            }
        }

        When("a non-member tries to deposit") {
            every { groupAccountRepository.findById("grp_1") } returns Optional.of(account)
            every { groupAccountMemberRepository.findByGroupAccountIdAndUserId("grp_1", "outsider") } returns null

            Then("it throws GroupAccountNotFoundException before touching the ledger") {
                try {
                    service.deposit("outsider", "grp_1", BigDecimal("1000"))
                    error("expected GroupAccountNotFoundException")
                } catch (e: GroupAccountNotFoundException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }

        When("the owner withdraws from the group account") {
            every { groupAccountRepository.findById("grp_1") } returns Optional.of(account)
            every { accountRepository.findById("account_grp_1") } returns Optional.of(account("account_grp_1", "owner_1", AccountType.GROUP, BigDecimal("5000")))
            every { accountRepository.findByUserIdAndType("owner_1", AccountType.MAIN) } returns account("account_owner_1", "owner_1")
            every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_2", emptyList())
            every { groupAccountMemberRepository.findByGroupAccountId("grp_1") } returns listOf(
                GroupAccountMember(id = "m1", groupAccountId = "grp_1", userId = "owner_1"),
                GroupAccountMember(id = "m2", groupAccountId = "grp_1", userId = "member_2"),
            )
            every { groupAccountMemberRepository.findByGroupAccountIdAndUserId("grp_1", "owner_1") } returns
                GroupAccountMember(id = "m1", groupAccountId = "grp_1", userId = "owner_1")
            every { userRepository.findAllById(any<List<String>>()) } returns listOf(user("owner_1"), user("member_2"))
            every { notificationRepository.save(any()) } answers { firstArg() }

            service.withdraw("owner_1", "grp_1", BigDecimal("2000"))

            Then("it posts a real ledger transfer to the owner's own MAIN account and notifies other members transparently") {
                verify(exactly = 1) { ledgerService.postLedgerTransaction(any(), any()) }
                verify(exactly = 1) { notificationRepository.save(match { it.userId == "member_2" && it.type == "GROUP_ACCOUNT_ACTIVITY" }) }
            }
        }

        When("a member who isn't the owner tries to withdraw") {
            every { groupAccountRepository.findById("grp_1") } returns Optional.of(account)

            Then("it throws GroupAccountNotFoundException -- matching Kakao Bank's real withdrawal-authority-stays-with-organizer rule") {
                try {
                    service.withdraw("member_2", "grp_1", BigDecimal("1000"))
                    error("expected GroupAccountNotFoundException")
                } catch (e: GroupAccountNotFoundException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }

        When("the owner tries to withdraw more than the real group balance") {
            every { groupAccountRepository.findById("grp_1") } returns Optional.of(account)
            every { accountRepository.findById("account_grp_1") } returns Optional.of(account("account_grp_1", "owner_1", AccountType.GROUP, BigDecimal("1000")))
            every { accountRepository.findByUserIdAndType("owner_1", AccountType.MAIN) } returns account("account_owner_1", "owner_1")

            Then("it throws InsufficientFundsException without touching the ledger") {
                try {
                    service.withdraw("owner_1", "grp_1", BigDecimal("50000"))
                    error("expected InsufficientFundsException")
                } catch (e: InsufficientFundsException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }

        When("the owner sets a real monthly dues amount") {
            every { groupAccountRepository.findById("grp_1") } returns Optional.of(account)
            every { groupAccountRepository.save(any()) } answers { firstArg() }

            val updated = service.setDuesAmount("owner_1", "grp_1", BigDecimal("2000"))

            Then("it's stored on the real account row") {
                updated.monthlyDuesAmount shouldBe BigDecimal("2000")
            }
        }

        When("a member who isn't the owner tries to set the dues amount") {
            every { groupAccountRepository.findById("grp_1") } returns Optional.of(account)

            Then("it throws GroupAccountNotFoundException, matching every other real settlement-authority action") {
                try {
                    service.setDuesAmount("member_2", "grp_1", BigDecimal("2000"))
                    error("expected GroupAccountNotFoundException")
                } catch (e: GroupAccountNotFoundException) {
                    verify(exactly = 0) { groupAccountRepository.save(any()) }
                }
            }
        }

        When("checking real dues status for the current cycle, with one member paid and one unpaid") {
            val duesAccount = GroupAccount(id = "grp_1", name = "Roommates", ownerId = "owner_1", accountId = "account_grp_1", monthlyDuesAmount = BigDecimal("2000"))
            val cycleMonth = java.time.YearMonth.now().toString()
            every { groupAccountRepository.findById("grp_1") } returns Optional.of(duesAccount)
            every { groupAccountMemberRepository.findByGroupAccountIdAndUserId("grp_1", "owner_1") } returns
                GroupAccountMember(id = "m1", groupAccountId = "grp_1", userId = "owner_1")
            every { groupAccountMemberRepository.findByGroupAccountId("grp_1") } returns listOf(
                GroupAccountMember(id = "m1", groupAccountId = "grp_1", userId = "owner_1"),
                GroupAccountMember(id = "m2", groupAccountId = "grp_1", userId = "member_2"),
            )
            every { userRepository.findAllById(any<List<String>>()) } returns listOf(user("owner_1"), user("member_2"))
            every { groupAccountContributionRepository.findByGroupAccountIdAndCycleMonth("grp_1", cycleMonth) } returns listOf(
                GroupAccountContribution(id = "c1", groupAccountId = "grp_1", userId = "owner_1", cycleMonth = cycleMonth, amount = BigDecimal("2000")),
                GroupAccountContribution(id = "c2", groupAccountId = "grp_1", userId = "member_2", cycleMonth = cycleMonth, amount = BigDecimal("500")),
            )

            val status = service.getDuesStatus("owner_1", "grp_1")

            Then("it correctly computes paid vs unpaid live from real deposits, never a separately-tracked flag") {
                status.duesAmount shouldBe BigDecimal("2000")
                status.members.first { it.userId == "owner_1" }.paid shouldBe true
                status.members.first { it.userId == "owner_1" }.contributedAmount shouldBe BigDecimal("2000")
                status.members.first { it.userId == "member_2" }.paid shouldBe false
                status.members.first { it.userId == "member_2" }.contributedAmount shouldBe BigDecimal("500")
            }
        }

        When("the owner requests reminders for real unpaid members") {
            val duesAccount = GroupAccount(id = "grp_1", name = "Roommates", ownerId = "owner_1", accountId = "account_grp_1", monthlyDuesAmount = BigDecimal("2000"))
            val cycleMonth = java.time.YearMonth.now().toString()
            every { groupAccountRepository.findById("grp_1") } returns Optional.of(duesAccount)
            every { groupAccountMemberRepository.findByGroupAccountId("grp_1") } returns listOf(
                GroupAccountMember(id = "m1", groupAccountId = "grp_1", userId = "owner_1"),
                GroupAccountMember(id = "m2", groupAccountId = "grp_1", userId = "member_2"),
                GroupAccountMember(id = "m3", groupAccountId = "grp_1", userId = "member_3"),
            )
            every { groupAccountContributionRepository.findByGroupAccountIdAndCycleMonth("grp_1", cycleMonth) } returns listOf(
                GroupAccountContribution(id = "c1", groupAccountId = "grp_1", userId = "owner_1", cycleMonth = cycleMonth, amount = BigDecimal("2000")),
            )
            // member_2 hasn't paid and hasn't been reminded yet -- member_3 hasn't paid but was already reminded this cycle.
            every { groupAccountDuesReminderRepository.existsByGroupAccountIdAndUserIdAndCycleMonth("grp_1", "member_2", cycleMonth) } returns false
            every { groupAccountDuesReminderRepository.existsByGroupAccountIdAndUserIdAndCycleMonth("grp_1", "member_3", cycleMonth) } returns true
            every { notificationRepository.save(any()) } answers { firstArg() }
            every { groupAccountDuesReminderRepository.save(any()) } answers { firstArg() }

            val remindedCount = service.requestUnpaidDues("owner_1", "grp_1")

            Then("it reminds only the real never-yet-reminded unpaid member -- not the paid owner, not the already-reminded member") {
                remindedCount shouldBe 1
                verify(exactly = 1) { notificationRepository.save(match { it.userId == "member_2" && it.type == "GROUP_ACCOUNT_DUES_REMINDER" }) }
                verify(exactly = 0) { notificationRepository.save(match { it.userId == "owner_1" && it.type == "GROUP_ACCOUNT_DUES_REMINDER" }) }
                verify(exactly = 0) { notificationRepository.save(match { it.userId == "member_3" && it.type == "GROUP_ACCOUNT_DUES_REMINDER" }) }
            }

            Then("only the real never-yet-reminded unpaid member gets a real mobile push notification") {
                verify(exactly = 1) { pushNotificationService.sendToUser("member_2", any(), any(), any()) }
                verify(exactly = 0) { pushNotificationService.sendToUser("owner_1", any(), any(), any()) }
                verify(exactly = 0) { pushNotificationService.sendToUser("member_3", any(), any(), any()) }
            }
        }

        When("a member who isn't the owner tries to request unpaid-dues reminders") {
            val duesAccount = GroupAccount(id = "grp_1", name = "Roommates", ownerId = "owner_1", accountId = "account_grp_1", monthlyDuesAmount = BigDecimal("2000"))
            every { groupAccountRepository.findById("grp_1") } returns Optional.of(duesAccount)

            Then("it throws GroupAccountNotFoundException before touching notifications") {
                try {
                    service.requestUnpaidDues("member_2", "grp_1")
                    error("expected GroupAccountNotFoundException")
                } catch (e: GroupAccountNotFoundException) {
                    verify(exactly = 0) { notificationRepository.save(any()) }
                }
            }
        }

        When("the owner requests reminders but never configured a dues amount") {
            every { groupAccountRepository.findById("grp_1") } returns Optional.of(account)

            Then("it throws a real, honest IllegalArgumentException rather than silently reminding nobody") {
                try {
                    service.requestUnpaidDues("owner_1", "grp_1")
                    error("expected IllegalArgumentException")
                } catch (e: IllegalArgumentException) {
                    verify(exactly = 0) { notificationRepository.save(any()) }
                }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
