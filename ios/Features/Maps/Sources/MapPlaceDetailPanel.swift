import SwiftUI
import CoreDesignSystem
import CoreNetwork

// Real Naver Map-style tabbed place-detail panel (itunda Maps redesign, 2026-08-28) --
// closes the biggest cross-platform gap found in this pass's research: iOS's place
// detail was booking-only, with no Home/Menu/Reviews/Photos/News/Info tab structure at
// all (Android/web already shipped this shape earlier this pass). New file from the
// start since MapScreenView.swift is at its frozen file-size-lint baseline with zero
// slack. Reads the one real consolidated MapPlaceDetailDto (MapsPlaceDetailService on
// the backend) -- renders nothing at all when nil (a place with no real itunda merchant
// match), same as every other honest-absence convention in this codebase.

enum MapPlaceTab: CaseIterable { case home, menu, reviews, photos, news, info }

// Real preset-tag display labels -- mirrors EatsReviewService.EATS_GOOD_POINTS' own real
// vocab on the backend. A local copy, same "each platform independently re-implements
// what it needs" convention Android's own local copy (MapPlaceDetailExtraTabs.kt) uses.
public let eatsGoodPointOptions: [(String, String)] = [
    ("GREAT_FOOD", "🍽️ Great food"), ("GREAT_DESSERT", "🍰 Great dessert"), ("NICE_INTERIOR", "🛋️ Nice interior"),
    ("GREAT_DRINKS", "🥤 Great drinks"), ("GOOD_FOR_CONVERSATION", "💬 Good for conversation"),
]
public let eatsGoodPointLabels: [String: String] = Dictionary(uniqueKeysWithValues: eatsGoodPointOptions)

struct MapPlaceDetailPanel: View {
    let detail: MapPlaceDetailDto
    @Binding var selectedTab: MapPlaceTab

    private var visibleTabs: [MapPlaceTab] {
        var tabs: [MapPlaceTab] = [.home]
        if !detail.menu.isEmpty { tabs.append(.menu) }
        if detail.rating.count > 0 || !detail.goodPointCounts.isEmpty { tabs.append(.reviews) }
        if !detail.photoUrls.isEmpty || detail.photoUrl != nil { tabs.append(.photos) }
        if !detail.updates.isEmpty { tabs.append(.news) }
        tabs.append(.info) // unconditional, matches the real reference's own always-present Info tab
        return tabs
    }

    private func label(_ tab: MapPlaceTab) -> String {
        switch tab {
        case .home: return "Home"
        case .menu: return "Menu (\(detail.menu.count))"
        case .reviews: return "Reviews (\(detail.rating.count))"
        case .photos: return "Photos"
        case .news: return "News"
        case .info: return "Info"
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 16) {
                ForEach(visibleTabs, id: \.self) { tab in
                    let active = selectedTab == tab
                    Text(label(tab))
                        .font(.caption).bold()
                        .foregroundColor(active ? IDS.Colors.brand : IDS.Colors.textSecondary)
                        .onTapGesture { selectedTab = tab }
                }
            }
            .padding(.top, 4)

            switch selectedTab {
            case .home:
                homeTab
            case .menu:
                menuTab
            case .reviews:
                reviewsTab
            case .photos:
                MapPlacePhotosTab(detail: detail)
            case .news:
                MapPlaceNewsTab(detail: detail)
            case .info:
                MapPlaceInfoTab(detail: detail)
            }
        }
    }

    @ViewBuilder
    private var homeTab: some View {
        VStack(alignment: .leading, spacing: 8) {
            if let summary = detail.aiSummary {
                HStack(alignment: .top, spacing: 8) {
                    Text("AI")
                        .font(.system(size: 10, weight: .bold))
                        .foregroundColor(.white)
                        .padding(.horizontal, 6).padding(.vertical, 2)
                        .background(IDS.Colors.brand).cornerRadius(4)
                    Text(summary).font(.caption).foregroundColor(IDS.Colors.textPrimary)
                }
                .padding(10)
                .background(IDS.Colors.chipBackground)
                .cornerRadius(10)
            }
            if let category = detail.category {
                Text(category).font(.caption).foregroundColor(IDS.Colors.textSecondary)
            }
        }
    }

    @ViewBuilder
    private var menuTab: some View {
        VStack(alignment: .leading, spacing: 6) {
            ForEach(detail.menu, id: \.id) { item in
                HStack {
                    Text(item.name).font(.caption).foregroundColor(IDS.Colors.textPrimary)
                    Spacer()
                    Text("\(Int(item.price)) RWF").font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
                }
                .opacity(item.active ? 1 : 0.5)
            }
        }
        .padding(.top, 4)
    }

    @ViewBuilder
    private var reviewsTab: some View {
        VStack(alignment: .leading, spacing: 6) {
            if let average = detail.rating.average {
                Text("★ \(String(format: "%.1f", average)) · \(detail.rating.count) review\(detail.rating.count == 1 ? "" : "s")")
                    .font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
            }
            ForEach(detail.goodPointCounts.sorted { $0.value > $1.value }, id: \.key) { tag, count in
                HStack {
                    Text(eatsGoodPointLabels[tag] ?? tag).font(.caption).foregroundColor(IDS.Colors.textPrimary)
                    Spacer()
                    Text("\(count)").font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
                }
                .padding(.horizontal, 12).padding(.vertical, 8)
                .background(IDS.Colors.chipBackground).cornerRadius(8)
            }
        }
        .padding(.top, 4)
    }
}

@ViewBuilder
func MapPlaceInfoTab(detail: MapPlaceDetailDto) -> some View {
    VStack(alignment: .leading, spacing: 6) {
        if let category = detail.category { Text("Category: \(category)").font(.caption).foregroundColor(IDS.Colors.textPrimary) }
        if let hours = detail.openingHours { Text("Hours: \(hours)").font(.caption).foregroundColor(IDS.Colors.textPrimary) }
        if let phone = detail.phoneNumber { Text("Phone: \(phone)").font(.caption).foregroundColor(IDS.Colors.textPrimary) }
    }
    .padding(.top, 4)
}

@ViewBuilder
func MapPlacePhotosTab(detail: MapPlaceDetailDto) -> some View {
    let gallery = ([detail.photoUrl].compactMap { $0 } + detail.photoUrls).reduce(into: [String]()) { acc, url in
        if !acc.contains(url) { acc.append(url) }
    }
    if gallery.isEmpty {
        Text("No photos yet.").font(.caption).foregroundColor(IDS.Colors.textTertiary).padding(.top, 4)
    } else {
        LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 8) {
            ForEach(gallery, id: \.self) { urlString in
                if let url = URL(string: urlString) {
                    AsyncImage(url: url) { phase in
                        if case .success(let image) = phase { image.resizable().scaledToFill() } else { IDS.Colors.chipBackground }
                    }
                    .frame(height: 90).clipShape(RoundedRectangle(cornerRadius: 8))
                }
            }
        }
        .padding(.top, 4)
    }
}

@ViewBuilder
func MapPlaceNewsTab(detail: MapPlaceDetailDto) -> some View {
    if detail.updates.isEmpty {
        Text("No updates yet.").font(.caption).foregroundColor(IDS.Colors.textTertiary).padding(.top, 4)
    } else {
        VStack(alignment: .leading, spacing: 12) {
            ForEach(detail.updates, id: \.id) { update in
                VStack(alignment: .leading, spacing: 2) {
                    Text(update.label)
                        .font(.caption2).bold()
                        .foregroundColor(update.label == "PROMO" ? IDS.Colors.brand : IDS.Colors.success)
                    Text(update.title).font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
                    Text(update.body).font(.caption).foregroundColor(IDS.Colors.textSecondary)
                    let period = [update.periodStart, update.periodEnd].compactMap { $0?.prefix(10) }.joined(separator: " ~ ")
                    if !period.isEmpty {
                        Text(period).font(.caption2).foregroundColor(IDS.Colors.textTertiary)
                    }
                    Text("♡ \(update.likeCount)").font(.caption2).foregroundColor(IDS.Colors.textTertiary)
                }
            }
        }
        .padding(.top, 4)
    }
}
