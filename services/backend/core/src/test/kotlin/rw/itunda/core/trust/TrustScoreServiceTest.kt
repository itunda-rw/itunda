package rw.itunda.core.trust

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.core.domain.JobPostStatus
import rw.itunda.core.domain.ListingStatus
import rw.itunda.core.domain.PropertyListingStatus
import rw.itunda.core.domain.User
import rw.itunda.core.repository.HoodTransactionReviewRepository
import rw.itunda.core.repository.JobPostRepository
import rw.itunda.core.repository.ListingRepository
import rw.itunda.core.repository.PropertyListingRepository
import rw.itunda.core.repository.UserRepository
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Optional

class TrustScoreServiceTest : BehaviorSpec({

    Given("a brand new user with no completed Hood transactions") {
        val userRepository = mockk<UserRepository>()
        val listingRepository = mockk<ListingRepository>()
        val jobPostRepository = mockk<JobPostRepository>()
        val propertyListingRepository = mockk<PropertyListingRepository>()
        val hoodTransactionReviewRepository = mockk<HoodTransactionReviewRepository>()
        val service = TrustScoreService(userRepository, listingRepository, jobPostRepository, propertyListingRepository, hoodTransactionReviewRepository)

        val user = User(id = "user_1", phoneNumber = "+250780250001", firstName = "New", lastName = "User", passwordHash = "hash", kycVerified = false, createdAt = Instant.now())
        every { userRepository.findById("user_1") } returns Optional.of(user)
        every { listingRepository.countBySellerIdAndStatus("user_1", ListingStatus.SOLD) } returns 0
        every { jobPostRepository.countByPosterIdAndStatus("user_1", JobPostStatus.FILLED) } returns 0
        every { propertyListingRepository.countByListerIdAndStatus("user_1", PropertyListingStatus.TAKEN) } returns 0
        every { hoodTransactionReviewRepository.countByRevieweeIdAndGoodPointsNot("user_1", "") } returns 0
        every { userRepository.save(any()) } answers { firstArg() }

        When("computing the score") {
            val result = service.computeScore("user_1")

            Then("it's exactly the real Karrot-Score-style base of 30 -- no unearned points") {
                result.score shouldBe 30
                result.factors.size shouldBe 1
                result.factors[0].name shouldBe "Base score"
            }
            Then("it writes the computed score back onto the user for card-badge display") {
                user.trustScore shouldBe 30
                verify(exactly = 1) { userRepository.save(user) }
            }
        }
    }

    Given("a well-established, KYC-verified user with real completed transactions across all three Hood surfaces and real good reviews") {
        val userRepository = mockk<UserRepository>()
        val listingRepository = mockk<ListingRepository>()
        val jobPostRepository = mockk<JobPostRepository>()
        val propertyListingRepository = mockk<PropertyListingRepository>()
        val hoodTransactionReviewRepository = mockk<HoodTransactionReviewRepository>()
        val service = TrustScoreService(userRepository, listingRepository, jobPostRepository, propertyListingRepository, hoodTransactionReviewRepository)

        val oldCreatedAt = Instant.now().minus(1000, ChronoUnit.DAYS)
        val user = User(id = "user_2", phoneNumber = "+250780250002", firstName = "Established", lastName = "User", passwordHash = "hash", kycVerified = true, createdAt = oldCreatedAt)
        every { userRepository.findById("user_2") } returns Optional.of(user)
        every { listingRepository.countBySellerIdAndStatus("user_2", ListingStatus.SOLD) } returns 3
        every { jobPostRepository.countByPosterIdAndStatus("user_2", JobPostStatus.FILLED) } returns 2
        every { propertyListingRepository.countByListerIdAndStatus("user_2", PropertyListingStatus.TAKEN) } returns 1
        every { hoodTransactionReviewRepository.countByRevieweeIdAndGoodPointsNot("user_2", "") } returns 4
        every { userRepository.save(any()) } answers { firstArg() }

        When("computing the score") {
            val result = service.computeScore("user_2")

            Then("every real signal contributes: 30 base + 50 KYC + 100 age (capped) + 90 transactions (6 * 15) + 40 reviews (4 * 10) = 310") {
                result.score shouldBe 310
                result.factors.map { it.name } shouldBe listOf(
                    "Base score", "Identity verified", "Account history", "Completed Hood transactions", "Positive neighbor reviews",
                )
            }
        }
    }

    Given("a user with enough completed transactions to threaten the real 1000-point ceiling") {
        val userRepository = mockk<UserRepository>()
        val listingRepository = mockk<ListingRepository>()
        val jobPostRepository = mockk<JobPostRepository>()
        val propertyListingRepository = mockk<PropertyListingRepository>()
        val hoodTransactionReviewRepository = mockk<HoodTransactionReviewRepository>()
        val service = TrustScoreService(userRepository, listingRepository, jobPostRepository, propertyListingRepository, hoodTransactionReviewRepository)

        val user = User(id = "user_3", phoneNumber = "+250780250003", firstName = "Power", lastName = "Seller", passwordHash = "hash", kycVerified = true, createdAt = Instant.now())
        every { userRepository.findById("user_3") } returns Optional.of(user)
        every { listingRepository.countBySellerIdAndStatus("user_3", ListingStatus.SOLD) } returns 100
        every { jobPostRepository.countByPosterIdAndStatus("user_3", JobPostStatus.FILLED) } returns 0
        every { propertyListingRepository.countByListerIdAndStatus("user_3", PropertyListingStatus.TAKEN) } returns 0
        every { hoodTransactionReviewRepository.countByRevieweeIdAndGoodPointsNot("user_3", "") } returns 0
        every { userRepository.save(any()) } answers { firstArg() }

        When("computing the score") {
            val result = service.computeScore("user_3")

            Then("it's capped at the real 1000-point ceiling, never fabricated beyond it") {
                result.score shouldBe 1000
                user.trustScore shouldBe 1000
            }
        }
    }

    Given("a user id that doesn't exist") {
        val userRepository = mockk<UserRepository>()
        val listingRepository = mockk<ListingRepository>()
        val jobPostRepository = mockk<JobPostRepository>()
        val propertyListingRepository = mockk<PropertyListingRepository>()
        val hoodTransactionReviewRepository = mockk<HoodTransactionReviewRepository>()
        val service = TrustScoreService(userRepository, listingRepository, jobPostRepository, propertyListingRepository, hoodTransactionReviewRepository)

        every { userRepository.findById("ghost") } returns Optional.empty()

        When("computing the score") {
            Then("it throws TrustScoreUserNotFoundException rather than a null-pointer surprise") {
                try {
                    service.computeScore("ghost")
                    error("expected TrustScoreUserNotFoundException")
                } catch (e: TrustScoreUserNotFoundException) {
                    // expected
                }
            }
        }
    }

    Given("a brand new user (no transactions, no KYC, no age) who has real good reviews only") {
        val userRepository = mockk<UserRepository>()
        val listingRepository = mockk<ListingRepository>()
        val jobPostRepository = mockk<JobPostRepository>()
        val propertyListingRepository = mockk<PropertyListingRepository>()
        val hoodTransactionReviewRepository = mockk<HoodTransactionReviewRepository>()
        val service = TrustScoreService(userRepository, listingRepository, jobPostRepository, propertyListingRepository, hoodTransactionReviewRepository)

        val user = User(id = "user_4", phoneNumber = "+250780250004", firstName = "Reviewed", lastName = "User", passwordHash = "hash", kycVerified = false, createdAt = Instant.now())
        every { userRepository.findById("user_4") } returns Optional.of(user)
        every { listingRepository.countBySellerIdAndStatus("user_4", ListingStatus.SOLD) } returns 0
        every { jobPostRepository.countByPosterIdAndStatus("user_4", JobPostStatus.FILLED) } returns 0
        every { propertyListingRepository.countByListerIdAndStatus("user_4", PropertyListingStatus.TAKEN) } returns 0
        every { hoodTransactionReviewRepository.countByRevieweeIdAndGoodPointsNot("user_4", "") } returns 2
        every { userRepository.save(any()) } answers { firstArg() }

        When("computing the score") {
            val result = service.computeScore("user_4")

            Then("the real fixed bug: 2 good reviews alone genuinely move the score -- 30 base + 20 reviews (2 * 10) = 50, isolated from every other factor") {
                result.score shouldBe 50
                result.factors.map { it.name } shouldBe listOf("Base score", "Positive neighbor reviews")
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
