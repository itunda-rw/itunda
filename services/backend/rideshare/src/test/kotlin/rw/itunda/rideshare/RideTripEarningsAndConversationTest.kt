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

// Real fix (2026-08-26): split out of RideTripServiceTest.kt once that file grew
// past its file-size-lint baseline. Both Given blocks were already fully self-
// contained (own fresh mocks + own newService() call, no shared spec-level
// state) -- a clean, zero-risk test-file split by concern. newService() itself
// duplicated (same small factory-with-defaults helper, same pattern as the
// original file) since Kotest spec-local functions can't be shared across files.
class RideTripEarningsAndConversationTest : BehaviorSpec({

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

    Given("a real driver with completed trips spread across two days") {
        val rideDriverRepository = mockk<RideDriverRepository>()
        val rideTripRepository = mockk<RideTripRepository>()
        val service = newService(rideDriverRepository = rideDriverRepository, rideTripRepository = rideTripRepository)

        val driver = RideDriver(id = "driver_earnings", userId = "driver_user_earnings", accountId = "account_earnings")
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
            id = "driver_share", userId = "driver_user_share", accountId = "account_share",
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

        val driver = RideDriver(id = "driver_tip", userId = "driver_user_tip", accountId = "account_driver_tip")
        val driverAccount = Account(
            id = "account_driver_tip", userId = "driver_user_tip", accountNumber = "1000000002", accountName = "Driver",
            type = AccountType.MAIN, balance = BigDecimal("5000"), availableBalance = BigDecimal("5000"),
        )
        val passengerAccount = Account(
            id = "account_passenger_tip", userId = "passenger_tip", accountNumber = "1000000003", accountName = "Passenger",
            type = AccountType.MAIN, balance = BigDecimal("10000"), availableBalance = BigDecimal("10000"),
        )
        val trip = RideTrip(
            id = "ride_trip_tip_1", passengerId = "passenger_tip", driverId = "driver_tip",
            pickupAddress = "Kigali Center", pickupLatitude = -1.9536, pickupLongitude = 30.0605,
            dropoffAddress = "Nyamirambo", dropoffLatitude = -1.9700, dropoffLongitude = 30.0450,
            distanceKm = BigDecimal("3.660"),
            fare = BigDecimal("2000"), platformFee = BigDecimal("30"), transactionId = "ledgertxn_tip_1",
            status = RideTripStatus.COMPLETED, updatedAt = java.time.Instant.now(),
        )

        every { rideTripRepository.findByIdForUpdate("ride_trip_tip_1") } returns java.util.Optional.of(trip)
        every { rideDriverRepository.findById("driver_tip") } returns java.util.Optional.of(driver)
        every { accountRepository.findByUserIdAndType("passenger_tip", AccountType.MAIN) } returns passengerAccount
        every { accountRepository.findById("account_driver_tip") } returns java.util.Optional.of(driverAccount)
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

            // Real lost-update regression test (concurrency sweep, §236) -- proves the
            // trip row is actually locked for the check-then-act-then-write on
            // tipAmount, same convention P2pTransferLimitServiceTest already
            // establishes for its own findByIdForUpdate fix.
            Then("it locks the trip row for the check-then-act-then-write on tipAmount") {
                verify(exactly = 1) { rideTripRepository.findByIdForUpdate("ride_trip_tip_1") }
            }
        }

        When("the same trip is tipped a second time") {
            val tipped = trip.also { it.tipAmount = BigDecimal("500"); it.tipTransactionId = "ledgertxn_tip_result" }
            every { rideTripRepository.findByIdForUpdate("ride_trip_tip_1") } returns java.util.Optional.of(tipped)

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
            every { rideTripRepository.findByIdForUpdate("ride_trip_tip_1") } returns java.util.Optional.of(inProgress)

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
            every { rideTripRepository.findByIdForUpdate("ride_trip_tip_1") } returns java.util.Optional.of(stale)

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
