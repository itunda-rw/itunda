package rw.itunda.p2p.web

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import rw.itunda.p2p.InvalidScamReportException
import rw.itunda.p2p.ScamReportAlreadyExistsException
import rw.itunda.p2p.ScamReportService

data class ReportScamRequest(val identifier: String, val reason: String)

// Real Toss 사기계좌 조회 (fraud-account lookup before transfer)-style scam report --
// see ScamReportService's own doc comment for the full sourced account.
@RestController
@RequestMapping("/api/v1/p2p/scam-reports")
class ScamReportController(private val scamReportService: ScamReportService) {

    @PostMapping
    fun report(@RequestBody request: ReportScamRequest, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> {
        val report = scamReportService.reportScam(currentUser.userId, request.identifier, request.reason)
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "report" to report))
    }

    @GetMapping("/check")
    fun check(@RequestParam identifier: String): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "result" to scamReportService.checkScamStatus(identifier)))

    @GetMapping("/mine")
    fun mine(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "reports" to scamReportService.getMyReports(currentUser.userId)))

    @ExceptionHandler(InvalidScamReportException::class)
    fun handleInvalid(ex: InvalidScamReportException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_SCAM_REPORT", ex.message ?: "Bad request"))

    @ExceptionHandler(ScamReportAlreadyExistsException::class)
    fun handleAlreadyExists(ex: ScamReportAlreadyExistsException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("SCAM_REPORT_ALREADY_EXISTS", ex.message ?: "Conflict"))

    @ExceptionHandler(RateLimitExceededException::class)
    fun handleRateLimit(ex: RateLimitExceededException) =
        ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ApiError("RATE_LIMITED", ex.message ?: "Too many requests"))
}
