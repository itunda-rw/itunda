package rw.itunda.overview

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.longs.shouldBeGreaterThanOrEqual
import io.kotest.matchers.longs.shouldBeLessThan
import java.math.BigDecimal

class DemoExternalBalanceServiceTest : BehaviorSpec({

    val service = DemoExternalBalanceService()

    Given("the same provider and account number") {
        When("generating a demo balance twice") {
            val first = service.generate("MTN MoMo", "0788123456")
            val second = service.generate("MTN MoMo", "0788123456")

            Then("it's deterministic -- not randomly flaky on repeated calls") {
                first shouldBe second
            }
        }
    }

    Given("different account numbers") {
        When("generating demo balances") {
            val a = service.generate("MTN MoMo", "0788111111")
            val b = service.generate("Bank of Kigali", "1234567890")

            Then("they land in a real, realistic RWF range, not an arbitrary or negative number") {
                a.toLong() shouldBeGreaterThanOrEqual 20_000L
                a.toLong() shouldBeLessThan 3_020_000L
                b.toLong() shouldBeGreaterThanOrEqual 20_000L
                b.toLong() shouldBeLessThan 3_020_000L
            }
            Then("they real-scale to exactly 2 decimal places, matching a real currency amount") {
                a.scale() shouldBe 2
            }
        }
    }
})
