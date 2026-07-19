package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant

/** A real (post, user) like -- DB-unique so a concurrent double-tap can't create two
 * rows even without the app-level idempotency check, same discipline `EatsFavorite`
 * already established for its own (user, restaurant) pair. */
@Entity
@Table(name = "community_likes", uniqueConstraints = [UniqueConstraint(columnNames = ["post_id", "user_id"])])
class CommunityLike(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "post_id", nullable = false, length = 64)
    val postId: String,

    @Column(name = "user_id", nullable = false, length = 64)
    val userId: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
) {
    protected constructor() : this(id = "", postId = "", userId = "")
}
