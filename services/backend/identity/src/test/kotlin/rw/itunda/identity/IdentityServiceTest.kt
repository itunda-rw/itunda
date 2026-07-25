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
import rw.itunda.core.domain.Merchant
import rw.itunda.core.domain.MerchantStatus
import rw.itunda.core.domain.User
import rw.itunda.core.repository.KycSubmissionRepository
import rw.itunda.core.repository.MerchantRepository
import rw.itunda.core.repository.UserRepository
import java.time.Instant
import java.util.Optional

class IdentityServiceTest : BehaviorSpec({

    Given("a user with no prior KYC submission") {
        val kycSubmissionRepository = mockk<KycSubmissionRepository>()
        val userRepository = mockk<UserRepository>()
        val merchantRepository = mockk<MerchantRepository>()
        // Real, not mocked -- pure and stateless, same convention as this repo's other
        // real-not-mocked deps (JwtService, QuoteStore) when the real thing is small and
        // self-contained.
        val service = IdentityService(kycSubmissionRepository, userRepository, DemoNidaVerificationService(), DemoKybVerificationService(), merchantRepository)

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
        val merchantRepository = mockk<MerchantRepository>()
        val service = IdentityService(kycSubmissionRepository, userRepository, DemoNidaVerificationService(), DemoKybVerificationService(), merchantRepository)

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
        val merchantRepository = mockk<MerchantRepository>()
        val service = IdentityService(kycSubmissionRepository, userRepository, DemoNidaVerificationService(), DemoKybVerificationService(), merchantRepository)

        every { kycSubmissionRepository.findByUserIdOrderBySubmittedAtDesc("user_7") } returns emptyList()
        val savedSlot = slot<KycSubmission>()
        every { kycSubmissionRepository.save(capture(savedSlot)) } answers { firstArg() }

        When("submitting a structurally valid PASSPORT") {
            service.submit("user_7", "PASSPORT", "P1234567", "doc-ref-3")

            Then("the real pre-check gives a real MATCHED/NOT_FOUND signal (2026-07-26) -- foreign residents now get real pre-check coverage, not an automatic 'unsupported' stub") {
                (savedSlot.captured.autoVerificationStatus == NidaVerificationStatus.MATCHED.name || savedSlot.captured.autoVerificationStatus == NidaVerificationStatus.NOT_FOUND.name) shouldBe true
            }
        }

        When("submitting a malformed PASSPORT number") {
            service.submit("user_7", "PASSPORT", "P1", "doc-ref-3b")

            Then("the submission is still created (a human still reviews it) but the real pre-check flags it as invalid format") {
                savedSlot.captured.autoVerificationStatus shouldBe NidaVerificationStatus.INVALID_FORMAT.name
            }
        }
    }

    Given("a merchant owner submitting a real business TIN for KYB") {
        val kycSubmissionRepository = mockk<KycSubmissionRepository>()
        val userRepository = mockk<UserRepository>()
        val merchantRepository = mockk<MerchantRepository>()
        val service = IdentityService(kycSubmissionRepository, userRepository, DemoNidaVerificationService(), DemoKybVerificationService(), merchantRepository)

        every { kycSubmissionRepository.findByUserIdOrderBySubmittedAtDesc("owner_1") } returns emptyList()
        val savedSlot = slot<KycSubmission>()
        every { kycSubmissionRepository.save(capture(savedSlot)) } answers { firstArg() }

        When("submitting a structurally valid 9-digit TIN") {
            service.submit("owner_1", "BUSINESS_TIN", "123456789", "doc-ref-4")

            Then("it routes to the real KYB pre-check, not the National ID one") {
                setOf(KybVerificationStatus.MATCHED.name, KybVerificationStatus.NOT_FOUND.name) shouldContain savedSlot.captured.autoVerificationStatus
            }
        }

        When("submitting a TIN that isn't exactly 9 digits") {
            service.submit("owner_1", "BUSINESS_TIN", "12345", "doc-ref-5")

            Then("the real pre-check flags it as invalid format") {
                savedSlot.captured.autoVerificationStatus shouldBe KybVerificationStatus.INVALID_FORMAT.name
            }
        }
    }

    Given("a user who already has a PENDING submission") {
        val kycSubmissionRepository = mockk<KycSubmissionRepository>()
        val userRepository = mockk<UserRepository>()
        val merchantRepository = mockk<MerchantRepository>()
        // Real, not mocked -- pure and stateless, same convention as this repo's other
        // real-not-mocked deps (JwtService, QuoteStore) when the real thing is small and
        // self-contained.
        val service = IdentityService(kycSubmissionRepository, userRepository, DemoNidaVerificationService(), DemoKybVerificationService(), merchantRepository)

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
        val merchantRepository = mockk<MerchantRepository>()
        // Real, not mocked -- pure and stateless, same convention as this repo's other
        // real-not-mocked deps (JwtService, QuoteStore) when the real thing is small and
        // self-contained.
        val service = IdentityService(kycSubmissionRepository, userRepository, DemoNidaVerificationService(), DemoKybVerificationService(), merchantRepository)

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
            Then("it never touches a merchant record for a personal-identity submission") {
                verify(exactly = 0) { merchantRepository.findByOwnerUserId(any()) }
            }
        }
    }

    Given("an ADMIN approving a real PENDING BUSINESS_TIN (KYB) submission") {
        val kycSubmissionRepository = mockk<KycSubmissionRepository>()
        val userRepository = mockk<UserRepository>()
        val merchantRepository = mockk<MerchantRepository>()
        val service = IdentityService(kycSubmissionRepository, userRepository, DemoNidaVerificationService(), DemoKybVerificationService(), merchantRepository)

        val submission = KycSubmission(id = "kyc_5", userId = "owner_2", documentType = "BUSINESS_TIN", documentNumber = "123456789", documentReference = "y", status = "PENDING", submittedAt = Instant.now())
        val merchant = Merchant(id = "merchant_1", ownerUserId = "owner_2", walletId = "wallet_1", businessName = "Kigali Coffee", status = MerchantStatus.ACTIVE)

        every { kycSubmissionRepository.findById("kyc_5") } returns Optional.of(submission)
        every { kycSubmissionRepository.save(any()) } answers { firstArg() }
        every { merchantRepository.findByOwnerUserId("owner_2") } returns merchant
        every { merchantRepository.save(any()) } answers { firstArg() }

        When("approving") {
            val decided = service.decide("kyc_5", "admin_1", approve = true, reason = null)

            Then("it flips the merchant's kybVerified flag, not the user's kycVerified flag") {
                decided.status shouldBe "VERIFIED"
                merchant.kybVerified shouldBe true
                verify(exactly = 1) { merchantRepository.save(merchant) }
                verify(exactly = 0) { userRepository.findById(any()) }
                verify(exactly = 0) { userRepository.save(any()) }
            }
        }
    }

    Given("an ADMIN approving a BUSINESS_TIN submission for an account with no registered merchant") {
        val kycSubmissionRepository = mockk<KycSubmissionRepository>()
        val userRepository = mockk<UserRepository>()
        val merchantRepository = mockk<MerchantRepository>()
        val service = IdentityService(kycSubmissionRepository, userRepository, DemoNidaVerificationService(), DemoKybVerificationService(), merchantRepository)

        val submission = KycSubmission(id = "kyc_6", userId = "owner_3", documentType = "BUSINESS_TIN", documentNumber = "123456789", documentReference = "y", status = "PENDING", submittedAt = Instant.now())
        every { kycSubmissionRepository.findById("kyc_6") } returns Optional.of(submission)
        every { kycSubmissionRepository.save(any()) } answers { firstArg() }
        every { merchantRepository.findByOwnerUserId("owner_3") } returns null

        When("approving") {
            Then("it real-fails rather than silently no-op-ing") {
                try {
                    service.decide("kyc_6", "admin_1", approve = true, reason = null)
                    error("expected IdentityUserNotFoundException")
                } catch (e: IdentityUserNotFoundException) {
                    // expected
                }
            }
        }
    }

    Given("an ADMIN rejecting a real PENDING submission") {
        val kycSubmissionRepository = mockk<KycSubmissionRepository>()
        val userRepository = mockk<UserRepository>()
        val merchantRepository = mockk<MerchantRepository>()
        // Real, not mocked -- pure and stateless, same convention as this repo's other
        // real-not-mocked deps (JwtService, QuoteStore) when the real thing is small and
        // self-contained.
        val service = IdentityService(kycSubmissionRepository, userRepository, DemoNidaVerificationService(), DemoKybVerificationService(), merchantRepository)

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
        val merchantRepository = mockk<MerchantRepository>()
        // Real, not mocked -- pure and stateless, same convention as this repo's other
        // real-not-mocked deps (JwtService, QuoteStore) when the real thing is small and
        // self-contained.
        val service = IdentityService(kycSubmissionRepository, userRepository, DemoNidaVerificationService(), DemoKybVerificationService(), merchantRepository)

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
