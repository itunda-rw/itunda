package rw.itunda.payment.db.repository

import com.itunda.payment.domain.PaymentStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import rw.itunda.payment.db.entity.PaymentRecordEntity

interface JpaPaymentRecordRepository : JpaRepository<PaymentRecordEntity, String> {
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query(
        "update PaymentRecordEntity p set p.status = :claimStatus " +
            "where p.orderId = :orderId and p.status in :eligibleStatuses"
    )
    fun claimConfirmation(
        @Param("orderId") orderId: String,
        @Param("eligibleStatuses") eligibleStatuses: Set<PaymentStatus>,
        @Param("claimStatus") claimStatus: PaymentStatus
    ): Int
}
