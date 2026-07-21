package rw.itunda.system.web

import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import rw.itunda.core.domain.HoodReportTargetType
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import rw.itunda.core.web.pageMeta
import rw.itunda.system.HoodReportAlreadyOpenException
import rw.itunda.system.HoodReportNotFoundException
import rw.itunda.system.HoodReportTargetNotFoundException
import rw.itunda.system.HoodReportService

data class CreateHoodReportRequest(val targetType: HoodReportTargetType, val targetId: String, val reason: String)

@RestController
@RequestMapping("/api/v1/hood/reports")
class HoodReportController(private val hoodReportService: HoodReportService) {
    @PostMapping
    fun create(@RequestBody request: CreateHoodReportRequest, @AuthenticationPrincipal user: CurrentUser) =
        ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "report" to hoodReportService.report(user.userId, request.targetType, request.targetId, request.reason)))

    @ExceptionHandler(HoodReportAlreadyOpenException::class)
    fun duplicate(ex: HoodReportAlreadyOpenException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("HOOD_REPORT_ALREADY_OPEN", ex.message ?: "Conflict"))

    @ExceptionHandler(HoodReportTargetNotFoundException::class)
    fun targetMissing(ex: HoodReportTargetNotFoundException) = ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("HOOD_REPORT_TARGET_NOT_FOUND", ex.message ?: "Not found"))
}

@RestController
@RequestMapping("/api/v1/system/hood-reports")
class HoodReportAdminController(private val hoodReportService: HoodReportService) {
    @GetMapping
    fun queue(@PageableDefault(size = 30) pageable: Pageable): ResponseEntity<Map<String, Any>> {
        val page = hoodReportService.queue(pageable)
        return ResponseEntity.ok(mapOf("success" to true, "reports" to page.content) + pageMeta(page))
    }

    @PostMapping("/{id}/resolve")
    fun resolve(@PathVariable id: String, @AuthenticationPrincipal user: CurrentUser) =
        ResponseEntity.ok(mapOf("success" to true, "report" to hoodReportService.resolve(id, user.userId)))

    @ExceptionHandler(HoodReportNotFoundException::class)
    fun missing(ex: HoodReportNotFoundException) = ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("HOOD_REPORT_NOT_FOUND", ex.message ?: "Not found"))
}
