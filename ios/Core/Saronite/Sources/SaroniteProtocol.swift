import Foundation

public enum SaroniteProtocol { public static let version = 1 }

public struct SaroniteRequest: Codable {
    public let protocolVersion: Int; public let kind: String; public let id: String
    public let capability: String; public let method: String
    public let payload: [String: AnyCodable]?; public let timeoutMs: Int?
    public init(id: String, capability: String, method: String, payload: [String: AnyCodable]? = nil, timeoutMs: Int? = nil) {
        protocolVersion = SaroniteProtocol.version; kind = "request"; self.id = id; self.capability = capability; self.method = method; self.payload = payload; self.timeoutMs = timeoutMs
    }
}
public struct SaroniteError: Codable {
    public let code: String; public let message: String; public let detail: [String: AnyCodable]?
    public init(code: String, message: String, detail: [String: AnyCodable]? = nil) { self.code=code; self.message=message; self.detail=detail }
}
public struct SaroniteResponse: Codable {
    public let protocolVersion: Int; public let kind: String; public let id: String; public let ok: Bool
    public let result: [String: AnyCodable]?; public let error: SaroniteError?
    public init(protocolVersion: Int = SaroniteProtocol.version, kind: String = "response", id: String, ok: Bool, result: [String: AnyCodable]? = nil, error: SaroniteError? = nil) {
        self.protocolVersion=protocolVersion; self.kind=kind; self.id=id; self.ok=ok; self.result=result; self.error=error
    }
}
public struct SaronitePermissionEvent: Codable {
    public let name: String; public let state: String
    public init(name: String, state: String) { self.name=name; self.state=state }
}
public struct SaroniteEvent: Codable {
    public let protocolVersion: Int; public let kind: String; public let id: String; public let event: String
    public let lifecycle: String?; public let permission: SaronitePermissionEvent?; public let payload: [String: AnyCodable]?
    public init(protocolVersion: Int = SaroniteProtocol.version, kind: String = "event", id: String, event: String, lifecycle: String? = nil, permission: SaronitePermissionEvent? = nil, payload: [String: AnyCodable]? = nil) {
        self.protocolVersion=protocolVersion; self.kind=kind; self.id=id; self.event=event; self.lifecycle=lifecycle; self.permission=permission; self.payload=payload
    }
}
public struct AnyCodable: Codable {
    public let value: Any
    public init(_ value: Any) { self.value=value }
    public init(from decoder: Decoder) throws {
        let c=try decoder.singleValueContainer()
        if let v=try? c.decode(String.self){value=v;return}; if let v=try? c.decode(Bool.self){value=v;return}
        if let v=try? c.decode(Int.self){value=v;return}; if let v=try? c.decode(Double.self){value=v;return}
        if let v=try? c.decode([String:AnyCodable].self){value=v;return}; if let v=try? c.decode([AnyCodable].self){value=v;return}; value=NSNull()
    }
    public func encode(to encoder: Encoder) throws {
        var c=encoder.singleValueContainer()
        switch value { case let v as String: try c.encode(v); case let v as Bool: try c.encode(v); case let v as Int: try c.encode(v); case let v as Double: try c.encode(v); case let v as [String:AnyCodable]: try c.encode(v); case let v as [AnyCodable]: try c.encode(v); default: try c.encodeNil() }
    }
}
public enum SaroniteWire {
    public static func decodeRequest(_ data: Data) throws -> SaroniteRequest { try JSONDecoder().decode(SaroniteRequest.self, from: data) }
    public static func encode(_ response: SaroniteResponse) throws -> Data { try JSONEncoder().encode(response) }
    public static func encode(_ event: SaroniteEvent) throws -> Data { try JSONEncoder().encode(event) }
}
