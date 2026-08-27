import SwiftUI
import CoreDesignSystem
import CoreNetwork

// Real post-delivery ratings & reviews (2026-07-18) -- itunda's own self-hosted rating
// system, ported from bank-mfe's own review UI (the template for this iOS version).
// Extracted from EatsRestaurantMenu.swift (2026-08-28, itunda Eats redesign) to make
// room for that file's own real menu/cart-checkout additions under the file-size-lint
// 500-line cap -- a real, cohesive review-flow group, same precedent as this session's
// other file-size-lint extractions.
struct StarRatingRow: View {
    let value: Int
    let onChange: (Int) -> Void

    var body: some View {
        HStack(spacing: 4) {
            ForEach(1...5, id: \.self) { n in
                Button(action: { onChange(n) }) {
                    IDS.Icons.star(size: 24, color: n <= value ? .yellow : IDS.Colors.textTertiary)
                }
                .accessibilityLabel("Rate \(n) star\(n == 1 ? "" : "s")")
            }
        }
    }
}

// Real written-review list + owner-reply display (item 184/185/186) -- bank-mfe (item
// 184) and Android (item 185) already have this; this is the first iOS client. Mirrors
// ProductRatingBadge's own expand-on-tap pattern exactly (ShopScreen.swift, this app's
// Commerce equivalent). photoUrl rendering added 2026-08-28 (itunda Eats redesign) --
// bank-mfe/Android already show real reviewer-submitted photos inline; iOS had zero
// photoUrl field at all until this pass.
struct RestaurantRatingBadge: View {
    let restaurantId: String
    @State private var rating: EatsRatingResponse?
    @State private var open = false
    @State private var reviews: [EatsReviewDto]?

    var body: some View {
        Group {
            if let rating, rating.count > 0 {
                VStack(alignment: .leading, spacing: 4) {
                    Button(action: toggle) {
                        HStack(spacing: 4) {
                            IDS.Icons.star(size: 13, color: .yellow)
                            Text(String(format: "%.1f (%d)", rating.average ?? 0.0, rating.count))
                                .font(.caption).foregroundColor(IDS.Colors.textSecondary)
                        }
                    }
                    if open {
                        if let reviews {
                            if reviews.isEmpty {
                                EmptyStateView("No written reviews yet — be the first to share how it went.")
                            } else {
                                ForEach(reviews, id: \.id) { r in
                                    let stars = String(repeating: "★", count: r.restaurantRating) + String(repeating: "☆", count: 5 - r.restaurantRating)
                                    Text(r.restaurantComment.map { "\(stars) — \($0)" } ?? stars)
                                        .font(.caption2).foregroundColor(IDS.Colors.textSecondary)
                                    if let photoUrl = r.photoUrl, let url = URL(string: photoUrl) {
                                        AsyncImage(url: url) { phase in
                                            if case .success(let image) = phase { image.resizable().scaledToFill() }
                                        }
                                        .frame(width: 64, height: 64).clipShape(RoundedRectangle(cornerRadius: 8))
                                        .padding(.leading, 12)
                                    }
                                    if let reply = r.ownerReply, !reply.isEmpty {
                                        Text("↳ Restaurant: \(reply)").font(.caption2).foregroundColor(IDS.Colors.textTertiary).padding(.leading, 12)
                                    }
                                }
                            }
                        } else {
                            Text("Loading reviews…").font(.caption2).foregroundColor(IDS.Colors.textSecondary)
                        }
                    }
                }
            }
        }
        .task {
            do {
                rating = try await NetworkClient.shared.getRestaurantRating(restaurantId)
            } catch {
                // Real, non-critical -- a rating fetch failure shouldn't block browsing
                // the menu.
            }
        }
    }

    private func toggle() {
        open.toggle()
        guard open, reviews == nil else { return }
        Task {
            do {
                reviews = try await NetworkClient.shared.getRestaurantReviews(restaurantId).reviews
            } catch {
                reviews = []
            }
        }
    }
}

struct ReviewOrderCard: View {
    let order: EatsOrderDto

    @State private var open = false
    @State private var done = false
    @State private var restaurantRating = 0
    @State private var restaurantComment = ""
    @State private var riderRating = 0
    @State private var riderComment = ""
    // Real review-photo submission (2026-08-28, itunda Eats redesign) -- itunda has no
    // upload/storage layer (see backend Merchant.kt's own doc comment), so this is a
    // "bring your own URL" field, matching RestaurantPhotoThumb's own convention.
    @State private var photoUrl = ""
    @State private var submitting = false
    @State private var error: String?

    var body: some View {
        if done {
            Text("Thanks for your review!").font(.caption).foregroundColor(IDS.Colors.textSecondary)
        } else if !open {
            Button(action: { open = true }) {
                Text("Rate this order").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                    .padding(.horizontal, 16).padding(.vertical, 10)
                    .background(IDS.Colors.chipBackground).cornerRadius(12)
            }
        } else {
            VStack(alignment: .leading, spacing: 10) {
                VStack(alignment: .leading, spacing: 4) {
                    Text("Restaurant").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                    StarRatingRow(value: restaurantRating) { restaurantRating = $0 }
                    TextField("How was the food? (optional)", text: $restaurantComment)
                        .padding(10).background(IDS.Colors.chipBackground).cornerRadius(10)
                }
                VStack(alignment: .leading, spacing: 4) {
                    Text("Rider").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                    StarRatingRow(value: riderRating) { riderRating = $0 }
                    TextField("How was the delivery? (optional)", text: $riderComment)
                        .padding(10).background(IDS.Colors.chipBackground).cornerRadius(10)
                }
                TextField("Photo URL (optional)", text: $photoUrl)
                    .padding(10).background(IDS.Colors.chipBackground).cornerRadius(10)
                    .keyboardType(.URL).autocapitalization(.none)
                if let error {
                    Text(error).font(.caption).foregroundColor(.red)
                }
                HStack(spacing: 10) {
                    Button(action: { open = false }) {
                        Text("Cancel").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                            .frame(maxWidth: .infinity).padding(.vertical, 12)
                            .background(IDS.Colors.chipBackground).cornerRadius(12)
                    }
                    Button(action: { Task { await submit() } }) {
                        Text(submitting ? "Submitting…" : "Submit review").font(.subheadline).bold().foregroundColor(.white)
                            .frame(maxWidth: .infinity).padding(.vertical, 12)
                            .background(submitting ? IDS.Colors.textTertiary : IDS.Colors.brand).cornerRadius(12)
                    }
                    .disabled(submitting)
                }
            }
        }
    }

    private func submit() async {
        guard restaurantRating > 0, riderRating > 0 else {
            error = "Rate both the restaurant and the rider."
            return
        }
        submitting = true
        error = nil
        defer { submitting = false }
        let trimmedPhotoUrl = photoUrl.trimmingCharacters(in: .whitespaces)
        do {
            _ = try await NetworkClient.shared.submitEatsReview(
                orderId: order.id,
                restaurantRating: restaurantRating,
                restaurantComment: restaurantComment.trimmingCharacters(in: .whitespaces).isEmpty ? nil : restaurantComment,
                riderRating: riderRating,
                riderComment: riderComment.trimmingCharacters(in: .whitespaces).isEmpty ? nil : riderComment,
                photoUrl: trimmedPhotoUrl.isEmpty ? nil : trimmedPhotoUrl
            )
            done = true
        } catch let NetworkError.httpError(statusCode) {
            // A 409 here is the real ORDER_ALREADY_REVIEWED case in practice -- this
            // card only ever renders for a real DELIVERED order, so the sibling "not
            // yet delivered" 409 can't actually occur through this UI path.
            if statusCode == 409 {
                done = true
            } else {
                error = TalkScreen.errorMessage(statusCode)
            }
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }
}
