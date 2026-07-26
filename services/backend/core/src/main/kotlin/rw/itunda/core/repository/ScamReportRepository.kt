package rw.itunda.core.repository

import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.ScamReport

interface ScamReportRepository : JpaRepository<ScamReport, String> {
    fun countByReportedIdentifier(reportedIdentifier: String): Long
    fun findByReporterIdOrderByCreatedAtDesc(reporterId: String): List<ScamReport>
    fun existsByReporterIdAndReportedIdentifier(reporterId: String, reportedIdentifier: String): Boolean
}
