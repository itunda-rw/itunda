package rw.itunda.payment.db.repository

import com.itunda.payment.application.PaymentRepositoryPort
import com.itunda.payment.domain.MobileMoneyDetails
import com.itunda.payment.domain.Payment
import com.itunda.payment.domain.PaymentStatus
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import rw.itunda.payment.db.entity.PaymentRecordEntity

@Repository
class PaymentRepositoryImpl(
    private val records: JpaPaymentRecordRepository
) : PaymentRepositoryPort {

    override fun findByOrderId(orderId: String): Payment? =
        records.findById(orderId).orElse(null)?.toDomain()

    /**
     * A conditional SQL UPDATE is the cross-replica claim. Only one request can
     * move an eligible row into CONFIRMING; a JVM lock alone cannot provide this.
     */
    @Transactional
    override fun claimForConfirmation(orderId: String): Payment? {
        val changed = records.claimConfirmation(
            orderId = orderId,
            eligibleStatuses = setOf(PaymentStatus.READY, PaymentStatus.IN_PROGRESS),
            claimStatus = PaymentStatus.CONFIRMING
        )
        if (changed != 1) return null
        return records.findById(orderId).orElse(null)?.toDomain()
    }

    override fun save(payment: Payment): Payment =
        records.save(payment.toEntity()).toDomain()

    private fun PaymentRecordEntity.toDomain() = Payment(
        mId = mId,
        paymentKey = paymentKey,
        orderId = orderId,
        orderName = orderName,
        status = status,
        requestedAt = requestedAt,
        approvedAt = approvedAt,
        totalAmount = totalAmount,
        balanceAmount = balanceAmount,
        currency = currency,
        mobileMoney = if (
            mobileMoneyProvider != null && mobileMoneyPhone != null && mobileMoneyReceiptUrl != null
        ) MobileMoneyDetails(mobileMoneyProvider!!, mobileMoneyPhone!!, mobileMoneyReceiptUrl!!) else null,
        country = country,
        method = method
    )

    private fun Payment.toEntity() = PaymentRecordEntity(
        orderId = orderId,
        mId = mId,
        paymentKey = paymentKey,
        orderName = orderName,
        status = status,
        requestedAt = requestedAt,
        approvedAt = approvedAt,
        totalAmount = totalAmount,
        balanceAmount = balanceAmount,
        currency = currency,
        mobileMoneyProvider = mobileMoney?.provider,
        mobileMoneyPhone = mobileMoney?.phoneNumber,
        mobileMoneyReceiptUrl = mobileMoney?.receiptUrl,
        country = country,
        method = method
    )
}
