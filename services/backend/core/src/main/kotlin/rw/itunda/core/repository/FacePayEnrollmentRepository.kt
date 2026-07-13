package rw.itunda.core.repository

import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.FacePayEnrollment

interface FacePayEnrollmentRepository : JpaRepository<FacePayEnrollment, String> {
    fun findByUserId(userId: String): FacePayEnrollment?
}
