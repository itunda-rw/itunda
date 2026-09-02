import SwiftUI
import UIKit
import CoreLocation
import CoreDesignSystem
import CoreNetwork

// Real gap found 2026-08-30 (project_itunda_money_formatting_sweep's own standing
// convention -- comma thousands-separator for every whole-number RWF amount --
// never reached this file). Same per-file shape TransactionHistoryScreen.swift
// already established.
private func formatAmount(_ value: Int) -> String {
    let formatter = NumberFormatter()
    formatter.numberStyle = .decimal
    formatter.groupingSeparator = ","
    return formatter.string(from: NSNumber(value: value)) ?? "0"
}

struct ListingCard: View {
    let listing: ListingDto
    let isMine: Bool
    let onChanged: () -> Void
    let onMessageSeller: (String) -> Void
    let onMakeOffer: (String, Double) -> Void
    // Real Marketplace listing wishlist (2026-07-21) -- state is lifted to
    // MarketplaceContent (mirroring the already-real lifted-favoriteIds pattern used
    // for Eats favorite restaurants) so the heart stays correct across Browse/
    // Neighborhood/My-listings without a per-card refetch.
    var favorited: Bool = false
    var favoriteBusy: Bool = false
    var onToggleFavorite: () -> Void = {}
    var sellerTrustScore: Int?
    var currentUserId: String?

    @State private var busy = false
    @State private var error: String?
    // Real Karrot "이 글 숨기기" (hide this post) -- see backend ListingHideService's
    // own doc comment. Real, shipped on the backend + bank-mfe (2026-08-24) with zero
    // iOS client until now -- found via a cross-platform-parity check.
    @State private var hiding = false
    @State private var offering = false
    @State private var offerAmount = ""
    @State private var showingSafetyChecklist = false
    @State private var showingReportOptions = false

    // Real optional buyer identification at mark-sold time (2026-07-24) -- see backend
    // MarketplaceService.markSold's own doc comment. Confirm with a phone number or
    // Skip, either way the sale completes.
    @State private var markingSold = false
    @State private var buyerPhone = ""

    // Real seller-paid sponsored placement -- see backend
    // MarketplaceService.boostListing's own doc comment. Android already has this;
    // this is the first iOS client.
    @State private var showBoostPicker = false
    @State private var boostTiers: [String: Double]?
    @State private var boosting = false

    // Real post-transaction review with asymmetric public/private visibility
    // (2026-07-24) -- see backend HoodReviewService's own doc comment.
    @State private var showReviewSheet = false
    @State private var selectedGoodPoints: Set<String> = []
    @State private var selectedUncomfortablePoints: Set<String> = []
    @State private var submittingReview = false
    @State private var reviewSubmitted = false
    // Real read-back for the review above (item 192/198/199) -- see bank-mfe's
    // HoodReviewResultView (item 192) for the full account.
    @State private var hoodReviews: [HoodReviewDto]?

    // Real "directions to this seller" (2026-07-19, item 8 on the Maps "100%" roadmap) --
    // reuses itunda's own self-hosted OSRM directions, same RouteMiniMap component Eats
    // orders use.
    @State private var myLocation: CLLocationCoordinate2D?
    @State private var showRoute = false
    @StateObject private var locationFetcher = HoodLocationFetcher()

    // Real "pay via itunda" Marketplace escrow -- see NetworkClient's own
    // MarketplaceEscrowDto doc comment for the full account (2026-08-15, first iOS
    // client). Escrow status/delivery address fetched+shown for BOTH the buyer and
    // seller of a SOLD listing, mirroring the real bug found+fixed on Android and
    // web's own client built the same session.
    @State private var paying = false
    @State private var deliveryAddress = ""
    @State private var escrow: MarketplaceEscrowDto?
    @State private var loadedEscrow = false
    @State private var showDispute = false
    @State private var disputeReason = ""
    @State private var resolvingEscrow = false
    private var isMyEscrowTrade: Bool {
        listing.status == "SOLD" && currentUserId != nil && (listing.sellerId == currentUserId || listing.buyerId == currentUserId)
    }
    private var isEscrowBuyer: Bool { isMyEscrowTrade && !isMine }

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    HStack {
                        Text(listing.title).font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
                        if listing.status == "SOLD" {
                            Text("SOLD")
                                .font(.caption2).bold()
                                .foregroundColor(IDS.Colors.textSecondary)
                                .padding(.horizontal, 8).padding(.vertical, 2)
                                .background(IDS.Colors.chipBackground)
                                .cornerRadius(8)
                        }
                        // Real "Sponsored" badge -- only ever shown for a listing with a
                        // real, still-future boostedUntil, matching Coupang/Baemin's own
                        // real sponsored-placement labeling convention.
                        if isListingBoosted(listing.boostedUntil) {
                            Text("Sponsored")
                                .font(.caption2).bold().foregroundColor(.white)
                                .padding(.horizontal, 8).padding(.vertical, 2)
                                .background(IDS.Colors.brand)
                                .cornerRadius(6)
                        }
                    }
                    Text("\(listing.category) · \(hoodRelativeTime(listing.createdAt))").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                }
                Spacer()
                if !isMine {
                    Button(action: { Task { await hideListing() } }) {
                        Text(hiding ? "…" : "Hide")
                            .font(.caption2)
                            .foregroundColor(IDS.Colors.textSecondary)
                    }
                    .accessibilityLabel("Hide this listing -- you won't see it again")
                    .disabled(hiding)
                    .padding(.trailing, 8)
                    Button(action: onToggleFavorite) {
                        WishlistHeart(favorited: favorited, size: 18)
                    }
                    .accessibilityLabel(favorited ? "Remove from favorites" : "Add to favorites")
                    .disabled(favoriteBusy)
                    .padding(.trailing, 6)
                }
                Text("\(formatAmount(Int(listing.price))) RWF").font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
            }
            // Real seller-uploaded photo (2026-08-01) -- see NetworkClient.swift's own
            // doc comment on ListingDto.photoUrl. Android already renders this; iOS
            // never had a photo field at all until now.
            if let photoUrlString = listing.photoUrl, let photoUrl = URL(string: photoUrlString) {
                AsyncImage(url: photoUrl) { phase in
                    if let image = phase.image {
                        image.resizable().aspectRatio(contentMode: .fill)
                    } else {
                        IDS.Colors.chipBackground
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: 220)
                .clipped()
                .cornerRadius(12)
            }
            // Real Karrot-Score trust badge (2026-07-24) -- see TrustBadge's own doc
            // comment. Only shown for someone else's listing.
            if !isMine, let sellerTrustScore {
                TrustBadge(score: sellerTrustScore)
            }
            Text(listing.description).font(.subheadline).foregroundColor(IDS.Colors.textSecondary)
            VehicleDetailSection(listing: listing)
            if let meetingPlace = listing.meetingPlace {
                Text("Suggested hand-off: \(meetingPlace)")
                    .font(.caption).foregroundColor(IDS.Colors.textSecondary)
            }
            Button(action: { showingSafetyChecklist.toggle() }) {
                Text(showingSafetyChecklist ? "Hide safety tips" : "Before you pay: safety tips")
                    .font(IDS.Typography.caption).foregroundColor(IDS.Colors.brand)
            }
            if showingSafetyChecklist {
                VStack(alignment: .leading, spacing: 5) {
                    Text("Before you meet or pay").font(IDS.Typography.sectionLabel).foregroundColor(IDS.Colors.textPrimary)
                    Text("• Meet in a safe public place\n• Inspect the item before payment\n• Keep the price and handover in Itunda chat\n• Never share a PIN or send money for an unseen item")
                        .font(IDS.Typography.caption).foregroundColor(IDS.Colors.textSecondary)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(12)
                .background(IDS.Colors.chipBackground)
                .cornerRadius(12)
            }
            if let error {
                Text(error).font(.caption).foregroundColor(.red)
            }
            if offering {
                HStack(spacing: 10) {
                    IdsTextField("Your offer (RWF)", text: $offerAmount, keyboardType: .numberPad)
                    Button(action: {
                        guard let amount = Double(offerAmount) else { return }
                        offering = false
                        offerAmount = ""
                        onMakeOffer(listing.id, amount)
                    }) {
                        Text("Send").font(.subheadline).bold().foregroundColor(.white)
                            .padding(.horizontal, 16).padding(.vertical, 10)
                            .background(IDS.Colors.brand).cornerRadius(12)
                    }
                    .disabled(Double(offerAmount) == nil)
                }
            }
            // Real optional "who bought this?" prompt (2026-07-24) -- see backend
            // MarketplaceService.markSold's own doc comment.
            if markingSold {
                IdsTextField("Buyer's phone (optional)", text: $buyerPhone, keyboardType: .phonePad)
                HStack(spacing: 10) {
                    actionButton("Skip", filled: false) { await markSold(buyerPhoneNumber: nil) }
                    // Real CTA-label-clarity fix (2026-08-24, docs/DESIGN_REFERENCES.md §11) --
                    // see the identical Jobs/Property fix + BankDashboard.tsx's
                    // VerificationRow (commit 58d58259) for the full sourced account.
                    actionButton("Mark as sold", filled: true) { await markSold(buyerPhoneNumber: buyerPhone.trimmingCharacters(in: .whitespaces)) }
                }
            }
            if isMine, listing.status == "SOLD", listing.buyerId != nil, reviewSubmitted, let hoodReviews {
                HoodReviewResultView(reviews: hoodReviews, myUserId: currentUserId)
            }
            // Real post-transaction review, preset checklist with asymmetric public/
            // private visibility (2026-07-24) -- see backend HoodReviewService's own
            // doc comment. Only offered once a real buyer was recorded at mark-sold
            // time.
            if isMine, listing.status == "SOLD", listing.buyerId != nil, !reviewSubmitted {
                if showReviewSheet {
                    HoodReviewForm(
                        selectedGoodPoints: $selectedGoodPoints,
                        selectedUncomfortablePoints: $selectedUncomfortablePoints,
                        submitting: submittingReview,
                        onCancel: { showReviewSheet = false },
                        onSubmit: { await submitReview() }
                    )
                } else {
                    actionButton("Rate this buyer", filled: true) { showReviewSheet = true }
                }
            }
            // Real seller-paid sponsored placement picker -- see backend
            // MarketplaceService.boostListing's own doc comment for the real flat-fee
            // tiers (never client-invented -- fetched from GET /boost-tiers).
            if showBoostPicker {
                if let boostTiers {
                    if boostTiers.isEmpty {
                        Text("Couldn't load boost options. Try again.").font(.footnote).foregroundColor(.red)
                    } else {
                        Text("Boost this listing to the top of search results").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                        HStack(spacing: 8) {
                            ForEach(boostTiers.sorted { (Int($0.key) ?? 0) < (Int($1.key) ?? 0) }, id: \.key) { days, price in
                                actionButton(boosting ? "…" : "\(days)d · \(formatAmount(Int(price))) RWF", filled: true) { await boost(days: Int(days) ?? 0) }
                            }
                        }
                        actionButton("Cancel", filled: false) { showBoostPicker = false }
                    }
                } else {
                    Text("Loading boost options…").font(.footnote).foregroundColor(IDS.Colors.textSecondary)
                }
            }
            // Real gap closed 2026-08-15 -- see NetworkClient's own MarketplaceEscrowDto
            // doc comment (당근마켓 바로구매-style shipped-item support). Deliberately
            // optional and blank by default: the original in-person handoff still
            // works with nothing typed here.
            if !isMine, listing.status == "ACTIVE", !offering {
                TextField("Delivery address (optional, for a shipped item)", text: $deliveryAddress)
                    .textFieldStyle(.roundedBorder)
                    .font(.footnote)
            }
            HStack(spacing: 10) {
                if isMine {
                    if listing.status == "ACTIVE" && !markingSold {
                        actionButton("Mark sold", filled: false) { markingSold = true }
                        actionButton("Boost", filled: false) {
                            showBoostPicker = true
                            if boostTiers == nil {
                                boostTiers = (try? await NetworkClient.shared.getBoostTiers())?.tiers ?? [:]
                            }
                        }
                    }
                    if listing.status != "REMOVED" {
                        actionButton("Remove", filled: false) { await remove() }
                    }
                } else if listing.status == "ACTIVE" && !offering {
                    actionButton(busy ? "Starting…" : "Message seller", filled: false) {
                        onMessageSeller(listing.id)
                    }
                    actionButton("Make an offer", filled: true) { offering = true }
                    // Real "pay via itunda" Marketplace escrow -- an opt-in safer
                    // alternative to the existing in-person cash handoff, never
                    // replacing it. First iOS client (2026-08-15).
                    actionButton(paying ? "Paying…" : "Pay via itunda", filled: false, icon: paying ? nil : AnyView(LockGlyph(size: 14))) { await payViaItunda() }
                    actionButton("Report", filled: false) { showingReportOptions = true }
                }
            }
            // Real escrow status -- shown to BOTH the buyer and seller of a SOLD
            // listing (the real backend already allows both to read it), not
            // buyer-only, so a seller can actually see the real delivery address a
            // buyer typed in. Confirm receipt/Report a problem stay buyer-only
            // actions -- only the buyer can judge whether the real item arrived.
            if isMyEscrowTrade, let escrow {
                VStack(alignment: .leading, spacing: 8) {
                    if let deliveryAddress = escrow.deliveryAddress {
                        HStack(spacing: 5) {
                            PackageGlyph(size: 13)
                            Text("Delivery address: \(deliveryAddress)")
                        }
                        .font(.footnote).foregroundColor(IDS.Colors.textSecondary)
                    }
                    if escrow.status == "HELD" {
                        HStack(spacing: 5) {
                            LockGlyph(size: 12)
                            Text(isEscrowBuyer ? "Payment held by itunda until you confirm receipt" : "Payment held by itunda until the buyer confirms receipt")
                        }
                            .font(.footnote).foregroundColor(IDS.Colors.textSecondary)
                        if isEscrowBuyer {
                            if showDispute {
                                TextField("What went wrong?", text: $disputeReason)
                                    .textFieldStyle(.roundedBorder)
                                    .font(.footnote)
                                HStack(spacing: 10) {
                                    actionButton("Cancel", filled: false) { showDispute = false }
                                    actionButton("Submit", filled: true) { await submitDispute() }
                                }
                            } else {
                                HStack(spacing: 10) {
                                    actionButton(resolvingEscrow ? "Working…" : "Confirm receipt", filled: true) { await confirmReceipt() }
                                    actionButton("Report a problem", filled: false) { showDispute = true }
                                }
                            }
                        }
                    } else if escrow.status == "DISPUTED" {
                        Text("⚠️ Reported -- itunda is reviewing this trade").font(.footnote).foregroundColor(.red)
                    } else if escrow.status == "RELEASED" {
                        Text("✅ Payment released to the seller").font(.footnote).foregroundColor(.green)
                    } else if escrow.status == "REFUNDED" {
                        Text(isEscrowBuyer ? "↩️ Refunded to you" : "↩️ Refunded to the buyer").font(.footnote).foregroundColor(.green)
                    }
                }
            }
            if !isMine, listing.status == "ACTIVE", let toLat = listing.latitude, let toLng = listing.longitude {
                Button(action: {
                    if showRoute { showRoute = false } else if myLocation != nil { showRoute = true } else { locationFetcher.requestLocation() }
                }) {
                    Text(showRoute ? "Hide directions" : "🚗 Directions to this seller")
                        .font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                        .padding(.horizontal, 16).padding(.vertical, 10)
                        .background(IDS.Colors.chipBackground).cornerRadius(12)
                }
                if showRoute, let myLocation {
                    RouteMiniMap(fromLat: myLocation.latitude, fromLng: myLocation.longitude, toLat: toLat, toLng: toLng, fromLabel: "You", toLabel: listing.title)
                }
            }
        }
        .padding(18)
        .background(IDS.Colors.card)
        .cornerRadius(IDS.Layout.cardCornerRadius).idsCardBorder(cornerRadius: IDS.Layout.cardCornerRadius)
        .confirmationDialog("Report this listing", isPresented: $showingReportOptions, titleVisibility: .visible) {
            Button("Item is unavailable or misleading") { Task { await report("The item appears unavailable or misleading") } }
            Button("Unsafe payment or contact request") { Task { await report("The seller made an unsafe payment or contact request") } }
            Button("Prohibited or suspicious item") { Task { await report("The item appears prohibited or suspicious") } }
        } message: { Text("Reports go to Itunda’s review queue.") }
        .onAppear {
            locationFetcher.onLocation = { coordinate in
                myLocation = coordinate
                showRoute = true
            }
        }
        .onChange(of: locationFetcher.errorMessage) { newValue in
            if let newValue { error = newValue }
        }
        .task { await loadHoodReviews() }
        .task { await loadEscrow() }
    }

    // Real escrow status fetch -- see NetworkClient's own MarketplaceEscrowDto doc
    // comment for the buyer+seller visibility account.
    private func loadEscrow() async {
        guard isMyEscrowTrade, !loadedEscrow else { return }
        escrow = try? await NetworkClient.shared.getEscrow(listing.id).escrow
        loadedEscrow = true
    }

    private func payViaItunda() async {
        paying = true
        defer { paying = false }
        do {
            _ = try await NetworkClient.shared.payEscrow(listing.id, deliveryAddress: deliveryAddress)
            onChanged()
        } catch let NetworkError.httpError(statusCode) {
            error = TalkScreen.errorMessage(statusCode)
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }

    private func confirmReceipt() async {
        resolvingEscrow = true
        defer { resolvingEscrow = false }
        do {
            escrow = try await NetworkClient.shared.confirmEscrowReceipt(listing.id).escrow
        } catch let NetworkError.httpError(statusCode) {
            error = TalkScreen.errorMessage(statusCode)
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }

    private func submitDispute() async {
        guard !disputeReason.trimmingCharacters(in: .whitespaces).isEmpty else { return }
        resolvingEscrow = true
        defer { resolvingEscrow = false }
        do {
            escrow = try await NetworkClient.shared.disputeEscrow(listing.id, reason: disputeReason.trimmingCharacters(in: .whitespaces)).escrow
            showDispute = false
        } catch let NetworkError.httpError(statusCode) {
            error = TalkScreen.errorMessage(statusCode)
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }

    // Real read-back for the review above (item 192/198/199).
    private func loadHoodReviews() async {
        guard isMine, listing.status == "SOLD", listing.buyerId != nil else { return }
        do {
            let reviews = try await NetworkClient.shared.getListingReviews(listing.id).reviews
            hoodReviews = reviews
            if reviews.contains(where: { $0.reviewerId == currentUserId }) { reviewSubmitted = true }
        } catch {
            // Real, non-critical -- the review form itself still works without this.
        }
    }

    private func actionButton(_ label: String, filled: Bool, icon: AnyView? = nil, action: @escaping () async -> Void) -> some View {
        Button(action: { Task { await action() } }) {
            HStack(spacing: 6) {
                if let icon { icon }
                Text(label)
            }
                .font(.subheadline).bold()
                .foregroundColor(filled ? .white : IDS.Colors.textPrimary)
                .padding(.horizontal, 16).padding(.vertical, 10)
                .background(filled ? IDS.Colors.brand : IDS.Colors.chipBackground)
                .cornerRadius(12)
        }
        .disabled(busy)
    }

    private func markSold(buyerPhoneNumber: String?) async {
        busy = true
        defer { busy = false }
        do {
            _ = try await NetworkClient.shared.markListingSold(listing.id, buyerPhoneNumber: buyerPhoneNumber?.isEmpty == true ? nil : buyerPhoneNumber)
            markingSold = false
            onChanged()
        } catch let NetworkError.httpError(statusCode) {
            error = TalkScreen.errorMessage(statusCode)
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }

    // Real post-transaction review with asymmetric public/private visibility
    // (2026-07-24) -- see backend HoodReviewService's own doc comment.
    private func submitReview() async {
        submittingReview = true
        defer { submittingReview = false }
        do {
            let res = try await NetworkClient.shared.submitListingReview(
                listing.id, goodPoints: Array(selectedGoodPoints), uncomfortablePoints: Array(selectedUncomfortablePoints),
            )
            reviewSubmitted = true
            showReviewSheet = false
            hoodReviews = (hoodReviews ?? []) + [res.review]
        } catch let NetworkError.httpError(statusCode) {
            error = TalkScreen.errorMessage(statusCode)
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }

    // Real Karrot "이 글 숨기기" (hide this post) -- backend excludes this listing from
    // browse, so calling onChanged() (a real refetch) is what makes it disappear,
    // matching bank-mfe's own hideListing() usage exactly. Fails silently, same
    // non-blocking discipline as toggleFavorite -- a failed hide just means the
    // listing is still visible.
    private func hideListing() async {
        hiding = true
        defer { hiding = false }
        do {
            _ = try await NetworkClient.shared.hideListing(listing.id)
            onChanged()
        } catch {
            // Real, non-critical -- see doc comment above.
        }
    }

    private func remove() async {
        busy = true
        defer { busy = false }
        do {
            _ = try await NetworkClient.shared.removeListing(listing.id)
            onChanged()
        } catch let NetworkError.httpError(statusCode) {
            error = TalkScreen.errorMessage(statusCode)
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }

    private func boost(days: Int) async {
        boosting = true
        error = nil
        defer { boosting = false }
        do {
            _ = try await NetworkClient.shared.boostListing(listing.id, days: days)
            showBoostPicker = false
            onChanged()
        } catch let NetworkError.httpError(statusCode) {
            error = TalkScreen.errorMessage(statusCode)
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }

    private func report(_ reason: String) async {
        busy = true
        defer { busy = false }
        do {
            _ = try await NetworkClient.shared.reportHoodContent(targetType: "MARKETPLACE_LISTING", targetId: listing.id, reason: reason)
            error = "Thanks. Your report was sent for review."
        } catch let NetworkError.httpError(statusCode) where statusCode == 409 {
            error = "You already reported this listing."
        } catch {
            self.error = "Couldn't send the report. Check your connection and try again."
        }
    }
}

