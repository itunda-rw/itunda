package rw.itunda.core.repository

import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import rw.itunda.core.domain.DebitCard
import rw.itunda.core.domain.DebitCardTransaction
import java.math.BigDecimal
import java.time.Instant
import java.util.Optional

interface DebitCardRepository : JpaRepository<DebitCard, String> {
    fun findByUserId(userId: String): DebitCard?

    // Real bug found live (2026-08-02): CardService.chargeWithCard computes
    // spentToday/spentThisMonth as a live SUM over DebitCardTransaction rows (correctly
    // avoiding a running-counter's drift risk, per that query's own doc comment), then
    // checks it against dailyLimit/monthlyLimit before inserting a new transaction row
    // and posting the real ledger debit -- but nothing locks the card across that
    // check-then-insert. Two concurrent chargeWithCard calls for the same card (e.g.
    // two devices, or a client retry racing the original) could both read the same
    // pre-charge sum before either commits, both pass the limit check, and both post
    // real money -- together exceeding the card's own documented daily/monthly limit.
    // Locking the card row for the duration of the check-then-insert (same
    // findByIdForUpdate convention WalletRepository/FraudFlagRepository/
    // FloatMarketplaceRepositories already use) serializes concurrent charges on one
    // card so the aggregate-sum check is actually atomic.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from DebitCard c where c.id = :id")
    fun findByIdForUpdate(@Param("id") id: String): Optional<DebitCard>
}

interface DebitCardTransactionRepository : JpaRepository<DebitCardTransaction, String> {
    fun findByCardIdOrderByCreatedAtDesc(cardId: String, pageable: Pageable): Page<DebitCardTransaction>

    /** Real spend-window check backing CardService's daily/monthly limit enforcement --
     * sums real posted rows rather than a running counter, so there's nothing to reset
     * and nothing that can drift out of sync with the real transaction history. */
    @Query("select coalesce(sum(t.amount), 0) from DebitCardTransaction t where t.cardId = :cardId and t.createdAt >= :from")
    fun sumAmountByCardIdAndCreatedAtSince(@Param("cardId") cardId: String, @Param("from") from: Instant): BigDecimal
}
