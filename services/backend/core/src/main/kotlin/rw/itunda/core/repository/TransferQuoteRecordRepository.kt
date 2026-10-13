package rw.itunda.core.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import rw.itunda.core.domain.TransferQuoteRecord
import rw.itunda.core.domain.TransferQuoteRecordStatus
import java.time.Instant

interface TransferQuoteRecordRepository : JpaRepository<TransferQuoteRecord, String> {
    /** Atomic single-winner claim shared across all application instances. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update TransferQuoteRecord q
           set q.status = :claimed, q.claimedAt = :claimedAt
         where q.id = :id
           and q.userId = :userId
           and q.status = :pending
           and q.expiresAt > :now
    """)
    fun claimPending(
        @Param("id") id: String,
        @Param("userId") userId: String,
        @Param("pending") pending: TransferQuoteRecordStatus,
        @Param("claimed") claimed: TransferQuoteRecordStatus,
        @Param("now") now: Instant,
        @Param("claimedAt") claimedAt: Instant,
    ): Int

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update TransferQuoteRecord q
           set q.status = :pending, q.claimedAt = null
         where q.id = :id and q.status = :claimed
    """)
    fun releaseClaim(
        @Param("id") id: String,
        @Param("claimed") claimed: TransferQuoteRecordStatus,
        @Param("pending") pending: TransferQuoteRecordStatus,
    ): Int

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update TransferQuoteRecord q
           set q.status = :confirmed, q.confirmedAt = :confirmedAt
         where q.id = :id and q.status = :claimed
    """)
    fun markConfirmed(
        @Param("id") id: String,
        @Param("claimed") claimed: TransferQuoteRecordStatus,
        @Param("confirmed") confirmed: TransferQuoteRecordStatus,
        @Param("confirmedAt") confirmedAt: Instant,
    ): Int
}
