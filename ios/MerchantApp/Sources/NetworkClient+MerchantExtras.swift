import Foundation

// Real gaps found live (uncalled-endpoint sweep, 2026-08-29/30): both features have
// been fully built on the backend + live on merchant-mfe/Android merchantapp since,
// and both are already displayed to customers via Android/iOS's real place-detail
// Maps tabs, but this iOS merchant app had zero UI for either. New file (not added to
// NetworkClient.swift, which was already at its file-size-lint baseline) matching
// NetworkClient+Payroll.swift's own same-pattern split.
struct SetMerchantPhotoUrlsRequest: Encodable { let photoUrls: [String] }

struct MerchantUpdateDto: Decodable, Identifiable {
    let id: String
    let merchantId: String
    let label: String
    let title: String
    let body: String
    let periodStart: String?
    let periodEnd: String?
    let likeCount: Int
    let createdAt: String
}
struct PostMerchantUpdateRequest: Encodable { let label: String; let title: String; let body: String }
struct MerchantUpdateResponse: Decodable { let success: Bool; let update: MerchantUpdateDto }
struct MerchantUpdatesResponse: Decodable { let success: Bool; let updates: [MerchantUpdateDto] }

extension MerchantNetworkClient {
    func setMerchantPhotoUrls(_ photoUrls: [String]) async throws -> MerchantResponse {
        try await post("api/v1/merchant/photos", body: SetMerchantPhotoUrlsRequest(photoUrls: photoUrls))
    }

    func postMerchantUpdate(label: String, title: String, body: String) async throws -> MerchantUpdateResponse {
        try await post("api/v1/merchant/updates", body: PostMerchantUpdateRequest(label: label, title: title, body: body))
    }

    func getMerchantUpdates(merchantId: String) async throws -> MerchantUpdatesResponse {
        try await get("api/v1/merchant/\(merchantId)/updates")
    }
}
