package rw.itunda.core.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.DesignatedDriver
import rw.itunda.core.domain.DesignatedDriverTrip

interface DesignatedDriverRepository : JpaRepository<DesignatedDriver, String> {
    fun findByUserId(userId: String): DesignatedDriver?
}

interface DesignatedDriverTripRepository : JpaRepository<DesignatedDriverTrip, String> {
    fun findByCustomerIdOrderByCreatedAtDesc(customerId: String, pageable: Pageable): Page<DesignatedDriverTrip>
    fun findByDriverIdOrderByCreatedAtDesc(driverId: String, pageable: Pageable): Page<DesignatedDriverTrip>
}
