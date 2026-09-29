package rw.itunda.rideshare

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.ParkingSession
import rw.itunda.core.domain.ParkingSessionStatus
import rw.itunda.core.domain.ParkingSpot
import rw.itunda.core.domain.Account
import rw.itunda.core.domain.AccountType
import rw.itunda.core.fraud.FraudRuleEngine
import rw.itunda.core.ledger.LedgerPostResult
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.ParkingSessionRepository
import rw.itunda.core.repository.ParkingSpotRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.AccountRepository
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.util.Optional

/**
 * First test coverage for real Kakao T 주차 (Kakao T Parking) -- see
 * ParkingService's own doc comment for the full sourced account. Mirrors
 * BikeRentalServiceTest's own established mocking conventions.
 */
class ParkingServiceTest : BehaviorSpec({

    fun newService(
        parkingSpotRepository: ParkingSpotRepository = mockk(),
        parkingSessionRepository: ParkingSessionRepository = mockk(),
        accountRepository: AccountRepository = mockk(),
        ledgerService: LedgerService = mockk(),
        rateLimiter: RateLimiter = mockk(relaxed = true),
        fraudRuleEngine: FraudRuleEngine = mockk(relaxed = true),
        notificationRepository: NotificationRepository = mockk(relaxed = true),
        pushNotificationService: PushNotificationService = mockk(relaxed = true),
    ) = ParkingService(
        parkingSpotRepository, parkingSessionRepository, accountRepository, ledgerService, rateLimiter, fraudRuleEngine,
        notificationRepository, pushNotificationService,
    )

    Given("a fresh owner account with a real account") {
        val parkingSpotRepository = mockk<ParkingSpotRepository>()
        val accountRepository = mockk<AccountRepository>()
        val account = Account(
            id = "account_owner", userId = "owner_1", accountNumber = "1000000001", accountName = "Owner",
            type = AccountType.MAIN, balance = BigDecimal.ZERO, availableBalance = BigDecimal.ZERO,
        )
        every { accountRepository.findByUserIdAndType("owner_1", AccountType.MAIN) } returns account
        val savedSlot = slot<ParkingSpot>()
        every { parkingSpotRepository.save(capture(savedSlot)) } answers { firstArg() }
        val service = newService(parkingSpotRepository = parkingSpotRepository, accountRepository = accountRepository)

        When("registering a real parking spot") {
            val result = service.registerSpot("owner_1", "Kigali Heights driveway", -1.9536, 30.0605, BigDecimal("500"))

            Then("a real spot row is saved with the account reused as payout destination") {
                result.ownerUserId shouldBe "owner_1"
                result.accountId shouldBe "account_owner"
                result.hourlyRate shouldBe BigDecimal("500")
                savedSlot.captured.ownerUserId shouldBe "owner_1"
            }
        }
    }

    // Real bug found live (2026-08-02): registerSpot used to have no rate limit at
    // all, unlike every other real "post a listing" creation method in this codebase.
    Given("an owner who has already registered too many real parking spots this hour") {
        val parkingSpotRepository = mockk<ParkingSpotRepository>()
        val accountRepository = mockk<AccountRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        every { accountRepository.findByUserIdAndType("owner_1", AccountType.MAIN) } returns Account(
            id = "account_owner", userId = "owner_1", accountNumber = "1000000001", accountName = "Owner",
            type = AccountType.MAIN, balance = BigDecimal.ZERO, availableBalance = BigDecimal.ZERO,
        )
        every { rateLimiter.checkLimit("parking:register:owner_1", limit = 10, window = Duration.ofHours(1)) } throws
            RateLimitExceededException("Too many requests")
        val service = newService(parkingSpotRepository = parkingSpotRepository, accountRepository = accountRepository, rateLimiter = rateLimiter)

        When("registering yet another real parking spot") {
            Then("it real-propagates RateLimitExceededException before ever touching the account") {
                try {
                    service.registerSpot("owner_1", "Kigali Heights driveway", -1.9536, 30.0605, BigDecimal("500"))
                    error("expected RateLimitExceededException")
                } catch (e: RateLimitExceededException) {
                    verify(exactly = 0) { accountRepository.findByUserIdAndType(any(), any()) }
                }
            }
        }
    }

    Given("a real renter trying to rent their own parking spot") {
        val parkingSpotRepository = mockk<ParkingSpotRepository>()
        val spot = ParkingSpot(
            id = "parking_spot_1", ownerUserId = "owner_1", accountId = "account_owner", address = "A",
            latitude = -1.9, longitude = 30.0, hourlyRate = BigDecimal("500"),
        )
        every { parkingSpotRepository.findById("parking_spot_1") } returns Optional.of(spot)
        val service = newService(parkingSpotRepository = parkingSpotRepository)

        When("that same owner tries to start a session on it") {
            Then("the self-rental block fires") {
                try {
                    service.startSession("owner_1", "parking_spot_1")
                    throw AssertionError("expected ParkingSelfRentalException")
                } catch (e: ParkingSelfRentalException) {
                    e.message shouldBe "Cannot rent your own parking spot"
                }
            }
        }
    }

    Given("a parking spot that is already occupied") {
        val parkingSpotRepository = mockk<ParkingSpotRepository>()
        val parkingSessionRepository = mockk<ParkingSessionRepository>()
        val spot = ParkingSpot(
            id = "parking_spot_1", ownerUserId = "owner_1", accountId = "account_owner", address = "A",
            latitude = -1.9, longitude = 30.0, hourlyRate = BigDecimal("500"), available = true,
        )
        val activeSession = ParkingSession(id = "parking_session_existing", spotId = "parking_spot_1", renterUserId = "other_renter")
        every { parkingSpotRepository.findById("parking_spot_1") } returns Optional.of(spot)
        every { parkingSessionRepository.findBySpotIdAndStatus("parking_spot_1", ParkingSessionStatus.ACTIVE) } returns activeSession
        val service = newService(parkingSpotRepository = parkingSpotRepository, parkingSessionRepository = parkingSessionRepository)

        When("a new renter tries to start a session on it") {
            Then("the already-occupied conflict fires") {
                try {
                    service.startSession("renter_1", "parking_spot_1")
                    throw AssertionError("expected ParkingSpotNotAvailableException")
                } catch (e: ParkingSpotNotAvailableException) {
                    e.message shouldBe "This parking spot is already occupied"
                }
            }
        }
    }

    // Real bug found live (2026-08-02) -- see BikeRentalServiceTest's own identical
    // test doc comment for the full account. This proves startSession's fix: it now
    // saves the spot itself with `available = false`, the versioned row a concurrent
    // second attempt would race against and real-409 on.
    Given("a real available parking spot with no active session") {
        val parkingSpotRepository = mockk<ParkingSpotRepository>()
        val parkingSessionRepository = mockk<ParkingSessionRepository>()
        val accountRepository = mockk<AccountRepository>()
        val spot = ParkingSpot(
            id = "parking_spot_1", ownerUserId = "owner_1", accountId = "account_owner", address = "A",
            latitude = -1.9, longitude = 30.0, hourlyRate = BigDecimal("500"), available = true,
        )
        val renterAccount = Account(
            id = "account_renter", userId = "renter_1", accountNumber = "1000000002", accountName = "Renter",
            type = AccountType.MAIN, balance = BigDecimal("20000"), availableBalance = BigDecimal("20000"),
        )
        every { parkingSpotRepository.findById("parking_spot_1") } returns Optional.of(spot)
        every { parkingSessionRepository.findBySpotIdAndStatus("parking_spot_1", ParkingSessionStatus.ACTIVE) } returns null
        every { accountRepository.findByUserIdAndType("renter_1", AccountType.MAIN) } returns renterAccount
        val spotSavedSlot = slot<ParkingSpot>()
        every { parkingSpotRepository.save(capture(spotSavedSlot)) } answers { firstArg() }
        every { parkingSessionRepository.save(any()) } answers { firstArg() }
        val service = newService(
            parkingSpotRepository = parkingSpotRepository, parkingSessionRepository = parkingSessionRepository, accountRepository = accountRepository,
        )

        When("a renter starts a real session") {
            service.startSession("renter_1", "parking_spot_1")

            Then("the spot itself is saved as unavailable") {
                spotSavedSlot.captured.available shouldBe false
            }
        }
    }

    Given("a real ACTIVE session the renter ends after 90 minutes") {
        val parkingSpotRepository = mockk<ParkingSpotRepository>()
        val parkingSessionRepository = mockk<ParkingSessionRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        every { notificationRepository.save(any()) } answers { firstArg() }
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = newService(
            parkingSpotRepository = parkingSpotRepository, parkingSessionRepository = parkingSessionRepository,
            accountRepository = accountRepository, ledgerService = ledgerService, fraudRuleEngine = fraudRuleEngine,
            notificationRepository = notificationRepository, pushNotificationService = pushNotificationService,
        )

        val spot = ParkingSpot(
            id = "parking_spot_1", ownerUserId = "owner_1", accountId = "account_owner", address = "A",
            latitude = -1.9, longitude = 30.0, hourlyRate = BigDecimal("500"),
        )
        val renterAccount = Account(
            id = "account_renter", userId = "renter_1", accountNumber = "1000000002", accountName = "Renter",
            type = AccountType.MAIN, balance = BigDecimal("20000"), availableBalance = BigDecimal("20000"),
        )
        val ownerAccount = Account(
            id = "account_owner", userId = "owner_1", accountNumber = "1000000001", accountName = "Owner",
            type = AccountType.MAIN, balance = BigDecimal.ZERO, availableBalance = BigDecimal.ZERO,
        )
        val session = ParkingSession(
            id = "parking_session_1", spotId = "parking_spot_1", renterUserId = "renter_1",
            startedAt = Instant.now().minusSeconds(90 * 60),
        )
        every { parkingSessionRepository.findById("parking_session_1") } returns Optional.of(session)
        every { parkingSpotRepository.findById("parking_spot_1") } returns Optional.of(spot)
        every { accountRepository.findByUserIdAndType("renter_1", AccountType.MAIN) } returns renterAccount
        every { accountRepository.findById("account_owner") } returns Optional.of(ownerAccount)
        every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_1", emptyList())
        every { parkingSessionRepository.save(any()) } answers { firstArg() }
        val spotSavedSlot = slot<ParkingSpot>()
        every { parkingSpotRepository.save(capture(spotSavedSlot)) } answers { firstArg() }

        When("the renter ends the session") {
            val result = service.endSession("renter_1", "parking_session_1")

            Then("a real fare rounds 90 minutes up to 2 billed hours, the session is marked COMPLETED, and the spot is made available again") {
                result.status shouldBe ParkingSessionStatus.COMPLETED
                result.durationMinutes shouldBe 90
                // 500/hour, 90 minutes rounds up to 2 hours -- 2 * 500 = 1000.
                result.totalFare shouldBe BigDecimal("1000.00")
                result.platformFee shouldBe BigDecimal("150.00")
                result.payoutTransactionId shouldBe "ledgertxn_1"
                spotSavedSlot.captured.available shouldBe true
            }

            // Real gap found live (2026-09-14, FraudRuleEngine-verify sweep): this
            // class's own doc comment says it uses "the same shape BikeRentalService
            // already establishes" -- but only copied the ledger-leg pattern, not the
            // fraudRuleEngine.evaluate call already added to that sibling.
            Then("the real fraud engine is evaluated against the renter and the real spot owner") {
                verify(exactly = 1) { fraudRuleEngine.evaluate("renter_1", "owner_1", BigDecimal("1000.00"), "ledgertxn_1") }
            }

            // Real gap found live (2026-09-14, sibling-asymmetry sweep): this class
            // had zero notification wiring -- the owner has no way to know their spot
            // was occupied and paid out otherwise.
            Then("the real spot owner is real-notified of the payout") {
                verify(exactly = 1) { notificationRepository.save(match { it.userId == "owner_1" && it.type == "PARKING_SESSION_PAYOUT" }) }
                verify(exactly = 1) { pushNotificationService.sendToUser("owner_1", any(), any(), any()) }
            }
        }
    }

    // Real bug fixed live (2026-08-18): an ACTIVE session had no timeout at all -- see
    // ParkingSession.MAX_SESSION_DURATION's own doc comment for the sourced Citi Bike
    // 24-hour account this reuses. Rejection case: a session still well within the real
    // window must NOT be force-ended -- the scheduler polling `getAbandonedSessions`
    // would never even hand this row to `forceEndAbandonedSession` in production, but
    // the re-check inside the method itself is what actually prevents a stray/late call
    // from prematurely billing a renter who is still genuinely parked.
    Given("a real ACTIVE session only 2 hours old, well within the max session window") {
        val parkingSpotRepository = mockk<ParkingSpotRepository>()
        val parkingSessionRepository = mockk<ParkingSessionRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val service = newService(
            parkingSpotRepository = parkingSpotRepository, parkingSessionRepository = parkingSessionRepository,
            accountRepository = accountRepository, ledgerService = ledgerService,
        )
        val session = ParkingSession(
            id = "parking_session_recent", spotId = "parking_spot_1", renterUserId = "renter_1",
            startedAt = Instant.now().minus(Duration.ofHours(2)),
        )
        every { parkingSessionRepository.findById("parking_session_recent") } returns Optional.of(session)

        When("the scheduler's force-end is (incorrectly) invoked on it anyway") {
            val result = service.forceEndAbandonedSession("parking_session_recent")

            Then("it is a real no-op -- still ACTIVE, no ledger transaction posted, no spot/account ever looked up") {
                result?.status shouldBe ParkingSessionStatus.ACTIVE
                verify(exactly = 0) { parkingSpotRepository.findById(any()) }
                verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
            }
        }
    }

    // Rejection case's positive counterpart: a session genuinely abandoned past the real
    // Citi Bike-sourced 24-hour window IS force-settled, using the exact same billing
    // math `endSession` uses (proving `settleSession` was actually reused, not
    // reimplemented).
    Given("a real ACTIVE session abandoned 25 hours ago, past the max session window") {
        val parkingSpotRepository = mockk<ParkingSpotRepository>()
        val parkingSessionRepository = mockk<ParkingSessionRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        every { notificationRepository.save(any()) } answers { firstArg() }
        val service = newService(
            parkingSpotRepository = parkingSpotRepository, parkingSessionRepository = parkingSessionRepository,
            accountRepository = accountRepository, ledgerService = ledgerService, notificationRepository = notificationRepository,
        )

        val spot = ParkingSpot(
            id = "parking_spot_1", ownerUserId = "owner_1", accountId = "account_owner", address = "Kigali Heights driveway",
            latitude = -1.9, longitude = 30.0, hourlyRate = BigDecimal("500"),
        )
        val renterAccount = Account(
            id = "account_renter", userId = "renter_1", accountNumber = "1000000002", accountName = "Renter",
            type = AccountType.MAIN, balance = BigDecimal("1000000"), availableBalance = BigDecimal("1000000"),
        )
        val ownerAccount = Account(
            id = "account_owner", userId = "owner_1", accountNumber = "1000000001", accountName = "Owner",
            type = AccountType.MAIN, balance = BigDecimal.ZERO, availableBalance = BigDecimal.ZERO,
        )
        val session = ParkingSession(
            id = "parking_session_abandoned", spotId = "parking_spot_1", renterUserId = "renter_1",
            startedAt = Instant.now().minus(Duration.ofHours(25)),
        )
        every { parkingSessionRepository.findById("parking_session_abandoned") } returns Optional.of(session)
        every { parkingSpotRepository.findById("parking_spot_1") } returns Optional.of(spot)
        every { accountRepository.findByUserIdAndType("renter_1", AccountType.MAIN) } returns renterAccount
        every { accountRepository.findById("account_owner") } returns Optional.of(ownerAccount)
        every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_forceend", emptyList())
        every { parkingSessionRepository.save(any()) } answers { firstArg() }
        val spotSavedSlot = slot<ParkingSpot>()
        every { parkingSpotRepository.save(capture(spotSavedSlot)) } answers { firstArg() }

        When("the scheduler force-ends the abandoned session") {
            val result = service.forceEndAbandonedSession("parking_session_abandoned")

            Then("the renter is real-billed for the full 25 hours rounded up, the owner is paid, and the spot re-enters the pool") {
                result?.status shouldBe ParkingSessionStatus.COMPLETED
                result?.durationMinutes shouldBe 25 * 60
                // 500/hour, 25 hours -- 25 * 500 = 12,500.
                result?.totalFare shouldBe BigDecimal("12500.00")
                result?.platformFee shouldBe BigDecimal("1875.00")
                result?.payoutTransactionId shouldBe "ledgertxn_forceend"
                spotSavedSlot.captured.available shouldBe true
            }
        }
    }
})
