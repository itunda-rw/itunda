package rw.itunda.maps

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.MapBookmark
import rw.itunda.core.geo.GeocodeSuggestion
import rw.itunda.core.geo.NearbyPlace
import rw.itunda.core.geo.NominatimGeocodingClient
import rw.itunda.core.geo.OsrmRoutingClient
import rw.itunda.core.geo.RouteResult
import rw.itunda.core.geo.TravelMode
import rw.itunda.core.repository.MapBookmarkRepository
import java.time.Duration

class MapsServiceTest : BehaviorSpec({

    Given("a real user searching for a real place") {
        val nominatimGeocodingClient = mockk<NominatimGeocodingClient>()
        val osrmRoutingClient = mockk<OsrmRoutingClient>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val mapBookmarkRepository = mockk<MapBookmarkRepository>(relaxed = true)
        val service = MapsService(nominatimGeocodingClient, osrmRoutingClient, rateLimiter, mapBookmarkRepository)

        When("a real query matches real places") {
            val suggestions = listOf(GeocodeSuggestion("Kigali International Airport, Rwanda", -1.9686, 30.1395))
            every { nominatimGeocodingClient.search("Kigali International", limit = 8) } returns suggestions

            val results = service.searchPlaces("user_1", "Kigali International")

            Then("it returns the real Nominatim suggestions") {
                results shouldBe suggestions
            }
        }

        When("the real per-user search rate limit is exceeded") {
            every { rateLimiter.checkLimit("maps:search:user_1", limit = 60, window = Duration.ofMinutes(1)) } throws
                RateLimitExceededException("Too many requests")

            Then("it real-propagates RateLimitExceededException") {
                try {
                    service.searchPlaces("user_1", "Kigali")
                    error("expected RateLimitExceededException")
                } catch (e: RateLimitExceededException) {
                    // expected
                }
            }
        }
    }

    Given("a real user requesting directions") {
        val nominatimGeocodingClient = mockk<NominatimGeocodingClient>()
        val osrmRoutingClient = mockk<OsrmRoutingClient>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val mapBookmarkRepository = mockk<MapBookmarkRepository>(relaxed = true)
        val service = MapsService(nominatimGeocodingClient, osrmRoutingClient, rateLimiter, mapBookmarkRepository)

        // Kigali city center -> near the airport, the same real coordinate pair this
        // project's own Maps live-verification passes have used before.
        val fromLat = -1.9441; val fromLng = 30.0619
        val toLat = -1.9686; val toLng = 30.1395

        When("a real route exists between two real in-Rwanda points") {
            val route = RouteResult(distanceKm = 11.375, durationMinutes = 18.2, geometry = listOf(listOf(fromLat, fromLng), listOf(toLat, toLng)))
            every { osrmRoutingClient.route(fromLat, fromLng, toLat, toLng) } returns route

            val result = service.getDirections("user_1", fromLat, fromLng, toLat, toLng)

            Then("it returns the real OSRM route with real geometry") {
                result shouldBe route
            }
        }

        // Real walking directions (2026-07-22) -- see OsrmRoutingClient.route's own doc
        // comment for the full account of the real, separately-deployed foot-profile
        // OSRM instance this now reaches.
        When("a real WALKING route is requested between the same two points") {
            val walkingRoute = RouteResult(distanceKm = 9.4817, durationMinutes = 113.8, geometry = listOf(listOf(fromLat, fromLng), listOf(toLat, toLng)))
            every { osrmRoutingClient.route(fromLat, fromLng, toLat, toLng, TravelMode.WALKING) } returns walkingRoute

            val result = service.getDirections("user_1", fromLat, fromLng, toLat, toLng, TravelMode.WALKING)

            Then("it passes WALKING through to OsrmRoutingClient rather than silently always routing by car") {
                result shouldBe walkingRoute
                verify(exactly = 1) { osrmRoutingClient.route(fromLat, fromLng, toLat, toLng, TravelMode.WALKING) }
            }
        }

        // Real alternative routes (2026-07-22) -- see OsrmRoutingClient.routeAlternatives'
        // own doc comment for the full account of the real, live-verified case (against
        // itunda's own MLD-algorithm OSRM instance) where OSRM genuinely returns more
        // than one distinct route for the same coordinate pair.
        When("real alternative routes exist between two real in-Rwanda points") {
            val primary = RouteResult(distanceKm = 4.7759, durationMinutes = 7.838, geometry = listOf(listOf(fromLat, fromLng), listOf(toLat, toLng)))
            val alternative = RouteResult(distanceKm = 5.8763, durationMinutes = 8.06, geometry = listOf(listOf(fromLat, fromLng), listOf(toLat, toLng)))
            every { osrmRoutingClient.routeAlternatives(fromLat, fromLng, toLat, toLng, TravelMode.DRIVING) } returns listOf(primary, alternative)

            val results = service.getDirectionsAlternatives("user_1", fromLat, fromLng, toLat, toLng)

            Then("it returns every real route OSRM offered rather than only the fastest") {
                results shouldBe listOf(primary, alternative)
            }
        }

        When("OSRM finds no real alternative routes at all") {
            every { osrmRoutingClient.routeAlternatives(fromLat, fromLng, toLat, toLng, TravelMode.DRIVING) } returns emptyList()

            Then("it throws RouteNotFoundException rather than returning a fabricated route") {
                try {
                    service.getDirectionsAlternatives("user_1", fromLat, fromLng, toLat, toLng)
                    error("expected RouteNotFoundException")
                } catch (e: RouteNotFoundException) {
                    // expected
                }
            }
        }

        When("OSRM finds no real route") {
            every { osrmRoutingClient.route(fromLat, fromLng, toLat, toLng) } returns null

            Then("it throws RouteNotFoundException rather than a fabricated route") {
                try {
                    service.getDirections("user_1", fromLat, fromLng, toLat, toLng)
                    error("expected RouteNotFoundException")
                } catch (e: RouteNotFoundException) {
                    // expected
                }
            }
        }

        When("an out-of-range coordinate is given") {
            Then("it throws InvalidMapsCoordinateException before ever calling OSRM") {
                try {
                    service.getDirections("user_1", 999.0, 30.0, toLat, toLng)
                    error("expected InvalidMapsCoordinateException")
                } catch (e: InvalidMapsCoordinateException) {
                    // expected
                }
            }
        }

        When("the destination is real but outside Rwanda's bounding envelope") {
            Then("it throws RouteNotFoundException without ever calling OSRM, avoiding its real out-of-region snapping bug") {
                try {
                    // (0,0) null-island -- the exact real coordinate that previously
                    // exposed OSRM's out-of-Rwanda snapping bug during Eats live-verification.
                    service.getDirections("user_1", fromLat, fromLng, 0.0, 0.0)
                    error("expected RouteNotFoundException")
                } catch (e: RouteNotFoundException) {
                    // expected
                }
            }
        }

        When("an ordered itinerary has real in-Rwanda stops") {
            val stop = MapsService.ItineraryWaypoint(-1.9536, 30.0606)
            val waypoints = listOf(
                MapsService.ItineraryWaypoint(fromLat, fromLng),
                stop,
                MapsService.ItineraryWaypoint(toLat, toLng),
            )
            val route = RouteResult(
                distanceKm = 13.2,
                durationMinutes = 23.0,
                geometry = listOf(listOf(fromLat, fromLng), listOf(stop.latitude, stop.longitude), listOf(toLat, toLng)),
            )
            every { osrmRoutingClient.routeThrough(listOf(fromLat to fromLng, stop.latitude to stop.longitude, toLat to toLng), TravelMode.DRIVING) } returns route

            val result = service.getItineraryDirections("user_1", waypoints)

            Then("it sends every ordered stop through one real OSRM itinerary route") {
                result shouldBe route
                verify(exactly = 1) {
                    osrmRoutingClient.routeThrough(
                        listOf(fromLat to fromLng, stop.latitude to stop.longitude, toLat to toLng),
                        TravelMode.DRIVING,
                    )
                }
                verify(exactly = 1) { rateLimiter.checkLimit("maps:directions:user_1", limit = 60, window = Duration.ofMinutes(1)) }
            }
        }

        When("an itinerary has more than the safe maximum of seven stops") {
            val waypoints = (0..7).map { MapsService.ItineraryWaypoint(-1.9441 - it * 0.001, 30.0619) }

            Then("it rejects it before consuming routing capacity or contacting OSRM") {
                try {
                    service.getItineraryDirections("user_1", waypoints)
                    error("expected InvalidMapsItineraryException")
                } catch (e: InvalidMapsItineraryException) {
                    // expected
                }
                verify(exactly = 0) { osrmRoutingClient.routeThrough(any(), any()) }
                verify(exactly = 0) { rateLimiter.checkLimit(any(), any(), any()) }
            }
        }

        When("one itinerary stop is outside Rwanda") {
            val waypoints = listOf(
                MapsService.ItineraryWaypoint(fromLat, fromLng),
                MapsService.ItineraryWaypoint(0.0, 0.0),
                MapsService.ItineraryWaypoint(toLat, toLng),
            )

            Then("it rejects it rather than allowing OSRM to snap it into Rwanda") {
                try {
                    service.getItineraryDirections("user_1", waypoints)
                    error("expected RouteNotFoundException")
                } catch (e: RouteNotFoundException) {
                    // expected
                }
                verify(exactly = 0) { osrmRoutingClient.routeThrough(any(), any()) }
            }
        }
    }

    Given("a real user browsing nearby places by category") {
        val nominatimGeocodingClient = mockk<NominatimGeocodingClient>()
        val osrmRoutingClient = mockk<OsrmRoutingClient>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val mapBookmarkRepository = mockk<MapBookmarkRepository>(relaxed = true)
        val service = MapsService(nominatimGeocodingClient, osrmRoutingClient, rateLimiter, mapBookmarkRepository)

        val lat = -1.9441
        val lng = 30.0619

        When("a real category matches real nearby places") {
            val places = listOf(NearbyPlace("Real Restaurant, Kigali", -1.9445, 30.0622, 0.05))
            every { nominatimGeocodingClient.searchNearby("restaurant", lat, lng, 2.0, limit = 20) } returns places

            val results = service.getNearbyPlaces("user_1", "RESTAURANT", lat, lng, 2.0)

            Then("it returns the real Nominatim category results") {
                results shouldBe places
            }
        }

        When("an unknown category is given") {
            Then("it throws InvalidMapsCategoryException before ever calling Nominatim") {
                try {
                    service.getNearbyPlaces("user_1", "not-a-real-category", lat, lng, 2.0)
                    error("expected InvalidMapsCategoryException")
                } catch (e: InvalidMapsCategoryException) {
                    // expected
                }
            }
        }

        When("the search center is outside Rwanda's bounding envelope") {
            Then("it returns an empty list without ever calling Nominatim") {
                val results = service.getNearbyPlaces("user_1", "RESTAURANT", 0.0, 0.0, 2.0)
                results shouldBe emptyList()
            }
        }
    }

    Given("a real user bookmarking a real place") {
        val nominatimGeocodingClient = mockk<NominatimGeocodingClient>()
        val osrmRoutingClient = mockk<OsrmRoutingClient>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val mapBookmarkRepository = mockk<MapBookmarkRepository>()
        val service = MapsService(nominatimGeocodingClient, osrmRoutingClient, rateLimiter, mapBookmarkRepository)

        val lat = -1.9686
        val lng = 30.1395

        When("a real new place is bookmarked") {
            every { mapBookmarkRepository.findByUserIdAndLatitudeAndLongitude("user_1", lat, lng) } returns null
            every { mapBookmarkRepository.save(any()) } answers { firstArg() }

            val bookmark = service.addBookmark("user_1", "Kigali International Airport", lat, lng)

            Then("it saves a real new bookmark") {
                bookmark.displayName shouldBe "Kigali International Airport"
                bookmark.latitude shouldBe lat
                bookmark.longitude shouldBe lng
            }
            // Real bug found live (2026-08-02): see addBookmark's own doc comment. This
            // is the actual fix -- every other real write-shaped call in this module
            // already carries this same real anti-spam check.
            Then("it real-checks the per-user anti-spam rate limit before creating") {
                verify(exactly = 1) { rateLimiter.checkLimit("maps:bookmark:user_1", limit = 60, window = Duration.ofMinutes(1)) }
            }
        }

        When("a place already bookmarked by the same user is bookmarked again") {
            val existing = MapBookmark(id = "map_bookmark_1", userId = "user_1", displayName = "Kigali International Airport", latitude = lat, longitude = lng)
            every { mapBookmarkRepository.findByUserIdAndLatitudeAndLongitude("user_1", lat, lng) } returns existing

            val bookmark = service.addBookmark("user_1", "Kigali International Airport", lat, lng)

            Then("it real-idempotently returns the existing bookmark rather than creating a duplicate") {
                bookmark shouldBe existing
                io.mockk.verify(exactly = 0) { mapBookmarkRepository.save(any()) }
            }
        }

        When("an out-of-range coordinate is bookmarked") {
            Then("it throws InvalidMapsCoordinateException before ever touching the repository") {
                try {
                    service.addBookmark("user_1", "Nowhere", 999.0, 30.0)
                    error("expected InvalidMapsCoordinateException")
                } catch (e: InvalidMapsCoordinateException) {
                    // expected
                }
            }
        }

        When("bookmarking with a blank name") {
            Then("it throws InvalidBookmarkNameException") {
                try {
                    service.addBookmark("user_1", "   ", lat, lng)
                    error("expected InvalidBookmarkNameException")
                } catch (e: InvalidBookmarkNameException) {
                    // expected
                }
            }
        }

        When("bookmarking with a name longer than the real 512-char DB column bound") {
            Then("it throws InvalidBookmarkNameException rather than risking a raw DB insert failure") {
                try {
                    service.addBookmark("user_1", "x".repeat(513), lat, lng)
                    error("expected InvalidBookmarkNameException")
                } catch (e: InvalidBookmarkNameException) {
                    // expected
                }
            }
        }

        // Real bookmark folders/colors (2026-07-22) -- see MapBookmark's own doc comment
        // for the full account of this Naver/Kakao Maps "My Places" parity gap.
        When("a real new place is bookmarked into a real named folder with a real color") {
            every { mapBookmarkRepository.findByUserIdAndLatitudeAndLongitude("user_1", lat, lng) } returns null
            every { mapBookmarkRepository.save(any()) } answers { firstArg() }

            val bookmark = service.addBookmark("user_1", "Kigali International Airport", lat, lng, folderName = "Family", color = "#3182F6")

            Then("it saves the bookmark into that real folder with that real color") {
                bookmark.folderName shouldBe "Family"
                bookmark.color shouldBe "#3182F6"
            }
        }

        When("bookmarking with no folder/color given") {
            every { mapBookmarkRepository.findByUserIdAndLatitudeAndLongitude("user_1", lat, lng) } returns null
            every { mapBookmarkRepository.save(any()) } answers { firstArg() }

            val bookmark = service.addBookmark("user_1", "Kigali International Airport", lat, lng)

            Then("it defaults into the real 'Saved places' folder in the real pre-existing star color") {
                bookmark.folderName shouldBe "Saved places"
                bookmark.color shouldBe "#F5A623"
            }
        }

        When("bookmarking with a real invalid (non-hex) color") {
            Then("it throws InvalidBookmarkColorException before ever touching the repository") {
                try {
                    service.addBookmark("user_1", "Kigali International Airport", lat, lng, color = "blue")
                    error("expected InvalidBookmarkColorException")
                } catch (e: InvalidBookmarkColorException) {
                    // expected
                }
            }
        }

        When("the real per-user bookmark rate limit is exceeded") {
            every { rateLimiter.checkLimit("maps:bookmark:user_1", limit = 60, window = Duration.ofMinutes(1)) } throws
                RateLimitExceededException("Too many requests")

            Then("it real-propagates RateLimitExceededException before ever creating a real duplicate-spam row") {
                try {
                    service.addBookmark("user_1", "Kigali International Airport", lat, lng)
                    error("expected RateLimitExceededException")
                } catch (e: RateLimitExceededException) {
                    io.mockk.verify(exactly = 0) { mapBookmarkRepository.save(any()) }
                }
            }
        }

        When("bookmarking with a folder name longer than the real 120-char DB column bound") {
            Then("it throws InvalidBookmarkFolderException rather than risking a raw DB insert failure") {
                try {
                    service.addBookmark("user_1", "Kigali International Airport", lat, lng, folderName = "x".repeat(121))
                    error("expected InvalidBookmarkFolderException")
                } catch (e: InvalidBookmarkFolderException) {
                    // expected
                }
            }
        }

        When("a real existing bookmark is moved to a different real folder") {
            val existing = MapBookmark(id = "map_bookmark_1", userId = "user_1", displayName = "Kigali International Airport", latitude = lat, longitude = lng, folderName = "Saved places", color = "#F5A623")
            every { mapBookmarkRepository.findByUserIdAndLatitudeAndLongitude("user_1", lat, lng) } returns existing
            every { mapBookmarkRepository.save(any()) } answers { firstArg() }

            val moved = service.moveBookmark("user_1", lat, lng, "Cafes to try", "#8B5CF6")

            Then("it real-preserves the original id/place while updating the real folder/color") {
                moved.id shouldBe existing.id
                moved.displayName shouldBe existing.displayName
                moved.folderName shouldBe "Cafes to try"
                moved.color shouldBe "#8B5CF6"
            }
        }

        When("moving a bookmark that doesn't real-exist at that location") {
            every { mapBookmarkRepository.findByUserIdAndLatitudeAndLongitude("user_1", lat, lng) } returns null

            Then("it throws BookmarkNotFoundException rather than silently creating one") {
                try {
                    service.moveBookmark("user_1", lat, lng, "Cafes to try", "#8B5CF6")
                    error("expected BookmarkNotFoundException")
                } catch (e: BookmarkNotFoundException) {
                    // expected
                }
            }
        }
    }

    Given("a user selecting a point on the Rwanda map") {
        val nominatimGeocodingClient = mockk<NominatimGeocodingClient>()
        val osrmRoutingClient = mockk<OsrmRoutingClient>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val mapBookmarkRepository = mockk<MapBookmarkRepository>(relaxed = true)
        val service = MapsService(nominatimGeocodingClient, osrmRoutingClient, rateLimiter, mapBookmarkRepository)
        val latitude = -1.9441; val longitude = 30.0619

        When("self-hosted Nominatim resolves the selected point") {
            every { nominatimGeocodingClient.reverseGeocode(latitude, longitude) } returns "Nyarugenge"

            val result = service.reverseGeocode("user_1", latitude, longitude)

            Then("it returns the real neighbourhood and applies a bounded rate limit") {
                result shouldBe "Nyarugenge"
                verify(exactly = 1) { rateLimiter.checkLimit("maps:reverse:user_1", limit = 60, window = Duration.ofMinutes(1)) }
            }
        }

        When("the point lies outside Rwanda") {
            Then("it rejects it before spending reverse-geocoder capacity") {
                try {
                    service.reverseGeocode("user_1", 0.0, 0.0)
                    error("expected RouteNotFoundException")
                } catch (e: RouteNotFoundException) {
                    // expected
                }
                verify(exactly = 0) { nominatimGeocodingClient.reverseGeocode(any(), any()) }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
