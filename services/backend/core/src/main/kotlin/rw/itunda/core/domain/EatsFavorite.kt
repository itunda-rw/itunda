package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * A real bookmarked/favorited restaurant (2026-07-19) -- closes the "favorites" item on
 * the Eats polish roadmap. Deliberately minimal: just the (buyer, restaurant) pair and
 * when it was favorited, no rating/note/anything else. Real DB unique constraint on
 * (user_id, restaurant_id) backs the same application-level "add is idempotent" check
 * `EatsFavoriteService.addFavorite` makes, not just the application check alone -- a
 * genuine race between two concurrent favorite taps for the same restaurant still can't
 * create two rows.
 */
@Entity
@Table(name = "eats_favorites")
class EatsFavorite(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "user_id", nullable = false, length = 64)
    val userId: String,

    @Column(name = "restaurant_id", nullable = false, length = 64)
    val restaurantId: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
) {
    protected constructor() : this(id = "", userId = "", restaurantId = "")
}
