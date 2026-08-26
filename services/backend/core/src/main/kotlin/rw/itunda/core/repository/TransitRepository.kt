package rw.itunda.core.repository

import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import rw.itunda.core.domain.TransitBalance
import rw.itunda.core.domain.TransitTrip
import java.util.Optional

interface TransitBalanceRepository : JpaRepository<TransitBalance, String> {
    fun findByUserId(userId: String): TransitBalance?

    // Same real check-then-write race DebitCardRepository.findByIdForUpdate already
    // guards against: a top-up and a fare tap for the same user, or two concurrent taps,
    // must not both read the same pre-write balance before either commits.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from TransitBalance b where b.id = :id")
    fun findByIdForUpdate(@Param("id") id: String): Optional<TransitBalance>
}

interface TransitTripRepository : JpaRepository<TransitTrip, String> {
    fun findByUserIdOrderByCreatedAtDesc(userId: String, pageable: Pageable): Page<TransitTrip>
}
