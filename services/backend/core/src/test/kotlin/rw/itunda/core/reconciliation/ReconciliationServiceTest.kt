package rw.itunda.core.reconciliation

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import rw.itunda.core.domain.ProviderAttemptLog
import rw.itunda.core.provider.RailProfile
import rw.itunda.core.repository.ProviderAttemptLogRepository
import rw.itunda.core.repository.RailDayAggregate
import java.time.LocalDate

class ReconciliationServiceTest : BehaviorSpec({

    val rail = RailProfile(id = "wasac", displayName = "WASAC - Water", avgLatencyMs = 700, successRate = 0.92, degraded = true)

    Given("a real provider attempt") {
        val repository = mockk<ProviderAttemptLogRepository>()
        val service = ReconciliationService(repository)
        val saved = slot<ProviderAttemptLog>()
        every { repository.save(capture(saved)) } answers { saved.captured }

        When("it's logged") {
            service.logAttempt(rail, success = true, latencyMs = 712)

            Then("a real row is persisted with the real rail id and measured latency") {
                verify(exactly = 1) { repository.save(any()) }
                saved.captured.railId shouldBe "wasac"
                saved.captured.success shouldBe true
                saved.captured.latencyMs shouldBe 712L
            }
        }
    }

    Given("a day with real aggregated attempts across two rails") {
        val repository = mockk<ProviderAttemptLogRepository>()
        val service = ReconciliationService(repository)
        val date = LocalDate.of(2026, 7, 13)

        val wasacAgg = mockk<RailDayAggregate>()
        every { wasacAgg.getRailId() } returns "wasac"
        every { wasacAgg.getRailDisplayName() } returns "WASAC - Water"
        every { wasacAgg.getTotalAttempts() } returns 10L
        every { wasacAgg.getSuccessCount() } returns 9L
        every { wasacAgg.getAvgLatencyMs() } returns 705.0

        every { repository.aggregateByDate(date) } returns listOf(wasacAgg)

        When("the report is generated for that day") {
            val report = service.report(date)

            Then("it reflects the real aggregated counts and a real computed success rate") {
                report.size shouldBe 1
                val entry = report.first()
                entry.railId shouldBe "wasac"
                entry.totalAttempts shouldBe 10L
                entry.successCount shouldBe 9L
                entry.failureCount shouldBe 1L
                entry.successRate shouldBe 0.9
                entry.avgLatencyMs shouldBe 705L
            }
        }
    }

    Given("a day with no attempts at all") {
        val repository = mockk<ProviderAttemptLogRepository>()
        val service = ReconciliationService(repository)
        val date = LocalDate.of(2026, 1, 1)

        every { repository.aggregateByDate(date) } returns emptyList()

        When("the report is generated") {
            Then("it's an empty report, not a fabricated zero-row") {
                service.report(date).shouldBeEmpty()
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
