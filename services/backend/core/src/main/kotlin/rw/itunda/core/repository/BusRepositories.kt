package rw.itunda.core.repository

import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import rw.itunda.core.domain.BusBooking
import rw.itunda.core.domain.BusTrip
import java.util.Optional

interface BusTripRepository : JpaRepository<BusTrip, String> {
    fun findByOperatorUserId(operatorUserId: String): List<BusTrip>
}

interface BusBookingRepository : JpaRepository<BusBooking, String> {
    fun findByRiderUserIdOrderByCreatedAtDesc(riderUserId: String, pageable: Pageable): Page<BusBooking>
    fun findByTripIdOrderByCreatedAtDesc(tripId: String): List<BusBooking>

    // Real gap found 2026-09-05 (concurrency audit): BusService.cancelBooking mutates
    // `status`/`refundTransactionId` after posting a real refund with a plain unlocked
    // findById, and BusBooking (unlike its sibling BikeRentalSession/ParkingSession)
    // carries no @Version -- two concurrent cancel calls for the same booking could
    // both read status=BOOKED and both post a real refund, double-crediting the rider.
    // Same findByIdForUpdate convention this codebase already establishes elsewhere
    // (LoanAccountRepository/SavingsGoalRepository).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from BusBooking b where b.id = :id")
    fun findByIdForUpdate(@Param("id") id: String): Optional<BusBooking>
}
