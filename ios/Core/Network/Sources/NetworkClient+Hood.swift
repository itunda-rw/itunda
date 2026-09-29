import Foundation

// Real itunda Hood (Karrot 당근마켓) redesign, 2026-08-28 -- genuinely new backend
// surface added this pass (résumé builder, 살아본 후기 neighborhood-lived reviews,
// 당근카 vehicle listing fields, 동네생활 topic chips). Split into its own extension
// file from the start, matching NetworkClient+Maps.swift's own precedent -- see that
// file's doc comment for why. Mirrors Android's HoodApi.kt/MarketplaceDtos.kt
// field-for-field.

// ListingDto/CreateListingRequest moved here from NetworkClient.swift to make room for
// the 9 new vehicle/lease-takeover fields below.
public struct ListingDto: Decodable, Identifiable {
    public let id: String
    public let sellerId: String
    public let title: String
    public let description: String
    public let price: Double
    public let category: String
    public let status: String
    public let createdAt: String
    public let latitude: Double?
    public let longitude: Double?
    public let meetingPlace: String?
    public let buyerId: String?
    public let boostedUntil: String?
    public let photoUrl: String?
    // Real 당근카 (Karrot Vehicles) fields (2026-08-28) -- see backend
    // Listing.vehicleIsLeaseTakeover's own doc comment. Nil mileage/claim count means
    // "not a vehicle listing"; lease* fields stay nil unless vehicleIsLeaseTakeover.
    public let vehicleMileageKm: Int?
    public let vehicleInsuranceClaimCount: Int?
    public let vehicleIsLeaseTakeover: Bool?
    public let leaseTotalAcquisitionCost: Double?
    public let leaseRemainingMonths: Int?
    public let leaseTotalMonths: Int?
    public let leaseMonthlyPayment: Double?
    public let leaseSubsidyAmount: Double?
    public let leaseReturnFee: Double?
}

public struct CreateListingRequest: Encodable {
    public let title: String
    public let description: String
    public let price: Double
    public let category: String
    public let latitude: Double?
    public let longitude: Double?
    public let meetingPlace: String?
    public let photoUrl: String?
    public let vehicleMileageKm: Int?
    public let vehicleInsuranceClaimCount: Int?
    public let vehicleIsLeaseTakeover: Bool
    public let leaseTotalAcquisitionCost: Double?
    public let leaseRemainingMonths: Int?
    public let leaseTotalMonths: Int?
    public let leaseMonthlyPayment: Double?
    public let leaseSubsidyAmount: Double?
    public let leaseReturnFee: Double?
}

// Real 이력서 (Karrot 당근알바-style résumé) builder -- see backend Resume.kt's own doc
// comment. `strengths` stays the raw comma-delimited string the backend returns (same
// convention Android's Kotlin client already uses); split on "," to render.
public struct ResumeStrengthDto: Decodable, Identifiable { public let id: String; public let label: String }
public struct ResumeDto: Decodable {
    public let id: String
    public let userId: String
    public let selfIntro: String?
    public let strengths: String?
    public let additionalInfo: String?
    public let createdAt: String
    public let updatedAt: String
}
public struct ResumeExperienceDto: Decodable, Identifiable { public let id: String; public let resumeId: String; public let company: String; public let role: String; public let period: String; public let description: String?; public let createdAt: String }
public struct ResumeEducationDto: Decodable, Identifiable { public let id: String; public let resumeId: String; public let school: String; public let degree: String?; public let major: String?; public let createdAt: String }
public struct ResumeCertificationDto: Decodable, Identifiable { public let id: String; public let resumeId: String; public let name: String; public let issuedDate: String?; public let createdAt: String }
public struct ResumeDetailResponse: Decodable {
    public let success: Bool
    public let resume: ResumeDto?
    public let experiences: [ResumeExperienceDto]
    public let educations: [ResumeEducationDto]
    public let certifications: [ResumeCertificationDto]
    public let completionPercent: Int
}
public struct ResumeStrengthsResponse: Decodable { public let success: Bool; public let strengths: [ResumeStrengthDto] }
public struct UpdateResumeProfileRequest: Encodable { public let selfIntro: String?; public let strengths: [String]; public let additionalInfo: String? }
public struct ResumeResponse: Decodable { public let success: Bool; public let resume: ResumeDto }
public struct AddResumeExperienceRequest: Encodable { public let company: String; public let role: String; public let period: String; public let description: String? }
public struct ResumeExperienceResponse: Decodable { public let success: Bool; public let experience: ResumeExperienceDto }
public struct AddResumeEducationRequest: Encodable { public let school: String; public let degree: String?; public let major: String? }
public struct ResumeEducationResponse: Decodable { public let success: Bool; public let education: ResumeEducationDto }
public struct AddResumeCertificationRequest: Encodable { public let name: String; public let issuedDate: String? }
public struct ResumeCertificationResponse: Decodable { public let success: Bool; public let certification: ResumeCertificationDto }

// Real 살아본 후기 (Karrot "lived here" neighborhood reviews) -- see backend
// NeighborhoodReview.kt's own doc comment. Distinct from a buyer/seller transaction
// review -- a public review of an area, not a transaction.
public struct NeighborhoodReviewDto: Decodable, Identifiable { public let id: String; public let userId: String; public let neighborhood: String; public let residencyYears: Int?; public let body: String; public let createdAt: String }
public struct NeighborhoodReviewsResponse: Decodable { public let success: Bool; public let reviews: [NeighborhoodReviewDto] }
public struct SubmitNeighborhoodReviewRequest: Encodable { public let residencyYears: Int?; public let body: String }
public struct NeighborhoodReviewResponse: Decodable { public let success: Bool; public let review: NeighborhoodReviewDto }

// Real 동네생활 topic-chip filter row (2026-08-28) -- reuses CommunityCategoryDto's own
// {id,label} shape, same as the backend reuses CommunityCategory's data-class shape.
public struct CommunityTopicsResponse: Decodable { public let success: Bool; public let topics: [CommunityCategoryDto] }

extension NetworkClient {
    public func getResumeStrengths() async throws -> ResumeStrengthsResponse { try await get("api/v1/jobs/resume/strengths") }
    public func getMyResume() async throws -> ResumeDetailResponse { try await get("api/v1/jobs/resume") }
    public func updateResumeProfile(selfIntro: String?, strengths: [String], additionalInfo: String?) async throws -> ResumeResponse {
        try await authenticatedPut("api/v1/jobs/resume", body: UpdateResumeProfileRequest(selfIntro: selfIntro, strengths: strengths, additionalInfo: additionalInfo))
    }
    public func addResumeExperience(company: String, role: String, period: String, description: String?) async throws -> ResumeExperienceResponse {
        try await authenticatedPost("api/v1/jobs/resume/experience", body: AddResumeExperienceRequest(company: company, role: role, period: period, description: description))
    }
    public func removeResumeExperience(_ id: String) async throws -> SuccessResponse { try await authenticatedDelete("api/v1/jobs/resume/experience/\(id)") }
    public func addResumeEducation(school: String, degree: String?, major: String?) async throws -> ResumeEducationResponse {
        try await authenticatedPost("api/v1/jobs/resume/education", body: AddResumeEducationRequest(school: school, degree: degree, major: major))
    }
    public func removeResumeEducation(_ id: String) async throws -> SuccessResponse { try await authenticatedDelete("api/v1/jobs/resume/education/\(id)") }
    public func addResumeCertification(name: String, issuedDate: String?) async throws -> ResumeCertificationResponse {
        try await authenticatedPost("api/v1/jobs/resume/certification", body: AddResumeCertificationRequest(name: name, issuedDate: issuedDate))
    }
    public func removeResumeCertification(_ id: String) async throws -> SuccessResponse { try await authenticatedDelete("api/v1/jobs/resume/certification/\(id)") }

    public func getNeighborhoodReviews(_ neighborhood: String) async throws -> NeighborhoodReviewsResponse {
        try await get("api/v1/community/neighborhoods/\(neighborhood)/reviews")
    }
    public func submitNeighborhoodReview(neighborhood: String, residencyYears: Int?, body: String) async throws -> NeighborhoodReviewResponse {
        try await authenticatedPost("api/v1/community/neighborhoods/\(neighborhood)/reviews", body: SubmitNeighborhoodReviewRequest(residencyYears: residencyYears, body: body))
    }

    public func getCommunityTopics() async throws -> CommunityTopicsResponse { try await get("api/v1/community/topics") }
}
