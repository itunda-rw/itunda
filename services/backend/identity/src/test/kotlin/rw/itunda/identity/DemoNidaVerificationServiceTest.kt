package rw.itunda.identity

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class DemoNidaVerificationServiceTest : BehaviorSpec({

    val service = DemoNidaVerificationService()

    Given("a document type this pre-check doesn't cover at all") {
        When("verifying a BUSINESS_TIN") {
            val result = service.verify("BUSINESS_TIN", "123456789")

            Then("it honestly reports it doesn't cover this document type -- KYB routes through DemoKybVerificationService instead") {
                result.status shouldBe NidaVerificationStatus.UNSUPPORTED_DOCUMENT_TYPE
            }
        }
    }

    Given("a passport number that's the wrong shape") {
        When("verifying a 5-character number, shorter than ICAO's shared shell") {
            val result = service.verify("PASSPORT", "P1234")

            Then("it real-fails format validation") {
                result.status shouldBe NidaVerificationStatus.INVALID_FORMAT
            }
        }
        When("verifying a number with a symbol") {
            val result = service.verify("PASSPORT", "P123-456")

            Then("it real-fails format validation") {
                result.status shouldBe NidaVerificationStatus.INVALID_FORMAT
            }
        }
    }

    Given("a structurally valid passport number") {
        When("verifying it twice") {
            val first = service.verify("PASSPORT", "P1234567")
            val second = service.verify("PASSPORT", "P1234567")

            Then("it deterministically returns the same simulated outcome both times -- not randomly flaky") {
                first.status shouldBe second.status
            }
            Then("it never fabricates citizenship/birth-year/gender fields a generic passport number can't actually convey") {
                first.citizenshipStatus shouldBe null
                first.birthYear shouldBe null
                first.gender shouldBe null
            }
        }

        When("verifying a lowercase number") {
            val result = service.verify("PASSPORT", "p1234567")

            Then("it real-normalizes case before validating -- the shape is what matters, not letter case") {
                (result.status == NidaVerificationStatus.MATCHED || result.status == NidaVerificationStatus.NOT_FOUND) shouldBe true
            }
        }
    }

    Given("a National ID that isn't 16 digits") {
        When("verifying a 10-digit number") {
            val result = service.verify("NATIONAL_ID", "1234567890")

            Then("it real-fails format validation") {
                result.status shouldBe NidaVerificationStatus.INVALID_FORMAT
            }
        }
        When("verifying a 16-character string with letters") {
            val result = service.verify("NATIONAL_ID", "119808001234567A")

            Then("it real-fails format validation") {
                result.status shouldBe NidaVerificationStatus.INVALID_FORMAT
            }
        }
    }

    Given("a National ID with an invalid citizenship digit") {
        When("digit 1 is not 1, 2, or 3") {
            // Real, sourced format: digit 1 must be 1 (citizen), 2 (refugee), or 3 (foreigner).
            val result = service.verify("NATIONAL_ID", "9198080012345678")

            Then("it real-fails format validation") {
                result.status shouldBe NidaVerificationStatus.INVALID_FORMAT
            }
        }
    }

    Given("a National ID with an implausible birth year") {
        When("the birth year is in the future") {
            val result = service.verify("NATIONAL_ID", "1299080012345678")

            Then("it real-fails format validation") {
                result.status shouldBe NidaVerificationStatus.INVALID_FORMAT
            }
        }
    }

    Given("a National ID with an invalid gender digit") {
        When("digit 6 is not 7 or 8") {
            // Real, sourced format: digit 6 must be 8 (male) or 7 (female).
            val result = service.verify("NATIONAL_ID", "1198050012345678")

            Then("it real-fails format validation") {
                result.status shouldBe NidaVerificationStatus.INVALID_FORMAT
            }
        }
    }

    Given("a structurally valid National ID") {
        When("verifying it twice") {
            val first = service.verify("NATIONAL_ID", "1198080012345678")
            val second = service.verify("NATIONAL_ID", "1198080012345678")

            Then("it deterministically returns the same simulated outcome both times -- not randomly flaky") {
                first.status shouldBe second.status
            }
            Then("it real-parses the structural fields correctly") {
                first.citizenshipStatus shouldBe "CITIZEN"
                first.birthYear shouldBe 1980
                first.gender shouldBe "MALE"
            }
        }

        When("verifying a different structurally valid ID") {
            val result = service.verify("NATIONAL_ID", "2200077654321098")

            Then("it real-parses a refugee/female ID correctly") {
                result.citizenshipStatus shouldBe "REFUGEE"
                result.birthYear shouldBe 2000
                result.gender shouldBe "FEMALE"
            }
        }
    }
})
