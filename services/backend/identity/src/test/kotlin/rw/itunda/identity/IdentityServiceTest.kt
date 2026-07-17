package rw.itunda.identity

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
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
        // Real, not mocked -- pure and stateless, same convention as this repo's other
        // real-not-mocked deps (JwtService, QuoteStore) when the real thing is small and
        // self-contained.
        val service = IdentityService(kycSubmissionRepository, userRepository, DemoNidaVerificationService())

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
            Then("it runs the real structural pre-check and stores a real result -- not honor-system") {
                setOf(NidaVerificationStatus.MATCHED.name, NidaVerificationStatus.NOT_FOUND.name) shouldContain submission.autoVerificationStatus
                submission.autoVerificationDetail shouldNotBe null
            }
        }
    }

    Given("a user submitting a structurally invalid National ID") {
        val kycSubmissionRepository = mockk<KycSubmissionRepository>()
        val userRepository = mockk<UserRepository>()
        val service = IdentityService(kycSubmissionRepository, userRepository, DemoNidaVerificationService())

        every { kycSubmissionRepository.findByUserIdOrderBySubmittedAtDesc("user_6") } returns emptyList()
        val savedSlot = slot<KycSubmission>()
        every { kycSubmissionRepository.save(capture(savedSlot)) } answers { firstArg() }

        When("submitting a 10-digit number, not a real 16-digit National ID") {
            service.submit("user_6", "NATIONAL_ID", "1234567890", "doc-ref-2")

            Then("the submission is still created (a human still reviews it) but the real pre-check flags it as invalid format") {
                savedSlot.captured.autoVerificationStatus shouldBe NidaVerificationStatus.INVALID_FORMAT.name
            }
        }
    }

    Given("a user submitting a passport, not a National ID") {
        val kycSubmissionRepository = mockk<KycSubmissionRepository>()
        val userRepository = mockk<UserRepository>()
        val service = IdentityService(kycSubmissionRepository, userRepository, DemoNidaVerificationService())

        every { kycSubmissionRepository.findByUserIdOrderBySubmittedAtDesc("user_7") } returns emptyList()
        val savedSlot = slot<KycSubmission>()
        every { kycSubmissionRepository.save(capture(savedSlot)) } answers { firstArg() }

        When("submitting a PASSPORT") {
            service.submit("user_7", "PASSPORT", "P1234567", "doc-ref-3")

            Then("the real pre-check honestly reports it doesn't cover this document type, rather than guessing") {
                savedSlot.captured.autoVerificationStatus shouldBe NidaVerificationStatus.UNSUPPORTED_DOCUMENT_TYPE.name
            }
        }
    }

    Given("a user who already has a PENDING submission") {
        val kycSubmissionRepository = mockk<KycSubmissionRepository>()
        val userRepository = mockk<UserRepository>()
        // Real, not mocked -- pure and stateless, same convention as this repo's other
        // real-not-mocked deps (JwtService, QuoteStore) when the real thing is small and
        // self-contained.
        val service = IdentityService(kycSubmissionRepository, userRepository, DemoNidaVerificationService())

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
        // Real, not mocked -- pure and stateless, same convention as this repo's other
        // real-not-mocked deps (JwtService, QuoteStore) when the real thing is small and
        // self-contained.
        val service = IdentityService(kycSubmissionRepository, userRepository, DemoNidaVerificationService())

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
        // Real, not mocked -- pure and stateless, same convention as this repo's other
        // real-not-mocked deps (JwtService, QuoteStore) when the real thing is small and
        // self-contained.
        val service = IdentityService(kycSubmissionRepository, userRepository, DemoNidaVerificationService())

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
        // Real, not mocked -- pure and stateless, same convention as this repo's other
        // real-not-mocked deps (JwtService, QuoteStore) when the real thing is small and
        // self-contained.
        val service = IdentityService(kycSubmissionRepository, userRepository, DemoNidaVerificationService())

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
