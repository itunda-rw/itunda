package rw.itunda.core.repository

import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.RideTrustedContact

interface RideTrustedContactRepository : JpaRepository<RideTrustedContact, String> {
    fun findByUserIdOrderByCreatedAtDesc(userId: String): List<RideTrustedContact>
    fun findByIdAndUserId(id: String, userId: String): RideTrustedContact?
    fun countByUserId(userId: String): Long
    fun existsByUserIdAndContactUserId(userId: String, contactUserId: String): Boolean
}
