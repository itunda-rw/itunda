package rw.itunda.core.review

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import rw.itunda.core.domain.HoodTransactionReview
import rw.itunda.core.domain.HoodTransactionType
import rw.itunda.core.domain.JobPost
import rw.itunda.core.domain.JobPostStatus
import rw.itunda.core.domain.JobPayType
import rw.itunda.core.domain.Listing
import rw.itunda.core.domain.ListingStatus
import rw.itunda.core.domain.PropertyListing
import rw.itunda.core.domain.PropertyListingStatus
import rw.itunda.core.domain.PropertyListingType
import rw.itunda.core.repository.HoodTransactionReviewRepository
import rw.itunda.core.repository.JobPostRepository
import rw.itunda.core.repository.ListingRepository
import rw.itunda.core.repository.PropertyListingRepository
import rw.itunda.core.trust.TrustScoreService
import java.math.BigDecimal
import java.util.Optional

/**
 * First test coverage for HoodReviewService -- shared by Marketplace/Jobs/Property's
 * own review flows (see this class's own doc comment). `resolveParties`/`submitReview`
 * are the actual access-control logic that makes this whole feature non-exploitable:
 * only the two real recorded parties to a COMPLETED transaction can review or view a
 * review, and only real allowlisted checklist points are ever persisted -- worth
 * testing directly rather than trusting it only through 3 separate callers' own tests.
 */
class HoodReviewServiceTest : BehaviorSpec({

    fun soldListing(buyerId: String? = "buyer_1") = Listing(
        id = "listing_1", sellerId = "seller_1", title = "Bike", description = "desc",
        price = BigDecimal("10000"), category = "sports", status = ListingStatus.SOLD, buyerId = buyerId,
    )

    fun filledJobPost(workerId: String? = "worker_1") = JobPost(
        id = "job_1", posterId = "poster_1", category = "delivery", title = "Rider", description = "desc",
        payType = JobPayType.HOURLY, payAmount = BigDecimal("1500"), status = JobPostStatus.FILLED, workerId = workerId,
    )

    fun takenPropertyListing(counterpartyId: String? = "tenant_1") = PropertyListing(
        id = "prop_1", listerId = "lister_1", listingType = PropertyListingType.RENT,
        propertyType = "apartment", title = "Nice place", description = "desc", price = BigDecimal("200000"),
        status = PropertyListingStatus.TAKEN, counterpartyId = counterpartyId,
    )

    fun service(
        reviewRepository: HoodTransactionReviewRepository = mockk(),
        listingRepository: ListingRepository = mockk(),
        jobPostRepository: JobPostRepository = mockk(),
        propertyListingRepository: PropertyListingRepository = mockk(),
        trustScoreService: TrustScoreService = mockk(relaxed = true),
    ) = HoodReviewService(reviewRepository, listingRepository, jobPostRepository, propertyListingRepository, trustScoreService)

    Given("a listing that hasn't been marked sold yet") {
        val listingRepository = mockk<ListingRepository>()
        every { listingRepository.findById("listing_1") } returns Optional.of(soldListing().apply { status = ListingStatus.ACTIVE })
        val hoodReviewService = service(listingRepository = listingRepository)

        When("someone tries to submit a review for it") {
            Then("it's rejected -- there's nothing completed to review yet") {
                shouldThrow<HoodReviewTransactionNotCompletedException> {
                    hoodReviewService.submitReview("seller_1", HoodTransactionType.LISTING, "listing_1", listOf("RESPONSIVE"), emptyList())
                }
            }
        }
    }

    Given("a sold listing with no buyer ever recorded") {
        val listingRepository = mockk<ListingRepository>()
        every { listingRepository.findById("listing_1") } returns Optional.of(soldListing(buyerId = null))
        val hoodReviewService = service(listingRepository = listingRepository)

        When("the seller tries to review it") {
            Then("it's rejected -- there is structurally no one to review") {
                shouldThrow<HoodReviewNoCounterpartyException> {
                    hoodReviewService.submitReview("seller_1", HoodTransactionType.LISTING, "listing_1", listOf("RESPONSIVE"), emptyList())
                }
            }
        }
    }

    Given("a completed listing sale and someone who was NOT a party to it") {
        val listingRepository = mockk<ListingRepository>()
        every { listingRepository.findById("listing_1") } returns Optional.of(soldListing())
        val hoodReviewService = service(listingRepository = listingRepository)

        When("that unrelated user tries to submit a review") {
            Then("it's rejected -- the real access-control guarantee of this whole feature") {
                shouldThrow<HoodReviewNotPartyException> {
                    hoodReviewService.submitReview("some_stranger", HoodTransactionType.LISTING, "listing_1", listOf("RESPONSIVE"), emptyList())
                }
            }
        }
    }

    Given("a completed listing sale where the buyer already reviewed it once") {
        val listingRepository = mockk<ListingRepository>()
        every { listingRepository.findById("listing_1") } returns Optional.of(soldListing())
        val reviewRepository = mockk<HoodTransactionReviewRepository>()
        every { reviewRepository.existsByTransactionTypeAndTransactionIdAndReviewerId(HoodTransactionType.LISTING, "listing_1", "buyer_1") } returns true
        val hoodReviewService = service(listingRepository = listingRepository, reviewRepository = reviewRepository)

        When("the buyer tries to review it again") {
            Then("it's rejected") {
                shouldThrow<HoodReviewAlreadySubmittedException> {
                    hoodReviewService.submitReview("buyer_1", HoodTransactionType.LISTING, "listing_1", listOf("RESPONSIVE"), emptyList())
                }
            }
        }
    }

    Given("a completed job post, reviewed by the poster (party A) about the worker (party B)") {
        val jobPostRepository = mockk<JobPostRepository>()
        every { jobPostRepository.findById("job_1") } returns Optional.of(filledJobPost())
        val reviewRepository = mockk<HoodTransactionReviewRepository>()
        every { reviewRepository.existsByTransactionTypeAndTransactionIdAndReviewerId(HoodTransactionType.JOB_POST, "job_1", "poster_1") } returns false
        val savedSlot = slot<HoodTransactionReview>()
        every { reviewRepository.save(capture(savedSlot)) } answers { firstArg() }
        val trustScoreService = mockk<TrustScoreService>(relaxed = true)
        val hoodReviewService = service(jobPostRepository = jobPostRepository, reviewRepository = reviewRepository, trustScoreService = trustScoreService)

        When("the poster submits a review with only real, allowlisted checklist points plus a bogus one mixed in") {
            hoodReviewService.submitReview(
                "poster_1", HoodTransactionType.JOB_POST, "job_1",
                goodPoints = listOf("RESPONSIVE", "NOT_A_REAL_POINT", "RESPONSIVE"),
                uncomfortablePoints = listOf("LATE", "<script>alert(1)</script>"),
            )

            Then("the review is saved about the WORKER (the counterparty), never the poster themselves") {
                savedSlot.captured.revieweeId shouldBe "worker_1"
                savedSlot.captured.reviewerId shouldBe "poster_1"
            }
            Then("only the real allowlisted points survive, deduplicated, and anything else is dropped silently") {
                savedSlot.captured.goodPoints shouldBe "RESPONSIVE"
                savedSlot.captured.uncomfortablePoints shouldBe "LATE"
            }
            Then("a real good point triggers a trust-score recompute for the reviewee") {
                verify(exactly = 1) { trustScoreService.computeScore("worker_1") }
            }
        }
    }

    Given("a completed job post, reviewed by the worker (party B) about the poster") {
        val jobPostRepository = mockk<JobPostRepository>()
        every { jobPostRepository.findById("job_1") } returns Optional.of(filledJobPost())
        val reviewRepository = mockk<HoodTransactionReviewRepository>()
        every { reviewRepository.existsByTransactionTypeAndTransactionIdAndReviewerId(HoodTransactionType.JOB_POST, "job_1", "worker_1") } returns false
        val savedSlot = slot<HoodTransactionReview>()
        every { reviewRepository.save(capture(savedSlot)) } answers { firstArg() }

        When("the worker submits a review with only uncomfortable points, no good points") {
            val trustScoreService = mockk<TrustScoreService>(relaxed = true)
            val serviceWithSpy = service(jobPostRepository = jobPostRepository, reviewRepository = reviewRepository, trustScoreService = trustScoreService)
            serviceWithSpy.submitReview("worker_1", HoodTransactionType.JOB_POST, "job_1", goodPoints = emptyList(), uncomfortablePoints = listOf("LATE"))

            Then("the review is saved about the POSTER (the other party)") {
                savedSlot.captured.revieweeId shouldBe "poster_1"
            }
            Then("no trust-score recompute happens -- only a real good point moves the needle") {
                verify(exactly = 0) { trustScoreService.computeScore(any()) }
            }
        }
    }

    Given("a taken property listing and a real party to it") {
        val propertyListingRepository = mockk<PropertyListingRepository>()
        every { propertyListingRepository.findById("prop_1") } returns Optional.of(takenPropertyListing())

        When("that party requests the reviews for it") {
            val reviewRepository = mockk<HoodTransactionReviewRepository>()
            every { reviewRepository.findByTransactionTypeAndTransactionId(HoodTransactionType.PROPERTY_LISTING, "prop_1") } returns emptyList()
            val serviceForRead = service(propertyListingRepository = propertyListingRepository, reviewRepository = reviewRepository)

            Then("it succeeds, no exception") {
                serviceForRead.getTransactionReviews("lister_1", HoodTransactionType.PROPERTY_LISTING, "prop_1")
            }
        }
    }

    Given("a taken property listing and someone who was never a party to it") {
        val propertyListingRepository = mockk<PropertyListingRepository>()
        every { propertyListingRepository.findById("prop_1") } returns Optional.of(takenPropertyListing())
        val hoodReviewService = service(propertyListingRepository = propertyListingRepository)

        When("that stranger requests the reviews for it") {
            Then("it's rejected -- reviews (including private uncomfortablePoints) are only visible to the 2 real parties") {
                shouldThrow<HoodReviewNotPartyException> {
                    hoodReviewService.getTransactionReviews("some_stranger", HoodTransactionType.PROPERTY_LISTING, "prop_1")
                }
            }
        }
    }

    Given("a user with a mix of good and uncomfortable reviews on file") {
        val reviewRepository = mockk<HoodTransactionReviewRepository>()
        every { reviewRepository.findByRevieweeId("user_1") } returns listOf(
            HoodTransactionReview(
                id = "r1", transactionType = HoodTransactionType.LISTING, transactionId = "l1",
                reviewerId = "buyer_1", revieweeId = "user_1", goodPoints = "RESPONSIVE|ON_TIME", uncomfortablePoints = "",
            ),
            HoodTransactionReview(
                id = "r2", transactionType = HoodTransactionType.LISTING, transactionId = "l2",
                reviewerId = "buyer_2", revieweeId = "user_1", goodPoints = "RESPONSIVE", uncomfortablePoints = "RUDE",
            ),
        )
        val hoodReviewService = service(reviewRepository = reviewRepository)

        When("their public good-point summary is fetched") {
            val counts = hoodReviewService.publicGoodPointCounts("user_1")

            Then("only good points are counted, aggregated correctly, and no uncomfortable point ever appears") {
                counts shouldBe mapOf("RESPONSIVE" to 2, "ON_TIME" to 1)
                counts.containsKey("RUDE") shouldBe false
            }
        }
    }
})
