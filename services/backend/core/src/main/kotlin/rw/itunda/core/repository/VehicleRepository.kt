package rw.itunda.core.repository

import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.Vehicle

interface VehicleRepository : JpaRepository<Vehicle, String> {
    fun findByUserIdOrderByCreatedAtDesc(userId: String): List<Vehicle>
    fun findByIdAndUserId(id: String, userId: String): Vehicle?
}
