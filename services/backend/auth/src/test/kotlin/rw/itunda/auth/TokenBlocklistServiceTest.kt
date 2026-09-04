package rw.itunda.auth

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.time.Duration
import java.time.Instant

/**
 * First direct test coverage for TokenBlocklistService -- the entire real-time
 * enforcement mechanism logout depends on (see JwtAuthenticationFilterTest's own
 * "blacklisted jti" scenario for the consuming side). Every existing caller mocks this
 * class away entirely, so its own branching (the early-return for an
 * already-expired/expiring-now token, and the exact Redis key format both methods must
 * agree on) had never been exercised directly.
 */
class TokenBlocklistServiceTest : BehaviorSpec({

    Given("a jti with a real future expiry") {
        val redisTemplate = mockk<StringRedisTemplate>()
        val valueOperations = mockk<ValueOperations<String, String>>(relaxed = true)
        every { redisTemplate.opsForValue() } returns valueOperations
        val service = TokenBlocklistService(redisTemplate)

        When("it's blacklisted") {
            service.blacklist("jti-1", Instant.now().plusSeconds(3600))

            Then("Redis is written with the right key, a positive TTL, and it comes back blacklisted") {
                val keySlot = slot<String>()
                val ttlSlot = slot<Duration>()
                verify(exactly = 1) { valueOperations.set(capture(keySlot), "revoked", capture(ttlSlot)) }
                keySlot.captured shouldBe "itunda:revoked-jti:jti-1"
                (ttlSlot.captured.seconds in 3590..3600) shouldBe true
            }
        }
    }

    Given("a jti whose token has ALREADY expired by the time blacklist() is called") {
        val redisTemplate = mockk<StringRedisTemplate>()
        val valueOperations = mockk<ValueOperations<String, String>>(relaxed = true)
        every { redisTemplate.opsForValue() } returns valueOperations
        val service = TokenBlocklistService(redisTemplate)

        When("it's blacklisted") {
            service.blacklist("jti-expired", Instant.now().minusSeconds(60))

            Then("nothing is written to Redis at all -- an already-expired token needs no tracking, and a negative TTL would behave unpredictably") {
                verify(exactly = 0) { valueOperations.set(any(), any(), any<Duration>()) }
            }
        }
    }

    Given("a jti whose expiry is exactly now") {
        val redisTemplate = mockk<StringRedisTemplate>()
        val valueOperations = mockk<ValueOperations<String, String>>(relaxed = true)
        every { redisTemplate.opsForValue() } returns valueOperations
        val service = TokenBlocklistService(redisTemplate)
        val now = Instant.now()

        When("it's blacklisted with that exact instant") {
            service.blacklist("jti-now", now)

            Then("nothing is written -- a zero TTL is treated the same as already expired") {
                verify(exactly = 0) { valueOperations.set(any(), any(), any<Duration>()) }
            }
        }
    }

    Given("a jti that was previously blacklisted") {
        val redisTemplate = mockk<StringRedisTemplate>()
        every { redisTemplate.hasKey("itunda:revoked-jti:jti-1") } returns true
        val service = TokenBlocklistService(redisTemplate)

        When("isBlacklisted is checked") {
            val result = service.isBlacklisted("jti-1")

            Then("it reports true") {
                result shouldBe true
            }
        }
    }

    Given("a jti that was never blacklisted") {
        val redisTemplate = mockk<StringRedisTemplate>()
        every { redisTemplate.hasKey("itunda:revoked-jti:jti-2") } returns false
        val service = TokenBlocklistService(redisTemplate)

        When("isBlacklisted is checked") {
            val result = service.isBlacklisted("jti-2")

            Then("it reports false") {
                result shouldBe false
            }
        }
    }
})
