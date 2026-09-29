import SwiftUI
import CoreDesignSystem
import CoreNetwork

// Real "My booking reviews" parity gap, closing the last open item from
// project_itunda_uncalled_method_sweep_2026_09_04: Android's MyTab.kt already shows this
// inline (its own real, live section); bank-mfe and iOS had neither a fetch nor a
// section, unlike My scam reports (MyScamReportsSection.swift) where bank-mfe at least
// had the fetch already defined. iOS's NetworkClient.getMyBookingReviews and
// MerchantBookingReviewDto were already fully built, just never called -- found via the
// same defined-but-uncalled-method sweep. Own file from the start (not inlined into
// MyTabView.swift first) since MyTabView.swift is already close to 500 lines after the
// scam-reports section landed.
//
// Reuses this platform's own existing star-rating (EatsReviews.swift/ShopReviews.swift's
// filled+unfilled "★"/"☆" convention) and owner-reply ("↳ Restaurant: ...") conventions
// rather than porting Android's boxed owner-reply treatment -- consistency with this
// platform's own sibling review screens matters more here than matching Android pixel
// for pixel.
struct MyBookingReviewsSection: View {
    let reviews: [MerchantBookingReviewDto]

    var body: some View {
        if !reviews.isEmpty {
            VStack(alignment: .leading, spacing: 10) {
                Text("My reviews").font(IDS.scaledFont(size: 19, weight: .bold, relativeTo: .title2)).foregroundColor(IDS.Colors.textPrimary)
                ForEach(reviews) { review in
                    VStack(alignment: .leading, spacing: 4) {
                        HStack {
                            Text(review.serviceName).font(IDS.scaledFont(size: 15, weight: .semibold, relativeTo: .subheadline)).foregroundColor(IDS.Colors.textPrimary)
                            Spacer()
                            let clampedRating = min(5, max(0, review.rating))
                            Text(String(repeating: "★", count: clampedRating) + String(repeating: "☆", count: 5 - clampedRating))
                                .font(.caption).foregroundColor(IDS.Colors.textSecondary)
                        }
                        if let comment = review.comment, !comment.isEmpty {
                            Text(comment).font(.caption).foregroundColor(IDS.Colors.textSecondary)
                        }
                        if let reply = review.ownerReply, !reply.isEmpty {
                            Text("↳ Owner: \(reply)").font(.caption2).foregroundColor(IDS.Colors.textTertiary).padding(.leading, 12)
                        }
                        Text(relativeTimeAgo(review.createdAt)).font(.caption2).foregroundColor(IDS.Colors.textTertiary)
                    }
                    .padding(.vertical, 6)
                    Divider()
                }
            }
        }
    }

    // Same "each screen keeps its own local copy" convention MyScamReportsSection.swift
    // already establishes for this module.
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
