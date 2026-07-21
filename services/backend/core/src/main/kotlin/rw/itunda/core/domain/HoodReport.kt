package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

enum class HoodReportTargetType { MARKETPLACE_LISTING, COMMUNITY_POST, JOB_POST, PROPERTY_LISTING }
enum class HoodReportStatus { OPEN, RESOLVED }

@Entity
@Table(name = "hood_reports")
class HoodReport(
    @Id @Column(length = 64) val id: String,
    @Column(name = "reporter_user_id", nullable = false, length = 64) val reporterUserId: String,
    @Enumerated(EnumType.STRING) @Column(name = "target_type", nullable = false, length = 32) val targetType: HoodReportTargetType,
    @Column(name = "target_id", nullable = false, length = 64) val targetId: String,
    @Column(nullable = false, length = 180) val reason: String,
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) var status: HoodReportStatus = HoodReportStatus.OPEN,
    @Column(name = "reviewed_by", length = 64) var reviewedBy: String? = null,
    @Column(name = "reviewed_at") var reviewedAt: Instant? = null,
    @Column(name = "created_at", nullable = false) val createdAt: Instant = Instant.now(),
) { protected constructor() : this("", "", HoodReportTargetType.JOB_POST, "", "") }
