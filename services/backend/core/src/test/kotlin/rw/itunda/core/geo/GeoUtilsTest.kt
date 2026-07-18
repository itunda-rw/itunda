package rw.itunda.core.geo

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe

class GeoUtilsTest : BehaviorSpec({

    Given("two real points on the same meridian (only latitude differs)") {
        When("computing the real Haversine distance") {
            // Exact by construction: along a meridian, great-circle distance is simply
            // Earth's radius times the latitude difference in radians -- 1 degree of
            // latitude is always ~111.19km regardless of where on the globe it is.
            val distanceKm = GeoUtils.haversineKm(-1.9441, 30.0619, -2.9441, 30.0619)

            Then("it matches the real known-exact value") {
                distanceKm shouldBe (111.19 plusOrMinus 0.05)
            }
        }
    }

    Given("the same real point twice") {
        When("computing the real Haversine distance") {
            val distanceKm = GeoUtils.haversineKm(-1.9441, 30.0619, -1.9441, 30.0619)

            Then("it's zero") {
                distanceKm shouldBe (0.0 plusOrMinus 0.0001)
            }
        }
    }

    Given("real coordinate validation") {
        When("a valid Rwanda coordinate is checked") {
            Then("it's valid") {
                GeoUtils.isValidCoordinate(-1.9441, 30.0619) shouldBe true
            }
        }

        When("an out-of-range latitude is checked") {
            Then("it's invalid") {
                GeoUtils.isValidCoordinate(999.0, 30.0) shouldBe false
            }
        }

        When("an out-of-range longitude is checked") {
            Then("it's invalid") {
                GeoUtils.isValidCoordinate(-1.9441, 999.0) shouldBe false
            }
        }
    }
})
