package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * A real 당근마켓 Keyword Alert (키워드 알림) -- Karrot Market's own official FAQ
 * (cs.kr.karrotmarket.com/wv/faqs/43): a user registers a real search keyword and is
 * pushed a real notification whenever ANY seller creates a new active listing whose
 * title matches it, without having to keep re-searching. Deliberately minimal, mirroring
 * [ListingFavorite]'s exact shape -- the (user, keyword) pair and when it was
 * registered, nothing else. `keyword` is always stored trimmed and lowercased (see
 * `KeywordAlertService.addAlert`'s own doc comment) so matching is a plain
 * case-insensitive substring check, not a second normalization step at match time.
 * Real DB unique constraint on (user_id, keyword) backs the same application-level
 * "add is idempotent" check `ListingFavoriteService.addFavorite` already establishes
 * for a different real Marketplace feature.
 */
@Entity
@Table(name = "keyword_alerts")
class KeywordAlert(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "user_id", nullable = false, length = 64)
    val userId: String,

    @Column(nullable = false, length = 64)
    val keyword: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
) {
    protected constructor() : this(id = "", userId = "", keyword = "")
}
