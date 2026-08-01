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
import rw.itunda.core.domain.Wallet
import rw.itunda.core.domain.WalletType
import rw.itunda.core.ledger.LedgerPostResult
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.DesignatedDriverRepository
import rw.itunda.core.repository.DesignatedDriverTripRepository
import rw.itunda.core.repository.TransactionRepository
import rw.itunda.core.repository.WalletRepository
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
        walletRepository: WalletRepository = mockk(),
        ledgerService: LedgerService = mockk(),
        transactionRepository: TransactionRepository = mockk<TransactionRepository>(relaxed = true).also { every { it.save(any()) } answers { firstArg() } },
        rateLimiter: RateLimiter = mockk(relaxed = true),
    ) = DesignatedDriverService(designatedDriverRepository, designatedDriverTripRepository, walletRepository, ledgerService, transactionRepository, rateLimiter)

    Given("a fresh account with a real wallet") {
        val designatedDriverRepository = mockk<DesignatedDriverRepository>()
        val service = newService(designatedDriverRepository = designatedDriverRepository)
        val wallet = Wallet(
            id = "wallet_1", userId = "user_1", accountNumber = "1000000001", accountName = "User",
            type = WalletType.MAIN, balance = BigDecimal.ZERO, availableBalance = BigDecimal.ZERO,
        )
        val walletRepository = mockk<WalletRepository>()
        every { walletRepository.findByUserIdAndType("user_1", WalletType.MAIN) } returns wallet
        val service2 = newService(designatedDriverRepository = designatedDriverRepository, walletRepository = walletRepository)

        When("registering as a designated driver for the first time") {
            every { designatedDriverRepository.findByUserId("user_1") } returns null
            val savedSlot = slot<DesignatedDriver>()
            every { designatedDriverRepository.save(capture(savedSlot)) } answers { firstArg() }

            val result = service2.register("user_1", "DL-12345")

            Then("a real driver row is saved with the wallet reused as payout destination") {
                result.userId shouldBe "user_1"
                result.walletId shouldBe "wallet_1"
                result.licenseNumber shouldBe "DL-12345"
                savedSlot.captured.userId shouldBe "user_1"
            }
        }

        When("registering twice for the same account") {
            every { designatedDriverRepository.findByUserId("user_1") } returns
                DesignatedDriver(id = "designated_driver_1", userId = "user_1", walletId = "wallet_1", licenseNumber = "DL-12345")

            Then("the second registration real-409s") {
                try {
                    service2.register("user_1", "DL-99999")
                    throw AssertionError("expected DesignatedDriverAlreadyRegisteredException")
                } catch (e: DesignatedDriverAlreadyRegisteredException) {
                    e.message shouldBe "This account is already registered as a designated driver"
                }
            }
        }
    }

    Given("a real customer with sufficient balance requesting their own car driven home") {
        val designatedDriverTripRepository = mockk<DesignatedDriverTripRepository>()
        val walletRepository = mockk<WalletRepository>()
        val ledgerService = mockk<LedgerService>()
        val service = newService(designatedDriverTripRepository = designatedDriverTripRepository, walletRepository = walletRepository, ledgerService = ledgerService)

        val customerWallet = Wallet(
            id = "wallet_customer", userId = "customer_1", accountNumber = "1000000002", accountName = "Customer",
            type = WalletType.MAIN, balance = BigDecimal("20000"), availableBalance = BigDecimal("20000"),
        )
        every { walletRepository.findByUserIdAndType("customer_1", WalletType.MAIN) } returns customerWallet
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
        }
    }

    Given("a real driver trying to accept their own requested trip") {
        val designatedDriverRepository = mockk<DesignatedDriverRepository>()
        val designatedDriverTripRepository = mockk<DesignatedDriverTripRepository>()
        val service = newService(designatedDriverRepository = designatedDriverRepository, designatedDriverTripRepository = designatedDriverTripRepository)

        val driver = DesignatedDriver(id = "designated_driver_1", userId = "user_1", walletId = "wallet_1", licenseNumber = "DL-1", available = true)
        val trip = DesignatedDriverTrip(
            id = "designated_trip_1", customerId = "user_1", pickupAddress = "A", pickupLatitude = -1.9, pickupLongitude = 30.0,
            dropoffAddress = "B", dropoffLatitude = -1.95, dropoffLongitude = 30.05, vehicleMake = "Toyota", vehicleModel = "RAV4",
            vehiclePlate = "RAB 123 A", distanceKm = BigDecimal("5.000"), fare = BigDecimal("4250.00"), platformFee = BigDecimal("63.75"),
            holdTransactionId = "ledgertxn_1",
        )
        every { designatedDriverRepository.findByUserId("user_1") } returns driver
        every { designatedDriverTripRepository.findById("designated_trip_1") } returns Optional.of(trip)

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

    Given("a real DRIVING trip a driver just completed") {
        val designatedDriverRepository = mockk<DesignatedDriverRepository>()
        val designatedDriverTripRepository = mockk<DesignatedDriverTripRepository>()
        val walletRepository = mockk<WalletRepository>()
        val ledgerService = mockk<LedgerService>()
        val service = newService(
            designatedDriverRepository = designatedDriverRepository, designatedDriverTripRepository = designatedDriverTripRepository,
            walletRepository = walletRepository, ledgerService = ledgerService,
        )

        val driver = DesignatedDriver(id = "designated_driver_1", userId = "driver_user_1", walletId = "wallet_driver", licenseNumber = "DL-1", available = true)
        val driverWallet = Wallet(
            id = "wallet_driver", userId = "driver_user_1", accountNumber = "1000000003", accountName = "Driver",
            type = WalletType.MAIN, balance = BigDecimal.ZERO, availableBalance = BigDecimal.ZERO,
        )
        val trip = DesignatedDriverTrip(
            id = "designated_trip_1", customerId = "customer_1", driverId = "designated_driver_1", pickupAddress = "A", pickupLatitude = -1.9,
            pickupLongitude = 30.0, dropoffAddress = "B", dropoffLatitude = -1.95, dropoffLongitude = 30.05, vehicleMake = "Toyota",
            vehicleModel = "RAV4", vehiclePlate = "RAB 123 A", distanceKm = BigDecimal("5.000"), fare = BigDecimal("4250.00"),
            platformFee = BigDecimal("63.75"), holdTransactionId = "ledgertxn_1", status = DesignatedDriverTripStatus.DRIVING,
        )
        every { designatedDriverRepository.findByUserId("driver_user_1") } returns driver
        every { designatedDriverTripRepository.findById("designated_trip_1") } returns Optional.of(trip)
        every { walletRepository.findById("wallet_driver") } returns Optional.of(driverWallet)
        every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_2", emptyList())
        every { designatedDriverTripRepository.save(any()) } answers { firstArg() }

        When("the driver completes the trip") {
            val result = service.completeTrip("driver_user_1", "designated_trip_1")

            Then("a real fare payout posts and the trip is marked COMPLETED") {
                result.status shouldBe DesignatedDriverTripStatus.COMPLETED
                result.payoutTransactionId shouldBe "ledgertxn_2"
            }
        }
    }

    Given("a real REQUESTED trip the customer wants to withdraw") {
        val designatedDriverTripRepository = mockk<DesignatedDriverTripRepository>()
        val walletRepository = mockk<WalletRepository>()
        val ledgerService = mockk<LedgerService>()
        val service = newService(designatedDriverTripRepository = designatedDriverTripRepository, walletRepository = walletRepository, ledgerService = ledgerService)

        val customerWallet = Wallet(
            id = "wallet_customer", userId = "customer_1", accountNumber = "1000000002", accountName = "Customer",
            type = WalletType.MAIN, balance = BigDecimal("20000"), availableBalance = BigDecimal("20000"),
        )
        val trip = DesignatedDriverTrip(
            id = "designated_trip_1", customerId = "customer_1", pickupAddress = "A", pickupLatitude = -1.9, pickupLongitude = 30.0,
            dropoffAddress = "B", dropoffLatitude = -1.95, dropoffLongitude = 30.05, vehicleMake = "Toyota", vehicleModel = "RAV4",
            vehiclePlate = "RAB 123 A", distanceKm = BigDecimal("5.000"), fare = BigDecimal("4250.00"), platformFee = BigDecimal("63.75"),
            holdTransactionId = "ledgertxn_1",
        )
        every { designatedDriverTripRepository.findById("designated_trip_1") } returns Optional.of(trip)
        every { walletRepository.findByUserIdAndType("customer_1", WalletType.MAIN) } returns customerWallet
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
})
