package rw.itunda.auth

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.RedisScript
import java.time.Duration

/** First direct test coverage for RateLimiter -- a shared component used by 142+ real
 * call sites (P2P/loans/savings/stocks/account/bills/etc, per this class's own doc
 * comment) with, until now, no test verifying its own threshold/boundary logic. Every
 * caller mocks this class away (`mockk<RateLimiter>(relaxed = true)`), which correctly
 * tests each caller's own wiring but never exercises RateLimiter's real branching --
 * this file closes that gap once, at the source, rather than duplicating shallow
 * coverage across every caller. */
class RateLimiterTest : BehaviorSpec({

    Given("a key under its configured limit") {
        val redisTemplate = mockk<StringRedisTemplate>()
        every { redisTemplate.execute(any<RedisScript<List<*>>>(), any<List<String>>(), *anyVararg()) } returns listOf(5L, 3599L)
        val rateLimiter = RateLimiter(redisTemplate)

        When("checkLimit is called") {
            Then("it does not throw") {
                rateLimiter.checkLimit("test:key", limit = 30, window = Duration.ofHours(1))
            }
        }
    }

    Given("a key exactly at its configured limit") {
        val redisTemplate = mockk<StringRedisTemplate>()
        every { redisTemplate.execute(any<RedisScript<List<*>>>(), any<List<String>>(), *anyVararg()) } returns listOf(30L, 3599L)
        val rateLimiter = RateLimiter(redisTemplate)

        When("checkLimit is called") {
            Then("it does not throw -- the limit itself is the last allowed call, not the first rejected one") {
                rateLimiter.checkLimit("test:key", limit = 30, window = Duration.ofHours(1))
            }
        }
    }

    Given("a key one over its configured limit") {
        val redisTemplate = mockk<StringRedisTemplate>()
        every { redisTemplate.execute(any<RedisScript<List<*>>>(), any<List<String>>(), *anyVararg()) } returns listOf(31L, 3599L)
        val rateLimiter = RateLimiter(redisTemplate)

        When("checkLimit is called") {
            Then("it throws RateLimitExceededException with a real, reassuring message") {
                val ex = shouldThrow<RateLimitExceededException> {
                    rateLimiter.checkLimit("test:key", limit = 30, window = Duration.ofHours(1))
                }
                ex.message shouldBe "Too many attempts, please try again later"
            }
        }
    }

    Given("a window duration") {
        val redisTemplate = mockk<StringRedisTemplate>()
        val windowArgSlot = slot<String>()
        every {
            redisTemplate.execute(any<RedisScript<List<*>>>(), any<List<String>>(), capture(windowArgSlot))
        } returns listOf(1L, 7200L)
        val rateLimiter = RateLimiter(redisTemplate)

        When("checkLimit is called with a 2-hour window") {
            rateLimiter.checkLimit("test:key", limit = 30, window = Duration.ofHours(2))

            Then("the EXPIRE argument passed to the Lua script is the window in real seconds, not minutes or hours") {
                windowArgSlot.captured shouldBe "7200"
            }
        }
    }

    Given("a real call within a configured limit") {
        val redisTemplate = mockk<StringRedisTemplate>()
        every { redisTemplate.execute(any<RedisScript<List<*>>>(), any<List<String>>(), *anyVararg()) } returns listOf(12L, 1800L)
        val rateLimiter = RateLimiter(redisTemplate)

        When("checkLimit is called") {
            rateLimiter.checkLimit("test:key", limit = 30, window = Duration.ofHours(1))

            Then("it stashes the real remaining count and reset seconds for RateLimitHeaderFilter to report") {
                val info = RateLimitContext.get()
                info shouldBe RateLimitInfo(limit = 30, remaining = 18, resetSeconds = 1800)
                RateLimitContext.clear()
            }
        }
    }

    Given("a call that exceeds its configured limit") {
        val redisTemplate = mockk<StringRedisTemplate>()
        every { redisTemplate.execute(any<RedisScript<List<*>>>(), any<List<String>>(), *anyVararg()) } returns listOf(31L, 900L)
        val rateLimiter = RateLimiter(redisTemplate)

        When("checkLimit throws") {
            Then("it still stashes remaining=0, not a negative count, so the 429 response reports a sane header") {
                shouldThrow<RateLimitExceededException> {
                    rateLimiter.checkLimit("test:key", limit = 30, window = Duration.ofHours(1))
                }
                RateLimitContext.get() shouldBe RateLimitInfo(limit = 30, remaining = 0, resetSeconds = 900)
                RateLimitContext.clear()
            }
        }
    }
})
