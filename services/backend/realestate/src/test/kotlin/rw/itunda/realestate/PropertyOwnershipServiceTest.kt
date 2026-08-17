package rw.itunda.realestate

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.core.domain.PropertyListing
import rw.itunda.core.domain.PropertyListingType
import rw.itunda.core.domain.PropertyOwnershipSubmission
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.PropertyListingRepository
import rw.itunda.core.repository.PropertyOwnershipSubmissionRepository
import java.math.BigDecimal
import java.time.Instant
import java.util.Optional

/**
 * First test coverage for PropertyOwnershipService.decide -- previously untested. Also
 * covers the 2026-08-17 fix: decide now notifies the real submitter of the outcome
 * (approve/reject), the same real gap Sections 146/147 closed for InsuranceService/
 * MarketplaceService.
 */
class PropertyOwnershipServiceTest : BehaviorSpec({

    fun listing() = PropertyListing(
        id = "prop_1", listerId = "user_1", listingType = PropertyListingType.SALE,
        propertyType = "house", title = "Nice house in Kigali", description = "desc",
        price = BigDecimal("50000000"),
    )

    fun submission(status: String = "PENDING") = PropertyOwnershipSubmission(
        id = "property_ownership_1", listingId = "prop_1", userId = "user_1",
        documentUrl = "/api/v1/uploads/doc.pdf", status = status, submittedAt = Instant.now(),
    )

    Given("an ADMIN approving a real pending ownership submission") {
        val submissionRepository = mockk<PropertyOwnershipSubmissionRepository>()
        val listingRepository = mockk<PropertyListingRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = PropertyOwnershipService(submissionRepository, listingRepository, notificationRepository, pushNotificationService)
        every { notificationRepository.save(any()) } answers { firstArg() }

        every { submissionRepository.findById("property_ownership_1") } returns Optional.of(submission())
        every { submissionRepository.save(any()) } answers { firstArg() }
        every { listingRepository.findById("prop_1") } returns Optional.of(listing())
        every { listingRepository.save(any()) } answers { firstArg() }

        When("approving it") {
            val decided = service.decide("property_ownership_1", "admin_1", approve = true, reason = null)

            Then("the submission is marked VERIFIED with a real reviewer") {
                decided.status shouldBe "VERIFIED"
                decided.reviewedBy shouldBe "admin_1"
            }
            Then("the listing's ownershipVerificationStatus flips to VERIFIED") {
                verify(exactly = 1) { listingRepository.save(match { it.ownershipVerificationStatus == "VERIFIED" }) }
            }
            Then("it real-notifies the submitter that their document was verified") {
                verify(exactly = 1) {
                    notificationRepository.save(
                        match { it.userId == "user_1" && it.type == "PROPERTY_OWNERSHIP_DECIDED" && it.body.contains("verified") },
                    )
                }
            }
            Then("the submitter also gets a real mobile push notification, not just the in-app one") {
                verify(exactly = 1) { pushNotificationService.sendToUser("user_1", "Ownership verified", any(), mapOf("submissionId" to "property_ownership_1")) }
            }
        }
    }

    Given("an ADMIN rejecting a real pending ownership submission") {
        val submissionRepository = mockk<PropertyOwnershipSubmissionRepository>()
        val listingRepository = mockk<PropertyListingRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = PropertyOwnershipService(submissionRepository, listingRepository, notificationRepository, pushNotificationService)
        every { notificationRepository.save(any()) } answers { firstArg() }

        every { submissionRepository.findById("property_ownership_1") } returns Optional.of(submission())
        every { submissionRepository.save(any()) } answers { firstArg() }
        every { listingRepository.findById("prop_1") } returns Optional.of(listing())
        every { listingRepository.save(any()) } answers { firstArg() }

        When("rejecting it with a real reason") {
            val decided = service.decide("property_ownership_1", "admin_1", approve = false, reason = "Document is illegible")

            Then("the submission is marked REJECTED") {
                decided.status shouldBe "REJECTED"
                decided.decisionReason shouldBe "Document is illegible"
            }
            Then("the listing falls back to NONE, not stuck on PENDING") {
                verify(exactly = 1) { listingRepository.save(match { it.ownershipVerificationStatus == "NONE" }) }
            }
            Then("it real-notifies the submitter with the real rejection reason") {
                verify(exactly = 1) {
                    notificationRepository.save(
                        match { it.userId == "user_1" && it.type == "PROPERTY_OWNERSHIP_DECIDED" && it.body.contains("Document is illegible") },
                    )
                }
            }
            Then("the submitter also gets a real mobile push notification") {
                verify(exactly = 1) { pushNotificationService.sendToUser("user_1", "Ownership verification rejected", any(), mapOf("submissionId" to "property_ownership_1")) }
            }
        }
    }

    Given("an ADMIN trying to decide an already-decided submission") {
        val submissionRepository = mockk<PropertyOwnershipSubmissionRepository>()
        val listingRepository = mockk<PropertyListingRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = PropertyOwnershipService(submissionRepository, listingRepository, notificationRepository, pushNotificationService)

        every { submissionRepository.findById("property_ownership_1") } returns Optional.of(submission(status = "VERIFIED"))

        When("approving it again") {
            Then("it throws PropertyOwnershipSubmissionNotPendingException and never re-notifies") {
                try {
                    service.decide("property_ownership_1", "admin_1", approve = true, reason = null)
                    error("expected PropertyOwnershipSubmissionNotPendingException")
                } catch (e: PropertyOwnershipSubmissionNotPendingException) {
                    verify(exactly = 0) { notificationRepository.save(any()) }
                }
            }
        }
    }
})
