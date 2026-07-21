package rw.itunda.agents

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.bigdecimal.shouldBeEqualIgnoringScale
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import rw.itunda.core.domain.Agent
import rw.itunda.core.domain.AgentStatus
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.Wallet
import rw.itunda.core.domain.WalletType
import rw.itunda.core.ledger.LedgerPostResult
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.AgentCashInRepository
import rw.itunda.core.repository.AgentCashOutRepository
import rw.itunda.core.repository.AgentOperatorRepository
import rw.itunda.core.repository.AgentTillReconciliationRepository
import rw.itunda.core.repository.AgentRepository
import rw.itunda.core.repository.LedgerAccountRepository
import rw.itunda.core.repository.TransactionRepository
import rw.itunda.core.repository.WalletRepository
import rw.itunda.core.repository.UserRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.agents.AgentWithdrawalAuthorizationService
import java.math.BigDecimal
import java.util.Optional

class AgentServiceTest : BehaviorSpec({
    val agentRepository = mockk<AgentRepository>()
    val cashInRepository = mockk<AgentCashInRepository>()
    val cashOutRepository = mockk<AgentCashOutRepository>()
    val operatorRepository = mockk<AgentOperatorRepository>()
    val tillReconciliationRepository = mockk<AgentTillReconciliationRepository>()
    val walletRepository = mockk<WalletRepository>()
    val ledgerAccountRepository = mockk<LedgerAccountRepository>()
    val ledgerService = mockk<LedgerService>()
    val transactionRepository = mockk<TransactionRepository>()
    val userRepository = mockk<UserRepository>()
    val withdrawalAuthorizationService = mockk<AgentWithdrawalAuthorizationService>()
    val notificationRepository = mockk<NotificationRepository>()
    val service = AgentService(agentRepository, operatorRepository, tillReconciliationRepository, cashInRepository, cashOutRepository, walletRepository, ledgerAccountRepository, ledgerService, transactionRepository, userRepository, withdrawalAuthorizationService, notificationRepository)
    val agent = Agent("agent_1", "Kigali Central", "agent_cash_1", AgentStatus.ACTIVE, BigDecimal("100000"))
    val wallet = Wallet("wallet_1", "user_1", "2024100001", "Jean Main", WalletType.MAIN, BigDecimal("1000"), BigDecimal("1000"))

    Given("an active agent and an Itunda main account") {
        every { agentRepository.findByIdForUpdate(agent.id) } returns Optional.of(agent)
        every { cashInRepository.existsByReceiptNumber(any()) } returns false
        every { cashOutRepository.existsByReceiptNumber(any()) } returns false
        every { cashInRepository.sumAmountByAgentIdBetween(any(), any(), any()) } returns BigDecimal.ZERO
        every { walletRepository.findByAccountNumber(wallet.accountNumber) } returns wallet
        every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_1", emptyList())
        every { cashInRepository.save(any()) } answers { firstArg() }
        every { transactionRepository.save(any()) } answers { firstArg() }
        every { notificationRepository.save(any()) } answers { firstArg() }

        When("an operator accepts cash") {
            val result = service.cashIn(agent.id, wallet.accountNumber, BigDecimal("25000"), "KGL-001", "admin_1")

            Then("the customer wallet is credited against that agent's dedicated cash account") {
                result["newBalance"] shouldBe wallet.balance
                val legs = slot<List<rw.itunda.core.ledger.LedgerLeg>>()
                verify(exactly = 1) { ledgerService.postLedgerTransaction("RWF", capture(legs)) }
                legs.captured.size shouldBe 2
                legs.captured[0].accountId shouldBe agent.cashAccountId
                legs.captured[0].accountType shouldBe LedgerAccountType.AGENT_CASH
                legs.captured[0].direction shouldBe LedgerDirection.DEBIT
                legs.captured[0].amount shouldBeEqualIgnoringScale BigDecimal("25000")
                legs.captured[1].accountId shouldBe wallet.id
                legs.captured[1].accountType shouldBe LedgerAccountType.WALLET
                legs.captured[1].direction shouldBe LedgerDirection.CREDIT
                legs.captured[1].amount shouldBeEqualIgnoringScale BigDecimal("25000")
                verify(exactly = 1) { cashInRepository.save(any()) }
                verify(exactly = 1) { transactionRepository.save(any()) }
            }
        }
    }

    Given("a receipt that has already been accepted") {
        every { cashInRepository.existsByReceiptNumber("KGL-DUPLICATE") } returns true

        Then("it is rejected before any ledger posting") {
            shouldThrow<AgentReceiptAlreadyUsedException> {
                service.cashIn(agent.id, wallet.accountNumber, BigDecimal("100"), "KGL-DUPLICATE", "admin_1")
            }
            // The successful case above made one call; this rejected path must not add another.
            verify(exactly = 1) { ledgerService.postLedgerTransaction(any(), any()) }
        }
    }

    Given("a cash-in that exceeds an agent's daily limit") {
        every { cashInRepository.existsByReceiptNumber("KGL-LIMIT") } returns false
        every { agentRepository.findByIdForUpdate(agent.id) } returns Optional.of(agent)
        every { cashInRepository.sumAmountByAgentIdBetween(any(), any(), any()) } returns BigDecimal("90000")

        Then("it is rejected before the wallet is resolved or credited") {
            shouldThrow<AgentDailyLimitExceededException> {
                service.cashIn(agent.id, wallet.accountNumber, BigDecimal("10001"), "KGL-LIMIT", "admin_1")
            }
            // The successful case above made one call to each dependency; this rejection
            // must happen before either gets another call.
            verify(exactly = 1) { walletRepository.findByAccountNumber(any()) }
            verify(exactly = 1) { ledgerService.postLedgerTransaction(any(), any()) }
        }
    }
})
