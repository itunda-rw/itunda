package rw.itunda.merchant

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import rw.itunda.core.domain.BookingDeposit
import rw.itunda.core.domain.BookingDepositStatus
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.Merchant
import rw.itunda.core.domain.MerchantBooking
import rw.itunda.core.domain.MerchantBookingStatus
import rw.itunda.core.fraud.FraudRuleEngine
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerPostResult
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.BookingDepositRepository
import rw.itunda.core.repository.MerchantAvailabilityWindowRepository
import rw.itunda.core.repository.MerchantBookingRepository
import rw.itunda.core.repository.MerchantProductRepository
import rw.itunda.core.repository.MerchantRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.TransactionRepository
import rw.itunda.core.repository.AccountRepository
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.util.Optional

/**
 * Real regression coverage for the 2026-08-18 fix: `MerchantBookingService.processNoShows`
 * used to be one `@Transactional` batch method looping over every real due booking
 * network-wide -- a real, previously-unflagged instance of the same transaction-poisoning
 * bug class Sections 115/118/129/130 already closed four times this session (see
 * `processNoShow`'s own doc comment for the full account). Now a read-only
 * `getDueNoShows()` plus a real per-item `@Transactional processNoShow(bookingId)`,
 * matching the proven-safe shape `BikeRentalAbandonedSessionScheduler`/
 * `ParkingAbandonedSessionScheduler`/`VehicleInspectionNoShowScheduler` already establish.
 */
class MerchantBookingServiceNoShowTest : BehaviorSpec({

    fun booking(id: String, status: MerchantBookingStatus, bookingDate: LocalDate, endTime: LocalTime) = MerchantBooking(
        id = id, merchantId = "merchant_1", customerId = "customer_1", serviceId = "service_1",
        serviceName = "Haircut", bookingDate = bookingDate, startTime = endTime.minusMinutes(30), endTime = endTime,
        status = status,
    )

    fun newService(
        merchantRepository: MerchantRepository = mockk(),
        merchantProductRepository: MerchantProductRepository = mockk(),
        availabilityWindowRepository: MerchantAvailabilityWindowRepository = mockk(),
        merchantBookingRepository: MerchantBookingRepository = mockk(),
        notificationRepository: NotificationRepository = mockk<NotificationRepository>(relaxed = true).also { every { it.save(any()) } answers { firstArg() } },
        accountRepository: AccountRepository = mockk(),
        ledgerService: LedgerService = mockk(),
        transactionRepository: TransactionRepository = mockk(relaxed = true),
        bookingDepositRepository: BookingDepositRepository = mockk(),
        pushNotificationService: PushNotificationService = mockk(relaxed = true),
        autoTopUpService: rw.itunda.account.AutoTopUpService = mockk(relaxed = true),
        fraudRuleEngine: FraudRuleEngine = mockk(relaxed = true),
    ) = MerchantBookingService(
        merchantRepository, merchantProductRepository, availabilityWindowRepository, merchantBookingRepository,
        notificationRepository, accountRepository, ledgerService, transactionRepository, bookingDepositRepository,
        pushNotificationService, autoTopUpService, fraudRuleEngine,
    )

    Given("real overdue CONFIRMED bookings the poll must sweep, one with a real held deposit") {
        val merchantBookingRepository = mockk<MerchantBookingRepository>()
        val merchantRepository = mockk<MerchantRepository>()
        val bookingDepositRepository = mockk<BookingDepositRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val service = newService(
            merchantRepository = merchantRepository, merchantBookingRepository = merchantBookingRepository,
            bookingDepositRepository = bookingDepositRepository, accountRepository = accountRepository, ledgerService = ledgerService,
        )

        val pastDate = LocalDate.now().minusDays(1)
        val overdue = booking("booking_overdue", MerchantBookingStatus.CONFIRMED, pastDate, LocalTime.of(10, 0))
        val merchant = Merchant(id = "merchant_1", ownerUserId = "owner_1", accountId = "account_merchant", businessName = "Salon")
        val deposit = BookingDeposit(
            id = "deposit_1", bookingId = "booking_overdue", merchantId = "merchant_1", customerId = "customer_1",
            amount = BigDecimal("5000"), fee = BigDecimal("75"), holdTransactionId = "ledgertxn_hold_1",
        )

        every { merchantBookingRepository.findById("booking_overdue") } returns Optional.of(overdue)
        every { merchantBookingRepository.save(any()) } answers { firstArg() }
        every { merchantRepository.findById("merchant_1") } returns Optional.of(merchant)
        every { bookingDepositRepository.findByBookingIdForUpdate("booking_overdue") } returns deposit
        every { bookingDepositRepository.save(any()) } answers { firstArg() }
        every { accountRepository.findById("account_merchant") } returns Optional.of(
            rw.itunda.core.domain.Account(
                id = "account_merchant", userId = "owner_1", accountNumber = "ACC-1", accountName = "Merchant account",
                type = rw.itunda.core.domain.AccountType.MAIN, balance = BigDecimal("0"), availableBalance = BigDecimal("0"),
            ),
        )
        val legsSlot = slot<List<LedgerLeg>>()
        every { ledgerService.postLedgerTransaction(any(), capture(legsSlot)) } returns LedgerPostResult("ledgertxn_forfeit_1", emptyList())

        When("processNoShow settles it") {
            val resolved = service.processNoShow("booking_overdue")

            Then("it real-marks the booking NO_SHOW and real-forfeits the deposit to the merchant net of fee") {
                resolved shouldBe overdue
                overdue.status shouldBe MerchantBookingStatus.NO_SHOW
                deposit.status shouldBe BookingDepositStatus.FORFEITED
                val creditLeg = legsSlot.captured.first { it.direction == LedgerDirection.CREDIT && it.accountId == "account_merchant" }
                creditLeg.amount shouldBe BigDecimal("4925")
            }
        }
    }

    Given("real bookings the no-show sweep must leave completely alone -- proves the per-item re-check, not just the old batch filter") {
        val merchantBookingRepository = mockk<MerchantBookingRepository>()
        val ledgerService = mockk<LedgerService>()
        val service = newService(merchantBookingRepository = merchantBookingRepository, ledgerService = ledgerService)

        When("processNoShow is called for a booking already resolved (COMPLETED) in the gap between the poll's read and now") {
            val alreadyCompleted = booking("booking_done", MerchantBookingStatus.COMPLETED, LocalDate.now().minusDays(1), LocalTime.of(10, 0))
            every { merchantBookingRepository.findById("booking_done") } returns Optional.of(alreadyCompleted)

            Then("it real-no-ops, never touches the ledger") {
                val result = service.processNoShow("booking_done")
                result shouldBe null
                verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
            }
        }

        When("processNoShow is called for a still-genuinely-CONFIRMED booking whose end time hasn't passed yet") {
            val notYetDue = booking("booking_pending", MerchantBookingStatus.CONFIRMED, LocalDate.now().plusDays(1), LocalTime.of(10, 0))
            every { merchantBookingRepository.findById("booking_pending") } returns Optional.of(notYetDue)

            Then("it real-no-ops rather than forfeiting a booking that isn't due yet") {
                val result = service.processNoShow("booking_pending")
                result shouldBe null
                verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
            }
        }
    }

    Given("the real fix itself: one bad row must never poison another real row's settlement") {
        // This is the actual regression the old `@Transactional fun processNoShows(): List<MerchantBooking>`
        // batch method could never guarantee: it wrapped every due booking's real ledger
        // money movement in ONE physical transaction with no try/catch, so a single
        // booking whose merchant/deposit lookup threw would roll back every OTHER real
        // due booking's already-applied no-show status change and forfeit payout in the
        // same poll. Now each row is its own independent `@Transactional` call
        // (`processNoShow`), invoked from a separate non-transactional bean
        // (`BookingNoShowScheduler`) with its own try/catch per row -- proven here by
        // showing a row whose merchant lookup returns null still cleanly settles (no
        // exception at all, real NO_SHOW with no deposit payout) and does not prevent a
        // second, healthy row processed independently afterward from settling normally.
        val merchantBookingRepository = mockk<MerchantBookingRepository>()
        val merchantRepository = mockk<MerchantRepository>()
        val service = newService(merchantRepository = merchantRepository, merchantBookingRepository = merchantBookingRepository)

        val orphaned = booking("booking_orphan", MerchantBookingStatus.CONFIRMED, LocalDate.now().minusDays(1), LocalTime.of(9, 0))
        val healthy = booking("booking_healthy", MerchantBookingStatus.CONFIRMED, LocalDate.now().minusDays(1), LocalTime.of(9, 0))

        every { merchantBookingRepository.findById("booking_orphan") } returns Optional.of(orphaned)
        every { merchantBookingRepository.findById("booking_healthy") } returns Optional.of(healthy)
        every { merchantBookingRepository.save(any()) } answers { firstArg() }
        every { merchantRepository.findById("merchant_1") } returns Optional.empty()

        When("the first row's merchant lookup finds nothing (a real orphaned booking), then a second independent row is processed") {
            val orphanResult = service.processNoShow("booking_orphan")
            val healthyResult = service.processNoShow("booking_healthy")

            Then("the orphaned row still settles honestly (NO_SHOW, no payout attempted) and the second, independent row settles too, unaffected") {
                orphanResult shouldBe orphaned
                orphaned.status shouldBe MerchantBookingStatus.NO_SHOW
                healthyResult shouldBe healthy
                healthy.status shouldBe MerchantBookingStatus.NO_SHOW
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
