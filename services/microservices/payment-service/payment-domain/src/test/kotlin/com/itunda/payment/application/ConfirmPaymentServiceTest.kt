package com.itunda.payment.application

import com.itunda.payment.domain.Payment
import com.itunda.payment.domain.PaymentStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class ConfirmPaymentServiceTest {

    @Test
    fun `concurrent confirmation requests start at most one provider operation`() {
        val repository = FakePaymentRepository()
        val providerCalls = AtomicInteger()
        val gateway = object : PaymentGatewayPort {
            override fun processMobileMoneyTransfer(
                paymentKey: String,
                amount: Long,
                idempotencyKey: String
            ): Boolean {
                providerCalls.incrementAndGet()
                // Widen the race window so competing requests attempt the DB claim.
                Thread.sleep(40)
                return true
            }
        }
        val service = ConfirmPaymentService(repository, gateway)
        val command = ConfirmPaymentCommand(
            paymentKey = "payment-key-1",
            orderId = "order-1",
            amount = 1000L,
            idempotencyKey = "client-request-key"
        )

        val workers = 12
        val ready = CountDownLatch(workers)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(workers)
        try {
            val futures = (1..workers).map {
                executor.submit {
                    ready.countDown()
                    start.await()
                    runCatching { service.confirm(command) }
                }
            }
            assertTrue(ready.await(3, TimeUnit.SECONDS), "workers did not become ready")
            start.countDown()
            futures.forEach { it.get(5, TimeUnit.SECONDS) }

            assertEquals(1, providerCalls.get(), "only one request may call the provider")
            assertEquals(PaymentStatus.DONE, repository.currentPayment()?.status)
        } finally {
            executor.shutdownNow()
        }
    }

    private class FakePaymentRepository : PaymentRepositoryPort {
        private var payment: Payment = Payment(
            mId = "itunda",
            paymentKey = "payment-key-1",
            orderId = "order-1",
            orderName = "Test payment",
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

        @Synchronized
        override fun findByOrderId(orderId: String): Payment? =
            payment.takeIf { it.orderId == orderId }

        @Synchronized
        override fun claimForConfirmation(orderId: String): Payment? {
            if (payment.orderId != orderId) return null
            if (payment.status != PaymentStatus.READY && payment.status != PaymentStatus.IN_PROGRESS) {
                return null
            }
            payment = payment.copy(status = PaymentStatus.CONFIRMING)
            return payment
        }

        @Synchronized
        override fun save(payment: Payment): Payment {
            this.payment = payment
            return payment
        }

        @Synchronized
        fun currentPayment(): Payment = payment
    }
}
