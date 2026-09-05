import SwiftUI
import UIKit
import CoreDesignSystem
import CoreNetwork

// Real emoticon picker (item 136) -- shows the sender's own owned packs only (each
// tappable emoticon sends immediately); a real "Get more" link opens the full store.
// Mirrors bank-mfe/Android's own EmoticonPickerPanel (items 133/135).
// Real gift-voucher composer (item 138) -- search for a real product to gift (same
// real Kakao gifticon UX of searching for what to send, e.g. "스타벅스 아메리카노",
// rather than browsing a merchant catalog first), pick one, confirm with the
// recipient's phone number. Product-only v1 -- the flat-cash-amount-at-a-merchant
// path is a real, deliberately deferred follow-up. Mirrors bank-mfe/Android's own
// GiftVoucherComposerPanel (items 134/137).
struct GiftVoucherComposerPanel: View {
    let onSent: () -> Void
    let onCancel: () -> Void

    @State private var phone = ""
    @State private var query = ""
    @State private var results: [ProductSearchResultDto]?
    @State private var searching = false
    @State private var selected: ProductSearchResultDto?
    @State private var sending = false
    @State private var error: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("🎟️ Send a gift voucher").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
            IdsTextField("Recipient phone number", text: $phone, keyboardType: .phonePad)

            if let selected {
                HStack {
                    Text("\(selected.name) · \(selected.merchantName) · \(formatAmount(Int(selected.price))) RWF")
                        .font(.caption).foregroundColor(IDS.Colors.textPrimary)
                    Spacer()
                    Button("Change") { self.selected = nil }
                        .font(.caption).foregroundColor(IDS.Colors.brand)
                }
                .padding(10)
                .background(IDS.Colors.card)
                .cornerRadius(8).idsCardBorder(cornerRadius: 8)
            } else {
                HStack(spacing: 8) {
                    IdsTextField("Search a product to gift", text: $query)
                    Button(action: { Task { await search() } }) {
                        Text(searching ? "…" : "Search").font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
                            .padding(.horizontal, 10).padding(.vertical, 8)
                            .background(IDS.Colors.card)
                            .cornerRadius(8).idsCardBorder(cornerRadius: 8)
                    }
                    .buttonStyle(.plain)
                    .disabled(searching || query.trimmingCharacters(in: .whitespaces).count < 2)
                }
                if let results {
                    if results.isEmpty {
                        Text("No products found.").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                    } else {
                        ForEach(results) { p in
                            Button(action: { selected = p }) {
                                HStack {
                                    Text("\(p.name) · \(p.merchantName)").font(.caption).foregroundColor(IDS.Colors.textPrimary)
                                    Spacer()
                                    Text("\(formatAmount(Int(p.price))) RWF").font(.caption).foregroundColor(IDS.Colors.textPrimary)
                                }
                                .padding(10)
                                .background(IDS.Colors.card)
                                .cornerRadius(8).idsCardBorder(cornerRadius: 8)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
            }

            if let error {
                Text(error).font(.caption).foregroundColor(.red)
            }

            HStack(spacing: 8) {
                Button(action: { Task { await send() } }) {
                    Text(sending ? "…" : "Send gift voucher").font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
                        .padding(.horizontal, 10).padding(.vertical, 6)
                        .background(IDS.Colors.card)
                        .cornerRadius(10).idsCardBorder(cornerRadius: 10)
                }
                .buttonStyle(.plain)
                .disabled(selected == nil || phone.trimmingCharacters(in: .whitespaces).isEmpty || sending)
                Button(action: onCancel) {
                    Text("Cancel").font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
                        .padding(.horizontal, 10).padding(.vertical, 6)
                        .background(IDS.Colors.card)
                        .cornerRadius(10).idsCardBorder(cornerRadius: 10)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(12)
        .background(IDS.Colors.chipBackground)
        .cornerRadius(12)
    }

    private func search() async {
        guard query.trimmingCharacters(in: .whitespaces).count >= 2 else { return }
        searching = true
        error = nil
        defer { searching = false }
        do {
            results = try await NetworkClient.shared.searchProducts(query.trimmingCharacters(in: .whitespaces)).products
        } catch {
            self.error = "Could not search products."
        }
    }

    private func send() async {
        guard let selected, !phone.trimmingCharacters(in: .whitespaces).isEmpty else { return }
        sending = true
        error = nil
        defer { sending = false }
        do {
            _ = try await NetworkClient.shared.purchaseGiftVoucher(
                PurchaseGiftVoucherRequest(recipientPhoneNumber: phone.trimmingCharacters(in: .whitespaces), merchantId: selected.merchantId, merchantProductId: selected.id)
            )
            onSent()
        } catch {
            self.error = "Could not send this gift voucher."
        }
    }
}

struct EmoticonPickerPanel: View {
    let onSend: (String) -> Void
    let onOpenStore: () -> Void

    @State private var ownedPacks: [OwnedEmoticonPackDto]?
    @State private var packTitles: [String: String] = [:]
    @State private var selectedPackId: String?
    @State private var packEmoticons: [EmoticonDto]?

    private let columns = Array(repeating: GridItem(.flexible()), count: 4)

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            if let ownedPacks {
                if ownedPacks.isEmpty {
                    VStack(spacing: 8) {
                        Text("You don't own any emoticon packs yet.").font(.footnote).foregroundColor(IDS.Colors.textSecondary)
                        Button(action: onOpenStore) {
                            Text("Browse Emoticon Store").font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
                                .padding(.horizontal, 12).padding(.vertical, 8)
                                .background(IDS.Colors.card).cornerRadius(10).idsCardBorder(cornerRadius: 10)
                        }
                        .buttonStyle(.plain)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 16)
                } else {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 6) {
                            ForEach(ownedPacks, id: \.packId) { op in
                                Button(action: { selectedPackId = op.packId }) {
                                    Text(packTitles[op.packId] ?? op.packId).font(.caption2).bold()
                                        .foregroundColor(selectedPackId == op.packId ? .white : IDS.Colors.textPrimary)
                                        .padding(.horizontal, 10).padding(.vertical, 6)
                                        .background(selectedPackId == op.packId ? IDS.Colors.brand : IDS.Colors.card)
                                        .cornerRadius(8)
                                        .idsCardBorder(cornerRadius: 8)
                                }
                                .buttonStyle(.plain)
                            }
                            Button(action: onOpenStore) {
                                Text("Get more").font(.caption2).bold().foregroundColor(IDS.Colors.textPrimary)
                                    .padding(.horizontal, 10).padding(.vertical, 6)
                                    .background(IDS.Colors.card).cornerRadius(8).idsCardBorder(cornerRadius: 8)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    if let packEmoticons {
                        LazyVGrid(columns: columns, spacing: 8) {
                            ForEach(packEmoticons) { e in
                                Button(action: { onSend(e.id) }) {
                                    AsyncImage(url: URL(string: e.imageUrl)) { image in
                                        image.resizable().aspectRatio(contentMode: .fit)
                                    } placeholder: {
                                        ProgressView()
                                    }
                                    .aspectRatio(1, contentMode: .fit)
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    } else {
                        ProgressView()
                    }
                }
            } else {
                ProgressView()
            }
        }
        .padding(12)
        .background(IDS.Colors.chipBackground)
        .cornerRadius(12)
        .task {
            async let owned = try? NetworkClient.shared.getOwnedEmoticonPacks().packs
            async let allPacks = try? NetworkClient.shared.getEmoticonPacks().packs
            let ownedResult = (await owned) ?? []
            ownedPacks = ownedResult
            packTitles = Dictionary(uniqueKeysWithValues: ((await allPacks) ?? []).map { ($0.id, $0.title) })
            if let first = ownedResult.first { selectedPackId = first.packId }
        }
        .task(id: selectedPackId) {
            guard let selectedPackId else { return }
            packEmoticons = nil
            packEmoticons = (try? await NetworkClient.shared.getPackEmoticons(packId: selectedPackId).emoticons) ?? []
        }
    }
}

// Real Emoticon Store (item 136) -- browse every real active pack, buy (once-off
// purchase, same "buy it once, own it" model Shop/Insurance already use), or gift to
// a friend by phone number. Mirrors bank-mfe/Android's own EmoticonStoreModal/Dialog
// (items 133/135).
struct EmoticonStoreView: View {
    let onClose: () -> Void

    @State private var packs: [EmoticonPackDto]?
    @State private var ownedPackIds: Set<String> = []
    @State private var busyPackId: String?
    @State private var giftingPackId: String?
    @State private var giftPhone = ""
    @State private var error: String?
    @State private var message: String?

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(alignment: .leading, spacing: 10) {
                    if let error {
                        Text(error).font(.footnote).foregroundColor(.red)
                    }
                    if let message {
                        Text(message).font(.footnote).foregroundColor(IDS.Colors.brand)
                    }
                    if let packs {
                        ForEach(packs) { pack in
                            let owned = ownedPackIds.contains(pack.id)
                            VStack(alignment: .leading, spacing: 6) {
                                HStack(spacing: 10) {
                                    AsyncImage(url: URL(string: pack.thumbnailUrl)) { image in
                                        image.resizable().aspectRatio(contentMode: .fit)
                                    } placeholder: { ProgressView() }
                                        .frame(width: 48, height: 48)
                                    VStack(alignment: .leading) {
                                        Text(pack.title).font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                                        Text("\(pack.artistName) · \(formatAmount(Int(pack.price))) RWF").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                                    }
                                    Spacer()
                                    Button(action: { Task { await buy(pack.id) } }) {
                                        Text(owned ? "Owned" : (busyPackId == pack.id ? "…" : "Buy")).font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
                                            .padding(.horizontal, 10).padding(.vertical, 6)
                                            .background(IDS.Colors.card).cornerRadius(8).idsCardBorder(cornerRadius: 8)
                                    }
                                    .buttonStyle(.plain)
                                    .disabled(owned || busyPackId != nil)
                                    Button(action: { giftingPackId = (giftingPackId == pack.id) ? nil : pack.id }) {
                                        Text("Gift").font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
                                            .padding(.horizontal, 10).padding(.vertical, 6)
                                            .background(IDS.Colors.card).cornerRadius(8).idsCardBorder(cornerRadius: 8)
                                    }
                                    .buttonStyle(.plain)
                                    .disabled(busyPackId != nil)
                                }
                                if giftingPackId == pack.id {
                                    HStack {
                                        IdsTextField("Recipient phone number", text: $giftPhone)
                                        Button(action: { Task { await gift(pack.id) } }) {
                                            Text(busyPackId == pack.id ? "…" : "Send gift").font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
                                                .padding(.horizontal, 10).padding(.vertical, 6)
                                                .background(IDS.Colors.card).cornerRadius(8).idsCardBorder(cornerRadius: 8)
                                        }
                                        .buttonStyle(.plain)
                                        .disabled(busyPackId != nil || giftPhone.trimmingCharacters(in: .whitespaces).isEmpty)
                                    }
                                }
                            }
                            .padding(10)
                            .background(IDS.Colors.chipBackground)
                            .cornerRadius(10)
                        }
                    } else {
                        ProgressView()
                    }
                }
                .padding(IDS.Layout.screenHorizontal)
            }
            .navigationTitle("Emoticon Store")
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Close", action: onClose)
                }
            }
        }
        .task { await load() }
    }

    private func load() async {
        do {
            packs = try await NetworkClient.shared.getEmoticonPacks().packs
            ownedPackIds = Set(try await NetworkClient.shared.getOwnedEmoticonPacks().packs.map { $0.packId })
        } catch {
            self.error = "Could not load the Emoticon Store."
        }
    }

    private func buy(_ packId: String) async {
        busyPackId = packId
        error = nil
        defer { busyPackId = nil }
        do {
            _ = try await NetworkClient.shared.purchaseEmoticonPack(packId: packId)
            await load()
        } catch let NetworkError.httpError(statusCode) where statusCode == 409 {
            await load()
        } catch {
            self.error = "Could not purchase this pack."
        }
    }

    private func gift(_ packId: String) async {
        busyPackId = packId
        error = nil
        message = nil
        defer { busyPackId = nil }
        do {
            _ = try await NetworkClient.shared.giftEmoticonPack(packId: packId, recipientPhoneNumber: giftPhone.trimmingCharacters(in: .whitespaces))
            message = "Pack gifted!"
            giftingPackId = nil
            giftPhone = ""
        } catch {
            self.error = "Could not gift this pack."
        }
    }
}

