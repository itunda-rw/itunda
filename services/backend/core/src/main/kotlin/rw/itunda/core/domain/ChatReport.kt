package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/** A participant-authorized report of one chosen private-chat message. */
@Entity
@Table(name = "chat_reports")
class ChatReport(
    @Id @Column(length = 64) val id: String,
    @Column(name = "reporter_user_id", nullable = false, length = 64) val reporterUserId: String,
    @Column(name = "message_id", nullable = false, length = 64) val messageId: String,
    @Column(nullable = false, length = 180) val reason: String,
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) var status: HoodReportStatus = HoodReportStatus.OPEN,
    @Column(name = "reviewed_by", length = 64) var reviewedBy: String? = null,
    @Column(name = "reviewed_at") var reviewedAt: Instant? = null,
    @Column(name = "created_at", nullable = false) val createdAt: Instant = Instant.now(),
) { protected constructor() : this("", "", "", "") }
