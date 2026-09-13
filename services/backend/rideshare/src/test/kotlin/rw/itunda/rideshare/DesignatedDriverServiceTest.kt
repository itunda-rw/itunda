package rw.itunda.rideshare

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.DesignatedDriver
import rw.itunda.core.domain.DesignatedDriverTrip
import rw.itunda.core.domain.DesignatedDriverTripStatus
import rw.itunda.core.domain.Account
import rw.itunda.core.domain.AccountType
import rw.itunda.core.fraud.FraudRuleEngine
import rw.itunda.core.ledger.LedgerPostResult
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.DesignatedDriverRepository
import rw.itunda.core.repository.DesignatedDriverTripRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.TransactionRepository
import rw.itunda.core.repository.AccountRepository
import java.math.BigDecimal
import java.util.Optional

/**
 * First test coverage for real Kakao T 대리운전 (designated driver) -- see
 * DesignatedDriverService's own doc comment for the full sourced account. Mirrors
 * RideTripServiceTest's/VehicleInspectionServiceTest's own established mocking
 * conventions for this codebase's escrow-holding shape.
 */
class DesignatedDriverServiceTest : BehaviorSpec({

    fun newService(
        designatedDriverRepository: DesignatedDriverRepository = mockk(),
        designatedDriverTripRepository: DesignatedDriverTripRepository = mockk(),
        accountRepository: AccountRepository = mockk(),
        ledgerService: LedgerService = mockk(),
        transactionRepository: TransactionRepository = mockk<TransactionRepository>(relaxed = true).also { every { it.save(any()) } answers { firstArg() } },
        rateLimiter: RateLimiter = mockk(relaxed = true),
        fraudRuleEngine: FraudRuleEngine = mockk(relaxed = true),
        notificationRepository: NotificationRepository = mockk(relaxed = true),
        pushNotificationService: PushNotificationService = mockk(relaxed = true),
    ) = DesignatedDriverService(
        designatedDriverRepository, designatedDriverTripRepository, accountRepository, ledgerService, transactionRepository,
        rateLimiter, fraudRuleEngine, notificationRepository, pushNotificationService,
    )

    Given("a fresh account with a real account") {
        val designatedDriverRepository = mockk<DesignatedDriverRepository>()
        val account = Account(
            id = "account_1", userId = "user_1", accountNumber = "1000000001", accountName = "User",
            type = AccountType.MAIN, balance = BigDecimal.ZERO, availableBalance = BigDecimal.ZERO,
        )
        val accountRepository = mockk<AccountRepository>()
        every { accountRepository.findByUserIdAndType("user_1", AccountType.MAIN) } returns account
        val service = newService(designatedDriverRepository = designatedDriverRepository, accountRepository = accountRepository)

        When("registering as a designated driver for the first time") {
            every { designatedDriverRepository.findByUserId("user_1") } returns null
            val savedSlot = slot<DesignatedDriver>()
            every { designatedDriverRepository.save(capture(savedSlot)) } answers { firstArg() }

            val result = service.register("user_1", "DL-12345")

            Then("a real driver row is saved with the account reused as payout destination") {
                result.userId shouldBe "user_1"
                result.accountId shouldBe "account_1"
                result.licenseNumber shouldBe "DL-12345"
                savedSlot.captured.userId shouldBe "user_1"
            }
        }

        When("registering twice for the same account") {
            every { designatedDriverRepository.findByUserId("user_1") } returns
                DesignatedDriver(id = "designated_driver_1", userId = "user_1", accountId = "account_1", licenseNumber = "DL-12345")

            Then("the second registration real-409s") {
                try {
                    service.register("user_1", "DL-99999")
                    throw AssertionError("expected DesignatedDriverAlreadyRegisteredException")
                } catch (e: DesignatedDriverAlreadyRegisteredException) {
                    e.message shouldBe "This account is already registered as a designated driver"
                }
            }
        }
    }

    Given("a real customer with sufficient balance requesting their own car driven home") {
        val designatedDriverTripRepository = mockk<DesignatedDriverTripRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val service = newService(designatedDriverTripRepository = designatedDriverTripRepository, accountRepository = accountRepository, ledgerService = ledgerService, fraudRuleEngine = fraudRuleEngine)

        val customerAccount = Account(
            id = "account_customer", userId = "customer_1", accountNumber = "1000000002", accountName = "Customer",
            type = AccountType.MAIN, balance = BigDecimal("20000"), availableBalance = BigDecimal("20000"),
        )
        every { accountRepository.findByUserIdAndType("customer_1", AccountType.MAIN) } returns customerAccount
        every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_1", emptyList())
        val savedSlot = slot<DesignatedDriverTrip>()
        every { designatedDriverTripRepository.save(capture(savedSlot)) } answers { firstArg() }

        When("the customer requests a real trip") {
            val result = service.requestTrip(
                "customer_1", "Kigali Heights", -1.9536, 30.0605, "Home", -1.9606, 30.0705,
                "Toyota", "RAV4", "RAB 123 A",
            )

            Then("a real REQUESTED trip is created with a real held fare") {
                result.status shouldBe DesignatedDriverTripStatus.REQUESTED
                result.driverId shouldBe null
                result.fare.compareTo(BigDecimal.ZERO) shouldBe 1
                savedSlot.captured.customerId shouldBe "customer_1"
            }

            Then("the real escrow hold is real-evaluated for fraud, same as RideTripService.requestTrip's identical shape") {
                io.mockk.verify(exactly = 1) { fraudRuleEngine.evaluate("customer_1", null, result.fare, "ledgertxn_1") }
            }
        }
    }

    Given("a real driver trying to accept their own requested trip") {
        val designatedDriverRepository = mockk<DesignatedDriverRepository>()
        val designatedDriverTripRepository = mockk<DesignatedDriverTripRepository>()
        val service = newService(designatedDriverRepository = designatedDriverRepository, designatedDriverTripRepository = designatedDriverTripRepository)

        val driver = DesignatedDriver(id = "designated_driver_1", userId = "user_1", accountId = "account_1", licenseNumber = "DL-1", available = true)
        val trip = DesignatedDriverTrip(
            id = "designated_trip_1", customerId = "user_1", pickupAddress = "A", pickupLatitude = -1.9, pickupLongitude = 30.0,
            dropoffAddress = "B", dropoffLatitude = -1.95, dropoffLongitude = 30.05, vehicleMake = "Toyota", vehicleModel = "RAV4",
            vehiclePlate = "RAB 123 A", distanceKm = BigDecimal("5.000"), fare = BigDecimal("4250.00"), platformFee = BigDecimal("63.75"),
            holdTransactionId = "ledgertxn_1",
        )
        every { designatedDriverRepository.findByUserId("user_1") } returns driver
        every { designatedDriverTripRepository.findByIdForUpdate("designated_trip_1") } returns Optional.of(trip)

        When("that same user tries to accept it as a driver") {
            Then("the self-trip block fires") {
                try {
                    service.acceptTrip("user_1", "designated_trip_1")
                    throw AssertionError("expected DesignatedDriverSelfTripException")
                } catch (e: DesignatedDriverSelfTripException) {
                    e.message shouldBe "Cannot accept your own trip"
                }
            }
        }
    }

    // Real gap found live (2026-09-14, sibling-asymmetry sweep): this whole class had
    // zero notification wiring at all, despite its own doc comment claiming to reuse
    // RideTripService's real escrow/payout shape -- that mirroring stopped at the
    // ledger mechanics. First-ever test coverage for a real driver genuinely accepting
    // a real trip.
    Given("a real driver accepting a real customer's requested trip") {
        val designatedDriverRepository = mockk<DesignatedDriverRepository>()
        val designatedDriverTripRepository = mockk<DesignatedDriverTripRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        every { notificationRepository.save(any()) } answers { firstArg() }
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = newService(
            designatedDriverRepository = designatedDriverRepository, designatedDriverTripRepository = designatedDriverTripRepository,
            notificationRepository = notificationRepository, pushNotificationService = pushNotificationService,
        )

        val driver = DesignatedDriver(id = "designated_driver_1", userId = "driver_user_1", accountId = "account_driver", licenseNumber = "DL-1", available = true)
        val trip = DesignatedDriverTrip(
            id = "designated_trip_1", customerId = "customer_1", pickupAddress = "A", pickupLatitude = -1.9, pickupLongitude = 30.0,
            dropoffAddress = "B", dropoffLatitude = -1.95, dropoffLongitude = 30.05, vehicleMake = "Toyota", vehicleModel = "RAV4",
            vehiclePlate = "RAB 123 A", distanceKm = BigDecimal("5.000"), fare = BigDecimal("4250.00"), platformFee = BigDecimal("63.75"),
            holdTransactionId = "ledgertxn_1",
        )
        every { designatedDriverRepository.findByUserId("driver_user_1") } returns driver
        every { designatedDriverTripRepository.findByIdForUpdate("designated_trip_1") } returns Optional.of(trip)
        every { designatedDriverTripRepository.save(any()) } answers { firstArg() }

        When("the driver accepts it") {
            val result = service.acceptTrip("driver_user_1", "designated_trip_1")

            Then("the trip is real-assigned to the real driver") {
                result.status shouldBe DesignatedDriverTripStatus.ACCEPTED
                result.driverId shouldBe "designated_driver_1"
            }

            Then("the real customer is real-notified a driver was assigned") {
                io.mockk.verify(exactly = 1) { notificationRepository.save(match { it.userId == "customer_1" && it.type == "DESIGNATED_DRIVER_TRIP_UPDATE" }) }
                io.mockk.verify(exactly = 1) { pushNotificationService.sendToUser("customer_1", any(), any(), any()) }
            }
        }
    }

    Given("a real ACCEPTED trip a driver is starting to drive") {
        val designatedDriverRepository = mockk<DesignatedDriverRepository>()
        val designatedDriverTripRepository = mockk<DesignatedDriverTripRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        every { notificationRepository.save(any()) } answers { firstArg() }
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = newService(
            designatedDriverRepository = designatedDriverRepository, designatedDriverTripRepository = designatedDriverTripRepository,
            notificationRepository = notificationRepository, pushNotificationService = pushNotificationService,
        )

        val driver = DesignatedDriver(id = "designated_driver_1", userId = "driver_user_1", accountId = "account_driver", licenseNumber = "DL-1", available = true)
        val trip = DesignatedDriverTrip(
            id = "designated_trip_1", customerId = "customer_1", driverId = "designated_driver_1", pickupAddress = "A", pickupLatitude = -1.9,
            pickupLongitude = 30.0, dropoffAddress = "B", dropoffLatitude = -1.95, dropoffLongitude = 30.05, vehicleMake = "Toyota",
            vehicleModel = "RAV4", vehiclePlate = "RAB 123 A", distanceKm = BigDecimal("5.000"), fare = BigDecimal("4250.00"),
            platformFee = BigDecimal("63.75"), holdTransactionId = "ledgertxn_1", status = DesignatedDriverTripStatus.ACCEPTED,
        )
        every { designatedDriverRepository.findByUserId("driver_user_1") } returns driver
        every { designatedDriverTripRepository.findById("designated_trip_1") } returns Optional.of(trip)
        every { designatedDriverTripRepository.save(any()) } answers { firstArg() }

        When("the driver starts driving") {
            val result = service.startDriving("driver_user_1", "designated_trip_1")

            Then("the trip real-advances to DRIVING") {
                result.status shouldBe DesignatedDriverTripStatus.DRIVING
            }

            Then("the real customer is real-notified the trip has started") {
                io.mockk.verify(exactly = 1) { notificationRepository.save(match { it.userId == "customer_1" && it.type == "DESIGNATED_DRIVER_TRIP_UPDATE" }) }
                io.mockk.verify(exactly = 1) { pushNotificationService.sendToUser("customer_1", any(), any(), any()) }
            }
        }
    }

    Given("a real DRIVING trip a driver just completed") {
        val designatedDriverRepository = mockk<DesignatedDriverRepository>()
        val designatedDriverTripRepository = mockk<DesignatedDriverTripRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        every { notificationRepository.save(any()) } answers { firstArg() }
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = newService(
            designatedDriverRepository = designatedDriverRepository, designatedDriverTripRepository = designatedDriverTripRepository,
            accountRepository = accountRepository, ledgerService = ledgerService,
            notificationRepository = notificationRepository, pushNotificationService = pushNotificationService,
        )

        val driver = DesignatedDriver(id = "designated_driver_1", userId = "driver_user_1", accountId = "account_driver", licenseNumber = "DL-1", available = true)
        val driverAccount = Account(
            id = "account_driver", userId = "driver_user_1", accountNumber = "1000000003", accountName = "Driver",
            type = AccountType.MAIN, balance = BigDecimal.ZERO, availableBalance = BigDecimal.ZERO,
        )
        val trip = DesignatedDriverTrip(
            id = "designated_trip_1", customerId = "customer_1", driverId = "designated_driver_1", pickupAddress = "A", pickupLatitude = -1.9,
            pickupLongitude = 30.0, dropoffAddress = "B", dropoffLatitude = -1.95, dropoffLongitude = 30.05, vehicleMake = "Toyota",
            vehicleModel = "RAV4", vehiclePlate = "RAB 123 A", distanceKm = BigDecimal("5.000"), fare = BigDecimal("4250.00"),
            platformFee = BigDecimal("63.75"), holdTransactionId = "ledgertxn_1", status = DesignatedDriverTripStatus.DRIVING,
        )
        every { designatedDriverRepository.findByUserId("driver_user_1") } returns driver
        every { designatedDriverTripRepository.findByIdForUpdate("designated_trip_1") } returns Optional.of(trip)
        every { accountRepository.findById("account_driver") } returns Optional.of(driverAccount)
        every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_2", emptyList())
        every { designatedDriverTripRepository.save(any()) } answers { firstArg() }

        When("the driver completes the trip") {
            val result = service.completeTrip("driver_user_1", "designated_trip_1")

            Then("a real fare payout posts and the trip is marked COMPLETED") {
                result.status shouldBe DesignatedDriverTripStatus.COMPLETED
                result.payoutTransactionId shouldBe "ledgertxn_2"
            }

            // Real fix (concurrency audit, 2026-08-21): proves completeTrip actually
            // locks the row rather than the shared unlocked getOwnedTrip path.
            Then("it real-locks the trip row before releasing the fare, same discipline GroupEatsOrderService.finalizeOrder already establishes") {
                io.mockk.verify(exactly = 1) { designatedDriverTripRepository.findByIdForUpdate("designated_trip_1") }
            }

            Then("the real customer is real-notified the trip completed") {
                io.mockk.verify(exactly = 1) { notificationRepository.save(match { it.userId == "customer_1" && it.type == "DESIGNATED_DRIVER_TRIP_UPDATE" }) }
                io.mockk.verify(exactly = 1) { pushNotificationService.sendToUser("customer_1", any(), any(), any()) }
            }
        }
    }

    Given("a real REQUESTED trip the customer wants to withdraw") {
        val designatedDriverTripRepository = mockk<DesignatedDriverTripRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val service = newService(designatedDriverTripRepository = designatedDriverTripRepository, accountRepository = accountRepository, ledgerService = ledgerService)

        val customerAccount = Account(
            id = "account_customer", userId = "customer_1", accountNumber = "1000000002", accountName = "Customer",
            type = AccountType.MAIN, balance = BigDecimal("20000"), availableBalance = BigDecimal("20000"),
        )
        val trip = DesignatedDriverTrip(
            id = "designated_trip_1", customerId = "customer_1", pickupAddress = "A", pickupLatitude = -1.9, pickupLongitude = 30.0,
            dropoffAddress = "B", dropoffLatitude = -1.95, dropoffLongitude = 30.05, vehicleMake = "Toyota", vehicleModel = "RAV4",
            vehiclePlate = "RAB 123 A", distanceKm = BigDecimal("5.000"), fare = BigDecimal("4250.00"), platformFee = BigDecimal("63.75"),
            holdTransactionId = "ledgertxn_1",
        )
        every { designatedDriverTripRepository.findByIdForUpdate("designated_trip_1") } returns Optional.of(trip)
        every { accountRepository.findByUserIdAndType("customer_1", AccountType.MAIN) } returns customerAccount
        every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_refund", emptyList())
        every { designatedDriverTripRepository.save(any()) } answers { firstArg() }

        When("the customer cancels before any driver accepts") {
            val result = service.cancelTrip("customer_1", "designated_trip_1")

            Then("a real full refund posts and the trip is marked CANCELLED") {
                result.status shouldBe DesignatedDriverTripStatus.CANCELLED
                result.refundTransactionId shouldBe "ledgertxn_refund"
            }
        }
    }

    // Real gap fix (2026-08-18): before this, an ACCEPTED trip had no cancel path at
    // all. These three cases prove the ported RideTripService cancellation-fee policy:
    // still a full refund inside the grace period (positive control -- the fix doesn't
    // over-charge a customer who cancels quickly), a real fee carved out and paid to
    // the driver past the grace period (the actual gap being closed), and DRIVING
    // still correctly rejected (the fix doesn't over-widen the cancel window).
    Given("a real ACCEPTED trip within the cancellation-fee grace period") {
        val designatedDriverRepository = mockk<DesignatedDriverRepository>()
        val designatedDriverTripRepository = mockk<DesignatedDriverTripRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        every { notificationRepository.save(any()) } answers { firstArg() }
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = newService(
            designatedDriverRepository = designatedDriverRepository, designatedDriverTripRepository = designatedDriverTripRepository,
            accountRepository = accountRepository, ledgerService = ledgerService,
            notificationRepository = notificationRepository, pushNotificationService = pushNotificationService,
        )

        val customerAccount = Account(
            id = "account_customer", userId = "customer_1", accountNumber = "1000000002", accountName = "Customer",
            type = AccountType.MAIN, balance = BigDecimal("20000"), availableBalance = BigDecimal("20000"),
        )
        val driver = DesignatedDriver(id = "designated_driver_1", userId = "driver_user_1", accountId = "account_driver", licenseNumber = "DL-1")
        val trip = DesignatedDriverTrip(
            id = "designated_trip_1", customerId = "customer_1", driverId = "designated_driver_1", pickupAddress = "A", pickupLatitude = -1.9,
            pickupLongitude = 30.0, dropoffAddress = "B", dropoffLatitude = -1.95, dropoffLongitude = 30.05, vehicleMake = "Toyota",
            vehicleModel = "RAV4", vehiclePlate = "RAB 123 A", distanceKm = BigDecimal("5.000"), fare = BigDecimal("4250.00"),
            platformFee = BigDecimal("63.75"), holdTransactionId = "ledgertxn_1", status = DesignatedDriverTripStatus.ACCEPTED,
            driverAcceptedAt = java.time.Instant.now(),
        )
        every { designatedDriverTripRepository.findByIdForUpdate("designated_trip_1") } returns Optional.of(trip)
        every { accountRepository.findByUserIdAndType("customer_1", AccountType.MAIN) } returns customerAccount
        every { designatedDriverRepository.findById("designated_driver_1") } returns Optional.of(driver)
        val legsSlot = slot<List<rw.itunda.core.ledger.LedgerLeg>>()
        every { ledgerService.postLedgerTransaction(any(), capture(legsSlot)) } returns LedgerPostResult("ledgertxn_refund", emptyList())
        every { designatedDriverTripRepository.save(any()) } answers { firstArg() }

        When("the customer cancels immediately after the driver accepted") {
            val result = service.cancelTrip("customer_1", "designated_trip_1")

            Then("a real full refund posts with no cancellation fee -- the fix doesn't over-charge") {
                result.status shouldBe DesignatedDriverTripStatus.CANCELLED
                legsSlot.captured.size shouldBe 2
                legsSlot.captured[1].amount.compareTo(BigDecimal("4250.00")) shouldBe 0
            }

            // Real gap found live (2026-09-14, sibling-asymmetry sweep): the driver
            // (who had already committed by accepting) was never told the customer
            // cancelled, even in the no-fee grace-period case.
            Then("the real driver is real-notified the trip was cancelled, even with no fee") {
                io.mockk.verify(exactly = 1) { notificationRepository.save(match { it.userId == "driver_user_1" && it.type == "DESIGNATED_DRIVER_TRIP_UPDATE" }) }
                io.mockk.verify(exactly = 1) { pushNotificationService.sendToUser("driver_user_1", any(), any(), any()) }
            }
        }
    }

    Given("a real ACCEPTED trip past the cancellation-fee grace period") {
        val designatedDriverRepository = mockk<DesignatedDriverRepository>()
        val designatedDriverTripRepository = mockk<DesignatedDriverTripRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        every { notificationRepository.save(any()) } answers { firstArg() }
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = newService(
            designatedDriverRepository = designatedDriverRepository, designatedDriverTripRepository = designatedDriverTripRepository,
            accountRepository = accountRepository, ledgerService = ledgerService,
            notificationRepository = notificationRepository, pushNotificationService = pushNotificationService,
        )

        val customerAccount = Account(
            id = "account_customer", userId = "customer_1", accountNumber = "1000000002", accountName = "Customer",
            type = AccountType.MAIN, balance = BigDecimal("20000"), availableBalance = BigDecimal("20000"),
        )
        val driver = DesignatedDriver(id = "designated_driver_1", userId = "driver_user_1", accountId = "account_driver", licenseNumber = "DL-1", available = false)
        val driverAccount = Account(
            id = "account_driver", userId = "driver_user_1", accountNumber = "1000000003", accountName = "Driver",
            type = AccountType.MAIN, balance = BigDecimal.ZERO, availableBalance = BigDecimal.ZERO,
        )
        val trip = DesignatedDriverTrip(
            id = "designated_trip_1", customerId = "customer_1", driverId = "designated_driver_1", pickupAddress = "A", pickupLatitude = -1.9,
            pickupLongitude = 30.0, dropoffAddress = "B", dropoffLatitude = -1.95, dropoffLongitude = 30.05, vehicleMake = "Toyota",
            vehicleModel = "RAV4", vehiclePlate = "RAB 123 A", distanceKm = BigDecimal("5.000"), fare = BigDecimal("4250.00"),
            platformFee = BigDecimal("63.75"), holdTransactionId = "ledgertxn_1", status = DesignatedDriverTripStatus.ACCEPTED,
            driverAcceptedAt = java.time.Instant.now().minus(java.time.Duration.ofMinutes(5)),
        )
        every { designatedDriverTripRepository.findByIdForUpdate("designated_trip_1") } returns Optional.of(trip)
        every { accountRepository.findByUserIdAndType("customer_1", AccountType.MAIN) } returns customerAccount
        every { designatedDriverRepository.findById("designated_driver_1") } returns Optional.of(driver)
        every { accountRepository.findById("account_driver") } returns Optional.of(driverAccount)
        val legsSlot = slot<List<rw.itunda.core.ledger.LedgerLeg>>()
        every { ledgerService.postLedgerTransaction(any(), capture(legsSlot)) } returns LedgerPostResult("ledgertxn_refund", emptyList())
        every { designatedDriverTripRepository.save(any()) } answers { firstArg() }

        When("the customer cancels 5 minutes after the driver accepted -- this is the real gap this fix closes") {
            val result = service.cancelTrip("customer_1", "designated_trip_1")

            Then("a real cancellation fee is paid to the driver and the rest refunded to the customer") {
                result.status shouldBe DesignatedDriverTripStatus.CANCELLED
                legsSlot.captured.size shouldBe 3
                legsSlot.captured[1].amount.compareTo(BigDecimal("1250.00")) shouldBe 0
                legsSlot.captured[2].accountId shouldBe "account_driver"
                legsSlot.captured[2].amount.compareTo(BigDecimal("3000")) shouldBe 0
            }

            // Real gap found live (2026-09-14, sibling-asymmetry sweep): the driver's
            // account was credited real cancellation-fee money with zero notification
            // of why.
            Then("the real driver is real-notified they were paid a real cancellation fee") {
                io.mockk.verify(exactly = 1) { notificationRepository.save(match { it.userId == "driver_user_1" && it.type == "DESIGNATED_DRIVER_CANCELLATION_FEE_PAID" }) }
                io.mockk.verify(exactly = 1) { pushNotificationService.sendToUser("driver_user_1", any(), any(), any()) }
            }
        }
    }

    Given("a real DRIVING trip") {
        val designatedDriverTripRepository = mockk<DesignatedDriverTripRepository>()
        val service = newService(designatedDriverTripRepository = designatedDriverTripRepository)

        val trip = DesignatedDriverTrip(
            id = "designated_trip_1", customerId = "customer_1", driverId = "designated_driver_1", pickupAddress = "A", pickupLatitude = -1.9,
            pickupLongitude = 30.0, dropoffAddress = "B", dropoffLatitude = -1.95, dropoffLongitude = 30.05, vehicleMake = "Toyota",
            vehicleModel = "RAV4", vehiclePlate = "RAB 123 A", distanceKm = BigDecimal("5.000"), fare = BigDecimal("4250.00"),
            platformFee = BigDecimal("63.75"), holdTransactionId = "ledgertxn_1", status = DesignatedDriverTripStatus.DRIVING,
        )
        every { designatedDriverTripRepository.findByIdForUpdate("designated_trip_1") } returns Optional.of(trip)

        When("the customer tries to cancel once the driver is already driving") {
            Then("the cancel is correctly rejected -- the fix doesn't over-widen the cancel window") {
                try {
                    service.cancelTrip("customer_1", "designated_trip_1")
                    throw AssertionError("expected InvalidDesignatedDriverTripStatusTransitionException")
                } catch (e: InvalidDesignatedDriverTripStatusTransitionException) {
                    e.message shouldBe "Only a REQUESTED or ACCEPTED trip can be cancelled -- this one is already DRIVING"
                }
            }
        }
    }
})
