import SwiftUI
import CoreDesignSystem
import CoreNetwork

/// Keeps the past-trip presentation and per-trip actions separate from ride booking
/// and live-trip state, keeping RideScreenView focused and under the file-size guardrail.
struct RidePastTripsContent: View {
    let trips: [RideTripDto]
    let hasMore: Bool
    let loadingMore: Bool
    let reviewedTripIds: Set<String>
    let tippedTripIds: Set<String>
    let busyTripId: String?
    let onLoadMore: () -> Void
    let onSubmitReview: (String, Int, String) async -> Void
    let onTipped: (String) -> Void
    let onReportIssue: (String) -> Void

    var body: some View {
        PastRideTripsSection(
            trips: trips,
            title: "Past rides",
            hasMore: hasMore,
            loadingMore: loadingMore,
            onLoadMore: onLoadMore,
            itemContent: { trip in
                AnyView(
                    Group {
                        if trip.status == "COMPLETED", trip.driverId != nil, !reviewedTripIds.contains(trip.id) {
                            RideReviewRow(busy: busyTripId == trip.id) { rating, comment in
                                await onSubmitReview(trip.id, rating, comment)
                            }
                        }
                        if trip.status == "COMPLETED", trip.driverId != nil, trip.tipAmount == nil, !tippedTripIds.contains(trip.id) {
                            TipDriverPrompt(tripId: trip.id, onTipped: { onTipped(trip.id) })
                        }
                        // Keep the existing support hand-off for completed rides.
                        if trip.status == "COMPLETED" {
                            Button(action: { onReportIssue(trip.transactionId) }) {
                                Text("Report an issue")
                                    .font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
                                    .padding(.horizontal, 12).padding(.vertical, 8)
                                    .background(IDS.Colors.chipBackground).cornerRadius(8)
                            }
                        }
                    }
                )
            }
        )
    }
}
