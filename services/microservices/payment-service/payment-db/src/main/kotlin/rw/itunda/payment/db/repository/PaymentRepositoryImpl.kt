package rw.itunda.payment.db.repository

import com.itunda.payment.application.PaymentRepositoryPort
import com.itunda.payment.domain.Payment
import com.itunda.payment.domain.PaymentStatus
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.util.concurrent.ConcurrentHashMap

@Repository
class PaymentRepositoryImpl : PaymentRepositoryPort {
    
    // In-memory simulation for MVP instead of setting up complete JPA Entities
    private val store = ConcurrentHashMap<String, Payment>()

    override fun findByOrderId(orderId: String): Payment? {
        // Fallback simulated payment if it's the specific test order
        if (orderId == "order_simulated") {
            return store.computeIfAbsent(orderId) {
                Payment(
                    mId = "tosspayments",
                    paymentKey = "pay_key_simulated",
                    orderId = "order_simulated",
                    orderName = "P2P Transfer Simulation",
                    status = PaymentStatus.IN_PROGRESS,
                    requestedAt = OffsetDateTime.now(),
                    approvedAt = null,
                    totalAmount = 1000L,
                    balanceAmount = 1000L,
                    currency = "RWF",
                    mobileMoney = null,
                    country = "RW",
                    method = "MOMO"
                )
            }
        }
        return store[orderId]
    }

    override fun save(payment: Payment): Payment {
        store[payment.orderId] = payment
        return payment
    }
}
