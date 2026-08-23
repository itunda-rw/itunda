import SwiftUI
import UIKit
import CoreDesignSystem
import CoreNetwork


// Real minimal shape both PriceOfferDto (Marketplace) and PropertyPriceOfferDto (Real
// Estate) get mapped into for display -- narrowed to just the fields OfferBubble
// actually reads (id/amount/status/proposedByUserId), so this one view renders both
// offer types without duplication. Mirrors bank-mfe's own OfferBubbleData narrowing
// (2026-07-19).
struct OfferBubbleData {
    let id: String
    let amount: Double
    let status: String
    let proposedByUserId: String
}

extension PriceOfferDto {
    func toBubbleData() -> OfferBubbleData { OfferBubbleData(id: id, amount: amount, status: status, proposedByUserId: proposedByUserId) }
}
extension PropertyPriceOfferDto {
    func toBubbleData() -> OfferBubbleData { OfferBubbleData(id: id, amount: amount, status: status, proposedByUserId: proposedByUserId) }
}

// Real 당근-style offer bubble (2026-07-19) -- see PriceOfferService's own doc comment.
// Renders inline wherever a message carries a real offer, replacing the plain-text
// bubble with amount + status + real Accept/Decline/Counter actions (only shown to
// whichever participant did NOT propose the current pending amount).
struct OfferBubble: View {
    let offer: OfferBubbleData
    let isMine: Bool
    let currentUserId: String?
    let onRespond: (String, String, Double?) -> Void

    @State private var countering = false
    @State private var counterAmount = ""

    private var canRespond: Bool { offer.status == "PENDING" && currentUserId != nil && currentUserId != offer.proposedByUserId }
    private var statusLabel: String {
        switch offer.status {
        case "PENDING": return "Pending"
        case "ACCEPTED": return "Accepted"
        case "REJECTED": return "Declined"
        case "COUNTERED": return "Countered"
        default: return offer.status
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 5) {
                MoneyBagGlyph(size: 16)
                Text("\(Int(offer.amount)) RWF")
            }
            .font(.subheadline).bold().foregroundColor(isMine ? .white : IDS.Colors.textPrimary)
            Text(statusLabel).font(.caption).foregroundColor(isMine ? .white.opacity(0.85) : IDS.Colors.textSecondary)
            if canRespond && !countering {
                HStack(spacing: 6) {
                    offerActionButton("Accept") { onRespond(offer.id, "ACCEPT", nil) }
                    offerActionButton("Decline") { onRespond(offer.id, "REJECT", nil) }
                    offerActionButton("Counter") { countering = true }
                }
            }
            if canRespond && countering {
                HStack(spacing: 6) {
                    IdsTextField("Counter (RWF)", text: $counterAmount, keyboardType: .numberPad)
                        .frame(width: 100)
                    offerActionButton("Send") {
                        guard let amount = Double(counterAmount) else { return }
                        countering = false
                        counterAmount = ""
                        onRespond(offer.id, "COUNTER", amount)
                    }
                }
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .background(isMine ? IDS.Colors.brand : IDS.Colors.chipBackground)
        .cornerRadius(16)
    }

    private func offerActionButton(_ label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label).font(.caption2).bold().foregroundColor(IDS.Colors.textPrimary)
                .padding(.horizontal, 10).padding(.vertical, 6)
                .background(IDS.Colors.card)
                .cornerRadius(10).idsCardBorder(cornerRadius: 10)
        }
        .buttonStyle(.plain)
    }
}

// Real KakaoTalk-style gift bubble (2026-07-20) -- see GiftService's own doc comment.
// Renders inline wherever a message carries a real gift, with a real Open/Claim button
// shown only to the recipient of a still-PENDING, not-yet-expired gift.
struct GiftBubble: View {
    let gift: GiftDto
    let isMine: Bool
    let currentUserId: String?
    let onClaim: (String) -> Void

    private static let isoFormatter = ISO8601DateFormatter(withFractionalSeconds: true)

    private var canClaim: Bool {
        guard gift.status == "PENDING", currentUserId == gift.recipientId else { return false }
        guard let expiresAt = Self.isoFormatter.date(from: gift.expiresAt) else { return true }
        return expiresAt > Date()
    }
    // Strips giftThemeLabels' own leading emoji (e.g. "🎉 Congratulations" ->
    // "Congratulations ") since GiftThemeGlyph now renders that emoji as a real
    // glyph alongside this text instead of leaving it baked into the string.
    private var themePrefixText: String {
        guard let theme = gift.theme, let label = giftThemeLabels[theme] else { return "" }
        return String(label.drop(while: { !$0.isWhitespace }).dropFirst()) + " "
    }

    private var statusLabel: String {
        switch gift.status {
        case "PENDING": return isMine ? "Waiting to be opened" : "Tap to open"
        case "CLAIMED": return "Opened"
        case "EXPIRED": return "Expired — refunded"
        default: return gift.status
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 6) {
                GiftThemeGlyph(theme: gift.theme, size: 18)
                Text(themePrefixText + "\(Int(gift.amount)) RWF")
            }
            .font(.headline).foregroundColor(isMine ? .white : IDS.Colors.textPrimary)
            if let note = gift.note {
                Text("\"\(note)\"").font(.caption).foregroundColor(isMine ? .white.opacity(0.9) : IDS.Colors.textSecondary)
            }
            Text(statusLabel).font(.caption).foregroundColor(isMine ? .white.opacity(0.85) : IDS.Colors.textSecondary)
            if canClaim {
                Button(action: { onClaim(gift.id) }) {
                    Text("Open gift").font(.caption2).bold().foregroundColor(IDS.Colors.textPrimary)
                        .padding(.horizontal, 10).padding(.vertical, 6)
                        .background(IDS.Colors.card)
                        .cornerRadius(10).idsCardBorder(cornerRadius: 10)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
        .background(isMine ? IDS.Colors.brand : IDS.Colors.chipBackground)
        .cornerRadius(16)
    }
}

// Real KakaoTalk-style 기프티콘 gift voucher bubble (item 138) -- see
// GiftVoucherComposerPanel's own doc comment. Redemption is merchant-side per
// GiftVoucherService.redeemVoucher's own doc comment, never a self-serve recipient
// redeem. Real one-time "extend expiry" action added item 195 (found via a
// defined-but-uncalled-method sweep); only offered once (!voucher.extended), and only
// within a real 30-day window of the current expiry, matching bank-mfe's own
// GIFT_VOUCHER_EXTENSION_WINDOW_MS / Android's item 194 exactly.
struct GiftVoucherBubble: View {
    let voucher: GiftVoucherDto
    let isMine: Bool
    var onExtend: () -> Void = {}

    private var statusLabel: String {
        switch voucher.status {
        case "ACTIVE": return "Present this at the store to redeem"
        case "REDEEMED": return "Redeemed"
        case "EXPIRED": return "Expired"
        default: return voucher.status
        }
    }

    private var canExtend: Bool {
        guard voucher.status == "ACTIVE", !voucher.extended else { return false }
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        guard let expiry = formatter.date(from: voucher.expiresAt) ?? ISO8601DateFormatter().date(from: voucher.expiresAt) else { return false }
        return expiry.timeIntervalSinceNow <= 30 * 24 * 60 * 60
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 6) {
                VoucherTicket(size: 18)
                Text(voucher.productNameSnapshot ?? "\(Int(voucher.amount)) RWF voucher")
            }
            .font(.headline).foregroundColor(isMine ? .white : IDS.Colors.textPrimary)
            Text(statusLabel).font(.caption).foregroundColor(isMine ? .white.opacity(0.85) : IDS.Colors.textSecondary)
            if voucher.status == "ACTIVE" {
                Text("Expires \(String(voucher.expiresAt.prefix(10)))")
                    .font(.caption2).foregroundColor(isMine ? .white.opacity(0.7) : IDS.Colors.textSecondary)
            }
            if canExtend {
                Button(action: onExtend) {
                    Text("Extend expiry").font(.caption).bold().foregroundColor(isMine ? .white : IDS.Colors.brand)
                }
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
        .background(isMine ? IDS.Colors.brand : IDS.Colors.chipBackground)
        .cornerRadius(16)
    }
}

struct MessageBubble: View {
    let message: MessageDto
    let isMine: Bool
    let currentUserId: String?
    let offer: OfferBubbleData?
    let gift: GiftDto?
    var voucher: GiftVoucherDto? = nil
    var emoticonImageUrl: String? = nil
    let onToggleReaction: (String) -> Void
    let onRespondToOffer: (String, String, Double?) -> Void
    let onClaimGift: (String) -> Void
    var onExtendVoucher: (String) -> Void = { _ in }
    let onReply: (MessageDto) -> Void
    var onOpenThread: (MessageDto) -> Void = { _ in }
    let onDelete: (String) -> Void
    let onPin: (MessageDto) -> Void
    let onForward: (MessageDto) -> Void
    let onReportMessage: (String, String) -> Void
    var showTimestamp: Bool = true
    @State private var reportOpen = false
    @State private var reportReason = ""

    var body: some View {
        VStack(alignment: isMine ? .trailing : .leading, spacing: 2) {
            HStack {
                if isMine { Spacer() }
                if let gift {
                    GiftBubble(gift: gift, isMine: isMine, currentUserId: currentUserId, onClaim: onClaimGift)
                } else if let voucher {
                    GiftVoucherBubble(voucher: voucher, isMine: isMine, onExtend: { onExtendVoucher(voucher.id) })
                } else if let offer {
                    OfferBubble(offer: offer, isMine: isMine, currentUserId: currentUserId, onRespond: onRespondToOffer)
                } else if message.emoticonId != nil {
                    EmoticonBubble(imageUrl: emoticonImageUrl)
                } else if let imageUrl = message.imageUrl {
                    // Real photo message -- ports Android TalkScreen.kt's own identical
                    // addition (2026-08-04) to iOS.
                    AsyncImage(url: URL(string: imageUrl)) { image in
                        image.resizable().aspectRatio(contentMode: .fit)
                    } placeholder: {
                        ProgressView()
                    }
                    .frame(maxWidth: 220)
                    .cornerRadius(16)
                } else {
                    MessageBodyWithEmoji(messageText: message.body, color: isMine ? .white : IDS.Colors.textPrimary, fontSize: 15)
                        .padding(.horizontal, 14)
                        .padding(.vertical, 10)
                        .background(isMine ? IDS.Colors.brand : IDS.Colors.chipBackground)
                        .cornerRadius(16)
                }
                if !isMine { Spacer() }
            }
            MessageReactionsRow(reactions: message.reactions, currentUserId: currentUserId, isMine: isMine, onToggle: onToggleReaction)
            Button("Reply") { onReply(message) }
                .font(.caption2).foregroundColor(IDS.Colors.textSecondary)
            if isMine && message.deletedAt == nil {
                Button("Delete") { onDelete(message.id) }
                    .font(.caption2).foregroundColor(IDS.Colors.textSecondary)
            }
            Button("Pin") { onPin(message) }
                .font(.caption2).foregroundColor(IDS.Colors.textSecondary)
            Button("Forward") { onForward(message) }
                .font(.caption2).foregroundColor(IDS.Colors.textSecondary)
            if message.forwardedFromMessageId != nil {
                Text("↪ Forwarded").font(.caption2).foregroundColor(IDS.Colors.textSecondary)
            }
            if let statusText = messageStatusText(isMine: isMine, unread: message.readAt == nil, showTimestamp: showTimestamp, sentAt: message.sentAt) {
                Text(statusText)
                    .font(.caption2)
                    .foregroundColor(IDS.Colors.textSecondary)
            }
            if !isMine {
                Button("Report message") { reportOpen = true }
                    .font(.caption2).foregroundColor(IDS.Colors.textSecondary)
            }
            // Real Thread support (2026-08-05) -- see GroupMessageBubble's own identical
            // affordance (docs/DESIGN_REFERENCES.md Talk section recommendation #3).
            if message.replyCount > 0 {
                Button("\(message.replyCount) \(message.replyCount == 1 ? "reply" : "replies") →") { onOpenThread(message) }
                    .font(.caption2).fontWeight(.bold).foregroundColor(IDS.Colors.brand)
            }
        }
        .alert("Report message", isPresented: $reportOpen) {
            IdsTextField("Reason", text: $reportReason)
            Button("Send") {
                let trimmed = reportReason.trimmingCharacters(in: .whitespacesAndNewlines)
                if trimmed.count >= 3 { onReportMessage(message.id, trimmed); reportReason = "" }
            }
            Button("Cancel", role: .cancel) {}
        } message: { Text("Explain why this selected message should be reviewed.") }
    }
}

// Real Thread support (2026-08-05) -- see docs/DESIGN_REFERENCES.md Talk section
// recommendation #3's own account and MessagingController.getThread's backend doc
// comment for the full sourced Kakao account. A real sub-conversation view: the root
// message, every direct reply oldest-first, and a composer that replies straight into
// this same thread. Named "Replies" rather than reusing "Thread" to avoid colliding
// with this file's own pre-existing ChatThreadScreen/GroupThreadScreen naming (the
// whole conversation screen, a different real concept).
struct RepliesThreadView: View {
    let rootMessage: MessageDto
    let currentUserId: String?
    let fetchThreadMessages: () async throws -> [MessageDto]
    let onSend: (String) async throws -> MessageResponse
    let onDismiss: () -> Void

    @State private var messages: [MessageDto]?
    @State private var draft = ""
    @State private var sending = false
    @State private var error: String?

    var body: some View {
        NavigationView {
            VStack {
                if let error {
                    Text(error).font(.caption).foregroundColor(.red)
                }
                ScrollView {
                    LazyVStack(spacing: 8) {
                        if let messages {
                            ForEach(Array(messages.enumerated()), id: \.element.id) { index, m in
                                let isMine = m.senderId == currentUserId
                                VStack(alignment: isMine ? .trailing : .leading, spacing: 2) {
                                    if index == 0 {
                                        Text("Original message").font(.caption2).foregroundColor(IDS.Colors.textSecondary)
                                    }
                                    HStack {
                                        if isMine { Spacer() }
                                        Group {
                                            if m.deletedAt == nil {
                                                MessageBodyWithEmoji(messageText: m.body, color: isMine ? .white : IDS.Colors.textPrimary, fontSize: 15)
                                            } else {
                                                Text("This message was deleted").font(.subheadline).foregroundColor(isMine ? .white : IDS.Colors.textPrimary)
                                            }
                                        }
                                            .padding(.horizontal, 14).padding(.vertical, 10)
                                            .background(isMine ? IDS.Colors.brand : IDS.Colors.chipBackground)
                                            .cornerRadius(16)
                                        if !isMine { Spacer() }
                                    }
                                    Text(chatMessageTime(m.sentAt)).font(.caption2).foregroundColor(IDS.Colors.textSecondary)
                                }
                                .frame(maxWidth: .infinity, alignment: isMine ? .trailing : .leading)
                            }
                        } else {
                            ProgressView().padding(.top, 20)
                        }
                    }
                    .padding(.horizontal, IDS.Layout.screenHorizontal)
                }
                HStack {
                    IdsTextField("Reply in thread", text: $draft)
                    Button(sending ? "…" : "Send") { Task { await send() } }
                        .disabled(sending || draft.trimmingCharacters(in: .whitespaces).isEmpty)
                }
                .padding(.horizontal, IDS.Layout.screenHorizontal)
                .padding(.bottom, 8)
            }
            .navigationTitle("Thread")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Close", action: onDismiss) }
            }
            .task { await load() }
        }
    }

    private func load() async {
        do { messages = try await fetchThreadMessages() } catch { self.error = "Could not load this thread." }
    }

    private func send() async {
        let body = draft.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !body.isEmpty else { return }
        sending = true
        defer { sending = false }
        do {
            _ = try await onSend(body)
            draft = ""
            await load()
        } catch {
            self.error = "Could not send this reply."
        }
    }
}

