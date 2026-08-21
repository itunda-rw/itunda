package rw.itunda.rideshare

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.Bike
import rw.itunda.core.domain.BikeRentalSession
import rw.itunda.core.domain.BikeRentalStatus
import rw.itunda.core.domain.BikeType
import rw.itunda.core.domain.Account
import rw.itunda.core.domain.AccountType
import rw.itunda.core.ledger.LedgerPostResult
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.BikeRentalSessionRepository
import rw.itunda.core.repository.BikeRepository
import rw.itunda.core.repository.AccountRepository
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.util.Optional

/**
 * First test coverage for real Kakao T 바이크 (Kakao T Bike) -- see
 * BikeRentalService's own doc comment for the full sourced account. Mirrors
 * DesignatedDriverServiceTest's own established mocking conventions.
 */
class BikeRentalServiceTest : BehaviorSpec({

    fun newService(
        bikeRepository: BikeRepository = mockk(),
        bikeRentalSessionRepository: BikeRentalSessionRepository = mockk(),
        accountRepository: AccountRepository = mockk(),
        ledgerService: LedgerService = mockk(),
        rateLimiter: RateLimiter = mockk(relaxed = true),
    ) = BikeRentalService(bikeRepository, bikeRentalSessionRepository, accountRepository, ledgerService, rateLimiter)

    Given("a fresh owner account with a real account") {
        val bikeRepository = mockk<BikeRepository>()
        val accountRepository = mockk<AccountRepository>()
        val account = Account(
            id = "account_owner", userId = "owner_1", accountNumber = "1000000001", accountName = "Owner",
            type = AccountType.MAIN, balance = BigDecimal.ZERO, availableBalance = BigDecimal.ZERO,
        )
        every { accountRepository.findByUserIdAndType("owner_1", AccountType.MAIN) } returns account
        val savedSlot = slot<Bike>()
        every { bikeRepository.save(capture(savedSlot)) } answers { firstArg() }
        val service = newService(bikeRepository = bikeRepository, accountRepository = accountRepository)

        When("registering a real bike") {
            val result = service.registerBike("owner_1", BikeType.ELECTRIC, -1.9536, 30.0605)

            Then("a real bike row is saved with the account reused as payout destination") {
                result.ownerUserId shouldBe "owner_1"
                result.accountId shouldBe "account_owner"
                result.type shouldBe BikeType.ELECTRIC
                savedSlot.captured.ownerUserId shouldBe "owner_1"
            }
        }
    }

    // Real bug found live (2026-08-02): registerBike used to have no rate limit at
    // all, unlike every other real "post a listing" creation method in this codebase.
    Given("an owner who has already registered too many real bikes this hour") {
        val bikeRepository = mockk<BikeRepository>()
        val accountRepository = mockk<AccountRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        every { accountRepository.findByUserIdAndType("owner_1", AccountType.MAIN) } returns Account(
            id = "account_owner", userId = "owner_1", accountNumber = "1000000001", accountName = "Owner",
            type = AccountType.MAIN, balance = BigDecimal.ZERO, availableBalance = BigDecimal.ZERO,
        )
        every { rateLimiter.checkLimit("bike:register:owner_1", limit = 10, window = Duration.ofHours(1)) } throws
            RateLimitExceededException("Too many requests")
        val service = newService(bikeRepository = bikeRepository, accountRepository = accountRepository, rateLimiter = rateLimiter)

        When("registering yet another real bike") {
            Then("it real-propagates RateLimitExceededException before ever touching the account") {
                try {
                    service.registerBike("owner_1", BikeType.ELECTRIC, -1.9536, 30.0605)
                    error("expected RateLimitExceededException")
                } catch (e: RateLimitExceededException) {
                    verify(exactly = 0) { accountRepository.findByUserIdAndType(any(), any()) }
                }
            }
        }
    }

    // Real bug found live (2026-08-02): updateLocation used to have no rate limit at
    // all, unlike its direct siblings RideDriverService.updateLocation/
    // DesignatedDriverService.updateLocation (both real 20/minute).
    Given("a real bike owner pushing too many real location updates in one minute") {
        val bikeRepository = mockk<BikeRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        every { rateLimiter.checkLimit("bike:location:owner_1", limit = 20, window = Duration.ofMinutes(1)) } throws
            RateLimitExceededException("Too many requests")
        val service = newService(bikeRepository = bikeRepository, rateLimiter = rateLimiter)

        When("pushing one more real location update") {
            Then("it real-propagates RateLimitExceededException before ever looking up the bike") {
                try {
                    service.updateLocation("owner_1", "bike_1", -1.9, 30.0)
                    error("expected RateLimitExceededException")
                } catch (e: RateLimitExceededException) {
                    verify(exactly = 0) { bikeRepository.findById(any()) }
                }
            }
        }
    }

    Given("a real rider trying to rent their own bike") {
        val bikeRepository = mockk<BikeRepository>()
        val bike = Bike(id = "bike_1", ownerUserId = "owner_1", accountId = "account_owner", type = BikeType.REGULAR, currentLatitude = -1.9, currentLongitude = 30.0)
        every { bikeRepository.findById("bike_1") } returns Optional.of(bike)
        val service = newService(bikeRepository = bikeRepository)

        When("that same owner tries to rent it") {
            Then("the self-rental block fires") {
                try {
                    service.startRental("owner_1", "bike_1", -1.9, 30.0)
                    throw AssertionError("expected BikeSelfRentalException")
                } catch (e: BikeSelfRentalException) {
                    e.message shouldBe "Cannot rent your own bike"
                }
            }
        }
    }

    Given("a bike that is already actively rented") {
        val bikeRepository = mockk<BikeRepository>()
        val bikeRentalSessionRepository = mockk<BikeRentalSessionRepository>()
        val bike = Bike(id = "bike_1", ownerUserId = "owner_1", accountId = "account_owner", type = BikeType.REGULAR, currentLatitude = -1.9, currentLongitude = 30.0, available = true)
        val activeSession = BikeRentalSession(id = "bike_rental_existing", bikeId = "bike_1", riderUserId = "other_rider", startLatitude = -1.9, startLongitude = 30.0)
        every { bikeRepository.findById("bike_1") } returns Optional.of(bike)
        every { bikeRentalSessionRepository.findByBikeIdAndStatus("bike_1", BikeRentalStatus.ACTIVE) } returns activeSession
        val service = newService(bikeRepository = bikeRepository, bikeRentalSessionRepository = bikeRentalSessionRepository)

        When("a new rider tries to start a rental on it") {
            Then("the already-rented conflict fires") {
                try {
                    service.startRental("rider_1", "bike_1", -1.9, 30.0)
                    throw AssertionError("expected BikeNotAvailableException")
                } catch (e: BikeNotAvailableException) {
                    e.message shouldBe "This bike is already rented"
                }
            }
        }
    }

    // Real bug found live (2026-08-02): startRental used to only check for the
    // ABSENCE of an active session, never writing to a shared, lockable row -- a
    // check-then-act race two concurrent riders could both pass. This test proves
    // the fix's real mechanism: starting a rental now saves the bike itself with
    // `available = false`, which is what makes optimistic locking (the bike's own
    // @Version) actually catch a concurrent second rental attempt (the loser's save
    // would real-409 via the existing global ObjectOptimisticLockingFailureException
    // handler, the same proven-safe shape RideTripService.acceptTrip already uses).
    Given("a real available bike with no active session") {
        val bikeRepository = mockk<BikeRepository>()
        val bikeRentalSessionRepository = mockk<BikeRentalSessionRepository>()
        val accountRepository = mockk<AccountRepository>()
        val bike = Bike(id = "bike_1", ownerUserId = "owner_1", accountId = "account_owner", type = BikeType.REGULAR, currentLatitude = -1.9, currentLongitude = 30.0, available = true)
        val riderAccount = Account(
            id = "account_rider", userId = "rider_1", accountNumber = "1000000002", accountName = "Rider",
            type = AccountType.MAIN, balance = BigDecimal("20000"), availableBalance = BigDecimal("20000"),
        )
        every { bikeRepository.findById("bike_1") } returns Optional.of(bike)
        every { bikeRentalSessionRepository.findByBikeIdAndStatus("bike_1", BikeRentalStatus.ACTIVE) } returns null
        every { accountRepository.findByUserIdAndType("rider_1", AccountType.MAIN) } returns riderAccount
        val bikeSavedSlot = slot<Bike>()
        every { bikeRepository.save(capture(bikeSavedSlot)) } answers { firstArg() }
        every { bikeRentalSessionRepository.save(any()) } answers { firstArg() }
        val service = newService(
            bikeRepository = bikeRepository, bikeRentalSessionRepository = bikeRentalSessionRepository, accountRepository = accountRepository,
        )

        When("a rider starts a real rental") {
            service.startRental("rider_1", "bike_1", -1.9, 30.0)

            Then("the bike itself is saved as unavailable, the versioned row a concurrent second attempt would race against") {
                bikeSavedSlot.captured.available shouldBe false
            }
        }
    }

    Given("a real ACTIVE rental the rider ends after 5 minutes") {
        val bikeRepository = mockk<BikeRepository>()
        val bikeRentalSessionRepository = mockk<BikeRentalSessionRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val service = newService(
            bikeRepository = bikeRepository, bikeRentalSessionRepository = bikeRentalSessionRepository,
            accountRepository = accountRepository, ledgerService = ledgerService,
        )

        val bike = Bike(id = "bike_1", ownerUserId = "owner_1", accountId = "account_owner", type = BikeType.REGULAR, currentLatitude = -1.9, currentLongitude = 30.0)
        val riderAccount = Account(
            id = "account_rider", userId = "rider_1", accountNumber = "1000000002", accountName = "Rider",
            type = AccountType.MAIN, balance = BigDecimal("20000"), availableBalance = BigDecimal("20000"),
        )
        val ownerAccount = Account(
            id = "account_owner", userId = "owner_1", accountNumber = "1000000001", accountName = "Owner",
            type = AccountType.MAIN, balance = BigDecimal.ZERO, availableBalance = BigDecimal.ZERO,
        )
        val session = BikeRentalSession(
            id = "bike_rental_1", bikeId = "bike_1", riderUserId = "rider_1",
            startedAt = Instant.now().minusSeconds(5 * 60), startLatitude = -1.9, startLongitude = 30.0,
        )
        every { bikeRentalSessionRepository.findById("bike_rental_1") } returns Optional.of(session)
        every { bikeRepository.findById("bike_1") } returns Optional.of(bike)
        every { accountRepository.findByUserIdAndType("rider_1", AccountType.MAIN) } returns riderAccount
        every { accountRepository.findById("account_owner") } returns Optional.of(ownerAccount)
        every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_1", emptyList())
        val bikeSavedSlot = slot<Bike>()
        every { bikeRentalSessionRepository.save(any()) } answers { firstArg() }
        every { bikeRepository.save(capture(bikeSavedSlot)) } answers { firstArg() }

        When("the rider ends the rental") {
            val result = service.endRental("rider_1", "bike_rental_1", -1.95, 30.05)

            Then("a real fare is computed for 5 minutes, the rental is marked COMPLETED, and the bike is made available again") {
                result.status shouldBe BikeRentalStatus.COMPLETED
                result.durationMinutes shouldBe 5
                // REGULAR rate is 80/minute -- 5 * 80 = 400.
                result.totalFare shouldBe BigDecimal("400.00")
                result.platformFee shouldBe BigDecimal("60.00")
                result.payoutTransactionId shouldBe "ledgertxn_1"
                bikeSavedSlot.captured.available shouldBe true
            }
        }
    }

    // Real bug fixed live (2026-08-18): an ACTIVE session had no timeout at all -- see
    // BikeRentalSession.MAX_RENTAL_DURATION's own doc comment for the sourced Citi Bike
    // 24-hour account. Positive-control case: a session still well within the real
    // window must NOT be force-ended -- the scheduler polling `getAbandonedRentals`
    // would never even hand this row to `forceEndAbandonedRental` in production, but
    // the re-check inside the method itself is what actually prevents a stray/late call
    // from prematurely billing a rider who is still genuinely riding.
    Given("a real ACTIVE rental only 2 hours old, well within the max rental window") {
        val bikeRepository = mockk<BikeRepository>()
        val bikeRentalSessionRepository = mockk<BikeRentalSessionRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val service = newService(
            bikeRepository = bikeRepository, bikeRentalSessionRepository = bikeRentalSessionRepository,
            accountRepository = accountRepository, ledgerService = ledgerService,
        )
        val session = BikeRentalSession(
            id = "bike_rental_recent", bikeId = "bike_1", riderUserId = "rider_1",
            startedAt = Instant.now().minus(Duration.ofHours(2)), startLatitude = -1.9, startLongitude = 30.0,
        )
        every { bikeRentalSessionRepository.findById("bike_rental_recent") } returns Optional.of(session)

        When("the scheduler's force-end is (incorrectly) invoked on it anyway") {
            val result = service.forceEndAbandonedRental("bike_rental_recent")

            Then("it is a real no-op -- still ACTIVE, no ledger transaction posted, no bike/account ever looked up") {
                result?.status shouldBe BikeRentalStatus.ACTIVE
                verify(exactly = 0) { bikeRepository.findById(any()) }
                verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
            }
        }
    }

    // Rejection case's positive counterpart: a session genuinely abandoned past the real
    // Citi Bike-sourced 24-hour window IS force-settled, using the exact same billing
    // math `endRental` uses (proving `settleRental` was actually reused, not
    // reimplemented), ending at the bike's own last-known location since there's no
    // fresh GPS ping from an abandoned rider to use instead.
    Given("a real ACTIVE rental abandoned 25 hours ago, past the max rental window") {
        val bikeRepository = mockk<BikeRepository>()
        val bikeRentalSessionRepository = mockk<BikeRentalSessionRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val service = newService(
            bikeRepository = bikeRepository, bikeRentalSessionRepository = bikeRentalSessionRepository,
            accountRepository = accountRepository, ledgerService = ledgerService,
        )

        val bike = Bike(id = "bike_1", ownerUserId = "owner_1", accountId = "account_owner", type = BikeType.ELECTRIC, currentLatitude = -1.90, currentLongitude = 30.00)
        val riderAccount = Account(
            id = "account_rider", userId = "rider_1", accountNumber = "1000000002", accountName = "Rider",
            type = AccountType.MAIN, balance = BigDecimal("1000000"), availableBalance = BigDecimal("1000000"),
        )
        val ownerAccount = Account(
            id = "account_owner", userId = "owner_1", accountNumber = "1000000001", accountName = "Owner",
            type = AccountType.MAIN, balance = BigDecimal.ZERO, availableBalance = BigDecimal.ZERO,
        )
        val session = BikeRentalSession(
            id = "bike_rental_abandoned", bikeId = "bike_1", riderUserId = "rider_1",
            startedAt = Instant.now().minus(Duration.ofHours(25)), startLatitude = -1.9, startLongitude = 30.0,
        )
        every { bikeRentalSessionRepository.findById("bike_rental_abandoned") } returns Optional.of(session)
        every { bikeRepository.findById("bike_1") } returns Optional.of(bike)
        every { accountRepository.findByUserIdAndType("rider_1", AccountType.MAIN) } returns riderAccount
        every { accountRepository.findById("account_owner") } returns Optional.of(ownerAccount)
        every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_forceend", emptyList())
        val bikeSavedSlot = slot<Bike>()
        every { bikeRentalSessionRepository.save(any()) } answers { firstArg() }
        every { bikeRepository.save(capture(bikeSavedSlot)) } answers { firstArg() }

        When("the scheduler force-ends the abandoned rental") {
            val result = service.forceEndAbandonedRental("bike_rental_abandoned")

            Then("the rider is real-billed for the full 25 hours, the owner is paid, and the bike re-enters the pool") {
                result?.status shouldBe BikeRentalStatus.COMPLETED
                result?.durationMinutes shouldBe 25 * 60
                // ELECTRIC rate is 150/minute -- 1500 minutes * 150 = 225,000.
                result?.totalFare shouldBe BigDecimal("225000.00")
                result?.platformFee shouldBe BigDecimal("33750.00")
                result?.payoutTransactionId shouldBe "ledgertxn_forceend"
                // Ends at the bike's own last-known location, not a rider-supplied one.
                result?.endLatitude shouldBe -1.90
                result?.endLongitude shouldBe 30.00
                bikeSavedSlot.captured.available shouldBe true
            }
        }
    }
})
