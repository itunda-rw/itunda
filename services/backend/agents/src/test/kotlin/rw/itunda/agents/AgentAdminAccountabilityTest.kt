package rw.itunda.agents

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.auth.RateLimiter
import rw.itunda.core.agents.AgentWithdrawalAuthorizationService
import rw.itunda.core.domain.Agent
import rw.itunda.core.domain.AgentOperator
import rw.itunda.core.domain.AgentStatus
import rw.itunda.core.domain.User
import rw.itunda.core.fraud.FraudRuleEngine
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.AccountRepository
import rw.itunda.core.repository.AgentCashInRepository
import rw.itunda.core.repository.AgentCashOutRepository
import rw.itunda.core.repository.AgentOperatorRepository
import rw.itunda.core.repository.AgentRepository
import rw.itunda.core.repository.AgentTillFundingRepository
import rw.itunda.core.repository.AgentTillReconciliationRepository
import rw.itunda.core.repository.LedgerAccountRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.TransactionRepository
import rw.itunda.core.repository.UserRepository
import java.math.BigDecimal
import java.util.Optional

/**
 * Real admin-accountability gap closed (2026-09-13) -- AgentAdminController.setStatus/
 * assignOperator/setOperatorStatus previously took no admin identity at all, the same
 * gap class already closed for fundTill (see AgentTillFunding's own doc comment) in
 * this same file/module, just missed for these 3 methods. Zero prior test coverage
 * existed for any of the three (confirmed via repo-wide grep), so this is new coverage,
 * not a regression test for an existing gap. Kept in its own file with fresh mocks
 * rather than added into AgentServiceTest.kt's own top-level SHARED mocks (that file's
 * own established, if unusual, convention -- one set of mocks reused across every
 * Given block in the whole spec) to avoid any risk of polluting that fragile shared
 * state.
 */
class AgentAdminAccountabilityTest : BehaviorSpec({

    fun service(
        agentRepository: AgentRepository = mockk(),
        agentOperatorRepository: AgentOperatorRepository = mockk(),
        userRepository: UserRepository = mockk(),
        notificationRepository: NotificationRepository = mockk<NotificationRepository>(relaxed = true).also { every { it.save(any()) } answers { firstArg() } },
        pushNotificationService: PushNotificationService = mockk(relaxed = true),
    ) = AgentService(
        agentRepository, agentOperatorRepository, mockk(), mockk(), mockk(), mockk(),
        mockk<AccountRepository>(), mockk<LedgerAccountRepository>(), mockk<LedgerService>(), mockk<TransactionRepository>(),
        userRepository, mockk<AgentWithdrawalAuthorizationService>(relaxed = true), notificationRepository,
        pushNotificationService, mockk<FraudRuleEngine>(relaxed = true), mockk<RateLimiter>(relaxed = true),
    )

    Given("an active agent an admin is about to suspend") {
        val agentRepository = mockk<AgentRepository>()
        val service = service(agentRepository = agentRepository)
        val agent = Agent("agent_1", "Kigali Central", "agent_cash_1", AgentStatus.ACTIVE, BigDecimal("100000"))
        every { agentRepository.findById("agent_1") } returns Optional.of(agent)
        every { agentRepository.save(any()) } answers { firstArg() }

        When("admin_1 suspends it") {
            val result = service.setStatus("agent_1", AgentStatus.SUSPENDED, "admin_1")

            Then("it real-flips the status and records which real admin acted") {
                result.status shouldBe AgentStatus.SUSPENDED
                result.statusChangedByUserId shouldBe "admin_1"
                result.statusChangedAt shouldNotBe null
            }
        }
    }

    Given("a real itunda user an admin is about to grant AGENT-role operator access to") {
        val agentRepository = mockk<AgentRepository>()
        val agentOperatorRepository = mockk<AgentOperatorRepository>()
        val userRepository = mockk<UserRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        every { notificationRepository.save(any()) } answers { firstArg() }
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = service(
            agentRepository = agentRepository, agentOperatorRepository = agentOperatorRepository, userRepository = userRepository,
            notificationRepository = notificationRepository, pushNotificationService = pushNotificationService,
        )
        val agent = Agent("agent_1", "Kigali Central", "agent_cash_1", AgentStatus.ACTIVE, BigDecimal("100000"))
        val user = User(id = "user_1", phoneNumber = "+250788000001", firstName = "Jean", lastName = "B", passwordHash = "hash")
        every { agentRepository.findById("agent_1") } returns Optional.of(agent)
        every { agentOperatorRepository.findByUserId("user_1") } returns null
        every { userRepository.findById("user_1") } returns Optional.of(user)
        every { agentOperatorRepository.save(any()) } answers { firstArg() }

        When("admin_1 grants the real AGENT role") {
            val result = service.assignOperator("agent_1", "user_1", "admin_1")

            Then("it real-grants the role and records which real admin approved it") {
                user.role shouldBe "AGENT"
                result.assignedByUserId shouldBe "admin_1"
            }
            // Real sibling-asymmetry gap found live (2026-09-14): granting real
            // cash-handling authority previously notified nobody.
            Then("the newly-granted operator is notified their account authority changed") {
                verify(exactly = 1) { notificationRepository.save(match { it.userId == "user_1" && it.type == "agent_operator_status_change" }) }
                verify(exactly = 1) { pushNotificationService.sendToUser("user_1", "You're now an agent operator", any()) }
            }
        }
    }

    Given("a real active agent operator an admin is about to deactivate") {
        val agentOperatorRepository = mockk<AgentOperatorRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        every { notificationRepository.save(any()) } answers { firstArg() }
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = service(agentOperatorRepository = agentOperatorRepository, notificationRepository = notificationRepository, pushNotificationService = pushNotificationService)
        val operator = AgentOperator("agent_operator_1", "agent_1", "user_1")
        every { agentOperatorRepository.findByUserId("user_1") } returns operator
        every { agentOperatorRepository.save(any()) } answers { firstArg() }

        When("admin_1 deactivates it") {
            val result = service.setOperatorStatus("agent_1", "user_1", false, "admin_1")

            Then("it real-flips isActive and records which real admin acted") {
                result.isActive shouldBe false
                result.statusChangedByUserId shouldBe "admin_1"
                result.statusChangedAt shouldNotBe null
            }
            Then("the affected operator is notified their access was suspended") {
                verify(exactly = 1) { notificationRepository.save(match { it.userId == "user_1" && it.type == "agent_operator_status_change" }) }
                verify(exactly = 1) { pushNotificationService.sendToUser("user_1", "Your agent operator access was suspended", any()) }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
