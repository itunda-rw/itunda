package rw.itunda.auth

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.util.Date

/**
 * First direct test coverage for JwtService's own branching -- every existing caller
 * (AuthServiceTest included) uses a real instance only incidentally, to test ITS OWN
 * login/register flow, never systematically exercising JwtService's own edge cases.
 * This is the actual token-minting/verification logic every other auth mechanism in
 * this backend (JwtAuthenticationFilter, DeviceVerificationFilter, TokenBlocklistService)
 * ultimately trusts -- same "shared component, no direct coverage of its own branching"
 * gap class as RateLimiterTest's own precedent.
 */
class JwtServiceTest : BehaviorSpec({

    val secret = "test-secret-at-least-32-bytes-long-for-hs256!!"

    Given("an access token issued for a real user") {
        val jwtService = JwtService(secret)
        val token = jwtService.issueAccessToken(userId = "user-1", phoneNumber = "+250700000000", role = "ADMIN", deviceId = "device-1")

        When("it's verified") {
            val decoded = jwtService.verify(token)

            Then("it decodes with the right userId/role/deviceId and is NOT a refresh token") {
                decoded.shouldNotBeNull()
                decoded.userId shouldBe "user-1"
                decoded.role shouldBe "ADMIN"
                decoded.deviceId shouldBe "device-1"
                decoded.isRefresh shouldBe false
            }
        }
    }

    Given("an access token issued with no deviceId (a client that hasn't adopted device binding yet)") {
        val jwtService = JwtService(secret)
        val token = jwtService.issueAccessToken(userId = "user-1", phoneNumber = "+250700000000", role = "USER")

        When("it's verified") {
            val decoded = jwtService.verify(token)

            Then("deviceId decodes as null -- the honest gradual-rollout gap, not a false positive") {
                decoded.shouldNotBeNull()
                decoded.deviceId.shouldBeNull()
            }
        }
    }

    Given("a refresh token") {
        val jwtService = JwtService(secret)
        val token = jwtService.issueRefreshToken(userId = "user-1", deviceId = "device-1")

        When("it's verified") {
            val decoded = jwtService.verify(token)

            Then("isRefresh is true, deviceId is still carried, and role defaults to USER (refresh tokens never carry a role claim)") {
                decoded.shouldNotBeNull()
                decoded.isRefresh shouldBe true
                decoded.deviceId shouldBe "device-1"
                decoded.role shouldBe "USER"
            }
        }
    }

    Given("two tokens issued back to back") {
        val jwtService = JwtService(secret)
        val first = jwtService.issueAccessToken("user-1", "+250700000000", "USER")
        val second = jwtService.issueAccessToken("user-1", "+250700000000", "USER")

        When("both are verified") {
            val firstJti = jwtService.verify(first)?.jti
            val secondJti = jwtService.verify(second)?.jti

            Then("each has a distinct jti -- revocation can't target one without also hitting the other otherwise") {
                firstJti.shouldNotBeNull()
                secondJti.shouldNotBeNull()
                firstJti shouldNotBe secondJti
            }
        }
    }

    Given("a token signed with a DIFFERENT secret than this JwtService trusts") {
        val jwtService = JwtService(secret)
        val forgedToken = JwtService("a-completely-different-secret-32-bytes-long!!").issueAccessToken("attacker", "+250700000000", "ADMIN")

        When("this JwtService verifies it") {
            val decoded = jwtService.verify(forgedToken)

            Then("it is rejected -- the single most important security property of this class") {
                decoded.shouldBeNull()
            }
        }
    }

    Given("a token whose payload has been tampered with after signing") {
        val jwtService = JwtService(secret)
        val realToken = jwtService.issueAccessToken("user-1", "+250700000000", "USER")
        // Flip the role claim's encoded segment directly, simulating an attacker editing
        // the JWT payload without re-signing it -- HS256 verification must catch this,
        // not just "does this parse as 3 dot-separated base64 segments."
        val parts = realToken.split(".")
        val tamperedPayload = parts[1].reversed()
        val tamperedToken = "${parts[0]}.$tamperedPayload.${parts[2]}"

        When("it's verified") {
            val decoded = jwtService.verify(tamperedToken)

            Then("it is rejected") {
                decoded.shouldBeNull()
            }
        }
    }

    Given("an already-expired token") {
        val jwtService = JwtService(secret)
        // JwtService's own issue* methods always mint a future expiry, so an expired
        // token has to be built directly with the same key/library to exercise this
        // branch at all.
        val key = Keys.hmacShaKeyFor(secret.toByteArray(Charsets.UTF_8))
        val expiredToken = Jwts.builder()
            .id("expired-jti").subject("user-1").claim("role", "USER")
            .issuedAt(Date(System.currentTimeMillis() - 2 * 60 * 60 * 1000))
            .expiration(Date(System.currentTimeMillis() - 60 * 60 * 1000))
            .signWith(key).compact()

        When("it's verified") {
            val decoded = jwtService.verify(expiredToken)

            Then("it is rejected") {
                decoded.shouldBeNull()
            }
        }
    }

    Given("a malformed, non-JWT string") {
        val jwtService = JwtService(secret)

        When("it's verified") {
            val decoded = jwtService.verify("not-a-real-jwt-at-all")

            Then("it is rejected, not thrown") {
                decoded.shouldBeNull()
            }
        }
    }

    Given("an empty string") {
        val jwtService = JwtService(secret)

        When("it's verified") {
            val decoded = jwtService.verify("")

            Then("it is rejected, not thrown") {
                decoded.shouldBeNull()
            }
        }
    }
})
