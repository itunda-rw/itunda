package rw.itunda.core.provider

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.core.health.ProviderHealthTracker
import rw.itunda.core.incident.IncidentDetector
import rw.itunda.core.reconciliation.ReconciliationService

/**
 * First test coverage for SimulatedProviderConnector -- the single choke point every
 * rail-calling flow passes through for IncidentDetector/ProviderHealthTracker/
 * ReconciliationService reporting (see this class's own doc comment). The simulated
 * outcome itself has no real financial stakes, but a bug in WHICH of the 3 reporting
 * calls fires, how many times, and with what latency/success values would silently
 * break real incident auto-detection and reconciliation reporting for every rail this
 * backend "calls." Every scenario uses a `successRate` of exactly 0.0 or 1.0 (rather
 * than a real profile's 0.92-0.99) to make the outcome deterministic -- `Random` isn't
 * injected into this class, so a real fractional rate can't be tested without either
 * flaky statistical assertions or a new static-mocking pattern this codebase doesn't
 * otherwise use. This intentionally leaves one real branch -- "a degraded rail fails
 * once then recovers on retry" -- unverified; every other real branch is covered.
 */
class SimulatedProviderConnectorTest : BehaviorSpec({

    fun railProfile(successRate: Double, degraded: Boolean = false, offline: Boolean = false) = RailProfile(
        id = "test_rail", displayName = "Test Rail", avgLatencyMs = 0, successRate = successRate, degraded = degraded, offline = offline,
    )

    Given("an offline rail") {
        val incidentDetector = mockk<IncidentDetector>(relaxed = true)
        val providerHealthTracker = mockk<ProviderHealthTracker>(relaxed = true)
        val reconciliationService = mockk<ReconciliationService>(relaxed = true)
        val connector = SimulatedProviderConnector(incidentDetector, providerHealthTracker, reconciliationService, simulationEnabled = true)
        val rail = railProfile(successRate = 1.0, offline = true)

        When("an attempt is made") {
            Then("it fails immediately without ever rolling for success, and every reporting sink is told about it with 0 latency") {
                shouldThrow<ProviderDeclinedException> { connector.attempt(rail, "test payment") }
                verify(exactly = 1) { incidentDetector.recordFailure(rail) }
                verify(exactly = 1) { providerHealthTracker.recordAttempt("test_rail", "Test Rail", success = false, latencyMs = 0) }
                verify(exactly = 1) { reconciliationService.logAttempt(rail, success = false, latencyMs = 0) }
            }
        }
    }

    Given("a rail guaranteed to succeed on the first real attempt") {
        val incidentDetector = mockk<IncidentDetector>(relaxed = true)
        val providerHealthTracker = mockk<ProviderHealthTracker>(relaxed = true)
        val reconciliationService = mockk<ReconciliationService>(relaxed = true)
        val connector = SimulatedProviderConnector(incidentDetector, providerHealthTracker, reconciliationService, simulationEnabled = true)
        val rail = railProfile(successRate = 1.0)

        When("an attempt is made") {
            Then("it succeeds, records exactly one successful attempt, and never reports an incident") {
                connector.attempt(rail, "test payment")
                verify(exactly = 1) { providerHealthTracker.recordAttempt("test_rail", "Test Rail", success = true, latencyMs = any()) }
                verify(exactly = 1) { reconciliationService.logAttempt(rail, success = true, latencyMs = any()) }
                verify(exactly = 0) { incidentDetector.recordFailure(any()) }
            }
        }
    }

    Given("a NON-degraded rail guaranteed to fail") {
        val incidentDetector = mockk<IncidentDetector>(relaxed = true)
        val providerHealthTracker = mockk<ProviderHealthTracker>(relaxed = true)
        val reconciliationService = mockk<ReconciliationService>(relaxed = true)
        val connector = SimulatedProviderConnector(incidentDetector, providerHealthTracker, reconciliationService, simulationEnabled = true)
        val rail = railProfile(successRate = 0.0, degraded = false)

        When("an attempt is made") {
            Then("it declines after exactly ONE attempt -- non-degraded rails never get a retry") {
                shouldThrow<ProviderDeclinedException> { connector.attempt(rail, "test payment") }
                verify(exactly = 1) { providerHealthTracker.recordAttempt("test_rail", "Test Rail", success = false, latencyMs = any()) }
                verify(exactly = 1) { incidentDetector.recordFailure(rail) }
            }
        }
    }

    Given("a DEGRADED rail guaranteed to fail on every attempt") {
        val incidentDetector = mockk<IncidentDetector>(relaxed = true)
        val providerHealthTracker = mockk<ProviderHealthTracker>(relaxed = true)
        val reconciliationService = mockk<ReconciliationService>(relaxed = true)
        val connector = SimulatedProviderConnector(incidentDetector, providerHealthTracker, reconciliationService, simulationEnabled = true)
        val rail = railProfile(successRate = 0.0, degraded = true)

        When("an attempt is made") {
            Then("it retries exactly once before declining, reporting BOTH real attempts but only ONE incident (after the retry also fails, not per-attempt)") {
                shouldThrow<ProviderDeclinedException> { connector.attempt(rail, "test payment") }
                verify(exactly = 2) { providerHealthTracker.recordAttempt("test_rail", "Test Rail", success = false, latencyMs = any()) }
                verify(exactly = 2) { reconciliationService.logAttempt(rail, success = false, latencyMs = any()) }
                verify(exactly = 1) { incidentDetector.recordFailure(rail) }
            }
        }
    }
})
