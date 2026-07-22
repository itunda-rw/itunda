package rw.itunda.core.web

import rw.itunda.core.repository.UserRepository

/**
 * Real Karrot-Score-style trust badge (2026-07-21) -- see
 * rw.itunda.core.trust.TrustScoreService's own doc comment for the full account of why
 * this is a plain 0-1000 score starting at 30. Additive helper, same "spread an extra
 * top-level key alongside the existing array" discipline `pageMeta` established above:
 * every listing/job-post/property-listing browse endpoint across Marketplace/Jobs/
 * RealEstate spreads a `trustScores` map (sellerId/posterId/listerId -> cached
 * `User.trustScore`) alongside its own array, resolved with exactly one `findAllById`
 * batch call -- the same N+1-avoiding discipline `ListingFavoriteService`'s own batch
 * listing-detail resolve already established, rather than one query per row.
 */
fun trustScores(userRepository: UserRepository, userIds: Collection<String>): Map<String, Int> {
    val distinctIds = userIds.distinct()
    if (distinctIds.isEmpty()) return emptyMap()
    return userRepository.findAllById(distinctIds).associate { it.id to it.trustScore }
}
