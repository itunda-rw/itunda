package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant

/** A real (update, user) like vote -- DB-unique so a concurrent double-tap can't
 * create two rows, same shape EatsReviewHelpfulVote already establishes. */
@Entity
@Table(name = "merchant_update_likes", uniqueConstraints = [UniqueConstraint(columnNames = ["update_id", "user_id"])])
class MerchantUpdateLike(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "update_id", nullable = false, length = 64)
    val updateId: String,

    @Column(name = "user_id", nullable = false, length = 64)
    val userId: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
) {
    protected constructor() : this(id = "", updateId = "", userId = "")
}
