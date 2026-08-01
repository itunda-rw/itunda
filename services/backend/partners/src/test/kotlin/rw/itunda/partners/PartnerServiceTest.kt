package rw.itunda.partners

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.Partner
import rw.itunda.core.domain.PartnerMiniApp
import rw.itunda.core.domain.PartnerMiniAppStatus
import rw.itunda.core.domain.PartnerStatus
import rw.itunda.core.repository.PartnerMiniAppRepository
import rw.itunda.core.repository.PartnerRepository
import java.security.MessageDigest
import java.util.Optional

class PartnerServiceTest : BehaviorSpec({

    fun sha256(s: String) = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    Given("a new company registering as a partner") {
        val partnerRepository = mockk<PartnerRepository>()
        val partnerMiniAppRepository = mockk<PartnerMiniAppRepository>()
        val rateLimiter = mockk<RateLimiter>()
        every { rateLimiter.checkLimit(any(), any(), any()) } returns Unit
        val service = PartnerService(partnerRepository, partnerMiniAppRepository, rateLimiter)

        every { partnerRepository.findByContactEmail("dev@acme.rw") } returns null
        val savedSlot = slot<Partner>()
        every { partnerRepository.save(capture(savedSlot)) } answers { firstArg() }

        When("registering") {
            val (partner, rawKey) = service.register("Acme Rwanda", "dev@acme.rw")

            Then("a real partner row is created with a real, unique, unpredictable API key") {
                partner.companyName shouldBe "Acme Rwanda"
                partner.contactEmail shouldBe "dev@acme.rw"
                partner.status shouldBe PartnerStatus.ACTIVE
                rawKey.startsWith("sk_test_") shouldBe true
                rawKey.length shouldBe "sk_test_".length + 48
            }
            Then("only a hash of the key is persisted, never the raw value") {
                savedSlot.captured.apiKeyHash shouldBe sha256(rawKey)
                savedSlot.captured.apiKeyHash shouldNotBe rawKey
            }
        }

        When("registering a second time with the same email") {
            every { partnerRepository.findByContactEmail("dev@acme.rw") } returns
                Partner(id = "partner_1", companyName = "Acme Rwanda", contactEmail = "dev@acme.rw", apiKeyHash = "x")

            Then("it real-conflicts rather than creating a second account") {
                try {
                    service.register("Acme Rwanda", "dev@acme.rw")
                    error("expected PartnerEmailAlreadyRegisteredException")
                } catch (e: PartnerEmailAlreadyRegisteredException) {
                    // expected
                }
            }
        }
    }

    Given("a registered partner submitting a mini-app for review") {
        val partnerRepository = mockk<PartnerRepository>()
        val partnerMiniAppRepository = mockk<PartnerMiniAppRepository>()
        val rateLimiter = mockk<RateLimiter>()
        every { rateLimiter.checkLimit(any(), any(), any()) } returns Unit
        val service = PartnerService(partnerRepository, partnerMiniAppRepository, rateLimiter)

        val partner = Partner(id = "partner_1", companyName = "Acme Rwanda", contactEmail = "dev@acme.rw", apiKeyHash = sha256("sk_test_real_key"), status = PartnerStatus.ACTIVE)
        every { partnerRepository.findByApiKeyHash(sha256("sk_test_real_key")) } returns partner
        every { partnerMiniAppRepository.save(any()) } answers { firstArg() }

        When("submitting with only real, allowed permission scopes") {
            val miniApp = service.submitMiniApp(
                "sk_test_real_key", "Acme Delivery", "Order food in Kigali", null, "https://acme.rw/bundle.js",
                listOf("wallet:read", "profile:read"),
            )

            Then("a real PENDING submission is created, not auto-approved") {
                miniApp.status shouldBe PartnerMiniAppStatus.PENDING
                miniApp.partnerId shouldBe "partner_1"
                miniApp.permissions shouldBe "wallet:read,profile:read"
            }
        }

        When("submitting with a permission scope that doesn't exist") {
            Then("it real-fails before ever creating a submission") {
                try {
                    service.submitMiniApp(
                        "sk_test_real_key", "Acme Delivery", "desc", null, "https://acme.rw/bundle.js",
                        listOf("wallet:write", "admin:everything"),
                    )
                    error("expected InvalidPermissionScopeException")
                } catch (e: InvalidPermissionScopeException) {
                    verify(exactly = 0) { partnerMiniAppRepository.save(any()) }
                }
            }
        }

        When("submitting with a name longer than the real 255-char DB column bound") {
            Then("it throws InvalidMiniAppSubmissionException rather than risking a raw DB insert failure") {
                try {
                    service.submitMiniApp(
                        "sk_test_real_key", "x".repeat(256), "desc", null, "https://acme.rw/bundle.js", emptyList(),
                    )
                    error("expected InvalidMiniAppSubmissionException")
                } catch (e: InvalidMiniAppSubmissionException) {
                    verify(exactly = 0) { partnerMiniAppRepository.save(any()) }
                }
            }
        }

        When("submitting with an unknown API key") {
            every { partnerRepository.findByApiKeyHash(sha256("sk_test_wrong")) } returns null

            Then("it real-401s rather than silently attributing the submission to nobody") {
                try {
                    service.submitMiniApp("sk_test_wrong", "x", "y", null, "z", emptyList())
                    error("expected InvalidApiKeyException")
                } catch (e: InvalidApiKeyException) {
                    // expected
                }
            }
        }

        When("the partner account is suspended") {
            val suspended = Partner(id = "partner_2", companyName = "Bad Actor Ltd", contactEmail = "x@bad.rw", apiKeyHash = sha256("sk_test_suspended"), status = PartnerStatus.SUSPENDED)
            every { partnerRepository.findByApiKeyHash(sha256("sk_test_suspended")) } returns suspended

            Then("it real-403s even with a technically valid key") {
                try {
                    service.submitMiniApp("sk_test_suspended", "x", "y", null, "z", emptyList())
                    error("expected PartnerSuspendedException")
                } catch (e: PartnerSuspendedException) {
                    // expected
                }
            }
        }
    }

    Given("an ADMIN reviewing a real PENDING mini-app submission") {
        val partnerRepository = mockk<PartnerRepository>()
        val partnerMiniAppRepository = mockk<PartnerMiniAppRepository>()
        val rateLimiter = mockk<RateLimiter>()
        every { rateLimiter.checkLimit(any(), any(), any()) } returns Unit
        val service = PartnerService(partnerRepository, partnerMiniAppRepository, rateLimiter)

        val miniApp = PartnerMiniApp(
            id = "partner_app_1", partnerId = "partner_1", name = "Acme Delivery", description = "desc",
            bundleUrl = "https://acme.rw/bundle.js", permissions = "wallet:read", status = PartnerMiniAppStatus.PENDING,
        )
        every { partnerMiniAppRepository.findById("partner_app_1") } returns Optional.of(miniApp)
        val savedSlot = slot<PartnerMiniApp>()
        every { partnerMiniAppRepository.save(capture(savedSlot)) } answers { firstArg() }

        When("approving") {
            val decided = service.decide("partner_app_1", "admin_1", approve = true, reason = null)

            Then("it's marked APPROVED and now appears in the real published catalog") {
                decided.status shouldBe PartnerMiniAppStatus.APPROVED
                decided.reviewedBy shouldBe "admin_1"
            }

            // Real bug found live (2026-08-02): decide() already read this exact
            // submission, checked its status, then wrote back to it -- the correct
            // check-then-act shape -- but with no @Version, two admins concurrently
            // reviewing the same submission could race to a conflicting final decision.
            // Asserts the mechanism the fix now relies on: the same versioned entity
            // read is the one saved.
            Then("the same versioned mini-app instance that was read is the one saved") {
                savedSlot.captured shouldBe miniApp
                savedSlot.captured.version shouldBe miniApp.version
            }
        }

        When("rejecting with a reason") {
            val pendingAgain = PartnerMiniApp(
                id = "partner_app_2", partnerId = "partner_1", name = "Sketchy App", description = "desc",
                bundleUrl = "https://sketchy.example.com/bundle.js", permissions = "", status = PartnerMiniAppStatus.PENDING,
            )
            every { partnerMiniAppRepository.findById("partner_app_2") } returns Optional.of(pendingAgain)

            val decided = service.decide("partner_app_2", "admin_1", approve = false, reason = "Bundle URL not HTTPS-verified")

            Then("it's marked REJECTED and records the real reason") {
                decided.status shouldBe PartnerMiniAppStatus.REJECTED
                decided.decisionReason shouldBe "Bundle URL not HTTPS-verified"
            }
        }

        When("trying to decide an already-decided submission") {
            val alreadyDecided = PartnerMiniApp(
                id = "partner_app_3", partnerId = "partner_1", name = "x", description = "y",
                bundleUrl = "z", permissions = "", status = PartnerMiniAppStatus.APPROVED,
            )
            every { partnerMiniAppRepository.findById("partner_app_3") } returns Optional.of(alreadyDecided)

            Then("it throws rather than silently re-deciding") {
                try {
                    service.decide("partner_app_3", "admin_1", approve = false, reason = null)
                    error("expected PartnerMiniAppNotPendingException")
                } catch (e: PartnerMiniAppNotPendingException) {
                    // expected
                }
            }
        }
    }

    Given("the real published mini-app catalog") {
        val partnerRepository = mockk<PartnerRepository>()
        val partnerMiniAppRepository = mockk<PartnerMiniAppRepository>()
        val rateLimiter = mockk<RateLimiter>()
        every { rateLimiter.checkLimit(any(), any(), any()) } returns Unit
        val service = PartnerService(partnerRepository, partnerMiniAppRepository, rateLimiter)

        val approved = PartnerMiniApp(id = "partner_app_1", partnerId = "partner_1", name = "Acme Delivery", description = "desc", bundleUrl = "z", permissions = "", status = PartnerMiniAppStatus.APPROVED)
        val pageable = PageRequest.of(0, 20)
        every { partnerMiniAppRepository.findByStatus(PartnerMiniAppStatus.APPROVED, pageable) } returns PageImpl(listOf(approved))

        When("fetched") {
            val catalog = service.getCatalog(pageable)

            Then("it only ever contains real APPROVED entries -- never PENDING/REJECTED ones") {
                catalog.content shouldBe listOf(approved)
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
