import Foundation

/// Versioned, fail-closed capability policy for the iOS Saronite native bridge.
///
/// Version 1 intentionally grants partner bundles only `account:read`. Every other
/// API operation (including all writes and bill/reward/insurance calls) is denied to
/// partners until a separately reviewed capability is added here and on Android.
enum SaroniteCapability: String, CaseIterable {
    case accountRead = "account:read"
}

enum SaroniteRuntimeTrust: Equatable {
    case uninitialized
    case firstParty
    case partner(policyVersion: Int, scopes: Set<String>)
}

enum SaroniteCapabilityPolicy {
    static let policyVersion = 1

    /// Pure decision function so the same rules can be tested without networking or UI.
    static func allows(path: String, method: String, runtime: SaroniteRuntimeTrust) -> Bool {
        let capability: SaroniteCapability? =
            method.uppercased() == "GET" && path == "api/v1/account" ? .accountRead : nil
        return allows(runtime: runtime, capability: capability)
    }

    static func allows(
        runtime: SaroniteRuntimeTrust,
        capability: SaroniteCapability?
    ) -> Bool {
        switch runtime {
        case .uninitialized:
            return false
        case .firstParty:
            return true
        case let .partner(version, scopes):
            guard version == policyVersion, let capability else { return false }
            return scopes.contains(capability.rawValue)
        }
    }

    /// Reject unknown versions, unknown capabilities, and duplicate scope declarations.
    /// The returned Set is safe to install as an approved runtime context.
    static func validatedPartnerScopes(
        policyVersion: Int,
        scopes: [String]
    ) -> Set<String>? {
        guard policyVersion == Self.policyVersion else { return nil }
        guard Set(scopes).count == scopes.count else { return nil }
        let known = Set(SaroniteCapability.allCases.map(\.rawValue))
        guard scopes.allSatisfy({ known.contains($0) }) else { return nil }
        return Set(scopes)
    }
}

/// Process-wide state matches the current iOS host's single active mini-app screen.
/// No state is trusted until a known first-party screen explicitly activates it.
enum SaroniteRuntimeContext {
    private static let lock = NSLock()
    private static var trust: SaroniteRuntimeTrust = .uninitialized

    static func activateFirstParty() {
        lock.lock()
        defer { lock.unlock() }
        trust = .firstParty
    }

    @discardableResult
    static func activatePartner(policyVersion: Int, scopes: [String]) -> Bool {
        guard let approved = SaroniteCapabilityPolicy.validatedPartnerScopes(
            policyVersion: policyVersion,
            scopes: scopes
        ) else {
            deactivate()
            return false
        }
        lock.lock()
        defer { lock.unlock() }
        trust = .partner(policyVersion: policyVersion, scopes: approved)
        return true
    }

    static func deactivate() {
        lock.lock()
        defer { lock.unlock() }
        trust = .uninitialized
    }

    static func allows(_ capability: SaroniteCapability?) -> Bool {
        lock.lock()
        defer { lock.unlock() }
        return SaroniteCapabilityPolicy.allows(runtime: trust, capability: capability)
    }

    static func allows(path: String, method: String) -> Bool {
        lock.lock()
        defer { lock.unlock() }
        return SaroniteCapabilityPolicy.allows(path: path, method: method, runtime: trust)
    }

    /// External navigation is intentionally first-party-only in v1.
    static var allowsExternalNavigation: Bool {
        lock.lock()
        defer { lock.unlock() }
        return trust == .firstParty
    }
}
