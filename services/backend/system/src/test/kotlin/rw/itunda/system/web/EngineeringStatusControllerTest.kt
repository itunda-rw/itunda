package rw.itunda.system.web

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import rw.itunda.system.engineering.EngineeringStatusProvider

class EngineeringStatusControllerTest : BehaviorSpec({
    Given("the engineering status endpoint") {
        val provider = mockk<EngineeringStatusProvider>()
        val controller = EngineeringStatusController(provider)

        When("GitHub returns a configured status snapshot") {
            every { provider.readStatus() } returns mapOf(
                "generatedAt" to "2026-10-13T00:00:00Z",
                "repository" to "itunda-rw/itunda",
                "branch" to "main",
                "headSha" to "0123456789012345678901234567890123456789",
                "requiredCheckIds" to listOf("CI"),
                "checks" to listOf(mapOf("id" to "CI", "result" to "success")),
            )
            val response = controller.status()

            Then("it returns the provider snapshot unchanged") {
                response.statusCode.value() shouldBe 200
                response.body?.get("requiredCheckIds") shouldBe listOf("CI")
                response.body?.get("headSha") shouldBe "0123456789012345678901234567890123456789"
            }
        }

        When("GitHub or configuration is unavailable") {
            every { provider.readStatus() } throws IllegalStateException("not configured")
            val response = controller.status()

            Then("it returns unavailable rather than fabricated healthy data") {
                response.statusCode.value() shouldBe 503
                response.body?.get("code") shouldBe "ENGINEERING_STATUS_UNAVAILABLE"
                response.body?.get("message") shouldBe "Engineering status is unavailable or not configured."
                response.body?.containsKey("checks") shouldBe false
                response.body.toString().contains("not configured") shouldBe true
                response.body.toString().contains("IllegalStateException") shouldBe false
            }
        }
    }
})
