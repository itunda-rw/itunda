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
import rw.itunda.core.domain.RideDriver
import rw.itunda.core.domain.RideTrip
import rw.itunda.core.domain.RideTripStatus
import rw.itunda.core.domain.RideTripStop
import rw.itunda.core.domain.Wallet
import rw.itunda.core.domain.WalletType
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
    ) = RideTripService(
        rideDriverRepository, rideTripRepository, rideTripStopRepository, walletRepository, ledgerService,
        transactionRepository, notificationRepository, rateLimiter, pushNotificationService,
    )

    Given("a real passenger with sufficient balance and one real nearby driver") {
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
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
