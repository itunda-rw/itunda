package rw.itunda.marketplace

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.Transaction
import rw.itunda.core.domain.TransactionStatus
import rw.itunda.core.domain.TransactionType
import rw.itunda.core.domain.VehicleInspectionBooking
import rw.itunda.core.domain.VehicleInspectionMechanic
import rw.itunda.core.domain.VehicleInspectionStatus
import rw.itunda.core.domain.WalletType
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.ListingRepository
import rw.itunda.core.repository.TransactionRepository
import rw.itunda.core.repository.VehicleInspectionBookingRepository
import rw.itunda.core.repository.VehicleInspectionMechanicRepository
import rw.itunda.core.repository.WalletRepository
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.UUID

class MechanicAlreadyRegisteredException(message: String) : RuntimeException(message)
class MechanicNotRegisteredException(message: String) : RuntimeException(message)
class MechanicNoWalletException(message: String) : RuntimeException(message)
class InvalidInspectionFeeException(message: String) : RuntimeException(message)
class InspectionBookingNotFoundException(message: String) : RuntimeException(message)
class InvalidInspectionStatusTransitionException(message: String) : RuntimeException(message)
class SelfInspectionException(message: String) : RuntimeException(message)

/**
 * Real 당근마켓 중고차 정비소 동행 (used-car mechanic-inspection accompaniment) -- see
 * `VehicleInspectionMechanic.kt`/`VehicleInspectionBooking.kt`'s own doc comments for
 * the full sourced account and the real 100%-prepay-to-book escrow shape this reuses.
 */
@Service
class VehicleInspectionService(
    private val vehicleInspectionMechanicRepository: VehicleInspectionMechanicRepository,
    private val vehicleInspectionBookingRepository: VehicleInspectionBookingRepository,
    private val listingRepository: ListingRepository,
    private val walletRepository: WalletRepository,
    private val transactionRepository: TransactionRepository,
    private val ledgerService: LedgerService,
) {
    companion object {
        // Same real 1.5% fee-schedule reasoning MarketplaceService.ESCROW_FEE_RATE/
        // OrderService.feeRate already establish -- reused, not reinvented.
        private val PLATFORM_FEE_RATE = BigDecimal("0.015")
    }

    /** Any itunda user can register, no admin approval gate -- same real light
     * self-service opt-in `RideDriverService.register`/`RiderService.register` already
     * establish for this codebase's other opt-in service roles. */
    @Transactional
    fun registerAsMechanic(userId: String, businessName: String): VehicleInspectionMechanic {
        if (vehicleInspectionMechanicRepository.findByUserId(userId) != null) {
            throw MechanicAlreadyRegisteredException("This account is already registered as a mechanic")
        }
        val trimmedName = businessName.trim().ifEmpty { throw InvalidInspectionFeeException("Business name is required") }.take(200)
        val wallet = walletRepository.findByUserIdAndType(userId, WalletType.MAIN)
            ?: throw MechanicNoWalletException("No wallet found for this account")
        return vehicleInspectionMechanicRepository.save(
            VehicleInspectionMechanic(id = "mechanic_${UUID.randomUUID()}", userId = userId, walletId = wallet.id, businessName = trimmedName),
        )
    }

    fun getMyMechanicProfile(userId: String): VehicleInspectionMechanic? = vehicleInspectionMechanicRepository.findByUserId(userId)

    fun getAvailableMechanics(): List<VehicleInspectionMechanic> = vehicleInspectionMechanicRepository.findByAvailableTrue()

    @Transactional
    fun setAvailability(userId: String, available: Boolean): VehicleInspectionMechanic {
        val mechanic = vehicleInspectionMechanicRepository.findByUserId(userId)
            ?: throw MechanicNotRegisteredException("This account is not registered as a mechanic")
        mechanic.available = available
        return vehicleInspectionMechanicRepository.save(mechanic)
    }

    /** Real 100%-prepay-to-book -- the buyer's real inspection fee leaves their wallet
     * right now, held until the mechanic actually delivers the inspection. */
    @Transactional
    fun requestInspection(buyerId: String, listingId: String, mechanicId: String, fee: BigDecimal, scheduledFor: Instant): VehicleInspectionBooking {
        if (fee <= BigDecimal.ZERO) {
            throw InvalidInspectionFeeException("Fee must be greater than zero")
        }
        val listing = listingRepository.findById(listingId).orElseThrow { ListingNotFoundException("Listing not found") }
        val mechanic = vehicleInspectionMechanicRepository.findById(mechanicId).orElseThrow { MechanicNotRegisteredException("Mechanic not found") }
        if (mechanic.userId == buyerId) {
            throw SelfInspectionException("Cannot book an inspection with yourself")
        }
        val buyerWallet = walletRepository.findByUserIdAndType(buyerId, WalletType.MAIN)
            ?: throw BuyerNoWalletException("No wallet found for this account")

        val platformFee = fee.multiply(PLATFORM_FEE_RATE).setScale(2, RoundingMode.HALF_UP)
        val result = ledgerService.postLedgerTransaction(
            buyerWallet.currency,
            listOf(
                LedgerLeg(buyerWallet.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, fee, "Inspection fee - ${listing.title}"),
                LedgerLeg("vehicle_inspection_holding", LedgerAccountType.VEHICLE_INSPECTION_HOLDING, LedgerDirection.CREDIT, fee, "Inspection fee held - ${listing.title}"),
            ),
        )
        transactionRepository.save(
            Transaction(
                id = result.transactionId,
                referenceNumber = "INSPECT${System.currentTimeMillis()}${UUID.randomUUID().toString().take(4)}",
                senderId = buyerId,
                recipientId = mechanic.userId,
                fromWalletId = buyerWallet.id,
                toWalletId = mechanic.walletId,
                amount = fee,
                fee = platformFee,
                currency = buyerWallet.currency,
                type = TransactionType.PAYMENT,
                status = TransactionStatus.COMPLETED,
                description = "Inspection fee - ${listing.title}",
                channel = "VEHICLE_INSPECTION",
                completedAt = Instant.now(),
            ),
        )

        return vehicleInspectionBookingRepository.save(
            VehicleInspectionBooking(
                id = "inspection_${UUID.randomUUID()}", listingId = listingId, buyerId = buyerId, mechanicId = mechanicId,
                fee = fee, platformFee = platformFee, scheduledFor = scheduledFor, holdTransactionId = result.transactionId,
            ),
        )
    }

    fun getMyBookings(buyerId: String): List<VehicleInspectionBooking> = vehicleInspectionBookingRepository.findByBuyerIdOrderByCreatedAtDesc(buyerId)

    fun getMyMechanicBookings(userId: String): List<VehicleInspectionBooking> {
        val mechanic = vehicleInspectionMechanicRepository.findByUserId(userId)
            ?: throw MechanicNotRegisteredException("This account is not registered as a mechanic")
        return vehicleInspectionBookingRepository.findByMechanicIdOrderByCreatedAtDesc(mechanic.id)
    }

    private fun ownedMechanicBooking(userId: String, bookingId: String): VehicleInspectionBooking {
        val mechanic = vehicleInspectionMechanicRepository.findByUserId(userId)
            ?: throw MechanicNotRegisteredException("This account is not registered as a mechanic")
        val booking = vehicleInspectionBookingRepository.findById(bookingId).orElseThrow { InspectionBookingNotFoundException("Booking not found") }
        if (booking.mechanicId != mechanic.id) {
            throw InspectionBookingNotFoundException("Booking not found")
        }
        return booking
    }

    @Transactional
    fun acceptInspection(mechanicUserId: String, bookingId: String): VehicleInspectionBooking {
        val booking = ownedMechanicBooking(mechanicUserId, bookingId)
        if (booking.status != VehicleInspectionStatus.REQUESTED) {
            throw InvalidInspectionStatusTransitionException("Only a REQUESTED booking can be accepted")
        }
        booking.status = VehicleInspectionStatus.ACCEPTED
        booking.updatedAt = Instant.now()
        return vehicleInspectionBookingRepository.save(booking)
    }

    /** Real release -- the mechanic delivered the real inspection, paid out of holding
     * net of itunda's real platform fee, matching `BookingDeposit`'s own
     * release-on-completion shape. */
    @Transactional
    fun completeInspection(mechanicUserId: String, bookingId: String, findings: String?): VehicleInspectionBooking {
        val booking = ownedMechanicBooking(mechanicUserId, bookingId)
        if (booking.status != VehicleInspectionStatus.ACCEPTED) {
            throw InvalidInspectionStatusTransitionException("Only an ACCEPTED booking can be completed")
        }
        val mechanic = vehicleInspectionMechanicRepository.findById(booking.mechanicId).orElseThrow { MechanicNotRegisteredException("Mechanic not found") }
        val mechanicWallet = walletRepository.findById(mechanic.walletId).orElseThrow { MechanicNoWalletException("Mechanic wallet not found") }
        val netToMechanic = booking.fee.subtract(booking.platformFee)

        val result = ledgerService.postLedgerTransaction(
            mechanicWallet.currency,
            listOf(
                LedgerLeg("vehicle_inspection_holding", LedgerAccountType.VEHICLE_INSPECTION_HOLDING, LedgerDirection.DEBIT, booking.fee, "Inspection fee released"),
                LedgerLeg(mechanicWallet.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, netToMechanic, "Inspection fee payout"),
                LedgerLeg("fee_revenue", LedgerAccountType.FEE_REVENUE, LedgerDirection.CREDIT, booking.platformFee, "Inspection platform fee"),
            ),
        )

        booking.status = VehicleInspectionStatus.COMPLETED
        booking.resolutionTransactionId = result.transactionId
        booking.findings = findings?.trim()?.take(2000)?.ifBlank { null }
        booking.updatedAt = Instant.now()
        return vehicleInspectionBookingRepository.save(booking)
    }

    /** Real full refund, no fee -- the trade genuinely didn't happen, same "an explicit
     * cancel always refunds" rule `BookingDeposit`/`MerchantBooking` already establish. */
    @Transactional
    fun cancelInspection(buyerId: String, bookingId: String): VehicleInspectionBooking {
        val booking = vehicleInspectionBookingRepository.findById(bookingId).orElseThrow { InspectionBookingNotFoundException("Booking not found") }
        if (booking.buyerId != buyerId) {
            throw InspectionBookingNotFoundException("Booking not found")
        }
        if (booking.status != VehicleInspectionStatus.REQUESTED && booking.status != VehicleInspectionStatus.ACCEPTED) {
            throw InvalidInspectionStatusTransitionException("Only a REQUESTED or ACCEPTED booking can be cancelled -- this one is already ${booking.status}")
        }
        val buyerWallet = walletRepository.findByUserIdAndType(buyerId, WalletType.MAIN)
            ?: throw BuyerNoWalletException("No wallet found for this account")

        val result = ledgerService.postLedgerTransaction(
            buyerWallet.currency,
            listOf(
                LedgerLeg("vehicle_inspection_holding", LedgerAccountType.VEHICLE_INSPECTION_HOLDING, LedgerDirection.DEBIT, booking.fee, "Inspection fee refunded"),
                LedgerLeg(buyerWallet.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, booking.fee, "Inspection cancelled - refund"),
            ),
        )

        booking.status = VehicleInspectionStatus.CANCELLED
        booking.resolutionTransactionId = result.transactionId
        booking.updatedAt = Instant.now()
        return vehicleInspectionBookingRepository.save(booking)
    }
}
