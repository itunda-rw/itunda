import SwiftUI
import CoreDesignSystem
import CoreNetwork

// Real 살아본 후기 (Karrot "lived here" neighborhood reviews), itunda Hood redesign
// 2026-08-28 -- see backend NeighborhoodReview.kt's own doc comment. Distinct from a
// buyer/seller transaction review -- a public review of an area, shown on every
// property listing in that neighborhood. Mirrors Android's
// PropertyNeighborhoodReviews.kt copy/structure exactly.
struct NeighborhoodReviewsSection: View {
    let neighborhood: String

    @State private var reviews: [NeighborhoodReviewDto]?
    @State private var showForm = false
    @State private var body_ = ""
    @State private var years = ""
    @State private var error: String?
    @State private var submitting = false

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("살아본 후기 · \(neighborhood)").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
            if let reviews {
                if reviews.isEmpty {
                    Text("No reviews yet — be the first to share what it's like living here.")
                        .font(.caption).foregroundColor(IDS.Colors.textSecondary)
                } else {
                    ForEach(reviews) { r in
                        VStack(alignment: .leading, spacing: 2) {
                            if let years = r.residencyYears {
                                Text("\(years) years living here").font(.caption2).bold().foregroundColor(IDS.Colors.textSecondary)
                            }
                            Text(r.body).font(.caption).foregroundColor(IDS.Colors.textPrimary)
                        }
                    }
                }
            } else {
                Text("Loading…").font(.caption).foregroundColor(IDS.Colors.textSecondary)
            }
            if !showForm {
                Button("+ Write a review") { showForm = true }.font(.caption).bold()
            } else {
                IdsTextField("What's it like living here?", text: $body_)
                IdsTextField("Years living here (optional)", text: $years, keyboardType: .numberPad)
                if let error { Text(error).font(.caption).foregroundColor(.red) }
                HStack(spacing: 10) {
                    Button("Cancel") { showForm = false; error = nil }
                    Button(submitting ? "Saving…" : "Save") { Task { await submit() } }
                        .disabled(submitting || body_.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
            }
        }
        .task { await load() }
    }

    private func load() async {
        reviews = (try? await NetworkClient.shared.getNeighborhoodReviews(neighborhood).reviews) ?? []
    }

    private func submit() async {
        submitting = true
        defer { submitting = false }
        do {
            _ = try await NetworkClient.shared.submitNeighborhoodReview(neighborhood: neighborhood, residencyYears: Int(years), body: body_.trimmingCharacters(in: .whitespacesAndNewlines))
            body_ = ""; years = ""; showForm = false; error = nil
            await load()
        } catch let NetworkError.httpError(statusCode) where statusCode == 409 {
            error = "You've already reviewed this neighborhood."
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }
}
