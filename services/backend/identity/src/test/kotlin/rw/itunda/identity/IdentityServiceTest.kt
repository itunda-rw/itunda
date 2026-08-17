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
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.KycSubmission
import rw.itunda.core.domain.Merchant
import rw.itunda.core.domain.MerchantStatus
import rw.itunda.core.domain.User
import rw.itunda.core.domain.Notification
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.KycSubmissionRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.MerchantRepository
import rw.itunda.core.repository.UserRepository
import java.time.Duration
import java.time.Instant
import java.util.Optional

class IdentityServiceTest : BehaviorSpec({

    Given("a user with no prior KYC submission") {
        val kycSubmissionRepository = mockk<KycSubmissionRepository>()
        val userRepository = mockk<UserRepository>()
        val merchantRepository = mockk<MerchantRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val notificationRepository = mockk<NotificationRepository>()
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        every { notificationRepository.save(any()) } answers { firstArg() }
        // Real, not mocked -- pure and stateless, same convention as this repo's other
        // real-not-mocked deps (JwtService, QuoteStore) when the real thing is small and
        // self-contained.
        val service = IdentityService(kycSubmissionRepository, userRepository, DemoNidaVerificationService(), DemoKybVerificationService(), merchantRepository, rateLimiter, notificationRepository, pushNotificationService)

        every { kycSubmissionRepository.findByUserIdOrderBySubmittedAtDesc("user_1") } returns emptyList()
        val savedSlot = slot<KycSubmission>()
        every { kycSubmissionRepository.save(capture(savedSlot)) } answers { firstArg() }
        // Real bug found live (2026-08-02): submit() now locks the caller's own User
        // row (findByIdForUpdate) to close a double-pending-submission race -- see
        // IdentityService.submit's own doc comment for the full account.
        every { userRepository.findByIdForUpdate("user_1") } returns Optional.of(User(id = "user_1", phoneNumber = "+250788000001", firstName = "Test", lastName = "User", passwordHash = "hash"))

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
            Then("it real-locks the user's own row and real-checks the rate limiter -- the two fixes closing the abuse/race gaps found live") {
                verify(exactly = 1) { userRepository.findByIdForUpdate("user_1") }
                verify(exactly = 1) { rateLimiter.checkLimit("identity:submit:user_1", limit = 5, window = Duration.ofHours(1)) }
            }
        }
    }

    Given("a user submitting a structurally invalid National ID") {
        val kycSubmissionRepository = mockk<KycSubmissionRepository>()
        val userRepository = mockk<UserRepository>()
        val merchantRepository = mockk<MerchantRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val notificationRepository = mockk<NotificationRepository>()
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        every { notificationRepository.save(any()) } answers { firstArg() }
        val service = IdentityService(kycSubmissionRepository, userRepository, DemoNidaVerificationService(), DemoKybVerificationService(), merchantRepository, rateLimiter, notificationRepository, pushNotificationService)

        every { kycSubmissionRepository.findByUserIdOrderBySubmittedAtDesc("user_6") } returns emptyList()
        val savedSlot = slot<KycSubmission>()
        every { kycSubmissionRepository.save(capture(savedSlot)) } answers { firstArg() }
        every { userRepository.findByIdForUpdate("user_6") } returns Optional.of(User(id = "user_6", phoneNumber = "+250788000006", firstName = "Test", lastName = "User", passwordHash = "hash"))

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
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val notificationRepository = mockk<NotificationRepository>()
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        every { notificationRepository.save(any()) } answers { firstArg() }
        val service = IdentityService(kycSubmissionRepository, userRepository, DemoNidaVerificationService(), DemoKybVerificationService(), merchantRepository, rateLimiter, notificationRepository, pushNotificationService)

        every { kycSubmissionRepository.findByUserIdOrderBySubmittedAtDesc("user_7") } returns emptyList()
        val savedSlot = slot<KycSubmission>()
        every { kycSubmissionRepository.save(capture(savedSlot)) } answers { firstArg() }
        every { userRepository.findByIdForUpdate("user_7") } returns Optional.of(User(id = "user_7", phoneNumber = "+250788000007", firstName = "Test", lastName = "User", passwordHash = "hash"))

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
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val notificationRepository = mockk<NotificationRepository>()
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        every { notificationRepository.save(any()) } answers { firstArg() }
        val service = IdentityService(kycSubmissionRepository, userRepository, DemoNidaVerificationService(), DemoKybVerificationService(), merchantRepository, rateLimiter, notificationRepository, pushNotificationService)

        every { kycSubmissionRepository.findByUserIdOrderBySubmittedAtDesc("owner_1") } returns emptyList()
        val savedSlot = slot<KycSubmission>()
        every { kycSubmissionRepository.save(capture(savedSlot)) } answers { firstArg() }
        every { userRepository.findByIdForUpdate("owner_1") } returns Optional.of(User(id = "owner_1", phoneNumber = "+250788000010", firstName = "Test", lastName = "Owner", passwordHash = "hash"))

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
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val notificationRepository = mockk<NotificationRepository>()
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        every { notificationRepository.save(any()) } answers { firstArg() }
        // Real, not mocked -- pure and stateless, same convention as this repo's other
        // real-not-mocked deps (JwtService, QuoteStore) when the real thing is small and
        // self-contained.
        val service = IdentityService(kycSubmissionRepository, userRepository, DemoNidaVerificationService(), DemoKybVerificationService(), merchantRepository, rateLimiter, notificationRepository, pushNotificationService)

        every { kycSubmissionRepository.findByUserIdOrderBySubmittedAtDesc("user_2") } returns listOf(
            KycSubmission(id = "kyc_1", userId = "user_2", documentType = "NATIONAL_ID", documentNumber = "x", documentReference = "y", status = "PENDING", submittedAt = Instant.now()),
        )
        every { userRepository.findByIdForUpdate("user_2") } returns Optional.of(User(id = "user_2", phoneNumber = "+250788000002", firstName = "Test", lastName = "User", passwordHash = "hash"))

        When("submitting again") {
            Then("it throws SubmissionAlreadyPendingException rather than creating a second row") {
                try {
                    service.submit("user_2", "NATIONAL_ID", "x", "doc-ref-again")
                    error("expected SubmissionAlreadyPendingException")
                } catch (e: SubmissionAlreadyPendingException) {
                    verify(exactly = 0) { kycSubmissionRepository.save(any()) }
                }
            }
        }
    }

    // Real bug found live (2026-08-02): the plain "any PENDING submission" check used to
    // read-then-CREATE with nothing serializing two concurrent callers -- this simulates
    // the second caller's view of the world AFTER the first caller has already locked
    // and committed a PENDING submission, the exact real-world moment the fix's locked
    // re-check exists to catch.
    Given("two concurrent submissions racing for the same user") {
        val kycSubmissionRepository = mockk<KycSubmissionRepository>()
        val userRepository = mockk<UserRepository>()
        val merchantRepository = mockk<MerchantRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val notificationRepository = mockk<NotificationRepository>()
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        every { notificationRepository.save(any()) } answers { firstArg() }
        val service = IdentityService(kycSubmissionRepository, userRepository, DemoNidaVerificationService(), DemoKybVerificationService(), merchantRepository, rateLimiter, notificationRepository, pushNotificationService)

        val user = User(id = "user_race", phoneNumber = "+250788000099", firstName = "Race", lastName = "User", passwordHash = "hash")
        every { userRepository.findByIdForUpdate("user_race") } returns Optional.of(user)

        When("this caller's lock-acquire happens to observe the other caller's already-committed PENDING row") {
            // The unlocked, top-of-method world this caller started with had no PENDING
            // row -- but by the time it actually acquires the lock, the racing caller
            // already committed one.
            every { kycSubmissionRepository.findByUserIdOrderBySubmittedAtDesc("user_race") } returns listOf(
                KycSubmission(id = "kyc_race_winner", userId = "user_race", documentType = "NATIONAL_ID", documentNumber = "x", documentReference = "y", status = "PENDING", submittedAt = Instant.now()),
            )

            Then("it real-rejects with SubmissionAlreadyPendingException instead of creating a real duplicate PENDING row") {
                try {
                    service.submit("user_race", "NATIONAL_ID", "1198080012345678", "doc-ref-race")
                    error("expected SubmissionAlreadyPendingException")
                } catch (e: SubmissionAlreadyPendingException) {
                    verify(exactly = 1) { userRepository.findByIdForUpdate("user_race") }
                    verify(exactly = 0) { kycSubmissionRepository.save(any()) }
                }
            }
        }
    }

    Given("an ADMIN approving a real PENDING submission") {
        val kycSubmissionRepository = mockk<KycSubmissionRepository>()
        val userRepository = mockk<UserRepository>()
        val merchantRepository = mockk<MerchantRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val notificationRepository = mockk<NotificationRepository>()
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        every { notificationRepository.save(any()) } answers { firstArg() }
        // Real, not mocked -- pure and stateless, same convention as this repo's other
        // real-not-mocked deps (JwtService, QuoteStore) when the real thing is small and
        // self-contained.
        val service = IdentityService(kycSubmissionRepository, userRepository, DemoNidaVerificationService(), DemoKybVerificationService(), merchantRepository, rateLimiter, notificationRepository, pushNotificationService)

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
            Then("it sends a real notification and push naming KYC, not KYB") {
                val notif = slot<Notification>()
                verify(exactly = 1) { notificationRepository.save(capture(notif)) }
                notif.captured.userId shouldBe "user_3"
                notif.captured.type shouldBe "IDENTITY_VERIFICATION_DECIDED"
                notif.captured.body shouldBe "Your identity verification (KYC) was approved."
                verify(exactly = 1) { pushNotificationService.sendToUser("user_3", any(), any(), any()) }
            }
        }
    }

    Given("an ADMIN approving a real PENDING BUSINESS_TIN (KYB) submission") {
        val kycSubmissionRepository = mockk<KycSubmissionRepository>()
        val userRepository = mockk<UserRepository>()
        val merchantRepository = mockk<MerchantRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val notificationRepository = mockk<NotificationRepository>()
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        every { notificationRepository.save(any()) } answers { firstArg() }
        val service = IdentityService(kycSubmissionRepository, userRepository, DemoNidaVerificationService(), DemoKybVerificationService(), merchantRepository, rateLimiter, notificationRepository, pushNotificationService)

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
            Then("it sends a real notification and push naming KYB, not KYC") {
                val notif = slot<Notification>()
                verify(exactly = 1) { notificationRepository.save(capture(notif)) }
                notif.captured.userId shouldBe "owner_2"
                notif.captured.body shouldBe "Your business verification (KYB) was approved."
            }
        }
    }

    Given("an ADMIN approving a BUSINESS_TIN submission for an account with no registered merchant") {
        val kycSubmissionRepository = mockk<KycSubmissionRepository>()
        val userRepository = mockk<UserRepository>()
        val merchantRepository = mockk<MerchantRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val notificationRepository = mockk<NotificationRepository>()
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        every { notificationRepository.save(any()) } answers { firstArg() }
        val service = IdentityService(kycSubmissionRepository, userRepository, DemoNidaVerificationService(), DemoKybVerificationService(), merchantRepository, rateLimiter, notificationRepository, pushNotificationService)

        val submission = KycSubmission(id = "kyc_6", userId = "owner_3", documentType = "BUSINESS_TIN", documentNumber = "123456789", documentReference = "y", status = "PENDING", submittedAt = Instant.now())
        every { kycSubmissionRepository.findById("kyc_6") } returns Optional.of(submission)
        every { kycSubmissionRepository.save(any()) } answers { firstArg() }
        every { merchantRepository.findByOwnerUserId("owner_3") } returns null

        When("approving") {
            Then("it real-fails rather than silently no-op-ing, and never sends a half-decided notification") {
                try {
                    service.decide("kyc_6", "admin_1", approve = true, reason = null)
                    error("expected IdentityUserNotFoundException")
                } catch (e: IdentityUserNotFoundException) {
                    verify(exactly = 0) { notificationRepository.save(any()) }
                }
            }
        }
    }

    Given("an ADMIN rejecting a real PENDING submission") {
        val kycSubmissionRepository = mockk<KycSubmissionRepository>()
        val userRepository = mockk<UserRepository>()
        val merchantRepository = mockk<MerchantRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val notificationRepository = mockk<NotificationRepository>()
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        every { notificationRepository.save(any()) } answers { firstArg() }
        // Real, not mocked -- pure and stateless, same convention as this repo's other
        // real-not-mocked deps (JwtService, QuoteStore) when the real thing is small and
        // self-contained.
        val service = IdentityService(kycSubmissionRepository, userRepository, DemoNidaVerificationService(), DemoKybVerificationService(), merchantRepository, rateLimiter, notificationRepository, pushNotificationService)

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
            Then("it sends a real rejection notification including the real reviewer reason") {
                val notif = slot<Notification>()
                verify(exactly = 1) { notificationRepository.save(capture(notif)) }
                notif.captured.userId shouldBe "user_4"
                notif.captured.body shouldBe "Your identity verification (KYC) was rejected. Reason: Document photo illegible You can resubmit with a new document."
                verify(exactly = 1) { pushNotificationService.sendToUser("user_4", any(), any(), any()) }
            }
        }
    }

    Given("a submission that was already decided") {
        val kycSubmissionRepository = mockk<KycSubmissionRepository>()
        val userRepository = mockk<UserRepository>()
        val merchantRepository = mockk<MerchantRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val notificationRepository = mockk<NotificationRepository>()
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        every { notificationRepository.save(any()) } answers { firstArg() }
        // Real, not mocked -- pure and stateless, same convention as this repo's other
        // real-not-mocked deps (JwtService, QuoteStore) when the real thing is small and
        // self-contained.
        val service = IdentityService(kycSubmissionRepository, userRepository, DemoNidaVerificationService(), DemoKybVerificationService(), merchantRepository, rateLimiter, notificationRepository, pushNotificationService)

        val submission = KycSubmission(id = "kyc_4", userId = "user_5", documentType = "NATIONAL_ID", documentNumber = "x", documentReference = "y", status = "VERIFIED", submittedAt = Instant.now())
        every { kycSubmissionRepository.findById("kyc_4") } returns Optional.of(submission)

        When("trying to decide it again") {
            Then("it throws SubmissionNotPendingException instead of silently re-deciding") {
                try {
                    service.decide("kyc_4", "admin_1", approve = false, reason = "changed my mind")
                    error("expected SubmissionNotPendingException")
                } catch (e: SubmissionNotPendingException) {
                    verify(exactly = 0) { kycSubmissionRepository.save(any()) }
                    verify(exactly = 0) { notificationRepository.save(any()) }
                }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
