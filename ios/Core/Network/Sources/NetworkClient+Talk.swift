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
    public init(question: String, options: [String], allowMultiple: Bool = false) {
        self.question = question
        self.options = options
        self.allowMultiple = allowMultiple
    }
}
public struct VoteGroupPollRequest: Encodable { public let optionId: String }

extension NetworkClient {
    public func getGroupAnnouncement(groupId: String) async throws -> GroupAnnouncementResponse {
        try await get("api/v1/messages/groups/\(groupId)/announcement")
    }

    public func postGroupAnnouncement(groupId: String, body: String) async throws -> GroupAnnouncementResponse {
        try await authenticatedPost("api/v1/messages/groups/\(groupId)/announcement", body: PostGroupAnnouncementRequest(body: body))
    }

    public func getGroupPolls(groupId: String) async throws -> GroupPollsResponse {
        try await get("api/v1/messages/groups/\(groupId)/polls")
    }

    public func createGroupPoll(groupId: String, question: String, options: [String], allowMultiple: Bool = false) async throws -> GroupPollResponse {
        try await authenticatedPost("api/v1/messages/groups/\(groupId)/polls", body: CreateGroupPollRequest(question: question, options: options, allowMultiple: allowMultiple))
    }

    public func voteGroupPoll(groupId: String, pollId: String, optionId: String) async throws -> GroupPollResponse {
        try await authenticatedPost("api/v1/messages/groups/\(groupId)/polls/\(pollId)/vote", body: VoteGroupPollRequest(optionId: optionId))
    }
}
