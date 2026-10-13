package rw.itunda.app.network

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Test
import rw.itunda.core.network.PlaceSearchResultDto

/**
 * Maps search contract regression tests.
 *
 * Gson allocates DTOs reflectively, so Kotlin constructor defaults alone do not
 * guarantee that an absent non-null collection field is safe for old API payloads.
 */
class MapsSearchDtoCompatibilityTest {
    private val gson = Gson()

    @Test
    fun legacyPayloadWithoutAlternateNamesExposesEmptyList() {
        val json = """{"displayName":"Kigali, Rwanda","latitude":-1.9441,"longitude":30.0619}"""

        val result = gson.fromJson(json, PlaceSearchResultDto::class.java)

        assertEquals("Kigali, Rwanda", result.displayName)
        assertEquals(emptyList<String>(), result.alternativeNames)
    }

    @Test
    fun payloadWithAlternateNamesPreservesThem() {
        val json = """{"displayName":"Kigali Convention Centre, Kigali, Rwanda","latitude":-1.95,"longitude":30.09,"alternativeNames":["Kigali Convention Center","KCC"]}"""

        val result = gson.fromJson(json, PlaceSearchResultDto::class.java)

        assertEquals(listOf("Kigali Convention Center", "KCC"), result.alternativeNames)
    }
}
