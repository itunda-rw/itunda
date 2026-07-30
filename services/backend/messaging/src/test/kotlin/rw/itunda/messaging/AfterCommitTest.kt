package rw.itunda.messaging

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import org.springframework.transaction.support.TransactionSynchronizationManager

class AfterCommitTest : BehaviorSpec({
    Given("a caller outside a Spring transaction") {
        Then("it delivers immediately for command-line and focused unit-test callers") {
            var deliveries = 0

            runAfterCommit { deliveries++ }

            deliveries shouldBe 1
        }
    }

    Given("an active transaction synchronization") {
        Then("it waits until the transaction commits") {
            var deliveries = 0
            TransactionSynchronizationManager.initSynchronization()
            try {
                runAfterCommit { deliveries++ }

                deliveries shouldBe 0
                TransactionSynchronizationManager.getSynchronizations().single().afterCommit()
                deliveries shouldBe 1
            } finally {
                TransactionSynchronizationManager.clearSynchronization()
            }
        }
    }
})
