import Foundation

public enum SaroniteProtocol {
    public static let version = 1
}

public struct SaroniteRequest: Codable, Sendable {
    public let protocolVersion: Int
    public let kind: String
    public let id: String
    public let capability: String
    public let method: String
    public let payload: [String: AnyCodable]?
    public let timeoutMs: Int?

    public init(id: String, capability: String, method: String, payload: [String: AnyCodable]? = nil, timeoutMs: Int? = nil) {
        self.protocolVersion = SaroniteProtocol.version
        self.kind = "request"
        self.id = id
        self.capability = capability
        self.method = method
        self.payload = payload
        self.timeoutMs = timeoutMs
    }
}

public struct SaroniteError: Codable, Sendable {
    public let code: String
    public let message: String
    public let detail: [String: AnyCodable]?
}

public struct SaroniteResponse: Codable, Sendable {
    public let protocolVersion: Int
    public let kind: String
    public let id: String
    public let ok: Bool
    public let result: [String: AnyCodable]?
    public let error: SaroniteError?
}

public struct SaronitePermissionEvent: Codable, Sendable {
    public let name: String
    public let state: String
}

public struct SaroniteEvent: Codable, Sendable {
    public let protocolVersion: Int
    public let kind: String
    public let id: String
    public let event: String
    public let lifecycle: String?
    public let permission: SaronitePermissionEvent?
    public let payload: [String: AnyCodable]?
}

public struct AnyCodable: Codable, Sendable {
    public let value: Any
    public init(_ value: Any) { self.value = value }
    public init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        if let value = try? container.decode(String.self) { self.value = value; return }
        if let value = try? container.decode(Bool.self) { self.value = value; return }
        if let value = try? container.decode(Int.self) { self.value = value; return }
        if let value = try? container.decode(Double.self) { self.value = value; return }
        if let value = try? container.decode([String: AnyCodable].self) { self.value = value; return }
        if let value = try? container.decode([AnyCodable].self) { self.value = value; return }
        self.value = NSNull()
    }
    public func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        switch value {
        case let value as String: try container.encode(value)
        case let value as Bool: try container.encode(value)
        case let value as Int: try container.encode(value)
        case let value as Double: try container.encode(value)
        case let value as [String: AnyCodable]: try container.encode(value)
        case let value as [AnyCodable]: try container.encode(value)
        default: try container.encodeNil()
        }
    }
}

public enum SaroniteWire {
    public static func decodeRequest(_ data: Data) throws -> SaroniteRequest {
        try JSONDecoder().decode(SaroniteRequest.self, from: data)
    }
    public static func encode(_ response: SaroniteResponse) throws -> Data {
        try JSONEncoder().encode(response)
    }
    public static func encode(_ event: SaroniteEvent) throws -> Data {
        try JSONEncoder().encode(event)
    }
}
