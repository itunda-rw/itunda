package rw.itunda.identity

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import rw.itunda.core.domain.KycSubmission
import rw.itunda.core.domain.User
import rw.itunda.core.repository.KycSubmissionRepository
import rw.itunda.core.repository.UserRepository
import java.time.Instant
import java.util.Optional

class IdentityServiceTest : BehaviorSpec({

    Given("a user with no prior KYC submission") {
        val kycSubmissionRepository = mockk<KycSubmissionRepository>()
        val userRepository = mockk<UserRepository>()
        val service = IdentityService(kycSubmissionRepository, userRepository)

        every { kycSubmissionRepository.findByUserIdOrderBySubmittedAtDesc("user_1") } returns emptyList()
        val savedSlot = slot<KycSubmission>()
        every { kycSubmissionRepository.save(capture(savedSlot)) } answers { firstArg() }

        When("submitting a national ID") {
            val submission = service.submit("user_1", "NATIONAL_ID", "1198080012345678", "doc-ref-1")

            Then("it creates a real PENDING submission, not an auto-approved one") {
                submission.status shouldBe "PENDING"
                submission.userId shouldBe "user_1"
                submission.documentNumber shouldBe "1198080012345678"
                verify(exactly = 1) { kycSubmissionRepository.save(any()) }
            }
        }
    }

    Given("a user who already has a PENDING submission") {
        val kycSubmissionRepository = mockk<KycSubmissionRepository>()
        val userRepository = mockk<UserRepository>()
        val service = IdentityService(kycSubmissionRepository, userRepository)

        every { kycSubmissionRepository.findByUserIdOrderBySubmittedAtDesc("user_2") } returns listOf(
            KycSubmission(id = "kyc_1", userId = "user_2", documentType = "NATIONAL_ID", documentNumber = "x", documentReference = "y", status = "PENDING", submittedAt = Instant.now()),
        )

        When("submitting again") {
            Then("it throws SubmissionAlreadyPendingException rather than creating a second row") {
                try {
                    service.submit("user_2", "NATIONAL_ID", "x", "y")
                    error("expected SubmissionAlreadyPendingException")
                } catch (e: SubmissionAlreadyPendingException) {
                    verify(exactly = 0) { kycSubmissionRepository.save(any()) }
                }
            }
        }
    }

    Given("an ADMIN approving a real PENDING submission") {
        val kycSubmissionRepository = mockk<KycSubmissionRepository>()
        val userRepository = mockk<UserRepository>()
        val service = IdentityService(kycSubmissionRepository, userRepository)

        val submission = KycSubmission(id = "kyc_2", userId = "user_3", documentType = "NATIONAL_ID", documentNumber = "x", documentReference = "y", status = "PENDING", submittedAt = Instant.now())
        val user = User(id = "user_3", phoneNumber = "0788000000", firstName = "Test", lastName = "User", passwordHash = "hash")

        every { kycSubmissionRepository.findById("kyc_2") } returns Optional.of(submission)
        every { kycSubmissionRepository.save(any()) } answers { firstArg() }
        every { userRepository.findById("user_3") } returns Optional.of(user)
        every { userRepository.save(any()) } answers { firstArg() }

        When("approving") {
            val decided = service.decide("kyc_2", "admin_1", approve = true, reason = null)

            Then("it marks the submission VERIFIED and records who reviewed it") {
                decided.status shouldBe "VERIFIED"
                decided.reviewedBy shouldBe "admin_1"
            }
            Then("it flips the user's kycVerified flag for real -- this is the actual gap being closed, not just a new table") {
                user.kycVerified shouldBe true
                verify(exactly = 1) { userRepository.save(user) }
            }
        }
    }

    Given("an ADMIN rejecting a real PENDING submission") {
        val kycSubmissionRepository = mockk<KycSubmissionRepository>()
        val userRepository = mockk<UserRepository>()
        val service = IdentityService(kycSubmissionRepository, userRepository)

        val submission = KycSubmission(id = "kyc_3", userId = "user_4", documentType = "NATIONAL_ID", documentNumber = "x", documentReference = "y", status = "PENDING", submittedAt = Instant.now())

        every { kycSubmissionRepository.findById("kyc_3") } returns Optional.of(submission)
        every { kycSubmissionRepository.save(any()) } answers { firstArg() }

        When("rejecting with a reason") {
            val decided = service.decide("kyc_3", "admin_1", approve = false, reason = "Document photo illegible")

            Then("it marks the submission REJECTED and never touches the user or its verified flag") {
                decided.status shouldBe "REJECTED"
                decided.decisionReason shouldBe "Document photo illegible"
                verify(exactly = 0) { userRepository.findById(any()) }
                verify(exactly = 0) { userRepository.save(any()) }
            }
        }
    }

    Given("a submission that was already decided") {
        val kycSubmissionRepository = mockk<KycSubmissionRepository>()
        val userRepository = mockk<UserRepository>()
        val service = IdentityService(kycSubmissionRepository, userRepository)

        val submission = KycSubmission(id = "kyc_4", userId = "user_5", documentType = "NATIONAL_ID", documentNumber = "x", documentReference = "y", status = "VERIFIED", submittedAt = Instant.now())
        every { kycSubmissionRepository.findById("kyc_4") } returns Optional.of(submission)

        When("trying to decide it again") {
            Then("it throws SubmissionNotPendingException instead of silently re-deciding") {
                try {
                    service.decide("kyc_4", "admin_1", approve = false, reason = "changed my mind")
                    error("expected SubmissionNotPendingException")
                } catch (e: SubmissionNotPendingException) {
                    verify(exactly = 0) { kycSubmissionRepository.save(any()) }
                }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
