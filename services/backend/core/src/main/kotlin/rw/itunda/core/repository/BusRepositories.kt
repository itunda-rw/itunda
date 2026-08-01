package rw.itunda.core.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.BusBooking
import rw.itunda.core.domain.BusTrip

interface BusTripRepository : JpaRepository<BusTrip, String> {
    fun findByOperatorUserId(operatorUserId: String): List<BusTrip>
}

interface BusBookingRepository : JpaRepository<BusBooking, String> {
    fun findByRiderUserIdOrderByCreatedAtDesc(riderUserId: String, pageable: Pageable): Page<BusBooking>
    fun findByTripIdOrderByCreatedAtDesc(tripId: String): List<BusBooking>
}
