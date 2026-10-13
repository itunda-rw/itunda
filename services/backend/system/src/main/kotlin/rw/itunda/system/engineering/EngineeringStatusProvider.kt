package rw.itunda.system.engineering

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import java.time.Instant

/**
 * Read-only adapter for GitHub Actions check runs on the configured repository/branch.
 *
 * It intentionally fails closed when configuration or GitHub data is unavailable.
 * A token is optional for public repositories but recommended for API rate limits.
 */
@Service
class EngineeringStatusProvider(
    private val objectMapper: ObjectMapper,
    @Value("\${itunda.engineering.repository:}") private val repository: String,
    @Value("\${itunda.engineering.branch:main}") private val branch: String,
    @Value("\${itunda.engineering.required-check-ids:}") requiredCheckIds: String,
    @Value("\${itunda.engineering.github-token:}") private val githubToken: String,
    @Value("\${itunda.engineering.environment:local}") private val environment: String,
    restClientBuilder: RestClient.Builder,
) {
    private val requiredIds = requiredCheckIds.split(',')
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinct()

    private val client = restClientBuilder.clone()
        .baseUrl("https://api.github.com")
        .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
        .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
        .apply {
            if (githubToken.isNotBlank()) {
                defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer $githubToken")
            }
        }
        .build()

    fun readStatus(): Map<String, Any> {
        require(repository.matches(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+"))) {
            "Engineering status is not configured: set ITUNDA_ENGINEERING_REPOSITORY."
        }
        require(branch.isNotBlank()) { "Engineering status branch is not configured." }
        require(requiredIds.isNotEmpty()) {
            "Engineering status is not configured: set ITUNDA_ENGINEERING_REQUIRED_CHECK_IDS."
        }

        val commit = getJson("/repos/$repository/commits/${encodePath(branch)}")
        val headSha = commit.path("sha").asText("")
        require(headSha.matches(Regex("[0-9a-fA-F]{40}"))) {
            "GitHub returned an invalid commit SHA."
        }

        // GitHub paginates check-runs. Never publish a partial snapshot as if it were complete.
        val allRuns = mutableListOf<JsonNode>()
        var paginationComplete = false
        for (page in 1..10) {
            val response = getJson("/repos/$repository/commits/$headSha/check-runs?per_page=100&page=$page")
            val pageRuns = response.path("check_runs")
            require(pageRuns.isArray) { "GitHub returned a malformed check-runs response." }
            allRuns.addAll(pageRuns)
            if (pageRuns.size() < 100) {
                paginationComplete = true
                break
            }
        }
        require(paginationComplete) {
            "GitHub check-runs exceeded the supported 1,000-run snapshot limit."
        }

        val now = Instant.now().toString()
        val checks = allRuns.map { run ->
            val status = run.path("status").asText("")
            val conclusion = run.path("conclusion").asText("")
            val result = when {
                status != "completed" -> "pending"
                conclusion == "success" -> "success"
                conclusion == "failure" || conclusion == "timed_out" || conclusion == "action_required" -> "failure"
                conclusion == "cancelled" || conclusion == "startup_failure" -> "cancelled"
                else -> "unknown"
            }
            mapOf(
                "id" to run.path("name").asText("unknown"),
                "name" to run.path("name").asText("Unnamed check"),
                "result" to result,
                "commitSha" to headSha,
                "environment" to environment,
                "source" to "GitHub Actions check runs",
                "observedAt" to now,
                "detailsUrl" to run.path("details_url").asText("").takeIf { it.startsWith("https://") },
            )
        }.toList()

        return mapOf(
            "generatedAt" to now,
            "repository" to repository,
            "branch" to branch,
            "headSha" to headSha,
            "requiredCheckIds" to requiredIds,
            "checks" to checks,
        )
    }

    private fun getJson(path: String): JsonNode {
        val body = client.get().uri(path).retrieve().body(String::class.java)
            ?: error("GitHub returned an empty response.")
        return objectMapper.readTree(body)
    }

    private fun encodePath(value: String): String =
        java.net.URLEncoder.encode(value, Charsets.UTF_8).replace("+", "%20")
}
