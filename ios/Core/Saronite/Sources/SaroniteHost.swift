import Foundation

public enum SaroniteLifecycle: String, Sendable {
    case installing, installed, launching, visible, hidden, suspended, terminated, failed
}

public enum SaronitePermissionState: String, Sendable {
    case requested, granted, denied, revoked
}

public protocol SaroniteTransport: AnyObject {
    func send(_ data: Data)
    func close()
}

public protocol SaroniteCapabilityHandler {
    func handle(method: String, payload: [String: AnyCodable]?, completion: @escaping (Result<[String: AnyCodable], SaroniteError>) -> Void)
}

public protocol SaronitePermissionPolicy {
    func state(for permission: String) -> SaronitePermissionState
}

public final class SaroniteHost {
    private let transport: SaroniteTransport
    private let permissions: SaronitePermissionPolicy
    private var handlers: [String: SaroniteCapabilityHandler] = [:]
    private var lifecycle: SaroniteLifecycle = .installing

    public init(transport: SaroniteTransport, permissions: SaronitePermissionPolicy) {
        self.transport = transport
        self.permissions = permissions
    }

    public func register(capability: String, handler: SaroniteCapabilityHandler) {
        handlers[capability] = handler
    }

    public func unregister(capability: String) {
        handlers.removeValue(forKey: capability)
    }

    public func emitLifecycle(_ next: SaroniteLifecycle) {
        lifecycle = next
        let event = SaroniteEvent(protocolVersion: SaroniteProtocol.version, kind: "event",
                                  id: Self.newId("evt"), event: "lifecycle",
                                  lifecycle: next.rawValue, permission: nil, payload: nil)
        if let data = try? SaroniteWire.encode(event) { transport.send(data) }
    }

    public func emitPermission(_ permission: String) {
        let event = SaroniteEvent(protocolVersion: SaroniteProtocol.version, kind: "event",
                                  id: Self.newId("evt"), event: "permission", lifecycle: nil,
                                  permission: SaronitePermissionEvent(name: permission, state: permissions.state(for: permission).rawValue),
                                  payload: nil)
        if let data = try? SaroniteWire.encode(event) { transport.send(data) }
    }

    public func dispatch(_ request: SaroniteRequest, completion: @escaping (SaroniteResponse) -> Void) {
        guard !request.capability.isEmpty, !request.method.isEmpty else {
            return respond(id: request.id, error: SaroniteError(code: "INVALID_REQUEST", message: "capability and method are required", detail: nil), completion: completion)
        }
        guard let handler = handlers[request.capability] else {
            return respond(id: request.id, error: SaroniteError(code: "UNKNOWN_CAPABILITY", message: "Capability is not registered: \(request.capability)", detail: nil), completion: completion)
        }
        if let permission = Self.permission(for: request.capability), permissions.state(for: permission) != .granted {
            return respond(id: request.id, error: SaroniteError(code: "PERMISSION_DENIED", message: "Permission is not granted: \(permission)", detail: nil), completion: completion)
        }
        guard lifecycle != .terminated && lifecycle != .failed else {
            return respond(id: request.id, error: SaroniteError(code: "INVALID_STATE", message: "Mini-app host is \(lifecycle.rawValue)", detail: nil), completion: completion)
        }

        handler.handle(method: request.method, payload: request.payload) { [weak self] result in
            guard let self else { return }
            switch result {
            case .success(let value): self.respond(id: request.id, result: value, completion: completion)
            case .failure(let error): completion(SaroniteResponse(protocolVersion: SaroniteProtocol.version, kind: "response", id: request.id, ok: false, result: nil, error: error))
            }
        }
    }

    public func close() {
        lifecycle = .terminated
        emitLifecycle(.terminated)
        transport.close()
    }

    private func respond(id: String, result: [String: AnyCodable]? = nil, error: SaroniteError? = nil, completion: @escaping (SaroniteResponse) -> Void) {
        completion(SaroniteResponse(protocolVersion: SaroniteProtocol.version, kind: "response", id: id, ok: error == nil, result: result, error: error))
    }

    public static func newId(_ prefix: String = "srn") -> String {
        "\(prefix)_\(UUID().uuidString.lowercased())"
    }

    private static func permission(for capability: String) -> String? {
        switch capability {
        case "identity": return "identity:read"
        case "location": return "location:read"
        case "camera": return "camera:capture"
        case "contacts": return "contacts:read"
        case "clipboard": return "clipboard:read"
        case "notifications": return "notifications:schedule"
        case "payments": return "payments:request"
        default: return nil
        }
    }
}
