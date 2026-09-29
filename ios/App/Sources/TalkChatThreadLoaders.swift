import Foundation
import CoreNetwork

// Real fix (2026-08-26): split out of TalkChatThread.swift once that file grew past
// its file-size-lint baseline. Pure network-fetch helpers used only by
// ChatThreadScreen's own refresh() -- explicit return values instead of `self.`
// state mutation, same technique used for Android's TalkChatThreadLoaders.kt.

func loadTalkThreadGifts(_ conversationId: String) async -> [String: GiftDto] {
    let gifts = (try? await NetworkClient.shared.getGiftsForConversation(conversationId: conversationId).gifts) ?? []
    return Dictionary(uniqueKeysWithValues: gifts.map { ($0.messageId, $0) })
}

// Real per-thread gift-voucher history -- see GiftVoucherComposerPanel's own doc
// comment.
func loadTalkThreadVouchers(_ conversationId: String) async -> [String: GiftVoucherDto] {
    let vouchers = (try? await NetworkClient.shared.getGiftVouchersForConversation(conversationId: conversationId).vouchers) ?? []
    return Dictionary(uniqueKeysWithValues: vouchers.map { ($0.messageId, $0) })
}

// Real-fetches both Marketplace and Real Estate offer history for this conversation
// -- a given real conversation only ever carries one type in practice, but fetching
// both is cheap and correct rather than guessing which one applies (mirrors
// bank-mfe's own ConversationThread.loadOffers).
func loadTalkThreadOffers(_ conversationId: String) async -> [String: OfferBubbleData] {
    let marketplaceOffers = (try? await NetworkClient.shared.getOffersForConversation(conversationId: conversationId).offers) ?? []
    let propertyOffers = (try? await NetworkClient.shared.getPropertyOffersForConversation(conversationId: conversationId).offers) ?? []
    var merged: [String: OfferBubbleData] = [:]
    for o in marketplaceOffers { merged[o.messageId] = o.toBubbleData() }
    for o in propertyOffers { merged[o.messageId] = o.toBubbleData() }
    return merged
}

func sendTalkThreadPhoto(conversationId: String, jpegData: Data, replyToMessageId: String?) async throws -> MessageDto? {
    let uploaded = try await NetworkClient.shared.uploadPhoto(data: jpegData, filename: "photo.jpg", mimeType: "image/jpeg")
    let res = try await NetworkClient.shared.sendMessage(conversationId: conversationId, body: "", replyToMessageId: replyToMessageId, imageUrl: uploaded.url)
    return res.success ? res.message : nil
}

// Real offer ids are stably prefixed by their real owning service
// ("price_offer_"/"property_offer_") -- a reliable dispatch key, matching bank-mfe's
// own ConversationThread.
func respondToTalkThreadOffer(offerId: String, action: String, counterAmount: Double?) async throws {
    if offerId.hasPrefix("property_offer_") {
        _ = try await NetworkClient.shared.respondToPropertyOffer(offerId: offerId, action: action, counterAmount: counterAmount)
    } else {
        _ = try await NetworkClient.shared.respondToOffer(offerId: offerId, action: action, counterAmount: counterAmount)
    }
}

func sendTalkThreadGift(conversationId: String, amount: Double, note: String, theme: String?) async throws {
    _ = try await NetworkClient.shared.sendGiftInConversation(
        conversationId: conversationId,
        amount: amount,
        note: note.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? nil : note,
        theme: theme
    )
}
