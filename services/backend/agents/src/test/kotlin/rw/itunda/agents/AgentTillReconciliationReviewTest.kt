package rw.itunda.agents

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.core.agents.AgentWithdrawalAuthorizationService
import rw.itunda.core.domain.AgentTillReconciliation
import rw.itunda.core.domain.TillReconciliationStatus
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.AgentCashInRepository
import rw.itunda.core.repository.AgentCashOutRepository
import rw.itunda.core.repository.AgentOperatorRepository
import rw.itunda.core.repository.AgentRepository
import rw.itunda.core.repository.AgentTillReconciliationRepository
import rw.itunda.core.repository.LedgerAccountRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.TransactionRepository
import rw.itunda.core.repository.UserRepository
import rw.itunda.core.repository.WalletRepository
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Optional

class AgentTillReconciliationReviewTest : BehaviorSpec({
    val reconciliations = mockk<AgentTillReconciliationRepository>()
    val service = AgentService(
        mockk<AgentRepository>(), mockk<AgentOperatorRepository>(), reconciliations,
        mockk<AgentCashInRepository>(), mockk<AgentCashOutRepository>(), mockk<WalletRepository>(),
        mockk<LedgerAccountRepository>(), mockk<LedgerService>(), mockk<TransactionRepository>(),
        mockk<UserRepository>(), mockk<AgentWithdrawalAuthorizationService>(), mockk<NotificationRepository>(),
    )

    Given("a pending till variance") {
        val reconciliation = AgentTillReconciliation(
            "rec_1", "agent_1", LocalDate.of(2026, 7, 21), BigDecimal("100000"), BigDecimal("99000"),
            BigDecimal("-1000"), "operator_1", TillReconciliationStatus.PENDING_REVIEW,
        )
        every { reconciliations.findByIdForUpdate("rec_1") } returns Optional.of(reconciliation)
        every { reconciliations.save(reconciliation) } returns reconciliation

        When("an administrator resolves it") {
            val resolved = service.resolveTillReconciliation("rec_1", "admin_1", "Count verified with store manager")

            Then("the decision is made against the locked reconciliation row") {
                resolved.status shouldBe TillReconciliationStatus.RESOLVED
                resolved.reviewedByUserId shouldBe "admin_1"
                verify(exactly = 1) { reconciliations.findByIdForUpdate("rec_1") }
                verify(exactly = 0) { reconciliations.findById("rec_1") }
            }
        }
    }

    Given("a reconciliation another administrator already resolved") {
        val reconciliation = AgentTillReconciliation(
            "rec_2", "agent_1", LocalDate.of(2026, 7, 21), BigDecimal("100000"), BigDecimal("99000"),
            BigDecimal("-1000"), "operator_1", TillReconciliationStatus.RESOLVED,
        )
        every { reconciliations.findByIdForUpdate("rec_2") } returns Optional.of(reconciliation)

        Then("a second review is rejected without overwriting the audit record") {
            shouldThrow<IllegalArgumentException> {
                service.resolveTillReconciliation("rec_2", "admin_2", "A conflicting second decision")
            }
            verify(exactly = 0) { reconciliations.save(reconciliation) }
        }
    }
})
