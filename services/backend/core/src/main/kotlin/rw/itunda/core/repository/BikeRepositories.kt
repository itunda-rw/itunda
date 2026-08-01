package rw.itunda.core.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.Bike
import rw.itunda.core.domain.BikeRentalSession
import rw.itunda.core.domain.BikeRentalStatus

interface BikeRepository : JpaRepository<Bike, String> {
    fun findByOwnerUserId(ownerUserId: String): List<Bike>
}

interface BikeRentalSessionRepository : JpaRepository<BikeRentalSession, String> {
    fun findByBikeIdAndStatus(bikeId: String, status: BikeRentalStatus): BikeRentalSession?
    fun findByRiderUserIdOrderByStartedAtDesc(riderUserId: String, pageable: Pageable): Page<BikeRentalSession>
}
