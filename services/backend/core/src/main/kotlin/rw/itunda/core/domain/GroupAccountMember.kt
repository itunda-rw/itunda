package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * See GroupAccount.kt's doc comment. The organizer is also a real row here (added at
 * creation) so membership checks for view/deposit are a single uniform query rather
 * than "is member OR is owner" scattered across every call site.
 */
@Entity
@Table(name = "group_account_members")
class GroupAccountMember(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "group_account_id", nullable = false, length = 64)
    val groupAccountId: String,

    @Column(name = "user_id", nullable = false, length = 64)
    val userId: String,

    @Column(name = "joined_at", nullable = false)
    val joinedAt: Instant = Instant.now(),
) {
    protected constructor() : this(id = "", groupAccountId = "", userId = "")
}
