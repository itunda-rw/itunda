import SwiftUI
import CoreDesignSystem

/// Real 비즈프로필 (Karrot Business Profile) visitor-count trend (itunda Hood
/// redesign, 2026-08-28) -- see NetworkClient+VisitorAnalytics.swift's own doc
/// comment. Every real consumer open of this merchant's Maps place-detail already
/// counts as one real visit; this is the owner-facing chart reading it.
struct VisitorAnalyticsTab: View {
    @State private var trend: [MerchantProfileViewDto]?
    @State private var error: String?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 8) {
                Text("Visitors").font(.headline)
                if let error {
                    Text(error).font(.footnote).foregroundColor(.red)
                }
                if let trend {
                    let totalVisits = trend.reduce(0) { $0 + $1.viewCount }
                    if trend.isEmpty || totalVisits == 0 {
                        Text("No real visits yet — once shoppers open your place on Maps, they'll show up here.")
                            .font(.footnote).foregroundColor(.secondary)
                    } else {
                        Text("\(totalVisits) visits in the last 7 days").font(.title2).bold()
                        VisitorTrendChart(trend: trend)
                        VStack(alignment: .leading, spacing: 6) {
                            ForEach(trend, id: \.id) { day in
                                HStack {
                                    Text(String(day.viewDate)).font(.footnote).foregroundColor(.secondary)
                                    Spacer()
                                    Text("\(day.viewCount)").font(.footnote).bold()
                                }
                            }
                        }
                    }
                } else {
                    Text("Loading…").font(.footnote).foregroundColor(.secondary)
                }
            }
            .padding(16).frame(maxWidth: .infinity, alignment: .leading)
            .background(Color(.secondarySystemBackground)).cornerRadius(12)
            .padding(16)
        }
        .task { await load() }
    }

    private func load() async {
        error = nil
        do {
            trend = try await MerchantNetworkClient.shared.getProfileViewTrend(days: 7).trend
        } catch let NetworkError.httpErrorWithMessage(_, message) {
            self.error = message ?? "Could not load your visitor data."
        } catch {
            self.error = "Could not load your visitor data."
        }
    }
}

private struct VisitorTrendChart: View {
    let trend: [MerchantProfileViewDto]

    var body: some View {
        let maxCount = max(1, trend.map(\.viewCount).max() ?? 1)
        HStack(alignment: .bottom, spacing: 6) {
            ForEach(trend, id: \.id) { day in
                RoundedRectangle(cornerRadius: 3)
                    .fill(IDS.Colors.brand)
                    .frame(height: max(4, CGFloat(day.viewCount) / CGFloat(maxCount) * 80))
            }
        }
        .frame(height: 80, alignment: .bottom)
    }
}
