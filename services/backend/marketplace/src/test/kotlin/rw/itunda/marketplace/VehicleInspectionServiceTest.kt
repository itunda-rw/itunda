package rw.itunda.marketplace

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.Listing
import rw.itunda.core.domain.ListingStatus
import rw.itunda.core.domain.VehicleInspectionBooking
import rw.itunda.core.domain.VehicleInspectionMechanic
import rw.itunda.core.domain.VehicleInspectionStatus
import rw.itunda.core.domain.Account
import rw.itunda.core.domain.AccountType
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerPostResult
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.ListingRepository
import rw.itunda.core.repository.TransactionRepository
import rw.itunda.core.repository.VehicleInspectionBookingRepository
import rw.itunda.core.repository.VehicleInspectionMechanicRepository
import rw.itunda.core.repository.AccountRepository
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.util.Optional

/**
 * First test coverage for the real 당근마켓 중고차 정비소 동행 (used-car
 * mechanic-inspection accompaniment) -- see VehicleInspectionMechanic.kt/
 * VehicleInspectionBooking.kt's own doc comments for the full sourced account.
 */
class VehicleInspectionServiceTest : BehaviorSpec({

    fun account(id: String, userId: String) = Account(
        id = id, userId = userId, accountNumber = "ACC-$id", accountName = "Test account",
        type = AccountType.MAIN, balance = BigDecimal("100000"), availableBalance = BigDecimal("100000"),
    )

    fun newService(
        vehicleInspectionMechanicRepository: VehicleInspectionMechanicRepository = mockk(),
        vehicleInspectionBookingRepository: VehicleInspectionBookingRepository = mockk(),
        listingRepository: ListingRepository = mockk(),
        accountRepository: AccountRepository = mockk(),
        transactionRepository: TransactionRepository = mockk<TransactionRepository>(relaxed = true).also { every { it.save(any()) } answers { firstArg() } },
        ledgerService: LedgerService = mockk(),
        rateLimiter: RateLimiter = mockk(relaxed = true),
    ) = VehicleInspectionService(
        vehicleInspectionMechanicRepository, vehicleInspectionBookingRepository, listingRepository, accountRepository, transactionRepository, ledgerService, rateLimiter,
    )

    Given("a real user registering as a mechanic") {
        val vehicleInspectionMechanicRepository = mockk<VehicleInspectionMechanicRepository>()
        val accountRepository = mockk<AccountRepository>()
        val service = newService(vehicleInspectionMechanicRepository = vehicleInspectionMechanicRepository, accountRepository = accountRepository)

        every { vehicleInspectionMechanicRepository.findByUserId("user_1") } returns null
        every { accountRepository.findByUserIdAndType("user_1", AccountType.MAIN) } returns account("account_1", "user_1")
        every { vehicleInspectionMechanicRepository.save(any()) } answers { firstArg() }

        When("registering with a real business name") {
            val mechanic = service.registerAsMechanic("user_1", "  Kigali Auto Care  ")

            Then("it real-trims the name and real-defaults to available") {
                mechanic.businessName shouldBe "Kigali Auto Care"
                mechanic.available shouldBe true
            }
        }

        When("registering a second real mechanic profile") {
            every { vehicleInspectionMechanicRepository.findByUserId("user_1") } returns
                VehicleInspectionMechanic(id = "mechanic_1", userId = "user_1", accountId = "account_1", businessName = "Existing")

            Then("it throws MechanicAlreadyRegisteredException") {
                try {
                    service.registerAsMechanic("user_1", "New name")
                    error("expected MechanicAlreadyRegisteredException")
                } catch (e: MechanicAlreadyRegisteredException) {
                    // expected
                }
            }
        }
    }

    Given("a real buyer requesting a real inspection of a real listing") {
        val vehicleInspectionMechanicRepository = mockk<VehicleInspectionMechanicRepository>()
        val vehicleInspectionBookingRepository = mockk<VehicleInspectionBookingRepository>()
        val listingRepository = mockk<ListingRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val service = newService(
            vehicleInspectionMechanicRepository = vehicleInspectionMechanicRepository, vehicleInspectionBookingRepository = vehicleInspectionBookingRepository,
            listingRepository = listingRepository, accountRepository = accountRepository, ledgerService = ledgerService, rateLimiter = rateLimiter,
        )

        val listing = Listing(
            id = "listing_1", sellerId = "seller_1", title = "2018 Toyota RAV4", description = "Clean title",
            price = BigDecimal("8000000"), category = "vehicles", status = ListingStatus.ACTIVE,
        )
        val mechanic = VehicleInspectionMechanic(id = "mechanic_1", userId = "mechanic_user_1", accountId = "account_mechanic", businessName = "Kigali Auto Care")
        every { listingRepository.findById("listing_1") } returns Optional.of(listing)
        every { vehicleInspectionMechanicRepository.findById("mechanic_1") } returns Optional.of(mechanic)
        every { accountRepository.findByUserIdAndType("buyer_1", AccountType.MAIN) } returns account("account_buyer", "buyer_1")
        every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_1", emptyList())
        val savedSlot = slot<VehicleInspectionBooking>()
        every { vehicleInspectionBookingRepository.save(capture(savedSlot)) } answers { firstArg() }

        When("paying a real 15,000 RWF inspection fee") {
            val legsSlot = mutableListOf<List<LedgerLeg>>()
            every { ledgerService.postLedgerTransaction(any(), capture(legsSlot)) } returns LedgerPostResult("ledgertxn_inspect_1", emptyList())

            val booking = service.requestInspection("buyer_1", "listing_1", "mechanic_1", BigDecimal("15000"), Instant.now().plusSeconds(86400))

            Then("it real-holds the fee in escrow and real-opens a REQUESTED booking") {
                booking.status shouldBe VehicleInspectionStatus.REQUESTED
                booking.fee shouldBe BigDecimal("15000")
                val creditLeg = legsSlot.first().first { it.direction == LedgerDirection.CREDIT }
                creditLeg.accountType shouldBe LedgerAccountType.VEHICLE_INSPECTION_HOLDING
                val debitLeg = legsSlot.first().first { it.direction == LedgerDirection.DEBIT }
                debitLeg.accountId shouldBe "account_buyer"
            }
        }

        When("the mechanic tries to book an inspection with themselves") {
            Then("it throws SelfInspectionException before touching the ledger") {
                try {
                    service.requestInspection("mechanic_user_1", "listing_1", "mechanic_1", BigDecimal("15000"), Instant.now().plusSeconds(86400))
                    error("expected SelfInspectionException")
                } catch (e: SelfInspectionException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }

        When("requesting with a real zero fee") {
            Then("it throws InvalidInspectionFeeException before ever looking up the listing") {
                try {
                    service.requestInspection("buyer_1", "listing_1", "mechanic_1", BigDecimal.ZERO, Instant.now().plusSeconds(86400))
                    error("expected InvalidInspectionFeeException")
                } catch (e: InvalidInspectionFeeException) {
                    verify(exactly = 0) { listingRepository.findById(any()) }
                }
            }
        }

        // Real bug found live (2026-08-02) -- see requestInspection's own doc comment:
        // this endpoint had no rate limit at all, unlike every other real "request a
        // paid service" creation method in this codebase.
        When("a real buyer exceeds the real inspection-request rate limit") {
            every { rateLimiter.checkLimit("marketplace:inspection-request:buyer_1", limit = 20, window = Duration.ofHours(1)) } throws
                RateLimitExceededException("Too many requests")

            Then("it real-propagates RateLimitExceededException before ever touching the ledger") {
                try {
                    service.requestInspection("buyer_1", "listing_1", "mechanic_1", BigDecimal("15000"), Instant.now().plusSeconds(86400))
                    error("expected RateLimitExceededException")
                } catch (e: RateLimitExceededException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }
    }

    Given("a real mechanic with a real ACCEPTED booking, completing the inspection") {
        val vehicleInspectionMechanicRepository = mockk<VehicleInspectionMechanicRepository>()
        val vehicleInspectionBookingRepository = mockk<VehicleInspectionBookingRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val service = newService(
            vehicleInspectionMechanicRepository = vehicleInspectionMechanicRepository, vehicleInspectionBookingRepository = vehicleInspectionBookingRepository,
            accountRepository = accountRepository, ledgerService = ledgerService,
        )

        val mechanic = VehicleInspectionMechanic(id = "mechanic_1", userId = "mechanic_user_1", accountId = "account_mechanic", businessName = "Kigali Auto Care")
        val booking = VehicleInspectionBooking(
            id = "inspection_1", listingId = "listing_1", buyerId = "buyer_1", mechanicId = "mechanic_1",
            fee = BigDecimal("15000"), platformFee = BigDecimal("225"), scheduledFor = Instant.now(), holdTransactionId = "ledgertxn_1",
            status = VehicleInspectionStatus.ACCEPTED,
        )
        every { vehicleInspectionMechanicRepository.findByUserId("mechanic_user_1") } returns mechanic
        every { vehicleInspectionBookingRepository.findById("inspection_1") } returns Optional.of(booking)
        every { vehicleInspectionMechanicRepository.findById("mechanic_1") } returns Optional.of(mechanic)
        every { accountRepository.findById("account_mechanic") } returns Optional.of(account("account_mechanic", "mechanic_user_1"))
        every { vehicleInspectionBookingRepository.save(any()) } answers { firstArg() }

        When("marking it complete with real findings") {
            val legsSlot = mutableListOf<List<LedgerLeg>>()
            every { ledgerService.postLedgerTransaction(any(), capture(legsSlot)) } returns LedgerPostResult("ledgertxn_complete_1", emptyList())

            val completed = service.completeInspection("mechanic_user_1", "inspection_1", "  Minor brake wear, otherwise sound.  ")

            Then("it real-pays out net of the real platform fee and real-records the findings") {
                completed.status shouldBe VehicleInspectionStatus.COMPLETED
                completed.findings shouldBe "Minor brake wear, otherwise sound."
                val creditLeg = legsSlot.first().first { it.accountId == "account_mechanic" }
                creditLeg.amount shouldBe BigDecimal("14775")
            }
        }
    }

    Given("a real buyer cancelling a real REQUESTED booking") {
        val vehicleInspectionBookingRepository = mockk<VehicleInspectionBookingRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val service = newService(vehicleInspectionBookingRepository = vehicleInspectionBookingRepository, accountRepository = accountRepository, ledgerService = ledgerService)

        val booking = VehicleInspectionBooking(
            id = "inspection_1", listingId = "listing_1", buyerId = "buyer_1", mechanicId = "mechanic_1",
            fee = BigDecimal("15000"), platformFee = BigDecimal("225"), scheduledFor = Instant.now(), holdTransactionId = "ledgertxn_1",
        )
        every { vehicleInspectionBookingRepository.findById("inspection_1") } returns Optional.of(booking)
        every { accountRepository.findByUserIdAndType("buyer_1", AccountType.MAIN) } returns account("account_buyer", "buyer_1")
        every { vehicleInspectionBookingRepository.save(any()) } answers { firstArg() }

        When("cancelling before the mechanic accepts") {
            val legsSlot = mutableListOf<List<LedgerLeg>>()
            every { ledgerService.postLedgerTransaction(any(), capture(legsSlot)) } returns LedgerPostResult("ledgertxn_cancel_1", emptyList())

            val cancelled = service.cancelInspection("buyer_1", "inspection_1")

            Then("it real-refunds the full fee, no platform fee taken") {
                cancelled.status shouldBe VehicleInspectionStatus.CANCELLED
                val creditLeg = legsSlot.first().first { it.direction == LedgerDirection.CREDIT }
                creditLeg.accountId shouldBe "account_buyer"
                creditLeg.amount shouldBe BigDecimal("15000")
            }
        }
    }

    // Real bug fix (2026-08-18) -- see VehicleInspectionStatus.NO_SHOW's own doc
    // comment: cancelInspection had no time-based check at all, so a buyer could let a
    // mechanic travel to/perform the real inspection and then cancel arbitrarily late
    // for a full refund. VehicleInspectionNoShowScheduler + processNoShow close this.
    Given("a real ACCEPTED booking whose scheduled time has already passed") {
        val vehicleInspectionMechanicRepository = mockk<VehicleInspectionMechanicRepository>()
        val vehicleInspectionBookingRepository = mockk<VehicleInspectionBookingRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val service = newService(
            vehicleInspectionMechanicRepository = vehicleInspectionMechanicRepository, vehicleInspectionBookingRepository = vehicleInspectionBookingRepository,
            accountRepository = accountRepository, ledgerService = ledgerService,
        )

        val mechanic = VehicleInspectionMechanic(id = "mechanic_1", userId = "mechanic_user_1", accountId = "account_mechanic", businessName = "Kigali Auto Care")
        val overdueBooking = VehicleInspectionBooking(
            id = "inspection_overdue", listingId = "listing_1", buyerId = "buyer_1", mechanicId = "mechanic_1",
            fee = BigDecimal("15000"), platformFee = BigDecimal("225"), scheduledFor = Instant.now().minusSeconds(3600),
            holdTransactionId = "ledgertxn_1", status = VehicleInspectionStatus.ACCEPTED,
        )

        When("the scheduler's poll runs") {
            every { vehicleInspectionBookingRepository.findByStatusAndScheduledForBefore(VehicleInspectionStatus.ACCEPTED, any()) } returns listOf(overdueBooking)

            Then("it real-finds the overdue booking as a no-show candidate") {
                service.findDueNoShows() shouldBe listOf(overdueBooking)
            }
        }

        When("processNoShow re-checks and settles it") {
            every { vehicleInspectionBookingRepository.findById("inspection_overdue") } returns Optional.of(overdueBooking)
            every { vehicleInspectionMechanicRepository.findById("mechanic_1") } returns Optional.of(mechanic)
            every { accountRepository.findById("account_mechanic") } returns Optional.of(account("account_mechanic", "mechanic_user_1"))
            every { vehicleInspectionBookingRepository.save(any()) } answers { firstArg() }
            val legsSlot = mutableListOf<List<LedgerLeg>>()
            every { ledgerService.postLedgerTransaction(any(), capture(legsSlot)) } returns LedgerPostResult("ledgertxn_noshow_1", emptyList())

            val resolved = service.processNoShow("inspection_overdue")

            Then("it real-forfeits the fee to the mechanic net of the platform fee, marks NO_SHOW") {
                resolved shouldBe overdueBooking
                overdueBooking.status shouldBe VehicleInspectionStatus.NO_SHOW
                val creditLeg = legsSlot.first().first { it.direction == LedgerDirection.CREDIT && it.accountId == "account_mechanic" }
                creditLeg.amount shouldBe BigDecimal("14775")
            }

            Then("the buyer can no longer cancel it for a refund") {
                every { vehicleInspectionBookingRepository.findById("inspection_overdue") } returns Optional.of(overdueBooking)
                try {
                    service.cancelInspection("buyer_1", "inspection_overdue")
                    error("expected InvalidInspectionStatusTransitionException")
                } catch (e: InvalidInspectionStatusTransitionException) {
                    verify(exactly = 0) { accountRepository.findByUserIdAndType("buyer_1", AccountType.MAIN) }
                }
            }
        }
    }

    // Positive control -- a booking that's still genuinely on time (or already resolved
    // some other way) must NOT be swept up and forfeited. Proves the fix doesn't
    // over-block a real still-pending or already-settled booking.
    Given("real bookings the no-show sweep must leave completely alone") {
        val vehicleInspectionMechanicRepository = mockk<VehicleInspectionMechanicRepository>()
        val vehicleInspectionBookingRepository = mockk<VehicleInspectionBookingRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val service = newService(
            vehicleInspectionMechanicRepository = vehicleInspectionMechanicRepository, vehicleInspectionBookingRepository = vehicleInspectionBookingRepository,
            accountRepository = accountRepository, ledgerService = ledgerService,
        )

        When("the scheduled time hasn't passed yet") {
            every { vehicleInspectionBookingRepository.findByStatusAndScheduledForBefore(VehicleInspectionStatus.ACCEPTED, any()) } returns emptyList()

            Then("the poll real-finds nothing due") {
                service.findDueNoShows() shouldBe emptyList()
            }
        }

        When("processNoShow is called for a booking the mechanic already completed in the gap between the poll and now") {
            val alreadyCompleted = VehicleInspectionBooking(
                id = "inspection_done", listingId = "listing_1", buyerId = "buyer_1", mechanicId = "mechanic_1",
                fee = BigDecimal("15000"), platformFee = BigDecimal("225"), scheduledFor = Instant.now().minusSeconds(3600),
                holdTransactionId = "ledgertxn_1", status = VehicleInspectionStatus.COMPLETED,
            )
            every { vehicleInspectionBookingRepository.findById("inspection_done") } returns Optional.of(alreadyCompleted)

            Then("it real-re-checks state and safely no-ops, never double-settles") {
                val result = service.processNoShow("inspection_done")
                result shouldBe null
                verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
            }
        }

        When("processNoShow is called for a still-genuinely-ACCEPTED booking that isn't overdue yet") {
            val notYetDue = VehicleInspectionBooking(
                id = "inspection_pending", listingId = "listing_1", buyerId = "buyer_1", mechanicId = "mechanic_1",
                fee = BigDecimal("15000"), platformFee = BigDecimal("225"), scheduledFor = Instant.now().plusSeconds(3600),
                holdTransactionId = "ledgertxn_1", status = VehicleInspectionStatus.ACCEPTED,
            )
            every { vehicleInspectionBookingRepository.findById("inspection_pending") } returns Optional.of(notYetDue)

            Then("it real-leaves the still-on-time booking untouched") {
                val result = service.processNoShow("inspection_pending")
                result shouldBe null
                verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
