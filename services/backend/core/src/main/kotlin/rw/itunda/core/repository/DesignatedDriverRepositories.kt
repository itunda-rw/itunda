package rw.itunda.core.repository

import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import rw.itunda.core.domain.DesignatedDriver
import rw.itunda.core.domain.DesignatedDriverTrip
import java.util.Optional

interface DesignatedDriverRepository : JpaRepository<DesignatedDriver, String> {
    fun findByUserId(userId: String): DesignatedDriver?
}

interface DesignatedDriverTripRepository : JpaRepository<DesignatedDriverTrip, String> {
    fun findByCustomerIdOrderByCreatedAtDesc(customerId: String, pageable: Pageable): Page<DesignatedDriverTrip>
    fun findByDriverIdOrderByCreatedAtDesc(driverId: String, pageable: Pageable): Page<DesignatedDriverTrip>

    // Real fix (concurrency audit, 2026-08-21): completeTrip checks status == DRIVING,
    // posts real ledger payout money, then writes status = COMPLETED -- only one real
    // caller may release the same trip's fare.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from DesignatedDriverTrip t where t.id = :id")
    fun findByIdForUpdate(@Param("id") id: String): Optional<DesignatedDriverTrip>
}
