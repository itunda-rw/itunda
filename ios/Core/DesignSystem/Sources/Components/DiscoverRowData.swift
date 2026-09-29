import Foundation

// Real Discover feed row (a CMS-style catalog, not user-specific data), purely
// informational, no click-through action or money movement. Android already has
// this (DiscoverSection in ItundaAppScreen.kt).
//
// Promoted here from Features/Banking/Sources/BankView.swift (2026-09-02, Home
// Feature-module decomposition) -- FeatureHome's HomeTabContent needs this type
// too, and a direct FeatureHome -> FeatureBanking import would be a forbidden
// cross-Feature impl-to-impl dependency (scripts/ios-silo-boundary-check.py),
// same class of boundary Android's own Konsist check enforces. Plain display
// model, zero Banking-specific business logic, so it moves cleanly.
public struct DiscoverRowData: Identifiable {
    public let id = UUID()
    public let title: String
    public let subtitle: String
    public let badge: String?
    public let isNew: Bool
    // Real Toss Bank reference (2026-09-11) -- BankView's own Recommendations
    // section (a filtered, Bank-relevant subset of this same row shape) needs real
    // tap targets, unlike Home's purely-informational Discover feed. Defaults to
    // nil so Home's existing construction (no onTap passed) is unaffected.
    public let onTap: (() -> Void)?

    public init(title: String, subtitle: String, badge: String?, isNew: Bool, onTap: (() -> Void)? = nil) {
        self.title = title
        self.subtitle = subtitle
        self.badge = badge
        self.isNew = isNew
        self.onTap = onTap
    }
}
