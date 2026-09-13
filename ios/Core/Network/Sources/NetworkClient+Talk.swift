import Foundation

// Real itunda Talk (KakaoTalk-parity) redesign, 2026-08-28 -- group announcement/poll,
// service channel, and AI chatbot channel. Backend fully shipped, deployed, and
// live-verified. Split into its own extension file from the start, matching
// NetworkClient+Hood.swift/+Maps.swift's own precedent -- NetworkClient.swift itself
// has almost no real headroom left under its own file-size-lint baseline.

// -- Real group 공지 (announcement) / 투표 (poll) -- see backend
// GroupPollAnnouncementService's own doc comment. Deliberately open to ANY group
// member (no admin/role gate exists in this codebase's group chat).

public struct GroupAnnouncementDto: Decodable, Identifiable {
    public let id: String
    public let groupConversationId: String
    public let createdBy: String
    public let body: String
    public let createdAt: String
}

public struct GroupAnnouncementResponse: Decodable { public let success: Bool; public let announcement: GroupAnnouncementDto? }
public struct PostGroupAnnouncementRequest: Encodable { public let body: String }

public struct GroupPollDto: Decodable, Identifiable {
    public let id: String
    public let groupConversationId: String
    public let createdBy: String
    public let question: String
    public let allowMultiple: Bool
    public let closesAt: String?
    public let createdAt: String
}

public struct GroupPollOptionDto: Decodable, Identifiable {
    public let id: String
    public let pollId: String
    public let text: String
}

public struct GroupPollWithOptionsDto: Decodable, Identifiable {
    public let poll: GroupPollDto
    public let options: [GroupPollOptionDto]
    public let voteCountByOptionId: [String: Int]
    public let myVoteOptionIds: [String]
    public var id: String { poll.id }
}

public struct GroupPollResponse: Decodable { public let success: Bool; public let poll: GroupPollWithOptionsDto }
public struct GroupPollsResponse: Decodable { public let success: Bool; public let polls: [GroupPollWithOptionsDto] }
public struct CreateGroupPollRequest: Encodable {
    public let question: String
    public let options: [String]
    public let allowMultiple: Bool
    public let closesAt: String?
    public init(question: String, options: [String], allowMultiple: Bool = false, closesAt: String? = nil) {
        self.question = question
        self.options = options
        self.allowMultiple = allowMultiple
        self.closesAt = closesAt
    }
}
public struct VoteGroupPollRequest: Encodable { public let optionId: String }

// -- Real itunda service channel -- see backend ServiceChannelService's own doc
// comment. A fully virtual, read-only thread projected from real Notification rows;
// there is deliberately no persisted conversation row for it.
public struct ServiceChannelBubbleDto: Decodable, Identifiable {
    public let id: String
    public let title: String
    public let body: String
    public let type: String
    public let createdAt: String
    public let isRead: Bool
    public let ctaRoute: String?
}

public struct ServiceChannelResponse: Decodable {
    public let success: Bool
    public let bubbles: [ServiceChannelBubbleDto]
    public let page: Int
    public let size: Int
    public let totalElements: Int
    public let totalPages: Int
}

// -- Real AI chatbot channel -- see backend AiChatService's own doc comment. A 429
// with {"success":false,"reason":"busy"} means the single shared self-hosted model
// instance is busy with another user's request right now -- a real, honest signal,
// never silently dropped or retried into a fabricated reply.
public struct AiChatMessageDto: Decodable, Identifiable {
    public let id: String
    public let userId: String
    public let role: String
    public let content: String
    public let createdAt: String
}

public struct AiChatSendResponse: Decodable { public let success: Bool; public let message: AiChatMessageDto; public let reply: AiChatMessageDto }
public struct AiChatHistoryResponse: Decodable {
    public let success: Bool
    public let messages: [AiChatMessageDto]
    public let page: Int
    public let size: Int
    public let totalElements: Int
    public let totalPages: Int
}
public struct SendAiChatMessageRequest: Encodable { public let text: String }

extension NetworkClient {
    // Real pagination-discard fix (2026-09-11, ported from bank-mfe's own fix,
    // d4b2a378 -- see project_itunda_pagination_discard_sweep memory) -- this
    // real Pageable endpoint's page was never sent, silently capping the
    // itunda service-channel thread at the most recent 20 notifications.
    public func getServiceChannel(page: Int = 0, size: Int = 30) async throws -> ServiceChannelResponse {
        try await get("api/v1/talk/service-channel", query: [
            URLQueryItem(name: "page", value: String(page)),
            URLQueryItem(name: "size", value: String(size)),
        ])
    }

    public func sendAiChatMessage(text: String) async throws -> AiChatSendResponse {
        try await authenticatedPost("api/v1/talk/ai-chat/messages", body: SendAiChatMessageRequest(text: text))
    }

    // Real pagination-discard fix (2026-09-11, ported from bank-mfe's own fix,
    // da4cfca4 -- see project_itunda_pagination_discard_sweep memory) -- this
    // real Pageable endpoint's page was never sent, silently capping the AI
    // chat thread's scrollback at the most recent 50 messages.
    public func getAiChatHistory(page: Int = 0, size: Int = 50) async throws -> AiChatHistoryResponse {
        try await get("api/v1/talk/ai-chat/messages", query: [
            URLQueryItem(name: "page", value: String(page)),
            URLQueryItem(name: "size", value: String(size)),
        ])
    }

    public func getGroupAnnouncement(groupId: String) async throws -> GroupAnnouncementResponse {
        try await get("api/v1/messages/groups/\(groupId)/announcement")
    }

    public func postGroupAnnouncement(groupId: String, body: String) async throws -> GroupAnnouncementResponse {
        try await authenticatedPost("api/v1/messages/groups/\(groupId)/announcement", body: PostGroupAnnouncementRequest(body: body))
    }

    public func getGroupPolls(groupId: String) async throws -> GroupPollsResponse {
        try await get("api/v1/messages/groups/\(groupId)/polls")
    }

    public func createGroupPoll(groupId: String, question: String, options: [String], allowMultiple: Bool = false, closesAt: String? = nil) async throws -> GroupPollResponse {
        try await authenticatedPost("api/v1/messages/groups/\(groupId)/polls", body: CreateGroupPollRequest(question: question, options: options, allowMultiple: allowMultiple, closesAt: closesAt))
    }

    public func voteGroupPoll(groupId: String, pollId: String, optionId: String) async throws -> GroupPollResponse {
        try await authenticatedPost("api/v1/messages/groups/\(groupId)/polls/\(pollId)/vote", body: VoteGroupPollRequest(optionId: optionId))
    }

    // Real cross-platform-parity gap found live (2026-09-13) -- web already shows a
    // real numeric badge (capped "99+") on the Messages tab using this exact
    // unbounded aggregate endpoint (BankDashboard.tsx); iOS had neither the endpoint
    // nor any tab-badge state at all.
    public func getUnreadCount() async throws -> UnreadCountResponse {
        try await get("api/v1/messages/unread-count")
    }
}

public struct UnreadCountResponse: Decodable { public let success: Bool; public let conversationsUnread: Int; public let groupsUnread: Int; public let total: Int }
