import Foundation

// Mirrors services/backend/auth/src/main/kotlin/rw/itunda/auth/AuthDtos.kt exactly --
// this app has no register screen (an agent operator is assigned by an admin via
// ops-mfe's Agents tab, then logs in with their existing itunda account).
struct LoginRequest: Encodable {
    let phoneNumber: String
    let password: String
}

struct PublicUser: Decodable {
    let id: String
    let phoneNumber: String
    let firstName: String
    let lastName: String
}

struct AuthResponse: Decodable {
    let message: String
    let user: PublicUser
    let accessToken: String
    let refreshToken: String
}

// Mirrors rw.itunda.agents's real Agent/AgentOperator/AgentTillReconciliation entities
// exactly (same field names as Android agentapp's own ApiService.kt equivalents) --
// trimmed to only the fields this app's UI actually reads.
struct OperatorDto: Decodable { let id: String; let agentId: String; let userId: String; let isActive: Bool }
struct OperatorResponse: Decodable { let success: Bool; let operator_: OperatorDto

    private enum CodingKeys: String, CodingKey { case success, operator_ = "operator" }
}

struct TillDto: Decodable {
    let agentId: String
    let agentName: String
    let expectedCash: Double
    let todayCashIn: Double
    let todayCashOut: Double
}
struct TillResponse: Decodable { let success: Bool; let till: TillDto }

struct ActivityDto: Decodable {
    let id: String
    let type: String
    let amount: Double
    let receiptNumber: String
    let createdAt: String
}
struct ActivityResponse: Decodable { let success: Bool; let activity: [ActivityDto] }

struct CashInRequest: Encodable { let accountNumber: String; let amount: Double; let receiptNumber: String }
struct CashOutRequest: Encodable { let accountNumber: String; let amount: Double; let receiptNumber: String; let authorizationCode: String }
struct TillCountRequest: Encodable { let countedCash: Double }

// Real push device-token registration -- see RiderApp/MerchantApp's own matching
// NetworkClient.swift doc comment, same pass (item 131): PushNotificationService.
// sendToUser silently no-ops for every real user with no registered token, and this
// dedicated agent-operator app never registered one at all.
struct RegisterDeviceTokenRequest: Encodable { let platform: String; let token: String }
struct SuccessResponse: Decodable { let success: Bool }

enum NetworkError: Error {
    case invalidResponse
    case httpError(statusCode: Int)
    // Real gap found live (Toss-style error-handling audit, 2026-08-30): a duplicate
    // receipt means an EARLIER attempt already succeeded and moved real money -- this
    // app's own blanket "do not give cash until confirmation succeeds" is actively
    // backwards advice for that specific case. `httpError` alone can't distinguish it
    // from AgentSuspendedException/idempotency conflicts, which also map to 409 --
    // same real-code-needed gap this codebase has hit before (see
    // NetworkClient+Calling.swift's own httpErrorWithMessage precedent elsewhere).
    case httpErrorWithCode(statusCode: Int, code: String?)
}

private struct AgentApiErrorBody: Decodable { let code: String?; let message: String? }

private struct EmptyBody: Encodable {}

/// Real, minimal URLSession client, mirroring RiderApp/MerchantApp's own
/// NetworkClient.swift exactly -- this app's own copy, scoped to the operator-facing
/// subset of rw.itunda.agents's real API surface (AgentOperatorController).
final class AgentNetworkClient {
    static let shared = AgentNetworkClient()

    private let baseURL = URL(string: "http://localhost:4001/")!
    private let session = URLSession(configuration: .default)
    private lazy var encoder = JSONEncoder()
    private lazy var decoder = JSONDecoder()

    private init() {}

    func login(_ request: LoginRequest) async throws -> AuthResponse {
        try await post("api/v1/auth/login", body: request, authenticated: false)
    }

    func me() async throws -> OperatorResponse { try await get("api/v1/agent/me") }

    func till() async throws -> TillResponse { try await get("api/v1/agent/till") }

    func activity(limit: Int = 20) async throws -> ActivityResponse {
        try await get("api/v1/agent/activity", query: [URLQueryItem(name: "limit", value: String(limit))])
    }

    func cashIn(_ request: CashInRequest) async throws -> SuccessResponse {
        try await postWithHeader("api/v1/agent/cash-ins", body: request, header: ("Idempotency-Key", UUID().uuidString))
    }

    func cashOut(_ request: CashOutRequest) async throws -> SuccessResponse {
        try await postWithHeader("api/v1/agent/cash-outs", body: request, header: ("Idempotency-Key", UUID().uuidString))
    }

    // Idempotency-Key added 2026-09-05 (see feedback_idempotency_key_sweep memory)
    // -- a lost response after a successful submission would previously resubmit
    // here and hit TillReconciliationAlreadySubmittedException on the retry, a
    // real cash-handling confusion risk (highest-priority item this thread names).
    func submitTillCount(_ request: TillCountRequest) async throws -> SuccessResponse {
        try await postWithHeader("api/v1/agent/till-reconciliations", body: request, header: ("Idempotency-Key", UUID().uuidString))
    }

    func registerDeviceToken(_ request: RegisterDeviceTokenRequest) async throws -> SuccessResponse {
        try await post("api/v1/notifications/device-tokens", body: request)
    }

    // MARK: - Helpers

    private func get<Response: Decodable>(_ path: String, query: [URLQueryItem] = []) async throws -> Response {
        var components = URLComponents(url: baseURL.appendingPathComponent(path), resolvingAgainstBaseURL: false)!
        if !query.isEmpty { components.queryItems = query }
        var request = URLRequest(url: components.url!)
        request.httpMethod = "GET"
        if let token = AgentKeychainTokenStore.shared.token() {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        let (data, response) = try await session.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse else { throw NetworkError.invalidResponse }
        guard (200...299).contains(httpResponse.statusCode) else { throw NetworkError.httpError(statusCode: httpResponse.statusCode) }
        return try decoder.decode(Response.self, from: data)
    }

    private func post<Body: Encodable, Response: Decodable>(_ path: String, body: Body, authenticated: Bool = true) async throws -> Response {
        let data = try await sendRequest(method: "POST", path: path, body: body, authenticated: authenticated, extraHeader: nil)
        return try decoder.decode(Response.self, from: data)
    }

    private func postWithHeader<Body: Encodable, Response: Decodable>(_ path: String, body: Body, header: (String, String)) async throws -> Response {
        let data = try await sendRequest(method: "POST", path: path, body: body, authenticated: true, extraHeader: header)
        return try decoder.decode(Response.self, from: data)
    }

    @discardableResult
    private func sendRequest<Body: Encodable>(method: String, path: String, body: Body?, authenticated: Bool, extraHeader: (String, String)?) async throws -> Data {
        var request = URLRequest(url: baseURL.appendingPathComponent(path))
        request.httpMethod = method
        if authenticated, let token = AgentKeychainTokenStore.shared.token() {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        if let (headerField, headerValue) = extraHeader {
            request.setValue(headerValue, forHTTPHeaderField: headerField)
        }
        if let body {
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.httpBody = try encoder.encode(body)
        }
        let (data, response) = try await session.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse else { throw NetworkError.invalidResponse }
        guard (200...299).contains(httpResponse.statusCode) else {
            let code = try? decoder.decode(AgentApiErrorBody.self, from: data).code
            throw NetworkError.httpErrorWithCode(statusCode: httpResponse.statusCode, code: code)
        }
        return data
    }
}
