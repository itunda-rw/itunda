package rw.itunda.maps

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import rw.itunda.core.domain.TransitRoute
import rw.itunda.core.domain.TransitScheduledTrip
import rw.itunda.core.domain.TransitStop
import rw.itunda.core.domain.TransitStopTime
import rw.itunda.core.repository.TransitRouteRepository
import rw.itunda.core.repository.TransitScheduledTripRepository
import rw.itunda.core.repository.TransitStopRepository
import rw.itunda.core.repository.TransitStopTimeRepository

class TransitRoutingServiceTest : BehaviorSpec({

    // Real Kigali-ish coordinates, close enough together that both stops fall within
    // the real 0.8km walk radius of their respective real origin/destination points.
    val originStop = TransitStop(id = "stop_a", name = "Downtown", latitude = -1.9500, longitude = 30.0600)
    val destStop = TransitStop(id = "stop_b", name = "Kimironko", latitude = -1.9350, longitude = 30.1050)
    val farStop = TransitStop(id = "stop_far", name = "Far Away", latitude = -2.5000, longitude = 29.5000)
    val route33 = TransitRoute(id = "route_33", shortName = "33", longName = "Downtown - Kimironko")

    Given("a real GTFS feed with one direct trip connecting two nearby stops") {
        val transitStopRepository = mockk<TransitStopRepository>()
        val transitRouteRepository = mockk<TransitRouteRepository>()
        val transitScheduledTripRepository = mockk<TransitScheduledTripRepository>()
        val transitStopTimeRepository = mockk<TransitStopTimeRepository>()
        val service = TransitRoutingService(transitStopRepository, transitRouteRepository, transitScheduledTripRepository, transitStopTimeRepository)

        every { transitStopRepository.findAll() } returns listOf(originStop, destStop, farStop)
        val trip = TransitScheduledTrip(id = "trip_1", routeId = "route_33", serviceId = "weekday")
        val originTime = TransitStopTime(id = "st1", tripId = "trip_1", stopId = "stop_a", arrivalSecondsAfterMidnight = 3600 * 8, departureSecondsAfterMidnight = 3600 * 8, stopSequence = 1)
        val destTime = TransitStopTime(id = "st2", tripId = "trip_1", stopId = "stop_b", arrivalSecondsAfterMidnight = 3600 * 8 + 1200, departureSecondsAfterMidnight = 3600 * 8 + 1200, stopSequence = 5)
        every { transitStopTimeRepository.findByStopIdIn(setOf("stop_a")) } returns listOf(originTime)
        every { transitStopTimeRepository.findByStopIdIn(setOf("stop_b")) } returns listOf(destTime)
        every { transitScheduledTripRepository.findAllById(setOf("trip_1")) } returns listOf(trip)
        every { transitRouteRepository.findAllById(setOf("route_33")) } returns listOf(route33)

        When("searching before the real scheduled departure time") {
            val journeys = service.findDirectJourneys(-1.9500, 30.0600, -1.9350, 30.1050, nowSecondsAfterMidnight = 3600 * 7)

            Then("it returns the real direct journey") {
                journeys.size shouldBe 1
                journeys[0].route.id shouldBe "route_33"
                journeys[0].originStop.id shouldBe "stop_a"
                journeys[0].destinationStop.id shouldBe "stop_b"
                journeys[0].departureSecondsAfterMidnight shouldBe 3600 * 8
            }
        }

        When("searching after the real scheduled departure has already passed") {
            val journeys = service.findDirectJourneys(-1.9500, 30.0600, -1.9350, 30.1050, nowSecondsAfterMidnight = 3600 * 9)

            Then("it honestly returns no journeys rather than a departure that's already gone") {
                journeys shouldBe emptyList()
            }
        }
    }

    Given("a trip whose stop sequence visits the destination-side stop BEFORE the origin-side stop") {
        val transitStopRepository = mockk<TransitStopRepository>()
        val transitRouteRepository = mockk<TransitRouteRepository>()
        val transitScheduledTripRepository = mockk<TransitScheduledTripRepository>()
        val transitStopTimeRepository = mockk<TransitStopTimeRepository>()
        val service = TransitRoutingService(transitStopRepository, transitRouteRepository, transitScheduledTripRepository, transitStopTimeRepository)

        every { transitStopRepository.findAll() } returns listOf(originStop, destStop)
        // Real reversed-direction trip: the "destination" stop has an earlier real
        // stop_sequence than the "origin" stop on this particular real trip.
        val originTime = TransitStopTime(id = "st1", tripId = "trip_reverse", stopId = "stop_a", arrivalSecondsAfterMidnight = 3600 * 8, departureSecondsAfterMidnight = 3600 * 8, stopSequence = 5)
        val destTime = TransitStopTime(id = "st2", tripId = "trip_reverse", stopId = "stop_b", arrivalSecondsAfterMidnight = 3600 * 7, departureSecondsAfterMidnight = 3600 * 7, stopSequence = 1)
        every { transitStopTimeRepository.findByStopIdIn(setOf("stop_a")) } returns listOf(originTime)
        every { transitStopTimeRepository.findByStopIdIn(setOf("stop_b")) } returns listOf(destTime)
        every { transitScheduledTripRepository.findAllById(setOf("trip_reverse")) } returns listOf(TransitScheduledTrip(id = "trip_reverse", routeId = "route_33", serviceId = null))
        every { transitRouteRepository.findAllById(setOf("route_33")) } returns listOf(route33)

        When("searching for a direct journey") {
            val journeys = service.findDirectJourneys(-1.9500, 30.0600, -1.9350, 30.1050, nowSecondsAfterMidnight = 0)

            Then("it correctly excludes this trip -- it doesn't actually go the requested direction") {
                journeys shouldBe emptyList()
            }
        }
    }

    Given("no real stop exists near the requested origin") {
        val transitStopRepository = mockk<TransitStopRepository>()
        val transitRouteRepository = mockk<TransitRouteRepository>()
        val transitScheduledTripRepository = mockk<TransitScheduledTripRepository>()
        val transitStopTimeRepository = mockk<TransitStopTimeRepository>()
        val service = TransitRoutingService(transitStopRepository, transitRouteRepository, transitScheduledTripRepository, transitStopTimeRepository)

        every { transitStopRepository.findAll() } returns listOf(destStop, farStop)

        When("searching for a direct journey from a real point with no nearby stop") {
            val journeys = service.findDirectJourneys(-1.9500, 30.0600, -1.9350, 30.1050)

            Then("it honestly returns no journeys, never a fabricated one") {
                journeys shouldBe emptyList()
            }
        }
    }

    Given("no real transit stops have been imported at all") {
        val transitStopRepository = mockk<TransitStopRepository>()
        val transitRouteRepository = mockk<TransitRouteRepository>()
        val transitScheduledTripRepository = mockk<TransitScheduledTripRepository>()
        val transitStopTimeRepository = mockk<TransitStopTimeRepository>()
        val service = TransitRoutingService(transitStopRepository, transitRouteRepository, transitScheduledTripRepository, transitStopTimeRepository)

        every { transitStopRepository.findAll() } returns emptyList()

        When("searching for a direct journey") {
            val journeys = service.findDirectJourneys(-1.9500, 30.0600, -1.9350, 30.1050)

            Then("it honestly returns no journeys rather than erroring") {
                journeys shouldBe emptyList()
            }
        }
    }
})
