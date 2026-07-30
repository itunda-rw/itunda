package rw.itunda.core.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import rw.itunda.core.domain.DebitCard
import rw.itunda.core.domain.DebitCardTransaction
import java.math.BigDecimal
import java.time.Instant

interface DebitCardRepository : JpaRepository<DebitCard, String> {
    fun findByUserId(userId: String): DebitCard?
}

interface DebitCardTransactionRepository : JpaRepository<DebitCardTransaction, String> {
    fun findByCardIdOrderByCreatedAtDesc(cardId: String, pageable: Pageable): Page<DebitCardTransaction>

    /** Real spend-window check backing CardService's daily/monthly limit enforcement --
     * sums real posted rows rather than a running counter, so there's nothing to reset
     * and nothing that can drift out of sync with the real transaction history. */
    @Query("select coalesce(sum(t.amount), 0) from DebitCardTransaction t where t.cardId = :cardId and t.createdAt >= :from")
    fun sumAmountByCardIdAndCreatedAtSince(@Param("cardId") cardId: String, @Param("from") from: Instant): BigDecimal
}
