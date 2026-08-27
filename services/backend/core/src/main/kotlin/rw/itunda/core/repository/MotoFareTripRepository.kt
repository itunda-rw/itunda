package rw.itunda.core.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.MotoFareTrip

interface MotoFareTripRepository : JpaRepository<MotoFareTrip, String> {
    fun findByRiderUserIdOrderByCreatedAtDesc(riderUserId: String, pageable: Pageable): Page<MotoFareTrip>
    fun findByDriverUserIdOrderByCreatedAtDesc(driverUserId: String, pageable: Pageable): Page<MotoFareTrip>
}
