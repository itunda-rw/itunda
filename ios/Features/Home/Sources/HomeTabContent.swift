import SwiftUI
import UIKit
import CoreNetwork
import CoreDesignSystem

// Real fix (2026-08-26): split out of ContentView.swift once that file grew past
// its file-size-lint baseline. HomeTabContent (the minimal Home access-point tab)
// plus its own small header helper -- only used from ContentView's own TabView,
// which stays behind.
//
// Moved into FeatureHome (2026-09-02, Home Feature-module decomposition, iOS
// parity pass for Android's own :features:home:impl -- see
// [[project_itunda_feature_isolation]]) -- real decoupling: was
// `@ObservedObject var bankViewModel: BankViewModel` (App/Sources-only, App's own
// equivalent of Android's MainViewModel); narrowed to the 2 published properties
// this view actually reads plus a load callback, same pattern Android's own
// HomeTab decoupling established. TransactionRow and DiscoverRowData (both used
// elsewhere too -- TransactionRow in App/Sources, DiscoverRowData in
// FeatureBanking's own BankView.swift) promoted to CoreDesignSystem in the same
// slice rather than FeatureHome importing FeatureBanking directly, which
// scripts/ios-silo-boundary-check.py forbids.

// Real minimal access-point Home (2026-08-13) -- see ContentView's own showBank
// doc comment for why this replaces BankView as tag(0)'s content. Discover
// (discoverRows) was already real, already-fetched data; isOffline was already
// tracked by BankViewModel but nothing here ever rendered it -- the same "tracked
// but never shown" bug just found and fixed on Android's identical HomeTab.
public struct HomeTabContent: View {
    let discoverRows: [DiscoverRowData]
    let isOffline: Bool
    let onLoad: () async -> Void

    public init(discoverRows: [DiscoverRowData], isOffline: Bool, onLoad: @escaping () async -> Void) {
        self.discoverRows = discoverRows
        self.isOffline = isOffline
        self.onLoad = onLoad
    }

    public var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(alignment: .leading, spacing: 16) {
                HeaderTitle(title: "itunda")
                if isOffline {
                    HStack(spacing: 10) {
                        Image(systemName: "wifi.slash")
                            .foregroundColor(IDS.Colors.danger)
                        Text("You are offline. Showing limited, non-live data.")
                            .font(IDS.scaledFont(size: 13, weight: .semibold, relativeTo: .footnote))
                            .foregroundColor(IDS.Colors.danger)
                    }
                    .padding(.horizontal, 14)
                    .padding(.vertical, 10)
                    .background(IDS.Colors.dangerTint)
                    .cornerRadius(14)
                    .padding(.horizontal, 24)
                }
                // Real flat-design fix (docs/UI_UX_GUIDELINES.md rule 10, "Flat over
                // card-heavy") -- this was the one screen every user sees first still
                // boxing its rows in a card, the same gap found on web's identical
                // DiscoverSection the same full-app audit pass caught. Rows now sit
                // directly on the page background with a divider between them,
                // matching every other already-flattened list in this codebase.
                if !discoverRows.isEmpty {
                    VStack(alignment: .leading, spacing: 12) {
                        Text("Discover")
                            .font(IDS.scaledFont(size: 20, weight: .bold, relativeTo: .title3))
                            .foregroundColor(IDS.Colors.textPrimary)
                        VStack(spacing: 0) {
                            ForEach(discoverRows) { row in
                                HStack(alignment: .top, spacing: 12) {
                                    Image(systemName: "sparkles")
                                        .foregroundColor(IDS.Colors.brand)
                                        .frame(width: 36, height: 36)
                                        .background(IDS.Colors.chipBackground)
                                        .clipShape(Circle())
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(row.isNew ? "\(row.title) · NEW" : row.title)
                                            .font(IDS.scaledFont(size: 15, weight: .semibold, relativeTo: .body))
                                            .foregroundColor(IDS.Colors.textPrimary)
                                        Text(row.subtitle)
                                            .font(IDS.scaledFont(size: 13, weight: .regular, relativeTo: .footnote))
                                            .foregroundColor(IDS.Colors.textSecondary)
                                    }
                                    Spacer()
                                    if let badge = row.badge {
                                        Text(badge)
                                            .font(IDS.scaledFont(size: 13, weight: .semibold, relativeTo: .footnote))
                                            .foregroundColor(IDS.Colors.textSecondary)
                                    }
                                }
                                .padding(.vertical, 10)
                                if row.id != discoverRows.last?.id {
                                    Divider().background(IDS.Colors.divider)
                                }
                            }
                        }
                    }
                    .padding(.horizontal, 24)
                }
            }
            .padding(.top, 8)
            .padding(.bottom, 32)
        }
        .background(IDS.Colors.backgroundPrimary.edgesIgnoringSafeArea(.all))
        .task { await onLoad() }
        // Real, minimal usage signal (2026-08-10) -- see the "itunda: the wedge, not
        // the mirror" strategy memo, recommendation (ii), and
        // NetworkClient.recordAnalyticsEventBestEffort's own doc comment. Fired once
        // per real appearance of Home, the baseline every retention question is
        // measured against -- same event name/shape bank-mfe's/Android's identical
        // Home effects already fire.
        .onAppear { NetworkClient.shared.recordAnalyticsEventBestEffort("home_view") }
    }
}

public struct HeaderTitle: View {
    let title: String
    public init(title: String) { self.title = title }
    public var body: some View {
        HStack {
            Text(title)
                .font(IDS.scaledFont(size: 28, weight: .bold, relativeTo: .largeTitle))
                .foregroundColor(IDS.Colors.textPrimary)
            Spacer()
            IDS.Icons.bell(size: 22, color: IDS.Colors.textSecondary)
        }
        .padding(.horizontal, 24)
        .padding(.top, 16)
    }
}
