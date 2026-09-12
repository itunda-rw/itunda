package rw.itunda.core.agents

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.bigdecimal.shouldBeEqualIgnoringScale
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.core.domain.Account
import rw.itunda.core.domain.AccountType
import rw.itunda.core.repository.AccountRepository
import java.math.BigDecimal
import java.time.Instant

class AgentWithdrawalAuthorizationServiceTest : BehaviorSpec({
    fun authorization(
        id: String,
        code: String,
        accountId: String = "account_1",
        amount: String = "5000",
        expiresAt: Instant = Instant.now().plusSeconds(600),
    ) = AgentWithdrawalAuthorization(id, "user_1", accountId, code, BigDecimal(amount), expiresAt)

    Given("a customer who already has three active cash-out codes") {
        val repository = mockk<AgentWithdrawalAuthorizationRepository>()
        val accountRepository = mockk<AccountRepository>()
        val service = AgentWithdrawalAuthorizationService(repository, accountRepository)
        every { repository.findByUserIdOrderByCreatedAtDesc("user_1") } returns listOf(
            authorization("a1", "CODE00000001"), authorization("a2", "CODE00000002"), authorization("a3", "CODE00000003"),
        )

        Then("it refuses another code without querying or reserving money") {
            shouldThrow<TooManyWithdrawalAuthorizationsException> {
                service.create("user_1", BigDecimal("5000"))
            }
            verify(exactly = 0) { accountRepository.findByUserIdAndType(any(), any()) }
            verify(exactly = 0) { repository.save(any()) }
        }
    }

    Given("a main account and no active codes") {
        val repository = mockk<AgentWithdrawalAuthorizationRepository>()
        val accountRepository = mockk<AccountRepository>()
        val service = AgentWithdrawalAuthorizationService(repository, accountRepository)
        val account = Account(id = "account_1", userId = "user_1", accountNumber = "2024100001", accountName = "Main", type = AccountType.MAIN, balance = BigDecimal("10000"), availableBalance = BigDecimal("10000"))
        every { repository.findByUserIdOrderByCreatedAtDesc("user_1") } returns emptyList()
        every { accountRepository.findByUserIdAndType("user_1", AccountType.MAIN) } returns account
        every { repository.existsByCode(any()) } returns false
        every { repository.save(any()) } answers { firstArg() }

        When("the customer creates a cash-out code") {
            val created = service.create("user_1", BigDecimal("2500"))

            Then("it is bound to their main account with an opaque 12-character code") {
                created.accountId shouldBe "account_1"
                created.amount shouldBeEqualIgnoringScale BigDecimal("2500")
                created.code.length shouldBe 12
                created.code shouldBe created.code.uppercase()
            }
        }
    }

    Given("an active authorization") {
        val repository = mockk<AgentWithdrawalAuthorizationRepository>()
        val service = AgentWithdrawalAuthorizationService(repository, mockk())
        val authorization = authorization("a1", "A1B2C3D4E5F6")
        every { repository.findByCode("A1B2C3D4E5F6") } returns authorization

        When("an agent attempts a payout for a different amount or account") {
            Then("the code remains unconsumed") {
                shouldThrow<WithdrawalAuthorizationInvalidException> {
                    service.consume("a1b2c3d4e5f6", "account_1", BigDecimal("2500"))
                }
                shouldThrow<WithdrawalAuthorizationInvalidException> {
                    service.consume("A1B2C3D4E5F6", "account_other", BigDecimal("5000"))
                }
                authorization.consumedAt shouldBe null
            }
        }

        When("the exact account and amount are supplied") {
            every { repository.save(any()) } answers { firstArg() }
            val consumed = service.consume("a1b2c3d4e5f6", "account_1", BigDecimal("5000"))

            Then("it becomes single-use") {
                (consumed.consumedAt == null) shouldBe false
                shouldThrow<WithdrawalAuthorizationInvalidException> {
                    service.consume("A1B2C3D4E5F6", "account_1", BigDecimal("5000"))
                }
            }
        }
    }

    Given("an expired authorization with otherwise matching payout details") {
        val repository = mockk<AgentWithdrawalAuthorizationRepository>()
        val service = AgentWithdrawalAuthorizationService(repository, mockk())
        val expired = authorization(
            id = "expired_1",
            code = "EXPIRE000001",
            expiresAt = Instant.now().minusSeconds(1),
        )
        every { repository.findByCode("EXPIRE000001") } returns expired

        Then("it cannot debit the customer account after its ten-minute window") {
            shouldThrow<WithdrawalAuthorizationInvalidException> {
                service.consume("expire000001", "account_1", BigDecimal("5000"))
            }
            expired.consumedAt shouldBe null
            verify(exactly = 0) { repository.save(any()) }
        }
    }

    Given("a code that was already cancelled by its owner") {
        val repository = mockk<AgentWithdrawalAuthorizationRepository>()
        val service = AgentWithdrawalAuthorizationService(repository, mockk())
        val cancelled = authorization("cancelled_1", "CANCEL000001").apply { cancelledAt = Instant.now() }
        every { repository.findByCode("CANCEL000001") } returns cancelled

        Then("a retry returns the existing cancellation rather than failing") {
            service.cancel("user_1", "cancel000001") shouldBe cancelled
        }
    }
})
