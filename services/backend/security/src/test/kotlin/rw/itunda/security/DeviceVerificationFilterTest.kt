package rw.itunda.security

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import rw.itunda.core.domain.TrustedDevice
import rw.itunda.core.repository.TrustedDeviceRepository
import rw.itunda.core.security.CurrentUser
import java.time.Instant

/**
 * First direct test coverage for DeviceVerificationFilter -- the real device-binding
 * enforcement point for every money-moving (Idempotency-Key-carrying) request across
 * the backend. Exercises every branch its own doc comment describes directly against a
 * mocked TrustedDeviceRepository: no request state to gate on, an unauthenticated
 * request, the deliberate gradual-rollout gap (no deviceId claim), an unrecognized
 * device, an untrusted device, and the real happy path.
 *
 * `setCurrentUser` is called INSIDE each `When` block, immediately before `doFilter` --
 * not in the enclosing `Given` block -- because Kotest's `beforeTest`/`afterTest` fire
 * on every node in the Given/When/Then tree (containers included), so setting it one
 * level up would get silently cleared by the nested `When` node's own `beforeTest`
 * before `doFilter` ever read it. Caught by this file's own first draft: 2 of the 3
 * "should reject" scenarios silently fell through to a false 200 pass, exactly the
 * kind of test that looks green without actually discriminating (see
 * `feedback_verify_new_test_actually_discriminates`).
 */
class DeviceVerificationFilterTest : BehaviorSpec({

    afterTest { SecurityContextHolder.clearContext() }

    fun setCurrentUser(userId: String = "user-1", deviceId: String? = "device-1") {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(CurrentUser(userId, "USER", deviceId), null, emptyList())
    }

    fun trustedDevice(trusted: Boolean) = TrustedDevice(
        id = "td-1", userId = "user-1", deviceId = "device-1", deviceName = "Test device",
        trusted = trusted, firstSeenAt = Instant.now(), lastSeenAt = Instant.now(),
    )

    Given("a request with no Idempotency-Key header at all") {
        val repository = mockk<TrustedDeviceRepository>()
        val filter = DeviceVerificationFilter(repository, ObjectMapper())

        When("the filter runs") {
            setCurrentUser()
            val request = MockHttpServletRequest()
            val response = MockHttpServletResponse()
            val chain = MockFilterChain()
            filter.doFilter(request, response, chain)

            Then("it passes through untouched -- this gate only applies to money-moving requests") {
                response.status shouldBe 200
                chain.request shouldBe request
            }
        }
    }

    Given("an Idempotency-Key request with no authenticated user") {
        val repository = mockk<TrustedDeviceRepository>()
        val filter = DeviceVerificationFilter(repository, ObjectMapper())

        When("the filter runs") {
            val request = MockHttpServletRequest().apply { addHeader("Idempotency-Key", "key-1") }
            val response = MockHttpServletResponse()
            val chain = MockFilterChain()
            filter.doFilter(request, response, chain)

            Then("it passes through -- JwtAuthenticationFilter's own rejection already handles this case") {
                response.status shouldBe 200
                chain.request shouldBe request
            }
        }
    }

    Given("an authenticated user whose token has no deviceId claim (the deliberate gradual-rollout gap)") {
        val repository = mockk<TrustedDeviceRepository>()
        val filter = DeviceVerificationFilter(repository, ObjectMapper())

        When("the filter runs") {
            setCurrentUser(deviceId = null)
            val request = MockHttpServletRequest().apply { addHeader("Idempotency-Key", "key-1") }
            val response = MockHttpServletResponse()
            val chain = MockFilterChain()
            filter.doFilter(request, response, chain)

            Then("it passes through untouched -- this feature can only apply once a client actually adopts it") {
                response.status shouldBe 200
                chain.request shouldBe request
            }
        }
    }

    Given("an authenticated user with a deviceId that has never been seen before") {
        val repository = mockk<TrustedDeviceRepository>()
        every { repository.findByUserIdAndDeviceId("user-1", "device-1") } returns null
        val filter = DeviceVerificationFilter(repository, ObjectMapper())

        When("the filter runs") {
            setCurrentUser()
            val request = MockHttpServletRequest().apply { addHeader("Idempotency-Key", "key-1") }
            val response = MockHttpServletResponse()
            val chain = MockFilterChain()
            filter.doFilter(request, response, chain)

            Then("it is rejected with a real DEVICE_NOT_VERIFIED 403, and the chain never continues") {
                response.status shouldBe 403
                response.contentAsString shouldBe """{"code":"DEVICE_NOT_VERIFIED","message":"This device hasn't been verified yet -- verify it (re-enter your password) before moving money."}"""
                chain.request shouldBe null
            }
        }
    }

    Given("an authenticated user whose device is known but not yet trusted") {
        val repository = mockk<TrustedDeviceRepository>()
        every { repository.findByUserIdAndDeviceId("user-1", "device-1") } returns trustedDevice(trusted = false)
        val filter = DeviceVerificationFilter(repository, ObjectMapper())

        When("the filter runs") {
            setCurrentUser()
            val request = MockHttpServletRequest().apply { addHeader("Idempotency-Key", "key-1") }
            val response = MockHttpServletResponse()
            val chain = MockFilterChain()
            filter.doFilter(request, response, chain)

            Then("it is rejected the same way as an unrecognized device") {
                response.status shouldBe 403
                chain.request shouldBe null
            }
        }
    }

    Given("an authenticated user on a genuinely trusted device") {
        val repository = mockk<TrustedDeviceRepository>()
        every { repository.findByUserIdAndDeviceId("user-1", "device-1") } returns trustedDevice(trusted = true)
        val filter = DeviceVerificationFilter(repository, ObjectMapper())

        When("the filter runs") {
            setCurrentUser()
            val request = MockHttpServletRequest().apply { addHeader("Idempotency-Key", "key-1") }
            val response = MockHttpServletResponse()
            val chain = MockFilterChain()
            filter.doFilter(request, response, chain)

            Then("the request proceeds normally") {
                response.status shouldBe 200
                chain.request shouldBe request
            }
        }
    }
})
