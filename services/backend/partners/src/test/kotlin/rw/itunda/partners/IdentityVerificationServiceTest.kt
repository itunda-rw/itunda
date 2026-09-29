package rw.itunda.partners

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.IdentityVerificationRequest
import rw.itunda.core.domain.IdentityVerificationStatus
import rw.itunda.core.domain.Partner
import rw.itunda.core.domain.PartnerStatus
import rw.itunda.core.domain.User
import rw.itunda.core.repository.IdentityVerificationRequestRepository
import rw.itunda.core.repository.PartnerMiniAppRepository
import rw.itunda.core.repository.PartnerRepository
import rw.itunda.core.repository.UserRepository
import java.time.Instant
import java.time.LocalDate
import java.util.Optional

class IdentityVerificationServiceTest : BehaviorSpec({

    fun buildService(
        requestRepository: IdentityVerificationRequestRepository,
        partnerRepository: PartnerRepository,
        userRepository: UserRepository,
        signingKeyProvider: IdentitySigningKeyProvider = IdentitySigningKeyProvider(),
    ): IdentityVerificationService {
        val rateLimiter = mockk<RateLimiter>()
        every { rateLimiter.checkLimit(any(), any(), any()) } returns Unit
        val partnerService = PartnerService(partnerRepository, mockk<PartnerMiniAppRepository>(), rateLimiter)
        return IdentityVerificationService(requestRepository, partnerRepository, userRepository, partnerService, signingKeyProvider, rateLimiter)
    }

    val partner = Partner(id = "partner_1", companyName = "Acme Rwanda", contactEmail = "dev@acme.rw", apiKeyHash = sha256Of("sk_test_realkey"), status = PartnerStatus.ACTIVE)
    val user = User(id = "user_1", phoneNumber = "+250788000001", firstName = "Jean", lastName = "Paul", passwordHash = "x", kycVerified = true, birthDate = LocalDate.of(1995, 3, 12))

    Given("a real partner creating an identity-verification request") {
        val requestRepository = mockk<IdentityVerificationRequestRepository>()
        val partnerRepository = mockk<PartnerRepository>()
        val userRepository = mockk<UserRepository>()
        every { partnerRepository.findByApiKeyHash(any()) } returns partner
        val savedSlot = slot<IdentityVerificationRequest>()
        every { requestRepository.save(capture(savedSlot)) } answers { firstArg() }
        val service = buildService(requestRepository, partnerRepository, userRepository)

        When("the request is created") {
            val request = service.createRequest("sk_test_realkey")

            Then("it's a real, short-lived PENDING request tied to that real partner") {
                request.partnerId shouldBe "partner_1"
                request.status shouldBe IdentityVerificationStatus.PENDING
                request.expiresAt.isAfter(Instant.now()) shouldBe true
                request.expiresAt.isBefore(Instant.now().plusSeconds(600)) shouldBe true
                savedSlot.captured.id shouldBe request.id
            }
        }
    }

    Given("a real pending request a user explicitly approves") {
        val requestRepository = mockk<IdentityVerificationRequestRepository>()
        val partnerRepository = mockk<PartnerRepository>()
        val userRepository = mockk<UserRepository>()
        val signingKeyProvider = IdentitySigningKeyProvider()
        val pending = IdentityVerificationRequest(id = "idverify_1", partnerId = "partner_1", expiresAt = Instant.now().plusSeconds(300))
        every { requestRepository.findById("idverify_1") } returns Optional.of(pending)
        every { userRepository.findById("user_1") } returns Optional.of(user)
        val savedSlot = slot<IdentityVerificationRequest>()
        every { requestRepository.save(capture(savedSlot)) } answers { firstArg() }
        val service = buildService(requestRepository, partnerRepository, userRepository, signingKeyProvider)

        When("the user approves it") {
            val approved = service.approve("idverify_1", "user_1")

            Then("it's marked APPROVED with a real, frozen, signed identity payload") {
                approved.status shouldBe IdentityVerificationStatus.APPROVED
                approved.userId shouldBe "user_1"
                approved.disclosedPayloadJson shouldNotBe null
                approved.disclosedPayloadJson!!.contains("\"firstName\":\"Jean\"") shouldBe true
                approved.disclosedPayloadJson!!.contains("\"kycVerified\":true") shouldBe true
                approved.disclosedPayloadJson!!.contains("\"birthDate\":\"1995-03-12\"") shouldBe true
            }
            Then("the signature really verifies against itunda's own signing key, over the exact stored payload") {
                signingKeyProvider.verify(approved.disclosedPayloadJson!!, approved.signature!!) shouldBe true
            }
            Then("a tampered payload real-fails signature verification") {
                signingKeyProvider.verify(approved.disclosedPayloadJson!!.replace("Jean", "Fake"), approved.signature!!) shouldBe false
            }
        }
    }

    Given("a user whose real name contains characters that are unsafe inside a raw JSON string") {
        val requestRepository = mockk<IdentityVerificationRequestRepository>()
        val partnerRepository = mockk<PartnerRepository>()
        val userRepository = mockk<UserRepository>()
        val signingKeyProvider = IdentitySigningKeyProvider()
        val trickyUser = User(
            id = "user_2", phoneNumber = "+250788000002",
            firstName = "Jean\n\"Le Grand\"\\Backslash", lastName = "Paul", passwordHash = "x", kycVerified = false,
        )
        val pending = IdentityVerificationRequest(id = "idverify_2", partnerId = "partner_1", expiresAt = Instant.now().plusSeconds(300))
        every { requestRepository.findById("idverify_2") } returns Optional.of(pending)
        every { userRepository.findById("user_2") } returns Optional.of(trickyUser)
        val savedSlot = slot<IdentityVerificationRequest>()
        every { requestRepository.save(capture(savedSlot)) } answers { firstArg() }
        val service = buildService(requestRepository, partnerRepository, userRepository, signingKeyProvider)

        When("the user approves it") {
            val approved = service.approve("idverify_2", "user_2")

            Then("the disclosed payload is real, parseable JSON a partner can actually read") {
                val parsed = com.fasterxml.jackson.databind.ObjectMapper().readValue(approved.disclosedPayloadJson, Map::class.java)
                parsed["firstName"] shouldBe "Jean\n\"Le Grand\"\\Backslash"
            }
        }
    }

    Given("a real pending request a user explicitly declines") {
        val requestRepository = mockk<IdentityVerificationRequestRepository>()
        val partnerRepository = mockk<PartnerRepository>()
        val userRepository = mockk<UserRepository>()
        val pending = IdentityVerificationRequest(id = "idverify_2", partnerId = "partner_1", expiresAt = Instant.now().plusSeconds(300))
        every { requestRepository.findById("idverify_2") } returns Optional.of(pending)
        val savedSlot = slot<IdentityVerificationRequest>()
        every { requestRepository.save(capture(savedSlot)) } answers { firstArg() }
        val service = buildService(requestRepository, partnerRepository, userRepository)

        When("the user declines it") {
            val declined = service.decline("idverify_2", "user_1")

            Then("it's marked DECLINED with no identity data ever built or stored") {
                declined.status shouldBe IdentityVerificationStatus.DECLINED
                declined.disclosedPayloadJson shouldBe null
                declined.signature shouldBe null
            }
        }
    }

    Given("a real request past its own expiry, never responded to") {
        val requestRepository = mockk<IdentityVerificationRequestRepository>()
        val partnerRepository = mockk<PartnerRepository>()
        val userRepository = mockk<UserRepository>()
        val expired = IdentityVerificationRequest(id = "idverify_3", partnerId = "partner_1", expiresAt = Instant.now().minusSeconds(60))
        every { requestRepository.findById("idverify_3") } returns Optional.of(expired)
        val savedSlot = slot<IdentityVerificationRequest>()
        every { requestRepository.save(capture(savedSlot)) } answers { firstArg() }
        val service = buildService(requestRepository, partnerRepository, userRepository)

        When("a user tries to approve it late") {
            Then("it real-fails as no-longer-pending, lazily marked EXPIRED on this same read") {
                try {
                    service.approve("idverify_3", "user_1")
                    error("expected IdentityVerificationRequestNotPendingException")
                } catch (e: IdentityVerificationRequestNotPendingException) {
                    // expected
                }
                savedSlot.captured.status shouldBe IdentityVerificationStatus.EXPIRED
            }
        }
    }

    Given("a real request belonging to a DIFFERENT partner") {
        val requestRepository = mockk<IdentityVerificationRequestRepository>()
        val partnerRepository = mockk<PartnerRepository>()
        val userRepository = mockk<UserRepository>()
        val otherPartner = Partner(id = "partner_2", companyName = "Other Co", contactEmail = "x@other.rw", apiKeyHash = sha256Of("sk_test_otherkey"), status = PartnerStatus.ACTIVE)
        every { partnerRepository.findByApiKeyHash(any()) } returns otherPartner
        every { requestRepository.findByIdAndPartnerId("idverify_1", "partner_2") } returns null
        val service = buildService(requestRepository, partnerRepository, userRepository)

        When("that partner tries to poll a request that isn't theirs") {
            Then("it real-404s, never leaking whether the request exists under a different partner") {
                try {
                    service.getForPartner("idverify_1", "sk_test_otherkey")
                    error("expected IdentityVerificationRequestNotFoundException")
                } catch (e: IdentityVerificationRequestNotFoundException) {
                    // expected
                }
            }
        }
    }

    // Real repo-wide rate-limiter-verify sweep (2026-09-09) -- every other Given block
    // in this file goes through buildService's shared, permissively-stubbed rateLimiter
    // (`checkLimit(any(), any(), any()) returns Unit`), so nothing anywhere in this file
    // ever exercised createRequest's own real rateLimiter.checkLimit call -- a real
    // regression (the check silently deleted) would have gone undetected. Same
    // throw-and-catch convention this codebase's other rate-limited services already
    // establish; constructs the service directly (not via buildService) so this one
    // Given gets its own strict, throwing rateLimiter mock.
    Given("a real partner who has exceeded the real identity-verification-request rate limit") {
        val requestRepository = mockk<IdentityVerificationRequestRepository>()
        val partnerRepository = mockk<PartnerRepository>()
        val userRepository = mockk<UserRepository>()
        every { partnerRepository.findByApiKeyHash(any()) } returns partner
        val rateLimiter = mockk<RateLimiter>()
        every { rateLimiter.checkLimit("identity:create:partner_1", limit = 30, window = any()) } throws RateLimitExceededException("Too many requests")
        val partnerService = PartnerService(partnerRepository, mockk<PartnerMiniAppRepository>(), rateLimiter)
        val service = IdentityVerificationService(requestRepository, partnerRepository, userRepository, partnerService, IdentitySigningKeyProvider(), rateLimiter)

        When("creating a request") {
            Then("a real RateLimitExceededException fires before ever saving a real request row") {
                try {
                    service.createRequest("sk_test_realkey")
                    error("expected RateLimitExceededException")
                } catch (e: RateLimitExceededException) {
                    verify(exactly = 0) { requestRepository.save(any()) }
                }
            }
        }
    }
})

private fun sha256Of(s: String) = java.security.MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
