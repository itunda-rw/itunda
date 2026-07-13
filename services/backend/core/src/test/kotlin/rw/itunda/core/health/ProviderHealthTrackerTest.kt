package rw.itunda.core.health

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize

class ProviderHealthTrackerTest : BehaviorSpec({

    Given("a rail with no attempts yet") {
        val tracker = ProviderHealthTracker()

        When("snapshot is taken") {
            Then("it doesn't appear at all -- no fabricated zero-row") {
                tracker.snapshot().shouldBeEmpty()
            }
        }
    }

    Given("a rail with a mix of real successes and failures") {
        val tracker = ProviderHealthTracker()

        When("3 successes and 1 failure are recorded with real latencies") {
            tracker.recordAttempt("wasac", "WASAC - Water", success = true, latencyMs = 700)
            tracker.recordAttempt("wasac", "WASAC - Water", success = true, latencyMs = 720)
            tracker.recordAttempt("wasac", "WASAC - Water", success = true, latencyMs = 680)
            tracker.recordAttempt("wasac", "WASAC - Water", success = false, latencyMs = 700)

            Then("the snapshot reflects real measured counts, rate, and average latency") {
                val snapshot = tracker.snapshot()
                snapshot shouldHaveSize 1
                val health = snapshot.first()
                health.railId shouldBe "wasac"
                health.totalAttempts shouldBe 4L
                health.successCount shouldBe 3L
                health.failureCount shouldBe 1L
                health.successRate shouldBe 0.75
                health.avgLatencyMs shouldBe 700L
            }
        }
    }

    Given("attempts across two different rails") {
        val tracker = ProviderHealthTracker()

        When("recording one attempt for each") {
            tracker.recordAttempt("wasac", "WASAC - Water", success = false, latencyMs = 700)
            tracker.recordAttempt("mtn_momo", "MTN Mobile Money", success = true, latencyMs = 400)

            Then("each rail is tracked independently") {
                tracker.snapshot() shouldHaveSize 2
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
