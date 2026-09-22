import Foundation

/// Shared Server-Driven UI contracts.
///
/// Kept separate from CoreNetwork so renderers can consume SDUI contracts
/// without depending on a concrete transport/network module.
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
    public let type: String
    public let data: [String: AnyCodable]
    public let actions: [SduiAction]?

    public init(type: String, data: [String: AnyCodable], actions: [SduiAction]? = nil) {
        self.type = type
        self.data = data
        self.actions = actions
    }
}

public struct SduiAction: Codable {
    public let actionType: String
    public let payload: [String: String]

    public init(actionType: String, payload: [String: String]) {
        self.actionType = actionType
        self.payload = payload
    }
}

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
        else if let objectVal = try? container.decode([String: AnyCodable].self) { value = objectVal }
        else if let arrayVal = try? container.decode([AnyCodable].self) { value = arrayVal }
        else {
            throw DecodingError.dataCorruptedError(
                in: container,
                debugDescription: "AnyCodable value cannot be decoded"
            )
        }
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        switch value {
        case let value as Int: try container.encode(value)
        case let value as Double: try container.encode(value)
        case let value as Bool: try container.encode(value)
        case let value as String: try container.encode(value)
        case let value as [String: AnyCodable]: try container.encode(value)
        case let value as [AnyCodable]: try container.encode(value)
        default:
            throw EncodingError.invalidValue(
                value,
                EncodingError.Context(
                    codingPath: encoder.codingPath,
                    debugDescription: "AnyCodable value cannot be encoded"
                )
            )
        }
    }
}
