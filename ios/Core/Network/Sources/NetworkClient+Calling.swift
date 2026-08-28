import Foundation

// Real 1:1 voice/video calling (itunda Talk redesign, 2026-08-28) -- backend fully
// shipped, deployed, and live-verified (see rw.itunda.calling.CallService's own doc
// comment). Split into its own extension file from the start, matching
// NetworkClient+Hood.swift/+Maps.swift's own precedent -- NetworkClient.swift itself
// has almost no real headroom left under its own file-size-lint baseline.
//
// This file only covers real call *history* (read-only) -- the actual call-placing
// UI (WebRTC signaling, TURN credentials, CallKit integration) is a deliberately
// separate, later pass; do not add offer/answer/ICE/turn-credentials calls here
// until that pass.

public struct CallSessionDto: Decodable, Identifiable {
    public let id: String
    public let conversationId: String
    public let callerId: String
    public let calleeId: String
    public let callType: String
    public let startedAt: String
    public let answeredAt: String?
    public let endedAt: String?
    public let endReason: String?
}

public struct CallHistoryResponse: Decodable {
    public let success: Bool
    public let calls: [CallSessionDto]
    public let page: Int
    public let size: Int
    public let totalElements: Int
    public let totalPages: Int
}

extension NetworkClient {
    public func getCallHistory() async throws -> CallHistoryResponse { try await get("api/v1/calls/history") }
}
