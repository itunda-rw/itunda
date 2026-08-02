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
import rw.itunda.core.domain.Wallet
import rw.itunda.core.domain.WalletType
import rw.itunda.core.ledger.LedgerPostResult
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.ParkingSessionRepository
import rw.itunda.core.repository.ParkingSpotRepository
import rw.itunda.core.repository.WalletRepository
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
        walletRepository: WalletRepository = mockk(),
        ledgerService: LedgerService = mockk(),
        rateLimiter: RateLimiter = mockk(relaxed = true),
    ) = ParkingService(parkingSpotRepository, parkingSessionRepository, walletRepository, ledgerService, rateLimiter)

    Given("a fresh owner account with a real wallet") {
        val parkingSpotRepository = mockk<ParkingSpotRepository>()
        val walletRepository = mockk<WalletRepository>()
        val wallet = Wallet(
            id = "wallet_owner", userId = "owner_1", accountNumber = "1000000001", accountName = "Owner",
            type = WalletType.MAIN, balance = BigDecimal.ZERO, availableBalance = BigDecimal.ZERO,
        )
        every { walletRepository.findByUserIdAndType("owner_1", WalletType.MAIN) } returns wallet
        val savedSlot = slot<ParkingSpot>()
        every { parkingSpotRepository.save(capture(savedSlot)) } answers { firstArg() }
        val service = newService(parkingSpotRepository = parkingSpotRepository, walletRepository = walletRepository)

        When("registering a real parking spot") {
            val result = service.registerSpot("owner_1", "Kigali Heights driveway", -1.9536, 30.0605, BigDecimal("500"))

            Then("a real spot row is saved with the wallet reused as payout destination") {
                result.ownerUserId shouldBe "owner_1"
                result.walletId shouldBe "wallet_owner"
                result.hourlyRate shouldBe BigDecimal("500")
                savedSlot.captured.ownerUserId shouldBe "owner_1"
            }
        }
    }

    // Real bug found live (2026-08-02): registerSpot used to have no rate limit at
    // all, unlike every other real "post a listing" creation method in this codebase.
    Given("an owner who has already registered too many real parking spots this hour") {
        val parkingSpotRepository = mockk<ParkingSpotRepository>()
        val walletRepository = mockk<WalletRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        every { walletRepository.findByUserIdAndType("owner_1", WalletType.MAIN) } returns Wallet(
            id = "wallet_owner", userId = "owner_1", accountNumber = "1000000001", accountName = "Owner",
            type = WalletType.MAIN, balance = BigDecimal.ZERO, availableBalance = BigDecimal.ZERO,
        )
        every { rateLimiter.checkLimit("parking:register:owner_1", limit = 10, window = Duration.ofHours(1)) } throws
            RateLimitExceededException("Too many requests")
        val service = newService(parkingSpotRepository = parkingSpotRepository, walletRepository = walletRepository, rateLimiter = rateLimiter)

        When("registering yet another real parking spot") {
            Then("it real-propagates RateLimitExceededException before ever touching the wallet") {
                try {
                    service.registerSpot("owner_1", "Kigali Heights driveway", -1.9536, 30.0605, BigDecimal("500"))
                    error("expected RateLimitExceededException")
                } catch (e: RateLimitExceededException) {
                    verify(exactly = 0) { walletRepository.findByUserIdAndType(any(), any()) }
                }
            }
        }
    }

    Given("a real renter trying to rent their own parking spot") {
        val parkingSpotRepository = mockk<ParkingSpotRepository>()
        val spot = ParkingSpot(
            id = "parking_spot_1", ownerUserId = "owner_1", walletId = "wallet_owner", address = "A",
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
            id = "parking_spot_1", ownerUserId = "owner_1", walletId = "wallet_owner", address = "A",
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
        val walletRepository = mockk<WalletRepository>()
        val spot = ParkingSpot(
            id = "parking_spot_1", ownerUserId = "owner_1", walletId = "wallet_owner", address = "A",
            latitude = -1.9, longitude = 30.0, hourlyRate = BigDecimal("500"), available = true,
        )
        val renterWallet = Wallet(
            id = "wallet_renter", userId = "renter_1", accountNumber = "1000000002", accountName = "Renter",
            type = WalletType.MAIN, balance = BigDecimal("20000"), availableBalance = BigDecimal("20000"),
        )
        every { parkingSpotRepository.findById("parking_spot_1") } returns Optional.of(spot)
        every { parkingSessionRepository.findBySpotIdAndStatus("parking_spot_1", ParkingSessionStatus.ACTIVE) } returns null
        every { walletRepository.findByUserIdAndType("renter_1", WalletType.MAIN) } returns renterWallet
        val spotSavedSlot = slot<ParkingSpot>()
        every { parkingSpotRepository.save(capture(spotSavedSlot)) } answers { firstArg() }
        every { parkingSessionRepository.save(any()) } answers { firstArg() }
        val service = newService(
            parkingSpotRepository = parkingSpotRepository, parkingSessionRepository = parkingSessionRepository, walletRepository = walletRepository,
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
        val walletRepository = mockk<WalletRepository>()
        val ledgerService = mockk<LedgerService>()
        val service = newService(
            parkingSpotRepository = parkingSpotRepository, parkingSessionRepository = parkingSessionRepository,
            walletRepository = walletRepository, ledgerService = ledgerService,
        )

        val spot = ParkingSpot(
            id = "parking_spot_1", ownerUserId = "owner_1", walletId = "wallet_owner", address = "A",
            latitude = -1.9, longitude = 30.0, hourlyRate = BigDecimal("500"),
        )
        val renterWallet = Wallet(
            id = "wallet_renter", userId = "renter_1", accountNumber = "1000000002", accountName = "Renter",
            type = WalletType.MAIN, balance = BigDecimal("20000"), availableBalance = BigDecimal("20000"),
        )
        val ownerWallet = Wallet(
            id = "wallet_owner", userId = "owner_1", accountNumber = "1000000001", accountName = "Owner",
            type = WalletType.MAIN, balance = BigDecimal.ZERO, availableBalance = BigDecimal.ZERO,
        )
        val session = ParkingSession(
            id = "parking_session_1", spotId = "parking_spot_1", renterUserId = "renter_1",
            startedAt = Instant.now().minusSeconds(90 * 60),
        )
        every { parkingSessionRepository.findById("parking_session_1") } returns Optional.of(session)
        every { parkingSpotRepository.findById("parking_spot_1") } returns Optional.of(spot)
        every { walletRepository.findByUserIdAndType("renter_1", WalletType.MAIN) } returns renterWallet
        every { walletRepository.findById("wallet_owner") } returns Optional.of(ownerWallet)
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
        }
    }
})
