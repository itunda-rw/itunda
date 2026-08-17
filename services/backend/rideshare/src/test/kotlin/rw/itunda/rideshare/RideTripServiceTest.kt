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
import rw.itunda.core.domain.Wallet
import rw.itunda.core.domain.WalletType
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
import rw.itunda.core.repository.WalletRepository
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
        walletRepository: WalletRepository = mockk(),
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
        rideDriverRepository, rideTripRepository, rideTripStopRepository, walletRepository, ledgerService,
        transactionRepository, notificationRepository, rateLimiter, pushNotificationService, messagingService, fraudRuleEngine,
        osrmRoutingClient,
    )

    Given("a real passenger with sufficient balance and one real nearby driver") {
        val rideDriverRepository = mockk<RideDriverRepository>()
        val rideTripRepository = mockk<RideTripRepository>()
        val walletRepository = mockk<WalletRepository>()
        val ledgerService = mockk<LedgerService>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        every { notificationRepository.save(any()) } answers { firstArg() }
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val fraudRuleEngine = mockk<rw.itunda.core.fraud.FraudRuleEngine>(relaxed = true)
        val service = newService(
            rideDriverRepository = rideDriverRepository, rideTripRepository = rideTripRepository,
            walletRepository = walletRepository, ledgerService = ledgerService, notificationRepository = notificationRepository,
            pushNotificationService = pushNotificationService, fraudRuleEngine = fraudRuleEngine,
        )

        val passengerWallet = Wallet(
            id = "wallet_passenger", userId = "passenger_1", accountNumber = "1000000001", accountName = "Passenger",
            type = WalletType.MAIN, balance = BigDecimal("20000"), availableBalance = BigDecimal("20000"),
        )
        val nearbyDriver = RideDriver(id = "driver_1", userId = "driver_user_1", walletId = "wallet_driver", available = true, currentLatitude = -1.9536, currentLongitude = 30.0605)

        every { walletRepository.findByUserIdAndType("passenger_1", WalletType.MAIN) } returns passengerWallet
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
        val walletRepository = mockk<WalletRepository>()
        val ledgerService = mockk<LedgerService>()
        val osrmRoutingClient = mockk<OsrmRoutingClient>()
        val service = newService(
            rideDriverRepository = rideDriverRepository, rideTripRepository = rideTripRepository,
            walletRepository = walletRepository, ledgerService = ledgerService, osrmRoutingClient = osrmRoutingClient,
        )

        val passengerWallet = Wallet(
            id = "wallet_passenger", userId = "passenger_1", accountNumber = "1000000001", accountName = "Passenger",
            type = WalletType.MAIN, balance = BigDecimal("20000"), availableBalance = BigDecimal("20000"),
        )
        every { walletRepository.findByUserIdAndType("passenger_1", WalletType.MAIN) } returns passengerWallet
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

    Given("a real driver with an active Uber-style Destination Filter") {
        val rideDriverRepository = mockk<RideDriverRepository>()
        val rideTripRepository = mockk<RideTripRepository>()
        val walletRepository = mockk<WalletRepository>()
        val ledgerService = mockk<LedgerService>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = newService(
            rideDriverRepository = rideDriverRepository, rideTripRepository = rideTripRepository,
            walletRepository = walletRepository, ledgerService = ledgerService, notificationRepository = notificationRepository,
            pushNotificationService = pushNotificationService,
        )

        val passengerWallet = Wallet(
            id = "wallet_passenger", userId = "passenger_1", accountNumber = "1000000001", accountName = "Passenger",
            type = WalletType.MAIN, balance = BigDecimal("20000"), availableBalance = BigDecimal("20000"),
        )
        // Real destination far east of the driver's current position -- a dropoff also
        // east of the driver moves them genuinely closer; a dropoff west moves them
        // genuinely further, same real haversine-distance-reduction check
        // RideTripService.rankNearbyDrivers's own doc comment describes.
        val filteredDriver = RideDriver(
            id = "driver_filtered", userId = "driver_user_filtered", walletId = "wallet_driver_filtered",
            available = true, currentLatitude = -1.9536, currentLongitude = 30.0605,
            destinationLatitude = -1.9300, destinationLongitude = 30.1300,
        )

        every { walletRepository.findByUserIdAndType("passenger_1", WalletType.MAIN) } returns passengerWallet
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
        val walletRepository = mockk<WalletRepository>()
        val service = newService(rideDriverRepository = rideDriverRepository, rideTripRepository = rideTripRepository, walletRepository = walletRepository)

        val poorWallet = Wallet(
            id = "wallet_poor", userId = "passenger_2", accountNumber = "1000000002", accountName = "Passenger",
            type = WalletType.MAIN, balance = BigDecimal("100"), availableBalance = BigDecimal("100"),
        )
        every { walletRepository.findByUserIdAndType("passenger_2", WalletType.MAIN) } returns poorWallet

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
        val walletRepository = mockk<WalletRepository>()
        val ledgerService = mockk<LedgerService>()
        val service = newService(
            rideDriverRepository = rideDriverRepository, rideTripRepository = rideTripRepository,
            rideTripStopRepository = rideTripStopRepository, walletRepository = walletRepository, ledgerService = ledgerService,
        )

        val passengerWallet = Wallet(
            id = "wallet_passenger_6", userId = "passenger_6", accountNumber = "1000000006", accountName = "Passenger",
            type = WalletType.MAIN, balance = BigDecimal("20000"), availableBalance = BigDecimal("20000"),
        )
        every { walletRepository.findByUserIdAndType("passenger_6", WalletType.MAIN) } returns passengerWallet
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

        val driver = RideDriver(id = "driver_6", userId = "driver_user_6", walletId = "wallet_driver_6", available = true)
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
        val walletRepository = mockk<WalletRepository>()
        val ledgerService = mockk<LedgerService>()
        val service = newService(
            rideDriverRepository = rideDriverRepository, rideTripRepository = rideTripRepository,
            walletRepository = walletRepository, ledgerService = ledgerService,
        )

        val passengerWallet = Wallet(
            id = "wallet_passenger_4", userId = "passenger_4", accountNumber = "1000000004", accountName = "Passenger",
            type = WalletType.MAIN, balance = BigDecimal("20000"), availableBalance = BigDecimal("20000"),
        )
        every { walletRepository.findByUserIdAndType("passenger_4", WalletType.MAIN) } returns passengerWallet
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

        val nearbyDriver = RideDriver(id = "driver_5", userId = "driver_user_5", walletId = "wallet_driver_5", available = true, currentLatitude = -1.9536, currentLongitude = 30.0605)
        val dueTrip = RideTrip(
            id = "ride_trip_due", passengerId = "passenger_5", pickupAddress = "A", pickupLatitude = -1.9536, pickupLongitude = 30.0605,
            dropoffAddress = "B", dropoffLatitude = -1.9506, dropoffLongitude = 30.0925, distanceKm = BigDecimal("3.5"),
            fare = BigDecimal("1875"), platformFee = BigDecimal("28.13"), transactionId = "txn_due",
            status = RideTripStatus.REQUESTED, scheduledFor = Instant.now(),
        )

        every { rideDriverRepository.findByAvailableTrueAndCurrentLatitudeIsNotNullAndCurrentLongitudeIsNotNull() } returns listOf(nearbyDriver)
        every { rideTripRepository.findDistinctDriverIdsByStatusIn(any()) } returns emptyList()
        val savedSlot = slot<RideTrip>()
        every { rideTripRepository.save(capture(savedSlot)) } answers { firstArg() }
        every { rideDriverRepository.save(any()) } answers { firstArg() }

        When("the scheduler activates it") {
            service.activateScheduledDispatch(listOf(dueTrip))

            Then("it marks real dispatch as started, exactly once, and offers the trip to the one real nearby driver") {
                dueTrip.scheduledDispatchStartedAt shouldNotBe null
                savedSlot.captured.offeredDriverId shouldBe "driver_5"
            }
        }
    }

    Given("a driver who's already carrying a real active trip") {
        val rideDriverRepository = mockk<RideDriverRepository>()
        val rideTripRepository = mockk<RideTripRepository>()
        val service = newService(rideDriverRepository = rideDriverRepository, rideTripRepository = rideTripRepository)

        val driver = RideDriver(id = "driver_2", userId = "driver_user_2", walletId = "wallet_driver_2", available = true)
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
        val walletRepository = mockk<WalletRepository>()
        val ledgerService = mockk<LedgerService>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        every { notificationRepository.save(any()) } answers { firstArg() }
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = newService(
            rideDriverRepository = rideDriverRepository, rideTripRepository = rideTripRepository,
            walletRepository = walletRepository, ledgerService = ledgerService, notificationRepository = notificationRepository,
            pushNotificationService = pushNotificationService,
        )

        val driver = RideDriver(id = "driver_3", userId = "driver_user_3", walletId = "wallet_driver_3", available = true)
        val driverWallet = Wallet(
            id = "wallet_driver_3", userId = "driver_user_3", accountNumber = "1000000003", accountName = "Driver",
            type = WalletType.MAIN, balance = BigDecimal("0"), availableBalance = BigDecimal("0"),
        )
        val trip = RideTrip(
            id = "ride_trip_2", passengerId = "passenger_4", driverId = "driver_3", pickupAddress = "A", pickupLatitude = -1.95,
            pickupLongitude = 30.06, dropoffAddress = "B", dropoffLatitude = -1.96, dropoffLongitude = 30.09,
            distanceKm = BigDecimal("4.0"), fare = BigDecimal("2000"), platformFee = BigDecimal("30"),
            transactionId = "ledgertxn_y", status = RideTripStatus.IN_PROGRESS,
        )
        every { rideDriverRepository.findByUserId("driver_user_3") } returns driver
        every { rideTripRepository.findById("ride_trip_2") } returns Optional.of(trip)
        every { walletRepository.findById("wallet_driver_3") } returns Optional.of(driverWallet)
        val legsSlot = slot<List<rw.itunda.core.ledger.LedgerLeg>>()
        every { ledgerService.postLedgerTransaction(any(), capture(legsSlot)) } returns LedgerPostResult("ledgertxn_payout", emptyList())
        every { rideTripRepository.save(any()) } answers { firstArg() }

        When("the driver completes it") {
            val result = service.completeTrip("driver_user_3", "ride_trip_2")

            Then("it real-releases the fare from escrow, net of the platform fee, to the driver") {
                result.status shouldBe RideTripStatus.COMPLETED
                result.payoutTransactionId shouldBe "ledgertxn_payout"
                val netLeg = legsSlot.captured.find { it.accountId == "wallet_driver_3" }
                netLeg?.amount shouldBe BigDecimal("1970")
            }
        }
    }

    Given("a real DRIVER_ASSIGNED trip with a real PIN, a driver starting it") {
        val rideDriverRepository = mockk<RideDriverRepository>()
        val rideTripRepository = mockk<RideTripRepository>()
        val service = newService(rideDriverRepository = rideDriverRepository, rideTripRepository = rideTripRepository)

        val driver = RideDriver(id = "driver_4", userId = "driver_user_4", walletId = "wallet_driver_4", available = true)
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
        val walletRepository = mockk<WalletRepository>()
        val ledgerService = mockk<LedgerService>()
        val service = newService(rideTripRepository = rideTripRepository, walletRepository = walletRepository, ledgerService = ledgerService)

        val passengerWallet = Wallet(
            id = "wallet_passenger_5", userId = "passenger_5", accountNumber = "1000000005", accountName = "Passenger",
            type = WalletType.MAIN, balance = BigDecimal("5000"), availableBalance = BigDecimal("5000"),
        )
        val trip = RideTrip(
            id = "ride_trip_3", passengerId = "passenger_5", pickupAddress = "A", pickupLatitude = -1.95, pickupLongitude = 30.06,
            dropoffAddress = "B", dropoffLatitude = -1.96, dropoffLongitude = 30.09, distanceKm = BigDecimal("2.0"),
            fare = BigDecimal("1500"), platformFee = BigDecimal("22.5"), transactionId = "ledgertxn_z", status = RideTripStatus.REQUESTED,
        )
        every { rideTripRepository.findById("ride_trip_3") } returns Optional.of(trip)
        every { walletRepository.findByUserIdAndType("passenger_5", WalletType.MAIN) } returns passengerWallet
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
        val walletRepository = mockk<WalletRepository>()
        val ledgerService = mockk<LedgerService>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        every { notificationRepository.save(any()) } answers { firstArg() }
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = newService(
            rideDriverRepository = rideDriverRepository, rideTripRepository = rideTripRepository,
            walletRepository = walletRepository, ledgerService = ledgerService, notificationRepository = notificationRepository,
            pushNotificationService = pushNotificationService,
        )

        val driver = RideDriver(id = "driver_cancel_1", userId = "driver_user_cancel_1", walletId = "wallet_driver_cancel_1")
        val driverWallet = Wallet(
            id = "wallet_driver_cancel_1", userId = "driver_user_cancel_1", accountNumber = "1000000010", accountName = "Driver",
            type = WalletType.MAIN, balance = BigDecimal("5000"), availableBalance = BigDecimal("5000"),
        )
        val passengerWallet = Wallet(
            id = "wallet_passenger_cancel_1", userId = "passenger_cancel_1", accountNumber = "1000000011", accountName = "Passenger",
            type = WalletType.MAIN, balance = BigDecimal("10000"), availableBalance = BigDecimal("10000"),
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
        every { walletRepository.findByUserIdAndType("passenger_cancel_1", WalletType.MAIN) } returns passengerWallet
        every { walletRepository.findById("wallet_driver_cancel_1") } returns Optional.of(driverWallet)
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
        val walletRepository = mockk<WalletRepository>()
        val ledgerService = mockk<LedgerService>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        every { notificationRepository.save(any()) } answers { firstArg() }
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = newService(
            rideDriverRepository = rideDriverRepository, rideTripRepository = rideTripRepository,
            walletRepository = walletRepository, ledgerService = ledgerService, notificationRepository = notificationRepository,
            pushNotificationService = pushNotificationService,
        )

        val driver = RideDriver(id = "driver_cancel_2", userId = "driver_user_cancel_2", walletId = "wallet_driver_cancel_2")
        val driverWallet = Wallet(
            id = "wallet_driver_cancel_2", userId = "driver_user_cancel_2", accountNumber = "1000000012", accountName = "Driver",
            type = WalletType.MAIN, balance = BigDecimal("5000"), availableBalance = BigDecimal("5000"),
        )
        val passengerWallet = Wallet(
            id = "wallet_passenger_cancel_2", userId = "passenger_cancel_2", accountNumber = "1000000013", accountName = "Passenger",
            type = WalletType.MAIN, balance = BigDecimal("10000"), availableBalance = BigDecimal("10000"),
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
        every { walletRepository.findByUserIdAndType("passenger_cancel_2", WalletType.MAIN) } returns passengerWallet
        every { walletRepository.findById("wallet_driver_cancel_2") } returns Optional.of(driverWallet)
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
                legs.captured[2].accountId shouldBe "wallet_driver_cancel_2"
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
        val walletRepository = mockk<WalletRepository>()
        val ledgerService = mockk<LedgerService>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        every { notificationRepository.save(any()) } answers { firstArg() }
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = newService(
            rideDriverRepository = rideDriverRepository, rideTripRepository = rideTripRepository,
            walletRepository = walletRepository, ledgerService = ledgerService, notificationRepository = notificationRepository,
            pushNotificationService = pushNotificationService,
        )

        val passengerWallet = Wallet(
            id = "wallet_passenger_6", userId = "passenger_6", accountNumber = "1000000006", accountName = "Passenger",
            type = WalletType.MAIN, balance = BigDecimal("20000"), availableBalance = BigDecimal("20000"),
        )
        // Real acceptance rate 1/10 = 10%, well under the real 30% floor, with a real
        // statistically meaningful sample (10 >= MIN_OFFERS_FOR_ACCEPTANCE_FILTER).
        val flakyCloseDriver = RideDriver(id = "driver_flaky", userId = "driver_user_flaky", walletId = "wallet_flaky", available = true, currentLatitude = -1.9536, currentLongitude = 30.0605, totalOffers = 10, totalAccepted = 1)
        val reliableFarDriver = RideDriver(id = "driver_reliable", userId = "driver_user_reliable", walletId = "wallet_reliable", available = true, currentLatitude = -1.9600, currentLongitude = 30.0900, totalOffers = 10, totalAccepted = 9)

        every { walletRepository.findByUserIdAndType("passenger_6", WalletType.MAIN) } returns passengerWallet
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

    Given("a real driver with completed trips spread across two days") {
        val rideDriverRepository = mockk<RideDriverRepository>()
        val rideTripRepository = mockk<RideTripRepository>()
        val service = newService(rideDriverRepository = rideDriverRepository, rideTripRepository = rideTripRepository)

        val driver = RideDriver(id = "driver_earnings", userId = "driver_user_earnings", walletId = "wallet_earnings")
        every { rideDriverRepository.findByUserId("driver_user_earnings") } returns driver

        fun trip(id: String, day: java.time.LocalDate, fare: String, fee: String) = RideTrip(
            id = id, passengerId = "passenger_x", driverId = "driver_earnings",
            pickupAddress = "A", pickupLatitude = -1.9536, pickupLongitude = 30.0605,
            dropoffAddress = "B", dropoffLatitude = -1.9506, dropoffLongitude = 30.0925,
            distanceKm = BigDecimal("5.000"),
            fare = BigDecimal(fare), platformFee = BigDecimal(fee), transactionId = "ledgertxn_$id",
            status = RideTripStatus.COMPLETED, createdAt = day.atTime(9, 0).toInstant(java.time.ZoneOffset.UTC),
        )

        val day1 = java.time.LocalDate.of(2026, 7, 10)
        val day2 = java.time.LocalDate.of(2026, 7, 11)
        every {
            rideTripRepository.findByDriverIdAndStatusAndCreatedAtBetween("driver_earnings", RideTripStatus.COMPLETED, any(), any())
        } returns listOf(
            trip("t1", day1, "3000", "45.00"),
            trip("t2", day1, "2000", "30.00"),
            trip("t3", day2, "5000", "75.00"),
        )

        When("requesting the earnings report for that range") {
            val report = service.getMyEarnings("driver_user_earnings", day1, day2)

            Then("it groups by day with correct real net-of-platform-fee totals") {
                report.size shouldBe 2

                val reportDay1 = report.first { it.date == day1 }
                reportDay1.tripCount shouldBe 2
                reportDay1.grossFare shouldBe BigDecimal("5000")
                reportDay1.platformFees shouldBe BigDecimal("75.00")
                reportDay1.netEarnings shouldBe BigDecimal("4925.00")

                val reportDay2 = report.first { it.date == day2 }
                reportDay2.tripCount shouldBe 1
                reportDay2.grossFare shouldBe BigDecimal("5000")
                reportDay2.platformFees shouldBe BigDecimal("75.00")
                reportDay2.netEarnings shouldBe BigDecimal("4925.00")
            }
        }

        When("requesting an earnings report for an account that isn't a driver") {
            every { rideDriverRepository.findByUserId("not_a_driver") } returns null

            Then("it throws RideDriverNotRegisteredException before ever querying trips") {
                try {
                    service.getMyEarnings("not_a_driver", day1, day2)
                    error("expected RideDriverNotRegisteredException")
                } catch (e: RideDriverNotRegisteredException) {
                    verify(exactly = 0) { rideTripRepository.findByDriverIdAndStatusAndCreatedAtBetween(any(), any(), any(), any()) }
                }
            }
        }

        When("requesting an earnings report with an inverted date range") {
            Then("it rejects the request before querying trips") {
                try {
                    service.getMyEarnings("driver_user_earnings", day2, day1)
                    error("expected InvalidEarningsRangeException")
                } catch (e: InvalidEarningsRangeException) {
                    verify(exactly = 0) { rideTripRepository.findByDriverIdAndStatusAndCreatedAtBetween(any(), any(), any(), any()) }
                }
            }
        }

        When("requesting more than the supported 31-day earnings window") {
            Then("it rejects the request before querying trips") {
                try {
                    service.getMyEarnings("driver_user_earnings", day1, day1.plusDays(31))
                    error("expected InvalidEarningsRangeException")
                } catch (e: InvalidEarningsRangeException) {
                    verify(exactly = 0) { rideTripRepository.findByDriverIdAndStatusAndCreatedAtBetween(any(), any(), any(), any()) }
                }
            }
        }
    }

    Given("a real completed trip with a driver whose location is known, and a real Talk conversation") {
        val rideDriverRepository = mockk<RideDriverRepository>()
        val rideTripRepository = mockk<RideTripRepository>()
        val messagingService = mockk<MessagingService>()
        val service = newService(
            rideDriverRepository = rideDriverRepository, rideTripRepository = rideTripRepository,
            messagingService = messagingService,
        )

        val trip = RideTrip(
            id = "ride_trip_share_1", passengerId = "passenger_share", driverId = "driver_share",
            pickupAddress = "Kigali Center", pickupLatitude = -1.9536, pickupLongitude = 30.0605,
            dropoffAddress = "Nyamirambo", dropoffLatitude = -1.9700, dropoffLongitude = 30.0450,
            distanceKm = BigDecimal("3.660"),
            fare = BigDecimal("2000"), platformFee = BigDecimal("30"), transactionId = "ledgertxn_share_1",
            status = RideTripStatus.IN_PROGRESS,
        )
        val driver = RideDriver(
            id = "driver_share", userId = "driver_user_share", walletId = "wallet_share",
            currentLatitude = -1.9600, currentLongitude = 30.0500,
        )
        every { rideTripRepository.findById("ride_trip_share_1") } returns Optional.of(trip)
        every { rideDriverRepository.findById("driver_share") } returns Optional.of(driver)

        When("the real passenger shares it into a real conversation they're a participant of") {
            every { messagingService.getConversationForParticipant("passenger_share", "conv_1") } returns mockk()
            val sentMessage = mockk<Message>()
            val bodySlot = slot<String>()
            every { messagingService.sendMessage("passenger_share", "conv_1", capture(bodySlot)) } returns sentMessage

            val result = service.shareTripStatus("passenger_share", "ride_trip_share_1", "conv_1")

            Then("it sends a real message with the trip's real status, addresses, and the driver's real current location") {
                result shouldBe sentMessage
                bodySlot.captured shouldBe
                    "🚗 My ride status: IN_PROGRESS\nFrom: Kigali Center\nTo: Nyamirambo\n" +
                    "Driver's last known location: -1.96, 30.05"
            }
        }

        When("someone who isn't this trip's real passenger tries to share it") {
            Then("it throws RideTripNotFoundException before ever touching messaging") {
                try {
                    service.shareTripStatus("a_stranger", "ride_trip_share_1", "conv_1")
                    error("expected RideTripNotFoundException")
                } catch (e: RideTripNotFoundException) {
                    verify(exactly = 0) { messagingService.sendMessage(any(), any(), any()) }
                }
            }
        }

        When("the real passenger shares it into a conversation they aren't actually a participant of") {
            every { messagingService.getConversationForParticipant("passenger_share", "conv_2") } throws
                ConversationNotFoundException("Conversation not found")

            Then("the real ConversationNotFoundException propagates and no message is sent") {
                try {
                    service.shareTripStatus("passenger_share", "ride_trip_share_1", "conv_2")
                    error("expected ConversationNotFoundException")
                } catch (e: ConversationNotFoundException) {
                    verify(exactly = 0) { messagingService.sendMessage(any(), any(), any()) }
                }
            }
        }
    }

    Given("a real completed trip, eligible to be tipped") {
        val rideDriverRepository = mockk<RideDriverRepository>()
        val rideTripRepository = mockk<RideTripRepository>()
        val walletRepository = mockk<WalletRepository>()
        val ledgerService = mockk<LedgerService>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        every { notificationRepository.save(any()) } answers { firstArg() }
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = newService(
            rideDriverRepository = rideDriverRepository, rideTripRepository = rideTripRepository,
            walletRepository = walletRepository, ledgerService = ledgerService, notificationRepository = notificationRepository,
            pushNotificationService = pushNotificationService,
        )

        val driver = RideDriver(id = "driver_tip", userId = "driver_user_tip", walletId = "wallet_driver_tip")
        val driverWallet = Wallet(
            id = "wallet_driver_tip", userId = "driver_user_tip", accountNumber = "1000000002", accountName = "Driver",
            type = WalletType.MAIN, balance = BigDecimal("5000"), availableBalance = BigDecimal("5000"),
        )
        val passengerWallet = Wallet(
            id = "wallet_passenger_tip", userId = "passenger_tip", accountNumber = "1000000003", accountName = "Passenger",
            type = WalletType.MAIN, balance = BigDecimal("10000"), availableBalance = BigDecimal("10000"),
        )
        val trip = RideTrip(
            id = "ride_trip_tip_1", passengerId = "passenger_tip", driverId = "driver_tip",
            pickupAddress = "Kigali Center", pickupLatitude = -1.9536, pickupLongitude = 30.0605,
            dropoffAddress = "Nyamirambo", dropoffLatitude = -1.9700, dropoffLongitude = 30.0450,
            distanceKm = BigDecimal("3.660"),
            fare = BigDecimal("2000"), platformFee = BigDecimal("30"), transactionId = "ledgertxn_tip_1",
            status = RideTripStatus.COMPLETED, updatedAt = java.time.Instant.now(),
        )

        every { rideTripRepository.findById("ride_trip_tip_1") } returns java.util.Optional.of(trip)
        every { rideDriverRepository.findById("driver_tip") } returns java.util.Optional.of(driver)
        every { walletRepository.findByUserIdAndType("passenger_tip", WalletType.MAIN) } returns passengerWallet
        every { walletRepository.findById("wallet_driver_tip") } returns java.util.Optional.of(driverWallet)
        every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_tip_result", emptyList())
        every { rideTripRepository.save(any()) } answers { firstArg() }

        When("the real passenger tips the driver") {
            val result = service.tipDriver("passenger_tip", "ride_trip_tip_1", BigDecimal("500"))

            Then("it records the real tip amount and a real transaction id, no platform fee leg") {
                result.tipAmount shouldBe BigDecimal("500")
                result.tipTransactionId shouldBe "ledgertxn_tip_result"
                verify(exactly = 1) {
                    ledgerService.postLedgerTransaction(
                        any(),
                        match { legs -> legs.size == 2 && legs.none { it.accountType == rw.itunda.core.domain.LedgerAccountType.FEE_REVENUE } },
                    )
                }
            }

            Then("it notifies the real driver they received a tip") {
                verify(exactly = 1) { notificationRepository.save(match { it.type == "RIDE_TIP_RECEIVED" && it.userId == "driver_user_tip" }) }
            }
        }

        When("the same trip is tipped a second time") {
            val tipped = trip.also { it.tipAmount = BigDecimal("500"); it.tipTransactionId = "ledgertxn_tip_result" }
            every { rideTripRepository.findById("ride_trip_tip_1") } returns java.util.Optional.of(tipped)

            Then("it throws RideTripAlreadyTippedException before touching the ledger") {
                try {
                    service.tipDriver("passenger_tip", "ride_trip_tip_1", BigDecimal("500"))
                    error("expected RideTripAlreadyTippedException")
                } catch (e: RideTripAlreadyTippedException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }

        When("a trip that isn't COMPLETED yet is tipped") {
            val inProgress = RideTrip(
                id = "ride_trip_tip_1", passengerId = "passenger_tip", driverId = "driver_tip",
                pickupAddress = "Kigali Center", pickupLatitude = -1.9536, pickupLongitude = 30.0605,
                dropoffAddress = "Nyamirambo", dropoffLatitude = -1.9700, dropoffLongitude = 30.0450,
                distanceKm = BigDecimal("3.660"),
                fare = BigDecimal("2000"), platformFee = BigDecimal("30"), transactionId = "ledgertxn_tip_1",
                status = RideTripStatus.IN_PROGRESS,
            )
            every { rideTripRepository.findById("ride_trip_tip_1") } returns java.util.Optional.of(inProgress)

            Then("it throws RideTripNotCompletedException before touching the ledger") {
                try {
                    service.tipDriver("passenger_tip", "ride_trip_tip_1", BigDecimal("500"))
                    error("expected RideTripNotCompletedException")
                } catch (e: RideTripNotCompletedException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }

        When("someone who isn't this trip's real passenger tries to tip") {
            Then("it throws RideTripNotFoundException before touching the ledger") {
                try {
                    service.tipDriver("a_stranger", "ride_trip_tip_1", BigDecimal("500"))
                    error("expected RideTripNotFoundException")
                } catch (e: RideTripNotFoundException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }

        When("the real passenger tips more than 30 days after real trip completion") {
            val stale = RideTrip(
                id = "ride_trip_tip_1", passengerId = "passenger_tip", driverId = "driver_tip",
                pickupAddress = "Kigali Center", pickupLatitude = -1.9536, pickupLongitude = 30.0605,
                dropoffAddress = "Nyamirambo", dropoffLatitude = -1.9700, dropoffLongitude = 30.0450,
                distanceKm = BigDecimal("3.660"),
                fare = BigDecimal("2000"), platformFee = BigDecimal("30"), transactionId = "ledgertxn_tip_1",
                status = RideTripStatus.COMPLETED, updatedAt = java.time.Instant.now().minus(java.time.Duration.ofDays(31)),
            )
            every { rideTripRepository.findById("ride_trip_tip_1") } returns java.util.Optional.of(stale)

            Then("it throws RideTripTipWindowExpiredException before touching the ledger") {
                try {
                    service.tipDriver("passenger_tip", "ride_trip_tip_1", BigDecimal("500"))
                    error("expected RideTripTipWindowExpiredException")
                } catch (e: RideTripTipWindowExpiredException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }

        When("the real passenger tries to tip a non-positive amount") {
            Then("it throws InvalidTipAmountException before touching the ledger") {
                try {
                    service.tipDriver("passenger_tip", "ride_trip_tip_1", BigDecimal.ZERO)
                    error("expected InvalidTipAmountException")
                } catch (e: InvalidTipAmountException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
