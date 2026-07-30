package rw.itunda.core.repository

import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.RideTripStop

interface RideTripStopRepository : JpaRepository<RideTripStop, String> {
    fun findByTripIdOrderBySequenceAsc(tripId: String): List<RideTripStop>
}
