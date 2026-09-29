import SwiftUI
import CoreDesignSystem
import CoreNetwork

// Real Toss/Kakao mini-app-store reference (2026-09-11, 7 real Kakao 미니앱
// screenshots) -- the real partner mini-app catalog used to be a small teaser
// buried inside MyTabView's own card (PartnerMiniAppCatalogCard). This is its
// own dedicated screen now, matching bank-mfe's/Android's identical
// MiniAppsHubScreen split -- .sheet-presented from MyTabView, same pattern
// MembershipScreenView/CouponBoxScreenView already establish (PayHomeExtras.swift).
//
// Real category taxonomy (deliberately small and generic -- no real submitted
// partner apps yet to justify more granularity) matches the backend's own
// PartnerMiniAppCategory enum exactly. Search is client-side over the fetched
// (category-filtered) catalog -- the real catalog is genuinely tiny today (no
// seed data, no real onboarded partners), so real backend full-text search
// infra would be building ahead of real need. Stays browse-only, matching
// this platform's existing honest scope boundary (only Android has a real
// bundle runtime).
private let miniAppCategories = ["Finance", "Shopping", "Productivity", "Lifestyle", "Other"]

struct MiniAppsHubScreenView: View {
    var onBack: () -> Void = {}

    @State private var miniApps: [PartnerMiniAppDto]?
    @State private var selectedCategory: String?
    @State private var searchQuery = ""

    private var filtered: [PartnerMiniAppDto] {
        let query = searchQuery.trimmingCharacters(in: .whitespaces)
        guard !query.isEmpty else { return miniApps ?? [] }
        return (miniApps ?? []).filter {
            $0.name.localizedCaseInsensitiveContains(query) || $0.description.localizedCaseInsensitiveContains(query)
        }
    }

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: onBack) { IDS.Icons.back(size: 17, color: IDS.Colors.textPrimary, relativeTo: .body) }.accessibilityLabel("Back")
                Spacer()
                Text("Mini apps").font(.headline).foregroundColor(IDS.Colors.textPrimary)
                Spacer()
                Color.clear.frame(width: 20)
            }
            .padding()

            TextField("Search mini apps", text: $searchQuery)
                .textFieldStyle(.roundedBorder)
                .padding(.horizontal, 20)
                .padding(.bottom, 10)

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    MiniAppCategoryChip(label: "All", active: selectedCategory == nil) { selectedCategory = nil }
                    ForEach(miniAppCategories, id: \.self) { category in
                        MiniAppCategoryChip(label: category, active: selectedCategory == category) {
                            selectedCategory = selectedCategory == category ? nil : category
                        }
                    }
                }
                .padding(.horizontal, 20)
            }
            .padding(.bottom, 14)

            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    if miniApps == nil {
                        ProgressView().padding(40).frame(maxWidth: .infinity)
                    } else if filtered.isEmpty {
                        Text("No mini apps match yet -- try a different category or search term.")
                            .font(.caption).foregroundColor(IDS.Colors.textSecondary).padding(.top, 24)
                    } else {
                        ForEach(filtered) { app in
                            PartnerMiniAppRow(app: app)
                        }
                    }
                }
                .padding(.horizontal, 20)
            }
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
        .task(id: selectedCategory) {
            miniApps = nil
            let result = try? await NetworkClient.shared.getMiniAppCatalog(category: selectedCategory?.uppercased())
            miniApps = result?.miniApps ?? []
        }
    }
}

private struct MiniAppCategoryChip: View {
    let label: String
    let active: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(label)
                .font(.caption).bold()
                .foregroundColor(active ? .white : IDS.Colors.textPrimary)
                .padding(.horizontal, 12).padding(.vertical, 8)
                .background(active ? IDS.Colors.brand : IDS.Colors.chipBackground)
                .clipShape(Capsule())
        }
        .buttonStyle(.plain)
    }
}
