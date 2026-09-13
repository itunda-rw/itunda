package rw.itunda.merchant

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import rw.itunda.core.domain.Account
import rw.itunda.core.domain.AccountType
import rw.itunda.core.domain.BookingDeposit
import rw.itunda.core.domain.BookingDepositStatus
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.fraud.FraudRuleEngine
import rw.itunda.core.domain.Merchant
import rw.itunda.core.domain.MerchantAvailabilityWindow
import rw.itunda.core.domain.MerchantBooking
import rw.itunda.core.domain.MerchantBookingStatus
import rw.itunda.core.domain.MerchantProduct
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerPostResult
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.AccountRepository
import rw.itunda.core.repository.BookingDepositRepository
import rw.itunda.core.repository.MerchantAvailabilityWindowRepository
import rw.itunda.core.repository.MerchantBookingRepository
import rw.itunda.core.repository.MerchantProductRepository
import rw.itunda.core.repository.MerchantRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.TransactionRepository
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.util.Optional

/**
 * Real Toss Bank/Toss Pay separation (2026-08-21) -- a prepay booking deposit is real
 * merchant collection, same as MerchantService.collect()'s QR path, so book() now draws
 * from the customer's itunda Pay money (auto-topped from Bank if short) instead of
 * Bank directly. Previously zero test coverage existed for the deposit-hold path at all
 * (MerchantBookingServiceSlotsTest only covers non-prepay slot generation,
 * MerchantBookingServiceNoShowTest only covers the merchant-side forfeit payout).
 */
class MerchantBookingServiceDepositTest : BehaviorSpec({

    fun account(id: String, userId: String, type: AccountType = AccountType.PAY, availableBalance: BigDecimal = BigDecimal("100000")) = Account(
        id = id, userId = userId, accountNumber = "ACC-$id", accountName = "Test account",
        type = type, balance = availableBalance, availableBalance = availableBalance,
    )

    Given("a prepay-required service and a customer booking it") {
        val merchantRepository = mockk<MerchantRepository>()
        val merchantProductRepository = mockk<MerchantProductRepository>()
        val availabilityWindowRepository = mockk<MerchantAvailabilityWindowRepository>()
        val merchantBookingRepository = mockk<MerchantBookingRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        // Same relaxed-mockk save() ClassCastException pitfall as bookingDepositRepository above.
        every { notificationRepository.save(any()) } answers { firstArg() }
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val transactionRepository = mockk<TransactionRepository>(relaxed = true)
        every { transactionRepository.save(any()) } answers { firstArg() }
        val bookingDepositRepository = mockk<BookingDepositRepository>(relaxed = true)
        // Same real ClassCastException pitfall this project's tests document repeatedly
        // (relaxed mockk's save() can't infer JpaRepository's generic <S extends T> S
        // save(S) signature) -- explicit stub for holdDeposit/refundDeposit's own save().
        every { bookingDepositRepository.save(any()) } answers { firstArg() }
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val autoTopUpService = mockk<rw.itunda.account.AutoTopUpService>(relaxed = true)
        // Real default: no top-up needed, the account passed in already covers the
        // deposit -- the short-balance test below overrides this with a more specific
        // stub for its own exact (userId, account, amount) triple.
        every { autoTopUpService.ensureSufficientPayBalance(any(), any(), any()) } answers { secondArg() }
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val service = MerchantBookingService(
            merchantRepository, merchantProductRepository, availabilityWindowRepository,
            merchantBookingRepository, notificationRepository, accountRepository, ledgerService,
            transactionRepository, bookingDepositRepository, pushNotificationService, autoTopUpService,
            fraudRuleEngine,
        )

        val merchant = Merchant(id = "merchant_1", ownerUserId = "owner_1", accountId = "account_merchant", businessName = "Kigali Spa")
        val merchantAccount = account("account_merchant", "owner_1", type = AccountType.MAIN)
        val prepayService = MerchantProduct(
            id = "service_1", merchantId = "merchant_1", name = "Massage", price = BigDecimal("8000"),
            durationMinutes = 60, requiresPrepay = true,
        )
        val futureDate = LocalDate.of(2099, 1, 5) // a Monday, far enough that today-filtering never applies
        val window = MerchantAvailabilityWindow(
            id = "window_1", merchantId = "merchant_1", dayOfWeek = DayOfWeek.MONDAY,
            startTime = LocalTime.of(9, 0), endTime = LocalTime.of(17, 0),
        )
        every { merchantRepository.findById("merchant_1") } returns Optional.of(merchant)
        every { merchantProductRepository.findById("service_1") } returns Optional.of(prepayService)
        every { availabilityWindowRepository.findByMerchantIdAndDayOfWeek("merchant_1", DayOfWeek.MONDAY) } returns listOf(window)
        every { merchantBookingRepository.findByMerchantIdAndBookingDateAndStatusIn(any(), any(), any()) } returns emptyList()
        every { merchantBookingRepository.save(any()) } answers { firstArg() }
        every { accountRepository.findById("account_merchant") } returns Optional.of(merchantAccount)

        When("the customer has enough itunda Pay money to cover the deposit") {
            val payerAccount = account("account_customer", "customer_1")
            every { accountRepository.findByUserIdAndType("customer_1", AccountType.PAY) } returns payerAccount
            val legsSlot = slot<List<LedgerLeg>>()
            every { ledgerService.postLedgerTransaction("RWF", capture(legsSlot)) } returns LedgerPostResult("ledgertxn_hold", emptyList())

            service.book("customer_1", "merchant_1", "service_1", futureDate, LocalTime.of(9, 0))

            Then("it holds the real deposit against the customer's itunda Pay account, not Bank") {
                val payerLeg = legsSlot.captured.first { it.accountId == "account_customer" }
                payerLeg.amount shouldBe BigDecimal("8000")
                payerLeg.direction shouldBe LedgerDirection.DEBIT
                verify(exactly = 1) { autoTopUpService.ensureSufficientPayBalance("customer_1", payerAccount, BigDecimal("8000")) }
                verify(exactly = 1) { fraudRuleEngine.evaluate("customer_1", "owner_1", BigDecimal("8000"), "ledgertxn_hold") }
            }
        }

        When("the customer's itunda Pay money is short but auto top-up from Bank covers it") {
            val shortAccount = account("account_customer_2", "customer_2", availableBalance = BigDecimal("1000"))
            val toppedUpAccount = account("account_customer_2", "customer_2", availableBalance = BigDecimal("10000"))
            every { accountRepository.findByUserIdAndType("customer_2", AccountType.PAY) } returns shortAccount
            every { autoTopUpService.ensureSufficientPayBalance("customer_2", shortAccount, BigDecimal("8000")) } returns toppedUpAccount
            val legsSlot = slot<List<LedgerLeg>>()
            every { ledgerService.postLedgerTransaction("RWF", capture(legsSlot)) } returns LedgerPostResult("ledgertxn_hold2", emptyList())

            service.book("customer_2", "merchant_1", "service_1", futureDate, LocalTime.of(10, 0))

            Then("it calls the shared top-up helper for exactly the real deposit amount, then holds the deposit") {
                verify(exactly = 1) { autoTopUpService.ensureSufficientPayBalance("customer_2", shortAccount, BigDecimal("8000")) }
                legsSlot.captured.first { it.accountId == "account_customer_2" }.amount shouldBe BigDecimal("8000")
            }
        }
    }

    Given("a REQUESTED booking with a held deposit that its customer cancels") {
        val merchantRepository = mockk<MerchantRepository>()
        val merchantBookingRepository = mockk<MerchantBookingRepository>()
        val bookingDepositRepository = mockk<BookingDepositRepository>(relaxed = true)
        // Same real ClassCastException pitfall this project's tests document repeatedly
        // (relaxed mockk's save() can't infer JpaRepository's generic <S extends T> S
        // save(S) signature) -- explicit stub for holdDeposit/refundDeposit's own save().
        every { bookingDepositRepository.save(any()) } answers { firstArg() }
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        // Same relaxed-mockk save() ClassCastException pitfall as bookingDepositRepository above.
        every { notificationRepository.save(any()) } answers { firstArg() }
        val transactionRepository = mockk<TransactionRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val merchantProductRepository = mockk<MerchantProductRepository>()
        val availabilityWindowRepository = mockk<MerchantAvailabilityWindowRepository>()
        val autoTopUpService = mockk<rw.itunda.account.AutoTopUpService>(relaxed = true)
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val service = MerchantBookingService(
            merchantRepository, merchantProductRepository, availabilityWindowRepository,
            merchantBookingRepository, notificationRepository, accountRepository, ledgerService,
            transactionRepository, bookingDepositRepository, pushNotificationService, autoTopUpService,
            fraudRuleEngine,
        )

        val merchant = Merchant(id = "merchant_1", ownerUserId = "owner_1", accountId = "account_merchant", businessName = "Kigali Spa")
        val booking = MerchantBooking(
            id = "booking_1", merchantId = "merchant_1", customerId = "customer_1", serviceId = "service_1",
            serviceName = "Massage", bookingDate = LocalDate.of(2099, 1, 5),
            startTime = LocalTime.of(9, 0), endTime = LocalTime.of(10, 0), status = MerchantBookingStatus.REQUESTED,
        )
        val deposit = BookingDeposit(
            id = "deposit_1", bookingId = "booking_1", merchantId = "merchant_1", customerId = "customer_1",
            amount = BigDecimal("8000"), fee = BigDecimal("120"), holdTransactionId = "ledgertxn_hold",
        )
        val payerAccount = account("account_customer", "customer_1")

        every { merchantBookingRepository.findById("booking_1") } returns Optional.of(booking)
        every { merchantBookingRepository.save(any()) } answers { firstArg() }
        every { merchantRepository.findById("merchant_1") } returns Optional.of(merchant)
        every { bookingDepositRepository.findByBookingIdForUpdate("booking_1") } returns deposit
        every { accountRepository.findByUserIdAndType("customer_1", AccountType.PAY) } returns payerAccount
        val legsSlot = slot<List<LedgerLeg>>()
        every { ledgerService.postLedgerTransaction("RWF", capture(legsSlot)) } returns LedgerPostResult("ledgertxn_refund", emptyList())

        When("cancel() runs") {
            service.cancel("customer_1", "booking_1")

            Then("it real-refunds the deposit to the customer's itunda Pay account, not Bank") {
                val refundLeg = legsSlot.captured.first { it.accountId == "account_customer" && it.direction == LedgerDirection.CREDIT }
                refundLeg.amount shouldBe BigDecimal("8000")
                deposit.status shouldBe BookingDepositStatus.REFUNDED
            }
        }
    }
})
