package rw.itunda.core.repository

import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.CommunityPostReport

interface CommunityPostReportRepository : JpaRepository<CommunityPostReport, String> {
    fun findByPostIdAndReporterId(postId: String, reporterId: String): CommunityPostReport?
    fun countByPostId(postId: String): Long
}
