package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/** A real comment on a `CommunityPost` -- see CommunityPost's own doc comment. Oldest-
 * first ordering (a real conversation thread), not newest-first like the post feed
 * itself. No edit/delete in v1, matching this project's own "MVP first, name the rest
 * honestly" discipline for brand-new surfaces. */
@Entity
@Table(name = "community_comments")
class CommunityComment(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "post_id", nullable = false, length = 64)
    val postId: String,

    @Column(name = "author_id", nullable = false, length = 64)
    val authorId: String,

    @Column(nullable = false, length = 1000)
    var body: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
) {
    protected constructor() : this(id = "", postId = "", authorId = "", body = "")
}
