package rw.itunda.feature.talk.impl

import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.PriceOfferDto
import rw.itunda.core.network.PropertyPriceOfferDto

// Real fix (2026-08-26): split out of TalkChatThread.kt once that file grew past its
// file-size-lint baseline. Pure network-fetch helpers used only by ChatThreadView's
// own refresh() -- explicit callbacks instead of captured `var`s, same technique used
// for EatsOrderFlowDispatch.kt/ShopDetailDispatch.kt.

// Real-fetches both Marketplace and Real Estate offer history for this conversation
// -- a given real conversation only ever carries one type in practice, but fetching
// both is cheap and correct rather than guessing which one applies (mirrors
// bank-mfe's own ConversationThread.loadOffers).
internal suspend fun loadTalkThreadOffers(conversationId: String, onLoaded: (Map<String, OfferBubbleData>) -> Unit) {
    val marketplaceOffers: List<PriceOfferDto> = try {
        NetworkClient.apiService.getOffersForConversation(conversationId).offers
    } catch (_: Exception) {
        emptyList()
    }
    val propertyOffers: List<PropertyPriceOfferDto> = try {
        NetworkClient.apiService.getPropertyOffersForConversation(conversationId).offers
    } catch (_: Exception) {
        emptyList()
    }
    onLoaded(
        (marketplaceOffers.map { it.toBubbleData() to it.messageId } + propertyOffers.map { it.toBubbleData() to it.messageId })
            .associate { (data, messageId) -> messageId to data }
    )
}

internal suspend fun loadTalkThreadGifts(conversationId: String, onLoaded: (Map<String, rw.itunda.core.network.GiftDto>) -> Unit) {
    try {
        val res = NetworkClient.apiService.getGiftsForConversation(conversationId)
        if (res.success) onLoaded(res.gifts.associateBy { it.messageId })
    } catch (_: Exception) {
        // Real, non-critical -- only backs the inline gift bubble.
    }
}

internal suspend fun loadTalkThreadVouchers(conversationId: String, onLoaded: (Map<String, rw.itunda.core.network.GiftVoucherDto>) -> Unit) {
    try {
        val res = NetworkClient.apiService.getGiftVouchersForConversation(conversationId)
        if (res.success) onLoaded(res.vouchers.associateBy { it.messageId })
    } catch (_: Exception) {
        // Real, non-critical -- only backs the inline gift-voucher bubble.
    }
}
