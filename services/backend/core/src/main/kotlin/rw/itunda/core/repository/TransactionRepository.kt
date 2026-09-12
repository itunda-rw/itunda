package rw.itunda.core.repository

import java.time.Instant
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import rw.itunda.core.domain.Transaction
import rw.itunda.core.domain.TransactionStatus
import rw.itunda.core.domain.TransactionType

// Extracted from Repositories.kt (2026-09-12, file-size-lint) -- TransactionRepository
// had grown into the single largest interface in that file (108 lines), the same
// "code that changes together lives together" split every other multi-repository file
// in this package already follows (MerchantCouponRepositories.kt, EatsFavoriteRepository.kt,
// GroupAnnouncementPollRepositories.kt), not a new pattern.
interface RecipientTransactionCountProjection {
    val recipientId: String
    val count: Long
}

interface TransactionRepository : JpaRepository<Transaction, String> {
    fun findBySenderIdOrRecipientIdOrderByCreatedAtDesc(senderId: String, recipientId: String): List<Transaction>
    // Real Toss Bank/Toss Pay separation follow-up (2026-08-21, user-provided real
    // screenshots): the real Toss Bank account detail screen and the real "Toss Pay
    // Money" detail screen each show their OWN separate transaction ledger, not one
    // shared user-wide list -- findBySenderIdOrRecipientIdOrderByCreatedAtDesc above
    // mixes every account's transactions together, which is honest for a general
    // history view but wrong for either detail screen specifically. accountId is
    // matched against the real fromAccountId/toAccountId columns every Transaction
    // row already carries (see MerchantService.collect/AccountService.confirmTransfer
    // etc. -- every money-moving write already sets one or both).
    fun findByFromAccountIdOrToAccountIdOrderByCreatedAtDesc(fromAccountId: String, toAccountId: String): List<Transaction>
    fun existsBySenderIdAndTypeAndStatus(senderId: String, type: TransactionType, status: TransactionStatus): Boolean

    /** Operations dashboard: settled volume belongs to the day a transaction completed. */
    @Query("select coalesce(sum(t.amount), 0) from Transaction t where t.status = :status and t.completedAt >= :from and t.completedAt < :to")
    fun sumAmountByStatusAndCompletedAtBetween(
        @Param("status") status: TransactionStatus,
        @Param("from") from: Instant,
        @Param("to") to: Instant,
    ): java.math.BigDecimal

    fun countByStatusAndCompletedAtGreaterThanEqualAndCompletedAtLessThan(
        status: TransactionStatus,
        from: Instant,
        to: Instant,
    ): Long

    // Merchant reports (2026-07-16): a merchant collection's Transaction row has
    // recipientId = the merchant owner's userId (see MerchantService.collect), so this
    // is the real join key -- not a merchant id or account id.
    fun findByRecipientIdAndTypeAndCreatedAtBetween(
        recipientId: String,
        type: TransactionType,
        from: Instant,
        to: Instant,
    ): List<Transaction>

    // Real FamilyLink daily spend-limit enforcement (2026-07-27) -- see FamilyLink.kt's
    // own doc comment. The child's own real sends since a real UTC day boundary,
    // summed in the service (same "coarse repo filter, exact logic in the service"
    // discipline the recurring-payment-detection query above already uses), not a new
    // running-total counter column that could drift from the real ledger.
    fun findBySenderIdAndTypeAndStatusAndCreatedAtGreaterThanEqual(
        senderId: String,
        type: TransactionType,
        status: TransactionStatus,
        from: Instant,
    ): List<Transaction>

    // Real YouthAccountService daily/monthly deposit-cap enforcement (2026-07-28) -- see
    // that class's own doc comment. Coarse repo filter (this account's own real deposits
    // since a real window start), exact cap comparison in the service, same discipline
    // the recurring-payment-detection/FamilyLink spend-limit queries above establish.
    @Query("select coalesce(sum(t.amount), 0) from Transaction t where t.toAccountId = :accountId and t.type = :type and t.status = :status and t.createdAt >= :from")
    fun sumAmountByToAccountIdAndTypeAndStatusAndCreatedAtGreaterThanEqual(
        @Param("accountId") accountId: String,
        @Param("type") type: TransactionType,
        @Param("status") status: TransactionStatus,
        @Param("from") from: Instant,
    ): java.math.BigDecimal

    // Real 단골 (regular customer) detection for MerchantCouponService -- a merchant
    // collection's Transaction row has recipientId = the merchant owner's userId (same
    // real join key as the report query above), senderId = the paying customer.
    fun countBySenderIdAndRecipientIdAndTypeAndStatus(
        senderId: String,
        recipientId: String,
        type: TransactionType,
        status: TransactionStatus,
    ): Long

    // Real N+1 fix (2026-09-12) -- MerchantCouponService.browseCoupons used to call
    // countBySenderIdAndRecipientIdAndTypeAndStatus above once per distinct merchant
    // owner across the whole cross-merchant Coupon Box browse (a real, frequently-hit
    // customer screen per that method's own doc comment). One batched GROUP BY instead,
    // same real "batch, don't N+1" discipline EatsFavoriteRepository.getFavoriteCounts
    // already established for an identical-shape browse-page problem.
    @Query(
        "SELECT t.recipientId as recipientId, COUNT(t) as count FROM Transaction t " +
            "WHERE t.senderId = :senderId AND t.recipientId IN :recipientIds AND t.type = :type AND t.status = :status " +
            "GROUP BY t.recipientId",
    )
    fun countBySenderIdAndRecipientIdInAndTypeAndStatus(
        @Param("senderId") senderId: String,
        @Param("recipientIds") recipientIds: List<String>,
        @Param("type") type: TransactionType,
        @Param("status") status: TransactionStatus,
    ): List<RecipientTransactionCountProjection>

    // Real recurring-payment detection for SubscriptionDetectionService -- a coarse
    // fetch of everything real money this user has ever sent out via a recurring-
    // capable channel (TRANSFER/PAYMENT/BILL -- DEPOSIT/WITHDRAWAL/AIRTIME/LOAN aren't
    // subscription-like), ascending so consecutive-occurrence intervals compute forward
    // in time. Real pattern detection itself happens in application code, same
    // "coarse repo filter, exact logic in the service" discipline this codebase already
    // uses for nearby()/spending-insight categorization.
    fun findBySenderIdAndTypeInAndStatusOrderByCreatedAtAsc(
        senderId: String,
        types: List<TransactionType>,
        status: TransactionStatus,
    ): List<Transaction>
}
