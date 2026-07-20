package rw.itunda.savings

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.GroupAccount
import rw.itunda.core.domain.GroupAccountMember
import rw.itunda.core.domain.User
import rw.itunda.core.domain.Wallet
import rw.itunda.core.domain.WalletType
import rw.itunda.core.ledger.InsufficientFundsException
import rw.itunda.core.ledger.LedgerPostResult
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.GroupAccountMemberRepository
import rw.itunda.core.repository.GroupAccountRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.UserRepository
import rw.itunda.core.repository.WalletRepository
import java.math.BigDecimal
import java.time.Instant
import java.util.Optional

/** First test coverage for the real Kakao Bank 모임통장 (group account) equivalent. */
class GroupAccountServiceTest : BehaviorSpec({

    fun wallet(id: String, userId: String, type: WalletType = WalletType.MAIN, balance: BigDecimal = BigDecimal("100000")) = Wallet(
        id = id, userId = userId, accountNumber = "ACC-$id", accountName = "Test wallet",
        type = type, balance = balance, availableBalance = balance,
    )

    fun user(id: String, firstName: String = "Test") = User(
        id = id, phoneNumber = "+25078800$id".take(13), firstName = firstName, lastName = "User",
        passwordHash = "unused", createdAt = Instant.now(),
    )

    Given("creating a group account") {
        val groupAccountRepository = mockk<GroupAccountRepository>()
        val groupAccountMemberRepository = mockk<GroupAccountMemberRepository>()
        val walletRepository = mockk<WalletRepository>()
        val userRepository = mockk<UserRepository>()
        val notificationRepository = mockk<NotificationRepository>()
        val ledgerService = mockk<LedgerService>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val service = GroupAccountService(groupAccountRepository, groupAccountMemberRepository, walletRepository, userRepository, notificationRepository, ledgerService, rateLimiter)

        When("an owner creates a new group account") {
            every { userRepository.findById("owner_1") } returns Optional.of(user("owner_1"))
            every { walletRepository.save(any()) } answers { firstArg() }
            every { groupAccountRepository.save(any()) } answers { firstArg() }
            val memberSlot = mutableListOf<GroupAccountMember>()
            every { groupAccountMemberRepository.save(capture(memberSlot)) } answers { firstArg() }

            val account = service.createGroupAccount("owner_1", "Roommates")

            Then("it provisions a real zero-balance GROUP wallet and adds the owner as a real member") {
                account.ownerId shouldBe "owner_1"
                account.name shouldBe "Roommates"
                val walletSlot = mutableListOf<Wallet>()
                verify(exactly = 1) { walletRepository.save(capture(walletSlot)) }
                walletSlot.single().type shouldBe WalletType.GROUP
                walletSlot.single().balance shouldBe BigDecimal.ZERO
                memberSlot.single().userId shouldBe "owner_1"
                verify(exactly = 1) { rateLimiter.checkLimit("group-account:create:owner_1", limit = 10, window = any()) }
            }
        }
    }

    Given("a group account with an owner and one invited member") {
        val groupAccountRepository = mockk<GroupAccountRepository>()
        val groupAccountMemberRepository = mockk<GroupAccountMemberRepository>()
        val walletRepository = mockk<WalletRepository>()
        val userRepository = mockk<UserRepository>()
        val notificationRepository = mockk<NotificationRepository>()
        val ledgerService = mockk<LedgerService>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val service = GroupAccountService(groupAccountRepository, groupAccountMemberRepository, walletRepository, userRepository, notificationRepository, ledgerService, rateLimiter)

        val account = GroupAccount(id = "grp_1", name = "Roommates", ownerId = "owner_1", walletId = "wallet_grp_1")

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
        }

        When("someone who isn't the owner tries to invite a member") {
            every { groupAccountRepository.findById("grp_1") } returns Optional.of(account)

            Then("it throws GroupAccountNotOwnerException before touching the member repository") {
                try {
                    service.inviteMember("member_2", "grp_1", "+250788000003")
                    error("expected GroupAccountNotOwnerException")
                } catch (e: GroupAccountNotOwnerException) {
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
            every { walletRepository.findByUserIdAndType("member_2", WalletType.MAIN) } returns wallet("wallet_member_2", "member_2")
            every { walletRepository.findById("wallet_grp_1") } returns Optional.of(wallet("wallet_grp_1", "owner_1", WalletType.GROUP, BigDecimal("5000")))
            every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_1", emptyList())
            every { groupAccountMemberRepository.findByGroupAccountId("grp_1") } returns listOf(
                GroupAccountMember(id = "m1", groupAccountId = "grp_1", userId = "owner_1"),
                GroupAccountMember(id = "m2", groupAccountId = "grp_1", userId = "member_2"),
            )
            every { userRepository.findAllById(any<List<String>>()) } returns listOf(user("owner_1"), user("member_2"))
            every { notificationRepository.save(any()) } answers { firstArg() }

            val result = service.deposit("member_2", "grp_1", BigDecimal("3000"))

            Then("it posts a real ledger transfer from the member's own MAIN wallet and notifies the other real members, not the depositor") {
                verify(exactly = 1) { ledgerService.postLedgerTransaction(any(), any()) }
                verify(exactly = 1) { notificationRepository.save(match { it.userId == "owner_1" && it.type == "GROUP_ACCOUNT_ACTIVITY" }) }
                verify(exactly = 0) { notificationRepository.save(match { it.userId == "member_2" && it.type == "GROUP_ACCOUNT_ACTIVITY" }) }
                result.members.size shouldBe 2
            }
        }

        When("a non-member tries to deposit") {
            every { groupAccountRepository.findById("grp_1") } returns Optional.of(account)
            every { groupAccountMemberRepository.findByGroupAccountIdAndUserId("grp_1", "outsider") } returns null

            Then("it throws GroupAccountNotMemberException before touching the ledger") {
                try {
                    service.deposit("outsider", "grp_1", BigDecimal("1000"))
                    error("expected GroupAccountNotMemberException")
                } catch (e: GroupAccountNotMemberException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }

        When("the owner withdraws from the group account") {
            every { groupAccountRepository.findById("grp_1") } returns Optional.of(account)
            every { walletRepository.findById("wallet_grp_1") } returns Optional.of(wallet("wallet_grp_1", "owner_1", WalletType.GROUP, BigDecimal("5000")))
            every { walletRepository.findByUserIdAndType("owner_1", WalletType.MAIN) } returns wallet("wallet_owner_1", "owner_1")
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

            Then("it posts a real ledger transfer to the owner's own MAIN wallet and notifies other members transparently") {
                verify(exactly = 1) { ledgerService.postLedgerTransaction(any(), any()) }
                verify(exactly = 1) { notificationRepository.save(match { it.userId == "member_2" && it.type == "GROUP_ACCOUNT_ACTIVITY" }) }
            }
        }

        When("a member who isn't the owner tries to withdraw") {
            every { groupAccountRepository.findById("grp_1") } returns Optional.of(account)

            Then("it throws GroupAccountNotOwnerException -- matching Kakao Bank's real withdrawal-authority-stays-with-organizer rule") {
                try {
                    service.withdraw("member_2", "grp_1", BigDecimal("1000"))
                    error("expected GroupAccountNotOwnerException")
                } catch (e: GroupAccountNotOwnerException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }

        When("the owner tries to withdraw more than the real group balance") {
            every { groupAccountRepository.findById("grp_1") } returns Optional.of(account)
            every { walletRepository.findById("wallet_grp_1") } returns Optional.of(wallet("wallet_grp_1", "owner_1", WalletType.GROUP, BigDecimal("1000")))
            every { walletRepository.findByUserIdAndType("owner_1", WalletType.MAIN) } returns wallet("wallet_owner_1", "owner_1")

            Then("it throws InsufficientFundsException without touching the ledger") {
                try {
                    service.withdraw("owner_1", "grp_1", BigDecimal("50000"))
                    error("expected InsufficientFundsException")
                } catch (e: InsufficientFundsException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
