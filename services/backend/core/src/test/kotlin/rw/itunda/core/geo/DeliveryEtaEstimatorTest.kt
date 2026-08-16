package rw.itunda.core.geo

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class DeliveryEtaEstimatorTest : BehaviorSpec({

    Given("a real 2km delivery with no merchant-set prep time and a non-busy kitchen") {
        When("estimating the delivery time") {
            val minutes = DeliveryEtaEstimator.estimateDeliveryMinutes(2.0, prepTimeMinutes = null, isBusy = false)

            Then("it uses the flat base prep time, unaffected by the busy bump") {
                // 15 min base prep + (2/20)*60 = 6 min travel = 21, rounded to nearest 5 -> 20
                minutes shouldBe 20
            }
        }
    }

    Given("the identical real 2km delivery, but the restaurant's kitchen is real-busy") {
        When("estimating the delivery time") {
            val minutes = DeliveryEtaEstimator.estimateDeliveryMinutes(2.0, prepTimeMinutes = null, isBusy = true)

            Then("it's real and strictly longer than the non-busy estimate, never fabricated to be shorter") {
                val nonBusy = DeliveryEtaEstimator.estimateDeliveryMinutes(2.0, prepTimeMinutes = null, isBusy = false)
                (minutes > nonBusy) shouldBe true
            }
        }
    }

    Given("a real busy delivery that would already exceed the max bound before the bump") {
        When("estimating the delivery time") {
            val minutes = DeliveryEtaEstimator.estimateDeliveryMinutes(30.0, prepTimeMinutes = 60, isBusy = true)

            Then("the busy bump never pushes the estimate past the real honest max bound") {
                minutes shouldBe 90
            }
        }
    }

    Given("isBusy defaulted (every existing call site before this feature)") {
        When("estimating the delivery time") {
            val withDefault = DeliveryEtaEstimator.estimateDeliveryMinutes(2.0, prepTimeMinutes = null)
            val explicitFalse = DeliveryEtaEstimator.estimateDeliveryMinutes(2.0, prepTimeMinutes = null, isBusy = false)

            Then("the default behaves identically to explicit isBusy=false, fully backward-compatible") {
                withDefault shouldBe explicitFalse
            }
        }
    }
})
