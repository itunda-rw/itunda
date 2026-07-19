package rw.itunda.core.repository

import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.PropertyPriceOffer

interface PropertyPriceOfferRepository : JpaRepository<PropertyPriceOffer, String> {
    // Real negotiation history for a listing, newest-first -- bounded by how many real
    // offers/counters a single listing realistically accumulates, matching
    // PriceOfferRepository.findByListingIdOrderByCreatedAtDesc's own note.
    fun findByPropertyListingIdOrderByCreatedAtDesc(propertyListingId: String): List<PropertyPriceOffer>

    // Real per-thread negotiation history -- see PropertyPriceOfferService's own doc
    // comment for the real IDOR filter applied on top of this.
    fun findByConversationIdOrderByCreatedAtDesc(conversationId: String): List<PropertyPriceOffer>
}
