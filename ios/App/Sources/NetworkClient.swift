import Foundation

// Mirrors services/backend/auth/src/main/kotlin/rw/itunda/auth/AuthDtos.kt exactly --
// same field names/nullability, so JSONDecoder reads the real backend's JSON directly.
struct RegisterRequest: Encodable {
    let phoneNumber: String
    let email: String?
    let firstName: String
    let lastName: String
    let password: String
    let referralCode: String?
    // Added 2026-07-21, same reasoning as LoginRequest's deviceId/deviceName -- the
    // device that registers proves password ownership in the same request, so it's
    // auto-trusted server-side (DeviceService.recordRegistrationDevice) with no
    // separate step-up needed.
    let deviceId: String?
    let deviceName: String?
}

// deviceId/deviceName added 2026-07-21 -- mirrors bank-mfe's real device-binding
// login call exactly. See DeviceStore.swift for how these are generated.
struct LoginRequest: Encodable {
    let phoneNumber: String
    let password: String
    let deviceId: String?
    let deviceName: String?
}

struct RefreshRequest: Encodable {
    let refreshToken: String
}

struct LogoutRequest: Encodable {
    let refreshToken: String?
}

struct PublicUser: Decodable {
    let id: String
    let phoneNumber: String
    let email: String?
    let firstName: String
    let lastName: String
    let kycVerified: Bool
    let creditScore: Int
    let createdAt: String
    // Real hyperlocal neighborhood (2026-07-20) -- see AuthService.setNeighborhood's own
    // doc comment. Set via a real coordinate, reverse-geocoded server-side; never
    // self-declared free text.
    let neighborhood: String?
    let neighborhoodVerifiedAt: String?
    let neighborhoodVerificationCount: Int?
}

struct SetNeighborhoodRequest: Encodable {
    let latitude: Double
    let longitude: Double
}

struct AuthResponse: Decodable {
    let message: String
    let user: PublicUser
    let accessToken: String
    let refreshToken: String
}

enum NetworkError: Error {
    case invalidResponse
    case httpError(statusCode: Int)
    // Real device binding (2026-07-21 port) -- a new, purely additive case rather
    // than widening httpError's own arity (which every existing `catch let
    // NetworkError.httpError(statusCode)` site across this target would need
    // updating for -- exactly the "broader networking-layer change" TalkScreen.swift's
    // own errorMessage doc comment (2026-07-19) already named and deliberately
    // deferred). Thrown only from authenticatedPost's idempotency-keyed path below,
    // mirroring the backend's own DeviceVerificationFilter, which only ever gates
    // requests carrying a real Idempotency-Key header.
    case deviceNotVerified
}

/// Real login/session flow (2026-07-11) -- this app previously had no networking
/// layer at all backing SessionManager/LoginScreen; Core/Network's BffClient
/// protocol (see Core/Network/Sources/BffClient.swift) is unused SDUI scaffolding,
/// not a real HTTP client, same as Android's core/network module before this
/// session's fix lived in :app instead. Plain URLSession, no third-party dependency,
/// matching how minimal the rest of this app's networking surface already is.
final class NetworkClient {
    static let shared = NetworkClient()

    // iOS Simulator shares the host Mac's network directly ("localhost" resolves to
    // the Mac itself), unlike the Android emulator's 10.0.2.2 alias -- no special
    // address needed here. A physical iOS device would need the host's real LAN IP
    // instead (see android/app/build.gradle.kts's apiBaseUrl comment for the Android
    // equivalent of this same problem).
    // static/internal (2026-07-16) so SaroniteBrownfieldModule can reuse the exact same
    // value rather than a second hardcoded literal that could drift out of sync.
    static let baseURLString = "http://localhost:4001/"
    private let baseURL = URL(string: NetworkClient.baseURLString)!
    private let session = URLSession(configuration: .default)

    private lazy var encoder: JSONEncoder = JSONEncoder()
    private lazy var decoder: JSONDecoder = JSONDecoder()

    private init() {}

    func register(_ request: RegisterRequest) async throws -> AuthResponse {
        try await post("api/v1/auth/register", body: request, authToken: nil)
    }

    func login(_ request: LoginRequest) async throws -> AuthResponse {
        try await post("api/v1/auth/login", body: request, authToken: nil)
    }

    func refresh(_ request: RefreshRequest) async throws -> AuthResponse {
        try await post("api/v1/auth/refresh", body: request, authToken: nil)
    }

    func logout(accessToken: String, request: LogoutRequest) async throws {
        _ = try await sendRequest(path: "api/v1/auth/logout", body: request, authToken: accessToken)
    }

    private func post<Body: Encodable, Response: Decodable>(
        _ path: String,
        body: Body,
        authToken: String?
    ) async throws -> Response {
        let data = try await sendRequest(path: path, body: body, authToken: authToken)
        return try decoder.decode(Response.self, from: data)
    }

    @discardableResult
    private func sendRequest<Body: Encodable>(path: String, body: Body, authToken: String?) async throws -> Data {
        var urlRequest = URLRequest(url: baseURL.appendingPathComponent(path))
        urlRequest.httpMethod = "POST"
        urlRequest.setValue("application/json", forHTTPHeaderField: "Content-Type")
        if let authToken {
            urlRequest.setValue("Bearer \(authToken)", forHTTPHeaderField: "Authorization")
        }
        urlRequest.httpBody = try encoder.encode(body)

        let (data, response) = try await session.data(for: urlRequest)
        guard let httpResponse = response as? HTTPURLResponse else { throw NetworkError.invalidResponse }
        guard (200...299).contains(httpResponse.statusCode) else {
            throw NetworkError.httpError(statusCode: httpResponse.statusCode)
        }
        return data
    }
}

// Mirrors services/backend/core/.../domain/Wallet.kt / SavingsGoal.kt / InterestJar.kt
// exactly -- same field names, so JSONDecoder reads the real backend's JSON directly
// (2026-07-11, alongside BankView.swift's real-data wiring; same DTOs Android's
// ApiService.kt just gained).
struct Wallet: Decodable {
    let id: String
    let userId: String
    let accountNumber: String
    let accountName: String
    let type: String
    let balance: Double
    let availableBalance: Double
    let currency: String
    let isActive: Bool
}

struct WalletsResponse: Decodable { let success: Bool; let wallets: [Wallet] }

struct SavingsGoal: Decodable {
    let id: String
    let userId: String
    let walletId: String
    let name: String
    let targetAmount: Double
    let currentAmount: Double
    let monthlyContribution: Double
    let interestRate: Double
    let targetDate: String?
    let category: String
    let status: String
    let color: String
}

struct SavingsGoalsResponse: Decodable { let success: Bool; let goals: [SavingsGoal] }

struct InterestJar: Decodable {
    let userId: String
    let walletId: String
    let balance: Double
    let rate: Double
    let earnedThisMonth: Double
    let earnedTotal: Double
}

struct InterestJarResponse: Decodable { let success: Bool; let jar: InterestJar }

/// Authenticated GET helper for feature screens that need to call the rest of
/// services/backend's API once logged in -- reads the bearer token from
/// KeychainTokenStore so callers never have to thread it through manually. First
/// real use (2026-07-11): BankView.swift's wallet/savings/interest-jar data, closing
/// the "iOS has no real feature data-fetching wired in" gap this comment used to name.
extension NetworkClient {
    func getWallets() async throws -> WalletsResponse { try await get("api/v1/wallet") }
    func getSavingsGoals() async throws -> SavingsGoalsResponse { try await get("api/v1/savings/goals") }
    func getInterestJar() async throws -> InterestJarResponse { try await get("api/v1/savings/interest-jar") }
    func getTransactionHistory() async throws -> TransactionHistoryResponse { try await get("api/v1/wallet/transactions") }
    // Real account settings screen (2026-07-12).
    func getProfile() async throws -> ProfileResponse { try await get("api/v1/auth/profile") }

    // Real hyperlocal neighborhood (2026-07-20) -- a real coordinate in, reverse-geocoded
    // server-side into a real neighborhood/sector name. See AuthService.setNeighborhood
    // and bank-mfe's lib/neighborhood.ts, which this mirrors exactly.
    func setNeighborhood(latitude: Double, longitude: Double) async throws -> ProfileResponse {
        try await authenticatedPost("api/v1/auth/profile/neighborhood", body: SetNeighborhoodRequest(latitude: latitude, longitude: longitude))
    }
    func getNotifications() async throws -> NotificationsResponse { try await get("api/v1/notifications") }

    func markNotificationRead(_ id: String) async throws {
        _ = try await authenticatedPost("api/v1/notifications/\(id)/read", body: EmptyBody()) as MarkReadResponse
    }

    private func get<Response: Decodable>(_ path: String) async throws -> Response {
        var request = URLRequest(url: baseURL.appendingPathComponent(path))
        request.httpMethod = "GET"
        if let token = KeychainTokenStore.shared.getAccessToken() {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        let (data, response) = try await session.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse else { throw NetworkError.invalidResponse }
        guard (200...299).contains(httpResponse.statusCode) else {
            throw NetworkError.httpError(statusCode: httpResponse.statusCode)
        }
        return try decoder.decode(Response.self, from: data)
    }

    /// Real query-param GET (2026-07-19) -- `get(_:)` above uses
    /// `appendingPathComponent`, which percent-encodes `?`/`=`/`&` and breaks a query
    /// string (same gotcha `searchDeliveryAddress` already worked around inline); this
    /// is the reusable version of that same fix for any future query-param endpoint.
    private func get<Response: Decodable>(_ path: String, query: [URLQueryItem]) async throws -> Response {
        var components = URLComponents(url: baseURL.appendingPathComponent(path), resolvingAgainstBaseURL: false)!
        components.queryItems = query.filter { $0.value != nil && !($0.value!.isEmpty) }
        var request = URLRequest(url: components.url!)
        request.httpMethod = "GET"
        if let token = KeychainTokenStore.shared.getAccessToken() {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        let (data, response) = try await session.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse else { throw NetworkError.invalidResponse }
        guard (200...299).contains(httpResponse.statusCode) else {
            throw NetworkError.httpError(statusCode: httpResponse.statusCode)
        }
        return try decoder.decode(Response.self, from: data)
    }

    /// Authenticated POST, with an optional Idempotency-Key -- every money-moving
    /// call below needs one so a retried tap after a timeout replays the original
    /// result instead of double-spending, same contract as Android's equivalent.
    fileprivate func authenticatedPost<Body: Encodable, Response: Decodable>(
        _ path: String,
        body: Body,
        idempotencyKey: String? = nil
    ) async throws -> Response {
        var request = URLRequest(url: baseURL.appendingPathComponent(path))
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        if let token = KeychainTokenStore.shared.getAccessToken() {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        if let idempotencyKey {
            request.setValue(idempotencyKey, forHTTPHeaderField: "Idempotency-Key")
        }
        request.httpBody = try encoder.encode(body)
        let (data, response) = try await session.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse else { throw NetworkError.invalidResponse }
        guard (200...299).contains(httpResponse.statusCode) else {
            // Real device binding (2026-07-21 port) -- only checked when this call
            // actually carries an Idempotency-Key, the same real signal the backend's
            // own DeviceVerificationFilter gates on, so this never misclassifies an
            // unrelated 403 on a non-money-moving call as device-not-verified.
            if idempotencyKey != nil, httpResponse.statusCode == 403,
               let errorBody = try? decoder.decode(ApiErrorBody.self, from: data),
               errorBody.code == "DEVICE_NOT_VERIFIED" {
                throw NetworkError.deviceNotVerified
            }
            throw NetworkError.httpError(statusCode: httpResponse.statusCode)
        }
        return try decoder.decode(Response.self, from: data)
    }
}

private struct ApiErrorBody: Decodable { let code: String? }

// Mirrors services/backend/wallet's WalletController.kt/TransferQuote.kt and
// services/backend/savings's SavingsController.kt exactly (2026-07-12) -- wires
// the real send-money and savings deposit/claim flows, same DTOs Android's
// ApiService.kt just gained.
struct QuoteTransferRequest: Encodable {
    let amount: Double
    let recipient: String
}

struct TransferQuoteDto: Decodable {
    let id: String
    let fromWalletId: String
    let recipient: String
    let amount: Double
    let fee: Double
    let totalDebit: Double
    let currency: String
    let expiresAt: String
}

struct QuoteTransferResponse: Decodable { let success: Bool; let quote: TransferQuoteDto }

struct ConfirmTransferRequest: Encodable { let quoteId: String }

struct TransactionDto: Decodable {
    let id: String
    let senderId: String
    let recipientId: String
    let type: String
    let amount: Double
    let fee: Double
    let currency: String
    let status: String
    let description: String
    let createdAt: String
}

struct TransactionHistoryResponse: Decodable { let success: Bool; let transactions: [TransactionDto] }

struct ConfirmTransferResponse: Decodable {
    let success: Bool
    let message: String
    let transaction: TransactionDto
    let newBalance: Double
}

// Real direct itunda-to-itunda push-transfer (rw.itunda.p2p, 2026-07-20) -- mirrors
// P2pController's real SendDirectP2pRequest exactly, same as Android's ApiService.kt.
// Deliberately distinct from QuoteTransferRequest/ConfirmTransferRequest above: those
// always route through a simulated external rail and never actually credit another
// itunda user's wallet, even when the recipient is a real itunda account (confirmed via
// a direct MySQL check while building the real fix on the backend one day earlier). This
// is the real one -- no quote step needed, since there's no external rail decision to
// quote.
struct SendDirectP2pRequest: Encodable { let recipient: String; let amount: Double; let description: String }
struct SendDirectP2pResponse: Decodable {
    let success: Bool
    let message: String
    let transaction: TransactionDto
    let newBalance: Double
}

struct DepositRequest: Encodable { let goalId: String; let amount: Double }
struct DepositResponse: Decodable { let success: Bool; let message: String; let goal: SavingsGoal }
struct ClaimInterestResponse: Decodable { let success: Bool; let message: String }

// Real offline-action-queue replay (2026-07-13) -- mirrors
// services/backend/offline/src/main/kotlin/rw/itunda/offline/web/ActionsBatchController.kt
// exactly. See OfflineActionQueue.swift for the local persisted queue this replays.
// Scoped to SAVINGS_DEPOSIT's real body shape rather than a generic [String: Any]
// body -- see OfflineActionQueue.swift's header for why that's the right scope here.
struct SavingsDepositActionBody: Encodable { let goalId: String; let amount: Double }

struct BatchActionRequest: Encodable {
    let clientActionId: String
    let type: String
    let idempotencyKey: String
    let body: SavingsDepositActionBody
}

struct BatchRequest: Encodable { let actions: [BatchActionRequest] }

struct BatchActionResultDto: Decodable {
    let clientActionId: String
    let type: String
    let status: Int
}

struct BatchResponse: Decodable { let success: Bool; let results: [BatchActionResultDto] }

// Mirrors services/backend/auth's AuthController.kt / notifications's
// NotificationController.kt (2026-07-12) -- backs the new Settings screen.
struct ProfileResponse: Decodable { let success: Bool; let user: PublicUser }

struct NotificationDto: Decodable, Identifiable {
    let id: String
    let userId: String
    let type: String
    let title: String
    let body: String
    let isRead: Bool
    let createdAt: String
}

struct NotificationsResponse: Decodable { let success: Bool; let notifications: [NotificationDto]; let unreadCount: Int }
struct MarkReadResponse: Decodable { let success: Bool }

// Real device binding (2026-07-21 port) -- mirrors bank-mfe's lib/device.ts /
// Android's TrustedDeviceDto exactly (same real endpoints, same shapes). See
// AuthController.kt on the backend for the real contract: verify always re-verifies
// the CURRENT device (resolved server-side from the caller's own JWT deviceId claim,
// never a client-supplied one), so no id is passed in VerifyDeviceRequest.
struct TrustedDeviceDto: Decodable, Identifiable {
    let id: String
    let userId: String
    let deviceId: String
    let deviceName: String?
    let trusted: Bool
    let firstSeenAt: String
    let lastSeenAt: String
    let verifiedAt: String?
}
struct DevicesResponse: Decodable { let success: Bool; let devices: [TrustedDeviceDto] }
struct VerifyDeviceRequest: Encodable { let password: String }
struct VerifyDeviceResponse: Decodable { let success: Bool; let device: TrustedDeviceDto }
struct RevokeDeviceResponse: Decodable { let success: Bool }

extension NetworkClient {
    func getMyDevices() async throws -> DevicesResponse { try await get("api/v1/auth/devices") }

    func verifyDevice(password: String) async throws -> VerifyDeviceResponse {
        try await authenticatedPost("api/v1/auth/devices/verify", body: VerifyDeviceRequest(password: password))
    }

    func revokeDevice(deviceId: String) async throws -> RevokeDeviceResponse {
        try await authenticatedDelete("api/v1/auth/devices/\(deviceId)")
    }
}

extension NetworkClient {
    func quoteTransfer(amount: Double, recipient: String) async throws -> QuoteTransferResponse {
        try await authenticatedPost("api/v1/wallet/transfer/quote", body: QuoteTransferRequest(amount: amount, recipient: recipient))
    }

    func confirmTransfer(quoteId: String) async throws -> ConfirmTransferResponse {
        try await authenticatedPost(
            "api/v1/wallet/transfer/confirm",
            body: ConfirmTransferRequest(quoteId: quoteId),
            idempotencyKey: UUID().uuidString
        )
    }

    // Real direct P2P push-transfer (2026-07-20) -- see SendDirectP2pRequest's own doc
    // comment for why this replaces quoteTransfer/confirmTransfer above in
    // TransferViewModel.sendTransfer.
    func sendDirect(recipient: String, amount: Double) async throws -> SendDirectP2pResponse {
        try await authenticatedPost(
            "api/v1/p2p/send",
            body: SendDirectP2pRequest(recipient: recipient, amount: amount, description: ""),
            idempotencyKey: UUID().uuidString
        )
    }

    func depositToGoal(goalId: String, amount: Double) async throws -> DepositResponse {
        try await authenticatedPost(
            "api/v1/savings/deposit",
            body: DepositRequest(goalId: goalId, amount: amount),
            idempotencyKey: UUID().uuidString
        )
    }

    func claimInterest() async throws -> ClaimInterestResponse {
        try await authenticatedPost(
            "api/v1/savings/interest-jar/claim",
            body: EmptyBody(),
            idempotencyKey: UUID().uuidString
        )
    }

    // Real offline-action-queue replay (2026-07-13) -- see OfflineActionQueue.swift.
    // No Idempotency-Key header on the batch call itself -- each individual action
    // inside it carries its own, exactly like the backend controller expects.
    func submitActionBatch(_ request: BatchRequest) async throws -> BatchResponse {
        try await authenticatedPost("api/v1/actions/batch", body: request)
    }
}

private struct EmptyBody: Encodable {}

// MARK: - Messaging / Marketplace / Commerce (2026-07-18)
//
// Mirrors android/app/.../network/ApiService.kt's real DTOs exactly, field-for-field --
// the same three new backend modules (rw.itunda.messaging/marketplace/commerce) that
// Android's Home/Shop/Hood/Talk/My nav redesign wired up. See that file's own header
// comment for the full backend account (real pagination, real IDOR protection, honest
// "poll-based delivery"/"self-declared fulfillment" scope).

struct ConversationDto: Decodable {
    let id: String
    let participantAId: String
    let participantBId: String
    let lastMessageAt: String
    let createdAt: String
}

struct ConversationSummaryDto: Decodable, Identifiable {
    let conversationId: String
    let otherUserId: String
    let otherUserName: String
    let lastMessageAt: String
    let lastMessagePreview: String?
    let unreadCount: Int
    var id: String { conversationId }
}

// Real emoji reactions (2026-07-19) -- see MessagingService.toggleReaction's own doc
// comment for the real toggle semantics (tapping an active reaction removes it).
struct ReactionGroupDto: Decodable { let emoji: String; let userIds: [String] }

struct MessageDto: Decodable, Identifiable {
    let id: String
    let conversationId: String
    let senderId: String
    let body: String
    let sentAt: String
    let readAt: String?
    let reactions: [ReactionGroupDto]

    // A custom init(from:) below suppresses Swift's automatic memberwise initializer,
    // so this is needed explicitly for real call sites that construct a MessageDto
    // directly (e.g. applying a real-time reaction push to already-loaded state).
    init(id: String, conversationId: String, senderId: String, body: String, sentAt: String, readAt: String?, reactions: [ReactionGroupDto]) {
        self.id = id
        self.conversationId = conversationId
        self.senderId = senderId
        self.body = body
        self.sentAt = sentAt
        self.readAt = readAt
        self.reactions = reactions
    }

    // Custom decode: the real-time WebSocket push for a brand-new message omits
    // `reactions` entirely (a message can't have a reaction the instant it's sent) --
    // defaults to empty rather than failing to decode the whole push.
    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decode(String.self, forKey: .id)
        conversationId = try container.decode(String.self, forKey: .conversationId)
        senderId = try container.decode(String.self, forKey: .senderId)
        body = try container.decode(String.self, forKey: .body)
        sentAt = try container.decode(String.self, forKey: .sentAt)
        readAt = try container.decodeIfPresent(String.self, forKey: .readAt)
        reactions = try container.decodeIfPresent([ReactionGroupDto].self, forKey: .reactions) ?? []
    }

    private enum CodingKeys: String, CodingKey { case id, conversationId, senderId, body, sentAt, readAt, reactions }
}

// Real WebSocket push envelopes (2026-07-18) -- see
// NetworkClient.connectMessagingSocket's own doc comment.
struct MessagingSocketTypeEnvelope: Decodable { let type: String }
struct MessagingSocketMessageEnvelope: Decodable { let type: String; let conversationId: String; let message: MessageDto }

// Real online/offline presence push (2026-07-19) -- see
// rw.itunda.core.realtime.RealtimeMessagePublisher.publishPresenceChange's own doc
// comment for the real transition-only/1:1-only scoping.
struct MessagingSocketPresenceEnvelope: Decodable { let type: String; let userId: String; let online: Bool }

// Real typing indicator push envelope (2026-07-19) -- see MessagingSocketPush's own doc
// comment.
struct MessagingSocketTypingEnvelope: Decodable {
    let type: String
    let conversationId: String?
    let groupConversationId: String?
    let userId: String
}

// Real live reaction push (2026-07-19) -- see
// MessagingWebSocketHandler.publishReactionChange/publishGroupReactionChange's own
// doc comments. Exactly one of conversationId/groupConversationId is set.
struct MessagingSocketReactionEnvelope: Decodable {
    let type: String
    let conversationId: String?
    let groupConversationId: String?
    let messageId: String
    let reactions: [ReactionGroupDto]
}

struct StartConversationRequest: Encodable {
    let phoneNumber: String?
    let otherUserId: String?
}

struct SendMessageRequest: Encodable { let body: String }
struct TalkContactDto: Decodable, Identifiable { let userId: String; let name: String; var id: String { userId } }
struct TalkContactsResponse: Decodable { let success: Bool; let contacts: [TalkContactDto] }
struct CreateChatReportRequest: Encodable { let messageId: String; let reason: String }
struct EmptyRequest: Encodable {}
struct ToggleReactionRequest: Encodable { let emoji: String }
struct ReactionsResponse: Decodable { let success: Bool; let reactions: [ReactionGroupDto] }

struct ConversationResponse: Decodable { let success: Bool; let conversation: ConversationDto }
struct ConversationsResponse: Decodable { let success: Bool; let conversations: [ConversationSummaryDto] }
struct MessagesResponse: Decodable { let success: Bool; let messages: [MessageDto] }
struct MessageResponse: Decodable { let success: Bool; let message: MessageDto }

// Real group chat (2026-07-18) -- see rw.itunda.messaging.web.GroupMessagingController.
// memberPhoneNumbers is the real human-friendly entry point (same reasoning as
// StartConversationRequest.phoneNumber).
struct CreateGroupRequest: Encodable { let name: String; let memberPhoneNumbers: [String] }
struct SendGroupMessageRequest: Encodable { let body: String }

struct GroupSummaryDto: Decodable, Identifiable {
    let groupId: String
    let name: String
    let memberCount: Int
    let lastMessageAt: String
    let lastMessagePreview: String?
    let unreadCount: Int
    var id: String { groupId }
}
struct GroupMessageDto: Decodable, Identifiable {
    let id: String
    let groupConversationId: String
    let senderId: String
    let body: String
    let sentAt: String
    let reactions: [ReactionGroupDto]

    // Explicit memberwise init -- see MessageDto's own identical note on why this is
    // needed once a custom init(from:) is present.
    init(id: String, groupConversationId: String, senderId: String, body: String, sentAt: String, reactions: [ReactionGroupDto]) {
        self.id = id
        self.groupConversationId = groupConversationId
        self.senderId = senderId
        self.body = body
        self.sentAt = sentAt
        self.reactions = reactions
    }

    // Same real-time-push-omits-reactions handling as MessageDto's own custom decode.
    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decode(String.self, forKey: .id)
        groupConversationId = try container.decode(String.self, forKey: .groupConversationId)
        senderId = try container.decode(String.self, forKey: .senderId)
        body = try container.decode(String.self, forKey: .body)
        sentAt = try container.decode(String.self, forKey: .sentAt)
        reactions = try container.decodeIfPresent([ReactionGroupDto].self, forKey: .reactions) ?? []
    }

    private enum CodingKeys: String, CodingKey { case id, groupConversationId, senderId, body, sentAt, reactions }
}
struct GroupResponse: Decodable { let success: Bool; let group: GroupSummaryDto }
struct GroupsResponse: Decodable { let success: Bool; let groups: [GroupSummaryDto] }
struct GroupMessagesResponse: Decodable { let success: Bool; let messages: [GroupMessageDto] }
struct GroupMessageResponse: Decodable { let success: Bool; let message: GroupMessageDto }
struct LeaveGroupResponse: Decodable { let success: Bool }

// Real member list with real resolved display names (2026-07-18) -- closes the honest,
// named limitation this UI carried since group chat first shipped: message bubbles
// showing a truncated sender id instead of a real name.
struct GroupMemberDto: Decodable, Identifiable { let userId: String; let name: String; var id: String { userId } }
struct GroupMembersResponse: Decodable { let success: Bool; let members: [GroupMemberDto] }

// Real online/offline presence (2026-07-19) -- see MessagingService.getPresence's own
// doc comment on the backend.
struct PresenceResponse: Decodable { let success: Bool; let presence: [String: Bool] }

// Real WebSocket push envelopes for group chat (2026-07-18).
struct MessagingSocketGroupEnvelope: Decodable { let type: String; let groupConversationId: String; let message: GroupMessageDto }

enum MessagingSocketPush {
    case directMessage(conversationId: String, message: MessageDto)
    case groupMessage(groupConversationId: String, message: GroupMessageDto)
    case presenceChange(userId: String, online: Bool)
    // Real typing indicator (2026-07-19) -- see
    // MessagingWebSocketHandler.handleTextMessage's own doc comment on the backend.
    // Ephemeral, never persisted; server-ratelimited to one relay per (user,
    // conversation) per 2s. Exactly one of conversationId/groupConversationId is set.
    case typingChange(conversationId: String?, groupConversationId: String?, userId: String)
    case reactionChange(conversationId: String?, groupConversationId: String?, messageId: String, reactions: [ReactionGroupDto])
}

struct ListingDto: Decodable, Identifiable {
    let id: String
    let sellerId: String
    let title: String
    let description: String
    let price: Double
    let category: String
    let status: String
    let createdAt: String
    // Real optional seller-set location (2026-07-18 backend) -- backs real proximity
    // search and, 2026-07-19, "Directions to this seller".
    let latitude: Double?
    let longitude: Double?
    let meetingPlace: String?
}

struct CreateListingRequest: Encodable {
    let title: String
    let description: String
    let price: Double
    let category: String
    let latitude: Double?
    let longitude: Double?
    let meetingPlace: String?
}

struct ListingResponse: Decodable { let success: Bool; let listing: ListingDto }
struct ListingsResponse: Decodable { let success: Bool; let listings: [ListingDto] }

// Real Marketplace listing wishlist (2026-07-21 backend + bank-mfe, ported here) --
// mirrors FavoriteRestaurantDto's exact shape; see ListingFavoriteService.kt's own doc
// comment on the backend for why add/remove are both idempotent.
struct FavoriteListingDto: Decodable, Identifiable {
    let listingId: String
    let title: String
    let price: Double
    let category: String
    let favoritedAt: String
    var id: String { listingId }
}
struct FavoriteListingsResponse: Decodable { let success: Bool; let favorites: [FavoriteListingDto] }

struct FavoriteJobPostDto: Decodable, Identifiable {
    let jobPostId: String
    let title: String
    let payAmount: Double
    let category: String
    let favoritedAt: String
    var id: String { jobPostId }
}
struct FavoriteJobPostsResponse: Decodable { let success: Bool; let favorites: [FavoriteJobPostDto] }

// Real KakaoTalk-style "선물하기" money gift (2026-07-20) -- see GiftService's own doc
// comment. Money leaves the sender's wallet into a real escrow account the moment a
// gift is sent, and only reaches the recipient's wallet once they explicitly claim it
// (or is auto-refunded after 7 days). Rendered inline as a gift bubble, same "special
// message body" convention PriceOfferDto already established.
struct GiftDto: Decodable, Identifiable {
    let id: String
    let senderId: String
    let recipientId: String
    let conversationId: String
    let messageId: String
    let amount: Double
    let note: String?
    let status: String
    let holdTransactionId: String
    let claimTransactionId: String?
    let expiresAt: String
    let claimedAt: String?
    let createdAt: String
}
struct SendGiftInConversationRequest: Encodable { let amount: Double; let note: String? }
struct GiftResponse: Decodable { let success: Bool; let gift: GiftDto }
struct GiftsResponse: Decodable { let success: Bool; let gifts: [GiftDto] }

// Real 동네생활-style community board (2026-07-19) -- see rw.itunda.community.web.CommunityController.
struct CommunityCategoryDto: Decodable, Identifiable { let id: String; let label: String }
struct CommunityPostDto: Decodable, Identifiable {
    let id: String
    let authorId: String
    let category: String
    let title: String
    let body: String
    let status: String
    let likeCount: Int
    let commentCount: Int
    let createdAt: String
    let latitude: Double?
    let longitude: Double?
}
struct CreateCommunityPostRequest: Encodable {
    let category: String; let title: String; let body: String
    let latitude: Double?; let longitude: Double?
}
struct CommunityPostResponse: Decodable { let success: Bool; let post: CommunityPostDto }
struct CommunityPostsResponse: Decodable { let success: Bool; let posts: [CommunityPostDto] }
struct CommunityCategoriesResponse: Decodable { let success: Bool; let categories: [CommunityCategoryDto] }
struct CommunityPostDetailResponse: Decodable { let success: Bool; let post: CommunityPostDto; let authorName: String; let likedByMe: Bool }
struct CommunityCommentDto: Decodable, Identifiable { let id: String; let postId: String; let authorId: String; let body: String; let createdAt: String }
struct CommunityCommentWithAuthorDto: Decodable, Identifiable {
    let comment: CommunityCommentDto
    let authorName: String
    var id: String { comment.id }
}
struct CommunityCommentsResponse: Decodable { let success: Bool; let comments: [CommunityCommentWithAuthorDto] }
struct AddCommunityCommentRequest: Encodable { let body: String }
struct CommunityCommentResponse: Decodable { let success: Bool; let comment: CommunityCommentDto }
struct ToggleCommunityLikeResponse: Decodable { let success: Bool; let liked: Bool }

// Real 당근알바-style local job board (2026-07-19) -- see rw.itunda.jobs.web.JobPostController.
struct JobCategoryDto: Decodable, Identifiable { let id: String; let label: String }
struct JobPostDto: Decodable, Identifiable {
    let id: String
    let posterId: String
    let category: String
    let title: String
    let description: String
    let payType: String
    let payAmount: Double
    let status: String
    let createdAt: String
    let latitude: Double?
    let longitude: Double?
}
struct CreateJobPostRequest: Encodable {
    let category: String; let title: String; let description: String; let payType: String; let payAmount: Double
    let latitude: Double?; let longitude: Double?
}
struct JobPostResponse: Decodable { let success: Bool; let post: JobPostDto }
struct JobPostsResponse: Decodable { let success: Bool; let posts: [JobPostDto] }
struct JobCategoriesResponse: Decodable { let success: Bool; let categories: [JobCategoryDto] }
struct ContactPosterResponse: Decodable { let success: Bool; let conversation: ConversationDto }
struct CreateHoodReportRequest: Encodable { let targetType: String; let targetId: String; let reason: String }
struct HoodReportResponse: Decodable { let success: Bool }

// Real 당근부동산-style property listing (2026-07-19) -- see rw.itunda.realestate.web.PropertyListingController.
struct PropertyTypeDto: Decodable, Identifiable { let id: String; let label: String }
struct PropertyListingDto: Decodable, Identifiable {
    let id: String
    let listerId: String
    let listingType: String
    let propertyType: String
    let title: String
    let description: String
    let price: Double
    let bedrooms: Int?
    let sizeSqm: Double?
    let status: String
    let createdAt: String
    let latitude: Double?
    let longitude: Double?
}
struct CreatePropertyListingRequest: Encodable {
    let listingType: String; let propertyType: String; let title: String; let description: String; let price: Double
    let bedrooms: Int?; let sizeSqm: Double?; let latitude: Double?; let longitude: Double?
}
struct PropertyListingResponse: Decodable { let success: Bool; let listing: PropertyListingDto }
struct PropertyListingsResponse: Decodable { let success: Bool; let listings: [PropertyListingDto] }
struct FavoritePropertyListingDto: Decodable, Identifiable { let propertyListingId: String; let title: String; let price: Double; let listingType: String; let favoritedAt: String; var id: String { propertyListingId } }
struct FavoritePropertyListingsResponse: Decodable { let success: Bool; let favorites: [FavoritePropertyListingDto] }
struct PropertyTypesResponse: Decodable { let success: Bool; let propertyTypes: [PropertyTypeDto] }
struct ContactListerResponse: Decodable { let success: Bool; let conversation: ConversationDto }
struct ContactSellerResponse: Decodable { let success: Bool; let conversation: ConversationDto }

// Real 당근-style price-offer negotiation (2026-07-19) -- see PriceOfferService's own
// doc comment. Each offer/counter/accept/reject is a real message in the same real
// conversation contactSeller establishes, rendered inline as an offer bubble.
struct PriceOfferDto: Decodable, Identifiable {
    let id: String
    let listingId: String
    let messageId: String
    let conversationId: String
    let buyerId: String
    let sellerId: String
    let proposedByUserId: String
    let amount: Double
    let status: String
    let createdAt: String
    let respondedAt: String?
}
struct MakeOfferRequest: Encodable { let amount: Double }
struct RespondToOfferRequest: Encodable { let action: String; let counterAmount: Double? }
struct PriceOfferResponse: Decodable { let success: Bool; let offer: PriceOfferDto }
struct PriceOffersResponse: Decodable { let success: Bool; let offers: [PriceOfferDto] }

// Real 당근-style price-offer negotiation on a real property listing (2026-07-19) -- see
// PropertyPriceOfferService's own doc comment. Mirrors PriceOfferDto field-for-field.
struct PropertyPriceOfferDto: Decodable, Identifiable {
    let id: String
    let propertyListingId: String
    let messageId: String
    let conversationId: String
    let inquirerId: String
    let listerId: String
    let proposedByUserId: String
    let amount: Double
    let status: String
    let createdAt: String
    let respondedAt: String?
}
struct MakePropertyOfferRequest: Encodable { let amount: Double }
struct RespondToPropertyOfferRequest: Encodable { let action: String; let counterAmount: Double? }
struct PropertyPriceOfferResponse: Decodable { let success: Bool; let offer: PropertyPriceOfferDto }
struct PropertyPriceOffersResponse: Decodable { let success: Bool; let offers: [PropertyPriceOfferDto] }

struct ShoppingMerchantDto: Decodable, Identifiable {
    let merchantId: String
    let businessName: String
    let category: String?
    let cashbackRate: String
    // Real optional location (2026-07-19) -- backs the real self-hosted Map view.
    let latitude: Double?
    let longitude: Double?
    var id: String { merchantId }

    init(merchantId: String, businessName: String, category: String?, cashbackRate: String, latitude: Double? = nil, longitude: Double? = nil) {
        self.merchantId = merchantId
        self.businessName = businessName
        self.category = category
        self.cashbackRate = cashbackRate
        self.latitude = latitude
        self.longitude = longitude
    }
}
struct ShoppingMerchantsResponse: Decodable { let success: Bool; let merchants: [ShoppingMerchantDto] }

// Real "search this map" + "directions" (2026-07-19) -- see rw.itunda.maps.MapsService's
// own doc comment on the backend for why these are a new, general-purpose front door
// onto itunda's already-deployed self-hosted Nominatim/OSRM.
// Codable, not just Decodable (2026-07-22) -- RecentMapSearchesStore needs to encode
// this back to JSON for local UserDefaults persistence, not just decode it from the API.
struct PlaceSearchResultDto: Codable { let displayName: String; let latitude: Double; let longitude: Double }
struct MapsSearchResponse: Decodable { let success: Bool; let results: [PlaceSearchResultDto] }
struct RouteStepDto: Decodable { let instruction: String; let distanceMeters: Double; let streetName: String? }
struct RouteResultDto: Decodable { let distanceKm: Double; let durationMinutes: Double; let geometry: [[Double]]; let steps: [RouteStepDto] }
struct MapsDirectionsResponse: Decodable { let success: Bool; let route: RouteResultDto }
struct ItineraryWaypointRequest: Encodable { let latitude: Double; let longitude: Double }
struct ItineraryDirectionsRequest: Encodable { let waypoints: [ItineraryWaypointRequest]; let mode: String }
// Real alternative routes (2026-07-22) -- see OsrmRoutingClient.routeAlternatives' own
// doc comment on the backend. Often just a single-element array -- OSRM itself decides
// whether a real alternative exists for a given trip.
struct MapsDirectionsAlternativesResponse: Decodable { let success: Bool; let routes: [RouteResultDto] }
struct MerchantCategoriesResponse: Decodable { let success: Bool; let categories: [String] }

// Real "nearby places" category search + bookmarked/favorite places (2026-07-19) -- see
// rw.itunda.maps.MapsService's own doc comment on the backend. `mapNearbyCategories`
// mirrors bank-mfe's own hardcoded `NEARBY_CATEGORIES` list exactly.
struct NearbyPlaceDto: Decodable { let displayName: String; let latitude: Double; let longitude: Double; let distanceKm: Double }
struct MapNearbyResponse: Decodable { let success: Bool; let places: [NearbyPlaceDto] }
// folderName/color added 2026-07-22 -- see MapBookmark.kt's own doc comment on the
// backend (migration V73). Every bookmark belongs to exactly one named folder with its
// own pin color; a bookmark saved before this existed defaults into "Saved places" /
// "#F5A623" (the same star-yellow the ★ icon already used).
struct MapBookmarkDto: Decodable, Identifiable { let id: String; let displayName: String; let latitude: Double; let longitude: Double; let folderName: String; let color: String; let createdAt: String }
struct MapBookmarksResponse: Decodable { let success: Bool; let bookmarks: [MapBookmarkDto] }
struct AddMapBookmarkRequest: Encodable { let displayName: String; let latitude: Double; let longitude: Double; let folderName: String?; let color: String? }
struct AddMapBookmarkResponse: Decodable { let success: Bool; let bookmark: MapBookmarkDto }
// Real "move to folder" (2026-07-22) -- see MapsService.moveBookmark's own doc comment.
struct MoveMapBookmarkRequest: Encodable { let folderName: String; let color: String }
struct MoveMapBookmarkResponse: Decodable { let success: Bool; let bookmark: MapBookmarkDto }

struct MapPlaceCategory: Identifiable { let id: String; let label: String }
let mapNearbyCategories: [MapPlaceCategory] = [
    MapPlaceCategory(id: "RESTAURANT", label: "Restaurants"),
    MapPlaceCategory(id: "CAFE", label: "Cafes"),
    MapPlaceCategory(id: "HOSPITAL", label: "Hospitals"),
    MapPlaceCategory(id: "PHARMACY", label: "Pharmacies"),
    MapPlaceCategory(id: "BANK", label: "Banks"),
    MapPlaceCategory(id: "ATM", label: "ATMs"),
    MapPlaceCategory(id: "HOTEL", label: "Hotels"),
    MapPlaceCategory(id: "SUPERMARKET", label: "Supermarkets"),
    MapPlaceCategory(id: "GAS_STATION", label: "Gas stations"),
    MapPlaceCategory(id: "SCHOOL", label: "Schools"),
]

// imageUrl/originalPrice/discountPercent added 2026-07-21, closing
// docs/DESIGN_REFERENCES.md Section 5 recommendation #4 -- see backend
// MerchantProduct.kt's own doc comment for the full account (merchant-supplied external
// URL, no upload/storage layer; discountPercent is server-computed, never client-set).
struct MerchantProductDto: Decodable, Identifiable {
    let id: String
    let merchantId: String
    let name: String
    let price: Double
    let active: Bool
    let createdAt: String
    let imageUrl: String?
    let originalPrice: Double?
    let discountPercent: Int?
}
struct MerchantSummaryDto: Decodable { let id: String; let businessName: String }
struct MerchantProductsResponse: Decodable { let success: Bool; let merchant: MerchantSummaryDto; let products: [MerchantProductDto] }

struct OrderItemRequest: Encodable { let productId: String; let quantity: Int }
struct PlaceOrderRequest: Encodable {
    let merchantId: String
    let items: [OrderItemRequest]
    let deliveryAddress: String
}

struct OrderDto: Decodable, Identifiable {
    let id: String
    let buyerId: String
    let merchantId: String
    let deliveryAddress: String
    let totalAmount: Double
    let fee: Double
    let transactionId: String
    let status: String
    let createdAt: String
    let updatedAt: String
    let refundTransactionId: String?
}
struct OrderItemDto: Decodable, Identifiable { let id: String; let orderId: String; let productId: String; let productName: String; let unitPrice: Double; let quantity: Int }
struct OrderDetailResponse: Decodable { let success: Bool; let order: OrderDto; let items: [OrderItemDto] }
struct OrdersResponse: Decodable { let success: Bool; let orders: [OrderDto] }

// Real post-delivery product reviews (2026-07-20) -- see ProductReviewService's own doc
// comment, mirroring SubmitEatsReviewRequest/EatsReviewDto below but keyed to one order
// line item rather than the whole order (a Commerce order can carry several different
// products from one merchant, and real Coupang reviews are per-product).
struct SubmitProductReviewRequest: Encodable { let rating: Int; let comment: String? }
struct ProductReviewDto: Decodable {
    let id: String
    let orderItemId: String
    let orderId: String
    let buyerId: String
    let productId: String
    let merchantId: String
    let rating: Int
    let comment: String?
    let createdAt: String
}
struct ProductReviewResponse: Decodable { let success: Bool; let review: ProductReviewDto }
struct ProductReviewsResponse: Decodable { let success: Bool; let reviews: [ProductReviewDto] }
struct ProductRatingResponse: Decodable { let success: Bool; let average: Double?; let count: Int }

/// Mirrors services/backend/eats's real DTOs exactly (2026-07-18) -- restaurant/menu
/// browsing reuses ShoppingMerchantDto/MerchantProductDto above (a restaurant IS a
/// Merchant, a menu item IS a MerchantProduct -- see rw.itunda.eats.EatsOrderService's
/// own doc comment).
struct EatsOrderItemRequest: Encodable { let menuItemId: String; let quantity: Int }
struct PlaceEatsOrderRequest: Encodable {
    let restaurantId: String
    let items: [EatsOrderItemRequest]
    let deliveryAddress: String
    let deliveryLatitude: Double?
    let deliveryLongitude: Double?
    let deliveryNotes: String?
}
struct UpdateEatsOrderStatusRequest: Encodable { let status: String }
struct SetRiderAvailabilityRequest: Encodable { let available: Bool }

// Real self-hosted address-search autocomplete (2026-07-18) -- backed by itunda's own
// Nominatim geocoder, not a third-party Maps API. See
// EatsOrderService.searchDeliveryAddress's own doc comment.
struct AddressSuggestionDto: Decodable, Identifiable {
    var id: String { displayName }
    let displayName: String
    let latitude: Double
    let longitude: Double
}
struct AddressSearchResponse: Decodable { let success: Bool; let suggestions: [AddressSuggestionDto] }

// Real post-delivery ratings & reviews (2026-07-18) -- see EatsReviewService's own doc
// comment. Ported from bank-mfe's own review UI, the template for this iOS version.
struct SubmitEatsReviewRequest: Encodable {
    let restaurantRating: Int
    let restaurantComment: String?
    let riderRating: Int
    let riderComment: String?
}
struct EatsReviewDto: Decodable {
    let id: String
    let orderId: String
    let buyerId: String
    let restaurantId: String
    let riderId: String
    let restaurantRating: Int
    let restaurantComment: String?
    let riderRating: Int
    let riderComment: String?
    let createdAt: String
}
struct EatsReviewResponse: Decodable { let success: Bool; let review: EatsReviewDto }
struct EatsRatingResponse: Decodable { let success: Bool; let average: Double?; let count: Int }

struct EatsOrderDto: Decodable, Identifiable {
    let id: String
    let buyerId: String
    let restaurantId: String
    let riderId: String?
    let deliveryAddress: String
    let itemsSubtotal: Double
    let deliveryFee: Double
    let platformFee: Double
    let totalAmount: Double
    let transactionId: String
    let deliveryPayoutTransactionId: String?
    let status: String
    let createdAt: String
    let updatedAt: String
    let refundTransactionId: String?
    let deliveryLatitude: Double?
    let deliveryLongitude: Double?
    let distanceKm: Double?
    let deliveryNotes: String?
}
struct EatsOrderItemDto: Decodable, Identifiable { let id: String; let orderId: String; let productId: String; let productName: String; let unitPrice: Double; let quantity: Int }
struct EatsOrderDetailResponse: Decodable { let success: Bool; let order: EatsOrderDto; let items: [EatsOrderItemDto] }
struct EatsOrdersResponse: Decodable { let success: Bool; let orders: [EatsOrderDto] }

struct RiderDto: Decodable, Identifiable { let id: String; let userId: String; let walletId: String; let status: String; let available: Bool; let createdAt: String }
struct RiderResponse: Decodable { let success: Bool; let rider: RiderDto }

// Real bookmarked/favorited restaurants (2026-07-19) -- add/remove are both idempotent
// on the backend, see EatsFavoriteService.kt's own doc comment.
struct FavoriteRestaurantDto: Decodable, Identifiable {
    let restaurantId: String
    let businessName: String
    let category: String?
    let favoritedAt: String
    var id: String { restaurantId }
}
struct FavoriteRestaurantsResponse: Decodable { let success: Bool; let favorites: [FavoriteRestaurantDto] }
struct SuccessResponse: Decodable { let success: Bool }

// Real Toss Securities-style stock investing (2026-07-20) -- the first iOS UI this
// feature has ever had, ported from bank-mfe/Android the same session. Day-over-day
// movement/history are real deterministic simulations, not live RSE data -- see the
// backend's StockCatalog.kt for the full account.
struct StockDto: Decodable, Identifiable {
    let id: String
    let symbol: String
    let name: String
    let price: Double
    let change: Double
    let changePercent: Double
    let marketCap: String
    let volume: Int
}
struct StocksResponse: Decodable { let success: Bool; let stocks: [StockDto]? ; let watchlist: [StockDto]? }
struct StockPricePointDto: Decodable, Identifiable { let date: String; let price: Double; var id: String { date } }
struct StockHistoryResponse: Decodable { let success: Bool; let history: [StockPricePointDto] }
struct PortfolioValuePointDto: Decodable, Identifiable { let date: String; let value: Double; var id: String { date } }
struct PortfolioHistoryResponse: Decodable { let success: Bool; let history: [PortfolioValuePointDto] }
struct StockHoldingDto: Decodable, Identifiable {
    let stockId: String
    let symbol: String
    let name: String
    let shares: Double
    let avgPrice: Double
    let currentPrice: Double
    let value: Double
    let returnPercent: Double
    var id: String { stockId }

    enum CodingKeys: String, CodingKey {
        case stockId, symbol, name, shares, avgPrice, currentPrice, value
        case returnPercent = "return"
    }
}
struct StockPortfolioDto: Decodable {
    let totalValue: Double
    let totalReturn: Double
    let totalReturnPercent: Double
    let holdings: [StockHoldingDto]
}
struct StockPortfolioResponse: Decodable { let success: Bool; let portfolio: StockPortfolioDto }
struct TradeStockRequest: Encodable { let stockId: String; let shares: Double }
struct TradeStockResponse: Decodable { let success: Bool; let message: String }

extension NetworkClient {
    func startConversation(phoneNumber: String) async throws -> ConversationResponse {
        try await authenticatedPost("api/v1/messages/conversations", body: StartConversationRequest(phoneNumber: phoneNumber, otherUserId: nil))
    }

    func getConversations() async throws -> ConversationsResponse { try await get("api/v1/messages/conversations") }

    func getTalkContacts() async throws -> TalkContactsResponse { try await get("api/v1/messages/contacts") }

    func getMessages(conversationId: String) async throws -> MessagesResponse {
        try await get("api/v1/messages/conversations/\(conversationId)/messages")
    }

    func searchMessages(conversationId: String, query: String) async throws -> MessagesResponse {
        let encoded = query.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? query
        return try await get("api/v1/messages/conversations/\(conversationId)/messages/search?query=\(encoded)")
    }

    func sendMessage(conversationId: String, body: String) async throws -> MessageResponse {
        try await authenticatedPost("api/v1/messages/conversations/\(conversationId)/messages", body: SendMessageRequest(body: body))
    }

    func blockConversationParticipant(conversationId: String) async throws -> SuccessResponse {
        try await authenticatedPost("api/v1/messages/conversations/\(conversationId)/block", body: EmptyRequest())
    }

    func unblockConversationParticipant(conversationId: String) async throws -> SuccessResponse {
        try await authenticatedDelete("api/v1/messages/conversations/\(conversationId)/block")
    }

    func reportChatMessage(messageId: String, reason: String) async throws -> SuccessResponse {
        try await authenticatedPost("api/v1/chat/reports", body: CreateChatReportRequest(messageId: messageId, reason: reason))
    }

    // Real toggle -- tapping an already-active reaction removes it, same semantics as
    // MessagingService.toggleReaction on the backend.
    func toggleReaction(messageId: String, emoji: String) async throws -> ReactionsResponse {
        try await authenticatedPost("api/v1/messages/messages/\(messageId)/reactions", body: ToggleReactionRequest(emoji: emoji))
    }

    // Real group chat (2026-07-18) -- see rw.itunda.messaging.web.GroupMessagingController.
    func createGroup(name: String, memberPhoneNumbers: [String]) async throws -> GroupResponse {
        try await authenticatedPost("api/v1/messages/groups", body: CreateGroupRequest(name: name, memberPhoneNumbers: memberPhoneNumbers))
    }

    func getMyGroups() async throws -> GroupsResponse { try await get("api/v1/messages/groups") }

    func getGroupMessages(groupId: String) async throws -> GroupMessagesResponse {
        try await get("api/v1/messages/groups/\(groupId)/messages")
    }

    func sendGroupMessage(groupId: String, body: String) async throws -> GroupMessageResponse {
        try await authenticatedPost("api/v1/messages/groups/\(groupId)/messages", body: SendGroupMessageRequest(body: body))
    }

    func toggleGroupReaction(groupMessageId: String, emoji: String) async throws -> ReactionsResponse {
        try await authenticatedPost("api/v1/messages/groups/messages/\(groupMessageId)/reactions", body: ToggleReactionRequest(emoji: emoji))
    }

    func getGroupMembers(groupId: String) async throws -> GroupMembersResponse {
        try await get("api/v1/messages/groups/\(groupId)/members")
    }

    // Real online/offline presence (2026-07-19) -- see MessagingService.getPresence's
    // own doc comment on the backend. Works for any set of user ids, not just 1:1
    // conversation partners -- e.g. a group thread can pass every member's id.
    func getPresence(userIds: [String]) async throws -> PresenceResponse {
        try await get("api/v1/messages/presence", query: userIds.map { URLQueryItem(name: "userIds", value: $0) })
    }

    /// Real WebSocket live-transport (2026-07-18) -- see
    /// rw.itunda.app.websocket.MessagingWebSocketHandler's own doc comment for the real
    /// backend push shape this mirrors exactly (also ported to Android the same day).
    /// Native `URLSessionWebSocketTask`, no third-party dependency. Routes both
    /// "message" (1:1) and "group_message" pushes -- group chat gained a real mobile UI
    /// the same day this was extended.
    func connectMessagingSocket(onPush: @escaping (MessagingSocketPush) -> Void) -> URLSessionWebSocketTask {
        let wsBase = NetworkClient.baseURLString
            .replacingOccurrences(of: "http://", with: "ws://")
            .replacingOccurrences(of: "https://", with: "wss://")
        let token = KeychainTokenStore.shared.getAccessToken() ?? ""
        let url = URL(string: "\(wsBase)ws/messaging?token=\(token)")!
        let task = session.webSocketTask(with: url)
        task.resume()
        receiveMessagingSocketFrame(task, onPush: onPush)
        return task
    }

    // Real typing indicator send (2026-07-19) -- best-effort, matching bank-mfe/Android's
    // own sendTyping helpers; a failed send on a closed/never-connected task is silently
    // swallowed, same as every other non-critical real-time signal in this layer.
    func sendTyping(_ task: URLSessionWebSocketTask, conversationId: String? = nil, groupConversationId: String? = nil) {
        var payload: [String: String] = ["type": "typing"]
        if let conversationId { payload["conversationId"] = conversationId }
        if let groupConversationId { payload["groupConversationId"] = groupConversationId }
        guard let data = try? JSONEncoder().encode(payload), let text = String(data: data, encoding: .utf8) else { return }
        task.send(.string(text)) { _ in }
    }

    private func receiveMessagingSocketFrame(_ task: URLSessionWebSocketTask, onPush: @escaping (MessagingSocketPush) -> Void) {
        task.receive { [weak self] result in
            guard let self else { return }
            if case .success(.string(let text)) = result, let data = text.data(using: .utf8) {
                // Two-pass decode: only fully decode `message` once we've confirmed the
                // real `type` -- a group_message push's nested message object has a
                // different shape (groupConversationId, not conversationId) and would
                // otherwise fail MessageDto's strict decode, and vice versa.
                if let typeEnvelope = try? JSONDecoder().decode(MessagingSocketTypeEnvelope.self, from: data) {
                    if typeEnvelope.type == "message",
                       let envelope = try? JSONDecoder().decode(MessagingSocketMessageEnvelope.self, from: data) {
                        onPush(.directMessage(conversationId: envelope.conversationId, message: envelope.message))
                    } else if typeEnvelope.type == "group_message",
                              let envelope = try? JSONDecoder().decode(MessagingSocketGroupEnvelope.self, from: data) {
                        onPush(.groupMessage(groupConversationId: envelope.groupConversationId, message: envelope.message))
                    } else if typeEnvelope.type == "presence",
                              let envelope = try? JSONDecoder().decode(MessagingSocketPresenceEnvelope.self, from: data) {
                        onPush(.presenceChange(userId: envelope.userId, online: envelope.online))
                    } else if typeEnvelope.type == "typing",
                              let envelope = try? JSONDecoder().decode(MessagingSocketTypingEnvelope.self, from: data) {
                        onPush(.typingChange(conversationId: envelope.conversationId, groupConversationId: envelope.groupConversationId, userId: envelope.userId))
                    } else if typeEnvelope.type == "reaction",
                              let envelope = try? JSONDecoder().decode(MessagingSocketReactionEnvelope.self, from: data) {
                        onPush(.reactionChange(
                            conversationId: envelope.conversationId, groupConversationId: envelope.groupConversationId,
                            messageId: envelope.messageId, reactions: envelope.reactions,
                        ))
                    }
                }
            }
            // Real, non-critical -- a malformed/unexpected push or a transient receive
            // error shouldn't kill the app; the 4s poll stays as the real fallback
            // delivery path regardless. Only a genuinely closed socket stops the loop.
            if case .success = result {
                self.receiveMessagingSocketFrame(task, onPush: onPush)
            } else if case .failure = result {
                // Socket closed/errored -- stop listening, poll takes over.
            }
        }
    }

    func createListing(title: String, description: String, price: Double, category: String, latitude: Double? = nil, longitude: Double? = nil, meetingPlace: String? = nil) async throws -> ListingResponse {
        try await authenticatedPost("api/v1/marketplace/listings", body: CreateListingRequest(title: title, description: description, price: price, category: category, latitude: latitude, longitude: longitude, meetingPlace: meetingPlace))
    }

    func browseListings(category: String? = nil) async throws -> ListingsResponse {
        var path = "api/v1/marketplace/listings"
        if let category, !category.isEmpty {
            path += "?category=\(category.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? category)"
        }
        return try await get(path)
    }

    func getNearbyListings(lat: Double, lng: Double, radiusKm: Double = 3) async throws -> ListingsResponse {
        try await get("api/v1/marketplace/listings/nearby", query: [URLQueryItem(name: "latitude", value: String(lat)), URLQueryItem(name: "longitude", value: String(lng)), URLQueryItem(name: "radiusKm", value: String(radiusKm))])
    }

    func getMyListings() async throws -> ListingsResponse { try await get("api/v1/marketplace/my-listings") }

    // Real hyperlocal "my neighborhood" browse (2026-07-20) -- see setNeighborhood.
    // Real 400 on the caller's own neighborhood-not-set case, matching Android/web.
    func getListingsMyNeighborhood(category: String? = nil) async throws -> ListingsResponse {
        try await get("api/v1/marketplace/listings/my-neighborhood", query: [URLQueryItem(name: "category", value: category)])
    }

    func markListingSold(_ listingId: String) async throws -> ListingResponse {
        try await authenticatedPost("api/v1/marketplace/listings/\(listingId)/mark-sold", body: EmptyBody())
    }

    func removeListing(_ listingId: String) async throws -> ListingResponse {
        try await authenticatedDelete("api/v1/marketplace/listings/\(listingId)")
    }

    func contactSeller(listingId: String) async throws -> ContactSellerResponse {
        try await authenticatedPost("api/v1/marketplace/listings/\(listingId)/contact-seller", body: EmptyBody())
    }

    // Real 당근-style price-offer negotiation (2026-07-19) -- see PriceOfferService.
    func makeOffer(listingId: String, amount: Double) async throws -> PriceOfferResponse {
        try await authenticatedPost("api/v1/marketplace/listings/\(listingId)/offers", body: MakeOfferRequest(amount: amount))
    }

    func respondToOffer(offerId: String, action: String, counterAmount: Double? = nil) async throws -> PriceOfferResponse {
        try await authenticatedPost("api/v1/marketplace/offers/\(offerId)/respond", body: RespondToOfferRequest(action: action, counterAmount: counterAmount))
    }

    func getOffersForConversation(conversationId: String) async throws -> PriceOffersResponse {
        try await get("api/v1/marketplace/conversations/\(conversationId)/offers")
    }

    // Real Marketplace listing wishlist (2026-07-21 backend + bank-mfe) -- ported here,
    // closing the "Android/iOS don't have this yet" gap that row's own doc comment
    // named. Mirrors addFavoriteRestaurant/removeFavoriteRestaurant exactly.
    func addListingFavorite(_ listingId: String) async throws -> SuccessResponse {
        try await authenticatedPost("api/v1/marketplace/listings/\(listingId)/favorite", body: EmptyBody())
    }

    func removeListingFavorite(_ listingId: String) async throws -> SuccessResponse {
        try await authenticatedDelete("api/v1/marketplace/listings/\(listingId)/favorite")
    }

    func getMyFavoriteListings() async throws -> FavoriteListingsResponse { try await get("api/v1/marketplace/listings/favorites") }

    // Real KakaoTalk-style gift send/claim (2026-07-20) -- see GiftService.
    func sendGiftInConversation(conversationId: String, amount: Double, note: String?) async throws -> GiftResponse {
        try await authenticatedPost(
            "api/v1/gifts/conversations/\(conversationId)",
            body: SendGiftInConversationRequest(amount: amount, note: note),
            idempotencyKey: UUID().uuidString
        )
    }

    func claimGift(giftId: String) async throws -> GiftResponse {
        try await authenticatedPost("api/v1/gifts/\(giftId)/claim", body: EmptyBody(), idempotencyKey: UUID().uuidString)
    }

    func getGiftsForConversation(conversationId: String) async throws -> GiftsResponse {
        try await get("api/v1/gifts/conversations/\(conversationId)")
    }

    // Real 동네생활-style community board (2026-07-19) -- see rw.itunda.community.web.CommunityController.
    func getCommunityCategories() async throws -> CommunityCategoriesResponse { try await get("api/v1/community/categories") }

    func createCommunityPost(category: String, title: String, body: String, latitude: Double? = nil, longitude: Double? = nil) async throws -> CommunityPostResponse {
        try await authenticatedPost("api/v1/community/posts", body: CreateCommunityPostRequest(category: category, title: title, body: body, latitude: latitude, longitude: longitude))
    }

    func browseCommunityPosts(category: String? = nil) async throws -> CommunityPostsResponse {
        try await get("api/v1/community/posts", query: [URLQueryItem(name: "category", value: category)])
    }

    func getNearbyCommunityPosts(lat: Double, lng: Double, radiusKm: Double = 3) async throws -> CommunityPostsResponse {
        try await get("api/v1/community/posts/nearby", query: [URLQueryItem(name: "latitude", value: String(lat)), URLQueryItem(name: "longitude", value: String(lng)), URLQueryItem(name: "radiusKm", value: String(radiusKm))])
    }

    func getMyCommunityPosts() async throws -> CommunityPostsResponse { try await get("api/v1/community/my-posts") }

    // Real hyperlocal "my neighborhood" browse (2026-07-20) -- see setNeighborhood.
    func getCommunityPostsMyNeighborhood(category: String? = nil) async throws -> CommunityPostsResponse {
        try await get("api/v1/community/posts/my-neighborhood", query: [URLQueryItem(name: "category", value: category)])
    }

    func getCommunityPost(_ postId: String) async throws -> CommunityPostDetailResponse { try await get("api/v1/community/posts/\(postId)") }

    func removeCommunityPost(_ postId: String) async throws -> CommunityPostResponse {
        try await authenticatedDelete("api/v1/community/posts/\(postId)")
    }

    func getCommunityComments(_ postId: String) async throws -> CommunityCommentsResponse {
        try await get("api/v1/community/posts/\(postId)/comments")
    }

    func addCommunityComment(_ postId: String, body: String) async throws -> CommunityCommentResponse {
        try await authenticatedPost("api/v1/community/posts/\(postId)/comments", body: AddCommunityCommentRequest(body: body))
    }

    func toggleCommunityLike(_ postId: String) async throws -> ToggleCommunityLikeResponse {
        try await authenticatedPost("api/v1/community/posts/\(postId)/like", body: EmptyBody())
    }

    // Real 당근알바-style local job board (2026-07-19) -- see rw.itunda.jobs.web.JobPostController.
    func getJobCategories() async throws -> JobCategoriesResponse { try await get("api/v1/jobs/categories") }

    func createJobPost(category: String, title: String, description: String, payType: String, payAmount: Double, latitude: Double? = nil, longitude: Double? = nil) async throws -> JobPostResponse {
        try await authenticatedPost("api/v1/jobs/posts", body: CreateJobPostRequest(category: category, title: title, description: description, payType: payType, payAmount: payAmount, latitude: latitude, longitude: longitude))
    }

    func browseJobPosts(category: String? = nil) async throws -> JobPostsResponse {
        try await get("api/v1/jobs/posts", query: [URLQueryItem(name: "category", value: category)])
    }

    func getNearbyJobPosts(lat: Double, lng: Double, radiusKm: Double = 3) async throws -> JobPostsResponse {
        try await get("api/v1/jobs/posts/nearby", query: [URLQueryItem(name: "latitude", value: String(lat)), URLQueryItem(name: "longitude", value: String(lng)), URLQueryItem(name: "radiusKm", value: String(radiusKm))])
    }

    func getMyJobPosts() async throws -> JobPostsResponse { try await get("api/v1/jobs/my-posts") }

    func addJobPostFavorite(_ jobPostId: String) async throws -> SuccessResponse {
        try await authenticatedPost("api/v1/jobs/posts/\(jobPostId)/favorite", body: EmptyBody())
    }

    func removeJobPostFavorite(_ jobPostId: String) async throws -> SuccessResponse {
        try await authenticatedDelete("api/v1/jobs/posts/\(jobPostId)/favorite")
    }

    func getMyFavoriteJobPosts() async throws -> FavoriteJobPostsResponse { try await get("api/v1/jobs/posts/favorites") }

    // Real hyperlocal "my neighborhood" browse (2026-07-20) -- see setNeighborhood.
    func getJobPostsMyNeighborhood(category: String? = nil) async throws -> JobPostsResponse {
        try await get("api/v1/jobs/posts/my-neighborhood", query: [URLQueryItem(name: "category", value: category)])
    }

    func markJobPostFilled(_ jobPostId: String) async throws -> JobPostResponse {
        try await authenticatedPost("api/v1/jobs/posts/\(jobPostId)/mark-filled", body: EmptyBody())
    }

    func removeJobPost(_ jobPostId: String) async throws -> JobPostResponse {
        try await authenticatedDelete("api/v1/jobs/posts/\(jobPostId)")
    }

    func contactPoster(_ jobPostId: String) async throws -> ContactPosterResponse {
        try await authenticatedPost("api/v1/jobs/posts/\(jobPostId)/contact-poster", body: EmptyBody())
    }

    func reportHoodContent(targetType: String, targetId: String, reason: String) async throws -> HoodReportResponse {
        try await authenticatedPost("api/v1/hood/reports", body: CreateHoodReportRequest(targetType: targetType, targetId: targetId, reason: reason))
    }

    // Real 당근부동산-style property listing (2026-07-19) -- see rw.itunda.realestate.web.PropertyListingController.
    func getPropertyTypes() async throws -> PropertyTypesResponse { try await get("api/v1/realestate/property-types") }

    func createPropertyListing(
        listingType: String, propertyType: String, title: String, description: String, price: Double,
        bedrooms: Int? = nil, sizeSqm: Double? = nil,
        latitude: Double? = nil, longitude: Double? = nil,
    ) async throws -> PropertyListingResponse {
        try await authenticatedPost(
            "api/v1/realestate/listings",
            body: CreatePropertyListingRequest(
                listingType: listingType, propertyType: propertyType, title: title, description: description, price: price,
                bedrooms: bedrooms, sizeSqm: sizeSqm, latitude: latitude, longitude: longitude,
            ),
        )
    }

    func browsePropertyListings(listingType: String? = nil, propertyType: String? = nil) async throws -> PropertyListingsResponse {
        try await get("api/v1/realestate/listings", query: [
            URLQueryItem(name: "listingType", value: listingType),
            URLQueryItem(name: "propertyType", value: propertyType),
        ])
    }

    func getNearbyPropertyListings(lat: Double, lng: Double, radiusKm: Double = 3) async throws -> PropertyListingsResponse {
        try await get("api/v1/realestate/listings/nearby", query: [URLQueryItem(name: "latitude", value: String(lat)), URLQueryItem(name: "longitude", value: String(lng)), URLQueryItem(name: "radiusKm", value: String(radiusKm))])
    }

    func getMyPropertyListings() async throws -> PropertyListingsResponse { try await get("api/v1/realestate/my-listings") }
    func addPropertyListingFavorite(_ id: String) async throws -> SuccessResponse { try await authenticatedPost("api/v1/realestate/listings/\(id)/favorite", body: EmptyBody()) }
    func removePropertyListingFavorite(_ id: String) async throws -> SuccessResponse { try await authenticatedDelete("api/v1/realestate/listings/\(id)/favorite") }
    func getMyFavoritePropertyListings() async throws -> FavoritePropertyListingsResponse { try await get("api/v1/realestate/listings/favorites") }

    // Real hyperlocal "my neighborhood" browse (2026-07-20) -- see setNeighborhood.
    // Deliberately not combined with listingType/propertyType filters -- an honest v1
    // scoping choice, same as the real backend endpoint this calls.
    func getPropertyListingsMyNeighborhood() async throws -> PropertyListingsResponse {
        try await get("api/v1/realestate/listings/my-neighborhood")
    }

    func markPropertyListingTaken(_ propertyListingId: String) async throws -> PropertyListingResponse {
        try await authenticatedPost("api/v1/realestate/listings/\(propertyListingId)/mark-taken", body: EmptyBody())
    }

    func removePropertyListing(_ propertyListingId: String) async throws -> PropertyListingResponse {
        try await authenticatedDelete("api/v1/realestate/listings/\(propertyListingId)")
    }

    func contactLister(_ propertyListingId: String) async throws -> ContactListerResponse {
        try await authenticatedPost("api/v1/realestate/listings/\(propertyListingId)/contact-lister", body: EmptyBody())
    }

    // Real 당근-style price-offer negotiation (2026-07-19) -- see PropertyPriceOfferService.
    func makePropertyOffer(listingId: String, amount: Double) async throws -> PropertyPriceOfferResponse {
        try await authenticatedPost("api/v1/realestate/listings/\(listingId)/offers", body: MakePropertyOfferRequest(amount: amount))
    }

    func respondToPropertyOffer(offerId: String, action: String, counterAmount: Double? = nil) async throws -> PropertyPriceOfferResponse {
        try await authenticatedPost("api/v1/realestate/offers/\(offerId)/respond", body: RespondToPropertyOfferRequest(action: action, counterAmount: counterAmount))
    }

    func getPropertyOffersForConversation(conversationId: String) async throws -> PropertyPriceOffersResponse {
        try await get("api/v1/realestate/conversations/\(conversationId)/offers")
    }

    // Real category/search filter (2026-07-19) -- both optional and combinable. See
    // ShoppingController.getEligibleMerchants's own doc comment on the backend.
    func getShoppingMerchants(category: String? = nil, q: String? = nil) async throws -> ShoppingMerchantsResponse {
        try await get("api/v1/shopping/merchants", query: [URLQueryItem(name: "category", value: category), URLQueryItem(name: "q", value: q)])
    }

    // Real distinct category list -- see MerchantRepository.findDistinctCategories's own
    // doc comment on the backend.
    func getMerchantCategories() async throws -> MerchantCategoriesResponse { try await get("api/v1/shopping/merchants/categories") }

    // Real "search this map" + "directions" (2026-07-19) -- see MapsService.
    func searchPlaces(query: String) async throws -> MapsSearchResponse {
        try await get("api/v1/maps/search", query: [URLQueryItem(name: "q", value: query)])
    }

    // mode added 2026-07-22 (default "DRIVING") -- see OsrmRoutingClient.route's own doc
    // comment on the backend for the real, separately-deployed foot-profile OSRM
    // instance this now lets a caller reach.
    func getDirections(fromLat: Double, fromLng: Double, toLat: Double, toLng: Double, mode: String = "DRIVING") async throws -> MapsDirectionsResponse {
        try await get("api/v1/maps/directions", query: [
            URLQueryItem(name: "fromLat", value: String(fromLat)),
            URLQueryItem(name: "fromLng", value: String(fromLng)),
            URLQueryItem(name: "toLat", value: String(toLat)),
            URLQueryItem(name: "toLng", value: String(toLng)),
            URLQueryItem(name: "mode", value: mode),
        ])
    }

    func getItineraryDirections(waypoints: [ItineraryWaypointRequest], mode: String = "DRIVING") async throws -> MapsDirectionsResponse {
        try await authenticatedPost("api/v1/maps/directions/itinerary", body: ItineraryDirectionsRequest(waypoints: waypoints, mode: mode))
    }

    // Real alternative routes (2026-07-22) -- see MapsDirectionsAlternativesResponse's
    // own doc comment.
    func getDirectionsAlternatives(fromLat: Double, fromLng: Double, toLat: Double, toLng: Double, mode: String = "DRIVING") async throws -> MapsDirectionsAlternativesResponse {
        try await get("api/v1/maps/directions/alternatives", query: [
            URLQueryItem(name: "fromLat", value: String(fromLat)),
            URLQueryItem(name: "fromLng", value: String(fromLng)),
            URLQueryItem(name: "toLat", value: String(toLat)),
            URLQueryItem(name: "toLng", value: String(toLng)),
            URLQueryItem(name: "mode", value: mode),
        ])
    }

    // Real "nearby places" category search + bookmarked/favorite places (2026-07-19) --
    // see rw.itunda.maps.MapsService's own doc comment on the backend.
    func searchNearbyPlaces(category: String, lat: Double, lng: Double, radiusKm: Double = 2.0) async throws -> MapNearbyResponse {
        try await get("api/v1/maps/nearby", query: [
            URLQueryItem(name: "category", value: category),
            URLQueryItem(name: "lat", value: String(lat)),
            URLQueryItem(name: "lng", value: String(lng)),
            URLQueryItem(name: "radiusKm", value: String(radiusKm)),
        ])
    }

    func getMyMapBookmarks() async throws -> MapBookmarksResponse { try await get("api/v1/maps/bookmarks") }

    func addMapBookmark(displayName: String, latitude: Double, longitude: Double, folderName: String? = nil, color: String? = nil) async throws -> AddMapBookmarkResponse {
        try await authenticatedPost("api/v1/maps/bookmarks", body: AddMapBookmarkRequest(displayName: displayName, latitude: latitude, longitude: longitude, folderName: folderName, color: color))
    }

    // Real "move to folder" (2026-07-22) -- see MoveMapBookmarkRequest's own doc
    // comment. A real query-param PATCH -- same manual-request pattern
    // removeMapBookmark's own doc comment above already established for a query-param
    // request this client's authenticated* helpers don't directly support.
    func moveMapBookmark(latitude: Double, longitude: Double, folderName: String, color: String) async throws -> MoveMapBookmarkResponse {
        var components = URLComponents(url: baseURL.appendingPathComponent("api/v1/maps/bookmarks"), resolvingAgainstBaseURL: false)!
        components.queryItems = [URLQueryItem(name: "lat", value: String(latitude)), URLQueryItem(name: "lng", value: String(longitude))]
        var request = URLRequest(url: components.url!)
        request.httpMethod = "PATCH"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONEncoder().encode(MoveMapBookmarkRequest(folderName: folderName, color: color))
        if let token = KeychainTokenStore.shared.getAccessToken() {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        let (data, response) = try await session.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse else { throw NetworkError.invalidResponse }
        guard (200...299).contains(httpResponse.statusCode) else {
            throw NetworkError.httpError(statusCode: httpResponse.statusCode)
        }
        return try decoder.decode(MoveMapBookmarkResponse.self, from: data)
    }

    // A real query-param DELETE -- `authenticatedDelete(_:)` below takes no query, so
    // this is a one-off manual request, same pattern `searchDeliveryAddress` already
    // uses for its own query-param GET.
    func removeMapBookmark(latitude: Double, longitude: Double) async throws -> SuccessResponse {
        var components = URLComponents(url: baseURL.appendingPathComponent("api/v1/maps/bookmarks"), resolvingAgainstBaseURL: false)!
        components.queryItems = [URLQueryItem(name: "lat", value: String(latitude)), URLQueryItem(name: "lng", value: String(longitude))]
        var request = URLRequest(url: components.url!)
        request.httpMethod = "DELETE"
        if let token = KeychainTokenStore.shared.getAccessToken() {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        let (data, response) = try await session.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse else { throw NetworkError.invalidResponse }
        guard (200...299).contains(httpResponse.statusCode) else {
            throw NetworkError.httpError(statusCode: httpResponse.statusCode)
        }
        return try decoder.decode(SuccessResponse.self, from: data)
    }

    func getMerchantProducts(merchantId: String) async throws -> MerchantProductsResponse {
        try await get("api/v1/shopping/merchants/\(merchantId)/products")
    }

    func placeOrder(_ request: PlaceOrderRequest) async throws -> OrderDetailResponse {
        try await authenticatedPost("api/v1/orders", body: request, idempotencyKey: UUID().uuidString)
    }

    func getMyOrders() async throws -> OrdersResponse { try await get("api/v1/orders/my-orders") }

    func getOrder(_ orderId: String) async throws -> OrderDetailResponse { try await get("api/v1/orders/\(orderId)") }

    /// Real cancellation + refund (2026-07-18) -- buyer or seller, PLACED orders only.
    /// See rw.itunda.commerce.OrderService.cancelOrder's own doc comment.
    func cancelOrder(_ orderId: String) async throws -> OrderDetailResponse {
        try await authenticatedPost("api/v1/orders/\(orderId)/cancel", body: EmptyBody())
    }

    /// Real post-delivery product reviews (2026-07-20) -- see OrderController.submitProductReview.
    func submitProductReview(orderItemId: String, rating: Int, comment: String?) async throws -> ProductReviewResponse {
        try await authenticatedPost("api/v1/orders/items/\(orderItemId)/review", body: SubmitProductReviewRequest(rating: rating, comment: comment))
    }

    func getProductRating(_ productId: String) async throws -> ProductRatingResponse {
        try await get("api/v1/orders/products/\(productId)/rating")
    }

    func getProductReviews(_ productId: String) async throws -> ProductReviewsResponse {
        try await get("api/v1/orders/products/\(productId)/reviews")
    }

    // Real Coupang Eats-style food delivery (2026-07-18) -- see rw.itunda.eats.web.EatsController.
    func placeEatsOrder(_ request: PlaceEatsOrderRequest) async throws -> EatsOrderDetailResponse {
        try await authenticatedPost("api/v1/eats/orders", body: request, idempotencyKey: UUID().uuidString)
    }

    func getMyEatsOrders() async throws -> EatsOrdersResponse { try await get("api/v1/eats/orders/my-orders") }

    /// Real order detail, including items -- backs the real "Reorder" button (2026-07-19):
    /// a buyer can re-populate a cart from a past order's real items rather than retyping
    /// their whole order from scratch.
    func getEatsOrder(_ orderId: String) async throws -> EatsOrderDetailResponse {
        try await get("api/v1/eats/orders/\(orderId)")
    }

    /// Real address-search autocomplete (2026-07-18) -- backed by itunda's own
    /// self-hosted Nominatim geocoder. See EatsController.searchDeliveryAddress's own
    /// doc comment. Uses URLComponents (not appendingPathComponent) so the query string
    /// is encoded correctly -- the first query-param GET in this client.
    // Real post-delivery ratings & reviews (2026-07-18) -- see EatsController.submitReview.
    func submitEatsReview(orderId: String, restaurantRating: Int, restaurantComment: String?, riderRating: Int, riderComment: String?) async throws -> EatsReviewResponse {
        try await authenticatedPost(
            "api/v1/eats/orders/\(orderId)/review",
            body: SubmitEatsReviewRequest(restaurantRating: restaurantRating, restaurantComment: restaurantComment, riderRating: riderRating, riderComment: riderComment)
        )
    }

    func getRestaurantRating(_ restaurantId: String) async throws -> EatsRatingResponse {
        try await get("api/v1/eats/restaurants/\(restaurantId)/rating")
    }

    func searchDeliveryAddress(_ query: String) async throws -> AddressSearchResponse {
        var components = URLComponents(url: baseURL.appendingPathComponent("api/v1/eats/geocode/search"), resolvingAgainstBaseURL: false)!
        components.queryItems = [URLQueryItem(name: "q", value: query)]
        var request = URLRequest(url: components.url!)
        request.httpMethod = "GET"
        if let token = KeychainTokenStore.shared.getAccessToken() {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        let (data, response) = try await session.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse else { throw NetworkError.invalidResponse }
        guard (200...299).contains(httpResponse.statusCode) else {
            throw NetworkError.httpError(statusCode: httpResponse.statusCode)
        }
        return try decoder.decode(AddressSearchResponse.self, from: data)
    }

    /// Real cancellation + refund (2026-07-18) -- buyer or restaurant, PLACED orders
    /// only. See rw.itunda.eats.EatsOrderService.cancelOrder's own doc comment.
    func cancelEatsOrder(_ orderId: String) async throws -> EatsOrderDetailResponse {
        try await authenticatedPost("api/v1/eats/orders/\(orderId)/cancel", body: EmptyBody())
    }
    func getRiderDeliveries() async throws -> EatsOrdersResponse { try await get("api/v1/eats/orders/rider-deliveries") }
    func getAvailableDeliveries() async throws -> EatsOrdersResponse { try await get("api/v1/eats/orders/available") }

    func claimDelivery(_ orderId: String) async throws -> EatsOrderDetailResponse {
        try await authenticatedPost("api/v1/eats/orders/\(orderId)/claim", body: EmptyBody())
    }

    func updateRiderOrderStatus(_ orderId: String, status: String) async throws -> EatsOrderDetailResponse {
        try await authenticatedPost("api/v1/eats/orders/\(orderId)/rider-status", body: UpdateEatsOrderStatusRequest(status: status))
    }

    func registerRider() async throws -> RiderResponse {
        try await authenticatedPost("api/v1/eats/riders/register", body: EmptyBody())
    }

    func getMyRiderProfile() async throws -> RiderResponse { try await get("api/v1/eats/riders/me") }

    func setRiderAvailability(_ available: Bool) async throws -> RiderResponse {
        try await authenticatedPost("api/v1/eats/riders/availability", body: SetRiderAvailabilityRequest(available: available))
    }

    // Real bookmarked/favorited restaurants (2026-07-19) -- see
    // EatsFavoriteService.kt's own doc comment for why add/remove are both idempotent.
    func addFavoriteRestaurant(_ restaurantId: String) async throws -> SuccessResponse {
        try await authenticatedPost("api/v1/eats/restaurants/\(restaurantId)/favorite", body: EmptyBody())
    }

    func removeFavoriteRestaurant(_ restaurantId: String) async throws -> SuccessResponse {
        try await authenticatedDelete("api/v1/eats/restaurants/\(restaurantId)/favorite")
    }

    func getMyFavoriteRestaurants() async throws -> FavoriteRestaurantsResponse { try await get("api/v1/eats/favorites") }

    // Real Toss Securities-style stock investing (2026-07-20) -- see StocksResponse's
    // own doc comment for the full account.
    func getStocks() async throws -> StocksResponse { try await get("api/v1/stocks") }

    func getStockHistory(stockId: String, days: Int = 14) async throws -> StockHistoryResponse {
        try await get("api/v1/stocks/\(stockId)/history", query: [URLQueryItem(name: "days", value: String(days))])
    }

    func getStockPortfolio() async throws -> StockPortfolioResponse { try await get("api/v1/stocks/portfolio") }

    func getPortfolioHistory(days: Int = 30) async throws -> PortfolioHistoryResponse {
        try await get("api/v1/stocks/portfolio/history", query: [URLQueryItem(name: "days", value: String(days))])
    }

    func buyStock(stockId: String, shares: Double) async throws -> TradeStockResponse {
        try await authenticatedPost("api/v1/stocks/buy", body: TradeStockRequest(stockId: stockId, shares: shares), idempotencyKey: UUID().uuidString)
    }

    func sellStock(stockId: String, shares: Double) async throws -> TradeStockResponse {
        try await authenticatedPost("api/v1/stocks/sell", body: TradeStockRequest(stockId: stockId, shares: shares), idempotencyKey: UUID().uuidString)
    }

    func getStockWatchlist() async throws -> StocksResponse { try await get("api/v1/stocks/watchlist") }

    func watchStock(stockId: String) async throws -> SuccessResponse {
        try await authenticatedPost("api/v1/stocks/\(stockId)/watch", body: EmptyBody())
    }

    func unwatchStock(stockId: String) async throws -> SuccessResponse {
        try await authenticatedDelete("api/v1/stocks/\(stockId)/watch")
    }

    /// Real DELETE support -- every other authenticated call so far was GET/POST only,
    /// see `authenticatedPost`'s own doc comment for why the Idempotency-Key handling
    /// lives there; DELETE never needs one (removing an already-removed listing is
    /// naturally idempotent at the database level, unlike a real money-moving POST).
    fileprivate func authenticatedDelete<Response: Decodable>(_ path: String) async throws -> Response {
        var request = URLRequest(url: baseURL.appendingPathComponent(path))
        request.httpMethod = "DELETE"
        if let token = KeychainTokenStore.shared.getAccessToken() {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        let (data, response) = try await session.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse else { throw NetworkError.invalidResponse }
        guard (200...299).contains(httpResponse.statusCode) else {
            throw NetworkError.httpError(statusCode: httpResponse.statusCode)
        }
        return try decoder.decode(Response.self, from: data)
    }
}
