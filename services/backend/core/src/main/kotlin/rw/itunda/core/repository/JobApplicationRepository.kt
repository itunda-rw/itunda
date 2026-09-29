package rw.itunda.core.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.JobApplication
import rw.itunda.core.domain.JobApplicationStatus

interface JobApplicationRepository : JpaRepository<JobApplication, String> {
    fun findByJobPostIdOrderBySubmittedAtDesc(jobPostId: String, pageable: Pageable): Page<JobApplication>
    fun findByApplicantIdOrderBySubmittedAtDesc(applicantId: String, pageable: Pageable): Page<JobApplication>
    fun existsByJobPostIdAndApplicantIdAndStatus(jobPostId: String, applicantId: String, status: JobApplicationStatus): Boolean
}
