package rw.itunda.core.network

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

// Real itunda Hood (Karrot 당근마켓) redesign, 2026-08-28 -- genuinely new backend
// surface added this pass (resume builder, 살아본 후기 neighborhood-lived reviews).
// Kept as its own Retrofit interface/file rather than growing the already-at-baseline
// ApiService.kt, same real precedent AuthApi already established (a separate
// `retrofit.create(...)` instance, not a single monolithic interface).

// Real 이력서 (Karrot 당근알바-style résumé) builder -- see backend Resume.kt's own doc
// comment. strengths stays the raw comma-delimited string the backend returns (same
// convention as Merchant.closedWeekdays already uses client-side); split on "," to render.
data class ResumeStrengthDto(val id: String, val label: String)
data class ResumeDto(
    val id: String, val userId: String, val selfIntro: String? = null,
    val strengths: String? = null, val additionalInfo: String? = null,
    val createdAt: String, val updatedAt: String,
)
data class ResumeExperienceDto(val id: String, val resumeId: String, val company: String, val role: String, val period: String, val description: String? = null, val createdAt: String)
data class ResumeEducationDto(val id: String, val resumeId: String, val school: String, val degree: String? = null, val major: String? = null, val createdAt: String)
data class ResumeCertificationDto(val id: String, val resumeId: String, val name: String, val issuedDate: String? = null, val createdAt: String)
data class ResumeDetailResponse(
    val success: Boolean, val resume: ResumeDto?, val experiences: List<ResumeExperienceDto>,
    val educations: List<ResumeEducationDto>, val certifications: List<ResumeCertificationDto>, val completionPercent: Int,
)
data class ResumeStrengthsResponse(val success: Boolean, val strengths: List<ResumeStrengthDto>)
data class UpdateResumeProfileRequest(val selfIntro: String? = null, val strengths: List<String> = emptyList(), val additionalInfo: String? = null)
data class ResumeResponse(val success: Boolean, val resume: ResumeDto)
data class AddResumeExperienceRequest(val company: String, val role: String, val period: String, val description: String? = null)
data class ResumeExperienceResponse(val success: Boolean, val experience: ResumeExperienceDto)
data class AddResumeEducationRequest(val school: String, val degree: String? = null, val major: String? = null)
data class ResumeEducationResponse(val success: Boolean, val education: ResumeEducationDto)
data class AddResumeCertificationRequest(val name: String, val issuedDate: String? = null)
data class ResumeCertificationResponse(val success: Boolean, val certification: ResumeCertificationDto)

// Real 살아본 후기 (Karrot "lived here" neighborhood reviews) -- see backend
// NeighborhoodReview.kt's own doc comment. Distinct from HoodReviewDto, which is a
// buyer/seller transaction review, not a public review of an area.
data class NeighborhoodReviewDto(
    val id: String, val userId: String, val neighborhood: String,
    val residencyYears: Int? = null, val body: String, val createdAt: String,
)
data class NeighborhoodReviewsResponse(val success: Boolean, val reviews: List<NeighborhoodReviewDto>)
data class SubmitNeighborhoodReviewRequest(val residencyYears: Int? = null, val body: String)
data class NeighborhoodReviewResponse(val success: Boolean, val review: NeighborhoodReviewDto)

interface HoodApi {
    @GET("api/v1/jobs/resume/strengths")
    suspend fun getResumeStrengths(): ResumeStrengthsResponse

    @GET("api/v1/jobs/resume")
    suspend fun getMyResume(): ResumeDetailResponse

    @PUT("api/v1/jobs/resume")
    suspend fun updateResumeProfile(@Body request: UpdateResumeProfileRequest): ResumeResponse

    @POST("api/v1/jobs/resume/experience")
    suspend fun addResumeExperience(@Body request: AddResumeExperienceRequest): ResumeExperienceResponse

    @DELETE("api/v1/jobs/resume/experience/{experienceId}")
    suspend fun removeResumeExperience(@Path("experienceId") experienceId: String): SuccessResponse

    @POST("api/v1/jobs/resume/education")
    suspend fun addResumeEducation(@Body request: AddResumeEducationRequest): ResumeEducationResponse

    @DELETE("api/v1/jobs/resume/education/{educationId}")
    suspend fun removeResumeEducation(@Path("educationId") educationId: String): SuccessResponse

    @POST("api/v1/jobs/resume/certification")
    suspend fun addResumeCertification(@Body request: AddResumeCertificationRequest): ResumeCertificationResponse

    @DELETE("api/v1/jobs/resume/certification/{certificationId}")
    suspend fun removeResumeCertification(@Path("certificationId") certificationId: String): SuccessResponse

    @GET("api/v1/community/neighborhoods/{neighborhood}/reviews")
    suspend fun getNeighborhoodReviews(@Path("neighborhood") neighborhood: String): NeighborhoodReviewsResponse

    @POST("api/v1/community/neighborhoods/{neighborhood}/reviews")
    suspend fun submitNeighborhoodReview(@Path("neighborhood") neighborhood: String, @Body request: SubmitNeighborhoodReviewRequest): NeighborhoodReviewResponse
}
