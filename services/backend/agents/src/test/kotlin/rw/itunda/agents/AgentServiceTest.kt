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
        // Real agent commission (2026-07-27) -- see AgentCommissionSchedule's own doc
        // comment. This existing test's own operator ("admin_1") has no real wallet
        // stubbed here, so commission is honestly skipped -- the pre-existing 2-leg
        // assertion below stays correct unchanged. See this file's own new commission-
        // specific Given blocks for the case where a real operator wallet exists.
        every { walletRepository.findByUserIdAndType("admin_1", WalletType.MAIN) } returns null
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

    // Real MTN MoMo-style agent commission (2026-07-27) -- see
    // AgentCommissionSchedule's own doc comment. A fresh, fully isolated set of mocks
    // (this file's own outer mocks accumulate call counts across every prior Given
    // block in declaration order, so a genuinely new scenario needs its own).
    Given("a real operator with their own real itunda wallet, accepting a real cash-in") {
        val agentRepository2 = mockk<AgentRepository>()
        val cashInRepository2 = mockk<AgentCashInRepository>()
        val cashOutRepository2 = mockk<AgentCashOutRepository>()
        val operatorRepository2 = mockk<AgentOperatorRepository>()
        val tillReconciliationRepository2 = mockk<AgentTillReconciliationRepository>()
        val walletRepository2 = mockk<WalletRepository>()
        val ledgerAccountRepository2 = mockk<LedgerAccountRepository>()
        val ledgerService2 = mockk<LedgerService>()
        val transactionRepository2 = mockk<TransactionRepository>()
        val userRepository2 = mockk<UserRepository>()
        val withdrawalAuthorizationService2 = mockk<AgentWithdrawalAuthorizationService>()
        val notificationRepository2 = mockk<NotificationRepository>()
        val service2 = AgentService(agentRepository2, operatorRepository2, tillReconciliationRepository2, cashInRepository2, cashOutRepository2, walletRepository2, ledgerAccountRepository2, ledgerService2, transactionRepository2, userRepository2, withdrawalAuthorizationService2, notificationRepository2)

        val agent2 = Agent("agent_2", "Nyamirambo Branch", "agent_cash_2", AgentStatus.ACTIVE, BigDecimal("100000"))
        val customerWallet = Wallet("wallet_customer", "user_customer", "2024100002", "Customer", WalletType.MAIN, BigDecimal("1000"), BigDecimal("1000"))
        val operatorWallet = Wallet("wallet_operator", "user_operator", "2024100003", "Operator", WalletType.MAIN, BigDecimal("500"), BigDecimal("500"))

        every { agentRepository2.findByIdForUpdate(agent2.id) } returns Optional.of(agent2)
        every { cashInRepository2.existsByReceiptNumber(any()) } returns false
        every { cashOutRepository2.existsByReceiptNumber(any()) } returns false
        every { cashInRepository2.sumAmountByAgentIdBetween(any(), any(), any()) } returns BigDecimal.ZERO
        every { walletRepository2.findByAccountNumber(customerWallet.accountNumber) } returns customerWallet
        every { walletRepository2.findByUserIdAndType("user_operator", WalletType.MAIN) } returns operatorWallet
        every { ledgerService2.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_commission_1", emptyList())
        every { cashInRepository2.save(any()) } answers { firstArg() }
        every { transactionRepository2.save(any()) } answers { firstArg() }
        every { notificationRepository2.save(any()) } answers { firstArg() }

        When("cashing in a real 15,000 RWF amount (the 1% tier-2 band)") {
            val result = service2.cashIn(agent2.id, customerWallet.accountNumber, BigDecimal("15000"), "KGL-C-001", "user_operator")

            Then("it real-credits the operator's own wallet with the correct tiered commission, as extra legs in the SAME ledger transaction") {
                val legs = mutableListOf<List<rw.itunda.core.ledger.LedgerLeg>>()
                verify(exactly = 1) { ledgerService2.postLedgerTransaction("RWF", capture(legs)) }
                legs.first().size shouldBe 4
                val commissionDebit = legs.first().first { it.accountType == LedgerAccountType.AGENT_COMMISSION_EXPENSE }
                commissionDebit.direction shouldBe LedgerDirection.DEBIT
                commissionDebit.amount shouldBeEqualIgnoringScale BigDecimal("150")
                val commissionCredit = legs.first().first { it.accountId == "wallet_operator" && it.accountType == LedgerAccountType.WALLET }
                commissionCredit.direction shouldBe LedgerDirection.CREDIT
                commissionCredit.amount shouldBeEqualIgnoringScale BigDecimal("150")
                result["operatorCommission"] shouldBe BigDecimal("150.00")
            }
        }
    }

    Given("a real operator with no real itunda wallet at all, accepting a real cash-in") {
        val agentRepository3 = mockk<AgentRepository>()
        val cashInRepository3 = mockk<AgentCashInRepository>()
        val cashOutRepository3 = mockk<AgentCashOutRepository>()
        val operatorRepository3 = mockk<AgentOperatorRepository>()
        val tillReconciliationRepository3 = mockk<AgentTillReconciliationRepository>()
        val walletRepository3 = mockk<WalletRepository>()
        val ledgerAccountRepository3 = mockk<LedgerAccountRepository>()
        val ledgerService3 = mockk<LedgerService>()
        val transactionRepository3 = mockk<TransactionRepository>()
        val userRepository3 = mockk<UserRepository>()
        val withdrawalAuthorizationService3 = mockk<AgentWithdrawalAuthorizationService>()
        val notificationRepository3 = mockk<NotificationRepository>()
        val service3 = AgentService(agentRepository3, operatorRepository3, tillReconciliationRepository3, cashInRepository3, cashOutRepository3, walletRepository3, ledgerAccountRepository3, ledgerService3, transactionRepository3, userRepository3, withdrawalAuthorizationService3, notificationRepository3)

        val agent3 = Agent("agent_3", "Kimisagara Branch", "agent_cash_3", AgentStatus.ACTIVE, BigDecimal("100000"))
        val customerWallet3 = Wallet("wallet_customer3", "user_customer3", "2024100004", "Customer", WalletType.MAIN, BigDecimal("1000"), BigDecimal("1000"))

        every { agentRepository3.findByIdForUpdate(agent3.id) } returns Optional.of(agent3)
        every { cashInRepository3.existsByReceiptNumber(any()) } returns false
        every { cashOutRepository3.existsByReceiptNumber(any()) } returns false
        every { cashInRepository3.sumAmountByAgentIdBetween(any(), any(), any()) } returns BigDecimal.ZERO
        every { walletRepository3.findByAccountNumber(customerWallet3.accountNumber) } returns customerWallet3
        every { walletRepository3.findByUserIdAndType("wallet_less_operator", WalletType.MAIN) } returns null
        every { ledgerService3.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_commission_2", emptyList())
        every { cashInRepository3.save(any()) } answers { firstArg() }
        every { transactionRepository3.save(any()) } answers { firstArg() }
        every { notificationRepository3.save(any()) } answers { firstArg() }

        When("cashing in a real amount") {
            val result = service3.cashIn(agent3.id, customerWallet3.accountNumber, BigDecimal("15000"), "KGL-C-002", "wallet_less_operator")

            Then("it honestly skips the commission -- never blocking the real customer's own cash-in") {
                val legs = mutableListOf<List<rw.itunda.core.ledger.LedgerLeg>>()
                verify(exactly = 1) { ledgerService3.postLedgerTransaction("RWF", capture(legs)) }
                legs.first().size shouldBe 2
                result["operatorCommission"] shouldBe BigDecimal.ZERO
            }
        }
    }

    Given("real amounts across every real commission tier band") {
        Then("computeCommission matches the real sourced tiered structure exactly") {
            AgentCommissionSchedule.computeCommission(BigDecimal("3000")) shouldBeEqualIgnoringScale BigDecimal("50")
            AgentCommissionSchedule.computeCommission(BigDecimal("15000")) shouldBeEqualIgnoringScale BigDecimal("150")
            AgentCommissionSchedule.computeCommission(BigDecimal("50000")) shouldBeEqualIgnoringScale BigDecimal("750")
            // Real per-transaction cap: 2% of 500,000 would be 10,000, capped at 3,000.
            AgentCommissionSchedule.computeCommission(BigDecimal("500000")) shouldBeEqualIgnoringScale BigDecimal("3000")
        }
    }
})
