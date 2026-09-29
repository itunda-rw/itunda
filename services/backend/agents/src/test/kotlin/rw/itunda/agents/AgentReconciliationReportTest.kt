package rw.itunda.agents

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.bigdecimal.shouldBeEqualIgnoringScale
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.core.agents.AgentWithdrawalAuthorizationService
import rw.itunda.auth.RateLimiter
import rw.itunda.core.fraud.FraudRuleEngine
import rw.itunda.core.domain.AgentTillReconciliation
import rw.itunda.core.domain.TillReconciliationStatus
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.push.PushNotificationService
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
import rw.itunda.core.repository.AccountRepository
import java.math.BigDecimal
import java.time.LocalDate

class AgentReconciliationReportTest : BehaviorSpec({
    val reconciliations = mockk<AgentTillReconciliationRepository>()
    val service = AgentService(
        mockk<AgentRepository>(), mockk<AgentOperatorRepository>(), reconciliations, mockk<AgentTillFundingRepository>(),
        mockk<AgentCashInRepository>(), mockk<AgentCashOutRepository>(), mockk<AccountRepository>(),
        mockk<LedgerAccountRepository>(), mockk<LedgerService>(), mockk<TransactionRepository>(),
        mockk<UserRepository>(), mockk<AgentWithdrawalAuthorizationService>(), mockk<NotificationRepository>(), mockk<PushNotificationService>(relaxed = true), mockk<FraudRuleEngine>(relaxed = true), mockk<RateLimiter>(relaxed = true),
    )
    val from = LocalDate.of(2026, 7, 1)
    val to = LocalDate.of(2026, 7, 2)

    Given("daily till counts with a pending variance") {
        val matched = AgentTillReconciliation("rec_1", "agent_1", from, BigDecimal("100"), BigDecimal("100"), BigDecimal.ZERO, "operator_1", TillReconciliationStatus.MATCHED)
        val pending = AgentTillReconciliation("rec_2", "agent_2", to, BigDecimal("300"), BigDecimal("275"), BigDecimal("-25"), "operator_2", TillReconciliationStatus.PENDING_REVIEW)
        every { reconciliations.findByBusinessDateBetweenOrderByCreatedAtDesc(from, to) } returns listOf(pending, matched)

        When("operations requests a bounded report") {
            val report = service.reconciliationReport(from, to)

            Then("it retains the entries and makes outstanding exposure explicit") {
                report.reconciliations shouldBe listOf(pending, matched)
                report.pendingReviewCount shouldBe 1
                report.totalVariance shouldBeEqualIgnoringScale BigDecimal("-25")
                verify(exactly = 1) { reconciliations.findByBusinessDateBetweenOrderByCreatedAtDesc(from, to) }
            }
        }
    }

    Given("an unsafe report range") {
        Then("it is rejected before querying operational records") {
            shouldThrow<IllegalArgumentException> { service.reconciliationReport(to, from) }
            shouldThrow<IllegalArgumentException> { service.reconciliationReport(from, from.plusDays(31)) }
            verify(exactly = 1) { reconciliations.findByBusinessDateBetweenOrderByCreatedAtDesc(any(), any()) }
        }
    }
})
