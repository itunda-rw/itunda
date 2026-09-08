package rw.itunda.merchant

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.Merchant
import rw.itunda.core.domain.MerchantStatus
import rw.itunda.core.domain.MerchantUpdate
import rw.itunda.core.domain.MerchantUpdateLabel
import rw.itunda.core.domain.MerchantUpdateLike
import rw.itunda.core.repository.MerchantRepository
import rw.itunda.core.repository.MerchantUpdateLikeRepository
import rw.itunda.core.repository.MerchantUpdateRepository
import java.util.Optional

class MerchantUpdateServiceTest : BehaviorSpec({

    Given("a registered merchant") {
        val merchantRepository = mockk<MerchantRepository>()
        val merchantUpdateRepository = mockk<MerchantUpdateRepository>()
        val merchantUpdateLikeRepository = mockk<MerchantUpdateLikeRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val service = MerchantUpdateService(merchantRepository, merchantUpdateRepository, merchantUpdateLikeRepository, rateLimiter)

        val merchant = Merchant(id = "merchant_1", ownerUserId = "owner_1", accountId = "account_1", businessName = "Kigali Diner", status = MerchantStatus.ACTIVE)

        When("the real owner posts a real update") {
            every { merchantRepository.findByOwnerUserId("owner_1") } returns merchant
            every { merchantUpdateRepository.save(any()) } answers { firstArg() }

            val update = service.postUpdate("owner_1", MerchantUpdateLabel.EVENT, "  설 연휴 정상 영업  ", "  Open as usual.  ", null, null)

            Then("it trims title/body and stamps the real merchant id") {
                update.merchantId shouldBe "merchant_1"
                update.title shouldBe "설 연휴 정상 영업"
                update.body shouldBe "Open as usual."
                update.label shouldBe MerchantUpdateLabel.EVENT
            }
        }

        When("someone who isn't a real registered merchant tries to post") {
            every { merchantRepository.findByOwnerUserId("stranger") } returns null

            Then("it throws MerchantNotFoundException") {
                try {
                    service.postUpdate("stranger", MerchantUpdateLabel.NOTICE, "Hi", "Body", null, null)
                    error("expected MerchantNotFoundException")
                } catch (e: MerchantNotFoundException) {
                    // expected
                }
            }
        }

        When("posting a blank title") {
            every { merchantRepository.findByOwnerUserId("owner_1") } returns merchant

            Then("it throws InvalidMerchantUpdateException before ever saving") {
                try {
                    service.postUpdate("owner_1", MerchantUpdateLabel.NOTICE, "   ", "Body", null, null)
                    error("expected InvalidMerchantUpdateException")
                } catch (e: InvalidMerchantUpdateException) {
                    verify(exactly = 0) { merchantUpdateRepository.save(any()) }
                }
            }
        }

        When("fetching a real merchant's real updates") {
            val update = MerchantUpdate(id = "u1", merchantId = "merchant_1", label = MerchantUpdateLabel.NOTICE, title = "T", body = "B")
            every { merchantUpdateRepository.findByMerchantIdOrderByCreatedAtDesc("merchant_1") } returns listOf(update)

            val updates = service.getUpdates("merchant_1")

            Then("it returns the real list") {
                updates shouldBe listOf(update)
            }
        }
    }

    Given("a real update with a real likeCount of 3") {
        val merchantRepository = mockk<MerchantRepository>()
        val merchantUpdateRepository = mockk<MerchantUpdateRepository>()
        val merchantUpdateLikeRepository = mockk<MerchantUpdateLikeRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val service = MerchantUpdateService(merchantRepository, merchantUpdateRepository, merchantUpdateLikeRepository, rateLimiter)

        val update = MerchantUpdate(id = "u1", merchantId = "merchant_1", label = MerchantUpdateLabel.NOTICE, title = "T", body = "B", likeCount = 3)

        When("a real viewer likes it for the first time") {
            every { merchantUpdateRepository.findById("u1") } returns Optional.of(update)
            every { merchantUpdateLikeRepository.findByUpdateIdAndUserId("u1", "viewer_1") } returns null
            every { merchantUpdateLikeRepository.save(any()) } answers { firstArg() }
            every { merchantUpdateRepository.save(any()) } answers { firstArg() }

            val liked = service.toggleLike("viewer_1", "u1")

            Then("it returns true and increments the real counter to 4") {
                liked shouldBe true
                update.likeCount shouldBe 4
            }
        }

        When("that same real viewer taps it again") {
            val existingLike = MerchantUpdateLike(id = "like_1", updateId = "u1", userId = "viewer_1")
            every { merchantUpdateRepository.findById("u1") } returns Optional.of(update)
            every { merchantUpdateLikeRepository.findByUpdateIdAndUserId("u1", "viewer_1") } returns existingLike
            every { merchantUpdateLikeRepository.delete(existingLike) } returns Unit
            every { merchantUpdateRepository.save(any()) } answers { firstArg() }

            val liked = service.toggleLike("viewer_1", "u1")

            Then("it returns false and decrements the real counter back to 2") {
                liked shouldBe false
                update.likeCount shouldBe 2
            }
        }

        When("liking an unknown update id") {
            every { merchantUpdateRepository.findById("does_not_exist") } returns Optional.empty()

            Then("it throws MerchantUpdateNotFoundException") {
                try {
                    service.toggleLike("viewer_1", "does_not_exist")
                    error("expected MerchantUpdateNotFoundException")
                } catch (e: MerchantUpdateNotFoundException) {
                    // expected
                }
            }
        }
    }

    // Real repo-wide rate-limiter-verify sweep (2026-09-09) -- rateLimiter was mocked
    // relaxed = true everywhere else in this file, with no test anywhere exercising
    // toggleLike's own real rateLimiter.checkLimit call -- a real regression (the
    // check silently deleted) would have gone undetected. Same throw-and-catch
    // convention this codebase's other rate-limited services already establish.
    Given("a viewer who has exceeded the real merchant-update-like rate limit") {
        val merchantRepository = mockk<MerchantRepository>()
        val merchantUpdateRepository = mockk<MerchantUpdateRepository>()
        val merchantUpdateLikeRepository = mockk<MerchantUpdateLikeRepository>()
        val rateLimiter = mockk<RateLimiter>()
        val service = MerchantUpdateService(merchantRepository, merchantUpdateRepository, merchantUpdateLikeRepository, rateLimiter)
        val update = MerchantUpdate(id = "u1", merchantId = "merchant_1", label = MerchantUpdateLabel.NOTICE, title = "T", body = "B", likeCount = 3)
        every { merchantUpdateRepository.findById("u1") } returns Optional.of(update)
        every { rateLimiter.checkLimit("merchant:update:like:viewer_1", limit = 60, window = any()) } throws RateLimitExceededException("Too many requests")

        When("liking it") {
            Then("a real RateLimitExceededException fires before ever touching the real like row") {
                try {
                    service.toggleLike("viewer_1", "u1")
                    error("expected RateLimitExceededException")
                } catch (e: RateLimitExceededException) {
                    verify(exactly = 0) { merchantUpdateLikeRepository.findByUpdateIdAndUserId(any(), any()) }
                }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
