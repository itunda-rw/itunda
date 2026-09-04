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

struct PropertyWishlistView: View {
    let onRemoved: () -> Void
    @State private var favorites: [FavoritePropertyListingDto]?
    @State private var error: String?
    var body: some View {
        Group {
            if let error { ErrorCardView(error) { Task { await load() } } }
            else if favorites == nil { HoodFeedSkeleton() }
            else if favorites!.isEmpty { Text("No saved properties yet — tap ♡ on a property to keep it here.").foregroundColor(IDS.Colors.textSecondary) }
            // Real fix (2026-08-24, flat-design sweep): dropped the per-row Card --
            // matches ListingWishlistView's identical entity-list conversion, no divider.
            else { ForEach(favorites!) { favorite in HStack { VStack(alignment: .leading) { Text(favorite.title).font(IDS.Typography.bodyBold); Text("\(favorite.listingType == "RENT" ? "For rent" : "For sale") · \(formatAmount(Int(favorite.price))) RWF").font(.caption).foregroundColor(IDS.Colors.textSecondary) }; Spacer(); Button("Remove") { Task { await remove(favorite.propertyListingId) } }.font(.caption).padding(8).background(IDS.Colors.chipBackground).cornerRadius(8) }.padding(.vertical, 10) } }
        }.task { await load() }
    }
    private func load() async { do { favorites = try await NetworkClient.shared.getMyFavoritePropertyListings().favorites; error = nil } catch { self.error = "Couldn't load your saved properties. Check your connection and try again." } }
    private func remove(_ id: String) async { do { _ = try await NetworkClient.shared.removePropertyListingFavorite(id); favorites?.removeAll { $0.propertyListingId == id }; onRemoved() } catch { self.error = "Couldn't remove this saved property. Check your connection and try again." } }
}

// Extracted into its own concretely-typed View (2026-07-22) -- the previous inline
// ForEach body (computing propertyTypeLabel/isMine inline via .first(where:)/??/||
// directly inside PropertyListingCard's call) made the surrounding PropertyView.body
// (a large ScrollView/VStack with many conditional branches) time out real Swift
// type-checking ("unable to type-check this expression in reasonable time") -- a real
// build failure found live via xcodebuild against a real Simulator, not assumed.
// Wrapping the row in its own struct with a concrete initializer resolves it, the
// standard fix for this class of SwiftUI compiler timeout.
struct PropertyListingRow: View {
    let listing: PropertyListingDto
    let propertyTypeLabel: String
    let isMine: Bool
    let favorited: Bool
    let favoriteBusy: Bool
    let onChanged: () -> Void
    let onToggleFavorite: () -> Void
    let onContact: () -> Void
    let onMakeOffer: (String, Double) -> Void
    var listerTrustScore: Int?
    var currentUserId: String?

    var body: some View {
        PropertyListingCard(
            listing: listing,
            propertyTypeLabel: propertyTypeLabel,
            isMine: isMine,
            onChanged: onChanged,
            onContact: onContact,
            onMakeOffer: onMakeOffer,
            favorited: favorited,
            favoriteBusy: favoriteBusy,
            onToggleFavorite: onToggleFavorite,
            listerTrustScore: listerTrustScore,
            currentUserId: currentUserId
        )
    }
}

struct PropertyListingCard: View {
    let listing: PropertyListingDto
    let propertyTypeLabel: String
    let isMine: Bool
    let onChanged: () -> Void
    let onContact: () -> Void
    let onMakeOffer: (String, Double) -> Void
    var favorited: Bool = false
    var favoriteBusy: Bool = false
    var onToggleFavorite: () -> Void = {}
    var listerTrustScore: Int?
    var currentUserId: String?

    @State private var busy = false
    @State private var error: String?
    // Real 당근-style price-offer negotiation (2026-07-19) -- see
    // PropertyPriceOfferService's own doc comment; mirrors ListingCard's own offering
    // state exactly.
    @State private var offering = false
    @State private var offerAmount = ""
    @State private var showingSafetyChecklist = false
    @State private var showingReportOptions = false
    @State private var myLocation: CLLocationCoordinate2D?
    @State private var showRoute = false
    @StateObject private var locationFetcher = HoodLocationFetcher()

    // Real optional buyer/tenant identification at mark-taken time (2026-07-24) --
    // see backend PropertyListingService.markTaken's own doc comment.
    @State private var markingTaken = false
    @State private var counterpartyPhone = ""

    // Real Karrot(당근마켓)-style price-drop notification -- see backend
    // PropertyListingService.updatePrice's own doc comment. Real, shipped on the
    // backend + bank-mfe with zero iOS client until now -- found via a
    // cross-platform-parity check.
    @State private var editingPrice = false
    @State private var newPrice = ""

    // Real post-transaction review with asymmetric public/private visibility
    // (2026-07-24) -- see backend HoodReviewService's own doc comment.
    @State private var showReviewSheet = false
    @State private var selectedGoodPoints: Set<String> = []
    @State private var selectedUncomfortablePoints: Set<String> = []
    @State private var submittingReview = false
    @State private var reviewSubmitted = false
    // Real read-back for the review above (item 192/198/199).
    @State private var hoodReviews: [HoodReviewDto]?

    // Real ownership verification (2026-07-25, real photo upload added 2026-08-01) --
    // see NetworkClient.swift's own doc comment on uploadPhoto/
    // submitPropertyOwnershipVerification. submittedStatus is local so "pending" shows
    // immediately without a full listing refetch, mirroring Android's own
    // submittedOwnershipStatus in PropertyScreen.kt (which this now matches field-for-
    // field: a real UIImagePickerController -> uploadPhoto -> submit chain, not a
    // paste-a-URL text field).
    @State private var showOwnershipForm = false
    @State private var showOwnershipPicker = false
    @State private var submittingOwnership = false
    @State private var submittedOwnershipStatus: String?

    private var ownershipStatus: String {
        submittedOwnershipStatus ?? listing.ownershipVerificationStatus ?? "NONE"
    }

    private var priceLabel: String {
        let base = "\(formatAmount(Int(listing.price))) RWF"
        return listing.listingType == "RENT" ? "\(base)/mo" : base
    }

    private var detailsLabel: String {
        var parts: [String] = []
        if let bedrooms = listing.bedrooms { parts.append("\(bedrooms) bd") }
        if let sizeSqm = listing.sizeSqm { parts.append("\(Int(sizeSqm)) m²") }
        return parts.joined(separator: " · ")
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                HStack(spacing: 6) {
                    Text("\(listing.listingType == "RENT" ? "For rent" : "For sale") · \(propertyTypeLabel) · \(hoodRelativeTime(listing.createdAt))")
                        .font(.caption2).bold().foregroundColor(IDS.Colors.brand)
                    if listing.status == "TAKEN" {
                        Text("TAKEN").font(.caption2).bold().foregroundColor(IDS.Colors.textSecondary)
                            .padding(.horizontal, 8).padding(.vertical, 2)
                            .background(IDS.Colors.chipBackground).cornerRadius(8)
                    }
                }
                Spacer()
                if !isMine { Button(action: onToggleFavorite) { WishlistHeart(favorited: favorited, size: 18) }.accessibilityLabel(favorited ? "Remove from favorites" : "Add to favorites").disabled(favoriteBusy).padding(.trailing, 6) }
                Text(priceLabel).font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
            }
            Text(listing.title).font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
            if !detailsLabel.isEmpty {
                Text(detailsLabel).font(.caption).foregroundColor(IDS.Colors.textSecondary)
            }
            // Real Karrot-Score trust badge (2026-07-24) -- see TrustBadge's own doc
            // comment. Only shown for someone else's listing.
            if !isMine, let listerTrustScore {
                TrustBadge(score: listerTrustScore)
            }
            // Real ownership verification badge (2026-07-25) -- shown to every viewer,
            // not just the lister, a trust signal for the buyer/tenant deciding whether
            // to contact this listing. Mirrors PropertyScreen.kt's own badge exactly.
            if ownershipStatus == "VERIFIED" {
                Text("✓ Owner verified").font(.caption2).bold().foregroundColor(IDS.Colors.brand)
                    .padding(.horizontal, 8).padding(.vertical, 2)
                    .background(IDS.Colors.brand.opacity(0.12)).cornerRadius(8)
            }
            Text(listing.description).font(.subheadline).foregroundColor(IDS.Colors.textSecondary)
            if let neighborhood = listing.neighborhood {
                NeighborhoodReviewsSection(neighborhood: neighborhood)
            }
            if offering {
                HStack(spacing: 8) {
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
            if let error {
                Text(error).font(.caption).foregroundColor(.red)
            }
            // Real optional "who's the buyer/tenant?" prompt (2026-07-24) -- see
            // backend PropertyListingService.markTaken's own doc comment.
            if markingTaken {
                IdsTextField("Their phone (optional)", text: $counterpartyPhone, keyboardType: .phonePad)
                HStack(spacing: 10) {
                    actionButton("Skip", filled: false) { await markTaken(counterpartyPhoneNumber: nil) }
                    // Real CTA-label-clarity fix (2026-08-24, docs/DESIGN_REFERENCES.md §11) --
                    // see the identical Jobs/Marketplace fix + BankDashboard.tsx's
                    // VerificationRow (commit 58d58259) for the full sourced account.
                    actionButton("Mark as taken", filled: true) { await markTaken(counterpartyPhoneNumber: counterpartyPhone.trimmingCharacters(in: .whitespaces)) }
                }
            }
            // Real post-transaction review, preset checklist with asymmetric public/
            // private visibility (2026-07-24) -- see backend HoodReviewService's own
            // doc comment. Only offered once a real counterparty was recorded at
            // mark-taken time; no pre-check for "already reviewed" (a real, honest v1
            // -- a second attempt just surfaces the backend's own
            // REVIEW_ALREADY_SUBMITTED error).
            if isMine, listing.status == "TAKEN", listing.counterpartyId != nil, reviewSubmitted, let hoodReviews {
                HoodReviewResultView(reviews: hoodReviews, myUserId: currentUserId)
            }
            if isMine, listing.status == "TAKEN", listing.counterpartyId != nil, !reviewSubmitted {
                if showReviewSheet {
                    HoodReviewForm(
                        selectedGoodPoints: $selectedGoodPoints,
                        selectedUncomfortablePoints: $selectedUncomfortablePoints,
                        submitting: submittingReview,
                        onCancel: { showReviewSheet = false },
                        onSubmit: { await submitReview() }
                    )
                } else {
                    actionButton(listing.listingType == "RENT" ? "Rate this tenant" : "Rate this buyer", filled: true) { showReviewSheet = true }
                }
            }
            // Real ownership verification action (2026-07-25) -- NONE -> offer to
            // submit a document URL; PENDING -> awaiting a real human reviewer,
            // nothing to do; VERIFIED -> already covered by the badge above.
            if isMine, ownershipStatus == "NONE" {
                if showOwnershipForm {
                    HStack(spacing: 10) {
                        actionButton("Cancel", filled: false) { showOwnershipForm = false }
                        actionButton(submittingOwnership ? "Uploading…" : "Choose a deed/title photo", filled: true) { showOwnershipPicker = true }
                    }
                    .disabled(submittingOwnership)
                    .sheet(isPresented: $showOwnershipPicker) {
                        ImagePickerView { image in
                            showOwnershipPicker = false
                            if let image { Task { await submitOwnership(image: image) } }
                        }
                    }
                } else {
                    actionButton("Verify ownership", filled: false) { showOwnershipForm = true }
                }
            }
            if isMine, ownershipStatus == "PENDING" {
                Text("Verification pending review").font(.caption).foregroundColor(IDS.Colors.textSecondary)
            }
            // Real Karrot(당근마켓)-style price-drop notification -- see backend
            // PropertyListingService.updatePrice's own doc comment.
            if isMine, editingPrice {
                TextField("New price (RWF)", text: $newPrice)
                    .textFieldStyle(.roundedBorder)
                    .keyboardType(.numberPad)
                HStack(spacing: 10) {
                    actionButton("Cancel", filled: false) { editingPrice = false }
                    actionButton(busy ? "Saving…" : "Save", filled: true) { await updatePrice() }
                }
            }
            HStack(spacing: 10) {
                if isMine {
                    if listing.status == "AVAILABLE" && !markingTaken && !editingPrice {
                        actionButton("Mark taken", filled: false) { markingTaken = true }
                        actionButton("Edit price", filled: false) {
                            newPrice = String(Int(listing.price))
                            editingPrice = true
                        }
                    }
                    if listing.status != "REMOVED" {
                        actionButton("Remove", filled: false) { await remove() }
                    }
                } else if listing.status == "AVAILABLE" && !offering {
                    actionButton("Message lister", filled: false) { onContact() }
                    actionButton("Make an offer", filled: true) { offering = true }
                    actionButton("Report", filled: false) { showingReportOptions = true }
                }
            }
            if !isMine, listing.status == "AVAILABLE", let toLat = listing.latitude, let toLng = listing.longitude {
                Button(action: {
                    if showRoute { showRoute = false } else if myLocation != nil { showRoute = true } else { locationFetcher.requestLocation() }
                }) {
                    Text(showRoute ? "Hide directions" : "🚗 Directions to this property")
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
        .confirmationDialog("Report this property", isPresented: $showingReportOptions, titleVisibility: .visible) {
            Button("Suspected fake or unavailable property") { Task { await report("The property may be fake or unavailable") } }
            Button("Misleading price or property details") { Task { await report("The price or property details appear misleading") } }
            Button("Unsafe payment request") { Task { await report("The lister made an unsafe payment request") } }
        } message: { Text("Reports go to Itunda’s review queue.") }
        .onAppear {
            locationFetcher.onLocation = { coordinate in
                myLocation = coordinate
                showRoute = true
            }
        }
        .onChange(of: locationFetcher.errorMessage) { message in
            if let message { error = message }
        }
        .task { await loadHoodReviews() }
    }

    private func loadHoodReviews() async {
        guard isMine, listing.status == "TAKEN", listing.counterpartyId != nil else { return }
        do {
            let reviews = try await NetworkClient.shared.getPropertyListingReviews(listing.id).reviews
            hoodReviews = reviews
            if reviews.contains(where: { $0.reviewerId == currentUserId }) { reviewSubmitted = true }
        } catch { /* non-critical */ }
    }

    private func actionButton(_ label: String, filled: Bool, action: @escaping () async -> Void) -> some View {
        Button(action: { Task { await action() } }) {
            Text(label)
                .font(.subheadline).bold()
                .foregroundColor(filled ? .white : IDS.Colors.textPrimary)
                .padding(.horizontal, 16).padding(.vertical, 10)
                .background(filled ? IDS.Colors.brand : IDS.Colors.chipBackground)
                .cornerRadius(12)
        }
        .disabled(busy)
    }

    // Real ownership verification (2026-07-25, real photo upload added 2026-08-01) --
    // see NetworkClient.swift's own doc comment on uploadPhoto/
    // submitPropertyOwnershipVerification. Mirrors PropertyScreen.kt's real
    // upload-then-submit chain exactly.
    private func submitOwnership(image: UIImage) async {
        guard let jpegData = image.jpegData(compressionQuality: 0.8) else {
            self.error = "Couldn't read that photo."
            return
        }
        submittingOwnership = true
        defer { submittingOwnership = false }
        do {
            let uploaded = try await NetworkClient.shared.uploadPhoto(data: jpegData, filename: "ownership-doc.jpg", mimeType: "image/jpeg")
            _ = try await NetworkClient.shared.submitPropertyOwnershipVerification(listing.id, documentUrl: uploaded.url)
            submittedOwnershipStatus = "PENDING"
            showOwnershipForm = false
        } catch {
            self.error = "Could not upload or submit this document."
        }
    }

    private func markTaken(counterpartyPhoneNumber: String?) async {
        busy = true
        defer { busy = false }
        do {
            _ = try await NetworkClient.shared.markPropertyListingTaken(
                listing.id, counterpartyPhoneNumber: counterpartyPhoneNumber?.isEmpty == true ? nil : counterpartyPhoneNumber,
            )
            markingTaken = false
            onChanged()
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
            let res = try await NetworkClient.shared.submitPropertyListingReview(
                listing.id, goodPoints: Array(selectedGoodPoints), uncomfortablePoints: Array(selectedUncomfortablePoints),
            )
            reviewSubmitted = true
            showReviewSheet = false
            hoodReviews = (hoodReviews ?? []) + [res.review]
        } catch let NetworkError.httpErrorWithMessage(statusCode, message) {
            error = message ?? TalkScreen.errorMessage(statusCode)
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }

    private func remove() async {
        busy = true
        defer { busy = false }
        do {
            _ = try await NetworkClient.shared.removePropertyListing(listing.id)
            onChanged()
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }

    private func updatePrice() async {
        guard let price = Double(newPrice), price > 0 else { return }
        busy = true
        error = nil
        defer { busy = false }
        do {
            _ = try await NetworkClient.shared.updatePropertyListingPrice(listing.id, price: price)
            editingPrice = false
            onChanged()
        } catch let NetworkError.httpErrorWithMessage(statusCode, message) {
            error = message ?? TalkScreen.errorMessage(statusCode)
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }

    private func report(_ reason: String) async {
        busy = true
        defer { busy = false }
        do {
            _ = try await NetworkClient.shared.reportHoodContent(targetType: "PROPERTY_LISTING", targetId: listing.id, reason: reason)
            error = "Thanks. Your report was sent for review."
        } catch let NetworkError.httpError(statusCode) where statusCode == 409 {
            error = "You already reported this property."
        } catch {
            self.error = "Couldn't send the report. Check your connection and try again."
        }
    }
}
