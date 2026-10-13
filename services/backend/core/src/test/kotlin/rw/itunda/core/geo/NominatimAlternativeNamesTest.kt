package rw.itunda.core.geo

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class NominatimAlternativeNamesTest : FunSpec({
    test("keeps localized and alternate place names but excludes metadata and duplicate display labels") {
        val names = linkedMapOf(
            "name" to "Kigali Convention Centre",
            "name:rw" to "Kigali Convention Centre",
            "name:fr" to "Centre de conventions de Kigali",
            "alt_name" to "Kigali Convention Center",
            "alt_name:en" to "Kigali Convention Center",
            "official_name" to "Centre de conventions de Kigali",
            "brand" to "Example Brand",
            "operator" to "Example Operator",
            "ref" to "A-123",
            "wikidata" to "Q123",
            "short_name" to " ",
        )

        extractAlternativeNames(
            names,
            "Kigali Convention Centre, KG 2 Roundabout, Kigali, Rwanda",
        ) shouldBe listOf(
            "Centre de conventions de Kigali",
            "Kigali Convention Center",
        )
    }

    test("returns an empty list when namedetails are missing") {
        extractAlternativeNames(null, "Huye, Rwanda") shouldBe emptyList()
    }

    test("preserves first-seen spelling while deduplicating case-insensitively") {
        extractAlternativeNames(
            linkedMapOf("alt_name" to "Gisenyi", "old_name" to "GISENYI", "name:fr" to "Rubavu"),
            "Rubavu, Rwanda",
        ) shouldBe listOf("Gisenyi")
    }
})
