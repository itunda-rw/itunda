import SwiftUI
import CoreDesignSystem
import CoreNetwork

// Real seller chat, real canned quick-reply categories (itunda Shopping redesign,
// 2026-08-28, direct user reference: real Toss Shopping seller-chat screenshots
// with canned inquiry categories). Wires Shop into the exact same real 1:1
// messaging system every other vertical (Marketplace/Community/Jobs/Property)
// already uses via its own onSwitchToTalk/pendingConversationId hand-off -- see
// HoodMarketplace.swift's own messageSeller for the reference pattern, and this
// file's own web/Android siblings (ShopSellerContactPicker.tsx/.kt) for the full
// account. Deliberately does NOT touch the shared conversation-thread UI at all:
// this is a small picker shown BEFORE entering it. Tapping a category is a real,
// honest compose-assist -- it calls the real contact-seller endpoint, sends a
// real first message with that category's own real label as the body, then hands
// the real conversation id up to the caller. "Just start chatting" opens the
// real conversation with no pre-sent message. Every message sent here is a real
// message in a real thread, no fabricated chat-bot layer.
private let contactCategories = [
    "Product inquiry", "Shipping inquiry", "Exchange inquiry",
    "Return inquiry", "Cancellation inquiry", "Other inquiry",
]

struct ShopSellerContactPicker: View {
    let merchantId: String
    let merchantName: String
    let onOpened: (String) -> Void

    @State private var sending = false
    @State private var error: String?
    @Environment(\.dismiss) private var dismiss

    private func start(_ firstMessage: String?) {
        sending = true
        error = nil
        Task {
            do {
                let res = try await NetworkClient.shared.contactMerchantSeller(merchantId: merchantId)
                if let firstMessage {
                    _ = try await NetworkClient.shared.sendMessage(conversationId: res.conversation.id, body: firstMessage)
                }
                onOpened(res.conversation.id)
            } catch {
                self.error = "Couldn't reach itunda. Check your connection and try again."
                sending = false
            }
        }
    }

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 4) {
                Text(merchantName).font(.caption).foregroundColor(IDS.Colors.textSecondary).padding(.horizontal, 20).padding(.top, 8)

                VStack(spacing: 0) {
                    ForEach(contactCategories, id: \.self) { category in
                        Button(action: { start(category) }) {
                            HStack {
                                Text(category).font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                                Spacer()
                                Text("›").foregroundColor(IDS.Colors.textSecondary)
                            }
                            .padding(.vertical, 14)
                        }
                        .buttonStyle(.plain)
                        .disabled(sending)
                        Divider()
                    }
                }
                .padding(.horizontal, 20)

                Button(action: { start(nil) }) {
                    Text("Just start chatting")
                        .font(.subheadline).bold()
                        .foregroundColor(IDS.Colors.textSecondary)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                }
                .buttonStyle(.plain)
                .disabled(sending)
                .padding(.horizontal, 20)

                if let error {
                    Text(error).font(.caption).foregroundColor(IDS.Colors.danger).padding(.horizontal, 20)
                }
                Spacer()
            }
            .navigationTitle("What can we help with?")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Close") { dismiss() }
                }
            }
        }
    }
}

// Real card-visual polish (itunda Shopping redesign, 2026-08-28) -- three small,
// shared trust-signal views used across Shop's product cards/detail screen,
// matching the real Toss Shopping reference's card style without fabricating
// anything: isBestSeller is a real, derived signal (see backend
// ShoppingController.bestSellerProductIds' own doc comment); deliveryTimeMinutes
// is itunda's own real, already-computed delivery-ETA estimate -- deliberately
// NOT the reference's literal "Ships today" parcel-shipping copy, since itunda's
// real commerce fulfillment model is merchant-pickup/delivery-time-estimate.

struct ShopMessageSellerButton: View {
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            HStack(spacing: 4) {
                Text("💬").font(.caption)
                Text("Message seller").font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
            }
            .padding(.horizontal, 12).padding(.vertical, 6)
            .background(IDS.Colors.backgroundTertiary)
            .clipShape(Capsule())
        }
        .buttonStyle(.plain)
    }
}

struct ShopBestSellerBadge: View {
    var body: some View {
        Text("Best seller")
            .font(.system(size: 11, weight: .bold))
            .foregroundColor(.white)
            .padding(.horizontal, 6).padding(.vertical, 2)
            .background(IDS.Colors.brand)
            .clipShape(RoundedRectangle(cornerRadius: 4))
    }
}

struct ShopDeliveryEtaPill: View {
    let minutes: Int?
    var body: some View {
        if let minutes {
            Text("🕒 ~\(minutes) min").font(.system(size: 11)).foregroundColor(IDS.Colors.textSecondary)
        }
    }
}
