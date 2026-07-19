package rw.itunda.core.repository

import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.PriceOffer

interface PriceOfferRepository : JpaRepository<PriceOffer, String> {
    fun findByMessageId(messageId: String): PriceOffer?

    // Real batch fetch (2026-07-19) -- backs attaching an offer to whichever message in
    // a fetched conversation page carries one, one query rather than one per message.
    fun findByMessageIdIn(messageIds: Collection<String>): List<PriceOffer>

    // Real negotiation history for a listing, newest-first -- bounded by how many real
    // offers/counters a single listing realistically accumulates (never unbounded across
    // the whole marketplace), so no pagination needed here yet.
    fun findByListingIdOrderByCreatedAtDesc(listingId: String): List<PriceOffer>

    // Real per-thread negotiation history -- see PriceOfferService.getOffersForConversation's
    // own doc comment for the real IDOR filter applied on top of this.
    fun findByConversationIdOrderByCreatedAtDesc(conversationId: String): List<PriceOffer>
}
