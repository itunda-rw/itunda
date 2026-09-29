package rw.itunda.core.repository

import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.EatsReviewReport

interface EatsReviewReportRepository : JpaRepository<EatsReviewReport, String> {
    fun findByReviewIdAndReporterId(reviewId: String, reporterId: String): EatsReviewReport?
    fun countByReviewId(reviewId: String): Long
}
