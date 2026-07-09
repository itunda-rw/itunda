package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "notifications")
class Notification(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "user_id", nullable = false, length = 64)
    val userId: String,

    @Column(nullable = false, length = 64)
    val type: String,

    @Column(nullable = false)
    val title: String,

    @Column(nullable = false)
    val body: String,

    @Column(name = "is_read", nullable = false)
    var isRead: Boolean,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,

    @Column(name = "data_json", columnDefinition = "TEXT")
    val dataJson: String?
) {
    protected constructor() : this(id = "", userId = "", type = "", title = "", body = "", isRead = false, createdAt = Instant.now(), dataJson = null)
}
