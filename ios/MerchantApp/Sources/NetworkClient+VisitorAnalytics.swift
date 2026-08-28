import Foundation

// Real 비즈프로필 (Karrot Business Profile) visitor-count trend (itunda Hood redesign,
// 2026-08-28) -- see backend MerchantProfileView.kt's own doc comment. A new file
// rather than growing the already-at-baseline NetworkClient.swift; `get`/`post` there
// were widened from `private` to internal (zero line-count change) so this extension
// can reuse them instead of duplicating request-building logic.
struct MerchantProfileViewDto: Decodable, Identifiable {
    let id: String
    let merchantId: String
    let viewDate: String
    let viewCount: Int
}
struct MerchantProfileViewTrendResponse: Decodable { let success: Bool; let trend: [MerchantProfileViewDto] }

extension MerchantNetworkClient {
    func getProfileViewTrend(days: Int = 7) async throws -> MerchantProfileViewTrendResponse {
        try await get("api/v1/merchant/profile-views/trend", query: [URLQueryItem(name: "days", value: String(days))])
    }
}
