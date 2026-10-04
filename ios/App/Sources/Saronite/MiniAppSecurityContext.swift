import React

/// Process-wide partner scope context, intentionally inert until an iOS partner
/// bundle loader activates it. This mirrors Android's single-active-bundle constraint.
/// nil means first-party/trusted mini-apps; a non-nil set is the partner's approved scopes.
final class MiniAppSecurityContext {
    static var activeScopes: Set<String>?

    static func isAllowed(_ requiredScope: String?) -> Bool {
        guard let scopes = activeScopes else { return true }
        return requiredScope.map { scopes.contains($0) } ?? false
    }

    static func requireScope(_ requiredScope: String?, reject: @escaping RCTPromiseRejectBlock) -> Bool {
        guard isAllowed(requiredScope) else {
            reject("SARONITE_SCOPE_DENIED", "This mini-app's approved permissions do not include" + (requiredScope.map { scope in " \"\(scope)\"" } ?? " this call"), nil)
            return false
        }
        return true
    }
}
