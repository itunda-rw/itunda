package rw.itunda.core.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.HoodReport
import rw.itunda.core.domain.HoodReportStatus
import rw.itunda.core.domain.HoodReportTargetType

interface HoodReportRepository : JpaRepository<HoodReport, String> {
    fun findByReporterUserIdAndTargetTypeAndTargetIdAndStatus(reporterUserId: String, targetType: HoodReportTargetType, targetId: String, status: HoodReportStatus): HoodReport?
    fun findByStatusOrderByCreatedAtAsc(status: HoodReportStatus, pageable: Pageable): Page<HoodReport>
}
