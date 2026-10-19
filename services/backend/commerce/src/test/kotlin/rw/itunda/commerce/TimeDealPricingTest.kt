package rw.itunda.commerce

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.core.domain.TimeDeal
import rw.itunda.core.repository.TimeDealRepository
import java.math.BigDecimal
import java.time.Instant

class TimeDealPricingTest : FunSpec({
    val now = Instant.parse("2026-10-14T00:00:00Z")

    fun deal(remaining: Int = 3) = TimeDeal(
        id = "deal_1",
        merchantId = "merchant_1",
        productId = "product_1",
        dealPrice = BigDecimal("1500"),
        originalPrice = BigDecimal("2000"),
        totalQuantity = 3,
        remainingQuantity = remaining,
        startsAt = now.minusSeconds(60),
        endsAt = now.plusSeconds(60),
    )

    test("uses deal price only after the database confirms inventory reservation") {
        val repository = mockk<TimeDealRepository>()
        every { repository.findActiveDealForProduct("product_1", now) } returns deal()
        every { repository.reserveQuantityIfAvailable("deal_1", 2, now) } returns 1

        resolveTimeDealUnitPrice(repository, "product_1", 2, now) {
            error("normal pricing must not be used after a successful reservation")
        } shouldBe BigDecimal("1500")

        verify(exactly = 1) { repository.reserveQuantityIfAvailable("deal_1", 2, now) }
    }

    test("falls back to normal price when another checkout wins the reservation") {
        val repository = mockk<TimeDealRepository>()
        every { repository.findActiveDealForProduct("product_1", now) } returns deal()
        every { repository.reserveQuantityIfAvailable("deal_1", 2, now) } returns 0
        var fallbackCalls = 0

        resolveTimeDealUnitPrice(repository, "product_1", 2, now) {
            fallbackCalls += 1
            BigDecimal("2000")
        } shouldBe BigDecimal("2000")

        fallbackCalls shouldBe 1
    }

    test("does not attempt a reservation when no active deal exists") {
        val repository = mockk<TimeDealRepository>()
        every { repository.findActiveDealForProduct("product_1", now) } returns null

        resolveTimeDealUnitPrice(repository, "product_1", 1, now) {
            BigDecimal("2000")
        } shouldBe BigDecimal("2000")

        verify(exactly = 0) { repository.reserveQuantityIfAvailable(any(), any(), any()) }
    }

    test("does not reserve a deal when requested quantity exceeds remaining inventory") {
        val repository = mockk<TimeDealRepository>()
        every { repository.findActiveDealForProduct("product_1", now) } returns deal(remaining = 1)

        resolveTimeDealUnitPrice(repository, "product_1", 2, now) {
            BigDecimal("2000")
        } shouldBe BigDecimal("2000")

        verify(exactly = 0) { repository.reserveQuantityIfAvailable(any(), any(), any()) }
    }
})
