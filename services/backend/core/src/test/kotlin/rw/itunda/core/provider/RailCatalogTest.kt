package rw.itunda.core.provider

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

/**
 * Real, sourced routing (RURA's national numbering plan, see resolveByPhoneNumber's own
 * doc comment): 078 -> MTN Rwanda, 072/073 -> Airtel Rwanda.
 */
class RailCatalogTest : BehaviorSpec({

    Given("resolveByPhoneNumber") {
        When("given a real MTN-prefixed number in international format") {
            Then("it routes to mtnMomo") {
                RailCatalog.resolveByPhoneNumber("+250788111222") shouldBe RailCatalog.mtnMomo
            }
        }
        When("given a real MTN-prefixed number in local format") {
            Then("it routes to mtnMomo") {
                RailCatalog.resolveByPhoneNumber("0788111222") shouldBe RailCatalog.mtnMomo
            }
        }
        When("given a real Airtel-prefixed number (072)") {
            Then("it routes to airtelMoney") {
                RailCatalog.resolveByPhoneNumber("+250722333444") shouldBe RailCatalog.airtelMoney
            }
        }
        When("given a real Airtel-prefixed number (073)") {
            Then("it routes to airtelMoney") {
                RailCatalog.resolveByPhoneNumber("+250733444555") shouldBe RailCatalog.airtelMoney
            }
        }
        When("given an unrecognized prefix") {
            Then("it falls back to generic rather than guessing") {
                RailCatalog.resolveByPhoneNumber("+250799999999") shouldBe RailCatalog.generic
            }
        }
        When("given a malformed or empty string") {
            Then("it falls back to generic") {
                RailCatalog.resolveByPhoneNumber("not-a-number") shouldBe RailCatalog.generic
            }
        }
    }
})
