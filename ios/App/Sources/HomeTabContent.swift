import SwiftUI
import UIKit
import CoreNetwork
import CoreDesignSystem
import FeatureBanking
import FeatureMaps
import FeaturePayments

// Real fix (2026-08-26): split out of ContentView.swift once that file grew past
// its file-size-lint baseline. HomeTabContent (the minimal Home access-point tab)
// plus its own small header/row helpers -- only used from ContentView's own
// TabView, which stays behind.

// Real minimal access-point Home (2026-08-13) -- see ContentView's own showBank
// doc comment for why this replaces BankView as tag(0)'s content. Discover
// (bankViewModel.discoverRows) was already real, already-fetched data; isOffline
// was already tracked by BankViewModel (see its own doc comment) but nothing here
// ever rendered it -- the same "tracked but never shown" bug just found and fixed
// on Android's identical HomeTab, found by grepping for the same pattern on this
// platform. Reuses the shared bankViewModel instance (not a second fetch) so this
// and the Bank destination never show two different snapshots of the same data.
struct HomeTabContent: View {
    @ObservedObject var bankViewModel: BankViewModel

    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(alignment: .leading, spacing: 16) {
                HeaderTitle(title: "itunda")
                if bankViewModel.isOffline {
                    HStack(spacing: 10) {
                        Image(systemName: "wifi.slash")
                            .foregroundColor(IDS.Colors.danger)
                        Text("You are offline. Showing limited, non-live data.")
                            .font(scaledFont(size: 13, weight: .semibold, relativeTo: .footnote))
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
                if !bankViewModel.discoverRows.isEmpty {
                    VStack(alignment: .leading, spacing: 12) {
                        Text("Discover")
                            .font(scaledFont(size: 20, weight: .bold, relativeTo: .title3))
                            .foregroundColor(IDS.Colors.textPrimary)
                        VStack(spacing: 0) {
                            ForEach(bankViewModel.discoverRows) { row in
                                HStack(alignment: .top, spacing: 12) {
                                    Image(systemName: "sparkles")
                                        .foregroundColor(IDS.Colors.brand)
                                        .frame(width: 36, height: 36)
                                        .background(IDS.Colors.chipBackground)
                                        .clipShape(Circle())
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(row.isNew ? "\(row.title) · NEW" : row.title)
                                            .font(scaledFont(size: 15, weight: .semibold, relativeTo: .body))
                                            .foregroundColor(IDS.Colors.textPrimary)
                                        Text(row.subtitle)
                                            .font(scaledFont(size: 13, weight: .regular, relativeTo: .footnote))
                                            .foregroundColor(IDS.Colors.textSecondary)
                                    }
                                    Spacer()
                                    if let badge = row.badge {
                                        Text(badge)
                                            .font(scaledFont(size: 13, weight: .semibold, relativeTo: .footnote))
                                            .foregroundColor(IDS.Colors.textSecondary)
                                    }
                                }
                                .padding(.vertical, 10)
                                if row.id != bankViewModel.discoverRows.last?.id {
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
        .task { await bankViewModel.load() }
        // Real, minimal usage signal (2026-08-10) -- see the "itunda: the wedge, not
        // the mirror" strategy memo, recommendation (ii), and
        // NetworkClient.recordAnalyticsEventBestEffort's own doc comment. Fired once
        // per real appearance of Home, the baseline every retention question is
        // measured against -- same event name/shape bank-mfe's/Android's identical
        // Home effects already fire.
        .onAppear { NetworkClient.shared.recordAnalyticsEventBestEffort("home_view") }
    }
}

// Real Toss Pay home reference (4 screenshots, 2026-08-22) -- PayScreen itself moved to
// PayHomeExtras.swift (file-size-lint: this file was already over its baseline before
// this change) alongside RewardsPreviewSection, which it uses directly.

// EntireMenuScreen (the "All" tab) also moved to BenefitsShopAllScreens.swift
// (2026-07-11) -- same reason as the comment above.

struct HeaderTitle: View {
    let title: String
    var body: some View {
        HStack {
            Text(title)
                .font(scaledFont(size: 28, weight: .bold, relativeTo: .largeTitle))
                .foregroundColor(IDS.Colors.textPrimary)
            Spacer()
            IDS.Icons.bell(size: 22, color: IDS.Colors.textSecondary)
        }
        .padding(.horizontal, 24)
        .padding(.top, 16)
    }
}

struct TransactionRow: View {
    let title: String
    let date: String
    let amount: String
    let isNegative: Bool
    // Optional so a read-only usage (a past transaction, say) doesn't need to pass a
    // no-op closure -- PayScreen's merchant rows are the first real, tappable usage.
    var action: (() -> Void)? = nil

    var body: some View {
        let content = HStack {
            Circle()
                .fill(IDS.Colors.chipBackground)
                .frame(width: 40, height: 40)

            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .font(scaledFont(size: 16, weight: .semibold, relativeTo: .body))
                    .foregroundColor(IDS.Colors.textPrimary)
                Text(date)
                    .font(scaledFont(size: 14, weight: .regular, relativeTo: .footnote))
                    .foregroundColor(IDS.Colors.textSecondary)
            }
            Spacer()
            Text(amount)
                .font(scaledFont(size: 16, weight: .bold, relativeTo: .body))
                .foregroundColor(isNegative ? IDS.Colors.textPrimary : IDS.Colors.brand)
        }
        .padding(.horizontal, 24)
        .padding(.vertical, 12)

        if let action {
            Button(action: action) { content }
                .buttonStyle(.plain)
        } else {
            content
        }
    }
}

// #Preview { ContentView() } removed (2026-07-11) -- the real, working Xcode
// toolchain discovered this session (DEVELOPER_DIR=/Applications/Xcode.app/...,
// Xcode 14.3.1 + Tuist 3.42.3 via mise, see ARCHITECTURE.md §3) rejected this with
// "use of unknown directive '#Preview'" -- the preview-macro plugin isn't available
// in this generated project's build configuration. #Preview has zero runtime/
// production effect (canvas-only, Xcode-GUI-only), and no interactive canvas can
// ever render in this non-GUI environment anyway, so removing it was the right call
// rather than fighting toolchain plumbing for a feature that could never be used
// here. This was the *only* error in an otherwise clean full xcodebuild of every
// target in the workspace.
