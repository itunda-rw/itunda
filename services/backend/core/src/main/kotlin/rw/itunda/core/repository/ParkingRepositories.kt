package rw.itunda.core.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.ParkingSession
import rw.itunda.core.domain.ParkingSessionStatus
import rw.itunda.core.domain.ParkingSpot
import java.time.Instant

interface ParkingSpotRepository : JpaRepository<ParkingSpot, String> {
    fun findByOwnerUserId(ownerUserId: String): List<ParkingSpot>
}

interface ParkingSessionRepository : JpaRepository<ParkingSession, String> {
    fun findBySpotIdAndStatus(spotId: String, status: ParkingSessionStatus): ParkingSession?
    fun findByRenterUserIdOrderByStartedAtDesc(renterUserId: String, pageable: Pageable): Page<ParkingSession>

    // Real query backing `ParkingService.getAbandonedSessions` -- see
    // `ParkingSession.MAX_SESSION_DURATION`'s own doc comment for the sourced Citi Bike
    // account this reuses, same shape `BikeRentalSessionRepository.findByStatusAndStartedAtBefore`
    // already establishes for the identical gap.
    fun findByStatusAndStartedAtBefore(status: ParkingSessionStatus, threshold: Instant): List<ParkingSession>
}
