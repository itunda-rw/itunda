package rw.itunda.core.transit

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class TransitGtfsImportServiceTest : BehaviorSpec({

    Given("real GTFS time strings") {
        When("parsing an ordinary time") {
            Then("it converts to real seconds-after-midnight") {
                TransitGtfsImportService.parseGtfsTime("08:15:00") shouldBe (8 * 3600 + 15 * 60)
            }
        }

        When("parsing a real GTFS overnight time past 24:00:00") {
            Then("it correctly handles the real GTFS convention rather than throwing") {
                TransitGtfsImportService.parseGtfsTime("25:30:00") shouldBe (25 * 3600 + 30 * 60)
            }
        }

        When("parsing a malformed time") {
            Then("it returns null rather than crashing the whole import over one bad row") {
                TransitGtfsImportService.parseGtfsTime("not-a-time") shouldBe null
                TransitGtfsImportService.parseGtfsTime(null) shouldBe null
            }
        }
    }

    Given("a real GTFS-shaped CSV with a quoted field containing an embedded comma") {
        val csv = "stop_id,stop_name,stop_lat,stop_lon\n" +
            "1001,\"Kigali, Nyarugenge\",-1.9500,30.0600\n" +
            "1002,Kimironko,-1.9350,30.1050\n"

        When("parsing it") {
            val rows = TransitGtfsImportService.parseCsv(csv)

            Then("it correctly keeps the quoted comma inside one real field, not split across two") {
                rows.size shouldBe 2
                rows[0]["stop_name"] shouldBe "Kigali, Nyarugenge"
                rows[0]["stop_lat"] shouldBe "-1.9500"
                rows[1]["stop_name"] shouldBe "Kimironko"
            }
        }
    }

    Given("a real GTFS CSV with an escaped double-quote inside a quoted field") {
        val csv = "route_id,route_long_name\n" +
            "r1,\"The \"\"Downtown\"\" Express\"\n"

        When("parsing it") {
            val rows = TransitGtfsImportService.parseCsv(csv)

            Then("it un-escapes the doubled quote back to a single real quote character") {
                rows[0]["route_long_name"] shouldBe "The \"Downtown\" Express"
            }
        }
    }

    Given("an empty real CSV") {
        When("parsing it") {
            Then("it returns an empty list, not an error") {
                TransitGtfsImportService.parseCsv("") shouldBe emptyList()
            }
        }
    }
})
