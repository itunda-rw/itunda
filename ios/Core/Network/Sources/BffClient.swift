import Foundation

/// Toss-Style Server-Driven UI (SDUI) Response Model
public struct SduiResponse: Codable {
    public let screenId: String
    public let version: String
    public let components: [SduiComponent]
    
    public init(screenId: String, version: String, components: [SduiComponent]) {
        self.screenId = screenId
        self.version = version
        self.components = components
    }
}

public struct SduiComponent: Codable {
    public let type: String // e.g., "HEADER", "BALANCE_CARD", "TRANSFER_LIST"
    public let data: [String: AnyCodable]
    public let actions: [SduiAction]?
    
    public init(type: String, data: [String: AnyCodable], actions: [SduiAction]?) {
        self.type = type
        self.data = data
        self.actions = actions
    }
}

public struct SduiAction: Codable {
    public let actionType: String // e.g., "NAVIGATE", "API_CALL", "DEEP_LINK"
    public let payload: [String: String]
    
    public init(actionType: String, payload: [String: String]) {
        self.actionType = actionType
        self.payload = payload
    }
}

/// Helper to decode arbitrary JSON objects in Swift
public struct AnyCodable: Codable {
    public let value: Any
    
    public init(_ value: Any) {
        self.value = value
    }
    
    public init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        if let intVal = try? container.decode(Int.self) { value = intVal }
        else if let doubleVal = try? container.decode(Double.self) { value = doubleVal }
        else if let boolVal = try? container.decode(Bool.self) { value = boolVal }
        else if let stringVal = try? container.decode(String.self) { value = stringVal }
        else { throw DecodingError.dataCorruptedError(in: container, debugDescription: "AnyCodable value cannot be decoded") }
    }
    
    public func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        if let intVal = value as? Int { try container.encode(intVal) }
        else if let doubleVal = value as? Double { try container.encode(doubleVal) }
        else if let boolVal = value as? Bool { try container.encode(boolVal) }
        else if let stringVal = value as? String { try container.encode(stringVal) }
        else { throw EncodingError.invalidValue(value, EncodingError.Context(codingPath: encoder.codingPath, debugDescription: "AnyCodable value cannot be encoded")) }
    }
}

/// BFF (Backend For Frontend) Client Protocol
public protocol BffClient {
    /// Fetches the Server-Driven UI layout and data for a specific screen.
    func getScreen(screenName: String, params: [String: String]) async throws -> SduiResponse
    
    /// Standard API execution for specific feature intentions.
    func executeAction(action: SduiAction) async throws -> [String: AnyCodable]
}
