package rw.itunda.system.engineering

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

class EngineeringStatusProviderTest : BehaviorSpec({
    Given("an Engineering Status provider") {
        val headSha = "0123456789012345678901234567890123456789"

        When("GitHub returns a successful check run for the configured branch") {
            val builder = RestClient.builder()
            val server = MockRestServiceServer.bindTo(builder).build()
            server.expect(requestTo("https://api.github.com/repos/itunda-rw/itunda/commits/main"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""{"sha":"$headSha"}""", MediaType.APPLICATION_JSON))
            server.expect(requestTo("https://api.github.com/repos/itunda-rw/itunda/commits/$headSha/check-runs?per_page=100&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                    """{"check_runs":[{"name":"CI","status":"completed","conclusion":"success","details_url":"https://github.com/itunda-rw/itunda/actions/runs/1"}]}""",
                    MediaType.APPLICATION_JSON,
                ))
            val provider = EngineeringStatusProvider(
                ObjectMapper(), "itunda-rw/itunda", "main", "CI", "", "test", builder,
            )

            val snapshot = provider.readStatus()
            val checks = snapshot["checks"] as List<*>
            val check = checks.single() as Map<*, *>

            Then("it binds the result to the exact HEAD SHA and preserves source metadata") {
                snapshot["headSha"] shouldBe headSha
                snapshot["requiredCheckIds"] shouldBe listOf("CI")
                checks shouldHaveSize 1
                check["id"] shouldBe "CI"
                check["result"] shouldBe "success"
                check["commitSha"] shouldBe headSha
                check["source"] shouldBe "GitHub Actions check runs"
                check["detailsUrl"] shouldBe "https://github.com/itunda-rw/itunda/actions/runs/1"
            }
            server.verify()
        }

        When("required checks are not configured") {
            val provider = EngineeringStatusProvider(
                ObjectMapper(), "itunda-rw/itunda", "main", "  ", "", "test", RestClient.builder(),
            )

            Then("it fails closed before making a request") {
                shouldThrow<IllegalArgumentException> { provider.readStatus() }
            }
        }

        When("GitHub returns an invalid HEAD SHA") {
            val builder = RestClient.builder()
            val server = MockRestServiceServer.bindTo(builder).build()
            server.expect(requestTo("https://api.github.com/repos/itunda-rw/itunda/commits/main"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""{"sha":"not-a-sha"}""", MediaType.APPLICATION_JSON))
            val provider = EngineeringStatusProvider(
                ObjectMapper(), "itunda-rw/itunda", "main", "CI", "", "test", builder,
            )

            Then("it rejects the response rather than publishing an unbound snapshot") {
                shouldThrow<IllegalArgumentException> { provider.readStatus() }
            }
            server.verify()
        }

        When("the first page is full") {
            val builder = RestClient.builder()
            val server = MockRestServiceServer.bindTo(builder).build()
            server.expect(requestTo("https://api.github.com/repos/itunda-rw/itunda/commits/main"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""{"sha":"$headSha"}""", MediaType.APPLICATION_JSON))
            val firstPageRuns = (1..100).joinToString(",") { index ->
                """{"name":"check-$index","status":"completed","conclusion":"success"}"""
            }
            server.expect(requestTo("https://api.github.com/repos/itunda-rw/itunda/commits/$headSha/check-runs?per_page=100&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""{"check_runs":[$firstPageRuns]}""", MediaType.APPLICATION_JSON))
            server.expect(requestTo("https://api.github.com/repos/itunda-rw/itunda/commits/$headSha/check-runs?per_page=100&page=2"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                    """{"check_runs":[{"name":"check-101","status":"completed","conclusion":"success"}]}""",
                    MediaType.APPLICATION_JSON,
                ))
            val provider = EngineeringStatusProvider(
                ObjectMapper(), "itunda-rw/itunda", "main", "check-101", "", "test", builder,
            )

            val snapshot = provider.readStatus()

            Then("it requests the next page and includes checks from it") {
                (snapshot["checks"] as List<*>) shouldHaveSize 101
                server.verify()
            }
        }
    }
})
