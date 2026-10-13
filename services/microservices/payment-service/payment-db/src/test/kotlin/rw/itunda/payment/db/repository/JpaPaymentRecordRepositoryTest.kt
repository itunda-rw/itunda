package rw.itunda.payment.db.repository

import com.itunda.payment.domain.PaymentStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import rw.itunda.payment.db.entity.PaymentRecordEntity
import java.time.OffsetDateTime

/**
 * Exercises the conditional claim against a real JPA provider and an isolated
 * relational database, rather than relying only on the application fake repository.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class JpaPaymentRecordRepositoryTest {

    @Autowired
    lateinit var records: JpaPaymentRecordRepository

    @Test
    fun `only one claim can move an eligible order into confirming`() {
        records.deleteAll()
        records.saveAndFlush(
            PaymentRecordEntity(
                orderId = "integration-order-1",
                mId = "itunda",
                paymentKey = "payment-key-1",
                orderName = "Integration test",
                status = PaymentStatus.IN_PROGRESS,
                requestedAt = OffsetDateTime.parse("2026-01-01T00:00:00Z"),
                totalAmount = 1000L,
                balanceAmount = 1000L,
                currency = "RWF",
                country = "RW",
                method = "MOMO"
            )
        )

        val eligible = setOf(PaymentStatus.READY, PaymentStatus.IN_PROGRESS)
        val firstClaim = records.claimConfirmation(
            "integration-order-1",
            eligible,
            PaymentStatus.CONFIRMING
        )
        val secondClaim = records.claimConfirmation(
            "integration-order-1",
            eligible,
            PaymentStatus.CONFIRMING
        )

        assertEquals(1, firstClaim, "the first conditional update should claim the row")
        assertEquals(0, secondClaim, "a second claim must not claim an already-confirming row")
        assertEquals(
            PaymentStatus.CONFIRMING,
            records.findById("integration-order-1").orElseThrow().status
        )
        assertNotNull(records.findById("integration-order-1").orElse(null))
        records.deleteById("integration-order-1")
    }
}
