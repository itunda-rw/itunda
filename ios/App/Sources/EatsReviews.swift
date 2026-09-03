import SwiftUI
import CoreDesignSystem
import CoreNetwork
import FeatureMaps

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
    // Real tag-based good points (itunda Maps redesign, 2026-08-28) -- aggregate counts
    // alongside the star rating, same as Android/web's own port.
    @State private var goodPointCounts: [String: Int] = [:]
    // Real Coupang/Naver-style "도움돼요" (helpful) toggle -- see backend
    // EatsReviewService.toggleHelpful's own doc comment. Real, shipped on the backend +
    // bank-mfe/Android with zero iOS client until now -- found via a
    // cross-platform-parity check. EatsReviewDto's fields are all `let`, so the
    // displayed count is tracked as a local delta rather than mutating the DTO,
    // mirroring ShopReviews.swift's own established pattern exactly.
    @State private var helpfulVoted: Set<String> = []
    @State private var helpfulCountDeltas: [String: Int] = [:]

    private func toggleHelpful(_ reviewId: String) {
        Task {
            do {
                let helpful = try await NetworkClient.shared.toggleEatsReviewHelpful(reviewId).helpful
                if helpful { helpfulVoted.insert(reviewId) } else { helpfulVoted.remove(reviewId) }
                helpfulCountDeltas[reviewId, default: 0] += helpful ? 1 : -1
            } catch {
                // Real, non-critical -- a failed helpful-vote shouldn't block reading reviews.
            }
        }
    }

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
                    if !goodPointCounts.isEmpty {
                        Text(goodPointCounts.sorted { $0.value > $1.value }.prefix(2).map { "\(eatsGoodPointLabels[$0.key] ?? $0.key) \($0.value)" }.joined(separator: " · "))
                            .font(.caption2).foregroundColor(IDS.Colors.textSecondary)
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
                                    HStack(spacing: 10) {
                                        let displayedCount = max(0, r.helpfulCount + (helpfulCountDeltas[r.id] ?? 0))
                                        Button(action: { toggleHelpful(r.id) }) {
                                            Text("👍 Helpful" + (displayedCount > 0 ? " (\(displayedCount))" : ""))
                                                .font(.caption2)
                                                .foregroundColor(helpfulVoted.contains(r.id) ? IDS.Colors.brand : IDS.Colors.textSecondary)
                                        }
                                        ReportEatsReviewButton(reviewId: r.id)
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
        .task {
            goodPointCounts = (try? await NetworkClient.shared.getRestaurantGoodPoints(restaurantId).counts) ?? [:]
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

// Real 배달의민족 리뷰 신고하기 (report a review) -- see backend
// EatsReviewService.reportReview's own doc comment. Same real preset-reason-picker
// shape as bank-mfe/Android's own report button -- genuinely NOT covered by the
// generic HoodReportButton mechanism (no REVIEW target exists there).
private let eatsReviewReportReasons: [(reason: String, label: String)] = [
    ("DEFAMATION", "False or defamatory"),
    ("PERSONAL_INFO_EXPOSURE", "Shares personal information"),
    ("OBSCENE_OR_VIOLENT", "Obscene or violent"),
    ("UNRELATED_ABUSE", "Unrelated or abusive"),
]

struct ReportEatsReviewButton: View {
    let reviewId: String
    @State private var showChoices = false
    @State private var sending = false
    @State private var message: String?

    var body: some View {
        Group {
            if let message {
                Text(message).font(.caption2).foregroundColor(message.hasPrefix("Thanks") ? IDS.Colors.success : IDS.Colors.danger)
            } else if showChoices {
                VStack(alignment: .leading, spacing: 2) {
                    ForEach(eatsReviewReportReasons, id: \.reason) { item in
                        Button(item.label) { send(item.reason) }
                            .font(.caption2).foregroundColor(IDS.Colors.textSecondary)
                    }
                    Button("Cancel") { showChoices = false }
                        .font(.caption2).foregroundColor(IDS.Colors.textTertiary)
                }
            } else {
                Button(sending ? "Reporting…" : "Report") { showChoices = true }
                    .font(.caption2).foregroundColor(IDS.Colors.textSecondary)
                    .disabled(sending)
            }
        }
    }

    private func send(_ reason: String) {
        showChoices = false
        sending = true
        Task {
            do {
                _ = try await NetworkClient.shared.reportEatsReview(reviewId, reason: reason)
                message = "Thanks. Your report was sent for review."
            } catch let NetworkError.httpError(statusCode) where statusCode == 409 {
                message = "You already reported this review."
            } catch {
                message = "Could not send the report."
            }
            sending = false
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
    // Real tag-based good points (itunda Maps redesign, 2026-08-28) -- ports the same
    // preset-tag pattern already shipped for Hood marketplace reviews (HoodReviewForm).
    @State private var selectedGoodPoints: Set<String> = []
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
                Text("What went well?").font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 6) {
                        ForEach(eatsGoodPointOptions, id: \.0) { id, label in
                            let selected = selectedGoodPoints.contains(id)
                            Text(label)
                                .font(.caption).bold()
                                .foregroundColor(selected ? .white : IDS.Colors.textPrimary)
                                .padding(.horizontal, 12).padding(.vertical, 6)
                                .background(selected ? IDS.Colors.brand : IDS.Colors.chipBackground)
                                .cornerRadius(999)
                                .onTapGesture {
                                    if selected { selectedGoodPoints.remove(id) } else { selectedGoodPoints.insert(id) }
                                }
                        }
                    }
                }
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
                photoUrl: trimmedPhotoUrl.isEmpty ? nil : trimmedPhotoUrl,
                goodPoints: Array(selectedGoodPoints)
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

// Real Uber Eats post-delivery tip -- ported from bank-mfe/Android (2026-09-03), see
// EatsOrderDto.tipAmount's own doc comment. Reuses the exact TIP_PRESETS bank-mfe's
// own TipRiderPrompt established. Caller only renders this for a real DELIVERY order
// (a PICKUP order has no rider) that hasn't been tipped yet.
private let eatsTipPresets = [500, 1000, 2000]

struct TipRiderPrompt: View {
    let orderId: String
    let onTipped: () -> Void

    @State private var amount: Int?
    @State private var customAmount = ""
    @State private var submitting = false
    @State private var error: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("Tip your rider").font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
            HStack(spacing: 6) {
                ForEach(eatsTipPresets, id: \.self) { preset in
                    let selected = amount == preset
                    Text("\(preset)")
                        .font(.caption).bold().foregroundColor(selected ? .white : IDS.Colors.textSecondary)
                        .frame(maxWidth: .infinity).padding(.vertical, 8)
                        .background(selected ? IDS.Colors.brand : Color.clear).cornerRadius(8)
                        .onTapGesture { amount = preset; customAmount = ""; Task { await submit(preset) } }
                }
            }
            HStack(spacing: 6) {
                IdsTextField("Custom amount (RWF)", text: Binding(get: { customAmount }, set: { customAmount = $0; amount = nil }))
                    .keyboardType(.numberPad)
                Button(action: { Task { await submit(nil) } }) {
                    Text(submitting ? "…" : "Tip").font(.caption).bold().foregroundColor(.white)
                        .padding(.horizontal, 14).padding(.vertical, 10)
                        .background((Int(customAmount) ?? 0) > 0 && !submitting ? IDS.Colors.brand : IDS.Colors.textTertiary).cornerRadius(8)
                }
                .disabled(submitting || (Int(customAmount) ?? 0) <= 0)
            }
            if let error { Text(error).font(.caption2).foregroundColor(.red) }
        }
        .padding(.top, 8)
    }

    private func submit(_ overrideAmount: Int?) async {
        let finalAmount = overrideAmount ?? amount ?? Int(customAmount)
        guard let finalAmount, finalAmount > 0 else { return }
        submitting = true
        error = nil
        defer { submitting = false }
        do {
            _ = try await NetworkClient.shared.tipEatsOrderRider(orderId: orderId, amount: Double(finalAmount))
            onTipped()
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }
}
