package rw.itunda.merchant

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.core.domain.FacePayEnrollment
import rw.itunda.core.repository.FacePayEnrollmentRepository
import java.util.Optional

class FacePayServiceTest : BehaviorSpec({

    Given("a user with no Face Pay enrollment yet") {
        val facePayEnrollmentRepository = mockk<FacePayEnrollmentRepository>()
        val merchantService = mockk<MerchantService>()
        val service = FacePayService(facePayEnrollmentRepository, merchantService)

        every { facePayEnrollmentRepository.findByUserId("user_1") } returns null
        every { facePayEnrollmentRepository.save(any()) } answers { firstArg() }

        When("enrolling") {
            val enrollment = service.enroll("user_1")

            Then("it creates a real, active enrollment with no biometric data attached") {
                enrollment.userId shouldBe "user_1"
                enrollment.active shouldBe true
            }
        }

        When("attempting to collect via Face Pay before ever enrolling") {
            Then("it throws FacePayNotEnrolledException, never touching MerchantService") {
                try {
                    service.collect("user_1", "pi_1")
                    error("expected FacePayNotEnrolledException")
                } catch (e: FacePayNotEnrolledException) {
                    // expected
                }
                verify(exactly = 0) { merchantService.collect(any(), any(), any()) }
            }
        }

        When("revoking an enrollment that was never created") {
            Then("it throws FacePayNotEnrolledException") {
                try {
                    service.revoke("user_1")
                    error("expected FacePayNotEnrolledException")
                } catch (e: FacePayNotEnrolledException) {
                    // expected
                }
            }
        }
    }

    Given("a user with an active Face Pay enrollment") {
        val facePayEnrollmentRepository = mockk<FacePayEnrollmentRepository>()
        val merchantService = mockk<MerchantService>()
        val service = FacePayService(facePayEnrollmentRepository, merchantService)

        val enrollment = FacePayEnrollment(id = "facepay_1", userId = "user_1", active = true)
        every { facePayEnrollmentRepository.findByUserId("user_1") } returns enrollment
        every { facePayEnrollmentRepository.save(any()) } answers { firstArg() }

        When("collecting a real payment intent via Face Pay") {
            every { merchantService.collect("user_1", "pi_1", "FACE_PAY") } returns
                mapOf("transactionId" to "ledgertxn_1", "channel" to "FACE_PAY", "status" to "COMPLETED")

            val result = service.collect("user_1", "pi_1")

            Then("it delegates to MerchantService.collect with the real FACE_PAY channel") {
                result["channel"] shouldBe "FACE_PAY"
                verify(exactly = 1) { merchantService.collect("user_1", "pi_1", "FACE_PAY") }
            }
        }

        When("revoking it") {
            val revoked = service.revoke("user_1")

            Then("it real-deactivates the enrollment") {
                revoked.active shouldBe false
                revoked.revokedAt shouldBe enrollment.revokedAt
            }
        }
    }

    Given("a user whose enrollment was previously revoked") {
        val facePayEnrollmentRepository = mockk<FacePayEnrollmentRepository>()
        val merchantService = mockk<MerchantService>()
        val service = FacePayService(facePayEnrollmentRepository, merchantService)

        val revokedEnrollment = FacePayEnrollment(id = "facepay_1", userId = "user_1", active = false)
        every { facePayEnrollmentRepository.findByUserId("user_1") } returns revokedEnrollment
        every { facePayEnrollmentRepository.save(any()) } answers { firstArg() }

        When("attempting to collect via Face Pay") {
            Then("it throws FacePayNotEnrolledException -- a revoked enrollment can't pay") {
                try {
                    service.collect("user_1", "pi_1")
                    error("expected FacePayNotEnrolledException")
                } catch (e: FacePayNotEnrolledException) {
                    // expected
                }
            }
        }

        When("re-enrolling") {
            val enrollment = service.enroll("user_1")

            Then("it real-reactivates the same row rather than creating a duplicate") {
                enrollment.active shouldBe true
                enrollment.id shouldBe "facepay_1"
                verify(exactly = 0) { facePayEnrollmentRepository.save(match { it.id != "facepay_1" }) }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
