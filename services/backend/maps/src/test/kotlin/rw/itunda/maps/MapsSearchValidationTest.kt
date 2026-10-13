package rw.itunda.maps

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import rw.itunda.auth.RateLimiter
import rw.itunda.core.geo.NominatimGeocodingClient
import rw.itunda.core.geo.GeocodingUnavailableException
import rw.itunda.core.geo.OsrmRoutingClient
import rw.itunda.core.repository.MapBookmarkRepository
import rw.itunda.core.weather.KigaliWeatherClient

class MapsSearchValidationTest : FunSpec({
    fun service() = MapsService(
        nominatimGeocodingClient = mockk<NominatimGeocodingClient>(relaxed = true),
        osrmRoutingClient = mockk<OsrmRoutingClient>(relaxed = true),
        rateLimiter = mockk<RateLimiter>(relaxed = true),
        mapBookmarkRepository = mockk<MapBookmarkRepository>(relaxed = true),
        kigaliWeatherClient = mockk<KigaliWeatherClient>(relaxed = true),
        transitRoutingService = mockk<TransitRoutingService>(relaxed = true),
    )

    test("rejects a blank search query before calling geocoding") {
        val exception = shouldThrow<InvalidMapsSearchQueryException> {
            service().searchPlaces("user_1", "   ")
        }
        exception.message shouldBe "Search query must not be empty"
    }

    test("rejects a query longer than the documented limit before calling geocoding") {
        val exception = shouldThrow<InvalidMapsSearchQueryException> {
            service().searchPlaces("user_1", "a".repeat(MapsService.MAX_SEARCH_QUERY_LENGTH + 1))
        }
        exception.message shouldBe "Search query must be ${MapsService.MAX_SEARCH_QUERY_LENGTH} characters or fewer"
    }

    test("reports unavailable when geocoding is not configured") {
        val geocoder = mockk<NominatimGeocodingClient>()
        io.mockk.every { geocoder.isConfigured } returns false
        val mapsService = MapsService(
            nominatimGeocodingClient = geocoder,
            osrmRoutingClient = mockk(relaxed = true),
            rateLimiter = mockk(relaxed = true),
            mapBookmarkRepository = mockk(relaxed = true),
            kigaliWeatherClient = mockk<KigaliWeatherClient>(relaxed = true),
            transitRoutingService = mockk<TransitRoutingService>(relaxed = true),
        )

        val exception = shouldThrow<MapsGeocodingUnavailableException> {
            mapsService.searchPlaces("user_1", "Kigali")
        }
        exception.message shouldBe "Map search is temporarily unavailable"
        io.mockk.verify(exactly = 0) { geocoder.searchForMaps(any(), any()) }
    }

    test("maps an upstream geocoding failure to a typed unavailable error") {
        val geocoder = mockk<NominatimGeocodingClient>()
        io.mockk.every { geocoder.isConfigured } returns true
        io.mockk.every { geocoder.searchForMaps("Kigali", limit = 8) } throws GeocodingUnavailableException("Nominatim search is temporarily unavailable")
        val mapsService = MapsService(
            nominatimGeocodingClient = geocoder,
            osrmRoutingClient = mockk(relaxed = true),
            rateLimiter = mockk(relaxed = true),
            mapBookmarkRepository = mockk(relaxed = true),
            kigaliWeatherClient = mockk<KigaliWeatherClient>(relaxed = true),
            transitRoutingService = mockk<TransitRoutingService>(relaxed = true),
        )

        val exception = shouldThrow<MapsGeocodingUnavailableException> {
            mapsService.searchPlaces("user_1", "Kigali")
        }
        exception.message shouldBe "Map search is temporarily unavailable"
    }

    test("preserves geocoder alternative names in maps search results") {
        val geocoder = mockk<NominatimGeocodingClient>()
        val expected = rw.itunda.core.geo.GeocodeSuggestion(
            displayName = "Kigali Convention Centre, Kigali, Rwanda",
            latitude = -1.95,
            longitude = 30.09,
            alternativeNames = listOf("Kigali Convention Center", "KCC"),
        )
        val mapsService = MapsService(
            nominatimGeocodingClient = geocoder,
            osrmRoutingClient = mockk(relaxed = true),
            rateLimiter = mockk(relaxed = true),
            mapBookmarkRepository = mockk(relaxed = true),
            kigaliWeatherClient = mockk<KigaliWeatherClient>(relaxed = true),
            transitRoutingService = mockk<TransitRoutingService>(relaxed = true),
        )
        io.mockk.every { geocoder.isConfigured } returns true
        io.mockk.every { geocoder.searchForMaps("Kigali Convention Centre", limit = 8) } returns listOf(expected)

        mapsService.searchPlaces("user_1", "Kigali Convention Centre") shouldBe listOf(expected)
        io.mockk.verify(exactly = 1) { geocoder.searchForMaps("Kigali Convention Centre", limit = 8) }
    }

    test("trims a valid query before sending it to geocoding") {
        val geocoder = mockk<NominatimGeocodingClient>()
        val mapsService = MapsService(
            nominatimGeocodingClient = geocoder,
            osrmRoutingClient = mockk(relaxed = true),
            rateLimiter = mockk(relaxed = true),
            mapBookmarkRepository = mockk(relaxed = true),
            kigaliWeatherClient = mockk<KigaliWeatherClient>(relaxed = true),
            transitRoutingService = mockk<TransitRoutingService>(relaxed = true),
        )
        io.mockk.every { geocoder.isConfigured } returns true
        io.mockk.every { geocoder.searchForMaps("Kigali", limit = 8) } returns emptyList()

        mapsService.searchPlaces("user_1", "  Kigali  ") shouldBe emptyList()
        io.mockk.verify(exactly = 1) { geocoder.searchForMaps("Kigali", limit = 8) }
    }
})
