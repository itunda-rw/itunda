package rw.itunda.core.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import rw.itunda.core.domain.RideDriver
import rw.itunda.core.domain.RideTrip
import rw.itunda.core.domain.RideTripStatus
import java.time.Instant

interface RideDriverRepository : JpaRepository<RideDriver, String> {
    fun findByUserId(userId: String): RideDriver?

    // Real candidate pool for dispatch -- every online driver with a real known
    // position, same shape RiderRepository's own equivalent already establishes.
    fun findByAvailableTrueAndCurrentLatitudeIsNotNullAndCurrentLongitudeIsNotNull(): List<RideDriver>
}

interface RideTripRepository : JpaRepository<RideTrip, String> {
    fun findByPassengerIdOrderByCreatedAtDesc(passengerId: String, pageable: Pageable): Page<RideTrip>
    fun findByDriverIdOrderByCreatedAtDesc(driverId: String, pageable: Pageable): Page<RideTrip>

    // Real automatic-dispatch reassignment query -- every real unassigned trip whose
    // exclusive offer window has expired, same shape
    // EatsOrderRepository.findByOfferExpiresAtBeforeAndRiderIdIsNull already establishes.
    fun findByOfferExpiresAtBeforeAndDriverIdIsNull(cutoff: Instant): List<RideTrip>

    // Real 단건배차 (single-trip) guarantee -- a driver only ever carries one active
    // trip at a time, same real correctness fix EatsOrderRepository.
    // existsByRiderIdAndStatusIn just closed for delivery riders.
    fun existsByDriverIdAndStatusIn(driverId: String, statuses: List<RideTripStatus>): Boolean

    // Real Uber Driver-style earnings report (2026-08-16) -- see
    // RideTripService.getMyEarnings's own doc comment. Unpaginated, same bounded-window
    // shape MerchantService.getReport's own transaction fetch already establishes.
    fun findByDriverIdAndStatusAndCreatedAtBetween(driverId: String, status: RideTripStatus, from: Instant, to: Instant): List<RideTrip>

    @Query("SELECT DISTINCT t.driverId FROM RideTrip t WHERE t.status IN :statuses AND t.driverId IS NOT NULL")
    fun findDistinctDriverIdsByStatusIn(@Param("statuses") statuses: List<RideTripStatus>): List<String>

    // Real Kakao T 예약 호출 (scheduled ride booking) -- every real scheduled trip
    // whose lead-time threshold has been crossed but whose first dispatch attempt
    // hasn't started yet. See RideTripService.activateScheduledDispatch's own doc
    // comment.
    fun findByStatusAndScheduledForIsNotNullAndScheduledForBeforeAndScheduledDispatchStartedAtIsNull(
        status: RideTripStatus,
        threshold: Instant,
    ): List<RideTrip>
}
