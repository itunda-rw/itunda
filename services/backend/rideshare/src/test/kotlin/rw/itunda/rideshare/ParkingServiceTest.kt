package rw.itunda.rideshare

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
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

        When("the renter ends the session") {
            val result = service.endSession("renter_1", "parking_session_1")

            Then("a real fare rounds 90 minutes up to 2 billed hours and the session is marked COMPLETED") {
                result.status shouldBe ParkingSessionStatus.COMPLETED
                result.durationMinutes shouldBe 90
                // 500/hour, 90 minutes rounds up to 2 hours -- 2 * 500 = 1000.
                result.totalFare shouldBe BigDecimal("1000.00")
                result.platformFee shouldBe BigDecimal("150.00")
                result.payoutTransactionId shouldBe "ledgertxn_1"
            }
        }
    }
})
