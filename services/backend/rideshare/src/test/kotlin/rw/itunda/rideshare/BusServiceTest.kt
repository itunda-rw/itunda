package rw.itunda.rideshare

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.BusBooking
import rw.itunda.core.domain.BusBookingStatus
import rw.itunda.core.domain.BusTrip
import rw.itunda.core.domain.Wallet
import rw.itunda.core.domain.WalletType
import rw.itunda.core.ledger.LedgerPostResult
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.BusBookingRepository
import rw.itunda.core.repository.BusTripRepository
import rw.itunda.core.repository.WalletRepository
import java.math.BigDecimal
import java.time.Instant
import java.util.Optional

/**
 * First test coverage for real Kakao T 시외버스 (intercity bus booking) -- see
 * BusService's own doc comment for the full sourced account. Mirrors
 * ParkingServiceTest's/DesignatedDriverServiceTest's own established mocking
 * conventions.
 */
class BusServiceTest : BehaviorSpec({

    fun newService(
        busTripRepository: BusTripRepository = mockk(),
        busBookingRepository: BusBookingRepository = mockk(),
        walletRepository: WalletRepository = mockk(),
        ledgerService: LedgerService = mockk(),
        rateLimiter: RateLimiter = mockk(relaxed = true),
    ) = BusService(busTripRepository, busBookingRepository, walletRepository, ledgerService, rateLimiter)

    Given("a real operator with a wallet posting a real future trip") {
        val busTripRepository = mockk<BusTripRepository>()
        val walletRepository = mockk<WalletRepository>()
        val wallet = Wallet(
            id = "wallet_operator", userId = "operator_1", accountNumber = "1000000001", accountName = "Operator",
            type = WalletType.MAIN, balance = BigDecimal.ZERO, availableBalance = BigDecimal.ZERO,
        )
        every { walletRepository.findByUserIdAndType("operator_1", WalletType.MAIN) } returns wallet
        val savedSlot = slot<BusTrip>()
        every { busTripRepository.save(capture(savedSlot)) } answers { firstArg() }
        val service = newService(busTripRepository = busTripRepository, walletRepository = walletRepository)

        When("posting a real trip") {
            val result = service.postTrip(
                "operator_1", "Kigali", "Musanze", Instant.now().plusSeconds(86400), 30, BigDecimal("3000"),
            )

            Then("a real trip row is saved with availableSeats seeded from totalSeats") {
                result.operatorUserId shouldBe "operator_1"
                result.walletId shouldBe "wallet_operator"
                result.totalSeats shouldBe 30
                result.availableSeats shouldBe 30
                savedSlot.captured.origin shouldBe "Kigali"
            }
        }
    }

    Given("a real trip with only 2 seats left") {
        val busTripRepository = mockk<BusTripRepository>()
        val trip = BusTrip(
            id = "bus_trip_1", operatorUserId = "operator_1", walletId = "wallet_operator", origin = "Kigali",
            destination = "Musanze", departureTime = Instant.now().plusSeconds(86400), totalSeats = 30,
            availableSeats = 2, farePerSeat = BigDecimal("3000"),
        )
        every { busTripRepository.findById("bus_trip_1") } returns Optional.of(trip)
        val service = newService(busTripRepository = busTripRepository)

        When("a rider tries to book 3 seats") {
            Then("the real insufficient-seats conflict fires") {
                try {
                    service.bookSeats("rider_1", "bus_trip_1", 3)
                    throw AssertionError("expected InsufficientSeatsException")
                } catch (e: InsufficientSeatsException) {
                    e.message shouldBe "Only 2 seat(s) remaining on this trip"
                }
            }
        }
    }

    Given("a real trip with sufficient seats and a rider with a real wallet") {
        val busTripRepository = mockk<BusTripRepository>()
        val busBookingRepository = mockk<BusBookingRepository>()
        val walletRepository = mockk<WalletRepository>()
        val ledgerService = mockk<LedgerService>()
        val service = newService(
            busTripRepository = busTripRepository, busBookingRepository = busBookingRepository,
            walletRepository = walletRepository, ledgerService = ledgerService,
        )

        val trip = BusTrip(
            id = "bus_trip_1", operatorUserId = "operator_1", walletId = "wallet_operator", origin = "Kigali",
            destination = "Musanze", departureTime = Instant.now().plusSeconds(86400), totalSeats = 30,
            availableSeats = 10, farePerSeat = BigDecimal("3000"),
        )
        val riderWallet = Wallet(
            id = "wallet_rider", userId = "rider_1", accountNumber = "1000000002", accountName = "Rider",
            type = WalletType.MAIN, balance = BigDecimal("20000"), availableBalance = BigDecimal("20000"),
        )
        val operatorWallet = Wallet(
            id = "wallet_operator", userId = "operator_1", accountNumber = "1000000001", accountName = "Operator",
            type = WalletType.MAIN, balance = BigDecimal.ZERO, availableBalance = BigDecimal.ZERO,
        )
        every { busTripRepository.findById("bus_trip_1") } returns Optional.of(trip)
        every { walletRepository.findByUserIdAndType("rider_1", WalletType.MAIN) } returns riderWallet
        every { walletRepository.findById("wallet_operator") } returns Optional.of(operatorWallet)
        every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_1", emptyList())
        every { busTripRepository.save(any()) } answers { firstArg() }
        val savedSlot = slot<BusBooking>()
        every { busBookingRepository.save(capture(savedSlot)) } answers { firstArg() }

        When("the rider books 2 seats") {
            val result = service.bookSeats("rider_1", "bus_trip_1", 2)

            Then("a real booking is created with the fare charged for 2 seats") {
                // 3000/seat * 2 seats = 6000; 5% platform fee = 300.
                result.totalFare shouldBe BigDecimal("6000.00")
                result.platformFee shouldBe BigDecimal("300.00")
                result.status shouldBe BusBookingStatus.BOOKED
                result.paymentTransactionId shouldBe "ledgertxn_1"
                savedSlot.captured.seatCount shouldBe 2
            }
        }
    }

    Given("a real BOOKED booking on a trip that hasn't departed yet") {
        val busTripRepository = mockk<BusTripRepository>()
        val busBookingRepository = mockk<BusBookingRepository>()
        val walletRepository = mockk<WalletRepository>()
        val ledgerService = mockk<LedgerService>()
        val service = newService(
            busTripRepository = busTripRepository, busBookingRepository = busBookingRepository,
            walletRepository = walletRepository, ledgerService = ledgerService,
        )

        val trip = BusTrip(
            id = "bus_trip_1", operatorUserId = "operator_1", walletId = "wallet_operator", origin = "Kigali",
            destination = "Musanze", departureTime = Instant.now().plusSeconds(86400), totalSeats = 30,
            availableSeats = 8, farePerSeat = BigDecimal("3000"),
        )
        val booking = BusBooking(
            id = "bus_booking_1", tripId = "bus_trip_1", riderUserId = "rider_1", seatCount = 2,
            totalFare = BigDecimal("6000.00"), platformFee = BigDecimal("300.00"), paymentTransactionId = "ledgertxn_1",
        )
        val riderWallet = Wallet(
            id = "wallet_rider", userId = "rider_1", accountNumber = "1000000002", accountName = "Rider",
            type = WalletType.MAIN, balance = BigDecimal("14000"), availableBalance = BigDecimal("14000"),
        )
        val operatorWallet = Wallet(
            id = "wallet_operator", userId = "operator_1", accountNumber = "1000000001", accountName = "Operator",
            type = WalletType.MAIN, balance = BigDecimal("5700"), availableBalance = BigDecimal("5700"),
        )
        every { busBookingRepository.findById("bus_booking_1") } returns Optional.of(booking)
        every { busTripRepository.findById("bus_trip_1") } returns Optional.of(trip)
        every { walletRepository.findByUserIdAndType("rider_1", WalletType.MAIN) } returns riderWallet
        every { walletRepository.findById("wallet_operator") } returns Optional.of(operatorWallet)
        every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_refund", emptyList())
        every { busTripRepository.save(any()) } answers { firstArg() }
        every { busBookingRepository.save(any()) } answers { firstArg() }

        When("the rider cancels before departure") {
            val result = service.cancelBooking("rider_1", "bus_booking_1")

            Then("a real full refund posts and the booking is marked CANCELLED") {
                result.status shouldBe BusBookingStatus.CANCELLED
                result.refundTransactionId shouldBe "ledgertxn_refund"
            }
        }
    }
})
