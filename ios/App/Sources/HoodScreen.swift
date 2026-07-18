import SwiftUI
import CoreDesignSystem

/// Real 당근마켓 (Danggeun/Karrot Market)-style neighborhood marketplace (2026-07-18) --
/// iOS mirror of Android's HoodTab (SuperAppTabs.kt). See NetworkClient.swift's
/// Marketplace extension and rw.itunda.marketplace.MarketplaceService's own doc
/// comment for the full backend account, including the honest "no real location data"
/// scope this screen inherits unchanged.
struct HoodScreen: View {
    /// Real "message seller" hand-off to TalkScreen -- see TalkScreen.swift's own
    /// doc comment on `pendingConversationId` for the full mechanism.
    @Binding var pendingConversationId: String?
    let onSwitchToTalk: () -> Void

    private enum HoodView { case browse, mine }

    @State private var view: HoodView = .browse
    @State private var listings: [ListingDto]?
    @State private var error: String?
    @State private var showNewListing = false
    private let currentUserId = KeychainTokenStore.shared.getUserId()

    var body: some View {
        ScrollView {
            VStack(spacing: IDS.Layout.cardGap) {
                TdsPlainTopBar(title: "Hood")

                Picker("", selection: $view) {
                    Text("Browse").tag(HoodView.browse)
                    Text("My listings").tag(HoodView.mine)
                }
                .pickerStyle(.segmented)

                if view == .mine {
                    if showNewListing {
                        NewListingForm(onCreated: {
                            showNewListing = false
                            Task { await load() }
                        }, onCancel: { showNewListing = false })
                    } else {
                        Button(action: { showNewListing = true }) {
                            Text("+ List an item")
                                .font(IDS.Typography.bodyBold)
                                .foregroundColor(.white)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 14)
                                .background(IDS.Colors.brand)
                                .cornerRadius(14)
                        }
                    }
                }

                if let error {
                    VStack(alignment: .leading, spacing: 10) {
                        Text(error).foregroundColor(.red).font(.subheadline)
                        Button("Retry") { Task { await load() } }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(20)
                    .background(IDS.Colors.card)
                    .cornerRadius(IDS.Layout.cardCornerRadius)
                } else if listings == nil {
                    ProgressView().frame(maxWidth: .infinity, minHeight: 120)
                } else if listings!.isEmpty {
                    Text(view == .browse ? "No listings yet." : "You haven't listed anything yet.")
                        .foregroundColor(IDS.Colors.textSecondary)
                } else {
                    ForEach(listings!) { listing in
                        ListingCard(
                            listing: listing,
                            isMine: view == .mine || listing.sellerId == currentUserId,
                            onChanged: { Task { await load() } },
                            onMessageSeller: { id in Task { await messageSeller(id) } }
                        )
                    }
                }
            }
            .padding(.horizontal, IDS.Layout.screenHorizontal)
            .padding(.top, IDS.Layout.screenTop)
            .padding(.bottom, IDS.Layout.sectionSpacing)
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
        .task { await load() }
        .onChange(of: view) { _ in Task { await load() } }
    }

    private func load() async {
        listings = nil
        do {
            let res = view == .browse ? try await NetworkClient.shared.browseListings() : try await NetworkClient.shared.getMyListings()
            listings = res.listings
            error = nil
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }

    private func messageSeller(_ listingId: String) async {
        do {
            let res = try await NetworkClient.shared.contactSeller(listingId: listingId)
            pendingConversationId = res.conversation.id
            onSwitchToTalk()
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }
}

private struct NewListingForm: View {
    let onCreated: () -> Void
    let onCancel: () -> Void

    @State private var title = ""
    @State private var description = ""
    @State private var price = ""
    @State private var category = ""
    @State private var error: String?
    @State private var submitting = false

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("List an item").font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
            TextField("What are you selling?", text: $title).padding(12).background(IDS.Colors.chipBackground).cornerRadius(12)
            TextField("Description", text: $description).padding(12).background(IDS.Colors.chipBackground).cornerRadius(12)
            HStack {
                TextField("Price (RWF)", text: $price).keyboardType(.numberPad).padding(12).background(IDS.Colors.chipBackground).cornerRadius(12)
                TextField("Category", text: $category).padding(12).background(IDS.Colors.chipBackground).cornerRadius(12)
            }
            if let error {
                Text(error).font(.caption).foregroundColor(.red)
            }
            HStack {
                Button("Cancel", action: onCancel).frame(maxWidth: .infinity)
                Button(action: { Task { await submit() } }) {
                    Text(submitting ? "Listing…" : "List it")
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                        .background(IDS.Colors.brand)
                        .cornerRadius(14)
                }
                .disabled(submitting)
            }
        }
        .padding(20)
        .background(IDS.Colors.card)
        .cornerRadius(IDS.Layout.cardCornerRadius)
    }

    private func submit() async {
        guard let priceValue = Double(price), priceValue > 0, !title.isEmpty, !description.isEmpty, !category.isEmpty else {
            error = "Fill in every field with a real price."
            return
        }
        submitting = true
        error = nil
        defer { submitting = false }
        do {
            _ = try await NetworkClient.shared.createListing(title: title, description: description, price: priceValue, category: category)
            onCreated()
        } catch let NetworkError.httpError(statusCode) {
            error = TalkScreen.errorMessage(statusCode)
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }
}

private struct ListingCard: View {
    let listing: ListingDto
    let isMine: Bool
    let onChanged: () -> Void
    let onMessageSeller: (String) -> Void

    @State private var busy = false
    @State private var error: String?

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
                    }
                    Text(listing.category).font(.caption).foregroundColor(IDS.Colors.textSecondary)
                }
                Spacer()
                Text("\(Int(listing.price)) RWF").font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
            }
            Text(listing.description).font(.subheadline).foregroundColor(IDS.Colors.textSecondary)
            if let error {
                Text(error).font(.caption).foregroundColor(.red)
            }
            HStack(spacing: 10) {
                if isMine {
                    if listing.status == "ACTIVE" {
                        actionButton("Mark sold", filled: false) { await markSold() }
                    }
                    if listing.status != "REMOVED" {
                        actionButton("Remove", filled: false) { await remove() }
                    }
                } else if listing.status == "ACTIVE" {
                    actionButton(busy ? "Starting…" : "Message seller", filled: true) {
                        onMessageSeller(listing.id)
                    }
                }
            }
        }
        .padding(18)
        .background(IDS.Colors.card)
        .cornerRadius(IDS.Layout.cardCornerRadius)
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

    private func markSold() async {
        busy = true
        defer { busy = false }
        do {
            _ = try await NetworkClient.shared.markListingSold(listing.id)
            onChanged()
        } catch let NetworkError.httpError(statusCode) {
            error = TalkScreen.errorMessage(statusCode)
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
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
}
