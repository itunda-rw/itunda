import SwiftUI
import CoreDesignSystem
import CoreNetwork

// Real "My scam reports" parity gap found 2026-09-04 via a defined-but-uncalled-method
// sweep (project_itunda_uncalled_method_sweep_2026_09_04): Android's MyTab.kt already
// shows this inline (its own real, live section), bank-mfe defines the fetch
// (lib/scamReports.ts's fetchMyScamReports) but never renders it anywhere, and iOS had
// neither. Extracted into its own file the moment adding it inline pushed MyTabView.swift
// past 500 lines for the first time -- extract, don't baseline a first-time crossing.
//
// Reuses MyTabView's own "My orders" inline-list convention rather than the count-badge/
// navigate pattern used by My favorites/My listings, since there's no dedicated
// scam-report list screen anywhere for a badge to navigate to.
struct MyScamReportsSection: View {
    let reports: [ScamReportDto]

    var body: some View {
        if !reports.isEmpty {
            VStack(alignment: .leading, spacing: 10) {
                Text("My scam reports").font(IDS.scaledFont(size: 19, weight: .bold, relativeTo: .title2)).foregroundColor(IDS.Colors.textPrimary)
                ForEach(reports, id: \.id) { report in
                    VStack(alignment: .leading, spacing: 4) {
                        Text(report.reportedIdentifier).font(IDS.scaledFont(size: 15, weight: .semibold, relativeTo: .subheadline)).foregroundColor(IDS.Colors.textPrimary)
                        Text(report.reason).font(.caption).foregroundColor(IDS.Colors.textSecondary)
                        Text(relativeTimeAgo(report.createdAt)).font(.caption2).foregroundColor(IDS.Colors.textTertiary)
                    }
                    .padding(.vertical, 6)
                    Divider()
                }
            }
        }
    }

    // Same "each screen keeps its own local copy" convention MyTabView.swift's own
    // formatAmount/errorMessage already establish -- App/Sources/HoodScreen.swift's own
    // hoodRelativeTime isn't reachable from this Feature module.
    private func relativeTimeAgo(_ isoTimestamp: String) -> String {
        let parser = ISO8601DateFormatter()
        parser.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        let date = parser.date(from: isoTimestamp) ?? {
            parser.formatOptions = [.withInternetDateTime]
            return parser.date(from: isoTimestamp)
        }() ?? Date()
        let seconds = max(0, Date().timeIntervalSince(date))
        switch seconds {
        case ..<60: return "Just now"
        case ..<3600: return "\(Int(seconds / 60))m ago"
        case ..<86400: return "\(Int(seconds / 3600))h ago"
        default: return "\(Int(seconds / 86400))d ago"
        }
    }
}
