import SwiftUI
import UIKit
import CoreLocation
import CoreDesignSystem
import CoreNetwork

// Real post-transaction review with Karrot's own asymmetric public/private visibility
// (2026-07-24) -- closes docs/DESIGN_REFERENCES.md Section 4 recommendation #2. A
// preset checklist, not free text, matching Karrot's own real review UX: "good points"
// are shown publicly (feed into the trust score), "uncomfortable points" stay private
// between the two real parties to the transaction. Shared by Marketplace/Jobs/Property.
struct HoodReviewForm: View {
    @Binding var selectedGoodPoints: Set<String>
    @Binding var selectedUncomfortablePoints: Set<String>
    let submitting: Bool
    let onCancel: () -> Void
    let onSubmit: () async -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("What went well? (shown publicly)").font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    ForEach(hoodGoodPointLabels, id: \.0) { id, label in
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
            Text("Anything uncomfortable? (private -- only you two see this)").font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    ForEach(hoodUncomfortablePointLabels, id: \.0) { id, label in
                        let selected = selectedUncomfortablePoints.contains(id)
                        Text(label)
                            .font(.caption).bold()
                            .foregroundColor(selected ? .white : IDS.Colors.textPrimary)
                            .padding(.horizontal, 12).padding(.vertical, 6)
                            .background(selected ? .red : IDS.Colors.chipBackground)
                            .cornerRadius(999)
                            .onTapGesture {
                                if selected { selectedUncomfortablePoints.remove(id) } else { selectedUncomfortablePoints.insert(id) }
                            }
                    }
                }
            }
            HStack(spacing: 10) {
                Button("Cancel", action: onCancel)
                    .font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                    .padding(.horizontal, 16).padding(.vertical, 10)
                    .background(IDS.Colors.chipBackground).cornerRadius(12)
                Button(action: { Task { await onSubmit() } }) {
                    Text(submitting ? "Submitting…" : "Submit review")
                        .font(.subheadline).bold().foregroundColor(.white)
                        .padding(.horizontal, 16).padding(.vertical, 10)
                        .background(IDS.Colors.brand).cornerRadius(12)
                }
                .disabled(submitting)
            }
        }
    }
}

// Real read-back for a submitted Hood transaction review (item 192/198/199) -- see
// bank-mfe's HoodReviewResultView (item 192) / Android's (item 198) for the full
// account. Only ever rendered for a real party to the transaction (the fetch itself
// real-403s otherwise via HOOD_REVIEW_NOT_PARTY), so both "your review" and "their
// review of you" -- including uncomfortablePoints -- are honestly shown here, matching
// Karrot's own asymmetric visibility: private between the two real parties, not public
// to anyone else. Shared by Marketplace/Jobs/Property, same as HoodReviewForm above.
struct HoodReviewResultView: View {
    let reviews: [HoodReviewDto]
    let myUserId: String?

    var body: some View {
        let mine = reviews.first { $0.reviewerId == myUserId }
        let theirs = reviews.first { $0.reviewerId != myUserId }
        if mine != nil || theirs != nil {
            VStack(alignment: .leading, spacing: 8) {
                if let mine { block(title: "Your review", review: mine) }
                if let theirs { block(title: "Their review of you", review: theirs) }
            }
        }
    }

    private func block(title: String, review: HoodReviewDto) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title).font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
            if !review.goodPoints.isEmpty {
                Text("👍 \(review.goodPoints.map { id in hoodGoodPointLabels.first { $0.0 == id }?.1 ?? id }.joined(separator: ", "))")
                    .font(.caption).foregroundColor(IDS.Colors.textSecondary)
            }
            if !review.uncomfortablePoints.isEmpty {
                Text("⚠️ \(review.uncomfortablePoints.map { id in hoodUncomfortablePointLabels.first { $0.0 == id }?.1 ?? id }.joined(separator: ", "))")
                    .font(.caption).foregroundColor(.red)
            }
        }
        .padding(.horizontal, 12).padding(.vertical, 10)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(IDS.Colors.chipBackground).cornerRadius(10)
    }
}

/// Real seller-paid sponsored placement -- see backend
/// MarketplaceService.boostListing's own doc comment. A real, still-future
/// boostedUntil only -- never fabricated for an unpaid or expired listing.
func isListingBoosted(_ boostedUntil: String?) -> Bool {
    guard let boostedUntil else { return false }
    let standard = ISO8601DateFormatter()
    let fractional = ISO8601DateFormatter()
    fractional.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
    guard let date = standard.date(from: boostedUntil) ?? fractional.date(from: boostedUntil) else { return false }
    return date > Date()
}

// Real Marketplace listing wishlist view (2026-07-21) -- iOS port of bank-mfe's
// ListingWishlistView, same day. Lists every real favorited listing (title/price/
// category straight from the favorites endpoint); a favorited-then-deleted listing's
// "no longer available" fallback is the backend's own responsibility
// (ListingFavoriteService.kt already resolves that server-side).
struct ListingWishlistView: View {
    let onRemoved: () -> Void

    @State private var favorites: [FavoriteListingDto]?
    @State private var error: String?
    @State private var removingId: String?

    var body: some View {
        Group {
            if let error {
                VStack(alignment: .leading, spacing: 10) {
                    Text(error).foregroundColor(.red).font(.subheadline)
                    Button("Retry") { Task { await load() } }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                // Real fix (2026-08-24, flat-design sweep): dropped the Card wrapper --
                // a lone error state.
                .padding(20)
            } else if favorites == nil {
                HoodFeedSkeleton()
            } else if favorites!.isEmpty {
                EmptyStateView("No saved listings yet -- tap ♡ on any listing to save it here.")
                    .foregroundColor(IDS.Colors.textSecondary)
            } else {
                // Real fix (2026-08-24, flat-design sweep): dropped the per-row Card --
                // an entity/favorites list, no divider, matching web's identical
                // ListingWishlistView conversion (docs/UI_UX_GUIDELINES.md §10).
                ForEach(favorites!) { f in
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(f.title).font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
                            Text("\(f.category) · \(formatAmount(Int(f.price))) RWF").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                        }
                        Spacer()
                        Button(action: { Task { await remove(f.listingId) } }) {
                            Text(removingId == f.listingId ? "Removing…" : "Remove")
                                .font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
                                .padding(.horizontal, 12).padding(.vertical, 8)
                                .background(IDS.Colors.chipBackground).cornerRadius(10)
                        }
                        .disabled(removingId == f.listingId)
                    }
                    .padding(.vertical, 10)
                }
            }
        }
        .task { await load() }
    }

    private func load() async {
        do {
            let res = try await NetworkClient.shared.getMyFavoriteListings()
            favorites = res.favorites
            error = nil
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }

    private func remove(_ listingId: String) async {
        removingId = listingId
        defer { removingId = nil }
        do {
            _ = try await NetworkClient.shared.removeListingFavorite(listingId)
            favorites = favorites?.filter { $0.listingId != listingId }
            onRemoved()
        } catch {
            self.error = "Couldn't remove this item. Check your connection and try again."
        }
    }
}

// Real 당근마켓 Keyword Alert (키워드 알림) -- first iOS client for this feature (item
// 116, found via a content-grep sweep: bank-mfe had it since item 114, Android
// ported it the same day as item 115, iOS never did). Mirrors bank-mfe's
// KeywordAlertsView and Android's KeywordAlertsView shape field-for-field: an
// add-keyword form, a list of existing alerts each with a Remove button, and a
// quiet-hours card with start/end time text fields and a toggle.
struct KeywordAlertsView: View {
    @State private var alerts: [KeywordAlertDto]?
    @State private var keyword = ""
    @State private var adding = false
    @State private var removingId: String?
    @State private var quietHours: KeywordAlertQuietHoursDto?
    @State private var quietHoursLoaded = false
    @State private var quietStart = "22:00"
    @State private var quietEnd = "08:00"
    @State private var savingQuietHours = false
    @State private var error: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                IdsTextField("Alert me for (e.g. iPhone 15)", text: $keyword)
                Button(action: { Task { await addAlert() } }) {
                    Text(adding ? "…" : "Add").font(.caption).bold().foregroundColor(.white)
                        .padding(.horizontal, 14).padding(.vertical, 8)
                        .background(IDS.Colors.brand).cornerRadius(10)
                }
                .disabled(adding || keyword.trimmingCharacters(in: .whitespaces).isEmpty)
            }
            if let error {
                Text(error).font(.caption).foregroundColor(.red)
            }
            if alerts == nil {
                HoodFeedSkeleton()
            } else if alerts!.isEmpty {
                EmptyStateView("No keyword alerts yet -- add one to get notified when a matching listing is posted.")
                    .foregroundColor(IDS.Colors.textSecondary)
            } else {
                // Real fix (2026-08-24, flat-design sweep): dropped the per-row Card --
                // an entity/management list (keyword alerts), no divider.
                ForEach(alerts!) { a in
                    HStack {
                        Text(a.keyword).font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
                        Spacer()
                        Button(action: { Task { await removeAlert(a.id) } }) {
                            Text(removingId == a.id ? "Removing…" : "Remove")
                                .font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
                                .padding(.horizontal, 12).padding(.vertical, 8)
                                .background(IDS.Colors.chipBackground).cornerRadius(10)
                        }
                        .disabled(removingId == a.id)
                    }
                    .padding(.vertical, 10)
                }
            }
            if quietHoursLoaded {
                // Real fix (2026-08-24, flat-design sweep): dropped the Card wrapper --
                // a sibling section shown together with the alerts list above, so a real
                // Divider marks the boundary instead.
                Divider().overlay(IDS.Colors.divider)
                VStack(alignment: .leading, spacing: 8) {
                    Text("Quiet hours").font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
                    Text("Don't send alert notifications during these hours.")
                        .font(.caption).foregroundColor(IDS.Colors.textSecondary)
                    HStack(spacing: 8) {
                        IdsTextField("Start (HH:mm)", text: $quietStart)
                        IdsTextField("End (HH:mm)", text: $quietEnd)
                    }
                    Button(action: { Task { await saveQuietHours(enabled: quietHours?.enabled != true) } }) {
                        Text(savingQuietHours ? "…" : (quietHours?.enabled == true ? "Turn off quiet hours" : "Turn on quiet hours"))
                            .font(.caption).bold()
                            .foregroundColor(quietHours?.enabled != true ? .white : IDS.Colors.textPrimary)
                            .frame(maxWidth: .infinity).padding(.vertical, 10)
                            .background(quietHours?.enabled != true ? IDS.Colors.brand : IDS.Colors.chipBackground)
                            .cornerRadius(10)
                    }
                    .disabled(savingQuietHours)
                }
            }
        }
        .task { await load() }
    }

    private func load() async {
        do {
            let res = try await NetworkClient.shared.getKeywordAlerts()
            alerts = res.alerts
            error = nil
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
        do {
            let qh = try await NetworkClient.shared.getKeywordAlertQuietHours().quietHours
            quietHours = qh
            if let qh { quietStart = qh.startTime; quietEnd = qh.endTime }
        } catch {
            // Non-critical -- the quiet-hours card just stays hidden.
        }
        quietHoursLoaded = true
    }

    private func addAlert() async {
        let trimmed = keyword.trimmingCharacters(in: .whitespaces)
        if trimmed.isEmpty { return }
        adding = true
        defer { adding = false }
        do {
            _ = try await NetworkClient.shared.addKeywordAlert(keyword: trimmed)
            keyword = ""
            await load()
        } catch {
            self.error = "Couldn't add this alert. Check your connection and try again."
        }
    }

    private func removeAlert(_ alertId: String) async {
        removingId = alertId
        defer { removingId = nil }
        do {
            _ = try await NetworkClient.shared.removeKeywordAlert(id: alertId)
            alerts = alerts?.filter { $0.id != alertId }
        } catch {
            self.error = "Couldn't remove this alert. Check your connection and try again."
        }
    }

    private func saveQuietHours(enabled: Bool) async {
        savingQuietHours = true
        defer { savingQuietHours = false }
        do {
            quietHours = try await NetworkClient.shared.setKeywordAlertQuietHours(startTime: quietStart, endTime: quietEnd, enabled: enabled).quietHours
            error = nil
        } catch {
            self.error = "Couldn't update quiet hours. Check your connection and try again."
        }
    }
}

// ============================== COMMUNITY (동네생활) ==============================

