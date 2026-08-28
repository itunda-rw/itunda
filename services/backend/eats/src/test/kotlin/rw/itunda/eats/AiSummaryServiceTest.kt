package rw.itunda.eats

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import rw.itunda.core.ai.AiSummaryClient
import rw.itunda.core.domain.Merchant
import rw.itunda.core.domain.MerchantStatus
import rw.itunda.core.repository.MerchantRepository
import java.time.Instant
import java.time.temporal.ChronoUnit

class AiSummaryServiceTest : BehaviorSpec({

    Given("a real merchant with real reviews and real preset tags") {
        val merchantRepository = mockk<MerchantRepository>()
        val eatsReviewService = mockk<EatsReviewService>()
        val aiSummaryClient = mockk<AiSummaryClient>()
        val service = AiSummaryService(merchantRepository, eatsReviewService, aiSummaryClient)

        val merchant = Merchant(id = "merchant_1", ownerUserId = "owner_1", accountId = "account_1", businessName = "Kigali Diner", status = MerchantStatus.ACTIVE, category = "Rwandan")

        When("generating a real summary with real facts available") {
            every { eatsReviewService.getRestaurantRating("merchant_1") } returns RatingSummary(average = 4.5, count = 20)
            every { eatsReviewService.restaurantGoodPointCounts("merchant_1") } returns mapOf("GREAT_FOOD" to 12, "GOOD_FOR_CONVERSATION" to 5)
            val promptSlot = slot<String>()
            every { aiSummaryClient.complete(any(), capture(promptSlot), any()) } returns "A well-loved Rwandan spot known for great food."

            val summary = service.generateSummaryFor(merchant)

            Then("it returns the real model output") {
                summary shouldBe "A well-loved Rwandan spot known for great food."
            }

            Then("the real prompt is built only from real, already-known facts -- never invented ones") {
                promptSlot.captured shouldBe
                    "Business name: Kigali Diner\nCategory: Rwandan\nReal average rating: 4.5 out of 5, from 20 real reviews\nWhat reviewers most often praised: great food, good for conversation"
            }
        }

        When("there's genuinely too little real signal to summarize honestly (no rating, no tags, no hours)") {
            val bareMerchant = Merchant(id = "merchant_2", ownerUserId = "owner_2", accountId = "account_2", businessName = "New Place", status = MerchantStatus.ACTIVE)
            every { eatsReviewService.getRestaurantRating("merchant_2") } returns RatingSummary(average = null, count = 0)
            every { eatsReviewService.restaurantGoodPointCounts("merchant_2") } returns emptyMap()

            val summary = service.generateSummaryFor(bareMerchant)

            Then("it honestly declines rather than asking the model to pad out nothing") {
                summary shouldBe null
            }
        }
    }

    Given("the real AI client isn't configured") {
        val merchantRepository = mockk<MerchantRepository>()
        val eatsReviewService = mockk<EatsReviewService>()
        val aiSummaryClient = mockk<AiSummaryClient>()
        val service = AiSummaryService(merchantRepository, eatsReviewService, aiSummaryClient)
        every { aiSummaryClient.isConfigured } returns false

        When("running the real batch job") {
            val generated = service.generateMissing()

            Then("it real-no-ops rather than attempting any real API calls") {
                generated shouldBe 0
            }
        }
    }

    Given("a real batch of merchants, one already summarized recently and one stale") {
        val merchantRepository = mockk<MerchantRepository>()
        val eatsReviewService = mockk<EatsReviewService>(relaxed = true)
        val aiSummaryClient = mockk<AiSummaryClient>()
        val service = AiSummaryService(merchantRepository, eatsReviewService, aiSummaryClient)
        every { aiSummaryClient.isConfigured } returns true
        every { eatsReviewService.getRestaurantRating(any()) } returns RatingSummary(average = 4.0, count = 5)
        every { eatsReviewService.restaurantGoodPointCounts(any()) } returns emptyMap()

        val fresh = Merchant(id = "m_fresh", ownerUserId = "o1", accountId = "a1", businessName = "Fresh", status = MerchantStatus.ACTIVE)
        fresh.aiSummary = "Already summarized."
        fresh.aiSummaryGeneratedAt = Instant.now()
        val stale = Merchant(id = "m_stale", ownerUserId = "o2", accountId = "a2", businessName = "Stale", status = MerchantStatus.ACTIVE)
        stale.aiSummary = "Old summary."
        stale.aiSummaryGeneratedAt = Instant.now().minus(30, ChronoUnit.DAYS)
        val missing = Merchant(id = "m_missing", ownerUserId = "o3", accountId = "a3", businessName = "Missing", status = MerchantStatus.ACTIVE)

        every { merchantRepository.findAll() } returns listOf(fresh, stale, missing)
        every { aiSummaryClient.complete(any(), any(), any()) } returns "Real generated summary."
        every { merchantRepository.save(any()) } answers { firstArg() }

        When("running the real batch job") {
            val generated = service.generateMissing()

            Then("it real-regenerates the stale one and the missing one, but leaves the fresh one alone") {
                generated shouldBe 2
                stale.aiSummary shouldBe "Real generated summary."
                missing.aiSummary shouldBe "Real generated summary."
                fresh.aiSummary shouldBe "Already summarized."
            }
        }
    }
})
