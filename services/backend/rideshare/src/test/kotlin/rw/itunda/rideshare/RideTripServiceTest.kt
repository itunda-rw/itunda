package rw.itunda.rideshare

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.transaction.support.TransactionSynchronizationManager
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.Message
import rw.itunda.core.domain.RideDriver
import rw.itunda.core.domain.RideTrip
import rw.itunda.core.domain.RideTripStatus
import rw.itunda.core.domain.RideTripStop
import rw.itunda.core.domain.Account
import rw.itunda.core.domain.AccountType
import rw.itunda.core.geo.OsrmRoutingClient
import rw.itunda.core.geo.RouteResult
import rw.itunda.core.geo.TravelMode
import rw.itunda.core.ledger.InsufficientFundsException
import rw.itunda.core.ledger.LedgerPostResult
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.RideDriverRepository
import rw.itunda.core.repository.RideTripRepository
import rw.itunda.core.repository.RideTripStopRepository
import rw.itunda.core.repository.TransactionRepository
import rw.itunda.core.repository.AccountRepository
import rw.itunda.messaging.ConversationNotFoundException
import rw.itunda.messaging.MessagingService
import java.math.BigDecimal
import java.time.Instant
import java.util.Optional

/**
 * First test coverage for real Kakao T-style ride-hailing -- see RideTripService's own
 * doc comment for the full sourced account. Mirrors EatsOrderServiceTest's own
 * established mocking conventions for this codebase's dispatch/escrow shape.
 */
class RideTripServiceTest : BehaviorSpec({

    fun newService(
        rideDriverRepository: RideDriverRepository = mockk(),
        rideTripRepository: RideTripRepository = mockk(),
        rideTripStopRepository: RideTripStopRepository = mockk(relaxed = true),
        accountRepository: AccountRepository = mockk(),
        ledgerService: LedgerService = mockk(),
        // Real, explicit stub, not relaxed=true's default -- same known "relaxed mockk
        // can't correctly infer JpaRepository's generic save() signature" gotcha
        // EatsOrderServiceTest's own tests already document repeatedly.
        transactionRepository: TransactionRepository = mockk<TransactionRepository>(relaxed = true).also { every { it.save(any()) } answers { firstArg() } },
        notificationRepository: NotificationRepository = mockk(relaxed = true),
        rateLimiter: RateLimiter = mockk(relaxed = true),
        pushNotificationService: PushNotificationService = mockk(relaxed = true),
        messagingService: rw.itunda.messaging.MessagingService = mockk(relaxed = true),
        fraudRuleEngine: rw.itunda.core.fraud.FraudRuleEngine = mockk(relaxed = true),
        // Explicit strict stub returning null, same EatsOrderServiceTest convention for
        // this exact client -- matches OsrmRoutingClient's own real never-fail contract
        // when unconfigured/unreachable, so every existing test keeps exercising the
        // real haversine fallback unchanged unless a test explicitly stubs a real route.
        osrmRoutingClient: OsrmRoutingClient = mockk<OsrmRoutingClient>().also { every { it.routeThrough(any(), any()) } returns null },
    ) = RideTripService(
        rideDriverRepository, rideTripRepository, rideTripStopRepository, accountRepository, ledgerService,
        transactionRepository, notificationRepository, rateLimiter, pushNotificationService, messagingService, fraudRuleEngine,
        osrmRoutingClient,
    )

    Given("a real passenger with sufficient balance and one real nearby driver") {
        val rideDriverRepository = mockk<RideDriverRepository>()
        val rideTripRepository = mockk<RideTripRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        every { notificationRepository.save(any()) } answers { firstArg() }
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val fraudRuleEngine = mockk<rw.itunda.core.fraud.FraudRuleEngine>(relaxed = true)
        val service = newService(
            rideDriverRepository = rideDriverRepository, rideTripRepository = rideTripRepository,
            accountRepository = accountRepository, ledgerService = ledgerService, notificationRepository = notificationRepository,
            pushNotificationService = pushNotificationService, fraudRuleEngine = fraudRuleEngine,
        )

        val passengerAccount = Account(
            id = "account_passenger", userId = "passenger_1", accountNumber = "1000000001", accountName = "Passenger",
            type = AccountType.MAIN, balance = BigDecimal("20000"), availableBalance = BigDecimal("20000"),
        )
        val nearbyDriver = RideDriver(id = "driver_1", userId = "driver_user_1", accountId = "account_driver", available = true, currentLatitude = -1.9536, currentLongitude = 30.0605, licenseNumber = "LIC-TEST")

        every { accountRepository.findByUserIdAndType("passenger_1", AccountType.MAIN) } returns passengerAccount
        every { rideDriverRepository.findByAvailableTrueAndCurrentLatitudeIsNotNullAndCurrentLongitudeIsNotNull() } returns listOf(nearbyDriver)
        every { rideTripRepository.findDistinctDriverIdsByStatusIn(any()) } returns emptyList()
        every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_1", emptyList())
        val savedSlot = slot<RideTrip>()
        every { rideTripRepository.save(capture(savedSlot)) } answers { firstArg() }
        every { rideDriverRepository.save(any()) } answers { firstArg() }

        When("the passenger requests a real trip") {
            val result = service.requestTrip(
                "passenger_1", "Kigali Heights", -1.9536, 30.0605, "Kigali Convention Centre", -1.9506, 30.0925,
            )

            Then("it computes a real fare from real haversine distance and holds it in escrow") {
                result.fare shouldBe (BigDecimal("1000").add(BigDecimal("250").multiply(result.distanceKm)).setScale(2, java.math.RoundingMode.HALF_UP))
                result.status shouldBe RideTripStatus.REQUESTED
            }

            Then("it real-dispatches an exclusive offer to the one real nearby driver") {
                savedSlot.captured.offeredDriverId shouldBe "driver_1"
                (savedSlot.captured.offerExpiresAt != null) shouldBe true
                verify { notificationRepository.save(match { it.type == "RIDE_TRIP_OFFER" && it.userId == "driver_user_1" }) }
            }

            Then("the offered driver also gets a real push notification, not just the in-app one -- critical given the 15-second window") {
                verify(exactly = 1) { pushNotificationService.sendToUser("driver_user_1", "New ride request", any(), any()) }
            }

            // Real gap closed 2026-08-17 -- see FraudRuleEngine's own doc comment:
            // ride/driver payouts were a named, honestly-noted-but-unwired gap.
            // recipientUserId is null -- no driver is matched yet at request time.
            Then("the real fraud engine is evaluated against the passenger and the real fare before the transaction is saved") {
                verify(exactly = 1) { fraudRuleEngine.evaluate("passenger_1", null, result.fare, "ledgertxn_1") }
            }
        }

        When("a ride request is still inside its payment transaction") {
            TransactionSynchronizationManager.initSynchronization()
            try {
                service.requestTrip(
                    "passenger_1", "Kigali Heights", -1.9536, 30.0605, "Kigali Convention Centre", -1.9506, 30.0925,
                )

                Then("the driver offer is stored, but its external push is withheld") {
                    verify(exactly = 1) { notificationRepository.save(match { it.type == "RIDE_TRIP_OFFER" && it.userId == "driver_user_1" }) }
                    verify(exactly = 0) { pushNotificationService.sendToUser(any(), any(), any(), any()) }
                }

                Then("the offered driver receives one push only after commit") {
                    TransactionSynchronizationManager.getSynchronizations().single().afterCommit()
                    verify(exactly = 1) { pushNotificationService.sendToUser("driver_user_1", "New ride request", any(), any()) }
                }
            } finally {
                TransactionSynchronizationManager.clearSynchronization()
            }
        }
    }

    // Real gap closed 2026-08-17 -- see RideTripService's own doc comment: the real
    // fare distance itself now prefers OsrmRoutingClient.routeThrough (real road
    // distance) over the old straight-line-only haversine sum, same never-fail
    // fallback discipline every other real OSRM caller in this codebase follows.
    Given("a real passenger requesting a trip while itunda's self-hosted OSRM instance is reachable") {
        val rideDriverRepository = mockk<RideDriverRepository>()
        val rideTripRepository = mockk<RideTripRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val osrmRoutingClient = mockk<OsrmRoutingClient>()
        val service = newService(
            rideDriverRepository = rideDriverRepository, rideTripRepository = rideTripRepository,
            accountRepository = accountRepository, ledgerService = ledgerService, osrmRoutingClient = osrmRoutingClient,
        )

        val passengerAccount = Account(
            id = "account_passenger", userId = "passenger_1", accountNumber = "1000000001", accountName = "Passenger",
            type = AccountType.MAIN, balance = BigDecimal("20000"), availableBalance = BigDecimal("20000"),
        )
        every { accountRepository.findByUserIdAndType("passenger_1", AccountType.MAIN) } returns passengerAccount
        every { rideDriverRepository.findByAvailableTrueAndCurrentLatitudeIsNotNullAndCurrentLongitudeIsNotNull() } returns emptyList()
        every { rideTripRepository.findDistinctDriverIdsByStatusIn(any()) } returns emptyList()
        every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_osrm_1", emptyList())
        every { rideTripRepository.save(any()) } answers { firstArg() }

        // A real road route is always longer than the direct straight-line distance
        // between the same two points (Kigali Heights -> Kigali Convention Centre is
        // ~2.9km haversine) -- 6.5km chosen specifically so the two distances can never
        // coincidentally match, proving the real road distance was actually used.
        val realRoadRoute = RouteResult(distanceKm = 6.5, durationMinutes = 14.0, geometry = emptyList())
        every { osrmRoutingClient.routeThrough(listOf(-1.9536 to 30.0605, -1.9506 to 30.0925), TravelMode.DRIVING) } returns realRoadRoute

        When("the trip is requested") {
            val result = service.requestTrip(
                "passenger_1", "Kigali Heights", -1.9536, 30.0605, "Kigali Convention Centre", -1.9506, 30.0925,
            )

            Then("the real fare is computed from OSRM's real road distance, not the shorter straight-line distance") {
                result.distanceKm shouldBe BigDecimal("6.500")
                result.fare shouldBe BigDecimal("1000").add(BigDecimal("250").multiply(BigDecimal("6.500"))).setScale(2, java.math.RoundingMode.HALF_UP)
            }
        }
    }

    // Real Uber "Upfront Fare" simplification -- see RideTripService.estimateFare's own
    // doc comment. Proves the actual invariant that matters: estimateFare and
    // requestTrip must return the IDENTICAL fare for the identical route (both reuse
    // the same real calculateFare helper), and estimateFare must never touch the
    // ledger/wallet/rate-limiter at all -- a passenger previewing a price before
    // committing must never hold real money or count against their real request quota.
    Given("a real passenger previewing a fare before requesting the real trip") {
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val rateLimiter = mockk<RateLimiter>()
        val osrmRoutingClient = mockk<OsrmRoutingClient>()
        val service = newService(accountRepository = accountRepository, ledgerService = ledgerService, rateLimiter = rateLimiter, osrmRoutingClient = osrmRoutingClient)

        val realRoadRoute = RouteResult(distanceKm = 6.5, durationMinutes = 14.0, geometry = emptyList())
        every { osrmRoutingClient.routeThrough(listOf(-1.9536 to 30.0605, -1.9506 to 30.0925), TravelMode.DRIVING) } returns realRoadRoute

        When("the fare is estimated for the same real route requestTrip charges above") {
            val estimatedFare = service.estimateFare(-1.9536, 30.0605, -1.9506, 30.0925)

            Then("it exactly matches the real fare requestTrip would charge for the identical route") {
                estimatedFare shouldBe BigDecimal("1000").add(BigDecimal("250").multiply(BigDecimal("6.500"))).setScale(2, java.math.RoundingMode.HALF_UP)
            }

            Then("no real money, wallet, or rate limit is ever touched by a preview") {
                verify(exactly = 0) { accountRepository.findByUserIdAndType(any(), any()) }
                verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                verify(exactly = 0) { rateLimiter.checkLimit(any(), any(), any()) }
            }
        }

        When("an invalid coordinate is given") {
            Then("it throws InvalidRideLocationException before ever calling OSRM") {
                try {
                    service.estimateFare(999.0, 30.0605, -1.9506, 30.0925)
                    error("expected InvalidRideLocationException")
                } catch (e: InvalidRideLocationException) {
                    verify(exactly = 0) { osrmRoutingClient.routeThrough(any(), any()) }
                }
            }
        }
    }

    Given("a real driver with an active Uber-style Destination Filter") {
        val rideDriverRepository = mockk<RideDriverRepository>()
        val rideTripRepository = mockk<RideTripRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = newService(
            rideDriverRepository = rideDriverRepository, rideTripRepository = rideTripRepository,
            accountRepository = accountRepository, ledgerService = ledgerService, notificationRepository = notificationRepository,
            pushNotificationService = pushNotificationService,
        )

        val passengerAccount = Account(
            id = "account_passenger", userId = "passenger_1", accountNumber = "1000000001", accountName = "Passenger",
            type = AccountType.MAIN, balance = BigDecimal("20000"), availableBalance = BigDecimal("20000"),
        )
        // Real destination far east of the driver's current position -- a dropoff also
        // east of the driver moves them genuinely closer; a dropoff west moves them
        // genuinely further, same real haversine-distance-reduction check
        // RideTripService.rankNearbyDrivers's own doc comment describes.
        val filteredDriver = RideDriver(
            id = "driver_filtered", userId = "driver_user_filtered", accountId = "account_driver_filtered",
            available = true, currentLatitude = -1.9536, currentLongitude = 30.0605,
            destinationLatitude = -1.9300, destinationLongitude = 30.1300, licenseNumber = "LIC-TEST",
        )

        every { accountRepository.findByUserIdAndType("passenger_1", AccountType.MAIN) } returns passengerAccount
        every { rideDriverRepository.findByAvailableTrueAndCurrentLatitudeIsNotNullAndCurrentLongitudeIsNotNull() } returns listOf(filteredDriver)
        every { rideTripRepository.findDistinctDriverIdsByStatusIn(any()) } returns emptyList()
        every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_1", emptyList())
        every { rideTripRepository.save(any()) } answers { firstArg() }
        every { rideDriverRepository.save(any()) } answers { firstArg() }

        When("the only real nearby driver's Destination Filter is active and the trip's real dropoff moves them further away") {
            val savedSlot = slot<RideTrip>()
            every { rideTripRepository.save(capture(savedSlot)) } answers { firstArg() }

            val result = service.requestTrip(
                "passenger_1", "Kigali Heights", -1.9536, 30.0605, "Nyamirambo", -1.9700, 29.9800,
            )

            Then("the filtered driver is real-excluded from the candidate pool and the trip falls to the real open list") {
                result.offeredDriverId shouldBe null
                verify(exactly = 0) { notificationRepository.save(match { it.type == "RIDE_TRIP_OFFER" }) }
            }
        }

        When("the only real nearby driver's Destination Filter is active and the trip's real dropoff genuinely brings them closer") {
            val savedSlot = slot<RideTrip>()
            every { rideTripRepository.save(capture(savedSlot)) } answers { firstArg() }

            val result = service.requestTrip(
                "passenger_1", "Kigali Heights", -1.9536, 30.0605, "Kanombe", -1.9350, 30.1250,
            )

            Then("the filtered driver is real-eligible and gets the real exclusive offer") {
                result.offeredDriverId shouldBe "driver_filtered"
            }
        }
    }

    Given("a real passenger with insufficient balance") {
        val rideDriverRepository = mockk<RideDriverRepository>(relaxed = true)
        val rideTripRepository = mockk<RideTripRepository>(relaxed = true)
        val accountRepository = mockk<AccountRepository>()
        val service = newService(rideDriverRepository = rideDriverRepository, rideTripRepository = rideTripRepository, accountRepository = accountRepository)

        val poorAccount = Account(
            id = "account_poor", userId = "passenger_2", accountNumber = "1000000002", accountName = "Passenger",
            type = AccountType.MAIN, balance = BigDecimal("100"), availableBalance = BigDecimal("100"),
        )
        every { accountRepository.findByUserIdAndType("passenger_2", AccountType.MAIN) } returns poorAccount

        When("they request a real trip") {
            Then("it throws InsufficientFundsException before any ledger post") {
                try {
                    service.requestTrip("passenger_2", "A", -1.9536, 30.0605, "B", -1.9506, 30.0925)
                    error("expected InsufficientFundsException")
                } catch (e: InsufficientFundsException) {
                    // expected
                }
            }
        }
    }

    Given("a real passenger requesting a real Kakao T-style multi-stop ride") {
        val rideDriverRepository = mockk<RideDriverRepository>(relaxed = true)
        val rideTripRepository = mockk<RideTripRepository>()
        val rideTripStopRepository = mockk<RideTripStopRepository>(relaxed = true)
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val service = newService(
            rideDriverRepository = rideDriverRepository, rideTripRepository = rideTripRepository,
            rideTripStopRepository = rideTripStopRepository, accountRepository = accountRepository, ledgerService = ledgerService,
        )

        val passengerAccount = Account(
            id = "account_passenger_6", userId = "passenger_6", accountNumber = "1000000006", accountName = "Passenger",
            type = AccountType.MAIN, balance = BigDecimal("20000"), availableBalance = BigDecimal("20000"),
        )
        every { accountRepository.findByUserIdAndType("passenger_6", AccountType.MAIN) } returns passengerAccount
        every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_multistop", emptyList())
        every { rideTripRepository.save(any()) } answers { firstArg() }

        When("the trip has one real extra stop between pickup and dropoff") {
            val stopSlot = slot<RideTripStop>()
            every { rideTripStopRepository.save(capture(stopSlot)) } answers { firstArg() }

            val trip = service.requestTrip(
                "passenger_6", "Kigali Heights", -1.9536, 30.0605, "Kigali Convention Centre", -1.9506, 30.0925,
                stops = listOf(RideStopInput("Kimironko Market", -1.9425, 30.1085)),
            )

            Then("the real fare/distance covers the whole route through the stop, longer than a direct trip") {
                val directDistance = BigDecimal(rw.itunda.core.geo.GeoUtils.haversineKm(-1.9536, 30.0605, -1.9506, 30.0925))
                (trip.distanceKm > directDistance) shouldBe true
            }

            Then("it persists the real stop at sequence 0, not yet arrived") {
                stopSlot.captured.tripId shouldBe trip.id
                stopSlot.captured.sequence shouldBe 0
                stopSlot.captured.arrivedAt shouldBe null
            }
        }

        When("a passenger requests more than the real max stop count") {
            Then("it throws RideTooManyStopsException before touching the ledger") {
                try {
                    service.requestTrip(
                        "passenger_6", "A", -1.9536, 30.0605, "B", -1.9506, 30.0925,
                        stops = List(RideTripService.MAX_STOPS + 1) { RideStopInput("Stop $it", -1.95, 30.06) },
                    )
                    error("expected RideTooManyStopsException")
                } catch (e: RideTooManyStopsException) {
                    // expected
                }
                verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
            }
        }
    }

    Given("a real driver on an in-progress multi-stop trip") {
        val rideDriverRepository = mockk<RideDriverRepository>(relaxed = true)
        val rideTripRepository = mockk<RideTripRepository>()
        val rideTripStopRepository = mockk<RideTripStopRepository>()
        val service = newService(rideDriverRepository = rideDriverRepository, rideTripRepository = rideTripRepository, rideTripStopRepository = rideTripStopRepository)

        val driver = RideDriver(id = "driver_6", userId = "driver_user_6", accountId = "account_driver_6", available = true, licenseNumber = "LIC-TEST")
        val trip = RideTrip(
            id = "ride_trip_multistop_1", passengerId = "passenger_7", driverId = "driver_6", pickupAddress = "A",
            pickupLatitude = -1.95, pickupLongitude = 30.06, dropoffAddress = "B", dropoffLatitude = -1.96, dropoffLongitude = 30.09,
            distanceKm = BigDecimal("5.0"), fare = BigDecimal("2250"), platformFee = BigDecimal("33.75"), transactionId = "txn_multistop_1",
            status = RideTripStatus.IN_PROGRESS,
        )
        val firstStop = RideTripStop(id = "ride_trip_stop_1", tripId = trip.id, sequence = 0, address = "Stop 1", latitude = -1.955, longitude = 30.07)
        val secondStop = RideTripStop(id = "ride_trip_stop_2", tripId = trip.id, sequence = 1, address = "Stop 2", latitude = -1.958, longitude = 30.08)

        every { rideDriverRepository.findByUserId("driver_user_6") } returns driver
        every { rideTripRepository.findById(trip.id) } returns java.util.Optional.of(trip)
        every { rideTripStopRepository.save(any()) } answers { firstArg() }

        When("the driver marks arrival at the next unvisited stop, in order") {
            every { rideTripStopRepository.findByTripIdOrderBySequenceAsc(trip.id) } returns listOf(firstStop, secondStop)

            val arrived = service.arriveAtStop("driver_user_6", trip.id)

            Then("it marks the real first (not second) unvisited stop arrived") {
                arrived.id shouldBe "ride_trip_stop_1"
                (arrived.arrivedAt != null) shouldBe true
            }
        }

        When("every real stop has already been marked arrived") {
            val bothArrived = listOf(
                RideTripStop(id = "ride_trip_stop_1", tripId = trip.id, sequence = 0, address = "Stop 1", latitude = -1.955, longitude = 30.07, arrivedAt = Instant.now()),
                RideTripStop(id = "ride_trip_stop_2", tripId = trip.id, sequence = 1, address = "Stop 2", latitude = -1.958, longitude = 30.08, arrivedAt = Instant.now()),
            )
            every { rideTripStopRepository.findByTripIdOrderBySequenceAsc(trip.id) } returns bothArrived

            Then("it throws RideNoRemainingStopsException") {
                try {
                    service.arriveAtStop("driver_user_6", trip.id)
                    error("expected RideNoRemainingStopsException")
                } catch (e: RideNoRemainingStopsException) {
                    // expected
                }
            }
        }
    }

    Given("a real passenger requesting a real Kakao T-style scheduled ride") {
        val rideDriverRepository = mockk<RideDriverRepository>(relaxed = true)
        val rideTripRepository = mockk<RideTripRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val service = newService(
            rideDriverRepository = rideDriverRepository, rideTripRepository = rideTripRepository,
            accountRepository = accountRepository, ledgerService = ledgerService,
        )

        val passengerAccount = Account(
            id = "account_passenger_4", userId = "passenger_4", accountNumber = "1000000004", accountName = "Passenger",
            type = AccountType.MAIN, balance = BigDecimal("20000"), availableBalance = BigDecimal("20000"),
        )
        every { accountRepository.findByUserIdAndType("passenger_4", AccountType.MAIN) } returns passengerAccount
        every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_scheduled", emptyList())
        val savedSlot = slot<RideTrip>()
        every { rideTripRepository.save(capture(savedSlot)) } answers { firstArg() }

        When("the requested time is well beyond the real dispatch lead time") {
            val scheduledFor = Instant.now().plus(java.time.Duration.ofHours(3))
            val trip = service.requestTrip(
                "passenger_4", "Kigali Heights", -1.9536, 30.0605, "Kigali Convention Centre", -1.9506, 30.0925,
                scheduledFor = scheduledFor,
            )

            Then("it holds the fare and records the real requested time, but does not dispatch yet") {
                trip.scheduledFor shouldBe scheduledFor
                savedSlot.captured.offeredDriverId shouldBe null
                verify(exactly = 0) { rideDriverRepository.findByAvailableTrueAndCurrentLatitudeIsNotNullAndCurrentLongitudeIsNotNull() }
            }
        }

        When("the requested time is in the past") {
            Then("it throws InvalidScheduledRideTimeException before touching the ledger") {
                try {
                    service.requestTrip(
                        "passenger_4", "A", -1.9536, 30.0605, "B", -1.9506, 30.0925,
                        scheduledFor = Instant.now().minusSeconds(60),
                    )
                    error("expected InvalidScheduledRideTimeException")
                } catch (e: InvalidScheduledRideTimeException) {
                    // expected
                }
                verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
            }
        }

        When("the requested time is beyond the real max scheduling window") {
            Then("it throws InvalidScheduledRideTimeException") {
                try {
                    service.requestTrip(
                        "passenger_4", "A", -1.9536, 30.0605, "B", -1.9506, 30.0925,
                        scheduledFor = Instant.now().plus(RideTripService.SCHEDULED_RIDE_MAX_WINDOW).plusSeconds(3600),
                    )
                    error("expected InvalidScheduledRideTimeException")
                } catch (e: InvalidScheduledRideTimeException) {
                    // expected
                }
            }
        }
    }

    Given("a real scheduled trip whose real dispatch lead time has just been crossed") {
        val rideDriverRepository = mockk<RideDriverRepository>()
        val rideTripRepository = mockk<RideTripRepository>()
        val service = newService(rideDriverRepository = rideDriverRepository, rideTripRepository = rideTripRepository)

        val nearbyDriver = RideDriver(id = "driver_5", userId = "driver_user_5", accountId = "account_driver_5", available = true, currentLatitude = -1.9536, currentLongitude = 30.0605, licenseNumber = "LIC-TEST")
        val dueTrip = RideTrip(
            id = "ride_trip_due", passengerId = "passenger_5", pickupAddress = "A", pickupLatitude = -1.9536, pickupLongitude = 30.0605,
            dropoffAddress = "B", dropoffLatitude = -1.9506, dropoffLongitude = 30.0925, distanceKm = BigDecimal("3.5"),
            fare = BigDecimal("1875"), platformFee = BigDecimal("28.13"), transactionId = "txn_due",
            status = RideTripStatus.REQUESTED, scheduledFor = Instant.now(),
        )

        every { rideDriverRepository.findByAvailableTrueAndCurrentLatitudeIsNotNullAndCurrentLongitudeIsNotNull() } returns listOf(nearbyDriver)
        every { rideTripRepository.findDistinctDriverIdsByStatusIn(any()) } returns emptyList()
        every { rideTripRepository.findById("ride_trip_due") } returns Optional.of(dueTrip)
        val savedSlot = slot<RideTrip>()
        every { rideTripRepository.save(capture(savedSlot)) } answers { firstArg() }
        every { rideDriverRepository.save(any()) } answers { firstArg() }

        When("the scheduler activates it") {
            val pools = service.computeDispatchPools()
            service.activateScheduledDispatchOne(dueTrip.id, pools)

            Then("it marks real dispatch as started, exactly once, and offers the trip to the one real nearby driver") {
                dueTrip.scheduledDispatchStartedAt shouldNotBe null
                savedSlot.captured.offeredDriverId shouldBe "driver_5"
            }
        }
    }

    // Section 180: RideDispatchScheduler used to call a single batch-@Transactional
    // reassignExpiredOffers(trips: List<RideTrip>) that looped every expired offer
    // network-wide in one shared transaction -- see RideTripService.reassignExpiredOffer's
    // own doc comment for the full account of the real transaction-poisoning bug this
    // closed. These two cases cover the per-item method that replaced it.
    Given("a real expired ride offer with a real nearby driver available") {
        val rideDriverRepository = mockk<RideDriverRepository>()
        val rideTripRepository = mockk<RideTripRepository>()
        val service = newService(rideDriverRepository = rideDriverRepository, rideTripRepository = rideTripRepository)

        val nearbyDriver = RideDriver(id = "driver_6", userId = "driver_user_6", accountId = "account_driver_6", available = true, currentLatitude = -1.9536, currentLongitude = 30.0605, licenseNumber = "LIC-TEST")
        val expiredTrip = RideTrip(
            id = "ride_trip_expired", passengerId = "passenger_6", pickupAddress = "A", pickupLatitude = -1.9536, pickupLongitude = 30.0605,
            dropoffAddress = "B", dropoffLatitude = -1.9506, dropoffLongitude = 30.0925, distanceKm = BigDecimal("3.5"),
            fare = BigDecimal("1875"), platformFee = BigDecimal("28.13"), transactionId = "txn_expired",
            status = RideTripStatus.REQUESTED, offeredDriverId = "driver_5", offerExpiresAt = Instant.now().minusSeconds(5),
        )

        every { rideDriverRepository.findByAvailableTrueAndCurrentLatitudeIsNotNullAndCurrentLongitudeIsNotNull() } returns listOf(nearbyDriver)
        every { rideTripRepository.findDistinctDriverIdsByStatusIn(any()) } returns emptyList()
        every { rideTripRepository.findById("ride_trip_expired") } returns Optional.of(expiredTrip)
        every { rideDriverRepository.findById("driver_5") } returns Optional.empty()
        val savedSlot = slot<RideTrip>()
        every { rideTripRepository.save(capture(savedSlot)) } answers { firstArg() }
        every { rideDriverRepository.save(any()) } answers { firstArg() }

        When("the scheduler reassigns it") {
            val pools = service.computeDispatchPools()
            service.reassignExpiredOffer(expiredTrip.id, pools)

            Then("it clears the stale offer and re-offers the trip to the next real nearby driver") {
                // Not `shouldBe null` here -- dispatchToNextDriver immediately re-sets a
                // fresh offerExpiresAt for driver_6's own new offer window on this same
                // mutable trip object, so by the time this assertion runs the field is
                // genuinely non-null again (this is the correct, intended behavior).
                expiredTrip.offerExpiresAt shouldNotBe null
                savedSlot.captured.offeredDriverId shouldBe "driver_6"
            }
        }
    }

    Given("a real expired ride offer that a driver already accepted in the gap before the scheduler ran") {
        val rideTripRepository = mockk<RideTripRepository>()
        val service = newService(rideTripRepository = rideTripRepository)

        // driverId already set -- a real concurrent acceptTrip() claimed this trip after
        // the scheduler's own read-only getExpiredOffers() poll but before this per-item
        // call ran.
        val claimedTrip = RideTrip(
            id = "ride_trip_claimed", passengerId = "passenger_7", pickupAddress = "A", pickupLatitude = -1.9536, pickupLongitude = 30.0605,
            dropoffAddress = "B", dropoffLatitude = -1.9506, dropoffLongitude = 30.0925, distanceKm = BigDecimal("3.5"),
            fare = BigDecimal("1875"), platformFee = BigDecimal("28.13"), transactionId = "txn_claimed",
            status = RideTripStatus.DRIVER_ASSIGNED, driverId = "driver_5", offeredDriverId = "driver_5", offerExpiresAt = Instant.now().minusSeconds(5),
        )
        every { rideTripRepository.findById("ride_trip_claimed") } returns Optional.of(claimedTrip)

        When("the scheduler's per-item call runs anyway") {
            val pools = DispatchPools(candidatePool = emptyList(), busyDriverIds = emptySet())
            service.reassignExpiredOffer(claimedTrip.id, pools)

            Then("the real re-check-before-act guard safely no-ops instead of clobbering the driver's real claim") {
                verify(exactly = 0) { rideTripRepository.save(any()) }
                claimedTrip.offeredDriverId shouldBe "driver_5"
            }
        }
    }

    Given("a driver who's already carrying a real active trip") {
        val rideDriverRepository = mockk<RideDriverRepository>()
        val rideTripRepository = mockk<RideTripRepository>()
        val service = newService(rideDriverRepository = rideDriverRepository, rideTripRepository = rideTripRepository)

        val driver = RideDriver(id = "driver_2", userId = "driver_user_2", accountId = "account_driver_2", available = true, licenseNumber = "LIC-TEST")
        val trip = RideTrip(
            id = "ride_trip_1", passengerId = "passenger_3", pickupAddress = "A", pickupLatitude = -1.95, pickupLongitude = 30.06,
            dropoffAddress = "B", dropoffLatitude = -1.96, dropoffLongitude = 30.09, distanceKm = BigDecimal("3.5"),
            fare = BigDecimal("1875"), platformFee = BigDecimal("28.13"), transactionId = "ledgertxn_x",
        )
        every { rideDriverRepository.findByUserId("driver_user_2") } returns driver
        every { rideTripRepository.findById("ride_trip_1") } returns Optional.of(trip)
        every { rideTripRepository.existsByDriverIdAndStatusIn("driver_2", listOf(RideTripStatus.DRIVER_ASSIGNED, RideTripStatus.IN_PROGRESS)) } returns true

        When("they try to accept a second real trip") {
            Then("it throws RideDriverAlreadyOnTripException -- itunda drivers carry one trip at a time") {
                try {
                    service.acceptTrip("driver_user_2", "ride_trip_1")
                    error("expected RideDriverAlreadyOnTripException")
                } catch (e: RideDriverAlreadyOnTripException) {
                    // expected
                }
            }
        }
    }

    Given("a real IN_PROGRESS trip a driver is completing") {
        val rideDriverRepository = mockk<RideDriverRepository>()
        val rideTripRepository = mockk<RideTripRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        every { notificationRepository.save(any()) } answers { firstArg() }
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = newService(
            rideDriverRepository = rideDriverRepository, rideTripRepository = rideTripRepository,
            accountRepository = accountRepository, ledgerService = ledgerService, notificationRepository = notificationRepository,
            pushNotificationService = pushNotificationService,
        )

        val driver = RideDriver(id = "driver_3", userId = "driver_user_3", accountId = "account_driver_3", available = true, licenseNumber = "LIC-TEST")
        val driverAccount = Account(
            id = "account_driver_3", userId = "driver_user_3", accountNumber = "1000000003", accountName = "Driver",
            type = AccountType.MAIN, balance = BigDecimal("0"), availableBalance = BigDecimal("0"),
        )
        val trip = RideTrip(
            id = "ride_trip_2", passengerId = "passenger_4", driverId = "driver_3", pickupAddress = "A", pickupLatitude = -1.95,
            pickupLongitude = 30.06, dropoffAddress = "B", dropoffLatitude = -1.96, dropoffLongitude = 30.09,
            distanceKm = BigDecimal("4.0"), fare = BigDecimal("2000"), platformFee = BigDecimal("30"),
            transactionId = "ledgertxn_y", status = RideTripStatus.IN_PROGRESS,
        )
        every { rideDriverRepository.findByUserId("driver_user_3") } returns driver
        every { rideTripRepository.findById("ride_trip_2") } returns Optional.of(trip)
        every { accountRepository.findById("account_driver_3") } returns Optional.of(driverAccount)
        val legsSlot = slot<List<rw.itunda.core.ledger.LedgerLeg>>()
        every { ledgerService.postLedgerTransaction(any(), capture(legsSlot)) } returns LedgerPostResult("ledgertxn_payout", emptyList())
        every { rideTripRepository.save(any()) } answers { firstArg() }

        When("the driver completes it") {
            val result = service.completeTrip("driver_user_3", "ride_trip_2")

            Then("it real-releases the fare from escrow, net of the platform fee, to the driver") {
                result.status shouldBe RideTripStatus.COMPLETED
                result.payoutTransactionId shouldBe "ledgertxn_payout"
                val netLeg = legsSlot.captured.find { it.accountId == "account_driver_3" }
                netLeg?.amount shouldBe BigDecimal("1970")
            }
        }
    }

    Given("a real DRIVER_ASSIGNED trip with a real PIN, a driver starting it") {
        val rideDriverRepository = mockk<RideDriverRepository>()
        val rideTripRepository = mockk<RideTripRepository>()
        val service = newService(rideDriverRepository = rideDriverRepository, rideTripRepository = rideTripRepository)

        val driver = RideDriver(id = "driver_4", userId = "driver_user_4", accountId = "account_driver_4", available = true, licenseNumber = "LIC-TEST")
        fun freshTrip() = RideTrip(
            id = "ride_trip_3", passengerId = "passenger_5", driverId = "driver_4", pickupAddress = "A", pickupLatitude = -1.95,
            pickupLongitude = 30.06, dropoffAddress = "B", dropoffLatitude = -1.96, dropoffLongitude = 30.09,
            distanceKm = BigDecimal("4.0"), fare = BigDecimal("2000"), platformFee = BigDecimal("30"),
            transactionId = "ledgertxn_z", status = RideTripStatus.DRIVER_ASSIGNED, pin = "4321",
        )
        every { rideDriverRepository.findByUserId("driver_user_4") } returns driver
        every { rideTripRepository.save(any()) } answers { firstArg() }

        When("the driver enters the exact PIN the passenger told them") {
            every { rideTripRepository.findById("ride_trip_3") } returns Optional.of(freshTrip())
            val result = service.startTrip("driver_user_4", "ride_trip_3", "4321")

            Then("it real-starts the trip") {
                result.status shouldBe RideTripStatus.IN_PROGRESS
            }
        }

        When("the driver enters the wrong PIN") {
            every { rideTripRepository.findById("ride_trip_3") } returns Optional.of(freshTrip())

            Then("it throws RidePinMismatchException and never starts the trip") {
                try {
                    service.startTrip("driver_user_4", "ride_trip_3", "0000")
                    error("expected RidePinMismatchException")
                } catch (e: RidePinMismatchException) {
                    // expected
                }
            }
        }

        When("the trip predates this feature and has no real PIN at all") {
            val trip = freshTrip()
            trip.pin = null
            every { rideTripRepository.findById("ride_trip_3") } returns Optional.of(trip)

            Then("it starts anyway rather than permanently locking out an old in-flight trip") {
                val result = service.startTrip("driver_user_4", "ride_trip_3", "anything")
                result.status shouldBe RideTripStatus.IN_PROGRESS
            }
        }
    }

    Given("a real REQUESTED trip (no driver assigned yet) the passenger wants to cancel") {
        val rideTripRepository = mockk<RideTripRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val service = newService(rideTripRepository = rideTripRepository, accountRepository = accountRepository, ledgerService = ledgerService)

        val passengerAccount = Account(
            id = "account_passenger_5", userId = "passenger_5", accountNumber = "1000000005", accountName = "Passenger",
            type = AccountType.MAIN, balance = BigDecimal("5000"), availableBalance = BigDecimal("5000"),
        )
        val trip = RideTrip(
            id = "ride_trip_3", passengerId = "passenger_5", pickupAddress = "A", pickupLatitude = -1.95, pickupLongitude = 30.06,
            dropoffAddress = "B", dropoffLatitude = -1.96, dropoffLongitude = 30.09, distanceKm = BigDecimal("2.0"),
            fare = BigDecimal("1500"), platformFee = BigDecimal("22.5"), transactionId = "ledgertxn_z", status = RideTripStatus.REQUESTED,
        )
        every { rideTripRepository.findById("ride_trip_3") } returns Optional.of(trip)
        every { accountRepository.findByUserIdAndType("passenger_5", AccountType.MAIN) } returns passengerAccount
        every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_refund", emptyList())
        every { rideTripRepository.save(any()) } answers { firstArg() }

        When("they cancel it") {
            val result = service.cancelTrip("passenger_5", "ride_trip_3")

            Then("it real-refunds the full fare, no fee -- an explicit cancel before any driver committed") {
                result.status shouldBe RideTripStatus.CANCELLED
                result.refundTransactionId shouldBe "ledgertxn_refund"
            }
        }
    }

    Given("a real DRIVER_ASSIGNED trip cancelled within the real Uber 2-minute grace period") {
        val rideDriverRepository = mockk<RideDriverRepository>()
        val rideTripRepository = mockk<RideTripRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        every { notificationRepository.save(any()) } answers { firstArg() }
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = newService(
            rideDriverRepository = rideDriverRepository, rideTripRepository = rideTripRepository,
            accountRepository = accountRepository, ledgerService = ledgerService, notificationRepository = notificationRepository,
            pushNotificationService = pushNotificationService,
        )

        val driver = RideDriver(id = "driver_cancel_1", userId = "driver_user_cancel_1", accountId = "account_driver_cancel_1", licenseNumber = "LIC-TEST")
        val driverAccount = Account(
            id = "account_driver_cancel_1", userId = "driver_user_cancel_1", accountNumber = "1000000010", accountName = "Driver",
            type = AccountType.MAIN, balance = BigDecimal("5000"), availableBalance = BigDecimal("5000"),
        )
        val passengerAccount = Account(
            id = "account_passenger_cancel_1", userId = "passenger_cancel_1", accountNumber = "1000000011", accountName = "Passenger",
            type = AccountType.MAIN, balance = BigDecimal("10000"), availableBalance = BigDecimal("10000"),
        )
        val trip = RideTrip(
            id = "ride_trip_cancel_1", passengerId = "passenger_cancel_1", driverId = "driver_cancel_1",
            pickupAddress = "A", pickupLatitude = -1.95, pickupLongitude = 30.06,
            dropoffAddress = "B", dropoffLatitude = -1.96, dropoffLongitude = 30.09, distanceKm = BigDecimal("2.0"),
            fare = BigDecimal("1500"), platformFee = BigDecimal("22.5"), transactionId = "ledgertxn_z2",
            status = RideTripStatus.DRIVER_ASSIGNED, driverAssignedAt = java.time.Instant.now().minusSeconds(30),
        )
        every { rideTripRepository.findById("ride_trip_cancel_1") } returns Optional.of(trip)
        every { rideDriverRepository.findById("driver_cancel_1") } returns Optional.of(driver)
        every { accountRepository.findByUserIdAndType("passenger_cancel_1", AccountType.MAIN) } returns passengerAccount
        every { accountRepository.findById("account_driver_cancel_1") } returns Optional.of(driverAccount)
        every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_refund_grace", emptyList())
        every { rideTripRepository.save(any()) } answers { firstArg() }

        When("the passenger cancels 30 real seconds after the driver was assigned") {
            val result = service.cancelTrip("passenger_cancel_1", "ride_trip_cancel_1")

            Then("it real-refunds the full fare, no fee -- still inside the real 2-minute grace window") {
                result.status shouldBe RideTripStatus.CANCELLED
                val legs = slot<List<rw.itunda.core.ledger.LedgerLeg>>()
                verify(exactly = 1) { ledgerService.postLedgerTransaction(any(), capture(legs)) }
                legs.captured.size shouldBe 2
                legs.captured[1].amount shouldBe BigDecimal("1500")
            }

            Then("it notifies the driver of a plain cancellation, not a fee payout") {
                verify(exactly = 1) {
                    notificationRepository.save(match { it.userId == "driver_user_cancel_1" && it.type == "RIDE_TRIP_UPDATE" })
                }
            }
        }
    }

    Given("a real DRIVER_ASSIGNED trip cancelled after the real Uber 2-minute grace period") {
        val rideDriverRepository = mockk<RideDriverRepository>()
        val rideTripRepository = mockk<RideTripRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        every { notificationRepository.save(any()) } answers { firstArg() }
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = newService(
            rideDriverRepository = rideDriverRepository, rideTripRepository = rideTripRepository,
            accountRepository = accountRepository, ledgerService = ledgerService, notificationRepository = notificationRepository,
            pushNotificationService = pushNotificationService,
        )

        val driver = RideDriver(id = "driver_cancel_2", userId = "driver_user_cancel_2", accountId = "account_driver_cancel_2", licenseNumber = "LIC-TEST")
        val driverAccount = Account(
            id = "account_driver_cancel_2", userId = "driver_user_cancel_2", accountNumber = "1000000012", accountName = "Driver",
            type = AccountType.MAIN, balance = BigDecimal("5000"), availableBalance = BigDecimal("5000"),
        )
        val passengerAccount = Account(
            id = "account_passenger_cancel_2", userId = "passenger_cancel_2", accountNumber = "1000000013", accountName = "Passenger",
            type = AccountType.MAIN, balance = BigDecimal("10000"), availableBalance = BigDecimal("10000"),
        )
        val trip = RideTrip(
            id = "ride_trip_cancel_2", passengerId = "passenger_cancel_2", driverId = "driver_cancel_2",
            pickupAddress = "A", pickupLatitude = -1.95, pickupLongitude = 30.06,
            dropoffAddress = "B", dropoffLatitude = -1.96, dropoffLongitude = 30.09, distanceKm = BigDecimal("2.0"),
            fare = BigDecimal("1500"), platformFee = BigDecimal("22.5"), transactionId = "ledgertxn_z3",
            status = RideTripStatus.DRIVER_ASSIGNED, driverAssignedAt = java.time.Instant.now().minusSeconds(180),
        )
        every { rideTripRepository.findById("ride_trip_cancel_2") } returns Optional.of(trip)
        every { rideDriverRepository.findById("driver_cancel_2") } returns Optional.of(driver)
        every { accountRepository.findByUserIdAndType("passenger_cancel_2", AccountType.MAIN) } returns passengerAccount
        every { accountRepository.findById("account_driver_cancel_2") } returns Optional.of(driverAccount)
        every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_refund_fee", emptyList())
        every { rideTripRepository.save(any()) } answers { firstArg() }

        When("the passenger cancels 3 real minutes after the driver was assigned") {
            val result = service.cancelTrip("passenger_cancel_2", "ride_trip_cancel_2")

            Then("it real-charges the modeled cancellation fee, refunding fare minus fee to the passenger and the fee to the driver") {
                result.status shouldBe RideTripStatus.CANCELLED
                val legs = slot<List<rw.itunda.core.ledger.LedgerLeg>>()
                verify(exactly = 1) { ledgerService.postLedgerTransaction(any(), capture(legs)) }
                legs.captured.size shouldBe 3
                legs.captured[1].amount shouldBe BigDecimal("500")
                legs.captured[2].amount shouldBe BigDecimal("1000")
                legs.captured[2].accountId shouldBe "account_driver_cancel_2"
            }

            Then("it notifies the driver of the real cancellation-fee payout") {
                verify(exactly = 1) {
                    notificationRepository.save(match { it.userId == "driver_user_cancel_2" && it.type == "RIDE_CANCELLATION_FEE_PAID" })
                }
            }
        }
    }

    Given("a driver with a real, statistically meaningful low acceptance rate") {
        val rideDriverRepository = mockk<RideDriverRepository>()
        val rideTripRepository = mockk<RideTripRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        every { notificationRepository.save(any()) } answers { firstArg() }
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = newService(
            rideDriverRepository = rideDriverRepository, rideTripRepository = rideTripRepository,
            accountRepository = accountRepository, ledgerService = ledgerService, notificationRepository = notificationRepository,
            pushNotificationService = pushNotificationService,
        )

        val passengerAccount = Account(
            id = "account_passenger_6", userId = "passenger_6", accountNumber = "1000000006", accountName = "Passenger",
            type = AccountType.MAIN, balance = BigDecimal("20000"), availableBalance = BigDecimal("20000"),
        )
        // Real acceptance rate 1/10 = 10%, well under the real 30% floor, with a real
        // statistically meaningful sample (10 >= MIN_OFFERS_FOR_ACCEPTANCE_FILTER).
        val flakyCloseDriver = RideDriver(id = "driver_flaky", userId = "driver_user_flaky", accountId = "account_flaky", available = true, currentLatitude = -1.9536, currentLongitude = 30.0605, totalOffers = 10, totalAccepted = 1, licenseNumber = "LIC-TEST")
        val reliableFarDriver = RideDriver(id = "driver_reliable", userId = "driver_user_reliable", accountId = "account_reliable", available = true, currentLatitude = -1.9600, currentLongitude = 30.0900, totalOffers = 10, totalAccepted = 9, licenseNumber = "LIC-TEST")

        every { accountRepository.findByUserIdAndType("passenger_6", AccountType.MAIN) } returns passengerAccount
        every { rideDriverRepository.findByAvailableTrueAndCurrentLatitudeIsNotNullAndCurrentLongitudeIsNotNull() } returns listOf(flakyCloseDriver, reliableFarDriver)
        every { rideTripRepository.findDistinctDriverIdsByStatusIn(any()) } returns emptyList()
        every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_2", emptyList())
        val savedSlot = slot<RideTrip>()
        every { rideTripRepository.save(capture(savedSlot)) } answers { firstArg() }
        every { rideDriverRepository.save(any()) } answers { firstArg() }

        When("a passenger requests a trip and the closest driver has a real low acceptance rate") {
            service.requestTrip("passenger_6", "A", -1.9536, 30.0605, "B", -1.9506, 30.0925)

            Then("real dispatch skips the closer but unreliable driver and offers the farther, reliable one instead") {
                savedSlot.captured.offeredDriverId shouldBe "driver_reliable"
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
