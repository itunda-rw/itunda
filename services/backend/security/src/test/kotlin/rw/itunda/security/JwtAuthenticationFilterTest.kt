package rw.itunda.security

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import rw.itunda.auth.DecodedToken
import rw.itunda.auth.JwtService
import rw.itunda.auth.TokenBlocklistService
import rw.itunda.core.security.CurrentUser
import java.time.Instant

/**
 * First direct test coverage for JwtAuthenticationFilter -- this is the actual
 * authentication enforcement point for every request across every backend service
 * (moved into this shared :security module, 2026-09-01, specifically so card-service
 * shares it rather than a driftable copy), but had zero tests of its own branching
 * before this file. Every branch this filter's own doc comment describes (missing/
 * malformed header, expired/invalid token, refresh-token-as-access-token, blacklisted
 * jti) is exercised directly against a mocked JwtService/TokenBlocklistService --
 * no Spring context needed, since OncePerRequestFilter.doFilterInternal is plain
 * Kotlin/Servlet-API code.
 *
 * Every scenario captures the resulting `SecurityContextHolder` authentication into a
 * local val IMMEDIATELY after `doFilter` returns, inside the same `When` block, rather
 * than reading `SecurityContextHolder` from a nested `Then` block -- Kotest's
 * `beforeTest`/`afterTest` fire on every node in the Given/When/Then tree (containers
 * included, not just leaves), so a `Then` node's own `beforeTest` would clear the
 * context before its assertions ever ran. Caught by this file's own first draft
 * failing 3 real assertions it should have passed -- a real instance of the
 * `feedback_verify_new_test_actually_discriminates` discipline in action.
 */
class JwtAuthenticationFilterTest : BehaviorSpec({

    afterTest { SecurityContextHolder.clearContext() }

    fun decodedToken(isRefresh: Boolean = false, jti: String = "jti-1", role: String = "USER", deviceId: String? = null) =
        DecodedToken(userId = "user-1", isRefresh = isRefresh, jti = jti, expiresAt = Instant.now().plusSeconds(3600), role = role, deviceId = deviceId)

    Given("no Authorization header at all") {
        val jwtService = mockk<JwtService>()
        val blocklistService = mockk<TokenBlocklistService>()
        val filter = JwtAuthenticationFilter(jwtService, blocklistService)
        val request = MockHttpServletRequest()
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        When("the filter runs") {
            filter.doFilter(request, response, chain)
            val authentication: Authentication? = SecurityContextHolder.getContext().authentication

            Then("no authentication is set") {
                authentication shouldBe null
            }
            Then("the filter chain still proceeds -- this filter never blocks by itself") {
                chain.request shouldBe request
            }
        }
    }

    Given("an Authorization header that isn't a Bearer token") {
        val jwtService = mockk<JwtService>()
        val blocklistService = mockk<TokenBlocklistService>()
        val filter = JwtAuthenticationFilter(jwtService, blocklistService)
        val request = MockHttpServletRequest().apply { addHeader("Authorization", "Basic dXNlcjpwYXNz") }
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        When("the filter runs") {
            filter.doFilter(request, response, chain)
            val authentication: Authentication? = SecurityContextHolder.getContext().authentication

            Then("no authentication is set and jwtService is never even consulted") {
                authentication shouldBe null
                verify(exactly = 0) { jwtService.verify(any()) }
            }
        }
    }

    Given("a Bearer token that fails verification (expired, wrong secret, malformed)") {
        val jwtService = mockk<JwtService>()
        every { jwtService.verify("bad-token") } returns null
        val blocklistService = mockk<TokenBlocklistService>()
        val filter = JwtAuthenticationFilter(jwtService, blocklistService)
        val request = MockHttpServletRequest().apply { addHeader("Authorization", "Bearer bad-token") }
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        When("the filter runs") {
            filter.doFilter(request, response, chain)
            val authentication: Authentication? = SecurityContextHolder.getContext().authentication

            Then("no authentication is set") {
                authentication shouldBe null
            }
        }
    }

    Given("a valid REFRESH token used where an access token is expected") {
        val jwtService = mockk<JwtService>()
        every { jwtService.verify("refresh-token") } returns decodedToken(isRefresh = true)
        val blocklistService = mockk<TokenBlocklistService>()
        val filter = JwtAuthenticationFilter(jwtService, blocklistService)
        val request = MockHttpServletRequest().apply { addHeader("Authorization", "Bearer refresh-token") }
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        When("the filter runs") {
            filter.doFilter(request, response, chain)
            val authentication: Authentication? = SecurityContextHolder.getContext().authentication

            Then("it is rejected -- a refresh token must never authenticate a normal request") {
                authentication shouldBe null
            }
        }
    }

    Given("a valid access token whose jti has been logged out (blacklisted)") {
        val jwtService = mockk<JwtService>()
        every { jwtService.verify("logged-out-token") } returns decodedToken(jti = "revoked-jti")
        val blocklistService = mockk<TokenBlocklistService>()
        every { blocklistService.isBlacklisted("revoked-jti") } returns true
        val filter = JwtAuthenticationFilter(jwtService, blocklistService)
        val request = MockHttpServletRequest().apply { addHeader("Authorization", "Bearer logged-out-token") }
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        When("the filter runs") {
            filter.doFilter(request, response, chain)
            val authentication: Authentication? = SecurityContextHolder.getContext().authentication

            Then("it is rejected -- otherwise logout would be purely cosmetic") {
                authentication shouldBe null
            }
        }
    }

    Given("a genuinely valid, non-refresh, non-blacklisted access token") {
        val jwtService = mockk<JwtService>()
        every { jwtService.verify("good-token") } returns decodedToken(role = "ADMIN", deviceId = "device-42")
        val blocklistService = mockk<TokenBlocklistService>()
        every { blocklistService.isBlacklisted("jti-1") } returns false
        val filter = JwtAuthenticationFilter(jwtService, blocklistService)
        val request = MockHttpServletRequest().apply { addHeader("Authorization", "Bearer good-token") }
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        When("the filter runs") {
            filter.doFilter(request, response, chain)
            val authentication: Authentication? = SecurityContextHolder.getContext().authentication
            val principal = authentication?.principal as? CurrentUser

            Then("authentication is set with the right principal and a ROLE_-prefixed authority") {
                principal?.userId shouldBe "user-1"
                principal?.role shouldBe "ADMIN"
                principal?.deviceId shouldBe "device-42"
                authentication?.authorities?.map { it.authority } shouldBe listOf("ROLE_ADMIN")
            }
        }
    }
})
