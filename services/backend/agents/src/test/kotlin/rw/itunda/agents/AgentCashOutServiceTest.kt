package rw.itunda.agents

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
import rw.itunda.core.domain.LedgerAccount
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.Account
import rw.itunda.core.domain.AccountType
import rw.itunda.core.ledger.LedgerPostResult
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.AgentCashInRepository
import rw.itunda.core.repository.AgentCashOutRepository
import rw.itunda.core.repository.AgentRepository
import rw.itunda.core.repository.AgentOperatorRepository
import rw.itunda.core.repository.AgentTillFundingRepository
import rw.itunda.core.repository.AgentTillReconciliationRepository
import rw.itunda.core.repository.LedgerAccountRepository
import rw.itunda.core.repository.TransactionRepository
import rw.itunda.core.repository.AccountRepository
import rw.itunda.core.repository.UserRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.agents.AgentWithdrawalAuthorizationService
import rw.itunda.auth.RateLimiter
import rw.itunda.core.fraud.FraudRuleEngine
import java.math.BigDecimal
import java.util.Optional

class AgentCashOutServiceTest : BehaviorSpec({
    Given("an active agent and a funded Itunda main account") {
        val agentRepository = mockk<AgentRepository>()
        val cashInRepository = mockk<AgentCashInRepository>()
        val cashOutRepository = mockk<AgentCashOutRepository>()
        val operatorRepository = mockk<AgentOperatorRepository>()
        val tillReconciliationRepository = mockk<AgentTillReconciliationRepository>()
        val agentTillFundingRepository = mockk<AgentTillFundingRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerAccountRepository = mockk<LedgerAccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val transactionRepository = mockk<TransactionRepository>()
        val userRepository = mockk<UserRepository>()
        val withdrawalAuthorizationService = mockk<AgentWithdrawalAuthorizationService>()
        val notificationRepository = mockk<NotificationRepository>()
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val service = AgentService(agentRepository, operatorRepository, tillReconciliationRepository, agentTillFundingRepository, cashInRepository, cashOutRepository, accountRepository, ledgerAccountRepository, ledgerService, transactionRepository, userRepository, withdrawalAuthorizationService, notificationRepository, pushNotificationService, fraudRuleEngine, rateLimiter)
        val agent = Agent("agent_1", "Kigali Central", "agent_cash_1", AgentStatus.ACTIVE, BigDecimal("100000"), BigDecimal("80000"))
        val account = Account(id = "account_1", userId = "user_1", accountNumber = "2024100001", accountName = "Jean Main", type = AccountType.MAIN, balance = BigDecimal("50000"), availableBalance = BigDecimal("50000"))
        every { cashOutRepository.existsByReceiptNumber("KGL-W-001") } returns false
        every { cashInRepository.existsByReceiptNumber("KGL-W-001") } returns false
        every { agentRepository.findByIdForUpdate(agent.id) } returns Optional.of(agent)
        every { cashOutRepository.sumAmountByAgentIdBetween(any(), any(), any()) } returns BigDecimal.ZERO
        every { accountRepository.findByAccountNumber(account.accountNumber) } returns account
        // Real agent commission (2026-07-27) -- see AgentCommissionSchedule's own doc
        // comment. This existing test's own operator ("admin_1") has no real account
        // stubbed here, so commission is honestly skipped -- the pre-existing 2-leg
        // assertion below stays correct unchanged.
        every { accountRepository.findByUserIdAndType("admin_1", AccountType.MAIN) } returns null
        every { withdrawalAuthorizationService.consume("AUTH001", account.id, BigDecimal("25000")) } returns mockk()
        every { ledgerAccountRepository.findByIdForUpdate(agent.cashAccountId) } returns Optional.of(LedgerAccount(agent.cashAccountId, "Agent cash", BigDecimal("-30000")))
        every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_1", emptyList())
        every { cashOutRepository.save(any()) } answers { firstArg() }
        every { transactionRepository.save(any()) } answers { firstArg() }
        every { notificationRepository.save(any()) } answers { firstArg() }

        When("the operator pays out cash") {
            service.cashOut(agent.id, account.accountNumber, BigDecimal("25000"), "KGL-W-001", "AUTH001", "admin_1")

            Then("the account is debited and that agent's cash account is credited") {
                val legs = slot<List<LedgerLeg>>()
                verify(exactly = 1) { ledgerService.postLedgerTransaction("RWF", capture(legs)) }
                legs.captured.size shouldBe 2
                legs.captured[0].accountId shouldBe account.id
                legs.captured[0].accountType shouldBe LedgerAccountType.WALLET
                legs.captured[0].direction shouldBe LedgerDirection.DEBIT
                legs.captured[0].amount shouldBeEqualIgnoringScale BigDecimal("25000")
                legs.captured[1].accountId shouldBe agent.cashAccountId
                legs.captured[1].accountType shouldBe LedgerAccountType.AGENT_CASH
                legs.captured[1].direction shouldBe LedgerDirection.CREDIT
                legs.captured[1].amount shouldBeEqualIgnoringScale BigDecimal("25000")
            }

            Then("the customer also gets a real mobile push notification, not just the in-app one") {
                verify(exactly = 1) { pushNotificationService.sendToUser("user_1", "Cash withdrawn", any(), any()) }
            }

            // Real gap found live (repo-wide fraud-engine-verification sweep,
            // 2026-09-08): fraudRuleEngine was relaxed = true with zero verify{}
            // anywhere in this file, so a future accidental removal of the real
            // fraudRuleEngine.evaluate call would have compiled and passed silently.
            Then("the real fraud engine is actually consulted, not just mocked away") {
                verify(exactly = 1) { fraudRuleEngine.evaluate("user_1", null, BigDecimal("25000"), "ledgertxn_1") }
            }
        }
    }

    // Real gap closed 2026-09-07 (Agents product-completeness pass): cashOut had zero
    // rateLimiter.checkLimit call before this -- only the daily *amount* limit above,
    // a different concern. Fresh mocks, not the shared `service` above, so stubbing
    // rateLimiter to throw here can't leak into the successful-payout Given block.
    Given("a real agent operator who has already hit the real cash-out rate limit") {
        val agentRepository = mockk<AgentRepository>()
        val cashInRepository = mockk<AgentCashInRepository>()
        val cashOutRepository = mockk<AgentCashOutRepository>()
        val operatorRepository = mockk<AgentOperatorRepository>()
        val tillReconciliationRepository = mockk<AgentTillReconciliationRepository>()
        val agentTillFundingRepository = mockk<AgentTillFundingRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerAccountRepository = mockk<LedgerAccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val transactionRepository = mockk<TransactionRepository>()
        val userRepository = mockk<UserRepository>()
        val withdrawalAuthorizationService = mockk<AgentWithdrawalAuthorizationService>()
        val notificationRepository = mockk<NotificationRepository>()
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>()
        every { rateLimiter.checkLimit("agent:cash-out:agent_1", limit = 30, window = java.time.Duration.ofMinutes(1)) } throws
            rw.itunda.auth.RateLimitExceededException("Too many requests")
        val service = AgentService(agentRepository, operatorRepository, tillReconciliationRepository, agentTillFundingRepository, cashInRepository, cashOutRepository, accountRepository, ledgerAccountRepository, ledgerService, transactionRepository, userRepository, withdrawalAuthorizationService, notificationRepository, pushNotificationService, fraudRuleEngine, rateLimiter)

        Then("cash-out real-429s before ever locking the agent row or touching the ledger") {
            io.kotest.assertions.throwables.shouldThrow<rw.itunda.auth.RateLimitExceededException> {
                service.cashOut("agent_1", "2024100001", BigDecimal("25000"), "KGL-RL-001", "AUTH001", "admin_1")
            }
            verify(exactly = 0) { agentRepository.findByIdForUpdate(any()) }
            verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
        }
    }
})
