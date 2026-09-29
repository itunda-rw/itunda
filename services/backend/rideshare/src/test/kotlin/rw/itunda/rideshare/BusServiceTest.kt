package rw.itunda.rideshare

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.BusBooking
import rw.itunda.core.domain.BusBookingStatus
import rw.itunda.core.domain.BusTrip
import rw.itunda.core.domain.Account
import rw.itunda.core.domain.AccountType
import rw.itunda.core.fraud.FraudRuleEngine
import rw.itunda.core.ledger.LedgerPostResult
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.BusBookingRepository
import rw.itunda.core.repository.BusTripRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.AccountRepository
import java.math.BigDecimal
import java.time.Duration
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
        accountRepository: AccountRepository = mockk(),
        ledgerService: LedgerService = mockk(),
        rateLimiter: RateLimiter = mockk(relaxed = true),
        fraudRuleEngine: FraudRuleEngine = mockk(relaxed = true),
        notificationRepository: NotificationRepository = mockk(relaxed = true),
        pushNotificationService: PushNotificationService = mockk(relaxed = true),
    ) = BusService(
        busTripRepository, busBookingRepository, accountRepository, ledgerService, rateLimiter, fraudRuleEngine,
        notificationRepository, pushNotificationService,
    )

    Given("a real operator with a account posting a real future trip") {
        val busTripRepository = mockk<BusTripRepository>()
        val accountRepository = mockk<AccountRepository>()
        val account = Account(
            id = "account_operator", userId = "operator_1", accountNumber = "1000000001", accountName = "Operator",
            type = AccountType.MAIN, balance = BigDecimal.ZERO, availableBalance = BigDecimal.ZERO,
        )
        every { accountRepository.findByUserIdAndType("operator_1", AccountType.MAIN) } returns account
        val savedSlot = slot<BusTrip>()
        every { busTripRepository.save(capture(savedSlot)) } answers { firstArg() }
        val service = newService(busTripRepository = busTripRepository, accountRepository = accountRepository)

        When("posting a real trip") {
            val result = service.postTrip(
                "operator_1", "Kigali", "Musanze", Instant.now().plusSeconds(86400), 30, BigDecimal("3000"),
            )

            Then("a real trip row is saved with availableSeats seeded from totalSeats") {
                result.operatorUserId shouldBe "operator_1"
                result.accountId shouldBe "account_operator"
                result.totalSeats shouldBe 30
                result.availableSeats shouldBe 30
                savedSlot.captured.origin shouldBe "Kigali"
            }
        }
    }

    // Real bug found live (2026-08-02): postTrip used to have no rate limit at all,
    // unlike every other real "post a listing" creation method in this codebase.
    Given("an operator who has already posted too many real trips this hour") {
        val busTripRepository = mockk<BusTripRepository>()
        val accountRepository = mockk<AccountRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        every { accountRepository.findByUserIdAndType("operator_1", AccountType.MAIN) } returns Account(
            id = "account_operator", userId = "operator_1", accountNumber = "1000000001", accountName = "Operator",
            type = AccountType.MAIN, balance = BigDecimal.ZERO, availableBalance = BigDecimal.ZERO,
        )
        every { rateLimiter.checkLimit("bus:post-trip:operator_1", limit = 10, window = Duration.ofHours(1)) } throws
            RateLimitExceededException("Too many requests")
        val service = newService(busTripRepository = busTripRepository, accountRepository = accountRepository, rateLimiter = rateLimiter)

        When("posting yet another real trip") {
            Then("it real-propagates RateLimitExceededException before ever touching the account") {
                try {
                    service.postTrip("operator_1", "Kigali", "Musanze", Instant.now().plusSeconds(86400), 30, BigDecimal("3000"))
                    error("expected RateLimitExceededException")
                } catch (e: RateLimitExceededException) {
                    verify(exactly = 0) { accountRepository.findByUserIdAndType(any(), any()) }
                }
            }
        }
    }

    Given("a real trip with only 2 seats left") {
        val busTripRepository = mockk<BusTripRepository>()
        val trip = BusTrip(
            id = "bus_trip_1", operatorUserId = "operator_1", accountId = "account_operator", origin = "Kigali",
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

    Given("a real trip with sufficient seats and a rider with a real account") {
        val busTripRepository = mockk<BusTripRepository>()
        val busBookingRepository = mockk<BusBookingRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        every { notificationRepository.save(any()) } answers { firstArg() }
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = newService(
            busTripRepository = busTripRepository, busBookingRepository = busBookingRepository,
            accountRepository = accountRepository, ledgerService = ledgerService, fraudRuleEngine = fraudRuleEngine,
            notificationRepository = notificationRepository, pushNotificationService = pushNotificationService,
        )

        val trip = BusTrip(
            id = "bus_trip_1", operatorUserId = "operator_1", accountId = "account_operator", origin = "Kigali",
            destination = "Musanze", departureTime = Instant.now().plusSeconds(86400), totalSeats = 30,
            availableSeats = 10, farePerSeat = BigDecimal("3000"),
        )
        val riderAccount = Account(
            id = "account_rider", userId = "rider_1", accountNumber = "1000000002", accountName = "Rider",
            type = AccountType.MAIN, balance = BigDecimal("20000"), availableBalance = BigDecimal("20000"),
        )
        val operatorAccount = Account(
            id = "account_operator", userId = "operator_1", accountNumber = "1000000001", accountName = "Operator",
            type = AccountType.MAIN, balance = BigDecimal.ZERO, availableBalance = BigDecimal.ZERO,
        )
        every { busTripRepository.findById("bus_trip_1") } returns Optional.of(trip)
        every { accountRepository.findByUserIdAndType("rider_1", AccountType.MAIN) } returns riderAccount
        every { accountRepository.findById("account_operator") } returns Optional.of(operatorAccount)
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

            // Real gap found live (2026-09-14, FraudRuleEngine-verify sweep): this
            // class's own doc comment says it bills "the same direct
            // account-to-account-at-purchase shape MerchantService.collect already
            // establishes" -- but never copied collect()'s real fraudRuleEngine.evaluate
            // call.
            Then("the real fraud engine is evaluated against the rider and the real bus operator") {
                verify(exactly = 1) { fraudRuleEngine.evaluate("rider_1", "operator_1", BigDecimal("6000.00"), "ledgertxn_1") }
            }

            // Real gap found live (2026-09-14, sibling-asymmetry sweep): this class
            // had zero notification wiring -- the operator has no way to know a real
            // rider booked seats on their trip otherwise.
            Then("the real bus operator is real-notified of the ticket sale") {
                verify(exactly = 1) { notificationRepository.save(match { it.userId == "operator_1" && it.type == "BUS_TICKET_SOLD" }) }
                verify(exactly = 1) { pushNotificationService.sendToUser("operator_1", any(), any(), any()) }
            }
        }
    }

    Given("a real BOOKED booking on a trip that hasn't departed yet") {
        val busTripRepository = mockk<BusTripRepository>()
        val busBookingRepository = mockk<BusBookingRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        every { notificationRepository.save(any()) } answers { firstArg() }
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = newService(
            busTripRepository = busTripRepository, busBookingRepository = busBookingRepository,
            accountRepository = accountRepository, ledgerService = ledgerService,
            notificationRepository = notificationRepository, pushNotificationService = pushNotificationService,
        )

        val trip = BusTrip(
            id = "bus_trip_1", operatorUserId = "operator_1", accountId = "account_operator", origin = "Kigali",
            destination = "Musanze", departureTime = Instant.now().plusSeconds(86400), totalSeats = 30,
            availableSeats = 8, farePerSeat = BigDecimal("3000"),
        )
        val booking = BusBooking(
            id = "bus_booking_1", tripId = "bus_trip_1", riderUserId = "rider_1", seatCount = 2,
            totalFare = BigDecimal("6000.00"), platformFee = BigDecimal("300.00"), paymentTransactionId = "ledgertxn_1",
        )
        val riderAccount = Account(
            id = "account_rider", userId = "rider_1", accountNumber = "1000000002", accountName = "Rider",
            type = AccountType.MAIN, balance = BigDecimal("14000"), availableBalance = BigDecimal("14000"),
        )
        val operatorAccount = Account(
            id = "account_operator", userId = "operator_1", accountNumber = "1000000001", accountName = "Operator",
            type = AccountType.MAIN, balance = BigDecimal("5700"), availableBalance = BigDecimal("5700"),
        )
        every { busBookingRepository.findByIdForUpdate("bus_booking_1") } returns Optional.of(booking)
        every { busTripRepository.findById("bus_trip_1") } returns Optional.of(trip)
        every { accountRepository.findByUserIdAndType("rider_1", AccountType.MAIN) } returns riderAccount
        every { accountRepository.findById("account_operator") } returns Optional.of(operatorAccount)
        every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_refund", emptyList())
        every { busTripRepository.save(any()) } answers { firstArg() }
        every { busBookingRepository.save(any()) } answers { firstArg() }

        When("the rider cancels before departure") {
            val result = service.cancelBooking("rider_1", "bus_booking_1")

            Then("a real full refund posts and the booking is marked CANCELLED") {
                result.status shouldBe BusBookingStatus.CANCELLED
                result.refundTransactionId shouldBe "ledgertxn_refund"
            }

            // Real gap found live (2026-09-14, sibling-asymmetry sweep): the operator
            // has no way to know their real ticket revenue was just reversed
            // otherwise.
            Then("the real bus operator is real-notified of the cancellation") {
                verify(exactly = 1) { notificationRepository.save(match { it.userId == "operator_1" && it.type == "BUS_BOOKING_CANCELLED" }) }
                verify(exactly = 1) { pushNotificationService.sendToUser("operator_1", any(), any(), any()) }
            }
        }
    }
})
