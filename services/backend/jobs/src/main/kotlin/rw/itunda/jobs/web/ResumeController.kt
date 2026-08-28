package rw.itunda.jobs.web

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import rw.itunda.jobs.InvalidResumeException
import rw.itunda.jobs.ResumeEntryNotFoundException
import rw.itunda.jobs.ResumeService

data class UpdateResumeProfileRequest(val selfIntro: String? = null, val strengths: List<String> = emptyList(), val additionalInfo: String? = null)
data class AddResumeExperienceRequest(val company: String, val role: String, val period: String, val description: String? = null)
data class AddResumeEducationRequest(val school: String, val degree: String? = null, val major: String? = null)
data class AddResumeCertificationRequest(val name: String, val issuedDate: String? = null)

// Real 이력서 (Karrot 당근알바-style résumé) builder -- see ResumeService's own doc
// comment. Normal itunda-user JWT gate (default SecurityConfig .anyRequest().authenticated()).
@RestController
@RequestMapping("/api/v1/jobs/resume")
class ResumeController(private val resumeService: ResumeService) {

    @GetMapping("/strengths")
    fun strengths(): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "strengths" to ResumeService.STRENGTHS))

    @GetMapping
    fun getMyResume(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> {
        val detail = resumeService.getResume(currentUser.userId)
        return ResponseEntity.ok(
            mapOf(
                "success" to true, "resume" to detail.resume, "experiences" to detail.experiences,
                "educations" to detail.educations, "certifications" to detail.certifications,
                "completionPercent" to detail.completionPercent,
            ),
        )
    }

    @PutMapping
    fun updateProfile(
        @RequestBody request: UpdateResumeProfileRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val resume = resumeService.updateProfile(currentUser.userId, request.selfIntro, request.strengths, request.additionalInfo)
        return ResponseEntity.ok(mapOf("success" to true, "resume" to resume))
    }

    @PostMapping("/experience")
    fun addExperience(
        @RequestBody request: AddResumeExperienceRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val experience = resumeService.addExperience(currentUser.userId, request.company, request.role, request.period, request.description)
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "experience" to experience))
    }

    @DeleteMapping("/experience/{experienceId}")
    fun removeExperience(@PathVariable experienceId: String, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> {
        resumeService.removeExperience(currentUser.userId, experienceId)
        return ResponseEntity.ok(mapOf("success" to true))
    }

    @PostMapping("/education")
    fun addEducation(
        @RequestBody request: AddResumeEducationRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val education = resumeService.addEducation(currentUser.userId, request.school, request.degree, request.major)
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "education" to education))
    }

    @DeleteMapping("/education/{educationId}")
    fun removeEducation(@PathVariable educationId: String, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> {
        resumeService.removeEducation(currentUser.userId, educationId)
        return ResponseEntity.ok(mapOf("success" to true))
    }

    @PostMapping("/certification")
    fun addCertification(
        @RequestBody request: AddResumeCertificationRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val certification = resumeService.addCertification(currentUser.userId, request.name, request.issuedDate)
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "certification" to certification))
    }

    @DeleteMapping("/certification/{certificationId}")
    fun removeCertification(@PathVariable certificationId: String, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> {
        resumeService.removeCertification(currentUser.userId, certificationId)
        return ResponseEntity.ok(mapOf("success" to true))
    }

    @ExceptionHandler(InvalidResumeException::class)
    fun handleInvalid(ex: InvalidResumeException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_RESUME", ex.message ?: "Bad request"))

    @ExceptionHandler(ResumeEntryNotFoundException::class)
    fun handleNotFound(ex: ResumeEntryNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("RESUME_ENTRY_NOT_FOUND", ex.message ?: "Not found"))
}
