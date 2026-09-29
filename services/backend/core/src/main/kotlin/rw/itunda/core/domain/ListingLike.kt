package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant

/** A real (listing, user) like -- DB-unique so a concurrent double-tap can't create two
 * rows even without the app-level idempotency check, same discipline `CommunityLike`
 * already established for its own (post, user) pair. */
@Entity
@Table(name = "listing_likes", uniqueConstraints = [UniqueConstraint(columnNames = ["listing_id", "user_id"])])
class ListingLike(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "listing_id", nullable = false, length = 64)
    val listingId: String,

    @Column(name = "user_id", nullable = false, length = 64)
    val userId: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
) {
    protected constructor() : this(id = "", listingId = "", userId = "")
}
