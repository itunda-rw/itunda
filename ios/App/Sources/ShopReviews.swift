import SwiftUI
import UIKit
import CoreDesignSystem
import CoreNetwork
import CoreLocation


/// Real Coupang-style pre-purchase product Q&A (상품문의) -- see
/// rw.itunda.commerce.ProductInquiryService's own doc comment. Genuinely distinct from
/// ProductRatingBadge's reviews below: no order/purchase required at all, so this is
/// always visible on a product's detail page, not gated behind having bought it.
/// bank-mfe/Android already have this; this is the first iOS client.
struct ProductInquirySection: View {
    let productId: String
    @State private var inquiries: [ProductInquiryDto]?
    @State private var question = ""
    @State private var asking = false
    @State private var error: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Questions & answers").font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
            HStack(spacing: 8) {
                TextField("Ask the seller a question", text: $question)
                    .padding(10).background(IDS.Colors.backgroundPrimary).cornerRadius(10)
                Button(action: { Task { await ask() } }) {
                    Text("Ask").bold().font(.caption).foregroundColor(IDS.Colors.textPrimary)
                        .padding(.horizontal, 14).padding(.vertical, 10)
                        .background(Color(.tertiarySystemBackground)).cornerRadius(10)
                }
                .disabled(asking || question.trimmingCharacters(in: .whitespaces).isEmpty)
            }
            if let error {
                Text(error).font(.caption2).foregroundColor(.red)
            }
            if let inquiries {
                if inquiries.isEmpty {
                    EmptyStateView("No questions yet — be the first to ask.")
                } else {
                    VStack(alignment: .leading, spacing: 10) {
                        ForEach(inquiries) { q in
                            VStack(alignment: .leading, spacing: 2) {
                                HStack(alignment: .top, spacing: 0) {
                                    Text("Q. ").font(.caption).bold().foregroundColor(IDS.Colors.textSecondary)
                                    Text(q.question).font(.caption).foregroundColor(IDS.Colors.textSecondary)
                                }
                                if let answer = q.answer {
                                    HStack(alignment: .top, spacing: 0) {
                                        Text("A. ").font(.caption).bold().foregroundColor(IDS.Colors.textSecondary)
                                        Text(answer).font(.caption).foregroundColor(IDS.Colors.textSecondary)
                                    }
                                    .padding(.leading, 12)
                                } else {
                                    Text("Awaiting seller response").font(.caption2).foregroundColor(IDS.Colors.textTertiary)
                                        .padding(.leading, 12)
                                }
                            }
                        }
                    }
                }
            } else {
                Text("Loading questions…").font(.caption).foregroundColor(IDS.Colors.textSecondary)
            }
        }
        .task { await load() }
    }

    private func load() async {
        inquiries = (try? await NetworkClient.shared.getProductInquiries(productId))?.inquiries ?? []
    }

    private func ask() async {
        let trimmed = question.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty else { return }
        asking = true
        error = nil
        defer { asking = false }
        do {
            _ = try await NetworkClient.shared.askProductInquiry(productId, question: trimmed)
            question = ""
            await load()
        } catch {
            self.error = "Could not submit your question."
        }
    }
}

struct ProductRatingBadge: View {
    let productId: String
    @State private var rating: ProductRatingResponse?
    @State private var open = false
    @State private var reviews: [ProductReviewDto]?
    // Real "도움돼요" (helpful) toggle (2026-08-25) -- see
    // ProductReviewService.toggleHelpful's own doc comment on the backend. Tracked as a
    // delta dictionary rather than rewriting ProductReviewDto (a Decodable-only struct
    // with no public memberwise init across the Core/Network <-> App module boundary).
    @State private var helpfulVoted: Set<String> = []
    @State private var helpfulCountDeltas: [String: Int] = [:]

    private func displayedHelpfulCount(_ r: ProductReviewDto) -> Int {
        max(0, (r.helpfulCount ?? 0) + (helpfulCountDeltas[r.id] ?? 0))
    }

    private func toggleHelpful(_ reviewId: String) {
        Task {
            do {
                let helpful = try await NetworkClient.shared.toggleProductReviewHelpful(reviewId).helpful
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
                            IDS.Icons.star(size: 12, color: .yellow)
                            Text(String(format: "%.1f (%d)", rating.average ?? 0.0, rating.count))
                                .font(.caption2).foregroundColor(IDS.Colors.textSecondary)
                        }
                    }
                    if open {
                        if let reviews {
                            if reviews.isEmpty {
                                Text("No written reviews yet — be the first to share how it went.").font(.caption2).foregroundColor(IDS.Colors.textSecondary)
                            } else {
                                ForEach(reviews, id: \.id) { r in
                                    let stars = String(repeating: "★", count: r.rating) + String(repeating: "☆", count: 5 - r.rating)
                                    Text(r.comment.map { "\(stars) — \($0)" } ?? stars)
                                        .font(.caption2).foregroundColor(IDS.Colors.textSecondary)
                                    if let reply = r.ownerReply, !reply.isEmpty {
                                        Text("↳ Seller: \(reply)").font(.caption2).foregroundColor(IDS.Colors.textTertiary).padding(.leading, 12)
                                    }
                                    Button(action: { toggleHelpful(r.id) }) {
                                        let count = displayedHelpfulCount(r)
                                        Text("👍 Helpful" + (count > 0 ? " (\(count))" : ""))
                                            .font(.caption2)
                                            .foregroundColor(helpfulVoted.contains(r.id) ? IDS.Colors.brand : IDS.Colors.textTertiary)
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
                rating = try await NetworkClient.shared.getProductRating(productId)
            } catch {
                // Real, non-critical -- a rating fetch failure shouldn't block browsing the catalog.
            }
        }
    }

    private func toggle() {
        open.toggle()
        guard open, reviews == nil else { return }
        Task {
            do {
                reviews = try await NetworkClient.shared.getProductReviews(productId).reviews
            } catch {
                reviews = []
            }
        }
    }
}

struct ProductReviewRow: View {
    let item: OrderItemDto

    @State private var open = false
    @State private var done = false
    @State private var rating = 0
    @State private var comment = ""
    @State private var submitting = false
    @State private var error: String?

    var body: some View {
        if done {
            Text("\(item.productName): thanks for your review!").font(.caption).foregroundColor(IDS.Colors.textSecondary)
        } else if !open {
            Button(action: { open = true }) {
                Text("Rate \(item.productName)").font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
                    .padding(.horizontal, 16).padding(.vertical, 10)
                    .background(IDS.Colors.chipBackground).cornerRadius(12)
            }
        } else {
            VStack(alignment: .leading, spacing: 8) {
                Text(item.productName).font(.caption).foregroundColor(IDS.Colors.textSecondary)
                ShopStarRatingRow(value: rating) { rating = $0 }
                TextField("How was it? (optional)", text: $comment)
                    .padding(10).background(IDS.Colors.chipBackground).cornerRadius(10)
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
        guard rating > 0 else {
            error = "Pick a star rating."
            return
        }
        submitting = true
        error = nil
        defer { submitting = false }
        do {
            _ = try await NetworkClient.shared.submitProductReview(
                orderItemId: item.id,
                rating: rating,
                comment: comment.trimmingCharacters(in: .whitespaces).isEmpty ? nil : comment
            )
            done = true
        } catch let NetworkError.httpError(statusCode) {
            // A 409 here is the real PRODUCT_ALREADY_REVIEWED case in practice -- this
            // row only ever renders for a real DELIVERED order.
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

struct OrderItemReviews: View {
    let order: OrderDto
    @State private var items: [OrderItemDto]?

    var body: some View {
        Group {
            if let items, !items.isEmpty {
                VStack(alignment: .leading, spacing: 8) {
                    ForEach(items) { item in ProductReviewRow(item: item) }
                }
            }
        }
        .task {
            do {
                let res = try await NetworkClient.shared.getOrder(order.id)
                items = res.items
            } catch {
                // Real, non-critical -- if item fetch fails, the order row itself still renders fine.
            }
        }
    }
}

