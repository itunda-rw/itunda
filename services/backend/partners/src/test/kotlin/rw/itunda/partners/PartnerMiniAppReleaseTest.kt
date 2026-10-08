package rw.itunda.partners

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.Partner
import rw.itunda.core.domain.PartnerMiniApp
import rw.itunda.core.domain.PartnerMiniAppRelease
import rw.itunda.core.domain.PartnerMiniAppReleaseStatus
import rw.itunda.core.domain.PartnerMiniAppStatus
import rw.itunda.core.repository.PartnerMiniAppReleaseRepository
import rw.itunda.core.repository.PartnerMiniAppRepository
import rw.itunda.core.repository.PartnerRepository

class PartnerMiniAppReleaseTest : BehaviorSpec({
    Given("an immutable mini-app release") {
        val partnerRepository = mockk<PartnerRepository>()
        val partnerMiniAppRepository = mockk<PartnerMiniAppRepository>()
        val releaseRepository = mockk<PartnerMiniAppReleaseRepository>()
        val rateLimiter = mockk<RateLimiter>()
        every { rateLimiter.checkLimit(any(), any(), any()) } returns Unit
        val service = PartnerService(partnerRepository, partnerMiniAppRepository, rateLimiter, releaseRepository)
        val release = PartnerMiniAppRelease(
            releaseId = "rel_001",
            miniAppId = "partner_app_1",
            bundleUrl = "https://acme.rw/v2.js",
            manifestSha256 = "a".repeat(64),
            bundleSha256 = "b".repeat(64),
            bundleSizeBytes = 1234,
            status = PartnerMiniAppReleaseStatus.APPROVED,
        )
        val app = PartnerMiniApp(
            id = "partner_app_1",
            partnerId = "partner_1",
            name = "Acme",
            description = "desc",
            bundleUrl = "https://acme.rw/v1.js",
            permissions = "",
            status = PartnerMiniAppStatus.APPROVED,
        )
        every { releaseRepository.findById("rel_001") } returns java.util.Optional.of(release)
        every { releaseRepository.save(any()) } answers { firstArg() }
        every { partnerMiniAppRepository.findById("partner_app_1") } returns java.util.Optional.of(app)
        every { partnerMiniAppRepository.save(any()) } answers { firstArg() }
        every { releaseRepository.findByMiniAppIdAndStatus("partner_app_1", PartnerMiniAppReleaseStatus.ACTIVE) } returns emptyList()

        When("staging then activating") {
            service.stageRelease("rel_001").status shouldBe PartnerMiniAppReleaseStatus.STAGED
            service.activateRelease("rel_001").status shouldBe PartnerMiniAppReleaseStatus.ACTIVE

            Then("the active catalog points at the immutable release artifact") {
                app.releaseId shouldBe "rel_001"
                app.bundleUrl shouldBe "https://acme.rw/v2.js"
                app.bundleSha256 shouldBe "b".repeat(64)
                app.bundleSizeBytes shouldBe 1234
            }
        }

        When("a staged release is rolled back into service") {
            release.status = PartnerMiniAppReleaseStatus.ROLLED_BACK
            release.activatedAt = null
            release.rolledBackAt = java.time.Instant.now()
            release.rollbackReason = "regression"

            val restored = service.rollbackRelease("rel_001", "restore")

            Then("the immutable artifact becomes active without changing its identity") {
                restored.releaseId shouldBe "rel_001"
                restored.status shouldBe PartnerMiniAppReleaseStatus.ACTIVE
                restored.rollbackReason shouldBe null
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
