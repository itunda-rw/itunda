package rw.itunda.merchant

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.core.domain.BookingDeposit
import rw.itunda.core.domain.BookingDepositStatus
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.Merchant
import rw.itunda.core.domain.MerchantAvailabilityWindow
import rw.itunda.core.domain.MerchantBooking
import rw.itunda.core.domain.MerchantBookingStatus
import rw.itunda.core.domain.Notification
import rw.itunda.core.domain.Transaction
import rw.itunda.core.domain.TransactionStatus
import rw.itunda.core.domain.TransactionType
import rw.itunda.core.domain.WalletType
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.BookingDepositRepository
import rw.itunda.core.repository.MerchantAvailabilityWindowRepository
import rw.itunda.core.repository.MerchantBookingRepository
import rw.itunda.core.repository.MerchantProductRepository
import rw.itunda.core.repository.MerchantRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.TransactionRepository
import rw.itunda.core.repository.WalletRepository
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.UUID

class InvalidAvailabilityWindowException(message: String) : RuntimeException(message)
class ServiceNotBookableException(message: String) : RuntimeException(message)
class InvalidBookingSlotException(message: String) : RuntimeException(message)
class SlotNoLongerAvailableException(message: String) : RuntimeException(message)
class MerchantBookingNotFoundException(message: String) : RuntimeException(message)
class InvalidBookingStatusTransitionException(message: String) : RuntimeException(message)

data class AvailabilityWindowRequest(val dayOfWeek: DayOfWeek, val startTime: LocalTime, val endTime: LocalTime)
data class BookingSlot(val startTime: LocalTime, val endTime: LocalTime)

/**
 * Real local-business appointment booking on top of the existing `Merchant`/
 * `MerchantProduct` catalog -- see `MerchantBooking.kt`'s own doc comment for the full
 * account (a bookable service IS a `MerchantProduct` with `durationMinutes` set, same
 * "no second catalog system" discipline `EatsOrderService`/`DineInOrderService` already
 * established for restaurants).
 *
 * Slots are generated fresh from the merchant's declared weekly [MerchantAvailabilityWindow]s
 * minus any real REQUESTED/CONFIRMED booking already on that date -- never persisted as
 * their own rows, so a later availability-window edit or duration change is instantly
 * reflected with no stale-slot cleanup needed.
 */
@Service
class MerchantBookingService(
    private val merchantRepository: MerchantRepository,
    private val merchantProductRepository: MerchantProductRepository,
    private val availabilityWindowRepository: MerchantAvailabilityWindowRepository,
    private val merchantBookingRepository: MerchantBookingRepository,
    private val notificationRepository: NotificationRepository,
    private val walletRepository: WalletRepository,
    private val ledgerService: LedgerService,
    private val transactionRepository: TransactionRepository,
    private val bookingDepositRepository: BookingDepositRepository,
    private val pushNotificationService: PushNotificationService,
) {
    companion object {
        // Same real fee rate MerchantService.feeRate/MarketplaceService.ESCROW_FEE_RATE
        // already established for a real held-then-released payment -- not a new number
        // invented for this feature.
        val DEPOSIT_FEE_RATE: BigDecimal = BigDecimal("0.015")
    }

    private fun getMyMerchant(ownerUserId: String) =
        merchantRepository.findByOwnerUserId(ownerUserId)
            ?: throw MerchantNotFoundException("This account is not registered as a merchant")

    private fun today(): LocalDate = LocalDate.now(ZoneOffset.UTC)
    private fun now(): LocalTime = LocalTime.now(ZoneOffset.UTC)

    @Transactional
    fun setAvailability(ownerUserId: String, windows: List<AvailabilityWindowRequest>): List<MerchantAvailabilityWindow> {
        val merchant = getMyMerchant(ownerUserId)
        if (windows.size > 50) {
            throw InvalidAvailabilityWindowException("Too many availability windows -- 50 is the real limit")
        }
        windows.forEach {
            if (!it.startTime.isBefore(it.endTime)) {
                throw InvalidAvailabilityWindowException("An availability window's start time must be before its end time")
            }
        }
        availabilityWindowRepository.deleteByMerchantId(merchant.id)
        val saved = windows.map {
            MerchantAvailabilityWindow(
                id = "merchant_availability_${UUID.randomUUID()}", merchantId = merchant.id,
                dayOfWeek = it.dayOfWeek, startTime = it.startTime, endTime = it.endTime,
            )
        }
        return availabilityWindowRepository.saveAll(saved)
    }

    fun getMyAvailability(ownerUserId: String): List<MerchantAvailabilityWindow> {
        val merchant = getMyMerchant(ownerUserId)
        return availabilityWindowRepository.findByMerchantIdOrderByDayOfWeekAscStartTimeAsc(merchant.id)
    }

    fun getAvailability(merchantId: String): List<MerchantAvailabilityWindow> =
        availabilityWindowRepository.findByMerchantIdOrderByDayOfWeekAscStartTimeAsc(merchantId)

    /** Real free-slot generation for a public/customer date picker -- see this class's
     * own doc comment for the "generated fresh, never persisted" design. */
    fun getAvailableSlots(merchantId: String, serviceId: String, date: LocalDate): List<BookingSlot> {
        if (date.isBefore(today())) {
            throw InvalidBookingSlotException("Cannot book a date in the past")
        }
        val service = merchantProductRepository.findById(serviceId)
            .orElseThrow { MerchantProductNotFoundException("Service not found") }
        if (service.merchantId != merchantId || !service.active) {
            throw MerchantProductNotFoundException("Service not found")
        }
        val durationMinutes = service.durationMinutes
            ?: throw ServiceNotBookableException("${service.name} is not a bookable service")

        val windows = availabilityWindowRepository.findByMerchantIdAndDayOfWeek(merchantId, date.dayOfWeek)
        if (windows.isEmpty()) return emptyList()

        val busy = merchantBookingRepository.findByMerchantIdAndBookingDateAndStatusIn(
            merchantId, date, listOf(MerchantBookingStatus.REQUESTED, MerchantBookingStatus.CONFIRMED),
        )
        val isToday = date.isEqual(today())
        val nowTime = now()

        return windows.flatMap { window ->
            // Bounded by a real slot COUNT (setAvailability already enforces
            // startTime.isBefore(endTime)), never by comparing wrapped LocalTime values --
            // LocalTime.plusMinutes silently wraps past midnight (e.g. 23:30 + 30min =
            // 00:00), so a takeWhile driven by isAfter(window.endTime) never terminates
            // once a window's close time is within one slot of midnight. Found live via
            // this exact case (a 00:00-23:30 window with 30-min slots spun the request
            // thread forever, pinning a CPU core until kubelet killed the pod).
            val slotCount = Duration.between(window.startTime, window.endTime).toMinutes() / durationMinutes
            (0 until slotCount).map { i ->
                val slotStart = window.startTime.plusMinutes(i * durationMinutes.toLong())
                BookingSlot(slotStart, slotStart.plusMinutes(durationMinutes.toLong()))
            }
                .filter { slot -> !isToday || slot.startTime.isAfter(nowTime) }
                .filter { slot -> busy.none { it.startTime.isBefore(slot.endTime) && slot.startTime.isBefore(it.endTime) } }
        }.sortedBy { it.startTime }
    }

    @Transactional
    fun book(customerId: String, merchantId: String, serviceId: String, date: LocalDate, startTime: LocalTime, notes: String? = null): MerchantBooking {
        val merchant = merchantRepository.findById(merchantId)
            .orElseThrow { MerchantNotFoundException("Merchant not found") }
        if (merchant.ownerUserId == customerId) {
            throw InvalidBookingSlotException("Cannot book your own business")
        }
        // Real, honest re-check at write time -- the slot list a customer saw may be
        // stale by the time they submit, and this backend has no DB-level partial unique
        // index to fall back on (MySQL can't express "unique only among non-terminal
        // rows" -- see MerchantBookingRepository's own doc comment), so this
        // application-level re-validation is the real, if imperfect, safeguard.
        val available = getAvailableSlots(merchantId, serviceId, date)
        val slot = available.find { it.startTime == startTime }
            ?: throw SlotNoLongerAvailableException("This time slot is no longer available")
        val service = merchantProductRepository.findById(serviceId).orElseThrow { MerchantProductNotFoundException("Service not found") }
        val trimmedNotes = notes?.trim()?.ifBlank { null }?.take(500)

        val booking = merchantBookingRepository.save(
            MerchantBooking(
                id = "merchant_booking_${UUID.randomUUID()}", merchantId = merchantId, customerId = customerId,
                serviceId = serviceId, serviceName = service.name, bookingDate = date,
                startTime = slot.startTime, endTime = slot.endTime, notes = trimmedNotes,
            ),
        )
        // Real Kakao Hair Shop-style prepay-to-book (2026-07-25) -- see
        // BookingDeposit.kt's own doc comment. Posted before notify() so an
        // InsufficientFundsException (thrown by ledgerService itself) rolls back the
        // whole booking inside this @Transactional method -- a customer who can't afford
        // the deposit simply can't book a prepay-required slot at all, the entire point
        // of the real mechanic this closes.
        if (service.requiresPrepay) {
            holdDeposit(booking, merchant, service.price, customerId)
        }
        notify(merchant.ownerUserId, booking, "New booking request", "A customer requested ${service.name} on $date at ${slot.startTime}.")
        return booking
    }

    private fun holdDeposit(booking: MerchantBooking, merchant: Merchant, amount: BigDecimal, customerId: String) {
        val customerWallet = walletRepository.findByUserIdAndType(customerId, WalletType.MAIN)
            ?: throw MerchantNoWalletException("No wallet found for this account")
        val merchantWallet = walletRepository.findById(merchant.walletId)
            .orElseThrow { MerchantNoWalletException("Merchant settlement wallet not found") }
        val fee = amount.multiply(DEPOSIT_FEE_RATE).setScale(2, RoundingMode.HALF_UP)

        val result = ledgerService.postLedgerTransaction(
            customerWallet.currency,
            listOf(
                LedgerLeg(customerWallet.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, amount, "Booking deposit - ${booking.serviceName}"),
                LedgerLeg("booking_deposit_holding", LedgerAccountType.BOOKING_DEPOSIT_HOLDING, LedgerDirection.CREDIT, amount, "Booking deposit held - ${booking.serviceName}"),
            ),
        )
        transactionRepository.save(
            Transaction(
                id = result.transactionId,
                referenceNumber = "BKDEP${System.currentTimeMillis()}${UUID.randomUUID().toString().take(4)}",
                senderId = customerId,
                recipientId = merchant.ownerUserId,
                fromWalletId = customerWallet.id,
                toWalletId = merchantWallet.id,
                amount = amount,
                fee = fee,
                currency = customerWallet.currency,
                type = TransactionType.PAYMENT,
                status = TransactionStatus.COMPLETED,
                description = "Booking deposit - ${booking.serviceName}",
                channel = "BOOKING_DEPOSIT",
                completedAt = Instant.now(),
            ),
        )
        bookingDepositRepository.save(
            BookingDeposit(
                id = "booking_deposit_${UUID.randomUUID()}", bookingId = booking.id, merchantId = merchant.id,
                customerId = customerId, amount = amount, fee = fee, holdTransactionId = result.transactionId,
            ),
        )
    }

    private fun getDeposit(bookingId: String): BookingDeposit? = bookingDepositRepository.findByBookingId(bookingId)

    // Shared by markCompleted (real RELEASED) and BookingNoShowScheduler (real
    // FORFEITED) -- both pay the merchant net of itunda's fee, only the resulting
    // status differs. A no-op if this booking was never prepay-required (no HELD
    // deposit exists) or its deposit was already resolved.
    private fun payOutDeposit(booking: MerchantBooking, merchant: Merchant, resultStatus: BookingDepositStatus) {
        val deposit = bookingDepositRepository.findByBookingIdForUpdate(booking.id) ?: return
        if (deposit.status != BookingDepositStatus.HELD) return
        val merchantWallet = walletRepository.findById(merchant.walletId)
            .orElseThrow { MerchantNoWalletException("Merchant settlement wallet not found") }
        val netToMerchant = deposit.amount.subtract(deposit.fee)
        val result = ledgerService.postLedgerTransaction(
            merchantWallet.currency,
            listOf(
                LedgerLeg("booking_deposit_holding", LedgerAccountType.BOOKING_DEPOSIT_HOLDING, LedgerDirection.DEBIT, deposit.amount, "Booking deposit ${resultStatus.name.lowercase()}"),
                LedgerLeg(merchantWallet.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, netToMerchant, "Booking deposit ${resultStatus.name.lowercase()}"),
                LedgerLeg("fee_revenue", LedgerAccountType.FEE_REVENUE, LedgerDirection.CREDIT, deposit.fee, "Booking deposit fee"),
            ),
        )
        deposit.status = resultStatus
        deposit.resolutionTransactionId = result.transactionId
        deposit.updatedAt = Instant.now()
        bookingDepositRepository.save(deposit)
    }

    // Always a full refund, no fee -- same "an explicit cancel/decline is never
    // penalized, only a real no-show is" rule MerchantBooking.kt's own doc comment
    // names. A no-op if this booking was never prepay-required or already resolved.
    private fun refundDeposit(booking: MerchantBooking) {
        val deposit = bookingDepositRepository.findByBookingIdForUpdate(booking.id) ?: return
        if (deposit.status != BookingDepositStatus.HELD) return
        val customerWallet = walletRepository.findByUserIdAndType(deposit.customerId, WalletType.MAIN)
            ?: throw MerchantNoWalletException("No wallet found for this account")
        val result = ledgerService.postLedgerTransaction(
            customerWallet.currency,
            listOf(
                LedgerLeg("booking_deposit_holding", LedgerAccountType.BOOKING_DEPOSIT_HOLDING, LedgerDirection.DEBIT, deposit.amount, "Booking deposit refunded"),
                LedgerLeg(customerWallet.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, deposit.amount, "Booking deposit refund"),
            ),
        )
        deposit.status = BookingDepositStatus.REFUNDED
        deposit.resolutionTransactionId = result.transactionId
        deposit.updatedAt = Instant.now()
        bookingDepositRepository.save(deposit)
    }

    @Transactional
    fun respond(ownerUserId: String, bookingId: String, confirm: Boolean): MerchantBooking {
        val merchant = getMyMerchant(ownerUserId)
        val booking = getOwnedBooking(bookingId, merchant.id)
        if (booking.status != MerchantBookingStatus.REQUESTED) {
            throw InvalidBookingStatusTransitionException("Only a REQUESTED booking can be confirmed or declined")
        }
        booking.status = if (confirm) MerchantBookingStatus.CONFIRMED else MerchantBookingStatus.DECLINED
        booking.updatedAt = Instant.now()
        val saved = merchantBookingRepository.save(booking)
        if (!confirm) {
            refundDeposit(saved)
        }
        val title = if (confirm) "Booking confirmed" else "Booking declined"
        notify(booking.customerId, saved, title, "${merchant.businessName}: ${booking.serviceName} on ${booking.bookingDate} at ${booking.startTime} was ${saved.status.name.lowercase()}.")
        return saved
    }

    @Transactional
    fun markCompleted(ownerUserId: String, bookingId: String): MerchantBooking {
        val merchant = getMyMerchant(ownerUserId)
        val booking = getOwnedBooking(bookingId, merchant.id)
        if (booking.status != MerchantBookingStatus.CONFIRMED) {
            throw InvalidBookingStatusTransitionException("Only a CONFIRMED booking can be marked completed")
        }
        booking.status = MerchantBookingStatus.COMPLETED
        booking.updatedAt = Instant.now()
        val saved = merchantBookingRepository.save(booking)
        payOutDeposit(saved, merchant, BookingDepositStatus.RELEASED)
        return saved
    }

    @Transactional
    fun cancel(requesterId: String, bookingId: String): MerchantBooking {
        val booking = merchantBookingRepository.findById(bookingId)
            .orElseThrow { MerchantBookingNotFoundException("Booking not found") }
        val merchant = merchantRepository.findById(booking.merchantId).orElse(null)
        val isCustomer = booking.customerId == requesterId
        val isMerchant = merchant?.ownerUserId == requesterId
        if (!isCustomer && !isMerchant) {
            throw MerchantBookingNotFoundException("Booking not found")
        }
        if (booking.status != MerchantBookingStatus.REQUESTED && booking.status != MerchantBookingStatus.CONFIRMED) {
            throw InvalidBookingStatusTransitionException("Only a REQUESTED or CONFIRMED booking can be cancelled -- this one is already ${booking.status}")
        }
        booking.status = MerchantBookingStatus.CANCELLED
        booking.updatedAt = Instant.now()
        val saved = merchantBookingRepository.save(booking)
        refundDeposit(saved)
        // Only notify the OTHER party -- same "don't notify someone about their own
        // action" discipline every other Notification call site in this codebase uses.
        if (isCustomer && merchant != null) {
            notify(merchant.ownerUserId, saved, "Booking cancelled", "A customer cancelled their ${booking.serviceName} booking on ${booking.bookingDate} at ${booking.startTime}.")
        } else if (isMerchant) {
            notify(booking.customerId, saved, "Booking cancelled", "${merchant?.businessName ?: "The business"} cancelled your ${booking.serviceName} booking on ${booking.bookingDate} at ${booking.startTime}.")
        }
        return saved
    }

    fun getMyBookings(customerId: String, pageable: Pageable): Page<MerchantBooking> =
        merchantBookingRepository.findByCustomerIdOrderByBookingDateDescStartTimeDesc(customerId, pageable)

    fun getMerchantBookings(ownerUserId: String, pageable: Pageable): Page<MerchantBooking> {
        val merchant = getMyMerchant(ownerUserId)
        return merchantBookingRepository.findByMerchantIdOrderByBookingDateDescStartTimeDesc(merchant.id, pageable)
    }

    private fun getOwnedBooking(bookingId: String, merchantId: String): MerchantBooking {
        val booking = merchantBookingRepository.findById(bookingId)
            .orElseThrow { MerchantBookingNotFoundException("Booking not found") }
        if (booking.merchantId != merchantId) {
            throw MerchantBookingNotFoundException("Booking not found")
        }
        return booking
    }

    private fun notify(userId: String, booking: MerchantBooking, title: String, body: String) {
        notificationRepository.save(
            Notification(
                id = "notif_${UUID.randomUUID()}", userId = userId, type = "MERCHANT_BOOKING_UPDATE",
                title = title, body = body, isRead = false, createdAt = Instant.now(),
                dataJson = "{\"bookingId\":\"${booking.id}\"}",
            ),
        )
        // Real push notification (2026-07-26) -- see PushNotificationService's own doc
        // comment for why this is the one real, sourced call site (Naver Smart Place's
        // own "push notifications on new bookings") this pass wires, not all 19
        // Notification call sites in this codebase. Best-effort by design -- never
        // throws, never blocks the booking action that triggered it.
        pushNotificationService.sendToUser(userId, title, body, mapOf("bookingId" to booking.id))
    }

    // Real read for whichever party paid/receives a prepay deposit -- same "don't reveal
    // a resource to someone who shouldn't see it" discipline MarketplaceService's
    // getEscrow already establishes.
    fun getBookingDeposit(requesterId: String, bookingId: String): BookingDeposit {
        val booking = merchantBookingRepository.findById(bookingId).orElseThrow { MerchantBookingNotFoundException("Booking not found") }
        val merchant = merchantRepository.findById(booking.merchantId).orElse(null)
        if (booking.customerId != requesterId && merchant?.ownerUserId != requesterId) {
            throw MerchantBookingNotFoundException("Booking not found")
        }
        return getDeposit(bookingId) ?: throw MerchantBookingNotFoundException("This booking has no deposit")
    }

    // Real no-show detection -- see BookingDeposit.kt's own doc comment and
    // BookingNoShowScheduler. A still-CONFIRMED booking whose real scheduled end time has
    // passed with no explicit action from either side is a genuine no-show: transitions
    // it to NO_SHOW and forfeits any held deposit to the merchant. A booking with no
    // deposit (service didn't require prepay) still gets the real NO_SHOW status -- an
    // honest record of what happened -- even though payOutDeposit is then a no-op for it.
    @Transactional
    fun processNoShows(): List<MerchantBooking> {
        val nowDateTime = LocalDateTime.now(ZoneOffset.UTC)
        val due = merchantBookingRepository.findByStatusAndBookingDateLessThanEqual(MerchantBookingStatus.CONFIRMED, today())
            .filter { LocalDateTime.of(it.bookingDate, it.endTime).isBefore(nowDateTime) }
        return due.map { booking ->
            booking.status = MerchantBookingStatus.NO_SHOW
            booking.updatedAt = Instant.now()
            val saved = merchantBookingRepository.save(booking)
            val merchant = merchantRepository.findById(booking.merchantId).orElse(null)
            if (merchant != null) {
                payOutDeposit(saved, merchant, BookingDepositStatus.FORFEITED)
                notify(merchant.ownerUserId, saved, "Marked as no-show", "${booking.serviceName} on ${booking.bookingDate} at ${booking.startTime} was marked a no-show.")
            }
            notify(booking.customerId, saved, "Missed appointment", "You missed your ${booking.serviceName} booking on ${booking.bookingDate} at ${booking.startTime}.")
            saved
        }
    }
}
