package rw.itunda.core.repository

import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.Resume
import rw.itunda.core.domain.ResumeCertification
import rw.itunda.core.domain.ResumeEducation
import rw.itunda.core.domain.ResumeExperience

interface ResumeRepository : JpaRepository<Resume, String> {
    fun findByUserId(userId: String): Resume?
}

interface ResumeExperienceRepository : JpaRepository<ResumeExperience, String> {
    fun findByResumeIdOrderByCreatedAtDesc(resumeId: String): List<ResumeExperience>
    fun deleteByIdAndResumeId(id: String, resumeId: String): Long
}

interface ResumeEducationRepository : JpaRepository<ResumeEducation, String> {
    fun findByResumeIdOrderByCreatedAtDesc(resumeId: String): List<ResumeEducation>
    fun deleteByIdAndResumeId(id: String, resumeId: String): Long
}

interface ResumeCertificationRepository : JpaRepository<ResumeCertification, String> {
    fun findByResumeIdOrderByCreatedAtDesc(resumeId: String): List<ResumeCertification>
    fun deleteByIdAndResumeId(id: String, resumeId: String): Long
}
