package rw.itunda.marketplace.web

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.springframework.http.HttpStatus
import org.springframework.web.multipart.MultipartFile
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.auth.RateLimiter
import rw.itunda.core.security.CurrentUser

class UploadControllerTest : BehaviorSpec({

    fun controller(rateLimiter: RateLimiter = mockk(relaxed = true)) = UploadController(rateLimiter)

    Given("a caller who has already hit the real hourly upload limit") {
        val rateLimiter = mockk<RateLimiter>()
        every { rateLimiter.checkLimit("uploads:user_1", limit = 20, window = any()) } throws
            RateLimitExceededException("Too many attempts, please try again later")
        val file = mockk<MultipartFile>(relaxed = true)
        val sut = controller(rateLimiter)

        When("attempting one more upload") {
            Then("the real rate-limit exception propagates rather than being silently swallowed") {
                shouldThrow<RateLimitExceededException> {
                    sut.upload(file, CurrentUser("user_1"))
                }
            }
        }

        When("that exception reaches this controller's own exception handler") {
            // Real gap found and fixed 2026-09-05 (documentation pass): this handler
            // didn't exist at all, so a rate-limited upload fell through to Spring's
            // default generic 500 instead of the same clean 429 every other rate-
            // limited endpoint in this backend returns.
            val response = sut.handleRateLimit(RateLimitExceededException("Too many attempts, please try again later"))

            Then("it maps to a real 429 RATE_LIMITED, not an unhandled error") {
                response.statusCode shouldBe HttpStatus.TOO_MANY_REQUESTS
                response.body?.code shouldBe "RATE_LIMITED"
            }
        }
    }

    Given("an empty file") {
        val file = mockk<MultipartFile>()
        every { file.isEmpty } returns true
        val sut = controller()

        When("uploading it") {
            Then("it's rejected as invalid, not silently accepted") {
                val ex = shouldThrow<InvalidUploadException> {
                    sut.upload(file, CurrentUser("user_1"))
                }
                ex.message shouldBe "No file provided"
            }
        }
    }

    Given("a file over the real 5MB size limit") {
        val file = mockk<MultipartFile>()
        every { file.isEmpty } returns false
        every { file.size } returns 6L * 1024 * 1024
        val sut = controller()

        When("uploading it") {
            Then("it's rejected before ever touching disk") {
                shouldThrow<InvalidUploadException> {
                    sut.upload(file, CurrentUser("user_1"))
                }
            }
        }
    }

    Given("a file of an unsupported content type") {
        val file = mockk<MultipartFile>()
        every { file.isEmpty } returns false
        every { file.size } returns 1024L
        every { file.contentType } returns "application/pdf"
        val sut = controller()

        When("uploading it") {
            Then("only real JPEG/PNG/WebP images are accepted") {
                shouldThrow<InvalidUploadException> {
                    sut.upload(file, CurrentUser("user_1"))
                }
            }
        }
    }
})
